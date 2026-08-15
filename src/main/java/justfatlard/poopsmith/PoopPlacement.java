package justfatlard.poopsmith;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

/**
 * Shared poop-deposit logic for animals, llamas, and players.
 * Natural accumulation caps at {@link #NATURAL_MAX_LAYERS} (7): the world
 * never fills a full-height stack on its own; only hand-placement reaches 8.
 */
public final class PoopPlacement {
	private PoopPlacement() {}

	public static final int NATURAL_MAX_LAYERS = 7;

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
		// Full stack (or blocked) at the origin: spread to an adjacent tile.
		// Shuffled so piles grow outward organically instead of always north-first.
		for (Direction direction : Direction.Plane.HORIZONTAL.shuffledCopy(world.getRandom())) {
			BlockPos side = origin.relative(direction);
			if (tryDepositAt(world, side, layerBlock)) return Optional.of(side);
		}
		return Optional.empty();
	}

	private static boolean tryDepositAt(ServerLevel world, BlockPos pos, PoopLayerBlock layerBlock) {
		BlockState state = world.getBlockState(pos);
		if (state.is(layerBlock)) {
			int layers = state.getValue(PoopLayerBlock.LAYERS);
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
	 * Bat deposit: guano falls from the bat (usually a ceiling roost) to the
	 * first spot below that can hold a layer. The scan walks down through air
	 * and replaceables and stops at the first solid obstruction.
	 */
	public static boolean batPoop(ServerLevel world, Entity bat) {
		BlockPos start = bat.blockPosition();
		for (int dy = 0; dy <= BAT_DROP_SCAN; dy++) {
			BlockPos pos = start.below(dy);
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

	/** Deposit for a regular animal: at the animal's own feet. */
	public static boolean animalPoop(ServerLevel world, Entity animal) {
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
	private static final int LATRINE_SEARCH_HEIGHT = 4;
	/** Column height above a pit's bottom block that still counts as the same pit. */
	private static final int LATRINE_COLUMN_SCAN = 4;

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
