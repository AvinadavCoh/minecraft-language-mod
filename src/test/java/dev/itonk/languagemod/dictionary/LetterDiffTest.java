package dev.itonk.languagemod.dictionary;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LetterDiffTest {
	/** G = green, R = red, one per target char. */
	private static String pattern(String target, String attempt) {
		StringBuilder sb = new StringBuilder();
		for (boolean b : LetterDiff.matchMask(target, attempt)) sb.append(b ? 'G' : 'R');
		return sb.toString();
	}

	@Test
	void exactAndAccentInsensitive() {
		assertEquals("GGG", pattern("măr", "mar"));
		assertEquals("GGGGG", pattern("pâine", "PAINE"));
	}

	@Test
	void wrongLettersAreRed() {
		assertEquals("GRGGG", pattern("pâine", "poine"));
		assertEquals("RRR", pattern("măr", "xyz"));
	}

	@Test
	void missingLetterOnlyMarksThatLetter() {
		// "scânduri" typed without the 'd': only the d is red, the rest stays green
		assertEquals("GGGGRGGG", pattern("scânduri", "scanuri"));
	}

	@Test
	void spacesNeverRed() {
		assertEquals("GGGGGGGGGGGGGGGGGG", pattern("scânduri de stejar", "scanduri de stejar"));
		assertEquals("GGGGGGGGGGGGRRRRRR", pattern("scânduri de stejar", "scanduri de"));
	}

	@Test
	void emptyAttemptAllRed() {
		assertEquals("RRR", pattern("măr", ""));
	}
}
