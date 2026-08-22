package justfatlard.poopsmith;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.TrailParticleOption;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Flies over a loose pile of poop: idle ambience, one swarm per player.
 *
 * <p>The firefly bush spawns its particles from animateTick, which is client
 * code we do not have: no poopsmith jar ever reaches a client. So this does the
 * same job from the other side, sampling random positions around each player
 * the way a client samples for animateTick and sending the particles to that
 * one player.
 *
 * <p>The particle is trail: one opaque pixel (generic_0), a colour we choose,
 * and a destination it glides to and dies at. Dust was the obvious reach and it
 * was wrong, because its sprite list runs generic_7 down to generic_0, a fat
 * blob shrinking to a dot as it ages, which is a puff of smoke by construction.
 * Firefly itself has the right body and the right wandering, but it is a
 * SimpleParticleType drawn full-bright: no colour to set, and a glowing speck
 * over a dung heap is a firefly, not a fly.
 *
 * <p>One particle is one straight line, so the zig-zag is built rather than
 * asked for: a fly is tracked between legs and handed a fresh heading from
 * wherever the last one dropped it. That tracking is also what bounds the
 * traffic, since a counted swarm can be capped and a stream of bursts cannot.
 */
public final class PoopFlies {
	private PoopFlies() {}

	// A fly is a single dark pixel, invisible from across a room, so the
	// sampled cube stays small: that is what keeps the hit rate per pile high
	// enough to refill a swarm as it ages out
	private static final int RADIUS = 5;
	private static final int SPAN = RADIUS * 2 + 1;
	private static final int SAMPLES_PER_PLAYER = 192;

	// Three over a lone turd, and the per-player ceiling is what a latrine
	// pit, a llama's communal spot, or a wall built out of poop runs into
	private static final int FLIES_PER_PILE = 3;
	private static final int MAX_FLIES_PER_PLAYER = 12;
	// Walking up to a poop field should draw a gathering swarm, not a wall
	private static final int RECRUITS_PER_TICK = 2;

	// A leg is one packet, so this is the traffic dial: at seven ticks a full
	// twelve-fly swarm costs about two packets a tick. It is also the speed
	// dial, since the box below decides how far a leg goes and this decides
	// how long it has to get there
	private static final int LEG_TICKS = 7;
	private static final int MIN_LEGS = 4;
	private static final int MAX_LEGS = 9;

	// Near-black rather than black: a body, not a hole in the world
	private static final int FLY_COLOR = 0x121212;

	// The box a fly works: the pile's own footprint, from just off the surface
	// to two thirds of a block up. Both ends of every leg land inside it, so
	// the swarm holds station while each fly in it is always going somewhere.
	// The box is also the speed, since a leg takes LEG_TICKS however long it is
	private static final double HOVER_LOW = 0.05D;
	private static final double HOVER_HIGH = 0.65D;

	private static final double NO_POOP = -1.0D;

	private static final Map<UUID, List<Fly>> swarms = new ConcurrentHashMap<>();
	private static int tick;

	/** One fly mid-flight: what it works, where the last leg left it, what it has left. */
	private static final class Fly {
		private final BlockPos over;
		private Vec3 at;
		private int legsLeft;
		private int nextLeg;

		private Fly(BlockPos over, Vec3 at, int legs) {
			this.over = over;
			this.at = at;
			this.legsLeft = legs;
		}
	}

	/** Flies keep daylight hours: the mirror of the firefly bush's night gate. */
	public static void onServerTick(MinecraftServer server) {
		tick++;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!(player.level() instanceof ServerLevel world) || !world.isBrightOutside()) {
				// Nightfall, or the Nether, where the sun never rises: the
				// swarm goes with the daylight rather than waiting on it
				swarms.remove(player.getUUID());
				continue;
			}
			List<Fly> swarm = swarms.computeIfAbsent(player.getUUID(), key -> new ArrayList<>());
			advance(world, player, swarm);
			recruit(world, player, swarm);
		}
	}

	public static void forget(UUID uuid) {
		swarms.remove(uuid);
	}

	private static void advance(ServerLevel world, ServerPlayer player, List<Fly> swarm) {
		Iterator<Fly> flies = swarm.iterator();
		while (flies.hasNext()) {
			Fly fly = flies.next();
			if (tick < fly.nextLeg) continue;
			if (fly.legsLeft <= 0 || !flyLeg(world, player, fly)) flies.remove();
		}
	}

	private static void recruit(ServerLevel world, ServerPlayer player, List<Fly> swarm) {
		RandomSource random = world.getRandom();
		BlockPos origin = player.blockPosition();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int recruited = 0;

		for (int i = 0; i < SAMPLES_PER_PLAYER; i++) {
			if (recruited >= RECRUITS_PER_TICK || swarm.size() >= MAX_FLIES_PER_PLAYER) return;
			pos.set(
				origin.getX() - RADIUS + random.nextInt(SPAN),
				origin.getY() - RADIUS + random.nextInt(SPAN),
				origin.getZ() - RADIUS + random.nextInt(SPAN));

			double top = surfaceOf(world, pos);
			if (top == NO_POOP || working(swarm, pos) >= FLIES_PER_PILE) continue;

			Fly fly = new Fly(pos.immutable(), somewhereOver(pos, top, random),
				MIN_LEGS + random.nextInt(MAX_LEGS - MIN_LEGS + 1));
			if (!flyLeg(world, player, fly)) continue;
			swarm.add(fly);
			recruited++;
		}
	}

	/**
	 * Sends one straight leg and leaves the fly at its far end, or reports the
	 * pile gone: shovelled, decayed, or built over while the fly was in the air.
	 */
	private static boolean flyLeg(ServerLevel world, ServerPlayer player, Fly fly) {
		double top = surfaceOf(world, fly.over);
		if (top == NO_POOP) return false;

		Vec3 to = somewhereOver(fly.over, top, world.getRandom());
		world.sendParticles(player, new TrailParticleOption(to, FLY_COLOR, LEG_TICKS),
			false, false, fly.at.x, fly.at.y, fly.at.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);

		fly.at = to;
		fly.legsLeft--;
		// A tick early: a hair of overlap where one leg hands over to the next
		// is invisible, and the gap it avoids is a blink
		fly.nextLeg = tick + LEG_TICKS - 1;
		return true;
	}

	/** Top face of a loose pile, or {@link #NO_POOP} if a fly has no business there. */
	private static double surfaceOf(ServerLevel world, BlockPos pos) {
		BlockState state = world.getBlockState(pos);
		if (!state.is(Main.POOP_LAYER_BLOCK)) return NO_POOP;
		// Loose heaps only, the line block-tip already draws: past the height
		// a pile reaches on open ground something contained it, and a latrine
		// column or a wall built out of poop is not what flies gather on
		if (state.getValue(PoopLayerBlock.LAYERS) > PoopPlacement.OPEN_STACK_LAYERS) return NO_POOP;
		// Buried poop does not buzz: a lidded pit keeps your business your
		// own, and the flies never render inside the floor above
		return world.getBlockState(pos.above()).isAir() ? PoopLayerBlock.topOf(state) : NO_POOP;
	}

	private static int working(List<Fly> swarm, BlockPos pos) {
		int count = 0;
		for (Fly fly : swarm) {
			if (fly.over.equals(pos)) count++;
		}
		return count;
	}

	private static Vec3 somewhereOver(BlockPos pos, double top, RandomSource random) {
		return new Vec3(
			pos.getX() + random.nextDouble(),
			pos.getY() + top + HOVER_LOW + random.nextDouble() * (HOVER_HIGH - HOVER_LOW),
			pos.getZ() + random.nextDouble());
	}
}
