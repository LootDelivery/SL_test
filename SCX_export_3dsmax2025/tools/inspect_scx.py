#!/usr/bin/env python3
"""Inspect and structurally validate INVO SCX v3/v4 files.

This tool is deliberately independent from 3ds Max. It validates container
structure and known record layouts; it does not certify game compatibility.
Only Python's standard library is required.
"""
from __future__ import annotations

import argparse
import json
import math
import struct
import sys
from pathlib import Path
from typing import Any


class ScxFormatError(ValueError):
    pass


def require_range(data: bytes, offset: int, size: int, what: str) -> None:
    if offset < 0 or size < 0 or offset + size > len(data):
        raise ScxFormatError(
            f"{what}: range 0x{offset:X}..0x{offset + size:X} "
            f"exceeds file size 0x{len(data):X}"
        )


def read_u16(data: bytes, offset: int, what: str) -> int:
    require_range(data, offset, 2, what)
    return struct.unpack_from("<H", data, offset)[0]


def read_u32(data: bytes, offset: int, what: str) -> int:
    require_range(data, offset, 4, what)
    return struct.unpack_from("<I", data, offset)[0]


def read_f32(data: bytes, offset: int, what: str) -> float:
    require_range(data, offset, 4, what)
    value = struct.unpack_from("<f", data, offset)[0]
    if not math.isfinite(value):
        raise ScxFormatError(f"{what}: non-finite float at 0x{offset:X}")
    return value


def fixed_string(data: bytes, offset: int, size: int) -> str:
    require_range(data, offset, size, "fixed string")
    raw = data[offset:offset + size].split(b"\0", 1)[0]
    return raw.decode("utf-8", errors="replace")


def parse_v3(data: bytes) -> dict[str, Any]:
    cursor = 8
    meshes: list[dict[str, Any]] = []
    terminated = False

    while cursor < len(data):
        if len(data) - cursor < 4:
            if any(data[cursor:]):
                raise ScxFormatError(f"SCX v3: non-zero trailing bytes at 0x{cursor:X}")
            break

        material_start = cursor
        material_size = read_u32(data, cursor, "v3 material size")
        if material_size == 0:
            cursor += 4
            terminated = True
            if any(data[cursor:]):
                raise ScxFormatError("SCX v3: non-zero data after end marker")
            break
        if material_size < 56:
            raise ScxFormatError(
                f"SCX v3: material block at 0x{material_start:X} "
                f"has invalid size {material_size} (minimum 56)"
            )
        require_range(data, material_start, material_size, "v3 material block")

        # v3 material record sizes currently understood by the independent
        # reader are 56 bytes (base), 104 bytes (extended), and 136 bytes
        # (extended + 32-byte material name). Preserve unknown tail bytes.
        if material_size > 56 and material_size < 104:
            raise ScxFormatError(
                f"SCX v3: unsupported partial extended material size {material_size}"
            )
        if material_size > 104 and material_size < 136:
            raise ScxFormatError(
                f"SCX v3: unsupported partial named material size {material_size}"
            )

        material_name = None
        if material_size >= 136:
            material_name = fixed_string(data, material_start + 104, 32)

        cursor = material_start + material_size
        vertex_count = read_u32(data, cursor, "v3 vertex count")
        cursor += 4
        stride = 64 if material_size > 56 else 44
        vertex_bytes = vertex_count * stride
        require_range(data, cursor, vertex_bytes, "v3 vertex data")

        # Check every float for NaN/Inf. The per-vertex channel layout follows
        # the public v3 reader: position, normal, UV1, UV2, BGRA color; for
        # extended materials an extra 20-byte UV3/unknown payload is present.
        float_count = 10 if stride == 44 else 12
        for vertex_index in range(vertex_count):
            base = cursor + vertex_index * stride
            for float_index in range(float_count):
                read_f32(data, base + float_index * 4, f"v3 vertex {vertex_index}")
        cursor += vertex_bytes

        face_count = read_u32(data, cursor, "v3 face count")
        cursor += 4
        face_bytes = face_count * 12
        require_range(data, cursor, face_bytes, "v3 face indices")
        for face_index in range(face_count):
            base = cursor + face_index * 12
            indices = struct.unpack_from("<III", data, base)
            if any(index >= vertex_count for index in indices):
                raise ScxFormatError(
                    f"SCX v3: face {face_index} references vertex outside "
                    f"0..{max(vertex_count - 1, 0)}"
                )
        cursor += face_bytes

        meshes.append({
            "material_name": material_name,
            "material_size": material_size,
            "vertices": vertex_count,
            "faces": face_count,
            "vertex_stride": stride,
            "offset": material_start,
        })

    return {
        "version": 3,
        "meshes": meshes,
        "mesh_count": len(meshes),
        "vertex_count": sum(item["vertices"] for item in meshes),
        "face_count": sum(item["faces"] for item in meshes),
        "end_marker": terminated,
        "parsed_bytes": cursor,
    }


V4_VERTEX_CHANNELS = (
    (0x00000001, 12, "position"),
    (0x00000002, 4, "bone_weight_0"),
    (0x00000004, 4, "bone_weight_1"),
    (0x00000008, 4, "bone_weight_2"),
    (0x00000010, 4, "bone_weight_3"),
    (0x00000020, 4, "bone_index_ref"),
    (0x00000040, 12, "normal"),
    (0x00000080, 4, "vertex_emissive"),
    (0x00000100, 4, "vertex_color"),
    (0x00000200, 8, "uv1"),
    (0x00000400, 8, "uv2"),
    (0x00000800, 8, "uv3"),
    (0x00040000, 12, "bump_map_normal"),
)
V4_KNOWN_VERTEX_MASK = sum(flag for flag, _, _ in V4_VERTEX_CHANNELS)


def parse_v4_material(data: bytes, offset: int, size: int) -> dict[str, Any]:
    if size < 20:
        raise ScxFormatError(f"SCX v4: material block at 0x{offset:X} is too small")
    entry_count = read_u32(data, offset + 16, "v4 material entry count")
    cursor = offset + 20
    end = offset + size
    entries: list[dict[str, Any]] = []
    for index in range(entry_count):
        entry_type = read_u32(data, cursor, f"v4 material entry {index} type")
        cursor += 4
        if entry_type in (0x00000000, 0x00000001, 0x00000002):
            payload_size, label = 4, "rgba"
        elif entry_type in (0x01000000, 0x01000001, 0x01000002):
            payload_size, label = 4, "float"
            read_f32(data, cursor, f"v4 material entry {index}")
        elif 0x06000000 <= entry_type <= 0x06000004:
            payload_size, label = 28, "map"
        elif entry_type == 0x08000000:
            payload_size, label = 32, "name"
            entries.append({
                "type": f"0x{entry_type:08X}",
                "kind": label,
                "name": fixed_string(data, cursor, payload_size),
            })
            cursor += payload_size
            continue
        else:
            raise ScxFormatError(
                f"SCX v4: unknown material entry 0x{entry_type:08X} "
                f"at 0x{cursor - 4:X}; record length cannot be inferred safely"
            )
        require_range(data, cursor, payload_size, f"v4 material entry {index}")
        entries.append({"type": f"0x{entry_type:08X}", "kind": label})
        cursor += payload_size

    if cursor != end:
        raise ScxFormatError(
            f"SCX v4: material block at 0x{offset:X} declares {size} bytes, "
            f"but known entries consume {cursor - offset}"
        )
    name = next((item["name"] for item in entries if item["kind"] == "name"), None)
    return {"material_entries": entry_count, "material_name": name}


def parse_v4_vertex_block(data: bytes, offset: int, size: int) -> dict[str, Any]:
    if size < 16:
        raise ScxFormatError(f"SCX v4: vertex block at 0x{offset:X} is too small")
    vertex_count = read_u32(data, offset + 8, "v4 vertex count")
    channel_flags = read_u32(data, offset + 12, "v4 vertex channel flags")
    unknown = channel_flags & ~V4_KNOWN_VERTEX_MASK
    if unknown:
        raise ScxFormatError(
            f"SCX v4: vertex block at 0x{offset:X} uses unknown channel "
            f"flags 0x{unknown:08X}; cannot compute a safe vertex stride"
        )
    stride = sum(
        size_bytes for flag, size_bytes, _ in V4_VERTEX_CHANNELS
        if channel_flags & flag
    )
    expected_size = 16 + vertex_count * stride
    if size != expected_size:
        raise ScxFormatError(
            f"SCX v4: vertex block at 0x{offset:X} has size {size}, "
            f"expected {expected_size} for {vertex_count} vertices "
            f"and stride {stride}"
        )

    # Validate all declared floating-point channels.
    for vertex_index in range(vertex_count):
        cursor = offset + 16 + vertex_index * stride
        for flag, channel_size, label in V4_VERTEX_CHANNELS:
            if not (channel_flags & flag):
                continue
            if label in ("position", "normal", "bump_map_normal"):
                for component in range(3):
                    read_f32(data, cursor + component * 4, f"v4 vertex {vertex_index} {label}")
            elif label.startswith("uv"):
                read_f32(data, cursor, f"v4 vertex {vertex_index} {label}.u")
                read_f32(data, cursor + 4, f"v4 vertex {vertex_index} {label}.v")
            elif label.startswith("bone_weight"):
                read_f32(data, cursor, f"v4 vertex {vertex_index} {label}")
            cursor += channel_size

    channels = [
        label for flag, _, label in V4_VERTEX_CHANNELS if channel_flags & flag
    ]
    return {
        "vertices": vertex_count,
        "vertex_stride": stride,
        "vertex_flags": f"0x{channel_flags:08X}",
        "channels": channels,
    }


def parse_v4_face_block(data: bytes, offset: int, size: int) -> dict[str, Any]:
    if size < 12:
        raise ScxFormatError(f"SCX v4: face block at 0x{offset:X} is too small")
    index_count = read_u32(data, offset + 8, "v4 index count")
    if index_count % 3:
        raise ScxFormatError(
            f"SCX v4: face block at 0x{offset:X} has {index_count} indices, "
            "which is not divisible by 3"
        )
    expected_size = 12 + index_count * 2
    if size != expected_size:
        raise ScxFormatError(
            f"SCX v4: face block at 0x{offset:X} has size {size}, "
            f"expected {expected_size} for {index_count} 16-bit indices"
        )
    indices = struct.unpack_from(f"<{index_count}H", data, offset + 12) if index_count else ()
    return {
        "indices": index_count,
        "faces": index_count // 3,
        "max_index": max(indices) if indices else None,
        "_indices": indices,
    }


def parse_v4(data: bytes) -> dict[str, Any]:
    entry_count = read_u32(data, 8, "v4 header entry count")
    table_start = 12
    table_size = entry_count * 8
    require_range(data, table_start, table_size, "v4 header table")
    data_start = table_start + table_size

    records: list[dict[str, Any]] = []
    for index in range(entry_count):
        table_offset = table_start + index * 8
        entry_type = read_u32(data, table_offset, f"v4 table entry {index} type")
        offset = read_u32(data, table_offset + 4, f"v4 table entry {index} offset")
        if offset < data_start:
            raise ScxFormatError(
                f"SCX v4: table entry {index} offset 0x{offset:X} points into header"
            )
        require_range(data, offset, 8, f"v4 block {index} header")
        stored_type = read_u32(data, offset, f"v4 block {index} type")
        size = read_u32(data, offset + 4, f"v4 block {index} size")
        if stored_type != entry_type:
            raise ScxFormatError(
                f"SCX v4: table entry {index} says type {entry_type}, "
                f"but block at 0x{offset:X} stores type {stored_type}"
            )
        if size < 8:
            raise ScxFormatError(f"SCX v4: block {index} has invalid size {size}")
        require_range(data, offset, size, f"v4 block {index}")

        record: dict[str, Any] = {
            "type": entry_type,
            "offset": offset,
            "size": size,
        }
        if entry_type == 0:
            record.update(parse_v4_material(data, offset, size))
        elif entry_type == 1:
            if size < 12:
                raise ScxFormatError(f"SCX v4: hard-surface block at 0x{offset:X} is too small")
            record["kind"] = "hard_surface"
        elif entry_type in (2, 3):
            # The public format pattern identifies these as bone-index lists,
            # but their payload layouts are not yet documented well enough
            # to parse safely. Keep their byte ranges intact.
            record["kind"] = "bone_index_list" if entry_type == 3 else "sparse_bone_index_list"
            record["payload_validated"] = False
        elif entry_type == 4:
            record["kind"] = "vertices"
            record.update(parse_v4_vertex_block(data, offset, size))
        elif entry_type == 5:
            record["kind"] = "faces"
            record.update(parse_v4_face_block(data, offset, size))
        else:
            raise ScxFormatError(
                f"SCX v4: unsupported block type {entry_type} at 0x{offset:X}"
            )
        records.append(record)

    sorted_records = sorted(records, key=lambda item: item["offset"])
    previous_end = data_start
    for record in sorted_records:
        if record["offset"] < previous_end:
            raise ScxFormatError(
                f"SCX v4: overlapping block at 0x{record['offset']:X}"
            )
        previous_end = record["offset"] + record["size"]

    # Face-index checks use the closest preceding vertex block. This mirrors
    # the ordinary material -> vertex -> face block ordering seen in fixtures.
    current_vertex_count: int | None = None
    total_vertices = 0
    total_faces = 0
    for record in sorted_records:
        if record["type"] == 4:
            current_vertex_count = record["vertices"]
            total_vertices += record["vertices"]
        elif record["type"] == 5:
            total_faces += record["faces"]
            if record["_indices"] and current_vertex_count is None:
                raise ScxFormatError(
                    f"SCX v4: face block at 0x{record['offset']:X} has no preceding vertex block"
                )
            if current_vertex_count is not None and any(
                index >= current_vertex_count for index in record["_indices"]
            ):
                raise ScxFormatError(
                    f"SCX v4: face block at 0x{record['offset']:X} references "
                    f"a vertex outside 0..{max(current_vertex_count - 1, 0)}"
                )

    for record in records:
        record.pop("_indices", None)

    return {
        "version": 4,
        "header_entries": entry_count,
        "records": records,
        "record_count": len(records),
        "material_count": sum(1 for item in records if item["type"] == 0),
        "vertex_block_count": sum(1 for item in records if item["type"] == 4),
        "face_block_count": sum(1 for item in records if item["type"] == 5),
        "vertex_count": total_vertices,
        "face_count": total_faces,
        "validated_bytes": max(
            (item["offset"] + item["size"] for item in records),
            default=data_start,
        ),
    }


def inspect_scx(path: Path) -> dict[str, Any]:
    data = path.read_bytes()
    if len(data) < 8:
        raise ScxFormatError("file is shorter than the INVO header")
    if data[:4] != b"INVO":
        raise ScxFormatError(f"invalid signature {data[:4]!r}; expected b'INVO'")
    version = read_u32(data, 4, "SCX version")
    if version == 3:
        result = parse_v3(data)
    elif version == 4:
        result = parse_v4(data)
    else:
        raise ScxFormatError(f"unsupported INVO version {version}")
    result["file"] = str(path)
    result["file_size"] = len(data)
    return result


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Inspect and structurally validate an INVO SCX v3/v4 model."
    )
    parser.add_argument("files", nargs="+", type=Path, help="SCX files to inspect")
    parser.add_argument("--json", action="store_true", help="print JSON output")
    args = parser.parse_args()

    results = []
    failed = False
    for path in args.files:
        try:
            result = inspect_scx(path)
            results.append(result)
            if not args.json:
                print(
                    f"OK  {path}: v{result['version']}, "
                    f"{result.get('vertex_count', 0)} vertices, "
                    f"{result.get('face_count', 0)} faces"
                )
                if result["version"] == 4:
                    print(
                        f"    {result['material_count']} materials; "
                        f"{result['vertex_block_count']} vertex blocks; "
                        f"{result['face_block_count']} face blocks"
                    )
                elif result["version"] == 3:
                    print(f"    {result['mesh_count']} mesh records")
        except (OSError, ScxFormatError, struct.error) as exc:
            failed = True
            results.append({"file": str(path), "error": str(exc)})
            if not args.json:
                print(f"FAIL {path}: {exc}", file=sys.stderr)

    if args.json:
        print(json.dumps(results, indent=2, ensure_ascii=False))
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
