package justfatlard.poopsmith;

import net.minecraft.world.entity.Entity;

/**
 * How much an animal leaves, and how often, from how big it is.
 *
 * <p>A chicken and a camel producing the same pile on the same clock was the thing that read as
 * wrong: the numbers were per-animal only in the sense that every animal had the same ones. Size
 * is the honest input, and it needs no table of entity ids to maintain - a modded moose sorts
 * itself.
 *
 * <p>Measured as width squared by height, which is vanilla's own reckoning of how much room a mob
 * takes up: it is the test farmland already uses to decide who is heavy enough to trample it. For
 * scale, a rabbit is 0.08, a cow 1.1, a horse 3.1 and a camel 6.9.
 */
public final class AnimalSize {
	private AnimalSize() {}

	/** Below this is a bird or a small pet: less often, and never more than a smear. */
	private static final float SMALL = 0.35F;

	/** Above this is a horse or a bear: a little more often than the herd animals. */
	private static final float LARGE = 2.0F;

	/** Above this is a camel and up: often, and two layers when it goes. */
	private static final float HUGE = 4.5F;

	private static float bulk(Entity animal) {
		return animal.getBbWidth() * animal.getBbWidth() * animal.getBbHeight();
	}

	/**
	 * What to multiply the wait by. Bigger animals come round sooner.
	 *
	 * <p>The spread is deliberately narrow at the top. Doubling the wait for something the size
	 * of a chicken is barely noticed in a coop of twenty; halving it for a camel would bury the
	 * ground it stands on.
	 */
	public static float intervalScale(Entity animal) {
		float bulk = bulk(animal);

		if (bulk < SMALL) return 2.0F;
		if (bulk >= HUGE) return 0.75F;
		if (bulk >= LARGE) return 0.85F;
		return 1.0F;
	}

	/** How many layers one visit leaves. */
	public static int layers(Entity animal) {
		return bulk(animal) >= HUGE ? 2 : 1;
	}
}
