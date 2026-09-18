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
 * rules. Instead of melting, layers rot away one at a time, and now and then the rotting
 * leaves a bonemeal growth in the ground beneath.
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

	/**
	 * Whether the pile is on a bed, which is the one thing it sits down into.
	 *
	 * <p>A pile is a block of its own, on top of the block below, so on a bed it floated seven
	 * pixels over the mattress. On a bed it is drawn, outlined and stood on that much lower, and it
	 * stops at {@link #BED_LAYERS}: droppings already slide off at that height on open ground (see
	 * PoopPlacement.OPEN_STACK_LAYERS), and one placed by hand stops there too, so a bed never
	 * carries a heap that would have to reach down further than the mattress allows.
	 */
	public static final net.minecraft.world.level.block.state.properties.BooleanProperty ON_BED =
		net.minecraft.world.level.block.state.properties.BooleanProperty.create("on_bed");

	/** Pixels between a bed's top and the block above it: how far a pile on one sits down. */
	private static final int BED_DROP = 7;

	/** The most a pile on a bed holds. */
	public static final int BED_LAYERS = 3;

	// 1-in-N gate on top of vanilla random ticks. A block sees a random tick about once a
	// minute, so N is roughly the expected minutes a single layer lasts: 40 was three quarters
	// of an hour, which meant a small pen was permanently ankle deep no matter how often
	// anybody shovelled it. Ten is about half a Minecraft day - slow enough to be worth
	// harvesting, fast enough that ground nobody crowds comes back on its own.
	private static final int DECAY_CHANCE = 10;

	/**
	 * How often a decaying layer feeds the ground under it.
	 *
	 * <p>It used to be every time, which made any pen a fertiliser engine running whether or not
	 * anybody tended it - and now that layers go four times as fast, every time would be four
	 * times the bonemeal for the same animals. A quarter keeps the good turn without the ground
	 * around a paddock growing like it has a farmer.
	 */
	private static final int FERTILISE_CHANCE = 4;

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
	/** On the ground, then on a bed; by layers. */
	private final VoxelShape[][] shapes = new VoxelShape[2][MAX_LAYERS + 1];

	/** Top face of the pile, in blocks: where anything sitting on it rests. */
	public static double topOf(BlockState state) {
		PoopLayerBlock block = (PoopLayerBlock) state.getBlock();
		return (block.heights[state.getValue(LAYERS)] - (state.getValue(ON_BED) ? BED_DROP : 0)) / 16.0D;
	}

	/**
	 * Whether the pile rots away. Poop does, a layer at a time, feeding the ground as it goes.
	 * Guano does not: it is dry, a roost drops it on the same spot for years, and the whole point
	 * of a bat colony is the bed of it that builds up to be quarried - which a pile rotting as
	 * fast as the bats could refill it never did.
	 */
	private final boolean rots;

	public PoopLayerBlock(Properties properties, int[] heights) {
		this(properties, heights, true);
	}

	public PoopLayerBlock(Properties properties, int[] heights, boolean rots) {
		super(properties);
		this.heights = heights;
		this.rots = rots;
		for (int bed = 0; bed <= 1; bed++) {
			int drop = bed * BED_DROP;
			shapes[bed][0] = Shapes.empty();
			for (int i = 1; i <= MAX_LAYERS; i++) {
				shapes[bed][i] = Block.box(0.0D, -drop, 0.0D, 16.0D, heights[i] - drop, 16.0D);
			}
		}
		this.registerDefaultState(this.getStateDefinition().any()
			.setValue(LAYERS, 1)
			.setValue(FACING, Direction.NORTH)
			.setValue(ON_BED, false));
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
		return shapeOf(state);
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
		return shapeOf(state);
	}

	@Override
	protected VoxelShape getVisualShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
		return shapeOf(state);
	}

	@Override
	protected boolean useShapeForLightOcclusion(BlockState state) {
		return true;
	}

	/** The loose heaps: modelled as lumps that leave most of the footprint bare. */
	private static final int HEAP_LAYERS = 2;

	@Override
	protected VoxelShape getOcclusionShape(BlockState state) {
		// The outline is the whole footprint, and the game reads the outline as the occlusion
		// shape: the ground under a pile loses its top face. A sheet covers that face, so nothing
		// shows; a heap is lumps with bare ground between them, and through the bare ground the
		// culled face looks straight down into the world. So a heap occludes nothing.
		int layers = state.getValue(LAYERS);
		if (heights == PILE_HEIGHTS && layers <= HEAP_LAYERS) return Shapes.empty();
		// A pile sitting down into the bed does not fill the bottom of its own block: hiding the
		// faces around that would open a hole in the world.
		if (state.getValue(ON_BED)) return Shapes.empty();
		return shapeOf(state);
	}

	/**
	 * Ground that holds a pile despite not being a full cube.
	 *
	 * <p>{@code isFaceFull} asks for a top face flush with the block above, and a dirt path is a
	 * pixel short of one - so a cow standing on a village path left nothing behind, which is most
	 * of the ground a village has. Vanilla answers the same question for snow with
	 * {@code support_override_snow_layer}; this is that list, for piles.
	 */
	public static final net.minecraft.tags.TagKey<Block> SUPPORTS_A_PILE =
		net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK,
			net.minecraft.resources.Identifier.fromNamespaceAndPath(Main.MOD_ID, "supports_a_pile"));

	@Override
	protected boolean canSurvive(BlockState state, LevelReader world, BlockPos pos) {
		BlockState below = world.getBlockState(pos.below());
		return below.is(SUPPORTS_A_PILE)
			|| Block.isFaceFull(below.getBlockSupportShape(world, pos.below()), Direction.UP)
			|| (below.is(this) && below.getValue(LAYERS) == MAX_LAYERS);
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader world, ScheduledTickAccess tickView, BlockPos pos, Direction direction, BlockPos neighborPos, BlockState neighborState, RandomSource random) {
		if (!state.canSurvive(world, pos)) {
			return Blocks.AIR.defaultBlockState();
		}
		if (direction == Direction.DOWN) state = state.setValue(ON_BED, onBed(world, pos));
		return super.updateShape(state, world, tickView, pos, direction, neighborPos, neighborState, random);
	}

	/**
	 * Settle a pile placed by anything - a dropping, a structure, a command - onto a bed under it.
	 * Placement by hand already knows (getStateForPlacement); the rest arrive at the default, which
	 * is off one.
	 */
	@Override
	protected void onPlace(BlockState state, net.minecraft.world.level.Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		super.onPlace(state, level, pos, oldState, movedByPiston);
		if (!(level instanceof ServerLevel)) return;
		boolean bed = onBed(level, pos);
		if (state.getValue(ON_BED) != bed) level.setBlock(pos, state.setValue(ON_BED, bed), Block.UPDATE_CLIENTS);
	}

	public static boolean onBed(LevelReader world, BlockPos pos) {
		return world.getBlockState(pos.below()).is(net.minecraft.tags.BlockTags.BEDS);
	}

	private VoxelShape shapeOf(BlockState state) {
		return shapes[state.getValue(ON_BED) ? 1 : 0][state.getValue(LAYERS)];
	}

	@Override
	protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
		int layers = state.getValue(LAYERS);
		int most = state.getValue(ON_BED) ? BED_LAYERS : MAX_LAYERS;
		if (context.getItemInHand().is(this.asItem()) && layers < most) {
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
		return this.defaultBlockState().setValue(ON_BED, onBed(context.getLevel(), context.getClickedPos()));
	}

	/** Copied from snow, which ticks to melt; a pile that does not rot has nothing to tick for. */
	@Override
	protected boolean isRandomlyTicking(BlockState state) {
		return this.rots && super.isRandomlyTicking(state);
	}

	/**
	 * A heap standing on a block of the same stuff is a midden, and a midden keeps.
	 *
	 * <p>Nothing else in the mod lets poop stand still: it rots where it falls, which is the whole
	 * nutrient cycle and the reason shovelling pays. But a block underneath is not ground that
	 * happens to have muck on it - it is muck somebody packed, in a pit they dug or a corner they
	 * fenced, and the layers riding on top of it are the same heap still being built. Rotting
	 * those was the mod quietly undoing the one part of it that takes work.
	 */
	private boolean kept(ServerLevel world, BlockPos pos) {
		return world.getBlockState(pos.below()).is(PoopPlacement.compostBase(this));
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
		if (!this.rots) return;
		if (kept(world, pos)) return;
		if (random.nextInt(DECAY_CHANCE) != 0) return;

		int layers = state.getValue(LAYERS);
		if (layers > 1) {
			world.setBlockAndUpdate(pos, state.setValue(LAYERS, layers - 1));
		} else {
			world.removeBlock(pos, false);
		}

		// Sometimes, not always. Rotting is what happens to the pile; feeding the ground is a
		// gift it occasionally leaves behind.
		if (random.nextInt(FERTILISE_CHANCE) == 0) {
			PoopPlacement.fertilizeAround(world, pos);
		}
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(LAYERS, FACING, ON_BED);
	}
}
