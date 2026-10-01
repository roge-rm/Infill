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


def road_shape(mask, lo, hi):
    """Whether a pixel is on a road that's [lo, hi] wide and joins the neighbours in [mask]."""
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
    """Lines along each way the road goes, at the offsets [at] across it."""
    vertical = mask & 5 or mask == 0
    horizontal = mask & 10
    for a in at:
        if vertical:
            top = 0 if mask & 1 else lo + 2
            bottom = T - 1 if mask & 4 else hi - 2
            for y in range(top, bottom + 1):
                if rng.random() < chance: px[a, y] = col
        if horizontal:
            left = 0 if mask & 8 else lo + 2
            right = T - 1 if mask & 2 else hi - 2
            for x in range(left, right + 1):
                if rng.random() < chance: px[x, a] = col


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
    """The railings along both sides of a bridge, drawn over the road."""
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


ROAD_ART = [("road_dirt", road), ("road_gravel", gravel_road), ("road_lane", lane), ("road_street", street), ("road_avenue", avenue)]


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


def flat_roof(img, roof, look, rng, features=(), parapet=PARAPET):
    """A flat roof of tarred gravel with a parapet, and what stands on it."""
    d = ImageDraw.Draw(img)
    x0, y0, x1, y1 = roof
    if look == "snow":
        d.rectangle(roof, SNOW_ROOF[0])
        # Drifts in the lee of the north parapet.
        d.line([x0 + 1, y0 + 2, x1 - 1, y0 + 2], SNOW_ROOF[2])
        d.line([x0 + 2, y0 + 3, x1 - 2, y0 + 3], SNOW_ROOF[1])
        d.rectangle([x0 + 1, y0 + 1, x1 - 1, y1 - 1], outline=c("#aab4bd"))
    else:
        noise_fill(img, (x0, y0, x1 + 1, y1 + 1), TAR_ROOF, rng, (0.7, 0.18, 0.12))
        d.rectangle([x0 + 1, y0 + 1, x1 - 1, y1 - 1], outline=parapet)
    for f in features:
        roof_feature(d, roof, f, look)
    d.rectangle(roof, outline=OUTLINE)


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


def chimney(b, x, y, height, col=BRICK, look="summer"):
    """A tall chimney standing at tile pixel x, y."""
    top = b.lift
    d = b.d
    d.rectangle([x - 2, y + top - height, x + 1, y + top], col)
    d.line([x - 2, y + top - height, x - 2, y + top], shade(col, 1.2))
    d.line([x + 1, y + top - height, x + 1, y + top], shade(col, 0.75))
    d.rectangle([x - 2, y + top - height, x + 1, y + top - height + 1], SNOW if look == "snow" else shade(col, 0.5))
    b.casters.append((1, x - 2, y - 1, x + 2, y + 1, height))


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


def cottage(look, v):
    b = Building(height=STOREY + 12)
    shapes = [(False, ("porch", "stack")), (True, ("stack",)), (False, ("dormers",)), (True, ("porch",))]
    ns, extras = shapes[v]
    return house_shape(b, look, v, (7, 9, 24, 24), 1, SIDING[v % 4], SHINGLE[v % 3], ns, extras)


def house(look, v):
    b = Building(height=2 * STOREY + 10)
    shapes = [(False, ("porch", "stack")), (True, ("bay_left",)), (False, ("dormers", "stack")), (True, ("porch", "stack"))]
    ns, extras = shapes[v]
    return house_shape(b, look, v, (5, 7, 26, 24), 2, SIDING[(v + 1) % 4], SHINGLE[(v + 1) % 3], ns, extras)


def large_house(look, v):
    b = Building(height=2 * STOREY + 12)
    shapes = [(False, ("porch", "bay_left", "stack")), (True, ("porch", "stack")), (False, ("dormers", "bay_right", "stack"))]
    ns, extras = shapes[v]
    brick_walls = v == 2
    wall_col = BRICK20 if brick_walls else SIDING[(v + 2) % 4]
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
    brick(b.d, wall, c("#7a3f36"))
    x0, y0, x1, y1 = wall
    b.d.rectangle([x0, y0 + STOREY + 1, x1, y0 + STOREY + 2], STONE)
    windows(b.d, wall, 2, sill=TRIM, every=5, skip_door=True)
    cx = (x0 + x1) // 2
    b.d.rectangle([cx - 2, y1 - 5, cx + 2, y1], c("#2a2a30"))
    b.d.rectangle([cx - 2, y1 - 6, cx + 2, y1 - 6], STONE)
    b.d.rectangle([cx + 4, y1 - 6, cx + 5, y1 - 4], c("#3f6fd8"))
    b.d.point((cx + 4, y1 - 6), c("#a8c4ff"))
    b.d.rectangle(wall, outline=OUTLINE)
    flat_roof(b.img, roof, look, random.Random(7500), [("stack", 6, 3), ("stack", 48, 3), ("hatch", 26, 8)], parapet=STONE)
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
    brick(d, wall, c("#9a3e30"))
    x0, y0, x1, y1 = wall
    windows(d, (x0, y0, x1, y0 + STOREY), 1, sill=TRIM, every=5)
    for dx in (6, 24):
        d.rectangle([x0 + dx, y1 - 9, x0 + dx + 11, y1], c("#c0392b"))
        d.line([x0 + dx + 5, y1 - 9, x0 + dx + 5, y1], c("#8e2a20"))
        d.rectangle([x0 + dx, y1 - 10, x0 + dx + 11, y1 - 10], STONE)
    d.rectangle(wall, outline=OUTLINE)
    flat_roof(b.img, roof, look, random.Random(7600), [("stack", 4, 3), ("vent", 20, 12)], parapet=STONE)
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
    lawn = GRASS[look] if look != "summer" else [c("#6aae4a"), c("#5f9f42"), c("#78bc56")]
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
            if look in ("spring", "summer"):
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
    brick(d, wall, c("#9a5a42"))
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
    """A fenced field with four little pump houses over the wells."""
    b = Building(2, 2, height=10)
    d = b.d
    gx0, gy0 = b.ground(2, 2)
    gx1, gy1 = b.ground(61, 61)
    d.rectangle([gx0, gy0, gx1, gy1], outline=c("#8a7a64"))
    for xx in range(gx0, gx1 + 1, 4):
        d.point((xx, gy0), c("#6b5a44"))
        d.point((xx, gy1), c("#6b5a44"))
    for (hx, hy) in ((12, 14), (40, 12), (16, 42), (44, 44)):
        roof, wall = b.box(hx, hy, hx + 8, hy + 7, STOREY)
        siding(d, wall, PAINT[v % len(PAINT)])
        door(d, wall)
        d.rectangle(wall, outline=OUTLINE)
        gable_ew(d, roof, SHINGLE[1], look)
    return b


def water_tower(look, v):
    """A timber tank on steel legs, standing high over its tile."""
    b = Building(1, 1, height=40)
    d = b.d
    top = b.lift
    # Legs from the ground up to the tank.
    for lx in (8, 23):
        d.line([lx, 26 + top, lx + 2, top - 24], STEEL_LEG)
    d.line([8, 26 + top - 16, 23, 26 + top - 30], STEEL_LEG)
    d.line([23, 26 + top - 16, 8, 26 + top - 30], STEEL_LEG)
    # The tank: a drum with bands, and its conical roof.
    tx0, tx1 = 5, 26
    ty0, ty1 = top - 38, top - 24
    d.rectangle([tx0, ty0, tx1, ty1], TANK, OUTLINE)
    for yy in range(ty0 + 3, ty1, 4):
        d.line([tx0 + 1, yy, tx1 - 1, yy], TANK_BAND)
    d.polygon([(tx0 - 1, ty0), ((tx0 + tx1) // 2, ty0 - 6), (tx1 + 1, ty0)], SNOW_ROOF[0] if look == "snow" else c("#5b5f6b"), OUTLINE)
    b.casters.append((1, 7, 10, 25, 22, 40))
    return b


def outfall(look, v, stain=True):
    """A stone headwall at the bank with a pipe's dark mouth, and the stain it leaves."""
    b = Building(1, 1, height=4)
    d = b.d
    roof, wall = b.box(8, 12, 23, 20, 4)
    d.rectangle(wall, CONCRETE if not stain else STONE)
    d.rectangle(roof, SNOW_ROOF[0] if look == "snow" else shade(CONCRETE, 1.1))
    d.ellipse([12, wall[1] + 1, 19, wall[3] + 1], PIPE_MOUTH)
    d.rectangle(wall, outline=OUTLINE)
    if stain:
        gx, gy = b.ground(15, 24)
        d.ellipse([gx - 8, gy - 3, gx + 8, gy + 5], STAIN)
    return b


def sewer_outfall(look, v):
    return outfall(look, v, stain=True)


def storm_outfall(look, v):
    return outfall(look, v, stain=False)


def storm_pond(look, v):
    """A pond on 2 by 2 tiles with grassy banks, reeds and a concrete inlet."""
    b = Building(2, 2, height=4)
    d = b.d
    rng = random.Random(7800)
    gx0, gy0 = b.ground(0, 0)
    ice = look == "snow"
    cols = [c("#c9d8e2"), c("#d7e2ea"), c("#bccbd6")] if ice else POND
    for y in range(64):
        for x in range(64):
            dx, dy = (x - 31.5) / 27, (y - 31.5) / 24
            if dx * dx + dy * dy <= 1:
                d.point((gx0 + x, gy0 + y), rng.choice(cols))
    if not ice:
        for _ in range(40):
            a = rng.random() * math.tau
            x = 31.5 + math.cos(a) * 26 * (0.9 + rng.random() * 0.15)
            y = 31.5 + math.sin(a) * 23 * (0.9 + rng.random() * 0.15)
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
    """A two storey schoolhouse on 2 by 2 tiles, brick or white boards, a bell on the roof and a yard to the south."""
    b = Building(2, 2, height=2 * STOREY + 18)
    d = b.d
    schoolyard(b, look, 4, 42, 59, 61)
    roof, wall = b.box(6, 10, 57, 38, 2 * STOREY + 2)
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
    bell_cupola(b, look, (6 + 57) // 2, 24, 2 * STOREY + 8)
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
    flat_roof(b.img, roof, look, random.Random(7800 + v), [("tank", 16, 9), ("stack", 3, 3), ("hatch", 6, 14)], parapet=CORNICE)
    return b


def apartment_court(look, v):
    """A block of flats round a courtyard on 2 by 2 tiles, open to the street through an arch on the south side."""
    b = Building(2, 2, height=5 * STOREY + 8)
    d = b.d
    rng = random.Random(7900 + v)
    col = [c("#a8583f"), c("#c9b48a")][v]
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
        brick(d, wall, col) if v == 0 else d.rectangle(wall, col)
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
    return b


def department_store(look, v):
    """Five storeys of pale stone on 2 by 2 tiles: display windows and awnings along the street, a clock at the corner, a flag on the roof."""
    b = Building(2, 2, height=5 * STOREY + 16)
    d = b.d
    col = [c("#ddd5c2"), c("#cfa98a")][v]
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
    return b


def works(look, v):
    """Works on 2 by 2 tiles: long sawtooth sheds, a boiler house with a tall chimney, a yard of coal and goods."""
    b = Building(2, 2, height=44)
    d = b.d
    col = [BRICKS[1], BRICKS[3]][v]
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
        # Rails out across the apron.
        for rx in (cx - 3, cx + 3):
            d.line([rx, y1 + 1, rx, gy1], TRAM_RAIL)
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
    # A bus on the forecourt.
    bx, by = b.ground(10, 48)
    d.rectangle([bx, by, bx + 22, by + 8], c("#2f7a5a") if v == 0 else c("#c0392b"), OUTLINE)
    d.rectangle([bx + 2, by + 2, bx + 20, by + 4], c("#d8e4ea"))
    return b


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


def banded_chimney(b, look, x, y, height, col, band):
    """A tall stack with bands near the top."""
    chimney(b, x, y, height, col, look)
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
    banded_chimney(b, look, 54, 40, 50, c("#b8b2a6"), c("#b04030"))
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
        chimney(b, x, 22, 40, c("#c4c8cc"), look)
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
    """A hyperbolic cooling tower: wide at the foot, waisted, flaring at the lip, with steam over the top."""
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
    # Steam: soft white puffs above the lip.
    steam = Image.new("RGBA", b.img.size, (0, 0, 0, 0))
    sd = ImageDraw.Draw(steam)
    for k, (dx, dy, rr) in enumerate([(-3, -4, 7), (3, -8, 8), (-1, -13, 6)]):
        sd.ellipse([gx + dx - rr, gy - h + dy - rr, gx + dx + rr, gy - h + dy + rr], (246, 248, 250, 225))
    b.img.alpha_composite(steam)
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


def substation(look, v):
    """A fenced yard of gravel with two transformers and a steel gantry the lines come in on."""
    b = Building(height=16)
    d = b.d
    gx0, gy0 = b.ground(2, 2)
    gx1, gy1 = b.ground(29, 29)
    rng = random.Random(9500)
    noise_fill(b.img, (gx0, gy0, gx1 + 1, gy1 + 1), [c("#9a968c"), c("#8c887e"), c("#a8a49a")] if look != "snow"
               else [c("#e4ebf0"), c("#d5dfe6"), c("#f2f6f9")], rng)
    d.rectangle([gx0, gy0, gx1, gy1], outline=c("#6a6e72"))
    for xx in range(gx0, gx1 + 1, 3):
        d.point((xx, gy1 - 1), c("#6a6e72"))
    # The gantry: two posts and a beam across the back.
    for x in (6, 25):
        gx, gy = b.ground(x, 10)
        d.line([gx, gy, gx, gy - 14], STEEL_LEG)
    gx, gy = b.ground(6, 10)
    d.line([gx, gy - 14, gx + 19, gy - 14], STEEL_LEG)
    for x in (10, 15, 20):
        d.point((gx + x - 6, gy - 13), INSULATOR)
    b.casters.append((1, 5, 9, 27, 11, 14))
    transformer(b, look, 6, 24)
    transformer(b, look, 18, 24)
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
    # The mounds, back to front.
    mounds = [(28, 26, 20), (66, 30, 22), (40, 58, 18), (74, 66, 14)]
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
    bx, by = b.ground(52, 80)
    d.rectangle([bx, by - 5, bx + 9, by], c("#d8a030"), OUTLINE)
    d.rectangle([bx + 2, by - 9, bx + 6, by - 5], c("#c89020"), OUTLINE)
    d.line([bx - 2, by - 4, bx - 2, by], c("#3a3c40"))
    # The shed by the gate.
    roof, wall = b.box(8, 78, 26, 92, STOREY + 2)
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
    banded_chimney(b, look, 54, 36, 54, c("#b4b0a8"), c("#8a8680"))
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
    "autumn": [c("#9a8048"), c("#b89a58")],
    "bare": [c("#7a5a3e"), c("#6a4c32")],
    "snow": [c("#e9eff3"), c("#cfd9e1")],
}


def farm(look, v):
    """A farm on 2 by 2 tiles: fields in rows, the way they run and the crop by the season, split by a lane, with the farmhouse and barn."""
    b = Building(2, 2, height=2 * STOREY + 6)
    d = b.d
    ground, crop = FIELD[look]
    gx0, gy0 = b.ground(1, 1)
    gx1, gy1 = b.ground(62, 62)
    hedge = c("#4f7a34") if look in ("spring", "summer") else c("#6b5a44")
    # Two or three fields, each its own way.
    fields = [((1, 1, 40, 30), True), ((1, 33, 40, 62), False), ((43, 33, 62, 62), True)] if v % 2 == 0 else \
        [((1, 1, 62, 22), False), ((1, 25, 30, 62), True), ((33, 25, 62, 62), False)]
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
    # The farmyard in the corner the fields leave: a house and a red barn.
    if v % 2 == 0:
        yx, yy, house, barn = 43, 1, (44, 4, 52, 12), (52, 16, 62, 28)
    else:
        yx, yy, house, barn = 1, 25, None, None
    if house:
        gx, gy = b.ground(yx, yy)
        d.rectangle([gx, gy, gx + 19, gy + 29], SNOW_GROUND if look == "snow" else c("#9a8a6a"))
        roof, wall = b.box(*house, STOREY + 2)
        siding(d, wall, SIDING[v % 4])
        windows(d, wall, 1, every=4)
        d.rectangle(wall, outline=OUTLINE)
        gable_ew(d, roof, SHINGLE[0], look)
        roof, wall = b.box(*barn, STOREY + 4)
        d.rectangle(wall, c("#9a3a2e"))
        d.rectangle([wall[0] + 3, wall[3] - 5, wall[0] + 7, wall[3]], c("#5a2a20"))
        d.rectangle(wall, outline=OUTLINE)
        gable_ew(d, roof, SHINGLE[1], look)
    else:
        roof, wall = b.box(4, 2, 14, 10, STOREY + 2)
        siding(d, wall, SIDING[v % 4])
        windows(d, wall, 1, every=4)
        d.rectangle(wall, outline=OUTLINE)
        gable_ew(d, roof, SHINGLE[0], look)
        roof, wall = b.box(18, 3, 30, 14, STOREY + 4)
        d.rectangle(wall, c("#9a3a2e"))
        d.rectangle([wall[0] + 3, wall[3] - 5, wall[0] + 7, wall[3]], c("#5a2a20"))
        d.rectangle(wall, outline=OUTLINE)
        gable_ew(d, roof, SHINGLE[1], look)
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
    b = Building(2, 2, height=40)
    d = b.d
    rng = random.Random(9950 + v)
    gx0, gy0 = b.ground(1, 1)
    gx1, gy1 = b.ground(62, 62)
    noise_fill(b.img, (gx0, gy0, gx1 + 1, gy1 + 1), [c("#7d7260"), c("#6e6454"), c("#8a7e6a")] if look != "snow"
               else [c("#dfe5ea"), c("#cfd7de"), c("#eef2f5")], rng)
    d.rectangle([gx0, gy0, gx1, gy1], outline=c("#5a5048"))
    spoil(b, look, 44, 18, 18, spoil_cols)
    # The engine house, brick, with its chimney.
    roof, wall = b.box(4, 30, 26, 46, 2 * STOREY)
    brick(d, wall, c("#8a4a38"))
    windows(d, wall, 2, every=6)
    d.rectangle(wall, outline=OUTLINE)
    gable_ew(d, roof, SHINGLE[2], look)
    chimney(b, 8, 30, 32, look=look)
    headframe(b, look, 36, 40, 34)
    # A track out with wagons on it.
    tx0, ty = b.ground(4, 56)
    d.line([tx0, ty, tx0 + 54, ty], c("#5a5048"))
    d.line([tx0, ty + 3, tx0 + 54, ty + 3], c("#5a5048"))
    for k in range(2):
        wx = tx0 + 26 + k * 12
        d.rectangle([wx, ty - 4, wx + 9, ty + 2], wagon_col, OUTLINE)
    return b


def mine(look, v):
    """An iron mine on 2 by 2 tiles: headframe, engine house, a rust-red spoil heap and ore wagons."""
    return pit(look, v, [c("#7a4030"), c("#8a4a36"), c("#9a5a40"), c("#a86a4a")], c("#8a4a36"))


def colliery(look, v):
    """A coal mine on 2 by 2 tiles: headframe, engine house, a black spoil heap and coal wagons."""
    return pit(look, v, [c("#1e1e22"), c("#2a2a2f"), c("#35353b"), c("#42424a")], c("#2a2a2f"))


def seam(look, kind):
    """Stones showing through the grass where there's a seam underneath: rusty for ore, black for coal."""
    img = Image.new("RGBA", (T, T), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    rng = random.Random(9970 if kind == "ore" else 9980)
    cols = [c("#8a4a36"), c("#a05a40"), c("#6a3a2a")] if kind == "ore" else [c("#2a2a2f"), c("#3a3a40"), c("#1e1e22")]
    for _ in range(4 if look == "snow" else 9):
        x, y = rng.randrange(2, 29), rng.randrange(2, 29)
        r = rng.choice((1, 1, 2))
        d.ellipse([x - r, y - r, x + r, y + r // 2 + 1], rng.choice(cols))
    return img


BUILDINGS = [
    ("cottage", cottage, 4), ("house", house, 4), ("large_house", large_house, 3), ("tenement", tenement, 3),
    ("general_store", general_store, 6), ("shop", shop, 6), ("hotel", hotel, 4), ("bank", bank, 4),
    ("workshop", workshop, 6), ("mill", mill, 4), ("warehouse", warehouse, 5), ("factory", factory, 4),
    ("coal_plant", coal_plant, 1),
    ("police_station", police_station, 1), ("fire_station", fire_station, 1), ("park", park, 4),
    ("station_ew", station_ew, 4), ("station_ns", station_ns, 4), ("yard_ew", yard_ew, 4), ("yard_ns", yard_ns, 4),
    ("pumping_station", pumping_station, 1), ("well_field", well_field, 1), ("tower", water_tower, 1),
    ("sewer_outfall", sewer_outfall, 1), ("storm_pond", storm_pond, 1), ("storm_outfall", storm_outfall, 1),
    ("school", school, 2), ("high_school", high_school, 2), ("clinic", clinic, 2), ("hospital", hospital, 1),
    ("row_houses", row_houses, 3), ("apartments", apartments, 3), ("apartment_court", apartment_court, 2),
    ("main_street", main_street, 4), ("office_block", office_block, 3), ("department_store", department_store, 2),
    ("works", works, 2), ("site_small", site_small, 2), ("site_large", site_large, 2),
    ("sewage_works", sewage_works, 1), ("treatment_plant", treatment_plant, 1),
    ("tram_depot", tram_depot, 1), ("bus_garage", bus_garage, 2), ("subway_station", subway_station, 1),
    ("oil_plant", oil_plant, 1), ("gas_plant", gas_plant, 1), ("hydro_plant", hydro_plant, 1), ("nuclear_plant", nuclear_plant, 1),
    ("substation", substation, 1), ("dump", dump, 2), ("incinerator", incinerator, 1), ("recycling", recycling, 1),
    ("farm", farm, 2), ("woodlot", woodlot, 3), ("mine", mine, 1), ("colliery", colliery, 1),
]


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
    m = median(look)
    out += [("median_n", m, 0, []), ("median_e", m.rotate(-90), 0, []), ("median_s", m.rotate(180), 0, []), ("median_w", m.rotate(90), 0, [])]
    for material in ("wood", "stone"):
        out.append((f"bridge_{material}_ns", bridge(material, True), 0, []))
        out.append((f"bridge_{material}_ew", bridge(material, False), 0, []))
    for material in ("wood", "stone"):
        out.append((f"rails_{material}_ns", rails(material, True), 0, []))
        out.append((f"rails_{material}_ew", rails(material, False), 0, []))
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
    img, casters = street_trees(look)
    out.append(("street_trees_0", img, LIFT, round_casters(casters)))
    out.append(("seam_0", seam(look, "ore"), 0, []))
    out.append(("seam_1", seam(look, "coal"), 0, []))
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
        starts.append(len(casters) // 6)
        counts.append(len(cs))
        for caster in cs:
            casters += [round(v) for v in caster]

    def ints(values, per_line=40):
        # As text, read once at start-up: a class's start-up code can't hold thousands of numbers.
        # Each string constant stays well under the class file's limit for one.
        text = ",".join(str(v) for v in values)
        lines = []
        for i in range(0, len(text), 2000):
            lines.append('            "' + text[i:i + 2000] + '"')
        return "decode(\n" + " +\n".join(lines or ['            ""']) + ",\n        )"

    groups = ["grass", "water", "shore", "corner"] + [r[0] for r in ROAD_ART] + ["arrow", "median", "bridge", "rails", "track", "crossing", "trestle", "tree", "forest"] + [b[0] for b in BUILDINGS] + ["power_line", "hv_line", "street_trees", "seam", "for_sale", "tramway", "trolley_wire", "tram_stop", "bus_stop"]
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
 * Where each sprite is in the atlases (files/atlas_32.png, _16 and _8) and what
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

    private fun decode(text: String): IntArray = if (text.isEmpty()) IntArray(0) else text.split(',').map {{ it.toInt() }}.toIntArray()
}}
"""
    OUT_KT.write_text(text)


if __name__ == "__main__":
    main()
