# Texturing a model: block, blend, detail

For stable signs, prioritize the Blockbench Minecraft Style Guide's pixel density over detail: one image pixel covers one
model unit in both face directions. Atlas size alone cannot prove this. The 64x32 sign atlases store multiple UV islands;
the standing board must sample 16x8 pixels for its final 16x8-unit face. Do not upscale the atlas to fit a finer emblem.
Use a simpler emblem instead. Validate all faces with `python tools/validate_sign_uv.py`.

Work in three passes, in this order. Do not jump to details early: details placed on an unblended base look pasted on, and
fixing the base later means redoing them.

## 0. Study a vanilla reference first

Open the closest vanilla texture and read it before choosing colors. Stripped dark oak, for example, uses only nine colors
across a very narrow brightness range, in vertical streaks 1-3 px wide, each broken into short segments one step lighter or
darker, with thin dark lines where streaks meet. Match that restraint: a narrow, low-contrast ramp, with the strongest
contrast saved for the details. (Take the palette and the structure, not the pixels.)

## 1. Color blocking

Pick the few base colors of each material and fill every surface flat with them.

- One ramp per material (the trough uses a five-tone walnut ramp and a five-tone iron ramp). Cool, dark shadows and warm lights.
- Give each surface its role: lit top edge, mid body, shaded underside, dark seam. Light comes from the top left.
- No grain, no dots, no noise yet. If it reads as the right object in flat colors at a glance, the blocking is done.

## 2. Blending

Blend each material family into itself, and only into itself.

- Browns blend into browns: feather the lit edge into the body and the body into the shade by dithering neighbor tones across the
  boundary, then add long grain runs that use only the tones next to the base tone (never a tone two steps away).
- Metals blend into metals: strap rows step down the iron ramp, with dithering at each step.
- Keep calm areas calm. Long, one-pixel streaks (as in a stripped log) read better than scattered noise.
- Do not add any new feature here. Blending changes how colors meet, not what is on the surface.

## 3. Details

Last, go back over the blended base and add the specific features:

- Carvings and emblems (the crest on the trough's side), with a lit lip under each cut so it reads as carved in.
- Metal fittings: nail heads with a shadow pixel, rivets, rings, strap edges.
- Knots, hairline cracks, scuffs on the lit edges, chipped corners.

Keep details few and deliberate. Each one should sit on a quiet area so it reads.

## Check

Render the model at in-game size and at close range after each pass. If a pass makes the 16 px icon harder to read, undo it.

## Example: the feeding trough

`tools/gen_trough.py` follows these passes in each texture function: `board()` does the blocking and blending for a plank,
`planks()` then adds knots, scuffs and nails; `corner()` blocks and blends an iron strap on a post and then adds rivets;
`emblem()` builds a clean board and carves the crest last.
