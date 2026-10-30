# Native 3ds Max 2025 exporter — first build

This folder contains the first native x64 plug-in project for 3ds Max 2025.

## What this milestone does

`SCXExporterLoadTest.dle` is a registration probe. Its only purpose is to verify that:

1. Visual Studio can compile and link against the installed 3ds Max 2025 SDK.
2. 3ds Max 2025 can load the resulting 64-bit plug-in.
3. The exporter appears in the file-format list as **SCX Exporter Load Test**.

It deliberately does **not** write an SCX file. If its `.scxprobe` format is selected and an export is attempted, it returns `IMPEXP_FAIL`.

The probe advertises support for 3ds Max's **Export Selected** option through `SceneExport::SupportsOptions`, so it should appear in both the regular Export and Export Selected format lists. The selected-only scene filtering is not exercised yet because this milestone does not serialize scene data.

This is intentional. We are separating SDK/toolchain/load problems from the later SCX writer implementation.

## SDK path configured

The project currently uses the SDK path confirmed by the user:

`C:\Program Files\Autodesk\3ds Max 2025 SDK\maxsdk`

The project expects these SDK locations:

- Headers: `$(MaxSDK)\include`
- x64 Release libraries: `$(MaxSDK)\lib\x64\Release`
- Libraries linked by this minimal probe: `core.lib`, `maxutil.lib` and `paramblk2.lib`

The project targets x64, toolset v143, Unicode and the Windows SDK 10.0.19041.0.

## Open and build

1. Open `SCXExporterLoadTest.sln` in Visual Studio 2022.
2. In the top toolbar select **Release** and **x64**.
3. Use **Build → Build Solution**.
4. The expected output is:

   `native\bin\x64\Release\SCXExporterLoadTest.dle`

The output and intermediate folders are local build artifacts and should not be committed.

## Loading into 3ds Max

After a successful build, add the generated `bin\x64\Release` folder as an additional plug-in search path in 3ds Max, or load the `.dle` through the Plug-in Manager. Then inspect the export-format list for **SCX Exporter Load Test**.

Do not replace any existing exporter or copy this test plug-in over the old binaries.

## Next milestone

After the test exporter loads, we will replace the probe implementation with shared scene extraction and then build the SCX v3/v4 serializers. This test project is not the final exporter.
