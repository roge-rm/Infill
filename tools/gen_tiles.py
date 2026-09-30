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
# south 4, west 8. In 1900 roads are packed dirt with wheel ruts.

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


def road(look, mask):
    img = Image.new("RGBA", (T, T), (0, 0, 0, 0))
    px = img.load()
    rng = random.Random(6000 + mask)

    def inside(x, y):
        if not (0 <= x < T and 0 <= y < T):
            # Past the tile's edge the road carries on only where it's joined.
            if y < 0: return mask & 1 and ROAD_LO <= x <= ROAD_HI
            if x >= T: return mask & 2 and ROAD_LO <= y <= ROAD_HI
            if y >= T: return mask & 4 and ROAD_LO <= x <= ROAD_HI
            return mask & 8 and ROAD_LO <= y <= ROAD_HI
        across = ROAD_LO <= x <= ROAD_HI
        down = ROAD_LO <= y <= ROAD_HI
        if across and down:
            return True
        return (across and ((y < ROAD_LO and mask & 1) or (y > ROAD_HI and mask & 4))) or \
               (down and ((x > ROAD_HI and mask & 2) or (x < ROAD_LO and mask & 8)))

    cols = DIRT_ROAD[look]
    for y in range(T):
        for x in range(T):
            if not inside(x, y):
                continue
            edge = not (inside(x - 1, y) and inside(x + 1, y) and inside(x, y - 1) and inside(x, y + 1))
            r = rng.random()
            px[x, y] = ROAD_EDGE[look] if edge else cols[0] if r < 0.75 else cols[1] if r < 0.88 else cols[2]
    # Wheel ruts along each way the road goes, through the middle.
    vertical = mask & 5 or mask == 0
    horizontal = mask & 10
    for rut in RUTS:
        if vertical:
            top = 0 if mask & 1 else ROAD_LO + 2
            bottom = T - 1 if mask & 4 else ROAD_HI - 2
            for y in range(top, bottom + 1):
                if rng.random() < 0.85: px[rut, y] = RUT[look]
        if horizontal:
            left = 0 if mask & 8 else ROAD_LO + 2
            right = T - 1 if mask & 2 else ROAD_HI - 2
            for x in range(left, right + 1):
                if rng.random() < 0.85: px[x, rut] = RUT[look]
    return img



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


def general_store(look, v):
    b = Building(height=STOREY + 5)
    roof, wall = b.box(4, 10, 27, 25, STOREY)
    # The false front stands taller than the roof behind it.
    front = (wall[0], wall[1] - 5, wall[2], wall[3])
    siding(b.d, front, [TIMBER, c("#c9b48a")][v % 2])
    b.d.rectangle([front[0] + 3, front[1] + 1, front[2] - 3, front[1] + 4], TRIM)
    b.d.rectangle([front[0] + 3, front[3] - 6, front[2] - 3, front[3] - 2], c("#8fb3c9"))
    b.d.rectangle(front, outline=OUTLINE)
    gable_ew(b.d, (roof[0], roof[1], roof[2], front[1] - 1), IRON_ROOF, look)
    b.casters[-1] = (1, 4, 10, 28, 26, STOREY + 5)
    return b


def shop(look, v):
    b = Building(height=2 * STOREY + 2)
    roof, wall = b.box(2, 6, 29, 26, 2 * STOREY + 2)
    brick(b.d, wall, BRICK)
    x0, y0, x1, y1 = wall
    windows(b.d, (x0, y0, x1, y0 + STOREY), 1, sill=TRIM, every=5)
    b.d.rectangle([x0 + 2, y1 - 5, x1 - 2, y1 - 1], c("#8fb3c9"))
    stripe = [c("#c8423a"), c("#3c78a8"), c("#3f8a4a")][v % 3]
    for xx in range(x0 + 1, x1, 4):
        b.d.rectangle([xx, y1 - 8, xx + 1, y1 - 6], stripe)
        b.d.rectangle([xx + 2, y1 - 8, xx + 3, y1 - 6], TRIM)
    b.d.rectangle(wall, outline=OUTLINE)
    features = [[("skylight", 8, 6), ("stack", 21, 3)], [("hatch", 5, 5), ("stack", 22, 12)], [("stack", 4, 3), ("vent", 16, 9), ("vent", 20, 9)]][v % 3]
    flat_roof(b.img, roof, look, random.Random(7100 + v), features)
    return b


def hotel(look, v):
    b = Building(height=3 * STOREY + 7)
    roof, wall = b.box(1, 2, 30, 28, 3 * STOREY + 3)
    brick(b.d, wall, [BRICK20, c("#b8866a")][v % 2])
    windows(b.d, wall, 3, sill=TRIM, every=4, skip_door=True)
    door(b.d, wall, c("#3a2a20"))
    b.d.rectangle([wall[0], wall[1], wall[2], wall[1] + 1], STONE)
    b.d.rectangle(wall, outline=OUTLINE)
    features = [[("tank", 19, 8), ("stack", 3, 3), ("stack", 3, 18), ("skylight", 9, 12)],
                [("tank", 4, 8), ("stack", 24, 3), ("hatch", 14, 16)]][v % 2]
    flat_roof(b.img, roof, look, random.Random(7200 + v), features, parapet=STONE)
    return b


def bank(look, v):
    b = Building(height=2 * STOREY + 4)
    roof, wall = b.box(3, 6, 28, 26, 2 * STOREY + 4)
    b.d.rectangle(wall, STONE)
    x0, y0, x1, y1 = wall
    # Columns across the front, under a cornice.
    for xx in range(x0 + 3, x1 - 1, 4):
        b.d.line([xx, y0 + 4, xx, y1 - 1], c("#efe8d8"))
        b.d.line([xx + 1, y0 + 4, xx + 1, y1 - 1], shade(STONE, 0.85))
    b.d.rectangle([x0, y0, x1, y0 + 2], c("#e2dac6"))
    door(b.d, wall, c("#3a2a20"))
    b.d.rectangle(wall, outline=OUTLINE)
    flat_roof(b.img, roof, look, random.Random(7300), [("skylight", 9, 6), ("skylight", 9, 12)], parapet=STONE)
    return b


def workshop(look, v):
    b = Building(height=STOREY + 3)
    roof, wall = b.box(3, 8, 28, 25, STOREY + 3)
    brick(b.d, wall, [BRICK, c("#8a5a44")][v % 2])
    x0, y0, x1, y1 = wall
    b.d.rectangle([x0 + 4, y1 - 6, x0 + 11, y1], TIMBER)
    b.d.line([x0 + 7, y1 - 6, x0 + 7, y1], shade(TIMBER, 0.7))
    windows(b.d, (x0 + 12, y0, x1, y1), 1, every=5)
    b.d.rectangle(wall, outline=OUTLINE)
    gable_ew(b.d, roof, IRON_ROOF, look)
    roof_feature(b.d, roof, ("stack", 18 if v else 4, 1), look)
    return b


def mill(look, v):
    b = Building(height=24)
    roof, wall = b.box(2, 7, 25, 27, 2 * STOREY + 2)
    brick(b.d, wall, [BRICK, c("#9a6a4a")][v % 2])
    windows(b.d, wall, 2, every=5)
    b.d.rectangle(wall, outline=OUTLINE)
    gable_ew(b.d, roof, IRON_ROOF, look)
    chimney(b, 28, 12, 24, look=look)
    return b


def warehouse(look, v):
    b = Building(height=2 * STOREY + 2)
    roof, wall = b.box(1, 4, 30, 27, 2 * STOREY + 2)
    brick(b.d, wall, [c("#8a5a44"), BRICK][v % 2])
    x0, y0, x1, y1 = wall
    for xx in (x0 + 4, x0 + 13, x0 + 22):
        b.d.rectangle([xx, y1 - 7, xx + 5, y1], TIMBER)
        b.d.rectangle([xx, y0 + 2, xx + 5, y0 + 4], WINDOW)
    b.d.rectangle(wall, outline=OUTLINE)
    rx0, ry0, rx1, ry1 = roof
    if look == "snow":
        b.d.rectangle(roof, SNOW_ROOF[0])
        b.d.line([rx0 + 1, ry0 + 2, rx1 - 1, ry0 + 2], SNOW_ROOF[2])
    else:
        b.d.rectangle(roof, IRON_ROOF)
        for xx in range(rx0 + 2, rx1, 3):
            b.d.line([xx, ry0 + 1, xx, ry1 - 1], shade(IRON_ROOF, 0.88))
    for vx in (8, 20):
        roof_feature(b.d, roof, ("vent", vx, 10), look)
    b.d.rectangle(roof, outline=OUTLINE)
    return b


def factory(look, v):
    b = Building(height=30)
    roof, wall = b.box(1, 3, 30, 28, 3 * STOREY)
    brick(b.d, wall, BRICK)
    windows(b.d, wall, 3, glass=c("#6c7f8a"), every=5, width=3)
    b.d.rectangle(wall, outline=OUTLINE)
    x0, y0, x1, y1 = roof
    # A sawtooth roof: glazed faces to the north, slopes to the south.
    for xx in range(x0, x1 - 4, 7):
        if look == "snow":
            b.d.rectangle([xx, y0, xx + 3, y1], SNOW_ROOF[0])
            b.d.rectangle([xx + 4, y0, min(xx + 6, x1), y1], SNOW_ROOF[2])
        else:
            b.d.rectangle([xx, y0, xx + 3, y1], SAW[0])
            b.d.rectangle([xx + 4, y0, min(xx + 6, x1), y1], c("#b8c4ca"))
    b.d.rectangle(roof, outline=OUTLINE)
    chimney(b, 26, 8, 30, look=look)
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


BUILDINGS = [
    ("cottage", cottage, 4), ("house", house, 4), ("large_house", large_house, 3), ("tenement", tenement, 3),
    ("general_store", general_store, 2), ("shop", shop, 3), ("hotel", hotel, 2), ("bank", bank, 1),
    ("workshop", workshop, 2), ("mill", mill, 2), ("warehouse", warehouse, 2), ("factory", factory, 1),
    ("coal_plant", coal_plant, 1),
]


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
    for mask in range(16):
        out.append((f"road_{mask}", road(look, mask), 0, []))
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

    def ints(values, per_line=20):
        lines = []
        for i in range(0, len(values), per_line):
            lines.append("        " + ", ".join(str(v) for v in values[i:i + per_line]) + ",")
        return "\n".join(lines)

    groups = ["grass", "water", "shore", "corner", "road", "tree", "forest"] + [b[0] for b in BUILDINGS] + ["power_line"]
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
 * [PER_LOOK]. Shores and corners go north, east, south, west. A road's
 * number adds its neighbours that are road: north 1, east 2, south 4, west 8.
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

    /**
     * Six numbers a caster, at 32 px, relative to the top left of the sprite's
     * first tile. A round one (0) is a tree: foot x, foot y, height of its
     * middle, radius. A box (1) is a building: left, top, right, bottom, height.
     */
    val casters = intArrayOf(
{ints(casters)}
    )
}}
"""
    OUT_KT.write_text(text)


if __name__ == "__main__":
    main()
