#!/usr/bin/env python3
"""Check the song recordings, their spacing and the taped applause outro."""
import hashlib
import json
from pathlib import Path
import re
import subprocess
import unittest

import numpy as np

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / 'src/main/resources/assets/mariohead'
ENTRIES = json.loads(Path(__file__).with_name('song-lines.json').read_text())
SONG_JAVA = ROOT / 'src/main/java/com/larsoyd/mariohead/entity/HeadSong.java'
RATE = 48000


def decode(name):
    pcm = subprocess.run(['ffmpeg', '-v', 'error', '-i', str(ASSETS / 'sounds/the_head' / (name + '.ogg')),
                          '-ac', '1', '-ar', str(RATE), '-f', 'f32le', '-'],
                         check=True, stdout=subprocess.PIPE, timeout=30).stdout
    return np.frombuffer(pcm, dtype=np.float32)


def rms_db(audio):
    return 20 * np.log10(np.sqrt(np.mean(audio ** 2)) + 1e-9)


def window(audio, start, end):
    return audio[int(start * RATE):int(end * RATE)]


def edges(audio, floor=-45):
    step = 240
    levels = [rms_db(audio[i:i + step]) for i in range(0, len(audio) - step, step)]
    loud = [i for i, level in enumerate(levels) if level > floor]
    return loud[0] * step / RATE, (loud[-1] + 1) * step / RATE


def entry(clip):
    return next(e for e in ENTRIES if e['id'] == clip)


class SongVoiceTests(unittest.TestCase):
    def test_exactly_the_requested_clips(self):
        self.assertEqual([e['id'] for e in ENTRIES], ['ask', 'verse1', 'verse2', 'outro'])
        self.assertIn('032_01m47s', entry('ask')['source'])
        self.assertIn('033_01m50s', entry('verse1')['source'])
        self.assertIn('034_01m56s', entry('verse2')['source'])

    def test_clips_timing_and_animation(self):
        source = (ROOT / 'src/main/java/com/larsoyd/mariohead/entity/SongVoice.java').read_text()
        sounds = json.loads((ASSETS / 'sounds.json').read_text())
        language = json.loads((ASSETS / 'lang/en_us.json').read_text())
        for clip in ENTRIES:
            name = 'song_' + clip['id']
            with self.subTest(clip=name):
                path = ASSETS / 'sounds/the_head' / (name + '.ogg')
                self.assertEqual(hashlib.sha256(path.read_bytes()).hexdigest(), clip['sha256'])
                probe = subprocess.run(['ffprobe', '-v', 'error', '-show_entries',
                                        'stream=sample_rate,channels,duration', '-of', 'json', str(path)],
                                       check=True, capture_output=True, text=True, timeout=10)
                stream = json.loads(probe.stdout)['streams'][0]
                self.assertEqual(stream['channels'], 1)
                self.assertEqual(stream['sample_rate'], '48000')
                self.assertAlmostEqual(float(stream['duration']) * 1000, clip['millis'], delta=2)
                event = sounds['entity.the_head.' + name]
                self.assertEqual(event['sounds'], ['mariohead:the_head/' + name])
                self.assertEqual(language[event['subtitle']], clip['text'])
                match = re.search(r'"' + name + r'", (\d+), new int\[\] \{([^}]+)', source)
                self.assertIsNotNone(match)
                self.assertEqual(int(match[1]), clip['millis'])
                mouth = [int(v) for v in re.findall(r'\d+', match[2])]
                self.assertAlmostEqual(len(mouth), clip['millis'] * 30 / 1000, delta=2)
                self.assertGreater(max(mouth), 0)

    def test_pauses_are_heard_between_the_words_not_the_files(self):
        constants = dict(re.findall(r'(\w+_TICKS) = (\d+);', SONG_JAVA.read_text()))
        ask, first, second, outro = (decode('song_' + e['id']) for e in ENTRIES)
        heard = [
            (int(constants['VERSE1_TICKS']) * 0.05 - edges(ask)[1] + edges(first)[0], 0.8),
            (int(constants['VERSE2_TICKS']) * 0.05 - edges(first)[1] + edges(second)[0], 0.5),
            (int(constants['OUTRO_TICKS']) * 0.05 - edges(second)[1] + edges(outro, -35)[0], 1.0),
        ]
        for got, want in heard:
            self.assertAlmostEqual(got, want, delta=0.04)

    def test_outro_layers_and_abrupt_tape_stop(self):
        recipe = entry('outro')['mix']
        outro = decode('song_outro')
        applause_at, voice_at, stop_at = recipe['applause_at'], recipe['voice_at'], recipe['stop_at']
        self.assertGreaterEqual(voice_at - applause_at, 0.5, 'he speaks before the applause registers')
        self.assertAlmostEqual(stop_at, recipe['voice_end'] - 0.1, delta=0.005)
        self.assertLessEqual(np.abs(outro).max(), 0.98)
        tape_bed = rms_db(window(outro, applause_at + 0.3, voice_at))
        late_bed = rms_db(window(outro, applause_at + 0.4, voice_at))
        voice = rms_db(window(outro, voice_at + 0.1, stop_at - 0.1))
        self.assertGreater(tape_bed, -34, 'applause not up before he speaks')
        self.assertLess(abs(late_bed - tape_bed), 3, 'applause still swelling when he speaks')
        self.assertLess(rms_db(window(outro, applause_at, applause_at + 0.08)), tape_bed - 3, 'applause has no build up')
        self.assertLess(tape_bed, -24, 'applause too loud')
        self.assertGreater(voice, tape_bed + 9, 'thank you is not on top')
        before = rms_db(window(outro, stop_at - 0.02, stop_at))
        self.assertGreater(before, -40, 'nothing left to cut at the stop')
        tail = rms_db(window(outro, stop_at + 0.15, stop_at + 0.25))
        self.assertLess(tail, before - 18, 'applause kept running past the stop')

    def test_mix_inputs_are_tracked_and_pinned(self):
        for clip in ENTRIES:
            self.assertEqual(hashlib.sha256((ROOT / 'voice-lines' / clip['source']).read_bytes()).hexdigest(), clip['source_sha256'])
        for source, pin in entry('outro')['mix']['inputs'].items():
            self.assertEqual(hashlib.sha256((ROOT / 'voice-lines' / source).read_bytes()).hexdigest(), pin)

    def test_mouth_stays_shut_over_the_tape_lead_in(self):
        source = (ROOT / 'src/main/java/com/larsoyd/mariohead/entity/SongVoice.java').read_text()
        match = re.search(r'"song_outro", \d+, new int\[\] \{([^}]+)', source)
        mouth = [int(v) for v in re.findall(r'\d+', match[1])]
        self.assertEqual(max(mouth[:int(entry('outro')['mix']['voice_at'] * 30)]), 0)


if __name__ == '__main__':
    unittest.main()
