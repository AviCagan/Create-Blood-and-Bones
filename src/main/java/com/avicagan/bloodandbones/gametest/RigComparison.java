package com.avicagan.bloodandbones.gametest;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.carcass.CarcassAssembler;
import com.avicagan.bloodandbones.carcass.CarcassButchery;
import com.avicagan.bloodandbones.carcass.CarcassDrag;
import com.avicagan.bloodandbones.carcass.CarcassJoints;
import com.avicagan.bloodandbones.carcass.CarcassSavedData;
import com.avicagan.bloodandbones.carcass.ShackleHookBlock;
import com.avicagan.bloodandbones.carcass.ShackleHookBlockEntity;
import com.avicagan.bloodandbones.carcass.rig.Bone;
import com.avicagan.bloodandbones.carcass.rig.Rig;
import com.avicagan.bloodandbones.carcass.rig.RigManager;
import com.avicagan.bloodandbones.parts.PartSlot;
import com.avicagan.bloodandbones.parts.PartSlots;
import com.avicagan.bloodandbones.parts.PartsData;
import com.avicagan.bloodandbones.registry.BBBlocks;
import com.avicagan.bloodandbones.registry.BBItems;
import dev.ryanhcode.sable.api.physics.constraint.ConstraintJointAxis;
import dev.ryanhcode.sable.api.physics.constraint.GenericConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Measuring a carcass against the design brief's "Physics: what I actually want". Each scenario builds its own carcass
 * (or two) in its test's arena, plays it out and hands back its numbers; the numbers say how well it did, never whether
 * a test passes (that is PhysicsTests' job, with bars of its own). It was written to set this mod's rigs beside rigs
 * made the Sable Ragdolls way (docs/NEXT.md item 2); this copy keeps only this mod's own, so the same scenarios can say
 * how the physics did before and after a change (docs/ARCHITECTURE-PROPOSAL.md 15.21).
 * <p>
 * Directions are read off the torso: forward is where the model's -Z (its head end) points in the world, up where its
 * -Y points; a leg's down is where its own +Y points. "The rearmost leg" is the leg with the largest pivot z (left on a
 * tie), "the head" the bone in the head slot. A part is always hooked at the middle of its drawn box.
 * <p>
 * The arena is the "empty" template, 11 by 7 by 11, floor at y = 1, air from y = 2, walled with the tests' barriers. The
 * kills are played in the "open_ground" template, 31 across, so nothing stops a fall.
 */
public final class RigComparison {
    /** {@code -Dbloodandbones.debug.rig_compare=true} runs the whole comparison (RigComparisonTests) and writes its files. */
    public static final String PROPERTY = "bloodandbones.debug.rig_compare";
    /** {@code -Dbloodandbones.debug.rig_compare_runs=N}: every scenario N times over (default once), each a test of its own. */
    public static final String RUNS_PROPERTY = "bloodandbones.debug.rig_compare_runs";
    /** {@code -Dbloodandbones.debug.rig_compare_mobs=cow,horse}: only these of the lineup (default all twelve). */
    public static final String MOBS_PROPERTY = "bloodandbones.debug.rig_compare_mobs";
    /** {@code -Dbloodandbones.debug.rig_compare_cost=false}: leave out what twelve at once cost (default in). */
    public static final String COST_PROPERTY = "bloodandbones.debug.rig_compare_cost";
    /** The tests' own walled arena, and the open ground the kills are played on. */
    public static final String ARENA = BloodAndBones.MOD_ID + ":empty";
    public static final String OPEN_GROUND = BloodAndBones.MOD_ID + ":open_ground";
    /** The middle of the open ground, where a mob is killed. */
    public static final double OPEN_MIDDLE = 15.5;
    /** The rigs measured: this mod's own. */
    public static final String LABEL = "A";

    /** The twelve mobs the comparison runs every scenario on. */
    public static final List<EntityType<? extends Mob>> LINEUP = List.of(EntityType.COW, EntityType.HORSE, EntityType.WOLF, EntityType.CHICKEN,
            EntityType.SPIDER, EntityType.ZOMBIE, EntityType.VILLAGER, EntityType.LLAMA, EntityType.PIG, EntityType.SHEEP, EntityType.RABBIT,
            EntityType.POLAR_BEAR);
    /** Every body under this speed (blocks a second) and spin (radians a second) for {@link #SETTLE_RUN} ticks is settled. */
    static final double SETTLED_LINEAR = 0.05;
    static final double SETTLED_ANGULAR = 0.1;
    static final int SETTLE_RUN = 10;
    static final int SETTLE_CAP = 200;
    /** Top of the arena's floor. */
    static final int FLOOR = 2;

    private RigComparison() {
    }

    public static boolean enabled() {
        return Boolean.getBoolean(PROPERTY);
    }

    /** How many times over a comparison run plays every scenario. */
    public static int runs() {
        return Math.max(1, Integer.getInteger(RUNS_PROPERTY, 1));
    }

    /** The mobs a comparison run plays: the lineup, or those of it named by {@link #MOBS_PROPERTY}. */
    public static List<EntityType<? extends Mob>> lineup() {
        String only = System.getProperty(MOBS_PROPERTY, "").trim().toLowerCase(Locale.ROOT);
        if (only.isEmpty()) {
            return LINEUP;
        }
        List<String> names = List.of(only.split(",")).stream().map(String::trim).toList();
        return LINEUP.stream().filter(type -> names.contains(mobName(type))).toList();
    }

    /** Whether a comparison run also measures what twelve at once cost. */
    public static boolean costIncluded() {
        return !"false".equalsIgnoreCase(System.getProperty(COST_PROPERTY, "true"));
    }

    public static String mobName(EntityType<?> type) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath();
    }

    // ---------------------------------------------------------------- results

    /** What one scenario measured: the numbers in the order they were taken, with their units. */
    public static final class Numbers {
        final Map<String, Double> values = new LinkedHashMap<>();
        final Map<String, String> units = new LinkedHashMap<>();
        /** Why the scenario could not be played out, or null. */
        @Nullable
        String broken;

        public Numbers put(String metric, double value, String unit) {
            values.put(metric, value);
            units.put(metric, unit);
            if (!Double.isFinite(value) && broken == null) {
                broken = metric + " is not a number";
            }
            return this;
        }

        public double get(String metric) {
            return values.getOrDefault(metric, Double.NaN);
        }

        public Numbers broken(String why) {
            if (broken == null) {
                broken = why;
            }
            return this;
        }

        @Override
        public String toString() {
            StringBuilder out = new StringBuilder("{");
            values.forEach((k, v) -> out.append(out.length() > 1 ? ", " : "").append(k).append('=')
                    .append(v == Math.rint(v) && Math.abs(v) < 1.0e6 ? String.valueOf((long) (double) v) : String.format(Locale.ROOT, "%.3f", v)));
            if (broken != null) {
                out.append(out.length() > 1 ? ", " : "").append("broken=").append(broken.replace(' ', '_'));
            }
            return out.append('}').toString();
        }
    }

    /** One scenario's numbers for one mob, in one run of a comparison run. */
    private record Result(String scenario, String mob, int run, Numbers numbers) {
    }

    /** Every result so far, in the order they came. */
    private static final List<Result> RESULTS = java.util.Collections.synchronizedList(new ArrayList<>());

    /**
     * One scenario's numbers: kept, written to the comparison's file when it runs, and one line to the log:
     * {@code [rigcompare] <scenario> <mob> A={...}}.
     */
    public static void record(String scenario, EntityType<?> mob, int run, Numbers numbers) {
        record(scenario, mobName(mob), run, numbers);
    }

    /** The same, for numbers that belong to no one mob ("lineup": all twelve at once). */
    public static synchronized void record(String scenario, String mob, int run, Numbers numbers) {
        RESULTS.add(new Result(scenario, mob, run, numbers));
        if (enabled()) {
            List<String> rows = new ArrayList<>();
            numbers.values.forEach((metric, value) -> rows.add(String.join(",", scenario, mob, "generated", String.valueOf(run), metric,
                    String.format(Locale.ROOT, "%.6f", value), numbers.units.getOrDefault(metric, ""))));
            appendCsv(rows);
        }
        BloodAndBones.LOGGER.info("[rigcompare] {} {}{} {}={}", scenario, mob, run > 0 ? " run " + run : "", LABEL, numbers);
    }

    private static final Path OUT = Path.of("rig-comparison");

    // ---------------------------------------------------------------- criterion 8: what it costs

    /**
     * Criterion 8 is measured in batches that run last, one after another, with nothing beside them
     * (GameTestServerMixin): twelve carcasses at once, and twelve empty arenas to measure against. A batch that is not
     * timed goes first, to warm the server up. Then they run twice, the second time in the reverse order (carcasses,
     * empty, then empty, carcasses), so a server that slows down or speeds up as it goes weighs the same on each.
     */
    public static final String COST_PREFIX = "rig_cost";
    public static final String COST_BASELINE = "empty";
    public static final String COST_CARCASSES = "a";
    public static final String COST_WARM_UP = costBatch("warm_up", 0);
    /** How long each of their tests runs, ticks. */
    public static final int COST_TICKS = 600;
    /** The two rounds of cost batches that are timed, in the order they run. */
    public static final List<String> COST_TIMED = List.of(costBatch(COST_CARCASSES, 1), costBatch(COST_BASELINE, 1),
            costBatch(COST_BASELINE, 2), costBatch(COST_CARCASSES, 2));
    /** Every cost batch, in the order they run: the warm-up, then the two rounds. */
    public static final List<String> COST_ORDER = java.util.stream.Stream.concat(java.util.stream.Stream.of(COST_WARM_UP), COST_TIMED.stream()).toList();

    /** A cost batch's name: what is in it (the carcasses, or the empty arenas) and its round. */
    public static String costBatch(String what, int round) {
        return COST_PREFIX + "_" + round + "_" + what;
    }

    /** batch -> how long each server tick took while it ran, milliseconds, by the tests' own tick (0 to COST_TICKS). */
    private static final Map<String, double[]> COST = new ConcurrentHashMap<>();

    /**
     * How long the server tick before this one took, milliseconds. A test's tick runs inside the server's tick, before
     * that tick's time is written down, so the last one written is the one before.
     */
    static double lastTickMs(ServerLevel level) {
        var server = level.getServer();
        return server.getTickTimesNanos()[Math.floorMod(server.getTickCount() - 1, 100)] / 1.0e6;
    }

    /**
     * One batch's tick times, from the one test in it that reads them. Once every batch is in, the cost is the carcasses'
     * mean over both rounds against the empty batches' mean over both: while the twelve are falling and settling (ticks
     * 5 to 100), and once they should be lying at rest (ticks 400 to 500). Each round's own figures are kept too.
     */
    static synchronized void costTimes(String batch, double[] ms) {
        COST.put(batch, ms);
        if (!COST.keySet().containsAll(COST_TIMED)) {
            return;
        }
        Numbers n = new Numbers();
        double[] active = new double[3];
        double[] rest = new double[3];
        double[] baseActive = new double[3];
        double[] baseRest = new double[3];
        for (int round = 1; round <= 2; round++) {
            double[] base = COST.get(costBatch(COST_BASELINE, round));
            baseActive[round] = mean(base, 5, 100);
            baseRest[round] = mean(base, 400, 500);
            double[] times = COST.get(costBatch(COST_CARCASSES, round));
            active[round] = mean(times, 5, 100);
            rest[round] = mean(times, 400, 500);
        }
        n.put("active_ms", (active[1] + active[2]) / 2.0 - (baseActive[1] + baseActive[2]) / 2.0, "ms a tick")
                .put("rest_ms", (rest[1] + rest[2]) / 2.0 - (baseRest[1] + baseRest[2]) / 2.0, "ms a tick");
        for (int round = 1; round <= 2; round++) {
            n.put("active_ms_round" + round, active[round] - baseActive[round], "ms a tick")
                    .put("rest_ms_round" + round, rest[round] - baseRest[round], "ms a tick")
                    .put("active_tick_ms_round" + round, active[round], "ms a tick")
                    .put("rest_tick_ms_round" + round, rest[round], "ms a tick")
                    .put("empty_active_tick_ms_round" + round, baseActive[round], "ms a tick")
                    .put("empty_rest_tick_ms_round" + round, baseRest[round], "ms a tick");
        }
        record("8_cost", "lineup", 0, n);
    }

    private static double mean(double[] values, int from, int to) {
        double sum = 0.0;
        int n = 0;
        for (int i = from; i <= to && i < values.length; i++) {
            sum += values[i];
            n++;
        }
        return n == 0 ? Double.NaN : sum / n;
    }

    private static synchronized void appendCsv(List<String> rows) {
        try {
            Files.createDirectories(OUT);
            Path file = OUT.resolve("results.csv");
            if (!Files.exists(file)) {
                Files.writeString(file, "criterion,mob,source,run,metric,value,unit\n", StandardCharsets.UTF_8);
            }
            Files.write(file, rows, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        } catch (IOException e) {
            BloodAndBones.LOGGER.warn("[rigcompare] could not write the results: {}", e.toString());
        }
    }

    /**
     * One of the brief's bars, as the comparison reads it: in which scenario, on which number, and what meets it. A
     * result whose drag let go never meets a bar about the drag.
     */
    record Bar(String label, String scenario, String metric, java.util.function.DoublePredicate meets, boolean dragMustHold) {
        boolean met(Numbers n) {
            double v = n.get(metric);
            return Double.isFinite(v) && meets.test(v) && (!dragMustHold || n.get("drag_broke") == 0);
        }
    }

    /** The bars, in the brief's order. */
    static final List<Bar> BARS = List.of(
            new Bar("Moving within 8 ticks of the blow", "1_ragdoll", "motion_start_ticks", v -> v <= 8, false),
            new Bar("Joints within a quarter block", "1_ragdoll", "joint_gap_max", v -> v <= 0.25, false),
            new Bar("Not a quarter block into the floor, at rest", "1_ragdoll", "sink_rest", v -> v <= 0.25, false),
            new Bar("Not a quarter block into the floor, at any moment", "1_ragdoll", "sink_max", v -> v <= 0.25, false),
            new Bar("Settled within 200 ticks", "1_ragdoll", "settle_ticks", v -> v < SETTLE_CAP, false),
            new Bar("Flank: tilted 45 degrees or more at rest", "4a_blow_flank", "tilt_deg", v -> v >= 45.0, false),
            new Bar("Flank: leaning within 60 degrees of the blow's way", "4a_blow_flank", "tilt_dir_err_deg", v -> v <= 60.0, false),
            new Bar("Flank: on its side, away from the blow (both)", "4a_blow_flank", "down_away", v -> v == 1, false),
            new Bar("Behind: 20 degrees nose down at some moment", "4b_blow_behind", "pitch_peak_deg", v -> v >= 20.0, false),
            new Bar("Behind: 20 degrees nose down at rest", "4b_blow_behind", "pitch_rest_deg", v -> v >= 20.0, false),
            new Bar("Behind: head on the ground at rest", "4b_blow_behind", "head_ground_rest", v -> v == 1, false),
            new Bar("Held on its side: legs within 60 degrees of straight down", "2a_legs_hang", "leg_hang_deg", v -> v < 60.0, false),
            new Bar("Held upright: head droops 5 degrees or more", "2b_head_droops", "head_droop_deg", v -> v >= 5.0, false),
            new Bar("Hind-leg hook: rear first within 45 degrees", "3a_hind_leg_hook", "rear_first_deg", v -> v <= 45.0, true),
            new Bar("Head hook: head first within 45 degrees", "3b_head_hook", "head_first_deg", v -> v <= 45.0, true),
            new Bar("Hung: legs within 45 degrees of straight down", "5_hanging", "limb_hang_deg", v -> v <= 45.0, false),
            new Bar("Hung: swings 0.15 blocks or more when knocked", "5_hanging", "swing_amp", v -> v >= 0.15, false),
            new Bar("Step by the torso: up and over", "6_step", "cleared", v -> v == 1, true),
            new Bar("Step by a hind leg: up and over", "6_step_by_leg", "cleared", v -> v == 1, true),
            new Bar("Cut leg changes how it hangs, 5 degrees or more", "7_cut_limb", "tilt_change_deg", v -> v >= 5.0, false),
            new Bar("Folds into one body", "8_rest", "bodies_rest", v -> v == 1, false));

    private static double median(List<Double> values) {
        if (values.isEmpty()) {
            return Double.NaN;
        }
        List<Double> sorted = values.stream().sorted().toList();
        int n = sorted.size();
        return n % 2 == 1 ? sorted.get(n / 2) : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0;
    }

    private static String number(double v) {
        return !Double.isFinite(v) ? "?" : Math.abs(v) >= 100 ? String.format(Locale.ROOT, "%.0f", v)
                : Math.abs(v) >= 10 ? String.format(Locale.ROOT, "%.1f", v) : String.format(Locale.ROOT, "%.3f", v);
    }

    /**
     * Written when the server stops after a comparison run: how many mobs meet each bar in most runs (and how many runs
     * meet it of all), then each scenario, mob and number as its median over the runs, lowest to highest.
     */
    public static void writeSummary() {
        if (!enabled() || RESULTS.isEmpty()) {
            return;
        }
        List<Result> all = List.copyOf(RESULTS);
        List<String> lines = new ArrayList<>();
        lines.add("This mod's own rigs measured against the brief's physics. " + runs() + " run(s) of each scenario.");
        lines.add("");
        lines.add("The brief's bars: mobs meeting the bar in most runs / mobs (runs meeting it / runs), median of all runs");
        for (Bar bar : BARS) {
            StringBuilder line = new StringBuilder(String.format(Locale.ROOT, "%-58s", bar.label()));
            Map<String, List<Numbers>> byMob = new TreeMap<>();
            for (Result r : all) {
                if (r.scenario().equals(bar.scenario()) && r.numbers().broken == null) {
                    byMob.computeIfAbsent(r.mob(), m -> new ArrayList<>()).add(r.numbers());
                }
            }
            if (byMob.isEmpty()) {
                continue;
            }
            int mobsMet = 0;
            int met = 0;
            int runs = 0;
            List<Double> values = new ArrayList<>();
            for (List<Numbers> results : byMob.values()) {
                int hits = 0;
                for (Numbers n : results) {
                    hits += bar.met(n) ? 1 : 0;
                    values.add(n.get(bar.metric()));
                }
                met += hits;
                runs += results.size();
                mobsMet += hits * 2 > results.size() ? 1 : 0;
            }
            line.append(String.format(Locale.ROOT, "  %s %2d/%-2d (%3d/%-3d) %8s", LABEL, mobsMet, byMob.size(), met, runs, number(median(values))));
            lines.add(line.toString());
        }
        lines.add("");
        lines.add("Per scenario, mob and number: median over the runs [lowest .. highest]");
        Map<String, List<Numbers>> grouped = new TreeMap<>();
        for (Result r : all) {
            grouped.computeIfAbsent(r.scenario() + " " + r.mob(), k -> new ArrayList<>()).add(r.numbers());
        }
        grouped.forEach((key, list) -> {
            Set<String> metrics = new java.util.LinkedHashSet<>();
            list.forEach(n -> metrics.addAll(n.values.keySet()));
            for (String metric : metrics) {
                List<Double> values = list.stream().map(n -> n.get(metric)).filter(Double::isFinite).sorted().toList();
                lines.add(String.format(Locale.ROOT, "%-30s %-28s  %s=%s [%s .. %s]", key, metric, LABEL, number(median(values)),
                        values.isEmpty() ? "?" : number(values.get(0)), values.isEmpty() ? "?" : number(values.get(values.size() - 1))));
            }
            list.stream().filter(n -> n.broken != null).findFirst()
                    .ifPresent(n -> lines.add(String.format(Locale.ROOT, "%-30s could not be played out: %s", key, n.broken)));
        });
        try {
            Files.createDirectories(OUT);
            Files.write(OUT.resolve("summary.txt"), lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            BloodAndBones.LOGGER.warn("[rigcompare] could not write the summary: {}", e.toString());
        }
    }

    // ---------------------------------------------------------------- a carcass under test

    /** One carcass in one test: where its bodies are and which way it faces, read off its own rig. */
    static final class Subject {
        final GameTestHelper helper;
        final ServerLevel level;
        final EntityType<? extends Mob> type;
        CarcassSavedData.Carcass carcass;
        Rig rig;

        Subject(GameTestHelper helper, EntityType<? extends Mob> type) {
            this.helper = helper;
            this.level = helper.getLevel();
            this.type = type;
        }

        /** The carcass record, followed if a cut moved its torso into another record. */
        CarcassSavedData.Carcass carcass() {
            CarcassSavedData.Carcass now = CarcassSavedData.get(level).carcass(carcass.id);
            return now == null ? carcass : now;
        }

        Map<String, ServerSubLevel> bodies() {
            Map<String, ServerSubLevel> out = new LinkedHashMap<>();
            ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
            for (Map.Entry<String, UUID> e : carcass().bones.entrySet()) {
                if (container.getSubLevel(e.getValue()) instanceof ServerSubLevel body && !body.isRemoved()) {
                    out.put(e.getKey(), body);
                }
            }
            return out;
        }

        @Nullable
        ServerSubLevel body(String bone) {
            return bodies().get(bone);
        }

        String torsoBody() {
            return carcass().rootBone;
        }

        @Nullable
        ServerSubLevel torso() {
            return body(torsoBody());
        }

        Bone bone(String name) {
            return rig.bone(name).orElseThrow();
        }

        /** Model space to the world for one body: its orientation with its bone's own turn taken out. */
        Quaterniond modelToWorld(String bone, ServerSubLevel body) {
            return new Quaterniond(body.logicalPose().orientation()).mul(new Quaterniond(bone(bone).rotation()).invert());
        }

        Vector3d forward() {
            ServerSubLevel torso = torso();
            return torso == null ? new Vector3d(Double.NaN) : modelToWorld(torsoBody(), torso).transform(new Vector3d(0, 0, -1));
        }

        Vector3d up() {
            ServerSubLevel torso = torso();
            return torso == null ? new Vector3d(Double.NaN) : modelToWorld(torsoBody(), torso).transform(new Vector3d(0, -1, 0));
        }

        /**
         * Where the head end of the body points in the world: its forward (the model's -Z) for a four-legged body, its up
         * (the model's -Y) for one that stands upright (a biped, a chicken), whose head sits on top. Which it is comes from
         * where the head joins the torso, in the model: in front of its middle, or above it.
         */
        Vector3d headEnd() {
            ServerSubLevel torso = torso();
            String head = generatedHead(type);
            Bone headBone = head == null ? null : rig.bone(head).orElse(null);
            if (torso == null) {
                return new Vector3d(Double.NaN);
            }
            Bone torsoBone = bone(torsoBody());
            Vector3d middle = new Vector3d(torsoBone.boxMin()).add(new Vector3d(torsoBone.boxMax())).mul(0.5);
            new Quaterniond(torsoBone.rotation()).transform(middle).add(new Vector3d(torsoBone.offset()));
            boolean upright = headBone != null && Math.abs(headBone.offset().y - middle.y) > Math.abs(headBone.offset().z - middle.z);
            return modelToWorld(torsoBody(), torso).transform(upright ? new Vector3d(0, -1, 0) : new Vector3d(0, 0, -1));
        }

        /** Where the torso's own right (the model's -X) points in the world. */
        Vector3d right() {
            ServerSubLevel torso = torso();
            return torso == null ? new Vector3d(Double.NaN) : modelToWorld(torsoBody(), torso).transform(new Vector3d(-1, 0, 0));
        }

        /** Where a leg's own +Y (down its length, at rest) points in the world. */
        Vector3d down(String bone) {
            ServerSubLevel body = body(bone);
            return body == null ? new Vector3d(Double.NaN) : modelToWorld(bone, body).transform(new Vector3d(0, 1, 0));
        }

        Vector3d torsoCentre() {
            ServerSubLevel torso = torso();
            return torso == null ? new Vector3d(Double.NaN) : new Vector3d(torso.logicalPose().position());
        }

        /** The middle of a body's drawn box, in its plot: where a scenario hooks it. */
        @Nullable
        Vector3d grabPoint(String body) {
            ServerSubLevel sub = body(body);
            Bone bone = rig.bone(body).orElse(null);
            if (sub == null || bone == null) {
                return null;
            }
            Vector3d middle = new Vector3d(bone.boxMin().x + bone.boxMax().x, bone.boxMin().y + bone.boxMax().y, bone.boxMin().z + bone.boxMax().z).div(32.0);
            return middle.add(CarcassAssembler.boneOriginInPlot(sub, bone));
        }

        /** The middle of a body's drawn box, in the world. */
        @Nullable
        Vector3d middle(String body) {
            ServerSubLevel sub = body(body);
            Vector3d plot = grabPoint(body);
            return sub == null || plot == null ? null : sub.logicalPose().transformPosition(plot, new Vector3d());
        }

        /** Hook a body the way a player's Meat Hook does, at the middle of its box. */
        boolean hook(Player player, String body) {
            Vector3d point = grabPoint(body);
            ServerSubLevel sub = body(body);
            if (point == null || sub == null) {
                return false;
            }
            BlockPos cell = BlockPos.containing(point.x, point.y, point.z);
            if (!(level.getBlockEntity(cell) instanceof com.avicagan.bloodandbones.carcass.CarcassPartBlockEntity)) {
                cell = sub.getPlot().getCenterBlock();
            }
            return CarcassDrag.start(level, player, cell, new Vec3(point.x, point.y, point.z));
        }

        /** The legs of the rig, as bodies of this carcass. */
        List<String> legBodies() {
            List<String> out = new ArrayList<>();
            for (String leg : generatedLegs(type)) {
                if (!out.contains(leg) && !leg.equals(torsoBody()) && body(leg) != null) {
                    out.add(leg);
                }
            }
            return out;
        }

        boolean still() {
            var physics = SubLevelContainer.getContainer(level).physicsSystem();
            for (ServerSubLevel body : bodies().values()) {
                RigidBodyHandle handle = physics.getPhysicsHandle(body);
                if (handle.getLinearVelocity(new Vector3d()).length() > SETTLED_LINEAR || handle.getAngularVelocity(new Vector3d()).length() > SETTLED_ANGULAR) {
                    return false;
                }
            }
            return true;
        }

        double torsoSpeed() {
            ServerSubLevel torso = torso();
            if (torso == null) {
                return Double.NaN;
            }
            return SubLevelContainer.getContainer(level).physicsSystem().getPhysicsHandle(torso).getLinearVelocity(new Vector3d()).length();
        }

        /** How far the two ends of the worst joint have come apart, in blocks. */
        double jointGap() {
            Map<String, ServerSubLevel> bodies = bodies();
            double worst = 0.0;
            for (CarcassJoints.Spec spec : carcass().joints) {
                ServerSubLevel parent = bodies.get(spec.parent());
                ServerSubLevel child = bodies.get(spec.child());
                if (parent == null || child == null) {
                    continue;
                }
                Vector3d a = parent.logicalPose().transformPosition(spec.anchorParent(parent), new Vector3d());
                Vector3d b = child.logicalPose().transformPosition(spec.anchorChild(child), new Vector3d());
                worst = Math.max(worst, a.distance(b));
            }
            return worst;
        }

        /** The mean, over the joints, of how far each has turned from the pose it was made in, degrees. */
        double poseChange() {
            Map<String, ServerSubLevel> bodies = bodies();
            double sum = 0.0;
            int n = 0;
            for (CarcassJoints.Spec spec : carcass().joints) {
                ServerSubLevel parent = bodies.get(spec.parent());
                ServerSubLevel child = bodies.get(spec.child());
                if (parent == null || child == null) {
                    continue;
                }
                Quaterniond now = new Quaterniond(parent.logicalPose().orientation()).invert().mul(child.logicalPose().orientation());
                sum += angleBetween(now, spec.frame1());
                n++;
            }
            return n == 0 ? 0.0 : sum / n;
        }

        /** How far one bone has turned from its pose relative to its parent, degrees; NaN if it has no joint. */
        double poseChange(String bone) {
            Map<String, ServerSubLevel> bodies = bodies();
            for (CarcassJoints.Spec spec : carcass().joints) {
                if (!spec.child().equals(bone)) {
                    continue;
                }
                ServerSubLevel parent = bodies.get(spec.parent());
                ServerSubLevel child = bodies.get(spec.child());
                if (parent == null || child == null) {
                    return Double.NaN;
                }
                Quaterniond now = new Quaterniond(parent.logicalPose().orientation()).invert().mul(child.logicalPose().orientation());
                return angleBetween(now, spec.frame1());
            }
            return Double.NaN;
        }

        /** The lowest corner of any body's drawn box below the floor's top, blocks (0 when none is below it). */
        double sink() {
            double floor = helper.absolutePos(new BlockPos(0, FLOOR, 0)).getY();
            double worst = 0.0;
            for (Map.Entry<String, ServerSubLevel> e : bodies().entrySet()) {
                Bone bone = rig.bone(e.getKey()).orElse(null);
                if (bone == null) {
                    continue;
                }
                Vector3d origin = CarcassAssembler.boneOriginInPlot(e.getValue(), bone);
                for (int i = 0; i < 8; i++) {
                    Vector3d corner = new Vector3d((i & 1) == 0 ? bone.boxMin().x : bone.boxMax().x, (i & 2) == 0 ? bone.boxMin().y : bone.boxMax().y,
                            (i & 4) == 0 ? bone.boxMin().z : bone.boxMax().z).div(16.0).add(origin);
                    worst = Math.max(worst, floor - e.getValue().logicalPose().transformPosition(corner).y);
                }
            }
            return worst;
        }

        /** The mass of every live body, Sable units. */
        double liveMass() {
            double mass = 0.0;
            for (ServerSubLevel body : bodies().values()) {
                mass += body.getMassTracker().getMass();
            }
            return mass;
        }

        void wake() {
            var pipeline = SubLevelContainer.getContainer(level).physicsSystem().getPipeline();
            for (ServerSubLevel body : bodies().values()) {
                pipeline.wakeUp(body);
            }
        }
    }

    static double angleDeg(Vector3d a, Vector3d b) {
        double la = a.length();
        double lb = b.length();
        if (la < 1.0e-9 || lb < 1.0e-9) {
            return Double.NaN;
        }
        return Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, a.dot(b) / (la * lb)))));
    }

    static double angleBetween(Quaterniond a, Quaterniond b) {
        double dot = Math.abs(a.x * b.x + a.y * b.y + a.z * b.z + a.w * b.w) / (Math.sqrt(a.lengthSquared() * b.lengthSquared()));
        return Math.toDegrees(2.0 * Math.acos(Math.max(-1.0, Math.min(1.0, dot))));
    }

    /** The rig's leg bones for a mob, rearmost first (largest pivot z; the left one first of a pair). */
    static List<String> generatedLegs(EntityType<?> type) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        Rig rig = RigManager.forEntity(id).orElseThrow();
        List<Bone> legs = new ArrayList<>();
        for (Bone bone : rig.bones()) {
            if (PartSlots.of(PartsData.SERVER, id, rig, bone.name()).slot() == PartSlot.LEG) {
                legs.add(bone);
            }
        }
        legs.sort((a, b) -> {
            int byZ = Float.compare(b.offset().z, a.offset().z);
            if (byZ != 0) {
                return byZ;
            }
            return Boolean.compare(!a.name().contains("left"), !b.name().contains("left"));
        });
        return legs.stream().map(Bone::name).toList();
    }

    /** The rig's head bone for a mob, or null. */
    @Nullable
    static String generatedHead(EntityType<?> type) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        Rig rig = RigManager.forEntity(id).orElseThrow();
        for (Bone bone : rig.bones()) {
            if (PartSlots.of(PartsData.SERVER, id, rig, bone.name()).slot() == PartSlot.HEAD) {
                return bone.name();
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- making the carcass

    /** A grown mob standing still at a point of the arena, facing a way (0 south, -90 east). */
    static Mob standing(GameTestHelper helper, EntityType<? extends Mob> type, Vec3 at, float yaw) {
        Mob mob = helper.spawn(type, BlockPos.containing(at));
        mob.setBaby(false);
        mob.setNoAi(true);
        mob.moveTo(helper.absoluteVec(at).x, helper.absoluteVec(at).y, helper.absoluteVec(at).z, yaw, 0.0F);
        mob.setYRot(yaw);
        mob.yBodyRot = yaw;
        mob.yBodyRotO = yaw;
        mob.setYHeadRot(yaw);
        mob.yHeadRotO = yaw;
        return mob;
    }

    /** "Assembled": built where it stands, the mob gone, no blow. */
    @Nullable
    static Subject assembled(GameTestHelper helper, EntityType<? extends Mob> type, Vec3 at, float yaw) {
        Mob mob = standing(helper, type, at, yaw);
        Subject s = new Subject(helper, type);
        s.carcass = CarcassAssembler.assemble(mob, null, true);
        mob.discard();
        if (s.carcass == null) {
            return null;
        }
        s.rig = RigManager.forCarcass(s.carcass).orElse(null);
        return s.rig == null ? null : s;
    }

    /**
     * "Killed": a stand-in player holding a Meat Hook, standing where given and looking at the mob's middle, lands a
     * lethal hit through the game's own death. The carcass is handed on once the death has made it, before its blow
     * (which comes as the dead mob goes, a few ticks on).
     */
    static void killed(GameTestHelper helper, EntityType<? extends Mob> type, Vec3 at, float yaw, Vec3 attackerAt, Consumer<Subject> then) {
        Mob mob = standing(helper, type, at, yaw);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BBItems.MEAT_HOOK.get()));
        Vec3 from = helper.absoluteVec(attackerAt);
        player.setPos(from);
        Vec3 target = mob.getBoundingBox().getCenter();
        lookAt(player, target);
        Set<UUID> before = new java.util.HashSet<>();
        CarcassSavedData.get(helper.getLevel()).all().forEach(c -> before.add(c.id));
        mob.hurt(helper.getLevel().damageSources().playerAttack(player), 10000.0F);
        Subject s = new Subject(helper, type);
        for (CarcassSavedData.Carcass c : CarcassSavedData.get(helper.getLevel()).all()) {
            if (!before.contains(c.id) && c.entity.equals(BuiltInRegistries.ENTITY_TYPE.getKey(type))) {
                s.carcass = c;
            }
        }
        if (s.carcass == null) {
            helper.fail("The " + mobName(type) + " did not become a carcass");
            return;
        }
        s.rig = RigManager.forCarcass(s.carcass).orElse(null);
        if (s.rig == null) {
            helper.fail("The " + mobName(type) + " carcass has no rig");
            return;
        }
        then.accept(s);
    }

    static void lookAt(Player player, Vec3 target) {
        Vec3 d = target.subtract(player.getEyePosition());
        float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z)));
        player.setYRot(yaw);
        player.setXRot(pitch);
        player.setYHeadRot(yaw);
        player.yRotO = yaw;
        player.xRotO = pitch;
    }

    /** Turn every body of the carcass about a world point. */
    static void turn(Subject s, Vector3d about, Quaterniond rotation) {
        var pipeline = SubLevelContainer.getContainer(s.level).physicsSystem().getPipeline();
        for (ServerSubLevel body : s.bodies().values()) {
            Pose3d pose = body.logicalPose();
            Vector3d position = new Vector3d(pose.position()).sub(about);
            rotation.transform(position).add(about);
            Quaterniond orientation = new Quaterniond(rotation).mul(pose.orientation());
            pose.position().set(position);
            pose.orientation().set(orientation);
            pipeline.teleport(body, pose.position(), pose.orientation());
            pipeline.resetVelocity(body);
            body.updateLastPose();
        }
    }

    /** Pin the torso where it is with a world joint that locks all six axes, as a resting carcass is pinned. */
    @Nullable
    static PhysicsConstraintHandle pin(Subject s) {
        ServerSubLevel torso = s.torso();
        if (torso == null) {
            return null;
        }
        BlockPos center = torso.getPlot().getCenterBlock();
        Vector3d plotPoint = new Vector3d(center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5);
        Pose3d pose = torso.logicalPose();
        Vector3d worldPoint = pose.transformPosition(plotPoint, new Vector3d());
        GenericConstraintConfiguration config = new GenericConstraintConfiguration(worldPoint, plotPoint, new Quaterniond(pose.orientation()), new Quaterniond(),
                EnumSet.allOf(ConstraintJointAxis.class));
        return SubLevelContainer.getContainer(s.level).physicsSystem().getPipeline().addConstraint(null, torso, config);
    }

    /** A Shackle Hook facing down under a stone at the arena's ceiling. */
    static BlockPos hook(GameTestHelper helper, BlockPos hookAt) {
        helper.setBlock(hookAt.above(), Blocks.STONE);
        helper.setBlock(hookAt, BBBlocks.SHACKLE_HOOK.get().defaultBlockState().setValue(ShackleHookBlock.FACING, Direction.UP));
        return hookAt;
    }

    /** Hang a carcass on a hook the way a player does: take hold of a body with the Meat Hook, then use it on the hook. */
    static boolean hang(Subject s, BlockPos hookAt) {
        Player player = s.helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BBItems.MEAT_HOOK.get()));
        player.setPos(s.helper.absoluteVec(Vec3.atBottomCenterOf(hookAt.below(3))));
        player.setOldPosAndRot();
        if (!s.hook(player, s.torsoBody())) {
            return false;
        }
        if (!(s.level.getBlockEntity(s.helper.absolutePos(hookAt)) instanceof ShackleHookBlockEntity hook)) {
            CarcassDrag.stop(s.level, player);
            return false;
        }
        hook.toggle(s.level, player);
        return hook.isOccupied();
    }

    /** How fast a knock on a hung carcass would set the whole of it moving, as its rig weighs it, blocks a second. */
    static final double KNOCK_SPEED = 1.5;

    /**
     * Knock a hung carcass along a way: a blow to its torso of the mob's weight as its rig has it times
     * {@link #KNOCK_SPEED}, so the torso's change of speed is that impulse over the torso's own mass. The same blow
     * whatever the code under it, so a number taken before a change and one after compare the carcass, not the knock.
     * Returns {impulse, the torso's change of speed}.
     */
    static double[] knock(Subject s, Vector3d way) {
        ServerSubLevel torso = s.torso();
        double weight = RigManager.forEntity(s.carcass().entity, s.carcass().baby).map(r -> (double) r.weight()).orElse(Double.NaN);
        if (torso == null || !Double.isFinite(weight)) {
            return new double[]{Double.NaN, Double.NaN};
        }
        double impulse = KNOCK_SPEED * weight;
        double speed = impulse / Math.max(1.0e-6, torso.getMassTracker().getMass());
        Vector3d velocity = new Vector3d(way).normalize().mul(speed);
        SubLevelContainer.getContainer(s.level).physicsSystem().getPhysicsHandle(torso).addLinearAndAngularVelocity(velocity, new Vector3d());
        s.wake();
        return new double[]{impulse, speed};
    }

    /** Whether the hook holds the carcass where the way to its head meets the body it hangs from. */
    static boolean hungByTheNeck(Subject s, BlockPos hookAt) {
        if (!(s.level.getBlockEntity(s.helper.absolutePos(hookAt)) instanceof ShackleHookBlockEntity hook) || !hook.isOccupied()) {
            return false;
        }
        ServerSubLevel held = s.body(hook.hookedBone());
        CarcassJoints.Spec toward = ShackleHookBlockEntity.jointTowardHead(s.carcass());
        return held != null && toward != null && toward.anchorParent(held).distance(hook.hookedAnchor()) < 1.0e-6;
    }

    /**
     * Take a limb off for good: the cuts through its joint, then its body gone. Returns its live mass. The piece is found by
     * its own body, not by its name: tests share one world, and another test's severed leg of the same name may lie about.
     */
    static double cutOff(Subject s, String body) {
        ServerSubLevel limb = s.body(body);
        double mass = limb == null ? 0.0 : limb.getMassTracker().getMass();
        UUID limbId = limb == null ? null : limb.getUniqueId();
        CarcassSavedData.Carcass carcass = s.carcass();
        for (int i = 0; i < CarcassButchery.CUTS_TO_SEVER && CarcassButchery.isAttached(carcass, body); i++) {
            CarcassButchery.cut(s.level, null, carcass, body, null);
        }
        if (limbId != null && SubLevelContainer.getContainer(s.level).getSubLevel(limbId) instanceof ServerSubLevel gone && !gone.isRemoved()) {
            SubLevelContainer.getContainer(s.level).removeSubLevel(gone, SubLevelRemovalReason.REMOVED);
        }
        return mass;
    }
}
