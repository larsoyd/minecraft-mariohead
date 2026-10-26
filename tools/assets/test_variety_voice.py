#!/usr/bin/env python3
"""Check chase variety recordings, timing and mouth animation."""
import hashlib
import json
from pathlib import Path
import re
import subprocess
import unittest

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / 'src/main/resources/assets/mariohead'
ENTRIES = json.loads(Path(__file__).with_name('variety-lines.json').read_text())


class VarietyVoiceTests(unittest.TestCase):
    def test_requested_new_recordings(self):
        ids = [e['id'].split('_')[0] for e in ENTRIES]
        expected = {'017', '036', '037', '043', '045', '049', '050', '051', '065',
                    '071', '074', '081', '084', '085', '101', '102', 'weee2',
                    'laugh1', 'laugh2', 'laugh3', 'laugh4', 'laugh5', 'drowning'}
        self.assertEqual(set(ids), expected)
        self.assertEqual(len(ids), len(expected))

    def test_full_recordings_and_animation(self):
        source_path = ROOT / 'src/main/java/com/larsoyd/mariohead/entity/VarietyVoice.java'
        self.assertTrue(source_path.exists(), 'missing variety mouth envelopes')
        source = source_path.read_text()
        sounds = json.loads((ASSETS / 'sounds.json').read_text())
        language = json.loads((ASSETS / 'lang/en_us.json').read_text())
        for entry in ENTRIES:
            name = 'variety_' + entry['id'].split('_')[0]
            with self.subTest(clip=name):
                path = ASSETS / 'sounds/the_head' / (name + '.ogg')
                self.assertTrue(path.exists(), 'missing ' + name)
                self.assertEqual(hashlib.sha256(path.read_bytes()).hexdigest(), entry['sha256'])
                probe = subprocess.run(['ffprobe', '-v', 'error', '-show_entries',
                                        'stream=sample_rate,channels,duration', '-of', 'json', str(path)],
                                       check=True, capture_output=True, text=True, timeout=10)
                stream = json.loads(probe.stdout)['streams'][0]
                self.assertEqual(stream['channels'], 1)
                self.assertEqual(stream['sample_rate'], '48000')
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
