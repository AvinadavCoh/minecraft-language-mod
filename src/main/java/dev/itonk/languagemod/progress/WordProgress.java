package dev.itonk.languagemod.progress;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * One player's dictionary: which words are new, learning, known or need repeating, and which
 * one to ask next. Spaced repetition on play time: each right answer moves a word one box up
 * and pushes its next review further out; a miss drops it back to box 1.
 *
 * Pure logic keyed by item-id strings, no Minecraft classes, so it is unit-tested directly.
 */
public final class WordProgress {
	public enum Status { NEEDS_REPEAT, NEW, LEARNING, KNOWN }

	public static final int MAX_BOX = 5;

	/** Saved: item id -> record, plus the player's total play ticks. */
	public long playTicks;
	public Map<String, WordRecord> words = new LinkedHashMap<>();

	public WordRecord get(String id) {
		return words.get(id);
	}

	public boolean has(String id) {
		return words.containsKey(id);
	}

	/** First time this item shows up in the inventory (a challenge is about to ask it). */
	public WordRecord seen(String id) {
		return words.computeIfAbsent(id, k -> {
			WordRecord r = new WordRecord();
			r.dueAt = playTicks;
			return r;
		});
	}

	/** Item held on first join: known to exist but not asked yet; comes back as a low-priority review. */
	public void seenLater(String id, long delayTicks) {
		WordRecord r = seen(id);
		r.dueAt = Math.max(r.dueAt, playTicks + delayTicks);
	}

	/** Right answer. With wrong tries first it doesn't move up a box (it was a struggle). */
	public void onCorrect(String id, int wrongTriesThisTime, long[] stepTicks) {
		WordRecord r = seen(id);
		r.timesAsked++;
		r.correct++;
		r.wrongTries += wrongTriesThisTime;
		r.lastAskedAt = playTicks;
		r.lastWasMiss = false;
		if (wrongTriesThisTime == 0 || r.box == 0) r.box = Math.min(MAX_BOX, r.box + 1);
		r.dueAt = playTicks + step(stepTicks, r.box);
	}

	/** Miss (time ran out, or a wrong answer when wrong answers punish). */
	public void onMiss(String id, int wrongTriesThisTime, long retryTicks) {
		WordRecord r = seen(id);
		r.timesAsked++;
		r.misses++;
		r.wrongTries += wrongTriesThisTime;
		r.lastAskedAt = playTicks;
		r.lastWasMiss = true;
		r.box = 1;
		r.dueAt = playTicks + retryTicks;
	}

	public boolean isDue(String id) {
		WordRecord r = words.get(id);
		return r != null && playTicks >= r.dueAt;
	}

	public Status status(String id) {
		WordRecord r = words.get(id);
		if (r == null || r.box == 0) return r != null && r.lastWasMiss ? Status.NEEDS_REPEAT : Status.NEW;
		if (r.lastWasMiss || playTicks >= r.dueAt) return Status.NEEDS_REPEAT;
		return r.box >= 3 ? Status.KNOWN : Status.LEARNING;
	}

	/**
	 * Which due word to ask next, from the item ids currently in the inventory.
	 * Order: missed words (most misses first), then the weakest box, then the most overdue.
	 */
	public Optional<String> nextReview(Collection<String> inInventory) {
		return inInventory.stream()
				.filter(id -> words.containsKey(id) && isDue(id))
				.min(priority());
	}

	/** Same priority, used to sort the "needs repeating" list in the dictionary screen. */
	public Comparator<String> priority() {
		return Comparator.<String, Boolean>comparing(id -> !words.get(id).lastWasMiss)
				.thenComparing(id -> -words.get(id).misses)
				.thenComparingInt(id -> words.get(id).box)
				.thenComparingLong(id -> words.get(id).dueAt);
	}

	public List<String> ids() {
		return List.copyOf(words.keySet());
	}

	private static long step(long[] steps, int box) {
		if (steps.length == 0) return 0;
		return steps[Math.min(box, steps.length) - 1];
	}
}
