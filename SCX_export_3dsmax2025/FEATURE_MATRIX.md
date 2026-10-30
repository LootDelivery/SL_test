# Legacy feature inventory and porting plan

Target host: Autodesk 3ds Max 2025 x64.
Working branch: `work/scx-export-3dsmax2025`.
Rule: all new work stays inside `SCX_export_3dsmax2025/`. The original `SCX_export_3dsmax5/` folder and all game files are read-only reference material.

## Legacy modules found

| Module | Type | Initial finding | Porting status |
|---|---|---|---|
| `InvictusExportGroups.dle` | 3ds Max exporter | x86 binary from 2002; uses Max core/geometry utility APIs. Exact group behavior still needs to be identified. | Required; investigate before implementation |
| `InvictusExportV3.dle` | 3ds Max exporter | x86 binary from 2002; references Mesh map channels, map faces/vertices and render normals. | Required |
| `InvictusExportV4.dle` | 3ds Max exporter | x86 binary from 2003; references mesh bounds, custom attributes, and contains strings for SKIN/PHYSIQUE export paths. | Required |
| `Solidify.dlm` | 3ds Max modifier | x86 binary from 2001; modifier/mesh API imports. It is not itself an SCX exporter. | Evaluate separately; preserve if it is part of the user's workflow |

All four files are PE32/x86 binaries built with the MSVC 6.0 linker. The V4 binary retains a path under `3DSMAX42\\MAXSDK`. No C++ source or project files for these modules are present in the supplied folder. A direct rebuild of the binaries is therefore not currently possible.

## Capability matrix

This matrix distinguishes verified clues from features that still require controlled tests in the old Max version. Binary strings alone are not treated as proof that a feature works.

| Capability | Evidence / current status |
|---|---|
| SCX v3 output | Dedicated V3 exporter exists; exact output behavior to be tested |
| SCX v4 output | Experimental C++ v4 writer is connected to Max evaluated triangular meshes. User has confirmed that a simple mesh exported by the rebuilt plugin loads and displays in SLRR. Materials/textures remain unvalidated in-game. |
| Group-aware export | Dedicated Groups exporter exists; semantics and output contract TBD |
| Multiple meshes / material submeshes | Initial v4 path splits triangle faces by Max face material ID into separate material mesh records. Hierarchy/group semantics remain TBD. |
| Materials and texture indices | Experimental Standard Material support now reads direct BitmapTex slots: Diffuse, Ambient (SCX DiffuseMixSecond), Bump, Reflection and Self-Illumination. Texture paths are deduplicated by first use; indices are serialized into SCX and written to an informational `.tex` sidecar. Nested/procedural maps and material-header flags remain unresolved; in-game material rendering still needs testing. |
| Normals and smoothing | Crash-isolation build temporarily computes flat face normals from world-space triangle positions to avoid the earlier mesh.dll crash path. Per-corner smoothing/render normals are deferred until dump/trace analysis. |
| UV channels | Initial v4 path reads Max map channels 1–3 where present. Legacy UV flip and map transforms still need scene fixtures. |
| Vertex colors / emissive | Present in v3/v4 format definitions; per-version export support needs verification |
| Skin / Physique | V4 binary contains explicit SKIN/PHYSIQUE export strings; exact weight/index encoding must be recovered |
| Hard-surface / physical mesh | V4 entry type exists in the format reference; exporter behavior needs verification |
| Bump-map normal / other extended attributes | Present in the v4 format pattern; importer support is incomplete, exporter support TBD |
| Transforms, pivots, scale, hierarchy | Current prototype bakes world transforms and converts Max Z-up to SCX axes; positions are written in centimetres. Pivot/hierarchy preservation and coordinate validation remain open. |
| Groups exporter options | Recovered UI: Group Export dialog, Frame # edit/spinner, Static Output group; output extension is .txt. The meaning and text grammar still need reference exports. |
| Solidify modifier behavior | Separate modifier module; evaluate only after exporter baseline |

## Material reference fixtures

The root-level `FL_seat.scx` and `F_bumper.scx` files provide additional SCX v4 material examples. In `FL_seat.scx`, material map index 0 resolves to `interior.dds` and index 1 to `general_env_map.png` (based on the supplied texture order); diffuse/ambient layer entries use channel 0, while reflection entries use channel `0xFFFFFFFE` (-2). The material-header flags vary between materials, so the prototype's old constant 0x33 is not a verified universal value.

## Reference implementation caveat

The public [io_invo_scx](https://github.com/nonamesex/io_invo_scx) project is an independent Blender **importer**, not an exporter. It supports reading v3 and v4, but its own README marks bone weights/index references and some advanced channels as unfinished. It is useful for understanding the file layout, but it cannot be treated as a complete specification of the legacy exporters.

The repository's `F_windshield.scx` and `F_windshield_edited.scx` are useful binary fixtures. The latter differs from the former in at least one floating-point field; both should be parsed and compared as part of format validation.

## Porting strategy

1. **Recover behavior before coding.** Inventory each legacy plugin's UI strings, class descriptors, resource dialogs, exported file variants and option names. Where possible, obtain controlled reference exports from the old plugin for the same scene.
2. **Build a format test harness.** Parse/write the INVO header and v3/v4 records independently of 3ds Max. Validate offsets, counts, record sizes, index ranges and channel flags. Add round-trip fixtures.
3. **Implement shared scene extraction.** Use the 3ds Max 2025 SDK to read scene nodes, transforms, evaluated triangle meshes, material assignments, mapping channels, normals, vertex colors and supported modifiers.
4. **Implement exporters by generation.** V3 and V4 share scene extraction but have separate serializers and explicitly documented option sets. Do not force both formats through one lossy schema.
5. **Implement Groups semantics.** Only after its expected behavior is confirmed from UI/resources/reference output.
6. **Implement skinning and Physique.** Recover vertex weights, bone references, submesh limits and modifier evaluation rules; compare against old plugin output.
7. **Evaluate Solidify separately.** It is a modifier, not a file exporter. Decide whether a port is necessary after confirming whether the workflow depends on it.
8. **Validate in SLRR.** A file that parses successfully is not automatically game-compatible. Test in-game loading, materials, hierarchy, collision/physical meshes and deformation separately.

## 3ds Max 2025 build environment

Autodesk's SDK requirements specify 3ds Max 2025 SDK, Visual Studio 2022 17.8.3, C++ Platform Toolset v143 and Windows Platform SDK 10.0.19041.0. Autodesk distributes the Max SDK separately from the main application. Before the first local build, check which of these components are installed; do not reinstall or change the user's working environment unnecessarily.

The user has Visual Studio 2022 installed but does not work with it. Build setup and each test will therefore be given as explicit, step-by-step actions, without assuming prior C++ or Visual Studio knowledge.


## Recovered export options

- V3 UI: TNL optimized skinning, Physics, Strip, Animation, Pivot, Seperate.
- V4 UI: Physics, Animation, Pivot, Seperate.
- Groups UI: Frame # numeric edit/spinner and Static Output group; the plugin is a text exporter (.txt), not an SCX exporter.

Resource evidence and control IDs are recorded in [LEGACY_PLUGIN_UI.md](LEGACY_PLUGIN_UI.md). These labels establish the presence of options, not their semantics. Defaults and behavior must be confirmed against the original plug-ins before implementation.