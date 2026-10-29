# Glass opacity slider prototype

Branch: `glass-opacity-slider-prototype`

This is an experimental source prototype for the stock screwdriver / **Fine tune specific parts** dialog. It uses the already-tested runtime render-type override from `Painter_RenderAlpha_Test.java`.

## Files

- `parts/scripts/bodypart/Src/GlassOpacityTuner.java` — common runtime override + slider logic.
- `parts/scripts/bodypart/Src/Window.java` — delegates screwdriver tuning to the helper.
- `parts/scripts/bodypart/Src/Windshield.java` — delegates screwdriver tuning to the helper.

## First test

1. Back up the game's existing `Window.java` and `Windshield.java` (and any compiled class/cache files your source setup uses).
2. Copy these three source files to the matching paths in a **test copy** of the game/source tree. Do not overwrite a more heavily modified local version without merging the added methods.
3. Launch the game and open the stock screwdriver / **Fine tune specific parts** dialog on a windshield/window part.
4. Move **Glass opacity** left and right. Lower values should make the glass more transparent; higher values should make it more opaque.
5. Test **OK** and **Cancel** separately. Cancel should restore the previous state, and cancelling the first change should restore the original render type.

## Limitations / safety

- This has **not yet been compiled or tested in-game**. Only source-level structural checks have been performed.
- The runtime recipe was validated on the user's tested build using `frontend:0x21r` (render type) and `frontend:0x5Er` (mesh). The code checks resource types but cannot inspect the render type's embedded flags through the regular Java API.
- The current code uses the tested glass diffuse RGB `(22, 13, 13)`; another car's window may use a different tint.
- The opacity value is currently **not saved to the part/car save file**. The helper intentionally does not modify the stock save format. The setting is runtime/session-only for this prototype.
- While the temporary render type is active, avoid saving from the game until the test is confirmed safe.
- If the game reports a compiler/runtime error, restore the backed-up files and share the exact error log before trying further changes.
