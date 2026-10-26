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
import java.text.Normalizer;
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
		Set<String> accepted = answers.get(itemId);
		return accepted != null && accepted.contains(normalize(input));
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
		String decomposed = Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
		return decomposed.replaceAll("\\p{M}", "").replaceAll("[\\p{P}\\p{S}]", " ").trim().replaceAll("\\s+", " ");
	}

	private static String stripArticle(String s, Set<String> articles) {
		List<String> words = List.of(s.split(" "));
		if (words.size() > 1 && articles.contains(words.get(0))) {
			return String.join(" ", words.subList(1, words.size()));
		}
		return s;
	}
}
