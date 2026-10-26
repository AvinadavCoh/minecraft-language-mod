package dev.itonk.languagemod.dictionary;

import java.text.Bidi;

/**
 * Right-to-left words (Hebrew) in display order. Minecraft only reorders text when the game's
 * own UI language is right-to-left, so with an English UI a Hebrew word would be drawn backwards.
 * Strings stay in logical (typed/spoken) order everywhere else; only drawing uses these.
 */
public final class BidiText {
	private BidiText() {}

	/** Logical character indices in left-to-right screen order (identity for left-to-right text). */
	public static int[] visualOrder(String s) {
		int n = s.length();
		int[] order = new int[n];
		Bidi bidi = new Bidi(s, Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT);
		if (bidi.isLeftToRight()) {
			for (int i = 0; i < n; i++) order[i] = i;
			return order;
		}
		int runs = bidi.getRunCount();
		byte[] levels = new byte[runs];
		Integer[] runOrder = new Integer[runs];
		for (int r = 0; r < runs; r++) {
			levels[r] = (byte) bidi.getRunLevel(r);
			runOrder[r] = r;
		}
		Bidi.reorderVisually(levels, 0, runOrder, 0, runs);
		int k = 0;
		for (int r : runOrder) {
			int start = bidi.getRunStart(r), limit = bidi.getRunLimit(r);
			if ((bidi.getRunLevel(r) & 1) == 1) {
				for (int i = limit - 1; i >= start; i--) order[k++] = i;
			} else {
				for (int i = start; i < limit; i++) order[k++] = i;
			}
		}
		return order;
	}

	/** The string as it should appear on screen, left to right. */
	public static String visual(String s) {
		int[] order = visualOrder(s);
		StringBuilder b = new StringBuilder(s.length());
		for (int i : order) b.append(s.charAt(i));
		return b.toString();
	}
}
