#!/usr/bin/env python3
"""Validate the requested taunt recordings and game sound registrations."""

import hashlib
import json
from pathlib import Path
import subprocess
import unittest

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/mariohead"
ENTRIES = json.loads(Path(__file__).with_name("taunts.json").read_text())


class TauntTests(unittest.TestCase):
    def test_requested_clips_are_present_once(self):
        requested = {"006", "008", "009", "010", "016", "023", "026", "078", "082", "088", "089", "090",
                     "091", "098", "099", "100", "105", "106", "107", "yohooo", "tooslow"}
        ids = [entry["id"].split("_")[0] for entry in ENTRIES]
        self.assertEqual(set(ids), requested)
        self.assertEqual(len(ids), len(requested))

    def test_added_lines_keep_their_masters_pinned_and_stay_under_full_scale(self):
        added = [entry for entry in ENTRIES if "source" in entry]
        self.assertEqual([entry["id"] for entry in added], ["yohooo", "tooslow"])
        for entry in added:
            with self.subTest(clip=entry["id"]):
                source = ROOT / "voice-lines" / entry["source"]
                self.assertEqual(hashlib.sha256(source.read_bytes()).hexdigest(), entry["source_sha256"])
                self.assertEqual(hashlib.sha256((ROOT / "voice-lines/ogg" / (entry["id"] + ".ogg")).read_bytes()).hexdigest(), entry["sha256"])
                detect = subprocess.run(["ffmpeg", "-nostats", "-i", str(ASSETS / "sounds/the_head" / ("taunt_" + entry["id"] + ".ogg")),
                                         "-af", "volumedetect", "-f", "null", "-"],
                                        check=True, capture_output=True, text=True, timeout=10).stderr
                peak = float(detect.split("max_volume: ")[1].split(" dB")[0])
                self.assertLess(peak, -1.5)
                self.assertGreater(peak, -6)

    def test_bundled_audio_matches_pinned_sources_and_is_positional(self):
        for entry in ENTRIES:
            name = "taunt_" + entry["id"].split("_")[0]
            path = ASSETS / "sounds/the_head" / (name + ".ogg")
            with self.subTest(clip=name):
                self.assertEqual(hashlib.sha256(path.read_bytes()).hexdigest(), entry["sha256"])
                probe = subprocess.run(["ffprobe", "-v", "error", "-show_entries",
                                        "stream=sample_rate,channels,duration", "-of", "json", str(path)],
                                       check=True, capture_output=True, text=True, timeout=10)
                stream = json.loads(probe.stdout)["streams"][0]
                self.assertEqual(stream["channels"], 1)
                self.assertEqual(stream["sample_rate"], "48000")
                self.assertAlmostEqual(float(stream["duration"]) * 1000, entry["millis"], delta=2)

    def test_every_taunt_has_a_sound_event_and_subtitle(self):
        sounds = json.loads((ASSETS / "sounds.json").read_text())
        language = json.loads((ASSETS / "lang/en_us.json").read_text())
        for entry in ENTRIES:
            name = "taunt_" + entry["id"].split("_")[0]
            with self.subTest(clip=name):
                event = sounds.get("entity.the_head." + name)
                self.assertIsNotNone(event, "unregistered taunt " + name)
                self.assertEqual(event["sounds"], ["mariohead:the_head/" + name])
                self.assertIn(event["subtitle"], language)


if __name__ == "__main__":
    unittest.main()
