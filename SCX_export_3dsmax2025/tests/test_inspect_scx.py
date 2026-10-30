import struct
import tempfile
import unittest
from pathlib import Path

from SCX_export_3dsmax2025.tools.inspect_scx import ScxFormatError, inspect_scx


def u32(value: int) -> bytes:
    return struct.pack("<I", value)


def make_v4_fixture() -> bytes:
    # One material (name), one triangle with three positions, one face.
    header_size = 12 + 3 * 8
    material = (
        u32(0) + u32(56) + u32(0) + u32(0) + u32(1)
        + u32(0x08000000) + b"TestMaterial".ljust(32, b"\0")
    )
    vertices = (
        u32(4) + u32(52) + u32(3) + u32(1)
        + struct.pack("<9f", 0, 0, 0, 1, 0, 0, 0, 1, 0)
    )
    faces = u32(5) + u32(18) + u32(3) + struct.pack("<3H", 0, 1, 2)
    offsets = (header_size, header_size + len(material),
               header_size + len(material) + len(vertices))
    table = b"".join(u32(kind) + u32(offset)
                     for kind, offset in zip((0, 4, 5), offsets))
    return b"INVO" + u32(4) + u32(3) + table + material + vertices + faces


def make_v3_fixture() -> bytes:
    # Minimal legacy v3 record: 56-byte material, 3 vertices, 1 triangle.
    material = u32(56) + bytes(36) + u32(0) + (b"\xFF\xFF" * 6)
    vertices = u32(3) + b"".join(
        struct.pack("<10f", *([float(i), 0, 0, 0, 0, 1, 0, 0, 0, 0]))
        + bytes(4)
        for i in range(3)
    )
    faces = u32(1) + struct.pack("<3I", 0, 1, 2)
    return b"INVO" + u32(3) + material + vertices + faces


class InspectScxTests(unittest.TestCase):
    def inspect_bytes(self, content: bytes):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "fixture.scx"
            path.write_bytes(content)
            return inspect_scx(path)

    def test_v4_minimal_triangle(self):
        result = self.inspect_bytes(make_v4_fixture())
        self.assertEqual(result["version"], 4)
        self.assertEqual(result["vertex_count"], 3)
        self.assertEqual(result["face_count"], 1)
        self.assertEqual(result["material_count"], 1)

    def test_v3_minimal_triangle(self):
        result = self.inspect_bytes(make_v3_fixture())
        self.assertEqual(result["version"], 3)
        self.assertEqual(result["vertex_count"], 3)
        self.assertEqual(result["face_count"], 1)

    def test_bad_signature(self):
        with self.assertRaises(ScxFormatError):
            self.inspect_bytes(b"NOPE" + u32(4))

    def test_invalid_v4_face_index(self):
        content = bytearray(make_v4_fixture())
        # Last face index is at the end of the file.
        struct.pack_into("<H", content, len(content) - 2, 3)
        with self.assertRaises(ScxFormatError):
            self.inspect_bytes(bytes(content))


if __name__ == "__main__":
    unittest.main()
