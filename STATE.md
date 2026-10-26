# STATE — Language Mod (Fabric 1.21.11)

**Goal:** Fawcette-style challenge: every new item type in your inventory must be named in the target language (default Romanian) within 15 s, or the stack is deleted + 0.5 heart damage.

**Versions:** MC 1.21.11 · Yarn 1.21.11+build.6 · Loader 0.20.1 · Loom 1.14.10 · Fabric API 0.141.6+1.21.11 · Gradle 9.2.0 · JDK 21 (pinned in gradle.properties because JAVA_HOME is JDK 17).

## Last slice (2026-10-26)
- Build + unit tests pass; client gametest bot passes 20/20 checks (adds F swap, inventory clicks, particles+sound, reload, Spanish).
- Played in PolyMC by Claude: dig dirt -> "pământ" correct; craft planks -> wrong answer deleted them. Owner's keybinds: inventory = Tab, drop = Z.
- Miss message now shows the first listed answer with accents (măr, pâine).
- PolyMC instance `%APPDATA%/PolyMC/instances/LanguageMod-1.21.11` (Fabric loader 0.20.1 + Fabric API + mod jar). Copy `build/libs/language-mod-*.jar` into its `.minecraft/mods` after each build.
- Gotcha: peaceful regen heals the 1.0 damage within a second, so tests measure the damage event, not health.

## Next 3 steps
1. Owner plays in PolyMC and judges feel (timer length, freeze, messages).
2. Persist `verified` sets per player (currently reset each server start).
3. Speech hook: call `ChallengeManager.submitAnswerFromAnyThread(server, uuid, text)` from the Whisper client.

## Key files
- `src/main/java/dev/itonk/languagemod/challenge/ChallengeManager.java` — rules (scan, timer, verify, punish)
- `.../challenge/InventoryOps.java` — all inventory writes (server thread only)
- `.../challenge/FreezeControl.java` — movement freeze via attribute modifiers
- `.../challenge/ChallengeEffects.java` — chat/sound/particles
- `.../dictionary/TranslationDictionary.java` — JSON dictionary + answer normalisation
- `.../mixin/ServerPlayNetworkHandlerMixin.java` — blocks drop/swap/slot clicks while frozen
- `src/main/resources/languagemod/defaults/{config,ro,es}.json` — copied to `config/languagemod/` on first launch
