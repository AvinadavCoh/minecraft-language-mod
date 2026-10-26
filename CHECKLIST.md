# Checklist

Bot = `./gradlew runClientGameTest` (src/gametest, real chat/W/Q/F/inventory input, 20 checks). PolyMC = played by Claude in instance "LanguageMod 1.21.11". Screenshots in docs/test-shots/.

- [x] Mod compiles against 1.21.11 · [RAN] ./gradlew build · 2026-10-26
- [x] Mod loads and plays in PolyMC · [SAW] polymc-correct-dirt.jpg · 2026-10-26
- [x] Romanian answers match ignoring case/diacritics/articles · [RAN] ./gradlew test · 2026-10-26
- [x] New item shows "Name this item in Romanian!" (/give, digging, crafting) · [RAN] bot + [SAW] PolyMC dig + craft · 2026-10-26
- [x] Player is frozen (W key moves 0 blocks, speed 0) · [RAN] bot · 2026-10-26
- [x] Drop key blocked while frozen · [RAN] bot (Q) + [SAW] PolyMC (Z bind) · 2026-10-26
- [x] Offhand swap (F) blocked · [RAN] bot · 2026-10-26
- [x] Inventory click / throw blocked, client resynced · [RAN] bot + [SAW] 01b-inventory-locked · 2026-10-26
- [x] Correct answer verifies + unfreezes · [RAN] bot + [SAW] PolyMC "Correct! Dirt" · 2026-10-26
- [x] Wrong answer deletes the stack, 1.0 damage, shows the word with accents · [RAN] bot + [SAW] 03-wrong, polymc-wrong-planks · 2026-10-26
- [x] Break particles + item-break sound · [RAN] bot (24 particles, sound event heard) + [SAW] 03-wrong · 2026-10-26
- [x] 15 s timeout does the same punishment · [RAN] bot + [SAW] 04-timeout · 2026-10-26
- [x] Verified item type isn't asked again · [RAN] bot · 2026-10-26
- [x] `/languagemod reload` + Spanish switch · [RAN] bot + [SAW] 05-spanish-prompt · 2026-10-26
- [x] Popup: item icon, big word to read, draining time bar (green→red) · [RAN] bot + [SAW] 02b-popup · 2026-10-26
- [x] Wrong try colours letters green/red, keeps timer (retry) · [RAN] bot ('mor' → GRG) + [SAW] 03-partial · 2026-10-26
- [x] Plain chat line counts as the answer (no /verify) · [RAN] bot · 2026-10-26
- [x] Popup result states (green Correct / red Item lost) then hides · [RAN] bot + [SAW] 03b-correct-popup, 04-timeout · 2026-10-26
- [ ] Popup seen in PolyMC · new jar installed, not played yet (focus taken by another window)
- [ ] Sound actually audible through speakers · owner (I can't hear)
- [ ] Fun/feel: 15 s, freeze, message wording · owner
