package justfatlard.poopsmith.integration;

import justfatlard.poopsmith.Main;
import justfatlard.poopsmith.PoopPlacement;
import justfatlard.village_quests.api.QuestRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;

/**
 * Registers the latrine quests with village-quests. This class references
 * village-quests types directly (compileOnly dependency, village-builder's
 * BuilderQuestRegistration pattern) and must only be loaded behind an
 * isModLoaded("village-quests-justfatlard") guard in Main.
 *
 * Offer conditions come straight from world state around the villager:
 * a full pit offers the cleanout, no pit at all offers the teaching quest.
 */
public final class LatrineQuestRegistration {
	private static final Logger LOGGER = LoggerFactory.getLogger(Main.MOD_ID);

	// Offer the cleanout once the pit holds at least this many converted
	// blocks above its seed; the requested count never exceeds the
	// accumulation, so completing it never requires the seed block
	private static final int CLEANOUT_MIN_ACCUMULATION = 2;
	private static final int CLEANOUT_MAX_REQUEST = 3;
	// The town-cleanup quest opens above this many open-air piles; the llama
	// schedule guarantees recurrence
	private static final int CLEANUP_OFFER_THRESHOLD = 6;
	private static final float OFFER_CHANCE = 0.25F;
	private static final int WITNESS_REPUTATION_DING = -1;

	private LatrineQuestRegistration() {}

	public static void register() {
		QuestRegistry.registerUniversalQuest((villager, villagerName, reputation, random) -> {
			if (!(villager.level() instanceof ServerLevel world)) return null;
			if (random.nextFloat() > OFFER_CHANCE) return null;

			// Filthiest problem first: town mess, then a full pit, then the
			// missing latrine (whose absence usually caused the mess)
			if (PoopPlacement.countOpenAirStacks(world, villager.blockPosition()) > CLEANUP_OFFER_THRESHOLD) {
				return new CleanupTownQuest(villagerName, villager.getUUID());
			}

			List<BlockPos> pits = PoopPlacement.findLatrinePits(world, villager.blockPosition());

			if (pits.isEmpty()) {
				return new DigLatrineQuest(villagerName, villager.getUUID(), new HashSet<>());
			}

			for (BlockPos pit : pits) {
				int accumulation = PoopPlacement.pitAccumulation(world, pit);
				if (accumulation >= CLEANOUT_MIN_ACCUMULATION) {
					int amount = Math.min(accumulation, CLEANOUT_MAX_REQUEST);
					return new CleanLatrineQuest(villagerName, villager.getUUID(), amount);
				}
			}
			return null;
		});

		registerWitnessDialogue();

		LOGGER.info("[{}] Registered latrine quests and witness dialogue with village-quests", Main.MOD_ID);
	}

	/**
	 * A villager who watched the player go in the open (see
	 * PlayerPoopManager.recordPublicWitnesses) gets a dialogue option about
	 * it. Picking it clears the record and costs a token point of standing;
	 * the line itself is the real consequence.
	 */
	private static void registerWitnessDialogue() {
		justfatlard.village_quests.api.DialogueRegistry.registerUniversalDialogue((villager, player, reputation) -> {
			if (!(villager.level() instanceof ServerLevel world)) return List.of();
			var data = justfatlard.poopsmith.player.PoopLevelData.get(world.getServer());
			java.util.UUID witnessed = data.getWitnessedPlayer(villager.getUUID(), world.getGameTime());
			if (witnessed == null || !witnessed.equals(player.getUUID())) return List.of();
			return List.of(new justfatlard.village_quests.api.DialogueRegistry.DialogueOption(
				"poopsmith_witness",
				net.minecraft.network.chat.Component.literal("About... what you saw."),
				-1000, 1000));
		});

		justfatlard.village_quests.api.DialogueRegistry.registerDialogueHandler("poopsmith_witness",
			(villager, player, optionId) -> {
				if (villager.level() instanceof ServerLevel world) {
					justfatlard.poopsmith.player.PoopLevelData.get(world.getServer())
						.clearWitness(villager.getUUID());
				}
				justfatlard.village_quests.api.VillageQuestsAPI.modifyPlayerReputation(
					player, villager.blockPosition(), WITNESS_REPUTATION_DING, "public indecency");
				String[] lines = {
					"I saw you. Behind the well. We have a privy.",
					"We don't speak of it. But we both know. The latrine has a door for a reason.",
					"Some things can't be unseen. Use the pit like everyone else. Please.",
					"Your business is your business. In the pit, it stays that way.",
				};
				return net.minecraft.network.chat.Component.literal(
					lines[java.util.concurrent.ThreadLocalRandom.current().nextInt(lines.length)]);
			});
	}
}
