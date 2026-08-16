package justfatlard.poopsmith.integration;

import justfatlard.poopsmith.Main;
import justfatlard.village_quests.quest.FetchItemQuest;
import justfatlard.village_quests.util.InventoryHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * "Clean out the latrine": the village privy has accumulated converted poop
 * blocks above its seed and somebody has to deal with it. A fetch-shaped
 * quest (the turn-in proof is N mined poop blocks) so the standard fetch
 * lifecycle, submission item icon, and click-time validation all apply.
 * The requested count is satisfiable without touching the seed block, so a
 * cleaned pit keeps working.
 */
public class CleanLatrineQuest extends FetchItemQuest {
	public CleanLatrineQuest(String requesterName, UUID villagerUuid, int amount) {
		super(requesterName, villagerUuid, Main.POOP_BLOCK_ITEM, amount, 8);
	}

	@Override
	public String getDescription() {
		int amount = getSubmissionAmount();
		String[] descriptions = {
			requesterName + ": \"The privy pit is full. Properly full. Nobody here will touch the job, so I'm asking you: dig out " + amount + " of the... solid layers and bring them to me as proof.\"",
			requesterName + ": \"You know the latrine? It has become a monument. Mine out " + amount + " blocks of it, bring them here, and we will never speak of what they are.\"",
			requesterName + ": \"Someone has to clean the pit and everyone else suddenly has knee trouble. " + amount + " blocks out of it, brought to me, and the village owes you.\"",
		};
		return descriptions[ThreadLocalRandom.current().nextInt(descriptions.length)];
	}

	@Override
	public String getObjective() {
		return "Mine " + getSubmissionAmount() + " accumulated poop blocks out of the village latrine and bring them back";
	}

	@Override
	public void onComplete(ServerPlayer player) {
		InventoryHelper.removeItem(player.getInventory(), getSubmissionItem(), getSubmissionAmount());
		String[] responses = {
			"*takes them at arm's length* You are a braver soul than anyone here. The pit thanks you. I thank you. Mostly the pit.",
			"*nods without breathing through the nose* Good work. Genuinely. Now please stand downwind.",
			"The privy lives to serve another season. That was you. Wear it proudly. From a distance.",
		};
		player.sendSystemMessage(
			Component.literal(requesterName + ": \"" + responses[ThreadLocalRandom.current().nextInt(responses.length)] + "\"")
				.withStyle(ChatFormatting.GREEN), true);
		this.completed = true;
	}
}
