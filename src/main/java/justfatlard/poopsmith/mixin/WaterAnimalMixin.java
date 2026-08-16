package justfatlard.poopsmith.mixin;

import justfatlard.poopsmith.PoopPlacement;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.fish.WaterAnimal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fish poop too. WaterAnimal (the fish branch) extends PathfinderMob, not
 * Animal, so AnimalMixin never touches it; same randomly-seeded daily timer,
 * always the dispersing water cloud. Squid and dolphins live on the separate
 * AgeableWaterCreature branch (see AgeableWaterCreatureMixin); turtles and
 * axolotls are Animals and already covered, so nothing poops twice.
 */
@Mixin(WaterAnimal.class)
public abstract class WaterAnimalMixin {
	@Unique private static final int FULL_DAY_TICKS = 24000;
	@Unique private static final int RESEED_BASE_TICKS = 20000;
	@Unique private static final int RESEED_JITTER_TICKS = 4000;

	@Unique private int poopsmith$poopTimer = Integer.MIN_VALUE;

	@Inject(method = "baseTick", at = @At("TAIL"))
	private void poopsmith$tickPoop(CallbackInfo ci) {
		WaterAnimal self = (WaterAnimal) (Object) this;
		if (!(self.level() instanceof ServerLevel serverWorld)) return;

		if (poopsmith$poopTimer == Integer.MIN_VALUE) {
			poopsmith$poopTimer = 1 + self.getRandom().nextInt(FULL_DAY_TICKS);
		}
		if (--poopsmith$poopTimer > 0) return;

		PoopPlacement.waterPoop(serverWorld, self);
		poopsmith$poopTimer = RESEED_BASE_TICKS + self.getRandom().nextInt(RESEED_JITTER_TICKS);
	}
}
