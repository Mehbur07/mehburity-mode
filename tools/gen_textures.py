"""Generates placeholder textures with the standard library only (no Pillow needed).

Run from the project root:  python tools/gen_textures.py
Outputs are placeholders; replace them with your own art at the same paths.
"""
import math
import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src" / "main" / "resources" / "assets" / "mehburity"

NEON_CYAN = (0, 240, 255, 255)
CYAN_SHADE = (0, 150, 170, 255)
INK = (10, 10, 18, 255)
BACKGROUND = (7, 7, 11, 255)


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


def smiley(size: int):
    """Neon cyan sphere with two oval eyes and a thin smile, shaded towards the lower right."""
    c = (size - 1) / 2
    r = size * 0.44

    def px(x, y):
        dx, dy = x - c, y - c
        d = math.hypot(dx, dy)
        if d > r:
            return BACKGROUND
        # Eyes: vertical ovals above the centre.
        for ex in (-0.16, 0.16):
            if ((dx / size - ex) / 0.035) ** 2 + ((dy / size + 0.10) / 0.07) ** 2 <= 1:
                return INK
        # Smile: thin arc below the centre.
        mr = size * 0.30
        md = math.hypot(dx, dy + size * 0.12)
        if abs(md - mr) < size * 0.012 and dy > size * 0.06 and abs(dx) < size * 0.22:
            return INK
        # Simple shading so it reads as a sphere.
        t = max(0.0, min(1.0, (dx + dy) / (2 * r) + 0.5))
        return tuple(int(a + (b - a) * t * 0.7) for a, b in zip(NEON_CYAN[:3], CYAN_SHADE[:3])) + (255,)

    return px


if __name__ == "__main__":
    write_png(ASSETS / "icon.png", 128, 128, smiley(128))
    print("wrote", ASSETS / "icon.png")
