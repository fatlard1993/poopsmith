package justfatlard.poopsmith.advancement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.advancements.triggers.SimpleCriterionTrigger;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

/**
 * Fires when a player's poop lands somewhere below where it left them, carrying how far it fell.
 *
 * <p>Nothing vanilla can see measures this: the layer that appears at the bottom is the same
 * layer a polite ground-level poop places, and the drop existed only in the deposit scan that is
 * now over. Reported at the splat, not the squat, so the advancement toast and the landing arrive
 * as one event.
 *
 * <p>Gated on {@code min_drop} so the data side asks for "this far or farther" and the hundred
 * can be retuned, or a lesser ledge advancement added, without touching code.
 */
public class PoopDropCriterion extends SimpleCriterionTrigger<PoopDropCriterion.Conditions> {

	@Override
	public Codec<Conditions> codec() {
		return Conditions.CODEC;
	}

	public void trigger(ServerPlayer player, int drop) {
		this.trigger(player, conditions -> conditions.matches(drop));
	}

	public record Conditions(Optional<Holder<LootItemCondition>> player, Optional<Integer> minDrop)
			implements SimpleCriterionTrigger.SimpleInstance {
		public static final Codec<Conditions> CODEC = RecordCodecBuilder.create(
			instance -> instance.group(
				LootItemCondition.CODEC.optionalFieldOf("player").forGetter(Conditions::player),
				Codec.INT.optionalFieldOf("min_drop").forGetter(Conditions::minDrop)
			).apply(instance, Conditions::new)
		);

		public boolean matches(int drop) {
			return this.minDrop.map(min -> drop >= min).orElse(true);
		}
	}
}
