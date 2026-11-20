# Plan: Hiccup's Sketchbook (implementation brief)

Hand-off brief for the agent implementing this. Read it fully, then read `DESIGN.md` and `ART-NOTES.md`.
Project: NeoForge 21.1, Minecraft 1.21.1, Java 21, mod id `bodaddon`, package `local.bodaddon`.

## Goal

A survival-craftable, reusable item, **Hiccup's Sketchbook**. Right-click one of your own tamed dragons and a
sketchbook GUI opens. It edits **only the dragon's colours and markings** (the Variants data), never bond, vitals,
personality or friendships. Pages turn with a real 3D page-flip animation.

The existing creative-only Dragon Editor already has a Variants tab. The sketchbook reuses that logic with a new look
and a stricter, survival-safe permission model. Do not copy-paste the Variants code. Extract it and share it.

## Defaults (the user has not decided these; implement, keep them easy to change, list them in your report)

- Reusable, not consumed.
- Works on tamed dragons the player owns, within reach. Not on wild dragons.
- No unlock gating in this first pass: every colour and pattern the species' pool offers is available (that is what
  `VariantEdits` already does). Planned follow-up, not part of this task: lock rare colours and markings behind the
  dragon's loyalty (bond). Keep the option list and the server check in one place so that gate is easy to add later.
- Recipe is a placeholder: shapeless Book + Ink Sac + Feather.

## Art you are given

In `reference/hiccups-sketchbook/art/` (git-ignored folder; copy what you use into the resources):

- `book_open.png`: 256x171, the open book. Layered page stacks, stitched spine, ribbon at the bottom, torn cover edges.
  The left page contains an inset paper card where the live dragon preview goes. The right page is blank.
- `page_blank.png`: 116x145, one blank page with torn edges and a curled top-right corner. This is the **flipping sheet**.

Copy them to `src/main/resources/assets/bodaddon/textures/gui/sketchbook/` as `book_open.png` and `page_blank.png`.
Measure all coordinates (page areas, the preview card, where the spine runs) from the actual pixels. Do not guess them.
Do not redraw or recolour the art. If something needed is missing (item icon, a page back, tape strips, a taped slip for
the option picker), draw a placeholder in the same ramp and palette from `DESIGN.md` and flag it in your report as
placeholder art.

Book of Dragons is All Rights Reserved. Learn from the idea of its page turn; never copy its code, textures or font.

## Where the existing code lives

- `editor/DragonEditorItem`: the creative-only wand, a good model for the new item's `interactLivingEntity`.
- `editor/EditorPayloads`: `Open` (server to client) and `Act` (client to server) payloads, version `"1"`.
- `editor/DragonEdits.handle`: server gate. Today it returns early unless `player.isCreative()`, then checks reach
  (`REACH_SQ`) and dispatches on `EditAction`.
- `editor/VariantEdits`: `rows(dragon)` builds the rows, `set(dragon, row, option)` applies one. This is the logic to share.
- `editor/EditorState` / `VariantRow`: what the client receives.
- `client/DragonEditorScreen` (~1000 lines): `Tab.VARIANTS`, `variantPage`, `ROWS_PER_PAGE`, `pickerRow`, `LiveSlots`
  for the preview. `client/EditorClient.show` opens it.
- `ModItems`, `lang/`, `ConfigClient`, `src/gametest/` (see how the existing game tests run in `build.gradle`).

## Work, in order

### 1. Item and permissions (server)

1. Register `hiccups_sketchbook` in `ModItems` (`stacksTo(1)`), class `editor/SketchbookItem`. Add a flat handheld
   item model and a lang entry, and a recipe JSON under `data/bodaddon/recipe/`.
2. `interactLivingEntity`: only `DragonBase` targets; client side returns `SUCCESS`; server side requires
   `dragon.isTame()` and that the player is the owner (use the owner API the existing code uses, `dragon.ownerId()`);
   on failure show an action-bar message (add lang keys) and return `FAIL`. Then send the open payload.
3. The server must not trust the client. Add a field to `EditorPayloads.Act` or a separate payload so the server knows
   the request came from the sketchbook. Then in `DragonEdits.handle`: if the player is **not** creative, allow
   **only** `SET_VARIANT`, and only if (a) the player holds a sketchbook in either hand, (b) the dragon is tame and
   owned by the player, (c) it is within `REACH_SQ`. Everything else stays creative-only exactly as today. If the
   payload `VERSION` has to change, change it in one place.
4. Do not weaken the creative path. The Dragon Editor must behave exactly as before.

### 2. Share the Variants logic (client)

Extract the Variants row list, paging, picker state and live-preview handling out of `DragonEditorScreen` into a
reusable class (for example `client/VariantsPanel`). `DragonEditorScreen` keeps working through it with no visible
change. The sketchbook screen uses the same panel with a different skin. Aim to shrink `DragonEditorScreen`, not grow it.

### 3. Sketchbook screen (no flip yet)

New `client/SketchbookScreen`, opened from a new method on `EditorClient`. Reuse `LiveSlots` for the 3D dragon preview.

- Draw `book_open.png` at an integer scale, centred. It must fit the minimum supported area of 320x240 (see `DESIGN.md`).
- Leaves: leaf 0 = the **portrait page** (left page: the dragon in the inset card, its name, a short "current coat"
  summary from the rows' current values). Following leaves hold the variant rows, `ROWS_PER_PAGE` per leaf.
  A spread shows leaves (2i, 2i+1). The first spread is the portrait leaf and the first rows leaf.
- Each row: label, current option name, previous/next chevrons, a colour swatch for colour rows. Clicking the row opens
  the existing option picker, drawn as a taped paper slip over the page.
- Page change is by clickable page corners (the curled corner in the art is the affordance) and by the mouse wheel and
  Left/Right keys. Selecting an option sends `SET_VARIANT` through the existing `Act` payload and the preview updates
  live, exactly as in the editor.
- Keyboard: Tab or arrows between rows, Enter/Space to open, Esc to close. No hand-drawn font exists yet; use the
  vanilla font and the ink colours from `DESIGN.md`, behind one constant so a pencil font can be swapped in later.
- Pixel-crisp art: nearest-neighbour sampling, integer scale, no blur.

### 4. Page flip, tier 1 (simple, always available)

A cheap flip used as the fallback and as the first shippable step:

- The flipping sheet is `page_blank.png` plus the page's rendered content.
- The sheet's width squashes from full to zero around the spine, swaps to the other side, and expands, over about
  0.45 s with ease in-out. Draw a soft shadow on the page underneath that grows and fades as the sheet lifts.
- Input during a flip is queued (at most a few), not dropped, and a flip started during a flip speeds up the animation
  (the same idea Book of Dragons uses: several sheets in a row, accelerating).

### 5. Page flip, tier 2 (the real 3D curl)

Behind a client config option `pageFlip = MESH | SIMPLE | OFF` (add it to `ConfigClient`, default `MESH`). Any failure
in the mesh path (exception, no framebuffer) must drop to `SIMPLE` for the session without crashing.

How it works (Minecraft 1.21.1 still uses the older `GuiGraphics` / `BufferBuilder` / `RenderTarget` path; most web
docs describe 1.21.6+ and are wrong for this version, so verify against the 1.21.1 sources):

1. Render the sheet's content (the page art plus its rows, text and swatches) into an off-screen `TextureTarget` sized
   to the page at a multiple of the GUI scale. Bind it, set an orthographic projection for its size, draw the page with
   a `GuiGraphics`, flush, then restore the main target, viewport, projection and any blend or depth state you touched.
2. Build a grid of quads (for example 24 columns by 2 rows) covering the sheet. For turn progress `t` in [0,1], roll the
   sheet around a cylinder whose axis is parallel to the spine and moves from the spine outward. Vertices before the
   roll line stay flat. Vertices past it wrap around a small radius, so the sheet bends and lifts. Add a slight corner
   lift and a small bow so the curl is not perfectly cylindrical.
3. Project with a mild perspective (a small eye distance) so the lifted paper gets bigger and smaller as it turns.
   Shade per vertex from the surface normal: darker at the crease, lighter at the crest, plus a soft contact shadow
   on the page underneath.
4. When the sheet passes vertical it shows its back. Use a back texture (mirror `page_blank.png`, slightly darker and
   cooler) and the next leaf's content on it. Cull nothing wrongly: draw back faces with the back texture.
5. Sample the off-screen texture with linear filtering for the curled sheet only. Static pages stay nearest-neighbour.
   The smooth curve against crisp pixel art is what makes this read as higher definition than normal Minecraft.
6. Render targets have an upside-down V. Handle that once, in one place.
7. Own the lifecycle: create the `TextureTarget` lazily, destroy it (`destroyBuffers`) in `removed()`, and recreate it on
   resize. Leaking one per open screen is a bug.
8. Optional, later: ember or dust specks as a separate light pass. Not required.

### 6. Text, polish, docs

- Lang keys for every string. Sentence case, short labels, no button punctuation.
- Add the item to `README.md` and `docs/curseforge-description.html` (item table, crafting table, a short section).
- Bump `mod_version` in `gradle.properties` to `0.3.0`.
- Update `ART-NOTES.md` with what you learned about the real page-flip implementation.
- Do not commit or push. The user reviews first.

## Tests and verification

- Game tests in `src/gametest/` following the existing ones: a non-creative owner holding the sketchbook can
  `SET_VARIANT` on their tamed dragon; the same player without the item cannot; a non-owner cannot; a wild dragon cannot;
  a non-creative player cannot send any action other than `SET_VARIANT`; creative editor behaviour is unchanged.
- Build with `./gradlew build` and run the project's game tests the way `build.gradle` defines them.
- Launch the client and look at it. Screenshot at GUI scales 1 to 4 and at the 320x240 minimum. Check: art is crisp,
  nothing is clipped, the flip is smooth, three quick page clicks produce an accelerating chain, closing the screen
  mid-flip does not leave the main framebuffer bound or the viewport changed, and reopening works.
- Check that `pageFlip = SIMPLE` and `OFF` both work, and that the mod still loads without Spatial GUI installed.
- Dragon Studio 1.0.7 is known to crash the client with Fauna Engine 1.3. Do not test with it installed.

## Definition of done

1. Craftable item that opens the sketchbook on an owned, tame dragon and changes only its colours and markings.
2. Server rejects every other action and every forged request from a non-creative player.
3. Variants logic is shared with the Dragon Editor, not duplicated.
4. MESH flip works with a clean fallback to SIMPLE, and no GL state or memory leaks.
5. Tests pass; docs and version updated.
6. Final report lists: what changed, what you could not verify, every placeholder asset, and the defaults above.

## Open questions for the user (do not block on these)

- Reusable or consumed on use? Tamed-only or also wild?
- Loyalty thresholds for each rarity (the lock itself is decided: rare colours need loyalty, as a follow-up).
- Final recipe and icon.
- A pencil-style bitmap font for the page text.
- A better hand-drawn dragon sketch for the portrait page (currently the live 3D preview fills the card).
