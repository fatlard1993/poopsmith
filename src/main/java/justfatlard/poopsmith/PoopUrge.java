package justfatlard.poopsmith;

/**
 * Duck interface implemented by the Animal mixin: lets the llama poop goal read
 * the mixin-owned poop urge and report completion back to the timer it owns.
 */
public interface PoopUrge {
	boolean poopsmith$needsPoop();

	void poopsmith$setNeedsPoop(boolean needsPoop);

	/** Poop delivered (by the goal or a fallback): clear the urge, reseed the daily timer. */
	void poopsmith$onPooped();
}
