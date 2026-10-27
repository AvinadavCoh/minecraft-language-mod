package dev.itonk.languagemod.dictionary;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Builds a full dictionary from one of Minecraft's own language files (Java version of
 * tools/generate_dictionary.py). The game's translations are Mojang's, so the mod never ships
 * them: the client builds the file on the player's PC from the game it already has.
 */
public final class DictionaryBuilder {
	private DictionaryBuilder() {}

	/**
	 * @param official    a Minecraft lang file ("item.minecraft.apple": "Яблоко", ...)
	 * @param handwritten the mod's own short answers for this language, kept as extra answers; may be null
	 */
	public static JsonObject build(JsonObject official, JsonObject handwritten, String source) {
		Map<String, List<String>> items = new TreeMap<>();
		for (Map.Entry<String, JsonElement> e : official.entrySet()) {
			String[] parts = e.getKey().split("\\.");
			if (parts.length != 3 || !(parts[0].equals("item") || parts[0].equals("block")) || !parts[1].equals("minecraft")) continue;
			String id = "minecraft:" + parts[2];
			// An item key wins over a block key of the same id.
			if (items.containsKey(id) && parts[0].equals("block")) continue;
			List<String> answers = new ArrayList<>();
			answers.add(e.getValue().getAsString().trim());
			items.put(id, answers);
		}
		JsonArray articles = new JsonArray();
		if (handwritten != null) {
			if (handwritten.has("articles")) articles = handwritten.getAsJsonArray("articles");
			for (Map.Entry<String, JsonElement> e : handwritten.getAsJsonObject("items").entrySet()) {
				List<String> merged = items.computeIfAbsent(e.getKey(), k -> new ArrayList<>());
				Set<String> seen = new HashSet<>();
				for (String a : merged) seen.add(fold(a));
				for (JsonElement a : e.getValue().getAsJsonArray()) {
					if (seen.add(fold(a.getAsString()))) merged.add(a.getAsString());
				}
			}
		}
		JsonObject out = new JsonObject();
		out.addProperty("_comment", "Built on this PC from Minecraft's " + source
				+ " plus the mod's hand-written extras. First answer = word shown in the popup. Local use only.");
		out.add("articles", articles);
		JsonObject itemsJson = new JsonObject();
		items.forEach((id, answers) -> {
			JsonArray a = new JsonArray();
			answers.forEach(a::add);
			itemsJson.add(id, a);
		});
		out.add("items", itemsJson);
		return out;
	}

	private static String fold(String s) {
		String d = Accents.strip(s.toLowerCase(Locale.ROOT));
		return d.trim().replaceAll("\\s+", " ");
	}
}
