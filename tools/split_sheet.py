"""Cut an already-pixelated sheet with a transparent background into one PNG per drawing.

    python tools/split_sheet.py <sheet.png> <out-dir> [gap]

Pieces closer than `gap` pixels count as one drawing. Files are numbered in reading order (rows, then columns).
"""
import pathlib
import sys
from PIL import Image, ImageFilter

sheet, out = sys.argv[1], pathlib.Path(sys.argv[2])
gap = int(sys.argv[3]) if len(sys.argv) > 3 else 5
out.mkdir(parents=True, exist_ok=True)
img = Image.open(sheet).convert("RGBA")
w, h = img.size
solid = img.getchannel("A").point(lambda v: 255 if v > 0 else 0)
grown = solid.filter(ImageFilter.MaxFilter(gap | 1)).load()

seen = [[False] * w for _ in range(h)]
boxes = []
for y0 in range(h):
    for x0 in range(w):
        if grown[x0, y0] and not seen[y0][x0]:
            stack, x1, y1, x2, y2 = [(x0, y0)], x0, y0, x0, y0
            seen[y0][x0] = True
            while stack:
                x, y = stack.pop()
                x1, y1, x2, y2 = min(x1, x), min(y1, y), max(x2, x), max(y2, y)
                for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
                    if 0 <= nx < w and 0 <= ny < h and grown[nx, ny] and not seen[ny][nx]:
                        seen[ny][nx] = True
                        stack.append((nx, ny))
            boxes.append((x1, y1, x2 + 1, y2 + 1))

# a stray fleck (under 10 px square) belongs to the nearest drawing
def centre(b): return ((b[0] + b[2]) / 2, (b[1] + b[3]) / 2)
for fleck in [b for b in boxes if b[2] - b[0] < 10 and b[3] - b[1] < 10]:
    others = [b for b in boxes if b is not fleck]
    near = min(others, key=lambda b: (centre(b)[0] - centre(fleck)[0]) ** 2 + (centre(b)[1] - centre(fleck)[1]) ** 2)
    boxes.remove(fleck)
    boxes[boxes.index(near)] = (min(near[0], fleck[0]), min(near[1], fleck[1]), max(near[2], fleck[2]), max(near[3], fleck[3]))

boxes.sort(key=lambda b: ((b[1] + b[3]) // 2 // 35, b[0]))   # rows about 35 px apart, then left to right
for n, box in enumerate(boxes, 1):
    tight = img.crop(box)
    tight = tight.crop(tight.getbbox())
    tight.save(out / f"doodle_{n}.png")
    print(f"doodle_{n}.png", tight.size, "from", box)
