# Changelog

All notable changes to Book of Dragons: Mazon's Almanac. Newest first.

## 0.3.0

### New
- **Sketchbook:** a reusable item that opens a book on one of your own tamed dragons and edits its colors and markings.
  - Crafting: Book + Ink Sac + Feather, shapeless.
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
