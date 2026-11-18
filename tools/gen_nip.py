"""Install the supplied diagonal grass-bundle artwork and its matching status icon."""
import json
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "src/main/resources"
# Recolor vanilla's untinted foliage shades into the shared dragon nip greens.
PALETTE = {
    (88, 89, 88): "2e4936",
    (107, 110, 107): "3e6242",
    (124, 126, 124): "577d4b",
    (140, 138, 140): "759b5c",
    (148, 145, 148): "99b86f",
    (165, 162, 165): "c4d48b",
}
COLORS = {source: tuple(bytes.fromhex(target)) for source, target in PALETTE.items()}


def tint_foliage(source):
    im = source.convert("RGBA")
    im.putdata([COLORS[min(COLORS, key=lambda shade: abs(shade[0] - pixel[0]))] + (pixel[3],)
                for pixel in im.getdata()])
    return im


def main():
    source = ROOT / "tools/art/dragon_nip.png"
    im = Image.open(source).convert("RGBA")
    if im.size != (16, 16):
        raise ValueError("Dragon nip source artwork must be 16x16.")
    for path in ["assets/bodaddon/textures/item/dragon_nip.png", "assets/bodaddon/textures/mob_effect/calmed.png"]:
        dest = RES / path
        dest.parent.mkdir(parents=True, exist_ok=True)
        dest.write_bytes(source.read_bytes())
    model = RES / "assets/bodaddon/models/item/dragon_nip.json"
    model.write_text(json.dumps({"parent": "minecraft:item/generated", "textures": {"layer0": "bodaddon:item/dragon_nip"}}, indent=2) + "\n")
    preview = ROOT / ".scratch/dragon_nip_preview.png"
    im.resize((128, 128), Image.Resampling.NEAREST).save(preview)
    print(preview)


if __name__ == "__main__":
    main()
