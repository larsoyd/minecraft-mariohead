#!/usr/bin/env python3
"""Bundle chase variety lines and convert the pinned drowning recording."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile

sys.path.insert(0, str(Path(__file__).resolve().parent))
import build_taunts


def build(source):
    entries = json.loads(Path(__file__).with_name('variety-lines.json').read_text())
    with tempfile.TemporaryDirectory(prefix='mariohead-variety-') as directory:
        staging = Path(directory)
        for entry in entries:
            if entry['id'] == 'drowning':
                wav = source / 'wav/drowning.wav'
                if hashlib.sha256(wav.read_bytes()).hexdigest() != entry['source_sha256']:
                    raise ValueError('source hash mismatch: drowning.wav')
                subprocess.run(['ffmpeg', '-v', 'error', '-nostdin', '-y', '-i', str(wav),
                                '-map', '0:a:0', '-ac', '1', '-ar', '48000', '-c:a', 'libvorbis',
                                '-q:a', '6', '-fflags', '+bitexact', '-flags:a', '+bitexact',
                                str(staging / 'drowning.ogg')], check=True, timeout=30)
            else:
                shutil.copyfile(source / 'ogg' / (entry['id'] + '.ogg'), staging / (entry['id'] + '.ogg'))
        build_taunts.build(staging, 'variety-lines.json', 'variety', 'VarietyVoice')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source', type=Path, help='directory containing ogg and wav source folders')
    build(parser.parse_args().source)
