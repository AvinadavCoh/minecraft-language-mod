package dev.itonk.languagemod.client;

import dev.itonk.languagemod.LanguageMod;
import dev.itonk.languagemod.client.voice.VoiceSettings;
import dev.itonk.languagemod.config.ModConfig;
import dev.itonk.languagemod.dictionary.LanguageList;
import dev.itonk.languagemod.network.HudPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

/**
 * In-game settings (key K, or the button in the dictionary): language to learn, time limit,
 * what a miss does, voice mode and GPU. Writes config.json / voice.json and applies them.
 * On a server that runs the mod, that server's own config sets the rules, so only voice is editable.
 */
public final class SettingsScreen extends Screen {
	private static final int[] TIMES = {10, 15, 20, 30, 45, 60};
	private static final String[] VOICE_MODES = {"open", "push_to_talk", "off"};
	private static final String[] VOICE_NAMES = {"Open mic", "Push-to-talk (hold V)", "Off"};
	private static final int W = 260, ROW = 24;

	private final Screen parent;
	private final Path dir = FabricLoader.getInstance().getConfigDir().resolve("languagemod");
	private final List<LanguageList.Language> languages = LanguageList.load();
	private final VoiceSettings voice = LanguageModClient.VOICE.settings();
	private ModConfig config;
	private int language;
	private int time;
	private boolean deleteItem;
	private int voiceMode;
	private boolean gpu;
	private String startVoiceMode;
	private boolean startGpu;
	private String message = "";

	public SettingsScreen(Screen parent) {
		super(Text.literal("Say It Or Lose It: settings"));
		this.parent = parent;
	}

	/** On a server running the mod, its config decides the rules (our files would change nothing). */
	private boolean rulesLocked() {
		return client != null && client.getServer() == null && ClientPlayNetworking.canSend(HudPayloads.DictionaryRequest.ID);
	}

	@Override
	protected void init() {
		if (config == null) {
			try {
				config = ModConfig.load(dir);
			} catch (Exception e) {
				config = new ModConfig();
			}
			language = Math.max(0, indexOfLanguage(config.targetLanguage));
			time = nearestTime(config.timeoutSeconds);
			deleteItem = config.deleteItemOnFail;
			voiceMode = Math.max(0, List.of(VOICE_MODES).indexOf(voice.mode));
			gpu = !"off".equalsIgnoreCase(voice.gpu);
			startVoiceMode = VOICE_MODES[voiceMode];
			startGpu = gpu;
		}
		int x = (width - W) / 2;
		int y = 40;
		boolean locked = rulesLocked();
		cycle(x, y, () -> "Language to learn: " + languages.get(language).name(), !locked,
				() -> language = (language + 1) % languages.size());
		cycle(x, y += ROW, () -> "Time to answer: " + TIMES[time] + " s", !locked, () -> time = (time + 1) % TIMES.length);
		cycle(x, y += ROW, () -> "On a miss: " + (deleteItem ? "lose the item + damage" : "damage only, keep the item"), !locked,
				() -> deleteItem = !deleteItem);
		y += 8;
		cycle(x, y += ROW, () -> "Voice: " + VOICE_NAMES[voiceMode], true, () -> voiceMode = (voiceMode + 1) % VOICE_MODES.length);
		cycle(x, y += ROW, () -> "Speech engine: " + (gpu ? "GPU when available" : "CPU only"), true, () -> gpu = !gpu);
		boolean present = LanguageModClient.VOICE.modelPresent();
		ButtonWidget model = ButtonWidget.builder(Text.literal(present ? "Voice model: downloaded" : "Download voice model (190 MB)"), b -> {
			LanguageModClient.VOICE.answerModelDownload(true);
			b.setMessage(Text.literal("Downloading voice model..."));
			b.active = false;
		}).dimensions(x, y += ROW, W, 20).build();
		model.active = !present;
		addDrawableChild(model);
		addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> save()).dimensions(x, height - 30, W / 2 - 2, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), b -> close()).dimensions(x + W / 2 + 2, height - 30, W / 2 - 2, 20).build());
	}

	private void cycle(int x, int y, Supplier<String> label, boolean active, Runnable next) {
		ButtonWidget b = ButtonWidget.builder(Text.literal(label.get()), btn -> {
			next.run();
			btn.setMessage(Text.literal(label.get()));
		}).dimensions(x, y, W, 20).build();
		b.active = active;
		addDrawableChild(b);
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		super.render(ctx, mouseX, mouseY, delta);
		ctx.drawCenteredTextWithShadow(textRenderer, title, width / 2, 14, 0xFFFFFFFF);
		int y = 40 + 6 * ROW + 8 + 6;
		if (rulesLocked()) {
			ctx.drawCenteredTextWithShadow(textRenderer, "This server runs the mod: its own config sets the rules.", width / 2, y, 0xFFAAAAAA);
			y += 12;
		}
		if (gpu != startGpu || (!startVoiceMode.equals("off") != !VOICE_MODES[voiceMode].equals("off"))) {
			ctx.drawCenteredTextWithShadow(textRenderer, "Speech engine / voice on-off take effect after a restart.", width / 2, y, 0xFFFFD34E);
			y += 12;
		}
		if (!message.isEmpty()) ctx.drawCenteredTextWithShadow(textRenderer, message, width / 2, y, 0xFFFF7777);
	}

	private void save() {
		try {
			if (!rulesLocked()) {
				LanguageList.Language lang = languages.get(language);
				if (!GameDictionaries.ensure(client, lang, dir)) {
					message = "No " + lang.name() + " word list could be built from the game.";
					return;
				}
				boolean languageChanged = !lang.code().equals(config.targetLanguage);
				config.targetLanguage = lang.code();
				config.targetLanguageName = lang.name();
				config.timeoutSeconds = TIMES[time];
				config.deleteItemOnFail = deleteItem;
				config.save(dir);
				applyRules();
				if (languageChanged) {
					// The open question was in the old language.
					LanguageModClient.HUD.reset();
					LanguageModClient.VOICE.onChallengeEnd();
					if (client.player != null) {
						client.player.sendMessage(Text.literal("[Say It] Now learning " + lang.name()
								+ ". Your " + lang.name() + " dictionary is separate from the others."), false);
					}
				}
			}
			voice.mode = VOICE_MODES[voiceMode];
			voice.gpu = gpu ? "auto" : "off";
			voice.save(dir);
			close();
		} catch (Exception e) {
			LanguageMod.LOG.error("[settings] could not save", e);
			message = "Could not save: " + e.getMessage();
		}
	}

	/** Same config files for every side: the integrated server reloads on its own thread, client-only mode here. */
	private void applyRules() {
		var server = client.getServer();
		if (server != null) {
			server.execute(LanguageMod::reload);
		} else {
			LanguageMod.reload();
			LanguageModClient.CLIENT_ONLY.reload();
		}
	}

	@Override
	public boolean keyPressed(KeyInput input) {
		if (LanguageModClient.settingsKey.matchesKey(input)) {
			close();
			return true;
		}
		return super.keyPressed(input);
	}

	@Override
	public void close() {
		client.setScreen(parent);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	private int indexOfLanguage(String code) {
		for (int i = 0; i < languages.size(); i++) if (languages.get(i).code().equals(code)) return i;
		return -1;
	}

	private static int nearestTime(int seconds) {
		int best = 0;
		for (int i = 0; i < TIMES.length; i++) if (Math.abs(TIMES[i] - seconds) < Math.abs(TIMES[best] - seconds)) best = i;
		return best;
	}
}
