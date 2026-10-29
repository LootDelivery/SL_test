# Glass opacity slider — persistence prototype

Branch: `glass-opacity-persistence-prototype`

This experimental revision adds a versioned per-part payload for Window/Windshield opacity.

## What changed

- `Part.java`: bumps `SAVEFILEVERSION_PART` from 2 to 3 and records each loaded part's own file version in `file_ver`. This avoids using the global `save_version` after recursive child loads.
- `GlassOpacityTuner.java`: stores two integers after a Window/Windshield record: an override marker and alpha (0–255). The transient render type/resource IDs are not serialized.
- `Window.java` and `Windshield.java`: load the payload only for v3+ records, save it after the base record, and rebuild the rendering override from `install()` after the part is attached to the scene graph.
- v2 parts are expected to load without attempting to read opacity bytes.

## Test on a disposable copy/save only

1. Back up the source files and the entire test save.
2. Compile the modified `Part.java`, `GlassOpacityTuner.java`, `Window.java`, and `Windshield.java` together.
3. Load a pre-existing v2 save. Confirm it loads, and that buying a car still works.
4. Open the screwdriver menu on a windshield/window, set an unusual value (for example 40%), confirm/OK, save, exit fully, and reload.
5. Check that the same glass retains its opacity. Also check an untouched window remains at its normal appearance.
6. Save and reload again, and repeat the purchase/load checks.

## Important limitations

- This has **not been compiled or tested in-game** by the authoring environment. The first in-game test must use a disposable copy.
- The save-format bump is global: all parts written by this source tree will have record version 3, while only Window/Windshield write the extra two integers.
- If loading an old save, saving it in this branch, then reverting to unmodified v2 scripts is **not guaranteed to be compatible**. Keep backups.
- If the slider works but the saved value does not return, capture the SLRR log and tell us whether the opacity override was applied immediately after the load/install process.
