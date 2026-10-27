# Store listing: Say It Or Lose It

Copy-paste source for Modrinth and CurseForge. Keep both in sync with the README.

## Fields

| Field | Modrinth | CurseForge |
|---|---|---|
| Name | Say It Or Lose It | Say It Or Lose It |
| URL slug | `say-it-or-lose-it` | `say-it-or-lose-it` |
| Summary (one sentence, no formatting) | Learn a language while you play: say the name of every new item you pick up before the timer runs out, or take damage. | same |
| Categories | Game Mechanics, Minigame, Utility | Miscellaneous, Utility & QoL |
| Loader / version | Fabric, Minecraft 1.21.11 | Fabric, 1.21.11 |
| Environment | Client: required. Server: optional (the server enforces the rules when it has the mod; without it the mod runs on the client) | Client + Server |
| Dependencies | Fabric API (required) | Fabric API (required) |
| License | MIT | MIT |
| Source | https://github.com/AvinadavCoh/minecraft-language-mod | same |
| Issues | https://github.com/AvinadavCoh/minecraft-language-mod/issues | same |
| Icon | 512×512 PNG made by the owner (Modrinth: no AI-made images) | 400×400 PNG, no Minecraft textures (copyrighted imagery) |
| Gallery | docs/test-shots: 0003_02b-popup, 0005_03b-correct-popup, 0004_03-partial, 0012_13-settings, 0014_15-hebrew-prompt, 0015_16-hebrew-dictionary, 0013_11-client-only-miss | same |
| Disclosures | "Contains AI-generated content": YES (code written with an AI assistant). No data uploaded anywhere. | none required |

## Description (both sites)

Learn a language while you play. Every time a new item lands in your inventory, a popup shows the item and its name in the language you're learning. Say it out loud, or type it, before the timer runs out. Miss it and you take damage, and if you want it harder, you lose the item too.

Inspired by Fawcette's "name every item in another language" challenge.

**What it does**

- Popup with the item, the word to read and a time bar. Wrong tries colour each letter green or red.
- Voice answers with speech-to-text that runs on your own PC (whisper.cpp). Your voice is never sent anywhere. Open mic or push-to-talk.
- Optional NVIDIA GPU mode: about 0.2 seconds per answer instead of about 4 seconds on the CPU.
- Typed answers: while a word is asked, a chat line is your answer, and nobody else sees it.
- Your dictionary (J): every word you've met, what you know and what needs repeating. Words come back on a spaced schedule; missed ones first.
- Settings (K): language, time to answer, what a miss does, voice mode.
- 12 languages: Spanish, French, German, Italian, Portuguese (Brazil), Russian, Japanese, Korean, Chinese (Simplified), Hindi, Romanian, Hebrew (right to left). Switch any time; each language keeps its own progress.
- Words in other alphabets show how to read them in small Latin letters (עפר → Afar, Торт → Tort, 土 → Tsuchi).
- Works on any server. With the mod on the server, the server enforces the rules. Without it, the mod runs on your side and a miss shakes your screen instead of dealing real damage.

**Good to know**

- Voice needs a speech model (ggml-small-q5_1.bin, 190 MB, from huggingface.co). The mod asks before downloading it; you can say no and type your answers instead.
- Item names come from Minecraft's own translations, built on your PC from the game's files.
- Faster voice with an NVIDIA card: see the GitHub README.

**Keys:** J dictionary, K settings, V push-to-talk (all can be changed in Controls).

## Changelog for the first upload (0.2.0)

- Renamed to Say It Or Lose It.
- Asks before downloading the voice model.
- Whisper loops can no longer disconnect you (fixed in 0.1.1).
