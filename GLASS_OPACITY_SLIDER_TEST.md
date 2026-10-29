# Glass opacity and tint controls — persistence prototype

Branch: `glass-opacity-tint-controls`

This revision keeps the working runtime render override and adds fine-grained tint controls.

## Features

- **Glass opacity:** 0–100%, in 5% increments (21 positions). The selected percentage is mapped to the 0–255 alpha channel.
- **Glass tint:** independent Red, Green, Blue sliders from 0 to 255. They use single-unit resolution to preserve the stock tint (22, 13, 13) exactly until the user changes it.
- **Persistence:** part-save format version 4. Version-3 Window/Windshield records still read their original two-value payload and default to stock RGB tint; v4 records also read RGB.
- **Render internals:** temporary RenderRef/resource handles are rebuilt after install and are not serialized.

## Reflection strength

No reflection-strength slider is included yet. In the renderer, the glass reflection contribution is driven by the mesh material's `env_factor`; the currently validated `RenderRef.setColor()` instance override changes colour channels, not that factor. Wiring a slider to tint or transparency would not be an independent reflection control. We need a supported way to edit or swap a per-instance material parameter first.

## Test on a disposable copy/save

1. Back up source files and the test save.
2. Compile `Part.java`, `GlassOpacityTuner.java`, `Window.java`, and `Windshield.java` together.
3. Load a current version-3 save and confirm it loads, buys cars, and retains existing alpha.
4. Tune opacity to 35%, tint to a visible colour (for example R=80, G=160, B=220), confirm the dialog, save, exit, and reload.
5. Confirm both the opacity and RGB tint survive a full restart.
6. Open the tuning dialog again and test Cancel; both opacity and tint should revert.
7. Save and reload again and verify car purchase/load.

## Compatibility warning

- This has **not** been compiled or tested in-game by the authoring environment. The first run must use a disposable copy.
- The global part save-format version is now 4. Existing version-3 saves should be read using their old two-integer Window/Windshield payload, then future saves are written as version 4.
- Do not revert to the old source tree after saving version-4 data unless you have backups.
