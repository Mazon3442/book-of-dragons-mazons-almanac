"""Generates the Dragon Editor item switcher and the addon lang file.
Run: python tools/gen_assets.py   (needs Pillow)"""
import json, os
from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main")
RES = os.path.join(ROOT, "resources")
MOD = "bodaddon"

def write(path, data, mode="w"):
    full = os.path.join(RES, path); os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, mode) as f: f.write(data)

lang = {"itemGroup.bodaddon": "Book of Dragons Addon"}
# The staff model, texture and icon (Drago's pike) come from tools/gen_pike.py; this file keeps the item switcher and lang.
write(f"assets/{MOD}/models/item/dragon_editor_2d.json",
      json.dumps({"parent": "minecraft:item/generated", "textures": {"layer0": f"{MOD}:item/dragon_editor"}}, indent=2))
write(f"assets/{MOD}/models/item/dragon_editor.json", json.dumps({
    "parent": "minecraft:item/handheld", "loader": "neoforge:separate_transforms",
    "base": {"parent": f"{MOD}:item/dragon_editor_3d"},
    "perspectives": {p: {"parent": f"{MOD}:item/dragon_editor_2d"} for p in ("gui", "ground", "fixed")}}, indent=2))
lang.update({
    "item.bodaddon.sagefruit": "Sagefruit",
    "item.bodaddon.sagefruit.desc1": "Feed to a dragon to befriend",
    "item.bodaddon.sagefruit.desc2": "every dragon within 32 blocks.",
    "block.bodaddon.sage_leaves": "Sage Leaves",
    "block.bodaddon.sage_sapling": "Sage Sapling",
    "bodaddon.sagefruit.owner_only": "Use sagefruit on a wild dragon or your own dragon.",
    "bodaddon.sagefruit.no_new_friends": "No new dragon friends nearby. Save this sagefruit for later.",
    "bodaddon.sagefruit.befriended": "This dragon formed %s lasting dragon friendships.",
    "item.bodaddon.dragon_nip": "Dragon Nip",
    "item.bodaddon.dragon_nip.desc1": "Feed to a dragon to calm it",
    "item.bodaddon.dragon_nip.desc2": "and ease taming for 5 minutes.",
    "effect.bodaddon.calmed": "Calmed",
    "bodaddon.nip.owner_only": "Use dragon nip on a wild dragon or your own dragon.",
    "bodaddon.nip.already_calmed": "This dragon is already calmed by dragon nip.",
    "bodaddon.nip.applied": "Dragon calmed for 5 minutes. It is now more receptive to taming.",
    "item.bodaddon.dragon_treat": "Dragon Treat",
    "item.bodaddon.dragon_poultice": "Dragon Poultice",
    "bodaddon.care.owner_only": "Use this on your own tamed dragon.",
    "bodaddon.care.bond_full": "This dragon's loyalty is already full.",
    "bodaddon.care.not_injured": "This dragon has no injuries to treat.",
    "bodaddon.care.treating": "This dragon already has a poultice applied.",
    "bodaddon.care.treatment_started": "Poultice applied. In 60 seconds, injury recovery will shorten by one hour.",
    "item.bodaddon.dragon_editor": "Dragon Editor",
    "item.bodaddon.dragon_editor.desc": "Creative only. Right-click a dragon to edit it.",
    "bodaddon.editor.creative_only": "The Dragon Editor only works in creative mode.",
    "bodaddon.editor.title": "Dragon editor",
    "bodaddon.editor.instant_changes": "Changes apply immediately",
    "bodaddon.editor.drag_hint": "Drag to turn",
    "bodaddon.editor.zoom_hint": "Scroll to zoom",
    "bodaddon.editor.preview": "Dragon preview",
    "bodaddon.editor.trait_balance": "Trait balance",
    "bodaddon.editor.preview_unavailable": "Out of view",
    "bodaddon.editor.tame": "Tame instantly",
    "bodaddon.editor.tame.tip": "Tames this dragon to you right away.",
    "bodaddon.editor.tamed": "Already tamed",
    "block.bodaddon.feeding_trough": "Feeding Trough",
    "bodaddon.editor.tab.bond": "Bond",
    "bodaddon.editor.tab.vitals": "Health and care",
    "bodaddon.editor.tab.personality": "Personality",
    "bodaddon.editor.tab.social": "Friendships",
    "bodaddon.editor.tab.variants": "Colors and markings",
    "bodaddon.editor.variants.none": "This dragon has no colors or markings to change.",
    "bodaddon.editor.variants.page": "Page %s of %s",
    "bodaddon.editor.loyalty": "Loyalty",
    "bodaddon.editor.health_label": "Health",
    "bodaddon.editor.max_health": "Max health",
    "bodaddon.editor.bond.wild": "Wild",
    "bodaddon.editor.bond.cautious": "Cautious",
    "bodaddon.editor.bond.wary": "Warming Up",
    "bodaddon.editor.bond.trusting": "Trusting",
    "bodaddon.editor.bond.devoted": "Devoted",
    "bodaddon.editor.bond.wild.desc": "Wild and untrusting. It will not accept a rider.",
    "bodaddon.editor.bond.cautious.desc": "Starting to tolerate you, but keeps its distance.",
    "bodaddon.editor.bond.wary.desc": "Warming up. It accepts food and a gentle touch.",
    "bodaddon.editor.bond.trusting.desc": "Trusts you and will follow your lead.",
    "bodaddon.editor.bond.devoted.desc": "Bonded for life. Fully devoted to its rider.",
    "bodaddon.editor.health": "Health: %s / %s",
    "bodaddon.editor.clear_injury": "Heal injuries",
    "bodaddon.editor.clear_breeding": "Clear breeding timer",
    "bodaddon.editor.injured": "Injured",
    "bodaddon.editor.healthy": "Not injured",
    "bodaddon.editor.breeding_timer": "Breeding timer: %sm %ss",
    "bodaddon.editor.befriend": "Befriend nearby dragons",
    "bodaddon.editor.befriend.desc": "Every dragon within 32 blocks will stop fighting and get along.",
    "bodaddon.editor.befriended": "%s dragons now get along.",
    "bodaddon.editor.trait.aggression": "Aggression",
    "bodaddon.editor.trait.bravery": "Bravery",
    "bodaddon.editor.trait.dominance": "Dominance",
    "bodaddon.editor.trait.energy": "Energy",
    "bodaddon.editor.trait.nervousness": "Nervousness",
    "bodaddon.editor.trait.nerves": "Nerves",
    "bodaddon.editor.trait.sympathy": "Sympathy",
})
write(f"assets/{MOD}/lang/en_us.json", json.dumps(lang, indent=2))
print("ok")
