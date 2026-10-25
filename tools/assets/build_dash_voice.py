#!/usr/bin/env python3
"""Bundle dash dialogue and half-second digging hums."""

import argparse
from pathlib import Path
import subprocess
import sys

sys.path.insert(0, str(Path(__file__).resolve().parent))
import build_head
import build_taunts


def build(source):
    build_taunts.build(source, 'dash-lines.json', 'dash', 'DashVoice')
    for index, millis in enumerate((1550, 2200, 1940), 1):
        factor = (millis / 500) ** (1 / 3)
        filters = ','.join([f'atempo={factor}'] * 3)
        filters += ',apad,atrim=duration=0.5,afade=t=out:st=0.48:d=0.02'
        subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i',
                        str(Path(build_head.SOUND_DIR) / f'mmm{index}_loud.ogg'),
                        '-af', filters, '-ac', '1', '-ar', '48000', '-c:a', 'libvorbis', '-q:a', '5',
                        str(Path(build_head.SOUND_DIR) / f'dig_mmm{index}.ogg')], check=True, timeout=30)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source', type=Path)
    build(parser.parse_args().source)
