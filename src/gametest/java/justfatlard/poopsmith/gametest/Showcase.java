package justfatlard.poopsmith.gametest;

import justfatlard.pandorical.api.PandoricalApi;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.core.BlockPos;

/**
 * The pictures for the readme and the mod page: the three places the cycle actually happens - a
 * village privy with its pit, a paddock that has been lived in, and a roost paying its way in
 * guano. Scenes rather than a shelf of blocks: the mod is what the blocks are doing.
 *
 * <p>Run it under xvfb-run; the frames land in build/run/clientGameTest/screenshots.
 */
public final class Showcase implements FabricClientGameTest {

	private static final int WIDTH = 1920;
	private static final int HEIGHT = 1080;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			TestServerContext server = world.getServer();
			TestServerConnection connection = world.getConnection();
			connection.waitForChunksRender();
			server.waitFor(s -> PandoricalApi.isAvailable(connection.getServerPlayer()));

			context.getInput().pressKey(options -> options.keyToggleGui);
			server.runCommand("gamerule doDaylightCycle false");
			server.runCommand("gamerule doWeatherCycle false");
			server.runCommand("gamerule doMobSpawning false");
			server.runCommand("weather clear");
			server.runCommand("time set 1000");
			server.runCommand("gamemode spectator @a");

			BlockPos origin = server.computeOnServer(s -> connection.getServerPlayer().blockPosition());
			int x = origin.getX();
			int y = origin.getY();
			int z = origin.getZ();

			buildLatrine(server, x, y, z);
			buildPaddock(server, x, y, z);
			buildRoost(server, x, y, z);
			context.waitTicks(60);

			// Away and back, so every chunk these scenes touched is sent again. A template or a
			// hundred setblocks into chunks the client already has does not always reach it: the
			// first run of this came out with the privy missing its roof.
			server.runCommand("tp @a %d %d %d".formatted(x + 600, y + 30, z));
			context.waitTicks(100);
			server.runCommand("tp @a %d %d %d".formatted(x, y + 10, z));
			context.waitTicks(140);

			look(server, x + 9.0, y + 2.0, z - 14.0, x - 1.0, y + 2.0, z - 23.0);
			context.waitTicks(40);
			shoot(context, "latrine");

			look(server, x + 0.5, y + 3.0, z + 28.0, x, y + 1.0, z + 14.0);
			context.waitTicks(40);
			shoot(context, "paddock");

			look(server, x + 22.0, y + 1.0, z + 0.5, x + 29.0, y + 1.4, z);
			context.waitTicks(40);
			shoot(context, "roost");
		}
	}

	/** The privy a village builds, with the pit under it doing its work. */
	private void buildLatrine(TestServerContext server, int x, int y, int z) {
		int at = z - 26;
		// A street to stand it on, and the hut itself, which is the mod's own template.
		server.runCommand("fill %d %d %d %d %d %d minecraft:dirt_path"
			.formatted(x - 8, y - 1, at - 4, x + 8, y - 1, at + 8));
		server.runCommand("place template poopsmith:latrine_plains %d %d %d".formatted(x - 3, y, at));
		// The street outside it, lived in: loose piles where the villagers did not make it.
		for (int[] spot : new int[][] {{3, 3, 2}, {5, 5, 1}, {6, 2, 3}, {-5, 6, 1}, {-4, 4, 2}}) {
			server.runCommand("setblock %d %d %d poopsmith:poop_layer[layers=%d,facing=north]"
				.formatted(x + spot[0], y, at + spot[1], spot[2]));
		}
		server.runCommand("summon minecraft:villager %d %d %d {NoAI:1b,Rotation:[150f,0f]}"
			.formatted(x + 2, y, at + 5));

	}

	/** A paddock that has been lived in: a mat of piles, and the animals that left them. */
	private void buildPaddock(TestServerContext server, int x, int y, int z) {
		int at = z + 14;
		server.runCommand("fill %d %d %d %d %d %d minecraft:oak_fence"
			.formatted(x - 7, y, at - 7, x + 7, y, at + 7));
		server.runCommand("fill %d %d %d %d %d %d minecraft:air"
			.formatted(x - 6, y, at - 6, x + 6, y, at + 6));
		server.runCommand("setblock %d %d %d minecraft:oak_fence_gate[facing=south]".formatted(x, y, at + 7));

		// A field rather than a tower: loose heaps at one to three layers, the way they spread.
		for (int i = -5; i <= 5; i++) {
			for (int j = -5; j <= 5; j++) {
				if ((i * 7 + j * 3) % 5 != 0) continue;
				int layers = 1 + Math.floorMod(i * 3 + j, 3);
				server.runCommand("setblock %d %d %d poopsmith:poop_layer[layers=%d,facing=north]"
					.formatted(x + i, y, at + j, layers));
			}
		}
		// And the corner that has been used enough to pack down.
		server.runCommand("setblock %d %d %d poopsmith:poop_block".formatted(x - 4, y, at - 4));
		server.runCommand("setblock %d %d %d poopsmith:poop_layer[layers=6,facing=north]"
			.formatted(x - 4, y + 1, at - 4));

		for (int i = -2; i <= 2; i += 2) {
			server.runCommand("summon minecraft:cow %d %d %d {NoAI:1b,Rotation:[%df,0f]}"
				.formatted(x + i, y, at + i, 120 + i * 30));
		}
		server.runCommand("summon minecraft:llama %d %d %d {NoAI:1b,Rotation:[200f,0f]}"
			.formatted(x + 3, y, at - 3));

	}

	/** A roost paying its way: a box on the wall and the bed of guano under it. */
	private void buildRoost(TestServerContext server, int x, int y, int z) {
		int at = x + 26;
		server.runCommand("fill %d %d %d %d %d %d minecraft:stone"
			.formatted(at - 2, y, z - 4, at + 6, y + 5, z + 4));
		server.runCommand("fill %d %d %d %d %d %d minecraft:air"
			.formatted(at - 2, y, z - 3, at + 4, y + 4, z + 3));
		server.runCommand("setblock %d %d %d minecraft:lantern[hanging=true]".formatted(at + 1, y + 4, z));
		server.runCommand("setblock %d %d %d poopsmith:bat_box[facing=west]".formatted(at + 4, y + 2, z));

		// The bed it has built underneath: deep where it falls, packed where it has been longest.
		for (int i = 0; i <= 3; i++) {
			for (int j = -2; j <= 2; j++) {
				int layers = Math.max(1, 6 - Math.abs(j) * 2 - i);
				server.runCommand("setblock %d %d %d poopsmith:guano_layer[layers=%d]"
					.formatted(at + i, y, z + j, layers));
			}
		}
		server.runCommand("setblock %d %d %d poopsmith:guano_block".formatted(at + 2, y, z));

	}

	/**
	 * Stand the camera at one place and point it at another. The camera's y is the feet, so it
	 * looks from 1.62 above where it stands.
	 */
	private void look(TestServerContext server, double x, double y, double z,
			double atX, double atY, double atZ) {
		double dx = atX - x;
		double dy = atY - (y + 1.62);
		double dz = atZ - z;
		double yaw = -Math.toDegrees(Math.atan2(dx, dz));
		double pitch = -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
		server.runCommand("tp @a %.2f %.2f %.2f %.1f %.1f".formatted(x, y, z, yaw, pitch));
	}

	private void shoot(ClientGameTestContext context, String name) {
		context.takeScreenshot(TestScreenshotOptions.of(name)
			.withSize(WIDTH, HEIGHT)
			.disableCounterPrefix());
	}
}
