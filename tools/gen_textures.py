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
RAGE_RED = (235, 25, 35, 255)
CYAN_SHADE = (0, 150, 170, 255)
INK = (10, 10, 18, 255)
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


def angry_face_ink(fx: float, fy: float) -> bool:
    """The same face in a rage: eyes cut by slanted brows (high outside, low inside) and a frown."""
    for ex in (-0.28, 0.28):
        in_eye = ((fx - ex) / 0.085) ** 2 + ((fy + 0.22) / 0.14) ** 2 <= 1.0
        # Brow line: higher on the outside, lower towards the nose (y points down).
        brow_y = -0.30 - 0.45 * (abs(fx) - 0.28)
        if in_eye and fy > brow_y:
            return True
        # The brow itself, a thick slanted bar above each eye.
        if abs(fx - ex) < 0.14 and abs(fy - (brow_y - 0.06)) < 0.035:
            return True
    frown_r = 0.5
    d = math.hypot(fx, fy - 0.62)
    return abs(d - frown_r) < 0.035 and fy < 0.3 and abs(fx) < 0.38


def icon(size: int, samples: int = 4):
    """Mod icon / logo: Mehburity's head, a shaded neon sphere turned a little to the side, with a soft glow
    on a transparent background. Supersampled for smooth edges."""
    c = size / 2
    r = size * 0.40
    halo = 1.22  # glow reaches this far (in sphere radii)
    light = normalize((-0.55, 0.6, 0.6))
    half = normalize((light[0], light[1], light[2] + 1.0))
    yaw = math.radians(-18.0)
    cos_y, sin_y = math.cos(yaw), math.sin(yaw)

    def sample(sx, sy):
        d2 = sx * sx + sy * sy
        if d2 > 1.0:
            d = math.sqrt(d2)
            if d > halo:
                return (0.0, 0.0, 0.0, 0.0)
            a = ((halo - d) / (halo - 1.0)) ** 2 * 0.55
            return (NEON_CYAN[0] / 255, NEON_CYAN[1] / 255, NEON_CYAN[2] / 255, a)
        n = (sx, -sy, math.sqrt(1.0 - d2))
        # Turn the face: the point in the sphere's own frame.
        ox = n[0] * cos_y - n[2] * sin_y
        oz = n[0] * sin_y + n[2] * cos_y
        oy = n[1]
        inked = oz > 0.0 and face_ink(ox, -oy)
        diffuse = max(0.0, dot(n, light))
        spec = max(0.0, dot(n, half)) ** 40
        rim = (1.0 - n[2]) ** 3
        base = INK if inked else NEON_CYAN
        shade = 0.38 + 0.72 * diffuse
        out = []
        for i in range(3):
            ch = base[i] / 255 * shade + spec * (0.35 if inked else 0.6)
            if not inked:
                ch += rim * 0.35 * (NEON_CYAN[i] / 255)
            out.append(min(1.0, ch))
        return (out[0], out[1], out[2], 1.0)

    def px(x, y):
        acc = [0.0, 0.0, 0.0, 0.0]
        for j in range(samples):
            for i in range(samples):
                sx = (x + (i + 0.5) / samples - c) / r
                sy = (y + (j + 0.5) / samples - c) / r
                cr, cg, cb, a = sample(sx, sy)
                acc[0] += cr * a
                acc[1] += cg * a
                acc[2] += cb * a
                acc[3] += a
        n = samples * samples
        a = acc[3] / n
        if a <= 0.0:
            return (0, 0, 0, 0)
        return (int(acc[0] / acc[3] * 255), int(acc[1] / acc[3] * 255), int(acc[2] / acc[3] * 255), int(a * 255))

    return px


def normalize(v):
    length = math.sqrt(sum(x * x for x in v))
    return tuple(x / length for x in v)


def dot(a, b):
    return sum(x * y for x, y in zip(a, b))


def monster_skin(width: int, height: int):
    """The demon's skin: near-black with glowing red veins running along the limbs."""

    def px(x, y):
        n = noise(x, y)
        vein = abs(math.sin(x * 0.42 + 2.2 * math.sin(y * 0.13)))
        branch = abs(math.sin(y * 0.55 + 1.7 * math.sin(x * 0.21)))
        if vein < 0.14 or (branch < 0.08 and (x // 8 + y // 8) % 3 == 0):
            return (255, 50 + n * 2, 30, 255)
        glow = max(0.0, 0.4 - vein) / 0.4
        r = 24 + n + int(150 * glow * glow)
        return (min(255, r), 12 + n // 2, 18 + n // 2, 255)

    return px


def sphere(width: int, height: int, color=NEON_CYAN, ink=face_ink):
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
        if dz > 0.0 and ink(dx, -dy):
            return INK
        # Slightly brighter towards the front so the face side reads as "the front".
        glow = 0.85 + 0.15 * max(0.0, dz)
        return tuple(int(c * glow) for c in color[:3]) + (255,)

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
    import sys

    if len(sys.argv) >= 3 and sys.argv[1] == "--logo":
        # A large copy of the logo:  python tools/gen_textures.py --logo <file.png> [size]
        size = int(sys.argv[3]) if len(sys.argv) > 3 else 512
        write_png(Path(sys.argv[2]), size, size, icon(size, 3))
        print("wrote", sys.argv[2])
        sys.exit(0)

    outputs = [
        (ASSETS / "icon.png", 128, 128, icon(128)),
        (ENTITY / "mehburity.png", 128, 64, sphere(128, 64)),
        (ENTITY / "mehburity_angry.png", 128, 64, sphere(128, 64, RAGE_RED, angry_face_ink)),
        (ENTITY / "mehburity_monster.png", 64, 64, monster_skin(64, 64)),
        (ENTITY / "box.png", 64, 64, box(64, 64)),
    ]
    for path, w, h, fn in outputs:
        write_png(path, w, h, fn)
        print("wrote", path.relative_to(ROOT))
