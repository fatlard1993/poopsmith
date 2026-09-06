package justfatlard.poopsmith;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

/**
 * The sound of a poop going over a ledge: a falling whistle for as long as the fall, then the
 * splat, arriving when the fall would have.
 *
 * <p>The layer itself is placed instantly by the deposit scan - there is no falling entity - so
 * this plays the fall the deposit implies. The whistle is a descending flute glissando (the one
 * cartoon sound vanilla's kit can actually make) whose notes slide down the real column the poop
 * fell through, so bystanders below hear it coming. The splat lands at the bottom at a volume the
 * impact earned, and the pooper standing at the top gets a separate faint echo of it, fainter the
 * longer the fall, because a hundred blocks down IS barely audible and that distance is the joke.
 *
 * <p>The timing is honest free fall, {@code t = sqrt(2h/g)} at Minecraft's ~32 blocks/s², so a
 * two-block hop is a blink and a cliff is two and a half seconds of dawning anticipation.
 *
 * <p>Landing is also where the {@code poopsmith:poop_dropped} criterion fires, so the "Off a
 * Cliff" toast and the distant splat arrive together.
 */
public final class DropTheater {
	private DropTheater() {}

	/** Below this the fall is a step, not an event: no whistle, no ceremony. */
	private static final int MIN_DROP = 2;

	/** One flute note per this many ticks; the glissando's grain. */
	private static final int NOTE_INTERVAL_TICKS = 2;

	private static final float WHISTLE_VOLUME = 0.6F;
	private static final float WHISTLE_PITCH_TOP = 1.9F;
	private static final float WHISTLE_PITCH_BOTTOM = 0.5F;

	/** The splat's volume grows with the fall; the echo the pooper hears shrinks with it. */
	private static final float SPLAT_BASE_VOLUME = 0.7F;
	private static final float SPLAT_VOLUME_PER_BLOCK = 0.006F;
	private static final float SPLAT_MAX_VOLUME = 1.3F;
	private static final float ECHO_BASE_VOLUME = 0.9F;
	private static final float ECHO_FADE_PER_BLOCK = 0.007F;
	private static final float ECHO_MIN_VOLUME = 0.1F;
	/** Within this, ordinary distance attenuation already carries the real splat to the pooper. */
	private static final int ECHO_ONLY_PAST_BLOCKS = 12;

	private static final class Fall {
		final ServerLevel world;
		final double x, z, topY;
		final int drop;
		final BlockPos landed;
		final PoopPlacement.Medium medium;
		final UUID pooper;
		final int totalTicks;
		int age;

		Fall(ServerLevel world, BlockPos from, PoopPlacement.Landing landing, UUID pooper) {
			this.world = world;
			this.x = from.getX() + 0.5;
			this.z = from.getZ() + 0.5;
			this.topY = from.getY();
			this.drop = from.getY() - landing.pos().getY();
			this.landed = landing.pos();
			this.medium = landing.medium();
			this.pooper = pooper;
			// Free fall: t = sqrt(2h/g), g ~32 blocks/s^2, in ticks. 5*sqrt(h).
			this.totalTicks = Math.max(NOTE_INTERVAL_TICKS, Math.round(5F * Mth.sqrt(this.drop)));
		}

		/** One tick of theater; true when the splat has landed and this fall is done. */
		boolean tick() {
			this.age++;
			if (this.age < this.totalTicks) {
				if (this.age % NOTE_INTERVAL_TICKS == 0) whistle();
				return false;
			}
			splat();
			return true;
		}

		private void whistle() {
			float progress = this.age / (float) this.totalTicks;
			// The pitch slides linearly but the position falls quadratically, like the thing
			// it is following
			float pitch = WHISTLE_PITCH_TOP - (WHISTLE_PITCH_TOP - WHISTLE_PITCH_BOTTOM) * progress;
			double y = this.topY - this.drop * progress * progress;
			this.world.playSound(null, this.x, y, this.z,
				SoundEvents.NOTE_BLOCK_FLUTE.value(), SoundSource.NEUTRAL, WHISTLE_VOLUME, pitch);
		}

		private void splat() {
			float volume = Math.min(SPLAT_MAX_VOLUME,
				SPLAT_BASE_VOLUME + this.drop * SPLAT_VOLUME_PER_BLOCK);
			// The arrival sounds like what it arrived in: splat on ground, sploosh into
			// water (with the dispersal cloud, since no layer was placed), sizzle into lava
			var impact = switch (this.medium) {
				case GROUND -> SoundEvents.SLIME_BLOCK_BREAK;
				case WATER -> SoundEvents.GENERIC_SPLASH;
				case LAVA -> SoundEvents.LAVA_EXTINGUISH;
			};
			if (this.medium == PoopPlacement.Medium.WATER) {
				PoopPlacement.waterPoopAt(this.world,
					this.landed.getX() + 0.5, this.landed.getY() + 0.5, this.landed.getZ() + 0.5);
			}
			this.world.playSound(null,
				this.landed.getX() + 0.5, this.landed.getY(), this.landed.getZ() + 0.5,
				impact, SoundSource.NEUTRAL, volume, 0.8F);

			ServerPlayer player = this.world.getServer().getPlayerList().getPlayer(this.pooper);
			if (player == null) return;

			// The echo: what a splat that far down sounds like from up here. Skipped for short
			// hops, where the real one is already in earshot and a double splat reads as a bug.
			if (this.drop > ECHO_ONLY_PAST_BLOCKS) {
				float echo = Math.max(ECHO_MIN_VOLUME,
					ECHO_BASE_VOLUME - this.drop * ECHO_FADE_PER_BLOCK);
				this.world.playSound(null, player.getX(), player.getY(), player.getZ(),
					impact, SoundSource.NEUTRAL, Math.min(echo, 0.6F), 1.1F);
			}

			Main.POOP_DROPPED.trigger(player, this.drop);
		}
	}

	private static final List<Fall> FALLS = new ArrayList<>();

	/**
	 * Start the fall's soundtrack, if the fall was far enough to have one. False means the drop
	 * was a non-event and the caller still owes whatever an immediate arrival does (a shoreline
	 * poop's dispersal, say); true means the theater owns the arrival now.
	 */
	public static boolean start(ServerLevel world, ServerPlayer pooper, BlockPos from,
			PoopPlacement.Landing landing) {
		if (from.getY() - landing.pos().getY() < MIN_DROP) return false;
		FALLS.add(new Fall(world, from, landing, pooper.getUUID()));
		return true;
	}

	public static void tick(MinecraftServer server) {
		if (FALLS.isEmpty()) return;
		FALLS.removeIf(Fall::tick);
	}
}
