package dev.itonk.languagemod.dictionary;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.itonk.languagemod.config.ModConfig;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Maps item ids to the answers accepted in one target language.
 *
 * File format (config/languagemod/&lt;lang&gt;.json):
 * <pre>
 * { "articles": ["un", "o"],
 *   "items": { "minecraft:oak_log": ["stejar", "bustean de stejar"] } }
 * </pre>
 * Immutable after construction, so it is safe to swap the whole instance on /languagemod reload.
 */
public final class TranslationDictionary {
	private final String language;
	private final Map<Identifier, Set<String>> answers;
	/** First listed answer as written in the file (accents kept), shown after a miss. */
	private final Map<Identifier, String> display;
	private final Set<String> articles;

	private TranslationDictionary(String language, Map<Identifier, Set<String>> answers,
			Map<Identifier, String> display, Set<String> articles) {
		this.language = language;
		this.answers = Map.copyOf(answers);
		this.display = Map.copyOf(display);
		this.articles = Set.copyOf(articles);
	}

	public static TranslationDictionary load(Path configDir, String language) throws IOException {
		Path file = configDir.resolve(language + ".json");
		ModConfig.copyDefaultIfMissing(language + ".json", file);
		if (!Files.exists(file)) {
			throw new IOException("No dictionary for language '" + language + "' at " + file);
		}
		try (Reader reader = Files.newBufferedReader(file)) {
			return fromJson(language, new Gson().fromJson(reader, JsonObject.class));
		}
	}

	static TranslationDictionary fromJson(String language, JsonObject root) {
		Set<String> articles = new HashSet<>();
		if (root.has("articles")) {
			for (JsonElement a : root.getAsJsonArray("articles")) articles.add(normalizeRaw(a.getAsString()));
		}

		Map<Identifier, Set<String>> answers = new HashMap<>();
		Map<Identifier, String> display = new HashMap<>();
		JsonObject items = root.getAsJsonObject("items");
		for (Map.Entry<String, JsonElement> entry : items.entrySet()) {
			Identifier id = Identifier.tryParse(entry.getKey());
			if (id == null) continue;
			Set<String> accepted = new HashSet<>();
			JsonArray list = entry.getValue().getAsJsonArray();
			for (JsonElement word : list) accepted.add(stripArticle(normalizeRaw(word.getAsString()), articles));
			if (accepted.isEmpty()) continue;
			answers.put(id, accepted);
			display.put(id, list.get(0).getAsString());
		}
		return new TranslationDictionary(language, answers, display, articles);
	}

	public String language() {
		return language;
	}

	public boolean hasEntry(Identifier itemId) {
		return answers.containsKey(itemId);
	}

	public boolean matches(Identifier itemId, String input) {
		return matches(itemId, input, false);
	}

	/**
	 * Compares letters only, so speech-to-text spellings like "Pa-nă" or "Pui crud." match.
	 * With {@code fuzzy}, small recognition slips are forgiven: 1 wrong letter for words of
	 * 4+ letters, 2 for 8+. Short words stay exact so "mor" never passes for "măr".
	 */
	public boolean matches(Identifier itemId, String input, boolean fuzzy) {
		Set<String> accepted = answers.get(itemId);
		if (accepted == null) return false;
		String said = normalize(input);
		if (accepted.contains(said)) return true;
		if (sameWord(accepted, lettersOnly(said), fuzzy)) return true;
		// The answer inside a longer phrase ("e o pâine", "pâine pâine") also counts: try every run
		// of consecutive words as long as an answer, or one word longer (Whisper splits "Pa-nă").
		// Exact letters only here: with slips allowed too, some piece of any long phrase would pass.
		// Chinese/Japanese have no spaces between words: the answer anywhere in the letters counts.
		String saidLetters = lettersOnly(said);
		for (String a : accepted) {
			if (Accents.noSpaces(a) && saidLetters.contains(lettersOnly(a))) return true;
		}
		String[] words = said.split(" ");
		for (String a : accepted) {
			int n = a.split(" ").length;
			for (int len = n; len <= n + 1; len++) {
				for (int start = 0; start + len <= words.length; start++) {
					if (len == words.length) continue; // the whole phrase was checked above
					String window = String.join("", java.util.Arrays.copyOfRange(words, start, start + len));
					if (sameWord(Set.of(a), lettersOnly(window), false)) return true;
				}
			}
		}
		return false;
	}

	private static boolean sameWord(Set<String> accepted, String saidLetters, boolean fuzzy) {
		for (String a : accepted) {
			String target = lettersOnly(a);
			if (target.equals(saidLetters)) return true;
			if (fuzzy) {
				int allowed = target.length() >= 8 ? 2 : target.length() >= 4 ? 1 : 0;
				if (allowed > 0 && editDistance(target, saidLetters, allowed) <= allowed) return true;
			}
		}
		return false;
	}

	private static String lettersOnly(String s) {
		return s.replaceAll("[^\\p{L}\\p{M}\\p{N}]", ""); // \p{M}: Hindi vowel signs are marks
	}

	/** Levenshtein distance, giving up early once it exceeds {@code max}. */
	static int editDistance(String a, String b, int max) {
		if (Math.abs(a.length() - b.length()) > max) return max + 1;
		int[] prev = new int[b.length() + 1];
		int[] cur = new int[b.length() + 1];
		for (int j = 0; j <= b.length(); j++) prev[j] = j;
		for (int i = 1; i <= a.length(); i++) {
			cur[0] = i;
			int rowMin = cur[0];
			for (int j = 1; j <= b.length(); j++) {
				int sub = prev[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1);
				cur[j] = Math.min(sub, Math.min(prev[j] + 1, cur[j - 1] + 1));
				rowMin = Math.min(rowMin, cur[j]);
			}
			if (rowMin > max) return max + 1;
			int[] t = prev; prev = cur; cur = t;
		}
		return prev[b.length()];
	}

	/** First listed answer, shown to the player after a miss so they learn it. */
	public Optional<String> exampleAnswer(Identifier itemId) {
		return Optional.ofNullable(display.get(itemId));
	}

	public String normalize(String input) {
		return stripArticle(normalizeRaw(input), articles);
	}

	/** Lowercase, drop diacritics (ș -> s, ă -> a) and punctuation (speech-to-text adds "Măr."), collapse whitespace. */
	private static String normalizeRaw(String s) {
		return Accents.strip(s.toLowerCase(Locale.ROOT)).replaceAll("[\\p{P}\\p{S}]", " ").trim().replaceAll("\\s+", " ");
	}

	private static String stripArticle(String s, Set<String> articles) {
		List<String> words = List.of(s.split(" "));
		if (words.size() > 1 && articles.contains(words.get(0))) {
			return String.join(" ", words.subList(1, words.size()));
		}
		return s;
	}
}
