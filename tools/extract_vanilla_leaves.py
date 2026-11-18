"""Copies the vanilla oak, jungle, azalea and flowering azalea leaves textures from the 1.21.1 client resources into reference/vanilla_leaves for study.

python tools/extract_vanilla_leaves.py

Reference only: the vanilla textures are not part of the mod and must not be shipped in it. They are grayscale and tinted
by biome in game; the sage leaves are hand-coloured instead.
"""
import io
import zipfile
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
JAR = ROOT / "build/moddev/artifacts/neoforge-21.1.265-minecraft-resources-aka-client-extra.jar"
OUT = ROOT / "reference/vanilla_leaves"

if __name__ == "__main__":
    z = zipfile.ZipFile(JAR)
    OUT.mkdir(parents=True, exist_ok=True)
    for name in ("oak_leaves", "jungle_leaves", "azalea_leaves", "flowering_azalea_leaves"):
        Image.open(io.BytesIO(z.read(f"assets/minecraft/textures/block/{name}.png"))).save(OUT / f"{name}.png")
    print("ok", OUT)
