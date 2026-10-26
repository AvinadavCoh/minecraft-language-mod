package dev.itonk.languagemod.challenge;

import dev.itonk.languagemod.progress.WordProgress;
import net.minecraft.util.Identifier;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Per-player challenge bookkeeping. Only ever touched on the server thread,
 * so plain (non-concurrent) collections are correct here.
 */
public final class PlayerChallengeState {
	/**
	 * One challenge at a time. {@code word} is the answer shown in the popup (first dictionary
	 * entry, accents kept); {@code review} is false for an item picked up for the first time.
	 */
	public record Challenge(Identifier itemId, int slot, long deadlineTick, String word, boolean review) {}

	/** This player's saved dictionary (what they know, what is due). */
	final WordProgress progress;
	/** Items picked up for the first time, asked before any review, in pickup order. */
	final Deque<Identifier> newPickups = new ArrayDeque<>();
	Challenge active;
	/** Wrong tries in the current challenge. */
	int wrongTries;
	/** The client says the player is talking or Whisper is still working on an answer. */
	boolean voiceBusy;
	/** Play tick when the last challenge ended (reviews wait reviewGapSeconds after it). */
	long lastEndPlayTick = Long.MIN_VALUE / 2;
	boolean dirty;

	PlayerChallengeState(WordProgress progress) {
		this.progress = progress;
	}

	public Challenge active() {
		return active;
	}

	public boolean isFrozen() {
		return active != null;
	}
}
