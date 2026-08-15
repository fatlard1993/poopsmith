package justfatlard.poopsmith.player;

import justfatlard.pandorical.api.ComponentBuilder;
import justfatlard.pandorical.api.ComponentType;
import justfatlard.pandorical.api.HudBuilder;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.protocol.ComponentUpdate;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-pushed poop bar via Pandorical's HUD API: a muted pink intestine
 * tube, hunger-bar width, sitting so the vanilla drumsticks overlap its top
 * edge (Pandorical draws over vanilla, so the layering is faked by position
 * and by the tube texture's curves dipping between drumstick columns). The
 * tube fills with brown from its left end proportional to the bar, revealed
 * by width-clipping the fill texture; fill changes glide via the HUD
 * geometry interpolation. On any poop the fill rushes to the end, a poop
 * item icon pops from the exit, and the tube clears.
 *
 * <p>Callers must only invoke on change (never per-tick) and never straight
 * from JOIN (the capability handshake lands after it; Main defers the first
 * push). Clients predating the sprite texture/clip props and bottom_center
 * anchor render top-left white rectangles instead, ugly but harmless, fixed
 * by updating the client jar.
 */
public final class PoopHud {
	private PoopHud() {}

	private static final String OVERLAY_ID = "poopsmith:poop_bar";
	private static final String TUBE_ID = "tube";
	private static final String FILL_ID = "fill";
	private static final String EXIT_ICON_ID = "exit_icon";

	// Vanilla frame: hunger row spans center+10 to center+91, its icons at
	// height-39..height-30, and the XP bar band at height-29..height-24. The
	// 3px ribbon rides the seam: one row overlapping the drumstick bottoms,
	// one in the gap, one on the XP bar's top edge.
	private static final int TUBE_WIDTH = 81;
	private static final int TUBE_HEIGHT = 3;
	private static final int LEFT_EDGE_FROM_CENTER = 10;
	private static final int BOTTOM_MARGIN = 28;

	private static final int ICON_SIZE = 9;
	private static final int ICON_X = TUBE_WIDTH - 8; // pokes past the exit end
	private static final int ICON_Y = 0;

	// Flush pacing: fill rushes over the ~3-tick geometry interpolation, the
	// icon pops shortly after the rush lands and lingers most of a second
	private static final int FLUSH_ICON_DELAY_TICKS = 5;
	private static final int FLUSH_ICON_LINGER_TICKS = 18;

	private static final String TEXTURE_TUBE = "poopsmith:textures/gui/poop_intestine.png";
	private static final String TEXTURE_FILL = "poopsmith:textures/gui/poop_intestine_fill.png";
	private static final String TEXTURE_POOP_ITEM = "poopsmith:textures/item/poop.png";

	private static final Set<UUID> shown = ConcurrentHashMap.newKeySet();

	/** Push the intestine (first time) or its new fill width to a capable player. */
	public static void showOrUpdate(ServerPlayer player, int level) {
		if (!PandoricalApi.isAvailable(player) || !PandoricalApi.hasCapability(player, "hud")) return;

		if (shown.add(player.getUUID())) {
			HudBuilder hud = new HudBuilder(OVERLAY_ID)
				.anchor("bottom_center")
				.offset(LEFT_EDGE_FROM_CENTER, BOTTOM_MARGIN)
				.component(new ComponentBuilder(TUBE_ID, ComponentType.SPRITE)
					.bounds(0, 0, TUBE_WIDTH, TUBE_HEIGHT)
					.prop(ComponentType.PROP_TEXTURE, TEXTURE_TUBE))
				.component(new ComponentBuilder(FILL_ID, ComponentType.SPRITE)
					.bounds(0, 0, fillWidth(level), TUBE_HEIGHT)
					.prop(ComponentType.PROP_TEXTURE, TEXTURE_FILL)
					.prop(ComponentType.PROP_TEXTURE_WIDTH, String.valueOf(TUBE_WIDTH))
					.prop(ComponentType.PROP_TEXTURE_HEIGHT, String.valueOf(TUBE_HEIGHT)))
				.component(new ComponentBuilder(EXIT_ICON_ID, ComponentType.SPRITE)
					.bounds(ICON_X, ICON_Y, 0, 0)
					.prop(ComponentType.PROP_TEXTURE, TEXTURE_POOP_ITEM));
			PandoricalApi.hud().show(player, hud.build());
		} else {
			PandoricalApi.hud().update(player, OVERLAY_ID, List.of(
				new ComponentUpdate(FILL_ID,
					Map.of(ComponentType.PROP_WIDTH, String.valueOf(fillWidth(level))))));
		}
	}

	/**
	 * The flush: rush the fill to the exit, pop the poop icon there for a
	 * moment, then clear to empty. Server-driven staged updates; each width
	 * change glides via the client's geometry interpolation.
	 */
	public static void flush(ServerPlayer player) {
		if (!PandoricalApi.isAvailable(player) || !PandoricalApi.hasCapability(player, "hud")) return;
		if (!shown.contains(player.getUUID())) {
			showOrUpdate(player, 0);
			return;
		}

		PandoricalApi.hud().update(player, OVERLAY_ID, List.of(
			new ComponentUpdate(FILL_ID,
				Map.of(ComponentType.PROP_WIDTH, String.valueOf(TUBE_WIDTH)))));

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
				new ComponentUpdate(FILL_ID,
					Map.of(ComponentType.PROP_WIDTH, "0"))));
		});
	}

	private static boolean stillShown(ServerPlayer player) {
		return !player.hasDisconnected() && shown.contains(player.getUUID());
	}

	private static int fillWidth(int level) {
		if (level <= 0) return 0;
		return Math.max(1, level * TUBE_WIDTH / PoopLevelData.MAX_LEVEL);
	}

	public static void onPlayerDisconnect(UUID uuid) {
		shown.remove(uuid);
	}
}
