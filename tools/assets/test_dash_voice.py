#!/usr/bin/env python3
"""Check dash dialogue sources and the full-length digging hums."""

import hashlib
import json
from pathlib import Path
import subprocess
import unittest

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / 'src/main/resources/assets/mariohead'


class DashVoiceTests(unittest.TestCase):
    def test_requested_recordings_and_subtitles(self):
        entries = json.loads(Path(__file__).with_name('dash-lines.json').read_text())
        self.assertEqual({int(e['id'][:3]) for e in entries}, {67, 73, 75, 86})
        sounds = json.loads((ASSETS / 'sounds.json').read_text())
        language = json.loads((ASSETS / 'lang/en_us.json').read_text())
        for entry in entries:
            name = 'dash_' + entry['id'][:3]
            audio = ASSETS / 'sounds/the_head' / (name + '.ogg')
            self.assertEqual(hashlib.sha256(audio.read_bytes()).hexdigest(), entry['sha256'])
            event = sounds['entity.the_head.' + name]
            self.assertEqual(event['sounds'], ['mariohead:the_head/' + name])
            self.assertIn(event['subtitle'], language)

    def test_digging_hums_preserve_the_full_recordings(self):
        for index, seconds in enumerate((1.55, 2.2, 1.94), 1):
            path = ASSETS / 'sounds/the_head' / f'dig_mmm{index}.ogg'
            result = subprocess.run(['ffprobe', '-v', 'error', '-show_entries',
                                     'stream=channels,duration,sample_rate', '-of', 'json', str(path)],
                                    capture_output=True, text=True, check=True, timeout=10)
            stream = json.loads(result.stdout)['streams'][0]
            self.assertEqual(stream['channels'], 1)
            self.assertEqual(stream['sample_rate'], '48000')
            self.assertAlmostEqual(float(stream['duration']), seconds, delta=0.002)
            self.assertEqual(path.read_bytes(), (ASSETS / 'sounds/the_head' / f'mmm{index}_loud.ogg').read_bytes())


if __name__ == '__main__':
    unittest.main()
