package com.example.losgearplus.shifter;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Persistence for the Shifter Mastery system: level, mastery XP, transformations used and the start of the
 * cooldown window for each player. Stored in the world (overworld), so it survives death, relogs and restarts.
 */
public final class ShifterMasteryData extends SavedData {
	public static final String NAME = "los_gear_plus_shifter_mastery";

	public static final class Entry {
		public int level;
		/** Total mastery XP earned (the level is derived from it, see {@link ShifterMastery#XP_TO_REACH}). */
		public int xp;
		/** Transformations used in the current cooldown window. */
		public int uses;
		/** Game time (ticks) of the first transformation of the current window. */
		public long windowStart;
	}

	private final Map<UUID, Entry> players = new HashMap<>();

	public static SavedData.Factory<ShifterMasteryData> factory() {
		return new SavedData.Factory<>(ShifterMasteryData::new, ShifterMasteryData::load, null);
	}

	public static ShifterMasteryData load(CompoundTag tag, HolderLookup.Provider lookup) {
		ShifterMasteryData data = new ShifterMasteryData();
		ListTag list = tag.getList("players", Tag.TAG_COMPOUND);
		for (int i = 0; i < list.size(); i++) {
			CompoundTag t = list.getCompound(i);
			Entry e = new Entry();
			e.level = Math.max(0, Math.min(ShifterMastery.MAX_LEVEL, t.getInt("level")));
			e.xp = Math.max(0, t.getInt("xp"));
			e.uses = Math.max(0, t.getInt("uses"));
			e.windowStart = t.getLong("windowStart");
			data.players.put(t.getUUID("id"), e);
		}
		return data;
	}

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
		ListTag list = new ListTag();
		for (Map.Entry<UUID, Entry> me : players.entrySet()) {
			Entry e = me.getValue();
			if (e.level == 0 && e.xp == 0 && e.uses == 0) continue; // defaults need not be stored
			CompoundTag t = new CompoundTag();
			t.putUUID("id", me.getKey());
			t.putInt("level", e.level);
			t.putInt("xp", e.xp);
			t.putInt("uses", e.uses);
			t.putLong("windowStart", e.windowStart);
			list.add(t);
		}
		tag.put("players", list);
		return tag;
	}

	/** The player's entry (created on demand). */
	public Entry get(UUID id) {
		return players.computeIfAbsent(id, k -> new Entry());
	}

	/** The player's entry only if it exists (never creates one). */
	public Entry peek(UUID id) {
		return players.get(id);
	}
}
