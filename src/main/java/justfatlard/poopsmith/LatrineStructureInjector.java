package justfatlard.poopsmith;

import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Injects the latrine into the five vanilla village houses pools so naturally
 * generated villages come with one. Same runtime pool-reflection approach as
 * village-mail's VillageStructureInjector: injection happens at
 * SERVER_STARTING, before world generation, and resets on stop so
 * singleplayer world reloads re-inject.
 */
public final class LatrineStructureInjector {
	private static final Logger LOGGER = LoggerFactory.getLogger(Main.MOD_ID);
	private static boolean injected = false;

	private static final Identifier[] HOUSES_POOLS = {
		Identifier.fromNamespaceAndPath("minecraft", "village/plains/houses"),
		Identifier.fromNamespaceAndPath("minecraft", "village/desert/houses"),
		Identifier.fromNamespaceAndPath("minecraft", "village/savanna/houses"),
		Identifier.fromNamespaceAndPath("minecraft", "village/snowy/houses"),
		Identifier.fromNamespaceAndPath("minecraft", "village/taiga/houses")
	};

	private static final Identifier LATRINE_STRUCTURE = Identifier.fromNamespaceAndPath(Main.MOD_ID, "latrine");
	// Vanilla's plains houses pool totals 87 weight across 37 entries, so a
	// weight of 5 is ~5% per house slot: roughly half of small villages and
	// two thirds of large ones get a privy, and the rest are what the
	// dig-a-latrine quest is for. Weight 1 put it at 11% and nobody found one.
	private static final int LATRINE_WEIGHT = 5;

	private LatrineStructureInjector() {}

	public static void register() {
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> injected = false);

		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			if (injected) return;
			injected = true;

			Registry<StructureTemplatePool> poolRegistry =
				server.registryAccess().lookupOrThrow(Registries.TEMPLATE_POOL);

			// RIGID matches vanilla houses: the entrance jigsaw anchors the
			// piece at street level, and the pit in the foundation must not
			// be warped to terrain. `single`, NOT `legacy`: legacy elements
			// add BlockIgnoreProcessor.STRUCTURE_AND_AIR (verified in the
			// game jar), which skips every explicit air block, so the sunk
			// pit shaft and the hut interior would stay filled with terrain
			StructurePoolElement latrineElement = StructurePoolElement.single(
				LATRINE_STRUCTURE.toString()
			).apply(StructureTemplatePool.Projection.RIGID);

			for (Identifier poolId : HOUSES_POOLS) {
				StructureTemplatePool pool = poolRegistry.getValue(poolId);
				if (pool == null) {
					LOGGER.error("[{}] Village pool not found: {}", Main.MOD_ID, poolId);
					continue;
				}
				if (addElementToPool(pool, latrineElement, LATRINE_WEIGHT)) {
					LOGGER.info("[{}] Added latrine to {}", Main.MOD_ID, poolId);
				}
			}
		});
	}

	/**
	 * Inject an element into a StructureTemplatePool via reflection.
	 *
	 * WARNING: This depends on StructureTemplatePool's private fields
	 * "templates" (flattened ObjectArrayList used for random selection) and
	 * "rawTemplates" (weighted pair list used for data pack reload). This is
	 * the most version-fragile code in the mod; re-verify both field names
	 * against the game jar on every version bump.
	 */
	@SuppressWarnings("unchecked")
	private static boolean addElementToPool(StructureTemplatePool pool, StructurePoolElement element, int weight) {
		try {
			Field templatesField = StructureTemplatePool.class.getDeclaredField("templates");
			templatesField.setAccessible(true);
			ObjectArrayList<StructurePoolElement> elements =
				(ObjectArrayList<StructurePoolElement>) templatesField.get(pool);
			for (int i = 0; i < weight; i++) {
				elements.add(element);
			}

			Field rawTemplatesField = StructureTemplatePool.class.getDeclaredField("rawTemplates");
			rawTemplatesField.setAccessible(true);
			List<Pair<StructurePoolElement, Integer>> rawElements =
				(List<Pair<StructurePoolElement, Integer>>) rawTemplatesField.get(pool);
			// The list may be immutable; swap in a mutable copy
			List<Pair<StructurePoolElement, Integer>> mutable = new ArrayList<>(rawElements);
			mutable.add(Pair.of(element, weight));
			rawTemplatesField.set(pool, mutable);

			return true;
		} catch (Exception e) {
			LOGGER.error("[{}] Failed to inject into structure pool (Minecraft version change may have altered StructureTemplatePool internals): {}",
				Main.MOD_ID, e.getMessage());
			return false;
		}
	}
}
