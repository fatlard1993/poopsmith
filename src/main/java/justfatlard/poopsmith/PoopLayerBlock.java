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
 * Snow-layer-alike: 1-8 stackable layers with snow placement and support
 * rules. Instead of melting, layers slowly decay into fertility:
 * each decay tick removes one layer and applies vanilla bonemeal growth nearby.
 */
public class PoopLayerBlock extends Block {
	public static final int MAX_LAYERS = 8;
	public static final IntegerProperty LAYERS = BlockStateProperties.LAYERS;

	/**
	 * Which way the pile was left facing.
	 *
	 * <p>Only the loose heaps at one and two layers are modelled with a front and a back; from three
	 * up they pack down into flat sheets where a rotation would be invisible. The property is on
	 * every state regardless, because a blockstate variant that names no facing matches all of
	 * them, and a pile that grows past two simply stops caring which way it was pointing.
	 */
	public static final net.minecraft.world.level.block.state.properties.EnumProperty<Direction> FACING =
		BlockStateProperties.HORIZONTAL_FACING;

	// 1-in-N gate on top of vanilla random ticks; N=40 puts the expected
	// lifetime of a single layer around 45 real minutes (roughly 2 day cycles)
	private static final int DECAY_CHANCE = 40;

	// Two pixels per layer, snow parity, except the first two: those render as
	// chunky piles (see generate_models.py) rather than flat sheets, and their
	// boxes match the pile peaks so the outline sits on the model instead of
	// sinking into it. A loose heap peaks at the same 6 pixels the third layer
	// packs down to, which is where stacking takes walls anyway.
	/** Heaps rather than sheets at the bottom, so the first two layers start taller. */
	public static final int[] PILE_HEIGHTS = {0, 4, 6, 6, 8, 10, 12, 14, 16};

	/** Snow parity, two pixels a layer all the way up: what guano uses. */
	public static final int[] SHEET_HEIGHTS = {0, 2, 4, 6, 8, 10, 12, 14, 16};

	private final int[] heights;
	private final VoxelShape[] shapes = new VoxelShape[MAX_LAYERS + 1];

	/** Top face of the pile, in blocks: where anything sitting on it rests. */
	public static double topOf(BlockState state) {
		PoopLayerBlock block = (PoopLayerBlock) state.getBlock();
		return block.heights[state.getValue(LAYERS)] / 16.0D;
	}

	public PoopLayerBlock(Properties properties, int[] heights) {
		super(properties);
		this.heights = heights;
		shapes[0] = Shapes.empty();
		for (int i = 1; i <= MAX_LAYERS; i++) {
			shapes[i] = Block.box(0.0D, 0.0D, 0.0D, 16.0D, heights[i], 16.0D);
		}
		this.registerDefaultState(this.getStateDefinition().any()
			.setValue(LAYERS, 1)
			.setValue(FACING, Direction.NORTH));
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
		return shapes[state.getValue(LAYERS)];
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
		// Nothing to stand on, at any depth. Snow parity gave the deeper piles a collision box one
		// layer short of the model, which meant a pen that had not been mucked out slowly grew a
		// step: the animals walked up their own leavings and over the fence.
		//
		// It also settles a disagreement with the client. Pandorical builds the client's stand-in
		// from the first state alone and reads its collision as the whole block's - see
		// DynamicBlock.declaresCollision - and the first state here is layers=1, which was already
		// empty. So the client has always treated every pile as walk-through while the server held
		// eight of them solid. Empty everywhere is the answer to both.
		return Shapes.empty();
	}

	@Override
	protected VoxelShape getBlockSupportShape(BlockState state, BlockGetter world, BlockPos pos) {
		return shapes[state.getValue(LAYERS)];
	}

	@Override
	protected VoxelShape getVisualShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
		return shapes[state.getValue(LAYERS)];
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
		builder.add(LAYERS, FACING);
	}
}
