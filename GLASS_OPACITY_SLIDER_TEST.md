# Glass opacity, tint presets, and inventory preview

Branch: `glass-opacity-palette-preview`

This revision builds on the working version-4 persistence branch.

## Changes

- **Opacity:** 0–100% selector in 5% increments.
- **Tint palette:** named presets are retained internally. To avoid a runtime cast on an unsupported selector gadget, this revision temporarily uses a `Next glass color` button that cycles through Stock, Black, Charcoal, Gray, Silver, White, Red, Orange, Yellow, Green, Cyan, Blue, Purple, Pink, and Brown.
- **Persistence:** existing version-4 part payload stores alpha and RGB.
- **Inventory preview:** helper methods exist on Window/Windshield, but no InventoryItem_Part replacement is included. The public source snapshot was incompatible with the user's installed version.

## Files to update

- `parts/scripts/bodypart/Src/GlassOpacityTuner.java`
- `parts/scripts/bodypart/Src/Window.java`
- `parts/scripts/bodypart/Src/Windshield.java`
- Keep the known-working version-4 `parts/scripts/src/Part.java`.

## Recovery from the incompatible InventoryItem_Part.java

If you copied the `InventoryItem_Part.java` from the previous version of this branch, restore your own game's original file from a backup or reinstall/restore the matching file from your exact game/source distribution. Do not use the public snapshot from this branch: it referenced `IIT_PART` and `type` fields absent from the user's installed API.

Do not replace the entire installed InventoryItem_Part.java with a different game's source. Inventory-preview support must be integrated as a minimal patch into the exact local version after comparing its imports, `show()` method, and part fields.

## Test on a backed-up game/save

1. Restore the matching local `InventoryItem_Part.java` before testing; compile it with the rest of the scripts.
2. Compile the three part scripts and keep Part.java at version 4.
3. Load a current save, verify car purchase and part removal.
4. Change opacity and tint, save, fully exit, and reload.
5. Test `Next glass color` (each click advances one preset), then test Cancel.

## Limitations

- Not compiled/tested in-game in this authoring environment.
- Inventory preview remains **unfixed** until the callback is integrated into the exact local `InventoryItem_Part.java` and tested. Do not treat the preview hook as complete.
- Reflection strength remains separate work; `RenderRef.setColor()` changes ARGB, not material `env_factor`.
