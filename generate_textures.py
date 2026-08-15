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


# Intestine HUD tube: hunger-bar width, gentle S-curves at the vanilla pip
# pitch so the curves read as weaving between the drumsticks above. All the
# visual judgment calls live in these constants for easy tuning.
INTESTINE_W, INTESTINE_H = 81, 3
INTESTINE_PITCH = 8       # curve period; matches vanilla pip spacing
INTESTINE_AMPLITUDE = 0.4 # curve depth in px; at ribbon size, a subtle wiggle
INTESTINE_PHASE = 2.0     # slides dips relative to drumstick columns
TUBE_RADIUS = 1.1         # pink flesh half-thickness
BORE_RADIUS = 1.2         # brown fill half-thickness
OUTLINE_RADIUS = 3.2

# Muted, low-contrast pinks: the tube should read as background plumbing
PINK = (0xC9, 0x8F, 0x96, 0xFF)
PINK_DARK = (0xAD, 0x72, 0x79, 0xFF)
PINK_OUTLINE = (0x7A, 0x4C, 0x52, 0xFF)


def intestine_center(x):
    return 1.5 + INTESTINE_AMPLITUDE * math.sin(2 * math.pi * (x + INTESTINE_PHASE) / INTESTINE_PITCH)


def intestine_tube(seed):
    """The empty tube: pink flesh with a darker outline along a sine path."""
    rng = random.Random(seed)
    rows = [[CLEAR] * INTESTINE_W for _ in range(INTESTINE_H)]
    for x in range(INTESTINE_W):
        c = intestine_center(x)
        for y in range(INTESTINE_H):
            d = abs(y + 0.5 - c)
            if d <= TUBE_RADIUS:
                rows[y][x] = PINK_DARK if rng.random() < 0.18 else PINK
            elif d <= OUTLINE_RADIUS:
                rows[y][x] = PINK_OUTLINE
    return rows


def intestine_fill(seed):
    """The bore contents: speckled brown along the same path, drawn by the
    client clipped to a width proportional to the poop bar."""
    rng = random.Random(seed)
    rows = [[CLEAR] * INTESTINE_W for _ in range(INTESTINE_H)]
    for x in range(INTESTINE_W):
        c = intestine_center(x)
        for y in range(INTESTINE_H):
            if abs(y + 0.5 - c) <= BORE_RADIUS:
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

write_png(os.path.join(OUT, "textures/gui/poop_intestine.png"), intestine_tube(seed=5233))
write_png(os.path.join(OUT, "textures/gui/poop_intestine_fill.png"), intestine_fill(seed=5233))
