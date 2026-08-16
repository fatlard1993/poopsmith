package justfatlard.poopsmith;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Digs the latrine's pit down past the depth the jigsaw placer will tolerate.
 *
 * Why this exists at all: a village house is chosen by
 * JigsawPlacement$Placer, and every vanilla street's building_entrance jigsaw
 * points at a block that is still inside the street piece's own bounding box
 * (182 of 182 across the five biomes), which makes the placer require the
 * candidate house to fit inside THAT box rather than inside the shared free
 * space. Street pieces are two rows tall, so a house that sinks more than two
 * rows below its own entrance-jigsaw row is silently rejected: no error, the
 * placer just moves on to the next pool entry. Measured with five identical
 * test huts sinking 1..5 rows, injected at weight 200 each into
 * village/plains/houses across four force-generated villages: only the 1- and
 * 2-row huts ever appeared, the 3-, 4- and 5-row ones never did.
 *
 * A flush door spends one of those two rows (the floor sits one row below the
 * door), so the template can only carry a one-row pit. This processor runs in
 * finalizeProcessing, which happens after the placer's bounding-box check and
 * before the template's own blocks are written, and which is handed a WRITABLE
 * ServerLevelAccessor: it opens the shaft the rest of the way down and moves
 * the seed poop block to the new bottom.
 *
 * It refuses to dig into air, fluid, or below the world floor, so a latrine
 * that lands over a cave keeps a shallower pit rather than opening into it.
 */
public final class LatrinePitProcessor implements StructureProcessor {
	/** Open blocks below the hut floor's walking surface once the pit is dug. */
	public static final int TARGET_PIT_DEPTH = 4;
	/** What create_latrine.py's template can carry on its own (see above). */
	private static final int TEMPLATE_PIT_DEPTH = 1;
	private static final int EXTRA_ROWS = TARGET_PIT_DEPTH - TEMPLATE_PIT_DEPTH;

	public static final LatrinePitProcessor INSTANCE = new LatrinePitProcessor();
	public static final MapCodec<LatrinePitProcessor> CODEC = MapCodec.unit(INSTANCE);

	private LatrinePitProcessor() {}

	/** Register the codec so a serialized pool referencing this type resolves. */
	public static void register() {
		Registry.register(BuiltInRegistries.STRUCTURE_PROCESSOR,
			Identifier.fromNamespaceAndPath(Main.MOD_ID, "latrine_pit"), CODEC);
	}

	@Override
	public MapCodec<? extends StructureProcessor> codec() {
		return CODEC;
	}

	@Override
	public List<StructureTemplate.StructureBlockInfo> finalizeProcessing(
			ServerLevelAccessor world,
			BlockPos origin,
			BlockPos pivot,
			List<StructureTemplate.StructureBlockInfo> originalBlocks,
			List<StructureTemplate.StructureBlockInfo> processedBlocks,
			StructurePlaceSettings settings) {
		List<StructureTemplate.StructureBlockInfo> result = new ArrayList<>(processedBlocks);

		for (int i = 0; i < result.size(); i++) {
			StructureTemplate.StructureBlockInfo info = result.get(i);
			if (!info.state().is(Main.POOP_BLOCK)) continue;

			// The template's seed sits one row under the floor; everything
			// below it is still untouched terrain at this point, because the
			// template's own blocks have not been written yet.
			BlockPos templateSeed = info.pos();
			int dug = diggableRows(world, templateSeed);
			if (dug <= 0) continue;

			BlockPos newSeed = templateSeed.below(dug);
			for (int d = 1; d < dug; d++) {
				world.setBlock(templateSeed.below(d), Blocks.AIR.defaultBlockState(),
					Block.UPDATE_CLIENTS);
			}
			world.setBlock(newSeed, Main.POOP_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);

			// The template would otherwise put a second poop block back on top
			// of the shaft and cap it one row down; open that row instead.
			result.set(i, new StructureTemplate.StructureBlockInfo(
				templateSeed, Blocks.AIR.defaultBlockState(), null));
		}

		return result;
	}

	/**
	 * How many rows under the template's seed can be opened before hitting
	 * something that must not be dug through. Returns 0 when the pit should be
	 * left exactly as the template placed it.
	 */
	private static int diggableRows(ServerLevelAccessor world, BlockPos templateSeed) {
		int usable = 0;
		for (int d = 1; d <= EXTRA_ROWS; d++) {
			BlockPos pos = templateSeed.below(d);
			if (pos.getY() <= world.getMinY() + 1) break;
			BlockState state = world.getBlockState(pos);
			// Air, water, lava, or anything already carved: stop here rather
			// than opening the privy into a cave, an aquifer, or the void
			if (state.isAir() || !state.getFluidState().isEmpty()) break;
			usable = d;
		}
		return usable;
	}
}
