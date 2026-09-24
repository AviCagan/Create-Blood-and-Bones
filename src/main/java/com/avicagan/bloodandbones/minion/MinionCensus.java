package com.avicagan.bloodandbones.minion;

import com.avicagan.bloodandbones.config.BBServerConfig;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Who has how many minions, awake, powered down or folded up, for the server's cap (none by default). Kept with
 * the world, since most of someone's minions may be in chunks nobody has loaded.
 */
public class MinionCensus extends SavedData {
    private static final String NAME = "bloodandbones_minions";
    private final Map<UUID, Set<UUID>> byMaker = new HashMap<>();

    public static MinionCensus get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(MinionCensus::new, MinionCensus::load, null), NAME);
    }

    private static MinionCensus load(CompoundTag tag, HolderLookup.Provider registries) {
        MinionCensus census = new MinionCensus();
        for (Tag t : tag.getList("Makers", Tag.TAG_COMPOUND)) {
            CompoundTag maker = (CompoundTag) t;
            Set<UUID> minions = new HashSet<>();
            for (Tag m : maker.getList("Minions", Tag.TAG_INT_ARRAY)) {
                minions.add(NbtUtils.loadUUID(m));
            }
            census.byMaker.put(maker.getUUID("Maker"), minions);
        }
        return census;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag makers = new ListTag();
        byMaker.forEach((maker, minions) -> {
            CompoundTag t = new CompoundTag();
            t.putUUID("Maker", maker);
            ListTag list = new ListTag();
            minions.forEach(m -> list.add(NbtUtils.createUUID(m)));
            t.put("Minions", list);
            makers.add(t);
        });
        tag.put("Makers", makers);
        return tag;
    }

    /** Whether this maker may have one more. */
    public static boolean mayMake(MinecraftServer server, UUID maker) {
        int cap = BBServerConfig.maxMinions();
        return cap < 0 || get(server).byMaker.getOrDefault(maker, Set.of()).size() < cap;
    }

    public static void count(MinecraftServer server, UUID maker, UUID minion) {
        MinionCensus census = get(server);
        census.byMaker.computeIfAbsent(maker, m -> new HashSet<>()).add(minion);
        census.setDirty();
    }

    /** It is gone for good (or folded into an item, which counts again when unfolded). */
    public static void forget(MinionEntity minion) {
        if (minion.getServer() == null || minion.makerId() == null) {
            return;
        }
        MinionCensus census = get(minion.getServer());
        Set<UUID> set = census.byMaker.get(minion.makerId());
        if (set != null && set.remove(minion.getUUID())) {
            census.setDirty();
        }
    }

    public static int of(MinecraftServer server, UUID maker) {
        return get(server).byMaker.getOrDefault(maker, Set.of()).size();
    }
}
