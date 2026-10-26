package dev.itonk.languagemod.client;

import dev.itonk.languagemod.client.voice.VoiceInput;
import dev.itonk.languagemod.client.voice.VoiceSettings;
import dev.itonk.languagemod.network.HudPayloads;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;

/** Client wiring only: packets -> state + voice, tick -> state, HUD -> renderer. */
public final class LanguageModClient implements ClientModInitializer {
	public static final ChallengeHudState HUD = new ChallengeHudState();
	public static VoiceInput VOICE;
	/** Toggle keys: the screens close themselves on the same key. */
	static KeyBinding dictionaryKey;
	static KeyBinding settingsKey;
	private static int consentDelay;
	/** Runs the rules in this game when the server has no LanguageMod. */
	public static final ClientOnlyMode CLIENT_ONLY = new ClientOnlyMode();

	@Override
	public void onInitializeClient() {
		Path dir = FabricLoader.getInstance().getConfigDir().resolve("languagemod");
		VoiceSettings settings = VoiceSettings.load(dir);
		VOICE = new VoiceInput(settings, dir.resolve("whisper"), HUD::heard);

		KeyBinding.Category category = KeyBinding.Category.create(Identifier.of("languagemod", "voice"));
		KeyBinding talkKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.languagemod.talk",
				InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_V, category));
		dictionaryKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.languagemod.dictionary",
				InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_J, category));
		settingsKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.languagemod.settings",
				InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, category));
		ClientPlayNetworking.registerGlobalReceiver(HudPayloads.DictionarySnapshot.ID,
				(p, ctx) -> DictionaryScreen.receive(p.entries()));

		// Fabric runs these receivers on the client thread, same thread as rendering.
		ClientPlayNetworking.registerGlobalReceiver(HudPayloads.Start.ID, (p, ctx) -> {
			HUD.start(p.stack(), p.word(), p.languageName(), p.durationTicks());
			VOICE.onChallengeStart(p.languageCode(), p.word());
		});
		ClientPlayNetworking.registerGlobalReceiver(HudPayloads.Attempt.ID,
				(p, ctx) -> HUD.attempt(p.attempt(), p.mask()));
		ClientPlayNetworking.registerGlobalReceiver(HudPayloads.End.ID, (p, ctx) -> {
			HUD.end(p.success(), p.itemKept());
			VOICE.onChallengeEnd();
		});

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			// Paused singleplayer also pauses the server timer, so pause the bar too.
			if (!client.isPaused()) HUD.tick();
			CLIENT_ONLY.tick(client);
			VOICE.tickPushToTalk(talkKey.isPressed());
			while (dictionaryKey.wasPressed()) client.setScreen(new DictionaryScreen());
			while (settingsKey.wasPressed()) client.setScreen(new SettingsScreen(null));
			// Ask once, in game, before the 190 MB voice model is downloaded.
			if (VOICE.needsModelConsent() && client.player != null && client.currentScreen == null && ++consentDelay > 60) {
				client.setScreen(new net.minecraft.client.gui.screen.ConfirmScreen(yes -> {
					VOICE.answerModelDownload(yes);
					client.setScreen(null);
				}, net.minecraft.text.Text.literal("Download the voice model?"),
						net.minecraft.text.Text.literal("To answer by voice, Say It Or Lose It needs a speech model: ggml-small-q5_1.bin, 190 MB, "
								+ "from huggingface.co (ggerganov/whisper.cpp). It runs on your PC; your voice is never sent anywhere. "
								+ "No = type your answers in chat. You can download it later in Settings (K)."),
						net.minecraft.text.Text.literal("Download"), net.minecraft.text.Text.literal("Not now")));
			}
		});
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> CLIENT_ONLY.onJoin());
		// In client-only mode a chat line while a word is asked is the answer: keep it off the server.
		ClientSendMessageEvents.ALLOW_CHAT.register(message -> !CLIENT_ONLY.onChat(message));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			CLIENT_ONLY.onDisconnect();
			HUD.reset();
			VOICE.onChallengeEnd();
		});

		ChallengeHudRenderer renderer = new ChallengeHudRenderer(HUD, VOICE);
		HudElementRegistry.addLast(Identifier.of("languagemod", "challenge_popup"), renderer::render);
	}
}
