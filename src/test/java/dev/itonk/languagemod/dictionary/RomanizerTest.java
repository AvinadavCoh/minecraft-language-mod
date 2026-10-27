package dev.itonk.languagemod.dictionary;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class RomanizerTest {
	@Test
	void latinLanguagesHaveNoReading() {
		assertEquals("", Romanizer.reading("ro", "Pâine"));
		assertEquals("", Romanizer.reading("fr", "Pain"));
	}

	@Test
	void hebrewComesFromTheWordTable() {
		assertEquals("Afar", Romanizer.reading("he", "עפר"));
		assertEquals("Ets alon", Romanizer.reading("he", "עץ אלון"));
		assertEquals("Abu-nafcha", Romanizer.reading("he", "אבו-נפחא"));
		assertEquals("Jungel", Romanizer.reading("he", "ג'ונגל"));
		assertEquals("", Romanizer.reading("he", "מילהשלאקיימת")); // unknown word: nothing, not a guess
	}

	@Test
	void japaneseReadsKanjiByWord() {
		assertEquals("Tsuchi", Romanizer.reading("ja", "土"));
		assertEquals("Ōku no genboku", Romanizer.reading("ja", "オークの原木"));
		assertEquals("Sagyōdai", Romanizer.reading("ja", "作業台"));
		assertEquals("Daiyamondo no ken", Romanizer.reading("ja", "ダイヤモンドの剣"));
		assertEquals("Pan", Romanizer.reading("ja", "パン"));
	}

	@Test
	void russianAndChineseUseIcu() {
		assertEquals("Zemlya", Romanizer.reading("ru", "Земля"));
		assertEquals("Almaznyy mech", Romanizer.reading("ru", "Алмазный меч"));
		assertEquals("Ní tǔ", Romanizer.reading("zh", "泥土"));
		assertEquals("Gōng zuò tái", Romanizer.reading("zh", "工作台"));
	}

	@Test
	void koreanRevisedRomanization() {
		assertEquals("Heuk", Romanizer.reading("ko", "흙"));
		assertEquals("Makdaegi", Romanizer.reading("ko", "막대기"));
		assertEquals("Chamnamu wonmok", Romanizer.reading("ko", "참나무 원목"));
		assertEquals("Hwaetbul", Romanizer.reading("ko", "횃불"));
		assertEquals("Jejakdae", Romanizer.reading("ko", "제작대"));
		assertEquals("Daiamondeu geom", Romanizer.reading("ko", "다이아몬드 검"));
		assertEquals("Dalgi", Romanizer.reading("ko", "닭이")); // ㄺ before a vowel splits: l + g
		assertEquals("Ppang", Romanizer.reading("ko", "빵"));
	}

	@Test
	void hindiDropsTheSilentA() {
		assertEquals("Mitti", Romanizer.reading("hi", "मिट्टी"));
		assertEquals("Danda", Romanizer.reading("hi", "डंडा"));
		assertEquals("Mashaal", Romanizer.reading("hi", "मशाल"));
		assertEquals("Baanj ka lakri", Romanizer.reading("hi", "बाँज का लकड़ी"));
		assertEquals("Kraaphting taalika", Romanizer.reading("hi", "क्राफ्टिंग तालिका"));
	}

	@Test
	void accentsStripOnlyRealAccents() {
		assertEquals("paine", Accents.strip("pâine"));
		assertEquals("ежик", Accents.strip("ёжик"));
		assertEquals("मिट्टी", Accents.strip("मिट्टी"));   // Hindi vowel signs stay
		assertEquals("が", Accents.strip("が"));          // dakuten stays
		assertEquals("흙", Accents.strip("흙"));          // Hangul syllable comes back whole
	}

	/**
	 * Every Hebrew and Japanese item name in the real game gets a reading. Runs where Loom has
	 * downloaded the game's assets (this PC); skipped elsewhere.
	 */
	@Test
	void everyGameNameHasAReading() throws Exception {
		Path assets = Path.of(System.getProperty("user.home"), ".gradle/caches/fabric-loom/assets");
		Path indexes = assets.resolve("indexes");
		assumeTrue(Files.isDirectory(indexes));
		Path index;
		try (var s = Files.list(indexes)) {
			index = s.filter(p -> p.getFileName().toString().startsWith("1.21.11")).findFirst().orElse(null);
		}
		assumeTrue(index != null);
		List<String> problems = new ArrayList<>();
		JsonObject objects = new Gson().fromJson(Files.readString(index), JsonObject.class).getAsJsonObject("objects");
		for (Map.Entry<String, String> lang : Map.of("he", "he_il", "ja", "ja_jp", "ko", "ko_kr", "hi", "hi_in", "zh", "zh_cn", "ru", "ru_ru").entrySet()) {
			String hash = objects.getAsJsonObject("minecraft/lang/" + lang.getValue() + ".json").get("hash").getAsString();
			JsonObject names = new Gson().fromJson(Files.readString(assets.resolve("objects").resolve(hash.substring(0, 2)).resolve(hash)), JsonObject.class);
			JsonObject dict = DictionaryBuilder.build(names, null, lang.getValue());
			List<String> missing = new ArrayList<>();
			int total = 0;
			for (var e : dict.getAsJsonObject("items").entrySet()) {
				String word = e.getValue().getAsJsonArray().get(0).getAsString();
				total++;
				String r = Romanizer.reading(lang.getKey(), word);
				if (total % 150 == 0) System.out.println("[readings] sample " + word + " -> " + r);
				if (r.isEmpty() || !r.matches("[\\p{IsLatin}\\p{M}\\p{N}\\p{P}\\p{Zs}ʹʺ]+")) missing.add(word + " -> " + r);
			}
			System.out.println("[readings] " + lang.getKey() + ": " + (total - missing.size()) + "/" + total);
			if (!missing.isEmpty()) problems.add(lang.getKey() + " has no/bad reading for " + missing.size() + ": " + missing.subList(0, Math.min(30, missing.size())));
		}
		assertTrue(problems.isEmpty(), String.join("\n", problems));
	}
}
