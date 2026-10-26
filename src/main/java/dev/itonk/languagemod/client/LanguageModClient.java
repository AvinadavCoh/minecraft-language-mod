package dev.itonk.languagemod.client;

import dev.itonk.languagemod.client.voice.VoiceInput;
import dev.itonk.languagemod.client.voice.VoiceSettings;
import dev.itonk.languagemod.network.HudPayloads;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
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

	@Override
	public void onInitializeClient() {
		Path dir = FabricLoader.getInstance().getConfigDir().resolve("languagemod");
		VoiceSettings settings = VoiceSettings.load(dir);
		VOICE = new VoiceInput(settings, dir.resolve("whisper"), HUD::heard);

		KeyBinding talkKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.languagemod.talk",
				InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_V, KeyBinding.Category.create(Identifier.of("languagemod", "voice"))));

		// Fabric runs these receivers on the client thread, same thread as rendering.
		ClientPlayNetworking.registerGlobalReceiver(HudPayloads.Start.ID, (p, ctx) -> {
			HUD.start(p.stack(), p.word(), p.languageName(), p.durationTicks());
			VOICE.onChallengeStart(p.languageCode(), p.word());
		});
		ClientPlayNetworking.registerGlobalReceiver(HudPayloads.Attempt.ID,
				(p, ctx) -> HUD.attempt(p.attempt(), p.mask()));
		ClientPlayNetworking.registerGlobalReceiver(HudPayloads.End.ID, (p, ctx) -> {
			HUD.end(p.success());
			VOICE.onChallengeEnd();
		});

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			// Paused singleplayer also pauses the server timer, so pause the bar too.
			if (!client.isPaused()) HUD.tick();
			VOICE.tickPushToTalk(talkKey.isPressed());
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			HUD.reset();
			VOICE.onChallengeEnd();
		});

		ChallengeHudRenderer renderer = new ChallengeHudRenderer(HUD, VOICE);
		HudElementRegistry.addLast(Identifier.of("languagemod", "challenge_popup"), renderer::render);
	}
}
