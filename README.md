# Poopsmith

A Fabric mod where everything that eats, poops: animals drop layers daily, players carry a server-tracked poop bar, and it all decays into fertilizer.

## Features

- **Poop layers**: a snow-layer-alike block (1-8 layers) that stacks as poop accumulates. Natural accumulation caps at 7 layers; only hand-placement builds a full-height stack. Shovel is the right tool; mining drops one poop item per layer.
- **Animal pooping**: every animal poops at least once per Minecraft day, on its own randomly-seeded schedule (no dawn chorus), with a suitably undignified sound.
- **Llama etiquette**: llamas and trader llamas walk to the nearest existing poop pile within ~16 blocks and go there, founding a communal spot in place when none exists. A llama that can't reach the spot in time has an accident where it stands.
- **Decay into bonemeal**: layers slowly decay via random ticks (a layer lasts on the order of a couple of day cycles). Each decayed layer applies vanilla bonemeal growth to the ground below, or to crops planted alongside, with the green particle burst. Breaking any poop or guano block without a shovel forces the decay instead of dropping anything: one growth charge per layer, eight for a full block.
- **Throwable poop**: the poop item throws like a snowball. Hitting a player inflicts brief nausea and slowness; hitting anything else just splats.
- **Poop block**: four poop items craft a full decorative block, which mines back into itself.
- **Composting**: both the poop item (moderate chance) and poop block (high chance) feed the composter.
- **Player poop bar**: eating fills a persistent 0-100 bar proportionally to nutrition, with a hefty bonus (and a ~30% diarrhea risk) for raw meat, rotten flesh, and suspicious foods. Diarrhea fills the bar rapidly for ~90 seconds under Hunger + Nausea, and persists through relogging. The bar renders as ten poop pips sitting just above the vanilla hunger bar, filling from the right with half-pip steps, styled like the vanilla status rows. Poop voluntarily with a keybind (default `G`, rebindable in the controls screen under the Pandorical category, bar >= 20) or involuntarily at 100. Either way: a layer at your feet, a fart, a hunger point, and a reset bar.

## Village latrines

Villagers poop too: roughly once per day, never at night and never mid-sleep. Where they go depends on the plumbing:

- **The latrine**: a small privy hut with a pit in the floor, its bottom lined with a poop block. Naturally generated villages can include one (it joins the vanilla house pools), and if [village-builder](../village-builder) is installed, growing villages can construct one as a utility building. A villager with business to do walks to the nearest latrine within about 32 blocks and deposits into the pit.
- **The compost pit rule**: poop layers stacked on top of a poop block are allowed to exceed the usual 7-layer cap: the eighth deposit converts the stack into a new full poop block. This works anywhere, not just latrines, so any poop block you place becomes the seed of a slowly rising compost column. A latrine pit therefore fills itself into solid poop blocks over time; clean it out by mining (shovel), which is the harvest.
- **No latrine?** Villagers fall back to the communal street pile (the same one llamas use), or failing that, wherever they happen to be standing. Streets of a latrine-less village will show it.
- **Llama etiquette, amended**: llamas actively avoid latrine stacks and stick to street piles. The latrine is for villagers; the llama knows its place.
- **The poopsmith**: rarely, a nitwit is born to a higher calling. Nothing about the villager changes except that it is, quietly and permanently, a poopsmith, and on Pandorical clients it wears its orange work gloves with pride. Vanilla clients see a plain nitwit.

## The guano economy

Bats finally earn their keep. Every bat drops guano roughly once per day on its own schedule, and the guano falls from the roost: it lands as a pale grey layer on the first surface below, cave floors included. Guano layers stack, decay, and fertilize exactly like poop layers, and a shovel harvests them one guano per layer.

- **Guano**: edible, barely nutritious, always eatable even on a full stomach. That last part matters because eating guano cures active diarrhea on the spot, clearing the Hunger and Nausea that come with it. It also tops the composter chart: one guano is one guaranteed compost level, richer than any poop. Four guano craft a decorative guano block, which composts and burns just as well.
- **Bat box**: a wall-shelf-shaped box that generates guano without needing an actual bat nearby (the guano in the recipe is the lure). It only produces when it would make a decent roost: open sky above, at least 2 blocks of clearance below, and water within about 8 blocks. When happy it makes roughly 3 guano per day and stores up to 6; right-click with an empty hand to collect, and a comparator reads the fill level. Crafted from 7 planks around 1 wild guano, so the first one is always foraged.
- **Gunpowder**: 1 guano + 1 charcoal (charcoal specifically, coal will not do) makes 3 gunpowder. Saltpeter is saltpeter.
- **Dung fuel**: the whole family burns. Poop and poop layers smelt about 1.5 items each, guano and guano layers twice that, and the full blocks run a furnace for 4 of their item's worth. Nothing beats coal per item; the point is having somewhere useful to shovel it all.

## Requires Pandorical

This mod is entirely server-side and depends on [Pandorical](../pandorical) for every client-facing piece: block/item/asset sync (textures ship in this jar; Pandorical's virtual resource pack delivers them), the thrown-poop renderer, the server-pushed poop bar HUD, the poopsmith gloves overlay, and the poop keybind (a Pandorical pooled keybind, so no poopsmith jar is ever needed on a client). Players on Pandorical clients get the HUD and the keybind; vanilla clients still fully participate server-side, they just can't see the bar or go voluntarily. Accidents happen.

Targets the Minecraft, Fabric Loader, and Fabric API versions declared in this mod's `gradle.properties`.

## License

MIT
