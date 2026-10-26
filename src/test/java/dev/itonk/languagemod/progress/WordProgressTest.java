package dev.itonk.languagemod.progress;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WordProgressTest {
	private static final long MIN = 20 * 60;
	private static final long[] STEPS = {5 * MIN, 20 * MIN, 60 * MIN, 180 * MIN, 600 * MIN};

	@Test
	void rightAnswersClimbBoxesAndSpaceOut() {
		WordProgress p = new WordProgress();
		p.seen("apple");
		assertEquals(WordProgress.Status.NEW, p.status("apple"));
		p.onCorrect("apple", 0, STEPS);
		assertEquals(1, p.get("apple").box);
		assertEquals(WordProgress.Status.LEARNING, p.status("apple"));
		assertFalse(p.isDue("apple"));
		p.playTicks += 5 * MIN;
		assertTrue(p.isDue("apple"), "due after the 5 min step");
		assertEquals(WordProgress.Status.NEEDS_REPEAT, p.status("apple"));
		p.onCorrect("apple", 0, STEPS);
		p.onCorrect("apple", 0, STEPS);
		assertEquals(3, p.get("apple").box);
		assertEquals(WordProgress.Status.KNOWN, p.status("apple"));
		assertEquals(p.playTicks + 60 * MIN, p.get("apple").dueAt, "box 3 waits 1 h of play");
	}

	@Test
	void struggleDoesNotPromote() {
		WordProgress p = new WordProgress();
		p.onCorrect("bread", 0, STEPS);
		p.onCorrect("bread", 2, STEPS);
		assertEquals(1, p.get("bread").box, "right after 2 wrong tries stays in box 1");
		assertEquals(2, p.get("bread").wrongTries);
	}

	@Test
	void missDropsToBoxOneAndNeedsRepeat() {
		WordProgress p = new WordProgress();
		p.onCorrect("log", 0, STEPS);
		p.onCorrect("log", 0, STEPS);
		p.onCorrect("log", 0, STEPS);
		p.onMiss("log", 1, 30 * 20);
		assertEquals(1, p.get("log").box);
		assertEquals(WordProgress.Status.NEEDS_REPEAT, p.status("log"));
		assertFalse(p.isDue("log"));
		p.playTicks += 30 * 20;
		assertTrue(p.isDue("log"));
	}

	@Test
	void priorityMissedThenWeakestThenOverdue() {
		WordProgress p = new WordProgress();
		p.onCorrect("known", 0, STEPS);
		p.onCorrect("known", 0, STEPS);
		p.onCorrect("known", 0, STEPS); // box 3
		p.onCorrect("weak", 0, STEPS);  // box 1
		p.onMiss("missed", 0, 0);
		p.playTicks += 1000 * MIN;    // everything due
		List<String> inv = List.of("known", "weak", "missed", "notInDictionary");
		assertEquals("missed", p.nextReview(inv).orElseThrow());
		p.onCorrect("missed", 0, STEPS);
		assertEquals("weak", p.nextReview(inv).orElseThrow());
		p.onCorrect("weak", 0, STEPS);
		assertEquals("known", p.nextReview(inv).orElseThrow());
	}

	@Test
	void onlyItemsInInventoryAreReviewed() {
		WordProgress p = new WordProgress();
		p.onMiss("carrot", 0, 0);
		assertTrue(p.nextReview(List.of("stone")).isEmpty());
	}
}
