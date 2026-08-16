package justfatlard.poopsmith.integration;

import justfatlard.poopsmith.Main;
import justfatlard.poopsmith.PoopPlacement;
import justfatlard.village_quests.quest.VillagerQuest;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * "Dig a new latrine": the teaching quest, offered when the village has no
 * latrine pit at all. The quest text explains the mechanic outright (dig a
 * pit, seed the bottom with a poop block); completion is a world-state check
 * at dialogue time: a valid pit exists near the player that was not in the
 * offer-time snapshot. Reward includes a couple of poop blocks so the player
 * can seed the next village's pit themselves.
 */
public class DigLatrineQuest extends VillagerQuest {
	private final Set<BlockPos> pitsAtOffer;

	public DigLatrineQuest(String requesterName, UUID villagerUuid, Set<BlockPos> pitsAtOffer) {
		super(QuestType.CREATION, requesterName, villagerUuid, 12);
		this.pitsAtOffer = pitsAtOffer;
	}

	@Override
	public String getDescription() {
		String[] descriptions = {
			requesterName + ": \"This village has no latrine and I have opinions about the streets. Dig a pit near the houses, two or three deep, and line the bottom with a poop block. Yes, a poop block. That is how a privy seeds itself: the pit fills on its own from there.\"",
			requesterName + ": \"We need a proper privy. Dig down a couple of blocks, put a poop block at the bottom, and leave the rest to nature. The seed block does the work. Do not ask me how we learned this.\"",
			requesterName + ": \"Every decent village has a pit. Ours has streets. Dig one a few blocks deep, set a poop block at its floor, and the village will handle the filling. Enthusiastically.\"",
		};
		return descriptions[ThreadLocalRandom.current().nextInt(descriptions.length)];
	}

	@Override
	public String getObjective() {
		return "Dig a pit 2-3 blocks deep near the village and place a poop block at its bottom";
	}

	@Override
	public boolean checkCompletion(ServerPlayer player) {
		if (!(player.level() instanceof ServerLevel world)) return false;
		for (BlockPos pit : PoopPlacement.findLatrinePits(world, player.blockPosition())) {
			if (!pitsAtOffer.contains(pit)) return true;
		}
		return false;
	}

	@Override
	public void onComplete(ServerPlayer player) {
		player.getInventory().add(new ItemStack(Main.POOP_BLOCK_ITEM, 2));
		String[] responses = {
			"A pit of our own. *wipes eye* Take these spares and start the next one yourself, wherever the need finds you.",
			"You dug it, you seeded it, and now it belongs to everyone. Here are two more seeds. Spread the practice.",
			"That is fine civic work. Keep these poop blocks; a traveler who can found a privy is welcome anywhere.",
		};
		player.sendSystemMessage(
			Component.literal(requesterName + ": \"" + responses[ThreadLocalRandom.current().nextInt(responses.length)] + "\"")
				.withStyle(ChatFormatting.GREEN), true);
		this.completed = true;
	}
}
