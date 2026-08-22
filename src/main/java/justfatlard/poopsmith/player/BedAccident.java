package justfatlard.poopsmith.player;

import justfatlard.poopsmith.PoopPlacement;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.AbstractBedBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Going to bed full is a gamble; going to bed with diarrhea is a bad hand.
 *
 * <p>The roll happens as you climb in, the accident lands a couple of seconds
 * into the sleep screen (so the sound arrives while you are lying there, not as
 * you lie down), and the bed carries the evidence afterwards: it comes out of
 * the night brown, whatever colour it went in.
 */
public final class BedAccident {
	private BedAccident() {}

	// Nothing under this is worth losing sleep over; from there the odds ramp
	// to FULL_RISK at a full bar. Diarrhea does not consult the bar at all.
	private static final int RISK_THRESHOLD = 70;
	private static final float FULL_RISK = 0.35F;
	private static final float DIARRHEA_RISK = 0.60F;

	// Far enough into the sleep screen to read as happening in the bed
	private static final int ACCIDENT_DELAY_TICKS = 40;
	// ...and far enough past waking that the screen has given the room back
	private static final int WAKE_MESSAGE_DELAY_TICKS = 10;

	/** Who went in the bed this sleep, waiting on their morning. */
	private static final Set<UUID> soiled = ConcurrentHashMap.newKeySet();

	public static void register() {
		// Villagers sleep too: the ServerPlayer check is what keeps this a
		// player problem, and it drops the client-side call in single player
		EntitySleepEvents.START_SLEEPING.register((entity, bedPos) -> {
			if (entity instanceof ServerPlayer player) rollForAccident(player, bedPos);
		});
		EntitySleepEvents.STOP_SLEEPING.register((entity, bedPos) -> {
			if (entity instanceof ServerPlayer player) onWake(player);
		});
	}

	public static void forget(UUID uuid) {
		soiled.remove(uuid);
	}

	private static void rollForAccident(ServerPlayer player, BlockPos bedPos) {
		MinecraftServer server = player.level().getServer();
		if (server == null) return;
		PoopLevelData data = PoopLevelData.get(server);
		if (player.getRandom().nextFloat() >= risk(data, player.getUUID())) return;

		PlayerPoopManager.scheduleDelayed(ACCIDENT_DELAY_TICKS, () -> {
			// Got up, got woken, or logged out inside the delay: no harm done
			if (player.hasDisconnected() || !player.isSleeping()) return;
			soil(player, bedPos, data);
		});
	}

	private static float risk(PoopLevelData data, UUID uuid) {
		int level = data.getLevel(uuid);
		float full = level <= RISK_THRESHOLD ? 0.0F
			: FULL_RISK * (level - RISK_THRESHOLD) / (PoopLevelData.MAX_LEVEL - RISK_THRESHOLD);
		float runs = data.getDiarrheaTicks(uuid) > 0 ? DIARRHEA_RISK : 0.0F;
		return Math.max(full, runs);
	}

	private static void soil(ServerPlayer player, BlockPos bedPos, PoopLevelData data) {
		ServerLevel world = (ServerLevel) player.level();
		PoopPlacement.playFart(world, player);
		stain(world, bedPos);
		// The bed took the deposit, so there is no layer to place: the bar
		// empties and the hunger point is spent all the same
		PlayerPoopManager.settle(player, data);
		soiled.add(player.getUUID());
	}

	/**
	 * Both halves in one breath and with no shape updates: a bed whose halves
	 * disagree fails its own neighbour check and breaks itself into air.
	 *
	 * <p>Dyed beds only. Brown is a wool colour and a straw bed has no wool,
	 * so a straw bed keeps its looks and the player keeps the rest.
	 */
	private static void stain(ServerLevel world, BlockPos bedPos) {
		Block brownBed = Blocks.BED.brown();
		BlockState state = world.getBlockState(bedPos);
		if (!(state.getBlock() instanceof BedBlock) || state.is(brownBed)) return;

		BlockPos otherPos = bedPos.relative(AbstractBedBlock.getConnectedDirection(state));
		BlockState other = world.getBlockState(otherPos);
		int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

		world.setBlock(bedPos, brownBed.withPropertiesOf(state), flags);
		if (other.getBlock() instanceof BedBlock
				&& other.getValue(AbstractBedBlock.PART) != state.getValue(AbstractBedBlock.PART)) {
			world.setBlock(otherPos, brownBed.withPropertiesOf(other), flags);
		}
	}

	private static void onWake(ServerPlayer player) {
		if (!soiled.remove(player.getUUID())) return;
		PlayerPoopManager.scheduleDelayed(WAKE_MESSAGE_DELAY_TICKS, () -> {
			if (player.hasDisconnected()) return;
			player.sendSystemMessage(
				Component.translatable("message.poopsmith.bed_accident").withStyle(ChatFormatting.GOLD), true);
		});
	}
}
