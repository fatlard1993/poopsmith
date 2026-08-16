#!/usr/bin/env python3
"""Generate the village latrine structure NBT.

One generic wood-and-cobble privy hut (5 wide x 5 tall x 5 deep) that fits any
village biome: raised plank floor, a 1-deep pit at the back floored with a
poop block, door on the front face. Villagers path to the pit and deposit on
the poop block; the compost-pit stacking rule converts full layer stacks into
new poop blocks, so the pit slowly fills until mined out.

NBT writer and template key format copied from village-mail's
generate_structures.py: the old Name/Properties palette keys with a matching
older DataVersion, upgraded by DataFixerUpper at load time. The current game
writes lowercase id/properties keys; claiming a current DataVersion with old
keys would skip the upgrade, so the pair below must stay consistent.

The jigsaw follows the vanilla house convention verified against this
snapshot's own village templates: streets attach houses via jigsaws whose
target is minecraft:building_entrance, matched against the house jigsaw's
NAME, so the latrine's jigsaw must be named minecraft:building_entrance
(joint aligned, oriented out the door face, placed in the foundation at the
doorway).
"""

import gzip, struct, io, os

OUT = os.path.join(os.path.dirname(__file__),
                   "src/main/resources/data/poopsmith/structure/latrine.nbt")

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


def jigsaw_entrance_block(x, y, z, state, final_state):
    nbt = [
        (8, 'id', 'minecraft:jigsaw'),
        (8, 'name', 'minecraft:building_entrance'),
        (8, 'target', 'minecraft:building_entrance'),
        (8, 'pool', 'minecraft:village/plains/streets'),
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
    with gzip.open(path, 'wb') as f:
        f.write(raw)
    print(f"  {path}: {len(raw)} bytes ({len(palette)} palette, {len(blocks)} blocks)")


class StructureBuilder:
    def __init__(self):
        self.palette = []
        self.palette_idx = {}
        self.blocks = []
        self.max = [0, 0, 0]

    def _get_state(self, name, props=None):
        key = (name, frozenset((props or {}).items()))
        if key not in self.palette_idx:
            self.palette_idx[key] = len(self.palette)
            self.palette.append(palette_entry(name, **(props or {})))
        return self.palette_idx[key]

    def put(self, x, y, z, name, props=None):
        self.blocks.append(block(x, y, z, self._get_state(name, props)))
        self._grow(x, y, z)

    def put_entrance_jigsaw(self, x, y, z, orientation, final_state):
        state = self._get_state('minecraft:jigsaw', {'orientation': orientation})
        self.blocks.append(jigsaw_entrance_block(x, y, z, state, final_state))
        self._grow(x, y, z)

    def _grow(self, x, y, z):
        self.max[0] = max(self.max[0], x + 1)
        self.max[1] = max(self.max[1], y + 1)
        self.max[2] = max(self.max[2], z + 1)

    def save(self, path):
        write_structure(path, tuple(self.max), self.palette, self.blocks)


# ══════════════════════════════════════════════════════════════
# VILLAGE LATRINE — 5w x 6h x 5d
# Vertical layout follows the vanilla house convention verified against
# this snapshot's plains_small_house_1/2: the entrance jigsaw sits in the
# GRADE row and the door's lower half is exactly one row above it (vanilla
# uses the jigsaw's final_state as the doorstep). Rows below the jigsaw
# sink under street level, which is where the pit gets its depth: two sunk
# rows make a pit whose poop-block floor is 2 open blocks below the hut
# floor. Door on the z=0 face; the entrance jigsaw faces north (-z is
# north in template space, so z=0 is the street side).
#
#   y0: pit floor row (poop block in the pit column, cobble elsewhere)
#   y1: sunk shaft row (air in the pit column, cobble elsewhere)
#   y2: GRADE: cobble floor, jigsaw at the doorway, pit opening
#   y3: wall base, door lower
#   y4: walls, door upper, windows
#   y5: slab roof
# ══════════════════════════════════════════════════════════════

def build_latrine():
    b = StructureBuilder()
    W, D = 5, 5
    PIT = (2, 3)  # x, z of the pit column, back center

    def is_corner(x, z):
        return x in (0, W - 1) and z in (0, D - 1)

    def is_wall(x, z):
        return x in (0, W - 1) or z in (0, D - 1)

    for x in range(W):
        for z in range(D):
            # y0-y1: sunk foundation rows carrying the pit shaft
            b.put(x, 0, z, 'poopsmith:poop_block' if (x, z) == PIT else 'minecraft:cobblestone')
            b.put(x, 1, z, 'minecraft:air' if (x, z) == PIT else 'minecraft:cobblestone')

            # y2: grade row: floor, pit opening, entrance jigsaw (its
            # final_state cobble is the doorstep, vanilla-style)
            if (x, z) == PIT:
                b.put(x, 2, z, 'minecraft:air')
            elif x == 2 and z == 0:
                b.put_entrance_jigsaw(x, 2, z, 'north_up', 'minecraft:cobblestone')
            else:
                b.put(x, 2, z, 'minecraft:cobblestone')

            # y3-y4: walls with log corners, door one row above the jigsaw,
            # windows; interior air so worldgen clears terrain inside
            for y in (3, 4):
                if is_corner(x, z):
                    b.put(x, y, z, 'minecraft:oak_log', {'axis': 'y'})
                elif x == 2 and z == 0:
                    half = 'lower' if y == 3 else 'upper'
                    b.put(x, y, z, 'minecraft:oak_door',
                          {'facing': 'south', 'half': half, 'hinge': 'left', 'open': 'false'})
                elif y == 4 and z == 2 and x in (0, W - 1):
                    b.put(x, y, z, 'minecraft:glass_pane',
                          {'north': 'true', 'south': 'true', 'east': 'false', 'west': 'false'})
                elif is_wall(x, z):
                    b.put(x, y, z, 'minecraft:oak_planks')
                else:
                    b.put(x, y, z, 'minecraft:air')

            # y5: flat slab roof
            b.put(x, 5, z, 'minecraft:oak_slab', {'type': 'bottom'})

    b.save(OUT)


if __name__ == '__main__':
    print("Generating latrine structure:")
    build_latrine()
