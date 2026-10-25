#!/usr/bin/env python3
"""Bundle dash dialogue and full-length digging hums."""

import argparse
from pathlib import Path
import shutil
import sys

sys.path.insert(0, str(Path(__file__).resolve().parent))
import build_head
import build_taunts


def build(source):
    build_taunts.build(source, 'dash-lines.json', 'dash', 'DashVoice')
    for index in range(1, 4):
        shutil.copyfile(Path(build_head.SOUND_DIR) / f'mmm{index}_loud.ogg',
                        Path(build_head.SOUND_DIR) / f'dig_mmm{index}.ogg')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source', type=Path)
    build(parser.parse_args().source)
