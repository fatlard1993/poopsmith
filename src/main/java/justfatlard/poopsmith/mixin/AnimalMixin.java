package justfatlard.poopsmith.mixin;

import justfatlard.poopsmith.PoopPlacement;
import justfatlard.poopsmith.PoopUrge;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.equine.Llama;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every animal poops at least once per Minecraft day. Each entity carries its
 * own randomly-seeded countdown, so poops spread across the day instead of
 * synchronizing at dawn; the reseed interval never exceeds one day.
 *
 * Llamas don't poop here: the timer raises a {@link PoopUrge} that
 * LlamaPoopGoal consumes by walking to the communal spot. If the goal hasn't
 * delivered by the time the backstop window lapses (leashed, ridden, boxed in),
 * the llama goes where it stands.
 */
@Mixin(Animal.class)
public abstract class AnimalMixin implements PoopUrge {
	@Unique private static final int FULL_DAY_TICKS = 24000;
	@Unique private static final int RESEED_BASE_TICKS = 20000;
	@Unique private static final int RESEED_JITTER_TICKS = 4000;
	@Unique private static final int RETRY_TICKS = 200;
	@Unique private static final int LLAMA_GOAL_WINDOW_TICKS = 600;

	@Unique private int poopsmith$poopTimer = Integer.MIN_VALUE;
	@Unique private boolean poopsmith$needsPoop = false;

	@Inject(method = "aiStep", at = @At("TAIL"))
	private void poopsmith$tickPoop(CallbackInfo ci) {
		Animal self = (Animal) (Object) this;
		if (!(self.level() instanceof ServerLevel serverWorld)) return;

		if (poopsmith$poopTimer == Integer.MIN_VALUE) {
			poopsmith$poopTimer = 1 + self.getRandom().nextInt(Math.max(1,
				Math.round(FULL_DAY_TICKS * justfatlard.poopsmith.AnimalSize.intervalScale(self))));
		}
		if (--poopsmith$poopTimer > 0) return;

		if (self instanceof Llama) {
			if (poopsmith$needsPoop) {
				// Backstop: the goal never delivered within its window
				if (self.onGround() && PoopPlacement.animalPoop(serverWorld, self)) {
					poopsmith$onPooped();
				} else {
					poopsmith$poopTimer = RETRY_TICKS;
				}
			} else {
				poopsmith$needsPoop = true;
				poopsmith$poopTimer = LLAMA_GOAL_WINDOW_TICKS;
			}
			return;
		}

		if (self.onGround() && PoopPlacement.animalPoop(serverWorld, self)) {
			poopsmith$poopTimer = poopsmith$reseed(self);
		} else {
			poopsmith$poopTimer = RETRY_TICKS;
		}
	}

	/**
	 * How long until this one goes again, scaled by how big it is.
	 *
	 * <p>The jitter scales with the wait rather than staying fixed, so a herd does not drift into
	 * step: shrinking the gap while leaving the spread alone would make a field of camels beat
	 * out the same rhythm.
	 */
	@Unique
	private int poopsmith$reseed(Animal self) {
		float scale = justfatlard.poopsmith.AnimalSize.intervalScale(self);
		int base = Math.max(1, Math.round(RESEED_BASE_TICKS * scale));
		int jitter = Math.max(1, Math.round(RESEED_JITTER_TICKS * scale));
		return base + self.getRandom().nextInt(jitter);
	}

	@Override
	public boolean poopsmith$needsPoop() {
		return poopsmith$needsPoop;
	}

	@Override
	public void poopsmith$setNeedsPoop(boolean needsPoop) {
		this.poopsmith$needsPoop = needsPoop;
	}

	@Override
	public void poopsmith$onPooped() {
		Animal self = (Animal) (Object) this;
		poopsmith$needsPoop = false;
		poopsmith$poopTimer = poopsmith$reseed(self);
	}
}
