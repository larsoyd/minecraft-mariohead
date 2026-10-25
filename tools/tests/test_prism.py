import json
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]


class PrismTests(unittest.TestCase):
    def test_setup_preserves_worlds_and_refuses_retargeting(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            mc = root / 'existing game'
            (mc / 'saves/world').mkdir(parents=True)
            world = mc / 'saves/world/level.dat'
            world.write_bytes(b'world data')
            prism = root / 'prism'
            command = ['python3', str(ROOT / 'tools/setup_prism.py'), str(prism), str(mc),
                       '26.3', '0.20.1', '/test/java']
            result = subprocess.run(command, capture_output=True, text=True, timeout=10)
            self.assertEqual(result.returncode, 0, result.stderr)
            instance = prism / 'instances/mariohead'
            self.assertEqual((instance / 'minecraft').resolve(), mc)
            self.assertEqual((instance / 'minecraft/saves/world/level.dat').read_bytes(), b'world data')
            pack = json.loads((instance / 'mmc-pack.json').read_text())
            self.assertEqual({c['uid']: c['version'] for c in pack['components']},
                             {'net.minecraft': '26.3', 'net.fabricmc.fabric-loader': '0.20.1'})
            self.assertIn('JavaPath=/test/java', (instance / 'instance.cfg').read_text())
            result = subprocess.run(command, capture_output=True, text=True, timeout=10)
            self.assertEqual(result.returncode, 0, result.stderr)
            other = root / 'other'
            other.mkdir()
            command[3] = str(other)
            result = subprocess.run(command, capture_output=True, text=True, timeout=10)
            self.assertNotEqual(result.returncode, 0)
            self.assertEqual(world.read_bytes(), b'world data')


if __name__ == '__main__':
    unittest.main()
