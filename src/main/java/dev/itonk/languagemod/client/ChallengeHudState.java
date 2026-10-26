package dev.itonk.languagemod.client;

import net.minecraft.item.ItemStack;

/**
 * What the popup currently shows. Written by packet handlers and the client tick,
 * read by the renderer — all on the client render thread, so no locking.
 */
public final class ChallengeHudState {
	/** How long the result (green/red flash) stays up after a challenge ends. */
	static final int RESULT_TICKS = 30;

	enum Phase { HIDDEN, ASKING, CORRECT, FAILED }

	Phase phase = Phase.HIDDEN;
	ItemStack stack = ItemStack.EMPTY;
	String word = "";
	String languageName = "";
	/** 'G'/'R' per letter after a wrong try; null before the first try. */
	String mask;
	String lastAttempt = "";
	int durationTicks;
	/** Client ticks since the popup opened (not counted while the game is paused). */
	int elapsedTicks;
	int resultTicks;
	/** Red screen flash after a miss in client-only mode (no real damage there). */
	int hurtTicks;
	static final int HURT_TICKS = 10;

	void start(ItemStack stack, String word, String languageName, int durationTicks) {
		this.phase = Phase.ASKING;
		this.stack = stack;
		this.word = word;
		this.languageName = languageName;
		this.durationTicks = Math.max(1, durationTicks);
		this.mask = null;
		this.lastAttempt = "";
		this.elapsedTicks = 0;
	}

	void attempt(String attempt, String mask) {
		if (phase != Phase.ASKING || mask.length() != word.length()) return;
		this.lastAttempt = attempt;
		this.mask = mask;
	}

	/** Speech-to-text result, shown right away while the server judges it. */
	void heard(String text) {
		if (phase == Phase.ASKING) lastAttempt = text;
	}

	/** After a miss: false = stack destroyed ("Item lost!"), true = only damage ("Ouch!"). */
	boolean itemKept;

	void end(boolean success, boolean itemKept) {
		if (phase != Phase.ASKING) return;
		this.itemKept = itemKept;
		phase = success ? Phase.CORRECT : Phase.FAILED;
		mask = (success ? "G" : "R").repeat(word.length());
		if (success) lastAttempt = ""; // the wrong try before it is no longer relevant
		resultTicks = 0;
	}

	void hurt() {
		hurtTicks = HURT_TICKS;
	}

	void tick() {
		if (hurtTicks > 0) hurtTicks--;
		switch (phase) {
			case ASKING -> elapsedTicks++;
			case CORRECT, FAILED -> {
				if (++resultTicks >= RESULT_TICKS) phase = Phase.HIDDEN;
			}
			default -> {}
		}
	}

	void reset() {
		phase = Phase.HIDDEN;
	}

	// Read-only views for the client gametest.
	public String debugPhase() { return phase.name(); }
	public String debugWord() { return word; }
	public String debugMask() { return mask == null ? "" : mask; }
	public String debugLastAttempt() { return lastAttempt; }
	public boolean debugItemKept() { return itemKept; }
	public int debugHurtTicks() { return hurtTicks; }

	/** 1 = full bar, 0 = time's up. */
	float timeLeft(float tickProgress) {
		float used = (elapsedTicks + tickProgress) / durationTicks;
		return Math.max(0f, Math.min(1f, 1f - used));
	}
}
