# Poopsmith

A Fabric mod where everything that eats, poops: animals drop layers daily, players carry a server-tracked poop bar, and it all decays into fertilizer.

## Features

- **Poop layers**: a snow-layer-alike block (1-8 layers) that stacks as poop accumulates. Natural accumulation caps at 7 layers; only hand-placement builds a full-height stack. Shovel is the right tool; mining drops one poop item per layer.
- **Animal pooping**: every animal poops at least once per Minecraft day, on its own randomly-seeded schedule (no dawn chorus), with a suitably undignified sound. Water animals (fish, squid, dolphins) do it too, in the water, as fish do: a brown cloud that disperses in the current instead of a layer, which also applies to anyone or anything pooping while swimming.
- **Llama etiquette**: llamas and trader llamas walk to the nearest existing poop pile within ~16 blocks and go there, founding a communal spot in place when none exists. A llama that can't reach the spot in time has an accident where it stands.
- **Decay into bonemeal**: layers slowly decay via random ticks (a layer lasts on the order of a couple of day cycles). Each decayed layer applies vanilla bonemeal growth to the ground below, or to crops planted alongside, with the green particle burst. Breaking any poop or guano block without a shovel forces the decay instead of dropping anything: one growth charge per layer, eight for a full block.
- **Throwable poop**: the poop item throws like a snowball. Hitting a player inflicts brief nausea and slowness. Landing on the ground fertilises where it lands, one layer's worth, so a thrown handful is muck arriving all at once rather than only a prank.
- **Poop block**: four poop items craft a full decorative block, which mines back into itself.
- **Composting**: both the poop item (moderate chance) and poop block (high chance) feed the composter.
- **Player poop bar**: eating fills a persistent 0-100 bar proportionally to nutrition, with a hefty bonus (and a ~30% diarrhea risk) for raw meat, rotten flesh, and suspicious foods. Diarrhea fills the bar rapidly for ~90 seconds under Hunger + Nausea, and persists through relogging. The bar renders as a digestive tract that takes over the vanilla hunger bar's exact rectangle and no more: a stomach at the left filling with amber as you eat, its outlet feeding a pink intestine that squiggles rightward and fills with brown as you don't poop, and a sphincter at the end that a poop icon briefly pops out of when you do. Clients whose Pandorical build can suppress vanilla HUD elements get it in place of the drumsticks; older ones keep the drumsticks and get the tract one row higher. Poop voluntarily with a keybind (default `G`, rebindable in the controls screen under the Pandorical category, bar >= 20) or involuntarily at 100. Either way: a layer at your feet, a fart, a hunger point, and a reset bar. Sneak while pooping to aim it one block behind you, dropping into whatever is down there, which is exactly how you use a latrine pit with dignity. Also: your first ever step into the Nether carries a 50% chance of an immediate accident, rolled exactly once per player, regardless of how empty you thought you were.

## Village latrines

Villagers poop too: roughly once per day, never at night and never mid-sleep. Where they go depends on the plumbing:

- **The latrine**: a small privy hut whose floor opens onto a pit lined at the bottom with a poop block. It comes in one variant per village biome, built from the same materials vanilla's own houses use there (oak in plains, spruce in taiga, spruce over packed ice with a snow-capped roof in snowy, acacia in savanna, and sandstone with a slab roof and open windows in desert), so a village gets the privy that matches its houses. Naturally generated villages can include one (it joins the vanilla house pools) and get the deep four-block pit, dug out under the hut as the structure is placed; if [village-builder](../village-builder) is installed, growing villages can construct the variant matching their own biome as a utility building, with the shallower pit the template itself carries. The door sits flush with the street, no step: a villager with business to do walks to the nearest latrine within about 32 blocks, stands at the pit rim, and deposits into the pit.
- **The compost pit rule**: poop layers stacked on top of a poop block are allowed to exceed the usual 7-layer cap: the eighth deposit converts the stack into a new full poop block. This works anywhere, not just latrines, so any poop block you place becomes the seed of a slowly rising compost column. A latrine pit therefore fills itself into solid poop blocks over time; clean it out by mining (shovel), which is the harvest.
- **No latrine?** Villagers fall back to the communal street pile (the same one llamas use), or failing that, wherever they happen to be standing. Streets of a latrine-less village will show it.
- **Llama etiquette, amended**: llamas actively avoid latrine stacks and stick to street piles. The latrine is for villagers; the llama knows its place.
- **Latrine quests**: with [village-quests](../village-quests) installed, villagers hand out latrine work: shoveling a town whose streets have accumulated too many open-air piles, cleaning out a pit that has filled with converted poop blocks (mine and deliver the proof), and digging a village its first latrine, with the quest text teaching the seed-block mechanic outright. The dig reward includes spare seed blocks, so the practice spreads. And mind where you go: a villager who watches you poop in the open remembers, and will bring it up. A covered pit keeps your business your own.
- **The poopsmith**: rarely, a nitwit is born to a higher calling. Nothing about the villager changes except that it is, quietly and permanently, a poopsmith, and on Pandorical clients it wears its orange work gloves with pride. Vanilla clients see a plain nitwit.

## The guano economy

Bats finally earn their keep. Every bat drops guano roughly once per day on its own schedule, and the guano falls from the roost: it lands as a pale grey layer on the first surface below, cave floors included. Guano layers stack, decay, and fertilize exactly like poop layers, and a shovel harvests them one guano per layer.

- **Guano**: edible, barely nutritious, always eatable even on a full stomach. That last part matters because eating guano cures active diarrhea on the spot, clearing the Hunger and Nausea that come with it. It also tops the composter chart: one guano is one guaranteed compost level, richer than any poop. Four guano craft a decorative guano block, which composts and burns just as well.
- **Bat box**: a wall-shelf-shaped box that generates guano without needing an actual bat nearby (the guano in the recipe is the lure). It only produces when it would make a decent roost: open sky above, at least 2 blocks of clearance below, and water within about 8 blocks. When happy it makes roughly 3 guano per day and stores up to 6; right-click with an empty hand to collect, and a comparator reads the fill level. Crafted from 7 planks around 1 wild guano, so the first one is always foraged.
- **Gunpowder**: 1 guano + 1 charcoal + 1 sulfur makes 5 gunpowder. Charcoal specifically, coal will not do, and either ordinary or potent sulfur works. Saltpeter, charcoal and brimstone: the real recipe, and the guano is the saltpeter.
- **Dung fuel**: the whole family burns. Poop and poop layers smelt about 1.5 items each, guano and guano layers twice that, and the full blocks run a furnace for 4 of their item's worth. Nothing beats coal per item; the point is having somewhere useful to shovel it all.

## Pandorical

Poopsmith runs server-side, and Pandorical is a hard dependency: it carries every client-facing piece. Block, item and asset sync (the textures ship in this jar; Pandorical's virtual resource pack delivers them), the thrown-poop renderer, the digestive HUD including its takeover of vanilla's hunger bar, the poopsmith's gloves overlay, and the poop keybind, which is a pooled Pandorical keybind so no poopsmith jar ever reaches a client.

Clients are the optional half. A player on a Pandorical client gets the bar and the keybind; a player on a vanilla client participates fully server-side but cannot see the bar or go voluntarily. Accidents happen.

## Installation

Install server-side alongside its declared dependencies (see `fabric.mod.json`); connecting clients need only Pandorical. Version targets live in `gradle.properties` (Minecraft, loader, Fabric API) and `fabric.mod.json` (Java).

## License

MIT, see [LICENSE](LICENSE).
