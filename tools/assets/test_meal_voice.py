#!/usr/bin/env python3
"""Check the gulp and after-meal recordings, timing and mouth animation."""
import hashlib
import json
from pathlib import Path
import re
import subprocess
import unittest

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / 'src/main/resources/assets/mariohead'
ENTRIES = json.loads(Path(__file__).with_name('meal-lines.json').read_text())


class MealVoiceTests(unittest.TestCase):
    def test_exactly_the_requested_clips(self):
        self.assertEqual([e['id'] for e in ENTRIES], ['gulp', 'yummy', 'lasagna'])

    def test_sources_stay_pinned(self):
        for entry in ENTRIES:
            with self.subTest(clip=entry['id']):
                source = ROOT / 'voice-lines' / entry['source']
                self.assertEqual(hashlib.sha256(source.read_bytes()).hexdigest(), entry['source_sha256'])

    def test_clips_timing_and_animation(self):
        source = (ROOT / 'src/main/java/com/larsoyd/mariohead/entity/MealVoice.java').read_text()
        sounds = json.loads((ASSETS / 'sounds.json').read_text())
        language = json.loads((ASSETS / 'lang/en_us.json').read_text())
        for entry in ENTRIES:
            name = 'meal_' + entry['id']
            with self.subTest(clip=name):
                path = ASSETS / 'sounds/the_head' / (name + '.ogg')
                self.assertEqual(hashlib.sha256(path.read_bytes()).hexdigest(), entry['sha256'])
                probe = subprocess.run(['ffprobe', '-v', 'error', '-show_entries', 'stream=channels,duration',
                                        '-of', 'json', str(path)],
                                       check=True, capture_output=True, text=True, timeout=10)
                stream = json.loads(probe.stdout)['streams'][0]
                self.assertEqual(stream['channels'], 1)
                self.assertAlmostEqual(float(stream['duration']) * 1000, entry['millis'], delta=2)
                event = sounds['entity.the_head.' + name]
                self.assertEqual(event['sounds'], ['mariohead:the_head/' + name])
                self.assertEqual(language[event['subtitle']], entry['text'])
                match = re.search(r'"' + name + r'", (\d+), new int\[\] \{([^}]+)', source)
                self.assertIsNotNone(match)
                self.assertEqual(int(match[1]), entry['millis'])
                mouth = [int(v) for v in re.findall(r'\d+', match[2])]
                self.assertAlmostEqual(len(mouth), entry['millis'] * 30 / 1000, delta=2)
                self.assertGreater(max(mouth), 0)


if __name__ == '__main__':
    unittest.main()
