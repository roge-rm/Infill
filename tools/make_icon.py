"""Makes the app's icons from tools/icon.png: Android's adaptive launcher
icon at each density, the store icon, the web page's icons, and the one on
the start screen. Run it again after changing icon.png:

    cd tools && uv run --with pillow make_icon.py
"""
from pathlib import Path

from PIL import Image

HERE = Path(__file__).parent
ROOT = HERE.parent
RES = ROOT / "app/src/main/res"
WEB = ROOT / "shared/src/wasmJsMain/resources"
COMPOSE = ROOT / "shared/src/commonMain/composeResources/drawable"

# An adaptive icon is 108dp a side, of which the middle 72dp is ever shown.
# The picture fills that middle, on a background of its own grass.
DENSITIES = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}


def scaled(img, side):
    return img.resize((side, side), Image.LANCZOS)


def main():
    icon = Image.open(HERE / "icon.png").convert("RGBA")
    # The grass is the commonest colour in the picture.
    counts = {}
    for y in range(0, icon.height, 4):
        for x in range(0, icon.width, 4):
            p = icon.getpixel((x, y))
            counts[p] = counts.get(p, 0) + 1
    grass = max(counts, key=counts.get)
    for name, k in DENSITIES.items():
        full = round(108 * k)
        shown = round(72 * k)
        layer = Image.new("RGBA", (full, full), (0, 0, 0, 0))
        off = (full - shown) // 2
        layer.paste(scaled(icon, shown), (off, off))
        out = RES / f"mipmap-{name}"
        out.mkdir(parents=True, exist_ok=True)
        layer.save(out / "ic_launcher_foreground.png")
    # The background, a plain colour taken from the picture's edge.
    (RES / "values").mkdir(exist_ok=True)
    hex_colour = "#{:02X}{:02X}{:02X}".format(*grass[:3])
    (RES / "values/ic_launcher_background.xml").write_text(
        '<?xml version="1.0" encoding="utf-8"?>\n<!-- Written by tools/make_icon.py. -->\n'
        f'<resources>\n    <color name="ic_launcher_background">{hex_colour}</color>\n</resources>\n'
    )
    # Android 13's themed icon is a single colour, so it's the houses alone, doors cut out,
    # where they stand in the picture, on the same 72dp of the 108.
    k = 72 / 1024
    paths = []
    for x0 in (68, 265, 580, 777):
        for y0 in (77, 265, 580, 768):
            def p(v, y=False):
                return f"{18 + v * k:.2f}"
            house = f"M{p(x0)},{p(y0)}H{p(x0 + 179)}V{p(y0 + 93)}H{p(x0 + 171)}V{p(y0 + 170)}H{p(x0 + 8)}V{p(y0 + 93)}H{p(x0)}Z"
            door = f"M{p(x0 + 68)},{p(y0 + 119)}H{p(x0 + 111)}V{p(y0 + 170)}H{p(x0 + 68)}Z"
            paths.append(f'    <path android:fillColor="#FFFFFFFF" android:fillType="evenOdd" android:pathData="{house}{door}" />')
    (RES / "drawable/ic_launcher_monochrome.xml").write_text(
        '<?xml version="1.0" encoding="utf-8"?>\n<!-- Written by tools/make_icon.py: the icon\'s houses, for themed icons. -->\n'
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        '    android:width="108dp" android:height="108dp"\n'
        '    android:viewportWidth="108" android:viewportHeight="108">\n' + "\n".join(paths) + "\n</vector>\n"
    )
    # The store's icon.
    scaled(icon, 512).save(ROOT / "app/src/main/ic_launcher-playstore.png")
    # The web page's tab and home screen icons.
    scaled(icon, 32).save(WEB / "favicon.png")
    scaled(icon, 180).save(WEB / "apple-touch-icon.png")
    scaled(icon, 192).save(WEB / "icon-192.png")
    scaled(icon, 512).save(WEB / "icon-512.png")
    # For the start screen.
    COMPOSE.mkdir(parents=True, exist_ok=True)
    scaled(icon, 256).save(COMPOSE / "app_icon.png")
    print("icons written, background", hex_colour)


if __name__ == "__main__":
    main()
