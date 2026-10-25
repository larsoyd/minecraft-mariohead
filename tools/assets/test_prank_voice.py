#!/usr/bin/env python3
"""Check prank recordings and the closed-mouth laugh."""
import hashlib
import json
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / 'src/main/resources/assets/mariohead'

class PrankVoiceTests(unittest.TestCase):
    def test_original_recordings_and_sound_events(self):
        for number in ('056', '057'):
            source = next((ROOT / 'voice-lines/ogg').glob(number + '_*.ogg'))
            bundled = ASSETS / 'sounds/the_head' / ('prank_' + number + '.ogg')
            self.assertTrue(bundled.exists(), 'missing prank recording ' + number)
            self.assertEqual(hashlib.sha256(bundled.read_bytes()).digest(), hashlib.sha256(source.read_bytes()).digest())
            sound = json.loads((ASSETS / 'sounds.json').read_text())['entity.the_head.prank_' + number]
            self.assertEqual(sound['sounds'], ['mariohead:the_head/prank_' + number])
            self.assertIn(sound['subtitle'], json.loads((ASSETS / 'lang/en_us.json').read_text()))

    def test_laugh_keeps_full_audio_duration_but_closes_mouth(self):
        path = ROOT / 'src/main/java/com/larsoyd/mariohead/entity/PrankVoice.java'
        self.assertTrue(path.exists(), 'missing prank mouth envelope')
        match = re.search(r'"prank_057", (\d+), new int\[\] \{([^}]+)', path.read_text())
        self.assertIsNotNone(match)
        self.assertEqual(int(match[1]), 2225)
        mouth = [int(v) for v in re.findall(r'\d+', match[2])]
        self.assertTrue(any(v >= 70 for v in mouth[:45]))
        self.assertTrue(len(mouth) >= 66)
        self.assertTrue(all(v == 0 for v in mouth[45:]), 'laugh must not flap mouth')

if __name__ == '__main__': unittest.main()
