package justfatlard.poopsmith.integration;

import java.util.List;
import justfatlard.poopsmith.Main;
import justfatlard.village_quests.api.DialogueRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * A farmer who will sell you a box and tell you where to hang it.
 *
 * <p>The bat box is the least likely thing in this mod to be discovered. It is
 * craftable, it looks like a decoration, and its whole value is a slow supply of
 * bonemeal that only appears if it is sited correctly. A player who has never
 * been told simply never builds one.
 *
 * <p>Which makes the siting rules the actual product. The box is eight emeralds;
 * being told it wants sky above, clearance below and water in range is what
 * stops it being eight emeralds thrown at a wall.
 */
public final class BatBoxDialogue {
	private BatBoxDialogue() {}

	private static final String OPTION_ID = "poopsmith:bat_box";

	private static final int MIN_REPUTATION = 25;
	private static final int PRICE = 8;

	public static void register() {
		DialogueRegistry.registerProfessionDialogue("farmer", (villager, player, reputation) ->
			List.of(new DialogueRegistry.DialogueOption(
				OPTION_ID,
				Component.literal("How do you keep the fields going without running out of bonemeal?"),
				MIN_REPUTATION, Integer.MAX_VALUE)));

		DialogueRegistry.registerDialogueHandler(OPTION_ID, BatBoxDialogue::sell);
	}

	private static Component sell(net.minecraft.world.entity.npc.villager.Villager villager,
			ServerPlayer player, String optionId) {
		if (countEmeralds(player) < PRICE) {
			return Component.literal("Bats. You hang a box up and they do the rest, and what they leave is worth more "
				+ "to a field than anything you can buy. " + PRICE + " emeralds for one of mine.");
		}

		takeEmeralds(player);
		ItemStack box = new ItemStack(Main.BAT_BOX_ITEM);
		if (!player.getInventory().add(box)) {
			player.drop(box, false, net.minecraft.util.Prediction.SERVER_ONLY);
		}

		// The rules are the point. A box in the wrong place is indistinguishable
		// from a box that is simply slow, and that is how people give up on them.
		return Component.literal("Open sky above it, room to hang underneath, and water within a stone's throw. "
			+ "Get any of that wrong and it will sit there for a season doing nothing, and you will blame the bats.");
	}

	private static int countEmeralds(ServerPlayer player) {
		int found = 0;
		for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
			if (stack.is(Items.EMERALD)) found += stack.getCount();
		}
		return found;
	}

	private static void takeEmeralds(ServerPlayer player) {
		int remaining = PRICE;
		for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
			if (remaining <= 0) return;
			if (!stack.is(Items.EMERALD)) continue;

			int taken = Math.min(remaining, stack.getCount());
			stack.shrink(taken);
			remaining -= taken;
		}
	}
}
