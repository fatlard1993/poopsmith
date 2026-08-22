package justfatlard.poopsmith;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What left a pile, for as long as that is worth knowing.
 *
 * <p>Deliberately not saved to disk. The whole point of the record is that it
 * goes cold, and a restart is only the world going cold sooner; nothing here
 * changes what a pile does, only what block-tip can say about it.
 *
 * <p>The rules are narrow on purpose, and one of them falls out of the
 * placement mechanics rather than being invented: a heap on open ground stops
 * at {@link PoopPlacement#OPEN_STACK_LAYERS} layers and the next deposit slides
 * to a neighbouring tile, so a taller stack means something contained it. The
 * piles that talk are therefore exactly the wild ones out in the open; a
 * latrine stack is not cold sign, it is not sign at all, and says nothing.
 */
public final class PoopOwners {
	private PoopOwners() {}

	/**
	 * How a trail cools: a hundred seconds a band, and past the last one there
	 * is nothing left to say. A quarter of a day all told, which is long enough
	 * to follow a herd through a wood and short enough to lose it.
	 */
	private static final long BAND_TICKS = 2000L;
	private static final String[] BANDS = {"fresh", "warm", "soft"};
	private static final long REMEMBERED_TICKS = BAND_TICKS * BANDS.length;

	/** Bands that still know which way it went. Cold sign keeps its maker, not its heading. */
	private static final int HEADING_BANDS = 2;

	/** Said of a pile whose trail has gone cold, or that more than one animal used. */
	private static final String OLD = "Old";

	// The map only grows when something poops, so that is also when it is
	// cleared: no tick hook for a table that sits still on a quiet server
	private static final int SWEEP_EVERY = 64;

	private record Spot(ResourceKey<Level> level, BlockPos pos) {}

	/**
	 * A null type is a pile more than one kind of animal used: nobody to blame.
	 * The heading is which way it was facing as it went, which is a hint and
	 * not a promise: an animal is free to turn around afterwards.
	 */
	private record Owner(EntityType<?> type, Direction heading, long stamp) {}

	private static final Map<Spot, Owner> owners = new ConcurrentHashMap<>();
	private static int sinceSweep;

	/** Called from every path that writes a poop layer, with whoever produced it. */
	public static void record(ServerLevel world, BlockPos pos, Entity source) {
		if (source == null) return;
		long now = world.getGameTime();

		Direction heading = source.getDirection();
		owners.compute(new Spot(world.dimension(), pos.immutable()), (spot, existing) -> {
			if (existing == null || cold(existing, now)) return new Owner(source.getType(), heading, now);
			// Somebody else's pile: from here nobody can be blamed for it
			EntityType<?> type = existing.type() == source.getType() ? existing.type() : null;
			return new Owner(type, heading, now);
		});

		if (++sinceSweep < SWEEP_EVERY) return;
		sinceSweep = 0;
		owners.values().removeIf(owner -> cold(owner, now));
	}

	/**
	 * The block-tip line for a poop layer: what left it and how the trail is
	 * cooling, plus a heading while that is still worth anything, or
	 * {@link #OLD} once it is spent or shared. Never an individual, so this
	 * says Cow and never which cow.
	 *
	 * <p>Null for a stack taller than open ground allows, which is a heap
	 * somebody built rather than a trail somebody left: it has no age to
	 * report, and saying Old about it would be answering a question nobody
	 * asked.
	 *
	 * <p>Resolved to words here rather than handed over as a translation key,
	 * because the card carries one key and this line is three facts. The cost
	 * is that it reads in the server's language.
	 */
	public static String describe(ServerLevel world, BlockPos pos, BlockState state) {
		if (state.getValue(PoopLayerBlock.LAYERS) > PoopPlacement.OPEN_STACK_LAYERS) return null;

		Owner owner = owners.get(new Spot(world.dimension(), pos));
		if (owner == null || owner.type() == null) return OLD;

		int band = (int) (age(owner, world.getGameTime()) / BAND_TICKS);
		if (band >= BANDS.length) return OLD;

		String line = owner.type().getDescription().getString() + ", " + BANDS[band];
		return band < HEADING_BANDS ? line + ", " + owner.heading().getName() : line;
	}

	private static long age(Owner owner, long now) {
		return Math.max(0L, now - owner.stamp());
	}

	private static boolean cold(Owner owner, long now) {
		return age(owner, now) >= REMEMBERED_TICKS;
	}
}
