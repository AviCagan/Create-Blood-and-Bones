package com.avicagan.bloodandbones.gametest;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.gametest.RigComparison.Numbers;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The physics measured: every scenario of the brief's physics goals on each of the twelve mobs in RigComparison's
 * lineup, then what twelve carcasses cost at once (criterion 8). Off unless {@code -Dbloodandbones.debug.rig_compare=true};
 * the numbers go to run/rig-comparison/results.csv as they come and to run/rig-comparison/summary.txt (the brief's bars,
 * then every number) when the server stops, one {@code [rigcompare]} line a scenario and mob to the log.
 * <p>
 * {@code -Dbloodandbones.debug.rig_compare_runs=N} plays every scenario N times over, {@code ..._mobs=cow,horse} only
 * those mobs, {@code ..._cost=false} leaves the cost out (it is measured once a server run whatever the runs).
 * <p>
 * A test here fails only when its scenario could not be played out (no carcass, a body missing, a number that is not
 * one), never on the numbers.
 */
@GameTestHolder(BloodAndBones.MOD_ID)
@PrefixGameTestTemplate(false)
public class RigComparisonTests {
    private static final String BATCH = "rig_compare";

    /** One scenario on one mob; it hands each of its results to the outcome it is given. */
    private interface Scenario {
        void run(GameTestHelper helper, EntityType<? extends Mob> type, Function<String, Consumer<Numbers>> result);
    }

    /** How one scenario is played: how many results it hands back, its time and its arena. */
    private record Play(int results, int ticks, String template, Scenario scenario) {
    }

    @GameTestGenerator
    public static Collection<TestFunction> rigComparison() {
        if (!RigComparison.enabled()) {
            return List.of();
        }
        Map<String, Play> plays = new LinkedHashMap<>();
        plays.put("1_ragdoll_4a_flank", new Play(2, 400, RigComparison.OPEN_GROUND,
                (h, type, r) -> RigScenarios.killedFromTheFlank(h, type, r.apply("1_ragdoll"), r.apply("4a_blow_flank"))));
        plays.put("4b_blow_behind", new Play(1, 300, RigComparison.OPEN_GROUND,
                (h, type, r) -> RigScenarios.killedFromBehind(h, type, r.apply("4b_blow_behind"))));
        plays.put("4c_blow_face", new Play(1, 300, RigComparison.OPEN_GROUND,
                (h, type, r) -> RigScenarios.killedInTheFace(h, type, r.apply("4c_blow_face"))));
        plays.put("8_rest", new Play(1, 800, RigComparison.OPEN_GROUND,
                (h, type, r) -> RigScenarios.comesToRest(h, type, r.apply("8_rest"))));
        plays.put("2a_legs_hang", new Play(1, 300, RigComparison.ARENA, (h, type, r) -> RigScenarios.heldUp(h, type, true, r.apply("2a_legs_hang"))));
        plays.put("2b_head_droops", new Play(1, 300, RigComparison.ARENA, (h, type, r) -> RigScenarios.heldUp(h, type, false, r.apply("2b_head_droops"))));
        plays.put("3a_hind_leg_hook", new Play(1, 300, RigComparison.ARENA, (h, type, r) -> RigScenarios.hooked(h, type, false, r.apply("3a_hind_leg_hook"))));
        plays.put("3b_head_hook", new Play(1, 300, RigComparison.ARENA, (h, type, r) -> RigScenarios.hooked(h, type, true, r.apply("3b_head_hook"))));
        plays.put("5_hanging", new Play(1, 400, RigComparison.ARENA, (h, type, r) -> RigScenarios.hanging(h, type, r.apply("5_hanging"))));
        plays.put("6_step", new Play(1, 400, RigComparison.ARENA, (h, type, r) -> RigScenarios.upAStep(h, type, false, r.apply("6_step"))));
        plays.put("6_step_by_leg", new Play(1, 400, RigComparison.ARENA, (h, type, r) -> RigScenarios.upAStep(h, type, true, r.apply("6_step_by_leg"))));
        plays.put("7_cut_limb", new Play(1, 300, RigComparison.ARENA, (h, type, r) -> RigScenarios.cutLimb(h, type, r.apply("7_cut_limb"))));

        List<TestFunction> out = new ArrayList<>();
        int runs = RigComparison.runs();
        for (int run = 1; run <= runs; run++) {
            int thisRun = runs == 1 ? 0 : run;
            for (EntityType<? extends Mob> type : RigComparison.lineup()) {
                for (Map.Entry<String, Play> entry : plays.entrySet()) {
                    Play play = entry.getValue();
                    // the step taken by the leg only for the two the brief names for it
                    if (entry.getKey().equals("6_step_by_leg") && type != EntityType.COW && type != EntityType.HORSE) {
                        continue;
                    }
                    String name = "rigcompare_" + entry.getKey() + "_" + RigComparison.mobName(type) + (thisRun > 0 ? "_" + thisRun : "");
                    out.add(new TestFunction(BATCH, name, play.template(), play.ticks(), 0L, true,
                            helper -> play.scenario().run(helper, type, outcome(helper, type, thisRun, play.results()))));
                }
            }
        }
        if (RigComparison.costIncluded()) {
            // criterion 8: a batch of twelve that is not timed, to warm the server up; then all twelve at once, and twelve
            // empty arenas to measure against, in two rounds, the second in the reverse order (GameTestServerMixin runs
            // them last, in RigComparison.COST_ORDER)
            for (EntityType<? extends Mob> type : RigComparison.LINEUP) {
                out.add(new TestFunction(RigComparison.COST_WARM_UP, "rigcost_0_warm_up_" + RigComparison.mobName(type), RigComparison.OPEN_GROUND,
                        RigComparison.COST_TICKS + 20, 0L, true, helper -> RigScenarios.cost(helper, type, 0, false, numbers -> helper.succeed())));
            }
            for (int round = 1; round <= 2; round++) {
                int thisRound = round;
                String batch = RigComparison.costBatch(RigComparison.COST_CARCASSES, round);
                for (EntityType<? extends Mob> type : RigComparison.LINEUP) {
                    out.add(new TestFunction(batch, "rigcost_" + round + "_" + RigComparison.mobName(type), RigComparison.OPEN_GROUND,
                            RigComparison.COST_TICKS + 20, 0L, true, helper -> RigScenarios.cost(helper, type, thisRound, type == EntityType.COW,
                            outcome(helper, type, thisRound, 1).apply("8_cost_" + thisRound))));
                }
                for (int i = 0; i < RigComparison.LINEUP.size(); i++) {
                    boolean timer = i == 0;
                    out.add(new TestFunction(RigComparison.costBatch(RigComparison.COST_BASELINE, round), "rigcost_" + round + "_empty_" + i,
                            RigComparison.OPEN_GROUND, RigComparison.COST_TICKS + 20, 0L, true,
                            helper -> RigScenarios.baseline(helper, thisRound, timer, helper::succeed)));
                }
            }
        }
        return out;
    }

    /**
     * Where a test's results go: each is recorded as it comes, and once all of them are in the test ends, failing only if
     * one of them could not be played out.
     */
    private static Function<String, Consumer<Numbers>> outcome(GameTestHelper helper, EntityType<? extends Mob> type, int run, int expected) {
        int[] left = {expected};
        String[] broken = {null};
        return scenario -> numbers -> {
            RigComparison.record(scenario, type, run, numbers);
            if (numbers.broken != null && broken[0] == null) {
                broken[0] = scenario + " could not be played out: " + numbers.broken;
            }
            if (--left[0] == 0) {
                if (broken[0] != null) {
                    helper.fail(broken[0]);
                } else {
                    helper.succeed();
                }
            }
        };
    }
}
