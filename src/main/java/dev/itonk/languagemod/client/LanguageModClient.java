package dev.itonk.languagemod.client;

import dev.itonk.languagemod.network.HudPayloads;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.util.Identifier;

/** Client wiring only: packets -> state, tick -> state, HUD -> renderer. */
public final class LanguageModClient implements ClientModInitializer {
	public static final ChallengeHudState HUD = new ChallengeHudState();

	@Override
	public void onInitializeClient() {
		// Fabric runs these receivers on the client thread, same thread as rendering.
		ClientPlayNetworking.registerGlobalReceiver(HudPayloads.Start.ID,
				(p, ctx) -> HUD.start(p.stack(), p.word(), p.languageName(), p.durationTicks()));
		ClientPlayNetworking.registerGlobalReceiver(HudPayloads.Attempt.ID,
				(p, ctx) -> HUD.attempt(p.attempt(), p.mask()));
		ClientPlayNetworking.registerGlobalReceiver(HudPayloads.End.ID,
				(p, ctx) -> HUD.end(p.success()));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			// Paused singleplayer also pauses the server timer, so pause the bar too.
			if (!client.isPaused()) HUD.tick();
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> HUD.reset());

		ChallengeHudRenderer renderer = new ChallengeHudRenderer(HUD);
		HudElementRegistry.addLast(Identifier.of("languagemod", "challenge_popup"), renderer::render);
	}
}
