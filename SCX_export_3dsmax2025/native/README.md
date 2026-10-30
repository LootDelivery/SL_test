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
- Duplicates vertices per face corner to preserve render-normal and UV seams.
- Reads render normals and mapping channels 1–3.
- Reads basic material data: name, diffuse and specular colors, transparency, self-illumination color and specular strength.
- Splits generated meshes before the 16-bit SCX v4 vertex-index limit.
- Writes the SCX v4 header table and record blocks with bounds checks.
- Honors the SCENE_EXPORT_SELECTED option.

### Not implemented yet

- SCX v3 serialization.
- SLRR external texture-index lookup and complete material-map extraction.
- Vertex-color and emissive-vertex extraction from Max mesh data.
- Skin weights, bone references and bone-index lists.
- Verified hard-surface / Physics semantics.
- Animation, pivot options and TNL optimized skinning.
- Legacy Groups .txt export and the separate Solidify modifier.
- The original exporter option dialogs and defaults.

These are still in the porting scope. The current v4 output is a first geometry prototype, not a claim of full legacy compatibility.

## Important format assumptions still requiring reference exports

The two repository windshield fixtures use material-header values 0x33 and 2.0, and a hard-surface payload of 0. The prototype preserves these observed values as defaults, but their full meaning is not established. They must be compared against controlled files exported by the original Invictus V4 plugin before being treated as confirmed format rules.

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

The project links core.lib, geom.lib, maxutil.lib and paramblk2.lib.

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

Validate the first geometry file, then recover external texture mapping and material semantics. After that add the SCX v3 writer and continue through skinning, Physics, animation, pivot, TNL optimization, Groups and Solidify compatibility.
