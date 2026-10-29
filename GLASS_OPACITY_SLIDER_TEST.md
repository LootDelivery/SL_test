# Glass opacity, tint presets, and inventory preview

Branch: `glass-opacity-palette-preview`

This revision builds on the working version-4 persistence branch.

## Changes

- **Opacity:** remains a 0–100% selector in 5% increments.
- **Tint:** replaces the three RGB sliders with a named preset selector. Its event handler reads the selected index from `m.gadget.osd.sliderValue`; the list item is a `Gadget`, not a `Slider`, so no `Slider` cast is used: Custom/current, Stock, Black, Charcoal, Gray, Silver, White, Red, Orange, Yellow, Green, Cyan, Blue, Purple, Pink, and Brown.
- **Persistence:** the RGB triplet is still written using the existing version-4 part payload. No further save-format bump is required.
- **Inventory preview:** adds a refresh callback to `Window` and `Windshield`, and calls it from `InventoryItem_Part.show()` after the inventory preview camera and light are created. This reapplies the saved alpha/RGB to the preview's current live render instance.

## Files

- `parts/scripts/bodypart/Src/GlassOpacityTuner.java`
- `parts/scripts/bodypart/Src/Window.java`
- `parts/scripts/bodypart/Src/Windshield.java`
- `parts/scripts/src/Part.java` (same version-4 file as the previously working branch; no further change intended)
- `sl/Scripts/game/src/InventoryItem_Part.java` (inventory preview hook; based on the public stock script snapshot)

## Test on a backed-up game/save

1. Copy the updated `GlassOpacityTuner.java`, `Window.java`, and `Windshield.java`.
2. Merge the import and two refresh calls from the supplied `InventoryItem_Part.java` into your installed version. If your local file differs from the supplied snapshot, do not replace your whole file blindly.
3. Do **not** downgrade `Part.java`: keep the version-4 save format from the working tint-controls branch.
4. Load a current save, check purchase/load, set tint and opacity, save, exit, and reload.
5. Put a tuned windshield/window into the parts inventory and inspect it before installing it on a car. Then install it and verify its appearance remains the same.
6. Test Cancel in the tuning menu.

## Limitations

- This has not been compiled or tested in-game by the authoring environment; the first run must use a backup.
- The inventory hook runs after the preview camera/light is created. If a particular build creates the render instance later than this, the log may report that the preview instance was not found; the hook timing will then need adjusting against that build.
- Reflection strength is still not independently adjustable. The tested `RenderRef.setColor()` path controls ARGB tint/alpha, not the material's `env_factor`.
