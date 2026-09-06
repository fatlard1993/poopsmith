package justfatlard.poopsmith.integration;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import justfatlard.poopsmith.Main;
import justfatlard.village_quests.VillageQuests;
import justfatlard.village_quests.Village;
import justfatlard.village_quests.reputation.ReputationEvent;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Clearing the streets is worth something to the people who live on them.
 *
 * <p>Scooping a pile inside a town is the smallest civic act the mod has, and it was worth nothing:
 * the town's own quest for it existed, but the plain act of tidying up outside a quest went
 * unnoticed. It now pays the same as tending a lamp, which is the other unglamorous thing you can
 * do for a village without being asked - and, like that one, it says so with the green motes that
 * mean the village noticed.
 *
 * <p>Only inside a village, and only on a cooldown. Poop comes back on its own - that is the whole
 * point of the llamas - so an uncapped payout would make a pen of them a reputation machine.
 */
public final class StreetSweeping {
	private StreetSweeping() {}

	/**
	 * How far from the centre still counts as the town. Matches the bounds Village Quests uses for
	 * its own placement events, so "in the village" means the same thing to both mods.
	 */
	private static final int VILLAGE_BOUNDS_RADIUS = 64;

	/**
	 * One payout a minute, however much is swept in it. Sweeping a street is several piles and
	 * should read as one good deed, not as several.
	 */
	private static final long COOLDOWN_MS = 60_000L;

	private static final Map<UUID, Long> lastCredited = new HashMap<>();

	public static void register() {
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			if (!(level instanceof ServerLevel world)) return;
			if (!(player instanceof ServerPlayer sweeper)) return;
			if (!state.is(Main.POOP_LAYER_BLOCK) && !state.is(Main.GUANO_LAYER_BLOCK)) return;

			credit(world, sweeper, pos);
		});
	}

	private static void credit(ServerLevel world, ServerPlayer sweeper, BlockPos pos) {
		Village village = VillageQuests.getCachedVillage(sweeper);
		if (village == null) return;

		BlockPos centre = village.getCenter();
		if (centre == null || !pos.closerThan(centre, VILLAGE_BOUNDS_RADIUS)) return;

		long now = System.currentTimeMillis();
		Long last = lastCredited.get(sweeper.getUUID());
		if (last != null && now - last < COOLDOWN_MS) return;
		lastCredited.put(sweeper.getUUID(), now);
		lastCredited.entrySet().removeIf(entry -> now - entry.getValue() > COOLDOWN_MS * 2L);

		VillageQuests.getReputationManager()
			.applyReputationEvent(sweeper, centre, ReputationEvent.STREET_SWEPT);
	}
}
