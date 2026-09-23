package com.avicagan.bloodandbones.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Per-world gameplay settings, kept in the world's serverconfig folder and sent to players who join.
 */
public class BBServerConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.DoubleValue ROT_SPEED;
    public static final ModConfigSpec.BooleanValue CRUMBLE;
    public static final ModConfigSpec.DoubleValue CRUMBLE_DAYS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("rot");
        ROT_SPEED = builder
                .comment("How fast carcasses rot. 1 is normal, 2 twice as fast, 0 never.")
                .defineInRange("rot_speed", 1.0, 0.0, 100.0);
        CRUMBLE = builder
                .comment("Rotten carcasses fall apart into bones and rotten flesh after a while, so old ones do not pile up.")
                .define("rotten_carcasses_crumble", true);
        CRUMBLE_DAYS = builder
                .comment("How long a rotten carcass lasts before it falls apart, in Minecraft days (20 minutes each), counted at the rot speed.")
                .defineInRange("crumble_after_days", 1.0, 0.01, 1000.0);
        builder.pop();
        SPEC = builder.build();
    }

    public static float rotSpeed() {
        try {
            return SPEC.isLoaded() ? ROT_SPEED.get().floatValue() : 1.0F;
        } catch (IllegalStateException e) {
            return 1.0F;
        }
    }

    public static boolean crumble() {
        try {
            return !SPEC.isLoaded() || CRUMBLE.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }

    /** Game ticks of rot a rotten carcass lasts. */
    public static float crumbleTicks() {
        try {
            return (float) ((SPEC.isLoaded() ? CRUMBLE_DAYS.get() : 1.0) * 24000.0);
        } catch (IllegalStateException e) {
            return 24000.0F;
        }
    }
}
