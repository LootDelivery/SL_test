# Build environment: 3ds Max 2025 exporter

This project targets a native 64-bit 3ds Max 2025 SceneExport plug-in.

## Official requirements

Autodesk's SDK requirements table for 3ds Max 2025 specifies:

- 3ds Max 2025 SDK
- Microsoft Visual Studio 2022, baseline version 17.8.3
- C++ Platform Toolset v143
- Windows Platform SDK 10.0.19041.0

Reference: [Autodesk SDK Requirements](https://help.autodesk.com/cloudhelp/2026/ENU/MAXDEV-Developer/files/about_the_3ds_max_sdk/sdk_requirements.html).

The 3ds Max SDK is a separate developer package. Autodesk's current download instructions point to the [3ds Max API / SDKs and tools page](https://aps.autodesk.com/developer/overview/3ds-max-api).

## Check Visual Studio before installing anything

Do not uninstall Visual Studio or change its version yet. First inspect the current installation.

1. Open **Visual Studio Installer** from the Windows Start menu.
2. Find the installed **Visual Studio 2022** entry and click **Modify**.
3. Open the **Workloads** tab. Check whether **Desktop development with C++** is selected.
4. Open **Individual components**.
5. Search for MSVC v143. Check whether the VS 2022 x64/x86 C++ build tools are installed. The Autodesk baseline is the v14.38 / VS 17.8 toolset.
6. Search for 10.0.19041.0. Check whether **Windows 10 SDK (10.0.19041.0)** is installed.

If any component is missing, do not guess which similarly named component to select. Send a screenshot of the filtered component list first; we can identify the exact item. The installer can retain multiple toolsets side by side, so a newer Visual Studio installation does not automatically mean it must be replaced.

## Confirmed 3ds Max SDK installation

The user confirmed that the SDK is installed and its headers, libraries, samples and additional directories are present.

Confirmed SDK root:

`C:\\Program Files\\Autodesk\\3ds Max 2025 SDK\\maxsdk`

The native starter project in `native/` is configured to use this exact path. It expects headers under `include` and x64 Release libraries under `lib\\x64\\Release`.

## What we will build

The first native plug-in will be a small 64-bit SceneExport module that only registers in Max and reports its version. It will not write or modify any SCX data. Once it loads successfully in Max 2025, we will add the shared scene-reading layer and the version-specific SCX serializers.

This staged build is intentional: it isolates SDK/toolchain problems from file-format problems. The actual exporter features will be added after the loader test succeeds.

## First local build

The user has confirmed installation of the required MSVC v143 toolset and Windows SDK 10.0.19041.0, and has supplied the SDK root path above. A minimal native x64 SceneExport registration project is now provided in `native/SCXExporterLoadTest.sln`.

The first build is a registration-only probe. It intentionally does not write SCX data; see `native/README.md` for the purpose and expected output.
