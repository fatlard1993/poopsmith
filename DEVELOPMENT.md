# Poopsmith - Development Guide

For what the mod is and how it plays, see [README.md](README.md).

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

## Installation

Install server-side alongside its declared dependencies (see `fabric.mod.json`); connecting clients need only Pandorical. Version targets live in `gradle.properties` (Minecraft, loader, Fabric API) and `fabric.mod.json` (Java).
