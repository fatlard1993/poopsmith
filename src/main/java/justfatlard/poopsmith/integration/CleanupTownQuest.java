package justfatlard.poopsmith.integration;

import justfatlard.poopsmith.PoopPlacement;
import justfatlard.village_quests.quest.VillagerQuest;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * "Clean up the town": too many open-air poop and guano piles in the
 * village. World-state completion: at dialogue time the open-air stack count
 * near the player is at or near zero. No material reward: shoveling the
 * streets is civic duty, and the reputation is the point. The llamas will
 * re-foul everything on schedule; that is the loop, not a bug.
 */
public class CleanupTownQuest extends VillagerQuest {
	/** At or below this many remaining open-air stacks counts as clean. */
	private static final int CLEAN_THRESHOLD = 1;

	public CleanupTownQuest(String requesterName, UUID villagerUuid) {
		super(QuestType.VILLAGE_DEVELOPMENT, requesterName, villagerUuid, 10);
	}

	@Override
	public String getDescription() {
		String[] descriptions = {
			requesterName + ": \"Have you smelled the street? Of course you have. Everyone has. Shovel the piles up, all of them, and this town might pass for civilized again.\"",
			requesterName + ": \"Between the llamas and... certain individuals, the lanes are a disgrace. Take a shovel to every pile in the open and I'll see you're thought well of.\"",
			requesterName + ": \"The town reeks and nobody's owning it. Clear the piles off the streets. A shovel gets you the goods back, if that sweetens it.\"",
		};
		return descriptions[ThreadLocalRandom.current().nextInt(descriptions.length)];
	}

	@Override
	public String getObjective() {
		return "Shovel up the open-air poop and guano piles around the village";
	}

	@Override
	public boolean checkCompletion(ServerPlayer player) {
		if (!(player.level() instanceof ServerLevel world)) return false;
		return PoopPlacement.countOpenAirStacks(world, player.blockPosition()) <= CLEAN_THRESHOLD;
	}

	@Override
	public void onComplete(ServerPlayer player) {
		String[] responses = {
			"*breathes in deliberately* Air. Just air. You have no idea what you've given us.",
			"The streets are streets again. That was honest work and everyone saw you do it.",
			"Clean. Actually clean. Enjoy it while it lasts; the llamas certainly won't let it.",
		};
		player.sendSystemMessage(
			Component.literal(requesterName + ": \"" + responses[ThreadLocalRandom.current().nextInt(responses.length)] + "\"")
				.withStyle(ChatFormatting.GREEN), true);
		this.completed = true;
	}
}
