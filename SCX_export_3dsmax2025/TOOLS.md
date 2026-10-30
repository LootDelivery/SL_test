# SCX format inspection tool

`tools/inspect_scx.py` is the first executable component of the 3ds Max 2025 exporter work. It reads SCX v3/v4 independently of 3ds Max and reports structural problems before we build a native exporter.

## Requirements

- Python 3.10 or newer
- No third-party packages

## Run against repository fixtures

From the repository root:

```powershell
python .\SCX_export_3dsmax2025\tools\inspect_scx.py .\F_windshield.scx .\F_windshield_edited.scx
```

For machine-readable output:

```powershell
python .\SCX_export_3dsmax2025\tools\inspect_scx.py --json .\F_windshield.scx
```

Run the synthetic parser tests:

```powershell
python -m unittest discover -s .\SCX_export_3dsmax2025\tests -v
```

## Current validation coverage

- INVO signature and supported version.
- SCX v3 material records, extended vertex stride, finite vertex floats, triangle indices and optional end marker.
- SCX v4 absolute header offsets, entry/block type agreement, block boundaries/overlap, material entry payload sizes, vertex channel flags/stride, finite float channels and 16-bit face indices.
- Basic face-to-nearest-preceding-vertex index-range checks.

## Known limits — do not treat as a complete SCX specification

The public format reference does not fully document v4 sparse/dense bone-index-list payloads, all material property IDs, physical mesh semantics, and every legacy exporter option. Those records are range-checked but not semantically decoded. If an unknown v4 material property or vertex channel appears, the tool intentionally reports it instead of guessing its length.

A structurally valid file is not guaranteed to load correctly in SLRR. The next stage is to compare the parser report against the two real windshield fixtures and controlled exports from the legacy plugins.
