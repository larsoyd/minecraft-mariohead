#!/usr/bin/env python3
"""Check the fallen taunt recording and its mouth envelope."""
import hashlib
import json
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / 'src/main/resources/assets/mariohead'


class FallVoiceTests(unittest.TestCase):
    def test_original_recording_subtitle_and_full_mouth_envelope(self):
        audio = ASSETS / 'sounds/the_head/fall_019.ogg'
        self.assertTrue(audio.exists(), 'missing fallen taunt recording')
        self.assertEqual(hashlib.sha256(audio.read_bytes()).hexdigest(),
                         '449f552f233438318c49cd1e544cf8b7f8800ae2536330c4de6478264c637a69')
        event = json.loads((ASSETS / 'sounds.json').read_text())['entity.the_head.fall_019']
        self.assertEqual(event['sounds'], ['mariohead:the_head/fall_019'])
        language = json.loads((ASSETS / 'lang/en_us.json').read_text())
        self.assertIn("can't get up", language[event['subtitle']])
        source = (ROOT / 'src/main/java/com/larsoyd/mariohead/entity/FallVoice.java').read_text()
        match = re.search(r'"fall_019", (\d+), new int\[\] \{([^}]+)', source)
        self.assertIsNotNone(match)
        self.assertEqual(int(match[1]), 3921)
        mouth = [int(v) for v in re.findall(r'\d+', match[2])]
        self.assertGreaterEqual(len(mouth), 117)
        self.assertTrue(any(v >= 70 for v in mouth))


if __name__ == '__main__':
    unittest.main()
