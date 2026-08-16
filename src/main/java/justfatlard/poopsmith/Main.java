package justfatlard.poopsmith;

import justfatlard.pandorical.api.BlockRegistration;
import justfatlard.pandorical.api.ItemRegistration;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.poopsmith.integration.VillageBuilderIntegration;
import justfatlard.poopsmith.player.PlayerPoopManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Compostable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import net.minecraft.world.level.storage.loot.providers.number.ResolvableNumber;
import net.minecraft.core.component.DataComponents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

public class Main implements ModInitializer {
	public static final String MOD_ID = "poopsmith";
	private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static final Identifier POOP_ID = Identifier.fromNamespaceAndPath(MOD_ID, "poop");
	public static final Identifier POOP_LAYER_ID = Identifier.fromNamespaceAndPath(MOD_ID, "poop_layer");
	public static final Identifier POOP_BLOCK_ID = Identifier.fromNamespaceAndPath(MOD_ID, "poop_block");
	public static final Identifier GUANO_ID = Identifier.fromNamespaceAndPath(MOD_ID, "guano");
	public static final Identifier GUANO_LAYER_ID = Identifier.fromNamespaceAndPath(MOD_ID, "guano_layer");
	public static final Identifier GUANO_BLOCK_ID = Identifier.fromNamespaceAndPath(MOD_ID, "guano_block");
	public static final Identifier BAT_BOX_ID = Identifier.fromNamespaceAndPath(MOD_ID, "bat_box");

	public static final ResourceKey<Block> POOP_LAYER_BLOCK_KEY = ResourceKey.create(Registries.BLOCK, POOP_LAYER_ID);
	public static final ResourceKey<Block> POOP_BLOCK_BLOCK_KEY = ResourceKey.create(Registries.BLOCK, POOP_BLOCK_ID);
	public static final ResourceKey<Block> GUANO_LAYER_BLOCK_KEY = ResourceKey.create(Registries.BLOCK, GUANO_LAYER_ID);
	public static final ResourceKey<Block> GUANO_BLOCK_BLOCK_KEY = ResourceKey.create(Registries.BLOCK, GUANO_BLOCK_ID);
	public static final ResourceKey<Block> BAT_BOX_BLOCK_KEY = ResourceKey.create(Registries.BLOCK, BAT_BOX_ID);
	public static final ResourceKey<Item> POOP_ITEM_KEY = ResourceKey.create(Registries.ITEM, POOP_ID);
	public static final ResourceKey<Item> POOP_LAYER_ITEM_KEY = ResourceKey.create(Registries.ITEM, POOP_LAYER_ID);
	public static final ResourceKey<Item> POOP_BLOCK_ITEM_KEY = ResourceKey.create(Registries.ITEM, POOP_BLOCK_ID);
	public static final ResourceKey<Item> GUANO_ITEM_KEY = ResourceKey.create(Registries.ITEM, GUANO_ID);
	public static final ResourceKey<Item> GUANO_LAYER_ITEM_KEY = ResourceKey.create(Registries.ITEM, GUANO_LAYER_ID);
	public static final ResourceKey<Item> GUANO_BLOCK_ITEM_KEY = ResourceKey.create(Registries.ITEM, GUANO_BLOCK_ID);
	public static final ResourceKey<Item> BAT_BOX_ITEM_KEY = ResourceKey.create(Registries.ITEM, BAT_BOX_ID);
	public static final ResourceKey<EntityType<?>> POOP_ENTITY_KEY = ResourceKey.create(Registries.ENTITY_TYPE, POOP_ID);

	// Vanilla data-driven composting providers: medium ≈ moderate, medium_high ≈ high
	private static final Compostable COMPOSTABLE_MEDIUM = new Compostable(ResolvableNumber.fromKey(
		ResourceKey.create(Registries.NUMBER_PROVIDER, Identifier.parse("minecraft:compostable/medium"))));
	private static final Compostable COMPOSTABLE_HIGH = new Compostable(ResolvableNumber.fromKey(
		ResourceKey.create(Registries.NUMBER_PROVIDER, Identifier.parse("minecraft:compostable/medium_high"))));
	// always_add_one is the top of the vanilla compostable ladder (a flat 1.0);
	// no "high" provider exists in this snapshot
	private static final ResourceKey<NumberProvider> COMPOSTABLE_ALWAYS_KEY =
		ResourceKey.create(Registries.NUMBER_PROVIDER, Identifier.parse("minecraft:compostable/always_add_one"));

	// Furnace fuel is data-driven in this snapshot: cooking-time number providers,
	// halved in fast-cooking blocks. Ours live in data/poopsmith/number_provider/cooking/.
	private static final ResourceKey<NumberProvider> FUEL_POOP_KEY =
		ResourceKey.create(Registries.NUMBER_PROVIDER, Identifier.fromNamespaceAndPath(MOD_ID, "cooking/time_poop"));
	private static final ResourceKey<NumberProvider> FUEL_POOP_BLOCK_KEY =
		ResourceKey.create(Registries.NUMBER_PROVIDER, Identifier.fromNamespaceAndPath(MOD_ID, "cooking/time_poop_block"));
	private static final ResourceKey<NumberProvider> FUEL_GUANO_KEY =
		ResourceKey.create(Registries.NUMBER_PROVIDER, Identifier.fromNamespaceAndPath(MOD_ID, "cooking/time_guano"));
	private static final ResourceKey<NumberProvider> FUEL_GUANO_BLOCK_KEY =
		ResourceKey.create(Registries.NUMBER_PROVIDER, Identifier.fromNamespaceAndPath(MOD_ID, "cooking/time_guano_block"));
	private static final ResourceKey<NumberProvider> FUEL_WOOD_BLOCKS_KEY =
		ResourceKey.create(Registries.NUMBER_PROVIDER, Identifier.parse("minecraft:cooking/time_wood_blocks"));

	public static final PoopLayerBlock POOP_LAYER_BLOCK = new PoopLayerBlock(
		BlockBehaviour.Properties.ofFullCopy(Blocks.SNOW)
			.mapColor(MapColor.COLOR_BROWN)
			.strength(0.2F)
			.sound(SoundType.MUD)
			.randomTicks()
			.setId(POOP_LAYER_BLOCK_KEY)
	);

	public static final Block POOP_BLOCK = new Block(
		BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_BROWN)
			.strength(0.5F)
			.sound(SoundType.MUD)
			// Snow parity with the layers: drops need a shovel; a shovel-less
			// break force-decays instead (see the AFTER handler below)
			.requiresCorrectToolForDrops()
			.setId(POOP_BLOCK_BLOCK_KEY)
	);

	public static final EntityType<PoopEntity> POOP_ENTITY_TYPE = EntityType.Builder
		.<PoopEntity>of(PoopEntity::new, MobCategory.MISC)
		.sized(0.25F, 0.25F)
		.clientTrackingRange(4)
		.updateInterval(10)
		.build(POOP_ENTITY_KEY);

	public static final PoopItem POOP_ITEM = new PoopItem(
		new Item.Properties()
			.setId(POOP_ITEM_KEY)
			.stacksTo(16)
			.component(DataComponents.COMPOSTABLE, COMPOSTABLE_MEDIUM)
			.cookingFuel(FUEL_POOP_KEY)
	);

	public static final BlockItem POOP_LAYER_ITEM = new BlockItem(
		POOP_LAYER_BLOCK,
		new Item.Properties()
			.setId(POOP_LAYER_ITEM_KEY)
			.useBlockDescriptionPrefix()
			.cookingFuel(FUEL_POOP_KEY)
	);

	public static final BlockItem POOP_BLOCK_ITEM = new BlockItem(
		POOP_BLOCK,
		new Item.Properties()
			.setId(POOP_BLOCK_ITEM_KEY)
			.useBlockDescriptionPrefix()
			.component(DataComponents.COMPOSTABLE, COMPOSTABLE_HIGH)
			.cookingFuel(FUEL_POOP_BLOCK_KEY)
	);

	public static final PoopLayerBlock GUANO_LAYER_BLOCK = new PoopLayerBlock(
		BlockBehaviour.Properties.ofFullCopy(Blocks.SNOW)
			.mapColor(MapColor.COLOR_LIGHT_GRAY)
			.strength(0.2F)
			.sound(SoundType.SNOW)
			.randomTicks()
			.setId(GUANO_LAYER_BLOCK_KEY)
	);

	public static final Block GUANO_BLOCK = new Block(
		BlockBehaviour.Properties.of()
			.mapColor(MapColor.TERRACOTTA_WHITE)
			.strength(0.5F)
			.sound(SoundType.SAND)
			.requiresCorrectToolForDrops()
			.setId(GUANO_BLOCK_BLOCK_KEY)
	);

	public static final BatBoxBlock BAT_BOX_BLOCK = new BatBoxBlock(
		BlockBehaviour.Properties.of()
			.mapColor(MapColor.WOOD)
			.strength(1.5F)
			.sound(SoundType.WOOD)
			.setId(BAT_BOX_BLOCK_KEY)
	);

	public static final BlockEntityType<BatBoxBlockEntity> BAT_BOX_BLOCK_ENTITY =
		new BlockEntityType<>(BatBoxBlockEntity::new, Set.of(BAT_BOX_BLOCK));

	// Eaten (not thrown): a plain low-value food, always edible because its
	// real job is curing diarrhea (see PlayerPoopManager.onAte)
	public static final Item GUANO_ITEM = new Item(
		new Item.Properties()
			.setId(GUANO_ITEM_KEY)
			.food(new FoodProperties.Builder().nutrition(1).saturationModifier(0.2F).alwaysEdible().build())
			.compostable(COMPOSTABLE_ALWAYS_KEY)
			.cookingFuel(FUEL_GUANO_KEY)
	);

	public static final BlockItem GUANO_LAYER_ITEM = new BlockItem(
		GUANO_LAYER_BLOCK,
		new Item.Properties()
			.setId(GUANO_LAYER_ITEM_KEY)
			.useBlockDescriptionPrefix()
			.cookingFuel(FUEL_GUANO_KEY)
	);

	public static final BlockItem GUANO_BLOCK_ITEM = new BlockItem(
		GUANO_BLOCK,
		new Item.Properties()
			.setId(GUANO_BLOCK_ITEM_KEY)
			.useBlockDescriptionPrefix()
			.compostable(COMPOSTABLE_ALWAYS_KEY)
			.cookingFuel(FUEL_GUANO_BLOCK_KEY)
	);

	public static final BlockItem BAT_BOX_ITEM = new BlockItem(
		BAT_BOX_BLOCK,
		new Item.Properties()
			.setId(BAT_BOX_ITEM_KEY)
			.useBlockDescriptionPrefix()
			// Planks parity, matching vanilla's wooden shelves
			.cookingFuel(FUEL_WOOD_BLOCKS_KEY)
	);

	@Override
	public void onInitialize() {
		PandoricalApi.content().registerBlock(MOD_ID + ":poop_layer", new BlockRegistration()
			.baseBlock("minecraft:snow")
			.model(MOD_ID + ":block/poop_height2"));
		PandoricalApi.content().registerBlock(MOD_ID + ":poop_block", new BlockRegistration()
			.baseBlock("minecraft:mud")
			.model(MOD_ID + ":block/poop_block"));
		PandoricalApi.content().registerItem(MOD_ID + ":poop", new ItemRegistration()
			.model(MOD_ID + ":item/poop"));
		PandoricalApi.content().registerItem(MOD_ID + ":poop_layer", new ItemRegistration()
			.model(MOD_ID + ":item/poop_layer"));
		PandoricalApi.content().registerItem(MOD_ID + ":poop_block", new ItemRegistration()
			.model(MOD_ID + ":item/poop_block"));
		PandoricalApi.content().registerBlock(MOD_ID + ":guano_layer", new BlockRegistration()
			.baseBlock("minecraft:snow")
			.model(MOD_ID + ":block/guano_height2"));
		PandoricalApi.content().registerBlock(MOD_ID + ":guano_block", new BlockRegistration()
			.baseBlock("minecraft:sand")
			.model(MOD_ID + ":block/guano_block"));
		PandoricalApi.content().registerBlock(MOD_ID + ":bat_box", new BlockRegistration()
			.baseBlock("minecraft:oak_planks")
			.model(MOD_ID + ":block/bat_box"));
		PandoricalApi.content().registerItem(MOD_ID + ":guano", new ItemRegistration()
			.model(MOD_ID + ":item/guano"));
		PandoricalApi.content().registerItem(MOD_ID + ":guano_layer", new ItemRegistration()
			.model(MOD_ID + ":item/guano_layer"));
		PandoricalApi.content().registerItem(MOD_ID + ":guano_block", new ItemRegistration()
			.model(MOD_ID + ":item/guano_block"));
		PandoricalApi.content().registerItem(MOD_ID + ":bat_box", new ItemRegistration()
			.model(MOD_ID + ":item/bat_box"));
		PandoricalApi.content().registerModAssets(MOD_ID);

		Registry.register(BuiltInRegistries.BLOCK, POOP_LAYER_ID, POOP_LAYER_BLOCK);
		Registry.register(BuiltInRegistries.BLOCK, POOP_BLOCK_ID, POOP_BLOCK);
		Registry.register(BuiltInRegistries.BLOCK, GUANO_LAYER_ID, GUANO_LAYER_BLOCK);
		Registry.register(BuiltInRegistries.BLOCK, GUANO_BLOCK_ID, GUANO_BLOCK);
		Registry.register(BuiltInRegistries.BLOCK, BAT_BOX_ID, BAT_BOX_BLOCK);
		Registry.register(BuiltInRegistries.ITEM, POOP_ID, POOP_ITEM);
		Registry.register(BuiltInRegistries.ITEM, POOP_LAYER_ID, POOP_LAYER_ITEM);
		Registry.register(BuiltInRegistries.ITEM, POOP_BLOCK_ID, POOP_BLOCK_ITEM);
		Registry.register(BuiltInRegistries.ITEM, GUANO_ID, GUANO_ITEM);
		Registry.register(BuiltInRegistries.ITEM, GUANO_LAYER_ID, GUANO_LAYER_ITEM);
		Registry.register(BuiltInRegistries.ITEM, GUANO_BLOCK_ID, GUANO_BLOCK_ITEM);
		Registry.register(BuiltInRegistries.ITEM, BAT_BOX_ID, BAT_BOX_ITEM);
		Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, BAT_BOX_ID, BAT_BOX_BLOCK_ENTITY);
		Registry.register(BuiltInRegistries.ENTITY_TYPE, POOP_ID, POOP_ENTITY_TYPE);
		PandoricalApi.registerEntityRenderer(POOP_ENTITY_TYPE, "thrown_item");

		ResourceKey<CreativeModeTab> tabKey = ResourceKey.create(
			Registries.CREATIVE_MODE_TAB, Identifier.fromNamespaceAndPath(MOD_ID, "poopsmith"));
		CreativeModeTab poopsmithGroup = FabricCreativeModeTab.builder()
			.title(Component.translatable("itemGroup.poopsmith.poopsmith"))
			.icon(() -> new ItemStack(POOP_ITEM))
			.displayItems((context, entries) -> {
				entries.accept(new ItemStack(POOP_ITEM));
				entries.accept(new ItemStack(POOP_LAYER_ITEM));
				entries.accept(new ItemStack(POOP_BLOCK_ITEM));
				entries.accept(new ItemStack(GUANO_ITEM));
				entries.accept(new ItemStack(GUANO_LAYER_ITEM));
				entries.accept(new ItemStack(GUANO_BLOCK_ITEM));
				entries.accept(new ItemStack(BAT_BOX_ITEM));
			})
			.build();
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, tabKey, poopsmithGroup);

		// The poop keybind is a Pandorical pooled keybind: no poopsmith client
		// code exists at all. 10 is InputConstants.KEY_G in this snapshot's
		// own key table (NOT GLFW's 71, which is scroll lock here); a literal
		// because InputConstants is client-only, absent on a dedicated server
		PandoricalApi.keybinds().register(MOD_ID + ":poop", 10, "Poop",
			PlayerPoopManager::tryManualPoop);

		ServerTickEvents.END_SERVER_TICK.register(PlayerPoopManager::onServerTick);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
			PlayerPoopManager.onPlayerJoin(handler.getPlayer()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
			PlayerPoopManager.onPlayerDisconnect(handler.getPlayer().getUUID()));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> PlayerPoopManager.onServerStopping());
		// The first-ever-Nether-entry scare is detected in PlayerPoopManager's
		// per-tick loop (see checkFirstNetherEntry for why not an event)

		// Forced decay: breaking any poop-family block without a shovel
		// applies the decay's bonemeal action instead of drops (drops are
		// tool-gated via requiresCorrectToolForDrops + the mineable/shovel
		// tag, snow-style). One fertilize charge per layer; full blocks are
		// eight layers' worth. Creative breaking stays clean.
		net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.AFTER.register(
			(world, player, pos, state, blockEntity) -> {
				if (!(world instanceof net.minecraft.server.level.ServerLevel serverWorld)) return;
				if (player.isCreative()) return;
				boolean isLayer = state.is(POOP_LAYER_BLOCK) || state.is(GUANO_LAYER_BLOCK);
				boolean isBlock = state.is(POOP_BLOCK) || state.is(GUANO_BLOCK);
				if (!isLayer && !isBlock) return;
				if (player.getMainHandItem().is(net.minecraft.tags.ItemTags.SHOVELS)) return;
				int charges = isLayer ? state.getValue(PoopLayerBlock.LAYERS) : 8;
				for (int i = 0; i < charges; i++) {
					PoopPlacement.fertilizeAround(serverWorld, pos);
				}
			});

		LatrineStructureInjector.register();
		VillageBuilderIntegration.registerStructures();

		// Latrine quests: guarded class load, LatrineQuestRegistration
		// references village-quests types directly (compileOnly)
		if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("village-quests-justfatlard")) {
			justfatlard.poopsmith.integration.LatrineQuestRegistration.register();
		}

		LOGGER.info("Loaded poopsmith (server-side with Pandorical)");
	}
}
