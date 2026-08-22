package justfatlard.poopsmith.integration;

import justfatlard.block_tip.api.BlockTipApi;
import justfatlard.poopsmith.Main;
import justfatlard.poopsmith.PoopOwners;

/**
 * What left the pile you are looking at, while the trail is still warm.
 *
 * <p>Two uses, one line. A wood reads differently when the droppings in it name
 * what walks through, and a village street reads differently when one of them
 * says Player. The card never names an individual, only a kind; the company you
 * keep can narrow it down from there, which is the entire joke.
 */
public final class PoopTipRegistration {
	private PoopTipRegistration() {}

	public static void register() {
		BlockTipApi.describe((level, pos, state, player) -> {
			if (!state.is(Main.POOP_LAYER_BLOCK)) return null;
			return PoopOwners.describe(level, pos, state);
		});
	}
}
