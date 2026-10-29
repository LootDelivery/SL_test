# Glass opacity, tint presets, and inventory preview

Branch: `glass-opacity-palette-preview`

This revision builds on the working version-4 persistence branch.

## Changes

- **Opacity:** 0–100% selector in 5% increments.
- **Tint palette:** named presets are retained internally. To avoid a runtime cast on an unsupported selector gadget, this revision temporarily uses a `Next glass color` button that cycles through Stock, Black, Charcoal, Gray, Silver, White, Red, Orange, Yellow, Green, Cyan, Blue, Purple, Pink, and Brown.
- **Persistence:** existing version-4 part payload stores alpha and RGB.
- **Inventory preview hook:** `sl/Scripts/game/src/InventoryItem_Part.java` has been added as a reconstructed compatibility source. It removes the unsupported `IIT_PART`/`type` references and calls the existing preview refresh method for `Window` and `Windshield` from `show()`.

## Files

- `parts/scripts/bodypart/Src/GlassOpacityTuner.java`
- `parts/scripts/bodypart/Src/Window.java`
- `parts/scripts/bodypart/Src/Windshield.java`
- `sl/Scripts/game/src/InventoryPanel.java` (existing source; used to confirm the preview lifecycle)
- `sl/Scripts/game/src/InventoryItem_Part.java` (reconstructed compatibility source; **not an exact decompilation**)
- Keep the known-working version-4 `parts/scripts/src/Part.java`.

The original `InventoryItem_Part.class` uploaded to `Inventory_classes/InventoryItem_Part.class` is retained as a reference/backup. The new Java source is based on the available implementation and cross-checked against the method surface visible in that class; it has not been compiled or tested in-game here.

## Recovery from the incompatible InventoryItem_Part.java

Do not use the older public snapshot unchanged: it referenced `IIT_PART` and `type`, which are absent from this installation's API. The new candidate source removes those references, but treat it as a candidate until your game's script compiler accepts it. Keep the saved `.class` file as a backup/reference.

## Test on a backed-up game/save

1. Compile `InventoryItem_Part.java` together with the other scripts. If the script compiler reports errors, stop and share the exact messages before replacing more files.
2. Compile the three part scripts and keep `Part.java` at version 4.
3. Load a current save and verify car purchase and part removal.
4. Change opacity and tint, save, fully exit, and reload.
5. Test `Next glass color` (each click advances one preset), then test Cancel.
6. Inspect a tinted/transparent Window and Windshield in the inventory preview, including after moving a part between the car and inventory.

## Limitations

- Not compiled/tested in-game in this authoring environment.
- The inventory preview callback is now wired into the candidate source but remains **unverified** until compiled and tested in the user's installation.
- Reflection strength remains separate work; `RenderRef.setColor()` changes ARGB, not material `env_factor`.
