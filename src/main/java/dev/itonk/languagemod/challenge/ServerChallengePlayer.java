package dev.itonk.languagemod.challenge;

import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.UUID;

/** A player on a server that runs the mod: real damage, the stack is deleted. Server thread only. */
public record ServerChallengePlayer(ServerPlayerEntity player) implements ChallengePlayer {
	@Override public UUID uuid() { return player.getUuid(); }

	@Override public PlayerInventory inventory() { return player.getInventory(); }

	@Override public boolean isAlive() { return player.isAlive(); }

	@Override public boolean isCreativeOrSpectator() { return player.isCreative() || player.isSpectator(); }

	@Override
	public void prompt(ItemStack stack, String word, String languageName, String languageCode, int durationTicks) {
		ChallengeEffects.prompt(player, stack, word, languageName, languageCode, durationTicks);
	}

	@Override
	public void attempt(String attempt, String word, boolean[] mask) {
		ChallengeEffects.attempt(player, attempt, word, mask);
	}

	@Override
	public void success(ItemStack stack) {
		ChallengeEffects.success(player, stack);
	}

	@Override
	public void punish(int slot, boolean keep, String reason, String answer, float damage) {
		ItemStack removed = !keep && slot >= 0 ? InventoryOps.deleteStack(player, slot) : ItemStack.EMPTY;
		ChallengeEffects.failure(player, removed, keep, reason, answer);
		player.damage(player.getEntityWorld(), player.getDamageSources().generic(), damage);
	}

	@Override
	public void welcome(String languageName, int carried, int gapSeconds, boolean off) {
		ChallengeEffects.welcome(player, languageName, carried, gapSeconds, off);
	}

	@Override public void freeze() { FreezeControl.freeze(player); }

	@Override public void unfreeze() { FreezeControl.unfreeze(player); }
}
