"""Build a full language-mod dictionary from Minecraft's own translation file.

Usage:
  python tools/generate_dictionary.py --lang-file <ro_ro.json> --handwritten <ro.json> --out <ro.json>
  python tools/generate_dictionary.py --polymc --lang ro --handwritten src/main/resources/languagemod/defaults/ro.json --out <path>

--polymc finds <lang>_<lang>.json in the PolyMC asset store (%APPDATA%/PolyMC/assets).

For every item/block name ("item.minecraft.X", "block.minecraft.X") the official translation becomes the
first answer (the word shown in the popup). Hand-written answers for the same id are kept as extra accepted
answers (e.g. "stejar" for an oak log). The output is for the user's own install: the translations are
Mojang's, so this file is generated locally and not shipped inside the mod.
"""
import argparse
import json
import os
import sys
import unicodedata
from pathlib import Path


def fold(s: str) -> str:
    """Same idea as the mod's matching: lowercase, no accents, single spaces."""
    s = unicodedata.normalize("NFD", s.lower())
    s = "".join(c for c in s if unicodedata.category(c) != "Mn")
    return " ".join(s.split())


def find_polymc_lang(locale: str) -> Path:
    assets = Path(os.environ["APPDATA"]) / "PolyMC" / "assets"
    key = f"minecraft/lang/{locale}.json"
    for index in sorted((assets / "indexes").glob("*.json"), key=lambda p: p.stat().st_mtime, reverse=True):
        obj = json.loads(index.read_text(encoding="utf-8")).get("objects", {}).get(key)
        if obj:
            h = obj["hash"]
            path = assets / "objects" / h[:2] / h
            if path.exists():
                return path
    sys.exit(f"{key} not found in {assets}")


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--lang-file", type=Path)
    ap.add_argument("--polymc", action="store_true")
    ap.add_argument("--lang", default="ro")
    ap.add_argument("--locale", help="Minecraft locale if not <lang>_<lang>, e.g. he_il")
    ap.add_argument("--handwritten", type=Path, help="hand-written dictionary whose answers are kept as extras")
    ap.add_argument("--out", type=Path, required=True)
    args = ap.parse_args()

    locale = args.locale or f"{args.lang}_{args.lang}"
    lang_file = args.lang_file or (find_polymc_lang(locale) if args.polymc else None)
    if lang_file is None:
        sys.exit("give --lang-file or --polymc")
    official = json.loads(lang_file.read_text(encoding="utf-8"))
    hand = json.loads(args.handwritten.read_text(encoding="utf-8")) if args.handwritten else {}

    items: dict[str, list[str]] = {}
    for key, name in official.items():
        parts = key.split(".")
        if len(parts) != 3 or parts[0] not in ("item", "block") or parts[1] != "minecraft":
            continue
        item_id = f"minecraft:{parts[2]}"
        # an item key wins over a block key of the same id (e.g. item names for placeable items)
        if item_id in items and parts[0] == "block":
            continue
        items[item_id] = [name.strip()]

    added_extra = 0
    for item_id, answers in hand.get("items", {}).items():
        merged = items.setdefault(item_id, [])
        seen = {fold(a) for a in merged}
        for a in answers:
            if fold(a) not in seen:
                merged.append(a)
                seen.add(fold(a))
                added_extra += 1

    out = {
        "_comment": f"Generated from Minecraft's {lang_file.name if args.lang_file else locale + '.json'} "
                    "plus hand-written extras. First answer = word shown in the popup. Local use only.",
        "articles": hand.get("articles", []),
        "items": dict(sorted(items.items())),
    }
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(out, ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"{len(items)} ids written to {args.out} ({added_extra} hand-written extra answers kept)")


if __name__ == "__main__":
    main()
