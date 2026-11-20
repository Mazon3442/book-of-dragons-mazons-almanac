# Art and GUI notes

What we learned while planning Hiccup's Sketchbook (a Variants-only colour/gene editor for tamed dragons).
Complements `DESIGN.md` (house style and palette). Not a spec.

## What makes the Book of Dragons book look so good

Found by reading class and resource names in `bookofdragons-1.4.2-1.21.1.jar`. Book of Dragons is All Rights Reserved:
learn the techniques, never copy its textures, font or code.

- **Page turn is real 3D.** `PageTurn` draws each page spread into an off-screen `TextureTarget`, then maps that texture
  onto a bent grid of quads (constants for bend, bow, corner curl, eye distance). `MAX_SHEETS` and `SPEED_UP` make a tab
  click flip several sheets in a row, speeding up. `PageTurnGlow` adds a separate glow and ember effect.
- **Custom handwritten pixel font** (`font/hand_monogram*.json`). A big part of the field-guide feel.
- **Data-driven layout** (`book_layout.json`, `Book*` classes).
- The smooth, filtered curl against pixel-crisp art is what reads as "higher def than Minecraft".
- Its GUI PNGs were not in the jar, so I could not inspect the art itself.

## How ours should differ

- Hiccup's sketchbook: stitched leather cover, tan paper, pencil and ink sketches. Book of Dragons uses coloured
  parchment pages with tab ribbons.
- Own flip animation (not their curl), own pencil-style font, red ribbon bookmark, masking-tape frames, peeling corner.

## Pixel-art rules (Blockbench Minecraft style guide, plus our own)

- Restricted palette. Each ramp starts at the midtone, then one shadow and one highlight, then more shades.
- **Shift hue and saturation between shades**, not only brightness. Shadows cool, highlights warm.
- Light from the top left. Outlines clearly darker than the midtone.
- Hard pixel tools only. Dither sparingly and consistently. No brush noise, banding or pillow shading.
- Never mix pixel sizes in one texture. Check tiling early.
- Items stay 16x16. GUI art uses a larger canvas but the same ramp and outline rules.

## Why my scripted art looked lazy (user feedback, 2026-11-19)

Compared with the user's hand-drawn pear, the generated Dragon Root had:

1. Typed ASCII pixel grids ("paint by numbers"): blocky, symmetric silhouettes.
2. One uniform dark outline all round (sticker look). Better: **selective outline**, a darker hue-shifted tone of the
   neighbouring fill, thinner or absent on the lit side.
3. Flat bands of colour by area. Better: shade by form (light direction), with a highlight and a darker bottom edge.
4. No side-by-side check against a reference.

The user liked the small amount of **clustered noise** added in the test. Keep it, in blobs and one tone step apart.

## Better scripting method

- Silhouette comes from the user or a reference; the script does ramps, lighting, outline and cleanup.
- Build hue-shifted ramps from a base colour, shade from a light direction, dither only between adjacent tones.
- Selective outline coloured from the neighbouring pixel. Remove stray pixels and jaggies.
- Always render at 8x or more and compare next to the reference before calling it done.
- Hybrid: script drafts, user polishes key items in Aseprite.
- Prototype result: shading and ramps were better, but leaves merged into a blob and the silhouette was weak. Silhouette
  is still the part to hand-direct.
- Scripts are best for flat GUI textures (paper, leather, tabs). Hand-led for small items and creatures.

## GUI building notes (NeoForge 1.21.1)

- Sprites go in `textures/gui/sprites/` with a `.mcmeta` using `"scaling": {"type": "nine_slice", ...}` so one panel
  scales without stretching its corners. Very small nine-slice textures can lag, and huge border values can bleed into
  neighbouring atlas art.
- Most web docs now describe the 1.21.6+ GUI pipeline, which changed. We are on 1.21.1, which still uses the older
  render path (`GuiGraphics`, `BufferBuilder`, `TextureTarget`), like Book of Dragons. No tutorial found for a page-curl
  mesh; write it from the Minecraft source.
- Plan: nine-slice panels, a custom bitmap font, the Variants picker inside the sketchbook, a cheap squash-and-shadow
  flip first, the full mesh curl later (the one part that may justify a stronger model).

## 3D to pixel art in Blender (for the item model and icon only)

- Orthographic camera, flat or toon materials, no anti-aliasing. Render at the pixel grid or downscale to it, snap to
  the palette and add the outline at grid size, then scale up with nearest-neighbour.
- Plain downscaling gives a blurry photo look. A real palette snap and outline pass are what make it pixel art.
- Paid tools exist (Pixel Kit, PixlyBakery); neither was tried. Free route: the BNPR pixel-art guide.
- Poor fit for flat parchment UI.

## Mockup status

`tools/mock_sketchbook.py` renders `.scratch/sketchbook_mock.png` (256x168 native, shown at 4x). It uses the vanilla
bitmap font as a stand-in. The dragon in the frame is a placeholder for the live 3D preview and its silhouette is weak.
Layout: leather cover with stitching and brass corners, two paper pages with gutter shading, taped preview frame,
five colour rows (Base, Dorsal, Underbelly, Gradient, Body markings) with chevrons and swatches, page indicator,
peeling corner, red ribbon. Not yet built in-game.

Reference photos of the real sketchbook are in `reference/hiccups-sketchbook/` (git-ignored).

## Mockup v2 review (user feedback)

v1 was rejected: pages were flat, everything was perfectly square, and the stitching was perfect. Book of Dragons has
layered pages with depth, an irregular outline and hand-made detail. Fixes in v2 (`tools/mock_sketchbook.py`):

- **Stacked page layers**: 4 sheets per side, each staggered outwards and down, with a dark line where a sheet ends
  and a light catch-line just inside it. The edge line is what reads as depth.
- **Pages bow toward the gutter** (top and bottom edges sag) and a dark notch shows where the spine meets the cover.
- **Random-walk edges** (smoothed, persistent drift) for cover, pages and cards instead of per-pixel jitter.
- **Rounded, worn cover**: corner radius, lighter scuffs only near edges, drop shadow.
- **Paper cards** (preview frame, colour rows) with torn edges, a rim, a drop shadow and a slight rotation.
- **Irregular stitching**: random length and spacing, mixed thread tones, occasional missing stitch, uneven along a
  wobbly line. Thread must be much lighter than the leather or it vanishes.
- **Brass corners** in different sizes, one dented. Ribbon curved and frayed.
- Mistake to avoid: tone steps between layers that are too close; the first try showed no layering until the edge
  lines were added.

## Sketchbook page turn

The open-book and loose-sheet textures are the supplied 256×171 and 116×145 PNGs. The open book has transparent pixels beyond its cover. The right page uses the same sheet art and row renderer in its resting position and on the moving face. Marck Script is used for the book lettering under the included SIL Open Font License.

In the 1.21.1 GUI pipeline, a scale-aware texture target holds both faces side by side. The mesh inverts its V coordinate once when it samples the target. The turn sweeps across the spine in 24 strips, with a tighter bend and a small corner lift toward the outside of the page. The reverse carries the left page's title, dragon name and rendered portrait, matching the left page when it lands. Repeated turns launch overlapping sheets in a staggered sequence.

Only the moving mesh uses linear filtering. Rendering restores the prior draw and read framebuffers, viewport, projection, and depth and blend settings. Finishing a turn, closing the book or resizing destroys the corresponding targets. The simple flip uses the same two faces and takes over for the session if the mesh fails.

## Sources

- https://blockbench.net/wiki/guides/minecraft-style-guide/
- https://docs.neoforged.net/docs/1.21.1/resources/client/textures
- https://docs.neoforged.net/docs/gui/screens
- https://bnpr.gitbook.io/bnpr/pixel-art
