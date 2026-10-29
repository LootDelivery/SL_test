# Glass opacity, tint presets, and inventory preview

Working snapshot: `stable_2_w_debug/` on `main`.

## Features

- **Opacity:** 0–100% selector in 5% increments.
- **Glass color:** a separate compact modal list shows the 15 named presets. The tuning row displays the active preset name; selecting a color applies it immediately and closes the list. The picker is a modeless high-priority overlay with 8 + 7 choices in two columns. It uses `show()` instead of blocking `display()`, so the main tuning dialog can still receive OK/CANCEL. The class is named `GlassTintPresetDialog` to avoid stale/package-local `GlassTintDialog` class collisions.
- **Tint presets:** Stock, Black, Charcoal, Gray, Silver, White, Red, Orange, Yellow, Green, Cyan, Blue, Purple, Pink, and Brown.
- **Selection:** the list marks the current preset with `*`. There is no cycling or wraparound behavior; each click selects the named color directly.
- **Persistence:** the existing version-4 part payload stores alpha and RGB tint; no additional save-format bump is used.
- **Inventory preview:** `Part.refreshGlassOpacityPreview()` dispatches to the Window/Windshield implementations. The inventory refresh hook retries visible previews so the saved appearance can be applied when the render child becomes available.
- **Diagnostics:** temporary glass/inventory SDAT trace writers and load markers have been removed.

## Snapshot files

- `stable_2_w_debug/GlassOpacityTuner.java`
- `stable_2_w_debug/GlassTintPresetDialog.java` (copy to `sl/Scripts/game/src/GlassTintPresetDialog.java`, where it matches package `java.game`; compile it with the game scripts before compiling `GlassOpacityTuner.java` in `parts/scripts/bodypart/Src/`)
- `stable_2_w_debug/Window.java`
- `stable_2_w_debug/Windshield.java`
- `stable_2_w_debug/Part.java`
- `stable_2_w_debug/InventoryItem_Part.java`
- `stable_2_w_debug/InventoryPanel.java`
- `stable_2_w_debug/VisualInventory.java`

## Test on a backed-up game/save

1. Back up the game files and save.
2. Copy the snapshot sources to their corresponding SLRR source locations and recompile the modified classes together.
3. Load a current save; check car purchase, loading, and part removal.
4. Open the glass tuning menu. Verify the color row shows the current preset name and opens a transparent two-column list above the tuning window, with all 15 choices visible and no additional dimming/background frame.
5. Select Brown and verify that the tint applies immediately and the list closes. Reopen the list and confirm Brown is marked as the active preset.
6. Change opacity and color, save, exit, and reload. Check Window and Windshield both on the car and in inventory preview.
7. Cancel a tuning session after changing the color and opacity; verify that both revert to the values from before the session.

## Limitations

- The popup source belongs in `sl/Scripts/game/src/` because it declares package `java.game` and accesses package-scoped `Player.controller`. It must be compiled as a game script before the bodypart tuner that references it. This source placement has not been compiled or exercised in the SLRR runtime from this editing environment. Test on a backed-up game/save before relying on the result.
- Reflection strength remains separate work. The currently used `RenderRef.setColor()` path controls ARGB, not the material's `env_factor`.
