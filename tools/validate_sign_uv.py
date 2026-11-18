"""Assert that every stable-sign face uses square texels at one pixel per model unit."""
import json
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/bodaddon'


def validate():
    count = 0
    for placement in ('wall', 'ceiling', 'floor', 'flat'):
        model = json.loads((ROOT / f'models/block/stable_sign_{placement}.json').read_text())
        for element in model['elements']:
            dx, dy, dz = [b - a for a, b in zip(element['from'], element['to'])]
            for direction, face in element['faces'].items():
                sprite = model['textures'][face['texture'].removeprefix('#')]
                with Image.open(ROOT / 'textures' / (sprite.split(':')[1] + '.png')) as image:
                    assert image.size == (64, 32), (placement, image.size)
                    assert set(image.convert('RGBA').getchannel('A').getdata()) <= {0, 255}
                    tw, th = image.size
                width, height = ((dx, dy) if direction in ('north', 'south') else
                                 (dz, dy) if direction in ('east', 'west') else (dx, dz))
                u0, v0, u1, v1 = face['uv']
                assert all(0 <= value <= 16 for value in face['uv'])
                pixels_u = abs(u1 - u0) * tw / 16
                pixels_v = abs(v1 - v0) * th / 16
                # Normalized UVs are stored to six decimals; permit only their rounding error.
                assert abs(pixels_u - width) < 0.00001, (placement, element['name'], direction, pixels_u, width)
                assert abs(pixels_v - height) < 0.00001, (placement, element['name'], direction, pixels_v, height)
                count += 1
    print(f'Validated {count} faces: one square texel per model unit, uniform across every sign placement.')


if __name__ == '__main__':
    validate()
