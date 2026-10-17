package com.mehbur.mehburity.client.screen;

import com.mehbur.mehburity.ai.GeminiClient;
import com.mehbur.mehburity.ai.GeminiException;
import com.mehbur.mehburity.client.ai.ClientAi;
import com.mehbur.mehburity.client.ai.MicRecorder;
import com.mehbur.mehburity.client.ai.VoiceInput;
import com.mehbur.mehburity.client.ai.VoicePlayer;
import com.mehbur.mehburity.client.config.MehburityClientConfig;
import com.mehbur.mehburity.config.MehburityConfig;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Mods → Mehburity → Configure (or {@code /mehburity ayarlar}): Gemini API key, model, Mehburity's voice and
 * push-to-talk (key, microphone test, longest recording). Changes apply immediately; "Done" saves them, "Cancel"
 * reloads the file. The push-to-talk key is a normal key binding and is saved right away, like in Controls.
 */
public class MehburityConfigScreen extends Screen {
	private static final String KEY = "mehburity.config.";
	private static final String API_KEY_PAGE = "https://aistudio.google.com/apikey";
	private static final int FULL_WIDTH = 308;
	private static final int HALF_WIDTH = 150;
	private static final double MIC_TEST_SECONDS = 2.0;
	/** Below this peak level (percent) the microphone test warns that it hears almost nothing. */
	private static final int MIC_QUIET_PERCENT = 3;

	/** Gemini's prebuilt voices and their character (ai.google.dev speech-generation docs). */
	private static final List<String[]> VOICES = List.of(
			new String[]{"Puck", "Upbeat"}, new String[]{"Fenrir", "Excitable"}, new String[]{"Sadachbia", "Lively"},
			new String[]{"Laomedeia", "Upbeat"}, new String[]{"Zephyr", "Bright"}, new String[]{"Autonoe", "Bright"},
			new String[]{"Leda", "Youthful"}, new String[]{"Achird", "Friendly"}, new String[]{"Zubenelgenubi", "Casual"},
			new String[]{"Aoede", "Breezy"}, new String[]{"Callirrhoe", "Easy-going"}, new String[]{"Umbriel", "Easy-going"},
			new String[]{"Charon", "Informative"}, new String[]{"Rasalgethi", "Informative"}, new String[]{"Kore", "Firm"},
			new String[]{"Orus", "Firm"}, new String[]{"Alnilam", "Firm"}, new String[]{"Enceladus", "Breathy"},
			new String[]{"Iapetus", "Clear"}, new String[]{"Erinome", "Clear"}, new String[]{"Algieba", "Smooth"},
			new String[]{"Despina", "Smooth"}, new String[]{"Algenib", "Gravelly"}, new String[]{"Achernar", "Soft"},
			new String[]{"Schedar", "Even"}, new String[]{"Gacrux", "Mature"}, new String[]{"Pulcherrima", "Forward"},
			new String[]{"Vindemiatrix", "Gentle"}, new String[]{"Sadaltager", "Knowledgeable"}, new String[]{"Sulafat", "Warm"});

	private final @Nullable Screen parent;
	private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
	private final MehburityClientConfig config = MehburityClientConfig.get();
	private @Nullable EditBox keyBox;
	private @Nullable StringWidget status;
	private boolean showKey;
	private boolean saved;
	/** The talk key button is waiting for a key (or mouse button) to bind. */
	private @Nullable Button talkKeyButton;
	private boolean capturingKey;
	/** Running microphone test. */
	private @Nullable MicRecorder micTest;

	public MehburityConfigScreen(@Nullable Screen parent) {
		super(Component.translatableWithFallback(KEY + "title", "Mehburity Settings"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		layout.addTitleHeader(this.title, this.font);
		GridLayout grid = new GridLayout().columnSpacing(8).rowSpacing(4);
		GridLayout.RowHelper rows = grid.createRowHelper(2);

		// API key, hidden as dots unless "Show" is on.
		keyBox = new EditBox(this.font, FULL_WIDTH, 20, Component.translatableWithFallback(KEY + "api_key", "Gemini API key"));
		keyBox.setMaxLength(256);
		keyBox.setHint(Component.translatableWithFallback(KEY + "api_key.hint", "Paste your Gemini API key here").withStyle(ChatFormatting.DARK_GRAY));
		keyBox.setValue(config.geminiApiKey);
		keyBox.addFormatter((text, offset) -> showKey ? null : FormattedCharSequence.forward("•".repeat(text.length()), Style.EMPTY));
		keyBox.setResponder(value -> config.geminiApiKey = value.strip());
		rows.addChild(keyBox, 2);

		rows.addChild(Button.builder(showKeyLabel(), button -> {
			showKey = !showKey;
			button.setMessage(showKeyLabel());
		}).width(HALF_WIDTH).build());
		rows.addChild(Button.builder(Component.translatableWithFallback(KEY + "test", "Test connection"), button -> testConnection())
				.width(HALF_WIDTH).build());

		status = new StringWidget(FULL_WIDTH, 12, config.geminiApiKey.isBlank()
				? Component.translatableWithFallback(KEY + "no_key", "No API key yet: Mehburity uses built-in replies.").withStyle(ChatFormatting.GRAY)
				: Component.translatableWithFallback(KEY + "has_key", "API key saved. Press \"Test connection\" to check it.").withStyle(ChatFormatting.GRAY),
				this.font);
		rows.addChild(status, 2);

		rows.addChild(CycleButton.builder((String model) -> Component.translatableWithFallback(KEY + "model_value", "Model: %s", shortModel(model)), config.chatModel)
				.withValues(MehburityClientConfig.CHAT_MODELS)
				.displayOnlyValue()
				.create(0, 0, HALF_WIDTH, 20, Component.translatableWithFallback(KEY + "model", "Model"),
						(button, value) -> config.chatModel = value));
		rows.addChild(Button.builder(Component.translatableWithFallback(KEY + "get_key", "Get an API key →"),
				ConfirmLinkScreen.confirmLink(this, API_KEY_PAGE)).width(HALF_WIDTH).build());

		rows.addChild(CycleButton.onOffBuilder(config.voiceEnabled)
				.create(0, 0, HALF_WIDTH, 20, Component.translatableWithFallback(KEY + "voice", "Speech"),
						(button, value) -> config.voiceEnabled = value));
		rows.addChild(CycleButton.builder(MehburityConfigScreen::voiceLabel, voiceOrDefault(config.voiceName))
				.withValues(VOICES.stream().map(v -> v[0]).toList())
				.create(0, 0, HALF_WIDTH, 20, Component.translatableWithFallback(KEY + "voice_name", "Voice"),
						(button, value) -> config.voiceName = value));

		rows.addChild(CycleButton.builder((String model) -> Component.translatableWithFallback(KEY + "voice_model_value", "TTS: %s", shortModel(model)), config.voiceModel)
				.withValues(MehburityClientConfig.VOICE_MODELS)
				.displayOnlyValue()
				.create(0, 0, HALF_WIDTH, 20, Component.translatableWithFallback(KEY + "voice_model", "Voice model"),
						(button, value) -> config.voiceModel = value));
		rows.addChild(new VolumeSlider(config.voiceVolume));

		rows.addChild(Button.builder(Component.translatableWithFallback(KEY + "listen", "▶ Listen"),
				button -> VoicePlayer.speak(MehburityConfig.get().ai.greeting, config.voiceName, config.voiceStyle))
				.width(HALF_WIDTH).build());
		rows.addChild(Button.builder(Component.translatableWithFallback(KEY + "mic_test", "🎙 Test microphone"), button -> startMicTest())
				.width(HALF_WIDTH).build());

		talkKeyButton = Button.builder(talkKeyLabel(), button -> captureTalkKey()).width(HALF_WIDTH).tooltip(Tooltip.create(Component.translatableWithFallback(KEY + "talk_key.tooltip",
				"Hold this key and speak; release to send it to Mehburity. Esc removes the key."))).build();
		rows.addChild(talkKeyButton);
		rows.addChild(new RecordingSlider(config.pushToTalkMaxSeconds));

		layout.addToContents(grid);
		LinearLayout footer = layout.addToFooter(LinearLayout.horizontal().spacing(8));
		footer.addChild(Button.builder(CommonComponents.GUI_DONE, button -> {
			config.sanitize();
			MehburityClientConfig.save();
			saved = true;
			onClose();
		}).width(HALF_WIDTH).build());
		footer.addChild(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose()).width(HALF_WIDTH).build());

		layout.visitWidgets(this::addRenderableWidget);
		repositionElements();
	}

	@Override
	protected void repositionElements() {
		layout.arrangeElements();
	}

	@Override
	protected void setInitialFocus() {
		if (keyBox != null) {
			setInitialFocus(keyBox);
		}
	}

	@Override
	public void onClose() {
		if (!saved) {
			MehburityClientConfig.load(); // throw away unsaved changes
		}
		this.minecraft.gui.setScreen(parent);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (capturingKey) {
			bindTalkKey(event.isEscape() ? InputConstants.UNKNOWN : InputConstants.getKey(event));
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (capturingKey) {
			bindTalkKey(InputConstants.Type.MOUSE.getOrCreate(event.button()));
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	/** The next key (or mouse button) becomes the push-to-talk key; Esc removes it. */
	public void captureTalkKey() {
		capturingKey = true;
		if (talkKeyButton != null) {
			talkKeyButton.setMessage(talkKeyLabel());
		}
	}

	private void bindTalkKey(InputConstants.Key key) {
		capturingKey = false;
		VoiceInput.PUSH_TO_TALK.setKey(key);
		KeyMapping.resetMapping();
		this.minecraft.options.save();
		if (talkKeyButton != null) {
			talkKeyButton.setMessage(talkKeyLabel());
		}
	}

	private Component talkKeyLabel() {
		Component key = capturingKey
				? Component.literal("> ").append(Component.translatableWithFallback(KEY + "press_key", "press a key")).append(" <")
						.withStyle(ChatFormatting.YELLOW)
				: VoiceInput.PUSH_TO_TALK.isUnbound()
						? Component.translatableWithFallback(KEY + "talk_key.none", "none").withStyle(ChatFormatting.GRAY)
						: VoiceInput.PUSH_TO_TALK.getTranslatedKeyMessage();
		return Component.translatableWithFallback(KEY + "talk_key", "Talk key: %s", key);
	}

	/** Records two seconds and shows how loud it was, so the player knows the microphone works. */
	private void startMicTest() {
		if (status == null || micTest != null) {
			return;
		}
		try {
			micTest = MicRecorder.start(MIC_TEST_SECONDS);
			status.setMessage(Component.translatableWithFallback(KEY + "mic_speak", "🎙 Say something...").withStyle(ChatFormatting.AQUA));
		} catch (Exception e) {
			status.setMessage(Component.translatableWithFallback(KEY + "mic_none", "No microphone found. Check Windows sound settings.")
					.withStyle(ChatFormatting.RED));
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (micTest != null && !micTest.isRecording()) {
			micTest.stop();
			int level = Math.round(micTest.peak() * 100);
			micTest = null;
			if (status != null) {
				status.setMessage(level < MIC_QUIET_PERCENT
						? Component.translatableWithFallback(KEY + "mic_quiet", "The microphone hears almost nothing (level %s%%).", level)
								.withStyle(ChatFormatting.YELLOW)
						: Component.translatableWithFallback(KEY + "mic_ok", "Microphone works ✔ (level %s%%)", level)
								.withStyle(ChatFormatting.GREEN));
			}
		}
	}

	@Override
	public void removed() {
		if (micTest != null) {
			micTest.stop();
			micTest = null;
		}
		super.removed();
	}

	private Component showKeyLabel() {
		return showKey
				? Component.translatableWithFallback(KEY + "hide_key", "Hide key")
				: Component.translatableWithFallback(KEY + "show_key", "Show key");
	}

	private void testConnection() {
		if (status == null) {
			return;
		}
		status.setMessage(Component.translatableWithFallback(KEY + "testing", "Testing...").withStyle(ChatFormatting.GRAY));
		String key = config.geminiApiKey;
		new GeminiClient(config.baseUri(), () -> key).checkKey().whenComplete((ok, error) -> this.minecraft.execute(() -> {
			if (status == null) {
				return;
			}
			status.setMessage(error == null
					? Component.translatableWithFallback(KEY + "test_ok", "Gemini connected ✔").withStyle(ChatFormatting.GREEN)
					: ClientAi.problemText(GeminiException.of(error).kind()).withStyle(ChatFormatting.RED));
		}));
	}

	/** "gemini-3.8-flash-lite-tts" → "3.8-flash-lite", so it fits on a button. */
	private static String shortModel(String model) {
		return model.replaceFirst("^gemini-", "").replaceFirst("-tts$", "");
	}

	private static String voiceOrDefault(String name) {
		for (String[] voice : VOICES) {
			if (voice[0].equals(name)) {
				return name;
			}
		}
		return VOICES.getFirst()[0];
	}

	private static Component voiceLabel(String name) {
		for (String[] voice : VOICES) {
			if (voice[0].equals(name)) {
				return Component.literal(voice[0] + " – " + voice[1]);
			}
		}
		return Component.literal(name);
	}

	/** Longest push-to-talk recording, 3-30 seconds. */
	private final class RecordingSlider extends AbstractSliderButton {
		private static final double MIN = 3.0;
		private static final double MAX = 30.0;

		RecordingSlider(double seconds) {
			super(0, 0, HALF_WIDTH, 20, Component.empty(), (seconds - MIN) / (MAX - MIN));
			updateMessage();
		}

		private double seconds() {
			return Math.round(MIN + value * (MAX - MIN));
		}

		@Override
		protected void updateMessage() {
			setMessage(Component.translatableWithFallback(KEY + "talk_seconds", "Longest recording: %s s", (int) seconds()));
		}

		@Override
		protected void applyValue() {
			config.pushToTalkMaxSeconds = seconds();
		}
	}

	private final class VolumeSlider extends AbstractSliderButton {
		VolumeSlider(double initial) {
			super(0, 0, HALF_WIDTH, 20, Component.empty(), initial);
			updateMessage();
		}

		@Override
		protected void updateMessage() {
			setMessage(Component.translatableWithFallback(KEY + "volume", "Voice volume: %s%%", Math.round(value * 100)));
		}

		@Override
		protected void applyValue() {
			config.voiceVolume = value;
		}
	}
}
