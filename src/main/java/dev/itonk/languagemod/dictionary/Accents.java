package dev.itonk.languagemod.dictionary;

import java.text.Normalizer;

/**
 * Drops accents (ă -> a, ё -> е, Hebrew vowel points) but keeps marks that are letters' own
 * parts in other scripts: Hindi vowel signs, Japanese dakuten (が is not か), Korean syllables.
 */
public final class Accents {
	private Accents() {}

	public static String strip(String s) {
		String d = Normalizer.normalize(s, Normalizer.Form.NFD);
		StringBuilder b = new StringBuilder(d.length());
		Character.UnicodeScript base = Character.UnicodeScript.COMMON;
		for (int i = 0; i < d.length(); ) {
			int cp = d.codePointAt(i);
			i += Character.charCount(cp);
			int type = Character.getType(cp);
			boolean mark = type == Character.NON_SPACING_MARK || type == Character.COMBINING_SPACING_MARK || type == Character.ENCLOSING_MARK;
			if (!mark) {
				base = Character.UnicodeScript.of(cp);
			} else if (accentScript(base)) {
				continue;
			}
			b.appendCodePoint(cp);
		}
		return Normalizer.normalize(b, Normalizer.Form.NFC);
	}

	/** True for scripts where every combining mark is an accent you may leave out. */
	static boolean accentScript(Character.UnicodeScript s) {
		return s == Character.UnicodeScript.LATIN || s == Character.UnicodeScript.CYRILLIC
				|| s == Character.UnicodeScript.GREEK || s == Character.UnicodeScript.HEBREW
				|| s == Character.UnicodeScript.COMMON;
	}

	/** Scripts written without spaces between words, so an answer can sit anywhere inside a phrase. */
	public static boolean noSpaces(String s) {
		for (int i = 0; i < s.length(); ) {
			int cp = s.codePointAt(i);
			i += Character.charCount(cp);
			Character.UnicodeScript sc = Character.UnicodeScript.of(cp);
			if (sc == Character.UnicodeScript.HAN || sc == Character.UnicodeScript.HIRAGANA || sc == Character.UnicodeScript.KATAKANA) return true;
		}
		return false;
	}
}
