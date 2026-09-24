package com.avicagan.bloodandbones.parts;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/** When a trait's effect happens (docs/PARTS-AND-TRAITS.md section 5.2). */
public enum Trigger implements StringRepresentable {
    /** Always, while worn or fitted. */
    PASSIVE,
    /** Every so many ticks. */
    TICK,
    /** When the host is hurt. */
    HURT,
    /** When the host hits something. */
    ATTACK,
    /** When the host lands from a fall. */
    FALL,
    /** When the host kills something. */
    KILL,
    /** When a mob sets its sights on the host. */
    TARGETED,
    /** When the host fires it (a key for players, the AI for minions). */
    ACTIVATE;

    public static final Codec<Trigger> CODEC = StringRepresentable.fromEnum(Trigger::values);

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
