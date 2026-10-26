#!/usr/bin/env python3
"""Check home dialogue assets and chat wiring."""

import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parent))
import build_head
import build_home_voice
import build_variety_voice

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / 'src/main/resources/assets/mariohead'
THREAT_HASH = 'ef3cd38582133098def7e039cbc6167aa13a728d96e55de7b1860cb0c35619b6'


class HomeVoiceTests(unittest.TestCase):
    def test_requested_sources_are_bundled(self):
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

    def test_threat_cues_use_the_approved_mix(self):
        for prefix, owner in [('home', 'HomeVoice'), ('variety', 'VarietyVoice')]:
            with self.subTest(cue=prefix):
                path = ASSETS / 'sounds/the_head' / (prefix + '_036.ogg')
                self.assertEqual(hashlib.sha256(path.read_bytes()).hexdigest(), THREAT_HASH)
                java = (Path(build_head.JAVA_DIR) / (owner + '.java')).read_text()
                clip = re.search(r'"' + prefix + r'_036", (\d+), new int\[\] \{([^}]+)', java)
                self.assertIsNotNone(clip)
                self.assertEqual(int(clip[1]), 4339)
                audio = build_head.decode_mono(str(path), 0, 4.339, build_head.MOUTH_RATE)
                self.assertEqual([int(v) for v in re.findall(r'\d+', clip[2])],
                                 build_head.mouth_envelope(audio, build_head.MOUTH_RATE))

    def test_approved_mix_survives_regeneration(self):
        approved = ASSETS / 'sounds/the_head/home_036.ogg'
        with tempfile.TemporaryDirectory(prefix='mariohead-threat-') as directory:
            with patch.object(build_head, 'SOUND_DIR', directory), patch.object(build_head, 'JAVA_DIR', directory):
                build_home_voice.build(ROOT / 'voice-lines/ogg', approved)
                build_variety_voice.build(ROOT / 'voice-lines', approved)
            for name in ['home_036.ogg', 'variety_036.ogg']:
                data = (Path(directory) / name).read_bytes()
                self.assertEqual(hashlib.sha256(data).hexdigest(), THREAT_HASH)

    def test_unapproved_threat_is_refused_before_writing(self):
        original = next((ROOT / 'voice-lines/ogg').glob('036_*.ogg'))
        for builder, source in [(build_home_voice, 'voice-lines/ogg'),
                                (build_variety_voice, 'voice-lines')]:
            with self.subTest(builder=builder.__name__), tempfile.TemporaryDirectory() as directory:
                with patch.object(build_head, 'SOUND_DIR', directory), patch.object(build_head, 'JAVA_DIR', directory):
                    with self.assertRaisesRegex(ValueError, 'source hash mismatch'):
                        builder.build(ROOT / source, original)
                self.assertEqual(list(Path(directory).iterdir()), [])


if __name__ == '__main__':
    unittest.main()
