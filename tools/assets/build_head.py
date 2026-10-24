#!/usr/bin/env python3
"""Build the head's bundled assets from the user's source files.

model: the blend and its PNGs -> the mesh, cap, iris and skin textures
voice: the voice clip and the full voice track -> four OGG clips and HeadVoice.java

Sources are pinned by SHA-256 in sources.json. Their paths come from local.properties at the
repository root (head.blend, head.voice, head.track) or from the command line.
"""

import argparse
import hashlib
import json
import os
import subprocess
import sys
import tempfile

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import head_mesh  # noqa: E402

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "mariohead")
MESH_PATH = os.path.join(ASSETS, "mesh", "the_head.mesh")
TEXTURE_DIR = os.path.join(ASSETS, "textures", "entity", "the_head")
SOUND_DIR = os.path.join(ASSETS, "sounds", "the_head")
JAVA_DIR = os.path.join(ROOT, "src", "main", "java", "com", "larsoyd", "mariohead", "entity")
VOICE_JAVA = os.path.join(JAVA_DIR, "HeadVoice.java")
SHAPE_JAVA = os.path.join(JAVA_DIR, "HeadShape.java")
SOURCES = os.path.join(os.path.dirname(os.path.abspath(__file__)), "sources.json")

BLOCKS_PER_UNIT = 1.275     # blend units to blocks: the head is 1.75 units tall and 2.23 blocks
LODS = (2, 1)               # subdivision level of each LOD, nearest first
PART_OF = {"mario_eyebrow": "brow", "mario_iris": "eye", "mario_face_closed": "mouth_closed",
           "mario_face": "mouth_open", "mario_teeth": "mouth_open"}
TEXTURE_OF = {"mario_head_swatch": "skin", "mario_head_hat": "cap", "mario_iris": "iris"}
CAVITY = (133, 0, 5)
CLOSED_SEAM = (40, 16, 12)  # the shut mouth keeps a thin dark seam where the cavity was
TONGUE = (150, 40, 40)
THROAT = (132, 38, 42)
TONGUE_CENTRE = (0.0, -0.54, 0.30)
THROAT_CENTRE = (0.0, -0.08, 0.47)
HULL_ROLL = 22.5            # the largest lean into a turn, the box has to hold it
HULL_PITCH = 10.0
HULL_BROW = 5.5
HULL_EYE = 6.5
HULL_MARGIN = 0.01

VOICE_RATE = 48000
MOUTH_RATE = 22050          # the mouth envelope is measured at this rate, frame for frame
MOUTH_FPS = 30
HELLO_CUT = ("track", 0.82, 1.38)
FLY_CUT = ("voice", 0.20, 1.60)
COMMA_PAUSE = 0.22
HUM_CUTS = [("voice", 2.30, 3.85), ("voice", 3.96, 6.16), ("voice", 6.72, 8.66)]
SPEECH_GAIN_DB = 4.0
HUM_GAIN_DB = 10.0
FADE = 0.008
CEILING = 0.95
MOUTH_BAND = (200.0, 3000.0)
MOUTH_RANGE_DB = 24.0
MOUTH_OPEN_RATE = 0.55
MOUTH_CLOSE_RATE = 0.30


def log(tag, **fields):
    print(f"[{tag}] " + " ".join(f"{k}={v}" for k, v in fields.items()), flush=True)


def local_properties():
    path = os.path.join(ROOT, "local.properties")
    props = {}
    if os.path.exists(path):
        with open(path, encoding="utf-8") as f:
            lines = f.read().splitlines()
        for line in lines:
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                key, value = line.split("=", 1)
                props[key.strip()] = os.path.expanduser(value.strip())
    return props


def verified(path, key):
    """The path, if it is the pinned source file. Anything else is refused."""
    with open(SOURCES, encoding="utf-8") as f:
        want = json.load(f)[key]
    if not path or not os.path.isfile(path):
        sys.exit(f"refused: source {key} missing at {path!r}")
    with open(path, "rb") as f:
        got = hashlib.sha256(f.read()).hexdigest()
    if got != want:
        sys.exit(f"refused: source {key} sha256={got} expected={want} path={path}")
    return path


# ------------------------------------------------------------------------------------ model ---

def run_blender(blend, level, out):
    script = os.path.join(os.path.dirname(os.path.abspath(__file__)), "blend_dump.py")
    done = subprocess.run(["blender", "--background", blend, "--python-exit-code", "1",
                           "--python", script, "--", out, str(level)],
                          capture_output=True, text=True, timeout=180)
    if done.returncode != 0 or f"DUMPED {level}" not in done.stdout:
        sys.exit(f"refused: blender dump failed level={level} exit={done.returncode}\n"
                 + done.stdout[-2000:] + done.stderr[-2000:])
    with open(out, encoding="utf-8") as f:
        return json.load(f)


def to_model(p, centre):
    """Blend (x right, y back, z up, facing -y) to model space (x, y up, z front), in blocks.

    centre is the blend height of the middle of the head and the blend depth he turns about.
    """
    return (p[0] * BLOCKS_PER_UNIT, (p[2] - centre[0]) * BLOCKS_PER_UNIT,
            (centre[1] - p[1]) * BLOCKS_PER_UNIT)


def model_normal(n):
    v = np.array([n[0], n[2], -n[1]], np.float64)
    length = np.linalg.norm(v)
    return tuple(v / length) if length > 1e-9 else (0.0, 1.0, 0.0)


def cell_colour(swatch, cell):
    h, w = swatch.shape[:2]
    x = min(w - 1, max(0, int(cell[0] * w)))
    y = min(h - 1, max(0, int((1.0 - cell[1]) * h)))
    return tuple(int(c) for c in swatch[y, x][:3])


def fan(centre, ring, closed, want):
    """Triangles of a fan, wound to face the model space direction want."""
    tris = [(0, i + 1, (i + 1) % len(ring) + 1) if closed else (0, i + 1, i + 2)
            for i in range(len(ring) if closed else len(ring) - 1)]
    pts = [centre] + ring
    a, b, c = (np.array(pts[i]) for i in tris[0])
    if np.dot(np.cross(b - a, c - b), want) < 0:
        tris = [t[::-1] for t in tris]
    return pts, tris


def add_fan(section, blend_pts, tris, centre, colour, normal=None):
    pts = [np.array(to_model(p, centre)) for p in blend_pts]
    normals = [np.zeros(3) for _ in pts]
    for t in tris:
        n = np.cross(pts[t[1]] - pts[t[0]], pts[t[2]] - pts[t[1]])
        for i in t:
            normals[i] += n
    for t in tris:
        section.quad([(tuple(pts[i]), normal or tuple(normals[i] / np.linalg.norm(normals[i])),
                       (0.5, 0.5), colour + (255,)) for i in t])


def mouth_inside(sections, centre):
    """The throat plate and the tongue, which only the open face shows."""
    tc = np.array(THROAT_CENTRE)
    ring = [tc + np.array([np.cos(a) * 0.36, 0.0, np.sin(a) * 0.19])
            for a in np.linspace(0.0, 2.0 * np.pi, 10, endpoint=False)]
    model_ring = [np.array(to_model(p, centre)) for p in ring]
    _, tris = fan(np.array(to_model(tc, centre)), model_ring, True, np.array([0.0, 0.0, 1.0]))
    add_fan(sections, [tc] + ring, tris, centre, THROAT, (0.0, 0.0, 1.0))
    gc = np.array(TONGUE_CENTRE)
    ring = []
    for k in range(7):
        a = np.pi * (0.08 + 0.84 * k / 6.0)
        ring.append(gc + np.array([np.cos(a) * 0.20, 0.13 - np.sin(a) * 0.04,
                                   -0.012 - np.sin(a) * 0.022]))
    model_ring = [np.array(to_model(p, centre)) for p in ring]
    _, tris = fan(np.array(to_model(gc, centre)), model_ring, False, np.array([0.0, 0.7, 0.7]))
    add_fan(sections, [gc] + ring, tris, centre, TONGUE)


def build_lod(dump, swatch, centre):
    sections = {}

    def section(part, texture):
        return sections.setdefault((part, texture), head_mesh.Section(part, texture))

    for name, obj in sorted(dump.items()):
        part = PART_OF.get(name, "root")
        texture = TEXTURE_OF[obj["material"]]
        target = section(part, texture)
        positions = obj["positions"]
        for poly in obj["polys"]:
            colour = (255, 255, 255)
            if texture == "skin":
                colour = cell_colour(swatch, poly["cell"])
                if part == "mouth_closed" and colour == CAVITY:
                    colour = CLOSED_SEAM
            target.quad([(to_model(positions[c["v"]], centre), model_normal(c["n"]),
                          (c["uv"][0], 1.0 - c["uv"][1]), colour + (255,))
                         for c in poly["corners"]])
    mouth_inside(section("mouth_open", "skin"), centre)
    return [sections[k] for k in sorted(sections, key=lambda k: (head_mesh.PARTS.index(k[0]),
                                                                  head_mesh.TEXTURES.index(k[1])))]


def pivot(dump):
    """The middle of the head's height, and the depth that keeps him narrowest as he turns."""
    p = np.array([q for obj in dump.values() for q in obj["positions"]])
    middle = (p[:, 2].min() + p[:, 2].max()) / 2.0
    depths = np.linspace(p[:, 1].min(), p[:, 1].max(), 401)
    radius = [np.sqrt(p[:, 0] ** 2 + (p[:, 1] - d) ** 2).max() for d in depths]
    return middle, float(depths[int(np.argmin(radius))])


def rotation(axis, degrees):
    a = np.radians(degrees)
    c, s = np.cos(a), np.sin(a)
    return {"x": np.array([[1, 0, 0], [0, c, -s], [0, s, c]]),
            "y": np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]]),
            "z": np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])}[axis]


def hull(lods):
    """Box width and height that hold every vertex at any yaw, lean, pitch, brow and glance."""
    points = []
    for sections in lods:
        for s in sections:
            p = np.array([v[0] for v in s.vertices], np.float64)
            if s.part == "brow":
                p = np.vstack([p, p @ rotation("x", HULL_BROW).T])
            elif s.part == "eye":
                p = np.vstack([p @ rotation("y", a).T for a in np.linspace(-HULL_EYE, HULL_EYE, 9)])
            points.append(p)
    p = np.vstack(points)
    radius, half = 0.0, 0.0
    for roll in np.linspace(-HULL_ROLL, HULL_ROLL, 61):
        for pitch in np.linspace(-HULL_PITCH, HULL_PITCH, 31):
            q = p @ (rotation("x", pitch) @ rotation("z", roll)).T
            radius = max(radius, float(np.sqrt(q[:, 0] ** 2 + q[:, 2] ** 2).max()))
            half = max(half, float(np.abs(q[:, 1]).max()))
    return 2.0 * (radius + HULL_MARGIN), 2.0 * (half + HULL_MARGIN)


def java_shape(width, height):
    return "\n".join([
        "// generated by build_head.py model, do not edit",
        "package com.larsoyd.mariohead.entity;", "",
        "public final class HeadShape {",
        f"\tpublic static final float WIDTH = {width:.4f}f;",
        f"\tpublic static final float HEIGHT = {height:.4f}f;",
        f"\tpublic static final float ROLL_MAX = {HULL_ROLL}f;",
        f"\tpublic static final float PITCH_MAX = {HULL_PITCH}f;", "",
        "\tprivate HeadShape() {", "\t}", "}", ""])


def build_model(blend):
    from PIL import Image

    tex_dir = os.path.dirname(blend)
    hat = verified(os.path.join(tex_dir, "mario_head_hat.png"), "hat")
    iris = verified(os.path.join(tex_dir, "mario_head_iris.png"), "iris")
    swatch_path = verified(os.path.join(tex_dir, "mario_head_swatch.png"), "swatch")
    swatch = np.asarray(Image.open(swatch_path).convert("RGB"))
    lods, centre = [], None
    with tempfile.TemporaryDirectory() as work:
        for level in LODS:
            dump = run_blender(blend, level, os.path.join(work, f"lod{level}.json"))
            if centre is None:
                centre = pivot(dump)
            lods.append(build_lod(dump, swatch, centre))
    os.makedirs(os.path.dirname(MESH_PATH), exist_ok=True)
    head_mesh.write(MESH_PATH, lods)
    os.makedirs(TEXTURE_DIR, exist_ok=True)
    Image.open(hat).convert("RGBA").save(os.path.join(TEXTURE_DIR, "cap.png"), optimize=True)
    Image.open(iris).convert("RGBA").save(os.path.join(TEXTURE_DIR, "iris.png"), optimize=True)
    Image.new("RGBA", (16, 16), (255, 255, 255, 255)).save(os.path.join(TEXTURE_DIR, "skin.png"))
    width, height = hull(lods)
    with open(SHAPE_JAVA, "w", encoding="utf-8") as f:
        f.write(java_shape(width, height))
    log("hull", width=f"{width:.4f}", height=f"{height:.4f}", roll=HULL_ROLL, pitch=HULL_PITCH)
    for level, sections in zip(LODS, lods):
        drawn = {s.part: 0 for s in sections}
        for s in sections:
            drawn[s.part] += len(s.quads)
        log("model", lod=level, sections=len(sections),
            quads=sum(len(s.quads) for s in sections),
            vertices=sum(len(s.vertices) for s in sections),
            parts=",".join(f"{p}:{n}" for p, n in drawn.items()))
    log("model", wrote=os.path.relpath(MESH_PATH, ROOT), bytes=os.path.getsize(MESH_PATH))


# ------------------------------------------------------------------------------------ voice ---

def decode_mono(path, start, end, rate):
    pcm = subprocess.run(
        ["ffmpeg", "-v", "error", "-ss", f"{start:.4f}", "-i", path, "-t", f"{end - start:.4f}",
         "-map", "0:a:0", "-ac", "1", "-ar", str(rate), "-f", "f32le", "-"],
        check=True, stdout=subprocess.PIPE, timeout=60).stdout
    return np.frombuffer(pcm, dtype=np.float32).astype(np.float64)


def limit(audio, ceiling, rate):
    """Look-ahead peak limiter, so the gain that carries his voice does not clip transients."""
    look, attack, release = int(0.010 * rate), int(0.005 * rate), int(0.120 * rate)
    peak = np.abs(audio)
    pad = np.concatenate([peak, np.zeros(look)])
    env = np.maximum.reduceat(pad, np.arange(0, len(pad), look))
    env = np.repeat(np.maximum(env, np.roll(env, -1)), look)[:len(peak)]
    want = np.minimum(1.0, ceiling / np.maximum(env, 1e-9))
    gain = np.empty_like(want)
    g, ka, kr = 1.0, 1.0 / attack, 1.0 / release
    for i in range(len(want)):
        g += (want[i] - g) * (ka if want[i] < g else kr)
        gain[i] = g
    return audio * gain


def fade_ends(audio, rate):
    n = max(1, int(FADE * rate))
    audio[:n] *= np.linspace(0, 1, n)
    audio[-n:] *= np.linspace(1, 0, n)
    return audio


def mouth_envelope(audio, rate):
    """Jaw opening per 30 Hz frame, 0..255, from the clip's own voice band loudness."""
    step = rate / MOUTH_FPS
    frames = max(1, int(np.ceil(len(audio) / step)))
    win = int(step)
    freqs = np.fft.rfftfreq(win, 1.0 / rate)
    band = (freqs >= MOUTH_BAND[0]) & (freqs <= MOUTH_BAND[1])
    levels = np.zeros(frames)
    for i in range(frames):
        x = audio[int(i * step):int(i * step) + win]
        if len(x) < win:
            x = np.concatenate([x, np.zeros(win - len(x))])
        power = (np.abs(np.fft.rfft(x * np.hanning(win))) ** 2)[band].sum()
        levels[i] = 10.0 * np.log10(power / win + 1e-12)
    out = np.clip((levels - (levels.max() - MOUTH_RANGE_DB)) / MOUTH_RANGE_DB, 0.0, 1.0)
    smooth, value = np.empty_like(out), 0.0
    for i, want in enumerate(out):
        value += (want - value) * (MOUTH_OPEN_RATE if want > value else MOUTH_CLOSE_RATE)
        smooth[i] = value
    return [int(v) for v in np.clip(np.round(smooth * 255), 0, 255)]


def clips(sources, rate):
    """The four clips as float audio at rate: hello first, then the three hums."""
    def cut(spec, gain_db):
        key, start, end = spec
        return decode_mono(sources[key], start, end, rate) * 10 ** (gain_db / 20)

    intro = np.concatenate([fade_ends(cut(HELLO_CUT, SPEECH_GAIN_DB), rate),
                            np.zeros(int(COMMA_PAUSE * rate)),
                            fade_ends(cut(FLY_CUT, SPEECH_GAIN_DB), rate)])
    out = [("hello", intro)] + [(f"mmm{i + 1}", cut(c, HUM_GAIN_DB)) for i, c in enumerate(HUM_CUTS)]
    finished = []
    for name, audio in out:
        audio = fade_ends(limit(audio, CEILING, rate), rate)
        peak = float(np.abs(audio).max())
        if peak > 0.98:
            sys.exit(f"refused: clip {name} peak={peak:.3f} after the limiter")
        finished.append((name, audio))
    return finished


def mouth_tracks(sources):
    """Envelopes measured on the clips at the mouth rate, padded to 16 samples like the original."""
    tracks = []
    for name, audio in clips(sources, MOUTH_RATE):
        audio = np.concatenate([audio, np.zeros((-len(audio)) % 16)])
        tracks.append((name, len(audio) * 1000 // MOUTH_RATE, mouth_envelope(audio, MOUTH_RATE)))
    return tracks


def write_ogg(path, audio):
    pcm = np.clip(audio, -1.0, 1.0).astype("<f4").tobytes()
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-f", "f32le", "-ar", str(VOICE_RATE),
                    "-ac", "1", "-i", "-", "-c:a", "libvorbis", "-q:a", "6", "-map_metadata", "-1",
                    "-fflags", "+bitexact", "-flags:a", "+bitexact", path],
                   input=pcm, check=True, timeout=60)


def java_voice(tracks):
    lines = ["// generated by build_head.py voice, do not edit",
             "package com.larsoyd.mariohead.entity;", "",
             "public final class HeadVoice {",
             f"\tpublic static final int MOUTH_FPS = {MOUTH_FPS};"]
    for name, millis, mouth in tracks:
        body = ", ".join(str(v) for v in mouth)
        wrapped, line = [], ""
        for value in body.split(", "):
            if len(line) + len(value) > 96:
                wrapped.append(line.rstrip())
                line = ""
            line += value + ", "
        wrapped.append(line.rstrip(", "))
        lines.append(f"\tpublic static final Clip {name.upper()} = new Clip(\"{name}\", {millis}, new int[] {{")
        lines += ["\t\t" + w for w in wrapped]
        lines.append("\t});")
    lines += ["\tpublic static final Clip[] HUMS = { MMM1, MMM2, MMM3 };", "",
              "\tprivate HeadVoice() {", "\t}", "",
              "\tpublic record Clip(String name, int millis, int[] mouth) {",
              "\t\tpublic int frames() {", "\t\t\treturn this.mouth.length;", "\t\t}", "\t}",
              "}", ""]
    return "\n".join(lines)


def build_voice(voice, track):
    sources = {"voice": verified(voice, "voice"), "track": verified(track, "track")}
    os.makedirs(SOUND_DIR, exist_ok=True)
    for name, audio in clips(sources, VOICE_RATE):
        path = os.path.join(SOUND_DIR, name + ".ogg")
        write_ogg(path, audio)
        log("voice", clip=name, seconds=f"{len(audio) / VOICE_RATE:.3f}",
            peak=f"{np.abs(audio).max():.3f}", bytes=os.path.getsize(path))
    tracks = mouth_tracks(sources)
    with open(VOICE_JAVA, "w") as f:
        f.write(java_voice(tracks))
    for name, millis, mouth in tracks:
        log("mouth", clip=name, millis=millis, frames=len(mouth), max=max(mouth))


def main():
    props = local_properties()
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("what", choices=("model", "voice", "all"))
    parser.add_argument("--blend", default=props.get("head.blend"))
    parser.add_argument("--voice", default=props.get("head.voice"))
    parser.add_argument("--track", default=props.get("head.track"))
    args = parser.parse_args()
    if args.what in ("model", "all"):
        build_model(verified(args.blend, "blend"))
    if args.what in ("voice", "all"):
        build_voice(args.voice, args.track)


if __name__ == "__main__":
    main()
