package justfatlard.poopsmith.player;

import justfatlard.pandorical.api.ComponentBuilder;
import justfatlard.pandorical.api.ComponentType;
import justfatlard.pandorical.api.HudBuilder;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.api.VanillaHudElement;
import justfatlard.pandorical.protocol.ComponentUpdate;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The digestive tract as a status bar, server-pushed through Pandorical's HUD API.
 *
 * <p>One continuous tract read left to right, occupying the vanilla hunger bar's
 * rectangle and nothing more: a stomach at the left whose amber contents rise with
 * the food level, its outlet feeding an intestine that squiggles rightward and fills
 * with brown waste toward the sphincter, which is where the poop item icon pops
 * during a flush and the only thing ever drawn past the tract's own art.
 *
 * <p>On a client that cannot suppress vanilla elements (older Pandorical build) the
 * drumsticks keep the hunger row, so the same tract is drawn one row higher instead,
 * where it can overlap air bubbles while swimming. Food level then reads twice, once
 * as drumsticks and once as amber, which is redundant but not broken.
 *
 * <p>Callers must only invoke on change (never per-tick beyond the cheap reconcile in
 * {@link #syncVisibility}) and never straight from JOIN: the capability handshake
 * lands after it, so Main defers the first push.
 */
public final class DigestiveHud {
	private DigestiveHud() {}

	private static final String OVERLAY_ID = "poopsmith:digestive";
	private static final String OWNER_ID = "poopsmith";
	private static final String TRACT_ID = "tract";
	private static final String FOOD_FILL_ID = "food_fill";
	private static final String WASTE_FILL_ID = "waste_fill";
	private static final String EXIT_ICON_ID = "exit_icon";

	// The whole tract lives inside the vanilla hunger bar's rectangle and nowhere
	// else: that row spans center+10 to center+91 and height-39..height-30, with
	// the XP bar immediately below and the air bubbles row immediately above, so
	// an assembly any taller would collide with one of them. Suppressing the
	// vanilla food bar buys exactly this rectangle, so the layout spends exactly
	// this rectangle. Internal geometry (stomach span, squiggle, sphincter) is
	// defined once in generate_textures.py; these mirror it.
	private static final int TRACT_WIDTH = 81;
	private static final int TRACT_HEIGHT = 9;
	private static final int STOMACH_SPAN = 15;   // x 0..14 of the tract canvas
	private static final int TUBE_START_X = 15;
	private static final int WASTE_SPAN = 63;     // tube start to the pucker hole
	private static final int LEFT_EDGE_FROM_CENTER = 10;
	private static final int BOTTOM_MARGIN = 30;  // puts the row where the drumsticks were

	// The flush icon emerges from the sphincter (canvas x 77.5). It is momentary,
	// so it is allowed to overhang the tract's right edge; nothing persistent does.
	private static final int ICON_SIZE = 8;
	private static final int ICON_X = 73;

	// Flush pacing: the fill rushes over the ~3-tick geometry interpolation, the icon
	// pops shortly after the rush lands and lingers most of a second
	private static final int FLUSH_ICON_DELAY_TICKS = 5;
	private static final int FLUSH_ICON_LINGER_TICKS = 18;

	private static final String TEXTURE_TRACT = "poopsmith:textures/gui/poop_tract.png";
	private static final String TEXTURE_FOOD = "poopsmith:textures/gui/poop_tract_food.png";
	private static final String TEXTURE_WASTE = "poopsmith:textures/gui/poop_tract_waste.png";
	private static final String TEXTURE_POOP_ITEM = "poopsmith:textures/item/poop.png";

	private static final int VANILLA_MAX_FOOD = 20;

	private static final Set<UUID> shown = ConcurrentHashMap.newKeySet();
	/** Players whose client took the stomach layout, i.e. whose food bar we suppressed. */
	private static final Set<UUID> replacingFoodBar = ConcurrentHashMap.newKeySet();
	/** Last food level pushed, so the per-tick reconcile only sends on change. */
	private static final Map<UUID, Integer> lastFood = new ConcurrentHashMap<>();

	/** Push the tract (first time) or its new fill widths to a capable player. */
	public static void showOrUpdate(ServerPlayer player, int level) {
		if (!PandoricalApi.isAvailable(player) || !PandoricalApi.hasCapability(player, "hud")) return;
		// Vanilla hides the whole survival status HUD in creative/spectator; a gut
		// floating alone would look wrong (syncVisibility re-shows it on the way back)
		if (player.isCreative() || player.isSpectator()) return;

		int food = player.getFoodData().getFoodLevel();

		if (shown.add(player.getUUID())) {
			boolean canReplaceFoodBar = PandoricalApi.hasCapability(player, "hud_elements");
			int foodHeight = foodFillHeight(food);

			HudBuilder hud = new HudBuilder(OVERLAY_ID)
				.anchor("bottom_center")
				// Without suppression the drumsticks still own the hunger row, so the
				// tract moves one row up into the air-bubble band: it overlaps bubbles
				// while swimming, which is the lesser of the two collisions.
				.offset(LEFT_EDGE_FROM_CENTER, canReplaceFoodBar ? BOTTOM_MARGIN : BOTTOM_MARGIN + TRACT_HEIGHT)
				.component(new ComponentBuilder(TRACT_ID, ComponentType.SPRITE)
					.bounds(0, 0, TRACT_WIDTH, TRACT_HEIGHT)
					.prop(ComponentType.PROP_TEXTURE, TEXTURE_TRACT))
				.component(new ComponentBuilder(FOOD_FILL_ID, ComponentType.SPRITE)
					.bounds(0, TRACT_HEIGHT - foodHeight, STOMACH_SPAN, foodHeight)
					.prop(ComponentType.PROP_TEXTURE, TEXTURE_FOOD)
					.prop(ComponentType.PROP_TEXTURE_WIDTH, String.valueOf(TRACT_WIDTH))
					.prop(ComponentType.PROP_TEXTURE_HEIGHT, String.valueOf(TRACT_HEIGHT))
					.prop(ComponentType.PROP_TEXTURE_V, String.valueOf(TRACT_HEIGHT - foodHeight))
					// Slower than the default blend on purpose. Hunger moves a point at
					// a time and rarely, so the default window reads as a jump; at this
					// length you see the stomach actually empty, which is the whole
					// reason for drawing a stomach instead of drumsticks.
					.prop(ComponentType.PROP_INTERP_TICKS, "8"))
				.component(new ComponentBuilder(WASTE_FILL_ID, ComponentType.SPRITE)
					.bounds(TUBE_START_X, 0, wasteFillWidth(level), TRACT_HEIGHT)
					.prop(ComponentType.PROP_TEXTURE, TEXTURE_WASTE)
					.prop(ComponentType.PROP_TEXTURE_WIDTH, String.valueOf(TRACT_WIDTH))
					.prop(ComponentType.PROP_TEXTURE_HEIGHT, String.valueOf(TRACT_HEIGHT))
					.prop(ComponentType.PROP_TEXTURE_U, String.valueOf(TUBE_START_X))
					// The intestine fills as the stomach drains, so it wants the same
					// pacing; the two together read as one movement through the tract.
					.prop(ComponentType.PROP_INTERP_TICKS, "8"))
				.component(new ComponentBuilder(EXIT_ICON_ID, ComponentType.SPRITE)
					.bounds(ICON_X, 0, 0, 0)
					.prop(ComponentType.PROP_TEXTURE, TEXTURE_POOP_ITEM));
			PandoricalApi.hud().show(player, hud.build());

			if (canReplaceFoodBar) {
				replacingFoodBar.add(player.getUUID());
				PandoricalApi.hud().hideVanillaElements(player, OWNER_ID, List.of(VanillaHudElement.FOOD_BAR));
			}
			lastFood.put(player.getUUID(), food);
		} else {
			List<ComponentUpdate> updates = new ArrayList<>();
			updates.add(new ComponentUpdate(WASTE_FILL_ID,
				Map.of(ComponentType.PROP_WIDTH, String.valueOf(wasteFillWidth(level)))));
			updates.add(foodFillUpdate(food));
			lastFood.put(player.getUUID(), food);
			PandoricalApi.hud().update(player, OVERLAY_ID, updates);
		}
	}

	/**
	 * A gauge that fills upward: position, height and source origin move together,
	 * so the revealed slice is always the BOTTOM of the stomach art.
	 */
	private static ComponentUpdate foodFillUpdate(int food) {
		int h = foodFillHeight(food);
		return new ComponentUpdate(FOOD_FILL_ID, Map.of(
			ComponentType.PROP_Y, String.valueOf(TRACT_HEIGHT - h),
			ComponentType.PROP_HEIGHT, String.valueOf(h),
			ComponentType.PROP_TEXTURE_V, String.valueOf(TRACT_HEIGHT - h)));
	}

	/**
	 * The flush: rush the fill to the exit, pop the poop icon there for a moment,
	 * then clear to empty. Server-driven staged updates; each width change glides via
	 * the client's geometry interpolation.
	 */
	public static void flush(ServerPlayer player) {
		if (!PandoricalApi.isAvailable(player) || !PandoricalApi.hasCapability(player, "hud")) return;
		if (player.isCreative() || player.isSpectator()) return;
		if (!shown.contains(player.getUUID())) {
			showOrUpdate(player, 0);
			return;
		}

		PandoricalApi.hud().update(player, OVERLAY_ID, List.of(
			new ComponentUpdate(WASTE_FILL_ID,
				Map.of(ComponentType.PROP_WIDTH, String.valueOf(WASTE_SPAN)))));

		PlayerPoopManager.scheduleDelayed(FLUSH_ICON_DELAY_TICKS, () -> {
			if (!stillShown(player)) return;
			PandoricalApi.hud().update(player, OVERLAY_ID, List.of(
				new ComponentUpdate(EXIT_ICON_ID, Map.of(
					ComponentType.PROP_WIDTH, String.valueOf(ICON_SIZE),
					ComponentType.PROP_HEIGHT, String.valueOf(ICON_SIZE)))));
		});

		PlayerPoopManager.scheduleDelayed(FLUSH_ICON_DELAY_TICKS + FLUSH_ICON_LINGER_TICKS, () -> {
			if (!stillShown(player)) return;
			PandoricalApi.hud().update(player, OVERLAY_ID, List.of(
				new ComponentUpdate(EXIT_ICON_ID, Map.of(
					ComponentType.PROP_WIDTH, "0",
					ComponentType.PROP_HEIGHT, "0")),
				new ComponentUpdate(WASTE_FILL_ID,
					Map.of(ComponentType.PROP_WIDTH, "0"))));
		});
	}

	/**
	 * Cheap per-tick reconciliation against gamemode and food level: entering
	 * creative/spectator hides the tract and hands the hunger bar back, returning to
	 * survival re-shows it, and a changed food level pushes one stomach update.
	 * Costs two boolean checks, a set lookup and an int compare per player per tick;
	 * pushes only happen on transitions.
	 */
	public static void syncVisibility(ServerPlayer player, int level) {
		UUID uuid = player.getUUID();
		boolean hidden = player.isCreative() || player.isSpectator();
		boolean shownNow = shown.contains(uuid);
		if (hidden && shownNow) {
			hide(player);
			return;
		}
		if (!hidden && !shownNow) {
			showOrUpdate(player, level);
			return;
		}
		int food = player.getFoodData().getFoodLevel();
		Integer previous = lastFood.get(uuid);
		if (previous != null && previous == food) return;
		lastFood.put(uuid, food);
		PandoricalApi.hud().update(player, OVERLAY_ID, List.of(foodFillUpdate(food)));
	}

	/** Take the overlay down and give the player their vanilla hunger bar back. */
	private static void hide(ServerPlayer player) {
		UUID uuid = player.getUUID();
		shown.remove(uuid);
		lastFood.remove(uuid);
		PandoricalApi.hud().hide(player, OVERLAY_ID);
		if (replacingFoodBar.remove(uuid)) {
			PandoricalApi.hud().restoreVanillaElements(player, OWNER_ID);
		}
	}

	private static boolean stillShown(ServerPlayer player) {
		return !player.hasDisconnected() && shown.contains(player.getUUID());
	}

	private static int wasteFillWidth(int level) {
		if (level <= 0) return 0;
		return Math.max(1, level * WASTE_SPAN / PoopLevelData.MAX_LEVEL);
	}

	private static int foodFillHeight(int food) {
		if (food <= 0) return 0;
		return Math.max(1, Math.min(food, VANILLA_MAX_FOOD) * TRACT_HEIGHT / VANILLA_MAX_FOOD);
	}

	public static void onPlayerDisconnect(UUID uuid) {
		shown.remove(uuid);
		replacingFoodBar.remove(uuid);
		lastFood.remove(uuid);
	}
}
