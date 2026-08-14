package justfatlard.poopsmith;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.equine.Llama;

import java.util.EnumSet;

/**
 * When the llama's daily urge fires, walk to the nearest existing poop layer
 * (the communal spot) and deposit there; found a new spot in place when none
 * exists. Gives up and goes where it stands if the walk takes too long.
 */
public class LlamaPoopGoal extends Goal {
	private static final double WALK_SPEED = 1.1;
	private static final double ARRIVE_DIST_SQ = 2.5 * 2.5;
	private static final int WALK_TIMEOUT_TICKS = 400;

	private final Llama llama;
	private BlockPos target;
	private int ticksWalking;

	public LlamaPoopGoal(Llama llama) {
		this.llama = llama;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE));
	}

	@Override
	public boolean canUse() {
		if (!(llama instanceof PoopUrge urge) || !urge.poopsmith$needsPoop()) return false;
		if (!llama.onGround() || !(llama.level() instanceof ServerLevel world)) return false;
		// No communal spot in range: found one right here (no walk needed)
		target = PoopPlacement.findNearestPoop(world, llama.blockPosition())
			.orElseGet(llama::blockPosition);
		return true;
	}

	@Override
	public boolean canContinueToUse() {
		return llama instanceof PoopUrge urge && urge.poopsmith$needsPoop()
			&& target != null && ticksWalking <= WALK_TIMEOUT_TICKS;
	}

	@Override
	public void start() {
		ticksWalking = 0;
		llama.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, WALK_SPEED);
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		ticksWalking++;
		ServerLevel world = (ServerLevel) llama.level();
		PoopUrge urge = (PoopUrge) llama;

		if (llama.blockPosition().distSqr(target) <= ARRIVE_DIST_SQ) {
			if (PoopPlacement.deposit(world, target).isPresent()) {
				PoopPlacement.playFart(world, llama);
			}
			urge.poopsmith$onPooped();
			target = null;
			return;
		}

		if (ticksWalking >= WALK_TIMEOUT_TICKS) {
			// Couldn't make it: an accident happens where the llama stands
			if (PoopPlacement.animalPoop(world, llama)) {
				urge.poopsmith$onPooped();
			}
			target = null;
			return;
		}

		if (llama.getNavigation().isDone()) {
			llama.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, WALK_SPEED);
		}
	}

	@Override
	public void stop() {
		llama.getNavigation().stop();
		target = null;
	}
}
