package justfatlard.poopsmith;

import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Thrown poop with snowball physics. Hitting a player inflicts a brief
 * sick/dizzy combo (nausea + slowness); landing on a block fertilises where it
 * lands, one layer's worth, the same as a layer decaying there.
 */
public class PoopEntity extends ThrowableItemProjectile {
	// Nausea's warp ramps in over 150 ticks and fades from 60 ticks before
	// expiry (verified blend registration, see PlayerPoopManager's nausea
	// constants): 120 ticks peaks around 40% warp, a solid dizzy insult that
	// never reaches the full-screen smear
	private static final int NAUSEA_TICKS = 120;
	private static final int SLOWNESS_TICKS = 100;

	public PoopEntity(EntityType<? extends ThrowableItemProjectile> entityType, Level world) {
		super(entityType, world);
	}

	public PoopEntity(Level world, LivingEntity owner, ItemStack stack) {
		super(Main.POOP_ENTITY_TYPE, owner, world, stack);
	}

	@Override
	protected Item getDefaultItem() {
		return Main.POOP_ITEM;
	}

	@Override
	protected void onHitEntity(EntityHitResult entityHitResult) {
		super.onHitEntity(entityHitResult);
		if (!(this.level() instanceof ServerLevel)) return;
		if (entityHitResult.getEntity() instanceof Player player) {
			player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, NAUSEA_TICKS, 0));
			player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SLOWNESS_TICKS, 0));
		}
	}

	@Override
	protected void onHit(HitResult hitResult) {
		super.onHit(hitResult);
		if (!(this.level() instanceof ServerLevel serverWorld)) return;

		// Landing on something fertilises it, the same as a layer decaying there.
		// A throw is one layer's worth of muck arriving all at once, so it carries
		// one layer's worth of fertility; the alternative was a splat that did
		// nothing, which made throwing it purely a prank.
		//
		// The face is offset into so the target matches what fertilizeAround
		// expects: the position the muck now occupies, whose block below is the
		// ground it landed on.
		if (hitResult.getType() == HitResult.Type.BLOCK && hitResult instanceof BlockHitResult blockHit) {
			PoopPlacement.fertilizeAround(serverWorld, blockHit.getBlockPos().relative(blockHit.getDirection()));
		}

		for (int i = 0; i < 8; i++) {
			serverWorld.sendParticles(
				new ItemParticleOption(ParticleTypes.ITEM, this.getItem().getItem()),
				this.getX(), this.getY(), this.getZ(), 1,
				((double) this.random.nextFloat() - 0.5) * 0.08,
				((double) this.random.nextFloat() - 0.5) * 0.08,
				((double) this.random.nextFloat() - 0.5) * 0.08,
				0.0);
		}
		serverWorld.playSound(null, this.getX(), this.getY(), this.getZ(),
			SoundEvents.SLIME_BLOCK_BREAK, SoundSource.NEUTRAL, 0.8F, 0.9F);
		this.discard();
	}
}
