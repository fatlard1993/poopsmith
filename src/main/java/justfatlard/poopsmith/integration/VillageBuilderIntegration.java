package justfatlard.poopsmith.integration;

import justfatlard.poopsmith.LatrineStructureInjector;
import justfatlard.poopsmith.Main;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Soft integration with the Village Builder mod: registers the latrine as a
 * buildable UTILITY structure so growing villages can construct one.
 *
 * One variant per village biome, each carrying its own biome preference, so a
 * growing village builds the privy that matches its own houses (village-mail's
 * post offices register the same way; StructureEntry#matchesBiome treats an
 * empty preference set as "any biome" and otherwise requires an exact match).
 *
 * Village Builder places the template directly, so a latrine it builds keeps
 * the shallow one-row pit the template carries. The deeper pit is dug by
 * LatrinePitProcessor, which only runs on the worldgen path (see that class
 * for why the template cannot carry the depth itself).
 *
 * Reflection keeps the build free of any village-builder dependency, matching
 * village-mail's VillageBuilderIntegration. Actual API surface targeted:
 *   justfatlard.village_builder.api.VillageBuilderAPI#registerTemplatePersistent
 *   justfatlard.village_builder.village.VillageNeedsAnalyzer$VillageNeed
 *   justfatlard.village_builder.building.StructureType$MaterialRequirement
 */
public final class VillageBuilderIntegration {
	private static final Logger LOGGER = LoggerFactory.getLogger(Main.MOD_ID);

	private static final String[] BIOMES = {"plains", "desert", "savanna", "snowy", "taiga"};
	private static final int CLEARANCE_SIZE = 4;

	private VillageBuilderIntegration() {}

	public static void registerStructures() {
		if (!FabricLoader.getInstance().isModLoaded("village-builder")) {
			return;
		}

		try {
			Class<?> apiClass = Class.forName("justfatlard.village_builder.api.VillageBuilderAPI");

			@SuppressWarnings({"unchecked", "rawtypes"})
			Class<Enum<?>> needEnum = (Class<Enum<?>>) Class.forName(
				"justfatlard.village_builder.village.VillageNeedsAnalyzer$VillageNeed");
			@SuppressWarnings({"unchecked", "rawtypes"})
			Object needUtility = Enum.valueOf((Class) needEnum, "UTILITY");

			Class<?> matReqClass = Class.forName(
				"justfatlard.village_builder.building.StructureType$MaterialRequirement");
			Constructor<?> matReq = matReqClass.getDeclaredConstructor(Item.class, int.class);

			Method registerTemplatePersistent = apiClass.getMethod("registerTemplatePersistent",
				Identifier.class, String.class, Set.class, List.class, Set.class, int.class);

			// Fallback materials for when NBT analysis fails; the single poop
			// block seeds the pit. The count is deliberately generous: the pit
			// sinks four rows, so the variants carry a solid foundation block
			// under the whole 5x5 footprint
			List<Object> materials = List.of(
				matReq.newInstance(Items.OAK_PLANKS, 24),
				matReq.newInstance(Items.COBBLESTONE, 96),
				matReq.newInstance((Item) Main.POOP_BLOCK_ITEM, 1)
			);

			for (String biome : BIOMES) {
				registerTemplatePersistent.invoke(null,
					LatrineStructureInjector.latrineTemplate(biome),
					formatName(biome) + " Latrine",
					Set.of(needUtility),
					materials,
					Set.of(biome),
					CLEARANCE_SIZE
				);
				LOGGER.info("[{}] Registered {} latrine with village-builder", Main.MOD_ID, biome);
			}
		} catch (ClassNotFoundException e) {
			LOGGER.error("[{}] Village Builder API not found (wrong version?): {}", Main.MOD_ID, e.getMessage());
		} catch (Exception e) {
			LOGGER.error("[{}] Failed to register latrine with village-builder: {}", Main.MOD_ID, e.getMessage(), e);
		}
	}

	private static String formatName(String biome) {
		return biome.substring(0, 1).toUpperCase() + biome.substring(1);
	}
}
