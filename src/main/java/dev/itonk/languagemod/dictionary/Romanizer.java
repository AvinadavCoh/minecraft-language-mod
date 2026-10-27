package dev.itonk.languagemod.dictionary;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * How to read a word in Latin letters, shown small under words in other scripts ("עפר" -> "Afar").
 * Russian and Chinese use ICU (Minecraft ships it); Korean and Hindi use the rules below;
 * Hebrew (no vowels in writing) and Japanese kanji (readings depend on the word) use word tables
 * in resources/languagemod/readings/. Returns "" for Latin-script languages or when a word is unknown:
 * no reading beats a wrong one.
 */
public final class Romanizer {
	private static final Map<String, String> CACHE = new ConcurrentHashMap<>();
	private static final Map<String, Map<String, String>> TABLES = new ConcurrentHashMap<>();
	private static final Pattern HEBREW_WORD = Pattern.compile("[\\u0590-\\u05FF'\"׳״]+");

	private Romanizer() {}

	public static String reading(String language, String word) {
		if (language == null || word == null || word.isBlank()) return "";
		return CACHE.computeIfAbsent(language + "\u0000" + word, k -> {
			try {
				// Zero-width joiners (Hindi spelling hints) would show as stray marks.
				return capitalize(compute(language, word).replaceAll("[\\u200B-\\u200D\\u2060]", "").trim().replaceAll("\\s+", " "));
			} catch (Throwable t) { // ICU missing (e.g. a dedicated server without it): just no reading
				return "";
			}
		});
	}

	private static String compute(String language, String word) {
		return switch (language) {
			case "ru" -> icu("Russian-Latin/BGN", word).replace('ʹ', '\'').replace("ʺ", ""); // soft sign -> ', hard sign silent
			case "zh" -> icu("Han-Latin", word);
			case "ko" -> korean(word);
			case "hi" -> hindi(word);
			case "he" -> hebrew(word);
			case "ja" -> japanese(word);
			default -> "";
		};
	}

	private static String icu(String id, String s) {
		return com.ibm.icu.text.Transliterator.getInstance(id).transliterate(s);
	}

	private static String capitalize(String s) {
		if (s.isEmpty()) return s;
		int first = s.codePointAt(0);
		return new String(Character.toChars(Character.toUpperCase(first))) + s.substring(Character.charCount(first));
	}

	static Map<String, String> table(String language) {
		return TABLES.computeIfAbsent(language, lang -> {
			Map<String, String> out = new HashMap<>();
			try (InputStream in = Romanizer.class.getResourceAsStream("/languagemod/readings/" + lang + ".json")) {
				if (in == null) return out;
				try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
					JsonObject o = new Gson().fromJson(r, JsonObject.class);
					for (var e : o.getAsJsonObject("words").entrySet()) out.put(e.getKey(), e.getValue().getAsString());
				}
			} catch (Exception e) {
				// A broken table only costs the readings.
			}
			return out;
		});
	}

	// ---- Hebrew: whole words from the table (written Hebrew has no vowels) ----

	private static String hebrew(String word) {
		Map<String, String> t = table("he");
		Matcher m = HEBREW_WORD.matcher(word);
		StringBuilder out = new StringBuilder();
		int last = 0;
		while (m.find()) {
			String w = m.group().replaceAll("^['\"׳״]+|['\"׳״]+$", "");
			String r = t.get(w);
			if (r == null) return "";
			out.append(word, last, m.start()).append(r);
			last = m.end();
		}
		out.append(word.substring(last));
		return out.toString();
	}

	// ---- Japanese: kanji words from the table -> kana -> Hepburn ----

	private static final String PARTICLES = "のとをが";

	private static String japanese(String word) {
		Map<String, String> t = table("ja");
		int longest = 0;
		for (String k : t.keySet()) longest = Math.max(longest, k.length());
		StringBuilder out = new StringBuilder();
		StringBuilder kana = new StringBuilder();
		int i = 0;
		while (i < word.length()) {
			String hit = null;
			for (int len = Math.min(longest, word.length() - i); len > 0 && hit == null; len--) {
				hit = t.get(word.substring(i, i + len));
				if (hit != null) {
					kana.append(hit);
					i += len;
				}
			}
			if (hit != null) continue;
			char c = word.charAt(i);
			if (Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN) return ""; // a kanji we can't read
			if (PARTICLES.indexOf(c) >= 0) {
				out.append(icu("Any-Latin", kana.toString())).append(' ').append(particle(c)).append(' ');
				kana.setLength(0);
			} else {
				kana.append(c);
			}
			i++;
		}
		out.append(icu("Any-Latin", kana.toString()));
		// Hepburn long vowels: ICU spells hiragana おう/うう out ("sagyoudai"); learners read "sagyōdai".
		// ICU writes small-kana sounds with "~" (ウォ -> "u~o", ヴェ -> "vu~e"): spell them as heard.
		return out.toString().replace("ou", "ō").replace("uu", "ū").replace("vu~", "v").replace("u~", "w").replace("~", "");
	}

	private static String particle(char c) {
		return switch (c) {
			case 'の' -> "no";
			case 'と' -> "to";
			case 'を' -> "o";
			default -> "ga";
		};
	}

	// ---- Korean: Revised Romanization with the common sound changes ----

	private static final String[] KO_INITIAL = {"g", "kk", "n", "d", "tt", "r", "m", "b", "pp", "s", "ss", "", "j", "jj", "ch", "k", "t", "p", "h"};
	private static final String[] KO_VOWEL = {"a", "ae", "ya", "yae", "eo", "e", "yeo", "ye", "o", "wa", "wae", "oe", "yo", "u", "wo", "we", "wi", "yu", "eu", "ui", "i"};
	/** Final as heard before a consonant or a pause. */
	private static final String[] KO_FINAL = {"", "k", "k", "k", "n", "n", "n", "t", "l", "k", "m", "l", "l", "l", "p", "l", "m", "p", "p", "t", "t", "ng", "t", "t", "k", "t", "p", "t"};
	/** Before a vowel (ㅇ initial): what stays as the final, and which initial moves over. */
	private static final String[] KO_KEEP = {"", "", "", "k", "", "n", "n", "", "", "l", "l", "l", "l", "l", "l", "", "", "", "p", "", "", "ng", "", "", "", "", "", ""};
	private static final String[] KO_MOVE = {"", "g", "kk", "s", "n", "j", "", "d", "r", "g", "m", "b", "s", "t", "p", "r", "m", "b", "s", "s", "ss", "", "j", "ch", "k", "t", "p", ""};
	private static final int KO_SILENT = 11, KO_N = 2, KO_R = 5, KO_M = 6;

	private static String korean(String word) {
		StringBuilder out = new StringBuilder();
		int n = word.length();
		for (int i = 0; i < n; i++) {
			char c = word.charAt(i);
			if (c < 0xAC00 || c > 0xD7A3) {
				out.append(c);
				continue;
			}
			int s = c - 0xAC00, l = s / (21 * 28), v = (s / 28) % 21, t = s % 28;
			if (i == 0 || !isHangul(word.charAt(i - 1))) out.append(KO_INITIAL[l]);
			out.append(KO_VOWEL[v]);
			if (i + 1 < n && isHangul(word.charAt(i + 1))) {
				int next = (word.charAt(i + 1) - 0xAC00) / (21 * 28);
				out.append(join(t, next));
			} else {
				out.append(KO_FINAL[t]);
			}
		}
		return out.toString();
	}

	/** This syllable's final + the next syllable's initial, with liaison, nasalisation and l/n rules. */
	private static String join(int t, int next) {
		String fin = KO_FINAL[t];
		if (next == KO_SILENT) return KO_KEEP[t] + KO_MOVE[t];
		if (t == 27 && (next == 0 || next == 3 || next == 12)) return KO_INITIAL[next].equals("g") ? "k" : next == 3 ? "t" : "ch"; // ㅎ aspirates
		if (next == KO_R) {
			if (fin.equals("l") || fin.equals("n")) return "ll";
			if (fin.equals("k")) return "ngn";
			if (fin.equals("p")) return "mn";
			if (fin.equals("t")) return "nn";
			if (fin.equals("m") || fin.equals("ng")) return fin + "n";
		}
		if (next == KO_N || next == KO_M) {
			if (fin.equals("k")) fin = "ng";
			else if (fin.equals("t")) fin = "n";
			else if (fin.equals("p")) fin = "m";
			else if (fin.equals("l") && next == KO_N) return "ll";
		}
		return fin + KO_INITIAL[next];
	}

	private static boolean isHangul(char c) {
		return c >= 0xAC00 && c <= 0xD7A3;
	}

	// ---- Hindi: Devanagari, simple spelling, silent inherent 'a' dropped ----

	private static final Map<Character, String> HI_CONS = new HashMap<>();
	private static final Map<Character, String> HI_VOWEL = new HashMap<>();
	private static final Map<Character, String> HI_MATRA = new HashMap<>();

	static {
		String cons = "कk खkh गg घgh ङn चch छchh जj झjh ञn टt ठth डd ढdh णn तt थth दd धdh नn पp फph बb भbh मm यy रr लl वv शsh षsh सs हh";
		for (String e : cons.split(" ")) HI_CONS.put(e.charAt(0), e.substring(1));
		String vow = "अa आaa इi ईi उu ऊu ऋri एe ऐai ओo औau ऑo";
		for (String e : vow.split(" ")) HI_VOWEL.put(e.charAt(0), e.substring(1));
		String mat = "ाaa िi ीi ुu ूu ृri ेe ैai ोo ौau ॉo";
		for (String e : mat.split(" ")) HI_MATRA.put(e.charAt(0), e.substring(1));
	}

	private static final char VIRAMA = '्', NUKTA = '़', ANUSVARA = 'ं', CHANDRABINDU = 'ँ', VISARGA = 'ः';

	/** One written unit: consonant (or "") + vowel; inherent = the vowel is the unwritten 'a'. */
	private record Unit(String cons, String vowel, boolean inherent, String nasal) {}

	private static String hindi(String text) {
		StringBuilder out = new StringBuilder();
		StringBuilder word = new StringBuilder();
		for (int i = 0; i <= text.length(); i++) {
			char c = i < text.length() ? text.charAt(i) : ' ';
			if (Character.UnicodeScript.of(c) == Character.UnicodeScript.DEVANAGARI && c != '।') {
				word.append(c);
			} else {
				if (!word.isEmpty()) out.append(hindiWord(word.toString()));
				word.setLength(0);
				if (i < text.length()) out.append(c);
			}
		}
		return out.toString();
	}

	private static String hindiWord(String w) {
		java.util.List<Unit> units = new java.util.ArrayList<>();
		for (int i = 0; i < w.length(); i++) {
			char c = w.charAt(i);
			String vowel = HI_VOWEL.get(c);
			if (vowel != null) {
				units.add(new Unit("", vowel, false, nasal(w, i + 1)));
				continue;
			}
			String cons = HI_CONS.get(c);
			if (cons == null) continue;
			if (i + 1 < w.length() && w.charAt(i + 1) == NUKTA) {
				cons = switch (c) {
					case 'ड' -> "r";
					case 'ढ' -> "rh";
					case 'क' -> "q";
					case 'ख' -> "kh";
					case 'ग' -> "gh";
					case 'ज' -> "z";
					case 'फ' -> "f";
					default -> cons;
				};
				i++;
			}
			char next = i + 1 < w.length() ? w.charAt(i + 1) : 0;
			if (next == VIRAMA) {
				units.add(new Unit(cons, "", false, ""));
				i++;
			} else if (HI_MATRA.containsKey(next)) {
				units.add(new Unit(cons, HI_MATRA.get(next), false, nasal(w, i + 2)));
				i++;
			} else {
				units.add(new Unit(cons, "a", true, nasal(w, i + 1)));
			}
		}
		// Schwa deletion: the unwritten 'a' is silent at the end of a word, and between a
		// vowel-carrying unit and one that carries a vowel too (ka-ma-la -> kamal, la-ka-rii -> lakri).
		int n = units.size();
		boolean[] silent = new boolean[n];
		if (n > 1 && units.get(n - 1).inherent() && units.get(n - 1).nasal().isEmpty()) silent[n - 1] = true;
		for (int i = n - 2; i >= 1; i--) {
			Unit u = units.get(i);
			if (!u.inherent() || !u.nasal().isEmpty() || silent[i + 1]) continue;
			boolean prevVowel = !units.get(i - 1).vowel().isEmpty() && !silent[i - 1];
			boolean nextVowel = !units.get(i + 1).vowel().isEmpty();
			if (prevVowel && nextVowel && !units.get(i + 1).cons().isEmpty()) silent[i] = true;
		}
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < n; i++) {
			Unit u = units.get(i);
			String vowel = silent[i] ? "" : u.vowel();
			if (i == n - 1 && vowel.equals("aa")) vowel = "a";
			b.append(u.cons()).append(vowel).append(u.nasal());
		}
		return b.toString();
	}

	private static String nasal(String w, int at) {
		if (at >= w.length()) return "";
		char c = w.charAt(at);
		if (c == ANUSVARA || c == CHANDRABINDU) {
			char after = at + 1 < w.length() ? w.charAt(at + 1) : 0;
			return (after == 'प' || after == 'ब' || after == 'भ' || after == 'म') ? "m" : "n";
		}
		return c == VISARGA ? "h" : "";
	}
}
