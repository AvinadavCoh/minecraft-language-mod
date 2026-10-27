# STATE — Language Mod (Fabric 1.21.11)

**Goal:** Fawcette-style challenge: every new item type in your inventory must be named in the target language (default Romanian) within 15 s, or the stack is deleted + 0.5 heart damage.

**Versions:** MC 1.21.11 · Yarn 1.21.11+build.6 · Loader 0.20.1 · Loom 1.14.10 · Fabric API 0.141.6+1.21.11 · Gradle 9.2.0 · JDK 21 (pinned in gradle.properties because JAVA_HOME is JDK 17).

## Last slice (2026-10-26)
- Build + unit tests pass; client gametest bot passes 20/20 checks (adds F swap, inventory clicks, particles+sound, reload, Spanish).
- Played in PolyMC by Claude: dig dirt -> "pământ" correct; craft planks -> wrong answer deleted them. Owner's keybinds: inventory = Tab, drop = Z.
- Miss message now shows the first listed answer with accents (măr, pâine).
- Fawcette-style popup (client/ package, HudPayloads S2C): icon + word to read + time bar; wrong tries colour letters (LetterDiff) and retry until timeout (`wrongAnswerPunishes=false`); plain chat = answer (`chatAnswers`). Bot 23/23.
- PolyMC instance `%APPDATA%/PolyMC/instances/LanguageMod-1.21.11` (Fabric loader 0.20.1 + Fabric API + mod jar). Copy `build/libs/language-mod-*.jar` into its `.minecraft/mods` after each build.
- Gotcha: peaceful regen heals the 1.0 damage within a second, so tests measure the damage event, not health.

- Voice input (client/voice + voice/): open mic during challenges (default) or push-to-talk V; energy VAD cuts phrases;
  whisper.cpp via whisper-jni 1.7.1 (vetted: Maven Central, GiviMAD/openHAB, Apache-2.0, no runtime deps, bundled AVX2 win DLL;
  a whisper.dll on PATH overrides it, e.g. a CUDA build). Model ggml-small-q5_1.bin (190 MB, auto-downloaded from
  huggingface ggerganov/whisper.cpp, sha256 ae85e4a9…). Settings: config/languagemod/voice.json.
- Measured (Ryzen 5 3600, 6 threads): full 30 s window ~7 s/answer; audioContext 1000 ~4 s, all test words right;
  below ~750 Whisper adds junk endings ("Mărf"). Phrases < 1 s must be padded (whisper.cpp rejects them).
  The on-screen word is passed as a prompt hint: "pâine" was misheard as "Pui ne?" without it; "mor" stays "Mor" with it.
- Mic gotcha: Java's default capture device follows Windows' default recording device, which on the owner's PC is
  Line In (hiss only). MicCapture now prefers a real "Microphone ..." device; voice.json "micDevice" overrides;
  the log lists all devices. Diagnose with ~/.claude/tools/mic-test (records every input to WAV at once).
  Owner's real voice in PolyMC: 2nd try accepted, ~4.3 s per answer. Debug WAVs: voice.json "saveRecordings": true.
- Test audio: edge-tts ro-RO-AlinaNeural clips in src/gametest/resources/voice. Real-Whisper IT: `./gradlew test -Dwhisper.model=PATH -Dwhisper.threads=6`.

- 2026-10-26 evening: freezeMovement default false (owner). Per-player WordProgress (progress/) saved to
  <world>/languagemod/players/<uuid>.json; Leitner boxes 1-5 on play time (5/20/60/180/600 min); reviews only for
  carried items, priority missed > lowest box > most overdue; 20 s gap between reviews; J = DictionaryScreen.
- Owner's real voice: Whisper mostly heard "pană"/"pui crud" right, but results were 5-24 s late because every
  repeat queued another 5 s transcription. Now only the newest phrase is transcribed; client sends VoiceBusy so the
  server waits (voiceGraceSeconds 10) instead of failing; matching is letters-only + fuzzy (1 slip per 4+ letters).
- Full dictionary: `python -X utf8 tools/generate_dictionary.py --polymc --lang ro --handwritten
  src/main/resources/languagemod/defaults/ro.json --out <instance>/config/languagemod/ro.json` (Mojang text, local only).

- 2026-10-26 late: owner plays on a server WITHOUT the mod (a friend's server), so the
  server-side rules never ran. Decision (owner): client-only mode. ChallengeManager is now side-agnostic via
  ChallengePlayer (ServerChallengePlayer / client ClientChallengePlayer). ClientOnlyMode starts 2 s after join when the
  server doesn't list our C2S channels; progress in config/languagemod/servers/<address>/. Miss = red flash +
  animateDamage shake + Ctrl+Q-style throw (no real damage possible). Chat answers cancelled client-side.
  Test hook LanguageMod.setServerRules(false) makes the integrated server act modless. Bot 48 PASS.

- GPU voice: whisper-jni 1.7.1 has no CUDA build, so the mod runs the official whisper.cpp CUDA build's
  whisper-server.exe (v1.9.2 whisper-cublas-12.4.0-bin-x64.zip, 639 MB, sha256 443110dd…; the 11.8 zip lacks cuBLAS and
  silently runs on CPU) as a 127.0.0.1 helper (WhisperServerTranscriber), FallbackTranscriber -> CPU. Unpacked in
  .tools/whisper-cuda (gitignored); voice.json "gpu"/"gpuServer". First start on a PC: ~139 s CUDA kernel compile
  (cached). Measured ~80 ms/answer on the RTX 2060 SUPER. Pid file stops a helper left by a crashed game.
- Matching: answer inside a longer phrase counts (exact letters for the inner window; fuzzy only on the whole phrase).

- Settings screen (K): writes config.json/voice.json, reloads on the integrated server thread or client-only rules;
  locked rules on a remote modded server. Languages list = resources/languagemod/languages.json; picking one builds
  config/languagemod/<code>.json from the game's own lang file via the resource manager (GameDictionaries +
  DictionaryBuilder), hand-written defaults merged; files under 500 items get upgraded. Progress per language:
  players/<lang>/<uuid>.json (old players/<uuid>.json read as Romanian). Language switch re-seeds carried items spaced out.
- Hebrew: Minecraft's Text/chat path reorders RTL itself even with an English UI; only the per-letter popup word needs
  BidiText.visualOrder. (First attempt reversed chat twice — caught by zooming the screenshot.)

- Public: https://github.com/AvinadavCoh/minecraft-language-mod (MIT). Server address scrubbed from history before
  the first push. JDK 21 found via gradle/gradle-daemon-jvm.properties (no machine path in the repo). Releases: v0.1.0, v0.1.1 (Whisper-loop disconnect fix, sha256 29bb860a…).

- 2026-10-27: 12 languages (top learned minus English, plus ro/he). Readings under non-Latin words (dictionary/Romanizer):
  ICU4J 77.1 is a Minecraft library (full translit data, zero jar cost): ru = Russian-Latin/BGN, zh = Han-Latin.
  ICU's Hangul is letter-by-letter and its Devanagari keeps every schwa, so ko (Revised Romanization + sound changes)
  and hi (schwa deletion) are own rules. ICU reads Japanese kanji as Mandarin and Hebrew without vowels, so ja/he use
  hand-written word tables (resources/languagemod/readings/*.json; 729 Hebrew words, 443 kanji tokens). Unknown word =
  no reading. RomanizerTest checks all 1,846 game names per language have one (needs Loom's asset cache).
  Accents.strip replaced the NFD+\p{M} stripping, which would have deleted Hindi vowel signs and Japanese dakuten.
  CJK answers count anywhere inside a phrase (no spaces in Chinese/Japanese). DictionarySnapshot now carries the language.

## Next 3 steps
1. Owner plays in PolyMC and judges feel (timer length, freeze, messages).
2. Persist `verified` sets per player (currently reset each server start).
3. Voice speed: ~4 s per answer on CPU. A CUDA whisper.dll (RTX 2060) on PATH would make it <1 s.

## Key files
- `src/main/java/dev/itonk/languagemod/challenge/ChallengeManager.java` — rules (scan, timer, verify, punish)
- `.../challenge/InventoryOps.java` — all inventory writes (server thread only)
- `.../challenge/FreezeControl.java` — movement freeze via attribute modifiers
- `.../challenge/ChallengeEffects.java` — chat/sound/particles
- `.../dictionary/TranslationDictionary.java` — JSON dictionary + answer normalisation
- `.../mixin/ServerPlayNetworkHandlerMixin.java` — blocks drop/swap/slot clicks while frozen
- `src/main/resources/languagemod/defaults/{config,ro,es}.json` — copied to `config/languagemod/` on first launch
