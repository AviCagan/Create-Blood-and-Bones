package com.avicagan.bloodandbones.parts;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Small helpers for showing traits. */
public final class Traits {
    private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

    private Traits() {
    }

    /** "Springy II", from the trait's own name key; the id if it has none. */
    public static MutableComponent describe(PartsData.Store store, TraitList.Resolved trait) {
        Trait def = store.trait(trait.id());
        MutableComponent name = def == null ? Component.literal(trait.id().toString()) : Component.translatable(def.name());
        int level = def == null ? trait.level() : Math.min(trait.level(), Math.max(1, def.maxLevel()));
        return def != null && def.maxLevel() > 1 ? name.append(" " + roman(level)) : name;
    }

    /** A trait's description key ("trait.bloodandbones.venomous.desc"), if the language has one; null if not. */
    @org.jetbrains.annotations.Nullable
    public static String descriptionKey(PartsData.Store store, TraitList.Resolved trait) {
        Trait def = store.trait(trait.id());
        if (def == null) {
            return null;
        }
        String key = def.name() + ".desc";
        return net.minecraft.locale.Language.getInstance().has(key) ? key : null;
    }

    public static String roman(int level) {
        return level >= 0 && level < ROMAN.length ? ROMAN[level] : Integer.toString(level);
    }
}
