# Design language: Berk field equipment

Everything this addon shows should feel like gear from Hiccup's Book of Dragons: dark wood, aged parchment,
brass fittings, and a warm ember accent. The editor is a parchment work board. Its reference is the Book of Dragons
in-game UI's warm paper, dark ink, clean pixel edges and generous text areas; it has no book spine or page spread.

## Rules

- Pixel art only. Item icons are **16x16**; model atlases retain their existing size. GUI sprites are
  native-resolution pixel art. Controls use nine-slicing; the board, paper and preview backing use artwork at
  their exact displayed dimensions so worn outlines, creases and layered edges never repeat or stretch.
- Detail lives on the frame and paper edges: wood grain, carved trim, brass corner plates, layered paper rims and
  worn corners. Reading areas have a fine, low-contrast cream fibre pattern with no dark blotches.
- Ember orange identifies slider values; muted orange, teal, green, purple and blue identify the five index tabs.
- Cut corners and projecting index tabs define the board silhouette. Keep accessories out of the controls.
- Sentence case, short labels, no punctuation on buttons.

## Pixel-art technique (apply to every new texture)

1. Every material is a **5-tone ramp**, dark to light, with the hue shifting: shadows lean cool/purple, highlights lean
   warm/yellow. Ramps live in `tools/pixelart.py` (item art) and `tools/gen_ui2.py` (UI).
2. **Light comes from the top-left**: lit edge on top/left, shaded edge on bottom/right.
3. **Outlines are a dark tone of the material**, not black, and may be open on the lit side.
4. Never use salt-and-pepper noise. Use **clustered** value noise for patches (`tiling_noise`), one tone step apart.
5. Nine-slice centres **tile**, so keep them low-contrast or the repeat shows as a grid. Use a bigger tile (64px) for
   large textured areas.
6. Add one bright hot spot or focal point per object (the ember).
7. Always **render at 8x and look**, then check in context at the real size. Fix what the render shows (this caught the
   hidden gem, the tiling grid, the stitch-through-text and the busy weave).

## Palette (sampled from the Book of Dragons book)

| Role | Hex |
|---|---|
| Outline / deep | `#25261F`, `#3B291E` |
| Cover leather | `#5A3823`, `#713F28`, `#8A5233` |
| Tan | `#7A593B`, `#AD825B`, `#C79A6C` |
| Page | `#D5B086`, `#EEC89E`, `#FAD7AF`, `#FEE3BF` |
| Ember | `#9A3A10`, `#E0731C`, `#FFB04A` |
| Brass | `#7A5320`, `#C98F34`, `#F0C060` |
| Ribbons | orange `#D8661A`, teal `#1F9E96`, green `#6FA83A`, blue `#2E7FB0` (each has a 5-tone ramp in `gen_ui2.RIB`) |

## Menu layout (DragonEditorScreen)

- Board 284x224 (`satchel` retains its resource ID), inset parchment at (10,34), 264x174.
- Top rail: brass dragon crest, editor title, selected dragon's name. Footer states that changes apply immediately.
- Five parchment index tabs down the left: Bond, Health and care, Personality, Colors and markings, Friendships.
  Etched, color-accented icons share one pixel density. The selected tab extends 6px with its category color along the lower edge.
- Controls occupy 144px on the left; the 88x104 live dragon preview sits to the right with a 12px gutter.
  Personality uses 104px controls and a 128px radar-chart region. Headings use the reference book's broken double ink rules.
- Native background sizes: board 284x224, layered sheet 264x174, dragon study 88x104. The study uses brass clips,
  small drafting marks, a folded corner and a dark offset paper backing. Keep wear out of the controls' padding.
- Wood buttons have distinct hover, focus and disabled states. Sliders use a recessed groove, ember fill and brass peg.
  Tabs and buttons support Enter/Space; sliders support arrows/Home/End; variant lists support Up/Down and Enter.
- Minimum supported scaled game area is 320x240. All content, index tabs and footer fit within this area.
- Generate with `python tools/gen_ui.py` then `python tools/gen_ui2.py`. Preview all five sections and the color picker
  with `python tools/preview_ui2.py output/dragon-editor-ui-all-sections.png`; the preview reads the Java layout constants.
- Visual intent: energy 2, rhythm 1, motion 1. Paper carries the controls, the dragon is the focal object, and wood carries wear.

## Item: Dragon Editor staff

Approved design: a hooked metal pike, dark wooden shaft and short cloth grip, with an iron butt cap. The held model
uses its existing 64x64 atlas. Its 16x16 inventory icon is authored in `tools/dragon_editor.json` and used by
`tools/gen_pike.py`. The item renders the 3D model in hand and the sprite in inventory, on the ground and in frames.

## Stable signs

- Follow the Blockbench Minecraft Style Guide: one texture pixel per model unit on every face. No mixed texel densities,
  stretched pixels, or finer pixels for the emblem, grain, rivets, or chains. Simplify detail when it does not fit.
- Both sign atlases are 64x32. The atlas stores multiple surfaces; its overall size does not determine surface pixel density.
- Standing-sign UV footprints account for the vanilla model's 2/3 scale. Keep the existing geometry and sample its final
  dimensions, rather than retaining the unscaled 24-pixel board artwork on a 16-unit board.
- Run `python tools/validate_sign_uv.py` after texture or model edits; use `tools/render_signs.py` for previews.

## Where things live

- `tools/pixelart.py`: item sprite and ramps. `tools/gen_assets.py`: staff model, texture atlas, lang.
- `tools/gen_ui.py`: buttons, sliders, rule, dots. `tools/gen_ui2.py`: satchel, sheet, card, props, ribbons, icons.
- `tools/extract_vanilla_signs.py`, `tools/extract_vanilla_leaves.py`: vanilla reference textures in `reference/` (study only, never shipped).
- `tools/preview_ui2.py`, `tools/render_item.py`: software previews (no game needed).
- Regenerate everything: `python tools/gen_assets.py && python tools/gen_ui.py && python tools/gen_ui2.py`.
