package justfatlard.poopsmith;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Throwable like a snowball; see {@link PoopEntity} for impact behavior. */
public class PoopItem extends Item {
	public PoopItem(Properties settings) {
		super(settings);
	}

	@Override
	public InteractionResult use(Level world, Player user, InteractionHand hand) {
		ItemStack itemStack = user.getItemInHand(hand);

		world.playSound(null, user.getX(), user.getY(), user.getZ(),
			SoundEvents.SNOWBALL_THROW, SoundSource.NEUTRAL,
			0.5F, 0.4F / (world.getRandom().nextFloat() * 0.4F + 0.8F));

		if (!world.isClientSide()) {
			PoopEntity poop = new PoopEntity(world, user, itemStack);
			poop.shootFromRotation(user, user.getXRot(), user.getYRot(), 0.0F, 1.5F, 1.0F);
			world.addFreshEntity(poop);
		}

		user.awardStat(Stats.ITEM_USED.get(this));
		itemStack.consume(1, user);
		return InteractionResult.SUCCESS;
	}
}
