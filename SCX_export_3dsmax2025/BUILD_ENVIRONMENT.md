# Build environment: 3ds Max 2025 exporter

## Confirmed local setup

The user has installed and confirmed:

- Visual Studio 2022.
- MSVC v143 x64/x86 toolset (14.38–17.8).
- Windows SDK 10.0.19041.0.
- 3ds Max 2025 SDK.

Confirmed SDK root:

`C:\Program Files\Autodesk\3ds Max 2025 SDK\maxsdk`

The SDK headers are under `include`; x64 Release libraries are under `lib\x64\Release`.

## Project configuration

The solution is `native/SCXExporterLoadTest.sln`. The project is configured for:

- 3ds Max 2025 x64.
- Visual Studio 2022 / Platform Toolset v143.
- Windows SDK 10.0.19041.0.
- C++17 and Unicode.
- SDK include path `$(MaxSDK)\include`.
- SDK library path `$(MaxSDK)\lib\x64\Release`.

The current project links `core.lib`, `geom.lib`, `mesh.lib`, `maxutil.lib` and `paramblk2.lib`. The Max SDK documentation identifies `mesh.lib` as the library required by plug-ins using the Mesh class and `geom.lib` for geometry plug-ins.

## Local build

The user has already confirmed that the initial registration-only plug-in compiled and loaded in 3ds Max 2025. The project has since been extended with the experimental SCX v4 scene extractor and serializer, so the latest branch must be downloaded before rebuilding.

From Visual Studio:

1. Open `native/SCXExporterLoadTest.sln`.
2. Select `Debug` and `x64` (or `Release` and `x64`).
3. Choose **Build → Rebuild Solution**.
4. The output should be `native\bin\x64\<Configuration>\SCXExporterLoadTest.dle`.

The code has not been compiled against the user's local Max SDK after the scene extractor was added. Treat compiler output from the next rebuild as the authoritative compatibility check.

## Current export scope

The plug-in now includes an experimental SCX v4 geometry path. It extracts evaluated triangular meshes, material splits, corner normals and UV channels, then writes an SCX v4 file.

The current implementation is not a complete port of the old exporter. SCX v3, external texture indices/maps, vertex colors, skinning/Physique, physical-mesh semantics, animation, pivot options, TNL optimization, Groups text export and Solidify compatibility remain to be implemented and validated. See `native/README.md` and `FEATURE_MATRIX.md`.
