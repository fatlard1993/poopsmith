#!/usr/bin/env python3
"""Generate the village latrine structure NBTs, one per village biome.

Five variants (latrine_plains / latrine_taiga / latrine_snowy /
latrine_savanna / latrine_desert), each a 5x5 privy hut whose materials match
what vanilla's own village houses use in that biome. Same shape everywhere:
raised hut over a deep pit floored with a poop block, door on the front face.
Villagers path to the pit rim and deposit into the pit; the compost-pit
stacking rule converts full layer stacks into new poop blocks, so the pit
slowly fills until mined out.

NBT writer and template key format copied from village-mail's
generate_structures.py: the old Name/Properties palette keys with a matching
older DataVersion, upgraded by DataFixerUpper at load time. The current game
writes lowercase id/properties keys; claiming a current DataVersion with old
keys would skip the upgrade, so the pair below must stay consistent.

Output is byte-for-byte deterministic: the gzip header carries no mtime and no
embedded filename, and palette/block order is a pure function of the build.


Vertical layout: the MEASURED vanilla entrance convention
─────────────────────────────────────────────────────────
Measured against this snapshot's own village house templates (all 118 house
templates across the five biomes carry a minecraft:building_entrance jigsaw):

  * 59 templates:  doorLowerY == jigsawY,      jigsawY == lowestRow + 1
  * 39 templates:  doorLowerY == jigsawY + 1,  jigsawY == lowestRow

The first (majority) form is the FLUSH entrance: the entrance jigsaw sits in
the front wall plane beside the door, in the same Y row as the door's lower
half, with exactly one foundation row beneath. Reference templates:
desert_small_house_8 (jigsaw (1,1,0) north_up final_state smooth_sandstone,
door (2,1,0) facing north) and snowy_small_house_1 (jigsaw (3,1,5) south_up,
door (3,1,4) facing south).

The second form is the RAISED entrance: the jigsaw sits one block OUTSIDE the
doorway in the floor row with a stair final_state, so the house floor lands a
block above the street and the stair is the doorstep. plains_small_house_1
(jigsaw (0,0,3) west_up final_state oak_stairs, door (1,1,3)) is that one.

Why it matters, from the generator (JigsawPlacement$Placer, verified by javap
on this snapshot's jar): a piece placed off a terrain-matching street gets
originY = getFirstFreeHeight(streetJigsawX, streetJigsawZ) - jigsawLocalY, and
getFirstFreeHeight is terrainTopY + 1. So the jigsaw's own row always lands on
the street's WALKABLE level (terrainTop + 1) and every row below it sinks
under grade. The flush form therefore puts the door's bottom block exactly at
street level; the raised form puts it one block above (hence the stair).

The latrine wants FLUSH: no step, walk straight in. Its previous layout used
the raised form's doorLowerY = jigsawY + 1 with the jigsaw in the doorway
column, which is what put the door one block above the street.


How deep the template may sink: a MEASURED hard limit of 2 rows
──────────────────────────────────────────────────────────────
Every vanilla street's building_entrance jigsaw points at a block that is
still inside the street piece's own bounding box (checked: 182 of 182 across
all five biomes). JigsawPlacement$Placer takes that branch
(`parentBox.isInside(attachPoint)`) and then requires the candidate house to
fit inside the PARENT piece's bounding box rather than inside the shared free
space. Street pieces are two rows tall, so a house may only sink a couple of
rows below its own entrance-jigsaw row before it pokes out of that box and is
silently rejected: no error, the placer just tries the next pool entry.

Measured, not reasoned: five otherwise-identical test huts sinking A = 1..5
rows below their entrance jigsaw were injected into village/plains/houses at
weight 200 each and four plains villages were force-generated. Only A <= 2
huts ever appeared (5 of them); A = 3, 4 and 5 appeared zero times. That is
why the four-row-deep version of this template generated in exactly zero of
16 sampled villages.

A flush door spends one of those two rows (the floor has to sit one row below
the door), which leaves exactly one row for the pit. So the pit gets one row
here and LatrinePitProcessor digs the rest at placement time, below the
template and therefore below the bounding box the placer checks.

  y0: pit row: the poop block seed in the pit column, foundation elsewhere
  y1: GRADE: hut floor, pit opening. Lands flush with the street's path blocks
  y2: wall base, door lower, ENTRANCE JIGSAW beside the door in the wall plane
  y3: walls, door upper, windows
  y4: roof
  y5: snow layer (snowy only)

Door on the z=0 face; the entrance jigsaw faces north (-z is north in template
space, so z=0 is the street side) and the door's `facing` matches it, per the
vanilla templates above.
"""

import gzip, struct, io, os

OUT_DIR = os.path.join(os.path.dirname(__file__),
                       "src/main/resources/data/poopsmith/structure")

# Last DataVersion this generator's old-style keys are known valid for;
# DataFixerUpper upgrades from here to whatever the running game needs
DATA_VERSION = 4325


# ── NBT Writer (village-mail's generate_structures.py conventions) ────

class NBTWriter:
    def __init__(self):
        self.buf = io.BytesIO()

    def write_byte(self, v):  self.buf.write(struct.pack('b', v))
    def write_short(self, v): self.buf.write(struct.pack('>h', v))
    def write_int(self, v):   self.buf.write(struct.pack('>i', v))

    def write_string(self, s):
        encoded = s.encode('utf-8')
        self.write_short(len(encoded))
        self.buf.write(encoded)

    def write_named_tag(self, tag_type, name, value):
        self.write_byte(tag_type)
        self.write_string(name)
        self._write_payload(tag_type, value)

    def _write_payload(self, tag_type, value):
        if tag_type == 3:
            self.write_int(value)
        elif tag_type == 8:
            self.write_string(value)
        elif tag_type == 9:
            list_type, items = value
            self.write_byte(list_type)
            self.write_int(len(items))
            for item in items:
                self._write_payload(list_type, item)
        elif tag_type == 10:
            for child in value:
                self.write_named_tag(*child)
            self.write_byte(0)

    def get_bytes(self):
        return self.buf.getvalue()


def palette_entry(name, **props):
    children = [(8, 'Name', name)]
    if props:
        children.append((10, 'Properties', [(8, k, str(v)) for k, v in props.items()]))
    return children


def block(x, y, z, state, nbt_children=None):
    children = [
        (9, 'pos', (3, [x, y, z])),
        (3, 'state', state),
    ]
    if nbt_children:
        children.append((10, 'nbt', nbt_children))
    return children


def jigsaw_entrance_block(x, y, z, state, pool, final_state):
    """Street-connection jigsaw, vanilla houses-pool convention.

    Streets select houses via jigsaws whose target is
    minecraft:building_entrance, matched against the house jigsaw's NAME, so
    the house jigsaw must be named minecraft:building_entrance, joint aligned,
    oriented out the door face, with its pool pointing at the biome's streets
    pool (village-mail's per-biome convention).

    final_state is the biome's wall block: this jigsaw sits IN the front wall
    plane beside the door, so leaving structure_void there would punch a hole.
    """
    nbt = [
        (8, 'id', 'minecraft:jigsaw'),
        (8, 'name', 'minecraft:building_entrance'),
        (8, 'target', 'minecraft:building_entrance'),
        (8, 'pool', pool),
        (8, 'joint', 'aligned'),
        (8, 'final_state', final_state),
    ]
    return block(x, y, z, state, nbt)


def write_structure(path, size, palette, blocks):
    root = [
        (3, 'DataVersion', DATA_VERSION),
        (9, 'size', (3, list(size))),
        (9, 'palette', (10, palette)),
        (9, 'blocks', (10, blocks)),
        (9, 'entities', (10, [])),
    ]
    w = NBTWriter()
    w.write_named_tag(10, '', root)
    raw = w.get_bytes()
    os.makedirs(os.path.dirname(path), exist_ok=True)
    # Deterministic gzip: no mtime, no embedded filename. Plain gzip.open()
    # stamps both, which would make two runs differ byte-for-byte.
    with open(path, 'wb') as fh:
        with gzip.GzipFile(filename='', mode='wb', fileobj=fh, mtime=0) as gz:
            gz.write(raw)
    print(f"  {os.path.basename(path)}: {len(raw)} bytes "
          f"({len(palette)} palette, {len(blocks)} blocks)")


class StructureBuilder:
    def __init__(self):
        self.palette = []
        self.palette_idx = {}
        self.blocks = []
        self.max = [0, 0, 0]

    def _get_state(self, name, props=None):
        key = (name, tuple(sorted((props or {}).items())))
        if key not in self.palette_idx:
            self.palette_idx[key] = len(self.palette)
            self.palette.append(palette_entry(name, **(props or {})))
        return self.palette_idx[key]

    def put(self, x, y, z, name, props=None):
        self.blocks.append(block(x, y, z, self._get_state(name, props)))
        self._grow(x, y, z)

    def put_entrance_jigsaw(self, x, y, z, orientation, pool, final_state):
        state = self._get_state('minecraft:jigsaw', {'orientation': orientation})
        self.blocks.append(jigsaw_entrance_block(x, y, z, state, pool, final_state))
        self._grow(x, y, z)

    def _grow(self, x, y, z):
        self.max[0] = max(self.max[0], x + 1)
        self.max[1] = max(self.max[1], y + 1)
        self.max[2] = max(self.max[2], z + 1)

    def save(self, path):
        # Last write wins per position: a template must not carry duplicate
        # position entries (placement order would silently pick one)
        by_pos = {}
        for entry in self.blocks:
            by_pos[tuple(entry[0][2][1])] = entry  # (9,'pos',(3,[x,y,z]))
        write_structure(path, tuple(self.max), self.palette, list(by_pos.values()))


# ══════════════════════════════════════════════════════════════
# Per-biome palettes
#
# Measured, not guessed: block-frequency histograms over every vanilla
# house template in each biome's village/<biome>/houses directory in this
# snapshot's jar (non-air blocks, most common first):
#
#   plains  : oak_stairs 1879, cobblestone 1767, oak_planks 1471, oak_log 966
#   taiga   : cobblestone 1665, spruce_log 1613, spruce_planks 310
#   snowy   : spruce_planks 1754, snow 1054, stripped_spruce_log 782,
#             snow_block 347, packed_ice 71   (no cobblestone at all)
#   savanna : acacia_stairs 1174, acacia_planks 1015, acacia_log 709,
#             yellow_terracotta 199           (no cobblestone at all)
#   desert  : smooth_sandstone 2066, sand 936, cut_sandstone 780,
#             smooth_sandstone_slab 523, jungle_door 64  (no glass_pane,
#             no wood: desert windows are open holes and roofs are slabs)
# ══════════════════════════════════════════════════════════════

BIOMES = {
    'plains': {
        'foundation': ('minecraft:cobblestone', None),
        'floor':      ('minecraft:cobblestone', None),
        'wall':       ('minecraft:oak_planks', None),
        'corner':     ('minecraft:oak_log', {'axis': 'y'}),
        'door':       'minecraft:oak_door',
        'roof':       ('minecraft:oak_slab', {'type': 'bottom'}),
        'window':     'glass',
        'snow':       False,
    },
    'taiga': {
        'foundation': ('minecraft:cobblestone', None),
        'floor':      ('minecraft:cobblestone', None),
        'wall':       ('minecraft:spruce_planks', None),
        'corner':     ('minecraft:spruce_log', {'axis': 'y'}),
        'door':       'minecraft:spruce_door',
        'roof':       ('minecraft:spruce_slab', {'type': 'bottom'}),
        'window':     'glass',
        'snow':       False,
    },
    'snowy': {
        # Snowy villages have no cobblestone: packed ice is the masonry and
        # stripped spruce the framing. Roof is a full plank deck rather than a
        # slab so the snow layer above it can survive (a snow layer needs a
        # full top face below it, which a bottom slab does not have).
        # The floor is planks, not ice: packed ice is slippery, and a villager
        # sliding on the rim of a four-deep pit is a trap, not a feature.
        'foundation': ('minecraft:packed_ice', None),
        'floor':      ('minecraft:spruce_planks', None),
        'wall':       ('minecraft:spruce_planks', None),
        'corner':     ('minecraft:stripped_spruce_log', {'axis': 'y'}),
        'door':       'minecraft:spruce_door',
        'roof':       ('minecraft:spruce_planks', None),
        'window':     'glass',
        'snow':       True,
    },
    'savanna': {
        'foundation': ('minecraft:yellow_terracotta', None),
        'floor':      ('minecraft:yellow_terracotta', None),
        'wall':       ('minecraft:acacia_planks', None),
        'corner':     ('minecraft:acacia_log', {'axis': 'y'}),
        'door':       'minecraft:acacia_door',
        'roof':       ('minecraft:acacia_slab', {'type': 'bottom'}),
        'window':     'glass',
        'snow':       False,
    },
    'desert': {
        'foundation': ('minecraft:sandstone', None),
        'floor':      ('minecraft:smooth_sandstone', None),
        'wall':       ('minecraft:smooth_sandstone', None),
        'corner':     ('minecraft:cut_sandstone', None),
        'door':       'minecraft:jungle_door',   # vanilla desert villages use jungle doors
        'roof':       ('minecraft:smooth_sandstone_slab', {'type': 'bottom'}),
        'window':     'open',                    # desert windows are open holes
        'snow':       False,
    },
}


# ══════════════════════════════════════════════════════════════
# VILLAGE LATRINE — 5w x 5d, 5 tall (6 for snowy's snow cap)
# See the module docstring for the measured vanilla jigsaw relationship and
# for why the template may only sink two rows below its entrance jigsaw.
# ══════════════════════════════════════════════════════════════

W, D = 5, 5
PIT = (2, 3)          # x, z of the pit column, back center
PIT_FLOOR_Y = 0       # the poop block seed, one row under the floor
GRADE_Y = 1           # hut floor row, lands flush with the street
DOOR_Y = GRADE_Y + 1  # door lower half AND the entrance jigsaw
ROOF_Y = GRADE_Y + 3
DOOR_X = 2            # doorway column in the z=0 front wall
JIGSAW_X = 3          # entrance jigsaw beside the door, in the same wall plane
# Keep in step with LatrinePitProcessor.TARGET_PIT_DEPTH: the template digs
# one row, the processor digs the rest once the piece has cleared the placer's
# bounding-box check.
TEMPLATE_PIT_DEPTH = GRADE_Y - PIT_FLOOR_Y   # 1


def build_latrine(biome, mats):
    b = StructureBuilder()

    foundation_name, foundation_props = mats['foundation']
    floor_name, floor_props = mats['floor']
    wall_name, wall_props = mats['wall']
    corner_name, corner_props = mats['corner']
    roof_name, roof_props = mats['roof']

    def is_corner(x, z):
        return x in (0, W - 1) and z in (0, D - 1)

    def is_wall(x, z):
        return x in (0, W - 1) or z in (0, D - 1)

    for x in range(W):
        for z in range(D):
            pit = (x, z) == PIT

            # y0: the one sunk row the placer allows. The poop block seeds the
            # compost pit; LatrinePitProcessor moves it down and opens the
            # shaft to its full depth right after placement.
            b.put(x, PIT_FLOOR_Y, z,
                  'poopsmith:poop_block' if pit else foundation_name,
                  None if pit else foundation_props)

            # y1 GRADE: hut floor, with the pit opening. This row lands at
            # terrainTop, flush with the street's path blocks.
            if pit:
                b.put(x, GRADE_Y, z, 'minecraft:air')
            else:
                b.put(x, GRADE_Y, z, floor_name, floor_props)

            # y5..y6: walls with corner posts, the door, the entrance jigsaw
            # beside it, and windows. Interior air so worldgen clears terrain.
            for y in (DOOR_Y, DOOR_Y + 1):
                if is_corner(x, z):
                    b.put(x, y, z, corner_name, corner_props)
                elif x == DOOR_X and z == 0:
                    half = 'lower' if y == DOOR_Y else 'upper'
                    # facing matches the jigsaw's outward direction, per
                    # desert_small_house_8 and snowy_small_house_1
                    b.put(x, y, z, mats['door'],
                          {'facing': 'north', 'half': half, 'hinge': 'left',
                           'open': 'false', 'powered': 'false'})
                elif x == JIGSAW_X and z == 0 and y == DOOR_Y:
                    b.put_entrance_jigsaw(
                        x, y, z, 'north_up',
                        'minecraft:village/' + biome + '/streets',
                        wall_name)
                elif y == DOOR_Y + 1 and z == 2 and x in (0, W - 1):
                    if mats['window'] == 'glass':
                        b.put(x, y, z, 'minecraft:glass_pane',
                              {'north': 'true', 'south': 'true',
                               'east': 'false', 'west': 'false'})
                    else:
                        b.put(x, y, z, 'minecraft:air')
                elif is_wall(x, z):
                    b.put(x, y, z, wall_name, wall_props)
                else:
                    b.put(x, y, z, 'minecraft:air')

            # y7: roof
            b.put(x, ROOF_Y, z, roof_name, roof_props)

            # y8: snow cap on the roof (snowy only)
            if mats['snow']:
                b.put(x, ROOF_Y + 1, z, 'minecraft:snow', {'layers': '2'})

    b.save(os.path.join(OUT_DIR, 'latrine_' + biome + '.nbt'))


if __name__ == '__main__':
    print(f"Generating latrine structures (grade row y{GRADE_Y}, "
          f"door row y{DOOR_Y}, template pit {TEMPLATE_PIT_DEPTH} deep):")
    for biome, mats in BIOMES.items():
        build_latrine(biome, mats)
    print("Done.")
