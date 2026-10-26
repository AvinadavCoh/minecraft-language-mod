package dev.itonk.languagemod.mixin;

import dev.itonk.languagemod.LanguageMod;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Locks the inventory while a challenge is open: no dropping (Q / Ctrl+Q),
 * no offhand swap (F), no clicking slots in any screen.
 *
 * Why the isOnThread() guard: vanilla packet handlers run TWICE. The first call is on
 * the Netty network thread; NetworkThreadUtils.forceMainThread(...) then throws to abort
 * it and re-queues the packet on the server thread. Reading our (server-thread-only)
 * state or cancelling on the network thread would race, so we let the first pass through
 * untouched and only act on the second, server-thread pass.
 */
@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerPlayNetworkHandlerMixin {
	@Shadow
	public ServerPlayerEntity player;

	@Inject(method = "onPlayerAction", at = @At("HEAD"), cancellable = true)
	private void languagemod$blockDrop(PlayerActionC2SPacket packet, CallbackInfo ci) {
		if (!LanguageMod.onServerThread() || !LanguageMod.challenges().isFrozen(player)) return;
		switch (packet.getAction()) {
			case DROP_ITEM, DROP_ALL_ITEMS, SWAP_ITEM_WITH_OFFHAND -> {
				ci.cancel();
				// The client already removed the item locally; resend the real inventory.
				player.currentScreenHandler.syncState();
			}
			default -> {}
		}
	}

	@Inject(method = "onClickSlot", at = @At("HEAD"), cancellable = true)
	private void languagemod$blockClick(ClickSlotC2SPacket packet, CallbackInfo ci) {
		if (!LanguageMod.onServerThread() || !LanguageMod.challenges().isFrozen(player)) return;
		ci.cancel();
		player.currentScreenHandler.syncState();
	}
}
