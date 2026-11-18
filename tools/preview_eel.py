"""Software previews of the existing eel geometry and the repellent cube, without launching Minecraft."""
import numpy as np
from PIL import Image, ImageDraw

import render_block as renderer
from eel_art import ASSETS, ROOT


def eel_preview():
    atlas = Image.open(ASSETS / 'textures/entity/eel.png')
    elements, textures = [], {}

    def box(name, bounds, uv, dimensions):
        u, v = uv
        w, h, d = dimensions
        regions = {
            'up': (u+d, v, u+d+w, v+d),
            'down': (u+d+w, v, u+d+2*w, v+d),
            'west': (u, v+d, u+d, v+d+h),
            'east': (u+d+w, v+d, u+2*d+w, v+d+h),
            'north': (u+d, v+d, u+d+w, v+d+h),
            'south': (u+2*d+w, v+d, u+2*d+2*w, v+d+h),
        }
        faces = {}
        for face, crop in regions.items():
            if crop[0] == crop[2] or crop[1] == crop[3]:
                continue
            image = atlas.crop(crop)
            if face == 'up':
                image = image.transpose(Image.Transpose.FLIP_TOP_BOTTOM)
            elif face != 'down':
                image = image.transpose(Image.Transpose.FLIP_LEFT_RIGHT)
            key = name + face
            textures[key] = np.array(image)
            faces[face] = {'texture': '#' + key, 'uv': [0, 0, 16, 16]}
        elements.append({'from': bounds[0], 'to': bounds[1], 'faces': faces})

    box('head', [(-1, 0, -8), (1, 2, -5)], (0, 0), (2, 2, 3))
    box('front', [(-1, 0, -5), (1, 2, 0)], (0, 6), (2, 2, 5))
    box('back', [(-1, 0, 0), (1, 2, 5)], (0, 14), (2, 2, 5))
    box('tail', [(-1, 0, 5), (1, 2, 9)], (0, 22), (2, 2, 4))
    box('dorsal1', [(-.02, 2, -5), (.02, 3, 0)], (16, 8), (0, 1, 5))
    box('dorsal2', [(-.02, 2, 0), (.02, 3, 5)], (16, 14), (0, 1, 5))
    box('fin', [(-.02, -1, 9), (.02, 3, 12)], (16, 0), (0, 4, 3))
    box('rightfin', [(-3, .98, -5), (-1, 1.02, -3)], (24, 0), (2, 0, 2))
    box('leftfin', [(1, .98, -5), (3, 1.02, -3)], (24, 2), (2, 0, 2))
    renderer.MODEL = {'elements': elements}
    renderer.TEX = textures
    renderer.CENTER = np.array([0., 1., 2.])
    return renderer.render(65, 24, 16, (430, 210))


def block_preview():
    renderer.MODEL = {'elements': [{'from': [0, 0, 0], 'to': [16, 16, 16], 'faces': {
        f: {'texture': '#'+('top' if f in ('up', 'down') else 'side'), 'uv': [0, 0, 16, 16]}
        for f in renderer.NORMAL}}]}
    renderer.TEX = {k: np.array(Image.open(ASSETS / 'textures/block' / f'{n}.png'))
                    for k, n in {'top': 'dragon_repellent_block_top', 'side': 'dragon_repellent_block'}.items()}
    renderer.CENTER = np.array([8., 8., 8.])
    return renderer.render(225, 30, 6, (210, 210))


if __name__ == '__main__':
    output = Image.new('RGBA', (800, 460), '#30343b')
    output.alpha_composite(eel_preview(), (5, 0))
    output.alpha_composite(block_preview(), (520, 0))
    draw = ImageDraw.Draw(output)
    draw.text((18, 195), 'Eel - existing model, revised skin (software preview)', fill='#eee7d6')
    draw.text((535, 195), 'Repellent block', fill='#eee7d6')
    for i, (name, label) in enumerate((('eel', 'Raw eel'), ('eel_bucket', 'Vanilla water bucket + eel'), ('dragon_repellent', 'Vanilla potion + olive liquid'))):
        image = Image.open(ASSETS / f'textures/item/{name}.png')
        x = 70 + i * 260
        output.alpha_composite(image.resize((160, 160), Image.Resampling.NEAREST), (x, 235))
        output.alpha_composite(image, (x + 72, 405))
        draw.text((x, 435), label, fill='#eee7d6')
    output.save(ROOT / 'output/eel-repellent-preview.png')
