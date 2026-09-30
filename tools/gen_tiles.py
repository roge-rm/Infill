#!/usr/bin/env python3
"""Draws Infill's tiles and sprites and packs them into the atlases.

    uv run --with pillow tools/gen_tiles.py

Everything is drawn at 32 px a tile, in each of the season looks, and written
as three atlases (32, 16 and 8 px) to shared/src/commonMain/composeResources/files/,
with map/Atlas.kt saying where each sprite is and what casts a shadow.

A PNG in tools/art/overrides/<look>/<name>.png replaces the drawn sprite of that
name and look. It has to be the same size.

Shadows aren't drawn here. The game draws them for wherever the sun is, from
the casters listed with each sprite.
"""
import math
import random
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
OUT_PNG = ROOT / "shared/src/commonMain/composeResources/files"
OUT_KT = ROOT / "shared/src/commonMain/kotlin/com/rm/infill/map/Atlas.kt"
OVERRIDES = ROOT / "tools/art/overrides"

T = 32
LOOKS = ["spring", "summer", "autumn", "bare", "snow"]
# Every rectangle sits on this grid so the smaller atlases divide exactly.
GRID = 4
ATLAS_WIDTH = 512


def c(h, a=255):
    return tuple(int(h[i:i + 2], 16) for i in (1, 3, 5)) + (a,)


def shade(col, f):
    return tuple(max(0, min(255, int(v * f))) for v in col[:3]) + (col[3],)


# ---- palettes, per look ------------------------------------------------------

GRASS = {
    "spring": [c("#63a844"), c("#58993b"), c("#72b852")],
    "summer": [c("#5a9a3c"), c("#4f8c34"), c("#66a846")],
    "autumn": [c("#8c9747"), c("#7d883e"), c("#9ea653")],
    "bare": [c("#7d8150"), c("#6f7446"), c("#8a8c5c")],
    "snow": [c("#e9eff3"), c("#d5dfe6"), c("#f7fafc")],
}
WATER = {look: c("#3a6fb0") for look in LOOKS} | {"snow": c("#31609c")}
RIPPLE = {look: c("#5a8fcc") for look in LOOKS} | {"snow": c("#4c7db8")}
BANK = {look: c("#d8c690") for look in LOOKS} | {"snow": c("#e3eaef"), "bare": c("#c9b884")}
FOAM = c("#cfe3f0")

TRUNK = c("#6b4a30")
BRANCH = c("#5a4030")
SNOW = c("#f4f8fb")
SNOW_SHADE = c("#c9d6e0")

# Deciduous canopies: dark, mid, light. Autumn gives each variant its own colour.
LEAF = {
    "spring": [[c("#4f9a3c"), c("#6cb84e"), c("#95d06a")]] * 3,
    "summer": [[c("#2f6b2a"), c("#3d8233"), c("#57a045")]] * 3,
    "autumn": [
        [c("#a4481c"), c("#cf6e28"), c("#eb9a45")],
        [c("#842822"), c("#b0402e"), c("#d66748")],
        [c("#a8841c"), c("#cfac36"), c("#ead064")],
    ],
}
BLOSSOM = [c("#f5c4d4"), c("#fbeef2")]
NEEDLES = [c("#1f4d2a"), c("#2b6236"), c("#3c7a45")]


def noise_fill(img, box, cols, rng, weights=(0.8, 0.1, 0.1)):
    x0, y0, x1, y1 = box
    px = img.load()
    for y in range(y0, y1):
        for x in range(x0, x1):
            r = rng.random()
            px[x, y] = cols[0] if r < weights[0] else cols[1] if r < weights[0] + weights[1] else cols[2]


# ---- ground ------------------------------------------------------------------

def grass(look, variant):
    rng = random.Random(1000 + variant)
    img = Image.new("RGBA", (T, T))
    noise_fill(img, (0, 0, T, T), GRASS[look], rng)
    px = img.load()
    extra = random.Random(2000 + variant)
    if look == "spring":
        for _ in range(3):
            px[extra.randrange(T), extra.randrange(T)] = extra.choice([c("#f2e27a"), c("#fbfbf2")])
    elif look == "autumn":
        for _ in range(4):
            px[extra.randrange(T), extra.randrange(T)] = extra.choice([c("#cf6e28"), c("#b0402e"), c("#cfac36")])
    elif look == "snow":
        for _ in range(3):
            x, y = extra.randrange(T), extra.randrange(T - 1)
            px[x, y] = c("#8a8c5c")
    return img


def water(look, variant):
    rng = random.Random(3000 + variant)
    img = Image.new("RGBA", (T, T), WATER[look])
    d = ImageDraw.Draw(img)
    for _ in range(3):
        x, y = rng.randrange(2, T - 9), rng.randrange(2, T - 2)
        d.line([x, y, x + rng.randrange(3, 7), y], RIPPLE[look])
    return img


# Sand depth along a shore, periodic over one tile so neighbouring tiles join up.
PROFILE = [3 + round(0.8 * math.sin(2 * math.pi * x / T) + 0.5 * math.sin(4 * math.pi * x / T)) for x in range(T)]


def shore_north(look):
    """Bank along the top edge of a water tile whose northern neighbour is land."""
    img = Image.new("RGBA", (T, T), (0, 0, 0, 0))
    px = img.load()
    for x in range(T):
        depth = PROFILE[x]
        for y in range(depth):
            px[x, y] = BANK[look]
        px[x, depth] = FOAM if look != "snow" else SNOW_SHADE
    return img


def corner_north_east(look):
    """A little bank in the corner where only the diagonal neighbour is land."""
    img = Image.new("RGBA", (T, T), (0, 0, 0, 0))
    px = img.load()
    for y in range(6):
        for x in range(T - 6, T):
            dist = math.hypot(x - (T - 1), y)
            if dist < 3.6:
                px[x, y] = BANK[look]
            elif dist < 4.6:
                px[x, y] = FOAM if look != "snow" else SNOW_SHADE
    return img


# ---- trees --------------------------------------------------------------------
# A tree sprite is 32 wide and 48 tall: the bottom 32 rows are its tile and the
# rest reaches up into the tile behind. The foot is where the trunk meets the
# ground, and it's what the shadow hangs from.

SPRITE_H = 48
LIFT = SPRITE_H - T  # how far above its tile a tree sprite starts


def canopy(d, img, cx, cy, r, cols, rng, blossom=None):
    d.ellipse([cx - r, cy - r, cx + r, cy + r], cols[0])
    d.ellipse([cx - r + 1, cy - r, cx + r - 2, cy + r - 3], cols[1])
    d.ellipse([cx - r + 3, cy - r + 2, cx - r + 3 + r, cy - r + 2 + r * 0.8], cols[2])
    px = img.load()
    for _ in range(r * 3):
        a, dist = rng.random() * math.tau, rng.random() * (r - 1)
        x, y = int(cx + math.cos(a) * dist), int(cy + math.sin(a) * dist)
        px[x, y] = cols[0] if rng.random() < 0.6 else cols[2]
    if blossom:
        for _ in range(r * 2):
            a, dist = rng.random() * math.tau, rng.random() * (r - 1)
            px[int(cx + math.cos(a) * dist), int(cy + math.sin(a) * dist)] = rng.choice(blossom)


def bare_crown(d, img, cx, cy, r, rng, snowy):
    # A few main branches and a haze of twigs.
    for k in range(5):
        a = -math.pi / 2 + (k - 2) * 0.45 + rng.uniform(-0.15, 0.15)
        x1, y1 = cx + math.cos(a) * r, cy + r * 0.6 + math.sin(a) * r
        d.line([cx, cy + r * 0.6, x1, y1], BRANCH)
        if snowy:
            d.point((x1, y1 - 1), SNOW)
    px = img.load()
    for _ in range(r * 5):
        a, dist = rng.random() * math.tau, rng.uniform(0.4, 1.0) * r
        x, y = int(cx + math.cos(a) * dist), int(cy + math.sin(a) * dist)
        px[x, y] = shade(BRANCH, 1.2) if not snowy or rng.random() < 0.6 else SNOW


def deciduous(img, look, variant, cx, foot_y, r, rng):
    d = ImageDraw.Draw(img)
    trunk_h = max(4, r - 2)
    cy = foot_y - trunk_h - r + 2
    d.rectangle([cx - 1, foot_y - trunk_h - 2, cx + 1, foot_y], TRUNK)
    d.point((cx - 1, foot_y - trunk_h - 2), shade(TRUNK, 1.3))
    if look in ("bare", "snow"):
        bare_crown(d, img, cx, cy, r, rng, look == "snow")
        if look == "snow":
            d.line([cx - 3, foot_y, cx + 3, foot_y], SNOW)
    else:
        blossom = BLOSSOM if look == "spring" and variant == 0 else None
        canopy(d, img, cx, cy, r, LEAF[look][variant % 3], rng, blossom)
    return cx, foot_y, foot_y - cy, r


def conifer(img, look, cx, foot_y, h, rng):
    d = ImageDraw.Draw(img)
    d.rectangle([cx - 1, foot_y - 3, cx, foot_y], TRUNK)
    tiers = 3
    top = foot_y - 3 - h
    for i in range(tiers):
        ty = top + i * h // tiers
        by = ty + h // tiers + 4
        half = 3 + (i + 1) * (h // 6)
        d.polygon([(cx, ty), (cx - half, by), (cx + half, by)], NEEDLES[0])
        d.polygon([(cx, ty + 1), (cx - half + 2, by - 1), (cx, by - 1)], NEEDLES[1])
        d.line([cx - 1, ty + 2, cx - half + 3, by - 2], NEEDLES[2])
        if look == "snow":
            d.line([cx, ty, cx - half + 1, by - 1], SNOW)
            d.line([cx, ty, cx + half // 2, ty + (by - ty) // 2], SNOW)
            d.line([cx - half + 2, by, cx + half - 2, by], SNOW_SHADE)
    if look == "snow":
        d.line([cx - 4, foot_y, cx + 4, foot_y], SNOW)
    return cx, foot_y, (foot_y - top) // 2 + 2, h // 3 + 2


def tree(look, variant):
    """Five single trees: three broadleaf, two conifers."""
    img = Image.new("RGBA", (T, SPRITE_H), (0, 0, 0, 0))
    rng = random.Random(4000 + variant)
    if variant < 3:
        caster = deciduous(img, look, variant, 15 + variant, 43 - variant, 9 + variant % 2 * 2, rng)
    else:
        caster = conifer(img, look, 15 + (variant - 3) * 2, 43, 26 + (variant - 3) * 4, rng)
    return img, [caster]


FOREST_LAYOUTS = [
    [(9, 30, "d", 7), (23, 34, "c", 18), (13, 44, "d", 8)],
    [(22, 31, "d", 8), (8, 38, "c", 20), (20, 45, "d", 7)],
    [(10, 31, "c", 17), (23, 36, "d", 8), (12, 45, "d", 7), (26, 46, "c", 14)],
]


def forest(look, variant):
    """A tile of woods: a few smaller trees, drawn back to front."""
    img = Image.new("RGBA", (T, SPRITE_H), (0, 0, 0, 0))
    rng = random.Random(5000 + variant)
    casters = []
    for i, (x, foot, kind, size) in enumerate(sorted(FOREST_LAYOUTS[variant], key=lambda t: t[1])):
        if kind == "d":
            casters.append(deciduous(img, look, variant + i, x, foot, size, rng))
        else:
            casters.append(conifer(img, look, x, foot, size, rng))
    return img, casters


# ---- the sprite list -----------------------------------------------------------

def sprites_for(look):
    """Every sprite in one look: (name, image, lift, casters). Same order in every look."""
    out = []
    for v in range(4):
        out.append((f"grass_{v}", grass(look, v), 0, []))
    for v in range(2):
        out.append((f"water_{v}", water(look, v), 0, []))
    n = shore_north(look)
    out += [
        ("shore_n", n, 0, []), ("shore_e", n.rotate(-90), 0, []),
        ("shore_s", n.rotate(180), 0, []), ("shore_w", n.rotate(90), 0, []),
    ]
    ne = corner_north_east(look)
    out += [
        ("corner_ne", ne, 0, []), ("corner_se", ne.rotate(-90), 0, []),
        ("corner_sw", ne.rotate(180), 0, []), ("corner_nw", ne.rotate(90), 0, []),
    ]
    for v in range(5):
        img, casters = tree(look, v)
        out.append((f"tree_{v}", img, LIFT, casters))
    for v in range(3):
        img, casters = forest(look, v)
        out.append((f"forest_{v}", img, LIFT, casters))
    for i, (name, img, lift, casters) in enumerate(out):
        override = OVERRIDES / look / f"{name}.png"
        if override.exists():
            o = Image.open(override).convert("RGBA")
            if o.size != img.size:
                raise SystemExit(f"{override} is {o.size}, it has to be {img.size}")
            out[i] = (name, o, lift, casters)
    return out


def pack(sizes):
    """Shelf packing, tallest first. Returns positions and the atlas size."""
    order = sorted(range(len(sizes)), key=lambda i: -sizes[i][1])
    pos = [None] * len(sizes)
    x = y = shelf = 0
    for i in order:
        w, h = sizes[i]
        if x + w > ATLAS_WIDTH:
            x, y, shelf = 0, y + shelf, 0
        pos[i] = (x, y)
        x += w
        shelf = max(shelf, h)
    height = y + shelf
    height += -height % GRID
    return pos, (ATLAS_WIDTH, height)


def main():
    looks = [sprites_for(look) for look in LOOKS]
    names = [s[0] for s in looks[0]]
    flat = [s for look in looks for s in look]
    for name, img, lift, _ in flat:
        assert img.width % GRID == 0 and img.height % GRID == 0 and lift % GRID == 0, name
    pos, size = pack([s[1].size for s in flat])
    atlas = Image.new("RGBA", size, (0, 0, 0, 0))
    for (name, img, _, _), p in zip(flat, pos):
        atlas.paste(img, p)
    OUT_PNG.mkdir(parents=True, exist_ok=True)
    atlas.save(OUT_PNG / "atlas_32.png", optimize=True)
    # Averaged down with alpha taken into account, so edges don't go dark.
    premul = atlas.convert("RGBa")
    premul.reduce(2).convert("RGBA").save(OUT_PNG / "atlas_16.png", optimize=True)
    premul.reduce(4).convert("RGBA").save(OUT_PNG / "atlas_8.png", optimize=True)
    write_kotlin(names, flat, pos, size)
    print(f"{len(flat)} sprites, {len(names)} a look, atlas {size[0]}x{size[1]}")


def write_kotlin(names, flat, pos, size):
    def first(prefix):
        return next(i for i, n in enumerate(names) if n.startswith(prefix))

    def count(prefix):
        return sum(1 for n in names if n.startswith(prefix))

    rects = []
    for (name, img, lift, _), (x, y) in zip(flat, pos):
        rects += [x, y, img.width, img.height, lift]
    starts, counts, casters = [], [], []
    for name, img, lift, cs in flat[:len(names)]:
        starts.append(len(casters) // 4)
        counts.append(len(cs))
        for fx, fy, h, r in cs:
            # The foot in tile coordinates rather than sprite ones.
            casters += [round(fx), round(fy) - lift, round(h), round(r)]

    def ints(values, per_line=20):
        lines = []
        for i in range(0, len(values), per_line):
            lines.append("        " + ", ".join(str(v) for v in values[i:i + per_line]) + ",")
        return "\n".join(lines)

    groups = ["grass", "water", "shore", "corner", "tree", "forest"]
    consts = []
    for g in groups:
        consts.append(f"    const val {g.upper()} = {first(g + '_')}")
        consts.append(f"    const val {g.upper()}_COUNT = {count(g + '_')}")
    look_consts = "\n".join(f"    const val {look.upper()} = {i}" for i, look in enumerate(LOOKS))
    text = f"""// Written by tools/gen_tiles.py. Change that and run it again rather than editing this.
package com.rm.infill.map

/**
 * Where each sprite is in the atlases (files/atlas_32.png, _16 and _8) and what
 * casts a shadow. A sprite's number is its place in a look plus the look times
 * [PER_LOOK]. Shores and corners go north, east, south, west.
 */
internal object Atlas {{
    const val LOOKS = {len(LOOKS)}
{look_consts}

    const val PER_LOOK = {len(names)}
{chr(10).join(consts)}

    const val WIDTH = {size[0]}
    const val HEIGHT = {size[1]}

    /** Five numbers a sprite, at 32 px: x and y in the atlas, width, height, and how far above its tile it starts. */
    val rects = intArrayOf(
{ints(rects)}
    )

    /** For each sprite in a look, where its casters start in [casters], and how many. */
    val casterStart = intArrayOf(
{ints(starts)}
    )
    val casterCount = intArrayOf(
{ints(counts)}
    )

    /** Four numbers a caster, at 32 px: its foot's x and y in the tile, the height of its middle, and its radius. */
    val casters = intArrayOf(
{ints(casters)}
    )
}}
"""
    OUT_KT.write_text(text)


if __name__ == "__main__":
    main()
