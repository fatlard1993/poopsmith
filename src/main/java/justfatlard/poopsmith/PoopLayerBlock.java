package justfatlard.poopsmith;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Snow-layer-alike: 1-8 stackable layers with snow-parity shapes, placement,
 * and support rules. Instead of melting, layers slowly decay into fertility:
 * each decay tick removes one layer and applies vanilla bonemeal growth nearby.
 */
public class PoopLayerBlock extends Block {
	public static final int MAX_LAYERS = 8;
	public static final IntegerProperty LAYERS = BlockStateProperties.LAYERS;

	// 1-in-N gate on top of vanilla random ticks; N=40 puts the expected
	// lifetime of a single layer around 45 real minutes (roughly 2 day cycles)
	private static final int DECAY_CHANCE = 40;

	private static final VoxelShape[] SHAPES = new VoxelShape[MAX_LAYERS + 1];
	static {
		SHAPES[0] = Shapes.empty();
		for (int i = 1; i <= MAX_LAYERS; i++) {
			SHAPES[i] = Block.box(0.0D, 0.0D, 0.0D, 16.0D, i * 2.0D, 16.0D);
		}
	}

	public PoopLayerBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.getStateDefinition().any().setValue(LAYERS, 1));
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
		return SHAPES[state.getValue(LAYERS)];
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
		// Snow parity: collision is one layer shorter, so a single layer is walk-through
		return SHAPES[state.getValue(LAYERS) - 1];
	}

	@Override
	protected VoxelShape getBlockSupportShape(BlockState state, BlockGetter world, BlockPos pos) {
		return SHAPES[state.getValue(LAYERS)];
	}

	@Override
	protected VoxelShape getVisualShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
		return SHAPES[state.getValue(LAYERS)];
	}

	@Override
	protected boolean useShapeForLightOcclusion(BlockState state) {
		return true;
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader world, BlockPos pos) {
		BlockState below = world.getBlockState(pos.below());
		return Block.isFaceFull(below.getBlockSupportShape(world, pos.below()), Direction.UP)
			|| (below.is(this) && below.getValue(LAYERS) == MAX_LAYERS);
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader world, ScheduledTickAccess tickView, BlockPos pos, Direction direction, BlockPos neighborPos, BlockState neighborState, RandomSource random) {
		if (!state.canSurvive(world, pos)) {
			return Blocks.AIR.defaultBlockState();
		}
		return super.updateShape(state, world, tickView, pos, direction, neighborPos, neighborState, random);
	}

	@Override
	protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
		int layers = state.getValue(LAYERS);
		if (context.getItemInHand().is(this.asItem()) && layers < MAX_LAYERS) {
			if (context.replacingClickedOnBlock()) {
				return context.getClickedFace() == Direction.UP;
			}
			return true;
		}
		return layers == 1;
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockState existing = context.getLevel().getBlockState(context.getClickedPos());
		if (existing.is(this)) {
			int layers = existing.getValue(LAYERS);
			return existing.setValue(LAYERS, Math.min(MAX_LAYERS, layers + 1));
		}
		return this.defaultBlockState();
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
		if (random.nextInt(DECAY_CHANCE) != 0) return;

		int layers = state.getValue(LAYERS);
		if (layers > 1) {
			world.setBlockAndUpdate(pos, state.setValue(LAYERS, layers - 1));
		} else {
			world.removeBlock(pos, false);
		}
		PoopPlacement.fertilizeAround(world, pos);
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(LAYERS);
	}
}
