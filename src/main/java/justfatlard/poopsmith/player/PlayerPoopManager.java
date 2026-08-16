package justfatlard.poopsmith.player;

import justfatlard.poopsmith.Main;
import justfatlard.poopsmith.PoopPlacement;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Server-authoritative player poop system: the bar fills from eating (and
 * diarrhea), empties through voluntary pooping (keybind, Pandorical clients)
 * or an accident at 100. Runs entirely server-side so vanilla-client players
 * still participate; they just get no bar and no keybind: accidents only.
 */
public final class PlayerPoopManager {
	private PlayerPoopManager() {}

	public static final int MANUAL_POOP_THRESHOLD = 20;

	private static final int GAIN_PER_NUTRITION = 3;
	private static final int RISKY_FOOD_BONUS = 25;
	private static final float DIARRHEA_CHANCE = 0.30F;
	private static final int DIARRHEA_DURATION_TICKS = 1800; // 90 seconds
	private static final int DIARRHEA_FILL_INTERVAL_TICKS = 20;

	// Nausea tuning, against this snapshot's verified warp behavior: the
	// screen spin intensity is the effect's blend factor, and nausea
	// registers setBlendDuration(blendIn=150, blendOut=20, blendOutAdvance=60)
	// (MobEffects bytecode). A pulse of D ticks therefore peaks at roughly
	// (D - 60) / 150 of full warp: 110 ticks peaks near a third, a queasy
	// wobble that never approaches the full-screen warp a long application
	// reaches. Diarrhea keeps full-duration Hunger (the mechanical driver)
	// but swaps constant nausea for these pulses on an interval.
	private static final int NAUSEA_PULSE_TICKS = 110;
	private static final int NAUSEA_PULSE_INTERVAL_TICKS = 300; // every 15s of diarrhea
	private static final int ACCIDENT_NAUSEA_TICKS = 110;

	// Shift-aimed voluntary poops go one block behind the player with a short
	// drop scan: stand at a latrine pit's edge facing away and go IN
	private static final int SHIFT_POOP_MAX_DROP = 3;

	/** Foods that upset the gut: raw meat, rot, and suspicious edibles. */
	private static final Set<Item> RISKY_FOODS = Set.of(
		Items.ROTTEN_FLESH, Items.SPIDER_EYE, Items.POISONOUS_POTATO,
		Items.SUSPICIOUS_STEW, Items.PUFFERFISH,
		Items.BEEF, Items.PORKCHOP, Items.CHICKEN, Items.MUTTON, Items.RABBIT,
		Items.COD, Items.SALMON);

	private record DelayedTask(int runAtTick, Runnable task) {}
	private static final List<DelayedTask> delayedTasks = new ArrayList<>();
	private static int tickCounter = 0;

	/** Run {@code task} on the server thread after {@code delayTicks} ticks. */
	public static void scheduleDelayed(int delayTicks, Runnable task) {
		synchronized (delayedTasks) {
			delayedTasks.add(new DelayedTask(tickCounter + delayTicks, task));
		}
	}

	public static void onServerTick(MinecraftServer server) {
		tickCounter++;
		runDueTasks();

		PoopLevelData data = PoopLevelData.get(server);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			DigestiveHud.syncVisibility(player, data.getLevel(player.getUUID()));
			checkFirstNetherEntry(player, data);

			int remaining = data.getDiarrheaTicks(player.getUUID());
			if (remaining <= 0) continue;
			data.setDiarrheaTicks(player.getUUID(), remaining - 1);
			if (remaining % DIARRHEA_FILL_INTERVAL_TICKS == 0) {
				addLevel(player, 1);
			}
			// Queasy reminder pulses; the onset pulse comes from startDiarrhea,
			// so skip the interval boundary at the full duration
			if (remaining % NAUSEA_PULSE_INTERVAL_TICKS == 0 && remaining < DIARRHEA_DURATION_TICKS) {
				player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, NAUSEA_PULSE_TICKS, 0));
			}
		}
	}

	private static void runDueTasks() {
		synchronized (delayedTasks) {
			Iterator<DelayedTask> iterator = delayedTasks.iterator();
			while (iterator.hasNext()) {
				DelayedTask delayed = iterator.next();
				if (tickCounter >= delayed.runAtTick()) {
					iterator.remove();
					delayed.task().run();
				}
			}
		}
	}

	/** Called from the FoodProperties.onConsume mixin after any finished meal. */
	public static void onAte(ServerPlayer player, ItemStack stack, FoodProperties food) {
		if (stack.is(Main.GUANO_ITEM)) {
			tryCureDiarrhea(player);
		}
		int gain = food.nutrition() * GAIN_PER_NUTRITION;
		if (RISKY_FOODS.contains(stack.getItem())) {
			gain += RISKY_FOOD_BONUS;
			if (player.getRandom().nextFloat() < DIARRHEA_CHANCE) {
				startDiarrhea(player);
			}
		}
		addLevel(player, gain);
	}

	/** Guano is the folk remedy: eating it ends active diarrhea outright. */
	private static void tryCureDiarrhea(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		if (server == null) return;
		PoopLevelData data = PoopLevelData.get(server);
		if (data.getDiarrheaTicks(player.getUUID()) <= 0) return;

		data.setDiarrheaTicks(player.getUUID(), 0);
		player.removeEffect(MobEffects.HUNGER);
		player.removeEffect(MobEffects.NAUSEA);
		player.sendSystemMessage(
			Component.translatable("message.poopsmith.guano_cure").withStyle(ChatFormatting.GREEN), false);
	}

	private static void startDiarrhea(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		if (server == null) return;
		PoopLevelData.get(server).setDiarrheaTicks(player.getUUID(), DIARRHEA_DURATION_TICKS);
		player.addEffect(new MobEffectInstance(MobEffects.HUNGER, DIARRHEA_DURATION_TICKS, 0));
		player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, NAUSEA_PULSE_TICKS, 0));
		player.sendSystemMessage(
			Component.translatable("message.poopsmith.diarrhea").withStyle(ChatFormatting.DARK_GREEN), false);
	}

	/** Handles the PoopC2S keybind payload; the server validates everything. */
	public static void tryManualPoop(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		if (server == null) return;
		PoopLevelData data = PoopLevelData.get(server);
		if (data.getLevel(player.getUUID()) < MANUAL_POOP_THRESHOLD) {
			player.sendSystemMessage(
				Component.translatable("message.poopsmith.not_ready").withStyle(ChatFormatting.GRAY), true);
			return;
		}
		poop(player, data, null);
	}

	private static void addLevel(ServerPlayer player, int amount) {
		MinecraftServer server = player.level().getServer();
		if (server == null) return;
		PoopLevelData data = PoopLevelData.get(server);
		int before = data.getLevel(player.getUUID());
		int after = data.setLevel(player.getUUID(), before + amount);
		if (after == before) return;
		if (after >= PoopLevelData.MAX_LEVEL) {
			poop(player, data, "message.poopsmith.accident");
		} else {
			DigestiveHud.showOrUpdate(player, after);
		}
	}

	/**
	 * Shared voluntary/accident resolution: layer (or water cloud), fart,
	 * reset, hunger cost. {@code accidentMessageKey} null means voluntary;
	 * otherwise the accident extras (nausea, message) apply with that lang
	 * key, so the nether scare can speak in its own voice.
	 */
	private static void poop(ServerPlayer player, PoopLevelData data, String accidentMessageKey) {
		ServerLevel world = (ServerLevel) player.level();

		if (player.isInWater()) {
			// No layer survives underwater: disperse instead (bar semantics
			// unchanged); waterPoop brings its own muffled fart
			PoopPlacement.waterPoop(world, player);
		} else {
			// Sneaking aims a voluntary poop one block behind (accidents
			// don't aim); if nothing behind can take it, fall back to
			// at-feet so the poop is never lost
			net.minecraft.core.BlockPos landed = null;
			if (accidentMessageKey == null && player.isShiftKeyDown()) {
				landed = PoopPlacement.depositWithDrop(world,
					player.blockPosition().relative(player.getDirection().getOpposite()),
					SHIFT_POOP_MAX_DROP).orElse(null);
			}
			if (landed == null) {
				landed = PoopPlacement.deposit(world, player.blockPosition()).orElse(null);
			}
			PoopPlacement.playFart(world, player);
			if (landed != null) {
				recordPublicWitnesses(world, player, landed);
			}
		}

		data.setLevel(player.getUUID(), 0);
		FoodData foodData = player.getFoodData();
		foodData.setFoodLevel(Math.max(0, foodData.getFoodLevel() - 1));

		if (accidentMessageKey != null) {
			player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, ACCIDENT_NAUSEA_TICKS, 0));
			player.sendSystemMessage(
				Component.translatable(accidentMessageKey).withStyle(ChatFormatting.GOLD), false);
		}
		DigestiveHud.flush(player);
	}

	// Public pooping has witnesses. A covered latrine pit is naturally
	// private (no sky above the deposit), which quietly teaches using them.
	private static final double WITNESS_RANGE = 12.0;

	private static void recordPublicWitnesses(ServerLevel world, ServerPlayer player,
			net.minecraft.core.BlockPos landed) {
		if (!world.canSeeSky(landed.above())) return;
		MinecraftServer server = world.getServer();
		PoopLevelData data = PoopLevelData.get(server);
		for (net.minecraft.world.entity.npc.villager.Villager villager : world.getEntitiesOfClass(
				net.minecraft.world.entity.npc.villager.Villager.class,
				new net.minecraft.world.phys.AABB(player.blockPosition()).inflate(WITNESS_RANGE),
				v -> v.hasLineOfSight(player))) {
			data.recordWitness(villager.getUUID(), player.getUUID(), world.getGameTime());
		}
	}

	/**
	 * First-ever Nether entry: a coin flip on whether the player poops
	 * themselves. Rolled exactly once per player ever (the flag persists
	 * regardless of the roll's outcome); fear does not consult the bar.
	 * Detected in the per-tick loop rather than a dimension-change event:
	 * verified empirically that Fabric's AFTER_PLAYER_CHANGE_LEVEL does not
	 * fire for command teleports on this snapshot, and a presence check
	 * catches every arrival mechanism (portal, teleport, whatever comes).
	 */
	private static void checkFirstNetherEntry(ServerPlayer player, PoopLevelData data) {
		if (player.level().dimension() != net.minecraft.world.level.Level.NETHER) return;
		if (data.hasEnteredNether(player.getUUID())) return;
		data.markEnteredNether(player.getUUID());
		if (player.getRandom().nextFloat() < 0.5F) {
			poop(player, data, "message.poopsmith.nether_accident");
		}
	}

	/** Deferred-push join hook (Pandorical handshake lands after JOIN). */
	public static void onPlayerJoin(ServerPlayer player) {
		scheduleDelayed(20, () -> {
			MinecraftServer server = player.level().getServer();
			if (server == null || player.hasDisconnected()) return;
			DigestiveHud.showOrUpdate(player, PoopLevelData.get(server).getLevel(player.getUUID()));
		});
	}

	public static void onPlayerDisconnect(UUID uuid) {
		DigestiveHud.onPlayerDisconnect(uuid);
	}

	public static void onServerStopping() {
		synchronized (delayedTasks) {
			delayedTasks.clear();
		}
	}
}
