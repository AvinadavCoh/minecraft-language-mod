package dev.itonk.languagemod.dictionary;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** The languages a player can pick (resources/languagemod/languages.json). */
public final class LanguageList {
	public record Language(String code, String name, String locale) {}

	private LanguageList() {}

	public static List<Language> load() {
		List<Language> out = new ArrayList<>();
		try (Reader r = new InputStreamReader(LanguageList.class.getResourceAsStream("/languagemod/languages.json"), StandardCharsets.UTF_8)) {
			JsonObject root = new Gson().fromJson(r, JsonObject.class);
			for (var e : root.getAsJsonArray("languages")) {
				JsonObject o = e.getAsJsonObject();
				out.add(new Language(o.get("code").getAsString(), o.get("name").getAsString(), o.get("locale").getAsString()));
			}
		} catch (Exception e) {
			throw new IllegalStateException("languages.json is missing or broken", e);
		}
		return out;
	}
}
