package justfatlard.poopsmith.mixin;

import justfatlard.poopsmith.LlamaPoopGoal;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.equine.AbstractChestedHorse;
import net.minecraft.world.entity.animal.equine.Llama;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds the communal-poop-spot walk to llamas. TraderLlama.registerGoals calls
 * super, so trader llamas pick this up too.
 */
@Mixin(Llama.class)
public abstract class LlamaMixin extends AbstractChestedHorse {
	protected LlamaMixin(EntityType<? extends AbstractChestedHorse> entityType, Level world) {
		super(entityType, world);
	}

	@Inject(method = "registerGoals", at = @At("TAIL"))
	private void poopsmith$addPoopGoal(CallbackInfo ci) {
		this.goalSelector.addGoal(3, new LlamaPoopGoal((Llama) (Object) this));
	}
}
