package justfatlard.poopsmith.player;

import justfatlard.pandorical.api.ComponentBuilder;
import justfatlard.pandorical.api.ComponentType;
import justfatlard.pandorical.api.HudBuilder;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.protocol.ComponentUpdate;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-pushed poop bar via Pandorical's HUD API, styled and positioned to
 * sit with the vanilla status rows: ten 9x9 pips, 8px spacing, right-aligned
 * to the hunger bar's right edge (center + 91) one row above it (the air-row
 * position). Each pip is 10 points with a half-pip at 5; like hunger, the row
 * fills from the right. Always shown, like hunger itself.
 *
 * <p>Callers must only invoke on change (never per-tick) and never straight
 * from JOIN (the capability handshake lands after it; Main defers the first
 * push). The bottom_center anchor and sprite texture prop are current
 * Pandorical features: clients predating them fall back to a top-left row of
 * white squares, ugly but harmless, fixed by updating the client jar.
 */
public final class PoopHud {
	private PoopHud() {}

	private static final String OVERLAY_ID = "poopsmith:poop_bar";

	private static final int PIP_COUNT = 10;
	private static final int PIP_SIZE = 9;
	private static final int PIP_SPACING = 8;
	// Vanilla hunger row: right edge at center + 91, second status row is 49
	// above the screen bottom; offsetY is measured to the overlay's bottom
	private static final int LEFT_EDGE_FROM_CENTER = 10;
	private static final int BOTTOM_MARGIN = 40;

	private static final String TEXTURE_FULL = "poopsmith:textures/gui/poop_pip_full.png";
	private static final String TEXTURE_HALF = "poopsmith:textures/gui/poop_pip_half.png";
	private static final String TEXTURE_EMPTY = "poopsmith:textures/gui/poop_pip_empty.png";

	private static final Set<UUID> shown = ConcurrentHashMap.newKeySet();

	/** Push the pip row (first time) or its new fill state to a capable player. */
	public static void showOrUpdate(ServerPlayer player, int level) {
		if (!PandoricalApi.isAvailable(player) || !PandoricalApi.hasCapability(player, "hud")) return;

		if (shown.add(player.getUUID())) {
			HudBuilder hud = new HudBuilder(OVERLAY_ID)
				.anchor("bottom_center")
				.offset(LEFT_EDGE_FROM_CENTER, BOTTOM_MARGIN);
			for (int i = 0; i < PIP_COUNT; i++) {
				// Pip 0 sits at the row's right end and fills first, mirroring
				// how the vanilla hunger row loses its rightmost pip first
				int x = (PIP_COUNT - 1 - i) * PIP_SPACING;
				hud.component(new ComponentBuilder(pipId(i), ComponentType.SPRITE)
					.bounds(x, 0, PIP_SIZE, PIP_SIZE)
					.prop(ComponentType.PROP_TEXTURE, pipTexture(i, level)));
			}
			PandoricalApi.hud().show(player, hud.build());
		} else {
			List<ComponentUpdate> updates = new ArrayList<>(PIP_COUNT);
			for (int i = 0; i < PIP_COUNT; i++) {
				updates.add(new ComponentUpdate(pipId(i),
					Map.of(ComponentType.PROP_TEXTURE, pipTexture(i, level))));
			}
			PandoricalApi.hud().update(player, OVERLAY_ID, updates);
		}
	}

	private static String pipId(int index) {
		return "pip" + index;
	}

	/** Pip {@code index} covers points (index*10, index*10+10]; half at the 5 mark. */
	private static String pipTexture(int index, int level) {
		int pipFloor = index * 10;
		if (level >= pipFloor + 10) return TEXTURE_FULL;
		if (level >= pipFloor + 5) return TEXTURE_HALF;
		return TEXTURE_EMPTY;
	}

	public static void onPlayerDisconnect(UUID uuid) {
		shown.remove(uuid);
	}
}
