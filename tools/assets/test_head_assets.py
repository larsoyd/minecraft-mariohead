#!/usr/bin/env python3
import os
import re
import subprocess
import sys
import tempfile
import unittest

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import build_head  # noqa: E402
import head_mesh  # noqa: E402

# jaw openings the head shipped with, measured from the same cuts at 22050 Hz
SHIPPED_MOUTH = {
    "hello": [0, 0, 0, 27, 19, 125, 190, 226, 240, 239, 236, 231, 222, 210, 197, 180, 126, 88, 62,
              43, 30, 21, 15, 10, 7, 126, 160, 145, 140, 172, 150, 126, 161, 183, 161, 113, 128,
              106, 74, 52, 36, 82, 145, 172, 176, 172, 162, 143, 100, 70, 49, 109, 157, 167, 160,
              152, 144, 150, 150, 142, 133, 117, 108, 76, 53, 37],
    "mmm1": [0, 0, 35, 149, 205, 232, 243, 248, 249, 252, 250, 249, 248, 246, 242, 241, 240, 238,
             237, 239, 239, 240, 240, 239, 239, 239, 239, 240, 239, 239, 238, 238, 237, 238, 237,
             236, 234, 233, 231, 227, 224, 219, 209, 185, 130, 91, 64],
    "mmm2": [0, 0, 89, 178, 219, 234, 239, 245, 248, 251, 253, 254, 253, 251, 247, 247, 244, 245,
             241, 243, 243, 239, 239, 240, 241, 235, 236, 237, 239, 239, 245, 245, 246, 247, 247,
             249, 249, 248, 248, 249, 251, 251, 250, 249, 249, 248, 249, 248, 246, 245, 245, 244,
             243, 241, 239, 236, 234, 228, 220, 210, 229, 237, 234, 177, 124, 87, 61],
    "mmm3": [0, 0, 86, 170, 214, 235, 246, 245, 244, 243, 242, 241, 238, 237, 236, 235, 232, 226,
             224, 222, 222, 219, 218, 217, 217, 218, 217, 217, 216, 215, 217, 216, 216, 219, 217,
             216, 215, 214, 214, 217, 217, 216, 215, 215, 214, 211, 207, 200, 189, 176, 167, 158,
             152, 146, 137, 127, 99, 70, 49],
}
SHIPPED_MILLIS = {"hello": 2180, "mmm1": 1550, "mmm2": 2200, "mmm3": 1940}


def bundled_voice():
    with open(build_head.VOICE_JAVA, encoding="utf-8") as f:
        src = f.read()
    return {m.group(1): (int(m.group(2)), [int(x) for x in re.findall(r"\d+", m.group(3))])
            for m in re.finditer(r'new Clip\("(\w+)", (\d+), new int\[\] \{(.*?)\}\);', src, re.S)}


class MeshFormat(unittest.TestCase):
    def test_roundtrip(self):
        s = head_mesh.Section("eye", "iris")
        corner = lambda x: ((x, 1.0, 2.0), (0.0, 1.0, 0.0), (0.25, 0.75), (1, 2, 3, 255))
        s.quad([corner(0.0), corner(1.0), corner(2.0), corner(3.0)])
        s.quad([corner(0.0), corner(1.0), corner(4.0)])
        with tempfile.TemporaryDirectory() as d:
            path = os.path.join(d, "m.mesh")
            head_mesh.write(path, [[s], []])
            lods = head_mesh.read(path)
        self.assertEqual(len(lods), 2)
        got = lods[0][0]
        self.assertEqual((got.part, got.texture), ("eye", "iris"))
        self.assertEqual(got.quads, [[0, 1, 2, 3], [0, 1, 4, 4]])
        self.assertEqual(got.vertices[4][0], (4.0, 1.0, 2.0))
        self.assertEqual(got.vertices[0][1], (0, 127, 0))
        self.assertEqual(got.vertices[0][3], (1, 2, 3, 255))

    def test_shared_corners_are_welded(self):
        s = head_mesh.Section("root", "skin")
        c = ((0.0, 0.0, 0.0), (0.0, 0.0, 1.0), (0.5, 0.5), (9, 9, 9, 255))
        d = ((1.0, 0.0, 0.0), (0.0, 0.0, 1.0), (0.5, 0.5), (9, 9, 9, 255))
        e = ((1.0, 1.0, 0.0), (0.0, 0.0, 1.0), (0.5, 0.5), (9, 9, 9, 255))
        s.quad([c, d, e])
        s.quad([c, e, d])
        self.assertEqual(len(s.vertices), 3)

    def test_bad_magic_refused(self):
        with tempfile.TemporaryDirectory() as d:
            path = os.path.join(d, "m.mesh")
            with open(path, "wb") as f:
                f.write(b"NOPE\x01\x00\x00\x00")
            with self.assertRaises(ValueError):
                head_mesh.read(path)


class BundledMesh(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.lods = head_mesh.read(build_head.MESH_PATH)

    def positions(self, lod, parts):
        return np.array([v[0] for s in self.lods[lod] if s.part in parts for v in s.vertices])

    def test_two_lods_with_every_part(self):
        self.assertEqual(len(self.lods), len(build_head.LODS))
        for sections in self.lods:
            self.assertEqual({s.part for s in sections}, set(head_mesh.PARTS))
            self.assertEqual({s.texture for s in sections}, set(head_mesh.TEXTURES))

    def test_near_lod_is_denser(self):
        quads = [sum(len(s.quads) for s in lod) for lod in self.lods]
        self.assertGreater(quads[0], 3 * quads[1])

    def test_indices_in_range_and_normals_unit(self):
        for sections in self.lods:
            for s in sections:
                top = len(s.vertices)
                self.assertTrue(all(0 <= i < top for q in s.quads for i in q), s.part)
                n = np.array([v[1] for v in s.vertices], np.float64) / 127.0
                lengths = np.linalg.norm(n, axis=1)
                self.assertTrue(np.all(np.abs(lengths - 1.0) < 0.03), (s.part, lengths.min()))

    def test_size_centre_and_symmetry(self):
        for lod in range(len(self.lods)):
            p = self.positions(lod, set(head_mesh.PARTS))
            height = p[:, 1].max() - p[:, 1].min()
            self.assertAlmostEqual(height, 2.23, delta=0.03)
            self.assertAlmostEqual((p[:, 1].max() + p[:, 1].min()) / 2, 0.0, delta=0.02)
            self.assertAlmostEqual(p[:, 0].max() + p[:, 0].min(), 0.0, delta=0.01)
            self.assertAlmostEqual(np.ptp(p[:, 0]), 2.6, delta=0.15)

    def test_faces_plus_z(self):
        self.assertGreater(self.positions(0, {"eye"})[:, 2].mean(), 0.4)
        self.assertGreater(self.positions(0, {"brow"})[:, 2].mean(), 0.4)
        hair = np.array([v[0] for s in self.lods[0] if s.part == "root" for v in s.vertices
                         if v[3][:3] == (133, 57, 23)])
        self.assertLess(hair[:, 2].mean(), 0.0)

    def test_iris_textured_and_cap_textured(self):
        for s in self.lods[0]:
            uvs = np.array([v[2] for v in s.vertices])
            if s.texture in ("cap", "iris"):
                self.assertGreater(np.ptp(uvs[:, 0]), 0.9, s.texture)
                self.assertGreater(np.ptp(uvs[:, 1]), 0.9, s.texture)
                self.assertTrue(all(v[3] == (255, 255, 255, 255) for v in s.vertices))

    def colours(self, part):
        return {v[3][:3] for s in self.lods[0] if s.part == part for v in s.vertices}

    def test_mouth_poses(self):
        closed, opened = self.colours("mouth_closed"), self.colours("mouth_open")
        self.assertIn(build_head.CLOSED_SEAM, closed)
        self.assertNotIn(build_head.CAVITY, closed)
        for colour in (build_head.CAVITY, build_head.TONGUE, build_head.THROAT):
            self.assertIn(colour, opened)

    def test_shut_lip_sits_higher(self):
        def lowest_lip(part):
            p = np.array([v[0] for s in self.lods[0] if s.part == part and s.texture == "skin"
                          for v in s.vertices if v[3][:3] == (188, 97, 79)])
            front = p[(np.abs(p[:, 0]) < 0.2) & (p[:, 2] > 0.6)]
            return front[:, 1].min()
        self.assertGreater(lowest_lip("mouth_closed"), lowest_lip("mouth_open") + 0.1)

    def test_skin_is_the_swatch(self):
        self.assertIn((188, 97, 79), self.colours("mouth_open"))
        self.assertIn((0, 0, 0), self.colours("brow"))


class Voice(unittest.TestCase):
    def test_bundled_mouth_matches_the_shipped_head(self):
        got = bundled_voice()
        self.assertEqual(sorted(got), sorted([*SHIPPED_MOUTH, "underground"]))
        for name, mouth in SHIPPED_MOUTH.items():
            self.assertEqual(got[name], (SHIPPED_MILLIS[name], mouth), name)

    def test_underground_line_is_bundled_and_spoken(self):
        millis, mouth = bundled_voice()["underground"]
        self.assertTrue(2500 <= millis <= 2800, millis)
        self.assertEqual(len(mouth), -(-millis * build_head.MOUTH_FPS // 1000), len(mouth))
        self.assertGreater(max(mouth), 200)
        self.assertGreater(sum(1 for v in mouth if v >= 70), len(mouth) // 3)
        path = os.path.join(build_head.SOUND_DIR, "underground.ogg")
        probe = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "stream=sample_rate,channels",
                                "-of", "csv=p=0", path], check=True, capture_output=True, text=True, timeout=60)
        self.assertEqual(probe.stdout.strip(), "48000,1")

    def test_bundled_clips_exist(self):
        for name in SHIPPED_MOUTH:
            path = os.path.join(build_head.SOUND_DIR, name + ".ogg")
            self.assertTrue(os.path.getsize(path) > 4000, path)

    def test_envelope_of_silence_then_tone(self):
        rate = build_head.MOUTH_RATE
        t = np.arange(rate) / rate
        audio = np.where(t > 0.5, 0.5 * np.sin(2 * np.pi * 440 * t), 0.0)
        mouth = build_head.mouth_envelope(audio, rate)
        self.assertEqual(len(mouth), 30)
        self.assertEqual(max(mouth[:14]), 0)
        self.assertGreater(min(mouth[20:]), 200)

    def test_java_voice_shape(self):
        src = build_head.java_voice([("hello", 10, [1, 2]), ("mmm1", 5, [3]),
                                     ("mmm2", 5, [4]), ("mmm3", 5, [5])])
        self.assertIn('HELLO = new Clip("hello", 10, new int[] {', src)
        self.assertIn("HUMS = { MMM1, MMM2, MMM3 }", src)


class Sources(unittest.TestCase):
    def test_wrong_hash_is_refused(self):
        with tempfile.TemporaryDirectory() as d:
            path = os.path.join(d, "voice1.wav")
            with open(path, "wb") as f:
                f.write(b"not the voice")
            with self.assertRaises(SystemExit) as caught:
                build_head.verified(path, "voice")
            self.assertIn("refused: source voice sha256=", str(caught.exception))

    def test_missing_source_is_refused(self):
        with self.assertRaises(SystemExit) as caught:
            build_head.verified("/nonexistent/head.blend", "blend")
        self.assertIn("refused: source blend missing", str(caught.exception))


if __name__ == "__main__":
    unittest.main()
