package dev.itonk.languagemod.progress;

/**
 * What one player knows about one word (item id). Saved as JSON, so fields stay plain.
 * Times are in the player's own play ticks (20 per second), not wall-clock time.
 */
public final class WordRecord {
	/** Leitner box: 0 = seen but never answered, 1..5 = learning -> known. */
	public int box;
	public int correct;
	public int misses;
	/** Wrong attempts before a right answer or a miss (retries inside a challenge). */
	public int wrongTries;
	public int timesAsked;
	/** True after a miss, until the next right answer. */
	public boolean lastWasMiss;
	public long dueAt;
	public long lastAskedAt;
}
