package justfatlard.poopsmith.mixin;

import justfatlard.poopsmith.player.PlayerPoopManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Eat-completion hook: FoodProperties.onConsume is the ConsumableListener
 * vanilla invokes when a food item finishes being eaten, with the stack still
 * intact: exactly the information the poop bar needs.
 */
@Mixin(FoodProperties.class)
public class FoodPropertiesMixin {
	@Inject(method = "onConsume", at = @At("TAIL"))
	private void poopsmith$afterEat(Level world, LivingEntity user, ItemStack stack, Consumable consumable, CallbackInfo ci) {
		if (!world.isClientSide() && user instanceof ServerPlayer player) {
			PlayerPoopManager.onAte(player, stack, (FoodProperties) (Object) this);
		}
	}
}
