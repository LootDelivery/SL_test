# Native 3ds Max 2025 exporter — current prototype

This folder contains the first native x64 plug-in project for 3ds Max 2025.

## Current prototype

The native plug-in now extracts evaluated triangular meshes from 3ds Max and writes an initial SCX v4 file. It appears as **SLRR SCX v4 Exporter** with the `.scx` extension.

The implementation currently covers:
- evaluated triangle meshes and world-space transforms;
- splitting faces into separate material meshes;
- per-corner render normals (vertices are duplicated at face corners to preserve normal/UV seams);
- UV channels 1–3 when present;
- material name, diffuse/specular color, basic transparency, self-illumination color and specular strength;
- bounded SCX v4 serialization and 16-bit index validation;
- selected-node extraction through `SCENE_EXPORT_SELECTED`.

It does **not** yet export external texture indices/maps, vertex colors, skin weights, bone references, hard-surface semantics, animation, pivots or the V3 format. Material header defaults are based on the two repository fixtures, not yet proven by a controlled legacy export.

This is an experimental first geometry path, not a claim of full SLRR compatibility. Validate the generated file with the included inspector and in SLRR before using it in production.


This is intentional. We are separating SDK/toolchain/load problems from the later SCX writer implementation.

## SDK path configured

The project currently uses the SDK path confirmed by the user:

`C:\Program Files\Autodesk\3ds Max 2025 SDK\maxsdk`

The project expects these SDK locations:

- Headers: `$(MaxSDK)\include`
- x64 Release libraries: `$(MaxSDK)\lib\x64\Release`
- Libraries linked by the current prototype: `core.lib`, `geom.lib`, `maxutil.lib` and `paramblk2.lib`

The project targets x64, toolset v143, Unicode and the Windows SDK 10.0.19041.0.

## Open and build

1. Open `SCXExporterLoadTest.sln` in Visual Studio 2022.
2. In the top toolbar select **Release** and **x64**.
3. Use **Build → Build Solution**.
4. The expected output is:

   `native\bin\x64\Release\SCXExporterLoadTest.dle`

The output and intermediate folders are local build artifacts and should not be committed.

## Loading into 3ds Max

After a successful build, add the generated `bin\x64\Release` folder as an additional plug-in search path in 3ds Max, or load the `.dle` through the Plug-in Manager. Then inspect the export-format list for **SLRR SCX v4 Exporter**.

Do not replace any existing exporter or copy this test plug-in over the old binaries.

## Current format core

The project now includes a format-independent SCX scene model (`scx_scene.h`) and a first SCX v4 serializer (`scx_v4_writer.cpp` / `scx_v4_writer.h`).

The writer emits the currently understood v4 record sequence for each material mesh:

1. Material definition (type 0)
2. Hard-surface record (type 1)
3. Vertex channel block (type 4)
4. 16-bit triangle index block (type 5)

It validates finite vertex data, triangle-index ranges, per-mesh 16-bit vertex addressing and file-offset bounds before serialization. It supports positions, normals, vertex color, emissive, UV1–UV3, bump normals, material color entries and material map records.

Important limitations, explicitly retained rather than guessed:

- The material header values 0x33 and 2.0, and the hard-surface payload 0, are defaults observed in the repository's windshield fixtures. Their complete semantics are not established yet.
- The writer has not yet been checked against a controlled export from the old Invictus exporter or loaded in SLRR.
- The writer is not yet connected to Max scene extraction. `DoExport` remains a deliberate registration-test stub until the extraction pipeline is ready.
- SCX v3 uses a different sequential record layout and will get a separate serializer.

## Next milestone

Connect 3ds Max evaluated mesh data to the shared scene model, preserving material splits, face-corner normals and mapping channels. Then wire `WriteV4` into the export plug-in after validation with fixture-based round-trip tests.
