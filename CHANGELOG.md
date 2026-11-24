# Changelog

All notable changes to Book of Dragons: Mazon's Almanac. Newest first.

## 0.4.0

### New
- **Sketchbook bond:** the book now shows the dragon's bond and what it needs for the next unlock, for example "Bond 42 / 50 for rare".
- **Sketchbook Page:** press Save in the Sketchbook to get a page holding the dragon's colors and markings. Press Paste in the book of another dragon of the same kind to apply a page you're carrying. Looks that dragon hasn't earned the bond for are skipped, and the page isn't used up.
- **Undo:** puts the dragon back to how it looked when you opened the book.
- **Status:** sneak and right-click a Feeding Trough or a Stable Sign with an empty hand to see who is in reach, who is hungry, how full the trough is, and how the stable is holding its dragons.

### Changed
- The Sketchbook network protocol changed, so the client and server both need 0.4.0.

## 0.3.3

### New
- **Dragon Nip Grass:** Bone Meal now spreads it to nearby open ground.
- **Tooltips:** Dragon Root Seeds, Eel, Cooked Eel and the Sketchbook now say where they come from and what they do, so the hint shows in JEI, EMI and REI too.

### Fixed
- **Spatial GUI:** the flight picking fix works again on Spatial GUI 1.12, which renamed two methods the Almanac hooks. It still works on earlier 1.x versions.

## 0.3.1

### Changed
- **Sage trees:** they now generate about 1 in 40 chunks, up from 1 in 300, so Sagefruit is easier to find.

## 0.3.0

### New
- **Sketchbook:** a reusable item that opens a book on one of your own tamed dragons and edits its colors and markings.
  - Crafting: Book + Ink Sac + Feather + any dragon scale, shapeless.
  - The dragon sits in a page-turning book with a real page-curl animation. Page corners, the arrow keys and scrolling all turn pages.
  - Italic pixel font, larger centered titles, and paint dabs tinted from the dragon's own palette.
  - A flat icon in the inventory and a 3D leather book in your hand, on the ground and in item frames.
  - What it offers depends on bond. Common looks are always available, and uncommon, rare and very rare unlock at bond 25, 50 and 90 out of 100. Locked options show a padlock and the bond they need, and the server enforces them. The thresholds are in the config.
  - Page flip setting in the client config: `MESH`, `SIMPLE` or `OFF`.
- **Cooked Eel:** cook an Eel in a furnace, smoker or campfire. It restores as much as cooked cod.
- **Dragon Root seeds:** the root now grows in stages from seeds. It grows in regular dirt and grows faster on farmland.

### Changed
- **Dragon Nip Grass:** break it with any tool, or your bare hand, for 1 to 2 Dragon Nip. It used to need a sword. Shears still give you the grass to replant.
- **Chest feeding:** a tamed dragon with a chest now starts eating when its food drops to 7 and keeps eating until it is full, instead of one item at a time. The start level is a config option.
- **Dragon Editor:** its Variants tab now shares code with the Sketchbook. It looks and works as before.

### Eels
- Killing an eel now drops an Eel.
- All dragons, wild or tamed, are scared of live eels and back away from them.
- Dragons are disgusted by eel items and buckets, whether lying on the ground, held or offered. They refuse them and never eat them from a chest.

### Config
- New "Sketchbook" bond thresholds.
- New "Dragon feeding" start level.

## 0.2.2

### Fixed
- **Spatial GUI:** menu clicks no longer land in the wrong place while riding a flying dragon, when Spatial GUI 1.x is installed. Client-side only, and it does nothing without Spatial GUI.
- **Stable Plate:** a held dragon now rests when it is tired, using the dragon's real fatigue.

## 0.1.0

First release: Dragon Nip, Dragon Poultice, Dragon Treat, Dragon Repellent and its block, Dragon Root, Sagefruit and Sage trees, Eels, the Feeding Trough, the Stable Plate and the Dragon Editor.
