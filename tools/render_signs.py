"""Render all stable-sign placements using the actual model JSONs and texture PNGs."""
import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image

import render_block as renderer

ROOT = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/bodaddon'


def render(output):
    output.mkdir(parents=True, exist_ok=True)
    renderer.CENTER = np.array([8., 8.5, 8.])
    for placement in ('wall', 'floor', 'ceiling', 'flat'):
        renderer.MODEL = json.loads((ROOT / f'models/block/stable_sign_{placement}.json').read_text())
        renderer.TEX = {
            key: np.array(Image.open(ROOT / 'textures' / (value.split(':')[1] + '.png')).convert('RGBA'))
            for key, value in renderer.MODEL['textures'].items() if key != 'particle'
        }
        renderer.render(22, 12, 28, (580, 600)).save(output / f'{placement}.png')
        renderer.render(202, 12, 28, (580, 600)).save(output / f'{placement}-back.png')


if __name__ == '__main__':
    render(Path(sys.argv[1]))
