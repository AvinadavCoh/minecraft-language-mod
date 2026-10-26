package dev.itonk.languagemod.dictionary;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Marks which letters of the target word the player got right.
 *
 * Aligns the attempt to the target with an edit-distance alignment (so one missing or
 * extra letter doesn't turn the rest of the word red), comparing letters without case
 * or accents. Spaces in the target are always "right" so they never show as red gaps.
 * Pure logic: no Minecraft classes, unit-tested on its own.
 */
public final class LetterDiff {
	private LetterDiff() {}

	/** @return one flag per char of {@code target}: true = matched (green), false = wrong (red). */
	public static boolean[] matchMask(String target, String attempt) {
		char[] t = fold(target);
		char[] a = fold(attempt.replaceAll("[\\p{P}\\p{S}]", " ").trim().replaceAll("\\s+", " "));
		int n = t.length, m = a.length;

		int[][] cost = new int[n + 1][m + 1];
		for (int i = 0; i <= n; i++) cost[i][0] = i;
		for (int j = 0; j <= m; j++) cost[0][j] = j;
		for (int i = 1; i <= n; i++) {
			for (int j = 1; j <= m; j++) {
				int sub = cost[i - 1][j - 1] + (t[i - 1] == a[j - 1] ? 0 : 1);
				cost[i][j] = Math.min(sub, Math.min(cost[i - 1][j] + 1, cost[i][j - 1] + 1));
			}
		}

		boolean[] mask = new boolean[n];
		int i = n, j = m;
		while (i > 0 && j > 0) {
			// Prefer skipping target letters on ties: walking back from the end, this keeps
			// a short attempt aligned to the START of the word ("scanduri de" -> "stejar" red).
			if (cost[i][j] == cost[i - 1][j] + 1) {
				i--;
			} else if (t[i - 1] == a[j - 1] && cost[i][j] == cost[i - 1][j - 1]) {
				mask[i - 1] = true;
				i--; j--;
			} else if (cost[i][j] == cost[i - 1][j - 1] + 1) {
				i--; j--;
			} else {
				j--;
			}
		}
		for (int k = 0; k < n; k++) if (t[k] == ' ') mask[k] = true;
		return mask;
	}

	/** Lowercase and strip accents per char, keeping a 1:1 index mapping with the input. */
	private static char[] fold(String s) {
		char[] out = new char[s.length()];
		for (int k = 0; k < s.length(); k++) {
			String base = Normalizer.normalize(String.valueOf(s.charAt(k)), Normalizer.Form.NFD)
					.replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
			out[k] = base.isEmpty() ? s.charAt(k) : base.charAt(0);
		}
		return out;
	}
}
