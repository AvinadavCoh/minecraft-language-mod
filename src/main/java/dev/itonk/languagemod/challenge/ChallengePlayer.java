package dev.itonk.languagemod.challenge;

import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;

import java.util.UUID;

/**
 * What the rules (ChallengeManager) need from a player, so the same rules run on a server
 * with the mod (ServerChallengePlayer: real damage, item deleted) and in client-only mode on
 * a server without it (the client adapter: hurt shake, item thrown away).
 */
public interface ChallengePlayer {
	UUID uuid();

	PlayerInventory inventory();

	boolean isAlive();

	boolean isCreativeOrSpectator();

	void prompt(ItemStack stack, String word, String languageName, String languageCode, int durationTicks);

	/** A wrong try while the timer still runs; {@code mask[i]} = letter i was right. */
	void attempt(String attempt, String word, boolean[] mask);

	void success(ItemStack stack);

	/**
	 * A miss: remove the stack in {@code slot} unless {@code keep} (slot may be -1 if it is gone),
	 * then the hurt and the message.
	 */
	void punish(int slot, boolean keep, String reason, String answer, float damage);

	void welcome(String languageName, int carried, int gapSeconds, boolean off);

	default void freeze() {}

	default void unfreeze() {}
}
