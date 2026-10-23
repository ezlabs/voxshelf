"""Draws the Google Play icon (512x512) and banner (1024x500) from the Voxshelf mark, with Pillow.

The mark is the app icon's shape (an open book as a V, with sound rising), drawn on a 108-unit grid.
Run from this folder: python make_graphics.py
"""
import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

GOLD = (242, 199, 110)
FOREST = (15, 26, 21)
HERE = Path(__file__).parent
COVERS = HERE.parent / "site" / "covers"


def cubic(p0, p1, p2, p3, n=40):
    return [tuple((1 - t) ** 3 * a + 3 * (1 - t) ** 2 * t * b + 3 * (1 - t) * t ** 2 * c + t ** 3 * d
                  for a, b, c, d in zip(p0, p1, p2, p3)) for t in (i / n for i in range(n + 1))]


def glow(size, center, radius, strength):
    """A soft gold circle that fades out, as an RGBA layer."""
    layer = Image.new("RGBA", size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    for i in range(60, 0, -1):
        r = radius * i / 60
        alpha = int(255 * strength * (1 - i / 60) ** 1.6)
        d.ellipse([center[0] - r, center[1] - r, center[0] + r, center[1] + r], fill=GOLD + (alpha,))
    return layer.filter(ImageFilter.GaussianBlur(radius / 12))


def mark(img, ox, oy, scale):
    """Draws the mark with its 108-grid origin at (ox, oy), at the given pixels per unit."""
    d = ImageDraw.Draw(img, "RGBA")
    P = lambda x, y: (ox + x * scale, oy + y * scale)
    left = cubic(P(54, 84), P(46, 70), P(36, 56), P(22, 44)) + cubic(P(22, 44), P(34, 40), P(46, 44), P(54, 56))
    right = cubic(P(54, 84), P(62, 70), P(72, 56), P(86, 44)) + cubic(P(86, 44), P(74, 40), P(62, 44), P(54, 56))
    d.polygon(left, fill=GOLD + (255,))
    d.polygon(right, fill=GOLD + (219,))
    # Three arcs, matching the icon's sound waves: (radius, centre height, opacity, half the chord).
    # Each is drawn solid on its own layer, then laid on at its opacity, so they fade like the app icon.
    w = 4 * scale
    for r, cy, a, half_chord in ((12, 44.9, 1.0, 9), (22, 43.1, 0.6, 16), (32, 41.2, 0.3, 23)):
        layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
        ld = ImageDraw.Draw(layer)
        half = math.degrees(math.asin(half_chord / r))
        cx, cyp, rp = ox + 54 * scale, oy + cy * scale, r * scale
        # Pillow draws the stroke inside the box, so grow the box by half the stroke to centre it on the radius.
        ld.arc([cx - rp - w / 2, cyp - rp - w / 2, cx + rp + w / 2, cyp + rp + w / 2], 270 - half, 270 + half, fill=GOLD + (255,), width=round(w))
        for ang in (270 - half, 270 + half):
            x, y = cx + rp * math.cos(math.radians(ang)), cyp + rp * math.sin(math.radians(ang))
            ld.ellipse([x - w / 2, y - w / 2, x + w / 2, y + w / 2], fill=GOLD + (255,))
        layer.putalpha(layer.getchannel("A").point(lambda v, a=a: int(v * a)))
        img.alpha_composite(layer)


def icon():
    S = 4  # draw big, then shrink, for smooth edges
    size = 512 * S
    img = Image.new("RGBA", (size, size), FOREST + (255,))
    scale = size / 108 * 0.86
    ox = size / 2 - 54 * scale
    oy = size / 2 - 50.5 * scale
    img.alpha_composite(glow((size, size), (size / 2, oy + 46 * scale), 34 * scale, 0.45))
    mark(img, ox, oy, scale)
    img.convert("RGB").resize((512, 512), Image.LANCZOS).save(HERE / "icon-512.png")


def banner():
    S = 2
    W, H = 1024 * S, 500 * S
    img = Image.new("RGBA", (W, H), FOREST + (255,))
    # A tilted wall of real covers on the right, fading into the green.
    covers = sorted(COVERS.glob("*.webp"))
    wall = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    tile, gap = 150 * S, 14 * S
    i = 0
    for row in range(5):
        for col in range(6):
            c = Image.open(covers[i % len(covers)]).convert("RGBA").resize((tile, tile))
            i += 1
            wall.alpha_composite(c, (W // 2 - 120 * S + col * (tile + gap) - row * 60 * S, -80 * S + row * (tile + gap)))
    wall = wall.rotate(12, resample=Image.BICUBIC, center=(W * 0.75, H / 2))
    fade = Image.new("L", (W, H), 0)
    fd = ImageDraw.Draw(fade)
    for x in range(W):
        fd.line([(x, 0), (x, H)], fill=int(150 * max(0.0, min(1.0, (x - W * 0.42) / (W * 0.3)))))
    wall.putalpha(Image.composite(wall.getchannel("A"), Image.new("L", (W, H), 0), fade).point(lambda v: min(v, 150)))
    img.alpha_composite(wall)
    # Darken behind the text.
    shade = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    sd = ImageDraw.Draw(shade)
    for x in range(W):
        sd.line([(x, 0), (x, H)], fill=FOREST + (int(255 * max(0.0, min(1.0, 1.25 - x / (W * 0.62)))),))
    img.alpha_composite(shade)

    scale = 2.15 * S
    ox, oy = 70 * S - 22 * scale, H / 2 - 52 * scale
    img.alpha_composite(glow((W, H), (ox + 54 * scale, oy + 46 * scale), 40 * scale, 0.4))
    mark(img, ox, oy, scale)
    d = ImageDraw.Draw(img)
    title = ImageFont.truetype(str(HERE / "Newsreader.ttf"), 92 * S)
    title.set_variation_by_axes([600, 72])  # weight, optical size
    body = ImageFont.truetype(str(HERE / "NunitoSans.ttf"), 30 * S)
    body.set_variation_by_axes([700, 100, 12, 500])  # weight, width, optical size, YTLC
    tx = ox + 98 * scale
    d.text((tx, H / 2 - 98 * S), "Voxshelf", font=title, fill=(238, 244, 239))
    d.text((tx + 4 * S, H / 2 + 14 * S), "Every classic, read aloud.", font=body, fill=GOLD)
    d.text((tx + 4 * S, H / 2 + 58 * S), "Free. No ads. No account.", font=body, fill=(207, 227, 214))
    img.convert("RGB").resize((1024, 500), Image.LANCZOS).save(HERE / "feature-1024x500.png")


icon()
banner()
print("Wrote icon-512.png and feature-1024x500.png")
