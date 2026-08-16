package justfatlard.poopsmith.player;

import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player poop level (0-100), persisted world-side via SavedData on the
 * overworld so it survives sessions regardless of which dimension the player
 * logs out in. Same PASSTHROUGH-codec-over-NBT pattern as village-quests'
 * ReputationManager.
 */
public class PoopLevelData extends SavedData {
	public static final int MAX_LEVEL = 100;

	private static final Codec<PoopLevelData> CODEC = Codec.PASSTHROUGH.xmap(
		dynamic -> fromNbt((CompoundTag) dynamic.convert(NbtOps.INSTANCE).getValue()),
		data -> new Dynamic<>(NbtOps.INSTANCE, data.toNbt()));

	private static final SavedDataType<PoopLevelData> TYPE = new SavedDataType<>(
		Identifier.fromNamespaceAndPath("poopsmith", "poop_levels"),
		PoopLevelData::new, CODEC, DataFixTypes.LEVEL);

	private final Map<UUID, Integer> levels = new ConcurrentHashMap<>();
	private final Map<UUID, Integer> diarrheaTicks = new ConcurrentHashMap<>();
	private final java.util.Set<UUID> netherEntered = ConcurrentHashMap.newKeySet();

	public static PoopLevelData get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	public int getLevel(UUID player) {
		return levels.getOrDefault(player, 0);
	}

	/** Clamps to [0, {@link #MAX_LEVEL}] and returns the stored value. */
	public int setLevel(UUID player, int level) {
		int clamped = Math.clamp(level, 0, MAX_LEVEL);
		levels.put(player, clamped);
		setDirty();
		return clamped;
	}

	public int getDiarrheaTicks(UUID player) {
		return diarrheaTicks.getOrDefault(player, 0);
	}

	/** Remaining diarrhea ticks; persisted so a relog doesn't cure the runs. */
	public void setDiarrheaTicks(UUID player, int ticks) {
		if (ticks <= 0) {
			diarrheaTicks.remove(player);
		} else {
			diarrheaTicks.put(player, ticks);
		}
		setDirty();
	}

	// Public-pooping witnesses: villager UUID -> (player UUID, game time seen).
	// Expire after a few in-game days; pruned lazily on read.
	private record WitnessRecord(UUID playerUuid, long gameTime) {}
	private static final long WITNESS_EXPIRY_TICKS = 3L * 24000L;
	private final Map<UUID, WitnessRecord> witnesses = new ConcurrentHashMap<>();

	public void recordWitness(UUID villager, UUID player, long gameTime) {
		witnesses.put(villager, new WitnessRecord(player, gameTime));
		setDirty();
	}

	/** The player this villager witnessed (unexpired), or null. */
	public UUID getWitnessedPlayer(UUID villager, long now) {
		WitnessRecord record = witnesses.get(villager);
		if (record == null) return null;
		if (now - record.gameTime() > WITNESS_EXPIRY_TICKS) {
			witnesses.remove(villager);
			setDirty();
			return null;
		}
		return record.playerUuid();
	}

	public void clearWitness(UUID villager) {
		if (witnesses.remove(villager) != null) setDirty();
	}

	/** One-shot Nether scare bookkeeping: has this player EVER entered the Nether. */
	public boolean hasEnteredNether(UUID player) {
		return netherEntered.contains(player);
	}

	public void markEnteredNether(UUID player) {
		netherEntered.add(player);
		setDirty();
	}

	private CompoundTag toNbt() {
		CompoundTag nbt = new CompoundTag();
		ListTag list = new ListTag();
		// Union of all keyed players: a player may carry only the nether flag
		// (or only diarrhea) with a zero level, and must still serialize
		java.util.Set<UUID> allPlayers = new java.util.HashSet<>(levels.keySet());
		allPlayers.addAll(diarrheaTicks.keySet());
		allPlayers.addAll(netherEntered);
		for (UUID uuid : allPlayers) {
			CompoundTag playerNbt = new CompoundTag();
			playerNbt.putLong("UUIDMost", uuid.getMostSignificantBits());
			playerNbt.putLong("UUIDLeast", uuid.getLeastSignificantBits());
			playerNbt.putInt("Level", levels.getOrDefault(uuid, 0));
			int diarrhea = diarrheaTicks.getOrDefault(uuid, 0);
			if (diarrhea > 0) playerNbt.putInt("DiarrheaTicks", diarrhea);
			if (netherEntered.contains(uuid)) playerNbt.putBoolean("NetherEntered", true);
			list.add(playerNbt);
		}
		nbt.put("Players", list);

		ListTag witnessList = new ListTag();
		for (Map.Entry<UUID, WitnessRecord> entry : witnesses.entrySet()) {
			CompoundTag w = new CompoundTag();
			w.putLong("VillagerMost", entry.getKey().getMostSignificantBits());
			w.putLong("VillagerLeast", entry.getKey().getLeastSignificantBits());
			w.putLong("PlayerMost", entry.getValue().playerUuid().getMostSignificantBits());
			w.putLong("PlayerLeast", entry.getValue().playerUuid().getLeastSignificantBits());
			w.putLong("GameTime", entry.getValue().gameTime());
			witnessList.add(w);
		}
		nbt.put("Witnesses", witnessList);
		return nbt;
	}

	private static PoopLevelData fromNbt(CompoundTag nbt) {
		PoopLevelData data = new PoopLevelData();
		nbt.getList("Players").ifPresent(list -> {
			for (int i = 0; i < list.size(); i++) {
				CompoundTag playerNbt = list.getCompoundOrEmpty(i);
				UUID uuid = new UUID(
					playerNbt.getLongOr("UUIDMost", 0L),
					playerNbt.getLongOr("UUIDLeast", 0L));
				data.levels.put(uuid, Math.clamp(playerNbt.getIntOr("Level", 0), 0, MAX_LEVEL));
				int diarrhea = playerNbt.getIntOr("DiarrheaTicks", 0);
				if (diarrhea > 0) data.diarrheaTicks.put(uuid, diarrhea);
				if (playerNbt.getBooleanOr("NetherEntered", false)) data.netherEntered.add(uuid);
			}
		});
		nbt.getList("Witnesses").ifPresent(list -> {
			for (int i = 0; i < list.size(); i++) {
				CompoundTag w = list.getCompoundOrEmpty(i);
				UUID villager = new UUID(w.getLongOr("VillagerMost", 0L), w.getLongOr("VillagerLeast", 0L));
				UUID player = new UUID(w.getLongOr("PlayerMost", 0L), w.getLongOr("PlayerLeast", 0L));
				data.witnesses.put(villager, new WitnessRecord(player, w.getLongOr("GameTime", 0L)));
			}
		});
		return data;
	}
}
