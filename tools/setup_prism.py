import configparser
import json
from pathlib import Path
import sys


def setup(prism, minecraft, version, loader, java):
    minecraft = Path(minecraft).resolve(strict=True)
    prism = Path(prism)
    config = configparser.ConfigParser(interpolation=None)
    config.read(prism / 'prismlauncher.cfg')
    instances = Path(config.get('General', 'InstanceDir', fallback='instances'))
    if not instances.is_absolute():
        instances = prism / instances
    instance = instances / 'mariohead'
    game = instance / 'minecraft'
    if instance.exists():
        if not game.is_symlink() or game.resolve() != minecraft:
            raise ValueError(f'{instance} already exists with a different game folder')
        return
    instance.mkdir(parents=True)
    game.symlink_to(minecraft, target_is_directory=True)
    (instance / 'instance.cfg').write_text(
        '[General]\nInstanceType=OneSix\nname=Mario Head\n'
        f'OverrideJavaLocation=true\nJavaPath={java}\n'
        'OverrideMemory=true\nMinMemAlloc=512\nMaxMemAlloc=4096\n')
    (instance / 'mmc-pack.json').write_text(json.dumps({
        'formatVersion': 1,
        'components': [
            {'uid': 'net.minecraft', 'version': version, 'important': True},
            {'uid': 'net.fabricmc.fabric-loader', 'version': loader},
        ],
    }, indent=2) + '\n')


if __name__ == '__main__':
    try:
        setup(*sys.argv[1:])
    except (OSError, ValueError, configparser.Error) as error:
        sys.exit(f'[play] refused: {error}')
