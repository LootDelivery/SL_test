# Glass opacity, tint presets, and inventory preview

Working snapshot: `stable_2_w_debug/` on `main`.

## Features

- **Opacity:** 0–100% selector in 5% increments.
- **Glass color:** named `MultiChoice` selector with `Original tint`, Stock, Black, Charcoal, Gray, Silver, White, Red, Orange, Yellow, Green, Cyan, Blue, Purple, Pink, and Brown.
- **Original tint:** restores the tint that was active when the tuning menu opened.
- **Persistence:** the existing version-4 part payload stores alpha and RGB tint; no additional save-format bump is used.
- **Inventory preview:** `Part.refreshGlassOpacityPreview()` dispatches to the Window/Windshield implementations. The inventory refresh hook retries visible previews so the saved appearance can be applied when the render child becomes available.

## Snapshot files

- `stable_2_w_debug/GlassOpacityTuner.java`
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
4. Open the glass tuning menu. Confirm that Glass color shows a named selector rather than a cycling button, then choose several colors and verify the rendered tint.
5. Choose **Original tint** after changing a color and verify that it restores the tint that was active when the menu opened.
6. Change opacity and color, confirm the tuning session, save, exit the game completely, and reload.
7. Check Window and Windshield both on the car and in the inventory preview. Move a tuned part between the car and inventory and verify that its appearance remains consistent.
8. Cancel a tuning session after changing the color and opacity; verify that both revert to the values from before the session.

## Limitations

- The dropdown change and debug cleanup have not been compiled or exercised in the SLRR runtime from this editing environment. Test on a backed-up game/save before relying on the result.
- Reflection strength remains separate work. The currently used `RenderRef.setColor()` path controls ARGB, not the material's `env_factor`.
