# Legacy plug-in resource audit

This file records items recovered directly from the PE resources and UTF-16 strings embedded in the original binaries. It is an evidence log, not a guess at the implementation behind each option.

## Binary metadata

| Module | PE target | Linker | PE timestamp | Product/file version strings |
|---|---|---|---|---|
| InvictusExportGroups.dle | x86 / PE32 | MSVC 6.0 | 2002-06-03 | 4.0.0.0 |
| InvictusExportV3.dle | x86 / PE32 | MSVC 6.0 | 2002-07-12 | 3.0.3.20 |
| InvictusExportV4.dle | x86 / PE32 | MSVC 6.0 | 2003-07-02 | 3.0.3.31 |

These version strings are the PE version-resource values, not necessarily the SCX file-format version.

## Exporter file types

| Module | Resource description | Extension |
|---|---|---|
| InvictusExportGroups.dle | Standard | .txt |
| InvictusExportV3.dle | Standard | .scx |
| InvictusExportV4.dle | Standard | .scx |

The Groups module is a text exporter and is not an SCX serializer. It will be treated as a separate output format with its own tests.

## Export UI options recovered from dialogs and wide strings

### InvictusExportV3.dle

The export dialog contains six checkbox controls. Their captions appear in the module's UTF-16 strings:

- TNL optimized skinning
- Physics
- Strip
- Animation
- Pivot
- Seperate

Control IDs in the dialog resource are 1025 through 1030. Their exact functional effects, defaults and interactions still need reference exports from the original plug-in.

### InvictusExportV4.dle

The export dialog contains four checkbox controls:

- Physics
- Animation
- Pivot
- Seperate

The corresponding dialog control IDs are 1026, 1028, 1029 and 1030. The V4 dialog does not contain the V3 TNL optimized skinning and Strip controls. Their absence is intentional evidence; do not silently add V3-only options to the V4 UI.

### InvictusExportGroups.dle

The dialog title is Group Export. The resource contains a Frame #: label, a numeric edit/spinner pair and a Static Output group label. The file-type resource identifies the output extension as .txt.

The exact meaning of Frame # and the text export grammar have not yet been recovered. These need sample output files from the old plug-in or further disassembly before implementation.

## About boxes and descriptions

The V3 and V4 modules share the description Invictus Scene Exporter. The Groups module identifies itself as Invictus Group Exporter. About-dialog text identifies Invictus / 3DSMAX exporter builds.

## Evidence boundaries

Resource templates establish the existence and labels of controls; they do not establish the code paths or exact semantics behind their state. No assumptions about skinning data, physics mesh selection, strip generation, animation sampling, pivots, or group text syntax should be encoded until they are verified against old-plugin exports.
