# Glass opacity slider prototype

Branch: `glass-opacity-slider-prototype`

This is an experimental source prototype for the stock screwdriver / **Fine tune specific parts** dialog. It uses the already-tested runtime render-type override from `Painter_RenderAlpha_Test.java`.

## Files

- `parts/scripts/bodypart/Src/GlassOpacityTuner.java` — common runtime override + slider logic.
- `parts/scripts/bodypart/Src/Window.java` — delegates screwdriver tuning to the helper.
- `parts/scripts/bodypart/Src/Windshield.java` — delegates screwdriver tuning to the helper.

## Saving the opacity

The opacity value is now serialized as part-specific data, and re-applied when that part loads. To keep existing saves compatible, the shared part format version must be bumped once:

- For the current source version that has `SAVEFILEVERSION_PART = 3`, apply [PART_SAVE_VERSION_4.patch](PART_SAVE_VERSION_4.patch) to `parts/scripts/src/Part.java` so the constant becomes `4`.
- The `Window.java` and `Windshield.java` changes read the extra field only when `file_ver >= 4`; older version-3 saves will load with factory glass settings. New saves store whether an opacity override exists, and the alpha value.
- The temporary render-type ID is **not** saved; load recreates the private render type and applies the saved alpha. If re-application fails, a message is written to the game's script log.
- The patch assumes the current Part.java source has an instance field `file_ver` and version 3 format. The `parts/scripts/src/Part.java` file already present in this test branch is an older v2 snapshot; **do not copy that file into the game**. Apply the one-line patch to the `Part.java` you currently compile against only if it has the shown version-3 line. If it does not, stop and compare versions before compiling.

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
- This prototype stores the override flag and alpha value at the end of each Window/Windshield part record, using part-save format version 4.
- The new code is designed to save while the temporary render type is active: the base save records the original mesh ID, while the per-part payload stores only the override flag and alpha. Still test this first on a disposable save before using a valuable career save.
- If the game reports a compiler/runtime error, restore the backed-up files and share the exact error log before trying further changes.
