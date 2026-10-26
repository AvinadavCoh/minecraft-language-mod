package dev.itonk.languagemod.challenge;

import net.minecraft.util.Identifier;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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
	/** Item types missed but kept (deleteItemOnFail=false): server tick until they may be asked again. */
	final Map<Identifier, Long> snoozedUntil = new HashMap<>();
	Challenge active;

	public Challenge active() {
		return active;
	}

	public boolean isFrozen() {
		return active != null;
	}

	boolean isKnown(Identifier itemId, long now) {
		Long snooze = snoozedUntil.get(itemId);
		if (snooze != null) {
			if (now < snooze) return true;
			snoozedUntil.remove(itemId);
		}
		return verified.contains(itemId) || queue.contains(itemId)
				|| (active != null && active.itemId().equals(itemId));
	}
}
