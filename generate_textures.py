#!/usr/bin/env python3
"""Generate poopsmith's textures: brown speckle block textures, a poop-pile
item icon, and the mod icon.

Pure-stdlib PNG writer (zlib + struct) so it runs without Pillow; same
script-generated-texture approach as village-builder's create_textures.py.
Deterministic (seeded) so re-running produces identical bytes.
"""

import math
import os
import random
import struct
import zlib

OUT = os.path.join(os.path.dirname(__file__), "src/main/resources/assets/poopsmith")

BROWN = (0x6B, 0x44, 0x23, 0xFF)
DARK = (0x4A, 0x2E, 0x17, 0xFF)
DARKER = (0x38, 0x22, 0x10, 0xFF)
LIGHT = (0x7E, 0x55, 0x30, 0xFF)
CLEAR = (0, 0, 0, 0)

# Guano palette: pale chalky grey, deliberately far from poop's brown
GUANO = (0xC6, 0xC2, 0xB2, 0xFF)
GUANO_DARK = (0xA8, 0xA3, 0x92, 0xFF)
GUANO_DARKER = (0x8D, 0x88, 0x79, 0xFF)
GUANO_LIGHT = (0xDD, 0xDA, 0xCC, 0xFF)

# Bat box: plank browns plus a near-black entry hole
PLANK = (0x9E, 0x7A, 0x4A, 0xFF)
PLANK_DARK = (0x7D, 0x5F, 0x38, 0xFF)
PLANK_DARKER = (0x5C, 0x45, 0x28, 0xFF)
HOLE = (0x14, 0x0E, 0x08, 0xFF)

# Poopsmith gloves: work-glove orange, speckled with the poop browns
ORANGE = (0xE8, 0x7A, 0x1E, 0xFF)
ORANGE_DARK = (0xC2, 0x60, 0x12, 0xFF)
ORANGE_LIGHT = (0xF5, 0x94, 0x3C, 0xFF)


def write_png(path, pixels):
    """pixels: list of rows, each row a list of RGBA tuples."""
    height = len(pixels)
    width = len(pixels[0])
    raw = b"".join(b"\x00" + b"".join(bytes(px) for px in row) for row in pixels)

    def chunk(tag, data):
        c = tag + data
        return struct.pack(">I", len(data)) + c + struct.pack(">I", zlib.crc32(c))

    ihdr = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    png = (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr)
           + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(png)
    print(f"wrote {path} ({width}x{height})")


def speckle(size, base, spots, density, seed):
    rng = random.Random(seed)
    rows = [[base] * size for _ in range(size)]
    for y in range(size):
        for x in range(size):
            if rng.random() < density:
                rows[y][x] = spots[rng.randrange(len(spots))]
    return rows


def poop_pile(size, seed, base=BROWN, dark=DARK, darker=DARKER, light=LIGHT):
    """A rounded three-tier mound with transparency, speckled."""
    rng = random.Random(seed)
    rows = [[CLEAR] * size for _ in range(size)]
    s = size / 16.0
    # (center_x, center_y, radius_x, radius_y) tiers, bottom to top
    tiers = [(8, 12.5, 6.2, 2.8), (8, 9.5, 4.6, 2.4), (8, 6.8, 3.0, 2.0), (8.6, 4.6, 1.6, 1.5)]
    for y in range(size):
        for x in range(size):
            fx, fy = x / s + 0.5, y / s + 0.5
            for (cx, cy, rx, ry) in tiers:
                if ((fx - cx) / rx) ** 2 + ((fy - cy) / ry) ** 2 <= 1.0:
                    r = rng.random()
                    rows[y][x] = dark if r < 0.18 else (light if r < 0.30 else base)
                    break
    # darken the outline for readability
    for y in range(size):
        for x in range(size):
            if rows[y][x][3] == 0:
                continue
            edge = any(
                0 <= y + dy < size and 0 <= x + dx < size and rows[y + dy][x + dx][3] == 0
                for dy in (-1, 0, 1) for dx in (-1, 0, 1)
            ) or y == size - 1
            if edge:
                rows[y][x] = darker
    return rows


def planks(size, seed):
    """Horizontal plank rows with grain flecks and dark seams."""
    rng = random.Random(seed)
    rows = [[PLANK] * size for _ in range(size)]
    for y in range(size):
        for x in range(size):
            if y % 4 == 3:
                rows[y][x] = PLANK_DARKER
            elif rng.random() < 0.16:
                rows[y][x] = PLANK_DARK
    return rows


# Digestive tract HUD: the whole assembly (stomach, intestine, exit coil) fits
# inside the vanilla hunger bar's footprint, 81x9, and nothing may spill past
# it: the XP bar sits immediately below and the air bubbles row immediately
# above. Read left to right as one continuous tract, per the design reference
# in design/digestive_hud_reference.png. Colors carry the meaning: PINK is
# organ wall, AMBER is food, BROWN is waste, and both fills sit inside the
# walls. All the visual judgment calls live in these constants.
TRACT_W, TRACT_H = 81, 9

# Horizontal budget. Stomach compact at the left, the squiggle taking most of
# the width, a short straight run into the exit coil.
STOMACH_X1 = 14            # stomach occupies x 0..14
TUBE_X0, TUBE_X1 = 15, 69  # squiggle span
STUB_X1 = 74               # straight run from the squiggle into the sphincter

# Squiggle: pitch 9 over 54px gives exactly 6 peaks, and the phase puts a zero
# crossing at both ends so the tube leaves the stomach and reaches the exit at
# mid height rather than mid-peak. Amplitude is what the 9px row can afford
# once the walls are drawn.
TUBE_PITCH = 9
TUBE_AMPLITUDE = 1.7
TUBE_BASELINE = 4.5
TUBE_PHASE = 3
TUBE_RADIUS = 1.0          # bore half-thickness; the wall is drawn outside it
TUBE_WALL = 0.9

# Sphincter pucker terminating the tract. Nothing is drawn past it: the poop
# item icon the flush pops is the only thing that ever appears there, and only
# while flushing.
PUCKER_CX, PUCKER_CY = 77.5, 4.5
PUCKER_R_OUTER = 2.8
PUCKER_R_MID = 1.6
PUCKER_R_HOLE = 0.8
PUCKER_FILL_X = 78         # waste runs into the pucker hole, not past it

# Stomach: an upright bean, esophagus stub entering top left, outlet leaving
# right at mid height straight into the tube. The bite ellipse is what turns a
# blob into a stomach's lesser curvature.
STOMACH_CX, STOMACH_CY = 7.0, 4.8
STOMACH_RX, STOMACH_RY = 6.0, 3.7
STOMACH_BITE_CX, STOMACH_BITE_CY = 3.5, -0.6
STOMACH_BITE_RX, STOMACH_BITE_RY = 3.6, 2.6
ESOPHAGUS_X0, ESOPHAGUS_X1 = 3, 4
ESOPHAGUS_Y1 = 2
PYLORUS_Y0, PYLORUS_Y1 = 4, 5   # outlet rows, aligned with TUBE_BASELINE

# Muted, low-contrast pinks: the walls should read as background plumbing so
# the amber and brown contents carry the signal
PINK = (0xC9, 0x8F, 0x96, 0xFF)
PINK_DARK = (0xAD, 0x72, 0x79, 0xFF)
PINK_OUTLINE = (0x7A, 0x4C, 0x52, 0xFF)
PUCKER_HOLE = (0x5A, 0x3A, 0x40, 0xFF)

# Amber chyme, deliberately far from the waste browns
AMBER = (0xE8, 0xA3, 0x1E, 0xFF)
AMBER_DARK = (0xC2, 0x84, 0x14, 0xFF)
AMBER_LIGHT = (0xF6, 0xC0, 0x52, 0xFF)


def tube_center(x):
    return TUBE_BASELINE + TUBE_AMPLITUDE * math.sin(
        2 * math.pi * (x + TUBE_PHASE) / TUBE_PITCH)


def _stomach_interior():
    interior = set()
    for x in range(STOMACH_X1 + 1):
        for y in range(TRACT_H):
            fx, fy = x + 0.5, y + 0.5
            in_body = math.hypot((fx - STOMACH_CX) / STOMACH_RX,
                                 (fy - STOMACH_CY) / STOMACH_RY) <= 1.0
            bitten = math.hypot((fx - STOMACH_BITE_CX) / STOMACH_BITE_RX,
                                (fy - STOMACH_BITE_CY) / STOMACH_BITE_RY) <= 1.0
            in_neck = ESOPHAGUS_X0 <= x <= ESOPHAGUS_X1 and fy <= ESOPHAGUS_Y1
            in_outlet = x >= STOMACH_CX and PYLORUS_Y0 <= y <= PYLORUS_Y1
            if (in_body and not bitten) or in_neck or in_outlet:
                interior.add((x, y))
    return interior


def _tube_bore():
    """Contents path: a THIN line down the middle of the tube, not the tube's
    full width, so pink lining stays visible on both sides of the waste and it
    reads as flowing THROUGH the intestine rather than replacing it.

    Rasterized with per-column vertical continuity (Bresenham-style): a pure
    distance-to-centerline test drops pixels on the squiggle's steep segments
    and the line breaks into dashes."""
    bore = set()
    prev = None
    for x in range(TUBE_X0, PUCKER_FILL_X):
        c = tube_center(x) if x <= TUBE_X1 else TUBE_BASELINE
        row = min(TRACT_H - 1, max(0, round(c - 0.5)))
        lo, hi = (row, row) if prev is None else (min(row, prev), max(row, prev))
        for y in range(lo, hi + 1):
            bore.add((x, y))
        prev = row
    return bore


def tract_walls(seed):
    """Everything permanently visible: stomach lining, tube walls, exit mouth
    and the little brown coil past it."""
    rng = random.Random(seed)
    rows = [[CLEAR] * TRACT_W for _ in range(TRACT_H)]
    interior = _stomach_interior()
    bore = _tube_bore()

    # Tube walls hug the bore
    for x in range(TUBE_X0, STUB_X1 + 1):
        c = tube_center(x) if x <= TUBE_X1 else TUBE_BASELINE
        for y in range(TRACT_H):
            d = abs(y + 0.5 - c)
            if d <= TUBE_RADIUS:
                rows[y][x] = PINK_DARK if rng.random() < 0.18 else PINK
            elif d <= TUBE_RADIUS + TUBE_WALL:
                rows[y][x] = PINK_OUTLINE

    # Stomach: lining interior with an outline traced around it
    for (x, y) in interior:
        rows[y][x] = PINK_DARK if rng.random() < 0.16 else PINK
    for (x, y) in list(interior):
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, ny = x + dx, y + dy
            if (nx, ny) in interior: continue
            if not (0 <= nx < TRACT_W and 0 <= ny < TRACT_H): continue
            if nx > STOMACH_X1: continue   # don't wall off the outlet
            rows[ny][nx] = PINK_OUTLINE

    # Sphincter pucker terminates the tract; nothing is drawn past it
    for x in range(TRACT_W):
        for y in range(TRACT_H):
            d = math.hypot(x + 0.5 - PUCKER_CX, y + 0.5 - PUCKER_CY)
            if d <= PUCKER_R_HOLE:
                rows[y][x] = PUCKER_HOLE
            elif d <= PUCKER_R_MID:
                rows[y][x] = PINK_OUTLINE
            elif d <= PUCKER_R_OUTER:
                rows[y][x] = PINK_DARK
    return rows


def tract_food_fill(seed):
    """Stomach contents only. The client reveals the BOTTOM rows of this
    canvas (position, height and texture_v pushed together), so the amber
    rises inside the organ the way the reference sketch shows."""
    rng = random.Random(seed)
    rows = [[CLEAR] * TRACT_W for _ in range(TRACT_H)]
    for (x, y) in _stomach_interior():
        if x > STOMACH_X1: continue
        r = rng.random()
        rows[y][x] = AMBER_DARK if r < 0.20 else (AMBER_LIGHT if r < 0.34 else AMBER)
    return rows


def tract_waste_fill(seed):
    """Tube contents only: speckled brown along the bore, revealed left to
    right by the client's width clip so waste advances toward the exit."""
    rng = random.Random(seed)
    rows = [[CLEAR] * TRACT_W for _ in range(TRACT_H)]
    for (x, y) in _tube_bore():
        r = rng.random()
        rows[y][x] = DARK if r < 0.20 else (LIGHT if r < 0.32 else BROWN)
    return rows


def poopsmith_gloves(seed):
    """64x64 villager-texture-layout overlay: orange gloves with poop
    speckle, transparent everywhere else, for rendering as an extra layer
    over the vanilla villager model. Painted regions follow the villager
    model's arm UVs: the crossed-forearms box at texOffs (40,38) occupies
    u40..64 v38..46, and the upper-arm boxes at texOffs (44,22) put their
    side faces at u44..60 v26..34, of which the lowest rows are the cuffs."""
    rng = random.Random(seed)
    rows = [[CLEAR] * 64 for _ in range(64)]

    def glove():
        r = rng.random()
        if r < 0.10: return BROWN
        if r < 0.16: return DARK
        if r < 0.30: return ORANGE_DARK
        if r < 0.42: return ORANGE_LIGHT
        return ORANGE

    # Crossed forearms and hands: the whole box
    for y in range(38, 46):
        for x in range(40, 64):
            rows[y][x] = glove()
    # Cuffs: lowest three rows of the upper-arm side faces
    for y in range(31, 34):
        for x in range(44, 60):
            rows[y][x] = glove()
    return rows


def bat_box_front(size, seed):
    """Plank face with the dark entry hole low in the middle: bats enter
    from underneath, so the hole sits near the bottom edge."""
    rows = planks(size, seed)
    cx, cy, rx, ry = size / 2.0, size * 0.72, size * 0.17, size * 0.20
    for y in range(size):
        for x in range(size):
            d = ((x + 0.5 - cx) / rx) ** 2 + ((y + 0.5 - cy) / ry) ** 2
            if d <= 1.0:
                rows[y][x] = HOLE
            elif d <= 1.7:
                rows[y][x] = PLANK_DARKER
    return rows


write_png(os.path.join(OUT, "textures/block/poop.png"),
          speckle(16, BROWN, [DARK, DARKER, LIGHT], 0.28, seed=6103))
write_png(os.path.join(OUT, "textures/block/poop_block.png"),
          speckle(16, BROWN, [DARK, DARKER, LIGHT, DARK], 0.38, seed=2607))
write_png(os.path.join(OUT, "textures/item/poop.png"), poop_pile(16, seed=1793))
write_png(os.path.join(OUT, "icon.png"), poop_pile(128, seed=1793))

write_png(os.path.join(OUT, "textures/block/guano.png"),
          speckle(16, GUANO, [GUANO_DARK, GUANO_DARKER, GUANO_LIGHT], 0.30, seed=4451))
write_png(os.path.join(OUT, "textures/block/guano_block.png"),
          speckle(16, GUANO, [GUANO_DARK, GUANO_DARKER, GUANO_LIGHT, GUANO_DARK], 0.38, seed=7793))
write_png(os.path.join(OUT, "textures/item/guano.png"),
          poop_pile(16, seed=9241, base=GUANO, dark=GUANO_DARK, darker=GUANO_DARKER, light=GUANO_LIGHT))
write_png(os.path.join(OUT, "textures/block/bat_box_side.png"), planks(16, seed=3307))
write_png(os.path.join(OUT, "textures/block/bat_box_front.png"), bat_box_front(16, seed=3307))

write_png(os.path.join(OUT, "textures/entity/poopsmith_gloves.png"), poopsmith_gloves(seed=8117))

write_png(os.path.join(OUT, "textures/gui/poop_tract.png"), tract_walls(seed=5233))
write_png(os.path.join(OUT, "textures/gui/poop_tract_food.png"), tract_food_fill(seed=6421))
write_png(os.path.join(OUT, "textures/gui/poop_tract_waste.png"), tract_waste_fill(seed=5233))
