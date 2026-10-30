#!/usr/bin/env python3
"""Bundle the pinned gulp and after-meal recordings with their mouth envelopes."""
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile

sys.path.insert(0, str(Path(__file__).resolve().parent))
import build_taunts

ROOT = Path(__file__).resolve().parents[2]


def build():
    entries = json.loads(Path(__file__).with_name('meal-lines.json').read_text())
    with tempfile.TemporaryDirectory(prefix='mariohead-meal-') as directory:
        staging = Path(directory)
        for entry in entries:
            source = ROOT / 'voice-lines' / entry['source']
            if hashlib.sha256(source.read_bytes()).hexdigest() != entry['source_sha256']:
                raise ValueError('source hash mismatch: ' + entry['source'])
            if source.suffix == '.ogg':
                shutil.copyfile(source, staging / (entry['id'] + '.ogg'))
            else:
                subprocess.run(['ffmpeg', '-v', 'error', '-nostdin', '-y', '-i', str(source),
                                '-map', '0:a:0', '-af', f"volume={entry.get('gain_db', 0)}dB",
                                '-ac', '1', '-ar', '48000', '-c:a', 'libvorbis',
                                '-q:a', '6', '-fflags', '+bitexact', '-flags:a', '+bitexact',
                                str(staging / (entry['id'] + '.ogg'))], check=True, timeout=30)
        build_taunts.build(staging, 'meal-lines.json', 'meal', 'MealVoice')


if __name__ == '__main__':
    build()
