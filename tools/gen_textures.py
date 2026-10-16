"""Generates placeholder textures with the standard library only (no Pillow needed).

Run from the project root:  python tools/gen_textures.py
Outputs are placeholders; replace them with your own art at the same paths and sizes.
"""
import math
import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src" / "main" / "resources" / "assets" / "mehburity"
ENTITY = ASSETS / "textures" / "entity" / "mehburity"

NEON_CYAN = (0, 240, 255, 255)
CYAN_SHADE = (0, 150, 170, 255)
INK = (10, 10, 18, 255)
BACKGROUND = (7, 7, 11, 255)
CARDBOARD = (181, 131, 79)
CARDBOARD_EDGE = (138, 96, 54)
CARDBOARD_INSIDE = (74, 50, 32)
TAPE = (222, 196, 140)


def write_png(path: Path, width: int, height: int, pixels) -> None:
    """pixels: callable (x, y) -> (r, g, b, a)."""
    raw = bytearray()
    for y in range(height):
        raw.append(0)  # filter type: none
        for x in range(width):
            raw.extend(pixels(x, y))

    def chunk(tag: bytes, data: bytes) -> bytes:
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(bytes(raw), 9))
    png += chunk(b"IEND", b"")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(png)


def face_ink(fx: float, fy: float) -> bool:
    """Verity-style face on a unit disc, y pointing down: two tall oval eyes and a thin smile."""
    for ex in (-0.28, 0.28):
        if ((fx - ex) / 0.075) ** 2 + ((fy + 0.25) / 0.16) ** 2 <= 1.0:
            return True
    smile_r = 0.58
    d = math.hypot(fx, fy + 0.22)
    return abs(d - smile_r) < 0.035 and fy > 0.14 and abs(fx) < 0.44


def icon(size: int):
    """Flat 2D smiley used as the mod icon."""
    c = (size - 1) / 2
    r = size * 0.44

    def px(x, y):
        dx, dy = (x - c) / r, (y - c) / r
        d = math.hypot(dx, dy)
        if d > 1.0:
            return BACKGROUND
        if face_ink(dx, dy):
            return INK
        t = max(0.0, min(1.0, (dx + dy) / 2 + 0.5))
        return tuple(int(a + (b - a) * t * 0.7) for a, b in zip(NEON_CYAN[:3], CYAN_SHADE[:3])) + (255,)

    return px


def sphere(width: int, height: int):
    """Equirectangular sphere texture: u = longitude (0.5 = front), v = latitude (0 = top).

    The face is drawn by projecting each texel straight onto the front plane, so it looks
    exactly like the flat design when Mehburity faces you.
    """

    def px(x, y):
        u = (x + 0.5) / width
        v = (y + 0.5) / height
        theta = v * math.pi
        phi = (u - 0.5) * 2 * math.pi
        dx = math.sin(theta) * math.sin(phi)
        dy = math.cos(theta)
        dz = math.sin(theta) * math.cos(phi)
        if dz > 0.0 and face_ink(dx, -dy):
            return INK
        # Slightly brighter towards the front so the face side reads as "the front".
        glow = 0.85 + 0.15 * max(0.0, dz)
        return tuple(int(c * glow) for c in NEON_CYAN[:3]) + (255,)

    return px


def noise(x: int, y: int) -> int:
    n = (x * 374761393 + y * 668265263) & 0xFFFFFFFF
    n = (n ^ (n >> 13)) * 1274126177 & 0xFFFFFFFF
    return (n >> 24) % 15 - 7


def box(width: int, height: int):
    """Cardboard for MehburityBoxModel: body cube at (0,0), front/back flaps at (0,24), side flaps at (0,32)."""

    def shade(rgb, amount):
        return tuple(max(0, min(255, c + amount)) for c in rgb) + (255,)

    def px(x, y):
        n = noise(x, y)
        # Body top face (inside of the box, visible once the flaps open).
        if 12 <= x < 24 and 0 <= y < 12:
            return shade(CARDBOARD_INSIDE, n)
        # Body side faces: darker 1-pixel border.
        if 0 <= x < 48 and 12 <= y < 24:
            local_x = x % 12
            if local_x in (0, 11) or y in (12, 23):
                return shade(CARDBOARD_EDGE, n)
            return shade(CARDBOARD, n)
        # Front/back flaps: tape strip down the middle, where the two flaps meet.
        if 0 <= x < 36 and 24 <= y < 30:
            if 6 <= x < 30 and (x - 6) % 12 in (5, 6):
                return shade(TAPE, n // 2)
            return shade(CARDBOARD, n)
        if 0 <= x < 36 and 32 <= y < 44:
            return shade(CARDBOARD_EDGE if (x % 6) in (0, 5) else CARDBOARD, n)
        return shade(CARDBOARD, n)

    return px


if __name__ == "__main__":
    outputs = [
        (ASSETS / "icon.png", 128, 128, icon(128)),
        (ENTITY / "mehburity.png", 128, 64, sphere(128, 64)),
        (ENTITY / "box.png", 64, 64, box(64, 64)),
    ]
    for path, w, h, fn in outputs:
        write_png(path, w, h, fn)
        print("wrote", path.relative_to(ROOT))
