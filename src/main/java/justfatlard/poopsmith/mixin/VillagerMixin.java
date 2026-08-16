package justfatlard.poopsmith.mixin;

import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.poopsmith.Main;
import justfatlard.poopsmith.PoopPlacement;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Villagers are Brain-driven, not goal-selector mobs, so AnimalMixin's daily
 * timer and LlamaPoopGoal's walk both miss them; this fuses the two. The
 * timer defers through night and sleep. On firing, the villager prefers the
 * nearest latrine pit (a poop block column), then the street communal pile,
 * then its own feet.
 *
 * The walk is driven by re-asserting WALK_TARGET at TAIL of
 * customServerAiStep: the brain ticks at the HEAD of that method (verified
 * against the game jar), so writing the memory after it each tick outbids
 * whatever a schedule behavior wrote during that tick, without touching the
 * behavior packages themselves. MoveToTargetSink then paths to our target on
 * the next brain tick.
 *
 * Also home of the poopsmith nitwit: a rare, persisted, cosmetic-only flag
 * rolled once per nitwit. No trades, no behavior change; the flag exists so
 * a Pandorical overlay layer can dress the villager when that rendering
 * capability lands (see poopsmith$isPoopsmith below).
 */
@Mixin(Villager.class)
public abstract class VillagerMixin {
	@Unique private static final int FULL_DAY_TICKS = 24000;
	@Unique private static final int RESEED_BASE_TICKS = 20000;
	@Unique private static final int RESEED_JITTER_TICKS = 4000;
	@Unique private static final int RETRY_TICKS = 200;
	@Unique private static final int TRIP_TIMEOUT_TICKS = 500;
	// Must cover standing at a latrine pit's rim above a 2-deep shaft: the
	// deposit target sits 2-3 below the villager's feet at the edge
	// (distSqr 5-6), and the target only rises as the pit fills
	@Unique private static final double ARRIVE_DIST_SQ = 8.0;
	@Unique private static final float WALK_SPEED = 0.55F;
	@Unique private static final int CLOSE_ENOUGH_DIST = 1;
	// Outlives the once-per-tick reassert, short enough to fade fast if the
	// trip ends abnormally (death, chunk unload)
	@Unique private static final long MEMORY_TTL_TICKS = 60L;
	@Unique private static final float POOPSMITH_CHANCE = 0.08F;

	@Unique private int poopsmith$poopTimer = Integer.MIN_VALUE;
	@Unique private BlockPos poopsmith$tripTarget;
	@Unique private int poopsmith$tripTicks;

	// Rolled at most once per villager, nitwits only, then persisted: once a
	// poopsmith, always a poopsmith. Purely cosmetic: Pandorical clients see
	// the gloves overlay, vanilla clients see a plain nitwit.
	@Unique private boolean poopsmith$rolled = false;
	@Unique private boolean poopsmith$isPoopsmith = false;

	@Unique private static final Identifier POOPSMITH_GLOVES =
		Identifier.fromNamespaceAndPath("poopsmith", "textures/entity/poopsmith_gloves.png");

	// Overlay state in Pandorical is not persisted, so re-push once per
	// entity instance: covers both the first roll and every chunk reload
	@Unique private boolean poopsmith$overlayPushed = false;

	@Inject(method = "customServerAiStep", at = @At("TAIL"))
	private void poopsmith$tickPoop(ServerLevel world, CallbackInfo ci) {
		Villager self = (Villager) (Object) this;

		poopsmith$rollPoopsmith(self);

		if (poopsmith$isPoopsmith && !poopsmith$overlayPushed) {
			poopsmith$overlayPushed = true;
			PandoricalApi.entityOverlays().set(self, POOPSMITH_GLOVES);
		}

		if (poopsmith$tripTarget != null) {
			poopsmith$tickTrip(world, self);
			return;
		}

		if (poopsmith$poopTimer == Integer.MIN_VALUE) {
			poopsmith$poopTimer = 1 + self.getRandom().nextInt(FULL_DAY_TICKS);
		}
		if (--poopsmith$poopTimer > 0) return;

		// Villagers keep it decent: never at night, never mid-sleep
		if (!world.isBrightOutside() || self.isSleeping()) {
			poopsmith$poopTimer = RETRY_TICKS;
			return;
		}

		BlockPos target = PoopPlacement.findLatrineTarget(world, self.blockPosition())
			.or(() -> PoopPlacement.findNearestPoop(world, self.blockPosition()))
			.orElse(null);
		if (target == null) {
			poopsmith$poopInPlace(world, self);
			return;
		}
		poopsmith$tripTarget = target;
		poopsmith$tripTicks = 0;
	}

	@Unique
	private void poopsmith$tickTrip(ServerLevel world, Villager self) {
		poopsmith$tripTicks++;
		BlockPos target = poopsmith$tripTarget;

		if (self.blockPosition().distSqr(target) <= ARRIVE_DIST_SQ) {
			poopsmith$endTrip(self);
			if (self.isInWater()) {
				// Wading villager at the pit's edge: disperse rather than
				// plant a layer underwater
				PoopPlacement.waterPoop(world, self);
				poopsmith$reseed(self);
			} else if (PoopPlacement.deposit(world, target).isPresent()) {
				PoopPlacement.playFart(world, self);
				poopsmith$reseed(self);
			} else {
				poopsmith$poopInPlace(world, self);
			}
			return;
		}

		// A player may break or block the pit mid-trip; deposit() is the
		// final validator, this just aborts trips that can no longer land
		BlockState targetState = world.getBlockState(target);
		boolean stillOpen = targetState.is(Main.POOP_LAYER_BLOCK)
			|| targetState.isAir() || targetState.canBeReplaced();
		if (!stillOpen || poopsmith$tripTicks >= TRIP_TIMEOUT_TICKS) {
			poopsmith$endTrip(self);
			poopsmith$poopInPlace(world, self);
			return;
		}

		// Fleeing outranks the urge; the trip clock keeps running so a long
		// panic still resolves via the timeout
		if (self.getBrain().isActive(Activity.PANIC)) return;

		self.getBrain().setMemoryWithExpiry(MemoryModuleType.WALK_TARGET,
			new WalkTarget(target, WALK_SPEED, CLOSE_ENOUGH_DIST), MEMORY_TTL_TICKS);
		self.getBrain().setMemoryWithExpiry(MemoryModuleType.LOOK_TARGET,
			new BlockPosTracker(target), MEMORY_TTL_TICKS);
	}

	@Unique
	private void poopsmith$endTrip(Villager self) {
		poopsmith$tripTarget = null;
		self.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
	}

	@Unique
	private void poopsmith$poopInPlace(ServerLevel world, Villager self) {
		if (self.onGround() && PoopPlacement.animalPoop(world, self)) {
			poopsmith$reseed(self);
		} else {
			poopsmith$poopTimer = RETRY_TICKS;
		}
	}

	@Unique
	private void poopsmith$reseed(Villager self) {
		poopsmith$poopTimer = RESEED_BASE_TICKS + self.getRandom().nextInt(RESEED_JITTER_TICKS);
	}

	@Unique
	private void poopsmith$rollPoopsmith(Villager self) {
		if (poopsmith$rolled) return;
		if (!self.getVillagerData().profession().is(VillagerProfession.NITWIT)) return;
		poopsmith$rolled = true;
		poopsmith$isPoopsmith = self.getRandom().nextFloat() < POOPSMITH_CHANCE;
	}

	@Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
	private void poopsmith$save(ValueOutput output, CallbackInfo ci) {
		if (poopsmith$rolled) {
			output.putBoolean("PoopsmithRolled", true);
			output.putBoolean("Poopsmith", poopsmith$isPoopsmith);
		}
	}

	@Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
	private void poopsmith$load(ValueInput input, CallbackInfo ci) {
		poopsmith$rolled = input.getBooleanOr("PoopsmithRolled", false);
		poopsmith$isPoopsmith = input.getBooleanOr("Poopsmith", false);
	}
}
