# Poopsmith

A Minecraft Fabric mod. Everything that eats, poops.

## What This Mod Does

Animals drop layers on their own daily schedule, players carry a server-tracked digestive tract, villagers use the privy if the village built one, bats pay their way in guano, and all of it rots down into fertiliser. It is a nutrient cycle with a punchline, and the punchline is load-bearing: the reason to shovel the street is that the shovelling is worth something.

## Piles

A snow-layer-alike block (1-8 layers) that stacks as poop accumulates. The first two layers render as chunky piles rather than flat sheets, and on open ground that is as high as it gets: a third deposit slides to a neighbouring tile instead, so a busy spot spreads into a wide field rather than growing a tower. Stacking higher takes containment, which is either walls on all four sides (a latrine pit, a fenced corner, a hole you dug) or a poop block underneath. Natural accumulation caps at 7 layers; only hand-placement builds a full-height stack. Shovel is the right tool; mining drops one poop item per layer.

**Nothing you can trip over.** A pile has no collision at any depth, so a herd cannot wall itself into a corner of its own pen, and a path through a field stays a path.

**Bigger animals leave more, and sooner.** How much and how often comes from the space the animal takes up - width squared by height, vanilla's own reckoning, and the same test farmland already uses to decide who is heavy enough to trample it. A rabbit is 0.08 and waits twice as long as usual; a cow is 1.1 and sets the pace; a horse is 3.1 and comes round a little sooner; a camel is 6.9 and leaves two layers when it goes. No table of entity ids to maintain, so a modded moose sorts itself.

## Flies

A loose pile of one or two layers draws a few near-black specks zig-zagging over it in short darting legs, close up and during daylight hours only. Put any block on top, or stack it higher than open ground allows, and they stop. They are ordinary particles sent by the server, so vanilla clients see them too; guano attracts none, and caves stay quiet.

## Reading A Pile

With [block-tip](../block-tip) installed, a pile of one or two layers names what left it and how cold the trail has gone: `Cow, fresh, north`, then `warm`, then `soft`, then just `Old`. A hundred seconds a band, so a pile that still talks is a quarter of a day old at most. The kind only, never the individual. The heading is the way it was facing as it went and only the first two bands carry it: cold sign keeps its maker, not its direction. A pile two different animals have been at reads Old at any age. A stack taller than open ground allows says nothing at all: it is a heap somebody built, not a trail somebody left, so the piles that talk are exactly the wild ones out in the street and a latrine keeps its users anonymous. None of it is written to disk: a restart forgets every trail.

## Who Goes, And Where

Every animal poops at least once per Minecraft day, on its own randomly-seeded schedule (no dawn chorus), with a suitably undignified sound. Water animals (fish, squid, dolphins) do it too, in the water, as fish do: a brown cloud that disperses in the current instead of a layer, which also applies to anyone or anything pooping while swimming.

**Llamas.** llamas and trader llamas walk to the nearest existing poop pile within ~16 blocks and go there, founding a communal spot in place when none exists. A llama that can't reach the spot in time has an accident where it stands.

## Shovelling It

Shovel is the right tool. Mining a stack by hand forces the decay instead, so the growth still happens and the items do not.

**Pooper Scooper** is a shovel enchantment of three levels, ordinary enough to turn up in an enchanting table, on a villager's trades, or in loot. Each level finds one more poop or guano in a pile than was strictly in it, so a full stack under a level III shovel comes up three richer. Somebody has to want this job.

Layers slowly decay via random ticks (a layer lasts on the order of a couple of day cycles). Each decayed layer applies vanilla bonemeal growth to the ground below, or to crops planted alongside, with the green particle burst. Breaking any poop or guano block without a shovel forces the decay instead of dropping anything: one growth charge per layer, eight for a full block.

## The Poop Item

The poop item throws like a snowball. Hitting a player inflicts brief nausea and slowness. Landing on the ground fertilises where it lands, one layer's worth, so a thrown handful is muck arriving all at once rather than only a prank.

**Poop block.** four poop items craft a full decorative block, which mines back into itself.

**Composting.** both the poop item (moderate chance) and poop block (high chance) feed the composter.

## The Digestive Tract

Eating fills a persistent 0-100 bar proportionally to nutrition, with a hefty bonus (and a ~30% diarrhea risk) for raw meat, rotten flesh, and suspicious foods. Diarrhea fills the bar rapidly for ~90 seconds under Hunger + Nausea, and persists through relogging. The bar renders as a digestive tract that takes over the vanilla hunger bar's exact rectangle and no more: a stomach at the left filling with amber as you eat, its outlet feeding a pink intestine that squiggles rightward and fills with brown as you don't poop, and a sphincter at the end that a poop icon briefly pops out of when you do. Clients whose Pandorical build can suppress vanilla HUD elements get it in place of the drumsticks; older ones keep the drumsticks and get the tract one row higher. Poop voluntarily with a keybind (default `G`, rebindable in the controls screen under the Pandorical category, bar >= 20) or involuntarily at 100. Either way: a layer at your feet, a fart, a hunger point, and a reset bar. Sneak while pooping to aim it one block behind you, dropping into whatever is down there, which is exactly how you use a latrine pit with dignity. Also: your first ever step into the Nether carries a 50% chance of an immediate accident, rolled exactly once per player, regardless of how empty you thought you were.

The bar is per player and survives a relog. It does **not** survive a death: you come back empty, which is the one mercy in the whole system.

## Bed Accidents

Going to sleep on a full intestine is a roll of the dice. Nothing under 70, ramping to a 1-in-3 chance at a full bar, and a flat 60% if you turn in with diarrhea. It lands a couple of seconds into the sleep screen, close enough to hear, and the bed comes out of the night brown whatever colour it went in: it drops as a brown bed too, so the only cleanup is a new bed. You wake to a quiet "Not again...". Straw beds have no wool to stain and get away with the noise alone.

## Village Latrines

Villagers poop too: roughly once per day, never at night and never mid-sleep. Where they go depends on the plumbing:

- **The latrine**: a small privy hut whose floor opens onto a pit lined at the bottom with a poop block. It comes in one variant per village biome, built from the same materials vanilla's own houses use there (oak in plains, spruce in taiga, spruce over packed ice with a snow-capped roof in snowy, acacia in savanna, and sandstone with a slab roof and open windows in desert), so a village gets the privy that matches its houses. Naturally generated villages can include one (it joins the vanilla house pools) and get the deep four-block pit, dug out under the hut as the structure is placed; if [village-builder](../village-builder) is installed, growing villages can construct the variant matching their own biome as a utility building, with the shallower pit the template itself carries. The door sits flush with the street, no step: a villager with business to do walks to the nearest latrine within about 32 blocks, stands at the pit rim, and deposits into the pit.
- **The compost pit rule**: poop layers stacked on top of a poop block are allowed to exceed the usual 7-layer cap: the eighth deposit converts the stack into a new full poop block. This works anywhere, not just latrines, so any poop block you place becomes the seed of a slowly rising compost column: standing on one counts as contained, so the stack above it keeps building instead of sliding off. A latrine pit therefore fills itself into solid poop blocks over time; clean it out by mining (shovel), which is the harvest.
- **No latrine?** Villagers fall back to the communal street pile (the same one llamas use), or failing that, wherever they happen to be standing. Streets of a latrine-less village will show it.
- **Llama etiquette, amended**: llamas actively avoid latrine stacks and stick to street piles. The latrine is for villagers; the llama knows its place.
- **Latrine quests**: with [village-quests](../village-quests) installed, villagers hand out latrine work: shoveling a town whose streets have accumulated too many open-air piles, cleaning out a pit that has filled with converted poop blocks (mine and deliver the proof), and digging a village its first latrine, with the quest text teaching the seed-block mechanic outright. The dig reward includes spare seed blocks, so the practice spreads. And mind where you go: a villager who watches you poop in the open remembers, and will bring it up. A covered pit keeps your business your own.
- **The poopsmith**: rarely, a nitwit is born to a higher calling. Nothing about the villager changes except that it is, quietly and permanently, a poopsmith, and on Pandorical clients it wears its orange work gloves with pride. Vanilla clients see a plain nitwit.

## The Guano Economy

Bats finally earn their keep. Every bat drops guano roughly once per day on its own schedule, and the guano falls from the roost: it lands as a pale grey layer on the first surface below, cave floors included. Guano layers stack, decay, and fertilize exactly like poop layers, and a shovel harvests them one guano per layer.

- **Guano**: edible, barely nutritious, always eatable even on a full stomach. That last part matters because eating guano cures active diarrhea on the spot, clearing the Hunger and Nausea that come with it. It also tops the composter chart: one guano is one guaranteed compost level, richer than any poop. Four guano craft a decorative guano block, which composts and burns just as well.
- **Bat box**: a wall-shelf-shaped box that generates guano without needing an actual bat nearby (the guano in the recipe is the lure). It only produces when it would make a decent roost: open sky above, at least 2 blocks of clearance below, and water within about 8 blocks. When happy it makes roughly 3 guano per day and stores up to 6; right-click with an empty hand to collect, and a comparator reads the fill level. Crafted from 7 planks around 1 wild guano, so the first one is always foraged.
- **Gunpowder**: 1 guano + 1 charcoal + 1 sulfur makes 5 gunpowder. Charcoal specifically, coal will not do, and either ordinary or potent sulfur works. Saltpeter, charcoal and brimstone: the real recipe, and the guano is the saltpeter.
- **Dung fuel**: the whole family burns. Poop and poop layers smelt about 1.5 items each, guano and guano layers twice that, and the full blocks run a furnace for 4 of their item's worth. Nothing beats coal per item; the point is having somewhere useful to shovel it all.

## Source Map

| File | What is in it |
|---|---|
| `PoopLayerBlock.java` | The stacking block: containment, spread, decay, no collision |
| `PoopPlacement.java` | Where a deposit actually lands, and what it does when it cannot |
| `AnimalSize.java` | How much an animal leaves and how often, from how big it is |
| `PoopOwners.java` | Who left a pile and how cold the trail is, for block-tip |
| `PoopFlies.java` | The specks over a loose pile |
| `PoopUrge.java` | Every animal's own randomly-seeded schedule |
| `LlamaPoopGoal.java` | Walking to the communal spot |
| `player/PlayerPoopManager.java` | The bar, diarrhea, going voluntarily and otherwise |
| `player/DigestiveHud.java` | The stomach, intestine and sphincter, drawn over vanilla's hunger bar |
| `player/BedAccident.java` | Turning in on a full intestine |
| `BatBoxBlock.java` | The roost that makes guano without a bat |
| `LatrineStructureInjector.java` | Putting a privy in the village house pool |
| `LatrinePitProcessor.java` | Digging the pit out under it |
| `integration/` | block-tip tips, village-quests work, village-builder construction |

## Pandorical

Poopsmith runs server-side, and Pandorical is a hard dependency: it carries every client-facing piece. Block, item and asset sync (the textures ship in this jar; Pandorical's virtual resource pack delivers them), the thrown-poop renderer, the digestive HUD including its takeover of vanilla's hunger bar, the poopsmith's gloves overlay, and the poop keybind, which is a pooled Pandorical keybind so no poopsmith jar ever reaches a client.

Clients are the optional half. A player on a Pandorical client gets the bar and the keybind; a player on a vanilla client participates fully server-side but cannot see the bar or go voluntarily. Accidents happen.

## Installation

Install server-side alongside its declared dependencies (see `fabric.mod.json`); connecting clients need only Pandorical. Version targets live in `gradle.properties` (Minecraft, loader, Fabric API) and `fabric.mod.json` (Java).

## License

MIT, see [LICENSE](LICENSE).
