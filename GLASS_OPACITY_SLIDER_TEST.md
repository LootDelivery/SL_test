# Glass opacity, tint presets, and inventory preview

Working snapshot: `stable_2_w_debug/` on `main`.

## Features

- **Opacity:** 0–100% selector in 5% increments.
- **Glass color:** the SLRR `MultiChoice` control cycles through named color options when clicked; it is not a desktop-style dropdown.
- **Tint presets:** Stock, Black, Charcoal, Gray, Silver, White, Red, Orange, Yellow, Green, Cyan, Blue, Purple, Pink, and Brown.
- **Brown wraparound:** after Brown, the next choice is Stock. The selector has no special “Original tint” entry, which previously caused the color to jump back to the saved Brown tint after a restart.
- **Persistence:** the existing version-4 part payload stores alpha and RGB tint; no additional save-format bump is used.
- **Inventory preview:** `Part.refreshGlassOpacityPreview()` dispatches to the Window/Windshield implementations. The inventory refresh hook retries visible previews so the saved appearance can be applied when the render child becomes available.
- **Diagnostics:** temporary glass/inventory SDAT trace writers and load markers have been removed.

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
4. Open the glass tuning menu. Verify that clicking the color item cycles through all 15 named presets and that Brown wraps to Stock.
5. Select Brown, confirm the tuning session, save, completely exit the game, and reload. Reopen the menu and verify the next click changes to Stock, then continue cycling through other colors.
6. Change opacity and color, save, exit, and reload. Check Window and Windshield both on the car and in inventory preview.
7. Cancel a tuning session after changing the color and opacity; verify that both revert to the values from before the session.

## Limitations

- The updated tint cycling has not been compiled or exercised in the SLRR runtime from this editing environment. Test on a backed-up game/save before relying on the result.
- Reflection strength remains separate work. The currently used `RenderRef.setColor()` path controls ARGB, not the material's `env_factor`.
