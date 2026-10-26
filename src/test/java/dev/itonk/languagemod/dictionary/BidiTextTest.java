package dev.itonk.languagemod.dictionary;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class BidiTextTest {
	@Test
	void leftToRightIsUnchanged() {
		assertEquals("Scânduri de stejar", BidiText.visual("Scânduri de stejar"));
		assertEquals("Деревянная лопата", BidiText.visual("Деревянная лопата"));
	}

	@Test
	void hebrewIsReversedForDrawing() {
		assertEquals("חופת", BidiText.visual("תפוח"));
		// Two words: the whole line runs right to left, so the first word ends up on the right.
		assertEquals("ןולא ץע", BidiText.visual("עץ אלון"));
		assertArrayEquals(new int[] {3, 2, 1, 0}, BidiText.visualOrder("תפוח"));
	}

	@Test
	void mixedLineKeepsLatinPartInPlace() {
		assertEquals("you said: חופת", BidiText.visual("you said: תפוח"));
	}
}
