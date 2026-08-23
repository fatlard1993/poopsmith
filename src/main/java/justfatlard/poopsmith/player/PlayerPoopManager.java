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
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-authoritative player poop system: the bar fills from eating (and
 * diarrhea), empties through voluntary pooping (keybind, Pandorical clients)
 * or an accident at 100. Runs entirely server-side so vanilla-client players
 * still participate; they just get no bar and no keybind: accidents only.
 */
public final class PlayerPoopManager {
	private PlayerPoopManager() {}

	public static final int MANUAL_POOP_THRESHOLD = 20;

	/**
	 * Full enough that there is more than one in there.
	 *
	 * <p>Read before {@link #settle} empties the bar, because by then the number that decides this
	 * is gone.
	 */
	public static final int DOUBLE_DEUCE_LEVEL = 80;

	/**
	 * Waste per hunger point actually burned.
	 *
	 * <p>Deliberately the old per-nutrition figure: a meal restores as many hunger
	 * points as it had nutrition, so the same food still produces the same total.
	 * What changed is when. Eating used to fill the bar on the spot, which had a
	 * player go from hungry to needing the toilet between one bite and the next;
	 * now the food sits in the stomach and becomes waste as it is used, which is
	 * what the HUD has been drawing all along.
	 */
	private static final int GAIN_PER_HUNGER = 3;
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
	// drop scan: stand at a latrine pit's edge facing away and go IN. Must
	// clear the generated pit, whose depositable spot is 4 blocks below the
	// rim a player stands on; 6 covers deeper hand-dug pits too

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
			digestSpentHunger(player);

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
		// The meal itself adds nothing: it goes to the stomach, which is the vanilla
		// hunger bar, and turns into waste as that drains. Bad food is the exception,
		// because what makes it bad is not its nutrition.
		if (RISKY_FOODS.contains(stack.getItem())) {
			addLevel(player, RISKY_FOOD_BONUS);
			if (player.getRandom().nextFloat() < DIARRHEA_CHANCE) {
				startDiarrhea(player);
			}
		}
	}

	/** Hunger last seen per player, to notice it being spent rather than restored. */
	private static final Map<UUID, Integer> lastFoodLevel = new ConcurrentHashMap<>();

	/**
	 * Turn hunger burned since the last tick into waste.
	 *
	 * <p>Only drops count. Eating raises the level and must not register as
	 * digestion, or a meal would pay twice. Saturation is ignored on purpose: it
	 * is the buffer before hunger moves at all, so waiting for the visible bar to
	 * fall is what makes the stomach emptying and the intestine filling read as
	 * one motion.
	 */
	private static void digestSpentHunger(ServerPlayer player) {
		int food = player.getFoodData().getFoodLevel();
		Integer previous = lastFoodLevel.put(player.getUUID(), food);
		if (previous == null || food >= previous) return;

		addLevel(player, (previous - food) * GAIN_PER_HUNGER);
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
		boolean doubleDeuce = data.getLevel(player.getUUID()) >= DOUBLE_DEUCE_LEVEL;

		if (player.isInWater()) {
			// No layer survives underwater: disperse instead (bar semantics
			// unchanged); waterPoop brings its own muffled fart
			PoopPlacement.waterPoop(world, player);
		} else {
			// Sneaking aims a voluntary poop one block behind (accidents
			// don't aim); if nothing behind can take it, fall back to
			// at-feet so the poop is never lost
			// Crouching aims it behind you; standing drops it where you stand. Either way it has
			// to reach the ground to land, and the ground can be a few blocks down - off a ledge,
			// over a pit, on a stair - so both aims fall the same distance before giving up.
			boolean aiming = accidentMessageKey == null && player.isShiftKeyDown();
			net.minecraft.core.BlockPos aim = aiming
				? player.blockPosition().relative(player.getDirection().getOpposite())
				: player.blockPosition();

			net.minecraft.core.BlockPos landed =
				PoopPlacement.depositWithDrop(world, aim, PoopPlacement.MAX_FALL, player).orElse(null);
			if (landed == null && aiming) {
				// Behind was a wall or a long way down: it lands at your feet rather than nowhere.
				landed = PoopPlacement.depositWithDrop(world, player.blockPosition(),
					PoopPlacement.MAX_FALL, player).orElse(null);
			}
			if (landed == null) {
				// Nothing within falling distance. It comes apart on the way down and feeds
				// whatever it reaches instead of vanishing.
				PoopPlacement.scatterFrom(world, aim);
			}
			PoopPlacement.playFart(world, player);
			if (landed != null) {
				recordPublicWitnesses(world, player, landed);
			}

			// The second of a double deuce goes through the same rules as the first, so it lands
			// beside its twin rather than on top of it wherever the ground is open.
			if (doubleDeuce) {
				PoopPlacement.deposit(world, player.blockPosition(), player);
			}
		}

		settle(player, data);

		if (accidentMessageKey != null) {
			player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, ACCIDENT_NAUSEA_TICKS, 0));
			player.sendSystemMessage(
				Component.translatable(accidentMessageKey).withStyle(ChatFormatting.GOLD), false);
		}
	}

	/**
	 * What every poop costs, wherever it landed: an empty bar and a hunger
	 * point. Shared with the bed accident, which has no layer to place.
	 */
	static void settle(ServerPlayer player, PoopLevelData data) {
		data.setLevel(player.getUUID(), 0);
		FoodData foodData = player.getFoodData();
		foodData.setFoodLevel(Math.max(0, foodData.getFoodLevel() - 1));
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

	/**
	 * Death empties you.
	 *
	 * <p>The level is kept in saved data against a UUID, which is exactly the kind of store a
	 * death does not touch: a player who died bursting respawned still bursting, and the bar
	 * they came back to was the one they had been trying to do something about. Nothing else
	 * about a body survives dying, and this should not either.
	 *
	 * <p>Reset rather than settled: {@link #settle} also docks a hunger point and places what
	 * was owed, and a corpse owes nothing. The respawned player just starts empty.
	 */
	public static void onPlayerDeath(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		if (server == null) return;

		PoopLevelData.get(server).setLevel(player.getUUID(), 0);
		lastFoodLevel.remove(player.getUUID());
		BedAccident.forget(player.getUUID());
	}

	/** Push the emptied bar once the respawned player's client is listening again. */
	public static void onPlayerRespawn(ServerPlayer player) {
		scheduleDelayed(20, () -> {
			MinecraftServer server = player.level().getServer();
			if (server == null || player.hasDisconnected()) return;
			DigestiveHud.showOrUpdate(player, PoopLevelData.get(server).getLevel(player.getUUID()));
		});
	}

	public static void onPlayerDisconnect(UUID uuid) {
		// Dropped rather than kept: a rejoining player's first tick would otherwise
		// compare against a stale level and bill them for hunger burned last session.
		lastFoodLevel.remove(uuid);
		DigestiveHud.onPlayerDisconnect(uuid);
		BedAccident.forget(uuid);
	}

	public static void onServerStopping() {
		synchronized (delayedTasks) {
			delayedTasks.clear();
		}
	}
}
