package justfatlard.poopsmith;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Slow guano generator. Every check interval the box tests its habitat
 * (open sky above, hanging clearance below, water in range) and, when all
 * three hold, advances toward the next guano: roughly 3 per Minecraft day,
 * pausing at a full store. No actual bat entity is required; the guano in
 * the crafting recipe is the lure, fiction-wise.
 */
public class BatBoxBlockEntity extends BlockEntity {
	public static final int MAX_STORED = 6;

	private static final int CHECK_INTERVAL_TICKS = 200;
	// 40 productive checks * 200 ticks = 8000 ticks per guano: 3 per day
	private static final int CHECKS_PER_GUANO = 40;
	// The water scan is the expensive test, so its result is cached and
	// refreshed only every N checks (~1000 ticks)
	private static final int WATER_RESCAN_CHECKS = 5;
	// A box hangs at head height or higher and the water it drinks from lies at the shore, so
	// the vertical reach matches the horizontal: two blocks missed a lake five below the box.
	private static final int WATER_RANGE_HORIZONTAL = 8;
	private static final int WATER_RANGE_VERTICAL = 8;
	private static final int CLEARANCE_BLOCKS = 2;

	private int stored = 0;
	private int progressChecks = 0;
	private int tickCounter = 0;
	private int checksSinceWaterScan = WATER_RESCAN_CHECKS;
	private boolean waterNearby = false;

	public BatBoxBlockEntity(BlockPos pos, BlockState state) {
		super(Main.BAT_BOX_BLOCK_ENTITY, pos, state);
	}

	public static void serverTick(Level world, BlockPos pos, BlockState state, BatBoxBlockEntity box) {
		if (++box.tickCounter < CHECK_INTERVAL_TICKS) return;
		box.tickCounter = 0;

		if (box.stored >= MAX_STORED) return;
		if (!box.isProductive(world, pos)) return;

		if (++box.progressChecks >= CHECKS_PER_GUANO) {
			box.progressChecks = 0;
			box.stored++;
			box.setChanged();
			world.updateNeighbourForOutputSignal(pos, state.getBlock());
		}
	}

	private boolean isProductive(Level world, BlockPos pos) {
		return blocker(world, pos) == null;
	}

	/**
	 * What stops this box filling, or null when nothing does.
	 *
	 * <p>In the order a player would fix them, and every test runs on every call: the water
	 * scan used to sit behind the sky and clearance tests and only ran when those passed, so a
	 * box under a roof reported no water when it had a lake beside it. The scan is the dear
	 * test, so its answer is kept for a few checks; a box asked before its first check scans
	 * on the spot rather than reporting the empty default.
	 */
	public String blocker(Level world, BlockPos pos) {
		if (++checksSinceWaterScan >= WATER_RESCAN_CHECKS) {
			checksSinceWaterScan = 0;
			waterNearby = scanForWater(world, pos);
		}
		if (!world.canSeeSky(pos.above())) return "No open sky above";
		for (int dy = 1; dy <= CLEARANCE_BLOCKS; dy++) {
			if (world.getBlockState(pos.below(dy)).isSolid()) return "No room to hang below";
		}
		if (!waterNearby) return "No water within " + WATER_RANGE_HORIZONTAL + " blocks";
		return null;
	}

	private static boolean scanForWater(Level world, BlockPos center) {
		for (BlockPos pos : BlockPos.betweenClosed(
				center.offset(-WATER_RANGE_HORIZONTAL, -WATER_RANGE_VERTICAL, -WATER_RANGE_HORIZONTAL),
				center.offset(WATER_RANGE_HORIZONTAL, WATER_RANGE_VERTICAL, WATER_RANGE_HORIZONTAL))) {
			if (world.getFluidState(pos).is(FluidTags.WATER)) return true;
		}
		return false;
	}

	/** Empty-hand harvest: pops the whole store at the player's feet. */
	public boolean harvest(Player player) {
		Level world = this.getLevel();
		if (world == null || stored <= 0) return false;

		Containers.dropItemStack(world, player.getX(), player.getY(), player.getZ(),
			new ItemStack(Main.GUANO_ITEM, stored));
		// Null source: the harvesting player must hear it too
		world.playSound(null, worldPosition, SoundEvents.BEEHIVE_SHEAR, SoundSource.BLOCKS, 0.8F, 1.0F);
		stored = 0;
		setChanged();
		world.updateNeighbourForOutputSignal(worldPosition, this.getBlockState().getBlock());
		return true;
	}

	/** How much guano is waiting, for anything that wants to report it. */
	public int stored() {
		return this.stored;
	}

	/** False when the box will never fill: bats want water in range. Scans if it has not yet. */
	public boolean hasWaterNearby() {
		Level world = this.getLevel();
		if (world != null && checksSinceWaterScan >= WATER_RESCAN_CHECKS) {
			checksSinceWaterScan = 0;
			waterNearby = scanForWater(world, worldPosition);
		}
		return this.waterNearby;
	}

	public int comparatorSignal() {
		return stored == 0 ? 0 : 1 + stored * 14 / MAX_STORED;
	}

	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		Level world = this.getLevel();
		if (world != null && stored > 0) {
			Containers.dropItemStack(world, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
				new ItemStack(Main.GUANO_ITEM, stored));
			stored = 0;
		}
		super.preRemoveSideEffects(pos, state);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putInt("Stored", stored);
		output.putInt("Progress", progressChecks);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		stored = Math.clamp(input.getIntOr("Stored", 0), 0, MAX_STORED);
		progressChecks = Math.max(0, input.getIntOr("Progress", 0));
	}
}
