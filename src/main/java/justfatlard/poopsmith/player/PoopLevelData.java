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

	private CompoundTag toNbt() {
		CompoundTag nbt = new CompoundTag();
		ListTag list = new ListTag();
		for (Map.Entry<UUID, Integer> entry : levels.entrySet()) {
			CompoundTag playerNbt = new CompoundTag();
			playerNbt.putLong("UUIDMost", entry.getKey().getMostSignificantBits());
			playerNbt.putLong("UUIDLeast", entry.getKey().getLeastSignificantBits());
			playerNbt.putInt("Level", entry.getValue());
			int diarrhea = diarrheaTicks.getOrDefault(entry.getKey(), 0);
			if (diarrhea > 0) playerNbt.putInt("DiarrheaTicks", diarrhea);
			list.add(playerNbt);
		}
		nbt.put("Players", list);
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
			}
		});
		return data;
	}
}
