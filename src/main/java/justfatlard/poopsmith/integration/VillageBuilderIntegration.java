package justfatlard.poopsmith.integration;

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
 * Reflection keeps the build free of any village-builder dependency, matching
 * village-mail's VillageBuilderIntegration. Actual API surface targeted:
 *   justfatlard.village_builder.api.VillageBuilderAPI#registerTemplatePersistent
 *   justfatlard.village_builder.village.VillageNeedsAnalyzer$VillageNeed
 *   justfatlard.village_builder.building.StructureType$MaterialRequirement
 */
public final class VillageBuilderIntegration {
	private static final Logger LOGGER = LoggerFactory.getLogger(Main.MOD_ID);

	private static final Identifier LATRINE_TEMPLATE = Identifier.fromNamespaceAndPath(Main.MOD_ID, "latrine");
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
			// block seeds the pit
			List<Object> materials = List.of(
				matReq.newInstance(Items.OAK_PLANKS, 24),
				matReq.newInstance(Items.COBBLESTONE, 32),
				matReq.newInstance((Item) Main.POOP_BLOCK_ITEM, 1)
			);

			registerTemplatePersistent.invoke(null,
				LATRINE_TEMPLATE,
				"Village Latrine",
				Set.of(needUtility),
				materials,
				Set.of(),
				CLEARANCE_SIZE
			);

			LOGGER.info("[{}] Registered latrine with village-builder", Main.MOD_ID);
		} catch (ClassNotFoundException e) {
			LOGGER.error("[{}] Village Builder API not found (wrong version?): {}", Main.MOD_ID, e.getMessage());
		} catch (Exception e) {
			LOGGER.error("[{}] Failed to register latrine with village-builder: {}", Main.MOD_ID, e.getMessage(), e);
		}
	}
}
