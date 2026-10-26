package dev.itonk.languagemod.challenge;

import net.minecraft.util.Identifier;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * Per-player challenge bookkeeping. Only ever touched on the server thread,
 * so plain (non-concurrent) collections are correct here.
 */
public final class PlayerChallengeState {
	/** One challenge at a time; the rest wait here in pickup order. */
	/** {@code word} is the answer shown in the popup (first dictionary entry, accents kept). */
	public record Challenge(Identifier itemId, int slot, long deadlineTick, String word) {}

	/** Item types this player has already named correctly (or owned on join). */
	final Set<Identifier> verified = new HashSet<>();
	final Deque<Identifier> queue = new ArrayDeque<>();
	Challenge active;

	public Challenge active() {
		return active;
	}

	public boolean isFrozen() {
		return active != null;
	}

	boolean isKnown(Identifier itemId) {
		return verified.contains(itemId) || queue.contains(itemId)
				|| (active != null && active.itemId().equals(itemId));
	}
}
