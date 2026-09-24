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
    public static final ModConfigSpec.IntValue MAX_MINIONS;
    public static final ModConfigSpec.EnumValue<MinionDeath> MINION_DEATH;
    public static final ModConfigSpec.IntValue TROUGH_RADIUS;
    public static final ModConfigSpec.DoubleValue POWER_DRAIN;

    /** What a lethal blow does to a minion that has power: powers it down, scatters it into its parts, or destroys it. */
    public enum MinionDeath {
        COLLAPSE, SCATTER, DESTROY
    }

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
        builder.push("minions");
        MAX_MINIONS = builder
                .comment("Most minions one player may have awake or dormant at once. -1 for no limit.")
                .defineInRange("max_minions_per_player", -1, -1, 10000);
        MINION_DEATH = builder
                .comment("What a lethal blow does to a minion: COLLAPSE (it powers down at 1 health, never destroyed), SCATTER (falls apart into its parts), DESTROY.")
                .defineEnum("minion_death", MinionDeath.COLLAPSE);
        TROUGH_RADIUS = builder
                .comment("How far, in blocks, a hungry organic minion looks for a Blood Trough.")
                .defineInRange("trough_search_radius", 48, 4, 256);
        POWER_DRAIN = builder
                .comment("How fast minions use their blood or soul blood. 1 is normal, 0 never.")
                .defineInRange("power_drain", 1.0, 0.0, 100.0);
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

    public static int maxMinions() {
        try {
            return SPEC.isLoaded() ? MAX_MINIONS.get() : -1;
        } catch (IllegalStateException e) {
            return -1;
        }
    }

    public static MinionDeath minionDeath() {
        try {
            return SPEC.isLoaded() ? MINION_DEATH.get() : MinionDeath.COLLAPSE;
        } catch (IllegalStateException e) {
            return MinionDeath.COLLAPSE;
        }
    }

    public static int troughRadius() {
        try {
            return SPEC.isLoaded() ? TROUGH_RADIUS.get() : 48;
        } catch (IllegalStateException e) {
            return 48;
        }
    }

    public static float powerDrain() {
        try {
            return SPEC.isLoaded() ? POWER_DRAIN.get().floatValue() : 1.0F;
        } catch (IllegalStateException e) {
            return 1.0F;
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
