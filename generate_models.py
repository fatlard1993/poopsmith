#!/usr/bin/env python3
"""Generate poopsmith's layer block models: chunky piles for layers 1-2,
flat snow-parity slabs for layers 3-7.

Why the split: a deposit on open ground never grows past 2 layers (it slides
aside instead, see PoopPlacement.OPEN_STACK_LAYERS), so layers 1-2 are what
the world actually shows and they read as loose lumps. Layers 3+ only happen
where a stack is contained (a latrine pit, a compost column), where the heap
is packed down by its own walls: flat fill, and the last one converts to a
full block.

Deterministic and hand-tuned rather than random: the lump lists below are the
whole design. Re-running rewrites every layer model, slabs included, so the
JSON on disk always matches this file.
"""

import json
import os
import re

OUT = os.path.join(os.path.dirname(__file__),
                   "src/main/resources/assets/poopsmith/models/block")

# Lump lists, bottom to top: a mound built from stacked plates with a turned
# crown, plus stray clumps beside it so the silhouette isn't a wedding cake.
# "rot" is a y-axis rotation in degrees about the lump's own centre; the model
# format only allows 22.5 and 45.
PILE_1 = [
    {"from": [5, 0, 6], "to": [11, 4, 11]},
    {"from": [3, 0, 8], "to": [8, 2, 13], "rot": 22.5},
    {"from": [9, 0, 3], "to": [13, 2, 8], "rot": 45},
    {"from": [2, 0, 3], "to": [5, 1, 6]},
]

# A second deposit landing on the first: the heap grown a crown, a smaller one
# spilling off its east side, and clumps flung clear.
PILE_2 = [
    {"from": [3, 0, 4], "to": [12, 3, 12]},
    {"from": [4, 3, 5], "to": [10, 5, 10]},
    {"from": [5, 5, 6], "to": [9, 6, 10], "rot": 45},
    {"from": [10, 0, 7], "to": [15, 3, 13], "rot": 22.5},
    {"from": [1, 0, 9], "to": [5, 2, 13], "rot": 45},
    {"from": [11, 0, 2], "to": [14, 2, 5]},
]

# Layers 3-7: the snow-layer slab, two pixels per layer.
SLAB_HEIGHTS = [6, 8, 10, 12, 14]


def face_uv(direction, f, t):
    """Vanilla's derived UVs (BlockElement#uvsByFace), written out explicitly."""
    if direction == "down":
        return [f[0], 16 - t[2], t[0], 16 - f[2]]
    if direction == "up":
        return [f[0], f[2], t[0], t[2]]
    if direction == "north":
        return [16 - t[0], 16 - t[1], 16 - f[0], 16 - f[1]]
    if direction == "south":
        return [f[0], 16 - t[1], t[0], 16 - f[1]]
    if direction == "west":
        return [f[2], 16 - t[1], t[2], 16 - f[1]]
    return [16 - t[2], 16 - t[1], 16 - f[2], 16 - f[1]]


# Which coordinate has to sit on the block boundary for a face to be cullable
CULL_EDGE = {
    "down": (1, 0), "up": (1, 16), "north": (2, 0),
    "south": (2, 16), "west": (0, 0), "east": (0, 16),
}


def element(lump):
    f, t = lump["from"], lump["to"]
    faces = {}
    for direction in ("down", "up", "north", "south", "west", "east"):
        face = {"uv": face_uv(direction, f, t), "texture": "#texture"}
        # Cull only a face lying flat on the block boundary: anything a
        # neighbouring full block genuinely hides. A turned lump's faces no
        # longer lie on that plane, so they never qualify.
        axis, edge = CULL_EDGE[direction]
        if "rot" not in lump and (f[axis] if edge == 0 else t[axis]) == edge:
            face["cullface"] = direction
        faces[direction] = face
    out = {"from": f, "to": t, "faces": faces}
    if "rot" in lump:
        origin = [(f[0] + t[0]) / 2, 0, (f[2] + t[2]) / 2]
        out = {"from": f, "to": t,
               "rotation": {"origin": origin, "axis": "y", "angle": lump["rot"]},
               "faces": faces}
    return out


def model(parent, texture, lumps):
    return {
        "parent": parent,
        "textures": {"particle": texture, "texture": texture},
        "elements": [element(lump) for lump in lumps],
    }


def slab(height):
    return [{"from": [0, 0, 0], "to": [16, height, 16]}]


def write(name, data):
    path = os.path.join(OUT, name + ".json")
    os.makedirs(OUT, exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")
    print(f"wrote {path}")



def verify(block_source):
    """Pile peaks have to agree with PoopLayerBlock's outline boxes, and no lump
    may float. Read the Java rather than restate it: a tweak up top that drifts
    from the block's HEIGHTS fails here instead of in a world."""
    heights = [int(n) for n in
               re.search(r"HEIGHTS = \{([^}]*)\}", open(block_source).read()).group(1).split(",")]
    for layer, lumps in ((1, PILE_1), (2, PILE_2)):
        peak = max(l["to"][1] for l in lumps)
        if peak != heights[layer]:
            raise SystemExit(f"layer {layer}: pile peaks at {peak}px, "
                             f"PoopLayerBlock.HEIGHTS says {heights[layer]}px")
        for lump in lumps:
            f, t = lump["from"], lump["to"]
            if any(not 0 <= f[a] < t[a] <= 16 for a in range(3)):
                raise SystemExit(f"layer {layer}: lump {f}->{t} leaves the block")
            rests = f[1] == 0 or any(
                o["to"][1] >= f[1] > o["from"][1]
                and o["from"][0] < t[0] and o["to"][0] > f[0]
                and o["from"][2] < t[2] and o["to"][2] > f[2]
                for o in lumps if o is not lump)
            if not rests:
                raise SystemExit(f"layer {layer}: lump {f}->{t} floats")
    for layer, height in enumerate(SLAB_HEIGHTS, start=3):
        if height != heights[layer]:
            raise SystemExit(f"layer {layer}: slab is {height}px, "
                             f"PoopLayerBlock.HEIGHTS says {heights[layer]}px")


verify(os.path.join(os.path.dirname(__file__),
                    "src/main/java/justfatlard/poopsmith/PoopLayerBlock.java"))

for family in ("poop", "guano"):
    texture = f"poopsmith:block/{family}"
    # Piles are free-standing geometry, so they take the plain block parent;
    # the slabs keep thin_block, which is what a snow-alike renders as in hand.
    write(f"{family}_pile1", model("minecraft:block/block", texture, PILE_1))
    write(f"{family}_pile2", model("minecraft:block/block", texture, PILE_2))
    for height in SLAB_HEIGHTS:
        write(f"{family}_height{height}",
              model("minecraft:block/thin_block", texture, slab(height)))
