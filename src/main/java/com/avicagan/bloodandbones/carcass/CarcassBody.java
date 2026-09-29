package com.avicagan.bloodandbones.carcass;

import com.avicagan.bloodandbones.carcass.rig.Rig;
import com.avicagan.bloodandbones.carcass.rig.RigManager;
import com.avicagan.bloodandbones.parts.PartsData;
import com.avicagan.bloodandbones.parts.ResolvedMob;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Optional;

/**
 * What a mob's groups say about its carcass, read by the parts of the mod that deal with carcasses: its weight class and
 * the figures that come from it (drag, blood, floating or sinking, rot time), and a baby's share of the yields. Rule 2:
 * set by group, a mob's own file only where it differs; rule 3: all of it data.
 */
public final class CarcassBody {
    private CarcassBody() {
    }

    /**
     * A mob's weight class: the one its groups name, else the smallest whose {@code up_to} its grown size fits, else the
     * largest there is; the figures from before the classes if none are loaded.
     */
    public static WeightClass weightClass(PartsData.Store store, ResourceLocation entity) {
        return weightClassEntry(store, entity).map(Map.Entry::getValue).orElse(WeightClass.NONE);
    }

    /** The class's id and figures, or empty when no classes are loaded. */
    public static Optional<Map.Entry<ResourceLocation, WeightClass>> weightClassEntry(PartsData.Store store, ResourceLocation entity) {
        Map<ResourceLocation, WeightClass> classes = store.weightClasses();
        Optional<ResourceLocation> named = store.resolve(entity, false).carcass().weightClass();
        if (named.isPresent()) {
            WeightClass chosen = classes.get(named.get());
            if (chosen != null) {
                return Optional.of(Map.entry(named.get(), chosen));
            }
        }
        float size = (store == PartsData.CLIENT ? RigManager.clientRig(entity) : RigManager.forEntity(entity)).map(Rig::weight).orElse(0.0F);
        return bySize(classes, size);
    }

    /** The smallest class whose up_to a size fits, else the largest of those that take mobs by size. */
    public static Optional<Map.Entry<ResourceLocation, WeightClass>> bySize(Map<ResourceLocation, WeightClass> classes, float size) {
        Map.Entry<ResourceLocation, WeightClass> fits = null;
        Map.Entry<ResourceLocation, WeightClass> largest = null;
        for (Map.Entry<ResourceLocation, WeightClass> e : classes.entrySet()) {
            if (e.getValue().upTo().isEmpty()) {
                continue;
            }
            float upTo = e.getValue().upTo().get();
            if (size <= upTo && (fits == null || upTo < fits.getValue().upTo().get())) {
                fits = e;
            }
            if (largest == null || upTo > largest.getValue().upTo().get()) {
                largest = e;
            }
        }
        return Optional.ofNullable(fits != null ? fits : largest);
    }

    public static WeightClass weightClass(CarcassSavedData.Carcass carcass) {
        return weightClass(PartsData.SERVER, carcass.entity);
    }

    /**
     * Ticks a carcass of this mob takes to rot in a temperate place: its rig's own figure where it has one, else the last of
     * its groups to name one, else its weight class's.
     */
    public static int rotTime(PartsData.Store store, ResourceLocation entity) {
        Optional<Integer> own = (store == PartsData.CLIENT ? RigManager.clientRig(entity) : RigManager.forEntity(entity)).flatMap(Rig::rotTime);
        if (own.isPresent()) {
            return own.get();
        }
        return store.resolve(entity, false).carcass().rotTime().orElseGet(() -> weightClass(store, entity).rotTime());
    }

    public static int rotTime(CarcassSavedData.Carcass carcass) {
        return rotTime(PartsData.SERVER, carcass.entity);
    }

    /** Blood a fresh carcass of this mob holds per block of animal, mB. */
    public static float bloodPerWeight(ResourceLocation entity) {
        return weightClass(PartsData.SERVER, entity).bloodPerWeight();
    }

    /**
     * A baby's share of what the grown one's butchery gives: what its groups say (a slime's smallest size gives a quarter,
     * a magma cube's nothing), else its share of the grown one's size.
     */
    public static float babyYield(CarcassSavedData.Carcass carcass) {
        if (!carcass.baby) {
            return 1.0F;
        }
        ResolvedMob mob = PartsData.SERVER.resolve(carcass.entity, false);
        if (mob.carcass().babyYield().isPresent()) {
            return Math.max(0.0F, mob.carcass().babyYield().get());
        }
        float grown = RigManager.forEntity(carcass.entity).map(Rig::weight).orElse(1.0F);
        float small = RigManager.forCarcass(carcass).map(Rig::weight).orElse(grown);
        return Math.min(1.0F, small / Math.max(grown, 1.0E-4F));
    }
}
