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
	private static final int ACCIDENT_NAUSEA_TICKS = 200;

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
			int remaining = data.getDiarrheaTicks(player.getUUID());
			if (remaining <= 0) continue;
			data.setDiarrheaTicks(player.getUUID(), remaining - 1);
			if (remaining % DIARRHEA_FILL_INTERVAL_TICKS == 0) {
				addLevel(player, 1);
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
		player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, DIARRHEA_DURATION_TICKS, 0));
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
		poop(player, data, false);
	}

	private static void addLevel(ServerPlayer player, int amount) {
		MinecraftServer server = player.level().getServer();
		if (server == null) return;
		PoopLevelData data = PoopLevelData.get(server);
		int before = data.getLevel(player.getUUID());
		int after = data.setLevel(player.getUUID(), before + amount);
		if (after == before) return;
		if (after >= PoopLevelData.MAX_LEVEL) {
			poop(player, data, true);
		} else {
			PoopHud.showOrUpdate(player, after);
		}
	}

	/** Shared voluntary/accident resolution: layer, fart, reset, hunger cost. */
	private static void poop(ServerPlayer player, PoopLevelData data, boolean accident) {
		ServerLevel world = (ServerLevel) player.level();

		PoopPlacement.deposit(world, player.blockPosition());
		PoopPlacement.playFart(world, player);

		int level = data.setLevel(player.getUUID(), 0);
		FoodData foodData = player.getFoodData();
		foodData.setFoodLevel(Math.max(0, foodData.getFoodLevel() - 1));

		if (accident) {
			player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, ACCIDENT_NAUSEA_TICKS, 0));
			player.sendSystemMessage(
				Component.translatable("message.poopsmith.accident").withStyle(ChatFormatting.GOLD), false);
		}
		PoopHud.showOrUpdate(player, level);
	}

	/** Deferred-push join hook (Pandorical handshake lands after JOIN). */
	public static void onPlayerJoin(ServerPlayer player) {
		scheduleDelayed(20, () -> {
			MinecraftServer server = player.level().getServer();
			if (server == null || player.hasDisconnected()) return;
			PoopHud.showOrUpdate(player, PoopLevelData.get(server).getLevel(player.getUUID()));
		});
	}

	public static void onPlayerDisconnect(UUID uuid) {
		PoopHud.onPlayerDisconnect(uuid);
	}

	public static void onServerStopping() {
		synchronized (delayedTasks) {
			delayedTasks.clear();
		}
	}
}
