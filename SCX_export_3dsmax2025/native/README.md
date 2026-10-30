# Native 3ds Max 2025 SCX exporter prototype

## Current state

The native x64 plug-in has moved beyond the loader test. It now contains an experimental path from the evaluated 3ds Max scene to an SCX v4 file.

It is exposed in the 3ds Max Export and Export Selected workflows as **SLRR SCX v4 Exporter** (.scx).

### Implemented in this prototype

- Reads evaluated scene objects that can be converted to triangular meshes.
- Bakes object transforms into exported positions.
- Converts Max Z-up coordinates to the SCX coordinate basis.
- Writes positions in centimetres; the known independent reader converts these to metres by dividing by 100.
- Splits faces by material ID and creates separate SCX mesh records.
- Duplicates vertices per face corner to preserve UV seams.
- Reads UV mapping channels 1–3 when map-face and map-vertex arrays are present.
- Currently emits flat geometric face normals as a temporary crash-isolation measure; smoothing-group normals are not yet enabled.
- Reads basic Standard Material data: name, diffuse/specular colors, transparency, self-illumination color and specular strength.
- Extracts direct BitmapTex maps from Standard Material Ambient, Diffuse, Bump, Reflection and Self-Illumination slots.
- Deduplicates bitmap paths in first-use order and writes their indices into the SCX material map entries.
- Writes a matching `.tex` sidecar in `index>>>>>path` form for modders; this file is informational and is not consumed by the game.
- Splits generated meshes before the 16-bit SCX v4 vertex-index limit.
- Writes the SCX v4 header table and record blocks with bounds checks.
- Honors the SCENE_EXPORT_SELECTED option.

### Not implemented yet

- SCX v3 serialization.
- Non-BitmapTex, nested/composite and procedural Standard Material maps; only direct BitmapTex slots are currently handled.
- Vertex-color and emissive-vertex extraction from Max mesh data.
- Skin weights, bone references and bone-index lists.
- Verified hard-surface / Physics semantics.
- Animation, pivot options and TNL optimized skinning.
- Legacy Groups .txt export and the separate Solidify modifier.
- Generalized material-header flags and complete legacy blend/render-state behavior; the current material flags value is still a fixture-derived placeholder.
- The original exporter option dialogs and defaults.

These are still in the porting scope. The current v4 output is a first geometry prototype, not a claim of full legacy compatibility.

## Crash-isolation build

The first Export Selected test crashed in `mesh.dll` with an access violation. The exact failing call is not yet proven because the GitHub-hosted minidump is binary and could not be decoded by the repository connector.

The current diagnostic build:
- avoids `Mesh::ComputeRenderNormal` and calculates a temporary flat geometric normal from transformed triangle positions;
- verifies map-face and map-vertex counts before reading UV arrays;
- writes stage markers to `%TEMP%\\SCXExporter_trace.log`, including evaluated-object conversion, mesh counts, face progress and serialization stages.

This is a temporary diagnostic configuration, not the final normal/smoothing implementation. Do not use its flat-shaded output as a production export.

## Important format assumptions still requiring reference exports

Material-header flags vary between the newly inspected FL_seat and F_bumper fixtures, so the current 0x33 flags value is still only a placeholder and must not be treated as universal. The following material-header scalar matches the 3ds Max Standard Material transparency type in the inspected files: 0 for ordinary materials and 2 for filter transparency; the exporter now reads this from Standard Material. The hard-surface payload is still emitted as 0.

Coordinate-system conversion and position units also need validation against a known model with measured dimensions. The current conversion follows the independent reader's /100 position conversion and its [x, -z, y] axis mapping.

## SDK and build configuration

Confirmed SDK root:

C:\Program Files\Autodesk\3ds Max 2025 SDK\maxsdk

The Visual Studio project targets:

- 3ds Max 2025 x64
- Visual Studio 2022 / MSVC v143
- Windows SDK 10.0.19041.0
- C++17
- Unicode

Include path: $(MaxSDK)\include

Library path: $(MaxSDK)\lib\x64\Release

The project links core.lib, geom.lib, mesh.lib, maxutil.lib and paramblk2.lib.

## Build

1. Open SCXExporterLoadTest.sln in Visual Studio 2022.
2. Select Debug or Release, and x64.
3. Choose Build → Rebuild Solution.
4. The output is native\bin\x64\<Configuration>\SCXExporterLoadTest.dle.

Local bin, obj and .vs folders are ignored by Git.

## First validation procedure

1. Export a simple test mesh from Max using the new .scx filter.
2. Run the repository's Python SCX inspector over the resulting file.
3. Inspect material and vertex block counts, positions, normals, UV channels and indices.
4. Compare against a controlled export from the legacy Invictus V4 plugin and then load in SLRR.

A file passing the structural inspector is not automatically game-compatible. Do not overwrite original game assets with prototype output.

## Next work

Build and test the new Standard Material map extraction in SLRR. In particular, verify diffuse/ambient texture slots, reflection environment maps, UV channel selection, tiling/offset, and whether the still-unresolved material flags affect rendering. Then recover the complete material-header flag rules before broadening support to nested maps. After that add the SCX v3 writer and continue through skinning, Physics, animation, pivot, TNL optimization, Groups and Solidify compatibility.
