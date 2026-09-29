package com.avicagan.bloodandbones.config;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;
import java.util.Set;

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
    public static final ModConfigSpec.BooleanValue MINION_BLOCK_DAMAGE;
    public static final ModConfigSpec.DoubleValue TRAIT_STRENGTH;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> DISABLED_EFFECT_TYPES;
    public static final ModConfigSpec.DoubleValue GOING_OFF_BELOW;
    public static final ModConfigSpec.DoubleValue ROTTEN_BELOW;
    public static final ModConfigSpec.DoubleValue DRAG_LIGHT_MASS;
    public static final ModConfigSpec.DoubleValue DRAG_LIGHT_PENALTY;
    public static final ModConfigSpec.DoubleValue DRAG_HEAVY_MASS;
    public static final ModConfigSpec.DoubleValue DRAG_HEAVY_PENALTY;
    public static final ModConfigSpec.IntValue CUTS_TO_SEVER;
    public static final ModConfigSpec.IntValue CUTS_TO_BUTCHER;
    public static final ModConfigSpec.IntValue STROKES_TO_SKIN;
    public static final ModConfigSpec.DoubleValue CARRY_MASS;
    public static final java.util.Map<com.avicagan.bloodandbones.machine.MachineKind, ModConfigSpec.DoubleValue> MACHINE_STRESS = new java.util.EnumMap<>(
            com.avicagan.bloodandbones.machine.MachineKind.class);
    public static final java.util.Map<com.avicagan.bloodandbones.machine.MachineKind, ModConfigSpec.DoubleValue> MACHINE_PACE = new java.util.EnumMap<>(
            com.avicagan.bloodandbones.machine.MachineKind.class);
    public static final ModConfigSpec.IntValue ROAST_MIN;
    public static final ModConfigSpec.IntValue ROAST_MAX;
    public static final ModConfigSpec.IntValue ROAST_MAX_WHOLE;
    public static final ModConfigSpec.IntValue ROAST_PER_BLOCK;
    public static final ModConfigSpec.IntValue SPOOL_TICKS;
    public static final ModConfigSpec.DoubleValue FULL_DRAIN;
    public static final ModConfigSpec.IntValue TAP;
    public static final java.util.Map<com.avicagan.bloodandbones.cyber.Module, ModConfigSpec.IntValue> MODULE_UPKEEP = new java.util.EnumMap<>(
            com.avicagan.bloodandbones.cyber.Module.class);
    public static final ModConfigSpec.IntValue NECROSIS_SWING;
    public static final ModConfigSpec.DoubleValue NECROSIS_BLOCKS;
    public static final ModConfigSpec.IntValue NECROSIS_MEAL;
    public static final ModConfigSpec.IntValue PERFUSE_MB;
    public static final ModConfigSpec.IntValue PERFUSE_POINTS;

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
        GOING_OFF_BELOW = builder
                .comment("Freshness (1 fresh, 0 rotten) below which a carcass is going off: its meat, offal and fat come out halved.")
                .defineInRange("going_off_below", 0.6, 0.0, 1.0);
        ROTTEN_BELOW = builder
                .comment("Freshness below which a carcass is rotting: its meat is rotten flesh, its offal and fat are gone, its hide and scraps halved,",
                        "and it is too far gone to build a minion from.")
                .defineInRange("rotting_below", 0.3, 0.0, 1.0);
        builder.pop();
        builder.push("carcasses");
        DRAG_LIGHT_MASS = builder
                .comment("The drag penalty: dragging this much mass (a whole chicken) costs drag_light_penalty of a dragger's speed.",
                        "Between that and drag_heavy_mass (a whole ravager) it rises with the logarithm of the mass; less falls in proportion;",
                        "nothing costs more than drag_heavy_penalty. Each weight class then multiplies it by its own drag.")
                .defineInRange("drag_light_mass", 0.127, 0.001, 1000.0);
        DRAG_LIGHT_PENALTY = builder.defineInRange("drag_light_penalty", 0.05, 0.0, 1.0);
        DRAG_HEAVY_MASS = builder.defineInRange("drag_heavy_mass", 5.98, 0.001, 1000.0);
        DRAG_HEAVY_PENALTY = builder.defineInRange("drag_heavy_penalty", 0.55, 0.0, 1.0);
        CUTS_TO_SEVER = builder
                .comment("Cleaver cuts to take a limb off a carcass.")
                .defineInRange("cuts_to_sever", 3, 1, 100);
        CUTS_TO_BUTCHER = builder
                .comment("Cleaver cuts to butcher a piece already off.")
                .defineInRange("cuts_to_butcher", 3, 1, 100);
        STROKES_TO_SKIN = builder
                .comment("Flensing Knife strokes to skin a carcass.")
                .defineInRange("strokes_to_skin", 4, 1, 100);
        CARRY_MASS = builder
                .comment("The most a piece may weigh (as its size in flesh, a full block being 1) and still be picked up as an item; heavier ones are dragged.")
                .defineInRange("carry_mass", 0.13, 0.0, 1000.0);
        builder.pop();
        builder.push("machines");
        for (com.avicagan.bloodandbones.machine.MachineKind kind : com.avicagan.bloodandbones.machine.MachineKind.values()) {
            MACHINE_STRESS.put(kind, builder
                    .comment("Stress the " + kind.id + " takes, per RPM.")
                    .defineInRange(kind.id + "_stress", kind.stress, 0.0, 1024.0));
            MACHINE_PACE.put(kind, builder
                    .comment("How long a stroke of the " + kind.id + " takes, as a share of the base time (smaller is faster).")
                    .defineInRange(kind.id + "_pace", (double) kind.pace, 0.01, 100.0));
        }
        builder.pop();
        builder.push("cooking");
        ROAST_MIN = builder
                .comment("Spit Roast: the shortest a piece takes to cook, in ticks at the base speed.")
                .defineInRange("roast_min_ticks", 200, 1, 1000000);
        ROAST_MAX = builder
                .comment("The longest a piece takes.")
                .defineInRange("roast_max_ticks", 1200, 1, 1000000);
        ROAST_MAX_WHOLE = builder
                .comment("The longest a whole carcass takes.")
                .defineInRange("roast_max_whole_ticks", 4800, 1, 1000000);
        ROAST_PER_BLOCK = builder
                .comment("Ticks of cooking for each block of meat on the spit, between those.")
                .defineInRange("roast_ticks_per_block", 2400, 0, 1000000);
        builder.pop();
        builder.push("cybernetics");
        SPOOL_TICKS = builder
                .comment("Ticks for the throttle to spool from nothing to full.")
                .defineInRange("spool_ticks", 40, 1, 12000);
        FULL_DRAIN = builder
                .comment("Soul blood, mB a second, the throttle drinks held at full spool (it climbs with the cube of the spool).")
                .defineInRange("full_spool_drain", 150.0, 0.0, 100000.0);
        TAP = builder
                .comment("Soul blood, mB, a firing module costs when let go, however little it was spooled.")
                .defineInRange("fire_cost", 5, 0, 100000);
        for (com.avicagan.bloodandbones.cyber.Module module : com.avicagan.bloodandbones.cyber.Module.values()) {
            MODULE_UPKEEP.put(module, builder
                    .comment("Soul blood, mB a second, the " + module.getSerializedName() + " costs just to be fitted and working.")
                    .defineInRange(module.getSerializedName() + "_upkeep", module.defaultUpkeep(), 0, 100000));
        }
        builder.pop();
        builder.push("necrosis");
        NECROSIS_SWING = builder
                .comment("Necrosis (out of 100) an organic arm takes from each swing.")
                .defineInRange("per_swing", 1, 0, 100);
        NECROSIS_BLOCKS = builder
                .comment("Blocks an organic leg walks for each point of necrosis.")
                .defineInRange("blocks_per_point", 4.0, 0.01, 100000.0);
        NECROSIS_MEAL = builder
                .comment("Necrosis an organic stomach takes from each meal.")
                .defineInRange("per_meal", 3, 0, 100);
        PERFUSE_MB = builder
                .comment("Blood, mB, a perfusion takes from the tank.")
                .defineInRange("perfuse_mb", 1, 0, 100000);
        PERFUSE_POINTS = builder
                .comment("Necrosis a perfusion takes away.")
                .defineInRange("perfuse_points", 2, 0, 100);
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
        MINION_BLOCK_DAMAGE = builder
                .comment("Whether minions and trait blasts may break blocks (a self-destruct, trampling, a minion's fireballs), where the mobGriefing game rule also allows it.")
                .define("minion_block_damage", false);
        builder.pop();
        builder.push("traits");
        TRAIT_STRENGTH = builder
                .comment("How strong the traits of carcass armour and minions are: their amounts (attribute changes, damage changes, heals, pushes, damage dealt) are multiplied by this. 1 is normal, 0 takes the amounts away.")
                .defineInRange("trait_strength", 1.0, 0.0, 10.0);
        DISABLED_EFFECT_TYPES = builder
                .comment("Trait effect types that do nothing on this server, by id, for example \"bloodandbones:teleport\". Traits that use them keep their other effects.")
                .defineListAllowEmpty("disabled_effect_types", List.of(), () -> "bloodandbones:", o -> o instanceof String s && ResourceLocation.tryParse(s) != null);
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

    public static boolean minionBlockDamage() {
        try {
            return SPEC.isLoaded() && MINION_BLOCK_DAMAGE.get();
        } catch (IllegalStateException e) {
            return false;
        }
    }

    public static float traitStrength() {
        try {
            return SPEC.isLoaded() ? TRAIT_STRENGTH.get().floatValue() : 1.0F;
        } catch (IllegalStateException e) {
            return 1.0F;
        }
    }

    private static volatile List<? extends String> disabledRead;
    private static volatile Set<ResourceLocation> disabled = Set.of();

    /** The effect types switched off, read again only when the setting changes. */
    public static Set<ResourceLocation> disabledEffectTypes() {
        List<? extends String> now;
        try {
            now = SPEC.isLoaded() ? DISABLED_EFFECT_TYPES.get() : List.of();
        } catch (IllegalStateException e) {
            now = List.of();
        }
        if (now != disabledRead) {
            java.util.Set<ResourceLocation> out = new java.util.HashSet<>();
            for (String id : now) {
                ResourceLocation parsed = ResourceLocation.tryParse(id);
                if (parsed != null) {
                    out.add(parsed);
                }
            }
            disabled = Set.copyOf(out);
            disabledRead = now;
        }
        return disabled;
    }

    private static double get(ModConfigSpec.DoubleValue value, double fallback) {
        try {
            return SPEC.isLoaded() ? value.get() : fallback;
        } catch (IllegalStateException e) {
            return fallback;
        }
    }

    private static int get(ModConfigSpec.IntValue value, int fallback) {
        try {
            return SPEC.isLoaded() ? value.get() : fallback;
        } catch (IllegalStateException e) {
            return fallback;
        }
    }

    /** Freshness below which meat, offal and fat come out halved. */
    public static float goingOffBelow() {
        return (float) get(GOING_OFF_BELOW, 0.6);
    }

    /** Freshness below which a carcass is rotting. */
    public static float rottenBelow() {
        return (float) get(ROTTEN_BELOW, 0.3);
    }

    public static double dragLightMass() {
        return get(DRAG_LIGHT_MASS, 0.127);
    }

    public static double dragLightPenalty() {
        return get(DRAG_LIGHT_PENALTY, 0.05);
    }

    public static double dragHeavyMass() {
        return get(DRAG_HEAVY_MASS, 5.98);
    }

    public static double dragHeavyPenalty() {
        return get(DRAG_HEAVY_PENALTY, 0.55);
    }

    public static int cutsToSever() {
        return get(CUTS_TO_SEVER, 3);
    }

    public static int cutsToButcher() {
        return get(CUTS_TO_BUTCHER, 3);
    }

    public static int strokesToSkin() {
        return get(STROKES_TO_SKIN, 4);
    }

    public static double carryMass() {
        return get(CARRY_MASS, 0.13);
    }

    public static double machineStress(com.avicagan.bloodandbones.machine.MachineKind kind) {
        return get(MACHINE_STRESS.get(kind), kind.stress);
    }

    public static float machinePace(com.avicagan.bloodandbones.machine.MachineKind kind) {
        return (float) get(MACHINE_PACE.get(kind), kind.pace);
    }

    public static int roastMin() {
        return get(ROAST_MIN, 200);
    }

    public static int roastMax() {
        return get(ROAST_MAX, 1200);
    }

    public static int roastMaxWhole() {
        return get(ROAST_MAX_WHOLE, 4800);
    }

    public static int roastPerBlock() {
        return get(ROAST_PER_BLOCK, 2400);
    }

    public static int spoolTicks() {
        return get(SPOOL_TICKS, 40);
    }

    public static float fullSpoolDrain() {
        return (float) get(FULL_DRAIN, 150.0);
    }

    public static int fireCost() {
        return get(TAP, 5);
    }

    public static int moduleUpkeep(com.avicagan.bloodandbones.cyber.Module module) {
        return get(MODULE_UPKEEP.get(module), module.defaultUpkeep());
    }

    public static int necrosisPerSwing() {
        return get(NECROSIS_SWING, 1);
    }

    public static double necrosisBlocksPerPoint() {
        return get(NECROSIS_BLOCKS, 4.0);
    }

    public static int necrosisPerMeal() {
        return get(NECROSIS_MEAL, 3);
    }

    public static int perfuseMb() {
        return get(PERFUSE_MB, 1);
    }

    public static int perfusePoints() {
        return get(PERFUSE_POINTS, 2);
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
