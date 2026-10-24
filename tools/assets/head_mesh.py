"""The head mesh file the mod loads: quads grouped by part and texture, one block of them per LOD."""

import struct
from dataclasses import dataclass, field

MAGIC = b"MHED"
VERSION = 1

PARTS = ("root", "brow", "eye", "mouth_closed", "mouth_open")
TEXTURES = ("skin", "cap", "iris")

VERTEX = struct.Struct("<3f3bx2f4B")


@dataclass
class Section:
    part: str
    texture: str
    vertices: list = field(default_factory=list)
    quads: list = field(default_factory=list)
    index: dict = field(default_factory=dict, repr=False)

    def vertex(self, pos, normal, uv, colour):
        packed = tuple(max(-127, min(127, round(c * 127))) for c in normal)
        key = (tuple(round(c, 5) for c in pos), packed,
               tuple(round(c, 5) for c in uv), tuple(colour))
        found = self.index.get(key)
        if found is None:
            found = self.index[key] = len(self.vertices)
            self.vertices.append(key)
        return found

    def quad(self, corners):
        """corners is three or four (pos, normal, uv, colour) tuples, counter clockwise from outside."""
        ids = [self.vertex(*c) for c in corners]
        if len(ids) == 3:
            ids.append(ids[2])
        if len(ids) != 4:
            raise ValueError(f"{self.part}/{self.texture}: a face has {len(ids)} corners")
        self.quads.append(ids)


def write(path, lods):
    with open(path, "wb") as f:
        f.write(MAGIC)
        f.write(struct.pack("<HH", VERSION, len(lods)))
        for sections in lods:
            f.write(struct.pack("<H", len(sections)))
            for s in sections:
                f.write(struct.pack("<BBII", PARTS.index(s.part), TEXTURES.index(s.texture),
                                    len(s.vertices), len(s.quads)))
                for pos, normal, uv, colour in s.vertices:
                    f.write(VERTEX.pack(*pos, *normal, *uv, *colour))
                for q in s.quads:
                    f.write(struct.pack("<4I", *q))


def read(path):
    with open(path, "rb") as f:
        data = f.read()
    if data[:4] != MAGIC:
        raise ValueError(f"{path}: bad magic {data[:4]!r}")
    version, lod_count = struct.unpack_from("<HH", data, 4)
    if version != VERSION:
        raise ValueError(f"{path}: version {version}, expected {VERSION}")
    at, lods = 8, []
    for _ in range(lod_count):
        (count,), at = struct.unpack_from("<H", data, at), at + 2
        sections = []
        for _ in range(count):
            part, tex, nv, nq = struct.unpack_from("<BBII", data, at)
            at += 10
            s = Section(PARTS[part], TEXTURES[tex])
            for _ in range(nv):
                v = VERTEX.unpack_from(data, at)
                at += VERTEX.size
                s.vertices.append((v[0:3], v[3:6], v[6:8], v[8:12]))
            for _ in range(nq):
                s.quads.append(list(struct.unpack_from("<4I", data, at)))
                at += 16
            sections.append(s)
        lods.append(sections)
    if at != len(data):
        raise ValueError(f"{path}: {len(data) - at} trailing bytes")
    return lods
