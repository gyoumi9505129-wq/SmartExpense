"""Generate mipmap launcher icons from drawable/ic_app_logo.png."""
from __future__ import annotations

from collections import Counter
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app" / "src" / "main" / "res"
SRC = RES / "drawable" / "ic_app_logo.png"

# Legacy launcher sizes (px)
LEGACY = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}

# Adaptive foreground full asset (xxxhdpi baseline = 432px for 108dp)
ADAPTIVE_FOREGROUND = {
    "drawable-mdpi": 108,
    "drawable-hdpi": 162,
    "drawable-xhdpi": 216,
    "drawable-xxhdpi": 324,
    "drawable-xxxhdpi": 432,
}

# Safe zone ~66/108 ≈ 61% of canvas; use ~58% content for a little extra padding
CONTENT_RATIO = 0.58


def dominant_edge_color(img: Image.Image) -> tuple[int, int, int]:
    rgba = img.convert("RGBA")
    w, h = rgba.size
    edge: list[tuple[int, int, int]] = []
    step = max(1, min(w, h) // 100)
    for x in range(0, w, step):
        for y in (0, h - 1):
            r, g, b, a = rgba.getpixel((x, y))
            if a > 200:
                edge.append((r, g, b))
    for y in range(0, h, step):
        for x in (0, w - 1):
            r, g, b, a = rgba.getpixel((x, y))
            if a > 200:
                edge.append((r, g, b))
    if not edge:
        return (18, 18, 18)
    return Counter(edge).most_common(1)[0][0]


def fit_contain(src: Image.Image, box: int) -> Image.Image:
    """Scale logo to fit inside box while keeping aspect ratio."""
    src = src.convert("RGBA")
    src.thumbnail((box, box), Image.Resampling.LANCZOS)
    canvas = Image.new("RGBA", (box, box), (0, 0, 0, 0))
    x = (box - src.width) // 2
    y = (box - src.height) // 2
    canvas.paste(src, (x, y), src)
    return canvas


def make_adaptive_layer(src: Image.Image, size: int, bg: tuple[int, int, int]) -> Image.Image:
    content = max(1, int(size * CONTENT_RATIO))
    logo = fit_contain(src, content)
    canvas = Image.new("RGBA", (size, size), bg + (255,))
    x = (size - logo.width) // 2
    y = (size - logo.height) // 2
    canvas.paste(logo, (x, y), logo)
    return canvas


def make_legacy_icon(src: Image.Image, size: int, bg: tuple[int, int, int], round_mask: bool) -> Image.Image:
    # Legacy icons are fully filled (no adaptive mask); keep padding so crop looks natural
    content = max(1, int(size * 0.78))
    logo = fit_contain(src, content)
    canvas = Image.new("RGBA", (size, size), bg + (255,))
    x = (size - logo.width) // 2
    y = (size - logo.height) // 2
    canvas.paste(logo, (x, y), logo)
    if round_mask:
        mask = Image.new("L", (size, size), 0)
        draw = ImageDraw.Draw(mask)
        draw.ellipse((0, 0, size - 1, size - 1), fill=255)
        out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        out.paste(canvas, (0, 0))
        out.putalpha(mask)
        return out
    return canvas


def main() -> None:
    if not SRC.exists():
        raise SystemExit(f"Missing logo: {SRC}")

    src = Image.open(SRC)
    bg = dominant_edge_color(src)
    print(f"source={src.size} bg=#{bg[0]:02X}{bg[1]:02X}{bg[2]:02X}")

    # Update solid background color used by adaptive icon XML
    bg_xml = RES / "drawable" / "ic_launcher_background.xml"
    bg_xml.write_text(
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<shape xmlns:android="http://schemas.android.com/apk/res/android"\n'
        '    android:shape="rectangle">\n'
        f'    <solid android:color="#{bg[0]:02X}{bg[1]:02X}{bg[2]:02X}" />\n'
        "</shape>\n",
        encoding="utf-8",
    )
    print(f"wrote {bg_xml}")

    # Adaptive foreground bitmaps (density-specific)
    for folder, size in ADAPTIVE_FOREGROUND.items():
        out_dir = RES / folder
        out_dir.mkdir(parents=True, exist_ok=True)
        # Transparent padding + logo only — background layer supplies fill color
        content = max(1, int(size * CONTENT_RATIO))
        logo = fit_contain(src, content)
        canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        x = (size - logo.width) // 2
        y = (size - logo.height) // 2
        canvas.paste(logo, (x, y), logo)
        out = out_dir / "ic_launcher_foreground.png"
        canvas.save(out, format="PNG", optimize=True)
        print(f"wrote {out} ({size}x{size})")

    # Fallback single drawable referenced by adaptive XML if density missing
    fallback = RES / "drawable" / "ic_launcher_foreground.png"
    fit_contain(src, int(432 * CONTENT_RATIO))
    canvas = Image.new("RGBA", (432, 432), (0, 0, 0, 0))
    logo = fit_contain(src, int(432 * CONTENT_RATIO))
    canvas.paste(logo, ((432 - logo.width) // 2, (432 - logo.height) // 2), logo)
    canvas.save(fallback, format="PNG", optimize=True)
    print(f"wrote {fallback}")

    # Legacy mipmaps
    for folder, size in LEGACY.items():
        out_dir = RES / folder
        out_dir.mkdir(parents=True, exist_ok=True)
        square = make_legacy_icon(src, size, bg, round_mask=False)
        round_icon = make_legacy_icon(src, size, bg, round_mask=True)
        square_path = out_dir / "ic_launcher.png"
        round_path = out_dir / "ic_launcher_round.png"
        square.save(square_path, format="PNG", optimize=True)
        round_icon.save(round_path, format="PNG", optimize=True)
        print(f"wrote {square_path} / {round_path}")

    # Colors helper for splash / launcher bg sync
    colors = RES / "values" / "colors.xml"
    text = colors.read_text(encoding="utf-8")
    hex_bg = f"#{bg[0]:02X}{bg[1]:02X}{bg[2]:02X}"
    if "ic_launcher_background" in text:
        import re

        text = re.sub(
            r'<color name="ic_launcher_background">[^<]+</color>',
            f'<color name="ic_launcher_background">{hex_bg}</color>',
            text,
        )
    else:
        text = text.replace(
            "</resources>",
            f'    <color name="ic_launcher_background">{hex_bg}</color>\n</resources>',
        )
    colors.write_text(text, encoding="utf-8")
    print(f"updated {colors}")


if __name__ == "__main__":
    main()
