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
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Shared poop-deposit logic for animals, llamas, and players.
 * Natural accumulation caps at {@link #NATURAL_MAX_LAYERS} (7): the world
 * never fills a full-height stack on its own; only hand-placement reaches 8.
 * On open ground it stops far lower, at {@link #OPEN_STACK_LAYERS}: a heap
 * with nothing holding it in slides sideways rather than growing a tower.
 */
public final class PoopPlacement {
	private PoopPlacement() {}

	public static final int NATURAL_MAX_LAYERS = 7;

	/**
	 * Layers a stack reaches on open ground before further deposits slide off
	 * it. Two is what a loose heap holds: past that it wants walls, which is
	 * what makes a latrine pit (or any fenced corner) worth building.
	 */
	public static final int OPEN_STACK_LAYERS = 2;

	/**
	 * How far a sliding deposit looks for somewhere lower. One ring is the
	 * eight tiles it could plausibly roll onto; the second catches a busy spot
	 * (a llama's communal pile) already covered to the first ring's edge.
	 */
	private static final int SLIDE_RADIUS = 2;

	/** Horizontal radius llamas scan for an existing communal poop spot. */
	public static final int LLAMA_SEARCH_RADIUS = 16;
	private static final int LLAMA_SEARCH_HEIGHT = 4;

	/** Vertical distance a bat's guano falls before giving up (deep shafts). */
	private static final int BAT_DROP_SCAN = 16;

	/**
	 * Deposit one poop layer at or adjacent to {@code origin}: increment an
	 * existing sub-cap layer stack, or found a new single layer on solid
	 * ground. Returns the position written, or empty if nothing fit.
	 */
	public static Optional<BlockPos> deposit(ServerLevel world, BlockPos origin) {
		return deposit(world, origin, Main.POOP_LAYER_BLOCK);
	}

	/** Same rules, any layer block: poop for animals/players, guano for bats. */
	public static Optional<BlockPos> deposit(ServerLevel world, BlockPos origin, PoopLayerBlock layerBlock) {
		if (tryDepositAt(world, origin, layerBlock)) return Optional.of(origin);
		// Capped, blocked, or slid off the origin: spread outward, nearest ring
		// first, each ring shuffled so piles grow organically rather than
		// always north-first.
		for (int radius = 1; radius <= SLIDE_RADIUS; radius++) {
			for (BlockPos pos : shuffledRing(origin, radius, world.getRandom())) {
				if (tryDepositAt(world, pos, layerBlock)) return Optional.of(pos);
			}
		}
		// Nowhere left to slide: the whole yard is covered, so the origin takes
		// it after all (up to the natural cap). Better a tall pile than a
		// deposit that silently evaporates.
		if (tryDepositAt(world, origin, layerBlock, true)) return Optional.of(origin);
		return Optional.empty();
	}

	/** The hollow square exactly {@code radius} tiles out, same level, in random order. */
	private static List<BlockPos> shuffledRing(BlockPos center, int radius, RandomSource random) {
		List<BlockPos> positions = new ArrayList<>();
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
				positions.add(center.offset(dx, 0, dz));
			}
		}
		for (int i = positions.size() - 1; i > 0; i--) {
			Collections.swap(positions, i, random.nextInt(i + 1));
		}
		return positions;
	}

	private static boolean tryDepositAt(ServerLevel world, BlockPos pos, PoopLayerBlock layerBlock) {
		return tryDepositAt(world, pos, layerBlock, false);
	}

	/**
	 * One deposit at one position. {@code forceStack} skips the open-ground
	 * slide rule: only the last-resort path in {@link #deposit} sets it, once
	 * every tile in slide range has been tried.
	 */
	private static boolean tryDepositAt(ServerLevel world, BlockPos pos, PoopLayerBlock layerBlock, boolean forceStack) {
		BlockState state = world.getBlockState(pos);
		if (state.is(layerBlock)) {
			int layers = state.getValue(PoopLayerBlock.LAYERS);
			if (layers >= OPEN_STACK_LAYERS && !forceStack && !isContained(world, pos, layerBlock)) {
				// Loose heap at its resting height: this one slides off
				return false;
			}
			if (layers >= NATURAL_MAX_LAYERS) {
				// Compost-pit rule: a capped stack supported by a poop block
				// converts to a full poop block instead of refusing (7 layers
				// plus this deposit is 8, a full block). Poop only; guano
				// keeps the hard cap.
				if (layerBlock == Main.POOP_LAYER_BLOCK
						&& world.getBlockState(pos.below()).is(Main.POOP_BLOCK)) {
					world.setBlockAndUpdate(pos, Main.POOP_BLOCK.defaultBlockState());
					return true;
				}
				return false;
			}
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
	 * Whether a stack here may grow past {@link #OPEN_STACK_LAYERS}. Two things
	 * hold a heap together: walls on all four sides (a latrine pit, a fenced
	 * corner, a hole you dug), and its own solid block underneath, which is a
	 * compost column packing down rather than a pile spreading out. The second
	 * is what keeps a placed poop block working as a compost seed anywhere.
	 */
	private static boolean isContained(ServerLevel world, BlockPos pos, PoopLayerBlock layerBlock) {
		if (world.getBlockState(pos.below()).is(compostBase(layerBlock))) return true;
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			BlockPos side = pos.relative(direction);
			if (!world.getBlockState(side).isFaceSturdy(world, side, direction.getOpposite())) {
				return false;
			}
		}
		return true;
	}

	/** The full block a layer stack of this kind composts into and rises on. */
	private static Block compostBase(PoopLayerBlock layerBlock) {
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
	public static Optional<BlockPos> depositWithDrop(ServerLevel world, BlockPos origin, int maxDrop) {
		for (int dy = 0; dy <= maxDrop; dy++) {
			BlockPos pos = origin.below(dy);
			if (tryDepositAt(world, pos, Main.POOP_LAYER_BLOCK)) return Optional.of(pos);
			BlockState state = world.getBlockState(pos);
			if (state.is(Main.POOP_LAYER_BLOCK)) {
				// Aimed at a heap that has stopped taking layers: let it slide
				// aside down there rather than bouncing the whole deposit back
				// to the aimer's own feet
				return deposit(world, pos, Main.POOP_LAYER_BLOCK);
			}
			if (!state.isAir() && !state.canBeReplaced()) {
				return Optional.empty();
			}
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
		Optional<BlockPos> placed = deposit(world, animal.blockPosition());
		if (placed.isPresent()) {
			playFart(world, animal);
			return true;
		}
		return false;
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
		if (tryGrow(world, pos.below())) return;
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			if (tryGrow(world, pos.relative(direction))) return;
		}
		// Diagonal-below ring: catches the grass beside a pile that sits on
		// path, poop block, or other unbonemealable ground
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			if (tryGrow(world, pos.relative(direction).below())) return;
		}
		// Nothing bonemealable in range (streets, latrine pits, bare dirt):
		// the charge escapes as a visible puff so the action never reads dead
		world.levelEvent(net.minecraft.world.level.block.LevelEvent.PARTICLES_AND_SOUND_PLANT_GROWTH, pos, 5);
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
