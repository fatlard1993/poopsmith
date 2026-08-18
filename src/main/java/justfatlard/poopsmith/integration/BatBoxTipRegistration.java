package justfatlard.poopsmith.integration;

import justfatlard.block_tip.api.BlockTipApi;
import justfatlard.poopsmith.BatBoxBlockEntity;
import justfatlard.poopsmith.Main;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * What a bat box has in it, and why it might have nothing.
 *
 * <p>A hive tells you how much honey is in it by dripping, and a bat box tells
 * you nothing at all. Worse, it has a requirement that fails silently: no water
 * within range and it will sit there forever looking exactly like one that is
 * working, which is a bad afternoon for whoever built it.
 *
 * <p>So the line leads with the fault when there is one. How full it is can wait
 * behind knowing it will never fill.
 */
public final class BatBoxTipRegistration {
	private BatBoxTipRegistration() {}

	public static void register() {
		BlockTipApi.describe((level, pos, state, player) -> {
			if (!state.is(Main.BAT_BOX_BLOCK)) return null;

			BlockEntity blockEntity = level.getBlockEntity(pos);
			if (!(blockEntity instanceof BatBoxBlockEntity box)) return null;

			if (!box.hasWaterNearby()) return "No water in range";

			int stored = box.stored();
			if (stored <= 0) return "Empty";
			if (stored >= BatBoxBlockEntity.MAX_STORED) return "Full";

			return "Guano " + stored + "/" + BatBoxBlockEntity.MAX_STORED;
		});
	}
}
