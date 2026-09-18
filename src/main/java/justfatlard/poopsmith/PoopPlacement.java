package justfatlard.poopsmith;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Shared poop-deposit logic for animals, llamas, and players.
 * Natural accumulation caps at {@link #NATURAL_MAX_LAYERS}, the full eight layers, and the ninth
 * deposit packs the pile into a block where something holds it in: nine poop to a block, the same
 * nine it crafts from and back into. Anything higher starts on that block.
 * On open ground it stops far lower, at {@link #OPEN_STACK_LAYERS}: a heap
 * with nothing holding it in slides sideways rather than growing a tower.
 */
public final class PoopPlacement {
	private PoopPlacement() {}

	public static final int NATURAL_MAX_LAYERS = PoopLayerBlock.MAX_LAYERS;

	/**
	 * Layers a stack reaches on open ground before further deposits slide off
	 * it. Three is what a loose heap holds: past that it wants walls, which is
	 * what makes a latrine pit (or any fenced corner) worth building.
	 */
	public static final int OPEN_STACK_LAYERS = 3;

	/**
	 * How far a sliding deposit looks for somewhere lower, as a real distance.
	 *
	 * <p>One and a half reaches the eight tiles touching the drop and nothing beyond: the corners sit
	 * at just over one and four tenths, so they are in, and everything at two is out. Nine tiles
	 * counting the drop itself, which is as far as something can plausibly roll.
	 */
	private static final double SLIDE_RADIUS = 1.5;

	/** Horizontal radius llamas scan for an existing communal poop spot. */
	public static final int LLAMA_SEARCH_RADIUS = 16;
	private static final int LLAMA_SEARCH_HEIGHT = 4;

	/** Vertical distance a bat's guano falls before giving up (deep shafts). */
	private static final int BAT_DROP_SCAN = 16;

	/**
	 * Deposit one poop layer at or adjacent to {@code origin}: increment an
	 * existing sub-cap layer stack, or found a new single layer on solid
	 * ground. Returns the position written, or empty if nothing fit.
	 *
	 * <p>{@code source} is who to blame, noted for as long as the pile stays
	 * fresh (see {@link PoopOwners}) and unused for anything else. It is a
	 * parameter rather than a call-site chore so that a new way to poop cannot
	 * quietly forget to leave a trail.
	 */
	public static Optional<BlockPos> deposit(ServerLevel world, BlockPos origin, Entity source) {
		Optional<BlockPos> placed = deposit(world, aboveFurniture(world, origin), Main.POOP_LAYER_BLOCK);
		placed.ifPresent(pos -> {
			PoopOwners.record(world, pos, source);
			faceLike(world, pos, source);
		});
		return placed;
	}

	/** Same rules, any layer block: poop for animals/players, guano for bats. */
	public static Optional<BlockPos> deposit(ServerLevel world, BlockPos origin, PoopLayerBlock layerBlock) {
		if (tryDepositAt(world, origin, layerBlock)) return Optional.of(origin);
		// Capped, blocked, or slid off the origin: spread outward, nearest ring
		// first, each ring shuffled so piles grow organically rather than
		// always north-first.
		for (BlockPos pos : spreadOrder(origin, world.getRandom())) {
			if (tryDepositAt(world, pos, layerBlock)) return Optional.of(pos);
		}
		// Every tile in reach is at its resting height, so the whole patch starts climbing
		// together: this goes on whichever pile is lowest, which is what keeps any one of them from
		// getting more than a layer ahead of its neighbours.
		return raiseLowest(world, origin, layerBlock);
	}

	/**
	 * Every tile the deposit could roll to, nearest first, ties in random order.
	 *
	 * <p>A disc rather than square rings: measuring by steps put the diagonals a step and a half
	 * further out than the sides while filling them at the same time, which spreads a heap into a
	 * visible box. Nearest first so a pile grows outward from where it landed, and the shuffle
	 * within a distance stops every heap creeping north before it creeps anywhere else.
	 *
	 * <p>The origin is not in here; the caller has already tried it.
	 */
	private static List<BlockPos> spreadOrder(BlockPos center, RandomSource random) {
		int reach = (int) Math.floor(SLIDE_RADIUS);
		List<BlockPos> positions = new ArrayList<>();
		for (int dx = -reach; dx <= reach; dx++) {
			for (int dz = -reach; dz <= reach; dz++) {
				if (dx == 0 && dz == 0) continue;
				if (Math.sqrt(dx * dx + dz * dz) > SLIDE_RADIUS) continue;
				positions.add(center.offset(dx, 0, dz));
			}
		}
		for (int i = positions.size() - 1; i > 0; i--) {
			Collections.swap(positions, i, random.nextInt(i + 1));
		}
		// Stable, so the shuffle survives as the tie-break within each distance.
		positions.sort(java.util.Comparator.comparingDouble(pos -> pos.distSqr(center)));
		return positions;
	}

	/**
	 * One deposit at one position, up to the resting height a loose heap holds.
	 *
	 * <p>Anything past that is not this method's business: growing the patch beyond three is
	 * {@link #raiseLowest}'s job, and it only ever raises the shortest pile.
	 */
	private static boolean tryDepositAt(ServerLevel world, BlockPos pos, PoopLayerBlock layerBlock) {
		BlockState state = world.getBlockState(pos);
		if (state.is(layerBlock)) {
			int layers = state.getValue(PoopLayerBlock.LAYERS);
			if (layers >= OPEN_STACK_LAYERS) return false;

			world.setBlockAndUpdate(pos, state.setValue(PoopLayerBlock.LAYERS, layers + 1));
			return true;
		}
		if (!state.isAir() && !state.canBeReplaced()) return false;
		BlockState layer = layerBlock.defaultBlockState();
		if (!layer.canSurvive(world, pos)) return false;
		world.setBlockAndUpdate(pos, layer);
		return true;
	}

	/**
	 * Raise the shortest pile in reach, so the patch rises as one.
	 *
	 * <p>Reached once every tile is at its resting height. Always choosing a shortest pile is what
	 * bounds the shape: a pile can only be raised while it is level with the lowest, so no one of
	 * them ever stands more than a single layer above its neighbours, and the patch grows as a
	 * thickening mat rather than as one tower with a skirt.
	 *
	 * <p>What happens at {@link #NATURAL_MAX_LAYERS} is where the two part company. A capped pile of
	 * poop only packs down into a block where something holds it in - a latrine pit, a fenced corner
	 * - because a yard that turned itself into terrain would be a yard nobody dug. Guano packs down
	 * wherever it lies: a roost drops it in the same spot for years and the real stuff is quarried
	 * out of caves in beds, so a bat colony leaves something worth swinging a shovel at.
	 *
	 * <p>Either way the block left behind is sturdy ground for the next stack to start on, which is
	 * how a deposit grows upward instead of stopping at eight.
	 */
	private static Optional<BlockPos> raiseLowest(ServerLevel world, BlockPos origin, PoopLayerBlock layerBlock) {
		BlockPos lowest = null;
		int fewest = Integer.MAX_VALUE;

		for (BlockPos pos : spreadOrder(origin, world.getRandom())) {
			BlockState state = world.getBlockState(pos);
			if (!state.is(layerBlock)) continue;

			int layers = state.getValue(PoopLayerBlock.LAYERS);
			if (layers >= fewest) continue;

			fewest = layers;
			lowest = pos;
		}

		BlockState here = world.getBlockState(origin);
		if (here.is(layerBlock)) {
			int layers = here.getValue(PoopLayerBlock.LAYERS);
			// The origin wins a tie: what lands at your feet stays at your feet where it can.
			if (layers <= fewest) {
				fewest = layers;
				lowest = origin;
			}
		}

		if (lowest == null) return Optional.empty();

		if (fewest < NATURAL_MAX_LAYERS) {
			world.setBlockAndUpdate(lowest, world.getBlockState(lowest)
				.setValue(PoopLayerBlock.LAYERS, fewest + 1));
			return Optional.of(lowest);
		}

		// Everything in reach is capped, so this one packs down if it is allowed to.
		boolean packsAnywhere = layerBlock != Main.POOP_LAYER_BLOCK;
		if (!packsAnywhere && !isContained(world, lowest, layerBlock)) return Optional.empty();

		world.setBlockAndUpdate(lowest, compostBase(layerBlock).defaultBlockState());
		return Optional.of(lowest);
	}

	/**
	 * Whether this tile is held in well enough to pack down rather than stop.
	 *
	 * <p>Two things hold a heap: four walls around it, which is a latrine pit or a fenced corner,
	 * and its own packed block underneath, which is the column of an already-filling pit carrying
	 * on upward.
	 *
	 * <p>Droppings are not walls. A full block of the stuff has a sturdy face like anything else, so
	 * counting it would let heaps standing next to each other wall one another in, and a field of
	 * piles would pack itself into a field of blocks with nobody having dug anything.
	 */
	private static boolean isContained(ServerLevel world, BlockPos pos, PoopLayerBlock layerBlock) {
		if (world.getBlockState(pos.below()).is(compostBase(layerBlock))) return true;

		for (Direction direction : Direction.Plane.HORIZONTAL) {
			BlockPos side = pos.relative(direction);
			BlockState wall = world.getBlockState(side);

			if (isDroppings(wall)) return false;
			if (!wall.isFaceSturdy(world, side, direction.getOpposite())) return false;
		}
		return true;
	}

	/** Any of it, in either form: layers part-way up, or packed down into a solid block. */
	private static boolean isDroppings(BlockState state) {
		return state.is(Main.POOP_LAYER_BLOCK) || state.is(Main.POOP_BLOCK)
			|| state.is(Main.GUANO_LAYER_BLOCK) || state.is(Main.GUANO_BLOCK);
	}

	/** The full block a layer stack of this kind packs down into and then rises on. */
	public static Block compostBase(PoopLayerBlock layerBlock) {
		return layerBlock == Main.POOP_LAYER_BLOCK ? Main.POOP_BLOCK : Main.GUANO_BLOCK;
	}

	/**
	 * Bat deposit: guano falls from the bat (usually a ceiling roost) to the
	 * first spot below that can hold a layer. The scan walks down through air
	 * and replaceables and stops at the first solid obstruction.
	 */
	public static boolean batPoop(ServerLevel world, Entity bat) {
		BlockPos start = bat.blockPosition();
		for (int dy = 0; dy <= BAT_DROP_SCAN; dy++) {
			BlockPos pos = start.below(dy);
			// Guano meeting a water surface sprays there instead of threading
			// down to plant a submerged layer
			if (world.getFluidState(pos).is(FluidTags.WATER)) {
				waterPoopAt(world, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5);
				playSqueak(world, bat);
				return true;
			}
			if (tryDepositAt(world, pos, Main.GUANO_LAYER_BLOCK)) {
				playSqueak(world, bat);
				return true;
			}
			BlockState state = world.getBlockState(pos);
			if (state.is(Main.GUANO_LAYER_BLOCK)) {
				// Landed on a capped stack: spread to an adjacent tile rather
				// than stalling the roost's output forever
				if (deposit(world, pos, Main.GUANO_LAYER_BLOCK).isPresent()) {
					playSqueak(world, bat);
					return true;
				}
				return false;
			}
			if (!state.isAir() && !state.canBeReplaced()) return false;
		}
		return false;
	}

	/** Quieter than the fart: a bat-pitched chirp from the roost. */
	private static void playSqueak(ServerLevel world, Entity bat) {
		RandomSource random = world.getRandom();
		world.playSound(null, bat.getX(), bat.getY(), bat.getZ(),
			SoundEvents.BAT_AMBIENT, SoundSource.NEUTRAL,
			0.4F, 1.4F + random.nextFloat() * 0.3F);
	}

	/**
	 * Aimed deposit with a short downward scan, the batPoop pattern capped for
	 * ground use: deposit at {@code origin} or the first spot below it that
	 * takes a layer, stopping at any solid obstruction. Lets a player at a
	 * latrine pit's edge drop into the pit (where a capped stack on a poop
	 * block converts via the compost-pit rule inside tryDepositAt), and lets a
	 * heap that has stopped stacking slide aside where it sits. Empty when
	 * nothing within {@code maxDrop} could take the layer; callers fall back.
	 */
	/**
	 * Turn a newly started pile to sit the way whoever left it was facing.
	 *
	 * <p>Only a fresh one. Adding to a heap that is already there does not spin it round to face
	 * the latest contributor: it was put down facing a way, and it keeps facing that way.
	 */
	private static void faceLike(ServerLevel world, BlockPos pos, Entity source) {
		BlockState state = world.getBlockState(pos);
		if (!state.hasProperty(PoopLayerBlock.FACING)) return;
		if (state.getValue(PoopLayerBlock.LAYERS) != 1) return;

		world.setBlock(pos, state.setValue(PoopLayerBlock.FACING, source.getDirection()),
			Block.UPDATE_CLIENTS);
	}

	/** What a falling deposit met at the bottom, and where. */
	public record Landing(BlockPos pos, Medium medium) {}

	public enum Medium { GROUND, WATER, LAVA }

	/**
	 * A deposit whose column never offered a landing: nothing lands, but whatever is underneath
	 * is fed.
	 */
	public static void scatterFrom(ServerLevel world, BlockPos origin) {
		BlockPos ground = origin;
		// Bounded rather than walked to the world floor: a deposit over the void has nothing to
		// feed, and a loop that finds that out one block at a time is a loop worth capping.
		for (int fallen = 0; fallen < 384; fallen++) {
			BlockState below = world.getBlockState(ground.below());
			if (!below.isAir() && !below.canBeReplaced()) break;
			ground = ground.below();
		}
		fertilizeAround(world, ground);
	}

	/**
	 * Where a deposit aimed at somebody's feet starts from.
	 *
	 * <p>Feet in a bed, a carpet or a low slab are inside the furniture's own block, and aiming
	 * there hits the furniture: a player standing on a bed could not go on it, only beside it.
	 * When the tile at the feet is taken and the one above is open, the deposit starts above.
	 */
	private static BlockPos aboveFurniture(ServerLevel world, BlockPos origin) {
		BlockState here = world.getBlockState(origin);
		if (here.isAir() || here.canBeReplaced() || here.getBlock() instanceof PoopLayerBlock) return origin;
		BlockState up = world.getBlockState(origin.above());
		return up.isAir() || up.canBeReplaced() ? origin.above() : origin;
	}

	public static Optional<Landing> depositWithDrop(ServerLevel world, BlockPos origin, int maxDrop,
			Entity source) {
		origin = aboveFurniture(world, origin);
		// Whether the aimed spot was empty. It is the difference between "there is nowhere for
		// this to go" and "this exact tile will not hold it", and those want opposite answers.
		boolean roomAtOrigin = false;

		for (int dy = 0; dy <= maxDrop; dy++) {
			BlockPos pos = origin.below(dy);
			// A column that ends in liquid ends AT the liquid: no layer survives underwater
			// (the rule waterPoop already keeps at ground level), and lava needs no help. The
			// caller decides what the arrival looks and sounds like; nothing is placed.
			var fluid = world.getFluidState(pos);
			if (!fluid.isEmpty()) {
				return Optional.of(new Landing(pos, fluid.is(net.minecraft.tags.FluidTags.LAVA)
					? Medium.LAVA : Medium.WATER));
			}
			if (tryDepositAt(world, pos, Main.POOP_LAYER_BLOCK)) {
				PoopOwners.record(world, pos, source);
				faceLike(world, pos, source);
				return Optional.of(new Landing(pos, Medium.GROUND));
			}
			BlockState state = world.getBlockState(pos);
			if (state.is(Main.POOP_LAYER_BLOCK)) {
				// Aimed at a heap that has stopped taking layers: let it slide
				// aside down there rather than bouncing the whole deposit back
				// to the aimer's own feet
				return deposit(world, pos, source).map(p -> new Landing(p, Medium.GROUND));
			}
			if (dy == 0) roomAtOrigin = state.isAir() || state.canBeReplaced();

			if (!state.isAir() && !state.canBeReplaced()) break;
		}

		// The column held nothing. If there was room where it was aimed, the ground under it
		// simply cannot carry a pile - a bottom slab, a stair, anything whose top is not a full
		// face - and the answer is the one a full heap already gets: slide aside and land next
		// to it. Scattering there was the poop dissolving into a puff of fertiliser on ordinary
		// flat ground, which reads as the mechanic failing rather than happening.
		if (roomAtOrigin) {
			return deposit(world, origin, source).map(landed -> new Landing(landed, Medium.GROUND));
		}
		return Optional.empty();
	}

	// Water pooping: no layer survives underwater, so the poop disperses as a
	// brown particle cloud instead (the PoopEntity splat pattern, denser,
	// with a slight drift that reads as dissolving in the current)
	private static final int WATER_POOP_PARTICLES = 24;
	private static final double WATER_POOP_SPREAD = 0.4;
	private static final double WATER_POOP_DRIFT = 0.05;

	/** Waterborne deposit: a dispersing cloud at the entity, muffled fart included. */
	public static boolean waterPoop(ServerLevel world, Entity entity) {
		return waterPoopAt(world, entity.getX(), entity.getY() + entity.getBbHeight() * 0.3, entity.getZ());
	}

	/** Positional variant, e.g. a bat's guano meeting a water surface below. */
	public static boolean waterPoopAt(ServerLevel world, double x, double y, double z) {
		world.sendParticles(
			new net.minecraft.core.particles.ItemParticleOption(
				net.minecraft.core.particles.ParticleTypes.ITEM, Main.POOP_ITEM),
			x, y, z, WATER_POOP_PARTICLES,
			WATER_POOP_SPREAD, WATER_POOP_SPREAD, WATER_POOP_SPREAD, WATER_POOP_DRIFT);
		// The fart, muffled by water: quieter and pitched lower than on land
		RandomSource random = world.getRandom();
		world.playSound(null, x, y, z,
			SoundEvents.PLAYER_BURP, SoundSource.NEUTRAL,
			0.55F, 0.22F + random.nextFloat() * 0.1F);
		return true;
	}

	/** Deposit for a regular animal: at the animal's own feet, or a water cloud when swimming. */
	public static boolean animalPoop(ServerLevel world, Entity animal) {
		if (animal.isInWater()) {
			return waterPoop(world, animal);
		}
		int helpings = AnimalSize.layers(animal);
		// Standing in a crop, it is manure where it falls: the plant gets the growth, and a big
		// animal's double helping feeds the plants round it. That is what makes a pen laid over
		// a field worth the fence: the stock grow the crop as they graze it.
		if (manure(world, animal.blockPosition(), helpings > 1)) {
			playFart(world, animal);
			return true;
		}
		// A second helping for the biggest of them, laid by the same rules as the first: it
		// stacks onto what just landed, or spreads to the next square if that pile is full.
		boolean any = false;
		for (int i = 0; i < helpings; i++) {
			if (deposit(world, animal.blockPosition(), animal).isPresent()) any = true;
		}

		if (any) playFart(world, animal);
		return any;
	}

	/**
	 * Nearest existing poop layer within {@link #LLAMA_SEARCH_RADIUS}: the
	 * communal spot. Layers sitting on a poop block are a latrine stack, not a
	 * street pile: llamas (and street-pooping villagers) keep out of latrines.
	 */
	public static Optional<BlockPos> findNearestPoop(ServerLevel world, BlockPos center) {
		BlockPos best = null;
		double bestDistSq = Double.MAX_VALUE;
		for (BlockPos pos : BlockPos.betweenClosed(
				center.offset(-LLAMA_SEARCH_RADIUS, -LLAMA_SEARCH_HEIGHT, -LLAMA_SEARCH_RADIUS),
				center.offset(LLAMA_SEARCH_RADIUS, LLAMA_SEARCH_HEIGHT, LLAMA_SEARCH_RADIUS))) {
			if (!world.getBlockState(pos).is(Main.POOP_LAYER_BLOCK)) continue;
			if (world.getBlockState(pos.below()).is(Main.POOP_BLOCK)) continue;
			double distSq = pos.distSqr(center);
			if (distSq < bestDistSq) {
				bestDistSq = distSq;
				best = pos.immutable();
			}
		}
		return Optional.ofNullable(best);
	}

	/** Horizontal radius villagers scan for a latrine pit (a poop block column). */
	public static final int LATRINE_SEARCH_RADIUS = 32;
	/**
	 * Vertical half-height of the pit search box, measured from the searcher's
	 * own feet. A village-generated latrine's seed block sits
	 * {@link LatrinePitProcessor#TARGET_PIT_DEPTH} rows below the hut floor a
	 * villager stands on, so anything under 5 cannot see the pit in its own
	 * privy; 8 leaves room for hand-dug pits and for searching from a street
	 * that sits a little above or below the hut.
	 */
	private static final int LATRINE_SEARCH_HEIGHT = 8;
	/**
	 * Column height above a pit's bottom block that still counts as the same
	 * pit. The generated pit is 4 open blocks deep, so the scan needs at least
	 * 4 to walk a fully converted column back up to the rim; 6 keeps a margin
	 * for deeper hand-dug pits and lets a full pit heap a little above grade
	 * before callers treat it as full and fall back to the street.
	 */
	private static final int LATRINE_COLUMN_SCAN = 6;

	/**
	 * Nearest depositable position above a poop block within
	 * {@link #LATRINE_SEARCH_RADIUS}: the latrine target. The column above the
	 * pit's bottom poop block is walked upward through converted poop blocks
	 * and layer stacks to the first position that can take a layer; a column
	 * obstructed by anything else (or taller than the scan cap) is full, so
	 * callers fall back to street pooping.
	 */
	public static Optional<BlockPos> findLatrineTarget(ServerLevel world, BlockPos center) {
		BlockPos best = null;
		double bestDistSq = Double.MAX_VALUE;
		for (BlockPos pos : BlockPos.betweenClosed(
				center.offset(-LATRINE_SEARCH_RADIUS, -LATRINE_SEARCH_HEIGHT, -LATRINE_SEARCH_RADIUS),
				center.offset(LATRINE_SEARCH_RADIUS, LATRINE_SEARCH_HEIGHT, LATRINE_SEARCH_RADIUS))) {
			if (!world.getBlockState(pos).is(Main.POOP_BLOCK)) continue;
			// Walk each column from its bottom block only, so a stack of
			// converted blocks is scanned once
			if (world.getBlockState(pos.below()).is(Main.POOP_BLOCK)) continue;
			BlockPos target = depositableAbove(world, pos.immutable());
			if (target == null) continue;
			double distSq = target.distSqr(center);
			if (distSq < bestDistSq) {
				bestDistSq = distSq;
				best = target;
			}
		}
		return Optional.ofNullable(best);
	}

	/**
	 * Every latrine pit bottom (the bottom-most poop block of a column)
	 * within the latrine scan range. Used by the quest integration to decide
	 * whether a village has a latrine and how full it is.
	 */
	public static java.util.List<BlockPos> findLatrinePits(ServerLevel world, BlockPos center) {
		java.util.List<BlockPos> pits = new java.util.ArrayList<>();
		for (BlockPos pos : BlockPos.betweenClosed(
				center.offset(-LATRINE_SEARCH_RADIUS, -LATRINE_SEARCH_HEIGHT, -LATRINE_SEARCH_RADIUS),
				center.offset(LATRINE_SEARCH_RADIUS, LATRINE_SEARCH_HEIGHT, LATRINE_SEARCH_RADIUS))) {
			if (!world.getBlockState(pos).is(Main.POOP_BLOCK)) continue;
			if (world.getBlockState(pos.below()).is(Main.POOP_BLOCK)) continue;
			pits.add(pos.immutable());
		}
		return pits;
	}

	/**
	 * Open-air poop or guano layer stacks within the latrine scan range:
	 * street piles, llama spots, and accident sites, but NOT layers under a
	 * roof (a covered latrine pit doesn't count as mess). Each layer
	 * blockstate counts once regardless of its layer count.
	 */
	public static int countOpenAirStacks(ServerLevel world, BlockPos center) {
		int count = 0;
		for (BlockPos pos : BlockPos.betweenClosed(
				center.offset(-LATRINE_SEARCH_RADIUS, -LATRINE_SEARCH_HEIGHT, -LATRINE_SEARCH_RADIUS),
				center.offset(LATRINE_SEARCH_RADIUS, LATRINE_SEARCH_HEIGHT, LATRINE_SEARCH_RADIUS))) {
			BlockState state = world.getBlockState(pos);
			if (!state.is(Main.POOP_LAYER_BLOCK) && !state.is(Main.GUANO_LAYER_BLOCK)) continue;
			if (world.canSeeSky(pos.above())) count++;
		}
		return count;
	}

	/** Converted poop blocks stacked above a pit's seed block: how full it is. */
	public static int pitAccumulation(ServerLevel world, BlockPos pitBottom) {
		int count = 0;
		BlockPos cursor = pitBottom.above();
		while (world.getBlockState(cursor).is(Main.POOP_BLOCK)) {
			count++;
			cursor = cursor.above();
		}
		return count;
	}

	private static BlockPos depositableAbove(ServerLevel world, BlockPos pitBottom) {
		BlockPos cursor = pitBottom.above();
		for (int i = 0; i < LATRINE_COLUMN_SCAN; i++) {
			BlockState state = world.getBlockState(cursor);
			if (state.is(Main.POOP_BLOCK)) {
				cursor = cursor.above();
				continue;
			}
			// A layer stack here sits on a poop block by construction, so it
			// either takes another layer or converts: always depositable
			if (state.is(Main.POOP_LAYER_BLOCK)) return cursor;
			if ((state.isAir() || state.canBeReplaced())
					&& Main.POOP_LAYER_BLOCK.defaultBlockState().canSurvive(world, cursor)) {
				return cursor;
			}
			return null;
		}
		return null;
	}

	/**
	 * Vanilla bonemeal growth for one layer's worth of fertility. The support
	 * block below is the primary target (grass spreads plants around the
	 * covered spot); if it isn't growable, the four horizontal neighbors at
	 * layer height catch crops planted beside the pile. Shared by natural
	 * layer decay (PoopLayerBlock.randomTick) and shovel-less forced decay
	 * (the break handler in Main).
	 */
	public static void fertilizeAround(ServerLevel world, BlockPos pos) {
		// The ground this landed on, before anything further off: bare tilled ground takes a seed,
		// which is the one thing bonemeal cannot do for it.
		if (sow(world, pos.below())) return;

		if (tryGrow(world, pos.below())) return;
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			if (tryGrow(world, pos.relative(direction))) return;
		}
		// Diagonal-below ring: catches the grass beside a pile that sits on
		// path, poop block, or other unbonemealable ground
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			if (tryGrow(world, pos.relative(direction).below())) return;
		}
		// Tilled ground beside it, for muck that landed on the path between two rows.
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			if (sow(world, pos.relative(direction).below())) return;
		}
		// Nothing bonemealable in range (streets, latrine pits, bare dirt):
		// the charge escapes as a visible puff so the action never reads dead
		world.levelEvent(net.minecraft.world.level.block.LevelEvent.PARTICLES_AND_SOUND_PLANT_GROWTH, pos, 5);
	}

	/**
	 * Poop aimed at a crop is muck on the crop: one bonemeal's growth for it, and no pile left
	 * standing in the field. A double deuce is more than the one plant's share, so it goes round
	 * the plants beside it too. False only when it was not aimed at a crop at all. A crop that has
	 * grown all it will still takes the deposit - as a puff, to nothing - because the other
	 * answer was to land it as poop, and a pile arriving in a row of crops is a crop lost.
	 */
	public static boolean manure(ServerLevel world, BlockPos aim, boolean generous) {
		if (!isCrop(world.getBlockState(aim))) return false;
		boolean any = tryGrow(world, aim);
		if (generous) {
			for (BlockPos pos : spreadOrder(aim, world.getRandom())) {
				if (isCrop(world.getBlockState(pos)) && tryGrow(world, pos)) any = true;
			}
		}
		if (!any) {
			world.levelEvent(net.minecraft.world.level.block.LevelEvent.PARTICLES_AND_SOUND_PLANT_GROWTH, aim, 5);
		}
		return true;
	}

	private static boolean isCrop(BlockState state) {
		return state.is(BlockTags.CROPS) || state.getBlock() instanceof CropBlock || state.getBlock() instanceof StemBlock;
	}

	/** How far muck carries a seed: the crops it could plausibly have come through. */
	private static final int SEED_REACH = 3;

	/**
	 * Sow bare farmland with whatever grows around it.
	 *
	 * <p>Muck is not sterile. It has been through something that ate a field, and what comes up
	 * out of a dung heap is whatever that field was growing - which is why a midden left alone
	 * sprouts. Bonemeal does nothing at all to tilled ground with nothing in it, so this is the
	 * one place the charge had nowhere to go and the pile just vanished.
	 *
	 * <p>The crop is drawn from the ones standing nearby, counted rather than listed, so a wheat
	 * field mostly reseeds itself and a mixed garden comes up mixed. Nothing growing within reach
	 * means nothing to carry, and the charge falls through to the puff: seed has to come from
	 * somewhere.
	 */
	private static boolean sow(ServerLevel world, BlockPos ground) {
		if (!world.getBlockState(ground).is(Blocks.FARMLAND)) return false;

		// Air, not merely replaceable. A pile part way through rotting is itself replaceable, and
		// planting into one would take the layers still standing with it.
		BlockPos bed = ground.above();
		if (!world.getBlockState(bed).isAir()) return false;

		List<Block> nearby = new ArrayList<>();
		for (BlockPos at : BlockPos.betweenClosed(
				bed.offset(-SEED_REACH, -1, -SEED_REACH), bed.offset(SEED_REACH, 1, SEED_REACH))) {
			if (world.getBlockState(at).getBlock() instanceof CropBlock crop) nearby.add(crop);
		}
		if (nearby.isEmpty()) return false;

		BlockState sown = nearby.get(world.getRandom().nextInt(nearby.size())).defaultBlockState();
		if (!sown.canSurvive(world, bed)) return false;

		world.setBlockAndUpdate(bed, sown);
		world.levelEvent(net.minecraft.world.level.block.LevelEvent.PARTICLES_AND_SOUND_PLANT_GROWTH, bed, 0);
		return true;
	}

	private static boolean tryGrow(ServerLevel world, BlockPos pos) {
		net.minecraft.world.item.ItemStack boneMeal =
			new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BONE_MEAL);
		if (net.minecraft.world.item.BoneMealItem.growCrop(boneMeal, world, pos)) {
			world.levelEvent(net.minecraft.world.level.block.LevelEvent.PARTICLES_AND_SOUND_PLANT_GROWTH, pos, 15);
			return true;
		}
		return false;
	}

	/**
	 * The fart. No vanilla fart exists, so this is a burp pitched down into
	 * flatulence range; kept in one place so the sound stays tunable.
	 * The null first argument is deliberate: the actor must hear it too.
	 */
	public static void playFart(ServerLevel world, Entity source) {
		RandomSource random = world.getRandom();
		world.playSound(null, source.getX(), source.getY(), source.getZ(),
			SoundEvents.PLAYER_BURP, SoundSource.NEUTRAL,
			1.0F, 0.35F + random.nextFloat() * 0.2F);
	}
}
