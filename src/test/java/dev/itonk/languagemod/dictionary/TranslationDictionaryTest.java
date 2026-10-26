package dev.itonk.languagemod.dictionary;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TranslationDictionaryTest {
	private static TranslationDictionary bundled(String lang) {
		var in = TranslationDictionaryTest.class.getResourceAsStream("/languagemod/defaults/" + lang + ".json");
		JsonObject root = new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class);
		return TranslationDictionary.fromJson(lang, root);
	}

	@Test
	void romanianAcceptsDiacriticsCaseAndArticles() {
		TranslationDictionary ro = bundled("ro");
		Identifier oakLog = Identifier.of("minecraft", "oak_log");
		assertTrue(ro.matches(oakLog, "stejar"));
		assertTrue(ro.matches(oakLog, "  Buștean   de STEJAR "));
		assertTrue(ro.matches(Identifier.of("minecraft", "apple"), "un măr"));
		assertTrue(ro.matches(Identifier.of("minecraft", "apple"), "Mărul"));
		assertTrue(ro.matches(Identifier.of("minecraft", "apple"), " Măr."), "speech-to-text punctuation is ignored");
		assertTrue(ro.matches(oakLog, "Buștean de stejar!"));
		assertFalse(ro.matches(oakLog, "roble"));
		assertFalse(ro.matches(Identifier.of("minecraft", "nether_star"), "stea"));
	}

	/** -Ddict.file=PATH: check a dictionary made by tools/generate_dictionary.py loads and matches. */
	@Test
	void generatedDictionaryLoads() throws Exception {
		String file = System.getProperty("dict.file", "");
		org.junit.jupiter.api.Assumptions.assumeTrue(!file.isBlank(), "no -Ddict.file");
		JsonObject root;
		try (var r = java.nio.file.Files.newBufferedReader(java.nio.file.Path.of(file))) {
			root = new Gson().fromJson(r, JsonObject.class);
		}
		TranslationDictionary ro = TranslationDictionary.fromJson("ro", root);
		assertTrue(ro.matches(Identifier.of("minecraft", "wooden_shovel"), "lopată de lemn"));
		assertTrue(ro.matches(Identifier.of("minecraft", "wooden_shovel"), "Lopata de lemn."));
		assertTrue(ro.matches(Identifier.of("minecraft", "oak_log"), "stejar"), "hand-written extra kept");
		assertTrue(ro.matches(Identifier.of("minecraft", "oak_planks"), "Scânduri de stejar"));
		assertTrue(ro.exampleAnswer(Identifier.of("minecraft", "wooden_shovel")).orElse("").equals("Lopată de lemn"));
		System.out.println("[dict] generated dictionary OK: " + root.getAsJsonObject("items").size() + " ids");
	}

	/** What Whisper actually wrote for the owner's (correct, per a native speaker) "pană" / "pui crud". */
	@Test
	void realWhisperSpellingsOfCorrectAnswers() {
		JsonObject root = new Gson().fromJson("""
				{"articles": ["un", "o"], "items": {
				  "minecraft:feather": ["Pană", "pana"],
				  "minecraft:chicken": ["Pui crud"],
				  "minecraft:apple": ["Măr"] }}""", JsonObject.class);
		TranslationDictionary ro = TranslationDictionary.fromJson("ro", root);
		Identifier feather = Identifier.of("minecraft", "feather");
		Identifier chicken = Identifier.of("minecraft", "chicken");
		Identifier apple = Identifier.of("minecraft", "apple");
		for (String heard : new String[] {"Pa-nă", "Pa-na.", "Pana", "Paană"}) {
			assertTrue(ro.matches(feather, heard, true), heard);
		}
		assertTrue(ro.matches(feather, "Pa-nă", false), "hyphens never matter, even without fuzzy");
		assertTrue(ro.matches(chicken, "Pui crud", true));
		assertTrue(ro.matches(chicken, "Pui cruld", true), "one slip in a long phrase is forgiven");
		assertFalse(ro.matches(feather, "Bună", true), "a different word is still wrong");
		assertFalse(ro.matches(feather, "Pa ne-o", true));
		assertFalse(ro.matches(apple, "mor", true), "3-letter words stay exact");
		assertFalse(ro.matches(chicken, "Pana", true));

		// The answer said inside a longer phrase counts (owner said two words and it failed).
		assertTrue(ro.matches(apple, "mor măr", true), "right word after a wrong one");
		assertTrue(ro.matches(feather, "Pană pană.", true), "said twice");
		assertTrue(ro.matches(chicken, "este pui crud", true), "multi-word answer inside a phrase");
		assertTrue(ro.matches(feather, "o pa-nă albă", true), "split word inside a phrase");
		assertFalse(ro.matches(chicken, "pui de găină", true), "half of a multi-word answer is not enough");
		assertFalse(ro.matches(feather, "Îți să le puteți", true), "background junk still fails");
	}

	@Test
	void spanishStillLoads() {
		TranslationDictionary es = bundled("es");
		assertTrue(es.matches(Identifier.of("minecraft", "oak_log"), "Roble"));
		assertTrue(es.matches(Identifier.of("minecraft", "cobblestone"), "el adoquín"));
	}
}
