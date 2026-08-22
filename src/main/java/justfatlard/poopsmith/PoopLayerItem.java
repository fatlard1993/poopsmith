package justfatlard.poopsmith;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

/**
 * Placing it by hand obeys the same rules as leaving it behind.
 *
 * <p>A plain {@link BlockItem} stacks layers wherever it is pointed, the way snow does, which put
 * hand-placed piles under a different physics from every other pile in the world: a llama's heap
 * spreads across the yard before it climbs, and a player's went straight up in a tower on one tile.
 * Two rules for one substance, and the player's was the one that looked wrong.
 *
 * <p>So this hands the click to {@link PoopPlacement#deposit}, the same entry the animals use. It
 * spreads outward through the nearby tiles first and only stacks where the heap is held in by walls
 * or standing on packed ground.
 */
public class PoopLayerItem extends BlockItem {
	private final PoopLayerBlock layerBlock;

	public PoopLayerItem(PoopLayerBlock block, Properties properties) {
		super(block, properties);
		this.layerBlock = block;
	}

	@Override
	public InteractionResult place(BlockPlaceContext context) {
		// The client only predicts; where it lands is the server's answer, and the spread rule can
		// put it a couple of tiles from the cursor.
		if (!(context.getLevel() instanceof ServerLevel level)) return InteractionResult.SUCCESS;

		Optional<BlockPos> placed = PoopPlacement.deposit(level, context.getClickedPos(), layerBlock);
		if (placed.isEmpty()) return InteractionResult.FAIL;

		BlockPos pos = placed.get();
		BlockState state = level.getBlockState(pos);
		Player player = context.getPlayer();

		SoundType sound = state.getSoundType();
		level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS,
			(sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
		level.gameEvent(GameEvent.BLOCK_PLACE, pos, GameEvent.Context.of(player, state));

		context.getItemInHand().consume(1, player);
		return InteractionResult.SUCCESS;
	}
}
