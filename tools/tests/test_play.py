#!/usr/bin/env python3
"""Exercise launcher selection without touching the user's game installation."""

import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import time
import unittest

ROOT = Path(__file__).resolve().parents[2]


class PlayTests(unittest.TestCase):
    def test_official_launch_uses_the_packaged_workaround(self):
        self.run_play("official", "wrapper")

    def test_default_launches_prism_instance(self):
        self.run_play("play", "--dir")

    def run_play(self, mode, expected):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            shutil.copyfile(ROOT / 'play.sh', root / 'play.sh')
            (root / 'tools').mkdir()
            shutil.copyfile(ROOT / 'tools/setup_prism.py', root / 'tools/setup_prism.py')
            (root / 'gradle.properties').write_text('version=0.1.0\n')
            (root / 'build/libs').mkdir(parents=True)
            (root / 'build/libs/mariohead-0.1.0.jar').write_text('test')
            mc = root / 'minecraft'
            (mc / 'mods').mkdir(parents=True)
            (mc / 'launcher_profiles.json').write_text('{}')
            (mc / 'mods/fabric-api-0.165.0+26.3.jar').write_text('test')
            cache = root / 'cache/mariohead'
            cache.mkdir(parents=True)
            (cache / 'fabric-installer-1.1.2.jar').write_text('test')
            commands = root / 'bin'
            commands.mkdir()
            scripts = {
                'gradlew': 'exit 0',
                'prismlauncher': 'printf \"%s\\n\" \"$@\" > \"$LAUNCH_RECORD\"',
                'java': 'exit 0',
                'curl': 'exit 99',
                'sha1sum': 'case "$1" in *installer*) echo "74685b5a0178c4dabd4be741a276d35c59b5ec96  $1";; *) echo "8f8a23d6c9fbf7b075b31b4fb0a1007b0488d1d2  $1";; esac',
                'minecraft-launcher.sh': 'echo wrapper > "$LAUNCH_RECORD"',
                'minecraft-launcher': 'echo binary > "$LAUNCH_RECORD"',
            }
            for name, body in scripts.items():
                path = root / name if name == 'gradlew' else commands / name
                path.write_text('#!/bin/sh\n' + body + '\n')
                path.chmod(0o755)
            record = root / 'launch'
            env = dict(os.environ, PATH=str(commands) + os.pathsep + os.environ['PATH'],
                       MINECRAFT_DIR=str(mc), XDG_CACHE_HOME=str(root / 'cache'), LAUNCH_RECORD=str(record), PRISM_DIR=str(root / 'prism'),
                       MARIOHEAD_JAVA=str(commands / 'java'))
            result = subprocess.run(['sh', str(root / 'play.sh'), mode], env=env,
                                    capture_output=True, text=True, timeout=15)
            self.assertEqual(result.returncode, 0, result.stderr)
            for _ in range(50):
                if record.exists():
                    break
                time.sleep(0.02)
            lines = record.read_text().splitlines()
            self.assertEqual(lines[0], expected)
            if mode == 'play':
                self.assertEqual(lines, ['--dir', str(root / 'prism'), '--launch', 'mariohead'])
                self.assertEqual((mc / 'mods/mariohead-0.1.0.jar').read_text(), 'test')


if __name__ == '__main__':
    unittest.main()
