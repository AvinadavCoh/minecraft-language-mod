package dev.itonk.languagemod.voice;

import java.util.HashSet;
import java.util.Set;

/**
 * Cleans what Whisper heard before it is used as an answer. On short or unclear audio Whisper
 * sometimes loops ("-אה, -אה, -אה, ..." 263 characters for 1 s of audio, seen on a real PC);
 * that must never reach the network (the answer packet holds at most 200 characters).
 */
public final class VoiceText {
	/** Longer than any item name; the packet limit is 200. */
	public static final int MAX_CHARS = 100;

	private VoiceText() {}

	/** The cleaned answer, or "" when it is junk (a loop) and should be ignored. */
	public static String clean(String heard) {
		if (heard == null) return "";
		// Direction marks Whisper adds around Hebrew text (U+200E/F, U+202A-202E, U+2066-2069).
		String s = heard.replaceAll("[\\u200E\\u200F\\u202A-\\u202E\\u2066-\\u2069]", "").trim();
		String[] words = s.split("[\\s,.;!?]+");
		if (words.length >= 6) {
			Set<String> distinct = new HashSet<>();
			for (String w : words) distinct.add(w.toLowerCase());
			if (distinct.size() * 3 <= words.length) return ""; // the same few words over and over
		}
		return s.length() > MAX_CHARS ? s.substring(0, MAX_CHARS).trim() : s;
	}
}
