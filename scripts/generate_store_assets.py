"""Generate Play Store graphic assets (hi-res icon, TV banner, feature graphic) for AHC Player."""
from PIL import Image, ImageDraw, ImageFont, ImageFilter

OUT = "kb/store"
BG = (11, 11, 15)        # #0B0B0F
ACCENT = (124, 77, 255)  # #7C4DFF
ACCENT_DIM = (91, 53, 204)
TEXT_PRIMARY = (255, 255, 255)
TEXT_SECONDARY = (176, 176, 192)

ARIAL_BOLD = "/System/Library/Fonts/Supplemental/Arial Bold.ttf"
ARIAL = "/System/Library/Fonts/Supplemental/Arial.ttf"


def glow(size, color, radius):
    """Radial glow blob as RGBA image."""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    draw.ellipse((0, 0, size, size), fill=color + (255,))
    return img.filter(ImageFilter.GaussianBlur(radius))


def paste_glow(canvas, cx, cy, size, color, radius, alpha=120):
    g = glow(size, color, radius)
    g.putalpha(g.split()[-1].point(lambda a: min(a, alpha)))
    canvas.alpha_composite(g, (cx - size // 2, cy - size // 2))


def draw_play_glyph(draw, cx, cy, r, color):
    """Equilateral play triangle (pointing right) centered at (cx, cy) with 'radius' r."""
    import math
    pts = []
    for ang in (90, 210, 330):
        a = math.radians(ang)
        x = cx + r * math.cos(a)
        y = cy - r * math.sin(a)
        pts.append((x, y))
    # rotate +90deg so the upward-pointing triangle points right
    pts = [(cx - (y - cy), cy + (x - cx)) for x, y in pts]
    draw.polygon(pts, fill=color)


def make_icon():
    size = 512
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    draw.rounded_rectangle((0, 0, size, size), radius=size // 5, fill=ACCENT)
    draw_play_glyph(draw, size // 2 + 18, size // 2, size * 0.22, TEXT_PRIMARY)
    img.convert("RGB").save(f"{OUT}/icon-512.png")


def wordmark(draw, cx, cy, scale=1.0):
    big = ImageFont.truetype(ARIAL_BOLD, int(96 * scale))
    small = ImageFont.truetype(ARIAL_BOLD, int(96 * scale))
    ahc = "AHC"
    player = " Player"
    w_ahc = draw.textlength(ahc, font=big)
    w_player = draw.textlength(player, font=small)
    total_w = w_ahc + w_player
    x = cx - total_w / 2
    y = cy - 96 * scale / 2
    draw.text((x, y), ahc, font=big, fill=ACCENT)
    draw.text((x + w_ahc, y), player, font=small, fill=TEXT_PRIMARY)
    return total_w


def make_banner():
    """1280x720 Android TV banner."""
    w, h = 1280, 720
    img = Image.new("RGBA", (w, h), BG + (255,))
    paste_glow(img, w // 2, int(h * 0.38), 1100, ACCENT, 160, alpha=70)
    draw = ImageDraw.Draw(img)

    # Play glyph
    draw_play_glyph(draw, w // 2 - 6, int(h * 0.38) - 70, 46, TEXT_PRIMARY)

    big = ImageFont.truetype(ARIAL_BOLD, 96)
    ahc = "AHC"
    player = " Player"
    w_ahc = draw.textlength(ahc, font=big)
    w_player = draw.textlength(player, font=big)
    total_w = w_ahc + w_player
    tx = w // 2 - total_w / 2
    ty = int(h * 0.38) - 20
    draw.text((tx, ty), ahc, font=big, fill=ACCENT)
    draw.text((tx + w_ahc, ty), player, font=big, fill=TEXT_PRIMARY)

    tagline_font = ImageFont.truetype(ARIAL, 38)
    tagline = "Your media. Your network. Your screen."
    tw = draw.textlength(tagline, font=tagline_font)
    draw.text((w // 2 - tw / 2, ty + 130), tagline, font=tagline_font, fill=TEXT_SECONDARY)

    img.convert("RGB").save(f"{OUT}/tv-banner-1280x720.png")


def make_feature_graphic():
    """1024x500 Play Store feature graphic."""
    w, h = 1024, 500
    img = Image.new("RGBA", (w, h), BG + (255,))
    paste_glow(img, int(w * 0.78), h // 2, 900, ACCENT, 160, alpha=70)
    draw = ImageDraw.Draw(img)

    # Icon badge on the left
    badge = 220
    bx, by = 90, (h - badge) // 2
    draw.rounded_rectangle((bx, by, bx + badge, by + badge), radius=badge // 5, fill=ACCENT)
    draw_play_glyph(draw, bx + badge // 2 + 8, by + badge // 2, badge * 0.22, TEXT_PRIMARY)

    big = ImageFont.truetype(ARIAL_BOLD, 80)
    ahc = "AHC"
    player = " Player"
    w_ahc = draw.textlength(ahc, font=big)
    tx = bx + badge + 60
    ty = by + 18
    draw.text((tx, ty), ahc, font=big, fill=ACCENT)
    draw.text((tx + w_ahc, ty), player, font=big, fill=TEXT_PRIMARY)

    tagline_font = ImageFont.truetype(ARIAL, 32)
    draw.text((tx, ty + 100), "Private Netflix-style player for your own NAS", font=tagline_font, fill=TEXT_SECONDARY)
    draw.text((tx, ty + 145), "Android TV / Fire TV", font=tagline_font, fill=TEXT_SECONDARY)

    img.convert("RGB").save(f"{OUT}/feature-graphic-1024x500.png")


if __name__ == "__main__":
    make_icon()
    make_banner()
    make_feature_graphic()
    print("done")
