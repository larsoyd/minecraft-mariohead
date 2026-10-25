#!/usr/bin/env python3
"""Check original home dialogue assets and chat wiring."""

import hashlib
import json
from pathlib import Path
import subprocess
import unittest

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / 'src/main/resources/assets/mariohead'


class HomeVoiceTests(unittest.TestCase):
    def test_requested_original_sources_are_bundled(self):
        entries = json.loads(Path(__file__).with_name('home-lines.json').read_text())
        self.assertEqual([int(e['id'][:3]) for e in entries], [53, 5, 59, 60, 36])
        sounds = json.loads((ASSETS / 'sounds.json').read_text())
        language = json.loads((ASSETS / 'lang/en_us.json').read_text())
        for entry in entries:
            name = 'home_' + entry['id'][:3]
            path = ASSETS / 'sounds/the_head' / (name + '.ogg')
            digest = hashlib.sha256(path.read_bytes()).hexdigest()
            self.assertEqual(digest, entry['sha256'])
            event = sounds['entity.the_head.' + name]
            self.assertEqual(event['sounds'], ['mariohead:the_head/' + name])
            self.assertEqual(language[event['subtitle']], entry['text'])
            result = subprocess.run(['ffprobe', '-v', 'error', '-show_entries',
                                     'stream=channels,duration,sample_rate', '-of', 'json', str(path)],
                                    capture_output=True, text=True, check=True, timeout=10)
            stream = json.loads(result.stdout)['streams'][0]
            self.assertEqual(stream['channels'], 1)
            self.assertEqual(stream['sample_rate'], '48000')
            self.assertAlmostEqual(float(stream['duration']),
                                   entry['millis'] / 1000, delta=0.002)
        self.assertIn('die!', language['chat.mariohead.the_head.home_return'])
        self.assertEqual(language['chat.mariohead.the_head.home_nice'], 'Hmm, you have a nice place here!')
        self.assertEqual(language['chat.mariohead.the_head.home_ask'], 'Can I have it?')


if __name__ == '__main__':
    unittest.main()
