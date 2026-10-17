package com.mehbur.mehburity.client.ai;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;
import java.io.ByteArrayOutputStream;

/**
 * Records the default microphone as 16 kHz, 16-bit, mono PCM on its own thread until {@link #stop()} or the
 * time limit. One recorder per push-to-talk press; it is not reusable.
 */
public final class MicRecorder {
	public static final int SAMPLE_RATE = 16000;
	private static final AudioFormat FORMAT = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
	/** Read in chunks of 50 ms, so stopping is quick. */
	private static final int CHUNK = SAMPLE_RATE * 2 / 20;

	private final TargetDataLine line;
	private final ByteArrayOutputStream pcm = new ByteArrayOutputStream();
	private final int maxBytes;
	private final Thread thread;
	private volatile boolean running = true;
	/** Loudest sample so far, 0..1, for the level shown while recording and by the microphone test. */
	private volatile float peak;

	private MicRecorder(TargetDataLine line, double maxSeconds) {
		this.line = line;
		this.maxBytes = (int) (maxSeconds * SAMPLE_RATE * 2);
		this.thread = new Thread(this::run, "Mehburity microphone");
		this.thread.setDaemon(true);
	}

	/** Opens the default microphone and starts recording. Throws when there is no usable microphone. */
	public static MicRecorder start(double maxSeconds) throws LineUnavailableException {
		DataLine.Info info = new DataLine.Info(TargetDataLine.class, FORMAT);
		if (!AudioSystem.isLineSupported(info)) {
			throw new LineUnavailableException("No microphone supports 16 kHz mono");
		}
		TargetDataLine line = (TargetDataLine) AudioSystem.getLine(info);
		line.open(FORMAT, CHUNK * 4);
		line.start();
		MicRecorder recorder = new MicRecorder(line, maxSeconds);
		recorder.thread.start();
		return recorder;
	}

	private void run() {
		byte[] buffer = new byte[CHUNK];
		try {
			while (running) {
				int read = line.read(buffer, 0, buffer.length);
				if (read <= 0) {
					continue;
				}
				synchronized (pcm) {
					pcm.write(buffer, 0, read);
				}
				for (int i = 0; i + 1 < read; i += 2) {
					float sample = Math.abs((short) ((buffer[i] & 0xFF) | (buffer[i + 1] << 8))) / 32768.0F;
					if (sample > peak) {
						peak = sample;
					}
				}
				if (pcm.size() >= maxBytes) {
					running = false;
				}
			}
		} finally {
			line.stop();
			line.close();
		}
	}

	/** True until the time limit is reached (or {@link #stop()} was called). */
	public boolean isRecording() {
		return running;
	}

	public double seconds() {
		return pcm.size() / (SAMPLE_RATE * 2.0);
	}

	public float peak() {
		return peak;
	}

	/** Stops and returns what was recorded (raw little-endian PCM). */
	public byte[] stop() {
		running = false;
		try {
			thread.join(500);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		synchronized (pcm) {
			return pcm.toByteArray();
		}
	}
}
