# Glass opacity, tint presets, and inventory preview

Branch: `glass-opacity-palette-preview`

This revision builds on the working version-4 persistence branch.

## Changes

- **Opacity:** 0–100% selector in 5% increments.
- **Tint palette:** named presets are retained internally. To avoid a runtime cast on an unsupported selector gadget, this revision temporarily uses a `Next glass color` button that cycles through Stock, Black, Charcoal, Gray, Silver, White, Red, Orange, Yellow, Green, Cyan, Blue, Purple, Pink, and Brown.
- **Persistence:** existing version-4 part payload stores alpha and RGB.
- **Inventory preview hook:** the reconstructed `InventoryItem_Part.java` calls the existing refresh method on `show()`. `InventoryPanel.focusHook()` retries refresh while a slot is focused, and `GlassOpacityTuner` keeps the temporary render type/resources alive if the engine has not created the live preview render child on the first attempt.

## Files

- `parts/scripts/bodypart/Src/GlassOpacityTuner.java`
- `parts/scripts/bodypart/Src/Window.java`
- `parts/scripts/bodypart/Src/Windshield.java`
- `sl/Scripts/game/src/InventoryPanel.java` (adds a focused-slot refresh retry)
- `sl/Scripts/game/src/InventoryItem_Part.java` (reconstructed compatibility source; **not an exact decompilation**)
- Keep the known-working version-4 `parts/scripts/src/Part.java`.

The original `InventoryItem_Part.class` uploaded to `Inventory_classes/InventoryItem_Part.class` is retained as a reference/backup. The new Java source is reconstructed, not an exact decompilation. The preview retry logic has been updated but is still not tested in-game by the author.

## Recovery from the incompatible InventoryItem_Part.java

Do not use the older public snapshot unchanged: it referenced `IIT_PART` and `type`, which are absent from this installation's API. The new candidate source removes those references, but treat it as a candidate until your game's script compiler accepts it. Keep the saved `.class` file as a backup/reference.

## Test on a backed-up game/save

1. Recompile `InventoryItem_Part.java`, `InventoryPanel.java`, and `GlassOpacityTuner.java` together so the retry hook and tuner state stay in sync. If the script compiler reports errors, stop and share the exact messages.
2. Compile the three part scripts and keep `Part.java` at version 4.
3. Load a current save and verify car purchase and part removal.
4. Change opacity and tint, save, fully exit, and reload.
5. Test `Next glass color` (each click advances one preset), then test Cancel.
6. Inspect a tinted/transparent Window and Windshield in the inventory preview, including after moving a part between the car and inventory.

## Limitations

- Not compiled/tested in-game in this authoring environment.
- The inventory preview retry is wired in but remains **unverified** until compiled and tested in the user's installation.
- Reflection strength remains separate work; `RenderRef.setColor()` changes ARGB, not material `env_factor`.

## Save/restore note

The SLRR script API in this installation does not expose `System.print(String)`. Do not add such calls for diagnostics: they compile but trigger a runtime `Thread::callMethod` error when executed. The current `GlassOpacityTuner.java` has these calls removed.
