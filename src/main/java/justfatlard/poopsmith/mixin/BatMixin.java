package justfatlard.poopsmith.mixin;

import justfatlard.poopsmith.PoopPlacement;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ambient.Bat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Bats are ambient creatures, not Animals, so AnimalMixin never touches
 * them; this gives each bat the same randomly-seeded daily timer. Instead
 * of pooping at its feet, a bat's guano falls from the roost to the ground
 * below (PoopPlacement.batPoop). customServerAiStep is the inject point:
 * server-only, runs every AI tick, and hands over the ServerLevel.
 */
@Mixin(Bat.class)
public abstract class BatMixin {
	@Unique private static final int FULL_DAY_TICKS = 24000;
	@Unique private static final int RESEED_BASE_TICKS = 20000;
	@Unique private static final int RESEED_JITTER_TICKS = 4000;
	@Unique private static final int RETRY_TICKS = 200;

	@Unique private int poopsmith$poopTimer = Integer.MIN_VALUE;

	@Inject(method = "customServerAiStep", at = @At("TAIL"))
	private void poopsmith$tickPoop(ServerLevel world, CallbackInfo ci) {
		Bat self = (Bat) (Object) this;

		if (poopsmith$poopTimer == Integer.MIN_VALUE) {
			poopsmith$poopTimer = 1 + self.getRandom().nextInt(FULL_DAY_TICKS);
		}
		if (--poopsmith$poopTimer > 0) return;

		if (PoopPlacement.batPoop(world, self)) {
			poopsmith$poopTimer = RESEED_BASE_TICKS + self.getRandom().nextInt(RESEED_JITTER_TICKS);
		} else {
			poopsmith$poopTimer = RETRY_TICKS;
		}
	}
}
