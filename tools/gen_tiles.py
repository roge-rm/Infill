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
LOOKS = ["spring", "summer", "autumn", "bare", "snow", "dry"]
# Every rectangle sits on this grid so the smaller atlases divide exactly.
GRID = 4
ATLAS_WIDTH = 1024


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
    # A dry summer: the grass burnt to straw.
    "dry": [c("#b5a35c"), c("#a59352"), c("#c3b16a")],
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
    "dry": [[c("#4a5e2c"), c("#5f7536"), c("#7c8e48")]] * 3,
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


# ---- roads -------------------------------------------------------------------
# A road's sprite depends on which neighbours are road too: north 1, east 2,
# south 4, west 8. In 1900 roads are packed dirt with wheel ruts; gravel,
# lanes, paved streets and avenues come with them. One-way roads are the same
# sprites with an arrow painted over, and a boulevard is avenue with half a
# median along the side it shares with the other carriageway.

DIRT_ROAD = {
    look: [c("#a88a5c"), c("#967a4e"), c("#b99b6a")] for look in LOOKS
} | {
    "bare": [c("#8f7650"), c("#7f6846"), c("#9c8460")],
    "snow": [c("#cdc6b8"), c("#bdb4a3"), c("#dbd5ca")],  # packed and dirty
}
RUT = {look: c("#86693f") for look in LOOKS} | {"bare": c("#6f5a3c"), "snow": c("#8e7a5e")}
ROAD_EDGE = {look: c("#7f6a44") for look in LOOKS} | {"snow": c("#b3aa98")}
ROAD_LO, ROAD_HI = 7, 24  # the road's width across a tile
RUTS = (12, 19)

GRAVEL = {
    look: [c("#b3a893"), c("#a1967f"), c("#c4baa6")] for look in LOOKS
} | {
    "bare": [c("#a39a86"), c("#948b77"), c("#b2a996")],
    "snow": [c("#dcd9d2"), c("#cbc7bd"), c("#e8e6e1")],
}
GRAVEL_EDGE = {look: c("#8f8674") for look in LOOKS} | {"snow": c("#b9b4a8")}
GRAVEL_STONE = {look: c("#7c7466") for look in LOOKS} | {"snow": c("#a8a296")}

LANE_LO, LANE_HI = 9, 22
LANE_MIDDLE = (15, 16)  # grass between the wheel tracks

MACADAM = {
    look: [c("#7d7a74"), c("#74716b"), c("#86837d")] for look in LOOKS
} | {
    "snow": [c("#d3d6d8"), c("#c2c6c9"), c("#e2e5e7")],  # ploughed, with slush
}
WHEEL_TRACK = {look: c("#6d6a64") for look in LOOKS} | {"snow": c("#a9aeb2")}
WALK = {look: [c("#c9c2b0"), c("#bdb6a4")] for look in LOOKS} | {"snow": [c("#eef2f5"), c("#dfe6eb")]}
CURB = {look: c("#8f8a80") for look in LOOKS} | {"snow": c("#b8bfc5")}
CENTRE_LINE = {look: c("#d8d2c0") for look in LOOKS} | {"snow": c("#9aa0a5")}
STREET_LO, STREET_HI = 4, 27
STREET_WALK = 2
AVENUE_WALK = 3
ARROW = (236, 232, 220, 210)
MEDIAN = 4  # half a boulevard's median, on each carriageway


def bend(lo, hi):
    """A corner's curve, worked out as the bend from the south round to the east: the
    radius of its inside and outside edges, and where their centre is (on both axes)."""
    width = hi - lo + 1
    # A road as wide as the tile can only round its outside corner.
    if width >= T:
        return 0, T // 2, T // 2
    inner = max(0, min(width // 3, T - 1 - lo - width))
    outer = width + inner
    return inner, outer, lo + outer


def road_shape(mask, lo, hi):
    """Whether a pixel is on a road that's [lo, hi] wide and joins the neighbours in [mask].
    Round a corner the outside edge curves, and the inside is filled in a little."""
    square = square_road_shape(mask, lo, hi)
    if mask not in (3, 6, 9, 12):
        return square
    # Worked out as the corner from the south round to the east, flipped for the others.
    flip_x = bool(mask & 8)
    flip_y = bool(mask & 1)
    # Both edges are arcs round one point, so the road keeps its width round the bend.
    inner, outer, centre = bend(lo, hi)
    def inside(x, y):
        if not (0 <= x < T and 0 <= y < T):
            return square(x, y)
        cx = T - 1 - x if flip_x else x
        cy = T - 1 - y if flip_y else y
        if lo <= cx < centre and lo <= cy < centre:
            d = (cx + 0.5 - centre) ** 2 + (cy + 0.5 - centre) ** 2
            return inner * inner <= d <= outer * outer
        return square(x, y)
    return inside


def square_road_shape(mask, lo, hi):
    """Whether a pixel is on a road that's [lo, hi] wide and joins the neighbours in [mask], with square corners."""
    def inside(x, y):
        if not (0 <= x < T and 0 <= y < T):
            # Past the tile's edge the road carries on only where it's joined.
            if y < 0: return bool(mask & 1) and lo <= x <= hi
            if x >= T: return bool(mask & 2) and lo <= y <= hi
            if y >= T: return bool(mask & 4) and lo <= x <= hi
            return bool(mask & 8) and lo <= y <= hi
        across = lo <= x <= hi
        down = lo <= y <= hi
        if across and down:
            return True
        return (across and ((y < lo and mask & 1) or (y > hi and mask & 4))) or \
               (down and ((x > hi and mask & 2) or (x < lo and mask & 8)))
    return inside


def near_edge(inside, x, y, d):
    """Whether a pixel on the road is within [d] of where it ends."""
    return any(not inside(x + dx, y + dy) for dy in range(-d, d + 1) for dx in range(-d, d + 1))


def track_lines(px, mask, at, lo, hi, col, rng, chance):
    """Lines along each way the road goes, at the offsets [at] across it. Round
    a corner they turn with it, the outer line outside the inner; a branch's
    lines stop at the lines of the road it joins."""
    def down(x, y0, y1):
        for y in range(min(y0, y1), max(y0, y1) + 1):
            if rng.random() < chance: px[x, y] = col
    def across(y, x0, x1):
        for x in range(min(x0, x1), max(x0, x1) + 1):
            if rng.random() < chance: px[x, y] = col
    near, far = min(at), max(at)
    if mask in (3, 6, 9, 12) and hi - lo < T - 1:
        # A corner: each line curves round with the road, about the same centre as its edges.
        # Worked out as the bend from the south round to the east, flipped for the others.
        flip_x = bool(mask & 8)
        flip_y = bool(mask & 1)
        _, _, centre = bend(lo, hi)
        def put(cx, cy):
            if 0 <= cx < T and 0 <= cy < T and rng.random() < chance:
                px[T - 1 - cx if flip_x else cx, T - 1 - cy if flip_y else cy] = col
        for a in at:
            r = centre - a
            steps = max(8, int(r * 3))
            done = set()
            for k in range(steps + 1):
                t = math.pi / 2 * k / steps
                p = (round(centre - r * math.cos(t)), round(centre - r * math.sin(t)))
                if p not in done:
                    done.add(p)
                    put(*p)
            # On to the tile's edges, if the curve stops short of them.
            for y in range(centre + 1, T):
                put(a, y)
            for x in range(centre + 1, T):
                put(x, a)
        return
    vertical = mask & 5 or mask == 0
    horizontal = mask & 10
    for a in at:
        if vertical:
            # Joining a road across, it stops at that road's nearer line; a dead end stops short.
            top = 0 if mask & 1 else far if horizontal else lo + 2
            bottom = T - 1 if mask & 4 else near if horizontal else hi - 2
            down(a, top, bottom)
        if horizontal:
            left = 0 if mask & 8 else far if vertical else lo + 2
            right = T - 1 if mask & 2 else near if vertical else hi - 2
            across(a, left, right)


def road(look, mask):
    img = Image.new("RGBA", (T, T), (0, 0, 0, 0))
    px = img.load()
    rng = random.Random(6000 + mask)
    inside = road_shape(mask, ROAD_LO, ROAD_HI)
    cols = DIRT_ROAD[look]
    for y in range(T):
        for x in range(T):
            if not inside(x, y):
                continue
            edge = not (inside(x - 1, y) and inside(x + 1, y) and inside(x, y - 1) and inside(x, y + 1))
            r = rng.random()
            px[x, y] = ROAD_EDGE[look] if edge else cols[0] if r < 0.75 else cols[1] if r < 0.88 else cols[2]
    # Wheel ruts along each way the road goes, through the middle.
    track_lines(px, mask, RUTS, ROAD_LO, ROAD_HI, RUT[look], rng, 0.85)
    return img


def gravel_road(look, mask):
    img = Image.new("RGBA", (T, T), (0, 0, 0, 0))
    px = img.load()
    rng = random.Random(6100 + mask)
    inside = road_shape(mask, ROAD_LO, ROAD_HI)
    cols = GRAVEL[look]
    for y in range(T):
        for x in range(T):
            if not inside(x, y):
                continue
            if near_edge(inside, x, y, 1):
                # A loose, ragged edge.
                px[x, y] = GRAVEL_EDGE[look] if rng.random() < 0.7 else cols[1]
                continue
            r = rng.random()
            px[x, y] = GRAVEL_STONE[look] if r < 0.08 else cols[0] if r < 0.7 else cols[1] if r < 0.85 else cols[2]
    track_lines(px, mask, RUTS, ROAD_LO, ROAD_HI, cols[1], rng, 0.6)
    return img


def lane(look, mask):
    """A narrow track: two wheel tracks with grass up the middle, except where it meets another."""
    img = Image.new("RGBA", (T, T), (0, 0, 0, 0))
    px = img.load()
    rng = random.Random(6200 + mask)
    inside = road_shape(mask, LANE_LO, LANE_HI)
    cols = DIRT_ROAD[look]
    m0, m1 = LANE_MIDDLE
    straight = mask in (5, 10, 1, 4, 2, 8)
    for y in range(T):
        for x in range(T):
            if not inside(x, y):
                continue
            if straight or mask == 0:
                middle = (mask & 5 or mask == 0) and m0 <= x <= m1 or (mask & 10) and m0 <= y <= m1
                # The grass stops short of a dead end.
                if middle and not near_edge(inside, x, y, 3):
                    continue
            edge = near_edge(inside, x, y, 1)
            r = rng.random()
            px[x, y] = ROAD_EDGE[look] if edge and r < 0.6 else cols[0] if r < 0.8 else cols[2]
    return img


def paved(look, mask, lo, hi, walk, centre):
    """A paved road with a curb and footpaths along its sides, and a painted line down the middle if [centre]."""
    img = Image.new("RGBA", (T, T), (0, 0, 0, 0))
    px = img.load()
    rng = random.Random(6300 + mask + lo * 16)
    inside = road_shape(mask, lo, hi)
    cols = MACADAM[look]
    walks = WALK[look]
    for y in range(T):
        for x in range(T):
            if not inside(x, y):
                continue
            if near_edge(inside, x, y, walk - 1):
                # Footpath flagstones, with a joint every so often.
                px[x, y] = walks[1] if (x + y * 3) % 7 == 0 else walks[0]
            elif near_edge(inside, x, y, walk):
                px[x, y] = CURB[look]
            else:
                r = rng.random()
                px[x, y] = cols[0] if r < 0.7 else cols[1] if r < 0.85 else cols[2]
    middle = (lo + hi) // 2
    track_lines(px, mask, (middle - 5, middle + 6), lo, hi, WHEEL_TRACK[look], rng, 0.35)
    if centre:
        # Dashed, and only along straight runs.
        for k in range(T):
            if k % 6 >= 3:
                continue
            if mask == 5:
                px[middle, k] = CENTRE_LINE[look]
                px[middle + 1, k] = CENTRE_LINE[look]
            elif mask == 10:
                px[k, middle] = CENTRE_LINE[look]
                px[k, middle + 1] = CENTRE_LINE[look]
    return img


def street(look, mask):
    return paved(look, mask, STREET_LO, STREET_HI, STREET_WALK, False)


def avenue(look, mask):
    return paved(look, mask, 0, T - 1, AVENUE_WALK, True)


def arrow(heading):
    """A painted arrow pointing north, turned for the other headings (1 north, 2 east, 3 south, 4 west)."""
    img = Image.new("RGBA", (T, T), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rectangle((15, 13, 16, 22), fill=ARROW)
    d.polygon([(15.5, 8), (11, 14), (20, 14)], fill=ARROW)
    return img.rotate(-90 * (heading - 1))


def median(look):
    """Half a boulevard's median along the north edge: grass, a curb and a few shrubs."""
    img = Image.new("RGBA", (T, T), (0, 0, 0, 0))
    px = img.load()
    rng = random.Random(6400)
    cols = GRASS[look]
    for y in range(MEDIAN):
        for x in range(T):
            px[x, y] = CURB[look] if y == MEDIAN - 1 else cols[0] if rng.random() < 0.8 else cols[1]
    shrub = {"autumn": c("#7d6a2e"), "bare": c("#6a5a40"), "snow": c("#c9d6e0")}.get(look, c("#2f6b2a"))
    for x in range(2, T, 8):
        px[x, 0] = shrub
        px[x + 1, 0] = shrub
        px[x, 1] = shrub
        px[x + 1, 1] = shade(shrub, 0.8)
    return img


PLANK = [c("#8a6a45"), c("#7a5c3a"), c("#96774f")]
PLANK_GAP = c("#5b4430")
BRIDGE_STONE = [c("#a9a49a"), c("#9c978d"), c("#b5b0a6")]
RAIL = c("#4a4540")
PARAPET_STONE = c("#6b665e")
DECK_SHADOW = (10, 25, 45, 110)
STEEL_DECK = [c("#5c636b"), c("#545b63"), c("#646b73")]
GIRDER = c("#3f4a56")
GIRDER_LIGHT = c("#6f7c88")
CABLE = c("#d8dde2")
TOWER_STONE = c("#c9c4ba")
WOOD_LO, WOOD_HI = 5, 26


def bridge(material, vertical):
    """A bridge deck running north to south, turned for east to west, with its shadow on the water."""
    img = Image.new("RGBA", (T, T), (0, 0, 0, 0))
    px = img.load()
    rng = random.Random(6500 + (material == "stone"))
    lo, hi = (WOOD_LO, WOOD_HI) if material == "wood" else (0, T - 1)
    for y in range(T):
        for x in range(lo, hi + 1):
            if material == "wood":
                px[x, y] = PLANK_GAP if y % 4 == 3 else rng.choice(PLANK)
            elif material == "steel":
                # Steel plates, riveted where they meet.
                px[x, y] = shade(STEEL_DECK[0], 0.85) if y % 8 == 0 else (STEEL_DECK[0] if rng.random() < 0.7 else rng.choice(STEEL_DECK))
            else:
                px[x, y] = BRIDGE_STONE[0] if rng.random() < 0.7 else rng.choice(BRIDGE_STONE)
    img = img if vertical else img.rotate(90)
    # The shadow falls on the water to the south and east of the deck.
    out = Image.new("RGBA", (T, T), (0, 0, 0, 0))
    shadow = Image.new("RGBA", (T, T), DECK_SHADOW)
    mask = img.split()[3]
    out.paste(shadow, (2, 2), mask)
    out.alpha_composite(img)
    return out


def rails(material, vertical):
    """The railings along both sides of a bridge, drawn over the road: timber rails, stone parapets, a steel truss,
    plain steel girders, or the main cables of a suspension or cable-stayed bridge."""
    if material in ("truss", "girder", "cable"):
        return bridge_sides(material, vertical)
    img = Image.new("RGBA", (T, T), (0, 0, 0, 0))
    px = img.load()
    lo, hi = (WOOD_LO, WOOD_HI) if material == "wood" else (0, T - 1)
    # Stone parapets are thicker than timber rails.
    xs = (lo, hi) if material == "wood" else (lo, lo + 1, hi - 1, hi)
    col = RAIL if material == "wood" else PARAPET_STONE
    for y in range(T):
        for x in xs:
            px[x, y] = col
        if y % 6 == 0:
            for x in xs:
                px[x, y] = shade(col, 0.7)
    return img if vertical else img.rotate(90)


def bridge_sides(kind, vertical):
    img = Image.new("RGBA", (T, T), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    if kind == "truss":
        # Deep steel sides, and the bracing overhead that crosses the road from side to side.
        for x0 in (0, T - 3):
            d.rectangle([x0, 0, x0 + 2, T - 1], GIRDER)
            for y in range(0, T, 8):
                d.line([x0, y, x0 + 2, y + 3], GIRDER_LIGHT)
        for y0 in (0, 16):
            d.line([3, y0, T - 4, y0 + 15], (*GIRDER[:3], 150))
            d.line([T - 4, y0, 3, y0 + 15], (*GIRDER[:3], 150))
    elif kind == "girder":
        for x0 in (0, T - 2):
            d.rectangle([x0, 0, x0 + 1, T - 1], GIRDER)
            for y in range(2, T, 4):
                d.point((x0 + (1 if x0 == 0 else 0), y), GIRDER_LIGHT)
    else:
        # A low parapet, the main cable just outside it and the hangers down to the deck.
        for x0 in (1, T - 3):
            d.rectangle([x0, 0, x0 + 1, T - 1], PARAPET_STONE)
        for x0 in (0, T - 1):
            d.line([x0, 0, x0, T - 1], CABLE)
            for y in range(1, T, 4):
                d.point((x0 + (1 if x0 == 0 else -1), y), CABLE)
    return img if vertical else img.rotate(90)


def pier(kind, vertical):
    """What stands up out of a bridge on some of its tiles: a suspension bridge's towers, a cable-stayed one's pylon with
    its fan of cables, the round pier a swing bridge turns on, or the towers a lift bridge's span rises between."""
    img = Image.new("RGBA", (T, T), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    if kind == "tower":
        for x0 in (0, T - 5):
            d.rectangle([x0, 11, x0 + 4, 20], TOWER_STONE, OUTLINE)
            d.line([x0 + 1, 12, x0 + 3, 12], shade(TOWER_STONE, 1.15))
        d.rectangle([5, 14, T - 6, 16], shade(TOWER_STONE, 0.8))
    elif kind == "pylon":
        mx = T // 2
        for (ex, ey) in ((0, 0), (T - 1, 0), (0, T - 1), (T - 1, T - 1), (0, 8), (T - 1, 8), (0, T - 9), (T - 1, T - 9)):
            d.line([mx, 15, ex, ey], CABLE)
        d.rectangle([mx - 3, 12, mx + 2, 19], TOWER_STONE, OUTLINE)
        d.rectangle([mx - 1, 14, mx, 17], shade(TOWER_STONE, 1.15))
    elif kind == "pivot":
        d.ellipse([3, 3, T - 4, T - 4], shade(BRIDGE_STONE[0], 0.8), OUTLINE)
        d.ellipse([7, 7, T - 8, T - 8], BRIDGE_STONE[0])
    else:
        # Lift towers of steel either side, the machinery on top.
        for x0 in (0, T - 6):
            d.rectangle([x0, 6, x0 + 5, 25], GIRDER, OUTLINE)
            d.line([x0 + 1, 7, x0 + 4, 24], GIRDER_LIGHT)
            d.line([x0 + 4, 7, x0 + 1, 24], GIRDER_LIGHT)
            d.rectangle([x0 + 1, 13, x0 + 4, 18], c("#c9b48a"))
    img = img if vertical else img.rotate(90)
    out = Image.new("RGBA", (T, T), (0, 0, 0, 0))
    out.paste(Image.new("RGBA", (T, T), DECK_SHADOW), (3, 3), img.split()[3])
    out.alpha_composite(img)
    return out


def portal(look, rail, heading):
    """Where a tunnel comes up: a concrete headwall with the dark mouth in it, and the road or track running out of it
    toward [heading] (1 north to 4 west). Drawn opening south and turned."""
    img = Image.new("RGBA", (T, T), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    rng = random.Random(7800 + rail)
    lo, hi = (TRACK_LO - 2, TRACK_HI + 2) if rail else (STREET_LO, STREET_HI)
    # The cutting down to the mouth: retaining walls either side.
    wall = c("#a8a399") if look != "snow" else c("#e6eaee")
    d.rectangle([lo - 3, 8, lo - 1, T - 1], wall)
    d.rectangle([hi + 1, 8, hi + 3, T - 1], wall)
    if rail:
        cols = BALLAST[look]
        d.rectangle([lo, 10, hi, T - 1], cols[1])
        for y in range(11, T, 4):
            d.line([lo + 1, y, hi - 1, y], SLEEPER[look])
        for x in (TRACK_LO + 2, TRACK_HI - 2):
            d.line([x, 10, x, T - 1], RAIL_STEEL)
    else:
        road = ASPHALT[look] if "ASPHALT" in globals() else [c("#6d6a64"), c("#67645f"), c("#73706a")]
        noise_fill(img, (lo, 10, hi + 1, T), road, rng)
    # The headwall across the top, and the dark mouth in it, deeper at the back.
    d.rectangle([lo - 4, 4, hi + 4, 10], shade(wall, 0.9), OUTLINE)
    d.rectangle([lo + 1, 6, hi - 1, 12], c("#141414"))
    d.rectangle([lo + 3, 6, hi - 3, 9], c("#050505"))
    d.line([lo - 4, 4, hi + 4, 4], shade(wall, 1.15))
    turns = {3: 0, 4: 270, 1: 180, 2: 90}
    return img.rotate(turns[heading])


def highway(look, mask):
    """A highway carriageway: the full tile in darker asphalt, a white line along each edge on a narrow shoulder, and lane dashes."""
    img = paved(look, mask, 0, T - 1, 3, False)
    px = img.load()
    walks = WALK[look]
    curb = CURB[look]
    edge = c("#e8e8e4") if look != "snow" else c("#ffffff")
    shoulder = c("#6a6a68") if look != "snow" else c("#c8ced4")
    for y in range(T):
        for x in range(T):
            p = px[x, y]
            if p[3] == 0:
                continue
            if p[:3] in (walks[0][:3], walks[1][:3]):
                px[x, y] = shoulder
            elif p[:3] == curb[:3]:
                px[x, y] = edge
            else:
                px[x, y] = shade(p, 0.82)
    # Two lanes each way: a dashed line between them, along straight runs.
    for k in range(T):
        if k % 8 >= 4:
            continue
        if mask == 5:
            px[T // 2, k] = edge
        elif mask == 10:
            px[k, T // 2] = edge
    return img


def ramp(look, mask):
    """A slip road, one lane between white lines: a quarter curve round the corner it turns, or straight."""
    img = Image.new("RGBA", (T, T), (0, 0, 0, 0))
    px = img.load()
    rng = random.Random(6700 + mask)
    cols = MACADAM[look]
    edge = c("#e8e8e4") if look != "snow" else c("#ffffff")
    lo, hi = 8, 23
    # The corner a turn curves round: between the two sides it joins.
    corners = {3: (T, 0), 6: (T, T), 12: (0, T), 9: (0, 0)}
    centre = corners.get(mask)
    for y in range(T):
        for x in range(T):
            if centre is not None:
                d = math.hypot(x + 0.5 - centre[0], y + 0.5 - centre[1])
                if not (lo <= d <= hi + 1):
                    continue
                on_edge = d < lo + 1.2 or d > hi - 0.2
            else:
                inside = road_shape(mask if mask else 5, lo, hi)
                if not inside(x, y):
                    continue
                on_edge = not (inside(x - 1, y) and inside(x + 1, y) and inside(x, y - 1) and inside(x, y + 1))
            if on_edge:
                px[x, y] = edge
            else:
                r = rng.random()
                px[x, y] = shade(cols[0] if r < 0.7 else cols[1] if r < 0.85 else cols[2], 0.85)
    return img


def overpass(look, vertical):
    """A road carried over another on a concrete deck: parapets, the road on it, and its shadow on the road below."""
    img = Image.new("RGBA", (T, T), (0, 0, 0, 0))
    px = img.load()
    rng = random.Random(6600)
    lo, hi = 3, T - 4
    cols = MACADAM[look]
    concrete = c("#b8b6ae") if look != "snow" else c("#dfe5ea")
    for y in range(T):
        for x in range(lo, hi + 1):
            if x in (lo, hi):
                px[x, y] = shade(concrete, 0.7) if y % 6 == 0 else concrete
            elif x in (lo + 1, hi - 1):
                px[x, y] = concrete
            else:
                r = rng.random()
                px[x, y] = cols[0] if r < 0.7 else cols[1] if r < 0.85 else cols[2]
    for y in range(0, T, 6):
        for k in range(3):
            if y + k < T:
                px[(lo + hi) // 2, y + k] = CENTRE_LINE[look]
    img = img if vertical else img.rotate(90)
    out = Image.new("RGBA", (T, T), (0, 0, 0, 0))
    shadow = Image.new("RGBA", (T, T), (10, 12, 16, 120))
    out.paste(shadow, (3, 3), img.split()[3])
    out.alpha_composite(img)
    return out


ROAD_ART = [("road_dirt", road), ("road_gravel", gravel_road), ("road_lane", lane), ("road_street", street), ("road_avenue", avenue), ("road_highway", highway), ("road_ramp", ramp)]


# ---- buildings -----------------------------------------------------------------
# Seen from above with the south wall showing: the roof is the footprint moved up
# by the wall's height, and the wall hangs below it down to the ground. A sprite
# is its footprint's tiles plus the wall's height above them. Each storey is 6 px.

STOREY = 6
OUTLINE = c("#1e2124")
SIDING = [c("#e8dcb5"), c("#a9c4d6"), c("#b7c9a0"), c("#e0b8a0")]
SHINGLE = [c("#8c3b2e"), c("#6b4a36"), c("#5b5f6b")]
BRICK = c("#9c4a36")
BRICK20 = c("#a8583f")
TRIM = c("#efe8d8")
SAW = [c("#7d7f82"), c("#a3a6a8")]
WINDOW = c("#35414b")
DOOR = c("#5a3a2a")
TAR_ROOF = [c("#5c5c62"), c("#525258"), c("#67676d")]
GRAVEL_ROOF = [c("#8c8476"), c("#7f776a"), c("#999182")]
MEMBRANE_ROOF = [c("#b2b4b6"), c("#a8aaac"), c("#bcbec0")]
PARAPET = c("#7a7a80")
STONE = c("#cfc6b0")
TIMBER = c("#b08a5a")
IRON_ROOF = c("#7f868c")
SKYLIGHT = c("#9fc3d8")
SNOW_ROOF = [c("#f2f6f9"), c("#dfe7ee"), c("#c9d6e0")]


def lift_for(height):
    return height + (-height % GRID)


class Building:
    """One building's sprite: its tiles, its south wall and its roof."""

    def __init__(self, tiles_w=1, tiles_h=1, height=12):
        self.w, self.h = tiles_w, tiles_h
        self.lift = lift_for(height + 2)
        self.img = Image.new("RGBA", (tiles_w * T, tiles_h * T + self.lift), (0, 0, 0, 0))
        self.d = ImageDraw.Draw(self.img)
        self.casters = []
        # Where smoke or steam comes out: (kind, x, y) in tile pixels, kept with the
        # image so it reaches the atlas. The game draws it rising, as it's never baked in.
        self.plumes = []
        self.img.info["plumes"] = self.plumes

    def plume(self, kind, x, y):
        self.plumes.append((kind, x, y))

    def box(self, x0, y0, x1, y1, height):
        """A block on the footprint x0..x1, y0..y1 (tile pixels), [height] px tall.
        Returns (roof, wall) rectangles in sprite pixels."""
        top = self.lift
        roof = (x0, y0 + top - height, x1, y1 + top - height)
        wall = (x0, y1 + top - height + 1, x1, y1 + top)
        self.casters.append((1, x0, y0, x1 + 1, y1 + 1, height))
        return roof, wall

    def ground(self, x, y):
        """A point on the ground, from tile pixels to sprite pixels."""
        return x, y + self.lift


def gable_ew(d, roof, col, look):
    """A pitched roof with its ridge running east to west: the north slope in the
    light, the south slope in shade."""
    x0, y0, x1, y1 = roof
    mid = (y0 + y1) // 2
    if look == "snow":
        d.rectangle([x0, y0, x1, mid], SNOW_ROOF[0])
        d.rectangle([x0, mid + 1, x1, y1], SNOW_ROOF[1])
        d.line([x0 + 1, mid, x1 - 1, mid], SNOW_ROOF[2])
        # The eaves show under the snow.
        d.line([x0, y1, x1, y1], shade(col, 0.85))
        d.line([x0, y0, x1, y0], shade(col, 1.1))
    else:
        d.rectangle([x0, y0, x1, mid], shade(col, 1.18))
        d.rectangle([x0, mid + 1, x1, y1], shade(col, 0.86))
        for yy in range(y0 + 2, y1, 3):
            d.line([x0 + 1, yy, x1 - 1, yy], shade(col, 0.74 if yy > mid else 1.02))
        d.line([x0 + 2, mid, x1 - 2, mid], shade(col, 1.4))
    d.rectangle(roof, outline=OUTLINE)


def gable_ns(d, roof, wall, col, wall_col, look):
    """A pitched roof with its ridge running north to south, so its gable end
    faces the street: a triangle of wall standing up in front of the roof."""
    x0, y0, x1, y1 = roof
    mid = (x0 + x1) // 2
    if look == "snow":
        d.rectangle([x0, y0, mid, y1], SNOW_ROOF[0])
        d.rectangle([mid + 1, y0, x1, y1], SNOW_ROOF[1])
        d.line([x0, y0, x0, y1], shade(col, 1.1))
        d.line([x1, y0, x1, y1], shade(col, 0.85))
    else:
        d.rectangle([x0, y0, mid, y1], shade(col, 1.12))
        d.rectangle([mid + 1, y0, x1, y1], shade(col, 0.82))
        for xx in range(x0 + 2, x1, 3):
            d.line([xx, y0 + 1, xx, y1 - 1], shade(col, 0.95 if xx <= mid else 0.7))
        d.line([mid, y0 + 1, mid, y1 - 1], shade(col, 1.35))
    d.rectangle(roof, outline=OUTLINE)
    wx0, wy0, wx1, _ = wall
    rise = max(3, (wx1 - wx0) // 3)
    apex = ((wx0 + wx1) // 2, wy0 - rise)
    d.polygon([(wx0, wy0), apex, (wx1, wy0)], wall_col, OUTLINE)
    # A little attic window in the gable.
    d.rectangle([apex[0] - 1, wy0 - rise // 2, apex[0], wy0 - rise // 2 + 1], WINDOW)
    if look == "snow":
        d.line([wx0, wy0, apex[0], apex[1]], SNOW)
        d.line([apex[0], apex[1], wx1, wy0], SNOW_ROOF[2])


def flat_roof(img, roof, look, rng, features=(), parapet=PARAPET, busy=True):
    """A flat roof of tarred gravel with a parapet, and what stands on it: the features asked for, and enough plant,
    hatches and vents for its size so no roof is bare. The parapet's coping catches the light and throws a shadow on
    the roof inside it, the felt is laid in strips with stains where water stands, and each thing on it casts a shadow."""
    d = ImageDraw.Draw(img)
    x0, y0, x1, y1 = roof
    w = x1 - x0
    h = y1 - y0
    snowy = look == "snow"
    # The finish by the kind of building the parapet says it is: gravel or tar on the older stone and brick ones,
    # a pale membrane or gravel on the newer ones of concrete and steel.
    old = parapet in (STONE, PARAPET_STONE, TRIM) or (parapet[0] > parapet[2] + 20)
    modern = parapet[0] == parapet[1] == parapet[2] or abs(parapet[0] - parapet[2]) < 8 and parapet != PARAPET
    finishes = [GRAVEL_ROOF, TAR_ROOF, GRAVEL_ROOF] if old else [MEMBRANE_ROOF, GRAVEL_ROOF] if modern else [TAR_ROOF, GRAVEL_ROOF, MEMBRANE_ROOF]
    # One finish for every roof of a building.
    finish = img.info.get("roof") or img.info.setdefault("roof", rng.choice(finishes))
    base = finish[0]
    if snowy:
        d.rectangle(roof, SNOW_ROOF[0])
        # Drifts in the lee of the north parapet.
        d.line([x0 + 1, y0 + 2, x1 - 1, y0 + 2], SNOW_ROOF[2])
        d.line([x0 + 2, y0 + 3, x1 - 2, y0 + 3], SNOW_ROOF[1])
        d.rectangle([x0 + 1, y0 + 1, x1 - 1, y1 - 1], outline=c("#aab4bd"))
    else:
        noise_fill(img, (x0, y0, x1 + 1, y1 + 1), finish, rng, (0.7, 0.18, 0.12))
        # The felt in strips, and a stain or two where water stands.
        if w > 10:
            for yy in range(y0 + 5, y1 - 2, 5):
                d.line([x0 + 2, yy, x1 - 2, yy], shade(base, 0.9))
        for _ in range(max(1, w * h // 400)):
            sx = rng.randint(x0 + 3, max(x0 + 3, x1 - 6))
            sy = rng.randint(y0 + 3, max(y0 + 3, y1 - 5))
            d.ellipse([sx, sy, sx + rng.randint(3, 6), sy + rng.randint(2, 4)], shade(base, 0.86))
        # The parapet: its coping lit along the top, its shadow on the roof inside the south and east walls of it.
        d.rectangle([x0 + 1, y0 + 1, x1 - 1, y1 - 1], outline=parapet)
        d.line([x0 + 1, y0 + 1, x1 - 1, y0 + 1], shade(parapet, 1.35))
        d.line([x0 + 1, y0 + 1, x0 + 1, y1 - 1], shade(parapet, 1.2))
        d.line([x0 + 2, y0 + 2, x1 - 2, y0 + 2], shade(base, 0.72))
        d.line([x0 + 2, y0 + 2, x0 + 2, y1 - 2], shade(base, 0.8))
    for f in features:
        roof_feature(d, roof, f, look)
    # Enough on a bigger roof that it isn't bare: kept clear of what's already there.
    taken = [(roof[0] + fx - 2, roof[1] + fy - 3, roof[0] + fx + 8, roof[1] + fy + 6) for (_, fx, fy) in features]
    extra = max(0, w * h // 260 - len(features)) if busy else 0
    tries = 0
    while extra > 0 and tries < 40 and w > 12 and h > 10:
        tries += 1
        kind = rng.choice(["plant", "plant", "hatch", "vent", "skylight", "flue"])
        fx = rng.randint(4, max(4, w - 10))
        fy = rng.randint(4, max(4, h - 8))
        box = (x0 + fx - 1, y0 + fy - 1, x0 + fx + 8, y0 + fy + 6)
        if any(not (box[2] < t[0] or box[0] > t[2] or box[3] < t[1] or box[1] > t[3]) for t in taken):
            continue
        taken.append(box)
        extra -= 1
        if kind == "plant":
            roof_plant(d, x0 + fx, y0 + fy, look, rng, base)
        elif kind == "flue":
            d.rectangle([x0 + fx + 1, y0 + fy + 1, x0 + fx + 3, y0 + fy + 3], shade(base, 0.6))
            d.ellipse([x0 + fx, y0 + fy, x0 + fx + 2, y0 + fy + 2], c("#8a8a90"), OUTLINE)
        else:
            roof_feature(d, roof, (kind, fx, fy), look)
    d.rectangle(roof, outline=OUTLINE)


def roof_plant(d, x, y, look, rng, base=None):
    """A box of plant on a roof: a fan unit with its grille, and its shadow."""
    big = rng.random() < 0.5
    w = 7 if big else 5
    h = 5 if big else 3
    d.rectangle([x + 1, y + 2, x + w + 2, y + h + 2], shade(base or TAR_ROOF[0], 0.55))
    d.rectangle([x, y, x + w, y + h], SNOW_ROOF[0] if look == "snow" else c("#c4c8cc"), OUTLINE)
    d.line([x + 1, y + 1, x + w - 1, y + 1], c("#e4e8ea") if look != "snow" else SNOW)
    if big:
        d.ellipse([x + 1, y + 1, x + 5, y + 4], c("#4a4e54"))
        d.line([x + 3, y + 1, x + 3, y + 4], c("#8a8e94"))
    else:
        for xx in range(x + 1, x + w, 2):
            d.point((xx, y + 2), c("#6a6e74"))


def roof_feature(d, roof, feature, look):
    x0, y0, x1, y1 = roof
    kind, fx, fy = feature
    x, y = x0 + fx, y0 + fy
    snowy = look == "snow"
    if kind == "stack":
        d.rectangle([x, y, x + 2, y + 3], BRICK)
        d.line([x, y, x + 2, y], SNOW if snowy else OUTLINE)
        d.point((x + 1, y + 1), OUTLINE)
    elif kind == "skylight":
        d.rectangle([x, y, x + 4, y + 2], c("#d0d4d8"))
        d.rectangle([x + 1, y + 1, x + 3, y + 1], SNOW_ROOF[1] if snowy else SKYLIGHT)
    elif kind == "tank":
        d.ellipse([x, y - 3, x + 6, y + 3], c("#7a5a3a"))
        d.ellipse([x + 1, y - 2, x + 5, y + 1], SNOW if snowy else c("#96734c"))
        d.line([x + 1, y + 3, x + 1, y + 5], c("#4a3a2a"))
        d.line([x + 5, y + 3, x + 5, y + 5], c("#4a3a2a"))
    elif kind == "hatch":
        d.rectangle([x, y, x + 3, y + 2], c("#8a8a90"))
        d.line([x, y, x + 3, y], SNOW if snowy else c("#a0a0a6"))
    elif kind == "vent":
        d.rectangle([x, y, x + 1, y + 1], c("#9a9aa0"))


def windows(d, wall, storeys, glass=WINDOW, sill=None, every=5, width=2, height=3, skip_door=False, skip=()):
    x0, y0, x1, y1 = wall
    per = (y1 - y0 + 1) / storeys
    for s_ in range(storeys):
        wy = int(y0 + s_ * per + 2)
        for wx in range(x0 + 3, x1 - 2, every):
            if skip_door and s_ == storeys - 1 and abs(wx - (x0 + x1) // 2) < 3:
                continue
            if any(a <= wx <= b for a, b in skip):
                continue
            d.rectangle([wx, wy, wx + width - 1, wy + height - 1], glass)
            if sill:
                d.line([wx - 1, wy + height, wx + width, wy + height], sill)


def siding(d, wall, col):
    d.rectangle(wall, col)
    x0, y0, x1, y1 = wall
    for yy in range(y0 + 2, y1, 2):
        d.line([x0 + 1, yy, x1 - 1, yy], shade(col, 0.92))


def brick(d, wall, col):
    d.rectangle(wall, col)
    x0, y0, x1, y1 = wall
    for yy in range(y0 + 1, y1, 3):
        d.line([x0 + 1, yy, x1 - 1, yy], shade(col, 0.9))


def door(d, wall, col=DOOR, at=None):
    x0, y0, x1, y1 = wall
    cx = (x0 + x1) // 2 if at is None else at
    d.rectangle([cx - 1, y1 - 4, cx + 1, y1], col)


def porch(d, wall, roof_col, look):
    """A porch along the front: a low roof on posts, standing on the ground just
    south of the wall."""
    x0, _, x1, y1 = wall
    px0, px1 = x0 + 2, x1 - 2
    d.rectangle([px0, y1 - 4, px1, y1 - 3], SNOW_ROOF[0] if look == "snow" else shade(roof_col, 0.9))
    d.line([px0, y1 - 2, px1, y1 - 2], OUTLINE)
    for xx in range(px0, px1 + 1, 4):
        d.line([xx, y1 - 2, xx, y1 + 2], TRIM)
    d.line([px0, y1 + 3, px1, y1 + 3], c("#8a7a64"))


def bay(d, wall, wall_col, left):
    """A bay window standing out from the ground floor."""
    x0, y0, x1, y1 = wall
    bx = x0 + 2 if left else x1 - 8
    d.rectangle([bx, y1 - 5, bx + 6, y1 + 1], shade(wall_col, 1.06), OUTLINE)
    d.rectangle([bx + 1, y1 - 4, bx + 5, y1 - 2], WINDOW)
    d.line([bx + 3, y1 - 4, bx + 3, y1 - 2], TRIM)


def dormers(d, roof, col, look, count):
    """Little windowed gables on the south slope of an east to west roof."""
    x0, y0, x1, y1 = roof
    mid = (y0 + y1) // 2
    step = (x1 - x0) // (count + 1)
    for k in range(1, count + 1):
        cx = x0 + k * step
        d.rectangle([cx - 2, mid + 1, cx + 2, mid + 4], SNOW_ROOF[0] if look == "snow" else shade(col, 1.05), OUTLINE)
        d.rectangle([cx - 1, mid + 3, cx + 1, mid + 4], WINDOW)


# What comes out of a stack: soot from coal, grey smoke, a wisp of steam, or a
# cooling tower's cloud of it.
SOOT, SMOKE, STEAM, CLOUD = 0, 1, 2, 3


def chimney(b, x, y, height, col=BRICK, look="summer", plume=SOOT):
    """A tall chimney standing at tile pixel x, y."""
    top = b.lift
    d = b.d
    d.rectangle([x - 2, y + top - height, x + 1, y + top], col)
    d.line([x - 2, y + top - height, x - 2, y + top], shade(col, 1.2))
    d.line([x + 1, y + top - height, x + 1, y + top], shade(col, 0.75))
    d.rectangle([x - 2, y + top - height, x + 1, y + top - height + 1], SNOW if look == "snow" else shade(col, 0.5))
    b.casters.append((1, x - 2, y - 1, x + 2, y + 1, height))
    b.plume(plume, x, y - height)


# Each type is drawn by a function taking the look and a variant number. The
# variants differ in shape as well as colour: which way the roof runs, a porch,
# a bay window, dormers, what stands on a flat roof.

def house_shape(b, look, v, box, storeys, wall_col, roof_col, ns, extras, brick_walls=False):
    x0, y0, x1, y1 = box
    height = storeys * STOREY + 1
    roof, wall = b.box(x0, y0, x1, y1, height)
    if ns:
        # A gable facing the street adds its triangle above the wall.
        b.casters[-1] = (1, x0, y0, x1 + 1, y1 + 1, height + (x1 - x0) // 3)
    (brick if brick_walls else siding)(b.d, wall, wall_col)
    windows(b.d, wall, storeys, sill=TRIM, every=6 if storeys < 3 else 4, skip_door=True,
            skip=[(wall[0], wall[0] + 9)] if "bay_left" in extras else [(wall[2] - 9, wall[2])] if "bay_right" in extras else [])
    door(b.d, wall)
    b.d.rectangle(wall, outline=OUTLINE)
    if ns:
        gable_ns(b.d, roof, wall, roof_col, wall_col, look)
    else:
        gable_ew(b.d, roof, roof_col, look)
        if "dormers" in extras:
            dormers(b.d, roof, roof_col, look, 2 if x1 - x0 > 20 else 1)
    if "stack" in extras:
        roof_feature(b.d, roof, ("stack", (x1 - x0) * 3 // 4, 1), look)
    if "bay_left" in extras:
        bay(b.d, wall, wall_col, True)
    if "bay_right" in extras:
        bay(b.d, wall, wall_col, False)
    if "porch" in extras:
        porch(b.d, wall, roof_col, look)
    return b


def front_yard(b, look, v, box):
    """A home's yard round [box]: a path from the door to the street, a fence, a hedge or nothing along the front, a
    bed of flowers by the door, and a tree or a shed out back."""
    d = b.d
    top = b.lift
    rng = random.Random(3100 + v * 7 + box[0])
    x0, y0, x1, y1 = box
    cx = (x0 + x1) // 2
    snowy = look == "snow"
    # Out back first, so the house stands in front of it.
    if rng.random() < 0.6:
        tree_at(b, look, v, rng.choice([3, 28]), 7, 4, rng)
    else:
        sx = rng.choice([1, 24])
        d.rectangle([sx, top + 1, sx + 5, top + 5], SNOW_ROOF[0] if snowy else c("#8a6a4a"), OUTLINE)
        d.line([sx + 1, top + 2, sx + 4, top + 2], SNOW_ROOF[1] if snowy else c("#a8845a"))
    if y1 >= 29:
        return
    d.rectangle([cx - 1, top + y1 + 1, cx + 1, top + 31], c("#e6ecf0") if snowy else PATH)
    if not snowy:
        flowers(b, look, cx - 7, y1 + 1, cx - 3, y1 + 4, rng, 4)
        flowers(b, look, cx + 3, y1 + 1, cx + 7, y1 + 4, rng, 4)
    kind = v % 3
    if kind == 0:
        # A picket fence, with a gate at the path.
        for xx in range(1, 31, 2):
            if abs(xx - cx) > 2:
                d.line([xx, top + 29, xx, top + 30], c("#f2f2ea") if not snowy else c("#d0d6dc"))
        d.line([1, top + 30, 30, top + 30], c("#d8d4ca"))
    elif kind == 1:
        hedge = c("#3f6a32") if look in ("summer", "spring", "dry") else c("#5a6a3a") if look == "autumn" else c("#4a5a3a")
        for (a, z) in ((1, cx - 3), (cx + 3, 30)):
            if z > a:
                d.rectangle([a, top + 29, z, top + 31], SNOW_ROOF[0] if snowy else hedge)
                d.line([a, top + 29, z, top + 29], SNOW_ROOF[1] if snowy else shade(hedge, 1.25))


def cottage(look, v):
    b = Building(height=STOREY + 12)
    shapes = [(False, ("porch", "stack")), (True, ("stack",)), (False, ("dormers",)), (True, ("porch",))]
    ns, extras = shapes[v]
    front_yard(b, look, v, (7, 9, 24, 24))
    return house_shape(b, look, v, (7, 9, 24, 24), 1, SIDING[v % 4], SHINGLE[v % 3], ns, extras)


def house(look, v):
    b = Building(height=2 * STOREY + 10)
    shapes = [(False, ("porch", "stack")), (True, ("bay_left",)), (False, ("dormers", "stack")), (True, ("porch", "stack"))]
    ns, extras = shapes[v]
    front_yard(b, look, v, (5, 7, 26, 24))
    return house_shape(b, look, v, (5, 7, 26, 24), 2, SIDING[(v + 1) % 4], SHINGLE[(v + 1) % 3], ns, extras)


def large_house(look, v):
    b = Building(height=2 * STOREY + 12)
    shapes = [(False, ("porch", "bay_left", "stack")), (True, ("porch", "stack")), (False, ("dormers", "bay_right", "stack"))]
    ns, extras = shapes[v]
    brick_walls = v == 2
    wall_col = BRICK20 if brick_walls else SIDING[(v + 2) % 4]
    front_yard(b, look, v, (3, 5, 28, 25))
    return house_shape(b, look, v, (3, 5, 28, 25), 2, wall_col, SHINGLE[v % 3], ns, extras, brick_walls)


def tenement(look, v):
    b = Building(height=3 * STOREY + 2)
    roof, wall = b.box(2, 3, 29, 28, 3 * STOREY + 2)
    brick(b.d, wall, [BRICK, BRICK20, c("#8a5a44")][v % 3])
    windows(b.d, wall, 3, sill=TRIM, every=4, skip_door=True)
    door(b.d, wall)
    b.d.line([wall[0], wall[1] + 1, wall[2], wall[1] + 1], TRIM)
    b.d.rectangle(wall, outline=OUTLINE)
    features = [
        [("stack", 3, 3), ("stack", 22, 3), ("hatch", 12, 12)],
        [("tank", 17, 8), ("stack", 3, 3), ("stack", 22, 18)],
        [("skylight", 10, 8), ("skylight", 10, 15), ("stack", 22, 3)],
    ][v % 3]
    flat_roof(b.img, roof, look, random.Random(7000 + v), features)
    return b


# Shops and works: each variant changes several things at once (how much of
# the lot it takes, what it's built of, its roof, its front and its yard), so
# a street of them doesn't read as one building over and over.

PAINT = [TIMBER, c("#c9b48a"), c("#e6dfcc"), c("#9fb39a"), c("#9c4f3a"), c("#8ea4b3")]
BRICKS = [BRICK, c("#8a5a44"), c("#b0704e"), c("#7a4a3c"), c("#c9a86a")]
SANDSTONE = c("#c98f6e")
GREY_STONE = c("#b9b8b0")
AWNINGS = [c("#c8423a"), c("#3c78a8"), c("#3f8a4a"), c("#d09a2a"), c("#7a3f7a"), c("#2f5f5a")]
PLATE_GLASS = c("#8fb3c9")
CRATE = c("#a07a4a")
LUMBER = c("#c29a62")


def awning(d, wall, colour, look, y_up=8):
    """A striped canvas awning over the shop windows."""
    x0, _, x1, y1 = wall
    top = y1 - y_up
    for xx in range(x0 + 1, x1, 4):
        d.rectangle([xx, top, xx + 1, top + 2], SNOW_ROOF[0] if look == "snow" else colour)
        d.rectangle([xx + 2, top, xx + 3, top + 2], TRIM)
    d.line([x0 + 1, top + 3, x1 - 1, top + 3], shade(colour, 0.6))


def sign(d, x0, x1, y, colour):
    """A painted signboard with a line of lettering."""
    d.rectangle([x0, y, x1, y + 2], colour, OUTLINE)
    for xx in range(x0 + 2, x1 - 1, 2):
        d.point((xx, y + 1), TRIM)


def yard(b, look, kind, x0, x1, y):
    """Things standing on the ground in front: crates, a lumber pile or barrels, at tile row [y]."""
    d = b.d
    gy = y + b.lift
    snow = look == "snow"
    if kind == "crates":
        for k, xx in enumerate(range(x0, x1 - 2, 4)):
            h = 3 if k % 2 == 0 else 2
            d.rectangle([xx, gy - h, xx + 2, gy], CRATE, OUTLINE)
            if snow: d.line([xx, gy - h, xx + 2, gy - h], SNOW)
    elif kind == "lumber":
        d.rectangle([x0, gy - 2, x1, gy], LUMBER, OUTLINE)
        for xx in range(x0 + 2, x1, 3):
            d.point((xx, gy - 1), shade(LUMBER, 0.7))
        if snow: d.line([x0, gy - 2, x1, gy - 2], SNOW)
    elif kind == "barrels":
        for xx in range(x0, x1 - 1, 3):
            d.ellipse([xx, gy - 2, xx + 2, gy], c("#6b4a30"), OUTLINE)
            if snow: d.point((xx + 1, gy - 2), SNOW)


def lean_to(b, look, x0, y0, x1, y1, col, roof_col):
    """A low shed against the side of the main building."""
    roof, wall = b.box(x0, y0, x1, y1, STOREY - 1)
    siding(b.d, wall, col)
    b.d.rectangle(wall, outline=OUTLINE)
    b.d.rectangle(roof, SNOW_ROOF[1] if look == "snow" else shade(roof_col, 0.95), OUTLINE)
    for yy in range(roof[1] + 2, roof[3], 2):
        if look != "snow": b.d.line([roof[0] + 1, yy, roof[2] - 1, yy], shade(roof_col, 0.8))


def general_store(look, v):
    """A timber store: a false front (or a gable to the street), a sign, sometimes a boardwalk and a shed."""
    width, depth, paint, roof_col, front, extra = [
        ((4, 27), (10, 25), 0, IRON_ROOF, "flat", "porch"),
        ((6, 25), (12, 25), 1, SHINGLE[1], "stepped", "shed"),
        ((3, 24), (9, 24), 2, SHINGLE[0], "gable", "crates"),
        ((7, 28), (11, 26), 3, IRON_ROOF, "stepped", "porch"),
        ((5, 22), (10, 25), 4, SHINGLE[2], "flat", "shed"),
        ((4, 26), (12, 26), 5, IRON_ROOF, "gable", "barrels"),
    ][v]
    x0, x1 = width
    y0, y1 = depth
    b = Building(height=STOREY + 7)
    wall_col = PAINT[paint]
    if extra == "shed":
        # Built first, so the store is drawn over it.
        sx = x1 + 1 if x1 < 26 else x0 - 5
        lean_to(b, look, sx, y0 + 5, sx + 4, y1, shade(wall_col, 0.9), roof_col)
    roof, wall = b.box(x0, y0, x1, y1, STOREY)
    if front == "gable":
        siding(b.d, wall, wall_col)
        b.d.rectangle([wall[0] + 3, wall[3] - 5, wall[2] - 3, wall[3] - 2], PLATE_GLASS)
        door(b.d, wall)
        b.d.rectangle(wall, outline=OUTLINE)
        gable_ns(b.d, roof, wall, roof_col, wall_col, look)
        b.casters[-1] = (1, x0, y0, x1 + 1, y1 + 1, STOREY + (x1 - x0) // 3)
    else:
        # The false front stands taller than the roof behind it.
        rise = 5
        top = (wall[0], wall[1] - rise, wall[2], wall[3])
        siding(b.d, top, wall_col)
        if front == "stepped":
            # The front steps up in the middle, with the roof showing either side.
            behind = SNOW_ROOF[1] if look == "snow" else shade(roof_col, 0.86)
            for cx in (top[0], top[2] - 4):
                b.d.rectangle([cx, top[1], cx + 4, top[1] + 1], behind)
        sign(b.d, top[0] + 3, top[2] - 3, top[1] + 2, [c("#3a3a3a"), c("#6b2330"), c("#2e4a3a")][v % 3])
        b.d.rectangle([top[0] + 3, top[3] - 6, top[2] - 3, top[3] - 2], PLATE_GLASS)
        door(b.d, top)
        b.d.rectangle(top if front == "flat" else (top[0], top[1] + 2, top[2], top[3]), outline=OUTLINE)
        gable_ew(b.d, (roof[0], roof[1], roof[2], top[1] - 1), roof_col, look)
        b.casters[-1] = (1, x0, y0, x1 + 1, y1 + 1, STOREY + rise)
    if extra == "porch":
        porch(b.d, wall, roof_col, look)
    elif extra in ("crates", "barrels"):
        yard(b, look, extra, x1 - 9, x1, 30)
    return b


def shop(look, v):
    """Two storeys, the shop below and rooms above: brick, stone or painted, with an awning or a signboard."""
    width, walls, awn, roof_kind, extra = [
        ((2, 29), ("brick", 0), 0, "flat", ("skylight", "stack")),
        ((4, 27), ("brick", 2), 1, "gable", ()),
        ((2, 25), ("stone", 0), None, "flat", ("hatch", "stack")),
        ((5, 29), ("paint", 2), 3, "gable", ("bay",)),
        ((3, 28), ("brick", 4), 4, "flat", ("vent", "tank")),
        ((2, 26), ("paint", 5), None, "flat", ("stack",)),
    ][v]
    x0, x1 = width
    b = Building(height=2 * STOREY + 4)
    height = 2 * STOREY + 2
    roof, wall = b.box(x0, 6, x1, 26, height)
    material, k = walls
    col = BRICKS[k] if material == "brick" else GREY_STONE if material == "stone" else PAINT[k]
    (brick if material == "brick" else siding)(b.d, wall, col)
    if material == "stone":
        b.d.rectangle(wall, col)
        b.d.line([wall[0], wall[1] + STOREY, wall[2], wall[1] + STOREY], shade(col, 0.8))
    wx0, wy0, wx1, wy1 = wall
    windows(b.d, (wx0, wy0, wx1, wy0 + STOREY), 1, sill=TRIM, every=5 if v % 2 == 0 else 6)
    b.d.rectangle([wx0 + 2, wy1 - 5, wx1 - 2, wy1 - 1], PLATE_GLASS)
    b.d.line([(wx0 + wx1) // 2, wy1 - 5, (wx0 + wx1) // 2, wy1 - 1], shade(col, 0.6))
    if awn is not None:
        awning(b.d, wall, AWNINGS[awn], look)
    else:
        sign(b.d, wx0 + 2, wx1 - 2, wy1 - 9, [c("#2b3440"), c("#6b2330")][v % 2])
    b.d.rectangle(wall, outline=OUTLINE)
    if roof_kind == "gable":
        gable_ew(b.d, roof, SHINGLE[v % 3], look)
        if "bay" in extra:
            dormers(b.d, roof, SHINGLE[v % 3], look, 2)
    else:
        spots = {"skylight": ("skylight", 8, 6), "stack": ("stack", 20, 3), "hatch": ("hatch", 5, 5),
                 "vent": ("vent", 16, 9), "tank": ("tank", 6, 9)}
        flat_roof(b.img, roof, look, random.Random(7100 + v), [spots[f] for f in extra if f in spots])
    return b


def light_well(d, roof, look, wall_col):
    """A light well down through the middle of a flat roof: a lit coping round the opening, the far wall inside it in
    shade with its windows, and the dark well below."""
    x0, y0, x1, y1 = roof
    cx0 = x0 + (x1 - x0) // 3
    cx1 = x1 - (x1 - x0) // 3
    cy0 = y0 + (y1 - y0) // 3
    cy1 = y1 - (y1 - y0) // 4
    d.rectangle([cx0 - 1, cy0 - 1, cx1 + 1, cy1 + 1], c("#d8d4ca") if look != "snow" else SNOW)
    d.rectangle([cx0, cy0, cx1, cy1], c("#2a2c30"))
    far = min(cy1, cy0 + max(2, (cy1 - cy0) // 2))
    d.rectangle([cx0, cy0, cx1, far], shade(wall_col, 0.55))
    for xx in range(cx0 + 1, cx1, 3):
        d.point((xx, cy0 + 1), c("#46586a"))


def raised(b, box, height, base):
    """Like [Building.box], for a block standing on a roof [base] high: its wall shows only above that roof."""
    x0, y0, x1, y1 = box
    top = b.lift
    roof = (x0, y0 + top - height, x1, y1 + top - height)
    wall = (x0, y1 + top - height + 1, x1, y1 + top - base)
    b.casters.append((1, x0, y0, x1 + 1, y1 + 1, height))
    return roof, wall


def setback(b, look, box, height, wall_col, rng, glass=None, base=None):
    """A storey set back on a roof [base] high, its own windows, and its own roof."""
    roof, wall = raised(b, box, height, base if base is not None else height - STOREY - 2)
    b.d.rectangle(wall, wall_col)
    windows(b.d, wall, 1, glass=glass or WINDOW, every=4)
    b.d.rectangle(wall, outline=OUTLINE)
    flat_roof(b.img, roof, look, rng, [], parapet=shade(wall_col, 1.1), busy=False)
    return roof


def turret(b, look, x, y, r, height, wall_col, base):
    """A round corner turret standing above a roof [base] high, with a copper dome gone green and a finial."""
    d = b.d
    roof, wall = raised(b, (x - r, y - r, x + r, y + r), height, base)
    d.rectangle(wall, wall_col)
    d.line([wall[0] + 1, wall[1], wall[0] + 1, wall[3]], shade(wall_col, 1.15))
    d.rectangle(wall, outline=OUTLINE)
    dome = SNOW_ROOF[0] if look == "snow" else c("#5f9a8a")
    d.pieslice([roof[0], roof[1] - r, roof[2], roof[3] + r // 2], 180, 360, dome, OUTLINE)
    mx = (roof[0] + roof[2]) // 2
    d.line([mx, roof[1] - r, mx, roof[1] - r - 3], c("#b08a3a"))


def roof_sign(d, roof, colour, letters):
    """A sign on its frame along the front of a roof, lettered in light points."""
    x0, y0, x1, y1 = roof
    sx0 = x0 + 3
    sx1 = min(x1 - 3, sx0 + letters * 3 + 2)
    d.line([sx0 + 1, y1 - 1, sx0 + 1, y1 - 3], c("#5a5a60"))
    d.line([sx1 - 1, y1 - 1, sx1 - 1, y1 - 3], c("#5a5a60"))
    d.rectangle([sx0, y1 - 7, sx1, y1 - 3], colour, OUTLINE)
    for k in range(letters):
        d.point((sx0 + 2 + k * 3, y1 - 5), c("#f2f2ea"))


def hotel(look, v):
    """Three or four storeys, with a canopy over the door and a busy roof."""
    storeys, walls, roof_kind, features = [
        (3, BRICK20, "flat", [("tank", 19, 8), ("stack", 3, 3), ("stack", 3, 18), ("skylight", 9, 12)]),
        (3, c("#b8866a"), "mansard", []),
        (4, GREY_STONE, "flat", [("tank", 4, 8), ("stack", 24, 3), ("hatch", 14, 16)]),
        (4, SANDSTONE, "flat", [("skylight", 6, 6), ("skylight", 16, 6), ("stack", 25, 14)]),
    ][v]
    height = storeys * STOREY + 3
    b = Building(height=height + 4)
    roof, wall = b.box(1, 2, 30, 28, height)
    if walls in (GREY_STONE, SANDSTONE):
        b.d.rectangle(wall, walls)
        for yy in range(wall[1] + STOREY, wall[3], STOREY):
            b.d.line([wall[0], yy, wall[2], yy], shade(walls, 0.88))
    else:
        brick(b.d, wall, walls)
    windows(b.d, wall, storeys, sill=TRIM, every=4, skip_door=True)
    door(b.d, wall, c("#3a2a20"))
    # A canopy on posts over the door.
    cx = (wall[0] + wall[2]) // 2
    b.d.rectangle([cx - 5, wall[3] - 6, cx + 5, wall[3] - 5], AWNINGS[(v + 1) % len(AWNINGS)], OUTLINE)
    b.d.line([cx - 4, wall[3] - 4, cx - 4, wall[3]], TRIM)
    b.d.line([cx + 4, wall[3] - 4, cx + 4, wall[3]], TRIM)
    b.d.rectangle([wall[0], wall[1], wall[2], wall[1] + 1], STONE)
    b.d.rectangle(wall, outline=OUTLINE)
    if roof_kind == "mansard":
        gable_ew(b.d, roof, SHINGLE[2], look)
        dormers(b.d, roof, SHINGLE[2], look, 3)
    else:
        flat_roof(b.img, roof, look, random.Random(7200 + v), features, parapet=STONE)
        if v == 0:
            roof_sign(b.d, roof, c("#8a2a2a"), 5)
        elif v == 3:
            setback(b, look, (6, 8, 25, 22), height + 5, shade(walls, 1.05), random.Random(7210), base=height)
    return b


def bank(look, v):
    """Stone, with columns across the front, and a pediment on the grander ones."""
    stone, columns, pediment, width = [
        (STONE, 4, False, (3, 28)),
        (GREY_STONE, 5, True, (2, 29)),
        (SANDSTONE, 3, True, (5, 26)),
        (c("#dcd6c8"), 6, False, (1, 30)),
    ][v]
    x0, x1 = width
    b = Building(height=2 * STOREY + 9)
    roof, wall = b.box(x0, 6, x1, 26, 2 * STOREY + 4)
    b.d.rectangle(wall, stone)
    wx0, wy0, wx1, wy1 = wall
    step = max(3, (wx1 - wx0 - 4) // columns)
    for xx in range(wx0 + 3, wx1 - 1, step):
        b.d.line([xx, wy0 + 4, xx, wy1 - 1], c("#efe8d8"))
        b.d.line([xx + 1, wy0 + 4, xx + 1, wy1 - 1], shade(stone, 0.85))
    b.d.rectangle([wx0, wy0, wx1, wy0 + 2], shade(stone, 1.08))
    door(b.d, wall, c("#3a2a20"))
    b.d.rectangle(wall, outline=OUTLINE)
    flat_roof(b.img, roof, look, random.Random(7300 + v), [("skylight", (x1 - x0) // 2 - 2, 6), ("skylight", (x1 - x0) // 2 - 2, 12)], parapet=stone)
    if pediment:
        cx = (wx0 + wx1) // 2
        half = (wx1 - wx0) // 3
        b.d.polygon([(cx - half, wy0), (cx, wy0 - 5), (cx + half, wy0)], shade(stone, 1.05), OUTLINE)
        b.casters[-1] = (1, x0, 6, x1 + 1, 27, 2 * STOREY + 9)
    elif v == 3:
        # A banking hall under a shallow dome.
        mx = (roof[0] + roof[2]) // 2
        my = (roof[1] + roof[3]) // 2
        b.d.ellipse([mx - 6, my - 5, mx + 6, my + 4], SNOW_ROOF[0] if look == "snow" else c("#6e8f86"), OUTLINE)
        b.d.arc([mx - 4, my - 4, mx + 2, my + 1], 180, 300, c("#9ab8ae") if look != "snow" else SNOW)
    return b


def workshop(look, v):
    """A low shop for a trade: brick, stone or timber, a big door, and its yard out front."""
    width, depth, walls, roof_kind, door_at, stuff = [
        ((3, 28), (8, 24), ("brick", 0), "iron", "left", "lumber"),
        ((5, 26), (10, 25), ("brick", 1), "iron", "right", "crates"),
        ((2, 24), (8, 23), ("timber", 0), "shingle", "left", "lumber"),
        ((4, 29), (9, 24), ("stone", 0), "iron_ns", "right", "barrels"),
        ((6, 27), (7, 24), ("brick", 3), "shingle", "left", "crates"),
        ((3, 25), (10, 25), ("timber", 3), "iron", "right", "barrels"),
    ][v]
    x0, x1 = width
    y0, y1 = depth
    b = Building(height=STOREY + 8)
    roof, wall = b.box(x0, y0, x1, y1, STOREY + 3)
    material, k = walls
    if material == "brick":
        col = BRICKS[k]
        brick(b.d, wall, col)
    elif material == "stone":
        col = GREY_STONE
        b.d.rectangle(wall, col)
    else:
        col = PAINT[k]
        siding(b.d, wall, col)
    wx0, wy0, wx1, wy1 = wall
    dx = wx0 + 3 if door_at == "left" else wx1 - 10
    b.d.rectangle([dx, wy1 - 6, dx + 7, wy1], TIMBER, OUTLINE)
    b.d.line([dx + 3, wy1 - 6, dx + 3, wy1], shade(TIMBER, 0.7))
    windows(b.d, (wx0 + 12, wy0, wx1, wy1) if door_at == "left" else (wx0, wy0, wx1 - 11, wy1), 1, every=5)
    b.d.rectangle(wall, outline=OUTLINE)
    roof_col = SHINGLE[k % 3] if roof_kind == "shingle" else IRON_ROOF
    if roof_kind == "iron_ns":
        gable_ns(b.d, roof, wall, roof_col, col, look)
        b.casters[-1] = (1, x0, y0, x1 + 1, y1 + 1, STOREY + 3 + (x1 - x0) // 3)
    else:
        gable_ew(b.d, roof, roof_col, look)
        roof_feature(b.d, roof, ("stack", (x1 - x0) * (3 if door_at == "left" else 1) // 4, 1), look)
    sx = wx1 - 10 if door_at == "left" else wx0 + 1
    yard(b, look, stuff, sx, sx + 9, 30)
    return b


def mill(look, v):
    """Two or three storeys of brick with a tall chimney at one end."""
    storeys, col, chimney_at, roof_kind, width = [
        (2, BRICK, "east", "iron", (2, 25)),
        (3, c("#9a6a4a"), "west", "iron", (6, 29)),
        (2, BRICKS[2], "west", "flat", (6, 29)),
        (3, BRICKS[3], "east", "shingle", (2, 25)),
    ][v]
    x0, x1 = width
    height = storeys * STOREY + 2
    b = Building(height=max(24, height + 4) + (4 if storeys == 3 else 0))
    roof, wall = b.box(x0, 7, x1, 27, height)
    brick(b.d, wall, col)
    windows(b.d, wall, storeys, every=5, sill=shade(col, 1.25))
    b.d.rectangle(wall, outline=OUTLINE)
    if roof_kind == "flat":
        flat_roof(b.img, roof, look, random.Random(7400 + v), [("vent", 8, 6), ("vent", 14, 6), ("hatch", 12, 12)])
    else:
        gable_ew(b.d, roof, IRON_ROOF if roof_kind == "iron" else SHINGLE[2], look)
    chimney(b, 28 if chimney_at == "east" else 3, 12, b.lift - 2, look=look)
    # The loading apron along the front, and what's waiting on it.
    ax, ay = b.ground(1, 29)
    b.d.rectangle([ax, ay, ax + 29, ay + 2], SNOW_GROUND if look == "snow" else c("#a8a49a"))
    kinds = ["crates", "barrels", "lumber"]
    yard(b, look, kinds[v % len(kinds)], 3 + (v % 2) * 14, 14 + (v % 2) * 14, 31)
    return b


def warehouse(look, v):
    """A big shed of brick or boards: loading doors along the front under an iron or tarred roof."""
    walls, doors, roof_kind, canopy, roof_col = [
        (("brick", 1), 3, "iron_ns", False, c("#8a4a3a")),
        (("brick", 0), 2, "iron_ew", True, IRON_ROOF),
        (("timber", 0), 3, "iron_ew", False, c("#4f6b52")),
        (("brick", 3), 4, "flat", True, IRON_ROOF),
        (("brick", 4), 2, "iron_ns", True, IRON_ROOF),
    ][v]
    b = Building(height=2 * STOREY + 4)
    roof, wall = b.box(1, 4, 30, 27, 2 * STOREY + 2)
    material, k = walls
    if material == "brick":
        brick(b.d, wall, BRICKS[k])
    else:
        siding(b.d, wall, PAINT[k])
    x0, y0, x1, y1 = wall
    step = (x1 - x0 - 4) // doors
    for n in range(doors):
        xx = x0 + 3 + n * step
        b.d.rectangle([xx, y1 - 7, xx + step - 4, y1], TIMBER, OUTLINE)
        b.d.rectangle([xx, y0 + 2, xx + step - 4, y0 + 4], WINDOW)
    if canopy:
        b.d.rectangle([x0 + 1, y1 - 9, x1 - 1, y1 - 8], SNOW_ROOF[1] if look == "snow" else shade(IRON_ROOF, 0.8), OUTLINE)
    b.d.rectangle(wall, outline=OUTLINE)
    rx0, ry0, rx1, ry1 = roof
    if roof_kind == "flat":
        flat_roof(b.img, roof, look, random.Random(7500 + v), [("vent", 8, 10), ("vent", 20, 10), ("hatch", 13, 5)])
    else:
        if look == "snow":
            b.d.rectangle(roof, SNOW_ROOF[0])
            b.d.line([rx0 + 1, ry0 + 2, rx1 - 1, ry0 + 2], SNOW_ROOF[2])
        else:
            b.d.rectangle(roof, roof_col)
            if roof_kind == "iron_ns":
                for xx in range(rx0 + 2, rx1, 3):
                    b.d.line([xx, ry0 + 1, xx, ry1 - 1], shade(roof_col, 0.88))
            else:
                mid = (ry0 + ry1) // 2
                b.d.rectangle([rx0, ry0, rx1, mid], shade(roof_col, 1.12))
                for yy in range(ry0 + 2, ry1, 2):
                    b.d.line([rx0 + 1, yy, rx1 - 1, yy], shade(roof_col, 0.9 if yy > mid else 1.05))
                b.d.line([rx0 + 1, mid, rx1 - 1, mid], shade(roof_col, 1.3))
        for vx in (8, 20):
            roof_feature(b.d, roof, ("vent", vx, 10), look)
        b.d.rectangle(roof, outline=OUTLINE)
    # The loading apron along the front, and what's waiting on it.
    ax, ay = b.ground(1, 29)
    b.d.rectangle([ax, ay, ax + 29, ay + 2], SNOW_GROUND if look == "snow" else c("#a8a49a"))
    kinds = ["crates", "barrels", "crates", "lumber"]
    yard(b, look, kinds[v % len(kinds)], 3 + (v % 2) * 14, 14 + (v % 2) * 14, 31)
    return b


def factory(look, v):
    """Three storeys of works under a sawtooth or a row of gables, with one or two chimneys."""
    col, roof_kind, chimneys, tank = [
        (BRICK, "saw", (26,), False),
        (BRICKS[1], "gables", (5, 26), False),
        (BRICKS[3], "saw", (5,), True),
        (BRICKS[2], "gables", (16,), True),
    ][v]
    b = Building(height=30)
    roof, wall = b.box(1, 3, 30, 28, 3 * STOREY)
    brick(b.d, wall, col)
    windows(b.d, wall, 3, glass=c("#6c7f8a"), every=5, width=3)
    b.d.rectangle(wall, outline=OUTLINE)
    x0, y0, x1, y1 = roof
    if roof_kind == "saw":
        # Glazed faces to the north, slopes to the south.
        for xx in range(x0, x1 - 4, 7):
            if look == "snow":
                b.d.rectangle([xx, y0, xx + 3, y1], SNOW_ROOF[0])
                b.d.rectangle([xx + 4, y0, min(xx + 6, x1), y1], SNOW_ROOF[2])
            else:
                b.d.rectangle([xx, y0, xx + 3, y1], SAW[0])
                b.d.rectangle([xx + 4, y0, min(xx + 6, x1), y1], c("#b8c4ca"))
    else:
        # Three gables side by side, ridges running north to south.
        bay_w = (x1 - x0) // 3
        for n in range(3):
            bx0 = x0 + n * bay_w
            bx1 = x1 if n == 2 else bx0 + bay_w
            mid = (bx0 + bx1) // 2
            if look == "snow":
                b.d.rectangle([bx0, y0, mid, y1], SNOW_ROOF[0])
                b.d.rectangle([mid + 1, y0, bx1, y1], SNOW_ROOF[1])
            else:
                slate = c("#5f6570")
                b.d.rectangle([bx0, y0, mid, y1], shade(slate, 1.2))
                b.d.rectangle([mid + 1, y0, bx1, y1], shade(slate, 0.8))
                for yy in range(y0 + 2, y1, 3):
                    b.d.line([bx0 + 1, yy, bx1 - 1, yy], shade(slate, 0.7))
                b.d.line([mid, y0 + 1, mid, y1 - 1], shade(slate, 1.5))
            # A gutter between each pair of gables.
            b.d.line([bx1, y0, bx1, y1], OUTLINE)
            b.d.line([bx1 - 1, y0, bx1 - 1, y1], OUTLINE)
    if tank:
        roof_feature(b.d, roof, ("tank", 20 if chimneys[0] < 16 else 4, 10), look)
    b.d.rectangle(roof, outline=OUTLINE)
    for cx in chimneys:
        chimney(b, cx, 8, 30, look=look)
    # The loading apron along the front, and what's waiting on it.
    ax, ay = b.ground(1, 29)
    b.d.rectangle([ax, ay, ax + 29, ay + 2], SNOW_GROUND if look == "snow" else c("#a8a49a"))
    kinds = ["barrels", "crates", "crates", "barrels"]
    yard(b, look, kinds[v % len(kinds)], 3 + (v % 2) * 14, 14 + (v % 2) * 14, 31)
    return b


def coal_plant(look, v):
    """A small coal power station on 2 by 2 tiles: the turbine hall, a boiler
    house with two chimneys, and a fenced yard in front with the coal."""
    b = Building(2, 2, height=46)
    d = b.d
    rng = random.Random(7400)
    # The yard: packed cinders, fenced along its edges.
    gx0, gy0 = b.ground(2, 44)
    gx1, gy1 = b.ground(61, 62)
    noise_fill(b.img, (gx0, gy0, gx1 + 1, gy1 + 1), [c("#6e665c"), c("#645c53"), c("#78706a")] if look != "snow"
               else [c("#dfe5ea"), c("#cfd7de"), c("#eef2f5")], rng)
    for xx in range(gx0, gx1 + 1, 4):
        d.line([xx, gy1 - 2, xx, gy1], c("#5a5048"))
    d.line([gx0, gy1 - 2, gx1, gy1 - 2], c("#8a8070"))
    d.line([gx0, gy0, gx0, gy1], c("#8a8070"))
    d.line([gx1, gy0, gx1, gy1], c("#8a8070"))
    # The coal heap: layers stacked smaller and higher, lumps scattered over it.
    heap = [c("#1e1e22"), c("#2a2a2f"), c("#35353b"), c("#42424a")]
    base_y = gy1 - 4
    for k, col in enumerate(heap):
        w = 13 - k * 3
        cy = base_y - 2 - k * 2
        d.ellipse([gx0 + 17 - w, cy - 3, gx0 + 17 + w, cy + 3], col)
    for _ in range(40):
        a, r = rng.random() * math.tau, rng.random()
        x = gx0 + 17 + int(math.cos(a) * r * 11)
        y = base_y - 3 + int(math.sin(a) * r * 3) - int((1 - r) * 5)
        d.point((x, y), heap[3] if rng.random() < 0.5 else heap[0])
    if look == "snow":
        d.ellipse([gx0 + 12, base_y - 11, gx0 + 22, base_y - 7], SNOW_ROOF[0])
        d.ellipse([gx0 + 8, base_y - 8, gx0 + 26, base_y - 5], SNOW_ROOF[1])
    b.casters.append((1, 6, 48, 32, 58, 5))
    # The turbine hall.
    roof, wall = b.box(3, 8, 40, 42, 3 * STOREY + 2)
    brick(d, wall, c("#7a4636"))
    windows(d, wall, 3, glass=c("#6c7f8a"), every=5, width=3, height=4)
    d.rectangle([wall[0] + 14, wall[3] - 7, wall[0] + 22, wall[3]], TIMBER)
    d.rectangle(wall, outline=OUTLINE)
    x0, y0, x1, y1 = roof
    if look == "snow":
        d.rectangle(roof, SNOW_ROOF[0])
        d.line([x0 + 1, y0 + 2, x1 - 1, y0 + 2], SNOW_ROOF[2])
    else:
        d.rectangle(roof, IRON_ROOF)
        for xx in range(x0 + 3, x1, 4):
            d.line([xx, y0 + 1, xx, y1 - 1], shade(IRON_ROOF, 0.86))
    d.rectangle(roof, outline=OUTLINE)
    # The boiler house, taller and narrower, with the chimneys behind it.
    chimney(b, 54, 6, 46, c("#8a4f3c"), look)
    chimney(b, 46, 6, 40, c("#8a4f3c"), look)
    roof2, wall2 = b.box(42, 14, 60, 42, 4 * STOREY)
    brick(d, wall2, c("#6e3e30"))
    windows(d, wall2, 4, glass=c("#6c7f8a"), every=5, width=2, height=3)
    d.rectangle(wall2, outline=OUTLINE)
    flat_roof(b.img, roof2, look, rng, [("vent", 4, 6), ("vent", 11, 6)])
    return b


def police_station(look, v):
    """Two storeys of dark brick with a stone band, a door in the middle and a blue lamp beside it."""
    b = Building(2, 1, height=2 * STOREY + 4)
    roof, wall = b.box(3, 5, 60, 26, 2 * STOREY + 4)
    brick(b.d, wall, [c("#7a3f36"), c("#a8885a")][v % 2])
    x0, y0, x1, y1 = wall
    b.d.rectangle([x0, y0 + STOREY + 1, x1, y0 + STOREY + 2], STONE)
    windows(b.d, wall, 2, sill=TRIM, every=5, skip_door=True)
    cx = (x0 + x1) // 2
    b.d.rectangle([cx - 2, y1 - 5, cx + 2, y1], c("#2a2a30"))
    b.d.rectangle([cx - 2, y1 - 6, cx + 2, y1 - 6], STONE)
    b.d.rectangle([cx + 4, y1 - 6, cx + 5, y1 - 4], c("#3f6fd8"))
    b.d.point((cx + 4, y1 - 6), c("#a8c4ff"))
    b.d.rectangle(wall, outline=OUTLINE)
    flat_roof(b.img, roof, look, random.Random(7500 + v), [("stack", 6, 3), ("stack", 48, 3), ("hatch", 26, 8)], parapet=STONE)
    if v % 2 == 1:
        roof_sign(b.d, roof, c("#22407a"), 6)
    return b


def fire_station(look, v):
    """A brick engine hall with two big red doors, a hose tower with a bell on top, and an apron out front."""
    b = Building(2, 2, height=5 * STOREY + 6)
    d = b.d
    # The apron in front of the doors.
    gx0, gy0 = b.ground(6, 50)
    gx1, gy1 = b.ground(46, 61)
    d.rectangle([gx0, gy0, gx1, gy1], c("#b8b2a6") if look != "snow" else c("#dfe5ea"))
    roof, wall = b.box(4, 14, 46, 48, 2 * STOREY + 4)
    brick(d, wall, [c("#9a3e30"), c("#b8784a")][v % 2])
    x0, y0, x1, y1 = wall
    windows(d, (x0, y0, x1, y0 + STOREY), 1, sill=TRIM, every=5)
    for dx in (6, 24):
        d.rectangle([x0 + dx, y1 - 9, x0 + dx + 11, y1], c("#c0392b"))
        d.line([x0 + dx + 5, y1 - 9, x0 + dx + 5, y1], c("#8e2a20"))
        d.rectangle([x0 + dx, y1 - 10, x0 + dx + 11, y1 - 10], STONE)
    d.rectangle(wall, outline=OUTLINE)
    flat_roof(b.img, roof, look, random.Random(7600 + v), [("stack", 4, 3), ("vent", 20, 12)], parapet=STONE)
    # The hose tower, standing taller at the east end.
    troof, twall = b.box(48, 22, 58, 34, 5 * STOREY + 2)
    brick(d, twall, c("#8a3a2e"))
    tx0, ty0, tx1, ty1 = twall
    for k in range(4):
        d.rectangle([tx0 + 4, ty0 + 3 + k * 7, tx0 + 6, ty0 + 5 + k * 7], WINDOW)
    d.rectangle(twall, outline=OUTLINE)
    rx0, ry0, rx1, ry1 = troof
    d.rectangle(troof, SNOW_ROOF[0] if look == "snow" else c("#5a5a60"))
    d.rectangle([rx0 + 3, ry0 + 3, rx1 - 3, ry1 - 3], c("#d9b44a"))
    d.point(((rx0 + rx1) // 2, (ry0 + ry1) // 2), c("#8a6a1a"))
    d.rectangle(troof, outline=OUTLINE)
    return b


PATH = c("#d9ccaa")
FLOWERS = [c("#e0584a"), c("#f2c94c"), c("#f2f2ea"), c("#b86ad0")]


def park(look, v):
    """A tile of park: lawn, paths and one of trees, a fountain, a bandstand or flower beds."""
    b = Building(height=LIFT)
    img, d = b.img, b.d
    rng = random.Random(7700 + v)
    top = b.lift
    # Watered, so green even in a dry summer.
    lawn = GRASS[look] if look not in ("summer", "dry") else [c("#6aae4a"), c("#5f9f42"), c("#78bc56")]
    noise_fill(img, (0, top, T, top + T), lawn, rng)
    path = PATH if look != "snow" else c("#e6ecf0")
    if v == 0:
        d.line([0, top + 20, 31, top + 12], path, 3)
    else:
        d.line([15, top, 15, top + 31], path, 3)
        d.line([0, top + 16, 31, top + 16], path, 3)
    if v == 1:
        d.ellipse([9, top + 10, 22, top + 23], c("#bdb6a4"))
        d.ellipse([11, top + 12, 20, top + 21], c("#6fa0cf") if look != "snow" else c("#dfe8ef"))
        d.point((15, top + 16), c("#e8f4ff"))
    elif v == 2:
        # A bandstand: an eight sided roof on posts.
        d.ellipse([8, top + 8, 23, top + 23], c("#efe8d8"))
        d.polygon([(10, top + 6), (21, top + 6), (24, top + 10), (21, top + 14), (10, top + 14), (7, top + 10)],
                  SNOW_ROOF[0] if look == "snow" else c("#3f6b48"), OUTLINE)
        for x in (9, 15, 22):
            d.line([x, top + 14, x, top + 20], TRIM)
        b.casters.append((1, 7, 6, 25, 15, 8))
    elif v == 3:
        for bx, by in ((3, 3), (19, 3), (3, 19), (19, 19)):
            d.rectangle([bx, top + by, bx + 9, top + by + 8], c("#6b4a30"))
            if look in ("spring", "summer", "dry"):
                for _ in range(10):
                    d.point((bx + 1 + rng.randrange(8), top + by + 1 + rng.randrange(7)), rng.choice(FLOWERS))
            elif look == "snow":
                d.rectangle([bx, top + by, bx + 9, top + by + 8], SNOW_ROOF[1])
    # A bench beside the path.
    d.rectangle([4, top + 26, 9, top + 27], c("#7a5a3a"))
    # Trees along the edges.
    spots = [(23, 26, 7)] if v == 2 else [(7, 12, 6), (23, 26, 7)] if v != 3 else [(25, 13, 6)]
    for fx, fy, r in spots:
        cast = deciduous(img, look, v, fx, fy + top, r, rng)
        b.casters.append((0, cast[0], cast[1] - top, cast[2], cast[3], 0))
    return b


# ---- railway -------------------------------------------------------------------
# Track is ballast, sleepers and two rails, joining its neighbours like a road
# (north 1, east 2, south 4, west 8). Where it crosses a road, planks between
# the rails and a crossbuck at two corners. Over water it runs on a timber
# trestle. Stations and freight yards come in two looks for each side the track
# can be on: north or south for those lying east to west, west or east for the
# others.

BALLAST = {look: [c("#8f877a"), c("#80786c"), c("#9c9588")] for look in LOOKS} | {"snow": [c("#e3e8ec"), c("#d4dbe1"), c("#eef2f5")]}
SLEEPER = {look: c("#5e4a36") for look in LOOKS} | {"snow": c("#b9b2a6")}
RAIL_STEEL = c("#a3a9ae")
RAIL_SHADE = c("#5f6468")
TRACK_LO, TRACK_HI = 8, 23
RAIL_AT = (12, 19)
CROSSING_PLANK = c("#a88c64")
TRESTLE = c("#6a5238")
TRESTLE_BENT = c("#4e3c29")
PLATFORM = c("#bdb6a6")
PLATFORM_EDGE = c("#e8e2d4")
COAL = [c("#1e1e22"), c("#2a2a2f"), c("#35353b")]


# Track is drawn from pieces: straights through the middle of the tile and
# curves round one of its corners. A bend is one curve; a junction is the
# straight line with a curve off it each way; a crossing is two straights; a
# dead end is half a straight with a buffer stop. All the pieces' ballast goes
# down first, then their sleepers, then their rails, so where they meet the
# rails lie over everything.

# Curves: the tile corner each goes round, and the two edges it joins, as the
# way along each edge away from that corner.
BENDS = {
    3: ((T, 0), (-1, 0), (0, 1)),   # north and east
    6: ((T, T), (0, -1), (-1, 0)),  # east and south
    12: ((0, T), (1, 0), (0, -1)),  # south and west
    9: ((0, 0), (0, 1), (1, 0)),    # west and north
}
BUFFER = c("#8a2f24")


class Straight:
    """Track through the middle of the tile, north to south or east to west, between [lo] and [hi] along it."""

    def __init__(self, vertical, lo=0, hi=T - 1):
        self.vertical, self.lo, self.hi = vertical, lo, hi

    def _along_across(self, x, y):
        return (y, x) if self.vertical else (x, y)

    def ballast(self, x, y):
        along, across = self._along_across(x, y)
        return self.lo <= along <= self.hi and TRACK_LO <= across <= TRACK_HI

    def sleeper(self, x, y):
        along, across = self._along_across(x, y)
        return self.lo <= along <= self.hi and TRACK_LO + 1 <= across <= TRACK_HI - 1 and along % 4 in (1, 2)

    def rail(self, x, y):
        """RAIL_STEEL, RAIL_SHADE or None. The lit side is west or north, as the sun is."""
        along, across = self._along_across(x, y)
        if not (self.lo <= along <= self.hi):
            return None
        for a in RAIL_AT:
            if across == a:
                return RAIL_STEEL
            if across == a + 1:
                return RAIL_SHADE
        return None


class Curve:
    """Track curving round a corner of the tile, meeting the straight track at both edges it joins."""

    # Sleepers: as many as fit round the middle of the curve, the first and last
    # as far in from the edges as the straight track's are.
    SLEEPERS = 7

    def __init__(self, mask):
        (self.cx, self.cy), self.a, self.b = BENDS[mask]
        self.start, self.end = self._rails_at(self.a), self._rails_at(self.b)
        self.rails = self._trace()

    def _rails_at(self, edge):
        # A rail's two pixels, r and r + 1, meet at r + 1: that's its middle.
        if edge[0] != 0:
            return sorted(abs(r + 1 - self.cx) for r in RAIL_AT)
        return sorted(abs(r + 1 - self.cy) for r in RAIL_AT)

    def _point(self, radius, t):
        a = t * math.pi / 2
        return (self.cx + radius * (math.cos(a) * self.a[0] + math.sin(a) * self.b[0]),
                self.cy + radius * (math.cos(a) * self.a[1] + math.sin(a) * self.b[1]))

    def _trace(self):
        """Each rail followed along the curve and plotted as the straight ones are:
        two pixels side by side, the lit one west or north of the other, one
        pair to a row where it runs north to south and to a column where it
        runs east to west, so it steps like a drawn line."""
        out = {}
        for k in range(2):
            # For each row or column, the point on the rail nearest its middle.
            best = {}
            steps = 800
            for n in range(steps + 1):
                t = n / steps
                c = self.start[k] + (self.end[k] - self.start[k]) * t
                x, y = self._point(c, t)
                x1, y1 = self._point(c, max(0.0, t - 0.001))
                x2, y2 = self._point(c, min(1.0, t + 0.001))
                upright = abs(y2 - y1) >= abs(x2 - x1)
                key = ("row", math.floor(y)) if upright else ("col", math.floor(x))
                off = abs(y - (math.floor(y) + 0.5)) if upright else abs(x - (math.floor(x) + 0.5))
                if key not in best or off < best[key][0]:
                    best[key] = (off, x, y, upright)
            for _, x, y, upright in best.values():
                if upright:
                    lit, dark = (round(x) - 1, math.floor(y)), (round(x), math.floor(y))
                else:
                    lit, dark = (math.floor(x), round(y) - 1), (math.floor(x), round(y))
                for (px_, py_), col in ((lit, RAIL_STEEL), (dark, RAIL_SHADE)):
                    if 0 <= px_ < T and 0 <= py_ < T and out.get((px_, py_)) != RAIL_STEEL:
                        out[(px_, py_)] = col
        return out

    def _polar(self, x, y):
        vx, vy = x + 0.5 - self.cx, y + 0.5 - self.cy
        d = math.hypot(vx, vy)
        t = math.atan2(vx * self.b[0] + vy * self.b[1], vx * self.a[0] + vy * self.a[1]) / (math.pi / 2)
        return d, t

    def ballast(self, x, y):
        d, t = self._polar(x, y)
        return -0.01 <= t <= 1.01 and TRACK_LO + 0.5 <= d <= TRACK_HI + 0.5

    def sleeper(self, x, y):
        d, t = self._polar(x, y)
        if not (-0.01 <= t <= 1.01 and TRACK_LO + 1 <= d <= TRACK_HI - 1):
            return False
        length = math.pi / 2 * 16
        arc = t * length
        pitch = (length - 3) / (self.SLEEPERS - 1)
        return any(abs(arc - (1.5 + k * pitch)) < 0.8 for k in range(self.SLEEPERS))

    def rail(self, x, y):
        return self.rails.get((x, y))


def pieces(mask):
    """The pieces of track for a tile joined to the neighbours in [mask]."""
    n, e, s_, w = mask & 1, mask & 2, mask & 4, mask & 8
    joins = bin(mask).count("1")
    if mask in BENDS:
        return [Curve(mask)]
    if joins == 4:
        return [Straight(True), Straight(False)]
    if joins == 3:
        # The straight line, and a curve off it each way to the branch.
        if n and s_:
            return [Straight(True), Curve(3 if e else 9), Curve(6 if e else 12)]
        return [Straight(False), Curve(3 if n else 6), Curve(9 if n else 12)]
    if mask in (5, 10):
        return [Straight(mask == 5)]
    # A dead end, or track on its own: half a straight, or a short one, with buffer stops.
    if n:
        return [Straight(True, 0, TRACK_HI)]
    if s_:
        return [Straight(True, TRACK_LO, T - 1)]
    if e:
        return [Straight(False, TRACK_LO, T - 1)]
    if w:
        return [Straight(False, 0, TRACK_HI)]
    return [Straight(True, TRACK_LO, TRACK_HI)]


def track(look, mask):
    img = Image.new("RGBA", (T, T), (0, 0, 0, 0))
    px = img.load()
    rng = random.Random(6600 + mask)
    parts = pieces(mask)
    ballast = [[any(p.ballast(x, y) for p in parts) for x in range(T)] for y in range(T)]

    def on(x, y):
        if not (0 <= x < T and 0 <= y < T):
            # Past the edge the track goes on only where it's joined.
            if y < 0: return bool(mask & 1) and TRACK_LO <= x <= TRACK_HI
            if x >= T: return bool(mask & 2) and TRACK_LO <= y <= TRACK_HI
            if y >= T: return bool(mask & 4) and TRACK_LO <= x <= TRACK_HI
            return bool(mask & 8) and TRACK_LO <= y <= TRACK_HI
        return ballast[y][x]

    cols = BALLAST[look]
    for y in range(T):
        for x in range(T):
            if not ballast[y][x]:
                continue
            if not (on(x - 1, y) and on(x + 1, y) and on(x, y - 1) and on(x, y + 1)) and rng.random() < 0.4:
                continue  # a ragged edge to the ballast
            r = rng.random()
            px[x, y] = cols[0] if r < 0.6 else cols[1] if r < 0.85 else cols[2]
    for y in range(T):
        for x in range(T):
            if any(p.sleeper(x, y) for p in parts):
                px[x, y] = SLEEPER[look]
    for y in range(T):
        for x in range(T):
            for p in parts:
                col = p.rail(x, y)
                if col is not None:
                    px[x, y] = col
                    break
    # Buffer stops across the rails at dead ends.
    d = ImageDraw.Draw(img)
    ends = {1: "s", 4: "n", 2: "w", 8: "e", 0: None}
    if mask in ends:
        sides = ["n", "s"] if mask == 0 else [ends[mask]]
        for side in sides:
            if side == "s": d.rectangle([TRACK_LO + 2, TRACK_HI - 2, TRACK_HI - 2, TRACK_HI - 1], BUFFER)
            if side == "n": d.rectangle([TRACK_LO + 2, TRACK_LO + 1, TRACK_HI - 2, TRACK_LO + 2], BUFFER)
            if side == "e": d.rectangle([TRACK_HI - 2, TRACK_LO + 2, TRACK_HI - 1, TRACK_HI - 2], BUFFER)
            if side == "w": d.rectangle([TRACK_LO + 1, TRACK_LO + 2, TRACK_LO + 2, TRACK_HI - 2], BUFFER)
    return img


# Tramways: rails set in the street along the track's pieces, with the poles
# for the overhead wire at the kerb. Drawn over the road tile.

TRAM_RAIL = c("#4a4c50")
TRAM_GROOVE = c("#2c2d30")
WIRE_POLE = c("#3a3c42")


def tramway(look, mask):
    img = Image.new("RGBA", (T, T), (0, 0, 0, 0))
    px = img.load()
    parts = pieces(mask)
    for y in range(T):
        for x in range(T):
            for p in parts:
                col = p.rail(x, y)
                if col is not None:
                    px[x, y] = TRAM_RAIL if col == RAIL_STEEL else TRAM_GROOVE
                    break
    d = ImageDraw.Draw(img)
    # Poles for the wire, at the kerb either side of a straight run, and the wire between them.
    # The span wire between the poles crosses the street; the contact wire runs along the track.
    if mask in (5, 1, 4):
        for x in (1, T - 2):
            d.rectangle([x, 14, x + 1, 15], WIRE_POLE)
        d.line([1, 15, T - 2, 15], (40, 42, 46, 120))
        d.line([15, 0, 15, T - 1], (40, 42, 46, 90))
    if mask in (10, 2, 8):
        for y in (1, T - 2):
            d.rectangle([14, y, 15, y + 1], WIRE_POLE)
        d.line([15, 1, 15, T - 2], (40, 42, 46, 120))
        d.line([0, 15, T - 1, 15], (40, 42, 46, 90))
    return img


def trolley_wire(look, mask):
    """Trolleybus wire over a road: a pair of wires along each way it's joined, and poles at the kerb on straight runs."""
    img = Image.new("RGBA", (T, T), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    wire = (36, 38, 42, 170)
    mid = T // 2
    ends = {1: (mid, 0), 2: (T - 1, mid), 4: (mid, T - 1), 8: (0, mid)}
    joined = [b for b in (1, 2, 4, 8) if mask & b] or [1, 4]
    for b in joined:
        ex, ey = ends[b]
        for off in (-2, 2):
            if b in (1, 4):
                d.line([mid + off, mid, ex + off, ey], wire)
            else:
                d.line([mid, mid + off, ex, ey + off], wire)
    if mask in (5, 1, 4):
        for x in (1, T - 2):
            d.rectangle([x, 14, x + 1, 15], WIRE_POLE)
        d.line([1, 15, T - 2, 15], (40, 42, 46, 110))
    if mask in (10, 2, 8):
        for y in (1, T - 2):
            d.rectangle([14, y, 15, y + 1], WIRE_POLE)
        d.line([15, 1, 15, T - 2], (40, 42, 46, 110))
    return img


def tram_stop(look):
    """A shelter at the kerb and a stop sign, at the tile's north west corner."""
    img = Image.new("RGBA", (T, T), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rectangle([1, 1, 9, 4], SNOW_ROOF[0] if look == "snow" else c("#3f6a4a"), OUTLINE)
    d.line([2, 5, 2, 6], OUTLINE)
    d.line([8, 5, 8, 6], OUTLINE)
    d.rectangle([11, 1, 13, 3], c("#e8e0c8"), OUTLINE)
    d.point((12, 2), c("#c0392b"))
    return img


def bus_stop(look):
    """A bench and a post with the stop's sign, at the tile's north west corner."""
    img = Image.new("RGBA", (T, T), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rectangle([2, 2, 8, 3], c("#7a5a3a"), OUTLINE)
    d.line([11, 1, 11, 6], c("#5a5c60"))
    d.rectangle([10, 0, 13, 2], c("#e0b83a"), OUTLINE)
    return img


def crossing(look, vertical):
    """A level crossing drawn over the road: planks between and beside the rails, and two crossbucks."""
    img = Image.new("RGBA", (T, T), (0, 0, 0, 0))
    px = img.load()
    d = ImageDraw.Draw(img)
    for y in range(T):
        for x in range(TRACK_LO + 1, TRACK_HI):
            px[x, y] = PLANK_GAP if y % 3 == 2 else (SNOW_ROOF[1] if look == "snow" and y % 3 == 0 else CROSSING_PLANK)
    for a in RAIL_AT:
        for y in range(T):
            px[a, y] = RAIL_STEEL
            px[a + 1, y] = RAIL_SHADE
    # Crossbucks on posts at two corners.
    for cx, cy in ((3, 3), (27, 27)):
        d.line([cx - 2, cy - 2, cx + 2, cy + 2], TRIM)
        d.line([cx - 2, cy + 2, cx + 2, cy - 2], TRIM)
        d.point((cx, cy + 3), OUTLINE)
    # Mirrored across the diagonal rather than turned, so each rail keeps its light side where the track has it.
    return img if vertical else img.transpose(Image.Transpose.TRANSPOSE)


def trestle(vertical):
    """A timber trestle carrying the track over water, with its bents and its shadow."""
    img = Image.new("RGBA", (T, T), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rectangle([TRACK_LO - 1, 0, TRACK_HI + 1, T - 1], TRESTLE)
    for y in range(2, T, 8):
        d.rectangle([TRACK_LO - 4, y, TRACK_HI + 4, y + 1], TRESTLE_BENT)
    img = img if vertical else img.rotate(90)
    out = Image.new("RGBA", (T, T), (0, 0, 0, 0))
    out.paste(Image.new("RGBA", (T, T), DECK_SHADOW), (2, 2), img.split()[3])
    out.alpha_composite(img)
    return out


def rail_siding(b, look, x0, y0, x1, y1):
    """A siding inside a yard: ballast, sleepers and rails along the longer side of the box (tile pixels)."""
    d = b.d
    gx0, gy0 = b.ground(x0, y0)
    gx1, gy1 = b.ground(x1, y1)
    cols = BALLAST[look]
    d.rectangle([gx0, gy0, gx1, gy1], cols[1])
    horizontal = gx1 - gx0 >= gy1 - gy0
    if horizontal:
        for xx in range(gx0 + 1, gx1, 4):
            d.line([xx, gy0 + 1, xx, gy1 - 1], SLEEPER[look])
        for ry in (gy0 + 2, gy1 - 2):
            d.line([gx0, ry, gx1, ry], RAIL_STEEL)
    else:
        for yy in range(gy0 + 1, gy1, 4):
            d.line([gx0 + 1, yy, gx1 - 1, yy], SLEEPER[look])
        for rx in (gx0 + 2, gx1 - 2):
            d.line([rx, gy0, rx, gy1], RAIL_STEEL)


def platform(b, look, x0, y0, x1, y1, edge_side):
    """A stone platform on the ground, with a pale edge on the track side."""
    d = b.d
    gx0, gy0 = b.ground(x0, y0)
    gx1, gy1 = b.ground(x1, y1)
    d.rectangle([gx0, gy0, gx1, gy1], SNOW_ROOF[0] if look == "snow" else PLATFORM)
    for xx in range(gx0 + 4, gx1, 6):
        if gy1 - gy0 < gx1 - gx0: d.line([xx, gy0, xx, gy1], shade(PLATFORM, 0.92))
    if edge_side == "n": d.line([gx0, gy0, gx1, gy0], PLATFORM_EDGE)
    if edge_side == "s": d.line([gx0, gy1, gx1, gy1], PLATFORM_EDGE)
    if edge_side == "w": d.line([gx0, gy0, gx0, gy1], PLATFORM_EDGE)
    if edge_side == "e": d.line([gx1, gy0, gx1, gy1], PLATFORM_EDGE)


def platform_canopy(b, look, x0, y0, x1, y1, col):
    """A platform canopy: a flat roof on posts, a storey up."""
    roof, wall = b.box(x0, y0, x1, y1, STOREY)
    d = b.d
    d.rectangle(roof, SNOW_ROOF[0] if look == "snow" else col)
    if look != "snow":
        long_way = roof[2] - roof[0] >= roof[3] - roof[1]
        if long_way:
            d.line([roof[0] + 1, (roof[1] + roof[3]) // 2, roof[2] - 1, (roof[1] + roof[3]) // 2], shade(col, 1.25))
        else:
            d.line([(roof[0] + roof[2]) // 2, roof[1] + 1, (roof[0] + roof[2]) // 2, roof[3] - 1], shade(col, 1.25))
    d.rectangle(roof, outline=OUTLINE)
    # Posts under the front edge, the wall itself left open.
    for xx in range(wall[0] + 2, wall[2], 8):
        d.line([xx, wall[1], xx, wall[3]], c("#4a4540"))


def station_ew(look, v):
    """A station lying east to west on 3 tiles, the track to the north (0, 1) or south (2, 3)."""
    north = v < 2
    style = v % 2
    b = Building(3, 1, height=2 * STOREY + 4)
    wall_col, roof_col, material = [(BRICK, SHINGLE[2], "brick"), (PAINT[3], SHINGLE[0], "timber")][style]
    if north:
        platform(b, look, 2, 0, 93, 7, "n")
        platform_canopy(b, look, 18, 1, 77, 6, c("#5b5f6b") if style == 0 else c("#7a3f36"))
        roof, wall = b.box(22, 11, 73, 27, STOREY + 4)
    else:
        platform(b, look, 2, 24, 93, 31, "s")
        roof, wall = b.box(22, 3, 73, 20, STOREY + 4)
    (brick if material == "brick" else siding)(b.d, wall, wall_col)
    windows(b.d, wall, 1, sill=TRIM, every=5, skip_door=True)
    door(b.d, wall)
    b.d.rectangle(wall, outline=OUTLINE)
    gable_ew(b.d, roof, roof_col, look)
    # A little clock turret on the ridge.
    cx = (roof[0] + roof[2]) // 2
    mid = (roof[1] + roof[3]) // 2
    b.d.rectangle([cx - 2, mid - 5, cx + 2, mid], shade(wall_col, 1.1), OUTLINE)
    b.d.point((cx, mid - 3), OUTLINE)
    if not north:
        platform_canopy(b, look, 18, 25, 77, 30, c("#5b5f6b") if style == 0 else c("#7a3f36"))
    return b


def station_ns(look, v):
    """A station lying north to south on 3 tiles, the track to the west (0, 1) or east (2, 3)."""
    west = v < 2
    style = v % 2
    b = Building(1, 3, height=2 * STOREY + 4)
    wall_col, roof_col, material = [(BRICK, SHINGLE[2], "brick"), (PAINT[3], SHINGLE[0], "timber")][style]
    cover = c("#5b5f6b") if style == 0 else c("#7a3f36")
    if west:
        platform(b, look, 0, 2, 7, 93, "w")
        platform_canopy(b, look, 1, 18, 6, 77, cover)
        roof, wall = b.box(10, 20, 28, 75, STOREY + 4)
    else:
        platform(b, look, 24, 2, 31, 93, "e")
        platform_canopy(b, look, 25, 18, 30, 77, cover)
        roof, wall = b.box(3, 20, 21, 75, STOREY + 4)
    (brick if material == "brick" else siding)(b.d, wall, wall_col)
    windows(b.d, wall, 1, sill=TRIM, every=5, skip_door=True)
    door(b.d, wall)
    b.d.rectangle(wall, outline=OUTLINE)
    gable_ns(b.d, roof, wall, roof_col, wall_col, look)
    b.casters[-1] = (1, roof[0], 20, roof[2] + 1, 76, STOREY + 4 + (roof[2] - roof[0]) // 3)
    return b


def coal_heap(b, look, cx, cy, w):
    d = b.d
    gx, gy = b.ground(cx, cy)
    for k, col in enumerate(COAL):
        ww = w - k * 3
        d.ellipse([gx - ww, gy - 3 - k * 2, gx + ww, gy + 3 - k * 2], col)
    if look == "snow":
        d.ellipse([gx - w // 2, gy - 8, gx + w // 2, gy - 5], SNOW_ROOF[0])


def goods_shed(b, look, x0, y0, x1, y1, style):
    roof, wall = b.box(x0, y0, x1, y1, STOREY + 4)
    if style == 0:
        brick(b.d, wall, BRICKS[1])
    else:
        siding(b.d, wall, PAINT[0])
    wx0, wy0, wx1, wy1 = wall
    for xx in range(wx0 + 3, wx1 - 6, 10):
        b.d.rectangle([xx, wy1 - 6, xx + 6, wy1], TIMBER, OUTLINE)
    b.d.rectangle(wall, outline=OUTLINE)
    if (x1 - x0) >= (y1 - y0):
        gable_ew(b.d, roof, IRON_ROOF if style == 0 else c("#8a4a3a"), look)
    else:
        gable_ns(b.d, roof, wall, IRON_ROOF if style == 0 else c("#8a4a3a"), BRICKS[1] if style == 0 else PAINT[0], look)
        b.casters[-1] = (1, x0, y0, x1 + 1, y1 + 1, STOREY + 4 + (x1 - x0) // 3)


def yard_ground(b, look, x0, y0, x1, y1):
    gx0, gy0 = b.ground(x0, y0)
    gx1, gy1 = b.ground(x1, y1)
    noise_fill(b.img, (gx0, gy0, gx1 + 1, gy1 + 1), [c("#7a7266"), c("#70685c"), c("#847c70")] if look != "snow"
               else [c("#dfe5ea"), c("#cfd7de"), c("#eef2f5")], random.Random(7700))


def yard_ew(look, v):
    """A freight yard on 3 by 2 tiles: a siding, a goods shed, coal and crates. Track to the north (0, 1) or south (2, 3)."""
    north = v < 2
    style = v % 2
    b = Building(3, 2, height=STOREY + 8)
    yard_ground(b, look, 1, 1, 94, 62)
    if north:
        rail_siding(b, look, 0, 2, 95, 9)
        shed = (8, 16, 47, 40) if style == 0 else (48, 16, 87, 40)
        heap_x = 70 if style == 0 else 22
        crates_y = 58
    else:
        rail_siding(b, look, 0, 54, 95, 61)
        shed = (8, 6, 47, 30) if style == 0 else (48, 6, 87, 30)
        heap_x = 70 if style == 0 else 22
        crates_y = 44
    coal_heap(b, look, heap_x, 28 if north else 20, 12)
    yard(b, look, "crates", heap_x - 12, heap_x + 12, crates_y)
    goods_shed(b, look, *shed, style)
    return b


def yard_ns(look, v):
    """A freight yard on 2 by 3 tiles, the track to the west (0, 1) or east (2, 3)."""
    west = v < 2
    style = v % 2
    b = Building(2, 3, height=STOREY + 8)
    yard_ground(b, look, 1, 1, 62, 94)
    if west:
        rail_siding(b, look, 2, 0, 9, 95)
        shed = (16, 8, 40, 47) if style == 0 else (16, 48, 40, 87)
        heap = (50, 72 if style == 0 else 24)
    else:
        rail_siding(b, look, 54, 0, 61, 95)
        shed = (22, 8, 46, 47) if style == 0 else (22, 48, 46, 87)
        heap = (12, 72 if style == 0 else 24)
    coal_heap(b, look, heap[0], heap[1], 9)
    yard(b, look, "barrels", heap[0] - 8, heap[0] + 8, heap[1] + 14)
    goods_shed(b, look, *shed, style)
    return b


# ---- ports -----------------------------------------------------------------------
# Ports lie along the water on one long side: a quay with bollards, cranes at the
# edge and sheds or stacks behind. A wharf has a derrick and a shed, docks have
# level-luffing cranes, a siding and a warehouse, and a container port has tall
# gantry cranes over rows of stacked containers.

QUAY = [c("#8d8a82"), c("#85827a"), c("#95928a")]
QUAY_TIMBER = [c("#8a6a45"), c("#7d5f3d"), c("#94744d")]
QUAY_EDGE = c("#55524c")
BOLLARD = c("#2a2a2a")
CRANE_PAINT = c("#c9a23a")
GANTRY_PAINT = [c("#b8452e"), c("#2f5f9a")]
CONTAINERS = [c("#b5452f"), c("#2f5f9a"), c("#3f8a4a"), c("#d08a2a"), c("#8a8f96"), c("#7a3f7a"), c("#c9c2b0")]


class PortLayout:
    """Places things on a port by how far along the quay and how far back from the water they are."""

    def __init__(self, b, side):
        self.b, self.side = b, side
        self.W, self.H = b.w * T, b.h * T

    @property
    def length(self):
        return self.W if self.side in "ns" else self.H

    @property
    def depth(self):
        return self.H if self.side in "ns" else self.W

    def rect(self, a0, d0, a1, d1):
        """A box from along-and-back to tile pixels."""
        if self.side == "n": return a0, d0, a1, d1
        if self.side == "s": return a0, self.H - 1 - d1, a1, self.H - 1 - d0
        if self.side == "w": return d0, a0, d1, a1
        return self.W - 1 - d1, a0, self.W - 1 - d0, a1


def quay(lay, look, back, timber=False):
    """The quay along the water, [back] pixels deep, with a dark edge and bollards."""
    b = lay.b
    x0, y0, x1, y1 = lay.rect(0, 0, lay.length - 1, back)
    gx0, gy0 = b.ground(x0, y0)
    gx1, gy1 = b.ground(x1, y1)
    cols = (QUAY_TIMBER if timber else QUAY) if look != "snow" else [c("#e3e8ec"), c("#d6dde3"), c("#eef2f5")]
    noise_fill(b.img, (gx0, gy0, gx1 + 1, gy1 + 1), cols, random.Random(7710))
    if timber and look != "snow":
        horizontal = lay.side in "ns"
        if horizontal:
            for xx in range(gx0 + 2, gx1, 4): b.d.line([xx, gy0, xx, gy1], shade(QUAY_TIMBER[0], 0.8))
        else:
            for yy in range(gy0 + 2, gy1, 4): b.d.line([gx0, yy, gx1, yy], shade(QUAY_TIMBER[0], 0.8))
    ex0, ey0, ex1, ey1 = lay.rect(0, 0, lay.length - 1, 1)
    b.d.rectangle([*b.ground(ex0, ey0), *b.ground(ex1, ey1)], QUAY_EDGE)
    for a in range(5, lay.length - 3, 10):
        bx, by, _, _ = lay.rect(a, 3, a + 1, 4)
        b.d.rectangle([*b.ground(bx, by), *b.ground(bx + 1, by + 1)], BOLLARD)


def derrick(lay, look, a, back):
    """A small crane on the quay: a post and a jib out over the water."""
    b = lay.b
    x0, y0, x1, y1 = lay.rect(a, back, a + 4, back + 4)
    roof, wall = b.box(x0, y0, x1, y1, 6)
    b.d.rectangle(wall, shade(CRANE_PAINT, 0.7), OUTLINE)
    b.d.rectangle(roof, SNOW_ROOF[0] if look == "snow" else CRANE_PAINT, OUTLINE)
    # The jib, from the top of the post out over the edge of the quay.
    tx, ty = (roof[0] + roof[2]) // 2, (roof[1] + roof[3]) // 2
    ex, ey, _, _ = lay.rect(a + 2, 0, a + 2, 0)
    gx, gy = b.ground(ex, ey)
    b.d.line([tx, ty - 3, gx, gy - 10], c("#3a3a3a"))
    b.d.line([gx, gy - 10, gx, gy - 4], c("#3a3a3a"))


def luffing_crane(lay, look, a, back):
    """A dockside crane: a portal on legs, a cab and a long jib leaning out over the water."""
    b = lay.b
    x0, y0, x1, y1 = lay.rect(a, back, a + 8, back + 8)
    roof, wall = b.box(x0, y0, x1, y1, 16)
    d = b.d
    # Legs, open between.
    d.rectangle([wall[0], wall[1], wall[0] + 1, wall[3]], shade(CRANE_PAINT, 0.6))
    d.rectangle([wall[2] - 1, wall[1], wall[2], wall[3]], shade(CRANE_PAINT, 0.6))
    d.rectangle([roof[0], roof[1], roof[2], roof[1] + 4], SNOW_ROOF[0] if look == "snow" else CRANE_PAINT, OUTLINE)
    # The cab on top, and the jib.
    cx, cy = (roof[0] + roof[2]) // 2, roof[1]
    d.rectangle([cx - 3, cy - 6, cx + 3, cy], shade(CRANE_PAINT, 0.9), OUTLINE)
    d.point((cx - 1, cy - 4), WINDOW)
    d.point((cx + 1, cy - 4), WINDOW)
    ex, ey, _, _ = lay.rect(a + 4, 0, a + 4, 0)
    gx, gy = b.ground(ex, ey)
    tip = (gx + (gx - cx) // 3, gy - 26)
    d.line([cx, cy - 4, tip[0], tip[1]], c("#3a3a3a"), 2)
    d.line([tip[0], tip[1], tip[0], tip[1] + 12], c("#2a2a2a"))
    b.casters[-1] = (1, x0, y0, x1 + 1, y1 + 1, 22)


def gantry_crane(lay, look, a, colour):
    """A container crane: tall legs astride the quay and a boom reaching out over the water."""
    b = lay.b
    x0, y0, x1, y1 = lay.rect(a, 2, a + 12, 22)
    roof, wall = b.box(x0, y0, x1, y1, 40)
    d = b.d
    paint = SNOW_ROOF[0] if look == "snow" else colour
    # Four legs, a cross beam and the machinery house on top.
    for lx in (roof[0], roof[2] - 1):
        d.rectangle([lx, roof[1], lx + 1, wall[3]], shade(colour, 0.75))
    d.line([roof[0], roof[3], roof[2], roof[3]], shade(colour, 0.6))
    d.rectangle([roof[0], roof[1] + 2, roof[2], roof[1] + 6], paint, OUTLINE)
    mx = (roof[0] + roof[2]) // 2
    d.rectangle([mx - 4, roof[1] - 4, mx + 4, roof[1] + 2], c("#d8d4cc"), OUTLINE)
    # The boom, out past the quay edge over the water side of the sprite.
    if lay.side in "ns":
        top = roof[1] + 3
        d.rectangle([roof[0] + 4, top - 1, roof[2] - 4, top + 1], shade(colour, 0.9))
    else:
        reach = -10 if lay.side == "w" else 10
        d.line([mx, roof[1] + 4, mx + reach, roof[1] + 4], shade(colour, 0.9), 3)
    b.casters[-1] = (1, x0, y0, x1 + 1, y1 + 1, 40)


def container_stacks(lay, look, a0, d0, a1, d1, seed):
    """Blocks of containers stacked one to three high, as things to draw in order."""
    b = lay.b
    rng = random.Random(seed)
    things = []

    def stack(x0, y0, x1, y1, high, col):
        roof, wall = b.box(x0, y0, x1, y1, high)
        b.d.rectangle(wall, shade(col, 0.72), OUTLINE)
        b.d.rectangle(roof, SNOW_ROOF[0] if look == "snow" else col, OUTLINE)

    for a in range(a0, a1 - 11, 13):
        for dd in range(d0, d1 - 4, 6):
            if rng.random() < 0.15:
                continue
            high = rng.choice((2, 4, 4, 6))
            r = lay.rect(a, dd, a + 11, dd + 4)
            things.append((r[3], lambda r=r, high=high, col=rng.choice(CONTAINERS): stack(*r, high, col)))
    return things


def port_warehouse(b, look, x0, y0, x1, y1, style):
    """A tall brick warehouse with rows of loading doors."""
    roof, wall = b.box(x0, y0, x1, y1, STOREY * 3)
    brick(b.d, wall, BRICKS[1 + style])
    windows(b.d, wall, 3)
    b.d.rectangle(wall, outline=OUTLINE)
    flat_roof(b.img, roof, look, random.Random(7720 + style), features=(("stack", 4, 3),) if style == 0 else ())


def port_side(v, vertical):
    near = v < 2
    return ("w" if near else "e") if vertical else ("n" if near else "s")


def ordered(things):
    """Draws things further north first, so nearer ones stand in front."""
    for _, f in sorted(things, key=lambda t: t[0]):
        f()


def wharf(look, v, w, h):
    """A wharf: a timber quay, a derrick, a goods shed and crates. Water north or west (0, 1), south or east (2, 3)."""
    b = Building(w, h, height=STOREY + 14)
    lay = PortLayout(b, port_side(v, h > w))
    style = v % 2
    yard_ground(b, look, 1, 1, w * T - 2, h * T - 2)
    quay(lay, look, 10, timber=True)
    things = []
    for k, a in enumerate((14, lay.length - 24)):
        r = lay.rect(a, 11, a + 4, 15)
        things.append((r[3], lambda a=a: derrick(lay, look, a, 11)))
    shed = lay.rect(8 if style == 0 else 22, 22, lay.length - (22 if style == 0 else 8), lay.depth - 8)
    things.append((shed[3], lambda: goods_shed(b, look, *shed, style)))
    cr = lay.rect(4, lay.depth - 6, lay.length - 4, lay.depth - 3)
    if lay.side in "ns":
        things.append((cr[3] + 1, lambda: yard(b, look, "crates" if style == 0 else "barrels", cr[0] + 2, cr[0] + 20, cr[3])))
    ordered(things)
    return b


def docks(look, v, w, h):
    """Docks: a stone quay, two dockside cranes, a siding, a transit shed and a warehouse."""
    b = Building(w, h, height=STOREY * 3 + 22)
    lay = PortLayout(b, port_side(v, h > w))
    style = v % 2
    yard_ground(b, look, 1, 1, w * T - 2, h * T - 2)
    quay(lay, look, 12)
    sx0, sy0, sx1, sy1 = lay.rect(0, 22, lay.length - 1, 29)
    rail_siding(b, look, sx0, sy0, sx1, sy1)
    things = []
    for a in (16, lay.length - 30):
        r = lay.rect(a, 3, a + 8, 11)
        things.append((r[3], lambda a=a: luffing_crane(lay, look, a, 3)))
    shed = lay.rect(6, 34, lay.length - 7, 58)
    things.append((shed[3], lambda: goods_shed(b, look, *shed, style)))
    ware = lay.rect(10 if style == 0 else lay.length // 2, 64, lay.length // 2 - 4 if style == 0 else lay.length - 10, lay.depth - 6)
    things.append((ware[3], lambda: port_warehouse(b, look, *ware, style)))
    ordered(things)
    return b


def container_port(look, v, w, h):
    """A container port: a long concrete quay, gantry cranes and rows of stacked containers."""
    b = Building(w, h, height=44)
    lay = PortLayout(b, port_side(v, h > w))
    style = v % 2
    gx0, gy0 = b.ground(1, 1)
    gx1, gy1 = b.ground(w * T - 2, h * T - 2)
    noise_fill(b.img, (gx0, gy0, gx1 + 1, gy1 + 1), QUAY if look != "snow" else [c("#e3e8ec"), c("#d6dde3"), c("#eef2f5")], random.Random(7730))
    quay(lay, look, 24)
    things = container_stacks(lay, look, 6, 30, lay.length - 6, lay.depth - 14, 7740 + v)
    for a in range(18, lay.length - 20, 52):
        r = lay.rect(a, 2, a + 12, 22)
        things.append((r[3], lambda a=a: gantry_crane(lay, look, a, GANTRY_PAINT[style])))
    gate = lay.rect(lay.length - 22, lay.depth - 10, lay.length - 6, lay.depth - 3)

    def office():
        roof, wall = b.box(*gate, STOREY)
        siding(b.d, wall, PAINT[2])
        b.d.rectangle(wall, outline=OUTLINE)
        b.d.rectangle(roof, SNOW_ROOF[0] if look == "snow" else c("#9aa0a6"), OUTLINE)
    things.append((gate[3], office))
    ordered(things)
    return b


def freight_terminal(look, v, w, h):
    """A freight terminal: two sidings along the track side, gantry cranes over them, container stacks behind and a
    gate for the trucks. Track to the north or west (0, 1), south or east (2, 3)."""
    b = Building(w, h, height=44)
    lay = PortLayout(b, port_side(v, h > w))
    style = v % 2
    gx0, gy0 = b.ground(0, 0)
    gx1, gy1 = b.ground(w * T - 1, h * T - 1)
    noise_fill(b.img, (gx0, gy0, gx1 + 1, gy1 + 1), QUAY if look != "snow" else [c("#e3e8ec"), c("#d6dde3"), c("#eef2f5")], random.Random(7750))
    for d0 in (2, 12):
        x0, y0, x1, y1 = lay.rect(0, d0, lay.length - 1, d0 + 7)
        rail_siding(b, look, x0, y0, x1, y1)
    things = container_stacks(lay, look, 6, 26, lay.length - 30, lay.depth - 4, 7760 + v)
    for a in range(10, lay.length - 24, 56):
        r = lay.rect(a, 2, a + 12, 22)
        things.append((r[3], lambda a=a: gantry_crane(lay, look, a, GANTRY_PAINT[1 - style])))
    gate = lay.rect(lay.length - 24, lay.depth - 22, lay.length - 6, lay.depth - 6)

    def office():
        roof, wall = b.box(*gate, STOREY)
        siding(b.d, wall, PAINT[2])
        b.d.rectangle(wall, outline=OUTLINE)
        b.d.rectangle(roof, SNOW_ROOF[0] if look == "snow" else c("#9aa0a6"), OUTLINE)
    things.append((gate[3], office))
    ordered(things)
    return b


def terminal_ew(look, v): return freight_terminal(look, v, 5, 2)
def terminal_ns(look, v): return freight_terminal(look, v, 2, 5)


RUNWAY = [c("#5c5a56"), c("#56544f"), c("#625f5a")]
APRON = [c("#9a978f"), c("#928f87"), c("#a29f97")]
GRASS_STRIP = [c("#7aa65a"), c("#72a052"), c("#82ae62")]
TERMINAL_GLASS = c("#6f9fb8")


def runway(b, look, x0, x1, y, wide, grass=False):
    """A runway along row [y] (tile pixels), [wide] across: tarmac with a dashed centre line and the threshold bars at
    each end, or a mown grass strip with markers."""
    gx0, gy0 = b.ground(x0, y - wide // 2)
    gx1, gy1 = b.ground(x1, y + wide // 2)
    snow = look == "snow"
    if grass:
        noise_fill(b.img, (gx0, gy0, gx1 + 1, gy1 + 1), [c("#e8edf1"), c("#dfe5ea"), c("#eef2f5")] if snow else GRASS_STRIP, random.Random(7810))
        for xx in range(gx0 + 2, gx1, 12):
            b.d.rectangle([xx, gy0, xx + 2, gy0 + 1], c("#f2f2f2"))
            b.d.rectangle([xx, gy1 - 1, xx + 2, gy1], c("#f2f2f2"))
        return
    noise_fill(b.img, (gx0, gy0, gx1 + 1, gy1 + 1), RUNWAY, random.Random(7811))
    my = (gy0 + gy1) // 2
    for xx in range(gx0 + 14, gx1 - 14, 10):
        b.d.line([xx, my, xx + 5, my], c("#f2f2f2"))
    for ex in (gx0 + 2, gx1 - 8):
        for yy in range(gy0 + 2, gy1 - 1, 3):
            b.d.line([ex, yy, ex + 6, yy], c("#f2f2f2"))
    # Touchdown marks in from each end, and the tyre marks where the planes come down.
    for ex in (gx0 + 16, gx1 - 26):
        for yy in (gy0 + 3, gy1 - 4):
            b.d.rectangle([ex, yy, ex + 8, yy + 1], c("#f2f2f2"))
        b.d.line([ex + 10, my - 1, ex + 22, my - 1], c("#3a3a40"))
        b.d.line([ex + 12, my + 1, ex + 20, my + 1], c("#3a3a40"))
    b.d.line([gx0, gy0, gx1, gy0], c("#f2f2f2"))
    b.d.line([gx0, gy1, gx1, gy1], c("#f2f2f2"))
    # Edge lights along both sides.
    for xx in range(gx0 + 4, gx1, 8):
        b.d.point((xx, gy0 - 1), c("#f2e6a0"))
        b.d.point((xx, gy1 + 1), c("#f2e6a0"))


LIVERIES = [c("#c0392b"), c("#2f5fa8"), c("#2a8a5a"), c("#e0a020"), c("#6a3a8a")]


def plane(b, x, y, size, jet, livery=None):
    """A parked plane, nose to the south, on the apron at tile pixel [x], [y]: its shadow, the fuselage with a row of
    windows, swept wings with engines for a jet, and the tail in its airline's colour."""
    gx, gy = b.ground(x, y)
    body = c("#eef0f2")
    edge = c("#b8bec4")
    tail = livery or LIVERIES[0]
    d = b.d
    span = size + (3 if jet else 1)
    sweep = 2 if jet else 0
    # The shadow, down and to the right.
    d.polygon([(gx - span + 2, gy + 2 + sweep), (gx + span + 2, gy + 2 + sweep), (gx + 3, gy + 4), (gx + 1, gy + 4)], (0, 0, 0, 50))
    d.rectangle([gx, gy - size + 2, gx + 3, gy + size + 2], (0, 0, 0, 50))
    # Wings, swept back on a jet, and the engines under them.
    d.polygon([(gx - span, gy + sweep), (gx + span, gy + sweep), (gx + 2, gy - 1), (gx - 2, gy - 1)], body)
    d.line([gx - span, gy + sweep, gx + span, gy + sweep], edge)
    if jet:
        for ex in (gx - span // 2 - 1, gx + span // 2 + 1):
            d.rectangle([ex - 1, gy + sweep // 2 - 1, ex, gy + sweep // 2 + 1], c("#8a8f96"))
    # The tailplane and fin, in the airline's colour.
    d.rectangle([gx - size // 2, gy - size, gx + size // 2, gy - size + 1], body)
    d.line([gx - size // 2, gy - size + 1, gx + size // 2, gy - size + 1], edge)
    d.rectangle([gx - 1, gy - size - 1, gx + 1, gy - size + 2], tail)
    # The fuselage, its windows and a stripe.
    d.rectangle([gx - 1, gy - size, gx + 1, gy + size], body)
    d.line([gx + 1, gy - size, gx + 1, gy + size], edge)
    if size >= 6:
        for yy in range(gy - size + 3, gy + size - 1, 2):
            d.point((gx, yy), c("#5a6a7a"))
    d.point((gx, gy + size), c("#3a5a8a"))
    d.point((gx - 1, gy + size - 1), tail)


def apron_stand(b, x, y, size):
    """A stand's markings on the apron: the yellow lead-in line and its stop bar."""
    gx, gy = b.ground(x, y)
    d = b.d
    d.line([gx, gy - size - 6, gx, gy + size + 2], c("#e0b830"))
    d.line([gx - 2, gy + size + 3, gx + 2, gy + size + 3], c("#e0b830"))


def service_truck(b, x, y, col):
    """A little airside truck: fuel, baggage or catering."""
    gx, gy = b.ground(x, y)
    b.d.rectangle([gx, gy, gx + 4, gy + 2], col, OUTLINE)
    b.d.point((gx + 4, gy + 1), c("#3a4a5a"))


def windsock(b, x, y):
    """A windsock on its pole, streaming east."""
    gx, gy = b.ground(x, y)
    b.d.line([gx, gy, gx, gy - 9], c("#d0d0d0"))
    b.d.polygon([(gx, gy - 9), (gx + 6, gy - 8), (gx, gy - 6)], c("#e8702a"))
    b.d.line([gx + 2, gy - 9, gx + 2, gy - 7], c("#f2f2ea"))


def hangar(b, look, x0, y0, x1, y1):
    """A hangar with a curved roof ribbed across, its doors open to the field and a plane's nose inside."""
    roof, wall = b.box(x0, y0, x1, y1, STOREY + 3)
    siding(b.d, wall, c("#a9aeb3"))
    dx0, dx1 = wall[0] + 3, wall[2] - 3
    b.d.rectangle([dx0, wall[1] + 2, dx1, wall[3]], c("#3e4348"))
    cx = (dx0 + dx1) // 2
    b.d.polygon([(cx - 6, wall[3]), (cx + 6, wall[3]), (cx + 1, wall[3] - 3), (cx - 1, wall[3] - 3)], c("#d8dce0"))
    b.d.rectangle([cx - 1, wall[3] - 5, cx + 1, wall[3]], c("#eef0f2"))
    b.d.rectangle(wall, outline=OUTLINE)
    if look == "snow":
        b.d.rectangle(roof, SNOW_ROOF[0], OUTLINE)
    else:
        rows = max(1, roof[3] - roof[1])
        for k, yy in enumerate(range(roof[1], roof[3] + 1)):
            # Lit on its northern curve, falling into shade to the south.
            b.d.line([roof[0], yy, roof[2], yy], shade(c("#9aa0a6"), 1.25 - 0.5 * k / rows))
        for xx in range(roof[0] + 4, roof[2], 5):
            b.d.line([xx, roof[1] + 1, xx, roof[3] - 1], shade(c("#9aa0a6"), 0.8))
        b.d.rectangle(roof, outline=OUTLINE)


def control_tower(b, look, x, y):
    """A control tower: a shaft and the glass cab on top."""
    roof, wall = b.box(x, y, x + 5, y + 5, 26)
    b.d.rectangle(wall, c("#d8d4cc"), OUTLINE)
    b.d.rectangle([roof[0] - 1, roof[1] - 2, roof[2] + 1, roof[1] + 3], TERMINAL_GLASS, OUTLINE)
    b.d.rectangle(roof, SNOW_ROOF[0] if look == "snow" else c("#c9c4ba"), OUTLINE)


def terminal_building(b, look, x0, y0, x1, y1, storeys):
    """The passenger terminal: long, glass across its front."""
    roof, wall = b.box(x0, y0, x1, y1, STOREY * storeys)
    b.d.rectangle(wall, c("#d8d4cc"))
    for yy in range(wall[1] + 2, wall[3] - 1, STOREY):
        b.d.rectangle([wall[0] + 2, yy, wall[2] - 2, yy + 2], TERMINAL_GLASS)
    b.d.rectangle(wall, outline=OUTLINE)
    flat_roof(b.img, roof, look, random.Random(7820 + storeys), features=(("skylight", 6, 3), ("skylight", 20, 3)))
    return roof


def airfield(look, v):
    """A grass airfield on 4 by 3 tiles: the strip along the north with its windsock, a hangar, a club house with its
    fuel pump, and light planes parked by the road in their owners' colours."""
    b = Building(4, 3, height=STOREY + 6)
    d = b.d
    gx0, gy0 = b.ground(0, 0)
    gx1, gy1 = b.ground(4 * T - 1, 3 * T - 1)
    noise_fill(b.img, (gx0, gy0, gx1 + 1, gy1 + 1), GRASS[look], random.Random(7830))
    runway(b, look, 2, 4 * T - 3, 20, 14, grass=True)
    left = v % 2 == 0
    windsock(b, 6 if left else 120, 32)
    # A worn track from the strip to the hangar, and the gravel apron in front of it.
    hx0, hx1 = (8, 40) if left else (80, 116)
    tx0, ty0 = b.ground((hx0 + hx1) // 2 - 2, 27)
    tx1, ty1 = b.ground((hx0 + hx1) // 2 + 2, 50)
    d.rectangle([tx0, ty0, tx1, ty1], SNOW_GROUND if look == "snow" else c("#a89a78"))
    ax0, ay0 = b.ground(hx0 - 2, 84)
    ax1, ay1 = b.ground(hx1 + 30 if left else hx1 + 2, 94)
    d.rectangle([ax0, ay0, ax1, ay1], SNOW_GROUND if look == "snow" else c("#b8ad90"))
    hangar(b, look, hx0, 52, hx1, 84)
    # The club house, with its fuel pump.
    cx0 = hx1 + 8 if left else hx0 - 30
    roof, wall = b.box(cx0, 66, cx0 + 20, 82, STOREY + 2)
    siding(d, wall, [c("#ece6d6"), c("#d8c8a8"), c("#c8d8e0"), c("#ece6d6")][v])
    windows(d, wall, 1, sill=TRIM, every=5, skip_door=True)
    door(d, wall)
    d.rectangle(wall, outline=OUTLINE)
    gable_ew(d, roof, SHINGLE[v % 3], look)
    px, py = b.ground(cx0 + 24, 86)
    d.rectangle([px, py - 4, px + 2, py], c("#c0392b"), OUTLINE)
    rng = random.Random(7860 + v)
    spots = [82, 98, 114] if left else [12, 26, 40]
    for k, x in enumerate(spots[: 2 + v // 2]):
        plane(b, x, 66 + (k % 2) * 6, 4, jet=False, livery=rng.choice(LIVERIES))
    return b


def airport(look, v, w, h, jet):
    """An airport: the runway along the north, the apron with parked planes, the terminal and its tower along the south."""
    b = Building(w, h, height=34 if jet else 30)
    gx0, gy0 = b.ground(0, 0)
    gx1, gy1 = b.ground(w * T - 1, h * T - 1)
    noise_fill(b.img, (gx0, gy0, gx1 + 1, gy1 + 1), GRASS[look], random.Random(7840))
    runway(b, look, 2, w * T - 3, 22, 18 if jet else 14)
    # A taxiway down to the apron.
    tx0, ty0 = b.ground(w * T // 2 - 3, 31)
    tx1, ty1 = b.ground(w * T // 2 + 3, 56)
    noise_fill(b.img, (tx0, ty0, tx1 + 1, ty1 + 1), RUNWAY, random.Random(7812))
    ax0, ay0 = b.ground(10, 56)
    ax1, ay1 = b.ground(w * T - 11, h * T - 34)
    noise_fill(b.img, (ax0, ay0, ax1 + 1, ay1 + 1), APRON, random.Random(7813))
    # The taxiway's yellow centre line, down from the runway and along the apron's north edge.
    lx, ly0 = b.ground(w * T // 2, 31)
    _, ly1 = b.ground(0, 58)
    b.d.line([lx, ly0, lx, ly1], c("#e0b830"))
    b.d.line([ax0 + 4, ly1, ax1 - 4, ly1], c("#e0b830"))
    rng = random.Random(7850 + v)
    size = 7 if jet else 5
    every = 22 if jet else 26
    stands = list(range(26, w * T - 26, every))
    for x in stands:
        apron_stand(b, x, 70, size)
    troof = terminal_building(b, look, 18, h * T - 30, w * T - 40, h * T - 8, 3 if jet else 2)
    # Jet bridges out from the terminal's roof edge to the bigger stands.
    if jet:
        for x in stands[::2]:
            bx = x + 3
            b.d.rectangle([bx, troof[1] - 7, bx + 2, troof[1]], c("#c8ccd0"), OUTLINE)
            b.d.rectangle([bx - 1, troof[1] - 9, bx + 3, troof[1] - 7], c("#a8acb0"), OUTLINE)
    for x in stands:
        if rng.random() < 0.75:
            plane(b, x, 68 + rng.randrange(0, 4), size, jet, livery=rng.choice(LIVERIES))
            if rng.random() < 0.6:
                service_truck(b, x + size + 2, 72 + rng.randrange(0, 6), rng.choice([c("#e8e4da"), c("#e0b830"), c("#c0392b")]))
    control_tower(b, look, w * T - 30, h * T - 30)
    windsock(b, w * T - 8, 34)
    return b


def airport_mid(look, v): return airport(look, v, 6, 4, jet=False)
def airport_big(look, v): return airport(look, v, 8, 4, jet=True)


def wharf_ew(look, v): return wharf(look, v, 3, 2)
def wharf_ns(look, v): return wharf(look, v, 2, 3)
def docks_ew(look, v): return docks(look, v, 4, 3)
def docks_ns(look, v): return docks(look, v, 3, 4)
def boxport_ew(look, v): return container_port(look, v, 6, 3)
def boxport_ns(look, v): return container_port(look, v, 3, 6)


# ---- water ---------------------------------------------------------------------
# The waterworks: a steam pumping station of brick with its chimney, a fenced
# well field with pump houses, a water tank on legs, and outfalls where the
# sewers and storm drains come out at the bank. A storm pond is a pond with
# reeds and a concrete inlet.

TANK = c("#8a6a45")
TANK_BAND = c("#5a4430")
STEEL_LEG = c("#4a4f55")
CONCRETE = c("#a8a49a")
PIPE_MOUTH = c("#26282c")
STAIN = (96, 78, 40, 150)
POND = [c("#4f7f8f"), c("#5a8d9c"), c("#46727f")]
REED = [c("#6f8a3a"), c("#86a04a"), c("#5a7030")]


def pumping_station(look, v):
    """A brick pumping station on 2 by 2 tiles, tall arched windows, a chimney and a small coal yard."""
    b = Building(2, 2, height=40)
    d = b.d
    chimney(b, 56, 10, 38, c("#8a4f3c"), look)
    roof, wall = b.box(4, 12, 50, 40, 3 * STOREY)
    brick(d, wall, [c("#9a5a42"), c("#8a6a52")][v % 2])
    x0, y0, x1, y1 = wall
    # Tall arched windows.
    for xx in range(x0 + 4, x1 - 4, 7):
        d.rectangle([xx, y0 + 3, xx + 3, y1 - 4], c("#6c7f8a"))
        d.point((xx, y0 + 3), c("#9a5a42"))
        d.point((xx + 3, y0 + 3), c("#9a5a42"))
    d.rectangle([x0, y0 + 1, x1, y0 + 1], STONE)
    d.rectangle(wall, outline=OUTLINE)
    gable_ew(d, roof, SHINGLE[2], look)
    # Coal for the boilers by the door.
    gx, gy = b.ground(54, 52)
    for k, col in enumerate(COAL):
        d.ellipse([gx - 6 + k, gy - 3 - k, gx + 6 - k, gy + 2 - k], col)
    return b


def well_field(look, v):
    """A fenced field with little pump houses over the wells: four in a square; three in a row along a path; or two and a
    small tank."""
    b = Building(2, 2, height=14)
    d = b.d
    gx0, gy0 = b.ground(2, 2)
    gx1, gy1 = b.ground(61, 61)
    d.rectangle([gx0, gy0, gx1, gy1], outline=c("#8a7a64"))
    for xx in range(gx0, gx1 + 1, 4):
        d.point((xx, gy0), c("#6b5a44"))
        d.point((xx, gy1), c("#6b5a44"))
    k = v % 3
    if k == 0:
        spots = ((12, 14), (40, 12), (16, 42), (44, 44))
    elif k == 1:
        spots = ((8, 28), (28, 28), (48, 28))
        px, py = b.ground(4, 40)
        d.rectangle([px, py, px + 56, py + 2], c("#b8a888") if look != "snow" else c("#dfe5ea"))
    else:
        spots = ((10, 10), (44, 40))
        cylinder(b, look, 42, 18, 6, 10, c("#8a8c90"))
    # Mown paths to each well, and the main they feed under the field.
    mown = c("#e6ecf0") if look == "snow" else c("#9ab878")
    for (hx, hy) in spots:
        sx, sy = b.ground(hx + 4, hy + 8)
        ex, ey = b.ground(hx + 4, 60)
        d.rectangle([sx - 1, sy, sx + 1, ey], mown)
    for (hx, hy) in spots:
        roof, wall = b.box(hx, hy, hx + 8, hy + 7, STOREY)
        siding(d, wall, PAINT[(v + hx) % len(PAINT)])
        door(d, wall)
        d.rectangle(wall, outline=OUTLINE)
        gable_ew(d, roof, SHINGLE[(1 + k) % 3], look)
    return b

def water_tower(look, v):
    """A water tower standing high over its tile: a timber tank on steel legs; a concrete standpipe of the thirties; or a steel
    sphere on a stem, painted pale blue."""
    b = Building(1, 1, height=40)
    d = b.d
    top = b.lift
    k = v % 3
    if k == 0:
        for lx in (8, 23):
            d.line([lx, 26 + top, lx + 2, top - 24], STEEL_LEG)
        d.line([8, 26 + top - 16, 23, 26 + top - 30], STEEL_LEG)
        d.line([23, 26 + top - 16, 8, 26 + top - 30], STEEL_LEG)
        tx0, tx1 = 5, 26
        ty0, ty1 = top - 38, top - 24
        d.rectangle([tx0, ty0, tx1, ty1], TANK, OUTLINE)
        for yy in range(ty0 + 3, ty1, 4):
            d.line([tx0 + 1, yy, tx1 - 1, yy], TANK_BAND)
        d.polygon([(tx0 - 1, ty0), ((tx0 + tx1) // 2, ty0 - 6), (tx1 + 1, ty0)], SNOW_ROOF[0] if look == "snow" else c("#5b5f6b"), OUTLINE)
        b.casters.append((1, 7, 10, 25, 22, 40))
    elif k == 1:
        # A tall round standpipe of concrete, fluted, with a lantern on top.
        col = c("#c8c4b8")
        d.rectangle([9, top - 36, 22, top + 24], col, OUTLINE)
        for xx in range(11, 22, 3):
            d.line([xx, top - 34, xx, top + 22], shade(col, 0.88))
        d.rectangle([8, top - 38, 23, top - 34], shade(col, 1.08), OUTLINE)
        d.ellipse([12, top - 43, 19, top - 37], SNOW_ROOF[0] if look == "snow" else c("#6a8a7a"), OUTLINE)
        b.casters.append((1, 9, 14, 23, 24, 40))
    else:
        # A sphere on a single stem.
        d.rectangle([14, top - 22, 17, top + 24], c("#9ab0c0"), OUTLINE)
        d.ellipse([5, top - 40, 26, top - 20], c("#a8c4d8") if look != "snow" else SNOW_ROOF[0], OUTLINE)
        d.arc([7, top - 38, 24, top - 22], 200, 300, c("#d8e8f2"))
        b.casters.append((1, 6, 10, 26, 22, 40))
    return b

def outfall(look, v, stain=True):
    """A stone headwall at the bank with a pipe's dark mouth, and the stain it leaves."""
    b = Building(1, 1, height=4)
    d = b.d
    # The bank cut back to the headwall, and its wing walls either side.
    gx0, gy0 = b.ground(4, 6)
    gx1, gy1 = b.ground(27, 22)
    d.rectangle([gx0, gy0, gx1, gy1], SNOW_GROUND if look == "snow" else c("#8a8070"))
    for (a, z) in ((4, 9), (22, 27)):
        wr, ww = b.box(a, 10, z, 20, 3)
        d.rectangle(ww, shade(CONCRETE if not stain else STONE, 0.9))
        d.rectangle(wr, SNOW_ROOF[0] if look == "snow" else shade(CONCRETE, 1.05), OUTLINE)
    roof, wall = b.box(9, 10, 22, 20, 5)
    d.rectangle(wall, CONCRETE if not stain else STONE)
    if stain:
        for yy in range(wall[1] + 2, wall[3], 3):
            d.line([wall[0] + 1, yy, wall[2] - 1, yy], shade(STONE, 0.88))
    d.rectangle(roof, SNOW_ROOF[0] if look == "snow" else shade(CONCRETE, 1.1))
    d.ellipse([11, wall[1] + 1, 20, wall[3] + 2], PIPE_MOUTH)
    for xx in range(13, 19, 2):
        d.line([xx, wall[1] + 2, xx, wall[3]], c("#5a5a60"))
    d.rectangle(wall, outline=OUTLINE)
    if stain:
        gx, gy = b.ground(15, 24)
        d.ellipse([gx - 9, gy - 3, gx + 9, gy + 6], STAIN)
    # Ripples where it runs into the water.
    gx, gy = b.ground(15, 28)
    d.arc([gx - 6, gy - 2, gx + 6, gy + 2], 0, 180, c("#c8dcea"))
    d.arc([gx - 9, gy - 1, gx + 9, gy + 4], 20, 160, c("#a8c4d8"))
    return b


def sewer_outfall(look, v):
    return outfall(look, v, stain=True)


def storm_outfall(look, v):
    return outfall(look, v, stain=False)


def storm_pond(look, v):
    """A pond on 2 by 2 tiles with grassy banks, reeds and a concrete inlet."""
    b = Building(2, 2, height=4)
    d = b.d
    rng = random.Random(7800 + v)
    gx0, gy0 = b.ground(0, 0)
    ice = look == "snow"
    cols = [c("#c9d8e2"), c("#d7e2ea"), c("#bccbd6")] if ice else POND
    # Round, long, or bent like a kidney.
    rx, ry, bend = [(27, 24, 0.0), (29, 16, 0.0), (26, 22, 0.35)][v % 3]
    for y in range(64):
        for x in range(64):
            dx, dy = (x - 31.5) / rx, (y - 31.5 - bend * ((x - 31.5) ** 2) / 30) / ry
            if dx * dx + dy * dy <= 1:
                d.point((gx0 + x, gy0 + y), rng.choice(cols))
    if not ice:
        for _ in range(40):
            a = rng.random() * math.tau
            x = 31.5 + math.cos(a) * (rx - 1) * (0.9 + rng.random() * 0.15)
            y = 31.5 + math.sin(a) * (ry - 1) * (0.9 + rng.random() * 0.15) + bend * ((x - 31.5) ** 2) / 30
            col = rng.choice(REED)
            d.line([gx0 + x, gy0 + y, gx0 + x, gy0 + y - 3], col)
    # The inlet on the north bank.
    d.rectangle([gx0 + 28, gy0 + 4, gx0 + 35, gy0 + 9], CONCRETE, OUTLINE)
    d.ellipse([gx0 + 30, gy0 + 6, gx0 + 33, gy0 + 9], PIPE_MOUTH)
    return b


# Schools and health care: a schoolhouse with its bell and yard, a high school
# with columns at the door, a doctor's clinic and a hospital of three wings.

PLAYGROUND = c("#c8b48a")
SNOW_GROUND = c("#e4ebf0")
CLINIC_BLUE = c("#3f6fb0")


def bell_cupola(b, look, x, y, height):
    """A little open bell tower with a pointed roof, standing on a roof at tile pixel x, y."""
    d = b.d
    top = b.lift
    base = y + top - height
    d.rectangle([x - 2, base - 5, x + 2, base], TRIM, OUTLINE)
    d.point((x, base - 3), c("#b08a3a"))
    d.polygon([(x - 3, base - 5), (x, base - 10), (x + 3, base - 5)], SNOW_ROOF[0] if look == "snow" else SHINGLE[2], OUTLINE)
    b.casters.append((1, x - 2, y - 2, x + 3, y + 2, height + 10))


def schoolyard(b, look, x0, y0, x1, y1):
    """Bare ground fenced off for play, with a few marks of games on it."""
    d = b.d
    gx0, gy0 = b.ground(x0, y0)
    gx1, gy1 = b.ground(x1, y1)
    d.rectangle([gx0, gy0, gx1, gy1], SNOW_GROUND if look == "snow" else PLAYGROUND)
    for xx in range(gx0, gx1 + 1, 3):
        d.point((xx, gy0), c("#6b5a44"))
        d.point((xx, gy1), c("#6b5a44"))
    for yy in range(gy0, gy1 + 1, 3):
        d.point((gx0, yy), c("#6b5a44"))
        d.point((gx1, yy), c("#6b5a44"))
    if look != "snow":
        # Hopscotch, and a swing frame.
        for k in range(3):
            d.rectangle([gx0 + 4, gy0 + 3 + k * 3, gx0 + 6, gy0 + 5 + k * 3], outline=c("#efe8d8"))
        d.line([gx1 - 10, gy0 + 3, gx1 - 3, gy0 + 3], c("#4a3a2a"))
        d.line([gx1 - 10, gy0 + 3, gx1 - 10, gy0 + 9], c("#4a3a2a"))
        d.line([gx1 - 3, gy0 + 3, gx1 - 3, gy0 + 9], c("#4a3a2a"))


def school(look, v):
    """A two storey schoolhouse on 2 by 2 tiles and a yard to the south: a long brick one with a bell on the roof, or one
    of white boards with a wing running back on the east."""
    b = Building(2, 2, height=2 * STOREY + 18)
    d = b.d
    schoolyard(b, look, 4, 42, 59, 61)
    if v == 1:
        # The wing, gable end to the yard, drawn first so the main block stands in front of it.
        wroof, wwall = b.box(42, 4, 58, 40, 2 * STOREY + 2)
        siding(d, wwall, c("#e2dccb"))
        windows(d, wwall, 2, glass=c("#46586a"), sill=TRIM, every=5, width=3, height=4)
        d.rectangle(wwall, outline=OUTLINE)
        gable_ns(d, wroof, wwall, SHINGLE[1], c("#e2dccb"), look)
    roof, wall = b.box(6, 10, 57 if v == 0 else 41, 38, 2 * STOREY + 2)
    if v == 0:
        brick(d, wall, c("#a0503a"))
    else:
        siding(d, wall, c("#ece6d6"))
    windows(d, wall, 2, glass=c("#46586a"), sill=TRIM, every=5, width=3, height=4, skip_door=True)
    x0, y0, x1, y1 = wall
    cx = (x0 + x1) // 2
    # Double doors under a little pediment.
    d.rectangle([cx - 3, y1 - 6, cx + 3, y1], c("#5a3a2a"))
    d.line([cx, y1 - 6, cx, y1], OUTLINE)
    d.polygon([(cx - 5, y1 - 7), (cx, y1 - 10), (cx + 5, y1 - 7)], TRIM, OUTLINE)
    d.rectangle(wall, outline=OUTLINE)
    gable_ew(d, roof, SHINGLE[0] if v == 0 else SHINGLE[2], look)
    bell_cupola(b, look, (6 + 57) // 2 if v == 0 else (6 + 41) // 2, 24, 2 * STOREY + 8)
    return b


def high_school(look, v):
    """Three storeys of brick or stone on 3 by 2 tiles, a columned door in the middle and a flag out front."""
    b = Building(3, 2, height=3 * STOREY + 8)
    d = b.d
    rng = random.Random(8100 + v)
    gx0, gy0 = b.ground(10, 48)
    gx1, gy1 = b.ground(85, 61)
    d.rectangle([gx0, gy0, gx1, gy1], SNOW_GROUND if look == "snow" else c("#7fa05a"))
    # A path up to the door.
    px, py = b.ground(46, 46)
    d.rectangle([px, py, px + 4, gy1], c("#d9ccaa") if look != "snow" else c("#cfd8df"))
    roof, wall = b.box(4, 8, 91, 44, 3 * STOREY + 2)
    if v == 0:
        brick(d, wall, c("#8e4a3a"))
    else:
        d.rectangle(wall, c("#cfc6b0"))
        x0, y0, x1, y1 = wall
        for yy in range(y0 + 2, y1, 4):
            d.line([x0 + 1, yy, x1 - 1, yy], shade(c("#cfc6b0"), 0.93))
    x0, y0, x1, y1 = wall
    d.rectangle([x0, y0 + 1, x1, y0 + 1], STONE)
    windows(d, wall, 3, glass=c("#46586a"), sill=TRIM, every=5, width=3, height=4, skip=[((x0 + x1) // 2 - 8, (x0 + x1) // 2 + 8)])
    cx = (x0 + x1) // 2
    # The portico: columns and a pediment over the door.
    d.rectangle([cx - 7, y1 - 12, cx + 7, y1], c("#e6dfcc"))
    for k in range(-6, 7, 3):
        d.line([cx + k, y1 - 11, cx + k, y1], c("#bfb7a4"))
    d.rectangle([cx - 2, y1 - 6, cx + 2, y1], c("#4a3226"))
    d.polygon([(cx - 9, y1 - 12), (cx, y1 - 17), (cx + 9, y1 - 12)], TRIM, OUTLINE)
    d.rectangle(wall, outline=OUTLINE)
    flat_roof(b.img, roof, look, rng, [("stack", 8, 4), ("stack", 76, 4), ("hatch", 40, 10), ("vent", 60, 14)], parapet=STONE)
    if v == 1:
        # A clock tower over the door, with a pyramid cap.
        troof, twall = b.box(40, 26, 55, 40, 5 * STOREY)
        d.rectangle(twall, c("#cfc6b0"), OUTLINE)
        tx0, ty0, tx1, ty1 = twall
        clock_face(d, (tx0 + tx1) // 2, ty0 + 5)
        rx0, ry0, rx1, ry1 = troof
        d.rectangle(troof, SNOW_ROOF[0] if look == "snow" else c("#6a5a4a"), OUTLINE)
        tcx, tcy = (rx0 + rx1) // 2, (ry0 + ry1) // 2
        d.polygon([(rx0, ry1), (tcx, tcy - 6), (rx1, ry1)], SNOW_ROOF[1] if look == "snow" else c("#5a4a3a"), OUTLINE)
    # The flagpole.
    fx, fy = b.ground(66, 54)
    d.line([fx, fy, fx, fy - 22], c("#d0d0d0"))
    d.rectangle([fx + 1, fy - 22, fx + 6, fy - 19], c("#c0392b"))
    d.rectangle([fx + 3, fy - 22, fx + 4, fy - 19], c("#f2f2ea"))
    b.casters.append((1, 65, 53, 67, 55, 22))
    return b


def clinic(look, v):
    """A doctor's clinic on one tile: a neat white building with a blue sign and a lamp by the door."""
    b = Building(height=2 * STOREY + 6)
    d = b.d
    roof, wall = b.box(4, 8, 27, 25, 2 * STOREY)
    if v == 0:
        siding(d, wall, c("#eeeae0"))
    else:
        brick(d, wall, c("#b07a5a"))
    windows(d, wall, 2, sill=TRIM, every=5, skip_door=True)
    door(d, wall, c("#3a4f6a"))
    x0, y0, x1, y1 = wall
    # A blue sign with a white cross over the door.
    cx = (x0 + x1) // 2
    d.rectangle([cx - 3, y0 + STOREY - 1, cx + 3, y0 + STOREY + 3], CLINIC_BLUE)
    d.line([cx, y0 + STOREY, cx, y0 + STOREY + 2], c("#ffffff"))
    d.line([cx - 1, y0 + STOREY + 1, cx + 1, y0 + STOREY + 1], c("#ffffff"))
    d.rectangle(wall, outline=OUTLINE)
    if v == 0:
        gable_ew(d, roof, SHINGLE[2], look)
    else:
        flat_roof(b.img, roof, look, random.Random(8200), [("vent", 6, 4)], parapet=STONE)
    return b


def cooling_centre(look, v):
    """A cooling centre on one tile: a low pale building with an awning over the door for shade, a sign with a snowflake,
    and the air conditioning on the roof."""
    b = Building(height=STOREY + 8)
    d = b.d
    roof, wall = b.box(4, 9, 27, 25, STOREY + 2)
    siding(d, wall, c("#e6ebef") if v == 0 else c("#dfe8e0"))
    windows(d, wall, 1, sill=TRIM, every=5, skip_door=True)
    door(d, wall, c("#3a5a7a"))
    x0, y0, x1, y1 = wall
    cx = (x0 + x1) // 2
    # The awning, striped, out over the door.
    for k, xx in enumerate(range(cx - 7, cx + 8)):
        d.line([xx, y0 - 1, xx, y0 + 2], c("#3f8fc0") if (k // 2) % 2 == 0 else c("#f2f2f2"))
    # A snowflake on a blue sign.
    d.rectangle([x1 - 7, y0 + 2, x1 - 2, y0 + 7], c("#3f8fc0"))
    for (dx, dy) in ((0, -2), (0, 2), (-2, 0), (2, 0), (-1, -1), (1, 1), (-1, 1), (1, -1)):
        d.point((x1 - 5 + dx // 2, y0 + 4 + dy // 2), c("#ffffff"))
    d.rectangle(wall, outline=OUTLINE)
    flat_roof(b.img, roof, look, random.Random(8250 + v), [("vent", 4, 3), ("vent", 12, 3), ("vent", 4, 8)], parapet=STONE)
    return b


def hospital(look, v):
    """A hospital on 3 by 3 tiles: a tall middle block and two wings, pale stone, a covered entrance and lawns."""
    b = Building(3, 3, height=4 * STOREY + 6)
    d = b.d
    rng = random.Random(8300)
    gx0, gy0 = b.ground(2, 2)
    gx1, gy1 = b.ground(93, 93)
    d.rectangle([gx0, gy0, gx1, gy1], SNOW_GROUND if look == "snow" else c("#86a85e"))
    # The drive to the door.
    dx0, dy0 = b.ground(40, 74)
    d.rectangle([dx0, dy0, dx0 + 15, gy1], c("#b8b2a6") if look != "snow" else c("#d6dde3"))
    wall_col = c("#ddd5c2")
    # The wings first, then the middle block in front of them.
    for (x0_, x1_) in ((6, 30), (66, 90)):
        roof, wall = b.box(x0_, 10, x1_, 70, 3 * STOREY)
        d.rectangle(wall, wall_col)
        windows(d, wall, 3, sill=TRIM, every=4)
        d.rectangle(wall, outline=OUTLINE)
        flat_roof(b.img, roof, look, rng, [("vent", 6, 10), ("vent", 14, 40)], parapet=STONE)
    roof, wall = b.box(28, 6, 68, 64, 4 * STOREY + 2)
    d.rectangle(wall, shade(wall_col, 1.03))
    x0, y0, x1, y1 = wall
    windows(d, wall, 4, sill=TRIM, every=4, skip=[((x0 + x1) // 2 - 7, (x0 + x1) // 2 + 7)])
    cx = (x0 + x1) // 2
    # The covered entrance, and a blue sign over it.
    d.rectangle([cx - 8, y1 - 7, cx + 8, y1 - 6], c("#5a6068"))
    for k in (-7, 7):
        d.line([cx + k, y1 - 5, cx + k, y1 + 3], c("#9a9aa0"))
    d.rectangle([cx - 2, y1 - 5, cx + 2, y1], c("#3a4f6a"))
    d.rectangle([cx - 4, y0 + 3, cx + 4, y0 + 7], CLINIC_BLUE)
    d.line([cx, y0 + 4, cx, y0 + 6], c("#ffffff"))
    d.line([cx - 1, y0 + 5, cx + 1, y0 + 5], c("#ffffff"))
    d.rectangle(wall, outline=OUTLINE)
    flat_roof(b.img, roof, look, rng, [("stack", 6, 4), ("tank", 26, 10), ("hatch", 12, 30)], parapet=STONE)
    return b


# Denser buildings: row houses, apartments and an apartment court for homes;
# a main street block, an office block and a department store for shops; and
# works on four lots for industry. Then building sites, in two steps.

CORNICE = c("#d8cfba")
PIER = c("#c4bba6")
EARTH = [c("#8a6a48"), c("#7a5c3e"), c("#96765a")]
SCAFFOLD = c("#b89a6a")
STEEL = c("#5a4a44")


def cornice(d, wall, col=CORNICE):
    x0, y0, x1, _ = wall
    d.rectangle([x0 - 1, y0, x1 + 1, y0 + 1], col)
    d.line([x0 - 1, y0 + 2, x1 + 1, y0 + 2], shade(col, 0.7))


def row_houses(look, v):
    """A terrace of three narrow brick houses, two storeys and an attic, each with its own door and stoop."""
    b = Building(height=2 * STOREY + 10)
    d = b.d
    cols = [[BRICK, BRICK20, c("#8a5a44")], [c("#b0704e"), BRICK, c("#a8583f")], [c("#7a4a3c"), c("#9a5a42"), BRICK20]][v]
    gabled = v != 1
    for k, (x0, x1) in enumerate(((2, 10), (11, 20), (21, 29))):
        roof, wall = b.box(x0, 7, x1, 25, 2 * STOREY + 2)
        brick(d, wall, cols[k])
        wx0, wy0, wx1, wy1 = wall
        d.rectangle([wx0 + 2, wy0 + 2, wx0 + 3, wy0 + 4], WINDOW)
        d.rectangle([wx1 - 3, wy0 + 2, wx1 - 2, wy0 + 4], WINDOW)
        d.rectangle([wx1 - 3, wy1 - 5, wx1 - 2, wy1 - 3], WINDOW)
        d.rectangle([wx0 + 2, wy1 - 5, wx0 + 3, wy1], DOOR)
        d.line([wx0 + 1, wy1 + 1, wx0 + 4, wy1 + 1], STONE)
        d.rectangle(wall, outline=OUTLINE)
        if gabled:
            gable_ew(d, roof, SHINGLE[(k + v) % 3], look)
            dormers(d, roof, SHINGLE[(k + v) % 3], look, 1)
        else:
            flat_roof(b.img, roof, look, random.Random(7700 + k), [("stack", 2, 2)], parapet=PARAPET)
    return b


def apartments(look, v):
    """Five storeys of flats in brick or stone, a cornice along the top, a canopy over the door, a tank on the roof."""
    b = Building(height=5 * STOREY + 8)
    d = b.d
    roof, wall = b.box(2, 3, 29, 28, 5 * STOREY + 2)
    col = [c("#9a5a42"), c("#cfc6b0"), c("#7a4a3c")][v]
    if v == 1:
        d.rectangle(wall, col)
        for yy in range(wall[1] + 3, wall[3], STOREY):
            d.line([wall[0] + 1, yy, wall[2] - 1, yy], shade(col, 0.9))
    else:
        brick(d, wall, col)
    windows(d, wall, 5, sill=TRIM, every=4, skip_door=True)
    cx = (wall[0] + wall[2]) // 2
    d.rectangle([cx - 1, wall[3] - 4, cx + 1, wall[3]], DOOR)
    d.rectangle([cx - 3, wall[3] - 6, cx + 3, wall[3] - 5], c("#3a3c42"))
    d.rectangle(wall, outline=OUTLINE)
    cornice(d, wall)
    if v == 1:
        flat_roof(b.img, roof, look, random.Random(7800 + v), [("tank", 3, 3), ("hatch", 20, 15)], parapet=CORNICE)
        light_well(d, roof, look, col)
    elif v == 2:
        flat_roof(b.img, roof, look, random.Random(7800 + v), [("stack", 3, 3), ("stack", 22, 3)], parapet=CORNICE)
        setback(b, look, (7, 8, 24, 20), 5 * STOREY + 7, shade(col, 1.1), random.Random(7805), base=5 * STOREY + 2)
    else:
        flat_roof(b.img, roof, look, random.Random(7800 + v), [("tank", 16, 9), ("stack", 3, 3), ("hatch", 6, 14)], parapet=CORNICE)
    return b


def apartment_court(look, v):
    """A block of flats round a courtyard on 2 by 2 tiles, open to the street through an arch on the south side."""
    b = Building(2, 2, height=5 * STOREY + 8)
    d = b.d
    rng = random.Random(7900 + v)
    col = [c("#a8583f"), c("#c9b48a"), c("#7a4a3c")][v % 3]
    gx0, gy0 = b.ground(16, 16)
    gx1, gy1 = b.ground(47, 46)
    d.rectangle([gx0, gy0, gx1, gy1], SNOW_GROUND if look == "snow" else c("#7fa05a"))
    if look != "snow":
        for (tx, ty) in ((24, 26), (38, 30)):
            px, py = b.ground(tx, ty)
            d.ellipse([px - 4, py - 4, px + 4, py + 3], c("#4f7a3a"), OUTLINE)
    h = 5 * STOREY + 2

    def wing(x0, y0, x1, y1, door_at=None):
        roof, wall = b.box(x0, y0, x1, y1, h)
        brick(d, wall, col) if v != 1 else d.rectangle(wall, col)
        windows(d, wall, 5, sill=TRIM, every=4)
        if door_at is not None:
            d.rectangle([door_at - 3, wall[3] - 7, door_at + 3, wall[3]], c("#2a2a30"))
            d.arc([door_at - 3, wall[3] - 10, door_at + 3, wall[3] - 4], 180, 360, STONE)
        d.rectangle(wall, outline=OUTLINE)
        cornice(d, wall)
        flat_roof(b.img, roof, look, rng, [("stack", 3, 3)], parapet=CORNICE)

    wing(2, 2, 61, 15)
    wing(2, 16, 15, 47)
    wing(48, 16, 61, 47)
    wing(2, 48, 61, 61, door_at=32)
    return b


def main_street(look, v):
    """Three storeys on the street: a shop front with an awning below, two floors of offices or rooms above, a sign under the cornice."""
    b = Building(height=3 * STOREY + 6)
    d = b.d
    col = [BRICKS[0], BRICKS[2], GREY_STONE, BRICKS[4]][v]
    roof, wall = b.box(1, 5, 30, 27, 3 * STOREY + 2)
    if col == GREY_STONE:
        d.rectangle(wall, col)
    else:
        brick(d, wall, col)
    wx0, wy0, wx1, wy1 = wall
    windows(d, (wx0, wy0 + 3, wx1, wy0 + 2 * STOREY + 2), 2, sill=TRIM, every=4)
    d.rectangle([wx0 + 2, wy1 - 5, wx1 - 2, wy1 - 1], PLATE_GLASS)
    for xx in (wx0 + 10, wx0 + 19):
        d.line([xx, wy1 - 5, xx, wy1 - 1], shade(col, 0.6))
    awning(d, wall, AWNINGS[(v + 2) % len(AWNINGS)], look)
    sign(d, wx0 + 4, wx1 - 4, wy0 + 1, [c("#2b3440"), c("#6b2330"), c("#2f5f5a"), c("#4a3226")][v])
    d.rectangle(wall, outline=OUTLINE)
    cornice(d, wall)
    flat_roof(b.img, roof, look, random.Random(8000 + v), [("skylight", 6, 6), ("stack", 22, 3)], parapet=CORNICE)
    if v % 4 == 1:
        roof_sign(b.d, roof, [c("#2a4a6a"), c("#8a2a2a")][v % 2], 6)
    elif v % 4 == 3:
        turret(b, look, 26, 23, 3, 3 * STOREY + 8, STONE, 3 * STOREY + 2)
    return b


def office_block(look, v):
    """Eight storeys of offices in pale stone or terracotta, piers running up between the windows, a tank on the roof."""
    b = Building(height=8 * STOREY + 8)
    d = b.d
    col = [c("#d6cdb8"), c("#c98f6e"), c("#b9b8b0")][v]
    roof, wall = b.box(3, 4, 28, 27, 8 * STOREY + 2)
    d.rectangle(wall, col)
    wx0, wy0, wx1, wy1 = wall
    for xx in range(wx0 + 2, wx1 - 1, 4):
        d.rectangle([xx, wy0 + 3, xx + 1, wy1 - 6], c("#4a5866"))
    for yy in range(wy0 + 3, wy1 - 6, STOREY):
        d.line([wx0 + 1, yy, wx1 - 1, yy], shade(col, 0.85))
    # The ground floor: a stone base and a revolving door.
    d.rectangle([wx0, wy1 - 5, wx1, wy1], shade(col, 0.8))
    cx = (wx0 + wx1) // 2
    d.rectangle([cx - 2, wy1 - 4, cx + 2, wy1], c("#2a2a30"))
    d.rectangle(wall, outline=OUTLINE)
    cornice(d, wall, shade(col, 1.1))
    flat_roof(b.img, roof, look, random.Random(8100 + v), [("tank", 14, 8), ("vent", 4, 4), ("hatch", 4, 14)], parapet=shade(col, 1.1))
    if v == 1:
        # Stepped back in tiers, as the zoning of the day asked.
        setback(b, look, (7, 7, 24, 23), 8 * STOREY + 8, shade(col, 1.05), random.Random(8110), glass=c("#4a5866"), base=8 * STOREY + 2)
        setback(b, look, (11, 10, 20, 19), 8 * STOREY + 14, shade(col, 1.1), random.Random(8111), glass=c("#4a5866"), base=8 * STOREY + 8)
    elif v == 2:
        turret(b, look, 24, 23, 4, 8 * STOREY + 8, shade(col, 1.05), 8 * STOREY + 2)
    return b


def department_store(look, v):
    """Five storeys of pale stone on 2 by 2 tiles: display windows and awnings along the street, a clock at the corner, a flag on the roof."""
    b = Building(2, 2, height=5 * STOREY + 16)
    d = b.d
    col = [c("#ddd5c2"), c("#cfa98a"), c("#c8c0b0")][v % 3]
    roof, wall = b.box(2, 4, 61, 58, 5 * STOREY + 2)
    d.rectangle(wall, col)
    wx0, wy0, wx1, wy1 = wall
    windows(d, (wx0, wy0, wx1, wy1 - 8), 4, glass=c("#46586a"), sill=TRIM, every=4, width=2, height=3)
    d.rectangle([wx0 + 2, wy1 - 6, wx1 - 2, wy1 - 1], PLATE_GLASS)
    for xx in range(wx0 + 10, wx1 - 2, 10):
        d.line([xx, wy1 - 6, xx, wy1 - 1], shade(col, 0.6))
    for k, xx in enumerate(range(wx0 + 2, wx1 - 10, 12)):
        awning(d, (xx, wy0, xx + 10, wy1), AWNINGS[(k + v) % 2 * 3], look, y_up=8)
    cx = (wx0 + wx1) // 2
    d.rectangle([cx - 3, wy1 - 6, cx + 3, wy1], c("#2a2a30"))
    sign(d, cx - 14, cx + 14, wy0 + 2, c("#2b3440"))
    # The corner clock.
    d.ellipse([wx1 - 8, wy0 + 6, wx1 - 2, wy0 + 12], TRIM, OUTLINE)
    d.line([wx1 - 5, wy0 + 9, wx1 - 5, wy0 + 7], OUTLINE)
    d.rectangle(wall, outline=OUTLINE)
    cornice(d, wall)
    flat_roof(b.img, roof, look, random.Random(8200 + v), [("skylight", 10, 12), ("skylight", 30, 12), ("skylight", 10, 30), ("skylight", 30, 30), ("tank", 46, 10), ("stack", 4, 4)], parapet=CORNICE)
    fx, fy = roof[0] + 30, roof[1] + 4
    d.line([fx, fy, fx, fy - 12], c("#d0d0d0"))
    d.rectangle([fx + 1, fy - 12, fx + 6, fy - 9], c("#2f5f8a"))
    if v % 3 == 1:
        # A clock turret on the corner.
        turret(b, look, 56, 53, 5, 5 * STOREY + 12, STONE, 5 * STOREY + 2)
    elif v % 3 == 2:
        roof_sign(b.d, roof, c("#8a2a2a"), 9)
    return b


def works(look, v):
    """Works on 2 by 2 tiles: long sawtooth sheds, a boiler house with a tall chimney, a yard of coal and goods."""
    b = Building(2, 2, height=44)
    d = b.d
    col = [BRICKS[1], BRICKS[3], BRICKS[2]][v % 3]
    gx0, gy0 = b.ground(2, 46)
    gx1, gy1 = b.ground(61, 61)
    d.rectangle([gx0, gy0, gx1, gy1], c("#8a8478") if look != "snow" else SNOW_GROUND)
    for k, col_ in enumerate(COAL):
        d.ellipse([gx0 + 4 + k, gy0 + 3 - k, gx0 + 18 - k, gy0 + 10 - k], col_)
    yard(b, look, "crates", 30, 58, 58)
    for (y0, y1) in ((4, 22), (24, 42)):
        roof, wall = b.box(2, y0, 44, y1, 2 * STOREY + 2)
        brick(d, wall, col)
        windows(d, wall, 2, glass=c("#6c7f8a"), every=5, width=3)
        d.rectangle(wall, outline=OUTLINE)
        rx0, ry0, rx1, ry1 = roof
        for xx in range(rx0, rx1 - 4, 7):
            d.rectangle([xx, ry0, xx + 3, ry1], SNOW_ROOF[0] if look == "snow" else SAW[0])
            d.rectangle([xx + 4, ry0, min(xx + 6, rx1), ry1], SNOW_ROOF[2] if look == "snow" else c("#b8c4ca"))
        d.rectangle(roof, outline=OUTLINE)
    roof, wall = b.box(47, 8, 61, 40, 3 * STOREY)
    brick(d, wall, shade(col, 0.9))
    windows(d, wall, 3, glass=c("#6c7f8a"), every=5)
    d.rectangle(wall, outline=OUTLINE)
    gable_ew(d, roof, c("#5f6570"), look)
    chimney(b, 54, 6, 44, look=look)
    if v == 1:
        chimney(b, 50, 6, 38, look=look)
    return b


def site_ground(b, look, w, h):
    """Dug earth over the lot inside a board fence."""
    d = b.d
    rng = random.Random(8300 + w)
    gx0, gy0 = b.ground(1, 1)
    gx1, gy1 = b.ground(w * T - 2, h * T - 2)
    for y in range(gy0, gy1 + 1):
        for x in range(gx0, gx1 + 1):
            d.point((x, y), SNOW_GROUND if look == "snow" and rng.random() < 0.7 else rng.choice(EARTH))
    d.rectangle([gx0, gy0, gx1, gy1], outline=c("#a08a64"))
    for xx in range(gx0, gx1, 3):
        d.point((xx, gy1), c("#6b5a44"))


def site_small(look, phase):
    """A building site on one tile: a hole and a pile of timber, then a frame in scaffolding."""
    b = Building(height=2 * STOREY + 8)
    d = b.d
    site_ground(b, look, 1, 1)
    if phase == 0:
        hx0, hy0 = b.ground(7, 8)
        d.rectangle([hx0, hy0, hx0 + 16, hy0 + 12], c("#5a4430"), OUTLINE)
        yard(b, look, "lumber", 4, 16, 27)
        px, py = b.ground(24, 26)
        d.rectangle([px, py - 2, px + 3, py], c("#6b6f74"), OUTLINE)
    else:
        top = b.lift
        h = 2 * STOREY + 4
        x0, x1, y0, y1 = 5, 26, 8, 24
        # The frame's posts and floors, and the scaffold poles in front.
        for xx in range(x0, x1 + 1, 4):
            d.line([xx, y1 + top, xx, y1 + top - h], TIMBER)
        for k in range(0, h + 1, STOREY):
            d.line([x0, y1 + top - k, x1, y1 + top - k], TIMBER)
            d.line([x0, y0 + top - k, x1, y0 + top - k], shade(TIMBER, 0.8))
        for xx in (x0, x1):
            d.line([xx, y0 + top - h, xx, y1 + top - h], shade(TIMBER, 0.8))
        for xx in range(x0 - 1, x1 + 2, 6):
            d.line([xx, y1 + top + 2, xx, y1 + top - h - 2], SCAFFOLD)
        b.casters.append((1, x0, y0, x1 + 1, y1 + 1, h // 2))
    return b


def site_lot(look, phase, w, h, storeys):
    """A building site on [w] by [h] tiles, for lots that aren't square: a hole and stacks of
    timber and crates, then a steel frame [storeys] high, with a derrick at the back."""
    b = Building(w, h, height=storeys * STOREY + 12)
    d = b.d
    site_ground(b, look, w, h)
    top = b.lift
    W, H = w * T, h * T
    if phase == 0:
        hx0, hy0 = b.ground(6, 6)
        hw, hh = W - 14, H - 18
        d.rectangle([hx0, hy0, hx0 + hw, hy0 + hh], c("#4a3828"), OUTLINE)
        d.rectangle([hx0 + 3, hy0 + 3, hx0 + hw - 3, hy0 + hh - 3], c("#3f3022"))
        yard(b, look, "lumber", 4, min(W // 2, 28), H - 4)
        if W >= 48:
            yard(b, look, "crates", W // 2 + 4, W - 4, H - 4)
    else:
        hgt = storeys * STOREY
        x0, x1, y0, y1 = 5, W - 6, 6, H - 8
        for xx in range(x0, x1 + 1, 8):
            d.line([xx, y1 + top, xx, y1 + top - hgt], STEEL)
            d.line([xx, y0 + top - hgt, xx, y1 + top - hgt], shade(STEEL, 1.2))
        for k in range(0, hgt + 1, STOREY):
            d.line([x0, y1 + top - k, x1, y1 + top - k], STEEL)
        d.line([x0, y0 + top - hgt, x1, y0 + top - hgt], shade(STEEL, 1.2))
        b.casters.append((1, x0, y0, x1 + 1, y1 + 1, hgt // 2))
    # The derrick: a mast and a boom over the site.
    mx, my = W - 6, 6
    reach = min(30, W - 12)
    d.line([mx, my + top, mx, my + top - storeys * STOREY - 8], c("#3a3c42"))
    d.line([mx, my + top - storeys * STOREY - 8, mx - reach, my + top - storeys * STOREY + 4], c("#3a3c42"))
    d.line([mx - reach, my + top - storeys * STOREY + 4, mx - reach, my + top - (storeys - 2) * STOREY], c("#8a8a90"))
    b.casters.append((1, mx - 1, my - 1, mx + 1, my + 1, storeys * STOREY + 8))
    return b


def site_wide(look, phase):
    """A site on 2 by 1 tiles: terraces, shops with flats over them and the like."""
    return site_lot(look, phase, 2, 1, 4)


def site_deep(look, phase):
    """A site on 1 by 2 tiles, the same lots turned the other way."""
    return site_lot(look, phase, 1, 2, 4)


def site_huge(look, phase):
    """A site on 3 by 3 tiles, for the tallest towers."""
    return site_lot(look, phase, 3, 3, 8)


def site_large(look, phase):
    """A building site on 2 by 2 tiles: a deep hole and a derrick, then a steel frame going up."""
    b = Building(2, 2, height=5 * STOREY + 12)
    d = b.d
    site_ground(b, look, 2, 2)
    top = b.lift
    if phase == 0:
        hx0, hy0 = b.ground(8, 10)
        d.rectangle([hx0, hy0, hx0 + 44, hy0 + 36], c("#4a3828"), OUTLINE)
        d.rectangle([hx0 + 4, hy0 + 4, hx0 + 40, hy0 + 32], c("#3f3022"))
        yard(b, look, "lumber", 6, 30, 58)
        yard(b, look, "crates", 36, 58, 58)
    else:
        h = 5 * STOREY
        x0, x1, y0, y1 = 6, 56, 8, 52
        for xx in range(x0, x1 + 1, 8):
            d.line([xx, y1 + top, xx, y1 + top - h], STEEL)
            d.line([xx, y0 + top - h, xx, y1 + top - h], shade(STEEL, 1.2))
        for k in range(0, h + 1, STOREY):
            d.line([x0, y1 + top - k, x1, y1 + top - k], STEEL)
        d.line([x0, y0 + top - h, x1, y0 + top - h], shade(STEEL, 1.2))
        b.casters.append((1, x0, y0, x1 + 1, y1 + 1, h // 2))
    # The derrick: a mast and a boom over the site.
    mx, my = 58, 6
    d.line([mx, my + top, mx, my + top - 5 * STOREY - 8], c("#3a3c42"))
    d.line([mx, my + top - 5 * STOREY - 8, mx - 30, my + top - 5 * STOREY + 4], c("#3a3c42"))
    d.line([mx - 30, my + top - 5 * STOREY + 4, mx - 30, my + top - 3 * STOREY], c("#8a8a90"))
    b.casters.append((1, mx - 1, my - 1, mx + 1, my + 1, 5 * STOREY + 8))
    return b


# Sewage works: round settling tanks and a pump house on 2 by 2 tiles from the
# Streetcar city; a treatment plant of long tanks and a works building on 3 by
# 2 from Renewal.

SETTLING = [c("#6f6a52"), c("#7a745a"), c("#655f48")]
AERATION = [c("#7d8a6a"), c("#889574"), c("#728060")]


def round_tank(b, look, cx, cy, r, cols):
    d = b.d
    gx, gy = b.ground(cx, cy)
    d.ellipse([gx - r - 1, gy - r - 1, gx + r + 1, gy + r + 1], CONCRETE, OUTLINE)
    d.ellipse([gx - r + 1, gy - r + 1, gx + r - 1, gy + r - 1], c("#dfe7ee") if look == "snow" else cols[0])
    d.ellipse([gx - r // 2, gy - r // 2, gx + r // 2, gy + r // 2], c("#d6dde3") if look == "snow" else cols[1])
    # The scraper arm across it.
    d.line([gx - r + 1, gy, gx + r - 1, gy], c("#4a4f55"))


def sewage_works(look, v):
    """Two round settling tanks and a small brick pump house, fenced, by the water."""
    b = Building(2, 2, height=STOREY + 10)
    d = b.d
    gx0, gy0 = b.ground(1, 1)
    gx1, gy1 = b.ground(62, 62)
    d.rectangle([gx0, gy0, gx1, gy1], SNOW_GROUND if look == "snow" else c("#9a9a88"))
    d.rectangle([gx0, gy0, gx1, gy1], outline=c("#6b5a44"))
    round_tank(b, look, 18, 18, 12, SETTLING)
    round_tank(b, look, 46, 18, 12, SETTLING)
    roof, wall = b.box(8, 40, 34, 56, STOREY + 2)
    brick(d, wall, c("#9a5a42"))
    windows(d, wall, 1, sill=TRIM, every=6, skip_door=True)
    door(d, wall)
    d.rectangle(wall, outline=OUTLINE)
    gable_ew(d, roof, SHINGLE[2], look)
    return b


def treatment_plant(look, v):
    """Long aeration tanks, two round clarifiers and a concrete works building on 3 by 2 tiles."""
    b = Building(3, 2, height=2 * STOREY + 6)
    d = b.d
    gx0, gy0 = b.ground(1, 1)
    gx1, gy1 = b.ground(94, 62)
    d.rectangle([gx0, gy0, gx1, gy1], SNOW_GROUND if look == "snow" else c("#a4a494"))
    d.rectangle([gx0, gy0, gx1, gy1], outline=c("#6b6f74"))
    for k in range(3):
        tx0, ty0 = b.ground(6, 6 + k * 12)
        d.rectangle([tx0, ty0, tx0 + 50, ty0 + 9], CONCRETE, OUTLINE)
        d.rectangle([tx0 + 2, ty0 + 2, tx0 + 48, ty0 + 7], c("#dfe7ee") if look == "snow" else AERATION[k % 3])
        if look != "snow":
            for xx in range(tx0 + 4, tx0 + 48, 5):
                d.point((xx, ty0 + 4), c("#c4cfb4"))
    round_tank(b, look, 74, 16, 11, SETTLING)
    round_tank(b, look, 74, 42, 11, SETTLING)
    roof, wall = b.box(6, 44, 46, 58, 2 * STOREY + 2)
    d.rectangle(wall, c("#c8c4b8"))
    windows(d, wall, 2, glass=c("#4a5866"), every=5, width=3)
    d.rectangle(wall, outline=OUTLINE)
    flat_roof(b.img, roof, look, random.Random(8400), [("vent", 6, 3), ("vent", 30, 4)], parapet=c("#d8d4c8"))
    return b


# Transit buildings: a tram depot, a bus garage and a subway station's entrance.

def tram_depot(look, v):
    """A brick car shed on 2 by 2 tiles, three arched doors to the south and rails out of each across the apron."""
    b = Building(2, 2, height=2 * STOREY + 8)
    d = b.d
    gx0, gy0 = b.ground(2, 44)
    gx1, gy1 = b.ground(61, 63)
    d.rectangle([gx0, gy0, gx1, gy1], c("#a8a49a") if look != "snow" else SNOW_GROUND)
    roof, wall = b.box(2, 6, 61, 42, 2 * STOREY + 2)
    brick(d, wall, c("#8e4a3a"))
    x0, y0, x1, y1 = wall
    for k in range(3):
        cx = x0 + 10 + k * 20
        d.rectangle([cx - 6, y1 - 10, cx + 6, y1], c("#2a2a30"))
        d.arc([cx - 6, y1 - 14, cx + 6, y1 - 6], 180, 360, STONE)
        # Rails out across the apron, and a car half out of the shed on some.
        for rx in (cx - 3, cx + 3):
            d.line([rx, y1 + 1, rx, gy1], TRAM_RAIL)
        if (k + v) % 3 != 1:
            ty = y1 + 2
            d.rectangle([cx - 4, ty + 1, cx + 5, ty + 15], (0, 0, 0, 60))
            d.rectangle([cx - 5, ty, cx + 4, ty + 14], SNOW_ROOF[0] if look == "snow" else [c("#e8dcc0"), c("#c0392b"), c("#2f6a4a")][(k + v) % 3], OUTLINE)
            d.line([cx - 4, ty + 2, cx - 4, ty + 12], c("#46586a"))
            d.line([cx + 3, ty + 2, cx + 3, ty + 12], c("#46586a"))
            d.line([cx - 1, ty + 4, cx - 1, ty - 2], c("#3a3a40"))
    d.rectangle([x0, y0 + 1, x1, y0 + 2], STONE)
    d.rectangle(wall, outline=OUTLINE)
    rx0, ry0, rx1, ry1 = roof
    # A long roof with a lantern of skylights down the ridge.
    d.rectangle(roof, SNOW_ROOF[0] if look == "snow" else c("#5f6570"))
    d.rectangle([rx0 + 4, (ry0 + ry1) // 2 - 2, rx1 - 4, (ry0 + ry1) // 2 + 2], SNOW_ROOF[1] if look == "snow" else SKYLIGHT)
    d.rectangle(roof, outline=OUTLINE)
    return b


def bus_garage(look, v):
    """A wide garage on 2 by 2 tiles with roller doors, a forecourt and a bus waiting on it."""
    b = Building(2, 2, height=2 * STOREY + 4)
    d = b.d
    gx0, gy0 = b.ground(2, 40)
    gx1, gy1 = b.ground(61, 63)
    d.rectangle([gx0, gy0, gx1, gy1], c("#9a9890") if look != "snow" else SNOW_GROUND)
    roof, wall = b.box(2, 6, 61, 38, 2 * STOREY)
    brick(d, wall, c("#a8583f")) if v == 0 else d.rectangle(wall, c("#c8c4b8"))
    x0, y0, x1, y1 = wall
    for k in range(4):
        dx = x0 + 4 + k * 14
        d.rectangle([dx, y1 - 8, dx + 10, y1], c("#7d8288"))
        for yy in range(y1 - 7, y1, 2):
            d.line([dx + 1, yy, dx + 9, yy], c("#6a6f75"))
    d.rectangle(wall, outline=OUTLINE)
    flat_roof(b.img, roof, look, random.Random(8500), [("vent", 8, 6), ("vent", 40, 6), ("skylight", 24, 14)], parapet=STONE)
    # Bays marked on the forecourt, and buses parked in some of them.
    livery = c("#2f7a5a") if v == 0 else c("#c0392b")
    rng = random.Random(8510 + v)
    for k in range(4):
        x = 4 + k * 14
        bx, by = b.ground(x, 42)
        d.line([bx, by, bx, by + 18], c("#e8e4da") if look != "snow" else c("#c8d0d6"))
        if rng.random() < 0.7:
            top_bus(d, bx + 3, by + 2, livery, look)
    return b


def top_bus(d, x, y, livery, look):
    """A bus seen from above, nose to the south: its shadow, the roof in the company's colour with a band of windows
    down each side and the air conditioning on top."""
    d.rectangle([x + 1, y + 1, x + 9, y + 17], (0, 0, 0, 60))
    d.rectangle([x, y, x + 8, y + 16], SNOW_ROOF[0] if look == "snow" else livery, OUTLINE)
    d.line([x + 1, y + 2, x + 1, y + 14], c("#d8e4ea"))
    d.line([x + 7, y + 2, x + 7, y + 14], c("#d8e4ea"))
    d.rectangle([x + 3, y + 5, x + 5, y + 8], c("#c8ccd0"))
    d.line([x + 2, y + 15, x + 6, y + 15], c("#f2e6a0"))


def subway_station(look, v):
    """A subway entrance: stairs down under a glass canopy, railings round, and the line's sign on a post."""
    b = Building(height=STOREY + 8)
    d = b.d
    gx0, gy0 = b.ground(1, 1)
    gx1, gy1 = b.ground(30, 30)
    d.rectangle([gx0, gy0, gx1, gy1], c("#b8b2a6") if look != "snow" else SNOW_GROUND)
    # The stairwell, getting darker as it goes down.
    sx0, sy0 = b.ground(8, 10)
    for k in range(6):
        shade_ = 160 - k * 22
        d.rectangle([sx0, sy0 + k * 2, sx0 + 15, sy0 + k * 2 + 1], (shade_, shade_ - 6, shade_ - 14, 255))
    d.rectangle([sx0 - 1, sy0 - 1, sx0 + 16, sy0 + 12], outline=c("#3a3c42"))
    # The canopy over it, on two posts.
    roof, wall = b.box(6, 6, 25, 9, STOREY + 2)
    d.rectangle(roof, SNOW_ROOF[0] if look == "snow" else c("#9fc3d8"), OUTLINE)
    d.line([wall[0] + 1, wall[1], wall[0] + 1, wall[3]], c("#3a3c42"))
    d.line([wall[2] - 1, wall[1], wall[2] - 1, wall[3]], c("#3a3c42"))
    # The sign: a blue plate with a white bar.
    px_, py_ = b.ground(26, 24)
    d.line([px_, py_, px_, py_ - 12], c("#5a5c60"))
    d.rectangle([px_ - 3, py_ - 16, px_ + 3, py_ - 11], c("#2f5f9a"), OUTLINE)
    d.line([px_ - 2, py_ - 14, px_ + 2, py_ - 14], c("#ffffff"))
    return b


# Power stations by era after coal: oil, gas, hydro and nuclear; then a
# substation, and the garbage service: a dump, an incinerator and a recycling depot.

def cylinder(b, look, cx, cy, r, h, col, top_col=None):
    """A round tank or tower standing at tile pixel cx, cy: its south face, then its top."""
    d = b.d
    gx, gy = b.ground(cx, cy)
    d.rectangle([gx - r, gy - h, gx + r, gy], col)
    d.line([gx - r + 1, gy - h, gx - r + 1, gy], shade(col, 1.15))
    d.line([gx + r - 2, gy - h, gx + r - 2, gy], shade(col, 0.8))
    d.ellipse([gx - r, gy - r // 2, gx + r, gy + r // 2], shade(col, 0.85))
    d.rectangle([gx - r, gy - h, gx + r, gy], outline=None)
    d.line([gx - r, gy - h, gx - r, gy], OUTLINE)
    d.line([gx + r, gy - h, gx + r, gy], OUTLINE)
    top = SNOW_ROOF[0] if look == "snow" else (top_col or shade(col, 1.1))
    d.ellipse([gx - r, gy - h - r // 2, gx + r, gy - h + r // 2], top, OUTLINE)
    b.casters.append((1, cx - r, cy - r // 2, cx + r + 1, cy + r // 2 + 1, h))


def plant_yard(b, look, x0, y0, x1, y1, col=c("#8e8a80")):
    d = b.d
    gx0, gy0 = b.ground(x0, y0)
    gx1, gy1 = b.ground(x1, y1)
    d.rectangle([gx0, gy0, gx1, gy1], SNOW_GROUND if look == "snow" else col)
    d.rectangle([gx0, gy0, gx1, gy1], outline=c("#5f5a52"))


def banded_chimney(b, look, x, y, height, col, band, plume=SOOT):
    """A tall stack with bands near the top."""
    chimney(b, x, y, height, col, look, plume)
    top = b.lift
    for k in (3, 8):
        b.d.rectangle([x - 2, y + top - height + k, x + 1, y + top - height + k + 1], band)


def transformer(b, look, x, y):
    d = b.d
    gx, gy = b.ground(x, y)
    d.rectangle([gx, gy - 6, gx + 6, gy], c("#5f666d"), OUTLINE)
    for xx in range(gx + 1, gx + 6, 2):
        d.line([xx, gy - 5, xx, gy - 1], c("#4a5056"))
    d.rectangle([gx + 1, gy - 8, gx + 5, gy - 6], SNOW if look == "snow" else c("#767d84"))
    for xx in (gx + 1, gx + 3, gx + 5):
        d.point((xx, gy - 9), INSULATOR)
    b.casters.append((1, x, y - 2, x + 7, y + 1, 8))


def oil_plant(look, v):
    """An oil-fired station on 2 by 2 tiles: a steel-clad hall, two round tanks of fuel oil, and one tall banded stack."""
    b = Building(2, 2, height=50)
    d = b.d
    plant_yard(b, look, 1, 1, 62, 62)
    cylinder(b, look, 14, 16, 10, 14, c("#c9c4b8"))
    cylinder(b, look, 38, 14, 9, 12, c("#c9c4b8"))
    roof, wall = b.box(4, 32, 44, 60, 2 * STOREY + 6)
    d.rectangle(wall, c("#9aa6ae"))
    for yy in range(wall[1] + 2, wall[3], 3):
        d.line([wall[0] + 1, yy, wall[2] - 1, yy], c("#8a959c"))
    windows(d, wall, 1, glass=c("#4a5866"), every=6, width=4, height=3)
    d.rectangle(wall, outline=OUTLINE)
    flat_roof(b.img, roof, look, random.Random(9100), [("vent", 6, 4), ("vent", 26, 4)], parapet=c("#7f8a90"))
    banded_chimney(b, look, 54, 40, 50, c("#b8b2a6"), c("#b04030"), SMOKE)
    return b


def gas_plant(look, v):
    """A gas turbine station on 2 by 2 tiles: a long pale hall, a bank of cooling fans and two slim stacks."""
    b = Building(2, 2, height=40)
    d = b.d
    plant_yard(b, look, 1, 1, 62, 62, c("#a29e94"))
    roof, wall = b.box(4, 4, 40, 22, STOREY)
    d.rectangle(wall, c("#b8bcc0"), OUTLINE)
    x0, y0, x1, y1 = roof
    d.rectangle(roof, SNOW_ROOF[0] if look == "snow" else c("#9ea4aa"), OUTLINE)
    for k in range(4):
        fx = x0 + 3 + k * 9
        d.ellipse([fx, y0 + 3, fx + 6, y1 - 3], c("#3a3e44"))
        d.line([fx + 3, y0 + 4, fx + 3, y1 - 4], c("#6a7076"))
    roof, wall = b.box(4, 30, 52, 58, 2 * STOREY + 8)
    d.rectangle(wall, c("#e2ddd0"))
    d.rectangle([wall[0], wall[3] - 3, wall[2], wall[3]], c("#4f7a9a"))
    d.rectangle(wall, outline=OUTLINE)
    flat_roof(b.img, roof, look, random.Random(9200), [("vent", 8, 6), ("vent", 30, 6)], parapet=c("#c8c2b4"))
    for x in (48, 57):
        chimney(b, x, 22, 40, c("#c4c8cc"), look, STEAM)
    transformer(b, look, 54, 60)
    return b


def hydro_plant(look, v):
    """A hydro station on 2 by 2 tiles: a concrete powerhouse with tall windows, the penstocks running into it, and its transformers."""
    b = Building(2, 2, height=3 * STOREY + 6)
    d = b.d
    plant_yard(b, look, 1, 1, 62, 62, c("#a8a49a"))
    # The penstocks, from the water down to the turbines.
    for x in (14, 30, 46):
        gx, gy = b.ground(x, 2)
        d.rectangle([gx - 3, gy, gx + 3, gy + 16], c("#5a6066"), OUTLINE)
        d.line([gx - 1, gy + 1, gx - 1, gy + 15], c("#7d848a"))
    roof, wall = b.box(4, 18, 60, 48, 3 * STOREY)
    d.rectangle(wall, c("#c4beb0"))
    x0, y0, x1, y1 = wall
    for k in range(6):
        wx = x0 + 4 + k * 9
        d.rectangle([wx, y0 + 3, wx + 4, y1 - 3], c("#4a5866"))
        d.arc([wx, y0 + 1, wx + 4, y0 + 5], 180, 360, c("#4a5866"))
    d.rectangle(wall, outline=OUTLINE)
    flat_roof(b.img, roof, look, random.Random(9300), [("vent", 10, 8), ("vent", 40, 8)], parapet=c("#b4ae9f"))
    transformer(b, look, 10, 58)
    transformer(b, look, 22, 58)
    return b


def cooling_tower(b, look, cx, cy, r, h):
    """A hyperbolic cooling tower: wide at the foot, waisted, flaring at the lip, with steam rising from it."""
    d = b.d
    gx, gy = b.ground(cx, cy)
    col = c("#c8c4bc") if look != "snow" else c("#d8dde2")
    left, right = [], []
    for k in range(h + 1):
        t = k / h
        w = r * (1 - 0.3 * math.sin(math.pi * min(1, t * 1.25)))
        left.append((gx - w, gy - k))
        right.append((gx + w, gy - k))
    d.polygon(left + right[::-1], col)
    d.line(left, OUTLINE)
    d.line(right, OUTLINE)
    for k in range(0, h, 2):
        x, y = right[k]
        d.point((x - 2, y), shade(col, 0.8))
    lip = r * (1 - 0.3 * math.sin(math.pi * 1.0))
    lip = r * 0.82
    d.ellipse([gx - lip, gy - h - lip / 2, gx + lip, gy - h + lip / 2], shade(col, 1.05), OUTLINE)
    d.ellipse([gx - lip + 3, gy - h - lip / 2 + 2, gx + lip - 3, gy - h + lip / 2 - 2], c("#4a4c50"))
    b.plume(CLOUD, cx, cy - h)
    b.casters.append((1, cx - r, cy - r // 2, cx + r + 1, cy + r // 2 + 1, h))


def nuclear_plant(look, v):
    """A nuclear station on 3 by 3 tiles: two cooling towers, the reactor's dome and a long turbine hall."""
    b = Building(3, 3, height=60)
    d = b.d
    plant_yard(b, look, 1, 1, 94, 94, c("#a6a49c"))
    gx0, gy0 = b.ground(1, 1)
    gx1, gy1 = b.ground(94, 94)
    d.rectangle([gx0, gy0, gx1, gy1], outline=c("#d0ccc0"))
    cooling_tower(b, look, 22, 34, 17, 56)
    cooling_tower(b, look, 60, 30, 17, 56)
    # The containment building: a cylinder under a dome.
    gx, gy = b.ground(80, 70)
    col = c("#d8d4c8")
    d.rectangle([gx - 11, gy - 24, gx + 11, gy], col)
    d.line([gx + 9, gy - 24, gx + 9, gy], shade(col, 0.8))
    d.line([gx - 11, gy - 24, gx - 11, gy], OUTLINE)
    d.line([gx + 11, gy - 24, gx + 11, gy], OUTLINE)
    d.pieslice([gx - 11, gy - 35, gx + 11, gy - 13], 180, 360, SNOW_ROOF[0] if look == "snow" else c("#e6e2d8"), OUTLINE)
    b.casters.append((1, 69, 64, 92, 76, 30))
    roof, wall = b.box(6, 64, 66, 90, 3 * STOREY)
    d.rectangle(wall, c("#b8bec4"))
    for yy in range(wall[1] + 2, wall[3], 3):
        d.line([wall[0] + 1, yy, wall[2] - 1, yy], c("#a8aeb4"))
    d.rectangle([wall[0], wall[1], wall[2], wall[1] + 2], c("#4f7a9a"))
    d.rectangle(wall, outline=OUTLINE)
    flat_roof(b.img, roof, look, random.Random(9400), [("vent", 8, 6), ("vent", 30, 6), ("vent", 50, 6)], parapet=c("#a8aeb4"))
    return b


def big_transformer(b, look, x, y):
    """A substation transformer, with its foot on tile pixels x, y: a tank with cooling fins down each side and three
    bushings standing up off its lid."""
    d = b.d
    gx, gy = b.ground(x, y)
    tank = c("#7f8a84")
    d.rectangle([gx, gy - 7, gx + 8, gy], tank, OUTLINE)
    for xx in range(gx + 2, gx + 7, 2):
        d.line([xx, gy - 6, xx, gy - 1], shade(tank, 0.75))
    d.rectangle([gx - 2, gy - 6, gx - 1, gy - 1], shade(tank, 0.85), OUTLINE)
    d.rectangle([gx + 9, gy - 6, gx + 10, gy - 1], shade(tank, 0.85), OUTLINE)
    d.rectangle([gx, gy - 10, gx + 8, gy - 8], SNOW if look == "snow" else shade(tank, 1.15), OUTLINE)
    for xx in (gx + 2, gx + 4, gx + 6):
        d.line([xx, gy - 14, xx, gy - 11], c("#8a5a3a"))
        d.point((xx, gy - 15), INSULATOR)
    b.casters.append((1, x - 2, y - 3, x + 11, y + 1, 12))


def substation(look, v):
    """A substation: a fenced yard of gravel with two transformers wired up to a steel gantry the lines come in on, and a
    warning sign on the fence; or a plain brick substation house with a transformer beside it."""
    b = Building(height=22)
    d = b.d
    gx0, gy0 = b.ground(1, 1)
    gx1, gy1 = b.ground(30, 30)
    rng = random.Random(9500 + v)
    noise_fill(b.img, (gx0, gy0, gx1 + 1, gy1 + 1), [c("#b4b0a6"), c("#a6a298"), c("#c2beb4")] if look != "snow"
               else [c("#e4ebf0"), c("#d5dfe6"), c("#f2f6f9")], rng)
    d.rectangle([gx0, gy0, gx1, gy1], outline=c("#4a4e52"))
    for xx in range(gx0, gx1 + 1, 3):
        d.point((xx, gy1 - 1), c("#6a6e72"))
    sx, sy = b.ground(14, 30)
    d.rectangle([sx, sy - 3, sx + 3, sy], c("#f2c94c"), OUTLINE)
    wire = c("#2a2c30")
    if v % 2 == 0:
        # The gantry, with the lines coming in over the north fence.
        hx0, hy = b.ground(4, 8)
        hx1, _ = b.ground(27, 8)
        for x in (hx0, hx1):
            d.line([x, hy, x, hy - 18], STEEL_LEG)
        d.line([hx0, hy - 18, hx1, hy - 18], STEEL_LEG)
        d.line([hx0, hy - 17, hx1, hy - 17], STEEL_LEG)
        b.casters.append((1, 3, 7, 29, 9, 18))
        for x in (8, 15, 22):
            gx, _ = b.ground(x, 8)
            d.point((gx, hy - 16), INSULATOR)
            d.line([gx, hy - 19, gx, b.lift - 2], wire)
        big_transformer(b, look, 4, 25)
        big_transformer(b, look, 18, 25)
        for (fx, tx) in ((8, 6), (15, 10), (22, 24)):
            ax, _ = b.ground(fx, 8)
            bx, by = b.ground(tx, 25)
            d.line([ax, hy - 16, bx, by - 15], wire)
    else:
        roof, wall = b.box(3, 5, 17, 17, STOREY + 2)
        brick(d, wall, c("#9a5a42"))
        d.rectangle([wall[0] + 4, wall[3] - 5, wall[0] + 9, wall[3]], c("#3a4048"))
        d.rectangle(wall, outline=OUTLINE)
        flat_roof(b.img, roof, look, random.Random(9510), [], parapet=STONE)
        big_transformer(b, look, 19, 22)
        ax, ay = b.ground(23, 1)
        d.line([ax, ay - 2, ax, ay + 7], wire)
        d.line([ax, ay + 7, ax - 1, ay + 7], wire)
    return b


GARBAGE = [c("#8a7a64"), c("#6f6656"), c("#9a8c74"), c("#7a705e")]
LITTER = [c("#d8d4ca"), c("#c8c4b8"), c("#5a6a80"), c("#8a5a4a"), c("#4a4c50"), c("#202226")]


def dump(look, v):
    """A garbage dump on 3 by 3 tiles: a fence, mounds of refuse, a bulldozer working one and a shed by the gate."""
    b = Building(3, 3, height=16)
    d = b.d
    rng = random.Random(9600 + v)
    gx0, gy0 = b.ground(1, 1)
    gx1, gy1 = b.ground(94, 94)
    noise_fill(b.img, (gx0, gy0, gx1 + 1, gy1 + 1), [c("#7d7260"), c("#6e6454"), c("#8a7e6a")] if look != "snow"
               else [c("#dfe5ea"), c("#cfd7de"), c("#eef2f5")], rng)
    for xx in range(gx0, gx1 + 1, 4):
        d.line([xx, gy0, xx, gy0 + 2], c("#5a5048"))
        d.line([xx, gy1 - 2, xx, gy1], c("#5a5048"))
    d.rectangle([gx0, gy0, gx1, gy1], outline=c("#6b6258"))
    # The mounds, back to front: one way round or the other.
    mounds = [(28, 26, 20), (66, 30, 22), (40, 58, 18), (74, 66, 14)] if v == 0 else [(46, 22, 24), (22, 52, 16), (64, 56, 20), (40, 76, 12)]
    for mx, my, r in mounds:
        gx, gy = b.ground(mx, my)
        for k, col in enumerate(GARBAGE):
            w = r - k * 4
            if w <= 2:
                break
            d.ellipse([gx - w, gy - w // 2 - k * 3, gx + w, gy + w // 2 - k * 3], SNOW_ROOF[min(2, k)] if look == "snow" else col)
        for _ in range(r * 3):
            a, dist = rng.random() * math.tau, rng.random() * r * 0.9
            x = int(gx + math.cos(a) * dist)
            y = int(gy + math.sin(a) * dist * 0.5 - (1 - dist / r) * 8)
            if look != "snow" or rng.random() < 0.25:
                d.point((x, y), rng.choice(LITTER))
        b.casters.append((1, mx - r // 2, my - r // 4, mx + r // 2, my + r // 4, 8))
    # A bulldozer on the front mound.
    bx, by = b.ground(52, 80) if v == 0 else b.ground(56, 64)
    d.rectangle([bx, by - 5, bx + 9, by], c("#d8a030"), OUTLINE)
    d.rectangle([bx + 2, by - 9, bx + 6, by - 5], c("#c89020"), OUTLINE)
    d.line([bx - 2, by - 4, bx - 2, by], c("#3a3c40"))
    if v == 1:
        # A garbage truck in at the gate.
        tx, ty = b.ground(20, 86)
        d.rectangle([tx, ty - 6, tx + 12, ty], c("#e0e0d8"), OUTLINE)
        d.rectangle([tx + 12, ty - 5, tx + 16, ty], c("#3a7a4a"), OUTLINE)
        d.rectangle([tx + 13, ty - 4, tx + 15, ty - 2], GLASS_DARK)
    # The shed by the gate.
    roof, wall = b.box(8, 78, 26, 92, STOREY + 2) if v == 0 else b.box(68, 80, 86, 92, STOREY + 2)
    siding(d, wall, c("#8a8478"))
    door(d, wall)
    d.rectangle(wall, outline=OUTLINE)
    gable_ew(d, roof, c("#5b5f6b"), look)
    return b


def incinerator(look, v):
    """An incinerator on 2 by 2 tiles: the tipping hall the trucks back into, the furnace house and a tall concrete stack."""
    b = Building(2, 2, height=54)
    d = b.d
    plant_yard(b, look, 1, 1, 62, 62, c("#98948a"))
    roof, wall = b.box(4, 6, 46, 30, 4 * STOREY)
    d.rectangle(wall, c("#a8a296"))
    windows(d, wall, 2, glass=c("#4a5866"), every=7, width=3, height=3)
    d.rectangle(wall, outline=OUTLINE)
    flat_roof(b.img, roof, look, random.Random(9700), [("vent", 8, 6), ("vent", 28, 8)], parapet=c("#948e82"))
    roof, wall = b.box(4, 34, 40, 56, 2 * STOREY + 2)
    d.rectangle(wall, c("#8a7a62"))
    x0, y0, x1, y1 = wall
    for k in range(3):
        dx = x0 + 3 + k * 12
        d.rectangle([dx, y1 - 9, dx + 8, y1], c("#4a4c50"))
    d.rectangle(wall, outline=OUTLINE)
    flat_roof(b.img, roof, look, random.Random(9701), [("vent", 14, 6)], parapet=c("#7a6c56"))
    banded_chimney(b, look, 54, 36, 54, c("#b4b0a8"), c("#8a8680"), SMOKE)
    # A garbage truck at the hall.
    tx, ty = b.ground(42, 54)
    d.rectangle([tx, ty - 6, tx + 14, ty], c("#e8e4da"), OUTLINE)
    d.rectangle([tx + 10, ty - 8, tx + 14, ty - 4], c("#3f6fa8"), OUTLINE)
    return b


def recycling(look, v):
    """A recycling depot on 2 by 2 tiles: a green steel shed, bales stacked by kind and three skips."""
    b = Building(2, 2, height=2 * STOREY + 6)
    d = b.d
    plant_yard(b, look, 1, 1, 62, 62, c("#9c988e"))
    roof, wall = b.box(4, 4, 60, 30, 2 * STOREY + 2)
    d.rectangle(wall, c("#4a7a5a"))
    for xx in range(wall[0] + 2, wall[2], 3):
        d.line([xx, wall[1] + 1, xx, wall[3] - 1], c("#3f6a4e"))
    for k in range(3):
        dx = wall[0] + 6 + k * 18
        d.rectangle([dx, wall[3] - 8, dx + 10, wall[3]], c("#6a7076"))
    d.rectangle(wall, outline=OUTLINE)
    x0, y0, x1, y1 = roof
    d.rectangle(roof, SNOW_ROOF[0] if look == "snow" else c("#6a8a72"), OUTLINE)
    # Bales: paper, cans, bottles.
    for row, col in enumerate([c("#8aa4c8"), c("#b8bcc0"), c("#5a9a6a")]):
        for k in range(4):
            bx, by = b.ground(6 + k * 8, 38 + row * 7)
            d.rectangle([bx, by - 4, bx + 6, by + 1], SNOW_ROOF[1] if look == "snow" and row == 0 else col, OUTLINE)
    for k, col in enumerate([c("#3f6fa8"), c("#d8b84a"), c("#4a8a5a")]):
        sx, sy = b.ground(44, 38 + k * 8)
        d.polygon([(sx, sy - 4), (sx + 14, sy - 4), (sx + 12, sy + 2), (sx + 2, sy + 2)], col, OUTLINE)
    return b


# Farmland: farms, woodlots and mines, by what's under the lot.

FIELD = {
    "spring": [c("#7a5a3a"), c("#6fa848")],
    "summer": [c("#c9a84a"), c("#dcbc5a")],
    "dry": [c("#c9a84a"), c("#dcbc5a")],
    "autumn": [c("#9a8048"), c("#b89a58")],
    "bare": [c("#7a5a3e"), c("#6a4c32")],
    "snow": [c("#e9eff3"), c("#cfd9e1")],
}


def farm(look, v):
    """A farm on 2 by 2 tiles: fields in rows, the way they run and the crop by the season, split by lanes, with the farmhouse
    and barn: three fields and the yard in a corner, the yard along the top, four fields round a yard with a silo, or one great
    field with the house behind a windbreak and a windmill."""
    b = Building(2, 2, height=3 * STOREY + 6)
    d = b.d
    ground, crop = FIELD[look]
    hedge = c("#4f7a34") if look in ("spring", "summer", "dry") else c("#6b5a44")
    k = v % 4
    if k == 0:
        fields = [((1, 1, 40, 30), True), ((1, 33, 40, 62), False), ((43, 33, 62, 62), True)]
    elif k == 1:
        fields = [((1, 1, 62, 22), False), ((1, 25, 30, 62), True), ((33, 25, 62, 62), False)]
    elif k == 2:
        fields = [((1, 1, 26, 26), False), ((37, 1, 62, 26), True), ((1, 37, 26, 62), True), ((37, 37, 62, 62), False)]
    else:
        fields = [((1, 18, 62, 62), True), ((24, 1, 62, 15), False)]
    for (x0, y0, x1, y1), across in fields:
        fx0, fy0 = b.ground(x0, y0)
        fx1, fy1 = b.ground(x1, y1)
        d.rectangle([fx0, fy0, fx1, fy1], ground)
        if across:
            for yy in range(fy0 + 2, fy1, 3):
                d.line([fx0 + 1, yy, fx1 - 1, yy], crop)
        else:
            for xx in range(fx0 + 2, fx1, 3):
                d.line([xx, fy0 + 1, xx, fy1 - 1], crop)
        d.rectangle([fx0, fy0, fx1, fy1], outline=hedge)
        if look == "autumn":
            rng = random.Random(9990 + v + x0)
            for _ in range(3):
                hx, hy = rng.randrange(fx0 + 3, fx1 - 3), rng.randrange(fy0 + 3, fy1 - 3)
                d.ellipse([hx - 2, hy - 2, hx + 2, hy + 1], c("#d8b860"), OUTLINE)

    def house_at(x0, y0):
        roof, wall = b.box(x0, y0, x0 + 9, y0 + 8, STOREY + 2)
        siding(d, wall, SIDING[v % 4])
        windows(d, wall, 1, every=4)
        d.rectangle(wall, outline=OUTLINE)
        gable_ew(d, roof, SHINGLE[0], look)

    def barn_at(x0, y0, w=10, h=11):
        roof, wall = b.box(x0, y0, x0 + w, y0 + h, STOREY + 4)
        d.rectangle(wall, c("#9a3a2e") if v % 3 else c("#7a6a5a"))
        d.rectangle([wall[0] + 3, wall[3] - 5, wall[0] + 7, wall[3]], c("#5a2a20"))
        d.rectangle(wall, outline=OUTLINE)
        gable_ew(d, roof, SHINGLE[1], look)

    def yard(x0, y0, x1, y1):
        gx, gy = b.ground(x0, y0)
        gx1, gy1 = b.ground(x1, y1)
        d.rectangle([gx, gy, gx1, gy1], SNOW_GROUND if look == "snow" else c("#9a8a6a"))

    if k == 0:
        yard(43, 1, 62, 30)
        house_at(44, 4)
        barn_at(52, 16)
    elif k == 1:
        house_at(4, 2)
        barn_at(18, 3, 12, 11)
    elif k == 2:
        # The yard where the lanes cross, the silo by the barn.
        yard(27, 27, 36, 36)
        lane = c("#a89878") if look != "snow" else c("#dfe5ea")
        gx, gy = b.ground(31, 1)
        d.rectangle([gx - 2, gy, gx + 2, gy + 61], lane)
        gx, gy = b.ground(1, 31)
        d.rectangle([gx, gy - 2, gx + 61, gy + 2], lane)
        house_at(27, 28)
        silo(b, look, 38, 28, 6, 3 * STOREY)
    else:
        yard(1, 1, 22, 16)
        # The windbreak along the north edge, behind the house.
        rng = random.Random(9970 + v)
        for x in range(2, 22, 4):
            tree_at(b, look, v, x, 2, 3, rng, conifer_tree=True)
        house_at(3, 6)
        barn_at(13, 5, 8, 8)
        # The windmill that pumps the water: a lattice tower and its wheel.
        gx, gy = b.ground(52, 8)
        d.line([gx - 2, gy, gx, gy - 16], c("#6a6a6e"))
        d.line([gx + 2, gy, gx, gy - 16], c("#6a6a6e"))
        d.ellipse([gx - 4, gy - 20, gx + 4, gy - 12], outline=c("#8a8a8e"))
        d.line([gx - 4, gy - 16, gx + 4, gy - 16], c("#8a8a8e"))
        d.line([gx, gy - 20, gx, gy - 12], c("#8a8a8e"))
        b.casters.append((1, 51, 7, 54, 9, 18))
    return b

def woodlot(look, v):
    """A stand of trees with a clearing, stumps and a pile of logs."""
    b = Building(height=LIFT)
    rng = random.Random(9900 + v)
    d = b.d
    for x, foot in ((8, 14), (22, 12), (26, 24)):
        if (x + v) % 3 == 0:
            conifer(b.img, look, x, foot + b.lift, 16, rng)
        else:
            deciduous(b.img, look, v, x, foot + b.lift, 6, rng)
    # Logs stacked and a stump or two.
    lx, ly = b.ground(5, 27)
    for k in range(3):
        d.ellipse([lx + k * 4, ly - 2, lx + k * 4 + 3, ly + 1], c("#8a6a44"), c("#5a4030"))
    d.line([lx, ly - 3, lx + 12, ly - 3], c("#6b4a30"))
    for x, y in ((16, 22), (12, 16)):
        sx, sy = b.ground(x, y)
        d.ellipse([sx - 1, sy - 1, sx + 1, sy + 1], c("#c8a878"), c("#6b4a30"))
    b.casters.append((1, 4, 24, 18, 28, 3))
    return b


def headframe(b, look, x, y, height):
    """A mine's headframe: two legs, a brace and the winding wheel on top."""
    d = b.d
    gx, gy = b.ground(x, y)
    top = gy - height
    d.line([gx - 6, gy, gx - 1, top], STEEL_LEG, 2)
    d.line([gx + 6, gy, gx + 1, top], shade(STEEL_LEG, 0.8), 2)
    d.line([gx + 6, gy, gx + 12, gy - height // 2], STEEL_LEG)
    for k in range(4, height, 6):
        w = 6 - 5 * k // height
        d.line([gx - w, gy - k, gx + w, gy - k], STEEL_LEG)
    d.ellipse([gx - 4, top - 4, gx + 4, top + 4], outline=c("#3a3c40"), width=2)
    d.point((gx, top), c("#3a3c40"))
    b.casters.append((1, x - 6, y - 1, x + 7, y + 1, height))


def spoil(b, look, cx, cy, w, cols):
    d = b.d
    gx, gy = b.ground(cx, cy)
    for k, col in enumerate(cols):
        ww = w - k * 4
        if ww <= 2:
            break
        d.ellipse([gx - ww, gy - ww // 2 - k * 3, gx + ww, gy + ww // 2 - k * 3], SNOW_ROOF[min(2, k)] if look == "snow" else col)
    b.casters.append((1, cx - w // 2, cy - w // 4, cx + w // 2, cy + w // 4, len(cols) * 3))


def pit(look, v, spoil_cols, wagon_col):
    """A mine on 2 by 2 tiles: headframe, engine house, a spoil heap and wagons on a track. The yard lies one of three ways, the
    third with a washery and a conveyor up to its heap."""
    b = Building(2, 2, height=40)
    d = b.d
    rng = random.Random(9950 + v)
    gx0, gy0 = b.ground(1, 1)
    gx1, gy1 = b.ground(62, 62)
    noise_fill(b.img, (gx0, gy0, gx1 + 1, gy1 + 1), [c("#7d7260"), c("#6e6454"), c("#8a7e6a")] if look != "snow"
               else [c("#dfe5ea"), c("#cfd7de"), c("#eef2f5")], rng)
    d.rectangle([gx0, gy0, gx1, gy1], outline=c("#5a5048"))
    k = v % 3

    def engine_house(x0, y0):
        roof, wall = b.box(x0, y0, x0 + 22, y0 + 16, 2 * STOREY)
        brick(d, wall, c("#8a4a38") if k != 1 else c("#9a6a4a"))
        windows(d, wall, 2, every=6)
        d.rectangle(wall, outline=OUTLINE)
        gable_ew(d, roof, SHINGLE[2], look)
        chimney(b, x0 + 4, y0, 32, look=look)

    def track(y, x0=4, length=54):
        tx0, ty = b.ground(x0, y)
        d.line([tx0, ty, tx0 + length, ty], c("#5a5048"))
        d.line([tx0, ty + 3, tx0 + length, ty + 3], c("#5a5048"))
        for n in range(2):
            wx = tx0 + length // 2 + n * 12
            d.rectangle([wx, ty - 4, wx + 9, ty + 2], wagon_col, OUTLINE)

    if k == 0:
        spoil(b, look, 44, 18, 18, spoil_cols)
        engine_house(4, 30)
        headframe(b, look, 36, 40, 34)
        track(56)
    elif k == 1:
        # The other way about: the heap to the west, the house to the east, the track along the top.
        spoil(b, look, 18, 40, 18, spoil_cols)
        engine_house(36, 30)
        headframe(b, look, 28, 22, 34)
        track(6)
    else:
        spoil(b, look, 48, 46, 16, spoil_cols)
        engine_house(4, 6)
        headframe(b, look, 34, 16, 30)
        # The washery, tall and plain, and the conveyor running up to the heap.
        roof, wall = b.box(8, 34, 26, 48, 3 * STOREY)
        siding(d, wall, c("#8a8478"))
        windows(d, wall, 3, every=6)
        d.rectangle(wall, outline=OUTLINE)
        gable_ew(d, roof, IRON_ROOF, look)
        sx, sy = b.ground(26, 40)
        ex, ey = b.ground(44, 44)
        d.line([sx, sy - 14, ex, ey - 4], c("#4a4a4e"), 2)
        track(58, 4, 34)
    return b

def mine(look, v):
    """An iron mine on 2 by 2 tiles: headframe, engine house, a rust-red spoil heap and ore wagons."""
    return pit(look, v, [c("#7a4030"), c("#8a4a36"), c("#9a5a40"), c("#a86a4a")], c("#8a4a36"))


def colliery(look, v):
    """A coal mine on 2 by 2 tiles: headframe, engine house, a black spoil heap and coal wagons."""
    return pit(look, v, [c("#1e1e22"), c("#2a2a2f"), c("#35353b"), c("#42424a")], c("#2a2a2f"))


def oil_well(look, v):
    """An oil well on one tile, the black of spilt crude round its foot: a timber derrick over the hole with a shed and a tank;
    a nodding pump with its tanks; or a steel derrick beside a pair of tanks."""
    b = Building(height=30)
    d = b.d
    gx, gy = b.ground(15, 22)
    d.ellipse([gx - 10, gy - 4, gx + 10, gy + 5], c("#2a2622") if look != "snow" else c("#6a6a70"))
    k = v % 3
    if k == 1:
        # The pump: a beam on a frame, a horse's head at one end, the weight at the other.
        d.line([gx - 6, gy, gx - 2, gy - 9], c("#4a4c50"), 2)
        d.line([gx + 2, gy, gx - 2, gy - 9], c("#4a4c50"), 2)
        d.line([gx - 10, gy - 7, gx + 8, gy - 11], c("#c0392b"), 2)
        d.polygon([(gx - 12, gy - 9), (gx - 9, gy - 9), (gx - 9, gy - 3), (gx - 12, gy - 5)], c("#c0392b"), OUTLINE)
        d.rectangle([gx + 6, gy - 13, gx + 10, gy - 8], c("#3a3a3e"))
        d.line([gx - 11, gy - 3, gx - 11, gy + 2], c("#3a3a3e"))
        b.casters.append((1, 4, 18, 26, 23, 12))
        cylinder(b, look, 24, 26, 3, 6, c("#8a8c90"))
        cylinder(b, look, 26, 18, 3, 6, c("#8a8c90"))
        return b
    top = gy - 28
    timber = c("#8a6a44") if k == 0 else c("#5a5e64")
    d.line([gx - 7, gy, gx - 1, top], timber, 2)
    d.line([gx + 7, gy, gx + 1, top], shade(timber, 0.75), 2)
    for n in range(4, 26, 5):
        w = 7 - 6 * n // 28
        d.line([gx - w, gy - n, gx + w, gy - n], timber)
        if k == 2:
            d.line([gx - w, gy - n, gx + w - 1, gy - n - 4], shade(timber, 0.85))
    d.rectangle([gx - 2, top - 2, gx + 2, top], c("#5a4030"))
    if look == "snow":
        d.line([gx - 2, top - 3, gx + 2, top - 3], SNOW)
    if k == 0:
        roof, wall = b.box(20, 20, 29, 27, STOREY)
        siding(d, wall, c("#8a8478"))
        d.rectangle(wall, outline=OUTLINE)
        gable_ew(d, roof, IRON_ROOF, look)
        cylinder(b, look, 6, 28, 3, 6, c("#4a4c50"))
    else:
        cylinder(b, look, 24, 12, 4, 8, c("#d8d4c8"))
        cylinder(b, look, 25, 25, 4, 8, c("#d8d4c8"))
    b.casters.append((1, 8, 21, 23, 23, 28))
    return b

def junction(look, kind):
    """What a crossing has on it, drawn over the road: stop signs, lights, a roundabout's island, or an overpass."""
    img = Image.new("RGBA", (T, T), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    white = c("#f2f2ea") if look != "snow" else c("#c8d0d8")
    if kind == "stop":
        for x, y in ((4, 4), (27, 4), (4, 27), (27, 27)):
            d.line([x, y, x, y + 3], c("#5a5c60"))
            d.regular_polygon((x, y - 1, 2), 8, fill=c("#c0392b"))
        for k in (6, 25):
            d.line([k, 9, k, 22], white)
            d.line([9, k, 22, k], white)
    elif kind == "lights":
        for x, y, lit in ((5, 5, "#3fbf5a"), (26, 5, "#d84343"), (5, 26, "#d84343"), (26, 26, "#3fbf5a")):
            d.line([x, y, x, y + 4], c("#3a3c40"))
            d.rectangle([x - 1, y - 3, x + 1, y + 1], c("#2a2a2f"))
            d.point((x, y - 2), c(lit))
        for k in range(8, 24, 3):
            d.line([k, 3, k + 1, 3], white)
            d.line([k, 28, k + 1, 28], white)
            d.line([3, k, 3, k + 1], white)
            d.line([28, k, 28, k + 1], white)
    else:
        # An overpass carrying the road east to west over the one below, its shadow under it.
        d.rectangle([0, 19, 31, 22], (0, 0, 0, 70))
        d.rectangle([0, 9, 31, 18], c("#8a8780"))
        d.line([0, 9, 31, 9], c("#c8c4b8"))
        d.line([0, 18, 31, 18], c("#c8c4b8"))
        for x in range(2, 31, 4):
            d.line([x, 13, x + 1, 13], c("#e8e4c0"))
    return img


def roundabout(look, mask):
    """A roundabout over the crossing: the ring road with its lane line, an
    island in the middle, and each road that joins it ([mask]: north 1, east
    2, south 4, west 8) running its lane line in to the ring past a little
    splitter island, so the lanes lead in and out."""
    img = Image.new("RGBA", (T, T), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    white = c("#f2f2ea") if look != "snow" else c("#c8d0d8")
    cx = cy = 15.5
    asphalt = MACADAM[look][0]
    d.ellipse([cx - 15, cy - 15, cx + 15, cy + 15], asphalt)
    # The ring's lane line, dashed.
    for a in range(0, 360, 20):
        for k in range(0, 9, 3):
            t = math.radians(a + k)
            d.point((cx + 10 * math.cos(t), cy + 10 * math.sin(t)), white)
    # Each road joining: its centre line in to the ring, and a splitter island at the mouth.
    arms = [(1, 0, -1), (2, 1, 0), (4, 0, 1), (8, -1, 0)]
    for bit, dx, dy in arms:
        if not mask & bit:
            continue
        for r in range(11, 16):
            if r % 3 == 2:
                continue
            d.point((cx + dx * r, cy + dy * r), white)
            d.point((cx + dx * r + dy, cy + dy * r + dx), white)
        # The splitter: a small kerbed triangle pointing out along the road.
        tip = (cx + dx * 15, cy + dy * 15)
        base_l = (cx + dx * 11 - dy * 2, cy + dy * 11 - dx * 2)
        base_r = (cx + dx * 11 + dy * 3, cy + dy * 11 + dx * 3)
        d.polygon([tip, base_l, base_r], c("#d8d4c8"))
    island = c("#5a9a3c") if look != "snow" else c("#e4ebf0")
    d.ellipse([cx - 6, cy - 6, cx + 6, cy + 6], island, c("#d8d4c8"))
    if look in ("spring", "summer", "autumn", "dry"):
        d.ellipse([cx - 3, cy - 3, cx + 3, cy + 3], c("#3d8233") if look != "autumn" else c("#cf6e28"))
    return img


def seam(look, kind):
    """Stones showing through the grass where there's a seam underneath: rusty for ore, black for coal."""
    img = Image.new("RGBA", (T, T), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    rng = random.Random({"ore": 9970, "coal": 9980, "oil": 9990}[kind])
    if kind == "oil":
        # Dark seeps where the oil comes up.
        for _ in range(2 if look == "snow" else 4):
            x, y = rng.randrange(4, 27), rng.randrange(4, 27)
            r = rng.choice((2, 3))
            d.ellipse([x - r, y - r // 2 - 1, x + r, y + r // 2 + 1], (24, 22, 26, 200))
            d.point((x - 1, y - 1), (90, 80, 110, 220))
        return img
    cols = [c("#8a4a36"), c("#a05a40"), c("#6a3a2a")] if kind == "ore" else [c("#2a2a2f"), c("#3a3a40"), c("#1e1e22")]
    for _ in range(4 if look == "snow" else 9):
        x, y = rng.randrange(2, 29), rng.randrange(2, 29)
        r = rng.choice((1, 1, 2))
        d.ellipse([x - r, y - r, x + r, y + r // 2 + 1], rng.choice(cols))
    return img


# Offices, on their own zone: rooms over a shop, an office building, a tower and a glass tower.

def offices(look, v):
    """Two or three storeys of brick with a shop front below and offices above, a brass plate by the door."""
    storeys = 2 + v % 2
    col = [BRICKS[1], c("#b8a888"), BRICKS[3]][v % 3]
    b = Building(height=storeys * STOREY + 6)
    d = b.d
    roof, wall = b.box(3, 6, 28, 27, storeys * STOREY + 2)
    brick(d, wall, col) if v != 1 else d.rectangle(wall, col)
    wx0, wy0, wx1, wy1 = wall
    windows(d, (wx0, wy0, wx1, wy1 - STOREY), storeys - 1, every=4, sill=TRIM)
    d.rectangle([wx0 + 1, wy1 - 5, wx1 - 1, wy1 - 1], c("#4a5866"))
    d.rectangle([wx0 + 12, wy1 - 6, wx0 + 16, wy1], DOOR)
    d.point((wx0 + 17, wy1 - 3), c("#d8b84a"))
    d.rectangle(wall, outline=OUTLINE)
    if v % 3 == 1:
        gable_ew(d, roof, SHINGLE[v % 3], look)
        dormers(d, roof, SHINGLE[v % 3], look, 2)
    else:
        cornice(d, wall, TRIM)
        flat_roof(b.img, roof, look, random.Random(8600 + v), [("vent", 6, 4), ("hatch", 16, 10)], parapet=shade(col, 1.15))
        if v % 3 == 2:
            roof_sign(d, roof, c("#2a4a6a"), 4)
    return b


def office_building(look, v):
    """Six storeys of pale stone, tall windows in bays, a rusticated base and a grand door."""
    col = [c("#d8d0bc"), c("#c4b49a"), c("#b8b0a4")][v % 3]
    b = Building(height=6 * STOREY + 8)
    d = b.d
    roof, wall = b.box(2, 4, 29, 27, 6 * STOREY + 2)
    d.rectangle(wall, col)
    wx0, wy0, wx1, wy1 = wall
    for xx in range(wx0 + 3, wx1 - 2, 5):
        for yy in range(wy0 + 4, wy1 - 7, STOREY):
            d.rectangle([xx, yy, xx + 2, yy + 3], c("#3e4a56"))
    for yy in range(wy1 - 6, wy1, 2):
        d.line([wx0 + 1, yy, wx1 - 1, yy], shade(col, 0.82))
    cx = (wx0 + wx1) // 2
    d.rectangle([cx - 3, wy1 - 6, cx + 3, wy1], c("#2a2a30"))
    d.arc([cx - 3, wy1 - 9, cx + 3, wy1 - 3], 180, 360, shade(col, 0.7))
    d.rectangle(wall, outline=OUTLINE)
    cornice(d, wall, shade(col, 1.12))
    if v % 3 == 2:
        # A mansard of slate, its dormers in a row.
        gable_ew(d, roof, SHINGLE[2], look)
        dormers(d, roof, SHINGLE[2], look, 3)
    else:
        flat_roof(b.img, roof, look, random.Random(8700 + v), [("tank", 18, 8), ("vent", 4, 4)], parapet=shade(col, 1.12))
        if v % 3 == 1:
            light_well(d, roof, look, col)
    return b


def office_tower(look, v):
    """A 1920s tower on 2 by 2 tiles: a wide base, a shaft set back in stages, and a crown on top."""
    col = [c("#cfc4a8"), c("#b8876a"), c("#9a9488")][v % 3]
    b = Building(2, 2, height=22 * STOREY)
    d = b.d
    # The base, five storeys over the whole lot.
    roof, wall = b.box(2, 30, 61, 61, 5 * STOREY)
    d.rectangle(wall, col)
    wx0, wy0, wx1, wy1 = wall
    for xx in range(wx0 + 3, wx1 - 2, 4):
        d.line([xx, wy0 + 3, xx, wy1 - 6], c("#3e4a56"), 2)
    d.rectangle([wx0 + 24, wy1 - 6, wx0 + 34, wy1], c("#2a2a30"))
    d.rectangle(wall, outline=OUTLINE)
    flat_roof(b.img, roof, look, random.Random(8800 + v), [], parapet=shade(col, 1.1))
    # The shaft, set back, and higher still a narrower crown: in two steps, as one tall shaft, or as a broad slab.
    stages = [
        ((12, 8, 51, 40, 16 * STOREY), (22, 12, 41, 30, 20 * STOREY)),
        ((18, 8, 45, 34, 19 * STOREY), (24, 12, 39, 28, 21 * STOREY)),
        ((8, 10, 55, 36, 14 * STOREY), (14, 14, 49, 30, 16 * STOREY)),
    ][v % 3]
    for (x0, y0, x1, y1, h) in stages:
        roof, wall = b.box(x0, y0, x1, y1, h)
        d.rectangle(wall, shade(col, 0.95))
        wx0, wy0, wx1, wy1 = wall
        for xx in range(wx0 + 2, wx1 - 1, 3):
            d.line([xx, wy0 + 2, xx, wy1 - 1], c("#3e4a56"))
        d.rectangle(wall, outline=OUTLINE)
        d.rectangle(roof, SNOW_ROOF[0] if look == "snow" else shade(col, 1.05), OUTLINE)
    sx, sy = roof[0] + (roof[2] - roof[0]) // 2, roof[1] + (roof[3] - roof[1]) // 2
    if v % 3 == 2:
        # A pyramid cap over the slab.
        d.polygon([(roof[0], roof[3]), (sx, sy - 10), (roof[2], roof[3])], c("#6a7a6a"), OUTLINE)
    else:
        # A spire.
        d.polygon([(sx - 3, sy), (sx + 3, sy), (sx, sy - 14 - 6 * (v % 3))], shade(col, 0.8), OUTLINE)
    return b


def glass_tower(look, v):
    """A 1960s slab of glass on 2 by 2 tiles over a plaza, a dark band at each mechanical floor."""
    b = Building(2, 2, height=26 * STOREY)
    d = b.d
    gx0, gy0 = b.ground(1, 1)
    gx1, gy1 = b.ground(62, 62)
    d.rectangle([gx0, gy0, gx1, gy1], SNOW_GROUND if look == "snow" else c("#c8c4b8"))
    for xx in range(gx0 + 4, gx1, 8):
        d.line([xx, gy0 + 1, xx, gy1 - 1], c("#b8b4a8"))
    roof, wall = b.box(10, 10, 53, 44, 24 * STOREY)
    glass = [c("#5f7f94"), c("#4f6f86"), c("#5a8a8a")][v % 3]
    d.rectangle(wall, glass)
    wx0, wy0, wx1, wy1 = wall
    for xx in range(wx0 + 3, wx1, 4):
        d.line([xx, wy0, xx, wy1], shade(glass, 1.25))
    for yy in range(wy0 + 3, wy1, 3):
        d.line([wx0, yy, wx1, yy], shade(glass, 0.85))
    for yy in (wy0 + 2, wy0 + (wy1 - wy0) // 2):
        d.rectangle([wx0, yy, wx1, yy + 2], c("#2a3036"))
    # Sky in the glass.
    d.line([wx0 + 2, wy0 + 6, wx0 + 14, wy0 + 30], (220, 235, 245, 140))
    d.rectangle(wall, outline=OUTLINE)
    flat_roof(b.img, roof, look, random.Random(8900 + v), [("vent", 8, 8), ("vent", 30, 8), ("hatch", 20, 20)], parapet=c("#3a4048"))
    rx0, ry0, rx1, ry1 = roof
    if v % 3 == 1:
        # A mast and its lights.
        mx = (rx0 + rx1) // 2
        b.d.line([mx, ry0 + 4, mx, ry0 - 16], c("#c8ccd0"), 2)
        b.d.point((mx, ry0 - 17), c("#e05040"))
    elif v % 3 == 2:
        # A crown of glass sloping up to the north.
        b.d.polygon([(rx0, ry1), (rx1, ry1), (rx1, ry0 - 5), (rx0, ry0 - 5)], c("#8ab4c8"), OUTLINE)
        for xx in range(rx0 + 4, rx1, 5):
            b.d.line([xx, ry1 - 1, xx, ry0 - 4], c("#6a94a8"))
    return b


# Rural lots, big and cheap at the edge of town: a farmstead, a country house,
# an acreage home, a crossroads store and a roadhouse.

DRIVE = c("#b9ad94")
FENCE = c("#8a6a48")


def lot_ground(b, look, x0, y0, x1, y1, col):
    """A patch of bare ground, gravel or paving on the lot, in tile pixels."""
    gx0, gy0 = b.ground(x0, y0)
    gx1, gy1 = b.ground(x1, y1)
    b.d.rectangle([gx0, gy0, gx1, gy1], SNOW_GROUND if look == "snow" else col)


def lawn_ground(b, look, x0, y0, x1, y1, seed):
    """Mown grass, kept greener than the field around it but browned in a dry summer."""
    gx0, gy0 = b.ground(x0, y0)
    gx1, gy1 = b.ground(x1, y1)
    cols = GRASS[look] if look not in ("summer", "spring") else [shade(g, 1.08) for g in GRASS[look]]
    noise_fill(b.img, (gx0, gy0, gx1 + 1, gy1 + 1), cols, random.Random(seed))


def rural_trees(b, look, v, spots, seed):
    """Trees at the given spots in tile pixels: (x, y, radius), a negative radius for a conifer."""
    rng = random.Random(seed)
    for fx, fy, r in spots:
        if r < 0:
            cast = conifer(b.img, look, fx, fy + b.lift, -r * 2, rng)
            b.casters.append((0, cast[0], cast[1] - b.lift, cast[2], cast[3], 0))
        else:
            cast = deciduous(b.img, look, v, fx, fy + b.lift, r, rng)
            b.casters.append((0, cast[0], cast[1] - b.lift, cast[2], cast[3], 0))


def rail_fence(b, look, x0, y0, x1, y1):
    """A split rail fence round a paddock, in tile pixels."""
    gx0, gy0 = b.ground(x0, y0)
    gx1, gy1 = b.ground(x1, y1)
    col = FENCE if look != "snow" else shade(FENCE, 0.9)
    b.d.rectangle([gx0, gy0, gx1, gy1], outline=col)
    for xx in range(gx0, gx1 + 1, 5):
        b.d.point((xx, gy0 - 1), col)
        b.d.point((xx, gy1 - 1), col)


def barn(b, look, x0, y0, x1, y1, height, roof_col, wall_col=c("#9a3a2e")):
    """A barn with a big door and a ridge to the street: red, weathered grey or white."""
    roof, wall = b.box(x0, y0, x1, y1, height)
    d = b.d
    d.rectangle(wall, wall_col)
    for xx in range(wall[0] + 2, wall[2], 3):
        d.line([xx, wall[1] + 1, xx, wall[3] - 1], shade(wall_col, 0.9))
    cx = (wall[0] + wall[2]) // 2
    d.rectangle([cx - 3, wall[3] - 7, cx + 3, wall[3]], shade(wall_col, 0.55))
    d.line([cx - 3, wall[3] - 7, cx + 3, wall[3]], TRIM)
    d.line([cx + 3, wall[3] - 7, cx - 3, wall[3]], TRIM)
    d.rectangle(wall, outline=OUTLINE)
    gable_ns(d, roof, wall, roof_col, wall_col, look)
    b.casters[-1] = (1, x0, y0, x1 + 1, y1 + 1, height + (x1 - x0) // 3)


def silo(b, look, x0, y0, size, height):
    """A round silo beside a barn: a pale stave tower with a domed cap."""
    roof, wall = b.box(x0, y0, x0 + size, y0 + size, height)
    d = b.d
    col = c("#c8c0ae")
    d.rectangle(wall, col)
    for yy in range(wall[1] + 2, wall[3], 3):
        d.line([wall[0], yy, wall[2], yy], shade(col, 0.88))
    d.rectangle(wall, outline=OUTLINE)
    cap = SNOW_ROOF[0] if look == "snow" else c("#8a8f94")
    d.ellipse([roof[0], roof[1], roof[2], roof[3]], cap, outline=OUTLINE)


def haystacks(b, look, spots):
    """Round haystacks in a field, in tile pixels; none under the snow."""
    if look == "snow":
        return
    col = c("#d8b84a") if look in ("summer", "dry", "autumn") else c("#b8a060")
    for x, y in spots:
        gx, gy = b.ground(x, y)
        b.d.ellipse([gx - 3, gy - 4, gx + 3, gy + 1], col, outline=shade(col, 0.6))
        b.d.line([gx - 2, gy - 3, gx + 1, gy - 3], shade(col, 1.15))


def coop(b, look, x0, y0):
    """A small hen house with a lean-to roof."""
    roof, wall = b.box(x0, y0, x0 + 6, y0 + 4, STOREY - 1)
    b.d.rectangle(wall, c("#c8b48a"))
    b.d.rectangle(wall, outline=OUTLINE)
    b.d.rectangle(roof, SNOW_ROOF[0] if look == "snow" else c("#6b4a36"))


def crop_rows(b, look, x0, y0, x1, y1, seed):
    """A field in rows: green shoots in spring, gold in late summer, stubble in autumn, bare in winter."""
    gx0, gy0 = b.ground(x0, y0)
    gx1, gy1 = b.ground(x1, y1)
    if look == "snow":
        b.d.rectangle([gx0, gy0, gx1, gy1], SNOW_GROUND)
        return
    soil = c("#6b4a30")
    b.d.rectangle([gx0, gy0, gx1, gy1], soil)
    crop = {"spring": c("#6fae4a"), "summer": c("#5f9f42"), "dry": c("#d8b84a"), "autumn": c("#b89a58")}.get(look)
    if crop is None:
        return
    rng = random.Random(seed)
    for yy in range(gy0 + 1, gy1, 3):
        for xx in range(gx0 + 1, gx1):
            if rng.random() < 0.8:
                b.d.point((xx, yy), crop)


def farmstead(look, v):
    """
    A farmhouse and a barn on 2 by 2 tiles, set forward from the back of the lot
    so nothing reaches over the farm behind: a yard between them and a lane to
    the road. Each of the four is its own farm: a paddock; an orchard and a
    silo; haystacks and pines down one side; a coop and a field of crops.
    """
    b = Building(2, 2, height=2 * STOREY + 14)
    d = b.d
    flip = v % 2 == 1

    def X(x0, x1):
        return (63 - x1, 63 - x0) if flip else (x0, x1)

    yard = [c("#a8946c"), c("#9c8a68"), c("#b09a70"), c("#a08c62")][v]
    yx0, yx1 = X(8, 48)
    lot_ground(b, look, yx0, 34, yx1, 46, yard)
    lx0, lx1 = X(24, 29)
    lot_ground(b, look, lx0, 46, lx1, 63, yard)
    # What's behind the buildings first, then the buildings, then what's in front, so each sits over what's behind it.
    if v == 2:
        # Pines down the west side, a windbreak, back to front.
        for k, yy in enumerate(range(14, 62, 8)):
            rural_trees(b, look, v, [(4, yy, -4)], 9200 + k)
    if v == 3:
        # An old tree behind the barn.
        rural_trees(b, look, v, [(X(54, 55)[0], 18, 7)], 9310)
    # The farmhouse and the barn.
    walls = [SIDING[0], c("#e8d48a"), BRICK20, SIDING[1]][v]
    hx0, hx1 = X(8, 26) if v != 2 else X(12, 28)
    house_shape(b, look, v, (hx0, 22, hx1, 34), 2, walls, [SHINGLE[0], SHINGLE[2], SHINGLE[1], c("#3f6b4a")][v], False, ("porch", "stack"), brick_walls=v == 2)
    bx0, bx1 = X(34, 54)
    barn_wall = [c("#9a3a2e"), c("#8c8a84"), c("#9a3a2e"), c("#e8e4da")][v]
    barn_roof = [SHINGLE[1], IRON_ROOF, IRON_ROOF, c("#3f6b4a")][v]
    barn(b, look, bx0, 18, bx1, 34, STOREY + 8, barn_roof, barn_wall)
    if v == 1:
        sx, _ = X(56, 61)
        silo(b, look, sx, 22, 5, 3 * STOREY)
    # In front: the garden, the paddock, the orchard, the hay or the crops.
    if v == 0:
        gx0, gx1 = X(6, 20)
        crop_rows(b, look, gx0, 49, gx1, 60, 9100)
        px0, px1 = X(34, 61)
        rail_fence(b, look, px0, 49, px1, 61)
        rural_trees(b, look, v, [(X(30, 31)[0], 44, 6)], 9300)
    elif v == 1:
        ox0, _ = X(36, 37)
        step = 8 if not flip else -8
        spots = [(ox0 + step * k, yy, 4) for yy in (50, 59) for k in range(3)]
        rural_trees(b, look, v, spots, 9320)
        rural_trees(b, look, v, [(X(14, 15)[0], 54, 6)], 9330)
    elif v == 2:
        haystacks(b, look, [(X(40, 41)[0], 52), (X(48, 49)[0], 56), (X(55, 56)[0], 51)])
        fx0, fx1 = X(34, 61)
        rail_fence(b, look, fx0, 47, fx1, 61)
    else:
        cx, _ = X(8, 14)
        coop(b, look, cx, 48)
        fx0, fx1 = X(32, 61)
        crop_rows(b, look, fx0, 49, fx1, 61, 9340)
        rural_trees(b, look, v, [(X(18, 19)[0], 58, 5)], 9350)
    return b


def country_house(look, v):
    """A big house set back on its 2 by 2 lot: a drive up from the road, lawns, old trees and a hedge."""
    b = Building(2, 2, height=2 * STOREY + 14)
    d = b.d
    flip = v == 1
    lawn_ground(b, look, 4, 4, 59, 59, 9400 + v)
    # The drive, from the road in the south, curving round to the door.
    dx = 46 if not flip else 17
    lot_ground(b, look, dx - 2, 32, dx + 2, 63, DRIVE)
    lot_ground(b, look, 20, 30, 46 if not flip else 44, 34, DRIVE)
    hedge = c("#3d6b2e") if look in ("spring", "summer", "dry") else c("#6b5a44") if look != "snow" else c("#dfe7ee")
    for gx in (2, 61):
        x, y = b.ground(gx, 4)
        d.rectangle([x, y, x + 1, y + 58], hedge)
    house = (14, 10, 48, 28) if not flip else (16, 10, 50, 28)
    if v == 2:
        # Grey stone, a wing the other way, and a pond in the garden.
        house = (10, 8, 40, 26)
        pond(b, look, 20, 48, 8, 6, random.Random(9480))
    wall_col = [c("#efe6d0"), BRICK20, c("#cfc8b8")][v % 3]
    extras = [("porch", "dormers", "stack", "bay_right"), ("porch", "dormers", "stack", "bay_left"), ("porch", "stack", "bay_left", "bay_right")][v % 3]
    house_shape(b, look, v % 2, house, 2, wall_col, SHINGLE[(2 - v) % 3], False, extras, brick_walls=v == 1)
    trees = [(8, 44, 7), (56 if flip else 8, 14, 6), (30, 52, 6), (54 if not flip else 6, 50, -5)] if v < 2 else \
        [(52, 12, 7), (6, 30, 6), (34, 56, -5), (58, 28, 5)]
    rural_trees(b, look, v, trees, 9500 + v)
    return b


def acreage_home(look, v):
    """A long ranch house on a big lot: a low roof, a garage at the end, a paved drive, a lawn and young trees; a pool out back on one."""
    b = Building(2, 2, height=STOREY + 12)
    d = b.d
    lawn_ground(b, look, 2, 2, 61, 61, 9600 + v)
    flip = v == 1
    gx0, gx1 = (40, 52) if not flip else (11, 23)
    lot_ground(b, look, gx0 + 1, 34, gx1 - 1, 63, c("#8a8a88"))
    if v >= 1 and look in ("summer", "dry"):
        px, py = b.ground(30 if v == 1 else 6, 6)
        d.rectangle([px, py, px + 16, py + 8], c("#e8e4da"))
        d.rectangle([px + 2, py + 2, px + 14, py + 6], c("#5ab4d8"))
    # The house, then the garage on the end.
    hx0, hx1 = (8, 40) if not flip else (23, 55)
    roof, wall = b.box(hx0, 16, hx1, 32, STOREY + 2)
    col = [c("#d8c4a0"), c("#b8c4c8"), c("#c8b8a8")][v % 3]
    siding(d, wall, col) if v == 1 else brick(d, wall, c("#b0704e") if v == 0 else c("#8a6a5a"))
    windows(d, wall, 1, every=7, width=4, skip_door=True)
    door(d, wall)
    d.rectangle(wall, outline=OUTLINE)
    gable_ew(d, roof, SHINGLE[(1 + v) % 3], look)
    roof, wall = b.box(gx0, 18, gx1, 33, STOREY)
    if v == 2:
        # An open carport on posts, a car under it.
        d.rectangle(wall, shade(col, 0.6))
        for xx in (wall[0] + 1, wall[2] - 1):
            d.line([xx, wall[1], xx, wall[3]], c("#e8e4da"))
        d.rectangle([wall[0] + 3, wall[3] - 4, wall[2] - 3, wall[3] - 1], c("#2f5f8a"), OUTLINE)
        d.rectangle(roof, SNOW_ROOF[0] if look == "snow" else c("#8a8a88"), outline=OUTLINE)
    else:
        d.rectangle(wall, shade(col, 0.95))
        d.rectangle([wall[0] + 2, wall[3] - 4, wall[2] - 2, wall[3]], c("#e8e4da"))
        for yy in range(wall[3] - 3, wall[3], 2):
            d.line([wall[0] + 3, yy, wall[2] - 3, yy], c("#c8c4b8"))
        d.rectangle(wall, outline=OUTLINE)
        gable_ew(d, roof, SHINGLE[(1 + v) % 3], look)
    rural_trees(b, look, v, [(10, 50, 4), (28, 54, 3), (56 if not flip else 6, 8, 4), (6 if not flip else 56, 8, -4)], 9700 + v)
    return b


def crossroads_store(look, v):
    """A small timber store on one tile with a false front, a gas pump out front and a gravel apron."""
    b = Building(height=STOREY + 10)
    d = b.d
    lot_ground(b, look, 1, 20, 30, 31, DRIVE)
    roof, wall = b.box(5, 6, 26, 20, STOREY)
    col = [PAINT[2], PAINT[4], PAINT[3]][v % 3]
    top = (wall[0], wall[1] - 5, wall[2], wall[3])
    siding(d, top, col)
    d.rectangle([wall[0] + 2, wall[3] - 5, wall[0] + 8, wall[3] - 2], PLATE_GLASS)
    d.rectangle([wall[2] - 8, wall[3] - 5, wall[2] - 2, wall[3] - 2], PLATE_GLASS)
    door(d, wall)
    sign(d, top[0] + 3, top[2] - 3, top[1] + 1, AWNINGS[(v * 3) % len(AWNINGS)])
    if v == 2:
        # A porch along the front on posts.
        for xx in range(wall[0] + 1, wall[2], 5):
            d.line([xx, wall[3] + 1, xx, wall[3] + 3], c("#7a5a3a"))
    d.rectangle(top, outline=OUTLINE)
    d.rectangle(roof, SNOW_ROOF[1] if look == "snow" else IRON_ROOF, OUTLINE)
    # The pump, red and white, on its island.
    px, py = b.ground(22, 27)
    d.rectangle([px - 3, py + 1, px + 4, py + 2], c("#a8a49a"))
    for k, ox in enumerate((0, -8) if v == 2 else (0,)):
        d.rectangle([px - 1 + ox, py - 7, px + 2 + ox, py], [c("#c0392b"), c("#e8e4da"), c("#3f8a4a")][(v + k) % 3], OUTLINE)
        d.ellipse([px - 1 + ox, py - 10, px + 2 + ox, py - 7], c("#f2f2ea"), OUTLINE)
        b.casters.append((1, 21 + ox, 26, 25 + ox, 28, 9))
    return b


def roadhouse(look, v):
    """A diner on 2 by 1 tiles, chrome and coloured stripes, its sign up on a pole and cars out on the lot."""
    b = Building(2, 1, height=STOREY + 22)
    d = b.d
    lot_ground(b, look, 1, 2, 62, 31, c("#5c5c62"))
    if look != "snow":
        for xx in range(36, 62, 6):
            gx, gy = b.ground(xx, 4)
            d.line([gx, gy, gx, gy + 10], c("#d8d8d0"))
    flip = v == 1
    dx0, dx1 = (4, 32) if not flip else (31, 59)
    roof, wall = b.box(dx0, 8, dx1, 22, STOREY + 1)
    stripe = [c("#c0392b"), c("#3c78a8"), c("#3f8a4a")][v % 3]
    d.rectangle(wall, c("#d8dce0"))
    d.rectangle([wall[0] + 1, wall[3] - 6, wall[2] - 1, wall[3] - 3], PLATE_GLASS)
    d.line([wall[0] + 1, wall[1] + 1, wall[2] - 1, wall[1] + 1], stripe)
    d.line([wall[0] + 1, wall[3] - 1, wall[2] - 1, wall[3] - 1], stripe)
    door(d, wall, at=wall[0] + 4)
    d.rectangle(wall, outline=OUTLINE)
    flat_roof(b.img, roof, look, random.Random(9800 + v), [("vent", 5, 4), ("vent", 18, 6)], parapet=c("#a8acb0"))
    # Cars nose in to the lot.
    cars = [c("#c0392b"), c("#2f5f8a"), c("#e8d070"), c("#3f8a4a"), c("#e8e4da")]
    for k, xx in enumerate(range(37 if not flip else 5, 60 if not flip else 28, 6)):
        if (k + v) % 3 == 2:
            continue
        gx, gy = b.ground(xx + 1, 5)
        d.rectangle([gx, gy, gx + 3, gy + 7], cars[(k + v) % len(cars)], OUTLINE)
        d.line([gx + 1, gy + 2, gx + 2, gy + 2], c("#2a3036"))
    if v == 2:
        # Picnic tables out by the diner.
        for xx in (8, 20):
            gx, gy = b.ground(xx, 25)
            d.rectangle([gx, gy, gx + 6, gy + 3], c("#a8845a"), OUTLINE)
    # The sign on its pole by the road.
    sx, sy = b.ground(34 if not flip else 29, 29)
    d.line([sx, sy, sx, sy - 22], c("#6a6a70"), 2)
    d.rectangle([sx - 7, sy - 30, sx + 8, sy - 22], stripe, OUTLINE)
    for xx in range(sx - 5, sx + 7, 2):
        d.point((xx, sy - 26), c("#f2e6a0"))
    b.casters.append((1, 33, 28, 35, 30, 28))
    return b


# Towers, from the motor age on: a tower block of flats, a slender glass tower,
# a hotel tower, a skyscraper and the supertall.

SLAB = c("#bdb9b0")


def curtain_wall(d, wall, glass, every=4, bands=3):
    """Glass from top to bottom: mullions, floor lines and a streak of sky."""
    x0, y0, x1, y1 = wall
    d.rectangle(wall, glass)
    for xx in range(x0 + 2, x1, every):
        d.line([xx, y0, xx, y1], shade(glass, 1.22))
    for yy in range(y0 + 2, y1, bands):
        d.line([x0, yy, x1, yy], shade(glass, 0.85))
    d.line([x0 + 2, y0 + 6, x0 + min(14, x1 - x0 - 2), y0 + 30], (220, 235, 245, 140))
    d.rectangle(wall, outline=OUTLINE)


def plaza(b, look, x0=1, y0=1, x1=62, y1=62):
    """Paving round a tower's foot."""
    gx0, gy0 = b.ground(x0, y0)
    gx1, gy1 = b.ground(x1, y1)
    b.d.rectangle([gx0, gy0, gx1, gy1], SNOW_GROUND if look == "snow" else c("#c8c4b8"))
    for xx in range(gx0 + 4, gx1, 8):
        b.d.line([xx, gy0 + 1, xx, gy1 - 1], c("#b8b4a8"))


def highrise(look, v):
    """A tower block of flats on 2 by 2 tiles: a brick tower of the thirties, or a concrete slab with balconies on a lawn."""
    if v == 0:
        b = Building(2, 2, height=16 * STOREY + 6)
        d = b.d
        plaza(b, look)
        # Wings either side, then the taller middle.
        for (x0, x1, h) in ((4, 20, 13), (43, 59, 13)):
            roof, wall = b.box(x0, 12, x1, 50, h * STOREY)
            brick(d, wall, BRICK20)
            windows(d, wall, h, sill=TRIM, every=4)
            d.rectangle(wall, outline=OUTLINE)
            flat_roof(b.img, roof, look, random.Random(9900 + x0), [("stack", 4, 6)], parapet=STONE)
        roof, wall = b.box(18, 8, 45, 54, 16 * STOREY)
        brick(d, wall, BRICK)
        windows(d, wall, 16, sill=TRIM, every=4, skip_door=True)
        door(d, wall)
        d.rectangle(wall, outline=OUTLINE)
        cornice(d, wall, STONE)
        flat_roof(b.img, roof, look, random.Random(9910), [("tank", 10, 8), ("hatch", 16, 30)], parapet=STONE)
        return b
    if v == 2:
        # A point block of the sixties: a square tower, panels of colour under each window, on a lawn with a car park.
        b = Building(2, 2, height=18 * STOREY + 4)
        d = b.d
        lawn_ground(b, look, 1, 1, 62, 62, 9925)
        lot_ground(b, look, 4, 46, 30, 60, c("#6a6a70"))
        roof, wall = b.box(18, 10, 46, 40, 18 * STOREY)
        d.rectangle(wall, c("#d0ccc4"))
        x0, y0, x1, y1 = wall
        per = (y1 - y0) / 18
        for k in range(18):
            yy = int(y0 + k * per)
            for n, xx in enumerate(range(x0 + 2, x1 - 2, 4)):
                d.rectangle([xx, yy + 2, xx + 2, yy + 3], c("#3e4a56"))
                d.point((xx + 1, yy + 4), [c("#c8603a"), c("#3c78a8"), c("#d8b040")][(n + k) % 3])
        d.rectangle([x0 + 11, y1 - 6, x0 + 17, y1], c("#2a2a30"))
        d.rectangle(wall, outline=OUTLINE)
        flat_roof(b.img, roof, look, random.Random(9935), [("hatch", 8, 6), ("tank", 16, 14)], parapet=c("#b8b4ac"))
        rural_trees(b, look, 0, [(52, 50, 5), (56, 14, 4), (8, 14, 4)], 9945)
        return b
    b = Building(2, 2, height=20 * STOREY + 4)
    d = b.d
    lawn_ground(b, look, 1, 1, 62, 62, 9920)
    roof, wall = b.box(6, 22, 57, 40, 20 * STOREY)
    d.rectangle(wall, SLAB)
    x0, y0, x1, y1 = wall
    per = (y1 - y0) / 20
    for k in range(20):
        yy = int(y0 + k * per)
        d.rectangle([x0 + 1, yy + 2, x1 - 1, yy + 4], c("#3e4a56"))
        # Balconies, every other bay.
        for xx in range(x0 + 2, x1 - 4, 8):
            d.rectangle([xx, yy + 4, xx + 5, yy + 5], shade(SLAB, 1.12))
    d.rectangle([x0 + 22, y1 - 6, x0 + 30, y1], c("#2a2a30"))
    d.rectangle(wall, outline=OUTLINE)
    flat_roof(b.img, roof, look, random.Random(9930), [("hatch", 10, 6), ("hatch", 38, 6), ("vent", 24, 10)], parapet=SLAB)
    rural_trees(b, look, 0, [(6, 56, 5), (56, 58, 5), (30, 60, 4)], 9940)
    return b


def slender_tower(look, v):
    """A slender glass tower of flats over a podium of shops on 2 by 2 tiles, balconies wrapping its corners."""
    b = Building(2, 2, height=32 * STOREY + 4)
    d = b.d
    roof, wall = b.box(2, 26, 61, 61, 4 * STOREY)
    d.rectangle(wall, [c("#d8d0bc"), c("#8a8a88"), c("#b8a890")][v % 3])
    d.rectangle([wall[0] + 1, wall[3] - 7, wall[2] - 1, wall[3] - 1], PLATE_GLASS)
    for xx in range(wall[0] + 8, wall[2], 10):
        d.line([xx, wall[3] - 7, xx, wall[3] - 1], OUTLINE)
    d.rectangle(wall, outline=OUTLINE)
    # Green on the podium's roof.
    rx0, ry0, rx1, ry1 = roof
    d.rectangle(roof, SNOW_ROOF[0] if look == "snow" else c("#8a8a88"), OUTLINE)
    if look != "snow":
        noise_fill(b.img, (rx0 + 2, ry0 + 2, rx0 + 18, ry1 - 1), GRASS[look], random.Random(9950 + v))
    roof, wall = b.box(22, 6, 52, 34, 32 * STOREY)
    glass = [c("#6f93a8"), c("#5a7a8e"), c("#7a9a8a")][v % 3]
    curtain_wall(d, wall, glass, every=5, bands=STOREY)
    x0, y0, x1, y1 = wall
    for yy in range(y0 + 4, y1 - 2, STOREY):
        d.line([x0 - 1, yy, x0 + 6, yy], c("#e8e8e4"))
        d.line([x1 - 6, yy, x1 + 1, yy], c("#e8e8e4"))
    flat_roof(b.img, roof, look, random.Random(9960 + v), [("hatch", 12, 10)], parapet=c("#3a4048"))
    rx0, ry0, rx1, ry1 = roof
    if v % 3 == 1:
        # A crown of glass sloping up to the north.
        d.polygon([(rx0, ry1), (rx1, ry1), (rx1, ry0 - 6), (rx0, ry0)], shade(glass, 1.15), OUTLINE)
        for xx in range(rx0 + 4, rx1, 4):
            d.line([xx, ry1 - 1, xx, ry0 + (ry0 - 6 - ry0) * (xx - rx0) // max(1, rx1 - rx0) + 1], shade(glass, 0.9))
    elif v % 3 == 2:
        if look != "snow":
            noise_fill(b.img, (rx0 + 3, ry0 + 3, rx1 - 2, ry1 - 2), GRASS[look], random.Random(9965))
        mx = (rx0 + rx1) // 2
        d.line([mx, ry0 + 2, mx, ry0 - 14], c("#c8ccd0"))
        d.point((mx, ry0 - 15), c("#e05040"))
    return b


def highrise_hotel(look, v):
    """A hotel tower on 2 by 2 tiles: a canopy over the drive at its door, a pool on the low wing, and its name up top."""
    b = Building(2, 2, height=22 * STOREY + 12)
    d = b.d
    plaza(b, look)
    col = [c("#e0d8c8"), c("#c8b8a0"), c("#d8c8b8")][v % 3]
    roof, wall = b.box(36, 34, 61, 54, 2 * STOREY)
    d.rectangle(wall, col)
    d.rectangle([wall[0] + 1, wall[3] - 5, wall[2] - 1, wall[3] - 1], PLATE_GLASS)
    d.rectangle(wall, outline=OUTLINE)
    rx0, ry0, rx1, ry1 = roof
    d.rectangle(roof, SNOW_ROOF[0] if look == "snow" else c("#e8e4da"), OUTLINE)
    if look in ("spring", "summer", "dry"):
        d.rectangle([rx0 + 4, ry0 + 4, rx1 - 4, ry1 - 6], c("#5ab4d8"), OUTLINE)
    roof, wall = b.box(6, 8, 38, 46, 22 * STOREY)
    d.rectangle(wall, col)
    x0, y0, x1, y1 = wall
    for xx in range(x0 + 3, x1 - 2, 4):
        for yy in range(y0 + 3, y1 - 8, STOREY):
            d.rectangle([xx, yy, xx + 1, yy + 2], c("#3e4a56"))
    # The canopy over the door, and the hotel's name.
    d.rectangle([x0 + 8, y1 - 7, x1 - 8, y1 - 5], AWNINGS[(1 + v * 3) % len(AWNINGS)], OUTLINE)
    d.rectangle(wall, outline=OUTLINE)
    flat_roof(b.img, roof, look, random.Random(9970 + v), [("vent", 6, 6), ("hatch", 22, 24)], parapet=shade(col, 1.08))
    if v % 3 == 1:
        setback(b, look, (12, 14, 32, 34), 22 * STOREY + 8, shade(col, 1.06), random.Random(9975), glass=c("#3e4a56"), base=22 * STOREY)
    sx0, sy = roof[0] + 4, roof[3] - 2
    d.rectangle([sx0, sy - 8, sx0 + 22, sy - 2], [c("#c0392b"), c("#2f5f8a"), c("#3f8a4a")][v % 3], OUTLINE)
    for xx in range(sx0 + 2, sx0 + 21, 3):
        d.rectangle([xx, sy - 6, xx + 1, sy - 4], c("#f2e6a0"))
    return b


def skyscraper(look, v):
    """A skyscraper on 2 by 2 tiles: stone piers set back in stages to a crown and a mast, or a dark tower of steel and glass."""
    if v == 0:
        col = c("#d8ccb0")
        b = Building(2, 2, height=34 * STOREY + 18)
        d = b.d
        plaza(b, look)
        stages = ((3, 18, 60, 58, 10), (8, 12, 55, 50, 22), (16, 8, 47, 42, 30), (23, 10, 40, 34, 34))
        for k, (x0, y0, x1, y1, h) in enumerate(stages):
            roof, wall = b.box(x0, y0, x1, y1, h * STOREY)
            d.rectangle(wall, shade(col, 1.0 - 0.03 * k))
            wx0, wy0, wx1, wy1 = wall
            for xx in range(wx0 + 2, wx1 - 1, 3):
                d.line([xx, wy0 + 2, xx, wy1 - (7 if k == 0 else 1)], c("#3e4a56"))
            if k == 0:
                d.rectangle([wx0 + 22, wy1 - 7, wx0 + 34, wy1], c("#2a2a30"))
                d.arc([wx0 + 22, wy1 - 12, wx0 + 34, wy1 - 2], 180, 360, c("#b8a060"))
            d.rectangle(wall, outline=OUTLINE)
            d.rectangle(roof, SNOW_ROOF[0] if look == "snow" else shade(col, 1.06), OUTLINE)
        sx, sy = (roof[0] + roof[2]) // 2, (roof[1] + roof[3]) // 2
        d.polygon([(sx - 4, sy), (sx + 4, sy), (sx, sy - 12)], c("#b8b8b0"), OUTLINE)
        d.line([sx, sy - 12, sx, sy - 18], c("#6a6a70"))
        return b
    if v == 2:
        # A white tower of the sixties, its grid of piers and windows, on a broad two storey podium.
        b = Building(2, 2, height=32 * STOREY + 6)
        d = b.d
        plaza(b, look)
        proof, pwall = b.box(3, 30, 60, 58, 2 * STOREY)
        d.rectangle(pwall, c("#3e4a56"))
        for xx in range(pwall[0] + 3, pwall[2], 4):
            d.line([xx, pwall[1], xx, pwall[3]], c("#e8e4da"))
        d.rectangle(pwall, outline=OUTLINE)
        flat_roof(b.img, proof, look, random.Random(9985), [("vent", 10, 6)], parapet=c("#e8e4da"))
        roof, wall = b.box(16, 6, 47, 36, 32 * STOREY)
        d.rectangle(wall, c("#e8e4da"))
        x0, y0, x1, y1 = wall
        for yy in range(y0 + 2, y1, STOREY):
            for xx in range(x0 + 2, x1 - 1, 3):
                d.rectangle([xx, yy, xx + 1, yy + 3], c("#46586a"))
        d.rectangle(wall, outline=OUTLINE)
        flat_roof(b.img, roof, look, random.Random(9990), [("hatch", 10, 10), ("tank", 20, 18)], parapet=c("#d0ccc4"))
        return b
    b = Building(2, 2, height=36 * STOREY + 6)
    d = b.d
    plaza(b, look)
    roof, wall = b.box(10, 8, 53, 48, 36 * STOREY)
    curtain_wall(d, wall, c("#3a4450"), every=3, bands=STOREY)
    x0, y0, x1, y1 = wall
    d.rectangle([x0 + 1, y1 - 8, x1 - 1, y1], c("#2a3036"))
    d.rectangle(wall, outline=OUTLINE)
    flat_roof(b.img, roof, look, random.Random(9980), [("vent", 8, 8), ("vent", 30, 8), ("hatch", 18, 24)], parapet=c("#2a3036"))
    return b


def supertall(look, v):
    """The tallest in town, on 3 by 3 tiles: glass stepping in as it rises, and a spire."""
    b = Building(3, 3, height=50 * STOREY + 30)
    d = b.d
    plaza(b, look, 1, 1, 94, 94)
    glass = [c("#7f9fb4"), c("#6a8a7c"), c("#8a9aa8")][v % 3]
    # Stepping in three times, one slim shaft on a podium, or twin shafts over a shared base.
    stages = [
        ((14, 22, 81, 84, 30), (22, 18, 73, 70, 42), (30, 16, 65, 56, 50)),
        ((18, 30, 77, 84, 8), (30, 14, 65, 62, 50)),
        ((14, 24, 81, 84, 12), (18, 14, 44, 56, 44), (52, 20, 78, 62, 50)),
    ][v % 3]
    for k, (x0, y0, x1, y1, h) in enumerate(stages):
        roof, wall = b.box(x0, y0, x1, y1, h * STOREY)
        curtain_wall(d, wall, shade(glass, 1.0 - 0.05 * k), every=4, bands=STOREY)
        d.rectangle(roof, SNOW_ROOF[0] if look == "snow" else c("#4a5058"), OUTLINE)
        if k < 2:
            d.rectangle([roof[0] + 2, roof[1] + 2, roof[2] - 2, roof[3] - 2], outline=c("#6a7078"))
    sx, sy = (roof[0] + roof[2]) // 2, (roof[1] + roof[3]) // 2
    if v % 3 == 0:
        d.polygon([(sx - 5, sy), (sx + 5, sy), (sx, sy - 24)], c("#c8ccd0"), OUTLINE)
        d.line([sx, sy - 24, sx, sy - 30], c("#8a8a90"))
    elif v % 3 == 1:
        # A crown of lit fins round the top.
        for k in range(-3, 4):
            x = sx + k * 4
            d.polygon([(x - 1, sy + 4), (x + 1, sy + 4), (x, sy - 10 + abs(k) * 2)], c("#f2e6a0"), OUTLINE)
    else:
        for x in (sx - 6, sx + 6):
            d.line([x, sy, x, sy - 22], c("#c8ccd0"), 2)
            d.point((x, sy - 23), c("#e05040"))
    return b


# Homes over shops, from the streetcar age: a shophouse, flats over a row of
# shops, a mixed block over a parade, and a tower of flats on a podium of shops.

def shopfront(d, wall, colour, look, door_at=None):
    """Plate glass along the ground floor under a striped awning, and a door."""
    x0, _, x1, y1 = wall
    d.rectangle([x0 + 1, y1 - 5, x1 - 1, y1 - 1], PLATE_GLASS)
    for xx in range(x0 + 7, x1 - 2, 8):
        d.line([xx, y1 - 5, xx, y1 - 1], OUTLINE)
    door(d, wall, c("#3a2a20"), door_at)
    awning(d, wall, colour, look, 9)


def shophouse(look, v):
    """A narrow brick shop of two storeys, the family's rooms above, a sign over the window."""
    storeys = 2 + (v == 2)
    b = Building(height=storeys * STOREY + 6)
    d = b.d
    x0, x1 = [(6, 25), (4, 27), (7, 24)][v]
    roof, wall = b.box(x0, 6, x1, 26, storeys * STOREY + 2)
    col = [BRICK, c("#b0704e"), PAINT[3]][v]
    brick(d, wall, col) if v != 2 else siding(d, wall, col)
    windows(d, (wall[0], wall[1], wall[2], wall[3] - STOREY - 2), storeys - 1, sill=TRIM, every=5)
    shopfront(d, wall, AWNINGS[v], look)
    sign(d, wall[0] + 3, wall[2] - 3, wall[3] - STOREY - 4, AWNINGS[(v + 3) % len(AWNINGS)])
    d.rectangle(wall, outline=OUTLINE)
    cornice(d, wall, TRIM)
    flat_roof(b.img, roof, look, random.Random(9990 + v), [("stack", 3, 3), ("hatch", 10, 8)], parapet=shade(col, 1.1))
    return b


def flats_over_shops(look, v):
    """Four storeys on a main street: a row of shops below with their awnings, flats above with bay windows."""
    b = Building(height=4 * STOREY + 6)
    d = b.d
    roof, wall = b.box(1, 4, 30, 27, 4 * STOREY + 2)
    col = [BRICK20, c("#c9a86a"), c("#8a5a44")][v]
    brick(d, wall, col)
    upper = (wall[0], wall[1], wall[2], wall[3] - STOREY - 2)
    windows(d, upper, 3, sill=TRIM, every=4)
    # Two shops side by side.
    mid = (wall[0] + wall[2]) // 2
    shopfront(d, (wall[0], wall[1], mid, wall[3]), AWNINGS[v], look, door_at=wall[0] + 4)
    shopfront(d, (mid, wall[1], wall[2], wall[3]), AWNINGS[v + 2], look, door_at=wall[2] - 4)
    d.line([mid, wall[3] - 9, mid, wall[3]], shade(col, 0.7))
    d.rectangle(wall, outline=OUTLINE)
    cornice(d, wall, CORNICE)
    flat_roof(b.img, roof, look, random.Random(10000 + v), [("stack", 3, 3), ("stack", 24, 3), ("tank", 12, 9)], parapet=CORNICE)
    return b


def mixed_block(look, v):
    """Seven storeys of flats with balconies over a glass parade of shops, the shops a little deeper than the flats."""
    b = Building(height=7 * STOREY + 6)
    d = b.d
    # The parade, out to the street.
    roof, wall = b.box(1, 16, 30, 29, STOREY + 2)
    d.rectangle(wall, c("#4a5058"))
    shopfront(d, wall, AWNINGS[1 + v * 2], look)
    d.rectangle(wall, outline=OUTLINE)
    d.rectangle(roof, SNOW_ROOF[0] if look == "snow" else c("#8a8a88"), OUTLINE)
    # The flats.
    roof, wall = b.box(3, 3, 28, 18, 7 * STOREY)
    col = [SLAB, c("#c8b49a"), c("#b8a898")][v % 3]
    d.rectangle(wall, col)
    x0, y0, x1, y1 = wall
    for k in range(6):
        yy = y0 + 2 + k * STOREY
        for xx in range(x0 + 2, x1 - 3, 6):
            d.rectangle([xx, yy, xx + 3, yy + 2], WINDOW)
            d.line([xx - 1, yy + 3, xx + 4, yy + 3], c("#e8e8e4"))
    d.rectangle(wall, outline=OUTLINE)
    flat_roof(b.img, roof, look, random.Random(10100 + v), [("hatch", 4, 4), ("vent", 16, 6)], parapet=shade(col, 1.08))
    return b


def podium_tower(look, v):
    """A tower of flats on 2 by 2 tiles over a podium of shops round its foot, a garden on the podium's roof."""
    b = Building(2, 2, height=28 * STOREY + 4)
    d = b.d
    plaza(b, look)
    roof, wall = b.box(2, 24, 61, 61, 3 * STOREY)
    d.rectangle(wall, [c("#d8d0bc"), c("#9a9690"), c("#c8b8a0")][v % 3])
    x0, y0, x1, y1 = wall
    for k, xx in enumerate(range(x0, x1 - 10, 15)):
        shopfront(d, (xx, y0, xx + 14, y1), AWNINGS[(k + v * 2) % len(AWNINGS)], look)
    d.rectangle(wall, outline=OUTLINE)
    rx0, ry0, rx1, ry1 = roof
    d.rectangle(roof, SNOW_ROOF[0] if look == "snow" else c("#8a8a88"), OUTLINE)
    if look != "snow":
        noise_fill(b.img, (rx0 + 2, ry0 + 2, rx1 - 1, ry0 + 10), GRASS[look], random.Random(10200 + v))
    # The tower, its balconies wrapping the front.
    roof, wall = b.box(16, 6, 47, 32, 28 * STOREY)
    col = [c("#e8e4da"), c("#c8ccd0"), c("#d8d0c4")][v % 3]
    d.rectangle(wall, col)
    x0, y0, x1, y1 = wall
    for yy in range(y0 + 3, y1 - 2, STOREY):
        d.rectangle([x0 + 1, yy, x1 - 1, yy + 2], [c("#5f7f94"), c("#4f6f86"), c("#5a8a8a")][v % 3])
        d.line([x0 - 1, yy + 3, x1 + 1, yy + 3], c("#f2f2ea"))
    d.rectangle(wall, outline=OUTLINE)
    flat_roof(b.img, roof, look, random.Random(10300 + v), [("hatch", 8, 8), ("vent", 20, 6)], parapet=shade(col, 0.9))
    return b


# The deeper services: a volunteer fire hall, a ladder company, an ambulance
# station, a nursing home, a library and a college round its green.

FIRE_RED = c("#c0392b")


def volunteer_hall(look, v):
    """A small timber hall on one tile with a single red door and a siren on a pole."""
    b = Building(height=STOREY + 14)
    d = b.d
    gx0, gy0 = b.ground(6, 24)
    gx1, gy1 = b.ground(26, 31)
    d.rectangle([gx0, gy0, gx1, gy1], c("#b8b2a6") if look != "snow" else c("#dfe5ea"))
    roof, wall = b.box(5, 6, 27, 23, STOREY + 3)
    siding(d, wall, c("#d8cdb2") if v == 0 else c("#9a5a3e"))
    x0, y0, x1, y1 = wall
    d.rectangle([x0 + 6, y1 - 8, x1 - 6, y1], FIRE_RED)
    d.line([(x0 + x1) // 2, y1 - 8, (x0 + x1) // 2, y1], c("#8e2a20"))
    d.rectangle(wall, outline=OUTLINE)
    gable_ew(d, roof, SHINGLE[1] if v == 0 else SHINGLE[0], look)
    # The siren on its pole.
    px, py = b.ground(28, 10)
    d.line([px, py, px, py - 16], c("#6a6a70"))
    d.rectangle([px - 1, py - 18, px + 1, py - 16], c("#d9b44a"), OUTLINE)
    b.casters.append((1, 27, 9, 29, 11, 16))
    return b


def ladder_company(look, v):
    """A wide brick hall on 2 by 2 tiles, one tall door, a drill tower, and the ladder truck out on the apron."""
    b = Building(2, 2, height=5 * STOREY + 4)
    d = b.d
    gx0, gy0 = b.ground(4, 46)
    gx1, gy1 = b.ground(52, 61)
    d.rectangle([gx0, gy0, gx1, gy1], c("#b8b2a6") if look != "snow" else c("#dfe5ea"))
    roof, wall = b.box(4, 8, 44, 44, 2 * STOREY + 4)
    brick(d, wall, [c("#8a4234"), c("#b0804e")][v % 2])
    x0, y0, x1, y1 = wall
    windows(d, (x0, y0, x1, y0 + STOREY), 1, sill=TRIM, every=5)
    d.rectangle([x0 + 8, y1 - 11, x1 - 8, y1], FIRE_RED)
    for k in range(x0 + 12, x1 - 8, 6):
        d.line([k, y1 - 11, k, y1], c("#8e2a20"))
    d.rectangle([x0 + 8, y1 - 12, x1 - 8, y1 - 12], STONE)
    d.rectangle(wall, outline=OUTLINE)
    flat_roof(b.img, roof, look, random.Random(8700), [("vent", 8, 6), ("stack", 30, 4)], parapet=STONE)
    # The drill tower, open at the top.
    troof, twall = b.box(48, 10, 58, 22, 5 * STOREY)
    brick(d, twall, c("#7a3a2e"))
    tx0, ty0, tx1, ty1 = twall
    for k in range(5):
        d.rectangle([tx0 + 3, ty0 + 3 + k * 7, tx0 + 6, ty0 + 5 + k * 7], WINDOW)
    d.rectangle(twall, outline=OUTLINE)
    d.rectangle(troof, SNOW_ROOF[0] if look == "snow" else c("#5a5a60"), OUTLINE)
    # The ladder truck: a long red body with the ladder along its top.
    tx, ty = b.ground(10, 50)
    d.rectangle([tx, ty, tx + 30, ty + 6], FIRE_RED, OUTLINE)
    d.rectangle([tx + 24, ty + 1, tx + 29, ty + 4], c("#6c7f8a"))
    d.line([tx + 2, ty + 2, tx + 22, ty + 2], c("#e6e6e6"))
    d.line([tx + 2, ty + 4, tx + 22, ty + 4], c("#e6e6e6"))
    for k in range(tx + 3, tx + 22, 3):
        d.line([k, ty + 2, k, ty + 4], c("#e6e6e6"))
    b.casters.append((1, 10, 50, 41, 57, 4))
    return b


def ambulance_station(look, v):
    """A low pale brick station on 2 by 1 tiles with two bay doors and an ambulance by them."""
    b = Building(2, 1, height=STOREY + 6)
    d = b.d
    gx0, gy0 = b.ground(4, 23)
    gx1, gy1 = b.ground(60, 31)
    d.rectangle([gx0, gy0, gx1, gy1], c("#b8b2a6") if look != "snow" else c("#dfe5ea"))
    roof, wall = b.box(4, 4, 60, 22, STOREY + 4)
    brick(d, wall, [c("#c9b89a"), c("#a8704e")][v % 2])
    x0, y0, x1, y1 = wall
    for dx in (4, 20):
        d.rectangle([x0 + dx, y1 - 8, x0 + dx + 12, y1], c("#e8e8e4"))
        for k in range(1, 4):
            d.line([x0 + dx, y1 - 8 + k * 2, x0 + dx + 12, y1 - 8 + k * 2], c("#b0b0ac"))
    door(d, (x0 + 36, y0, x1, y1), c("#3a4f6a"))
    d.rectangle([x1 - 10, y0 + 2, x1 - 4, y0 + 6], c("#ffffff"), OUTLINE)
    d.line([x1 - 7, y0 + 3, x1 - 7, y0 + 5], FIRE_RED)
    d.line([x1 - 8, y0 + 4, x1 - 6, y0 + 4], FIRE_RED)
    d.rectangle(wall, outline=OUTLINE)
    flat_roof(b.img, roof, look, random.Random(8710), [("vent", 10, 4), ("vent", 40, 6)], parapet=STONE)
    # The ambulance, white with a red stripe.
    ax, ay = b.ground(40, 24)
    d.rectangle([ax, ay, ax + 14, ay + 5], c("#f2f2ee"), OUTLINE)
    d.line([ax + 1, ay + 3, ax + 13, ay + 3], FIRE_RED)
    d.rectangle([ax + 10, ay + 1, ax + 13, ay + 2], c("#6c7f8a"))
    return b


def nursing_home(look, v):
    """A long two storey home on 2 by 2 tiles with a deep porch, set in a garden with paths and benches."""
    b = Building(2, 2, height=2 * STOREY + 10)
    d = b.d
    gx0, gy0 = b.ground(2, 2)
    gx1, gy1 = b.ground(61, 61)
    d.rectangle([gx0, gy0, gx1, gy1], SNOW_GROUND if look == "snow" else c("#86a85e"))
    px, py = b.ground(28, 40)
    d.rectangle([px, py, px + 6, gy1], PATH if look != "snow" else c("#cfd8df"))
    d.rectangle([gx0 + 6, gy1 - 8, gx1 - 6, gy1 - 6], PATH if look != "snow" else c("#cfd8df"))
    roof, wall = b.box(6, 8, 58, 36, 2 * STOREY + 2)
    if v == 0:
        siding(d, wall, c("#eee6d2"))
    else:
        brick(d, wall, c("#a86a4e"))
    windows(d, wall, 2, sill=TRIM, every=5, skip_door=True)
    x0, y0, x1, y1 = wall
    cx = (x0 + x1) // 2
    # The porch along the front, under its own roof.
    d.rectangle([x0 + 6, y1 - 7, x1 - 6, y1 - 6], TRIM)
    for k in range(x0 + 6, x1 - 5, 6):
        d.line([k, y1 - 5, k, y1], TRIM)
    d.rectangle([cx - 2, y1 - 5, cx + 2, y1], c("#5a3a2a"))
    d.rectangle(wall, outline=OUTLINE)
    gable_ew(d, roof, SHINGLE[2] if v == 0 else SHINGLE[0], look)
    if look != "snow":
        for (fx, fy) in ((10, 44), (48, 44), (14, 54), (44, 54)):
            x, y = b.ground(fx, fy)
            d.rectangle([x, y, x + 4, y + 1], c("#6b4a2a"))
        for k, (fx, fy) in enumerate(((4, 42), (56, 50), (6, 56))):
            x, y = b.ground(fx, fy)
            d.ellipse([x, y, x + 3, y + 3], FLOWERS[k % len(FLOWERS)])
    return b


def library(look, v):
    """A small library on one tile: stone or brick, columns at the door, steps, and a dome or a pediment."""
    b = Building(height=2 * STOREY + 8)
    d = b.d
    gx0, gy0 = b.ground(3, 25)
    gx1, gy1 = b.ground(28, 30)
    d.rectangle([gx0, gy0, gx1, gy1], SNOW_GROUND if look == "snow" else c("#86a85e"))
    roof, wall = b.box(4, 6, 28, 24, 2 * STOREY)
    wall_col = c("#d8cfb8") if v == 0 else c("#9a5040")
    d.rectangle(wall, wall_col)
    x0, y0, x1, y1 = wall
    for yy in range(y0 + 3, y1, 4):
        d.line([x0 + 1, yy, x1 - 1, yy], shade(wall_col, 0.92))
    cx = (x0 + x1) // 2
    windows(d, wall, 2, glass=c("#46586a"), sill=TRIM, every=6, width=2, height=4, skip=[(cx - 6, cx + 6)])
    d.rectangle([cx - 5, y1 - 10, cx + 5, y1], c("#e6dfcc"))
    for k in (-4, -1, 2, 4):
        d.line([cx + k, y1 - 9, cx + k, y1], c("#bfb7a4"))
    d.rectangle([cx - 1, y1 - 5, cx + 1, y1], c("#4a3226"))
    d.polygon([(cx - 6, y1 - 10), (cx, y1 - 14), (cx + 6, y1 - 10)], TRIM, OUTLINE)
    d.rectangle([cx - 6, y1 + 1, cx + 6, y1 + 2], STONE)
    d.rectangle(wall, outline=OUTLINE)
    flat_roof(b.img, roof, look, random.Random(8720 + v), [], parapet=STONE)
    if v == 0:
        rx0, ry0, rx1, ry1 = roof
        mx, my = (rx0 + rx1) // 2, (ry0 + ry1) // 2
        d.ellipse([mx - 5, my - 5, mx + 5, my + 5], SNOW_ROOF[0] if look == "snow" else c("#6e8f86"), OUTLINE)
        d.ellipse([mx - 2, my - 3, mx + 1, my], c("#8fb0a6") if look != "snow" else c("#ffffff"))
    else:
        bell_cupola(b, look, 16, 14, 2 * STOREY + 4)
    return b


def college(look, v):
    """A college on 3 by 3 tiles: halls round three sides of a green, with a clock tower on the hall at the back."""
    b = Building(3, 3, height=5 * STOREY + 10)
    d = b.d
    rng = random.Random(8730 + v)
    gx0, gy0 = b.ground(2, 2)
    gx1, gy1 = b.ground(93, 93)
    d.rectangle([gx0, gy0, gx1, gy1], SNOW_GROUND if look == "snow" else c("#7fa05a"))
    wall_col = c("#c9b893") if v == 0 else c("#8e4a3a")
    roof_col = SHINGLE[0] if v == 0 else SHINGLE[2]

    def hall(x0_, y0_, x1_, y1_, storeys):
        roof, wall = b.box(x0_, y0_, x1_, y1_, storeys * STOREY + 2)
        if v == 0:
            d.rectangle(wall, wall_col)
            wx0, wy0, wx1, wy1 = wall
            for yy in range(wy0 + 3, wy1, 4):
                d.line([wx0 + 1, yy, wx1 - 1, yy], shade(wall_col, 0.92))
        else:
            brick(d, wall, wall_col)
        windows(d, wall, storeys, glass=c("#46586a"), sill=TRIM, every=5, width=2, height=4)
        d.rectangle(wall, outline=OUTLINE)
        return roof, wall

    # The back hall, then the two side halls in front of it.
    roof, wall = hall(6, 6, 90, 30, 3)
    gable_ew(d, roof, roof_col, look)
    for (x0_, x1_) in ((6, 26), (70, 90)):
        roof, wall = hall(x0_, 28, x1_, 80, 2)
        flat_roof(b.img, roof, look, rng, [("vent", 6, 10)], parapet=STONE)
    # The green in the middle, crossed by paths.
    qx0, qy0 = b.ground(30, 36)
    qx1, qy1 = b.ground(66, 90)
    if look != "snow":
        d.rectangle([qx0, qy0, qx1, qy1], c("#8db866"))
        d.line([qx0, qy0, qx1, qy1], PATH)
        d.line([qx1, qy0, qx0, qy1], PATH)
        d.rectangle([(qx0 + qx1) // 2 - 1, qy0, (qx0 + qx1) // 2 + 1, qy1], PATH)
    # The clock tower over the middle of the back hall.
    troof, twall = b.box(42, 14, 54, 26, 5 * STOREY + 6)
    d.rectangle(twall, wall_col)
    tx0, ty0, tx1, ty1 = twall
    tcx = (tx0 + tx1) // 2
    d.ellipse([tcx - 3, ty0 + 3, tcx + 3, ty0 + 9], c("#f2f2ea"), OUTLINE)
    d.line([tcx, ty0 + 6, tcx, ty0 + 4], OUTLINE)
    d.line([tcx, ty0 + 6, tcx + 2, ty0 + 6], OUTLINE)
    d.rectangle(twall, outline=OUTLINE)
    rx0, ry0, rx1, ry1 = troof
    d.polygon([(rx0, ry1), ((rx0 + rx1) // 2, ry0 - 6), (rx1, ry1)], SNOW_ROOF[0] if look == "snow" else c("#5a6a70"), OUTLINE)
    return b


# Justice: a police headquarters, a courthouse and a jail.


def police_hq(look, v):
    """Police headquarters on 3 by 2 tiles: three storeys of grey stone, a blue lamp, a flag and patrol cars out front."""
    b = Building(3, 2, height=3 * STOREY + 8)
    d = b.d
    rng = random.Random(8800)
    gx0, gy0 = b.ground(4, 48)
    gx1, gy1 = b.ground(92, 61)
    d.rectangle([gx0, gy0, gx1, gy1], c("#b8b2a6") if look != "snow" else c("#dfe5ea"))
    roof, wall = b.box(4, 6, 92, 46, 3 * STOREY + 2)
    wall_col = c("#a8a69e")
    d.rectangle(wall, wall_col)
    x0, y0, x1, y1 = wall
    for yy in range(y0 + 3, y1, 4):
        d.line([x0 + 1, yy, x1 - 1, yy], shade(wall_col, 0.92))
    cx = (x0 + x1) // 2
    windows(d, wall, 3, glass=c("#46586a"), sill=TRIM, every=5, width=2, height=3, skip=[(cx - 6, cx + 6)])
    d.rectangle([cx - 4, y1 - 8, cx + 4, y1], c("#2a2a30"))
    d.rectangle([cx - 6, y1 - 9, cx + 6, y1 - 9], STONE)
    for k in (-6, 6):
        d.rectangle([cx + k, y1 - 12, cx + k + 1, y1 - 10], c("#3f6fd8"))
        d.point((cx + k, y1 - 12), c("#a8c4ff"))
    d.rectangle(wall, outline=OUTLINE)
    flat_roof(b.img, roof, look, rng, [("stack", 8, 4), ("hatch", 40, 10), ("vent", 70, 8), ("vent", 20, 20)], parapet=STONE)
    # The flag, and the patrol cars at the kerb.
    fx, fy = b.ground(20, 54)
    d.line([fx, fy, fx, fy - 22], c("#d0d0d0"))
    d.rectangle([fx + 1, fy - 22, fx + 6, fy - 19], c("#c0392b"))
    d.rectangle([fx + 3, fy - 22, fx + 4, fy - 19], c("#f2f2ea"))
    b.casters.append((1, 19, 53, 21, 55, 22))
    for k, px in enumerate((54, 68)):
        x, y = b.ground(px, 52)
        d.rectangle([x, y, x + 10, y + 5], c("#1e2a44") if k == 0 else c("#f2f2ee"), OUTLINE)
        d.rectangle([x + 3, y + 1, x + 7, y + 3], c("#6c7f8a"))
        d.point((x + 5, y), c("#3f6fd8"))
    return b


def courthouse(look, v):
    """A courthouse on 2 by 2 tiles: pale stone, a row of columns under a pediment, wide steps, and a dome or a clock."""
    b = Building(2, 2, height=3 * STOREY + 14)
    d = b.d
    gx0, gy0 = b.ground(2, 2)
    gx1, gy1 = b.ground(61, 61)
    d.rectangle([gx0, gy0, gx1, gy1], SNOW_GROUND if look == "snow" else c("#86a85e"))
    px, py = b.ground(26, 48)
    d.rectangle([px, py, px + 11, gy1], PATH if look != "snow" else c("#cfd8df"))
    roof, wall = b.box(6, 8, 58, 46, 3 * STOREY)
    wall_col = c("#e0d8c2") if v == 0 else c("#cfc0a0")
    d.rectangle(wall, wall_col)
    x0, y0, x1, y1 = wall
    cx = (x0 + x1) // 2
    windows(d, wall, 3, glass=c("#46586a"), sill=TRIM, every=6, width=2, height=4, skip=[(cx - 14, cx + 14)])
    # The portico: columns the height of the front, a pediment, steps.
    d.rectangle([cx - 14, y0 + 6, cx + 14, y1], c("#efe8d8"))
    for k in range(-13, 14, 4):
        d.line([cx + k, y0 + 8, cx + k, y1], c("#c9c0aa"))
    d.polygon([(cx - 16, y0 + 6), (cx, y0 - 2), (cx + 16, y0 + 6)], TRIM, OUTLINE)
    d.rectangle([cx - 2, y1 - 7, cx + 2, y1], c("#4a3226"))
    for k in range(3):
        d.rectangle([cx - 15 - k, y1 + 1 + k, cx + 15 + k, y1 + 1 + k], STONE)
    d.rectangle(wall, outline=OUTLINE)
    flat_roof(b.img, roof, look, random.Random(8810 + v), [], parapet=STONE)
    rx0, ry0, rx1, ry1 = roof
    mx, my = (rx0 + rx1) // 2, (ry0 + ry1) // 2
    if v == 0:
        d.ellipse([mx - 9, my - 9, mx + 9, my + 9], SNOW_ROOF[0] if look == "snow" else c("#6e8f86"), OUTLINE)
        d.ellipse([mx - 4, my - 6, mx + 1, my - 1], c("#8fb0a6") if look != "snow" else c("#ffffff"))
        d.rectangle([mx - 1, my - 12, mx + 1, my - 9], c("#d9b44a"))
    else:
        d.rectangle([mx - 6, my - 6, mx + 6, my + 6], wall_col, OUTLINE)
        d.ellipse([mx - 4, my - 4, mx + 4, my + 4], c("#f2f2ea"), OUTLINE)
        d.line([mx, my, mx, my - 3], OUTLINE)
        d.line([mx, my, mx + 2, my], OUTLINE)
    b.casters.append((1, 22, 18, 42, 38, 3 * STOREY + 14))
    return b


def jail(look, v):
    """A jail on 3 by 3 tiles: a high wall round a bare yard, a long cell block of small barred windows, and a watch tower."""
    b = Building(3, 3, height=4 * STOREY + 4)
    d = b.d
    rng = random.Random(8820)
    gx0, gy0 = b.ground(2, 2)
    gx1, gy1 = b.ground(93, 93)
    d.rectangle([gx0, gy0, gx1, gy1], SNOW_GROUND if look == "snow" else c("#a89a7e"))
    # The cell block across the back, and the gatehouse at the front.
    roof, wall = b.box(10, 8, 86, 40, 3 * STOREY)
    brick(d, wall, c("#8a7a6a"))
    x0, y0, x1, y1 = wall
    for row in range(3):
        for xx in range(x0 + 3, x1 - 2, 4):
            yy = y0 + 3 + row * STOREY
            d.rectangle([xx, yy, xx + 1, yy + 2], c("#2a2a30"))
    d.rectangle(wall, outline=OUTLINE)
    flat_roof(b.img, roof, look, rng, [("vent", 10, 6), ("vent", 40, 6), ("vent", 60, 6)], parapet=c("#6a6058"))
    groof, gwall = b.box(38, 76, 58, 90, STOREY + 4)
    brick(d, gwall, c("#7a6a5a"))
    gx0_, gy0_, gx1_, gy1_ = gwall
    d.rectangle([(gx0_ + gx1_) // 2 - 3, gy1_ - 7, (gx0_ + gx1_) // 2 + 3, gy1_], c("#3a3a40"))
    for k in range((gx0_ + gx1_) // 2 - 2, (gx0_ + gx1_) // 2 + 3, 2):
        d.line([k, gy1_ - 7, k, gy1_], c("#8a8a90"))
    d.rectangle(gwall, outline=OUTLINE)
    flat_roof(b.img, groof, look, rng, [], parapet=c("#6a6058"))
    # The wall round the yard.
    wx0, wy0 = b.ground(4, 44)
    wx1, wy1 = b.ground(91, 91)
    for (a, bb) in (((wx0, wy0), (wx0, wy1)), ((wx1, wy0), (wx1, wy1)), ((wx0, wy1), (wx1, wy1))):
        d.line([a, bb], c("#6a6058"), width=3)
    b.casters.append((1, 4, 44, 6, 91, 10))
    b.casters.append((1, 89, 44, 91, 91, 10))
    # The watch tower in the corner.
    troof, twall = b.box(80, 72, 90, 82, 4 * STOREY + 2)
    brick(d, twall, c("#7a6a5a"))
    d.rectangle(twall, outline=OUTLINE)
    d.rectangle(troof, SNOW_ROOF[0] if look == "snow" else c("#4a4a50"), OUTLINE)
    tx0, ty0, tx1, ty1 = twall
    d.rectangle([tx0 + 2, ty0 + 2, tx1 - 2, ty0 + 4], c("#d9c060"))
    return b


# ---- Other sizes ---------------------------------------------------------------
# Every zone has buildings for a lot of one tile, two (running back from the
# road, deep, or along it, wide: the same building turned) and four, so a gap
# of any shape has something to grow. The 1 by 2s take [wide].

PARKING = c("#77777a")
GLASSHOUSE = c("#c8dce4")


def walls_of(d, wall, kind, col):
    """Brick, painted boards, dressed stone or glass curtain wall."""
    if kind == "brick":
        brick(d, wall, col)
    elif kind == "siding":
        siding(d, wall, col)
    elif kind == "glass":
        d.rectangle(wall, col)
        for xx in range(wall[0] + 3, wall[2], 4):
            d.line([xx, wall[1] + 1, xx, wall[3] - 1], shade(col, 0.75))
    else:
        d.rectangle(wall, col)
        for yy in range(wall[1] + 3, wall[3], STOREY):
            d.line([wall[0] + 1, yy, wall[2] - 1, yy], shade(col, 0.9))


def block(b, look, box, storeys, kind, col, roof="flat", roof_col=None, every=4, shop=None, has_door=True,
          features=(), seed=0, glass=WINDOW, ledge=True):
    """One block on the footprint [box]: walls, windows, a shop front if [shop] is its awning colour, and a roof."""
    d = b.d
    x0, y0, x1, y1 = box
    roof_r, wall = b.box(x0, y0, x1, y1, storeys * STOREY + 2)
    walls_of(d, wall, kind, col)
    if shop is not None:
        if storeys > 1:
            windows(d, (wall[0], wall[1], wall[2], wall[3] - STOREY - 2), storeys - 1, sill=TRIM, every=every, glass=glass)
        shopfront(d, wall, shop, look)
    else:
        windows(d, wall, storeys, sill=TRIM if kind != "glass" else None, every=every, glass=glass, skip_door=has_door)
        if has_door:
            door(d, wall)
    d.rectangle(wall, outline=OUTLINE)
    if roof == "flat":
        if ledge:
            cornice(d, wall, shade(col, 1.12) if kind == "stone" else CORNICE)
        flat_roof(b.img, roof_r, look, random.Random(seed), features, parapet=CORNICE if ledge else PARAPET)
        # Something on top by the version, so versions differ in shape: a sign over a shop, a light well down through
        # a deep block, or a storey set back on a tall one.
        rw = x1 - x0
        rh = y1 - y0
        top = seed % 4
        if top == 1 and shop is not None and rw >= 12:
            roof_sign(d, roof_r, shade(shop, 0.8), max(3, min(9, rw // 4)))
        elif top == 2 and rw >= 18 and rh >= 14:
            light_well(d, roof_r, look, col)
        elif top == 3 and storeys >= 3 and rw >= 14 and rh >= 12:
            setback(b, look, (x0 + 4, y0 + 3, x1 - 4, y1 - 5), storeys * STOREY + 7, shade(col, 1.06), random.Random(seed + 1),
                    glass=glass, base=storeys * STOREY + 2)
    elif roof == "gable_ew":
        gable_ew(d, roof_r, roof_col, look)
    elif roof == "gable_ns":
        gable_ns(d, roof_r, wall, roof_col, col, look)
        b.casters[-1] = (1, x0, y0, x1 + 1, y1 + 1, storeys * STOREY + 2 + (x1 - x0) // 3)
    elif roof == "saw":
        rx0, ry0, rx1, ry1 = roof_r
        for xx in range(rx0, rx1 - 4, 7):
            top = SNOW_ROOF[0] if look == "snow" else SAW[0]
            d.rectangle([xx, ry0, xx + 3, ry1], top)
            d.rectangle([xx + 4, ry0, min(xx + 6, rx1), ry1], SNOW_ROOF[2] if look == "snow" else c("#b8c4ca"))
        d.rectangle(roof_r, outline=OUTLINE)
    return roof_r, wall


def parking(b, look, x0, y0, x1, y1, seed):
    """A car park: tarmac, its bays marked, and a few cars."""
    gx0, gy0 = b.ground(x0, y0)
    gx1, gy1 = b.ground(x1, y1)
    b.d.rectangle([gx0, gy0, gx1, gy1], SNOW_GROUND if look == "snow" else PARKING)
    rng = random.Random(seed)
    for xx in range(gx0 + 2, gx1 - 2, 5):
        b.d.line([xx, gy0 + 1, xx, gy0 + 3], c("#e8e4da"))
        if rng.random() < 0.6:
            b.d.rectangle([xx + 1, gy0 + 1, xx + 3, gy0 + 4], rng.choice([c("#6b2330"), c("#263a5a"), c("#d9cfb0"), c("#2e4a3a")]))


def stacks(b, look, x0, y0, x1, y1, col):
    """Piles of timber or bricks in a yard, in tile pixels."""
    for yy in range(y0, y1, 5):
        for xx in range(x0, x1, 7):
            gx, gy = b.ground(xx, yy)
            top = SNOW if look == "snow" else shade(col, 1.15)
            b.d.rectangle([gx, gy - 2, gx + 5, gy + 2], col, OUTLINE)
            b.d.line([gx + 1, gy - 1, gx + 4, gy - 1], top)


def lot_size(wide):
    return Building(2, 1, height=0) if wide else Building(1, 2, height=0)


def sized(w, h, height):
    return Building(w, h, height=height)


# Homes.

def cabin(look, v):
    """A rural cabin on one lot: a one-storey house of boards, a woodpile, a garden and a tree."""
    b = Building(height=STOREY + 12)
    lot_ground(b, look, 4, 20, 27, 27, c("#a8946c"))
    house_shape(b, look, v, (6, 8, 22, 19), 1, [TIMBER, c("#c9b48a"), SIDING[2]][v], SHINGLE[v % 3], v == 1, ("porch", "stack"))
    stacks(b, look, 24, 12, 29, 18, LUMBER)
    if look not in ("snow", "winter"):
        crop_rows(b, look, 3, 23, 14, 29, 9400 + v)
    rural_trees(b, look, v, [(27, 28, 4)] if v != 2 else [(4, 28, -4)], 9410 + v)
    return b


def smallholding(look, v, wide):
    """A cottage, a shed and a vegetable plot on a long rural lot, along the road or back from it."""
    b = Building(2, 1, height=STOREY + 12) if wide else Building(1, 2, height=STOREY + 12)
    if wide:
        house_shape(b, look, v, (4, 8, 22, 22), 1, SIDING[(v + 1) % 4], SHINGLE[v % 3], False, ("porch", "stack"))
        barn(b, look, 40, 8, 54, 22, STOREY + 4, IRON_ROOF if v else SHINGLE[1])
        crop_rows(b, look, 26, 24, 60, 30, 9420 + v)
        rail_fence(b, look, 24, 3, 61, 30)
    else:
        barn(b, look, 8, 6, 22, 16, STOREY + 2, IRON_ROOF if v else SHINGLE[1])
        crop_rows(b, look, 4, 20, 27, 34, 9420 + v)
        rail_fence(b, look, 2, 18, 29, 36)
        house_shape(b, look, v, (6, 42, 25, 56), 1, SIDING[(v + 1) % 4], SHINGLE[v % 3], False, ("porch", "stack"))
        lot_ground(b, look, 13, 57, 17, 63, c("#a8946c"))
    return b


def villa(look, v, wide):
    """A big detached house with a long garden: beside it along the road, or behind it."""
    b = Building(2, 1, height=2 * STOREY + 12) if wide else Building(1, 2, height=2 * STOREY + 12)
    wall_col = [c("#efe6d0"), BRICK20, c("#d6c9a8")][v]
    if wide:
        lawn_ground(b, look, 2, 2, 61, 29, 9430 + v)
        house_shape(b, look, v, (4, 8, 30, 24), 2, wall_col, SHINGLE[2 - v % 3], False, ("porch", "dormers", "bay_left"), brick_walls=v == 1)
        rural_trees(b, look, v, [(42, 14, 6), (56, 24, 5), (36, 28, -4)], 9440 + v)
    else:
        lawn_ground(b, look, 2, 2, 29, 61, 9430 + v)
        rural_trees(b, look, v, [(8, 12, 6), (22, 20, 5), (14, 30, -4)], 9440 + v)
        house_shape(b, look, v, (4, 38, 28, 54), 2, wall_col, SHINGLE[2 - v % 3], False, ("porch", "dormers", "bay_right"), brick_walls=v == 1)
        lot_ground(b, look, 14, 55, 18, 63, DRIVE)
    return b


def mansion(look, v):
    """A grand house on 2 by 2 tiles: three storeys of stone or brick, a drive round to the door, lawns, a hedge and old trees."""
    b = Building(2, 2, height=3 * STOREY + 14)
    d = b.d
    lawn_ground(b, look, 2, 2, 61, 61, 9450 + v)
    lot_ground(b, look, 28, 40, 35, 63, DRIVE)
    lot_ground(b, look, 16, 38, 47, 42, DRIVE)
    col = [c("#d8cfba"), BRICK20][v]
    house_shape(b, look, v, (12, 16, 51, 36), 3, col, SHINGLE[2], False, ("porch", "dormers", "stack", "bay_left", "bay_right"), brick_walls=v == 1)
    rural_trees(b, look, v, [(6, 50, 7), (56, 52, 7), (8, 10, 6), (56, 10, -6)], 9460 + v)
    return b


def terrace(look, v, wide):
    """A row of narrow brick houses, each with its door and stoop; turned, a short row with back yards and sheds behind."""
    b = Building(2, 1, height=2 * STOREY + 10) if wide else Building(1, 2, height=2 * STOREY + 10)
    d = b.d
    cols = [BRICK, BRICK20, c("#8a5a44"), c("#b0704e"), c("#7a4a3c"), c("#a8583f")]
    spans = [(2 + k * 10, 11 + k * 10) for k in range(6)] if wide else [(2, 10), (11, 20), (21, 29)]
    y0, y1 = (7, 25) if wide else (39, 57)
    if not wide:
        lot_ground(b, look, 2, 4, 29, 36, c("#8a8478"))
        for k, (x0, x1) in enumerate(spans):
            roof, wall = b.box(x0 + 2, 10, x1 - 2, 16, STOREY - 1)
            d.rectangle(wall, TIMBER)
            d.rectangle(wall, outline=OUTLINE)
            d.rectangle(roof, SNOW_ROOF[0] if look == "snow" else c("#5a5a60"))
    for k, (x0, x1) in enumerate(spans):
        roof, wall = b.box(x0, y0, x1, y1, 2 * STOREY + 2)
        brick(d, wall, cols[(k + v) % len(cols)])
        wx0, wy0, wx1, wy1 = wall
        d.rectangle([wx0 + 2, wy0 + 2, wx0 + 3, wy0 + 4], WINDOW)
        d.rectangle([wx1 - 3, wy0 + 2, wx1 - 2, wy0 + 4], WINDOW)
        d.rectangle([wx1 - 3, wy1 - 5, wx1 - 2, wy1 - 3], WINDOW)
        d.rectangle([wx0 + 2, wy1 - 5, wx0 + 3, wy1], DOOR)
        d.line([wx0 + 1, wy1 + 1, wx0 + 4, wy1 + 1], STONE)
        d.rectangle(wall, outline=OUTLINE)
        gable_ew(d, roof, SHINGLE[(k + v) % 3], look)
    return b


def court_tenements(look, v):
    """Four tenements round a court on 2 by 2 tiles, three storeys of brick, a way in from the street."""
    b = Building(2, 2, height=3 * STOREY + 4)
    lot_ground(b, look, 20, 20, 43, 43, c("#8a8478"))
    col = [BRICK, c("#8a5a44")][v]
    block(b, look, (2, 2, 61, 17), 3, "brick", col, every=4, seed=9470 + v, features=[("stack", 4, 3), ("stack", 30, 3), ("stack", 52, 3)])
    block(b, look, (2, 18, 17, 61), 3, "brick", shade(col, 1.05), every=4, has_door=False, seed=9471 + v)
    block(b, look, (46, 18, 61, 61), 3, "brick", shade(col, 0.95), every=4, has_door=False, seed=9472 + v)
    block(b, look, (18, 46, 27, 61), 3, "brick", col, every=4, has_door=False, seed=9473 + v)
    block(b, look, (36, 46, 45, 61), 3, "brick", col, every=4, has_door=False, seed=9474 + v)
    return b


def slab(look, v, wide):
    """A long slab of flats, six storeys, balconies in rows: along the road, or running back from it."""
    b = Building(2, 1, height=6 * STOREY + 6) if wide else Building(1, 2, height=6 * STOREY + 6)
    col = [c("#cfc6b0"), c("#b8b0a0"), c("#d8d4c8")][v]
    if not wide:
        lawn_ground(b, look, 2, 2, 29, 61, 9480 + v)
    box = (2, 4, 61, 27) if wide else (4, 6, 27, 58)
    roof, wall = block(b, look, box, 6, "stone", col, every=3, seed=9481 + v, features=[("hatch", 6, 4), ("vent", 20, 6)])
    d = b.d
    for yy in range(wall[1] + 5, wall[3] - 4, STOREY):
        d.line([wall[0] + 2, yy, wall[2] - 2, yy], shade(col, 0.7))
    return b


# Shops.

def storefronts(look, v, wide):
    """A row of one-storey shops under awnings; turned, two shops with a store shed behind."""
    b = Building(2, 1, height=STOREY + 10) if wide else Building(1, 2, height=STOREY + 10)
    if wide:
        for k in range(4):
            block(b, look, (2 + k * 15, 8, 15 + k * 15, 26), 1, "brick" if (k + v) % 2 else "siding", PAINT[(k + v) % len(PAINT)] if (k + v) % 2 == 0 else BRICKS[(k + v) % len(BRICKS)],
                  shop=AWNINGS[(k + v) % len(AWNINGS)], seed=9500 + k, features=[("vent", 4, 3)])
    else:
        lot_ground(b, look, 2, 4, 29, 30, c("#8a8478"))
        roof, wall = b.box(4, 8, 27, 20, STOREY + 2)
        siding(b.d, wall, PAINT[v % len(PAINT)])
        b.d.rectangle(wall, outline=OUTLINE)
        gable_ew(b.d, roof, IRON_ROOF, look)
        stacks(b, look, 6, 24, 26, 29, CRATE)
        for k in range(2):
            block(b, look, (2 + k * 15, 38, 15 + k * 15, 58), 1, "brick", BRICKS[(k + v) % len(BRICKS)], shop=AWNINGS[(k + 2 * v) % len(AWNINGS)], seed=9510 + k)
    return b


def covered_market(look, v):
    """A market hall on 2 by 2 tiles: iron and glass over brick, stalls with awnings along the front."""
    b = Building(2, 2, height=3 * STOREY + 8)
    d = b.d
    roof, wall = b.box(4, 8, 59, 46, 3 * STOREY + 2)
    brick(d, wall, [BRICKS[0], BRICKS[2]][v])
    for xx in range(wall[0] + 4, wall[2] - 3, 8):
        d.arc([xx, wall[1] + 2, xx + 6, wall[1] + 10], 180, 360, TRIM)
        d.rectangle([xx + 1, wall[1] + 6, xx + 5, wall[3] - 1], PLATE_GLASS)
    d.rectangle(wall, outline=OUTLINE)
    rx0, ry0, rx1, ry1 = roof
    d.rectangle(roof, SNOW_ROOF[0] if look == "snow" else GLASSHOUSE)
    for xx in range(rx0 + 3, rx1, 4):
        d.line([xx, ry0, xx, ry1], c("#5a6670"))
    d.line([rx0, (ry0 + ry1) // 2, rx1, (ry0 + ry1) // 2], c("#3e4850"))
    d.rectangle(roof, outline=OUTLINE)
    lot_ground(b, look, 2, 48, 61, 61, c("#a49a88"))
    for k, xx in enumerate(range(6, 58, 10)):
        block(b, look, (xx, 51, xx + 7, 58), 1, "siding", PAINT[(k + v) % len(PAINT)], shop=AWNINGS[(k + v) % len(AWNINGS)], seed=9520 + k, ledge=False)
    return b


def arcade(look, v, wide):
    """Shops either side of a glazed arcade, two storeys of brick or stone, the arcade's mouth on the street."""
    b = Building(2, 1, height=3 * STOREY + 6) if wide else Building(1, 2, height=3 * STOREY + 6)
    d = b.d
    col = [BRICKS[1], GREY_STONE, BRICKS[4]][v]
    kind = "stone" if col == GREY_STONE else "brick"
    box = (1, 5, 62, 27) if wide else (2, 6, 29, 59)
    roof, wall = block(b, look, box, 3, kind, col, shop=AWNINGS[(v + 1) % len(AWNINGS)], seed=9530 + v)
    rx0, ry0, rx1, ry1 = roof
    if wide:
        mx = (rx0 + rx1) // 2
        d.rectangle([mx - 4, ry0 + 1, mx + 4, ry1 - 1], SNOW_ROOF[1] if look == "snow" else GLASSHOUSE, OUTLINE)
        d.rectangle([mx - 3, wall[3] - 8, mx + 3, wall[3]], c("#2a2a30"))
    else:
        d.rectangle([rx0 + 10, ry0 + 2, rx1 - 10, ry1 - 2], SNOW_ROOF[1] if look == "snow" else GLASSHOUSE, OUTLINE)
    sign(d, wall[0] + 6, wall[2] - 6, wall[1] + 1, AWNINGS[(v + 4) % len(AWNINGS)])
    return b


def emporium(look, v):
    """A big store on 2 by 2 tiles: four storeys, shop windows all along, a corner tower with a clock."""
    b = Building(2, 2, height=6 * STOREY + 8)
    col = [c("#d6cdb8"), BRICKS[2]][v]
    kind = "stone" if v == 0 else "brick"
    roof, wall = block(b, look, (2, 6, 61, 59), 4, kind, col, shop=AWNINGS[(v + 2) % len(AWNINGS)], every=5, seed=9540 + v,
                       features=[("skylight", 10, 10), ("skylight", 30, 10), ("tank", 40, 30), ("hatch", 12, 34)])
    block(b, look, (48, 46, 61, 59), 6, kind, shade(col, 0.95), every=4, has_door=False, seed=9541 + v)
    sign(b.d, wall[0] + 6, wall[2] - 20, wall[3] - STOREY - 4, c("#2b3440"))
    return b


def store_block(look, v, wide):
    """Seven storeys of shops and offices, shop windows below and a cornice above: along the road or running back."""
    b = Building(2, 1, height=7 * STOREY + 8) if wide else Building(1, 2, height=7 * STOREY + 8)
    col = [c("#c98f6e"), c("#d6cdb8"), c("#b9b8b0")][v]
    box = (2, 4, 61, 27) if wide else (3, 6, 28, 58)
    block(b, look, box, 7, "stone", col, shop=AWNINGS[v % len(AWNINGS)], every=4, seed=9550 + v, features=[("tank", 10, 8), ("stack", 4, 3)])
    return b


def roadside_deep(look, v):
    """The roadhouse turned: the diner on the road and its car park behind."""
    b = Building(1, 2, height=STOREY + 10)
    parking(b, look, 3, 4, 28, 30, 9560 + v)
    block(b, look, (3, 38, 28, 57), 1, "siding", [c("#e8e0c8"), c("#c8423a")][v], shop=AWNINGS[(v + 3) % len(AWNINGS)], seed=9561 + v)
    sign(b.d, 6, 25, b.lift + 38 - STOREY - 1, c("#c8423a") if v == 0 else c("#2f5f5a"))
    return b


def feed_store(look, v):
    """A farm supply store on 2 by 2 tiles: a barn of a shop, grain bins, and sacks and fencing stacked in the yard."""
    b = Building(2, 2, height=3 * STOREY + 8)
    lot_ground(b, look, 2, 30, 61, 61, c("#a8946c"))
    barn(b, look, 6, 12, 36, 32, STOREY + 8, IRON_ROOF if v else SHINGLE[1], [c("#9a3a2e"), c("#8c8a84")][v])
    silo(b, look, 42, 16, 7, 3 * STOREY)
    silo(b, look, 52, 16, 7, 3 * STOREY)
    stacks(b, look, 8, 42, 34, 56, CRATE)
    rail_fence(b, look, 40, 38, 60, 58)
    return b


# Industry.

def lumber_yard(look, v, wide):
    """A lumber yard: a saw shed with an iron roof and stacks of boards drying in the yard."""
    b = Building(2, 1, height=STOREY + 10) if wide else Building(1, 2, height=STOREY + 10)
    if wide:
        lot_ground(b, look, 2, 2, 61, 29, c("#8a8070"))
        block(b, look, (3, 8, 24, 26), 1, "siding", PAINT[v % len(PAINT)], roof="gable_ew", roof_col=IRON_ROOF, has_door=True, seed=9600 + v)
        stacks(b, look, 28, 8, 60, 28, LUMBER)
    else:
        lot_ground(b, look, 2, 2, 29, 61, c("#8a8070"))
        stacks(b, look, 4, 6, 28, 32, LUMBER)
        block(b, look, (4, 38, 27, 57), 1, "siding", PAINT[v % len(PAINT)], roof="gable_ew", roof_col=IRON_ROOF, has_door=True, seed=9600 + v)
    return b


def brickworks(look, v):
    """A brickworks on 2 by 2 tiles: bottle kilns, a tall chimney, drying sheds and stacks of bricks."""
    b = Building(2, 2, height=46)
    d = b.d
    lot_ground(b, look, 2, 2, 61, 61, c("#9a6a4e"))
    stacks(b, look, 6, 46, 58, 60, BRICK)
    block(b, look, (4, 26, 34, 40), 1, "siding", TIMBER, roof="gable_ew", roof_col=IRON_ROOF, has_door=False, seed=9610 + v)
    for k, kx in enumerate((40, 52)):
        roof, wall = b.box(kx - 5, 24, kx + 5, 34, 3 * STOREY)
        d.rectangle(wall, BRICKS[(k + v) % len(BRICKS)])
        d.rectangle(wall, outline=OUTLINE)
        d.ellipse([roof[0], roof[1], roof[2], roof[3]], SNOW_ROOF[0] if look == "snow" else c("#3a3a3e"), outline=OUTLINE)
    chimney(b, 20, 20, 46, BRICKS[v % len(BRICKS)], look)
    return b


def sheds(look, v, wide):
    """Goods sheds: a long run of loading doors under an iron roof, along the road or back from it."""
    b = Building(2, 1, height=2 * STOREY + 4) if wide else Building(1, 2, height=2 * STOREY + 4)
    d = b.d
    box = (1, 4, 62, 27) if wide else (3, 4, 28, 59)
    roof, wall = b.box(*box, 2 * STOREY + 2)
    brick(d, wall, BRICKS[(v + 1) % len(BRICKS)])
    x0, y0, x1, y1 = wall
    for xx in range(x0 + 3, x1 - 6, 9):
        d.rectangle([xx, y1 - 7, xx + 5, y1], TIMBER, OUTLINE)
    d.rectangle(wall, outline=OUTLINE)
    if wide:
        gable_ew(d, roof, IRON_ROOF if v != 1 else c("#4f6b52"), look)
    else:
        flat_roof(b.img, roof, look, random.Random(9620 + v), [("vent", 6, 8), ("vent", 16, 20), ("vent", 6, 34)])
    return b


def storehouses(look, v):
    """Two big warehouses on 2 by 2 tiles with a yard between for the carts and lorries."""
    b = Building(2, 2, height=3 * STOREY + 4)
    lot_ground(b, look, 2, 2, 61, 61, c("#8a8478"))
    block(b, look, (2, 4, 61, 22), 3, "brick", BRICKS[v % len(BRICKS)], every=5, seed=9630 + v, features=[("vent", 8, 6), ("vent", 30, 6), ("hatch", 48, 6)])
    block(b, look, (2, 40, 40, 60), 2, "brick", BRICKS[(v + 2) % len(BRICKS)], every=5, seed=9631 + v, features=[("vent", 10, 6)])
    stacks(b, look, 44, 44, 60, 58, CRATE)
    return b


def foundry(look, v):
    """A foundry on one lot: a brick casting shed, its roof vents glowing, a tall chimney."""
    b = Building(height=40)
    d = b.d
    roof, wall = block(b, look, (2, 8, 25, 28), 2, "brick", BRICKS[(v + 3) % len(BRICKS)], roof="gable_ew", roof_col=IRON_ROOF, seed=9640 + v)
    d.rectangle([roof[0] + 6, roof[1] + 1, roof[0] + 12, roof[1] + 2], c("#e8803a") if look != "snow" else SNOW_ROOF[1])
    chimney(b, 28, 10, 40, BRICK, look)
    return b


def machine_shop(look, v, wide):
    """A machine shop under a sawtooth roof, a chimney at the end: along the road or running back."""
    b = Building(2, 1, height=36) if wide else Building(1, 2, height=36)
    box = (1, 4, 56, 27) if wide else (2, 10, 29, 59)
    block(b, look, box, 3, "brick", BRICKS[v % len(BRICKS)], roof="saw", every=5, glass=c("#6c7f8a"), seed=9650 + v)
    if wide:
        chimney(b, 60, 12, 36, BRICK, look)
    else:
        chimney(b, 8, 6, 36, BRICK, look)
    return b


# Offices.

def chambers(look, v, wide):
    """Office chambers: three storeys of stone or brick with a grand door, along the road or with a yard behind."""
    b = Building(2, 1, height=3 * STOREY + 6) if wide else Building(1, 2, height=3 * STOREY + 6)
    col = [c("#d8d0bc"), BRICKS[2], c("#c4b49a")][v]
    kind = "brick" if col == BRICKS[2] else "stone"
    if wide:
        block(b, look, (2, 6, 61, 27), 3, kind, col, every=5, seed=9700 + v, features=[("stack", 6, 3), ("stack", 50, 3)])
    else:
        lawn_ground(b, look, 2, 2, 29, 30, 9701 + v)
        rural_trees(b, look, v, [(10, 18, 5), (22, 24, 4)], 9702 + v)
        block(b, look, (3, 36, 28, 58), 3, kind, col, every=5, seed=9700 + v, features=[("stack", 4, 3)])
    return b


def office_park(look, v):
    """A 1950s office park on 2 by 2 tiles: two low glass-fronted blocks among lawns and a car park."""
    b = Building(2, 2, height=2 * STOREY + 4)
    lawn_ground(b, look, 2, 2, 61, 61, 9710 + v)
    parking(b, look, 4, 46, 59, 60, 9711 + v)
    block(b, look, (4, 6, 40, 20), 2, "glass", c("#8fa8b8"), seed=9712 + v, ledge=False, features=[("vent", 10, 4)])
    block(b, look, (30, 26, 59, 40), 2, "glass", c("#a8b8c0"), seed=9713 + v, ledge=False, features=[("vent", 6, 4)])
    rural_trees(b, look, v, [(10, 36, 5), (52, 14, 5)], 9714 + v)
    return b


def office_row(look, v, wide):
    """Five storeys of offices in pale stone, tall windows in bays: along the road or running back."""
    b = Building(2, 1, height=5 * STOREY + 8) if wide else Building(1, 2, height=5 * STOREY + 8)
    col = [c("#d8d0bc"), c("#c4b49a"), c("#cfc6b0")][v]
    box = (2, 4, 61, 27) if wide else (3, 6, 28, 58)
    block(b, look, box, 5, "stone", col, every=4, seed=9720 + v, features=[("tank", 16, 8), ("vent", 4, 4)])
    return b


def office_court(look, v):
    """Offices round a court on 2 by 2 tiles, five storeys of stone, a grand entrance on the street."""
    b = Building(2, 2, height=5 * STOREY + 8)
    col = [c("#d6cdb8"), c("#c98f6e")][v]
    lawn_ground(b, look, 20, 20, 43, 43, 9730 + v)
    block(b, look, (2, 2, 61, 17), 5, "stone", col, every=4, has_door=False, seed=9731 + v, features=[("tank", 30, 6)])
    block(b, look, (2, 18, 17, 61), 5, "stone", shade(col, 1.04), every=4, has_door=False, seed=9732 + v)
    block(b, look, (46, 18, 61, 61), 5, "stone", shade(col, 0.96), every=4, has_door=False, seed=9733 + v)
    block(b, look, (18, 46, 45, 61), 5, "stone", col, every=4, seed=9734 + v)
    return b


def slim_offices(look, v):
    """A narrow office tower on one lot: nine storeys, piers running up, a crown on top."""
    b = Building(height=9 * STOREY + 8)
    col = [c("#cfc4a8"), c("#b8876a")][v]
    roof, wall = block(b, look, (5, 6, 26, 26), 9, "stone", col, every=4, seed=9740 + v, features=[("tank", 8, 6)])
    d = b.d
    for xx in range(wall[0] + 4, wall[2] - 2, 5):
        d.line([xx, wall[1] + 2, xx, wall[3] - 6], shade(col, 0.85))
    return b


def office_slab(look, v, wide):
    """A slab of offices, ten storeys of glass and stone bands: along the road or running back."""
    b = Building(2, 1, height=10 * STOREY + 6) if wide else Building(1, 2, height=10 * STOREY + 6)
    box = (2, 4, 61, 27) if wide else (3, 6, 28, 58)
    roof, wall = block(b, look, box, 10, "glass", [c("#7f98a8"), c("#8a9a90")][v], seed=9750 + v, ledge=False, features=[("hatch", 6, 6), ("vent", 20, 8)])
    d = b.d
    for yy in range(wall[1] + 4, wall[3], STOREY):
        d.line([wall[0] + 1, yy, wall[2] - 1, yy], c("#d8d4c8"))
    return b


# Homes over shops.

def twin_shophouses(look, v, wide):
    """Two shophouses side by side; turned, one on the street and a yard and store behind."""
    b = Building(2, 1, height=2 * STOREY + 8) if wide else Building(1, 2, height=2 * STOREY + 8)
    if wide:
        for k in range(2):
            block(b, look, (3 + k * 30, 6, 29 + k * 30, 26), 2, "brick", BRICKS[(k + v) % len(BRICKS)], shop=AWNINGS[(k + 2 * v) % len(AWNINGS)], every=5,
                  seed=9800 + k, features=[("stack", 3, 3)])
    else:
        lot_ground(b, look, 3, 4, 28, 30, c("#8a8478"))
        stacks(b, look, 6, 10, 26, 26, CRATE)
        block(b, look, (3, 36, 28, 58), 3, "brick", BRICKS[v % len(BRICKS)], shop=AWNINGS[(v + 1) % len(AWNINGS)], every=5, seed=9801 + v, features=[("stack", 3, 3)])
    return b


def corner_parade(look, v):
    """A parade of shops with rooms above, round a street corner on 2 by 2 tiles, a yard behind."""
    b = Building(2, 2, height=2 * STOREY + 8)
    lot_ground(b, look, 2, 2, 40, 40, c("#8a8478"))
    for k in range(4):
        block(b, look, (2 + k * 15, 44, 15 + k * 15, 61), 2, "brick", BRICKS[(k + v) % len(BRICKS)], shop=AWNINGS[(k + v) % len(AWNINGS)], every=5, seed=9810 + k)
    for k in range(2):
        block(b, look, (44, 6 + k * 18, 61, 22 + k * 18), 2, "brick", BRICKS[(k + v + 2) % len(BRICKS)], shop=AWNINGS[(k + v + 3) % len(AWNINGS)], every=5, seed=9815 + k)
    return b


def shops_and_flats(look, v, wide):
    """Four storeys of flats over a row of shops: along the road or running back from it."""
    b = Building(2, 1, height=4 * STOREY + 6) if wide else Building(1, 2, height=4 * STOREY + 6)
    box = (2, 5, 61, 27) if wide else (3, 6, 28, 58)
    block(b, look, box, 4, "brick", [BRICKS[0], BRICKS[2], BRICKS[3]][v], shop=AWNINGS[(v + 1) % len(AWNINGS)], every=4, seed=9820 + v,
          features=[("stack", 4, 3), ("stack", 24, 3), ("hatch", 12, 8)])
    return b


def parade_block(look, v):
    """A mansion block of flats over a parade of shops on 2 by 2 tiles, four storeys round a court."""
    b = Building(2, 2, height=4 * STOREY + 6)
    col = [BRICKS[2], BRICKS[0]][v]
    block(b, look, (2, 2, 61, 17), 4, "brick", col, every=4, has_door=False, seed=9830 + v)
    block(b, look, (2, 18, 17, 43), 4, "brick", shade(col, 1.05), every=4, has_door=False, seed=9831 + v)
    block(b, look, (46, 18, 61, 43), 4, "brick", shade(col, 0.95), every=4, has_door=False, seed=9832 + v)
    block(b, look, (2, 44, 61, 61), 4, "brick", col, shop=AWNINGS[(v + 2) % len(AWNINGS)], every=4, seed=9833 + v)
    return b


def mixed_slab(look, v, wide):
    """A slab of flats on a podium of shops, seven storeys: along the road or running back."""
    b = Building(2, 1, height=7 * STOREY + 6) if wide else Building(1, 2, height=7 * STOREY + 6)
    box = (2, 4, 61, 27) if wide else (3, 6, 28, 58)
    roof, wall = block(b, look, box, 7, "stone", [c("#d8d4c8"), c("#cfc6b0")][v], shop=AWNINGS[(v + 4) % len(AWNINGS)], every=3, seed=9840 + v,
                       features=[("hatch", 6, 6)])
    d = b.d
    for yy in range(wall[1] + 5, wall[3] - STOREY - 4, STOREY):
        d.line([wall[0] + 2, yy, wall[2] - 2, yy], c("#9a9a9e"))
    return b


def mixed_court(look, v):
    """Six storeys of flats round a court on 2 by 2 tiles, shops all along the ground floor."""
    b = Building(2, 2, height=6 * STOREY + 6)
    col = [c("#d8d4c8"), c("#c9b48a")][v]
    lawn_ground(b, look, 20, 20, 43, 43, 9850 + v)
    block(b, look, (2, 2, 61, 17), 6, "stone", col, every=3, has_door=False, seed=9851 + v)
    block(b, look, (2, 18, 17, 43), 6, "stone", shade(col, 1.04), every=3, has_door=False, seed=9852 + v)
    block(b, look, (46, 18, 61, 43), 6, "stone", shade(col, 0.96), every=3, has_door=False, seed=9853 + v)
    block(b, look, (2, 44, 61, 61), 6, "stone", col, shop=AWNINGS[(v + 5) % len(AWNINGS)], every=3, seed=9854 + v)
    return b


# Farmland.

def market_garden(look, v):
    """A market garden on one lot: beds in rows, a glasshouse and a potting shed."""
    b = Building(height=STOREY + 6)
    d = b.d
    crop_rows(b, look, 2, 14, 29, 29, 9900 + v)
    roof, wall = b.box(3, 3, 18, 11, STOREY)
    d.rectangle(wall, GLASSHOUSE)
    d.rectangle(wall, outline=OUTLINE)
    d.rectangle(roof, SNOW_ROOF[0] if look == "snow" else shade(GLASSHOUSE, 1.05), OUTLINE)
    for xx in range(roof[0] + 3, roof[2], 3):
        d.line([xx, roof[1], xx, roof[3]], c("#8aa0aa"))
    roof, wall = b.box(22, 4, 28, 10, STOREY - 1)
    d.rectangle(wall, TIMBER)
    d.rectangle(wall, outline=OUTLINE)
    d.rectangle(roof, SNOW_ROOF[0] if look == "snow" else c("#6b4a36"), OUTLINE)
    return b


def orchard(look, v, wide):
    """An orchard: fruit trees in rows and a small packing shed, along the road or back from it."""
    b = Building(2, 1, height=STOREY + 8) if wide else Building(1, 2, height=STOREY + 8)
    lawn_ground(b, look, 2, 2, (61 if wide else 29), (29 if wide else 61), 9910 + v)
    if wide:
        spots = [(xx, yy, 4) for yy in (12, 24) for xx in range(14, 60, 9)]
        block(b, look, (2, 14, 10, 24), 1, "siding", TIMBER, roof="gable_ew", roof_col=SHINGLE[1], has_door=True, seed=9911 + v)
    else:
        spots = [(xx, yy, 4) for yy in range(10, 50, 9) for xx in (8, 20)]
        block(b, look, (8, 52, 22, 60), 1, "siding", TIMBER, roof="gable_ew", roof_col=SHINGLE[1], has_door=True, seed=9911 + v)
    rural_trees(b, look, v, spots, 9912 + v)
    return b


# Each 1 by 2 drawn both ways: deep, back from the road, and wide, along it.
def both(name, draw, count):
    return [(name + "_deep", lambda look, v: draw(look, v, False), count), (name + "_wide", lambda look, v: draw(look, v, True), count)]


SIZES = (
    [("cabin", cabin, 3)] + both("smallholding", smallholding, 2) + both("villa", villa, 3) + [("mansion", mansion, 2)]
    + both("terrace", terrace, 2) + [("court_tenements", court_tenements, 2)] + both("slab", slab, 3)
    + both("storefronts", storefronts, 2) + [("covered_market", covered_market, 2)] + both("arcade", arcade, 3) + [("emporium", emporium, 2)]
    + both("store_block", store_block, 3) + [("roadside_deep", roadside_deep, 2), ("feed_store", feed_store, 2)]
    + both("lumber_yard", lumber_yard, 2) + [("brickworks", brickworks, 2)] + both("sheds", sheds, 2) + [("storehouses", storehouses, 2), ("foundry", foundry, 2)]
    + both("machine_shop", machine_shop, 2)
    + both("chambers", chambers, 3) + [("office_park", office_park, 2)] + both("office_row", office_row, 3) + [("office_court", office_court, 2), ("slim_offices", slim_offices, 2)]
    + both("office_slab", office_slab, 2)
    + both("twin_shophouses", twin_shophouses, 2) + [("street_parade", corner_parade, 2)] + both("shops_and_flats", shops_and_flats, 3)
    + [("parade_block", parade_block, 2)] + both("mixed_slab", mixed_slab, 2) + [("mixed_court", mixed_court, 2)]
    + [("market_garden", market_garden, 2)] + both("orchard", orchard, 2)
)



# ---- newer kinds of the town's services ------------------------------------
#
# Each takes the footprint of the kind before it and looks of its time: the
# 1930s in long low brick with big windows, the 1950s to 70s in pale brick,
# concrete and ribbon windows, and from the 1990s glass, timber and green.

GLASS_NEW = c("#5d8aa0")
GLASS_DARK = c("#3d5a6c")
TIMBER = c("#b08a5a")
PALE_BRICK = c("#cdb78f")
GREEN_ROOF = c("#6f9a4e")
PANEL = c("#2c3e5c")


def solar_rows(d, x0, y0, x1, y1, look):
    """Rows of solar panels on a flat roof, from x0, y0 to x1, y1 in sprite pixels."""
    if look == "snow":
        return
    for yy in range(y0, y1, 3):
        d.rectangle([x0, yy, x1, yy + 1], PANEL)
        d.line([x0, yy, x1, yy], c("#4d6890"))


def green_patch(d, x0, y0, x1, y1, look):
    """A planted green roof."""
    d.rectangle([x0, y0, x1, y1], SNOW_ROOF[0] if look == "snow" else GREEN_ROOF)
    if look != "snow":
        for xx in range(x0 + 1, x1, 3):
            d.point((xx, (y0 + y1) // 2), shade(GREEN_ROOF, 1.2))


def ribbon_windows(d, wall, storeys, glass=GLASS_NEW, gap=None):
    """Long bands of glass across each storey."""
    x0, y0, x1, y1 = wall
    per = (y1 - y0 + 1) / storeys
    for k in range(storeys):
        wy = int(y0 + k * per + 2)
        d.rectangle([x0 + 2, wy, x1 - 2, wy + 2], glass)
        if gap:
            for xx in range(x0 + 2 + gap, x1 - 2, gap):
                d.line([xx, wy, xx, wy + 2], shade(glass, 0.75))


def elementary_school(look, v):
    """An elementary school of the 1930s on 2 by 2 tiles: one long storey of brick with tall windows, a flat roof, an entrance
    block with the school's name over it, and a yard."""
    b = Building(2, 2, height=STOREY + 12)
    d = b.d
    schoolyard(b, look, 4, 40, 59, 61)
    roof, wall = b.box(4, 12, 59, 36, STOREY + 4)
    brick(d, wall, c("#a85a40") if v == 0 else c("#c49a6a"))
    windows(d, wall, 1, glass=c("#46586a"), sill=TRIM, every=4, width=3, height=6, skip_door=True)
    d.rectangle(wall, outline=OUTLINE)
    flat_roof(b.img, roof, look, random.Random(9100 + v), [("vent", 8, 6), ("vent", 44, 6)], parapet=STONE)
    # The entrance block, a storey taller, in the middle.
    eroof, ewall = b.box(26, 26, 38, 38, STOREY + 10)
    d.rectangle(ewall, STONE)
    ex0, ey0, ex1, ey1 = ewall
    d.rectangle([ex0 + 3, ey1 - 6, ex1 - 3, ey1], c("#5a3a2a"))
    d.line([ex0 + 2, ey0 + 3, ex1 - 2, ey0 + 3], shade(STONE, 0.8))
    d.rectangle(ewall, outline=OUTLINE)
    flat_roof(b.img, eroof, look, random.Random(9110), [], parapet=STONE)
    if v == 1:
        # A bike shed by the yard.
        bx, by = b.ground(48, 40)
        b.d.rectangle([bx, by, bx + 10, by + 4], c("#5a7a8a"), OUTLINE)
        bike_rack(b, 49, 46)
    return b


def community_school(look, v):
    """A community school of the 2000s on 2 by 2 tiles: two storeys of glass and timber, a green roof with solar panels, and a
    games court open in the evenings."""
    b = Building(2, 2, height=2 * STOREY + 8)
    d = b.d
    gx0, gy0 = b.ground(4, 42)
    gx1, gy1 = b.ground(59, 61)
    d.rectangle([gx0, gy0, gx1, gy1], SNOW_GROUND if look == "snow" else c("#4f7a8a"))
    if look != "snow":
        d.rectangle([gx0 + 3, gy0 + 3, gx1 - 3, gy1 - 3], outline=c("#e8e8e0"))
        d.line([(gx0 + gx1) // 2, gy0 + 3, (gx0 + gx1) // 2, gy1 - 3], c("#e8e8e0"))
    roof, wall = b.box(4, 8, 59, 38, 2 * STOREY + 2)
    d.rectangle(wall, TIMBER if v == 0 else c("#d8d4c8"))
    x0, y0, x1, y1 = wall
    for xx in range(x0 + 2, x1, 2):
        d.line([xx, y0, xx, y1], shade(TIMBER if v == 0 else c("#d8d4c8"), 0.9))
    ribbon_windows(d, wall, 2, gap=6)
    d.rectangle([(x0 + x1) // 2 - 4, y1 - 6, (x0 + x1) // 2 + 4, y1], GLASS_DARK)
    d.rectangle(wall, outline=OUTLINE)
    flat_roof(b.img, roof, look, random.Random(9200 + v), [], parapet=c("#8a8a84"))
    rx0, ry0, rx1, ry1 = roof
    green_patch(d, rx0 + 3, ry0 + 3, (rx0 + rx1) // 2 - 2, ry1 - 3, look)
    solar_rows(d, (rx0 + rx1) // 2 + 2, ry0 + 3, rx1 - 3, ry1 - 3, look)
    return b


def composite_high(look, v):
    """A composite high school of the 1960s on 3 by 2 tiles: long blocks of pale brick with bands of windows, a tall gym,
    and a playing field."""
    b = Building(3, 2, height=3 * STOREY + 6)
    d = b.d
    gx0, gy0 = b.ground(4, 46)
    gx1, gy1 = b.ground(91, 61)
    d.rectangle([gx0, gy0, gx1, gy1], SNOW_GROUND if look == "snow" else c("#7fa05a"))
    if look != "snow":
        d.rectangle([gx0 + 30, gy0 + 3, gx1 - 6, gy1 - 3], outline=c("#e8e8e0"))
    roof, wall = b.box(4, 8, 64, 42, 2 * STOREY + 2)
    d.rectangle(wall, PALE_BRICK if v == 0 else c("#b8b4aa"))
    ribbon_windows(d, wall, 2, gap=5)
    d.rectangle(wall, outline=OUTLINE)
    flat_roof(b.img, roof, look, random.Random(9300 + v), [("vent", 8, 6), ("hatch", 30, 10), ("vent", 50, 20)], parapet=c("#8a8a84"))
    groof, gwall = b.box(66, 10, 91, 40, 3 * STOREY + 2)
    d.rectangle(gwall, shade(PALE_BRICK if v == 0 else c("#b8b4aa"), 0.92))
    gx0w, gy0w, gx1w, gy1w = gwall
    d.rectangle([gx0w + 2, gy0w + 2, gx1w - 2, gy0w + 4], GLASS_NEW)
    d.rectangle(gwall, outline=OUTLINE)
    flat_roof(b.img, groof, look, random.Random(9310), [("vent", 10, 10)], parapet=c("#8a8a84"))
    return b


def branch_library(look, v):
    """A branch library of the 1950s on one tile: low and pale, with a wall of glass at the front under a deep flat roof."""
    b = Building(height=STOREY + 8)
    d = b.d
    gx0, gy0 = b.ground(3, 25)
    gx1, gy1 = b.ground(28, 30)
    d.rectangle([gx0, gy0, gx1, gy1], SNOW_GROUND if look == "snow" else c("#86a85e"))
    roof, wall = b.box(4, 8, 28, 24, STOREY + 4)
    d.rectangle(wall, PALE_BRICK if v == 0 else c("#d8d0c0"))
    x0, y0, x1, y1 = wall
    d.rectangle([x0 + 3, y0 + 2, x1 - 3, y1 - 1], GLASS_NEW)
    for xx in range(x0 + 6, x1 - 3, 4):
        d.line([xx, y0 + 2, xx, y1 - 1], c("#d8d8d0"))
    d.line([x0, y0, x1, y0], c("#6a6a66"))
    d.rectangle(wall, outline=OUTLINE)
    flat_roof(b.img, roof, look, random.Random(9400 + v), [("vent", 4, 4)], parapet=c("#8a8a84"))
    if v == 1:
        bench_tree(b, look, v, 4, 26)
    return b


def media_library(look, v):
    """A media library of the 2000s on one tile: a glass box with a timber screen and a roof that tilts up to the street."""
    b = Building(height=2 * STOREY + 8)
    d = b.d
    gx0, gy0 = b.ground(3, 25)
    gx1, gy1 = b.ground(28, 30)
    d.rectangle([gx0, gy0, gx1, gy1], SNOW_GROUND if look == "snow" else c("#9a9a92"))
    roof, wall = b.box(4, 7, 28, 24, 2 * STOREY + 2)
    d.rectangle(wall, GLASS_NEW)
    x0, y0, x1, y1 = wall
    for xx in range(x0 + 1, x1, 2 if v == 0 else 3):
        d.line([xx, y0, xx, y1 - 5], TIMBER)
    d.rectangle([(x0 + x1) // 2 - 3, y1 - 5, (x0 + x1) // 2 + 3, y1], GLASS_DARK)
    d.rectangle(wall, outline=OUTLINE)
    rx0, ry0, rx1, ry1 = roof
    d.rectangle(roof, SNOW_ROOF[0] if look == "snow" else c("#7a7c80"))
    for yy in range(ry0 + 2, ry1, 2):
        d.line([rx0 + 1, yy, rx1 - 1, yy], shade(c("#7a7c80"), 1.1 - (yy - ry0) * 0.01) if look != "snow" else SNOW_ROOF[1])
    solar_rows(d, rx0 + 3, ry0 + 3, rx1 - 3, ry0 + 9, look)
    d.rectangle(roof, outline=OUTLINE)
    if v == 1:
        bike_rack(b, 5, 26)
        bike_rack(b, 20, 26)
    return b


def health_centre(look, v):
    """A health centre of the 1950s on one tile: low pale brick with a flat roof, a canopy over the door and a red cross."""
    b = Building(height=STOREY + 8)
    d = b.d
    roof, wall = b.box(3, 8, 28, 25, STOREY + 4)
    d.rectangle(wall, PALE_BRICK if v == 0 else c("#e0dccf"))
    windows(d, wall, 1, glass=GLASS_DARK, every=4, width=3, height=4, skip_door=True)
    x0, y0, x1, y1 = wall
    cx = (x0 + x1) // 2
    d.rectangle([cx - 5, y1 - 7, cx + 5, y1 - 6], c("#6a6a66"))
    door(d, wall, c("#3a4f6a"))
    d.rectangle([x1 - 7, y0 + 1, x1 - 3, y0 + 5], c("#ffffff"))
    d.line([x1 - 5, y0 + 1, x1 - 5, y0 + 5], c("#c0392b"))
    d.line([x1 - 7, y0 + 3, x1 - 3, y0 + 3], c("#c0392b"))
    d.rectangle(wall, outline=OUTLINE)
    flat_roof(b.img, roof, look, random.Random(9500 + v), [("vent", 5, 4), ("vent", 16, 8)], parapet=c("#8a8a84"))
    if v == 1:
        parked_car(b, 20, 27, c("#f2f2ea"), look)
    return b


def community_health(look, v):
    """A community health centre of the 2000s on one tile: white panels and glass, a green sign and bicycle stands."""
    b = Building(height=2 * STOREY + 6)
    d = b.d
    gx0, gy0 = b.ground(2, 26)
    gx1, gy1 = b.ground(29, 30)
    d.rectangle([gx0, gy0, gx1, gy1], SNOW_GROUND if look == "snow" else c("#a6a69e"))
    for k in range(4):
        d.line([gx0 + 3 + k * 3, gy0 + 1, gx0 + 3 + k * 3, gy0 + 3], c("#3a3a40"))
    roof, wall = b.box(4, 7, 28, 25, 2 * STOREY)
    d.rectangle(wall, c("#efefea") if v == 0 else c("#e2e8e4"))
    ribbon_windows(d, wall, 2, glass=GLASS_NEW, gap=5)
    x0, y0, x1, y1 = wall
    d.rectangle([x0 + 2, y0 - 1, x0 + 8, y0 + 1], c("#3a9a5a"))
    d.rectangle([(x0 + x1) // 2 - 3, y1 - 5, (x0 + x1) // 2 + 3, y1], GLASS_DARK)
    d.rectangle(wall, outline=OUTLINE)
    flat_roof(b.img, roof, look, random.Random(9600 + v), [], parapet=c("#9a9a94"))
    rx0, ry0, rx1, ry1 = roof
    solar_rows(d, rx0 + 3, ry0 + 3, rx1 - 3, ry1 - 3, look)
    if v == 1:
        bike_rack(b, 4, 27)
        bench_tree(b, look, v, 18, 26)
    return b


def general_hospital(look, v):
    """A general hospital of the 1940s on 3 by 3 tiles: a tall stepped block of pale brick, long wards either side, and
    lawns."""
    b = Building(3, 3, height=7 * STOREY + 6)
    d = b.d
    rng = random.Random(9700 + v)
    gx0, gy0 = b.ground(2, 2)
    gx1, gy1 = b.ground(93, 93)
    d.rectangle([gx0, gy0, gx1, gy1], SNOW_GROUND if look == "snow" else c("#86a85e"))
    dx0, dy0 = b.ground(40, 76)
    d.rectangle([dx0, dy0, dx0 + 15, gy1], c("#b8b2a6") if look != "snow" else c("#d6dde3"))
    wall_col = PALE_BRICK if v == 0 else c("#d6cbb6")
    for (x0_, x1_) in ((4, 32), (64, 92)):
        roof, wall = b.box(x0_, 14, x1_, 72, 4 * STOREY)
        d.rectangle(wall, wall_col)
        windows(d, wall, 4, glass=GLASS_DARK, every=4, width=2, height=3)
        d.rectangle(wall, outline=OUTLINE)
        flat_roof(b.img, roof, look, rng, [("vent", 6, 10), ("vent", 14, 40)], parapet=STONE)
    roof, wall = b.box(30, 8, 66, 66, 7 * STOREY)
    d.rectangle(wall, shade(wall_col, 1.04))
    x0, y0, x1, y1 = wall
    for xx in range(x0 + 4, x1 - 2, 6):
        d.line([xx, y0 + 2, xx, y1 - 8], shade(wall_col, 0.88))
    windows(d, wall, 7, glass=GLASS_DARK, every=6, width=3, height=3, skip=[((x0 + x1) // 2 - 6, (x0 + x1) // 2 + 6)])
    cx = (x0 + x1) // 2
    d.rectangle([cx - 6, y1 - 7, cx + 6, y1], STONE)
    d.rectangle([cx - 3, y1 - 5, cx + 3, y1], c("#4a3a2a"))
    d.rectangle(wall, outline=OUTLINE)
    flat_roof(b.img, roof, look, rng, [("tank", 10, 10), ("vent", 24, 30)], parapet=STONE)
    if v % 2 == 1:
        # A red cross on the roof of the main block, for the ambulances' planes to see.
        rx0, ry0, rx1, ry1 = roof
        mx, my = (rx0 + rx1) // 2, (ry0 + ry1) // 2 + 6
        b.d.rectangle([mx - 7, my - 7, mx + 7, my + 7], c("#f2f2ea") if look != "snow" else SNOW, OUTLINE)
        b.d.rectangle([mx - 2, my - 5, mx + 2, my + 5], c("#c0392b"))
        b.d.rectangle([mx - 5, my - 2, mx + 5, my + 2], c("#c0392b"))
    return b


def medical_centre(look, v):
    """A medical centre of the 1980s on 3 by 3 tiles: a concrete tower with a helicopter pad on top, a low glass podium and
    a car park."""
    b = Building(3, 3, height=8 * STOREY + 6)
    d = b.d
    rng = random.Random(9800 + v)
    gx0, gy0 = b.ground(2, 2)
    gx1, gy1 = b.ground(93, 93)
    d.rectangle([gx0, gy0, gx1, gy1], SNOW_GROUND if look == "snow" else c("#9a9a92"))
    if look != "snow":
        for xx in range(gx0 + 4, gx1 - 30, 5):
            d.line([xx, gy1 - 16, xx, gy1 - 4], c("#e8e8e0"))
    proof, pwall = b.box(4, 30, 92, 74, 2 * STOREY)
    d.rectangle(pwall, GLASS_NEW)
    px0, py0, px1, py1 = pwall
    for xx in range(px0 + 4, px1, 6):
        d.line([xx, py0, xx, py1], c("#c8c8c0"))
    d.rectangle(pwall, outline=OUTLINE)
    flat_roof(b.img, proof, look, rng, [("vent", 10, 10), ("vent", 70, 20)], parapet=c("#9a9a94"))
    roof, wall = b.box(28, 6, 68, 40, 8 * STOREY)
    d.rectangle(wall, CONCRETE if v == 0 else c("#c4beb2"))
    ribbon_windows(d, wall, 8, glass=GLASS_DARK, gap=8)
    d.rectangle(wall, outline=OUTLINE)
    flat_roof(b.img, roof, look, rng, [], parapet=c("#8a8a84"), busy=False)
    rx0, ry0, rx1, ry1 = roof
    mx, my = (rx0 + rx1) // 2, (ry0 + ry1) // 2
    d.ellipse([mx - 10, my - 10, mx + 10, my + 10], c("#5a5e62") if look != "snow" else SNOW_ROOF[1], OUTLINE)
    d.line([mx - 3, my - 5, mx - 3, my + 5], c("#f2f2ea"))
    d.line([mx + 3, my - 5, mx + 3, my + 5], c("#f2f2ea"))
    d.line([mx - 3, my, mx + 3, my], c("#f2f2ea"))
    return b


def care_home(look, v):
    """A care home of the 1970s on 2 by 2 tiles: low wings of pale brick round a garden court, with a covered drop-off."""
    b = Building(2, 2, height=STOREY + 10)
    d = b.d
    lawn_ground(b, look, 2, 2, 61, 61, 9900 + v)
    wall_col = PALE_BRICK if v == 0 else c("#d4c8b0")
    for (x0_, y0_, x1_, y1_) in ((4, 6, 59, 20), (4, 22, 18, 50), (45, 22, 59, 50)):
        roof, wall = b.box(x0_, y0_, x1_, y1_, STOREY + 4)
        d.rectangle(wall, wall_col)
        windows(d, wall, 1, glass=GLASS_DARK, every=4, width=3, height=3)
        d.rectangle(wall, outline=OUTLINE)
        gable_ew(d, roof, c("#7a5a4a"), look)
    gx0, gy0 = b.ground(22, 24)
    gx1, gy1 = b.ground(41, 48)
    if look != "snow":
        d.ellipse([gx0 + 4, gy0 + 6, gx1 - 4, gy1 - 6], c("#5f8f4a"))
        d.point(((gx0 + gx1) // 2, (gy0 + gy1) // 2), c("#d9b44a"))
    return b


def motor_fire_station(look, v):
    """A motor fire hall of the 1910s and 20s on 2 by 2 tiles: brick with three wide doors, red engines showing in one, a tall
    hose tower and a drive out front."""
    b = Building(2, 2, height=5 * STOREY + 6)
    d = b.d
    gx0, gy0 = b.ground(4, 50)
    gx1, gy1 = b.ground(50, 61)
    d.rectangle([gx0, gy0, gx1, gy1], c("#a8a49a") if look != "snow" else c("#dfe5ea"))
    roof, wall = b.box(4, 14, 50, 48, 2 * STOREY + 4)
    brick(d, wall, c("#8e3a2c") if v == 0 else c("#a8704a"))
    x0, y0, x1, y1 = wall
    windows(d, (x0, y0, x1, y0 + STOREY), 1, sill=TRIM, every=5)
    for k, dx in enumerate((3, 18, 33)):
        d.rectangle([x0 + dx, y1 - 9, x0 + dx + 11, y1], c("#2a2a30") if k == 1 else c("#c0392b"))
        if k == 1:
            d.rectangle([x0 + dx + 2, y1 - 5, x0 + dx + 9, y1 - 1], c("#d63a2a"))
        d.rectangle([x0 + dx, y1 - 10, x0 + dx + 11, y1 - 10], STONE)
    d.rectangle(wall, outline=OUTLINE)
    flat_roof(b.img, roof, look, random.Random(10000 + v), [("stack", 4, 3), ("vent", 24, 12)], parapet=STONE)
    troof, twall = b.box(51, 18, 60, 30, 5 * STOREY + 4)
    brick(d, twall, c("#7e342a"))
    tx0, ty0, tx1, ty1 = twall
    for k in range(4):
        d.rectangle([tx0 + 3, ty0 + 3 + k * 7, tx0 + 5, ty0 + 5 + k * 7], WINDOW)
    d.rectangle(twall, outline=OUTLINE)
    flat_roof(b.img, troof, look, random.Random(10010), [], parapet=STONE)
    return b


def fire_hall(look, v):
    """A fire and rescue hall of the 1970s on 2 by 2 tiles: concrete and glass, three tall doors with red trucks behind the
    glass, a training tower and a wide apron."""
    b = Building(2, 2, height=5 * STOREY + 6)
    d = b.d
    gx0, gy0 = b.ground(4, 48)
    gx1, gy1 = b.ground(59, 61)
    d.rectangle([gx0, gy0, gx1, gy1], c("#9a9a92") if look != "snow" else c("#dfe5ea"))
    roof, wall = b.box(4, 12, 48, 46, 2 * STOREY + 2)
    d.rectangle(wall, CONCRETE if v == 0 else c("#c8c0b0"))
    x0, y0, x1, y1 = wall
    d.rectangle([x0, y0, x1, y0 + 2], c("#c0392b"))
    for dx in (3, 17, 31):
        d.rectangle([x0 + dx, y1 - 10, x0 + dx + 11, y1], GLASS_NEW)
        d.rectangle([x0 + dx + 2, y1 - 5, x0 + dx + 9, y1 - 1], c("#d63a2a"))
        for yy in range(y1 - 9, y1, 3):
            d.line([x0 + dx, yy, x0 + dx + 11, yy], c("#c8d8e0"))
    d.rectangle(wall, outline=OUTLINE)
    flat_roof(b.img, roof, look, random.Random(10100 + v), [("vent", 8, 8), ("hatch", 28, 14)], parapet=c("#8a8a84"))
    troof, twall = b.box(51, 14, 59, 24, 5 * STOREY + 4)
    d.rectangle(twall, c("#b8b0a0"))
    tx0, ty0, tx1, ty1 = twall
    for k in range(5):
        d.rectangle([tx0 + 2, ty0 + 2 + k * 6, tx1 - 2, ty0 + 3 + k * 6], WINDOW)
    d.rectangle(twall, outline=OUTLINE)
    flat_roof(b.img, troof, look, random.Random(10110), [], parapet=c("#8a8a84"))
    return b


def parked_car(b, x, y, col, look):
    """A car parked at tile pixel [x], [y], seen from above: its shadow, the body, the roof and windscreen."""
    gx, gy = b.ground(x, y)
    d = b.d
    d.rectangle([gx + 1, gy + 1, gx + 7, gy + 4], (0, 0, 0, 60))
    d.rectangle([gx, gy, gx + 6, gy + 3], SNOW_ROOF[0] if look == "snow" else col, OUTLINE)
    d.rectangle([gx + 2, gy + 1, gx + 4, gy + 2], shade(col, 0.75) if look != "snow" else SNOW_ROOF[1])


def bike_rack(b, x, y):
    """A rack of bicycles at tile pixel [x], [y]."""
    gx, gy = b.ground(x, y)
    for k in range(4):
        b.d.line([gx + k * 2, gy, gx + k * 2, gy + 3], [c("#c0392b"), c("#2f5f8a"), c("#e8d070"), c("#3a3a40")][k])


def bench_tree(b, look, v, x, y):
    """A bench under a young tree at tile pixel [x], [y]."""
    bench(b, x, y + 2)
    tree_at(b, look, v, x + 8, y, 3, random.Random(x * 31 + y))


def precinct(look, v):
    """A police precinct of the 1930s on 2 by 1 tiles: three storeys of pale stone with tall stepped windows, lamps either
    side of the door and a flag."""
    b = Building(2, 1, height=3 * STOREY + 6)
    d = b.d
    roof, wall = b.box(3, 5, 60, 26, 3 * STOREY + 2)
    d.rectangle(wall, c("#d8d0bc") if v == 0 else c("#b8a890"))
    x0, y0, x1, y1 = wall
    for xx in range(x0 + 6, x1 - 3, 8):
        d.rectangle([xx, y0 + 2, xx + 2, y1 - 3], GLASS_DARK)
        d.line([xx - 1, y0 + 1, xx + 3, y0 + 1], shade(c("#d8d0bc"), 0.8))
    cx = (x0 + x1) // 2
    d.rectangle([cx - 3, y1 - 6, cx + 3, y1], c("#2a2a30"))
    for k in (-6, 5):
        d.rectangle([cx + k, y1 - 6, cx + k + 1, y1 - 4], c("#3f6fd8"))
    d.rectangle(wall, outline=OUTLINE)
    flat_roof(b.img, roof, look, random.Random(10200 + v), [("stack", 6, 3), ("hatch", 30, 8)], parapet=STONE)
    rx0, ry0, rx1, ry1 = roof
    d.line([rx1 - 6, ry0 + 2, rx1 - 6, ry0 - 8], c("#d0d0d0"))
    d.rectangle([rx1 - 5, ry0 - 8, rx1 - 1, ry0 - 6], c("#c0392b"))
    if v == 1:
        parked_car(b, 8, 27, c("#22407a"), look)
        parked_car(b, 18, 27, c("#f2f2ea"), look)
    return b


def community_policing(look, v):
    """A community police office of the 1990s on 2 by 1 tiles: two storeys with a glass front onto the street, a blue band and
    bicycle stands."""
    b = Building(2, 1, height=2 * STOREY + 6)
    d = b.d
    gx0, gy0 = b.ground(2, 27)
    gx1, gy1 = b.ground(61, 30)
    d.rectangle([gx0, gy0, gx1, gy1], c("#b0aca4") if look != "snow" else SNOW_GROUND)
    for k in range(5):
        d.line([gx0 + 4 + k * 3, gy0, gx0 + 4 + k * 3, gy0 + 2], c("#3a3a40"))
    roof, wall = b.box(3, 6, 60, 26, 2 * STOREY + 2)
    brick(d, wall, c("#b8846a") if v == 0 else c("#9a9a96"))
    x0, y0, x1, y1 = wall
    d.rectangle([x0 + 4, y0 + STOREY, x1 - 4, y1 - 1], GLASS_NEW)
    for xx in range(x0 + 8, x1 - 4, 6):
        d.line([xx, y0 + STOREY, xx, y1 - 1], c("#d8d8d0"))
    d.rectangle([x0, y0 + STOREY - 2, x1, y0 + STOREY - 1], c("#2f5fb8"))
    windows(d, (x0, y0, x1, y0 + STOREY - 3), 1, glass=GLASS_DARK, every=5, width=3, height=2)
    d.rectangle(wall, outline=OUTLINE)
    flat_roof(b.img, roof, look, random.Random(10300 + v), [("vent", 8, 6), ("vent", 40, 6)], parapet=c("#8a8a84"))
    if v == 1:
        bike_rack(b, 6, 27)
        parked_car(b, 50, 27, c("#f2f2ea"), look)
    return b


# ---- green space ---------------------------------------------------------------

def green_lawn(look):
    """Kept grass, watered so it stays green in a dry summer."""
    return GRASS[look] if look not in ("summer", "dry") else [c("#6aae4a"), c("#5f9f42"), c("#78bc56")]


def lawn_box(b, look, x0, y0, x1, y1, rng):
    noise_fill(b.img, (x0, y0 + b.lift, x1 + 1, y1 + b.lift + 1), green_lawn(look), rng)


def tree_at(b, look, v, x, y, r, rng, conifer_tree=False):
    # Kept inside the sprite, crown and all.
    x = max(r + 2, min(b.img.width - r - 3, x))
    if conifer_tree:
        cast = conifer(b.img, look, x, y + b.lift, r * 2, rng)
    else:
        cast = deciduous(b.img, look, v, x, y + b.lift, r, rng)
    b.casters.append((0, cast[0], cast[1] - b.lift, cast[2], cast[3], 0))


def path_line(b, look, pts, width=3):
    """A path through [pts]: straight between two, and a smooth curve through more, as a path is walked into the
    ground, with no corners at the bends."""
    col = PATH if look != "snow" else c("#e6ecf0")
    pts = [(x, y + b.lift) for x, y in pts]
    if len(pts) > 2:
        # A Catmull-Rom curve through every point, its ends held.
        ext = [pts[0]] + pts + [pts[-1]]
        curve = []
        for k in range(1, len(ext) - 2):
            p0, p1, p2, p3 = ext[k - 1], ext[k], ext[k + 1], ext[k + 2]
            steps = max(4, int(math.hypot(p2[0] - p1[0], p2[1] - p1[1]) // 2))
            for j in range(steps):
                t = j / steps
                t2, t3 = t * t, t * t * t
                curve.append(tuple(0.5 * ((2 * p1[i]) + (-p0[i] + p2[i]) * t + (2 * p0[i] - 5 * p1[i] + 4 * p2[i] - p3[i]) * t2 +
                                          (-p0[i] + 3 * p1[i] - 3 * p2[i] + p3[i]) * t3) for i in (0, 1)))
        curve.append(pts[-1])
        pts = curve
    b.d.line(pts, col, width, joint="curve")
    r = (width - 1) / 2
    for (x, y) in (pts[0], pts[-1]):
        b.d.ellipse([x - r, y - r, x + r, y + r], col)


def bench(b, x, y):
    b.d.rectangle([x, y + b.lift, x + 5, y + 1 + b.lift], c("#7a5a3a"))


def flowers(b, look, x0, y0, x1, y1, rng, n=12):
    d = b.d
    d.rectangle([x0, y0 + b.lift, x1, y1 + b.lift], c("#6b4a30") if look != "snow" else SNOW_ROOF[1])
    if look in ("spring", "summer", "dry"):
        for _ in range(n):
            d.point((rng.randrange(x0 + 1, x1), rng.randrange(y0 + 1, y1) + b.lift), rng.choice(FLOWERS))


def pond(b, look, cx, cy, rx, ry, rng, lilies=True):
    """A pond with an uneven shore: a few overlapping lobes, a darker edge and, in season, lily pads."""
    d = b.d
    top = b.lift
    water = c("#5a8fbf") if look != "snow" else c("#dfe8ef")
    edge = c("#3f6f9a") if look != "snow" else c("#c8d4dc")
    lobes = [(cx, cy, rx, ry)]
    for _ in range(3):
        ox = rng.randint(-rx // 2, rx // 2)
        oy = rng.randint(-ry // 2, ry // 2)
        lobes.append((cx + ox, cy + oy, max(3, rx * rng.randint(45, 75) // 100), max(3, ry * rng.randint(45, 75) // 100)))
    # The shore first, a pixel bigger all round, then the water over it.
    for (x, y, a, e) in lobes:
        d.ellipse([x - a - 1, y - e - 1 + top, x + a + 1, y + e + 1 + top], edge)
    for (x, y, a, e) in lobes:
        d.ellipse([x - a, y - e + top, x + a, y + e + top], water)
    if lilies and look in ("spring", "summer"):
        for _ in range(max(2, rx // 4)):
            x, y, a, e = rng.choice(lobes)
            px = x + rng.randint(-a // 2, a // 2)
            py = y + rng.randint(-e // 2, e // 2)
            d.point((px, py + top), c("#4f8a3a"))
            d.point((px + 1, py + top), c("#4f8a3a"))
    elif look != "snow":
        for _ in range(max(2, rx // 5)):
            x, y, a, e = rng.choice(lobes)
            d.line([x - 2, y + top, x + 1, y + top], c("#8fbce0"))


def playground(look, v):
    """A playground on one tile, fenced, on soft ground: swings, a slide and a sandpit; or a climbing frame, a roundabout and a
    seesaw; or a paddling pool with benches round it."""
    b = Building(height=LIFT)
    d = b.d
    rng = random.Random(11000 + v)
    lawn_box(b, look, 0, 0, 31, 31, rng)
    top = b.lift
    d.rectangle([3, top + 3, 28, top + 28], c("#c8a878") if look != "snow" else SNOW_GROUND, outline=c("#6b5a44"))
    if v == 0:
        d.rectangle([5, top + 18, 13, top + 26], c("#e6d49a") if look != "snow" else SNOW_ROOF[1])
        d.line([16, top + 6, 26, top + 6], c("#c0392b"), 1)
        for x in (16, 26):
            d.line([x, top + 6, x, top + 12], c("#4a3a2a"))
        for x in (19, 23):
            d.line([x, top + 6, x, top + 10], c("#9a9a9a"))
        d.line([18, top + 16, 26, top + 24], c("#f2c94c"), 2)
        tree_at(b, look, v, 7, 10, 5, rng)
    elif v == 1:
        # A climbing frame of bars, a roundabout and a seesaw.
        for k in range(4):
            d.line([6 + k * 3, top + 6, 6 + k * 3, top + 14], c("#2f6fb8"))
            d.line([6, top + 6 + k * 2 + 2, 15, top + 6 + k * 2 + 2], c("#2f6fb8"))
        d.ellipse([18, top + 6, 26, top + 14], c("#c0392b"), OUTLINE)
        d.line([22, top + 6, 22, top + 14], c("#f2c94c"))
        d.line([18, top + 10, 26, top + 10], c("#f2c94c"))
        d.line([6, top + 22, 18, top + 20], c("#8a6a4a"), 2)
        tree_at(b, look, v, 24, 22, 4, rng)
    else:
        # A paddling pool, dry in winter, and benches for the parents.
        d.ellipse([7, top + 8, 24, top + 22], c("#d8d0c0"), OUTLINE)
        if look in ("spring", "summer", "dry"):
            d.ellipse([9, top + 10, 22, top + 20], c("#7fb8e0"))
        bench(b, 6, 25)
        bench(b, 19, 25)
        tree_at(b, look, v, 27, 8, 3, rng)
    return b

def town_square(look, v):
    """A town square on 2 by 2 tiles, paved, with trees and benches round it: a statue in the middle; or a fountain among
    flower beds; or a war memorial on a lawn."""
    b = Building(2, 2, height=LIFT + 8)
    d = b.d
    rng = random.Random(11100 + v)
    top = b.lift
    pave = c("#c8bfae") if look != "snow" else c("#e6ecf0")
    d.rectangle([2, top + 2, 61, top + 61], pave)
    for k in range(4, 61, 6):
        d.line([2, top + k, 61, top + k], shade(pave, 0.94))
    if v == 0:
        d.rectangle([24, top + 24, 39, top + 39], c("#a89c88"), outline=OUTLINE)
        d.rectangle([29, top + 22, 34, top + 34], c("#6e6a62"))
        b.casters.append((1, 29, 22, 35, 35, 12))
        corners = ((10, 12), (53, 12), (10, 54), (53, 54))
    elif v == 1:
        for (x0, y0) in ((8, 8), (40, 8), (8, 40), (40, 40)):
            flowers(b, look, x0, y0, x0 + 15, y0 + 15, rng, n=20)
        d.ellipse([22, top + 22, 41, top + 41], c("#bdb6a4"), OUTLINE)
        d.ellipse([25, top + 25, 38, top + 38], c("#6fa0cf") if look != "snow" else c("#dfe8ef"))
        d.rectangle([30, top + 28, 33, top + 34], c("#d8d0c0"))
        corners = ((4, 31), (59, 31))
    else:
        lawn_box(b, look, 12, 12, 51, 51, rng)
        d.line([31, top + 12, 31, top + 51], pave, 3)
        d.line([12, top + 31, 51, top + 31], pave, 3)
        d.rectangle([27, top + 27, 36, top + 36], c("#b8b0a0"), outline=OUTLINE)
        d.polygon([(29, top + 18), (34, top + 18), (33, top + 32), (30, top + 32)], c("#d8d0c0"), OUTLINE)
        b.casters.append((1, 29, 18, 34, 33, 18))
        corners = ((6, 6), (57, 6), (6, 57), (57, 57))
    for x, y in corners:
        tree_at(b, look, v, x, y, 6, rng)
    for x, y in ((20, 18), (38, 18), (20, 44), (38, 44)):
        bench(b, x, y)
    return b

def plaza_square(look, v):
    """A plaza of the 1960s on 2 by 2 tiles, broad concrete paving and seating: a long fountain pool with planters; or a sunken
    court with steps and a sculpture; or a grid of trees round a kiosk."""
    b = Building(2, 2, height=LIFT + 6)
    d = b.d
    rng = random.Random(11200 + v)
    top = b.lift
    pave = c("#cfcac0") if look != "snow" else c("#e6ecf0")
    d.rectangle([1, top + 1, 62, top + 62], pave)
    for k in range(1, 62, 8):
        d.line([k, top + 1, k, top + 62], shade(pave, 0.93))
    if v == 0:
        d.rectangle([12, top + 26, 51, top + 37], c("#8a8a84"))
        d.rectangle([14, top + 28, 49, top + 35], c("#6fa0cf") if look != "snow" else c("#dfe8ef"))
        for x in (20, 31, 42):
            d.point((x, top + 31), c("#e8f4ff"))
        for x, y in ((8, 8), (48, 8), (8, 48), (48, 48)):
            d.rectangle([x, top + y, x + 8, top + y + 8], c("#7a7a74"))
            tree_at(b, look, v, x + 4, y + 6, 4, rng)
    elif v == 1:
        for k in range(4):
            d.rectangle([10 + k * 3, top + 10 + k * 3, 53 - k * 3, top + 53 - k * 3], outline=shade(pave, 0.85 - k * 0.04))
        d.rectangle([22, top + 22, 41, top + 41], shade(pave, 0.82))
        d.polygon([(28, top + 26), (36, top + 30), (32, top + 38), (26, top + 34)], c("#b8603a"), OUTLINE)
        b.casters.append((1, 26, 26, 37, 39, 10))
        for x, y in ((5, 5), (58, 5), (5, 58), (58, 58)):
            tree_at(b, look, v, x, y, 4, rng)
    else:
        for x in (10, 24, 39, 53):
            for y in (10, 53):
                d.rectangle([x - 3, top + y - 3, x + 3, top + y + 3], c("#7a7a74"))
                tree_at(b, look, v, x, y + 2, 3, rng)
        kroof, kwall = b.box(26, 26, 37, 36, 8)
        d.rectangle(kwall, c("#d8c8a8"))
        d.rectangle([kwall[0] + 2, kwall[1] + 1, kwall[2] - 2, kwall[1] + 3], GLASS_NEW)
        d.rectangle(kwall, outline=OUTLINE)
        d.rectangle(kroof, c("#c0392b") if look != "snow" else SNOW_ROOF[0], outline=OUTLINE)
        for x, y in ((14, 30), (46, 30)):
            bench(b, x, y)
    return b

def formal_garden(look, v):
    """A formal garden on 2 by 2 tiles, clipped hedges and gravel walks: four squares of beds round a fountain; or a round
    parterre of beds like spokes; or a rose garden under a pergola."""
    b = Building(2, 2, height=LIFT)
    d = b.d
    rng = random.Random(11300 + v)
    top = b.lift
    lawn_box(b, look, 0, 0, 63, 63, rng)
    gravel = c("#d9ccaa") if look != "snow" else c("#e6ecf0")
    hedge = c("#3f6b3a") if look != "snow" else c("#c9d6e0")
    water = c("#6fa0cf") if look != "snow" else c("#dfe8ef")
    if v == 0:
        d.line([32, top + 2, 32, top + 61], gravel, 4)
        d.line([2, top + 32, 61, top + 32], gravel, 4)
        for (x0, y0) in ((6, 6), (38, 6), (6, 38), (38, 38)):
            d.rectangle([x0, top + y0, x0 + 19, top + y0 + 19], outline=hedge, width=2)
            flowers(b, look, x0 + 5, y0 + 5, x0 + 14, y0 + 14, rng)
        d.ellipse([26, top + 26, 38, top + 38], c("#bdb6a4"))
        d.ellipse([28, top + 28, 36, top + 36], water)
        for x, y in ((3, 3), (60, 3), (3, 60), (60, 60)):
            tree_at(b, look, v, x, y, 3, rng, conifer_tree=True)
    elif v == 1:
        import math
        d.ellipse([6, top + 6, 57, top + 57], gravel)
        d.ellipse([9, top + 9, 54, top + 54], outline=hedge, width=2)
        for k in range(8):
            a = k * math.pi / 4
            d.line([32, top + 32, 32 + int(math.cos(a) * 22), top + 32 + int(math.sin(a) * 22)], hedge, 2)
        for k in range(8):
            a = (k + 0.5) * math.pi / 4
            x, y = 32 + int(math.cos(a) * 14), 32 + int(math.sin(a) * 14)
            flowers(b, look, x - 3, y - 3, x + 3, y + 3, rng, n=5)
        d.ellipse([28, top + 28, 36, top + 36], c("#d8d0c0"), OUTLINE)
        for x, y in ((3, 3), (60, 3), (3, 60), (60, 60)):
            tree_at(b, look, v, x, y, 3, rng, conifer_tree=True)
    else:
        d.line([2, top + 32, 61, top + 32], gravel, 5)
        for x0 in (6, 22, 38):
            flowers(b, look, x0, 8, x0 + 13, 26, rng, n=22)
            flowers(b, look, x0, 38, x0 + 13, 56, rng, n=22)
        # The pergola over the walk, its posts and beams.
        for x in range(8, 58, 8):
            d.line([x, top + 29, x, top + 35], c("#8a6a4a"))
        d.line([6, top + 29, 58, top + 29], c("#a8845a"))
        d.line([6, top + 35, 58, top + 35], c("#a8845a"))
        b.casters.append((1, 6, 29, 59, 36, 6))
        tree_at(b, look, v, 60, 4, 3, rng)
    return b

def city_park(look, v):
    """A city park on 4 by 4 tiles, rolling lawns and many trees: a lake with paths round it and a bandstand; a lake the other
    side; or a meadow with a playing field, a duck pond and an avenue of trees."""
    b = Building(4, 4, height=LIFT)
    d = b.d
    rng = random.Random(11400 + v)
    top = b.lift
    lawn_box(b, look, 0, 0, 127, 127, rng)
    keep_off = []
    if v == 0:
        pond(b, look, 86, 38, 24, 18, rng)
        keep_off.append((58, 16, 114, 62))
        path_line(b, look, [(0, 90), (30, 70), (64, 72), (96, 90), (127, 84)])
        path_line(b, look, [(64, 0), (58, 40), (64, 72), (60, 127)])
    elif v == 1:
        pond(b, look, 42, 86, 26, 20, rng)
        keep_off.append((12, 62, 74, 110))
        path_line(b, look, [(0, 40), (40, 48), (80, 40), (127, 52)])
        path_line(b, look, [(90, 0), (84, 60), (96, 127)])
    else:
        # A playing field, marked out, and a small pond; an avenue down the middle.
        fx0, fy0, fx1, fy1 = 76, 66, 122, 116
        d.rectangle([fx0, top + fy0, fx1, top + fy1], c("#7cb85a") if look not in ("snow", "bare") else shade(green_lawn(look)[0], 1.05))
        if look != "snow":
            d.rectangle([fx0 + 2, top + fy0 + 2, fx1 - 2, top + fy1 - 2], outline=c("#f2f2ea"))
            d.line([fx0 + 2, top + (fy0 + fy1) // 2, fx1 - 2, top + (fy0 + fy1) // 2], c("#f2f2ea"))
        keep_off.append((fx0 - 4, fy0 - 4, fx1 + 4, fy1 + 4))
        pond(b, look, 26, 26, 14, 10, rng)
        keep_off.append((8, 12, 44, 40))
        path_line(b, look, [(60, 0), (60, 127)], 4)
        for y in range(8, 124, 14):
            tree_at(b, look, v, 52, y, 4, rng)
            tree_at(b, look, v, 68, y, 4, rng)
        keep_off.append((46, 0, 74, 127))
    if v < 2:
        d.ellipse([86, top + 96, 104, top + 114], c("#efe8d8"))
        d.polygon([(87, top + 95), (103, top + 95), (106, top + 101), (103, top + 107), (87, top + 107), (84, top + 101)],
                  SNOW_ROOF[0] if look == "snow" else c("#3f6b48"), OUTLINE)
        b.casters.append((1, 84, 95, 106, 108, 8))
        keep_off.append((80, 90, 110, 118))
    for _ in range(30):
        x, y = rng.randrange(6, 122), rng.randrange(6, 122)
        if any(a <= x <= cc and e <= y <= f for a, e, cc, f in keep_off):
            continue
        tree_at(b, look, v, x, y, rng.randrange(5, 9), rng, conifer_tree=rng.random() < 0.25)
    for x, y in ((40, 74), (80, 76), (56, 30)) if v < 2 else ((30, 60), (40, 100)):
        bench(b, x, y)
    return b

def allotments(look, v):
    """Allotments on 2 by 2 tiles, narrow plots of vegetables in rows, sheds and water butts: plots either side of a middle path;
    or long plots across; or plots round a greenhouse with fruit trees."""
    b = Building(2, 2, height=LIFT + 4)
    d = b.d
    rng = random.Random(11500 + v)
    top = b.lift
    lawn_box(b, look, 0, 0, 63, 63, rng)
    soil = c("#6b4a30") if look != "snow" else SNOW_GROUND
    growing = look in ("spring", "summer", "dry")

    def plot(x0, y0, x1, y1, k, across):
        d.rectangle([x0, top + y0, x1, top + y1], soil)
        if growing:
            if across:
                for yy in range(y0 + 2, y1, 3):
                    d.line([x0 + 2, top + yy, x1 - 2, top + yy], c("#5f9f42") if k % 2 == 0 else c("#8ab04a"))
            else:
                for xx in range(x0 + 2, x1, 3):
                    d.line([xx, top + y0 + 2, xx, top + y1 - 2], c("#5f9f42") if k % 2 == 0 else c("#8ab04a"))

    def shed(x, y):
        d.rectangle([x, top + y, x + 6, top + y + 5], c("#8a6a4a"), outline=OUTLINE)
        b.casters.append((1, x, y, x + 7, y + 6, 6))

    if v == 0:
        path_line(b, look, [(32, 0), (32, 63)], 4)
        for k in range(4):
            for side in (0, 1):
                x0 = 3 if side == 0 else 36
                y0 = 3 + k * 15
                plot(x0, y0, x0 + 25, y0 + 12, k + side, True)
                if (k + side) % 3 == 0:
                    shed(x0 + 18, y0 + 1)
    elif v == 1:
        path_line(b, look, [(0, 32), (63, 32)], 4)
        for k in range(5):
            for side in (0, 1):
                y0 = 3 if side == 0 else 36
                x0 = 3 + k * 12
                plot(x0, y0, x0 + 9, y0 + 25, k + side, False)
        shed(54, 4)
        shed(4, 54)
    else:
        groof, gwall = b.box(24, 22, 40, 36, 8)
        d.rectangle(gwall, GLASSHOUSE)
        d.rectangle(groof, GLASSHOUSE if look != "snow" else SNOW_ROOF[0], outline=OUTLINE)
        for (x0, y0) in ((4, 4), (44, 4), (4, 44), (44, 44)):
            plot(x0, y0, x0 + 15, y0 + 15, x0 + y0, True)
        for x, y in ((30, 6), (30, 54), (8, 30), (56, 30)):
            tree_at(b, look, v, x, y, 3, rng)
    return b

def community_garden(look, v):
    """A community garden of the 1970s on 2 by 2 tiles: raised beds, a greenhouse and a compost corner; or round beds about a
    mural wall and a tool shed; or rows of beds, a hen house and picnic tables."""
    b = Building(2, 2, height=LIFT + 10)
    d = b.d
    rng = random.Random(11600 + v)
    top = b.lift
    lawn_box(b, look, 0, 0, 63, 63, rng)
    if v == 0:
        for (x0, y0) in ((4, 4), (22, 4), (4, 22), (22, 22), (4, 40), (22, 40)):
            d.rectangle([x0, top + y0, x0 + 14, top + y0 + 12], c("#8a6a4a"))
            flowers(b, look, x0 + 2, y0 + 2, x0 + 12, y0 + 10, rng, n=8)
        groof, gwall = b.box(42, 6, 60, 26, 8)
        d.rectangle(gwall, GLASSHOUSE)
        d.rectangle(groof, GLASSHOUSE if look != "snow" else SNOW_ROOF[0], outline=OUTLINE)
        d.rectangle([44, top + 44, 58, top + 58], c("#5a3a22"))
        bench(b, 44, 34)
    elif v == 1:
        for (cx, cy) in ((14, 16), (40, 14), (14, 44), (44, 44)):
            d.ellipse([cx - 9, top + cy - 9, cx + 9, top + cy + 9], c("#8a6a4a"))
            flowers(b, look, cx - 6, cy - 6, cx + 6, cy + 6, rng, n=10)
        mroof, mwall = b.box(24, 26, 38, 30, 10)
        for k, col in enumerate((c("#c0392b"), c("#f2c94c"), c("#2f6fb8"), c("#3a9a5a"))):
            d.rectangle([mwall[0] + k * 4, mwall[1], min(mwall[2], mwall[0] + k * 4 + 3), mwall[3]], col)
        d.rectangle(mwall, outline=OUTLINE)
        d.rectangle(mroof, c("#9a8a78") if look != "snow" else SNOW_ROOF[0], outline=OUTLINE)
        d.rectangle([54, top + 4, 60, top + 10], c("#8a6a4a"), outline=OUTLINE)
    else:
        for k in range(5):
            d.rectangle([4, top + 4 + k * 8, 40, top + 9 + k * 8], c("#8a6a4a"))
            if look in ("spring", "summer", "dry"):
                d.line([6, top + 6 + k * 8, 38, top + 6 + k * 8], c("#5f9f42") if k % 2 else c("#9ab04a"))
        hroof, hwall = b.box(46, 6, 58, 14, 6)
        d.rectangle(hwall, c("#b07a4a"))
        d.rectangle(hroof, c("#7a4a2a") if look != "snow" else SNOW_ROOF[0], outline=OUTLINE)
        d.rectangle([44, top + 16, 60, top + 26], outline=c("#9a9a9a"))
        for x, y in ((10, 50), (30, 50)):
            d.rectangle([x, top + y, x + 10, top + y + 5], c("#a8845a"), outline=OUTLINE)
        tree_at(b, look, v, 54, 50, 6, rng)
    return b

def pocket_park(look, v):
    """A pocket park of the 1960s on one tile, fitted between buildings behind a low wall: a lawn and a shade tree; or seats
    among planters; or a little fountain."""
    b = Building(height=LIFT)
    d = b.d
    rng = random.Random(11700 + v)
    top = b.lift
    d.rectangle([0, top, 31, top + 31], c("#c8bfae") if look != "snow" else c("#e6ecf0"))
    d.rectangle([2, top + 2, 29, top + 3], c("#9a8a78"))
    if v == 0:
        lawn_box(b, look, 4, 4, 27, 22, rng)
        bench(b, 6, 26)
        bench(b, 18, 26)
        tree_at(b, look, v, 16, 14, 7, rng)
    elif v == 1:
        for (x, y) in ((4, 6), (20, 6), (4, 20), (20, 20)):
            d.rectangle([x, top + y, x + 7, top + y + 7], c("#7a7a74"))
            flowers(b, look, x + 1, y + 1, x + 6, y + 6, rng, n=4)
        bench(b, 13, 16)
        tree_at(b, look, v, 8, 12, 3, rng)
    else:
        lawn_box(b, look, 4, 4, 27, 27, rng)
        d.ellipse([10, top + 10, 21, top + 21], c("#bdb6a4"), OUTLINE)
        d.ellipse([12, top + 12, 19, top + 19], c("#6fa0cf") if look != "snow" else c("#dfe8ef"))
        bench(b, 4, 26)
        tree_at(b, look, v, 26, 8, 4, rng)
    return b

def urban_woodland(look, v):
    """A tile of planted woodland: trees close together over leaf litter, some young and some grown."""
    b = Building(height=LIFT)
    rng = random.Random(11800 + v)
    top = b.lift
    floor = [c("#5a6a34"), c("#4e5e2e"), c("#6a7a3e")] if look not in ("snow", "bare", "autumn") else (
        [c("#e4ebf0"), c("#d8e0e6"), c("#eef2f5")] if look == "snow" else [c("#7a6a3e"), c("#6a5a34"), c("#8a7a4a")])
    noise_fill(b.img, (0, top, T, top + T), floor, rng)
    spots = [(8, 9, 6), (22, 7, 5), (15, 20, 7), (27, 24, 5), (5, 26, 4)]
    for k, (x, y, r) in enumerate(spots):
        tree_at(b, look, v, x, y, r, rng, conifer_tree=(k + v) % 3 == 0)
    return b


def botanical_garden(look, v):
    """A botanical garden of the 1910s on 3 by 3 tiles: a palm house of white iron and glass, a dome over its middle and a
    wing either side, palms showing through the glass, beds of every colour, a pond and trees from abroad."""
    b = Building(3, 3, height=3 * STOREY + 8)
    d = b.d
    rng = random.Random(11900 + v)
    top = b.lift
    lawn_box(b, look, 0, 0, 95, 95, rng)
    path_line(b, look, [(48, 95), (48, 50)], 4)
    path_line(b, look, [(4, 72), (92, 72)], 3)
    for (x0, y0) in ((8, 78), (58, 78), (8, 58), (62, 58)):
        flowers(b, look, x0, y0, x0 + 26, y0 + 10, rng, n=24)
    pond(b, look, 85, 50, 7, 5, rng)
    glass = GLASSHOUSE if look != "snow" else SNOW_ROOF[0]
    iron = c("#f2f4f2")
    # The two wings, low, with ribs across them.
    for (x0, x1) in ((14, 38), (58, 82)):
        roof, wall = b.box(x0, 20, x1, 40, 2 * STOREY)
        d.rectangle(wall, shade(GLASSHOUSE, 0.92))
        for xx in range(wall[0] + 3, wall[2], 3):
            d.line([xx, wall[1], xx, wall[3]], iron)
        d.rectangle(wall, outline=OUTLINE)
        rx0, ry0, rx1, ry1 = roof
        d.rectangle(roof, glass)
        # A curved glass roof: lighter along the ridge, ribs across it.
        d.line([rx0 + 1, (ry0 + ry1) // 2, rx1 - 1, (ry0 + ry1) // 2], shade(GLASSHOUSE, 1.12) if look != "snow" else SNOW_ROOF[1])
        for xx in range(rx0 + 3, rx1, 3):
            d.line([xx, ry0 + 1, xx, ry1 - 1], iron)
        if look != "snow":
            for _ in range(3):
                px, py = rng.randint(rx0 + 3, rx1 - 3), rng.randint(ry0 + 3, ry1 - 3)
                d.ellipse([px - 2, py - 2, px + 2, py + 2], c("#4f8a3a"))
        d.rectangle(roof, outline=OUTLINE)
    # The dome over the middle, taller, its ribs running out from the lantern at the top.
    droof, dwall = b.box(36, 14, 60, 46, 3 * STOREY + 4)
    d.rectangle(dwall, shade(GLASSHOUSE, 0.92))
    for xx in range(dwall[0] + 2, dwall[2], 3):
        d.line([xx, dwall[1], xx, dwall[3]], iron)
    d.rectangle([(dwall[0] + dwall[2]) // 2 - 2, dwall[3] - 5, (dwall[0] + dwall[2]) // 2 + 2, dwall[3]], c("#3a5a4a"))
    d.rectangle(dwall, outline=OUTLINE)
    rx0, ry0, rx1, ry1 = droof
    mx, my = (rx0 + rx1) // 2, (ry0 + ry1) // 2
    d.ellipse([rx0, ry0, rx1, ry1], glass, OUTLINE)
    if look != "snow":
        for _ in range(5):
            px, py = mx + rng.randint(-7, 7), my + rng.randint(-10, 10)
            d.ellipse([px - 2, py - 2, px + 2, py + 2], c("#3f7a32"))
    for k in range(12):
        import math
        ang = k * math.pi / 6
        d.line([mx, my, mx + int(math.cos(ang) * (rx1 - rx0) / 2), my + int(math.sin(ang) * (ry1 - ry0) / 2)], iron)
    d.ellipse([mx - 3, my - 3, mx + 3, my + 3], c("#d9b44a"), OUTLINE)
    for x, y, r in ((8, 10, 6), (8, 40, 5), (88, 44, 6), (88, 90, 5)):
        tree_at(b, look, v, x, y, r, rng, conifer_tree=(x + y + v) % 2 == 0)
    return b


def wetland_reserve(look, v):
    """A wetland reserve of the 1980s on 3 by 3 tiles: open water among reeds and sedge, a boardwalk across it and a bird
    hide."""
    b = Building(3, 3, height=LIFT + 6)
    d = b.d
    rng = random.Random(12000 + v)
    top = b.lift
    reeds = [c("#7a8a4a"), c("#6a7a3e"), c("#8a9a56")] if look not in ("snow", "bare", "autumn") else (
        [c("#e4ebf0"), c("#d8e0e6"), c("#eef2f5")] if look == "snow" else [c("#a89a5a"), c("#988a4e"), c("#b8aa6a")])
    noise_fill(b.img, (0, top, 96, top + 96), reeds, rng)
    layouts = (((28, 24, 18, 12), (64, 66, 20, 15), (22, 74, 10, 10)),
               ((66, 22, 20, 14), (24, 62, 16, 14), (70, 78, 12, 8)),
               ((44, 26, 28, 12), (28, 74, 14, 10), (74, 74, 12, 10)))
    for (cx, cy, rx, ry) in layouts[v % 3]:
        pond(b, look, cx, cy, rx, ry, rng, lilies=False)
    walk = c("#9a7a52") if look != "snow" else c("#c8b8a0")
    d.line([0, top + 48, 60, top + 48], walk, 3)
    d.line([60, top + 48, 60, top + 92], walk, 3)
    hroof, hwall = b.box(62, 20, 76, 30, 6)
    d.rectangle(hwall, c("#7a5a3a"))
    d.line([hwall[0] + 2, hwall[1] + 2, hwall[2] - 2, hwall[1] + 2], OUTLINE)
    d.rectangle(hroof, c("#5a4a3a") if look != "snow" else SNOW_ROOF[0], outline=OUTLINE)
    for x, y in ((88, 8), (6, 92), (92, 92)):
        tree_at(b, look, v, x, y, 5, rng)
    return b


def greenway(look, v):
    """A tile of greenway: a paved path for walking and cycling through grass and trees, along or across, some with a bench
    and a lamp."""
    b = Building(height=LIFT)
    d = b.d
    rng = random.Random(12100 + v)
    top = b.lift
    lawn_box(b, look, 0, 0, 31, 31, rng)
    pave = c("#9a9a92") if look != "snow" else c("#e6ecf0")
    line = c("#f2f2ea") if look != "snow" else pave
    if v % 2 == 0:
        d.rectangle([12, top, 19, top + 31], pave)
        d.line([16, top + 2, 16, top + 29], line)
        if v == 2:
            bench(b, 22, 14)
            d.line([9, top + 6, 9, top + 12], c("#4a4a4e"))
            d.point((9, top + 5), c("#f2e8a0"))
            tree_at(b, look, v, 26, 26, 4, rng)
        else:
            tree_at(b, look, v, 5, 12, 4, rng)
            tree_at(b, look, v, 26, 24, 4, rng)
    else:
        d.rectangle([0, top + 12, 31, top + 19], pave)
        d.line([2, top + 16, 29, top + 16], line)
        if v == 3:
            bench(b, 13, 22)
            d.line([24, top + 4, 24, top + 10], c("#4a4a4e"))
            d.point((24, top + 3), c("#f2e8a0"))
            tree_at(b, look, v, 6, 6, 4, rng)
        else:
            tree_at(b, look, v, 8, 6, 4, rng)
            tree_at(b, look, v, 24, 28, 4, rng)
    return b

DOG_COATS = [c("#8a5a32"), c("#f2ede4"), c("#2a2a2e"), c("#c89a52")]


def dog(d, x, y, coat, east=True):
    """A dog seen from the side at play: body, head and tail, with a dark edge so it shows on grass."""
    s = 1 if east else -1
    d.rectangle([min(x, x + 3 * s), y, max(x, x + 3 * s), y + 1], coat)
    d.rectangle([min(x + 3 * s, x + 4 * s), y - 1, max(x + 3 * s, x + 4 * s), y], coat)
    d.point((x - s, y - 1), coat)
    d.point((x, y + 2), OUTLINE)
    d.point((x + 3 * s, y + 2), OUTLINE)
    d.point((x + 4 * s, y - 1), OUTLINE)


def dog_park(look, v):
    """A dog park of the 1990s on one tile, a fenced run with a double gate, a bench, a water bowl and dogs out on it: with a
    jump and a tunnel; or a tire and weave poles; or rough grass under a big shade tree."""
    b = Building(height=LIFT)
    d = b.d
    rng = random.Random(12200 + v)
    top = b.lift
    noise_fill(b.img, (0, top, T, top + T), [c("#8aa65a"), c("#7a964e"), c("#9ab66a")] if look not in ("snow",) else
               [c("#e4ebf0"), c("#d8e0e6"), c("#eef2f5")], rng)
    d.rectangle([1, top + 1, 30, top + 30], outline=c("#3a3a3e"))
    d.rectangle([2, top + 2, 29, top + 29], outline=c("#7a7a7e"))
    # The gate, a worn path in, a bench and the water bowl.
    d.rectangle([13, top + 26, 18, top + 31], c("#a8885a"))
    d.line([13, top + 28, 18, top + 28], c("#3a3a3e"))
    d.rectangle([3, top + 4, 8, top + 5], c("#7a5a3a"), OUTLINE)
    d.ellipse([24, top + 25, 27, top + 27], c("#c8ccd0"), OUTLINE)
    d.point((25, top + 26), c("#4a8ac8"))
    if v == 0:
        # A jump with a striped bar, and a blue tunnel.
        for x in (17, 25):
            d.line([x, top + 6, x, top + 10], c("#f2f2ea"))
        for x in range(17, 26):
            d.point((x, top + 8), c("#c0392b") if x % 2 else c("#f2f2ea"))
        d.rectangle([5, top + 14, 13, top + 17], c("#2f6fb8"), OUTLINE)
        for x in (7, 9, 11):
            d.line([x, top + 15, x, top + 16], c("#24589a"))
        if look != "snow":
            dog(d, 19, top + 12, DOG_COATS[0])
            dog(d, 20, top + 21, DOG_COATS[1], False)
            dog(d, 8, top + 22, DOG_COATS[2])
    elif v == 1:
        # A tire on a frame, and a row of weave poles.
        d.line([5, top + 9, 5, top + 15], c("#5a5a5e"))
        d.line([13, top + 9, 13, top + 15], c("#5a5a5e"))
        d.line([5, top + 9, 13, top + 9], c("#5a5a5e"))
        d.ellipse([7, top + 10, 11, top + 14], c("#2a2a2e"))
        d.point((9, top + 12), c("#8aa65a") if look != "snow" else SNOW)
        for x in range(17, 28, 2):
            d.line([x, top + 7, x, top + 10], c("#f2c94c") if x % 4 == 1 else c("#3c78a8"))
        if look != "snow":
            dog(d, 15, top + 15, DOG_COATS[3])
            dog(d, 22, top + 19, DOG_COATS[0], False)
            dog(d, 7, top + 22, DOG_COATS[1])
    else:
        tree_at(b, look, v, 16, 12, 7, rng)
        if look != "snow":
            dog(d, 5, top + 21, DOG_COATS[2])
            dog(d, 22, top + 20, DOG_COATS[3], False)
            dog(d, 10, top + 25, DOG_COATS[1])
    return b



# ---- sport and culture ---------------------------------------------------------

TURF = c("#5f9f42")
TURF_LINE = c("#f2f2ea")
TRACK_RED = c("#b8603a")
POOL_BLUE = c("#5ab4d8")
STAND = c("#8a8a90")


def pitch(b, look, x0, y0, x1, y1, lines=True, col=None):
    """A marked playing field."""
    d = b.d
    top = b.lift
    green = col or (TURF if look not in ("snow",) else c("#e4ebf0"))
    d.rectangle([x0, top + y0, x1, top + y1], green)
    if look != "snow" and lines:
        d.rectangle([x0 + 2, top + y0 + 2, x1 - 2, top + y1 - 2], outline=TURF_LINE)
        mx = (x0 + x1) // 2
        d.line([mx, top + y0 + 2, mx, top + y1 - 2], TURF_LINE)
        r = max(2, min(x1 - x0, y1 - y0) // 6)
        d.ellipse([mx - r, top + (y0 + y1) // 2 - r, mx + r, top + (y0 + y1) // 2 + r], outline=TURF_LINE)


def stands(b, look, x0, y0, x1, y1, height, col=STAND, roof=True):
    """A grandstand: tiers of seats, its back wall and maybe a roof over the top rows."""
    d = b.d
    rf, wall = b.box(x0, y0, x1, y1, height)
    d.rectangle(wall, shade(col, 0.8))
    d.rectangle(wall, outline=OUTLINE)
    d.rectangle(rf, SNOW_ROOF[0] if look == "snow" else col)
    rx0, ry0, rx1, ry1 = rf
    for yy in range(ry0 + 1, ry1, 2):
        d.line([rx0 + 1, yy, rx1 - 1, yy], shade(col, 1.15) if look != "snow" else SNOW_ROOF[1])
    if roof:
        d.rectangle([rx0, ry0, rx1, ry0 + 2], c("#4a4c52"))
    d.rectangle(rf, outline=OUTLINE)


def hall(b, look, x0, y0, x1, y1, height, wall_col, roof_kind="flat", roof_col=None, storeys=2, rng=None, glass=None, entrance=True):
    """A plain public building: walls, windows and a roof of one kind or another."""
    d = b.d
    rf, wall = b.box(x0, y0, x1, y1, height)
    d.rectangle(wall, wall_col)
    windows(d, wall, storeys, glass=glass or WINDOW, sill=TRIM, every=5, skip_door=entrance)
    if entrance:
        door(d, wall, c("#3a2e26"))
    d.rectangle(wall, outline=OUTLINE)
    if roof_kind == "gable":
        gable_ew(d, rf, roof_col or SHINGLE[0], look)
    elif roof_kind == "barrel":
        d.rectangle(rf, SNOW_ROOF[0] if look == "snow" else (roof_col or c("#7a8088")))
        rx0, ry0, rx1, ry1 = rf
        for yy in range(ry0 + 1, ry1, 2):
            d.line([rx0 + 1, yy, rx1 - 1, yy], shade(roof_col or c("#7a8088"), 1.0 + 0.25 * abs((yy - ry0) - (ry1 - ry0) / 2) / ((ry1 - ry0) / 2 + 1)) if look != "snow" else SNOW_ROOF[1])
        d.rectangle(rf, outline=OUTLINE)
    else:
        flat_roof(b.img, rf, look, rng or random.Random(14000), [("vent", 4, 4)], parapet=roof_col or STONE)
    return rf, wall


def sports_ground(look, v):
    """A sports ground on 2 by 2 tiles: a grass pitch with its lines, a little wooden stand and a clubhouse; or a cricket green
    with its square and pavilion; or a running track round a field."""
    b = Building(2, 2, height=STOREY + 8)
    d = b.d
    rng = random.Random(13000 + v)
    lawn_box(b, look, 0, 0, 63, 63, rng)
    if v == 0:
        pitch(b, look, 4, 4, 59, 44)
        stands(b, look, 6, 48, 34, 56, 6, c("#8a6a4a"), roof=False)
        hall(b, look, 40, 48, 58, 58, STOREY + 2, c("#e8dcb5"), "gable", SHINGLE[0], 1, rng)
    elif v == 1:
        d.ellipse([4, b.lift + 4, 59, b.lift + 50], TURF if look != "snow" else c("#e4ebf0"))
        d.rectangle([28, b.lift + 18, 35, b.lift + 36], c("#c8b48a") if look != "snow" else c("#e4ebf0"))
        hall(b, look, 20, 50, 44, 60, STOREY + 2, c("#f2efe6"), "gable", c("#3f6b48"), 1, rng)
    else:
        d.ellipse([3, b.lift + 3, 60, b.lift + 52], TRACK_RED if look != "snow" else c("#e4ebf0"))
        pitch(b, look, 12, 12, 51, 43, lines=True)
        stands(b, look, 14, 54, 50, 60, 5, STAND, roof=False)
    return b


def lit_fields(look, v):
    """Lit playing fields of the 1980s on 2 by 2 tiles: an all weather pitch under floodlights, with a changing pavilion; or
    two small pitches side by side."""
    b = Building(2, 2, height=5 * STOREY)
    d = b.d
    rng = random.Random(13100 + v)
    lawn_box(b, look, 0, 0, 63, 63, rng)
    turf = c("#3f8a4a") if look != "snow" else c("#cfd9e1")
    if v % 2 == 0:
        pitch(b, look, 4, 4, 59, 46, col=turf)
        hall(b, look, 20, 50, 44, 60, STOREY, c("#c8c0b0"), "flat", None, 1, rng)
    else:
        pitch(b, look, 3, 4, 30, 46, col=turf)
        pitch(b, look, 33, 4, 60, 46, col=turf)
        hall(b, look, 22, 50, 42, 60, STOREY, c("#b8b0a4"), "flat", None, 1, rng)
    for x, y in ((3, 3), (60, 3), (3, 47), (60, 47)):
        gx, gy = b.ground(x, y)
        d.line([gx, gy, gx, gy - 24], c("#6a6a70"))
        d.rectangle([gx - 2, gy - 27, gx + 2, gy - 24], c("#f2f2d8"), OUTLINE)
        b.casters.append((1, x, y, x + 1, y + 1, 26))
    return b


def public_baths(look, v):
    """Public baths of 1900 on 2 by 2 tiles: brick, with a tall chimney for the boilers, round topped windows and BATHS in
    stone over the door."""
    b = Building(2, 2, height=3 * STOREY + 8)
    d = b.d
    rng = random.Random(13200 + v)
    gx0, gy0 = b.ground(2, 48)
    gx1, gy1 = b.ground(61, 61)
    d.rectangle([gx0, gy0, gx1, gy1], c("#b8b2a6") if look != "snow" else c("#dfe5ea"))
    rf, wall = b.box(4, 8, 59, 46, 3 * STOREY)
    brick(d, wall, [c("#9a4a36"), c("#b07a50")][v % 2])
    x0, y0, x1, y1 = wall
    for xx in range(x0 + 4, x1 - 3, 6):
        d.rectangle([xx, y0 + 4, xx + 3, y1 - 6], c("#5a7080"))
        d.arc([xx, y0 + 2, xx + 3, y0 + 6], 180, 360, STONE)
    d.rectangle([(x0 + x1) // 2 - 8, y0 + 1, (x0 + x1) // 2 + 8, y0 + 3], STONE)
    door(d, wall, c("#3a2e26"))
    d.rectangle(wall, outline=OUTLINE)
    # A glass lantern down the roof over the pool hall.
    d.rectangle(rf, SNOW_ROOF[0] if look == "snow" else c("#5b5f6b"), OUTLINE)
    rx0, ry0, rx1, ry1 = rf
    d.rectangle([rx0 + 10, ry0 + 6, rx1 - 10, ry1 - 6], GLASSHOUSE if look != "snow" else SNOW_ROOF[1], OUTLINE)
    chimney(b, 54, 10, 36, look=look, plume=SMOKE)
    return b


def swimming_pool(look, v):
    """A swimming pool of the thirties on 2 by 2 tiles: an open air pool, white edged, with diving boards and a pale changing
    block; or an indoor pool under a curved roof."""
    b = Building(2, 2, height=2 * STOREY + 6)
    d = b.d
    rng = random.Random(13300 + v)
    top = b.lift
    d.rectangle([2, top + 2, 61, top + 61], c("#e8e4da") if look != "snow" else c("#e6ecf0"))
    if v % 2 == 0:
        water = POOL_BLUE if look in ("spring", "summer", "dry") else c("#b8c4cc")
        d.rectangle([8, top + 6, 55, top + 40], water, OUTLINE)
        if look in ("spring", "summer", "dry"):
            for xx in range(14, 55, 8):
                d.line([xx, top + 7, xx, top + 39], c("#7cc8e8"))
        d.rectangle([30, top + 4, 34, top + 10], c("#f2f2ea"), OUTLINE)
        hall(b, look, 6, 46, 57, 58, STOREY + 2, c("#f2efe6"), "flat", c("#d8d4ca"), 1, rng)
    else:
        rf, wall = hall(b, look, 4, 8, 59, 46, 2 * STOREY + 2, c("#d8d0c0"), "barrel", c("#8a9aa8"), 1, rng, glass=c("#7a9ab0"))
        lawn_box(b, look, 2, 50, 61, 61, rng)
    return b


def aquatic_centre(look, v):
    """An aquatic centre of the 1980s on 2 by 2 tiles: a big glazed hall with a water slide curling out of its side and a car
    park."""
    b = Building(2, 2, height=3 * STOREY + 6)
    d = b.d
    rng = random.Random(13400 + v)
    top = b.lift
    d.rectangle([2, top + 46, 61, top + 61], c("#6a6a70") if look != "snow" else c("#e6ecf0"))
    if look != "snow":
        for xx in range(6, 60, 6):
            d.line([xx, top + 48, xx, top + 58], c("#d8d8d0"))
    rf, wall = b.box(4, 6, 50, 42, 3 * STOREY)
    d.rectangle(wall, GLASS_NEW)
    x0, y0, x1, y1 = wall
    for xx in range(x0 + 3, x1, 4):
        d.line([xx, y0, xx, y1], c("#e8eef0"))
    d.rectangle(wall, outline=OUTLINE)
    d.rectangle(rf, SNOW_ROOF[0] if look == "snow" else [c("#d8d8d0"), c("#8aa0b0")][v % 2], OUTLINE)
    rx0, ry0, rx1, ry1 = rf
    d.rectangle([rx0 + 6, ry0 + 6, rx1 - 6, ry1 - 6], GLASSHOUSE if look != "snow" else SNOW_ROOF[1], OUTLINE)
    # The slide: a coloured tube winding down from the roof.
    tube = [c("#f2c94c"), c("#c0392b")][v % 2]
    pts = [(rx1, ry0 + 8), (rx1 + 8, ry0 + 6), (rx1 + 10, ry0 + 14), (rx1 + 4, ry0 + 20), (rx1 + 9, ry0 + 28), (rx1 + 3, ry0 + 34)]
    d.line(pts, tube, 3)
    return b


def tennis_courts(look, v):
    """Tennis courts on 2 by 1 tiles: two grass or red clay courts, lined, with nets and a wire fence round them."""
    b = Building(2, 1, height=LIFT)
    d = b.d
    top = b.lift
    surface = [c("#6aa84a"), c("#c8703a"), c("#4a7aa8")][v % 3] if look != "snow" else c("#e4ebf0")
    d.rectangle([1, top + 1, 62, top + 30], surface)
    if look != "snow":
        for x0 in (4, 34):
            d.rectangle([x0, top + 4, x0 + 25, top + 27], outline=TURF_LINE)
            d.line([x0 + 12, top + 4, x0 + 12, top + 27], c("#e8e8e8"))
            d.line([x0, top + 15, x0 + 25, top + 15], TURF_LINE)
    d.rectangle([1, top + 1, 62, top + 30], outline=c("#4a4c50"))
    return b


def ice_rink(look, v):
    """An indoor ice rink on 2 by 2 tiles: a long hall under a curved roof, pale walls and a car park; or an open air rink of
    the twenties with a warming hut."""
    b = Building(2, 2, height=2 * STOREY + 8)
    d = b.d
    rng = random.Random(13600 + v)
    top = b.lift
    if v % 2 == 0:
        d.rectangle([2, top + 46, 61, top + 61], c("#6a6a70") if look != "snow" else c("#e6ecf0"))
        rf, wall = hall(b, look, 4, 6, 59, 42, 2 * STOREY + 4, c("#d8dce0"), "barrel", c("#7a8a98"), 1, rng)
        cx = (wall[0] + wall[2]) // 2
        d.rectangle([cx - 10, wall[1] + 2, cx + 10, wall[1] + 6], c("#2a5a8a"), OUTLINE)
        for xx in range(cx - 8, cx + 9, 3):
            d.point((xx, wall[1] + 4), c("#e8f2f8"))
        for xx in range(6, 58, 6):
            if rng.random() < 0.5:
                gx, gy = b.ground(xx, 50)
                d.rectangle([gx, gy, gx + 3, gy + 5], rng.choice([c("#c0392b"), c("#2f5f8a"), c("#d8d4ca"), c("#3f8a4a")]), OUTLINE)
    else:
        lawn_box(b, look, 0, 0, 63, 63, rng)
        d.rounded_rectangle([6, top + 6, 57, top + 44], 8, c("#e8f2f8"), outline=c("#8a8a90"))
        if look != "snow":
            d.line([31, top + 8, 31, top + 42], c("#c0392b"))
            d.ellipse([27, top + 21, 35, top + 29], outline=c("#3c78a8"))
        hall(b, look, 20, 48, 44, 60, STOREY + 2, c("#8a6a4a"), "gable", SHINGLE[1], 1, rng)
    return b


def ballpark(look, v):
    """A ballpark of the 1910s on 3 by 3 tiles: home plate in a corner, the outfield fanning out from it to a fence, a diamond of
    red earth with its bases, stands behind home along both sides, and a scoreboard out past the fence."""
    b = Building(3, 3, height=3 * STOREY + 6)
    d = b.d
    rng = random.Random(13700 + v)
    top = b.lift
    lawn_box(b, look, 0, 0, 95, 95, rng)
    field = TURF if look != "snow" else c("#e4ebf0")
    dirt = c("#b8703a") if look != "snow" else c("#dfe5ea")
    east = v % 2 == 0
    hx, hy = (78, 78) if east else (17, 78)
    r = 74
    start, end = (180, 270) if east else (270, 360)
    d.pieslice([hx - r, top + hy - r, hx + r, top + hy + r], start, end, field, outline=c("#3f5a2e"))
    # The diamond: home, first, second and third, the infield dirt round them.
    sx = -1 if east else 1
    bases = [(hx, hy), (hx + sx * 0, hy - 20), (hx + sx * 20, hy - 20), (hx + sx * 20, hy)]
    bases = [(hx, hy), (hx, hy - 22), (hx + sx * 22, hy - 22), (hx + sx * 22, hy)]
    d.polygon([(x, top + y) for x, y in bases], dirt)
    inner = [(hx + sx * 4, hy - 4), (hx + sx * 4, hy - 18), (hx + sx * 18, hy - 18), (hx + sx * 18, hy - 4)]
    d.polygon([(x, top + y) for x, y in inner], field)
    for (x, y) in bases:
        d.rectangle([x - 1, top + y - 1, x + 1, top + y + 1], TURF_LINE)
    d.ellipse([hx + sx * 11 - 2, top + hy - 13, hx + sx * 11 + 2, top + hy - 9], dirt)
    # Stands behind home, along the bottom and the near side.
    if east:
        stands(b, look, 40, 84, 94, 94, 3 * STOREY, STAND)
        stands(b, look, 84, 40, 94, 84, 3 * STOREY, STAND)
        sbx = 6
    else:
        stands(b, look, 1, 84, 56, 94, 3 * STOREY, STAND)
        stands(b, look, 1, 40, 11, 84, 3 * STOREY, STAND)
        sbx = 72
    gx, gy = b.ground(sbx, 8)
    d.rectangle([gx, gy - 10, gx + 16, gy], c("#2a3a2a"), OUTLINE)
    for k in range(3):
        d.line([gx + 2, gy - 8 + k * 3, gx + 14, gy - 8 + k * 3], c("#f2e6a0"))
    b.casters.append((1, sbx, 7, sbx + 17, 9, 10))
    return b


def arena(look, v):
    """An arena of the sixties on 3 by 3 tiles: a great round roof on a ring of glass, with a plaza and car parks round it."""
    b = Building(3, 3, height=3 * STOREY + 8)
    d = b.d
    rng = random.Random(13800 + v)
    top = b.lift
    d.rectangle([1, top + 1, 94, top + 94], c("#6a6a70") if look != "snow" else c("#e6ecf0"))
    if look != "snow":
        for xx in range(4, 92, 6):
            d.line([xx, top + 4, xx, top + 14], c("#d8d8d0"))
            d.line([xx, top + 82, xx, top + 92], c("#d8d8d0"))
    rf, wall = b.box(14, 16, 81, 78, 3 * STOREY)
    d.rectangle(wall, GLASS_NEW)
    for xx in range(wall[0] + 2, wall[2], 3):
        d.line([xx, wall[1], xx, wall[3]], c("#c8d8e0"))
    d.rectangle(wall, outline=OUTLINE)
    roofc = [c("#c8c4bc"), c("#b87a4a")][v % 2] if look != "snow" else SNOW_ROOF[0]
    d.ellipse([rf[0], rf[1], rf[2], rf[3]], roofc, OUTLINE)
    mx, my = (rf[0] + rf[2]) // 2, (rf[1] + rf[3]) // 2
    import math
    for k in range(16):
        a = k * math.pi / 8
        d.line([mx, my, mx + int(math.cos(a) * (rf[2] - rf[0]) / 2), my + int(math.sin(a) * (rf[3] - rf[1]) / 2)], shade(roofc, 0.88))
    d.ellipse([mx - 4, my - 4, mx + 4, my + 4], shade(roofc, 1.1), OUTLINE)
    return b


def stadium(look, v):
    """A stadium on 4 by 4 tiles: a pitch in a bowl of stands, roofed along the sides, floodlights at the corners and a car park
    round it."""
    b = Building(4, 4, height=4 * STOREY + 10)
    d = b.d
    rng = random.Random(13900 + v)
    top = b.lift
    d.rectangle([1, top + 1, 126, top + 126], c("#6a6a70") if look != "snow" else c("#e6ecf0"))
    seat = [c("#3c78a8"), c("#c0392b"), c("#3f8a4a")][v % 3]
    # The bowl: stands round the four sides, the pitch in the middle.
    stands(b, look, 10, 10, 117, 28, 4 * STOREY, seat)
    stands(b, look, 10, 28, 28, 98, 3 * STOREY, seat, roof=False)
    stands(b, look, 99, 28, 117, 98, 3 * STOREY, seat, roof=False)
    pitch(b, look, 30, 30, 97, 96)
    stands(b, look, 10, 98, 117, 116, 4 * STOREY, seat)
    for x, y in ((6, 6), (120, 6), (6, 120), (120, 120)):
        gx, gy = b.ground(x, y)
        d.line([gx, gy, gx, gy - 34], c("#6a6a70"), 2)
        d.rectangle([gx - 4, gy - 38, gx + 4, gy - 33], c("#f2f2d8"), OUTLINE)
        b.casters.append((1, x, y, x + 2, y + 2, 36))
    return b


def golf_course(look, v):
    """A golf course on 4 by 4 tiles: fairways of short grass between the rough, greens with flags, sand bunkers, a pond, trees
    and a clubhouse."""
    b = Building(4, 4, height=STOREY + 8)
    d = b.d
    rng = random.Random(14000 + v)
    top = b.lift
    rough = [c("#4f8a3a"), c("#47803a"), c("#5a9442")] if look not in ("snow", "bare", "autumn") else GRASS[look]
    noise_fill(b.img, (0, top, 128, top + 128), rough, rng)
    fair = c("#7cbc5a") if look not in ("snow",) else c("#eef2f5")
    holes = [((10, 100), (60, 20)), ((70, 110), (112, 60)), ((112, 40), (80, 10))] if v % 2 == 0 else \
        [((20, 20), (100, 30)), ((110, 50), (40, 70)), ((30, 110), (110, 104))]
    for (tx, ty), (gx, gy) in holes:
        d.line([tx, top + ty, gx, top + gy], fair, 14)
        d.ellipse([gx - 8, top + gy - 6, gx + 8, top + gy + 6], c("#8ad06a") if look != "snow" else c("#f2f6f9"))
        d.line([gx, top + gy, gx, top + gy - 8], c("#e8e8e8"))
        d.rectangle([gx, top + gy - 8, gx + 4, top + gy - 6], c("#c0392b"))
        bx, by = (tx + gx) // 2 + 8, (ty + gy) // 2 - 6
        d.ellipse([bx - 5, top + by - 3, bx + 5, top + by + 3], c("#e8dcb0") if look != "snow" else c("#e6ecf0"))
    pond(b, look, 64, 64, 10, 7, rng)
    for _ in range(18):
        x, y = rng.randrange(6, 122), rng.randrange(6, 122)
        tree_at(b, look, v, x, y, rng.randrange(4, 7), rng, conifer_tree=rng.random() < 0.3)
    hall(b, look, 4, 4, 26, 18, STOREY + 2, c("#f2efe6"), "gable", c("#3f6b48"), 1, rng)
    return b


def skater(d, x, y, shirt):
    """A skater on a board, seen from above: board, legs and shirt, and a head."""
    d.line([x - 2, y + 1, x + 2, y + 1], c("#2a2a2e"))
    d.rectangle([x - 1, y - 2, x + 1, y], shirt)
    d.point((x, y - 3), c("#d8a880"))


def skate_park(look, v):
    """A skate park of the nineties on one tile, fenced: a deep bowl with its coping and a quarter pipe; or a half pipe and a
    fun box; each with a grind rail, a wall of paint and skaters."""
    b = Building(height=LIFT)
    d = b.d
    top = b.lift
    snow = look == "snow"
    conc = c("#cac6be") if not snow else c("#e6ecf0")
    coping = c("#8a9096")
    d.rectangle([1, top + 1, 30, top + 30], conc, c("#5a5a5e"))
    if v % 2 == 0:
        # The bowl: coping, then rings of concrete darker as it deepens.
        d.ellipse([3, top + 3, 21, top + 19], coping, OUTLINE)
        for k, f in enumerate((0.9, 0.8, 0.7, 0.6)):
            d.ellipse([4 + 2 * k, top + 4 + 2 * k, 20 - 2 * k, top + 18 - 2 * k], shade(conc, f))
        d.line([8, top + 15, 16, top + 15], c("#2f6fb8"))
        # A quarter pipe along the east side, light at its lip and dark where it meets the ground.
        for k in range(6):
            d.line([24 + k, top + 3, 24 + k, top + 20], shade(conc, 0.95 - 0.06 * (5 - k)))
        d.line([29, top + 3, 29, top + 20], coping)
        d.rectangle([23, top + 3, 29, top + 20], outline=OUTLINE)
        d.line([5, top + 24, 15, top + 24], c("#3a3c40"))
        for x in (6, 14):
            d.point((x, top + 25), c("#3a3c40"))
        if not snow:
            skater(d, 11, top + 11, c("#c0392b"))
            skater(d, 20, top + 25, c("#f2c94c"))
    else:
        # A half pipe across the tile: a lip at each end, the flat dark in the middle.
        for k in range(17):
            f = 0.62 + 0.035 * abs(8 - k)
            d.line([3, top + 3 + k, 28, top + 3 + k], shade(conc, f))
        d.line([3, top + 3, 28, top + 3], coping)
        d.line([3, top + 19, 28, top + 19], coping)
        d.rectangle([3, top + 3, 28, top + 19], outline=OUTLINE)
        d.rectangle([17, top + 22, 26, top + 26], shade(conc, 0.82), OUTLINE)
        d.line([18, top + 24, 25, top + 24], coping)
        d.line([4, top + 24, 13, top + 24], c("#3a3c40"))
        for x in (5, 12):
            d.point((x, top + 25), c("#3a3c40"))
        if not snow:
            skater(d, 10, top + 9, c("#3c78a8"))
            skater(d, 21, top + 14, c("#c0392b"))
    if not snow:
        for k, col in enumerate((c("#c0392b"), c("#f2c94c"), c("#3c78a8"), c("#5aa040"))):
            d.line([3 + k * 2, top + 29, 4 + k * 2, top + 27], col)
    return b



def rec_centre(look, v):
    """A recreation centre of the fifties on 2 by 2 tiles: a gym hall of pale brick with high windows, a lower wing of rooms, and
    a ball court beside it."""
    b = Building(2, 2, height=2 * STOREY + 8)
    d = b.d
    rng = random.Random(14200 + v)
    top = b.lift
    lawn_box(b, look, 0, 0, 63, 63, rng)
    court = c("#c8703a") if look != "snow" else c("#e4ebf0")
    d.rectangle([38, top + 40, 60, top + 60], court)
    if look != "snow":
        d.rectangle([40, top + 42, 58, top + 58], outline=TURF_LINE)
        d.line([40, top + 50, 58, top + 50], TURF_LINE)
    hall(b, look, 4, 6, 40, 36, 2 * STOREY + 4, [PALE_BRICK, c("#c8a888")][v % 2], "flat", None, 1, rng, entrance=False)
    hall(b, look, 4, 38, 34, 56, STOREY + 2, shade([PALE_BRICK, c("#c8a888")][v % 2], 0.94), "flat", None, 1, rng)
    return b


def bandstand(look, v):
    """A bandstand on one tile: an eight sided roof with a finial on dark iron posts, over a raised round floor with a
    railing and steps, a band on it, in a ring of lawn and paths."""
    b = Building(height=LIFT + 8)
    d = b.d
    rng = random.Random(14300 + v)
    top = b.lift
    lawn_box(b, look, 0, 0, 31, 31, rng)
    path_line(b, look, [(0, 27), (31, 27)])
    # The floor: a stone base, its edge in shade, and steps down to the path.
    d.ellipse([5, top + 10, 26, top + 25], c("#b8ad98"), OUTLINE)
    d.ellipse([5, top + 8, 26, top + 23], c("#efe8d8"), OUTLINE)
    d.rectangle([13, top + 23, 18, top + 26], c("#d8cfbc"), OUTLINE)
    # The band, in their coats.
    for x, y in ((11, 16), (15, 18), (19, 16), (15, 14)):
        d.rectangle([x, top + y, x + 1, top + y + 1], c("#2c3a5a"))
    # The railing round the edge, and the posts up to the roof.
    for x in range(7, 25, 3):
        d.point((x, top + 19), c("#2a2a2e"))
    iron = c("#2a2a2e")
    for x in (7, 11, 20, 24):
        d.line([x, top + 11, x, top + 19], iron)
    # The roof, its ridges, and a gold finial on top.
    roofc = [c("#3f6b48"), c("#8a3b2e")][v % 2] if look != "snow" else SNOW_ROOF[0]
    d.polygon([(9, top + 2), (22, top + 2), (26, top + 7), (22, top + 12), (9, top + 12), (5, top + 7)], roofc, OUTLINE)
    for (x, y) in ((9, 2), (22, 2), (26, 7), (22, 12), (9, 12), (5, 7)):
        d.line([15, top + 7, x, top + y], shade(roofc, 0.75))
    d.rectangle([15, top + 5, 16, top + 7], c("#d9b44a"))
    b.casters.append((1, 5, 2, 27, 13, 10))
    return b


def theatre_front(b, look, wall, name_col, lights=False):
    """A theatre's front: a canopy over the doors and its name up in lights."""
    d = b.d
    x0, y0, x1, y1 = wall
    cx = (x0 + x1) // 2
    d.rectangle([x0 + 6, y1 - 8, x1 - 6, y1 - 6], c("#2a2a30"))
    for xx in range(x0 + 7, x1 - 6, 2):
        d.point((xx, y1 - 7), c("#f2e6a0") if lights else c("#d8d0c0"))
    d.rectangle([cx - 8, y0 + 3, cx + 8, y0 + 8], name_col, OUTLINE)
    for xx in range(cx - 6, cx + 7, 2):
        d.point((xx, y0 + 5), c("#f2f2ea"))
    d.rectangle([cx - 3, y1 - 5, cx + 3, y1], c("#3a2e26"))


def variety_theatre(look, v):
    """A variety theatre of 1900 on 2 by 2 tiles: an ornate front of brick and stone, a canopy and bills by the doors, and the
    stage house rising behind."""
    b = Building(2, 2, height=4 * STOREY + 6)
    d = b.d
    rng = random.Random(14400 + v)
    rf, wall = b.box(6, 6, 57, 22, 4 * STOREY + 4)
    brick(d, wall, c("#9a4a36"))
    d.rectangle(rf, SNOW_ROOF[0] if look == "snow" else c("#5b5f6b"), OUTLINE)
    rf, wall = b.box(4, 24, 59, 52, 3 * STOREY)
    d.rectangle(wall, [c("#d8c8a8"), c("#c8a888")][v % 2])
    windows(d, wall, 3, glass=c("#46586a"), sill=TRIM, every=6, skip=[((wall[0] + wall[2]) // 2 - 9, (wall[0] + wall[2]) // 2 + 9)])
    theatre_front(b, look, wall, [c("#8a2a2a"), c("#2a4a6a")][v % 2])
    d.rectangle(wall, outline=OUTLINE)
    cornice(d, wall, STONE)
    flat_roof(b.img, rf, look, rng, [("vent", 6, 6)], parapet=STONE)
    return b


def picture_palace(look, v):
    """A picture palace of the twenties and thirties on 2 by 2 tiles: a tall front with its name up the vertical sign, a lit
    canopy over the doors and a long plain auditorium behind."""
    b = Building(2, 2, height=4 * STOREY + 8)
    d = b.d
    rng = random.Random(14500 + v)
    rf, wall = b.box(8, 4, 55, 34, 3 * STOREY)
    d.rectangle(wall, c("#b8a890"))
    d.rectangle(wall, outline=OUTLINE)
    flat_roof(b.img, rf, look, rng, [("vent", 8, 8), ("vent", 30, 12)], parapet=c("#a89878"))
    rf, wall = b.box(4, 36, 59, 52, 3 * STOREY + 4)
    col = [c("#e8dcc0"), c("#d8c0a0"), c("#c8d0c0")][v % 3]
    d.rectangle(wall, col)
    x0, y0, x1, y1 = wall
    for xx in range(x0 + 3, x1 - 2, 4):
        d.line([xx, y0 + 2, xx, y1 - 10], shade(col, 0.9))
    theatre_front(b, look, wall, [c("#c0392b"), c("#2f5f8a"), c("#3f8a4a")][v % 3], lights=True)
    d.rectangle(wall, outline=OUTLINE)
    flat_roof(b.img, rf, look, rng, [], parapet=shade(col, 1.06))
    sx = x1 - 10
    d.rectangle([sx, y0 - 14, sx + 4, y1 - 10], [c("#c0392b"), c("#2f5f8a"), c("#3f8a4a")][v % 3], OUTLINE)
    for yy in range(y0 - 12, y1 - 12, 3):
        d.point((sx + 2, yy), c("#f2e6a0"))
    return b


def multiplex(look, v):
    """A multiplex cinema of the eighties on 2 by 2 tiles: a big plain box of coloured panels, a glass foyer at the corner with
    posters, and a car park."""
    b = Building(2, 2, height=3 * STOREY + 4)
    d = b.d
    rng = random.Random(14600 + v)
    top = b.lift
    d.rectangle([2, top + 44, 61, top + 61], c("#6a6a70") if look != "snow" else c("#e6ecf0"))
    if look != "snow":
        for xx in range(6, 60, 6):
            d.line([xx, top + 46, xx, top + 58], c("#d8d8d0"))
    rf, wall = b.box(4, 4, 59, 40, 3 * STOREY)
    col = [c("#c8c4bc"), c("#b8b0c8"), c("#c8b8a8")][v % 3]
    d.rectangle(wall, col)
    x0, y0, x1, y1 = wall
    d.rectangle([x0, y0 + 3, x1, y0 + 6], [c("#c0392b"), c("#7a3f7a"), c("#2f5f8a")][v % 3])
    d.rectangle([x0 + 2, y1 - 8, x0 + 20, y1], GLASS_NEW, OUTLINE)
    for xx in range(x0 + 24, x1 - 2, 6):
        d.rectangle([xx, y1 - 7, xx + 3, y1 - 2], c("#f2e6a0"))
    d.rectangle(wall, outline=OUTLINE)
    flat_roof(b.img, rf, look, rng, [("vent", 8, 8), ("vent", 24, 8), ("vent", 40, 8), ("hatch", 20, 20)], parapet=shade(col, 1.06))
    if v % 3 != 0:
        roof_sign(b.d, rf, [c("#2a2a30"), c("#8a2a6a"), c("#2a4a8a")][v % 3], 8)
    return b


def opera_house(look, v):
    """An opera house on 3 by 2 tiles: a grand front of pale stone with columns and a pediment, a dome or a tall stage house
    behind, and steps down to a square."""
    b = Building(3, 2, height=5 * STOREY + 8)
    d = b.d
    rng = random.Random(14700 + v)
    top = b.lift
    d.rectangle([2, top + 48, 93, top + 61], c("#c8bfae") if look != "snow" else c("#e6ecf0"))
    rf, wall = b.box(20, 6, 75, 24, 5 * STOREY)
    d.rectangle(wall, shade(STONE, 0.95))
    d.rectangle(wall, outline=OUTLINE)
    d.rectangle(rf, SNOW_ROOF[0] if look == "snow" else c("#5b6a6a"), OUTLINE)
    rf, wall = b.box(6, 26, 89, 46, 3 * STOREY)
    d.rectangle(wall, c("#e8e0cc"))
    x0, y0, x1, y1 = wall
    cx = (x0 + x1) // 2
    for xx in range(cx - 20, cx + 21, 4):
        d.line([xx, y0 + 4, xx, y1], c("#c8c0ac"))
    d.polygon([(cx - 22, y0 + 4), (cx, y0 - 6), (cx + 22, y0 + 4)], c("#e8e0cc"), OUTLINE)
    windows(d, wall, 3, glass=c("#46586a"), sill=TRIM, every=6, skip=[(cx - 22, cx + 22)])
    d.rectangle([cx - 4, y1 - 6, cx + 4, y1], c("#3a2e26"))
    d.rectangle(wall, outline=OUTLINE)
    flat_roof(b.img, rf, look, rng, [], parapet=STONE)
    if v % 2 == 0:
        mx, my = (rf[0] + rf[2]) // 2, (rf[1] + rf[3]) // 2
        d.ellipse([mx - 9, my - 9, mx + 9, my + 9], c("#6e8f86") if look != "snow" else SNOW_ROOF[0], OUTLINE)
        d.ellipse([mx - 2, my - 6, mx + 1, my - 3], c("#a8c8be"))
    return b


def museum(look, v):
    """A museum on 3 by 2 tiles: a long classical front with columns along it and wide steps, wings either side, and a lawn
    with a statue; or brick with a clock tower."""
    b = Building(3, 2, height=4 * STOREY + 8)
    d = b.d
    rng = random.Random(14800 + v)
    lawn_box(b, look, 0, 44, 95, 63, rng)
    path_line(b, look, [(47, 63), (47, 44)], 4)
    col = [c("#ddd5c2"), c("#a85a40")][v % 2]
    rf, wall = b.box(4, 8, 91, 40, 3 * STOREY)
    if v % 2 == 0:
        d.rectangle(wall, col)
    else:
        brick(d, wall, col)
    x0, y0, x1, y1 = wall
    cx = (x0 + x1) // 2
    windows(d, wall, 3, glass=c("#46586a"), sill=TRIM, every=6, skip=[(cx - 16, cx + 16)])
    d.rectangle([cx - 16, y0, cx + 16, y1], c("#e8e0cc"))
    for xx in range(cx - 14, cx + 15, 4):
        d.line([xx, y0 + 3, xx, y1], c("#c8c0ac"))
    d.polygon([(cx - 18, y0 + 3), (cx, y0 - 6), (cx + 18, y0 + 3)], c("#e8e0cc"), OUTLINE)
    d.rectangle([cx - 18, y1 + 1, cx + 18, y1 + 3], STONE)
    d.rectangle(wall, outline=OUTLINE)
    flat_roof(b.img, rf, look, rng, [("skylight", 10, 8), ("skylight", 60, 8)], parapet=STONE)
    if v % 2 == 1:
        troof, twall = b.box(80, 10, 88, 18, 4 * STOREY + 6)
        brick(d, twall, col)
        d.ellipse([twall[0] + 1, twall[1] + 3, twall[2] - 1, twall[1] + 9], c("#f2f2ea"), OUTLINE)
        d.rectangle(twall, outline=OUTLINE)
        d.rectangle(troof, SNOW_ROOF[0] if look == "snow" else c("#5b6a6a"), OUTLINE)
    else:
        gx, gy = b.ground(70, 54)
        d.rectangle([gx - 2, gy - 2, gx + 2, gy], c("#a8a090"))
        d.rectangle([gx - 1, gy - 9, gx + 1, gy - 2], c("#6e6a62"))
        b.casters.append((1, 69, 53, 72, 55, 9))
    return b


def art_gallery(look, v):
    """An art gallery on 2 by 2 tiles: pale stone rooms lit from above through long skylights, a sculpture garden; or a modern
    white box with a single big window."""
    b = Building(2, 2, height=2 * STOREY + 8)
    d = b.d
    rng = random.Random(14900 + v)
    lawn_box(b, look, 0, 44, 63, 63, rng)
    if v % 2 == 0:
        rf, wall = b.box(4, 6, 59, 40, 2 * STOREY + 2)
        d.rectangle(wall, c("#e0d8c4"))
        x0, y0, x1, y1 = wall
        for xx in range(x0 + 3, x1 - 2, 6):
            d.line([xx, y0 + 2, xx, y1], shade(c("#e0d8c4"), 0.9))
        door(d, wall, c("#3a2e26"))
        d.rectangle(wall, outline=OUTLINE)
        flat_roof(b.img, rf, look, rng, [("skylight", 6, 6), ("skylight", 22, 6), ("skylight", 38, 6)], parapet=STONE)
        for x, y in ((16, 54), (44, 52)):
            gx, gy = b.ground(x, y)
            d.ellipse([gx - 3, gy - 6, gx + 3, gy], c("#6e6a62"), OUTLINE)
    else:
        rf, wall = b.box(8, 8, 55, 40, 2 * STOREY + 4)
        d.rectangle(wall, c("#f2f2ee"))
        x0, y0, x1, y1 = wall
        d.rectangle([x0 + 6, y0 + 4, x1 - 6, y1 - 4], GLASS_DARK)
        d.rectangle(wall, outline=OUTLINE)
        flat_roof(b.img, rf, look, rng, [], parapet=c("#d8d8d4"), busy=False)
        rx0, ry0, rx1, ry1 = rf
        d.rectangle([rx0 + 6, ry0 + 6, rx1 - 6, ry0 + 10], c("#d0d4d8"), OUTLINE)
        d.line([rx0 + 7, ry0 + 8, rx1 - 7, ry0 + 8], SKYLIGHT if look != "snow" else SNOW_ROOF[1])
        # A sculpture on the lawn, a red steel arc.
        gx, gy = b.ground(46, 56)
        d.arc([gx - 6, gy - 10, gx + 6, gy + 2], 180, 360, c("#c0392b"), 2)
        b.casters.append((1, 40, 55, 52, 56, 6))
    return b


def concert_hall(look, v):
    """A concert hall of the sixties on 3 by 2 tiles: a big angular roof, folded like paper, over a glass foyer, with a plaza in
    front."""
    b = Building(3, 2, height=4 * STOREY + 8)
    d = b.d
    rng = random.Random(15000 + v)
    top = b.lift
    d.rectangle([2, top + 46, 93, top + 61], c("#cfcac0") if look != "snow" else c("#e6ecf0"))
    rf, wall = b.box(6, 8, 89, 42, 3 * STOREY)
    d.rectangle(wall, GLASS_NEW)
    for xx in range(wall[0] + 2, wall[2], 4):
        d.line([xx, wall[1], xx, wall[3]], c("#c8d8e0"))
    d.rectangle(wall, outline=OUTLINE)
    roofc = [c("#d8d4ca"), c("#a8b0a8")][v % 2] if look != "snow" else SNOW_ROOF[0]
    d.rectangle(rf, roofc)
    rx0, ry0, rx1, ry1 = rf
    for k, xx in enumerate(range(rx0, rx1, 10)):
        d.polygon([(xx, ry1), (xx + 5, ry0), (xx + 10, ry1)], shade(roofc, 0.86 if k % 2 else 1.06))
    d.rectangle(rf, outline=OUTLINE)
    return b


def zoo(look, v):
    """A zoo on 4 by 4 tiles: paddocks with animals, a pond for the birds, a big house for the elephants, paths and trees."""
    b = Building(4, 4, height=2 * STOREY + 8)
    d = b.d
    rng = random.Random(15100 + v)
    top = b.lift
    lawn_box(b, look, 0, 0, 127, 127, rng)
    path_line(b, look, [(0, 64), (40, 60), (64, 64), (100, 70), (127, 64)], 3)
    path_line(b, look, [(64, 0), (60, 40), (64, 64), (70, 100), (64, 127)], 3)
    earth = c("#b89a6a") if look != "snow" else c("#e4ebf0")
    paddocks = [(6, 6, 54, 54), (74, 6, 122, 54), (6, 74, 54, 122), (74, 74, 122, 122)]
    animals = [c("#8a8a8a"), c("#d8b060"), c("#2a2a2a"), c("#a8784a")]
    for k, (x0, y0, x1, y1) in enumerate(paddocks):
        d.rectangle([x0, top + y0, x1, top + y1], earth, outline=c("#6b5a44"))
        if k == (v % 4):
            pond(b, look, (x0 + x1) // 2, (y0 + y1) // 2, 14, 10, rng)
            continue
        for _ in range(3):
            ax, ay = rng.randrange(x0 + 6, x1 - 6), rng.randrange(y0 + 6, y1 - 6)
            d.ellipse([ax - 3, top + ay - 2, ax + 3, top + ay + 2], animals[k], OUTLINE)
        tree_at(b, look, v, x0 + 8, y0 + 8, 5, rng)
    house = paddocks[(v + 2) % 4]
    hall(b, look, house[0] + 14, house[1] + 14, house[2] - 4, house[3] - 8, STOREY + 6, c("#c8a878"), "barrel", c("#8a6a4a"), 1, rng)
    return b


def fairground(look, v):
    """A fairground on 3 by 3 tiles: a big wheel, a carousel under its striped top, stalls and caravans on trampled grass."""
    b = Building(3, 3, height=5 * STOREY + 4)
    d = b.d
    rng = random.Random(15200 + v)
    top = b.lift
    ground = [c("#8aa65a"), c("#7a964e"), c("#a8a070")] if look != "snow" else [c("#e4ebf0"), c("#d8e0e6"), c("#eef2f5")]
    noise_fill(b.img, (0, top, 96, top + 96), ground, rng)
    # The carousel.
    cx, cy = (30, 60) if v % 2 == 0 else (66, 30)
    for k in range(12):
        import math
        a0 = k * 30
        d.pieslice([cx - 14, top + cy - 14, cx + 14, top + cy + 14], a0, a0 + 30, c("#c0392b") if k % 2 else c("#f2f2ea"))
    d.ellipse([cx - 3, top + cy - 3, cx + 3, top + cy + 3], c("#d9b44a"), OUTLINE)
    # The big wheel, standing up from its foot.
    wx, wy = (68, 70) if v % 2 == 0 else (26, 74)
    gx, gy = b.ground(wx, wy)
    d.line([gx - 6, gy, gx, gy - 18], c("#6a6a70"), 2)
    d.line([gx + 6, gy, gx, gy - 18], c("#6a6a70"), 2)
    d.ellipse([gx - 16, gy - 34, gx + 16, gy - 2], outline=c("#8a8a90"), width=2)
    for k in range(8):
        a = k * math.pi / 4
        px, py = gx + int(math.cos(a) * 16), gy - 18 + int(math.sin(a) * 16)
        d.line([gx, gy - 18, px, py], c("#8a8a90"))
        d.rectangle([px - 2, py - 1, px + 2, py + 2], [c("#c0392b"), c("#f2c94c"), c("#3c78a8")][k % 3])
    b.casters.append((1, wx - 16, wy - 2, wx + 16, wy + 1, 34))
    for k in range(5):
        sx, sy = 8 + k * 17, 10 if v % 2 == 0 else 86
        d.rectangle([sx, top + sy, sx + 12, top + sy + 7], [c("#c0392b"), c("#f2c94c"), c("#3c78a8"), c("#3f8a4a"), c("#7a3f7a")][k], OUTLINE)
    return b


def amusement_park(look, v):
    """An amusement park of the fifties on 3 by 3 tiles: a roller coaster's track looping round, a log flume, rides, and paths
    between bright stalls."""
    b = Building(3, 3, height=5 * STOREY + 6)
    d = b.d
    rng = random.Random(15300 + v)
    top = b.lift
    d.rectangle([1, top + 1, 94, top + 94], c("#d8d0c0") if look != "snow" else c("#e6ecf0"))
    lawn_box(b, look, 6, 6, 40, 40, rng)
    lawn_box(b, look, 56, 56, 90, 90, rng)
    # The coaster's track, a loop of white rail on red supports.
    pts = [(10, 70), (30, 50), (60, 46), (86, 30), (80, 10), (50, 14), (20, 24), (8, 50), (10, 70)] if v % 2 == 0 else \
        [(86, 70), (66, 50), (36, 46), (10, 30), (16, 10), (46, 14), (76, 24), (88, 50), (86, 70)]
    for (x, y) in pts:
        gx, gy = b.ground(x, y)
        d.line([gx, gy, gx, gy - 10], c("#c0392b"))
    d.line([(x, y + top - 10) for x, y in pts], c("#f2f2ea"), 2)
    # A flume of blue water, and a few rides.
    d.line([(60 if v % 2 == 0 else 34, top + 92), (70 if v % 2 == 0 else 24, top + 70), (88 if v % 2 == 0 else 8, top + 66)], c("#5ab4d8"), 4)
    for (x, y, col) in ((24, 84, c("#f2c94c")), (48, 70, c("#3c78a8")), (72, 82, c("#7a3f7a"))):
        d.ellipse([x - 6, top + y - 6, x + 6, top + y + 6], col, OUTLINE)
    return b


def drive_in(look, v):
    """A drive-in cinema of the fifties on 3 by 3 tiles: a big white screen on its frame, rows of parking bays curving towards it
    with posts for the speakers, and a snack bar in the middle."""
    b = Building(3, 3, height=4 * STOREY)
    d = b.d
    rng = random.Random(15400 + v)
    top = b.lift
    d.rectangle([1, top + 1, 94, top + 94], c("#8a8478") if look != "snow" else c("#e6ecf0"))
    for k in range(6):
        y = 30 + k * 10
        d.arc([6, top + y - 20, 89, top + y + 20], 20, 160, c("#6a645a") if look != "snow" else c("#d0d8de"))
        for x in range(12, 86, 8):
            d.point((x, top + y + 6), c("#3a3a3e"))
    if look != "snow":
        for _ in range(8 + 4 * v):
            x, y = rng.randrange(14, 82), rng.randrange(34, 88)
            d.rectangle([x, top + y, x + 3, top + y + 5], rng.choice([c("#c0392b"), c("#2f5f8a"), c("#e8d070"), c("#3f8a4a")]), OUTLINE)
    gx, gy = b.ground(48, 14)
    d.rectangle([gx - 30, gy - 24, gx + 30, gy - 4], c("#f2f2ee"), OUTLINE)
    d.line([gx - 24, gy - 4, gx - 24, gy], c("#6a6a70"), 2)
    d.line([gx + 24, gy - 4, gx + 24, gy], c("#6a6a70"), 2)
    b.casters.append((1, 18, 13, 79, 15, 24))
    hall(b, look, 40, 54, 56, 64, STOREY, c("#e8dcb5"), "flat", c("#c0392b"), 1, rng)
    if v % 2 == 1:
        # The sign out by the road, lit up on its pole.
        gx, gy = b.ground(84, 90)
        d.line([gx, gy, gx, gy - 18], c("#6a6a70"), 2)
        d.rectangle([gx - 8, gy - 26, gx + 8, gy - 18], c("#c0392b"), OUTLINE)
        for xx in range(gx - 6, gx + 7, 2):
            d.point((xx, gy - 22), c("#f2e6a0"))
        b.casters.append((1, 80, 89, 89, 90, 26))
    return b


def aquarium(look, v):
    """An aquarium of the nineties on 3 by 2 tiles: curving glass and pale panels like a wave, an outdoor seal pool and a
    plaza."""
    b = Building(3, 2, height=3 * STOREY + 8)
    d = b.d
    rng = random.Random(15500 + v)
    top = b.lift
    d.rectangle([2, top + 44, 93, top + 61], c("#cfcac0") if look != "snow" else c("#e6ecf0"))
    pond(b, look, 80 if v % 2 == 0 else 14, 52, 10, 6, rng, lilies=False)
    rf, wall = b.box(6, 6, 89, 40, 3 * STOREY)
    d.rectangle(wall, [c("#e8eef0"), c("#d8e4e8")][v % 2])
    x0, y0, x1, y1 = wall
    for xx in range(x0 + 2, x1 - 1, 3):
        h = 3 + int(3 * (1 + __import__("math").sin(xx / 6)))
        d.line([xx, y0 + h, xx, y1 - 6], GLASS_NEW)
    d.rectangle([x0 + 30, y1 - 6, x0 + 52, y1], GLASS_DARK)
    d.rectangle(wall, outline=OUTLINE)
    d.rectangle(rf, SNOW_ROOF[0] if look == "snow" else c("#a8c4d0"))
    rx0, ry0, rx1, ry1 = rf
    for yy in range(ry0 + 2, ry1, 3):
        d.line([rx0 + 1, yy, rx1 - 1, yy], shade(c("#a8c4d0"), 1.08) if look != "snow" else SNOW_ROOF[1])
    d.rectangle(rf, outline=OUTLINE)
    return b


def convention_centre(look, v):
    """A convention centre of the seventies on 3 by 3 tiles: a vast low hall under a flat roof of trusses, a glass entrance at
    the corner, flags along the front and a car park."""
    b = Building(3, 3, height=3 * STOREY + 6)
    d = b.d
    rng = random.Random(15600 + v)
    top = b.lift
    d.rectangle([1, top + 68, 94, top + 94], c("#6a6a70") if look != "snow" else c("#e6ecf0"))
    if look != "snow":
        for xx in range(4, 92, 6):
            d.line([xx, top + 72, xx, top + 90], c("#d8d8d0"))
    rf, wall = b.box(4, 6, 91, 64, 3 * STOREY)
    d.rectangle(wall, [c("#c8c4bc"), c("#b8b4ac")][v % 2])
    x0, y0, x1, y1 = wall
    d.rectangle([x0 + 2, y1 - 10, x0 + 30, y1], GLASS_NEW, OUTLINE)
    d.rectangle(wall, outline=OUTLINE)
    rx0, ry0, rx1, ry1 = rf
    if v % 2 == 0:
        d.rectangle(rf, SNOW_ROOF[0] if look == "snow" else c("#8a8a90"))
        for xx in range(rx0 + 6, rx1, 8):
            d.line([xx, ry0 + 1, xx, ry1 - 1], c("#6a6a70") if look != "snow" else SNOW_ROOF[1])
    else:
        # A shell roof in white, lit along its crown and shaded down its southern fall.
        rows = max(1, ry1 - ry0)
        for k, yy in enumerate(range(ry0, ry1 + 1)):
            t = abs(k - rows * 0.35) / rows
            d.line([rx0, yy, rx1, yy], SNOW_ROOF[0] if look == "snow" else shade(c("#dcdcd6"), 1.08 - 0.5 * t))
        for xx in range(rx0 + 10, rx1, 12):
            d.line([xx, ry0 + 1, xx, ry1 - 1], shade(c("#dcdcd6"), 0.85))
    d.rectangle(rf, outline=OUTLINE)
    for k, xx in enumerate(range(40, 90, 8)):
        gx, gy = b.ground(xx, 66)
        d.line([gx, gy, gx, gy - 10], c("#d8d8d0"))
        d.rectangle([gx + 1, gy - 10, gx + 5, gy - 7], [c("#c0392b"), c("#3c78a8"), c("#f2c94c"), c("#3f8a4a")][k % 4])
    return b

# Civic buildings: the town hall and its successors, a post office, a cemetery and a memorial garden, a fountain, a clock
# tower and a war memorial.


def clock_face(d, cx, cy, r=3):
    """A round clock face with its hands at ten to two."""
    d.ellipse([cx - r, cy - r, cx + r, cy + r], c("#f2f2ea"), OUTLINE)
    d.line([cx, cy, cx - 1, cy - r + 1], OUTLINE)
    d.line([cx, cy, cx + r - 1, cy - 1], OUTLINE)


def flagpole(b, x, y, height, col=c("#c0392b")):
    """A flag on a pole standing at tile pixel x, y."""
    d = b.d
    top = b.lift
    d.line([x, y + top, x, y + top - height], c("#d0d0d0"))
    d.rectangle([x + 1, y + top - height, x + 5, y + top - height + 3], col)
    b.casters.append((1, x, y - 1, x + 1, y, height))


def paving(b, look, x0, y0, x1, y1, col=c("#c8c0b0")):
    """Paved ground, laid in slabs."""
    d = b.d
    top = b.lift
    d.rectangle([x0, y0 + top, x1, y1 + top], SNOW_GROUND if look == "snow" else col)
    if look != "snow":
        for xx in range(x0 + 4, x1, 5):
            d.line([xx, y0 + top, xx, y1 + top], shade(col, 0.93))
        for yy in range(y0 + 4, y1, 5):
            d.line([x0, yy + top, x1, yy + top], shade(col, 0.93))


def town_hall(look, v):
    """A town hall of 1900 on 2 by 2 tiles: two storeys of stone or brick round a door under a pediment, a clock cupola on
    the roof and a flag; or a frontier hall of white boards under a steep roof."""
    b = Building(2, 2, height=2 * STOREY + 22)
    d = b.d
    rng = random.Random(15000 + v)
    lawn_box(b, look, 0, 40, 63, 63, rng)
    paving(b, look, 26, 40, 37, 63)
    roof, wall = b.box(6, 10, 57, 38, 2 * STOREY + 4)
    x0, y0, x1, y1 = wall
    cx = (x0 + x1) // 2
    if v == 0:
        d.rectangle(wall, c("#d8cfb8"))
        for yy in range(y0 + 3, y1, 4):
            d.line([x0 + 1, yy, x1 - 1, yy], shade(c("#d8cfb8"), 0.92))
    elif v == 1:
        brick(d, wall, c("#9a4a36"))
        d.rectangle([x0, y0 + STOREY + 2, x1, y0 + STOREY + 3], STONE)
    else:
        siding(d, wall, c("#ece6d6"))
    windows(d, wall, 2, glass=c("#46586a"), sill=TRIM, every=6, width=2, height=4, skip=[(cx - 7, cx + 7)])
    d.rectangle([cx - 3, y1 - 7, cx + 3, y1], c("#4a3226"))
    d.polygon([(cx - 7, y1 - 8), (cx, y1 - 13), (cx + 7, y1 - 8)], TRIM, OUTLINE)
    d.rectangle([cx - 7, y1 + 1, cx + 7, y1 + 2], STONE)
    d.rectangle(wall, outline=OUTLINE)
    if v == 2:
        gable_ew(d, roof, SHINGLE[1], look)
    else:
        flat_roof(b.img, roof, look, rng, [("stack", 6, 4), ("stack", 44, 4)], parapet=STONE)
        cornice(d, wall, STONE)
    # The clock cupola over the door.
    croof, cwall = b.box(cx - 5, 18, cx + 5, 28, 2 * STOREY + 16)
    d.rectangle(cwall, TRIM)
    clock_face(d, cx, cwall[1] + 5)
    d.rectangle(cwall, outline=OUTLINE)
    rx0, ry0, rx1, ry1 = croof
    d.polygon([(rx0, ry1), (cx, ry0 - 6), (rx1, ry1)], SNOW_ROOF[0] if look == "snow" else c("#5b6a6a"), OUTLINE)
    flagpole(b, 52, 46, 16)
    return b


def city_hall(look, v):
    """A city hall of the 1920s on 2 by 2 tiles: four storeys of pale stone, a row of columns up the front, and a dome or a
    tall clock tower over the middle."""
    b = Building(2, 2, height=4 * STOREY + 26)
    d = b.d
    rng = random.Random(15100 + v)
    paving(b, look, 0, 44, 63, 63)
    for x in (10, 53):
        tree_at(b, look, v, x, 58, 4, rng)
    roof, wall = b.box(4, 8, 59, 42, 4 * STOREY)
    col = [c("#ddd5c2"), c("#c8b898")][v % 2]
    d.rectangle(wall, col)
    x0, y0, x1, y1 = wall
    cx = (x0 + x1) // 2
    windows(d, wall, 4, glass=c("#46586a"), sill=TRIM, every=5, width=2, height=3, skip=[(cx - 12, cx + 12)])
    d.rectangle([cx - 12, y0, cx + 12, y1], c("#e8e0cc"))
    for xx in range(cx - 10, cx + 11, 4):
        d.line([xx, y0 + 4, xx, y1], c("#c8c0ac"))
    d.rectangle([cx - 3, y1 - 6, cx + 3, y1], c("#3a2e26"))
    d.rectangle([cx - 14, y1 + 1, cx + 14, y1 + 3], STONE)
    d.rectangle(wall, outline=OUTLINE)
    cornice(d, wall, STONE)
    flat_roof(b.img, roof, look, rng, [("hatch", 6, 6), ("hatch", 44, 6)], parapet=STONE)
    if v == 0:
        # A dome on a drum.
        droof, dwall = b.box(cx - 8, 16, cx + 8, 30, 4 * STOREY + 10)
        d.rectangle(dwall, col)
        for xx in range(dwall[0] + 2, dwall[2], 3):
            d.line([xx, dwall[1] + 1, xx, dwall[3] - 1], c("#46586a"))
        d.rectangle(dwall, outline=OUTLINE)
        d.pieslice([cx - 9, droof[1] - 10, cx + 9, droof[1] + 8], 180, 360, SNOW_ROOF[0] if look == "snow" else c("#6e8f86"), OUTLINE)
        d.line([cx, droof[1] - 10, cx, droof[1] - 14], c("#b08a3a"))
    else:
        troof, twall = b.box(cx - 5, 18, cx + 5, 28, 4 * STOREY + 22)
        d.rectangle(twall, col)
        clock_face(d, cx, twall[1] + 5)
        for yy in range(twall[1] + 11, twall[3] - 2, 5):
            d.rectangle([cx - 1, yy, cx + 1, yy + 2], c("#46586a"))
        d.rectangle(twall, outline=OUTLINE)
        rx0, ry0, rx1, ry1 = troof
        d.polygon([(rx0, ry1), (cx, ry0 - 8), (rx1, ry1)], SNOW_ROOF[0] if look == "snow" else c("#5b6a6a"), OUTLINE)
    flagpole(b, 6, 50, 18)
    return b


def civic_centre(look, v):
    """A civic centre of the 1970s on 2 by 2 tiles: a council chamber and an office block of concrete and glass over a paved
    plaza; or a hall on stilts with its chamber standing out from it."""
    b = Building(2, 2, height=5 * STOREY + 8)
    d = b.d
    rng = random.Random(15200 + v)
    paving(b, look, 0, 0, 63, 63, c("#bdb8ac"))
    concrete = c("#b4b0a6")
    if v == 0:
        roof, wall = b.box(6, 6, 34, 30, 5 * STOREY)
        d.rectangle(wall, concrete)
        ribbon_windows(d, wall, 5, glass=GLASS_DARK, gap=4)
        d.rectangle(wall, outline=OUTLINE)
        flat_roof(b.img, roof, look, rng, [("vent", 4, 4), ("hatch", 16, 10)], parapet=c("#8a8a84"))
        roof, wall = b.box(38, 18, 58, 38, 2 * STOREY + 2)
        d.rectangle(wall, concrete)
        for xx in range(wall[0] + 2, wall[2] - 1, 3):
            d.line([xx, wall[1] + 2, xx, wall[3] - 2], shade(concrete, 0.85))
        d.rectangle(wall, outline=OUTLINE)
        rx0, ry0, rx1, ry1 = roof
        d.polygon([(rx0, ry1), (rx0 + 4, ry0), (rx1 - 4, ry0), (rx1, ry1)], SNOW_ROOF[0] if look == "snow" else c("#7a7c80"), OUTLINE)
    else:
        roof, wall = b.box(4, 8, 59, 34, 4 * STOREY)
        x0, y0, x1, y1 = wall
        d.rectangle([x0, y0, x1, y1 - 7], concrete)
        ribbon_windows(d, [x0, y0, x1, y1 - 7], 3, glass=GLASS_DARK, gap=5)
        for xx in range(x0 + 4, x1, 10):
            d.rectangle([xx, y1 - 7, xx + 2, y1], shade(concrete, 0.8))
        d.rectangle(wall, outline=OUTLINE)
        flat_roof(b.img, roof, look, rng, [("vent", 6, 4), ("vent", 40, 4)], parapet=c("#8a8a84"))
        rx0, ry0, rx1, ry1 = roof
        mx = (rx0 + rx1) // 2
        d.ellipse([mx - 9, ry0 + 4, mx + 9, ry1 - 4], SNOW_ROOF[0] if look == "snow" else c("#8a6a4a"), OUTLINE)
    # A pool in the plaza.
    gx, gy = b.ground(14, 50)
    d.rectangle([gx, gy, gx + 16, gy + 6], c("#8a8a84"))
    d.rectangle([gx + 1, gy + 1, gx + 15, gy + 5], c("#5a8fbf") if look != "snow" else c("#dfe8ef"))
    flagpole(b, 48, 52, 14, c("#2a4a8a"))
    return b


def post_office(look, v):
    """A post office on 2 by 1 tiles: brick with a stone band and its sign over the doors, a post box out front; or a low
    pale one of the 1950s with a long counter window."""
    b = Building(2, 1, height=2 * STOREY + 6)
    d = b.d
    rng = random.Random(15300 + v)
    paving(b, look, 0, 26, 63, 31)
    if v == 0:
        roof, wall = b.box(3, 5, 60, 25, 2 * STOREY + 4)
        brick(d, wall, c("#9a4a36"))
        x0, y0, x1, y1 = wall
        d.rectangle([x0, y0 + 2, x1, y0 + 5], STONE)
        windows(d, wall, 2, sill=TRIM, every=6, skip_door=True)
        flat_roof(b.img, roof, look, rng, [("stack", 6, 3), ("stack", 50, 3)], parapet=STONE)
    else:
        roof, wall = b.box(3, 8, 60, 25, STOREY + 6)
        d.rectangle(wall, PALE_BRICK)
        x0, y0, x1, y1 = wall
        d.rectangle([x0 + 4, y0 + 3, x1 - 12, y1 - 3], GLASS_NEW)
        flat_roof(b.img, roof, look, rng, [("vent", 6, 4)], parapet=c("#8a8a84"))
    x0, y0, x1, y1 = wall
    cx = (x0 + x1) // 2
    d.rectangle([cx - 10, y0 + 1, cx + 10, y0 + 4], c("#c0392b"))
    for xx in range(cx - 8, cx + 9, 2):
        d.point((xx, y0 + 2), c("#f2e6a0"))
    d.rectangle([cx - 3, y1 - 6, cx + 3, y1], c("#3a2e26"))
    d.rectangle(wall, outline=OUTLINE)
    # The post box, and a van.
    gx, gy = b.ground(48, 28)
    d.rectangle([gx, gy - 4, gx + 2, gy], c("#c0392b"), OUTLINE)
    if v == 1:
        gx, gy = b.ground(8, 27)
        d.rectangle([gx, gy, gx + 7, gy + 3], c("#c0392b"))
        d.rectangle([gx + 5, gy, gx + 7, gy + 1], GLASS_DARK)
    return b


def shelter(look, v):
    """A shelter on one lot: a plain three-storey brick hostel of the old missions with a lamp over its door and a bench by
    it; or a newer one of pale brick with a glass porch and a bike rack."""
    b = Building(1, 1, height=3 * STOREY + 6)
    d = b.d
    rng = random.Random(15350 + v)
    paving(b, look, 0, 25, 31, 31)
    if v == 0:
        roof, wall = b.box(3, 6, 28, 24, 3 * STOREY)
        brick(d, wall, c("#7a3e30"))
        windows(d, wall, 3, sill=STONE, every=5, skip_door=True)
        gable_ew(d, roof, SHINGLE[2], look)
        roof_feature(d, roof, ("stack", 19, 1), look)
    else:
        roof, wall = b.box(3, 8, 28, 24, 2 * STOREY + 4)
        d.rectangle(wall, PALE_BRICK)
        windows(d, wall, 2, glass=GLASS_NEW, every=6, skip_door=True)
        flat_roof(b.img, roof, look, rng, [("vent", 4, 3)], parapet=c("#8a8a84"))
    x0, y0, x1, y1 = wall
    cx = (x0 + x1) // 2
    d.rectangle(wall, outline=OUTLINE)
    if v == 0:
        # The door, a lamp over it and the sign.
        d.rectangle([cx - 2, y1 - 6, cx + 2, y1], c("#3a2e26"))
        d.point((cx, y1 - 8), c("#f2d06a"))
        d.rectangle([cx - 6, y0 + 1, cx + 6, y0 + 3], c("#2e4a3a"))
    else:
        d.rectangle([cx - 4, y1 - 7, cx + 4, y1], GLASS_NEW, OUTLINE)
        gx, gy = b.ground(4, 27)
        for k in range(3):
            d.line([gx + k * 3, gy, gx + k * 3, gy + 3], c("#5a6066"))
    # A bench by the door.
    gx, gy = b.ground(cx + 6, 27)
    d.rectangle([gx, gy, gx + 5, gy + 1], c("#7a5a3a"))
    return b


# Landmarks: one of each, earned by the town.

def founders_statue(look, v):
    """The founder's statue on one tile: a bronze figure with an arm raised, on a stepped stone plinth with a gilt plaque, in a
    round of paving with paths, flower beds and lamps."""
    b = Building(height=3 * STOREY + 6)
    d = b.d
    rng = random.Random(16100)
    top = b.lift
    lawn_box(b, look, 0, 0, 31, 31, rng)
    pave = SNOW_GROUND if look == "snow" else c("#d2cabb")
    d.rectangle([14, top, 17, top + 31], pave)
    d.rectangle([0, top + 14, 31, top + 17], pave)
    d.ellipse([3, top + 4, 28, top + 29], pave, OUTLINE)
    flowers(b, look, 5, 22, 10, 26, rng, 6)
    flowers(b, look, 21, 22, 26, 26, rng, 6)
    # A wide low step, then the plinth with its plaque.
    step, step_wall = b.box(9, 11, 22, 22, 2)
    d.rectangle(step_wall, c("#b0a690"), OUTLINE)
    d.rectangle(step, SNOW_ROOF[0] if look == "snow" else c("#e2dbc8"), OUTLINE)
    roof, wall = b.box(12, 13, 19, 19, STOREY + 6)
    d.rectangle(wall, c("#c8bea6"), OUTLINE)
    d.rectangle(roof, SNOW_ROOF[0] if look == "snow" else c("#e8e1d0"), OUTLINE)
    d.rectangle([wall[0] + 2, wall[1] + 3, wall[2] - 2, wall[1] + 5], c("#c8a040"))
    # The figure in greened bronze, outlined so it stands off the stone: coat, shoulders, head, and the raised arm.
    bronze = c("#5f9a80")
    light = c("#86bba2")
    fx = (roof[0] + roof[2]) // 2
    fy = roof[3] - 1
    d.line([fx + 2, fy - 10, fx + 6, fy - 17], OUTLINE, 3)
    d.line([fx + 2, fy - 10, fx + 6, fy - 17], bronze)
    d.polygon([(fx - 3, fy), (fx + 2, fy), (fx + 1, fy - 8), (fx - 2, fy - 8)], bronze, OUTLINE)
    d.rectangle([fx - 3, fy - 12, fx + 2, fy - 8], bronze, OUTLINE)
    d.line([fx - 2, fy - 11, fx - 2, fy - 2], light)
    d.ellipse([fx - 2, fy - 17, fx + 1, fy - 13], light, OUTLINE)
    b.casters[-1] = (1, 12, 13, 20, 20, STOREY + 22)
    for (x, y) in ((5, 12), (26, 12)):
        gx, gy = b.ground(x, y)
        d.line([gx, gy, gx, gy - 8], c("#3a3c40"))
        d.rectangle([gx - 1, gy - 10, gx + 1, gy - 8], c("#f2d06a"))
    return b



def mayors_mansion(look, v):
    """The mayor's mansion on 2 by 2 tiles: a big house of pale stone with a portico and a hipped roof, behind iron railings,
    a drive round a fountain and trees."""
    b = Building(2, 2, height=3 * STOREY + 12)
    d = b.d
    rng = random.Random(16200)
    lawn_box(b, look, 0, 0, 63, 63, rng)
    path_line(b, look, [(32, 63), (32, 52), (22, 46), (32, 40), (42, 46), (32, 52)], 3)
    roof, wall = b.box(8, 8, 55, 32, 3 * STOREY)
    d.rectangle(wall, c("#e2d8c2"))
    x0, y0, x1, y1 = wall
    cx = (x0 + x1) // 2
    windows(d, wall, 3, glass=c("#46586a"), sill=TRIM, every=6, skip=[(cx - 6, cx + 6)])
    # The portico: four columns under a pediment.
    d.rectangle([cx - 7, y0 + 4, cx + 7, y1], c("#efe8d8"))
    for xx in range(cx - 6, cx + 7, 4):
        d.line([xx, y0 + 6, xx, y1], c("#c8c0ac"))
    d.polygon([(cx - 9, y0 + 5), (cx, y0 - 2), (cx + 9, y0 + 5)], TRIM, OUTLINE)
    d.rectangle([cx - 2, y1 - 6, cx + 2, y1], c("#4a3226"))
    d.rectangle(wall, outline=OUTLINE)
    # A hipped roof: slopes on all four sides up to a short ridge.
    rx0, ry0, rx1, ry1 = roof
    my = (ry0 + ry1) // 2
    slate = SNOW_ROOF if look == "snow" else [c("#6a7480"), c("#58616c"), c("#4a525c")]
    d.rectangle(roof, slate[1], OUTLINE)
    d.polygon([(rx0, ry0), (rx1, ry0), (rx1 - 10, my), (rx0 + 10, my)], slate[0])
    d.polygon([(rx0, ry1), (rx1, ry1), (rx1 - 10, my), (rx0 + 10, my)], slate[2])
    d.line([rx0 + 10, my, rx1 - 10, my], OUTLINE)
    roof_feature(d, roof, ("stack", 8, 3), look)
    roof_feature(d, roof, ("stack", 38, 3), look)
    # The fountain in the middle of the drive.
    gx, gy = b.ground(32, 46)
    d.ellipse([gx - 4, gy - 2, gx + 4, gy + 2], c("#a8a090"), OUTLINE)
    d.ellipse([gx - 3, gy - 1, gx + 3, gy + 1], c("#5a8fbf") if look != "snow" else c("#dfe8ef"))
    # Railings along the street.
    gx0, gy0 = b.ground(1, 62)
    gx1, _ = b.ground(62, 62)
    d.line([gx0, gy0 - 3, gx1, gy0 - 3], c("#2a2a30"))
    for xx in range(gx0, gx1 + 1, 2):
        if abs(xx - 32) > 3:
            d.line([xx, gy0 - 3, xx, gy0], c("#2a2a30"))
    for (x, y) in ((6, 44), (58, 44), (6, 56), (58, 56)):
        tree_at(b, look, v, x, y, 4, rng)
    return b


def exhibition_hall(look, v):
    """The exhibition hall on 3 by 3 tiles: a long hall of iron and glass with a great barrel roof and a dome where it
    crosses, flags along the front and a paved forecourt with a fountain."""
    b = Building(3, 3, height=4 * STOREY + 16)
    d = b.d
    rng = random.Random(16300)
    lawn_box(b, look, 0, 0, 95, 95, rng)
    paving(b, look, 8, 66, 87, 94)
    roof, wall = b.box(6, 14, 89, 62, 3 * STOREY)
    d.rectangle(wall, c("#d8cfb8"))
    x0, y0, x1, y1 = wall
    # Tall arched windows of glass between iron ribs.
    for xx in range(x0 + 3, x1 - 4, 7):
        d.rectangle([xx, y0 + 4, xx + 4, y1 - 3], c("#7fa8c0"))
        d.ellipse([xx, y0 + 2, xx + 4, y0 + 6], c("#7fa8c0"))
    cx = (x0 + x1) // 2
    d.rectangle([cx - 6, y1 - 9, cx + 6, y1], c("#4a3a2a"), OUTLINE)
    d.rectangle(wall, outline=OUTLINE)
    # The barrel roof: glass panes in bands, lit along the top.
    rx0, ry0, rx1, ry1 = roof
    glass = SNOW_ROOF if look == "snow" else [c("#a8c8d8"), c("#8fb4c8"), c("#7898ac")]
    band = (ry1 - ry0) // 3
    for k in range(3):
        d.rectangle([rx0, ry0 + k * band, rx1, ry0 + (k + 1) * band], glass[k])
    for xx in range(rx0 + 4, rx1, 6):
        d.line([xx, ry0, xx, ry1], c("#4a5058"))
    d.rectangle(roof, outline=OUTLINE)
    # The dome at the crossing.
    dx = (rx0 + rx1) // 2
    dy = (ry0 + ry1) // 2
    d.ellipse([dx - 12, dy - 14, dx + 12, dy + 8], glass[0], OUTLINE)
    d.ellipse([dx - 7, dy - 10, dx + 7, dy + 2], glass[1])
    d.line([dx, dy - 22, dx, dy - 14], c("#4a5058"))
    d.rectangle([dx + 1, dy - 22, dx + 5, dy - 19], c("#c0392b"))
    b.casters.append((1, 36, 24, 60, 40, 4 * STOREY + 16))
    for xx in range(14, 84, 12):
        flagpole(b, xx, 70, 14, [c("#c0392b"), c("#2f5f9a"), c("#e0b030")][(xx // 12) % 3])
    gx, gy = b.ground(48, 82)
    d.ellipse([gx - 7, gy - 3, gx + 7, gy + 3], c("#a8a090"), OUTLINE)
    d.ellipse([gx - 5, gy - 2, gx + 5, gy + 2], c("#5a8fbf") if look != "snow" else c("#dfe8ef"))
    for (x, y) in ((4, 86), (92, 86)):
        tree_at(b, look, v, x, y, 4, rng)
    return b


def observation_tower(look, v):
    """The observation tower on 2 by 2 tiles: a slim concrete shaft rising high over the town to a round deck of glass and
    a mast, on a plaza."""
    b = Building(2, 2, height=40 * STOREY)
    d = b.d
    plaza(b, look, 1, 1, 62, 62)
    # The shaft.
    roof, wall = b.box(28, 28, 35, 35, 36 * STOREY)
    d.rectangle(wall, c("#d0ccc4"))
    x0, y0, x1, y1 = wall
    d.line([x0 + 2, y0, x0 + 2, y1], c("#e8e4dc"))
    d.line([x1 - 2, y0, x1 - 2, y1], c("#b0aca4"))
    d.rectangle(wall, outline=OUTLINE)
    # The deck: a wide round of glass with a roof, near the top.
    cx = (x0 + x1) // 2
    deck_y = y0 + 6
    d.ellipse([cx - 14, deck_y - 4, cx + 14, deck_y + 6], c("#b8b4ac"), OUTLINE)
    d.rectangle([cx - 13, deck_y, cx + 13, deck_y + 4], c("#5d8aa0"))
    for xx in range(cx - 11, cx + 12, 3):
        d.line([xx, deck_y, xx, deck_y + 4], c("#3d5a6c"))
    d.ellipse([cx - 13, deck_y - 6, cx + 13, deck_y + 1], SNOW_ROOF[0] if look == "snow" else c("#e0dcd4"), OUTLINE)
    # The mast, with a light at the top.
    d.line([cx, deck_y - 6, cx, deck_y - 30], c("#c8c8c8"), 2)
    d.point((cx, deck_y - 31), c("#e05040"))
    b.casters[-1] = (1, 28, 28, 36, 36, 40 * STOREY)
    for (x, y) in ((8, 54), (56, 54)):
        bench(b, x, y)
    return b


def conservatory(look, v):
    """The conservatory on 3 by 2 tiles: a great glasshouse of white iron with a tall domed middle and wings, palms showing
    through the glass, and gardens laid out in front."""
    b = Building(3, 2, height=3 * STOREY + 10)
    d = b.d
    rng = random.Random(16500)
    lawn_box(b, look, 0, 0, 95, 63, rng)
    path_line(b, look, [(48, 63), (48, 44)], 4)
    flowers(b, look, 10, 48, 40, 60, rng, 18)
    flowers(b, look, 56, 48, 86, 60, rng, 18)
    glass = c("#b8d8d0") if look != "snow" else c("#dfe8ee")
    frame = c("#f2f0ea")
    for (x0, x1, h) in ((6, 36, 2 * STOREY), (60, 90, 2 * STOREY), (34, 62, 3 * STOREY)):
        roof, wall = b.box(x0, 10, x1, 40, h)
        d.rectangle(wall, glass)
        for xx in range(wall[0] + 3, wall[2], 4):
            d.line([xx, wall[1], xx, wall[3]], frame)
        # Green showing through: palms and ferns.
        for _ in range(6):
            px = rng.randint(wall[0] + 2, wall[2] - 2)
            py = rng.randint(wall[1] + 2, wall[3] - 2)
            d.ellipse([px - 2, py - 2, px + 2, py + 2], c("#4f8a4a"))
        d.rectangle(wall, outline=OUTLINE)
        rx0, ry0, rx1, ry1 = roof
        d.rectangle(roof, glass, OUTLINE)
        for xx in range(rx0 + 3, rx1, 4):
            d.line([xx, ry0, xx, ry1], frame)
    # The dome over the middle.
    rx0, ry0, rx1, ry1 = roof
    cx = (rx0 + rx1) // 2
    cy = (ry0 + ry1) // 2
    d.ellipse([cx - 13, cy - 16, cx + 13, cy + 8], glass, OUTLINE)
    for k in range(-9, 10, 4):
        d.line([cx + k, cy - 14 + abs(k) // 2, cx + k, cy + 6], frame)
    d.line([cx, cy - 22, cx, cy - 16], frame, 2)
    b.casters.append((1, 36, 18, 60, 32, 3 * STOREY + 10))
    return b


def town_museum(look, v):
    """The town museum on 2 by 2 tiles: an old brick hall turned museum, a banner over its door, and in its yard the things
    the town kept: an old steam engine, a tram and a bell."""
    b = Building(2, 2, height=2 * STOREY + 12)
    d = b.d
    rng = random.Random(16600)
    lawn_box(b, look, 0, 0, 63, 63, rng)
    paving(b, look, 4, 42, 59, 62)
    roof, wall = b.box(6, 6, 57, 34, 2 * STOREY + 2)
    brick(d, wall, c("#8a4a38"))
    x0, y0, x1, y1 = wall
    cx = (x0 + x1) // 2
    windows(d, wall, 2, glass=c("#46586a"), sill=STONE, every=6, skip_door=True)
    d.rectangle([cx - 4, y1 - 7, cx + 4, y1], c("#3a2e26"))
    d.rectangle([cx - 12, y0 + 2, cx + 12, y0 + 6], c("#6b2330"))
    for xx in range(cx - 10, cx + 11, 2):
        d.point((xx, y0 + 4), c("#f2e6a0"))
    d.rectangle(wall, outline=OUTLINE)
    gable_ew(d, roof, SHINGLE[0], look)
    clock_face(d, cx, roof[1] + 4, 3)
    # The old steam engine, the tram and the bell.
    ex, ey = b.ground(10, 52)
    d.rectangle([ex, ey - 6, ex + 14, ey], c("#1f2024"), OUTLINE)
    d.rectangle([ex + 10, ey - 9, ex + 14, ey - 6], c("#1f2024"))
    d.rectangle([ex + 2, ey - 9, ex + 4, ey - 6], c("#3a3c42"))
    d.line([ex - 1, ey + 1, ex + 15, ey + 1], c("#5a5048"))
    tx, ty = b.ground(34, 52)
    d.rectangle([tx, ty - 6, tx + 14, ty], c("#e8dcc0"), OUTLINE)
    d.rectangle([tx, ty - 6, tx + 2, ty], c("#8e2a2a"))
    d.rectangle([tx + 12, ty - 6, tx + 14, ty], c("#8e2a2a"))
    bx, by = b.ground(54, 54)
    d.rectangle([bx - 1, by - 8, bx + 1, by], c("#5a3a2a"))
    d.ellipse([bx - 3, by - 11, bx + 3, by - 6], c("#b08a3a"), OUTLINE)
    return b


def headstones(b, look, x0, y0, x1, y1, rng, every=5):
    """Rows of headstones on the grass, some leaning."""
    d = b.d
    top = b.lift
    for yy in range(y0, y1, every):
        for xx in range(x0 + rng.randint(0, 2), x1, 4):
            if rng.random() < 0.15:
                continue
            col = rng.choice([c("#b8b4aa"), c("#a8a49a"), c("#d0ccc0"), c("#8a8a84")])
            d.rectangle([xx, yy + top - 2, xx + 1, yy + top], col)
            d.point((xx, yy + top + 1), c("#4a5a3a") if look != "snow" else c("#c8d0d6"))


def cemetery(look, v):
    """A cemetery on 3 by 3 tiles: rows of headstones in the grass between gravel paths and old trees, behind a wall, with a
    stone chapel; or a family vault and yews; or a plain one with a lych gate."""
    b = Building(3, 3, height=3 * STOREY)
    d = b.d
    rng = random.Random(15400 + v)
    top = b.lift
    lawn_box(b, look, 0, 0, 95, 95, rng)
    d.rectangle([0, top, 95, top + 95], outline=c("#8a8274"))
    d.rectangle([1, top + 1, 94, top + 94], outline=c("#a49c8c"))
    path_line(b, look, [(47, 95), (47, 6)], 3)
    path_line(b, look, [(6, 50), (89, 50)], 3)
    headstones(b, look, 6, 8, 43, 46, rng)
    headstones(b, look, 52, 8, 90, 46, rng)
    headstones(b, look, 6, 56, 43, 92, rng)
    if v == 0:
        roof, wall = b.box(58, 58, 86, 76, STOREY + 6)
        d.rectangle(wall, STONE)
        wx0, wy0, wx1, wy1 = wall
        d.rectangle([(wx0 + wx1) // 2 - 2, wy1 - 6, (wx0 + wx1) // 2 + 2, wy1], c("#4a3226"))
        d.rectangle(wall, outline=OUTLINE)
        gable_ew(d, roof, SHINGLE[2], look)
        rx0, ry0, rx1, ry1 = roof
        d.line([rx0 + 3, ry0 - 1, rx0 + 3, ry0 - 6], c("#5a5a5a"))
        d.line([rx0 + 1, ry0 - 4, rx0 + 5, ry0 - 4], c("#5a5a5a"))
        for (x, y) in ((24, 30), (70, 22), (20, 74)):
            tree_at(b, look, v, x, y, 6, rng)
    elif v == 1:
        roof, wall = b.box(60, 62, 80, 76, STOREY + 2)
        d.rectangle(wall, c("#c8c0ac"))
        for xx in range(wall[0] + 3, wall[2] - 1, 4):
            d.line([xx, wall[1] + 1, xx, wall[3]], c("#a8a090"))
        d.rectangle(wall, outline=OUTLINE)
        d.polygon([(roof[0], roof[3]), ((roof[0] + roof[2]) // 2, roof[1] - 3), (roof[2], roof[3])], SNOW_ROOF[0] if look == "snow" else c("#9a9488"), OUTLINE)
        for (x, y) in ((40, 24), (56, 24), (40, 70), (88, 88), (10, 52)):
            tree_at(b, look, v, x, y, 4, rng, conifer_tree=True)
    else:
        headstones(b, look, 52, 56, 90, 92, rng)
        roof, wall = b.box(42, 86, 52, 92, STOREY)
        d.rectangle(wall, c("#6b4a30"))
        d.rectangle([wall[0] + 3, wall[1] + 2, wall[2] - 3, wall[3]], c("#2a2a2a"))
        gable_ew(d, roof, SHINGLE[1], look)
        for (x, y) in ((14, 28), (82, 30), (30, 80)):
            tree_at(b, look, v, x, y, 6, rng)
    return b


def memorial_garden(look, v):
    """A memorial garden of the 1970s on 3 by 3 tiles: lawns along winding paths, flower beds, benches and a pond, with the
    old stones gathered along the wall."""
    b = Building(3, 3, height=LIFT)
    d = b.d
    rng = random.Random(15500 + v)
    top = b.lift
    lawn_box(b, look, 0, 0, 95, 95, rng)
    d.rectangle([0, top, 95, top + 95], outline=c("#a49c8c"))
    if v == 0:
        path_line(b, look, [(47, 95), (40, 70), (56, 48), (44, 24), (52, 4)], 3)
        pond(b, look, 70, 66, 12, 8, rng)
        flowers(b, look, 12, 40, 32, 46, rng, 16)
        flowers(b, look, 60, 20, 84, 26, rng, 16)
        trees = ((18, 18), (80, 44), (22, 76), (72, 88))
    else:
        path_line(b, look, [(0, 47), (30, 40), (66, 54), (95, 47)], 3)
        path_line(b, look, [(47, 95), (47, 50)], 3)
        d.ellipse([36, top + 38, 58, top + 60], PATH if look != "snow" else c("#e6ecf0"))
        d.rectangle([45, top + 44, 49, top + 52], c("#b8b4aa"), OUTLINE)
        flowers(b, look, 14, 64, 34, 70, rng, 16)
        flowers(b, look, 60, 64, 82, 70, rng, 16)
        pond(b, look, 22, 20, 10, 7, rng)
        trees = ((74, 18), (86, 82), (10, 86), (58, 26))
    # The old stones along the north wall.
    for xx in range(4, 92, 4):
        d.rectangle([xx, top + 2, xx + 1, top + 4], c("#b8b4aa"))
    for (x, y) in ((30, 60), (64, 36)):
        bench(b, x, y)
    for (x, y) in trees:
        tree_at(b, look, v, x, y, 6, rng)
    return b


def fountain(look, v):
    """A fountain on one tile: a round stone basin on a little paved square with water leaping from the middle; a square pool
    with jets; or a tiered one, its water falling from bowl to bowl."""
    b = Building(height=LIFT + 6)
    d = b.d
    rng = random.Random(15600 + v)
    top = b.lift
    paving(b, look, 0, 0, 31, 31)
    water = c("#5a8fbf") if look != "snow" else c("#dfe8ef")
    if v == 1:
        d.rectangle([5, top + 7, 26, top + 26], c("#a8a090"), OUTLINE)
        d.rectangle([7, top + 9, 24, top + 24], water)
        if look != "snow":
            for (x, y) in ((11, 13), (20, 13), (11, 20), (20, 20)):
                d.line([x, top + y, x, top + y - 4], c("#d8ecf8"))
    else:
        d.ellipse([4, top + 6, 27, top + 27], c("#a8a090"), OUTLINE)
        d.ellipse([6, top + 8, 25, top + 25], water)
        d.rectangle([14, top + 10, 17, top + 18], c("#c8c0ac"), OUTLINE)
        if v == 2:
            d.ellipse([10, top + 8, 21, top + 13], c("#c8c0ac"), OUTLINE)
            d.ellipse([12, top + 3, 19, top + 7], c("#c8c0ac"), OUTLINE)
        if look != "snow":
            d.line([15, top + 10, 15, top + 1], c("#d8ecf8"))
            d.line([16, top + 10, 16, top + 2], c("#bfe0f4"))
    for (x, y) in ((2, 29), (24, 29)):
        bench(b, x, y)
    b.casters.append((1, 13, 14, 18, 18, 8))
    return b


def clock_tower(look, v):
    """A clock tower on one tile: tall and square, of stone or brick, its faces near the top under a pointed roof, on a little
    square."""
    b = Building(height=6 * STOREY + 10)
    d = b.d
    rng = random.Random(15700 + v)
    paving(b, look, 0, 0, 31, 31)
    roof, wall = b.box(10, 10, 21, 21, 6 * STOREY)
    col = [c("#d8cfb8"), c("#9a4a36")][v % 2]
    if v == 0:
        d.rectangle(wall, col)
    else:
        brick(d, wall, col)
    x0, y0, x1, y1 = wall
    cx = (x0 + x1) // 2
    clock_face(d, cx, y0 + 5, 4)
    for yy in range(y0 + 12, y1 - 4, 6):
        d.rectangle([cx - 1, yy, cx + 1, yy + 3], c("#46586a"))
    d.rectangle([cx - 2, y1 - 5, cx + 2, y1], c("#3a2e26"))
    d.rectangle(wall, outline=OUTLINE)
    rx0, ry0, rx1, ry1 = roof
    d.polygon([(rx0, ry1), ((rx0 + rx1) // 2, ry0 - 9), (rx1, ry1)], SNOW_ROOF[0] if look == "snow" else [c("#5b6a6a"), SHINGLE[2]][v % 2], OUTLINE)
    for (x, y) in ((3, 28), (24, 6)):
        bench(b, x, y)
    if v == 1:
        tree_at(b, look, v, 5, 8, 4, rng)
    return b


def war_memorial(look, v):
    """A war memorial on one tile: a stone cenotaph on its steps, wreaths of poppies at its foot, in a ring of lawn; or a
    tall cross with the names on a low wall behind it."""
    b = Building(height=3 * STOREY + 6)
    d = b.d
    rng = random.Random(15800 + v)
    top = b.lift
    lawn_box(b, look, 0, 0, 31, 31, rng)
    path_line(b, look, [(15, 31), (15, 24)], 4)
    d.rectangle([8, top + 14, 23, top + 24], c("#c8c0ac"), OUTLINE)
    d.rectangle([10, top + 16, 21, top + 22], c("#d8d0bc"))
    if v == 0:
        roof, wall = b.box(12, 15, 19, 20, 3 * STOREY)
        d.rectangle(wall, c("#e0d8c4"))
        d.rectangle(wall, outline=OUTLINE)
        d.rectangle(roof, c("#ece4d0"), OUTLINE)
    else:
        d.rectangle([4, top + 6, 27, top + 9], c("#b8b0a0"), OUTLINE)
        for xx in range(6, 26, 2):
            d.point((xx, top + 7), c("#6a6a66"))
        roof, wall = b.box(14, 16, 17, 19, 3 * STOREY + 2)
        d.rectangle(wall, c("#e0d8c4"))
        d.rectangle(wall, outline=OUTLINE)
        wx = (wall[0] + wall[2]) // 2
        d.rectangle([wx - 4, wall[1] + 3, wx + 4, wall[1] + 5], c("#e0d8c4"), OUTLINE)
    if look != "snow":
        for x in (10, 20):
            d.ellipse([x, top + 23, x + 3, top + 26], c("#c0392b"))
            d.point((x + 1, top + 24), c("#2a2a2a"))
    return b


# More schooling: a kindergarten, a junior high, a vocational school, a central library, a community college, a university
# and a research campus.


def kindergarten(look, v):
    """A kindergarten on one tile: a low cottage with bright trim and a fenced sandpit and swing in the yard; or a single
    storey of the 1960s with big windows and a coloured door."""
    b = Building(height=STOREY + 12)
    d = b.d
    rng = random.Random(15900 + v)
    schoolyard(b, look, 2, 20, 29, 31)
    roof, wall = b.box(4, 4, 27, 18, STOREY + 4)
    trim = [c("#e0584a"), c("#3a8ad0"), c("#f2c94c")][v % 3]
    if v == 0:
        siding(d, wall, c("#f2e6c8"))
        windows(d, wall, 1, glass=c("#46586a"), sill=trim, every=5, width=2, height=3, skip_door=True)
        door(d, wall, trim)
        d.rectangle(wall, outline=OUTLINE)
        gable_ew(d, roof, SHINGLE[0], look)
    else:
        d.rectangle(wall, PALE_BRICK)
        x0, y0, x1, y1 = wall
        d.rectangle([x0 + 2, y0 + 2, x1 - 8, y1 - 2], GLASS_NEW)
        d.rectangle([x1 - 6, y0 + 2, x1 - 3, y1], trim)
        d.rectangle(wall, outline=OUTLINE)
        flat_roof(b.img, roof, look, rng, [("vent", 4, 3)], parapet=trim)
    if look != "snow":
        gx, gy = b.ground(20, 23)
        d.rectangle([gx, gy, gx + 6, gy + 4], c("#e8d8a0"), c("#8a6a4a"))
    return b


def junior_high(look, v):
    """A junior high of the 1930s on 2 by 2 tiles: two storeys of brick in an L round a yard, tall windows and a stone door
    surround; or a long pale block of the 1950s with a gym at one end."""
    b = Building(2, 2, height=2 * STOREY + 8)
    d = b.d
    rng = random.Random(16000 + v)
    schoolyard(b, look, 30, 34, 61, 61)
    lawn_box(b, look, 2, 50, 28, 61, rng)
    if v == 0:
        roof, wall = b.box(4, 6, 59, 30, 2 * STOREY + 4)
        brick(d, wall, c("#a0503a"))
        windows(d, wall, 2, glass=c("#46586a"), sill=TRIM, every=5, width=3, height=4, skip_door=True)
        x0, y0, x1, y1 = wall
        cx = (x0 + x1) // 2
        d.rectangle([cx - 4, y0, cx + 4, y1], STONE)
        d.rectangle([cx - 2, y1 - 6, cx + 2, y1], c("#4a3226"))
        d.rectangle(wall, outline=OUTLINE)
        flat_roof(b.img, roof, look, rng, [("stack", 6, 4), ("hatch", 30, 10)], parapet=STONE)
        roof, wall = b.box(4, 30, 26, 48, 2 * STOREY + 4)
        brick(d, wall, c("#a0503a"))
        windows(d, wall, 2, glass=c("#46586a"), sill=TRIM, every=5, width=3, height=4)
        d.rectangle(wall, outline=OUTLINE)
        flat_roof(b.img, roof, look, rng, [("vent", 4, 4)], parapet=STONE)
    else:
        roof, wall = b.box(4, 8, 44, 30, 2 * STOREY + 2)
        d.rectangle(wall, PALE_BRICK)
        ribbon_windows(d, wall, 2, glass=GLASS_DARK, gap=4)
        door(d, wall, c("#3a4f6a"))
        d.rectangle(wall, outline=OUTLINE)
        flat_roof(b.img, roof, look, rng, [("vent", 6, 4), ("vent", 30, 6)], parapet=c("#8a8a84"))
        hall(b, look, 46, 6, 60, 30, 2 * STOREY + 6, c("#d8d0c0"), "barrel", storeys=1, entrance=False)
        roof, wall = b.box(4, 32, 22, 46, STOREY + 4)
        d.rectangle(wall, PALE_BRICK)
        ribbon_windows(d, wall, 1, glass=GLASS_DARK)
        d.rectangle(wall, outline=OUTLINE)
        flat_roof(b.img, roof, look, rng, [], parapet=c("#8a8a84"))
    return b


def vocational_school(look, v):
    """A vocational school on 2 by 2 tiles: a classroom block in front, and behind it the workshops under a saw-tooth roof
    with a yard of timber and an old engine to learn on."""
    b = Building(2, 2, height=2 * STOREY + 8)
    d = b.d
    rng = random.Random(16100 + v)
    yard_col = c("#9a8e78") if look != "snow" else SNOW_GROUND
    gx0, gy0 = b.ground(2, 2)
    gx1, gy1 = b.ground(61, 61)
    d.rectangle([gx0, gy0, gx1, gy1], yard_col)
    # The workshops: a saw-tooth roof, its glass facing north.
    roof, wall = b.box(4, 4, 59, 26, STOREY + 6)
    d.rectangle(wall, c("#8a4a3a") if v == 0 else c("#9a9a92"))
    d.rectangle([wall[0] + 4, wall[1] + 2, wall[0] + 14, wall[3]], c("#4a4a50"))
    d.rectangle([wall[2] - 14, wall[1] + 2, wall[2] - 4, wall[3]], c("#4a4a50"))
    d.rectangle(wall, outline=OUTLINE)
    rx0, ry0, rx1, ry1 = roof
    tooth = 6
    for yy in range(ry0, ry1, tooth):
        d.rectangle([rx0, yy, rx1, min(ry1, yy + tooth - 2)], SNOW_ROOF[0] if look == "snow" else c("#6a6c70"))
        d.rectangle([rx0, min(ry1, yy + tooth - 2), rx1, min(ry1, yy + tooth - 1)], c("#9ab8c8"))
    d.rectangle(roof, outline=OUTLINE)
    # The classrooms in front.
    roof, wall = b.box(6, 34, 46, 52, 2 * STOREY + 2)
    if v == 0:
        brick(d, wall, c("#a0503a"))
        windows(d, wall, 2, glass=c("#46586a"), sill=TRIM, every=5, width=3, height=4, skip_door=True)
    else:
        d.rectangle(wall, PALE_BRICK)
        ribbon_windows(d, wall, 2, glass=GLASS_DARK, gap=4)
    door(d, wall, c("#3a2e26"))
    d.rectangle(wall, outline=OUTLINE)
    flat_roof(b.img, roof, look, rng, [("vent", 6, 4)], parapet=STONE if v == 0 else c("#8a8a84"))
    # Timber stacked in the yard, and an engine.
    gx, gy = b.ground(50, 36)
    for k in range(3):
        d.rectangle([gx, gy + k * 3, gx + 9, gy + k * 3 + 1], TIMBER)
    gx, gy = b.ground(50, 50)
    d.rectangle([gx, gy, gx + 7, gy + 4], c("#c0392b") if v == 0 else c("#2e4a3a"))
    d.rectangle([gx + 5, gy, gx + 7, gy + 2], GLASS_DARK)
    return b


def central_library(look, v):
    """A central library on 2 by 2 tiles: a grand stone hall with columns, wide steps and a reading room under a dome; or a
    glass and stone library of the 1990s round a tall atrium."""
    b = Building(2, 2, height=3 * STOREY + 20)
    d = b.d
    rng = random.Random(16200 + v)
    paving(b, look, 0, 44, 63, 63)
    for x in (8, 55):
        tree_at(b, look, v, x, 58, 4, rng)
    if v == 0:
        roof, wall = b.box(4, 6, 59, 42, 3 * STOREY + 2)
        d.rectangle(wall, c("#ddd5c2"))
        x0, y0, x1, y1 = wall
        cx = (x0 + x1) // 2
        windows(d, wall, 3, glass=c("#46586a"), sill=TRIM, every=6, skip=[(cx - 14, cx + 14)])
        for xx in range(cx - 12, cx + 13, 4):
            d.line([xx, y0 + 4, xx, y1], c("#c8c0ac"))
        d.polygon([(cx - 15, y0 + 4), (cx, y0 - 4), (cx + 15, y0 + 4)], c("#e8e0cc"), OUTLINE)
        d.rectangle([cx - 3, y1 - 6, cx + 3, y1], c("#3a2e26"))
        d.rectangle([cx - 16, y1 + 1, cx + 16, y1 + 4], STONE)
        d.rectangle(wall, outline=OUTLINE)
        cornice(d, wall, STONE)
        flat_roof(b.img, roof, look, rng, [("skylight", 6, 6), ("skylight", 44, 6)], parapet=STONE)
        mx = (roof[0] + roof[2]) // 2
        my = (roof[1] + roof[3]) // 2
        d.pieslice([mx - 10, my - 12, mx + 10, my + 6], 180, 360, SNOW_ROOF[0] if look == "snow" else c("#6e8f86"), OUTLINE)
        d.ellipse([mx - 2, my - 13, mx + 2, my - 10], c("#d8d0bc"), OUTLINE)
    else:
        roof, wall = b.box(4, 6, 40, 42, 3 * STOREY + 2)
        d.rectangle(wall, c("#c8c0ac"))
        windows(d, wall, 3, glass=GLASS_DARK, every=5, width=3, height=4, skip_door=True)
        door(d, wall, c("#3a4f6a"))
        d.rectangle(wall, outline=OUTLINE)
        flat_roof(b.img, roof, look, rng, [("vent", 6, 4)], parapet=c("#8a8a84"))
        roof, wall = b.box(40, 12, 59, 42, 3 * STOREY + 8)
        d.rectangle(wall, GLASS_NEW)
        for xx in range(wall[0] + 3, wall[2], 4):
            d.line([xx, wall[1], xx, wall[3]], c("#d8d8d0"))
        d.rectangle(wall, outline=OUTLINE)
        rx0, ry0, rx1, ry1 = roof
        d.rectangle(roof, SNOW_ROOF[0] if look == "snow" else c("#a8c4d4"), OUTLINE)
        for yy in range(ry0 + 3, ry1, 4):
            d.line([rx0 + 1, yy, rx1 - 1, yy], c("#d8d8d0"))
    return b


def community_college(look, v):
    """A community college of the 1960s and 70s on 2 by 2 tiles: low blocks of concrete and glass round a courtyard, and a
    car park for those who drive in."""
    b = Building(2, 2, height=2 * STOREY + 8)
    d = b.d
    rng = random.Random(16300 + v)
    parking(b, look, 2, 50, 61, 61, 16300 + v)
    col = [c("#b4b0a6"), c("#c8a888")][v % 2]
    for box in ((4, 4, 59, 18), (4, 20, 18, 46), (46, 20, 59, 46)):
        roof, wall = b.box(*box, 2 * STOREY + 2)
        d.rectangle(wall, col)
        ribbon_windows(d, wall, 2, glass=GLASS_DARK, gap=5)
        d.rectangle(wall, outline=OUTLINE)
        flat_roof(b.img, roof, look, rng, [("vent", 4, 3)], parapet=c("#8a8a84"))
    lawn_box(b, look, 20, 20, 44, 46, rng)
    path_line(b, look, [(32, 46), (32, 20)])
    tree_at(b, look, v, 26, 34, 4, rng)
    if v == 1:
        tree_at(b, look, v, 40, 30, 4, rng)
    return b


def university(look, v):
    """A university on 4 by 4 tiles: old halls of stone round a quadrangle with a library dome and a bell tower, and a
    modern science block beside them; or brick halls, a chapel and playing fields."""
    b = Building(4, 4, height=6 * STOREY + 12)
    d = b.d
    rng = random.Random(16400 + v)
    lawn_box(b, look, 0, 0, 127, 127, rng)
    wall_col = [c("#c9b893"), c("#8e4a3a")][v % 2]
    roof_col = SHINGLE[2] if v == 0 else SHINGLE[0]

    def range_(x0, y0, x1, y1, storeys, gable=True):
        roof, wall = b.box(x0, y0, x1, y1, storeys * STOREY + 2)
        if v == 0:
            d.rectangle(wall, wall_col)
            for yy in range(wall[1] + 3, wall[3], 4):
                d.line([wall[0] + 1, yy, wall[2] - 1, yy], shade(wall_col, 0.92))
        else:
            brick(d, wall, wall_col)
        windows(d, wall, storeys, glass=c("#46586a"), sill=TRIM, every=5, width=2, height=4)
        d.rectangle(wall, outline=OUTLINE)
        if gable:
            gable_ew(d, roof, roof_col, look)
        else:
            flat_roof(b.img, roof, look, rng, [("stack", 4, 4)], parapet=STONE)
        return roof, wall

    # The quad: halls on the north, west and east, open to the south.
    range_(8, 6, 82, 26, 3)
    range_(8, 28, 24, 74, 3, gable=False)
    range_(66, 28, 82, 74, 3, gable=False)
    qx0, qy0 = b.ground(28, 30)
    qx1, qy1 = b.ground(62, 74)
    if look != "snow":
        d.rectangle([qx0, qy0, qx1, qy1], c("#8db866"))
        d.line([qx0, qy0, qx1, qy1], PATH)
        d.line([qx1, qy0, qx0, qy1], PATH)
    # The bell tower over the north hall.
    troof, twall = b.box(40, 12, 50, 22, 6 * STOREY + 4)
    d.rectangle(twall, wall_col)
    clock_face(d, (twall[0] + twall[2]) // 2, twall[1] + 5)
    d.rectangle(twall, outline=OUTLINE)
    rx0, ry0, rx1, ry1 = troof
    d.polygon([(rx0, ry1), ((rx0 + rx1) // 2, ry0 - 8), (rx1, ry1)], SNOW_ROOF[0] if look == "snow" else c("#5a6a70"), OUTLINE)
    if v == 0:
        # The library under its dome, and a science block of the sixties.
        roof, wall = b.box(88, 10, 120, 40, 3 * STOREY)
        d.rectangle(wall, wall_col)
        windows(d, wall, 3, glass=c("#46586a"), sill=TRIM, every=6)
        d.rectangle(wall, outline=OUTLINE)
        flat_roof(b.img, roof, look, rng, [], parapet=STONE)
        mx, my = (roof[0] + roof[2]) // 2, (roof[1] + roof[3]) // 2
        d.pieslice([mx - 11, my - 12, mx + 11, my + 8], 180, 360, SNOW_ROOF[0] if look == "snow" else c("#6e8f86"), OUTLINE)
        roof, wall = b.box(88, 50, 122, 100, 5 * STOREY)
        d.rectangle(wall, c("#b4b0a6"))
        ribbon_windows(d, wall, 5, glass=GLASS_DARK, gap=4)
        d.rectangle(wall, outline=OUTLINE)
        flat_roof(b.img, roof, look, rng, [("vent", 6, 6), ("hatch", 20, 20)], parapet=c("#8a8a84"))
        parking(b, look, 8, 110, 80, 122, 16400)
    else:
        # A chapel, and playing fields.
        roof, wall = b.box(92, 12, 108, 44, 2 * STOREY + 6)
        brick(d, wall, wall_col)
        for yy in range(wall[1] + 2, wall[3] - 2, 4):
            d.rectangle([wall[0] + 3, yy, wall[0] + 4, yy + 2], c("#5a4a8a"))
        d.rectangle(wall, outline=OUTLINE)
        gable_ew(d, roof, SHINGLE[2], look)
        pitch(b, look, 88, 56, 124, 120)
        pitch(b, look, 8, 86, 76, 122)
    for (x, y) in ((32, 90), (60, 86), (4, 100)) if v == 0 else ((84, 50), (4, 80), (84, 124)):
        tree_at(b, look, v, x, y, 5, rng)
    return b


def research_campus(look, v):
    """A research campus of the 2000s on 3 by 3 tiles: glass buildings with green roofs set in lawns round a pond, linked by
    paths, and a car park under solar panels; or a curving block round a court."""
    b = Building(3, 3, height=4 * STOREY + 6)
    d = b.d
    rng = random.Random(16500 + v)
    lawn_box(b, look, 0, 0, 95, 95, rng)
    parking(b, look, 2, 80, 93, 93, 16500 + v)
    gx0, gy0 = b.ground(2, 80)
    solar_rows(d, gx0 + 2, gy0 + 6, gx0 + 88, gy0 + 12, look)
    if v == 0:
        boxes = ((6, 6, 40, 30, 4), (54, 6, 90, 26, 3), (6, 44, 34, 72, 3))
        pond(b, look, 68, 54, 14, 10, rng)
    else:
        boxes = ((6, 6, 90, 22, 4), (6, 24, 22, 72, 3), (74, 24, 90, 72, 3))
        pond(b, look, 48, 50, 14, 10, rng)
    for (x0, y0, x1, y1, storeys) in boxes:
        roof, wall = b.box(x0, y0, x1, y1, storeys * STOREY + 2)
        d.rectangle(wall, GLASS_NEW)
        for xx in range(wall[0] + 3, wall[2], 4):
            d.line([xx, wall[1], xx, wall[3]], c("#d8d8d0"))
        d.rectangle(wall, outline=OUTLINE)
        rx0, ry0, rx1, ry1 = roof
        d.rectangle(roof, c("#d8d8d0"), OUTLINE)
        green_patch(d, rx0 + 2, ry0 + 2, rx1 - 2, ry1 - 2, look)
    path_line(b, look, [(47, 80), (47, 36), (20, 36)], 3)
    for (x, y) in ((44, 66), (88, 40), (30, 40)):
        tree_at(b, look, v, x, y, 4, rng)
    return b


# More health: a sanatorium and a public health office.


def sanatorium(look, v):
    """A sanatorium on 3 by 2 tiles: a long building facing the sun with open balconies along each storey for the patients'
    beds, set among lawns and pines."""
    b = Building(3, 2, height=3 * STOREY + 8)
    d = b.d
    rng = random.Random(16600 + v)
    lawn_box(b, look, 0, 0, 95, 63, rng)
    roof, wall = b.box(6, 8, 89, 30, 3 * STOREY + 2)
    col = [c("#ece6d6"), c("#d8c8a8")][v % 2]
    d.rectangle(wall, col)
    x0, y0, x1, y1 = wall
    per = (y1 - y0 + 1) // 3
    for k in range(3):
        yy = y0 + k * per
        d.rectangle([x0 + 2, yy + 1, x1 - 2, yy + per - 2], c("#6a6a66"))
        for xx in range(x0 + 4, x1 - 2, 4):
            d.line([xx, yy + 1, xx, yy + per - 2], col)
        d.line([x0 + 2, yy + per - 2, x1 - 2, yy + per - 2], TRIM)
    d.rectangle(wall, outline=OUTLINE)
    if v == 0:
        gable_ew(d, roof, SHINGLE[0], look)
    else:
        flat_roof(b.img, roof, look, rng, [("stack", 10, 4), ("stack", 70, 4)], parapet=STONE)
    path_line(b, look, [(48, 63), (48, 34)], 3)
    for x in range(10, 90, 18):
        bench(b, x, 40)
    for (x, y) in ((8, 54), (24, 56), (72, 54), (88, 56), (4, 6)):
        tree_at(b, look, v, x, y, 4, rng, conifer_tree=True)
    return b


def public_health_office(look, v):
    """A public health office on 2 by 1 tiles: a plain block of brick or pale stone with its name over the door, a white van
    for the inspectors and a sign with a cross."""
    b = Building(2, 1, height=2 * STOREY + 6)
    d = b.d
    rng = random.Random(16700 + v)
    paving(b, look, 0, 26, 63, 31)
    roof, wall = b.box(3, 5, 60, 25, 2 * STOREY + 4)
    if v == 0:
        brick(d, wall, c("#a8885a"))
        windows(d, wall, 2, sill=TRIM, every=5, skip_door=True)
    else:
        d.rectangle(wall, c("#e0dccf"))
        ribbon_windows(d, wall, 2, glass=GLASS_DARK, gap=4)
    x0, y0, x1, y1 = wall
    cx = (x0 + x1) // 2
    d.rectangle([cx - 3, y1 - 6, cx + 3, y1], c("#3a4f6a"))
    d.rectangle([x1 - 8, y0 + 1, x1 - 3, y0 + 6], c("#ffffff"))
    d.line([x1 - 6, y0 + 1, x1 - 6, y0 + 6], c("#2a7a5a"))
    d.line([x1 - 8, y0 + 3, x1 - 3, y0 + 3], c("#2a7a5a"))
    d.rectangle(wall, outline=OUTLINE)
    flat_roof(b.img, roof, look, rng, [("vent", 6, 4), ("hatch", 30, 8)], parapet=STONE if v == 0 else c("#8a8a84"))
    gx, gy = b.ground(8, 27)
    d.rectangle([gx, gy, gx + 7, gy + 3], c("#f2f2ea"), OUTLINE)
    d.point((gx + 3, gy + 1), c("#2a7a5a"))
    return b


# More police and fire: a call post, traffic police and a fireboat station.


def police_box(look, v):
    """A call post on a street corner, on one tile: a cast-iron post with the call box on it, its phone for the constable on
    the beat, and a lamp on top that lights to call him in; a constable at it on one of them."""
    b = Building(height=3 * STOREY)
    d = b.d
    paving(b, look, 0, 0, 31, 31, c("#b8b0a0"))
    iron = [c("#2f4a3a"), c("#2a2c30")][v % 2]
    hi = shade(iron, 1.5)
    # The post, then the box on it, with a white band and a gilt crest.
    px, py = b.ground(15, 18)
    d.rectangle([px - 2, py - 1, px + 3, py + 1], shade(iron, 0.8), OUTLINE)
    d.rectangle([px, py - 14, px + 1, py - 1], iron, OUTLINE)
    d.rectangle([px - 3, py - 17, px + 4, py - 9], iron, OUTLINE)
    d.line([px - 2, py - 16, px - 2, py - 10], hi)
    d.line([px - 2, py - 15, px + 3, py - 15], c("#e8ecf0"))
    d.point((px + 1, py - 12), c("#c8a040"))
    d.rectangle([px - 1, py - 19, px + 2, py - 18], iron, OUTLINE)
    lamp = [c("#e05040"), c("#5a8ae0")][v % 2]
    d.ellipse([px - 1, py - 23, px + 2, py - 20], lamp, OUTLINE)
    d.point((px, py - 22), c("#f2f6ff"))
    b.casters.append((1, 13, 16, 19, 20, 22))
    if v % 2 == 0 and look != "snow":
        # The constable, in a dark tunic and cap, at the phone.
        cx, cy = b.ground(20, 21)
        d.rectangle([cx, cy - 7, cx + 3, cy], c("#1e2a4a"), OUTLINE)
        d.line([cx + 1, cy - 6, cx + 1, cy - 2], c("#c8a040"))
        d.rectangle([cx + 1, cy - 9, cx + 2, cy - 8], c("#e0b090"))
        d.rectangle([cx, cy - 11, cx + 3, cy - 10], c("#141c30"))
    else:
        gx, gy = b.ground(6, 26)
        d.rectangle([gx, gy - 2, gx + 7, gy], c("#7a5a3a"), OUTLINE)
    return b




def traffic_police(look, v):
    """Traffic police on 2 by 1 tiles: a station with garage doors for the patrol cars and motorcycles lined up out front, and
    a striped barrier; or one of the 1960s with a glass front and a radio mast."""
    b = Building(2, 1, height=2 * STOREY + 10)
    d = b.d
    rng = random.Random(16800 + v)
    paving(b, look, 0, 24, 63, 31, c("#8e8a80"))
    roof, wall = b.box(3, 4, 60, 22, 2 * STOREY + 2)
    if v == 0:
        brick(d, wall, c("#8a4a3a"))
        windows(d, wall, 2, sill=TRIM, every=5, skip=[(wall[0] + 30, wall[2])])
    else:
        d.rectangle(wall, c("#d8d0c0"))
        ribbon_windows(d, [wall[0], wall[1], wall[0] + 28, wall[3]], 2, glass=GLASS_DARK, gap=4)
    x0, y0, x1, y1 = wall
    for k in range(2):
        gx = x0 + 32 + k * 12
        d.rectangle([gx, y1 - 8, gx + 9, y1], c("#5a5a60"))
        for yy in range(y1 - 7, y1, 2):
            d.line([gx + 1, yy, gx + 8, yy], c("#6a6a70"))
    d.rectangle([x0 + 2, y0 + 1, x0 + 26, y0 + 2], c("#2a4a8a"))
    d.rectangle(wall, outline=OUTLINE)
    flat_roof(b.img, roof, look, rng, [("hatch", 10, 6)], parapet=STONE)
    if v == 1:
        rx0, ry0, rx1, ry1 = roof
        d.line([rx1 - 6, ry0 + 3, rx1 - 6, ry0 - 10], c("#c0c0c0"))
        d.line([rx1 - 9, ry0 - 7, rx1 - 3, ry0 - 7], c("#c0c0c0"))
    # Motorcycles and a car.
    gx, gy = b.ground(6, 26)
    for k in range(4):
        d.rectangle([gx + k * 4, gy, gx + k * 4 + 1, gy + 3], c("#e8e8e8"))
        d.point((gx + k * 4, gy + 1), c("#2a4a8a"))
    gx, gy = b.ground(30, 26)
    d.rectangle([gx, gy, gx + 7, gy + 3], c("#f2f2ea"), OUTLINE)
    d.line([gx + 1, gy + 1, gx + 6, gy + 1], c("#2a4a8a"))
    d.line([2, b.lift + 30, 20, b.lift + 30], c("#f2f2ea"))
    for xx in range(3, 20, 4):
        d.line([xx, b.lift + 30, xx + 1, b.lift + 30], c("#c0392b"))
    return b


def fireboat_station(look, v):
    """A fireboat station on 2 by 2 tiles: a boathouse of brick or timber with its doors open to a slipway, a hose tower,
    and the red fireboat moored at the pier."""
    b = Building(2, 2, height=4 * STOREY + 6)
    d = b.d
    rng = random.Random(16900 + v)
    top = b.lift
    # The pier and the water it reaches out over.
    d.rectangle([0, top + 40, 63, top + 63], c("#5a8fbf") if look != "snow" else c("#dfe8ef"))
    d.rectangle([4, top + 40, 22, top + 62], TIMBER)
    for yy in range(top + 42, top + 62, 3):
        d.line([4, yy, 22, yy], shade(TIMBER, 0.85))
    # The fireboat beside it.
    d.polygon([(28, top + 46), (52, top + 46), (56, top + 51), (52, top + 56), (28, top + 56)], c("#c0392b"), OUTLINE)
    d.rectangle([34, top + 48, 44, top + 54], c("#f2f2ea"), OUTLINE)
    d.line([46, top + 51, 52, top + 48], c("#d9b44a"))
    # The boathouse.
    paving(b, look, 0, 0, 63, 39, c("#8e8a80"))
    roof, wall = b.box(4, 6, 46, 36, 2 * STOREY + 4)
    if v == 0:
        brick(d, wall, c("#9a4a36"))
    else:
        siding(d, wall, c("#d8c8a8"))
    x0, y0, x1, y1 = wall
    d.rectangle([x0 + 4, y1 - 9, x0 + 18, y1], c("#6a1a1a"))
    windows(d, wall, 2, sill=TRIM, every=6, skip=[(x0 + 2, x0 + 20)])
    d.rectangle(wall, outline=OUTLINE)
    gable_ew(d, roof, SHINGLE[2] if v == 0 else SHINGLE[1], look)
    troof, twall = b.box(50, 8, 58, 16, 4 * STOREY + 4)
    brick(d, twall, c("#9a4a36"))
    d.rectangle(twall, outline=OUTLINE)
    d.rectangle(troof, SNOW_ROOF[0] if look == "snow" else c("#5b5f6b"), OUTLINE)
    return b


def ferry_terminal(look, v):
    """A ferry terminal on 2 by 2 tiles: a waiting hall with a clock over its door, a long canopy over the queue along
    the front and a gangway frame at the back, on a paved lot. The ferry ties up on the water beside it."""
    b = Building(2, 2, height=2 * STOREY + 10)
    d = b.d
    paving(b, look, 0, 0, 63, 63, c("#9a958a"))
    # Lines painted for the cars waiting to board.
    for xx in range(10, 58, 8):
        d.line([xx, 50, xx, 60], c("#e8e2d0"))
    roof, wall = b.box(6, 4, 58, 30, STOREY + 6)
    if v == 0:
        brick(d, wall, c("#b5653e"))
    else:
        siding(d, wall, c("#e0d6bc"))
    x0, y0, x1, y1 = wall
    # Doors in the middle, a clock over them.
    mid = (x0 + x1) // 2
    d.rectangle([mid - 5, y1 - 9, mid + 5, y1], c("#3a2a1e"), OUTLINE)
    d.ellipse([mid - 4, y0 + 1, mid + 4, y0 + 9], c("#f4f0e2"), OUTLINE)
    d.line([mid, y0 + 5, mid, y0 + 2], OUTLINE)
    d.line([mid, y0 + 5, mid + 2, y0 + 5], OUTLINE)
    windows(d, wall, 1, sill=TRIM, every=6, skip=[(mid - 7, mid + 7)])
    d.rectangle(wall, outline=OUTLINE)
    gable_ew(d, roof, c("#3f6f7a") if v == 0 else SHINGLE[1], look)
    # A canopy along the front over the queue, on posts.
    d.rectangle([8, y1 + 2, 56, y1 + 7], c("#2f5f6f"), OUTLINE)
    for xx in (10, 32, 54):
        d.line([xx, y1 + 8, xx, y1 + 12], c("#3a3a3a"))
    # Bollards and a lamp at the corners of the lot.
    for xx, yy in ((2, 62), (61, 62), (2, 40), (61, 40)):
        d.rectangle([xx - 1, yy - 2, xx + 1, yy], c("#3a3a3a"))
    return b


BUILDINGS = [
    ("cottage", cottage, 4), ("house", house, 4), ("large_house", large_house, 3), ("tenement", tenement, 3),
    ("general_store", general_store, 6), ("shop", shop, 6), ("hotel", hotel, 4), ("bank", bank, 4),
    ("workshop", workshop, 6), ("mill", mill, 4), ("warehouse", warehouse, 5), ("factory", factory, 4),
    ("coal_plant", coal_plant, 1),
    ("police_station", police_station, 2), ("fire_station", fire_station, 2), ("park", park, 4),
    ("station_ew", station_ew, 4), ("station_ns", station_ns, 4), ("yard_ew", yard_ew, 4), ("yard_ns", yard_ns, 4),
    ("wharf_ew", wharf_ew, 4), ("wharf_ns", wharf_ns, 4), ("docks_ew", docks_ew, 4), ("docks_ns", docks_ns, 4),
    ("boxport_ew", boxport_ew, 4), ("boxport_ns", boxport_ns, 4),
    ("terminal_ew", terminal_ew, 4), ("terminal_ns", terminal_ns, 4),
    ("airfield", airfield, 4), ("airport_mid", airport_mid, 2), ("airport_big", airport_big, 2),
    ("pumping_station", pumping_station, 2), ("well_field", well_field, 3), ("tower", water_tower, 3),
    ("sewer_outfall", sewer_outfall, 1), ("storm_pond", storm_pond, 3), ("storm_outfall", storm_outfall, 1),
    ("school", school, 2), ("high_school", high_school, 2), ("clinic", clinic, 2), ("hospital", hospital, 1),
    ("row_houses", row_houses, 3), ("apartments", apartments, 3), ("apartment_court", apartment_court, 3),
    ("main_street", main_street, 4), ("office_block", office_block, 3), ("department_store", department_store, 3),
    ("works", works, 3), ("site_small", site_small, 2), ("site_large", site_large, 2), ("site_wide", site_wide, 2), ("site_deep", site_deep, 2), ("site_huge", site_huge, 2),
    ("sewage_works", sewage_works, 1), ("treatment_plant", treatment_plant, 1),
    ("tram_depot", tram_depot, 1), ("bus_garage", bus_garage, 2), ("subway_station", subway_station, 1),
    ("oil_plant", oil_plant, 1), ("gas_plant", gas_plant, 1), ("hydro_plant", hydro_plant, 1), ("nuclear_plant", nuclear_plant, 1),
    ("substation", substation, 2), ("dump", dump, 2), ("incinerator", incinerator, 1), ("recycling", recycling, 1),
    ("farm", farm, 4), ("woodlot", woodlot, 3), ("mine", mine, 3), ("colliery", colliery, 3), ("oil_well", oil_well, 3),
    ("offices", offices, 3), ("office_building", office_building, 3), ("office_tower", office_tower, 3), ("glass_tower", glass_tower, 3),
    ("volunteer_hall", volunteer_hall, 2), ("ladder_company", ladder_company, 2), ("ambulance_station", ambulance_station, 2),
    ("nursing_home", nursing_home, 2), ("cooling_centre", cooling_centre, 2), ("library", library, 2), ("college", college, 2),
    # Newer kinds of the services, each where the kind before it stood.
    ("elementary_school", elementary_school, 2), ("community_school", community_school, 2), ("composite_high", composite_high, 2),
    ("branch_library", branch_library, 2), ("media_library", media_library, 2), ("health_centre", health_centre, 2),
    ("community_health", community_health, 2), ("general_hospital", general_hospital, 2), ("medical_centre", medical_centre, 2),
    ("care_home", care_home, 2), ("motor_fire_station", motor_fire_station, 2), ("fire_hall", fire_hall, 2),
    ("precinct", precinct, 2), ("community_policing", community_policing, 2),
    # Green space.
    ("playground", playground, 3), ("town_square", town_square, 3), ("plaza", plaza_square, 3), ("formal_garden", formal_garden, 3),
    ("city_park", city_park, 3), ("allotments", allotments, 3), ("community_garden", community_garden, 3), ("pocket_park", pocket_park, 3),
    ("urban_woodland", urban_woodland, 4), ("botanical_garden", botanical_garden, 2), ("wetland_reserve", wetland_reserve, 3),
    ("greenway", greenway, 4), ("dog_park", dog_park, 3),
    # Sport and culture.
    ("sports_ground", sports_ground, 3), ("lit_fields", lit_fields, 2), ("public_baths", public_baths, 2), ("swimming_pool", swimming_pool, 2),
    ("aquatic_centre", aquatic_centre, 2), ("tennis_courts", tennis_courts, 3), ("ice_rink", ice_rink, 2), ("ballpark", ballpark, 2),
    ("arena", arena, 2), ("stadium", stadium, 3), ("golf_course", golf_course, 2), ("skate_park", skate_park, 2), ("rec_centre", rec_centre, 2),
    ("bandstand", bandstand, 2), ("variety_theatre", variety_theatre, 2), ("picture_palace", picture_palace, 3), ("multiplex", multiplex, 3),
    ("opera_house", opera_house, 2), ("museum", museum, 2), ("art_gallery", art_gallery, 2), ("concert_hall", concert_hall, 2),
    ("zoo", zoo, 4), ("fairground", fairground, 2), ("amusement_park", amusement_park, 2), ("drive_in", drive_in, 2),
    ("aquarium", aquarium, 2), ("convention_centre", convention_centre, 2),
    # Civic buildings, and the rest of schooling, health, police and fire.
    ("town_hall", town_hall, 3), ("city_hall", city_hall, 2), ("civic_centre", civic_centre, 2), ("post_office", post_office, 2), ("shelter", shelter, 2), ("founders_statue", founders_statue, 1), ("mayors_mansion", mayors_mansion, 1),
    ("exhibition_hall", exhibition_hall, 1), ("observation_tower", observation_tower, 1), ("conservatory", conservatory, 1),
    ("town_museum", town_museum, 1),
    ("cemetery", cemetery, 3), ("memorial_garden", memorial_garden, 2), ("fountain", fountain, 3), ("clock_tower", clock_tower, 2),
    ("war_memorial", war_memorial, 2), ("kindergarten", kindergarten, 2), ("junior_high", junior_high, 2),
    ("vocational_school", vocational_school, 2), ("central_library", central_library, 2), ("community_college", community_college, 2),
    ("university", university, 2), ("research_campus", research_campus, 2), ("sanatorium", sanatorium, 2),
    ("public_health_office", public_health_office, 2), ("police_box", police_box, 2), ("traffic_police", traffic_police, 2),
    ("fireboat_station", fireboat_station, 2),
    ("ferry_terminal", ferry_terminal, 2),
    ("police_hq", police_hq, 1), ("courthouse", courthouse, 2), ("jail", jail, 1),
    ("farmstead", farmstead, 4), ("country_house", country_house, 3), ("acreage_home", acreage_home, 3),
    ("crossroads_store", crossroads_store, 3), ("roadhouse", roadhouse, 3),
    ("highrise", highrise, 3), ("slender_tower", slender_tower, 3), ("tall_hotel", highrise_hotel, 3),
    ("skyscraper", skyscraper, 3), ("supertall", supertall, 3),
    ("shophouse", shophouse, 3), ("flats_over_shops", flats_over_shops, 3), ("mixed_block", mixed_block, 3), ("podium_tower", podium_tower, 3),
]
BUILDINGS += SIZES


def for_sale(look):
    """A sign on a post at the front corner of a home's lot, for a home standing empty."""
    b = Building(height=12)
    d = b.d
    x, y = b.ground(3, 30)
    d.line([x, y, x, y - 10], c("#6b4a30"))
    d.rectangle([x + 1, y - 10, x + 8, y - 5], c("#f2f2ea"), OUTLINE)
    d.line([x + 3, y - 8, x + 6, y - 8], c("#c0392b"))
    d.line([x + 3, y - 7, x + 5, y - 7], c("#c0392b"))
    return b


# ---- power lines ------------------------------------------------------------------
# A pole in the middle of the tile with a cross arm, and wires to the neighbours
# that carry power. The wires are thin and let the ground show through.

POLE = c("#6b4a30")
WIRE = (32, 34, 38, 150)
INSULATOR = c("#dfe6ea")
POLE_HEIGHT = 14
SAG = 3


def power_line(look, mask):
    """North 1, east 2, south 4, west 8. Each pole draws its wires half way to
    the next pole, which draws the other half."""
    lift = lift_for(POLE_HEIGHT + T // 2)
    img = Image.new("RGBA", (T, T + lift), (0, 0, 0, 0))
    wires = Image.new("RGBA", img.size, (0, 0, 0, 0))
    wd = ImageDraw.Draw(wires)
    cx, foot = 15, 16 + lift
    top = foot - POLE_HEIGHT
    half = T // 2 + 1
    # East and west runs carry two wires, dipping towards the middle of the span,
    # which is the tile's edge. A north to south run shows as one wire, since two
    # seen from above read as a pair of rails.
    for gap in (-3, 3):
        for side, bit in ((1, 2), (-1, 8)):
            if not mask & bit:
                continue
            pts = []
            for k in range(half + 1):
                u = k / T
                pts.append((cx + side * k, top + gap + round(4 * SAG * u * (1 - u))))
            wd.line(pts, WIRE)
    if mask & 1: wd.line([cx + 1, top, cx + 1, top - half], WIRE)
    if mask & 4: wd.line([cx + 1, top, cx + 1, top + half], WIRE)
    img.alpha_composite(wires)
    d = ImageDraw.Draw(img)
    d.rectangle([cx, top, cx + 1, foot], POLE)
    d.line([cx + 1, top + 1, cx + 1, foot], shade(POLE, 0.7))
    # The cross arm runs across the wires it carries.
    if mask & 10:
        d.line([cx, top - 3, cx, top + 3], POLE)
        for yy in (top - 3, top + 3):
            d.point((cx + 1, yy), INSULATOR)
    else:
        d.line([cx - 2, top, cx + 3, top], POLE)
        d.point((cx + 1, top - 1), INSULATOR)
    if look == "snow":
        d.point((cx, top - 1), SNOW)
        d.point((cx + 1, top - 1), SNOW)
    return img, lift, [(0, cx, foot - lift, POLE_HEIGHT, 1, 0)]


# Telephone lines: a shorter, thinner pole with a single wire, black for
# copper and bright for fibre, set to one side of the tile so it sits clear
# of a power line on the same street.

PHONE_POLE = c("#7a5a3e")
PHONE_HEIGHT = 10
COPPER_WIRE = (24, 24, 28, 170)
FIBRE_WIRE = (222, 120, 40, 220)


def phone_line(look, mask, fibre):
    """North 1, east 2, south 4, west 8: one wire each way it runs, from a slim pole near the tile's corner."""
    lift = lift_for(PHONE_HEIGHT + T // 2)
    img = Image.new("RGBA", (T, T + lift), (0, 0, 0, 0))
    wires = Image.new("RGBA", img.size, (0, 0, 0, 0))
    wd = ImageDraw.Draw(wires)
    wire = FIBRE_WIRE if fibre else COPPER_WIRE
    cx, foot = 6, 26 + lift
    top = foot - PHONE_HEIGHT
    for side, bit in ((1, 2), (-1, 8)):
        if not mask & bit:
            continue
        end = T if side > 0 else 0
        pts = []
        steps = abs(end - cx)
        for k in range(steps + 1):
            u = k / max(1, steps)
            pts.append((cx + side * k, top + round(3 * u * (1 - u) * 2)))
        wd.line(pts, wire)
    if mask & 1: wd.line([cx, top, cx, top - (foot - lift)], wire)
    if mask & 4: wd.line([cx, top, cx, top + (T - (foot - lift))], wire)
    img.alpha_composite(wires)
    d = ImageDraw.Draw(img)
    d.line([cx, top, cx, foot], PHONE_POLE)
    d.line([cx - 1, top + 1, cx + 1, top + 1], PHONE_POLE)
    d.point((cx, top), INSULATOR if not fibre else c("#f0c070"))
    if look == "snow":
        d.point((cx, top - 1), SNOW)
    return img, lift, [(0, cx, foot - lift, PHONE_HEIGHT, 1, 0)]


def phone_span(across, fibre):
    """A phone wire over a tile with no pole, as over track."""
    lift = lift_for(PHONE_HEIGHT + T // 2)
    img = Image.new("RGBA", (T, T + lift), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    wire = FIBRE_WIRE if fibre else COPPER_WIRE
    cx, foot = 6, 26 + lift
    top = foot - PHONE_HEIGHT
    if across:
        d.line([(x, top + round(2 * math.sin(math.pi * x / T))) for x in range(T + 1)], wire)
    else:
        d.line([cx, top - (foot - lift), cx, top - (foot - lift) + T], wire)
    return img, lift


def exchange(look, v):
    """A telephone exchange on 2 by 1 tiles: plain brick, tall windows for the switchboards, an aerial on the roof."""
    b = Building(2, 1, height=2 * STOREY + 14)
    d = b.d
    roof, wall = b.box(3, 4, 60, 24, 2 * STOREY + 2)
    brick(d, wall, c("#8e5a44") if v == 0 else c("#a89a84"))
    x0, y0, x1, y1 = wall
    windows(d, wall, 2, glass=c("#46586a"), sill=TRIM, every=6, width=3, height=5, skip_door=True)
    door(d, wall, c("#3a3a40"))
    d.rectangle([x0 + 4, y0 + 2, x0 + 14, y0 + 4], c("#d9c060"))
    d.rectangle(wall, outline=OUTLINE)
    flat_roof(b.img, roof, look, random.Random(8900 + v), [("vent", 8, 4)], parapet=STONE)
    # The aerial, or on the newer kind a microwave mast with its dishes.
    rx0, ry0, rx1, ry1 = roof
    if v == 1:
        mx, my = rx0 + 10, (ry0 + ry1) // 2
        d.line([mx, my, mx, my - 18], c("#8a8f96"), 2)
        for k, side in enumerate((-1, 1)):
            d.ellipse([mx + side * 2 - 2, my - 16 + k * 5, mx + side * 2 + 2, my - 12 + k * 5], c("#e8e8e4"), OUTLINE)
        d.point((mx, my - 19), c("#e05040"))
        b.casters.append((1, 12, (ry0 + ry1) // 2 - 1 - b.lift, 15, (ry0 + ry1) // 2 + 1 - b.lift, 18))
    ax, ay = rx1 - 8, (ry0 + ry1) // 2
    d.line([ax, ay, ax, ay - 14], c("#9aa0a6"))
    for k in range(3):
        d.line([ax - 3 + k, ay - 12 + k * 4, ax + 3 - k, ay - 12 + k * 4], c("#9aa0a6"))
    b.casters.append((1, rx1 - 9, (ry0 + ry1) // 2 - 1 - b.lift, rx1 - 7, (ry0 + ry1) // 2 + 1 - b.lift, 14))
    return b


def cell_tower(look, v):
    """A mobile phone mast on one tile: a steel lattice tower with panels at the top, fenced at its foot."""
    b = Building(height=40)
    d = b.d
    gx0, gy0 = b.ground(4, 4)
    gx1, gy1 = b.ground(27, 27)
    d.rectangle([gx0, gy0, gx1, gy1], c("#b8b2a6") if look != "snow" else c("#dfe5ea"))
    for xx in range(gx0, gx1 + 1, 2):
        d.point((xx, gy0), c("#6a6a70"))
        d.point((xx, gy1), c("#6a6a70"))
    for yy in range(gy0, gy1 + 1, 2):
        d.point((gx0, yy), c("#6a6a70"))
        d.point((gx1, yy), c("#6a6a70"))
    fx, fy = b.ground(15, 18)
    top = fy - 36
    steel = c("#9aa0a6")
    d.line([fx - 4, fy, fx, top], steel)
    d.line([fx + 4, fy, fx, top], steel)
    for k in range(1, 6):
        yy = fy - k * 6
        w = max(1, 4 - k * 4 // 6)
        d.line([fx - w, yy, fx + w, yy], steel)
    for dx in (-3, 2):
        d.rectangle([fx + dx, top + 2, fx + dx + 1, top + 7], c("#e6e6e6"), OUTLINE)
    d.point((fx, top - 1), c("#e05040"))
    d.rectangle([gx0 + 3, gy1 - 6, gx0 + 8, gy1 - 2], c("#d0d0cc"), OUTLINE)
    b.casters.append((1, 13, 16, 17, 20, 36))
    return b


BUILDINGS += [("exchange", exchange, 2), ("cell_tower", cell_tower, 1)]


# Power from the weather: a wind farm, a solar farm and a battery yard.


def wind_farm(look, v):
    """Two wind turbines on 2 by 2 tiles of grass: white towers, a hub and three long blades each."""
    b = Building(2, 2, height=46)
    d = b.d
    white = c("#eef0f2")
    # Two turbines on the diagonal, so neither tower stands in front of the other's blades.
    for k, (tx, ty) in enumerate(((18, 22), (46, 54))):
        fx, fy = b.ground(tx, ty)
        top = fy - 34
        # The tower, narrowing up.
        d.polygon([(fx - 1, fy), (fx + 1, fy), (fx, top)], white, None)
        d.line([fx + 1, fy, fx, top], c("#b8bec4"))
        # The nacelle and hub.
        d.rectangle([fx - 2, top - 1, fx + 2, top + 1], white, OUTLINE)
        # Three blades, each turbine turned a little differently.
        a0 = (k * 37 + v * 20) * math.pi / 180
        for j in range(3):
            a = a0 + j * 2 * math.pi / 3
            ex = fx + round(11 * math.cos(a))
            ey = top + round(11 * math.sin(a))
            d.line([fx, top, ex, ey], white, width=2)
            d.line([fx, top, ex, ey], c("#c8ced4"))
        d.point((fx, top), c("#9aa0a6"))
        b.casters.append((1, tx - 1, ty - 1, tx + 2, ty + 2, 34))
    return b


def solar_farm(look, v):
    """A solar farm on 3 by 3 tiles: rows of dark blue panels tilted to the sun, with paths between and a small shed."""
    b = Building(3, 3, height=6)
    d = b.d
    gx0, gy0 = b.ground(2, 2)
    gx1, gy1 = b.ground(93, 93)
    d.rectangle([gx0, gy0, gx1, gy1], SNOW_GROUND if look == "snow" else c("#9ab07a"))
    panel = c("#26406a")
    glint = c("#4a6aa0")
    for row in range(8):
        y = 6 + row * 11
        px0, py0 = b.ground(6, y)
        px1, py1 = b.ground(80, y + 6)
        d.rectangle([px0, py0, px1, py1], SNOW_ROOF[0] if look == "snow" and row % 2 == 0 else panel)
        for xx in range(px0 + 4, px1, 6):
            d.line([xx, py0, xx, py1], c("#1a2c4c"))
        d.line([px0, py0, px1, py0], glint)
        d.line([px0, py1 + 1, px1, py1 + 1], c("#3a3a40"))
    # The inverter shed.
    roof, wall = b.box(83, 74, 92, 86, 6)
    d.rectangle(wall, c("#d0d0cc"))
    d.rectangle(wall, outline=OUTLINE)
    d.rectangle(roof, SNOW_ROOF[0] if look == "snow" else c("#8a9096"), OUTLINE)
    return b


def battery(look, v):
    """A battery yard on 2 by 1 tiles: a row of white containers with vents, behind a fence."""
    b = Building(2, 1, height=10)
    d = b.d
    gx0, gy0 = b.ground(2, 4)
    gx1, gy1 = b.ground(61, 29)
    d.rectangle([gx0, gy0, gx1, gy1], c("#b8b2a6") if look != "snow" else c("#dfe5ea"))
    for xx in range(gx0, gx1 + 1, 2):
        d.point((xx, gy0), c("#6a6a70"))
        d.point((xx, gy1), c("#6a6a70"))
    for k in range(5):
        x0 = 5 + k * 11
        roof, wall = b.box(x0, 8, x0 + 9, 22, 8)
        d.rectangle(wall, c("#e6e8ea"))
        for yy in range(wall[1] + 2, wall[3], 2):
            d.line([wall[0] + 2, yy, wall[2] - 2, yy], c("#b8bec4"))
        d.point((wall[2] - 2, wall[1] + 1), c("#4cb86a"))
        d.rectangle(wall, outline=OUTLINE)
        d.rectangle(roof, SNOW_ROOF[0] if look == "snow" else c("#cfd3d6"), OUTLINE)
    return b


BUILDINGS += [("wind_farm", wind_farm, 2), ("solar_farm", solar_farm, 1), ("battery", battery, 1)]


def river_turbine(look, v):
    """A turbine in a river's current: a small concrete platform with its housing, rails, and the water churned behind it."""
    b = Building(height=10)
    d = b.d
    gx0, gy0 = b.ground(8, 10)
    gx1, gy1 = b.ground(24, 22)
    # White water downstream of it.
    for k in range(6):
        x, y = b.ground(10 + k * 2, 24 + (k % 2))
        d.point((x, y), c("#e6f0f6"))
    d.rectangle([gx0, gy0, gx1, gy1], c("#b8b4aa"), OUTLINE)
    roof, wall = b.box(11, 12, 21, 19, 6)
    d.rectangle(wall, c("#d8d4ca"))
    d.rectangle(wall, outline=OUTLINE)
    d.rectangle(roof, SNOW_ROOF[0] if look == "snow" else c("#5a7a8a"), OUTLINE)
    d.point((gx1 - 1, gy0 + 1), c("#e8a33a"))
    return b


def tidal_turbine(look, v):
    """A tidal turbine on 2 by 1 tiles of water: a yellow pile with its control house, a crossbeam out to two rotors turning in
    rings of white water, and buoys at the ends."""
    b = Building(2, 1, height=16)
    d = b.d
    white = c("#eef4f8")
    # The crossbeam, just under the surface.
    x0, y0 = b.ground(10, 16)
    x1, _ = b.ground(54, 16)
    d.rectangle([x0, y0 - 1, x1, y0 + 1], (40, 50, 60, 170))
    for cx in (12, 52):
        x, y = b.ground(cx, 16)
        d.ellipse([x - 9, y - 6, x + 9, y + 6], (230, 240, 246, 110))
        d.ellipse([x - 9, y - 6, x + 9, y + 6], outline=white)
        d.ellipse([x - 6, y - 4, x + 6, y + 4], outline=(230, 240, 246, 170))
        # Two blades and a hub, dark under the water.
        d.line([x - 7, y + 3, x + 7, y - 3], (20, 30, 40, 200), 2)
        d.ellipse([x - 2, y - 2, x + 2, y + 2], c("#e0b020"), OUTLINE)
    roof, wall = b.box(27, 10, 37, 22, 6)
    d.rectangle(wall, c("#e0b020"))
    d.rectangle(wall, outline=OUTLINE)
    d.rectangle(roof, c("#e8e4da"), OUTLINE)
    d.rectangle([roof[0] + 2, roof[1] + 2, roof[0] + 6, roof[1] + 6], c("#7a8088"), OUTLINE)
    mx, my = b.ground(34, 14)
    d.line([mx, my - 6, mx, my - 15], c("#c8ccd0"))
    d.point((mx, my - 16), c("#e05040"))
    for bx, by in ((3, 5), (60, 27)):
        x, y = b.ground(bx, by)
        d.ellipse([x - 2, y - 3, x + 2, y + 1], c("#e8a33a"), OUTLINE)
        d.point((x, y - 4), c("#2a2a2e"))
    return b



def offshore_wind(look, v):
    """Two wind turbines on 2 by 2 tiles of open water, each on a yellow foundation with a ring of white water."""
    b = Building(2, 2, height=50)
    d = b.d
    white = c("#eef0f2")
    for k, (tx, ty) in enumerate(((18, 22), (46, 54))):
        fx, fy = b.ground(tx, ty)
        d.ellipse([fx - 5, fy - 2, fx + 5, fy + 3], c("#e6f0f6"))
        d.rectangle([fx - 2, fy - 3, fx + 2, fy], c("#e0b020"), OUTLINE)
        top = fy - 38
        d.polygon([(fx - 1, fy - 3), (fx + 1, fy - 3), (fx, top)], white, None)
        d.line([fx + 1, fy - 3, fx, top], c("#b8bec4"))
        d.rectangle([fx - 2, top - 1, fx + 2, top + 1], white, OUTLINE)
        a0 = (k * 53 + v * 25) * math.pi / 180
        for j in range(3):
            a = a0 + j * 2 * math.pi / 3
            ex = fx + round(13 * math.cos(a))
            ey = top + round(13 * math.sin(a))
            d.line([fx, top, ex, ey], white, width=2)
            d.line([fx, top, ex, ey], c("#c8ced4"))
        d.point((fx, top), c("#9aa0a6"))
        b.casters.append((1, tx - 1, ty - 1, tx + 2, ty + 2, 38))
    return b


BUILDINGS += [("river_turbine", river_turbine, 1), ("tidal_turbine", tidal_turbine, 1), ("offshore_wind", offshore_wind, 2)]


# Newer power stations, each in the footprint of the one before it, and the stations of their own.


def pulverized_coal(look, v):
    """A coal station of the 1930s on 2 by 2 tiles: a tall brick boiler house with a row of three stacks, the turbine hall in
    front and a conveyor up from the coal."""
    b = Building(2, 2, height=56)
    d = b.d
    rng = random.Random(17000 + v)
    plant_yard(b, look, 1, 1, 62, 62, c("#7a7268"))
    gx, gy = b.ground(48, 58)
    for k, col in enumerate([c("#1e1e22"), c("#2a2a2f"), c("#35353b")]):
        d.ellipse([gx - 10 + k * 3, gy - 4 - k * 2, gx + 10 - k * 3, gy + 2 - k * 2], SNOW_ROOF[k] if look == "snow" else col)
    d.line([gx - 4, gy - 6, gx - 14, gy - 22], c("#5a5048"), 2)
    for x in ((12, 24, 36) if v == 0 else (14, 34)):
        chimney(b, x, 6, 56, c("#8a4f3c"), look)
    if v == 1:
        cylinder(b, look, 52, 12, 6, 16, c("#8a8c90"))
    roof, wall = b.box(4, 10, 44, 30, 5 * STOREY)
    brick(d, wall, c("#7a4636") if v == 0 else c("#8a5a44"))
    windows(d, wall, 5, glass=c("#6c7f8a"), every=5, width=2, height=4)
    d.rectangle(wall, outline=OUTLINE)
    flat_roof(b.img, roof, look, rng, [("vent", 4, 4)], parapet=c("#6e3e30"))
    roof, wall = b.box(4, 32, 40, 52, 2 * STOREY + 4)
    brick(d, wall, c("#7a4636"))
    windows(d, wall, 2, glass=c("#6c7f8a"), every=5, width=3, height=4)
    d.rectangle(wall, outline=OUTLINE)
    d.rectangle(roof, SNOW_ROOF[0] if look == "snow" else IRON_ROOF, OUTLINE)
    return b


def supercritical_coal(look, v):
    """A big coal station of the 1970s on 2 by 2 tiles: a steel-clad boiler house, one very tall concrete stack, a cooling
    tower and a long coal belt."""
    b = Building(2, 2, height=64)
    d = b.d
    rng = random.Random(17100 + v)
    plant_yard(b, look, 1, 1, 62, 62, c("#8a867c"))
    if v == 0:
        cooling_tower(b, look, 46, 40, 12, 34)
    else:
        cooling_tower(b, look, 16, 44, 12, 34)
    roof, wall = b.box(4, 6, 32, 34, 5 * STOREY + 4) if v == 0 else b.box(34, 6, 60, 32, 5 * STOREY + 4)
    d.rectangle(wall, c("#9aa6ae") if v == 0 else c("#b8b2a2"))
    for yy in range(wall[1] + 2, wall[3], 3):
        d.line([wall[0] + 1, yy, wall[2] - 1, yy], shade(c("#9aa6ae"), 0.9))
    d.rectangle(wall, outline=OUTLINE)
    flat_roof(b.img, roof, look, rng, [("vent", 6, 6), ("hatch", 16, 12)], parapet=c("#7f8a90"))
    banded_chimney(b, look, 38 if v == 0 else 30, 10, 64, c("#b4b0a8"), c("#b04030"))
    gx, gy = b.ground(6, 58)
    d.rectangle([gx, gy - 4, gx + 22, gy], SNOW_ROOF[1] if look == "snow" else c("#2a2a2f"))
    d.line([gx + 22, gy - 2, gx + 30, gy - 24], c("#6a6a70"), 2)
    transformer(b, look, 56, 60)
    return b


def large_oil(look, v):
    """A large oil station of the 1950s on 2 by 2 tiles: four tanks of fuel oil, a pale hall and twin banded stacks."""
    b = Building(2, 2, height=56)
    d = b.d
    plant_yard(b, look, 1, 1, 62, 62)
    for (x, y) in ((10, 10), (28, 10), (10, 28), (28, 28)) if v == 0 else ((10, 14), (26, 14), (42, 14)):
        cylinder(b, look, x, y, 7, 10, c("#c9c4b8") if v == 0 else c("#d8d4ca"))
    roof, wall = b.box(4, 40, 46, 60, 2 * STOREY + 6)
    d.rectangle(wall, c("#d8d4c8"))
    windows(d, wall, 1, glass=c("#4a5866"), every=6, width=4, height=3)
    d.rectangle(wall, outline=OUTLINE)
    flat_roof(b.img, roof, look, random.Random(17200 + v), [("vent", 6, 4), ("vent", 26, 4)], parapet=c("#a8a296"))
    for x in (50, 58):
        banded_chimney(b, look, x, 34, 56, c("#b8b2a6"), c("#b04030"), SMOKE)
    return b


def combined_cycle(look, v):
    """A combined cycle gas station of the 1990s on 2 by 2 tiles: a long white turbine hall, the heat recovery boilers beside
    it with their stacks, and an air-cooled condenser of fans."""
    b = Building(2, 2, height=44)
    d = b.d
    plant_yard(b, look, 1, 1, 62, 62, c("#a29e94"))
    roof, wall = b.box(4, 4, 58, 20, STOREY + 2)
    d.rectangle(wall, c("#b8bcc0"), OUTLINE)
    d.rectangle(roof, SNOW_ROOF[0] if look == "snow" else c("#9ea4aa"), OUTLINE)
    for k in range(6 if v == 0 else 3):
        fx = roof[0] + 3 + k * 9
        d.ellipse([fx, roof[1] + 3, fx + 6, roof[3] - 3], c("#3a3e44"))
    if v == 1:
        cylinder(b, look, 42, 10, 5, 8, c("#d8d4ca"))
        cylinder(b, look, 54, 10, 5, 8, c("#d8d4ca"))
    roof, wall = b.box(4, 30, 36, 58, 3 * STOREY)
    d.rectangle(wall, c("#eceae4"))
    d.rectangle([wall[0], wall[3] - 3, wall[2], wall[3]], c("#4f7a9a") if v == 0 else c("#3f8a5a"))
    d.rectangle(wall, outline=OUTLINE)
    flat_roof(b.img, roof, look, random.Random(17300 + v), [("vent", 8, 6)], parapet=c("#c8c2b4"))
    for x in (42, 52):
        roof, wall = b.box(x - 4, 30, x + 4, 52, 3 * STOREY + 4)
        d.rectangle(wall, c("#c4c8cc"))
        d.rectangle(wall, outline=OUTLINE)
        d.rectangle(roof, c("#a8aeb4"), OUTLINE)
        chimney(b, x, 32, 44, c("#c4c8cc"), look, STEAM)
    return b


def hydro_station(look, v):
    """A big hydro station of the 1930s on 2 by 2 tiles: a tall powerhouse of concrete in the style of its day, with long
    vertical windows, its penstocks, and a switchyard."""
    b = Building(2, 2, height=4 * STOREY + 8)
    d = b.d
    plant_yard(b, look, 1, 1, 62, 62, c("#a8a49a"))
    for x in ((10, 22, 34, 46) if v == 0 else (10, 22, 34)):
        gx, gy = b.ground(x, 2)
        d.rectangle([gx - 3, gy, gx + 3, gy + 14], c("#5a6066"), OUTLINE)
    if v == 1:
        cylinder(b, look, 52, 8, 5, 22, c("#c4beb0"))
    roof, wall = b.box(4, 16, 60, 46, 4 * STOREY)
    d.rectangle(wall, c("#d8d2c2") if v == 0 else c("#c4beb0"))
    x0, y0, x1, y1 = wall
    for k in range(5):
        wx = x0 + 5 + k * 11
        d.rectangle([wx, y0 + 2, wx + 3, y1 - 3], c("#4a5866"))
        d.line([wx - 2, y0, wx - 2, y1], shade(c("#d8d2c2"), 0.85))
    d.rectangle([x0, y0, x1, y0 + 2], c("#b4ae9f"))
    d.rectangle(wall, outline=OUTLINE)
    flat_roof(b.img, roof, look, random.Random(17400 + v), [("vent", 10, 8), ("vent", 40, 8)], parapet=c("#b4ae9f"))
    for x in (10, 22, 34):
        transformer(b, look, x, 58)
    return b


def advanced_reactor(look, v):
    """An advanced reactor of the 2000s on 3 by 3 tiles: a single big cooling tower, a smooth domed containment beside a
    pale turbine hall, and a neat fenced yard."""
    b = Building(3, 3, height=64)
    d = b.d
    plant_yard(b, look, 1, 1, 94, 94, c("#b0aea6"))
    gx0, gy0 = b.ground(1, 1)
    gx1, gy1 = b.ground(94, 94)
    d.rectangle([gx0, gy0, gx1, gy1], outline=c("#d0ccc0"))
    if v == 0:
        cooling_tower(b, look, 26, 40, 20, 60)
    else:
        roof, wall = b.box(6, 10, 50, 52, STOREY + 2)
        d.rectangle(wall, c("#b8bcc0"), OUTLINE)
        d.rectangle(roof, SNOW_ROOF[0] if look == "snow" else c("#9ea4aa"), OUTLINE)
        for k in range(4):
            for j in range(3):
                fx = roof[0] + 4 + k * 10
                fy = roof[1] + 4 + j * 13
                d.ellipse([fx, fy, fx + 8, fy + 8], c("#3a3e44"))
                d.line([fx + 4, fy + 1, fx + 4, fy + 7], c("#6a7076"))
    gx, gy = b.ground(72, 46)
    col = c("#e6e2d8")
    d.rectangle([gx - 14, gy - 26, gx + 14, gy], col)
    d.line([gx - 14, gy - 26, gx - 14, gy], OUTLINE)
    d.line([gx + 14, gy - 26, gx + 14, gy], OUTLINE)
    d.pieslice([gx - 14, gy - 40, gx + 14, gy - 12], 180, 360, SNOW_ROOF[0] if look == "snow" else c("#eeeae2"), OUTLINE)
    b.casters.append((1, 58, 38, 87, 54, 32))
    roof, wall = b.box(6, 66, 88, 90, 3 * STOREY)
    d.rectangle(wall, c("#e2e4e6"))
    d.rectangle([wall[0], wall[1], wall[2], wall[1] + 2], c("#3f8a9a") if v == 0 else c("#4f7a9a"))
    d.rectangle(wall, outline=OUTLINE)
    flat_roof(b.img, roof, look, random.Random(17500 + v), [("vent", 8, 6), ("vent", 40, 6), ("vent", 70, 6)], parapet=c("#b8bec4"))
    return b


def tall_turbine(b, look, tx, ty, height, blade, angle):
    """One tall modern wind turbine standing at tile pixel tx, ty."""
    d = b.d
    white = c("#f2f4f6")
    fx, fy = b.ground(tx, ty)
    top = fy - height
    d.polygon([(fx - 1, fy), (fx + 2, fy), (fx, top)], white, None)
    d.line([fx + 1, fy, fx, top], c("#b8bec4"))
    d.rectangle([fx - 3, top - 1, fx + 3, top + 1], white, OUTLINE)
    a0 = angle * math.pi / 180
    for j in range(3):
        a = a0 + j * 2 * math.pi / 3
        ex = fx + round(blade * math.cos(a))
        ey = top + round(blade * math.sin(a))
        d.line([fx, top, ex, ey], white, width=2)
        d.line([fx, top, ex, ey], c("#c8ced4"))
    d.point((fx, top), c("#e05040"))
    b.casters.append((1, tx - 1, ty - 1, tx + 2, ty + 2, height))


def tall_wind(look, v):
    """A tall wind turbine of the 2020s on 2 by 2 tiles of grass: one tower twice the height of the old ones, with long
    slender blades and a red light on the nacelle."""
    b = Building(2, 2, height=62)
    if v == 1:
        tx, ty = b.ground(32, 46)
        b.d.rectangle([tx - 1, ty, tx + 1, ty + 17], SNOW_GROUND if look == "snow" else c("#b8ad90"))
        b.d.rectangle([tx + 6, ty + 8, tx + 11, ty + 13], c("#8a9096"), OUTLINE)
    tall_turbine(b, look, 32, 44, 50, 18, 20 + v * 35)
    return b


def bifacial_solar(look, v):
    """A bifacial solar farm of the 2020s on 3 by 3 tiles: rows of panels up on trackers that turn with the sun, with grass
    and sheep under them."""
    b = Building(3, 3, height=8)
    d = b.d
    rng = random.Random(17600 + v)
    gx0, gy0 = b.ground(2, 2)
    gx1, gy1 = b.ground(93, 93)
    d.rectangle([gx0, gy0, gx1, gy1], SNOW_GROUND if look == "snow" else c("#8aaa6a"))
    if v == 1:
        rx, ry = b.ground(45, 2)
        d.rectangle([rx, ry, rx + 5, ry + 91], SNOW_GROUND if look == "snow" else c("#b8ad90"))
    spans = [(4, 90)] if v == 0 else [(4, 42), (52, 90)]
    for row in range(7):
        y = 6 + row * 13
        for (a, z) in spans:
            px0, py0 = b.ground(a, y)
            px1, py1 = b.ground(z, y + 4)
            d.line([px0, py1 + 3, px1, py1 + 3], c("#5a5a60"))
            d.rectangle([px0, py0, px1, py1], SNOW_ROOF[0] if look == "snow" and row % 2 == 0 else c("#2a4a7a"))
            for xx in range(px0 + 3, px1, 4):
                d.line([xx, py0, xx, py1], c("#1a2c4c"))
            d.line([px0, py0, px1, py0], c("#7a9ac8"))
    if look not in ("snow",):
        for _ in range(6):
            sx, sy = b.ground(rng.randrange(6, 88), rng.choice([11, 24, 37, 50, 63, 76]))
            d.rectangle([sx, sy, sx + 2, sy + 1], c("#f2f0ea"))
            d.point((sx + 3, sy), c("#3a3a3a"))
    return b


def floating_offshore(look, v):
    """A floating wind turbine of the 2030s on 2 by 2 tiles of deep water: one very tall turbine on a yellow three-legged
    float, moored by cables, with white water round it."""
    b = Building(2, 2, height=64)
    d = b.d
    fx, fy = b.ground(32, 44)
    for (dx, dy) in ((-9, 4), (9, 4), (0, -6)):
        d.line([fx, fy, fx + dx * 2, fy + dy * 2], (40, 60, 80, 140))
    d.ellipse([fx - 10, fy - 3, fx + 10, fy + 5], c("#e6f0f6"))
    d.polygon([(fx - 8, fy + 2), (fx + 8, fy + 2), (fx, fy - 6)], c("#e0b020"), OUTLINE)
    tall_turbine(b, look, 32, 42, 50, 18, 40 + v * 30)
    if v == 1:
        sx, sy = b.ground(48, 56)
        b.d.polygon([(sx, sy), (sx + 10, sy), (sx + 12, sy + 2), (sx + 10, sy + 4), (sx, sy + 4)], c("#e8702a"), OUTLINE)
        b.d.rectangle([sx + 2, sy + 1, sx + 5, sy + 3], c("#f2f2ea"))
        b.d.line([sx - 3, sy + 2, sx - 1, sy + 2], c("#e6f0f6"))
    return b


def tidal_array(look, v):
    """A tidal array of the 2030s on 2 by 1 tiles of water: a long floating platform with a row of rotors hung under it,
    their wakes trailing, and a light at each end."""
    b = Building(2, 1, height=10)
    d = b.d
    for cx in (10, 24, 40, 54):
        x, y = b.ground(cx, 18)
        d.ellipse([x - 5, y - 3, x + 5, y + 3], (20, 40, 60, 140))
        d.point((x, y + 5), c("#e6f0f6"))
    roof, wall = b.box(4, 12, 60, 18, 3)
    d.rectangle(wall, c("#d8b030"))
    d.rectangle(wall, outline=OUTLINE)
    d.rectangle(roof, c("#e8c040"), OUTLINE)
    for x in (6, 58):
        mx, my = b.ground(x, 14)
        d.line([mx, my - 3, mx, my - 8], c("#c8ccd0"))
        d.point((mx, my - 9), c("#e05040"))
    return b


def hydro_dam(look, v):
    """A hydro dam on 3 by 3 tiles: a curved concrete wall holding back the river, the spillway foaming, and the powerhouse at
    its foot with its switchyard."""
    b = Building(3, 3, height=4 * STOREY + 4)
    d = b.d
    top = b.lift
    water = c("#5a8fbf") if look != "snow" else c("#dfe8ef")
    d.rectangle([0, top, 95, top + 30], water)
    plant_yard(b, look, 1, 52, 94, 94, c("#a8a49a"))
    # The wall, curved against the water.
    d.chord([-20, top + 18, 115, top + 70], 180, 360, c("#c8c4b8"), OUTLINE)
    d.chord([-16, top + 24, 111, top + 66], 180, 360, c("#b4b0a6"))
    for x in range(10, 90, 10):
        d.line([x, top + 26, x, top + 40], shade(c("#b4b0a6"), 0.85))
    # The spillway.
    d.rectangle([40, top + 30, 56, top + 52], c("#e6f0f6"))
    for yy in range(top + 32, top + 52, 3):
        d.line([41, yy, 55, yy], c("#bcd4e4"))
    roof, wall = b.box(8, 56, 36, 76, 2 * STOREY + 4)
    d.rectangle(wall, c("#d0cabc"))
    for wx in range(wall[0] + 4, wall[2] - 2, 6):
        d.rectangle([wx, wall[1] + 2, wx + 2, wall[3] - 3], c("#4a5866"))
    d.rectangle(wall, outline=OUTLINE)
    flat_roof(b.img, roof, look, random.Random(17700 + v), [("vent", 6, 6)], parapet=c("#b4ae9f"))
    for x in (64, 76, 88):
        transformer(b, look, x, 76)
    return b


def pumped_storage(look, v):
    """Pumped storage on 3 by 2 tiles: an upper pond behind an embankment, the pipes down the slope and the pump house at the
    water's edge."""
    b = Building(3, 2, height=3 * STOREY)
    d = b.d
    rng = random.Random(17800 + v)
    lawn_box(b, look, 0, 0, 95, 63, rng)
    top = b.lift
    if v == 0:
        d.rectangle([6, top + 4, 60, top + 30], c("#8a8478"))
        d.rectangle([9, top + 7, 57, top + 27], c("#5a8fbf") if look != "snow" else c("#dfe8ef"))
    else:
        d.ellipse([6, top + 2, 58, top + 32], c("#8a8478"))
        d.ellipse([9, top + 5, 55, top + 29], c("#5a8fbf") if look != "snow" else c("#dfe8ef"))
        d.line([50, top + 26, 58, top + 18], c("#c8c4b8"), 3)
    for x in (66, 74):
        d.line([x, top + 14, x, top + 46], c("#5a6066"), 3)
    d.line([58, top + 14, 74, top + 14], c("#5a6066"), 3)
    roof, wall = b.box(56, 40, 90, 58, 2 * STOREY + 4)
    d.rectangle(wall, c("#c4beb0"))
    windows(d, wall, 2, glass=c("#4a5866"), every=6, width=3, height=3)
    d.rectangle(wall, outline=OUTLINE)
    flat_roof(b.img, roof, look, rng, [("vent", 6, 4)], parapet=c("#b4ae9f"))
    transformer(b, look, 20, 50)
    return b


def geothermal(look, v):
    """A geothermal station on 2 by 2 tiles: well heads in the yard, insulated steam pipes on stilts looping across it, a
    turbine hall and plumes of steam from the cooling towers."""
    b = Building(2, 2, height=3 * STOREY + 10)
    d = b.d
    plant_yard(b, look, 1, 1, 62, 62, c("#a8a090"))
    pipe = c("#c8ccd0")
    for (x, y) in (((8, 8), (24, 6), (52, 10)) if v == 0 else ((8, 6), (20, 6), (32, 6), (44, 6))):
        gx, gy = b.ground(x, y)
        d.rectangle([gx - 2, gy - 3, gx + 2, gy + 1], c("#7a8088"), OUTLINE)
    for pts in (((8, 10), (8, 24), (40, 24)), ((24, 8), (24, 20)), ((52, 12), (52, 24), (40, 24))):
        d.line([(x, y + b.lift - 3) for x, y in pts], pipe, 2)
    roof, wall = b.box(6, 32, 40, 58, 2 * STOREY + 4)
    d.rectangle(wall, c("#d8d4c8") if v == 0 else c("#c8b8a0"))
    windows(d, wall, 2, glass=c("#4a5866"), every=6, width=3, height=3)
    d.rectangle(wall, outline=OUTLINE)
    flat_roof(b.img, roof, look, random.Random(17900 + v), [("vent", 6, 6)], parapet=c("#a8a296"))
    roof, wall = b.box(44, 32, 60, 56, STOREY + 6)
    d.rectangle(wall, c("#8a9096"))
    d.rectangle(wall, outline=OUTLINE)
    d.rectangle(roof, c("#6a7076"), OUTLINE)
    b.plume(CLOUD, 52, 44 - (STOREY + 6))
    return b


def small_reactor(look, v):
    """A small modular reactor of the 2030s on 2 by 2 tiles: a low windowless reactor building, a turbine hall, a ring of
    mechanical draft cooling fans and a security fence."""
    b = Building(2, 2, height=3 * STOREY + 4)
    d = b.d
    plant_yard(b, look, 1, 1, 62, 62, c("#b4b2aa"))
    gx0, gy0 = b.ground(1, 1)
    gx1, gy1 = b.ground(62, 62)
    d.rectangle([gx0, gy0, gx1, gy1], outline=c("#d0ccc0"))
    roof, wall = b.box(6, 6, 30, 30, 3 * STOREY)
    d.rectangle(wall, c("#e6e2d8"))
    d.rectangle(wall, outline=OUTLINE)
    rx0, ry0, rx1, ry1 = roof
    d.rectangle(roof, SNOW_ROOF[0] if look == "snow" else c("#d8d4ca"), OUTLINE)
    if v == 0:
        d.ellipse([rx0 + 4, ry0 + 4, rx1 - 4, ry1 - 4], c("#c8c4ba"), OUTLINE)
    else:
        mid = (rx0 + rx1) // 2
        for (a, z) in ((rx0 + 2, mid - 1), (mid + 1, rx1 - 2)):
            d.ellipse([a, ry0 + 6, z, ry1 - 6], c("#c8c4ba"), OUTLINE)
    roof, wall = b.box(34, 10, 58, 30, 2 * STOREY + 2)
    d.rectangle(wall, c("#d4d8dc"))
    d.rectangle([wall[0], wall[1], wall[2], wall[1] + 2], c("#3f8a9a"))
    d.rectangle(wall, outline=OUTLINE)
    flat_roof(b.img, roof, look, random.Random(18000 + v), [("vent", 6, 6)], parapet=c("#b8bec4"))
    roof, wall = b.box(6, 40, 58, 52, STOREY)
    d.rectangle(wall, c("#b8bcc0"), OUTLINE)
    d.rectangle(roof, SNOW_ROOF[0] if look == "snow" else c("#9ea4aa"), OUTLINE)
    for k in range(5):
        fx = roof[0] + 3 + k * 10
        d.ellipse([fx, roof[1] + 2, fx + 7, roof[3] - 2], c("#3a3e44"))
    return b


def long_storage(look, v):
    """Long-duration storage of the 2030s on 2 by 2 tiles: big tanks of flow battery liquid in two colours, the pumps and
    stacks between them in a white shed, and a switchyard."""
    b = Building(2, 2, height=3 * STOREY)
    d = b.d
    plant_yard(b, look, 1, 1, 62, 62, c("#b8b2a6"))
    for (x, y, col) in ((12, 12, c("#c8c4e0")), (30, 12, c("#c8c4e0")), (12, 34, c("#e0c8b4")), (30, 34, c("#e0c8b4"))):
        cylinder(b, look, x, y, 7, 14, col)
    roof, wall = b.box(42, 8, 60, 44, 2 * STOREY)
    d.rectangle(wall, c("#eceae4"))
    for yy in range(wall[1] + 2, wall[3], 2):
        d.line([wall[0] + 2, yy, wall[2] - 2, yy], c("#c8ccd0"))
    d.rectangle(wall, outline=OUTLINE)
    d.rectangle(roof, SNOW_ROOF[0] if look == "snow" else c("#cfd3d6"), OUTLINE)
    transformer(b, look, 48, 56)
    return b


# Garbage by era.


def sanitary_landfill(look, v):
    """A sanitary landfill of the 1950s on 3 by 3 tiles: cells of refuse covered over with earth each day, one open where the
    compactor works, a lined pond for what drains off, and a weighbridge at the gate."""
    b = Building(3, 3, height=14)
    d = b.d
    rng = random.Random(18100 + v)
    gx0, gy0 = b.ground(1, 1)
    gx1, gy1 = b.ground(94, 94)
    noise_fill(b.img, (gx0, gy0, gx1 + 1, gy1 + 1), [c("#8a7a5a"), c("#7e6e50"), c("#94845e")] if look != "snow"
               else [c("#dfe5ea"), c("#cfd7de"), c("#eef2f5")], rng)
    d.rectangle([gx0, gy0, gx1, gy1], outline=c("#6b6258"))
    # Finished cells, grassed over, and the cell being filled.
    for (x0, y0, x1, y1) in (((4, 4, 44, 40), (48, 4, 90, 40)) if v == 0 else ((4, 4, 90, 26),)):
        cx0, cy0 = b.ground(x0, y0)
        cx1, cy1 = b.ground(x1, y1)
        noise_fill(b.img, (cx0, cy0, cx1, cy1), GRASS[look], rng)
        d.rectangle([cx0, cy0, cx1, cy1], outline=shade(c("#6a8a4a"), 0.8) if look != "snow" else c("#cfd7de"))
    cx0, cy0 = b.ground(4, 44)
    cx1, cy1 = b.ground(60, 76)
    for _ in range(160):
        x, y = rng.randrange(cx0, cx1), rng.randrange(cy0, cy1)
        if look != "snow" or rng.random() < 0.25:
            d.point((x, y), rng.choice(LITTER + GARBAGE))
    bx, by = b.ground(30, 62)
    d.rectangle([bx, by - 5, bx + 9, by], c("#d8a030"), OUTLINE)
    d.rectangle([bx + 2, by - 9, bx + 6, by - 5], c("#c89020"), OUTLINE)
    # The leachate pond.
    px, py = b.ground(66, 48)
    d.rectangle([px, py, px + 24, py + 16], c("#3a3a40"))
    d.rectangle([px + 2, py + 2, px + 22, py + 14], c("#5a6a5a") if look != "snow" else c("#dfe8ef"))
    roof, wall = b.box(66, 80, 90, 92, STOREY)
    d.rectangle(wall, c("#d8d4ca"))
    door(d, wall)
    d.rectangle(wall, outline=OUTLINE)
    flat_roof(b.img, roof, look, rng, [], parapet=c("#a8a296"))
    if v == 1:
        # Gas wells across the capped cell, and the header pipe joining them.
        hx0, hy = b.ground(8, 22)
        hx1, _ = b.ground(86, 22)
        b.d.line([hx0, hy, hx1, hy], c("#e8a33a"))
        for x in range(12, 86, 12):
            wx, wy = b.ground(x, 14)
            b.d.line([wx, wy, wx, hy], c("#e8a33a"))
            b.d.rectangle([wx - 1, wy - 2, wx + 1, wy], c("#8a8c90"), OUTLINE)
    return b


def waste_to_energy(look, v):
    """A waste-to-energy plant of the 1970s and on, on 2 by 2 tiles: a big clad hall with a curving roof, the trucks' ramp,
    one slim stack and its own transformers."""
    b = Building(2, 2, height=56)
    d = b.d
    plant_yard(b, look, 1, 1, 62, 62, c("#a29e94"))
    roof, wall = b.box(4, 6, 50, 44, 4 * STOREY)
    col = c("#7a9aa8") if v == 0 else c("#a8b4a0")
    d.rectangle(wall, col)
    for xx in range(wall[0] + 2, wall[2], 3):
        d.line([xx, wall[1] + 1, xx, wall[3] - 1], shade(col, 0.9))
    d.rectangle([wall[0] + 4, wall[3] - 9, wall[0] + 16, wall[3]], c("#4a4c50"))
    d.rectangle(wall, outline=OUTLINE)
    rx0, ry0, rx1, ry1 = roof
    d.rectangle(roof, SNOW_ROOF[0] if look == "snow" else c("#c8ccd0"))
    for yy in range(ry0 + 1, ry1, 2):
        d.line([rx0 + 1, yy, rx1 - 1, yy], shade(c("#c8ccd0"), 1.0 - 0.15 * abs(yy - (ry0 + ry1) / 2) / ((ry1 - ry0) / 2 + 1)))
    d.rectangle(roof, outline=OUTLINE)
    if v == 0:
        banded_chimney(b, look, 56, 30, 56, c("#d8d8d0"), c("#4f7a9a"), SMOKE)
    else:
        for x in (54, 59):
            banded_chimney(b, look, x, 26, 52, c("#d8d8d0"), c("#3f8a5a"), SMOKE)
    tx, ty = b.ground(8, 58)
    d.rectangle([tx, ty - 6, tx + 14, ty], c("#e8e4da"), OUTLINE)
    d.rectangle([tx + 10, ty - 8, tx + 14, ty - 4], c("#3f6fa8"), OUTLINE)
    transformer(b, look, 40, 58)
    if v == 1:
        for k in range(2):
            tx, ty = b.ground(26 + k * 9, 56)
            b.d.rectangle([tx, ty - 6, tx + 7, ty], c("#e8e4da"), OUTLINE)
            b.d.rectangle([tx, ty - 8, tx + 7, ty - 6], c("#3f6fa8"), OUTLINE)
    return b


def materials_recovery(look, v):
    """A materials recovery facility of the 1990s on 2 by 2 tiles: a big blue shed where the mixed recycling's sorted on
    conveyors, bales stacked in rows outside and a loading bay."""
    b = Building(2, 2, height=3 * STOREY)
    d = b.d
    plant_yard(b, look, 1, 1, 62, 62, c("#9c988e"))
    roof, wall = b.box(4, 4, 60, 34, 3 * STOREY - 2)
    col = c("#3f6fa8") if v == 0 else c("#4a7a5a")
    d.rectangle(wall, col)
    for xx in range(wall[0] + 2, wall[2], 3):
        d.line([xx, wall[1] + 1, xx, wall[3] - 1], shade(col, 0.88))
    for k in range(3):
        dx = wall[0] + 6 + k * 18
        d.rectangle([dx, wall[3] - 9, dx + 10, wall[3]], c("#6a7076"))
    d.rectangle(wall, outline=OUTLINE)
    d.rectangle(roof, SNOW_ROOF[0] if look == "snow" else shade(col, 1.2), OUTLINE)
    for row, bc in enumerate([c("#8aa4c8"), c("#b8bcc0"), c("#5a9a6a"), c("#c89a5a")]):
        for k in range(6):
            bx, by = b.ground(6 + k * 9, 40 + row * 6)
            d.rectangle([bx, by - 4, bx + 7, by + 1], SNOW_ROOF[1] if look == "snow" and row == 0 else bc, OUTLINE)
    if v == 1:
        for k, bc in enumerate([c("#8aa4c8"), c("#b8bcc0"), c("#c89a5a")]):
            bx, by = b.ground(6 + k * 18, 40)
            b.d.rectangle([bx - 1, by - 1, bx + 15, by + 21], c("#9c988e"))
            b.d.line([bx + 16, by, bx + 16, by + 21], c("#6a6a70"), 2)
            b.d.ellipse([bx, by + 4, bx + 14, by + 20], bc, OUTLINE)
        lx, ly = b.ground(52, 50)
        b.d.rectangle([lx, ly, lx + 6, ly + 4], c("#d8a030"), OUTLINE)
    return b


def advanced_sorting(look, v):
    """An advanced sorting plant of the 2020s on 2 by 2 tiles: a clean white hall with solar panels on its roof, where
    optical sorters and robots pick the recycling, and tidy bays of sorted bales."""
    b = Building(2, 2, height=3 * STOREY)
    d = b.d
    plant_yard(b, look, 1, 1, 62, 62, c("#aaa69c"))
    roof, wall = b.box(4, 4, 60, 36, 3 * STOREY - 2)
    d.rectangle(wall, c("#eceae4"))
    d.rectangle([wall[0], wall[3] - 3, wall[2], wall[3]], c("#3f8a5a"))
    d.rectangle([wall[0] + 4, wall[1] + 3, wall[2] - 4, wall[1] + 6], GLASS_NEW)
    d.rectangle(wall, outline=OUTLINE)
    rx0, ry0, rx1, ry1 = roof
    d.rectangle(roof, SNOW_ROOF[0] if look == "snow" else c("#d8d8d0"), OUTLINE)
    solar_rows(d, rx0 + 3, ry0 + 3, rx1 - 3, ry1 - 3, look)
    for k, bc in enumerate([c("#8aa4c8"), c("#b8bcc0"), c("#5a9a6a"), c("#c89a5a"), c("#d8d0b8"), c("#8a6aa0")]):
        bx0, by0 = b.ground(4 + k * 10, 44)
        d.rectangle([bx0, by0, bx0 + 8, by0 + 14], c("#c8c4b8"), OUTLINE)
        d.rectangle([bx0 + 1, by0 + 6, bx0 + 7, by0 + 13], bc)
    return b


def transfer_station(look, v):
    """A transfer station on 2 by 1 tiles: a tipping shed the town's trucks back into, and the long trailers that take it on
    to the dump."""
    b = Building(2, 1, height=2 * STOREY + 4)
    d = b.d
    plant_yard(b, look, 1, 1, 62, 30, c("#98948a"))
    roof, wall = b.box(4, 4, 40, 22, 2 * STOREY + 2)
    col = c("#8a7a62") if v == 0 else c("#6a7a82")
    d.rectangle(wall, col)
    for k in range(3):
        dx = wall[0] + 3 + k * 12
        d.rectangle([dx, wall[3] - 8, dx + 8, wall[3]], c("#4a4c50"))
    d.rectangle(wall, outline=OUTLINE)
    d.rectangle(roof, SNOW_ROOF[0] if look == "snow" else shade(col, 1.15), OUTLINE)
    for k in range(2 if v == 0 else 1):
        tx, ty = b.ground(44, 10 + k * 10)
        d.rectangle([tx, ty - 5, tx + 16, ty], c("#b8bcc0"), OUTLINE)
    if v == 1:
        tx, ty = b.ground(42, 28)
        d.rectangle([tx, ty - 4, tx + 18, ty], c("#2f7a5a"), OUTLINE)
        d.rectangle([tx + 13, ty - 7, tx + 16, ty - 3], c("#c0392b"), OUTLINE)
    return b


def compost_yard(look, v):
    """A compost yard on 2 by 2 tiles: long steaming windrows of garden and food waste on a pad, a turner working one, and a
    screened heap of finished compost."""
    b = Building(2, 2, height=8)
    d = b.d
    rng = random.Random(18200 + v)
    plant_yard(b, look, 1, 1, 62, 62, c("#8a7a62"))
    for row in range(5):
        if v == 0:
            y = 6 + row * 10
            px0, py0 = b.ground(6, y)
            px1, py1 = b.ground(44, y + 5)
            d.ellipse([px0, py0, px1, py1], c("#4a3a2a") if look != "snow" else SNOW_ROOF[1])
            d.line([px0 + 4, py0 + 1, px1 - 4, py0 + 1], c("#6a5a3a") if look != "snow" else SNOW_ROOF[0])
        else:
            x = 6 + row * 8
            px0, py0 = b.ground(x, 6)
            px1, py1 = b.ground(x + 5, 52)
            d.ellipse([px0, py0, px1, py1], c("#4a3a2a") if look != "snow" else SNOW_ROOF[1])
            d.line([px0 + 1, py0 + 4, px0 + 1, py1 - 4], c("#6a5a3a") if look != "snow" else SNOW_ROOF[0])
    gx, gy = b.ground(54, 30)
    for k, col in enumerate([c("#3a2a1e"), c("#4a3826"), c("#5a4630")]):
        d.ellipse([gx - 8 + k * 2, gy - 4 - k * 2, gx + 8 - k * 2, gy + 2 - k * 2], SNOW_ROOF[k] if look == "snow" else col)
    tx, ty = b.ground(20, 58)
    d.rectangle([tx, ty - 4, tx + 10, ty], c("#3f8a5a"), OUTLINE)
    if look in ("bare", "snow", "autumn"):
        steam = Image.new("RGBA", b.img.size, (0, 0, 0, 0))
        sd = ImageDraw.Draw(steam)
        for _ in range(5):
            x, y = b.ground(rng.randrange(10, 40), rng.randrange(6, 50))
            sd.ellipse([x - 3, y - 6, x + 3, y - 1], (240, 242, 244, 150))
        b.img.alpha_composite(steam)
    return b


def landfill_gas(look, v):
    """A landfill gas plant on one tile: a container of engines that burn the gas drawn off the fill, a flare stack beside it
    and the pipes coming in."""
    b = Building(height=20)
    d = b.d
    plant_yard(b, look, 1, 1, 30, 30, c("#a29e94"))
    d.line([0, b.lift + 6, 10, b.lift + 6], c("#e8a33a"), 2)
    d.line([0, b.lift + 26, 10, b.lift + 26], c("#e8a33a"), 2)
    roof, wall = b.box(6, 8, 24, 20, 7)
    d.rectangle(wall, c("#3f8a5a") if v == 0 else c("#d8d4ca"))
    for yy in range(wall[1] + 2, wall[3], 2):
        d.line([wall[0] + 2, yy, wall[2] - 2, yy], shade(c("#3f8a5a"), 0.85))
    d.rectangle(wall, outline=OUTLINE)
    d.rectangle(roof, c("#b8bec4"), OUTLINE)
    chimney(b, 26, 6, 20, c("#c4c8cc"), look, SMOKE)
    fx, fy = b.ground(26, 6)
    d.point((fx, fy - 21), c("#f2a03a"))
    if v == 1:
        roof, wall = b.box(4, 22, 16, 30, 5)
        b.d.rectangle(wall, c("#3f8a5a"))
        b.d.rectangle(wall, outline=OUTLINE)
        b.d.rectangle(roof, c("#b8bec4"), OUTLINE)
    return b


def biogas(look, v):
    """A biogas plant on 2 by 2 tiles: two round digesters under green domes, a gas holder, and a small engine house that
    turns the gas into power."""
    b = Building(2, 2, height=3 * STOREY)
    d = b.d
    plant_yard(b, look, 1, 1, 62, 62, c("#a8a49a"))
    for (x, y) in ((16, 18), (40, 18)):
        cylinder(b, look, x, y, 11, 10, c("#c8c4b8"), c("#4a8a5a") if look != "snow" else None)
    gx, gy = b.ground(16, 48)
    d.ellipse([gx - 10, gy - 10, gx + 10, gy + 4], SNOW_ROOF[0] if look == "snow" else c("#e6e2d8"), OUTLINE)
    roof, wall = b.box(36, 40, 58, 56, STOREY + 4)
    d.rectangle(wall, c("#d8d4ca"))
    d.rectangle(wall, outline=OUTLINE)
    flat_roof(b.img, roof, look, random.Random(18300 + v), [("vent", 4, 3)], parapet=c("#a8a296"))
    return b


BUILDINGS += [
    ("pulverized_coal", pulverized_coal, 2), ("supercritical_coal", supercritical_coal, 2), ("large_oil", large_oil, 2),
    ("combined_cycle", combined_cycle, 2), ("hydro_station", hydro_station, 2), ("advanced_reactor", advanced_reactor, 2),
    ("tall_wind", tall_wind, 2), ("bifacial_solar", bifacial_solar, 2), ("floating_offshore", floating_offshore, 2),
    ("tidal_array", tidal_array, 1), ("hydro_dam", hydro_dam, 1), ("pumped_storage", pumped_storage, 2),
    ("geothermal", geothermal, 2), ("small_reactor", small_reactor, 2), ("long_storage", long_storage, 1),
    ("sanitary_landfill", sanitary_landfill, 2), ("waste_to_energy", waste_to_energy, 2), ("materials_recovery", materials_recovery, 2),
    ("advanced_sorting", advanced_sorting, 1), ("transfer_station", transfer_station, 2), ("compost_yard", compost_yard, 2),
    ("landfill_gas", landfill_gas, 2), ("biogas", biogas, 1),
]


# High-voltage lines: a steel lattice pylon, taller, with three wires a side.
PYLON = c("#7d848a")
HV_WIRE = (40, 42, 46, 160)
PYLON_HEIGHT = 22


def hv_line(look, mask):
    """North 1, east 2, south 4, west 8, like a power line."""
    lift = lift_for(PYLON_HEIGHT + T // 2)
    img = Image.new("RGBA", (T, T + lift), (0, 0, 0, 0))
    wires = Image.new("RGBA", img.size, (0, 0, 0, 0))
    wd = ImageDraw.Draw(wires)
    cx, foot = 15, 16 + lift
    top = foot - PYLON_HEIGHT
    half = T // 2 + 1
    for gap in (-5, 0, 5):
        for side, bit in ((1, 2), (-1, 8)):
            if not mask & bit:
                continue
            pts = []
            for k in range(half + 1):
                u = k / T
                pts.append((cx + side * k, top + 2 + gap + round(4 * SAG * u * (1 - u))))
            wd.line(pts, HV_WIRE)
    for dx in (-4, 0, 4):
        if mask & 1: wd.line([cx + dx, top + 2, cx + dx, top + 2 - half], HV_WIRE)
        if mask & 4: wd.line([cx + dx, top + 2, cx + dx, top + 2 + half], HV_WIRE)
    img.alpha_composite(wires)
    d = ImageDraw.Draw(img)
    # The lattice: legs splayed at the foot, narrowing up, braced across.
    d.line([cx - 3, foot, cx - 1, top], PYLON)
    d.line([cx + 3, foot, cx + 1, top], shade(PYLON, 0.75))
    for k in range(3, PYLON_HEIGHT, 4):
        w = 3 - 2 * k / PYLON_HEIGHT
        d.line([cx - w, foot - k, cx + w, foot - k + 3], PYLON)
    if mask & 10:
        d.line([cx, top - 3, cx, top + 7], PYLON)
        for yy in (top - 3, top + 2, top + 7):
            d.point((cx + 1, yy), INSULATOR)
    else:
        d.line([cx - 5, top + 2, cx + 5, top + 2], PYLON)
        for xx in (cx - 4, cx, cx + 4):
            d.point((xx, top + 3), INSULATOR)
    if look == "snow":
        d.point((cx, top - 1), SNOW)
    return img, lift, [(0, cx, foot - lift, PYLON_HEIGHT, 2, 0)]


def span(height, base, gaps, wire, cx, across):
    """Wires over a tile with no pole of their own, as over track: joined to
    the poles either side at their height, sagging a little between. [across]
    is east to west; otherwise north to south."""
    lift = lift_for(height + T // 2)
    img = Image.new("RGBA", (T, T + lift), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    top = 16 + lift - height + base
    for gap in gaps:
        if across:
            pts = [(x, top + gap + 3 + round(2 * math.sin(math.pi * x / T))) for x in range(T + 1)]
            d.line(pts, wire)
        else:
            d.line([cx + gap, top - T // 2, cx + gap, top + T // 2], wire)
    return img, lift


def street_trees(look):
    """Small trees along the kerbs, one at each corner of the tile."""
    img = Image.new("RGBA", (T, SPRITE_H), (0, 0, 0, 0))
    rng = random.Random(9800)
    casters = []
    for k, (x, y) in enumerate([(5, 6), (26, 6), (5, 29), (26, 29)]):
        casters.append(deciduous(img, look, 1, x, LIFT + y, 5, rng))
    return img, casters


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
    for name, draw in ROAD_ART:
        for mask in range(16):
            out.append((f"{name}_{mask}", draw(look, mask), 0, []))
    for h in range(1, 5):
        out.append((f"arrow_{h}", arrow(h), 0, []))
    out.append(("overpass_ns", overpass(look, True), 0, []))
    out.append(("overpass_ew", overpass(look, False), 0, []))
    m = median(look)
    out += [("median_n", m, 0, []), ("median_e", m.rotate(-90), 0, []), ("median_s", m.rotate(180), 0, []), ("median_w", m.rotate(90), 0, [])]
    for material in ("wood", "stone", "steel"):
        out.append((f"bridge_{material}_ns", bridge(material, True), 0, []))
        out.append((f"bridge_{material}_ew", bridge(material, False), 0, []))
    for material in ("wood", "stone", "truss", "girder", "cable"):
        out.append((f"rails_{material}_ns", rails(material, True), 0, []))
        out.append((f"rails_{material}_ew", rails(material, False), 0, []))
    for kind in ("tower", "pylon", "pivot", "lift"):
        out.append((f"pier_{kind}_ns", pier(kind, True), 0, []))
        out.append((f"pier_{kind}_ew", pier(kind, False), 0, []))
    for rail in (0, 1):
        for h in range(1, 5):
            out.append((f"portal_{rail}_{h}", portal(look, rail, h), 0, []))
    for mask in range(16):
        out.append((f"track_{mask}", track(look, mask), 0, []))
    out += [("crossing_ns", crossing(look, True), 0, []), ("crossing_ew", crossing(look, False), 0, [])]
    out += [("trestle_ns", trestle(True), 0, []), ("trestle_ew", trestle(False), 0, [])]
    def round_casters(cs):
        return [(0, fx, fy - LIFT, h, r, 0) for fx, fy, h, r in cs]
    for v in range(5):
        img, casters = tree(look, v)
        out.append((f"tree_{v}", img, LIFT, round_casters(casters)))
    for v in range(3):
        img, casters = forest(look, v)
        out.append((f"forest_{v}", img, LIFT, round_casters(casters)))
    for name, draw, variants in BUILDINGS:
        for v in range(variants):
            b = draw(look, v)
            out.append((f"{name}_{v}", b.img, b.lift, b.casters))
    for mask in range(16):
        img, lift, casters = power_line(look, mask)
        out.append((f"power_line_{mask}", img, lift, casters))
    for mask in range(16):
        img, lift, casters = hv_line(look, mask)
        out.append((f"hv_line_{mask}", img, lift, casters))
    # Spans: north to south (0) and east to west (1), over track where no pole can stand.
    for k in range(2):
        img, lift = span(POLE_HEIGHT, 0, (-3, 3) if k else (1,), WIRE, 15, k == 1)
        out.append((f"power_span_{k}", img, lift, []))
    for k in range(2):
        img, lift = span(PYLON_HEIGHT, 2, (-5, 0, 5) if k else (-4, 0, 4), HV_WIRE, 15, k == 1)
        out.append((f"hv_span_{k}", img, lift, []))
    for k in range(2):
        img, lift = phone_span(k == 1, False)
        out.append((f"copper_span_{k}", img, lift, []))
    for k in range(2):
        img, lift = phone_span(k == 1, True)
        out.append((f"fibre_span_{k}", img, lift, []))
    for mask in range(16):
        img, lift, casters = phone_line(look, mask, False)
        out.append((f"copper_line_{mask}", img, lift, casters))
    for mask in range(16):
        img, lift, casters = phone_line(look, mask, True)
        out.append((f"fibre_line_{mask}", img, lift, casters))
    img, casters = street_trees(look)
    out.append(("street_trees_0", img, LIFT, round_casters(casters)))
    out.append(("seam_0", seam(look, "ore"), 0, []))
    out.append(("seam_1", seam(look, "coal"), 0, []))
    out.append(("seam_2", seam(look, "oil"), 0, []))
    for k, kind in enumerate(("stop", "lights", "overpass")):
        out.append((f"junction_{k}", junction(look, kind), 0, []))
    for mask in range(16):
        out.append((f"roundabout_{mask}", roundabout(look, mask), 0, []))
    sign = for_sale(look)
    out.append(("for_sale_0", sign.img, sign.lift, []))
    for mask in range(16):
        out.append((f"tramway_{mask}", tramway(look, mask), 0, []))
    for mask in range(16):
        out.append((f"trolley_wire_{mask}", trolley_wire(look, mask), 0, []))
    out.append(("tram_stop_0", tram_stop(look), 0, []))
    out.append(("bus_stop_0", bus_stop(look), 0, []))
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
    # A sheet for each look, so the game only holds the looks it's showing. Every
    # look has the same sprites at the same sizes, so they all sit in the same places.
    sizes = [s[1].size for s in looks[0]]
    for look in looks:
        assert [s[1].size for s in look] == sizes, "the looks differ in size"
    pos, size = pack(sizes)
    OUT_PNG.mkdir(parents=True, exist_ok=True)
    for old in OUT_PNG.glob("atlas_*.png"):
        old.unlink()
    for name, look in zip(LOOKS, looks):
        atlas = Image.new("RGBA", size, (0, 0, 0, 0))
        for (_, img, _, _), p in zip(look, pos):
            atlas.paste(img, p)
        atlas.save(OUT_PNG / f"atlas_32_{name}.png", optimize=True)
        # Averaged down with alpha taken into account, so edges don't go dark.
        premul = atlas.convert("RGBa")
        premul.reduce(2).convert("RGBA").save(OUT_PNG / f"atlas_16_{name}.png", optimize=True)
        premul.reduce(4).convert("RGBA").save(OUT_PNG / f"atlas_8_{name}.png", optimize=True)
    write_kotlin(names, flat, pos * len(LOOKS), size)
    print(f"{len(flat)} sprites, {len(names)} a look, {len(LOOKS)} sheets of {size[0]}x{size[1]}")


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
        starts.append(len(casters) // 6)
        counts.append(len(cs))
        for caster in cs:
            casters += [round(v) for v in caster]
    plume_starts, plume_counts, plumes = [], [], []
    for name, img, lift, cs in flat[:len(names)]:
        ps = img.info.get("plumes", [])
        plume_starts.append(len(plumes) // 3)
        plume_counts.append(len(ps))
        for kind, x, y in ps:
            plumes += [kind, round(x), round(y)]

    def ints(values, per_line=40):
        # As text, read once at start-up: a class's start-up code can't hold thousands of numbers.
        # Each string constant stays well under the class file's limit for one.
        text = ",".join(str(v) for v in values)
        lines = []
        for i in range(0, len(text), 2000):
            lines.append('            "' + text[i:i + 2000] + '"')
        return "decode(\n" + " +\n".join(lines or ['            ""']) + ",\n        )"

    groups = ["grass", "water", "shore", "corner"] + [r[0] for r in ROAD_ART] + ["arrow", "overpass", "median", "bridge", "rails", "pier", "portal", "track", "crossing", "trestle", "tree", "forest"] + [b[0] for b in BUILDINGS] + ["power_line", "hv_line", "copper_line", "fibre_line", "power_span", "hv_span", "copper_span", "fibre_span", "street_trees", "seam", "junction", "roundabout", "for_sale", "tramway", "trolley_wire", "tram_stop", "bus_stop"]
    # A sprite's name must start with exactly one group's, or the counts go wrong.
    for n in names:
        owners = [g for g in groups if n.startswith(g + "_")]
        assert len(owners) == 1, f"{n} belongs to {owners}"
    consts = []
    for g in groups:
        consts.append(f"    const val {g.upper()} = {first(g + '_')}")
        consts.append(f"    const val {g.upper()}_COUNT = {count(g + '_')}")
    look_consts = "\n".join(f"    const val {look.upper()} = {i}" for i, look in enumerate(LOOKS))
    text = f"""// Written by tools/gen_tiles.py. Change that and run it again rather than editing this.
package com.rm.infill.map

/**
 * Where each sprite is in the atlases (files/atlas_32_<look>.png, _16_ and _8_) and what
 * casts a shadow. A sprite's number is its place in a look plus the look times
 * [PER_LOOK]. Shores, corners and medians go north, east, south, west. A
 * road's number adds its neighbours that are road: north 1, east 2, south 4,
 * west 8. Arrows go north, east, south, west. Bridges and rails are wood
 * then stone, each north to south then east to west.
 */
internal object Atlas {{
    const val LOOKS = {len(LOOKS)}
    const val ROUND = 0
    const val BOX = 1
{look_consts}

    const val PER_LOOK = {len(names)}
{chr(10).join(consts)}

    const val WIDTH = {size[0]}
    const val HEIGHT = {size[1]}

    /** Five numbers a sprite, at 32 px: x and y in the atlas, width, height, and how far above its tile it starts. */
    val rects = {ints(rects)}

    /** For each sprite in a look, where its casters start in [casters], and how many. */
    val casterStart = {ints(starts)}
    val casterCount = {ints(counts)}

    /**
     * Six numbers a caster, at 32 px, relative to the top left of the sprite's
     * first tile. A round one (0) is a tree: foot x, foot y, height of its
     * middle, radius. A box (1) is a building: left, top, right, bottom, height.
     */
    val casters = {ints(casters)}

    /** For each sprite in a look, where its stacks start in [plumes], and how many. */
    val plumeStart = {ints(plume_starts)}
    val plumeCount = {ints(plume_counts)}

    /**
     * Three numbers a stack, at 32 px, relative to the top left of the sprite's
     * first tile: what comes out (0 soot, 1 smoke, 2 steam, 3 a cooling tower's
     * cloud), then the x and y of its top.
     */
    val plumes = {ints(plumes)}

    private fun decode(text: String): IntArray = if (text.isEmpty()) IntArray(0) else text.split(',').map {{ it.toInt() }}.toIntArray()
}}
"""
    OUT_KT.write_text(text)


if __name__ == "__main__":
    main()
