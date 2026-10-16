package com.mehbur.mehburity.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.mehbur.mehburity.MehburityMod;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.Supplier;

/**
 * Reads and writes one JSON config file. A missing file is created with defaults; a broken file is
 * kept as {@code <name>.broken} so hand edits are never silently lost, and defaults are used instead.
 * After every load the sanitized values are written back, so new fields show up in the file automatically.
 */
public final class JsonConfigFile<T extends JsonConfigFile.Sanitizable> {
	/** Implemented by config roots to repair null sections and out-of-range values after loading. */
	public interface Sanitizable {
		void sanitize();
	}

	public enum Status {
		LOADED, CREATED, RECOVERED_FROM_BROKEN, IO_ERROR
	}

	public record Result<T>(T value, Status status) {
		public boolean ok() {
			return status == Status.LOADED || status == Status.CREATED;
		}
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private final Path path;
	private final Class<T> type;
	private final Supplier<T> defaults;

	public JsonConfigFile(Path path, Class<T> type, Supplier<T> defaults) {
		this.path = path;
		this.type = type;
		this.defaults = defaults;
	}

	public Path path() {
		return path;
	}

	public Result<T> load() {
		if (!Files.exists(path)) {
			T value = defaults.get();
			value.sanitize();
			save(value);
			return new Result<>(value, Status.CREATED);
		}

		T loaded;
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			loaded = GSON.fromJson(reader, type);
		} catch (JsonParseException e) {
			MehburityMod.LOGGER.warn("{} is not valid JSON ({}), keeping it as .broken and using defaults",
					path.getFileName(), e.getMessage());
			backupBroken();
			T value = defaults.get();
			value.sanitize();
			save(value);
			return new Result<>(value, Status.RECOVERED_FROM_BROKEN);
		} catch (IOException e) {
			// Do not overwrite a file we could not read (it may be locked by an editor).
			MehburityMod.LOGGER.error("Could not read {}, using defaults for this session", path.getFileName(), e);
			T value = defaults.get();
			value.sanitize();
			return new Result<>(value, Status.IO_ERROR);
		}

		// An empty file parses to null.
		T value = loaded != null ? loaded : defaults.get();
		value.sanitize();
		save(value);
		return new Result<>(value, Status.LOADED);
	}

	public boolean save(T value) {
		Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
				GSON.toJson(value, type, writer);
			}
			try {
				Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
			}
			return true;
		} catch (IOException e) {
			MehburityMod.LOGGER.error("Could not write {}", path.getFileName(), e);
			return false;
		}
	}

	private void backupBroken() {
		try {
			Files.copy(path, path.resolveSibling(path.getFileName() + ".broken"), StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			MehburityMod.LOGGER.error("Could not back up broken {}", path.getFileName(), e);
		}
	}
}
