#!/usr/bin/env python3
"""Write the game test arena: open air, 20 by 20 and 8 high, over the framework's barrier floor."""

import os

SIZE = (20, 8, 20)
DATA_VERSION = 5023
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "src", "gametest", "resources",
                   "data", "mariohead-gametest", "gametest", "structure", "arena.snbt")


def main():
    blocks = []
    for y in range(SIZE[1]):
        for z in range(SIZE[2]):
            for x in range(SIZE[0]):
                blocks.append(f"{{pos: [{x}, {y}, {z}], state: 0}}")
    text = (f"{{DataVersion: {DATA_VERSION}, size: [{SIZE[0]}, {SIZE[1]}, {SIZE[2]}], entities: [], "
            f"palette: [{{Name: \"minecraft:air\"}}], blocks: [\n"
            + ",\n".join(blocks) + "\n]}\n")
    with open(os.path.normpath(OUT), "w", encoding="utf-8") as f:
        f.write(text)
    print(f"[arena] blocks={len(blocks)} bytes={len(text)}")


if __name__ == "__main__":
    main()
