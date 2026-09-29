package com.avicagan.bloodandbones.gametest;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.body.Surgery;
import com.avicagan.bloodandbones.carcass.CarcassLook;
import com.avicagan.bloodandbones.carcass.rig.Rig;
import com.avicagan.bloodandbones.carcass.rig.RigManager;
import com.avicagan.bloodandbones.minion.MinionBuild;
import com.avicagan.bloodandbones.minion.MinionData;
import com.avicagan.bloodandbones.minion.MinionDisposition;
import com.avicagan.bloodandbones.minion.MinionEntity;
import com.avicagan.bloodandbones.minion.MinionFitness;
import com.avicagan.bloodandbones.minion.MinionStats;
import com.avicagan.bloodandbones.minion.MinionTask;
import com.avicagan.bloodandbones.minion.TaskWords;
import com.avicagan.bloodandbones.minion.PieceRef;
import com.avicagan.bloodandbones.parts.CarcassArmour;
import com.avicagan.bloodandbones.parts.MobGroup;
import com.avicagan.bloodandbones.parts.PartsData;
import com.avicagan.bloodandbones.registry.BBItems;
import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Tasks instead of jobs, stage A (docs/NEXT.md 1.9, "worked out without a world"): every minion's fitness at every task,
 * from its build, its data, what it holds and the time of day ({@link MinionFitness}), with nothing in play yet reading it.
 * The worked examples of docs/NEXT.md 1.2 are worked out here again by hand, factor by factor, from the data they read.
 */
@GameTestHolder(BloodAndBones.MOD_ID)
@PrefixGameTestTemplate(false)
public class MinionFitnessTests {
    private static ResourceLocation mob(String name) {
        return ResourceLocation.withDefaultNamespace(name);
    }

    private static ResourceLocation bb(String name) {
        return BloodAndBones.asResource(name);
    }

    private static PieceRef ref(String entity, String bone) {
        return ref(entity, bone, Map.of());
    }

    private static PieceRef ref(String entity, String bone, Map<String, String> traits) {
        return ref(ResourceLocation.withDefaultNamespace(entity), bone, traits);
    }

    private static PieceRef ref(ResourceLocation entity, String bone, Map<String, String> traits) {
        return new PieceRef(entity, bone, ResourceLocation.withDefaultNamespace("textures/entity/" + entity.getPath() + ".png"), List.of(), 1.0F, false,
                traits, false);
    }

    private static PieceRef villagerHead(String profession) {
        return ref("villager", "head", Map.of("profession", profession));
    }

    /** A zombie's torso, arms and legs under this head. */
    private static MinionBuild armed(PieceRef head) {
        return MinionBuild.of(ref("zombie", "body")).with("head", head).with("right_arm", ref("zombie", "right_arm")).with("left_arm", ref("zombie", "left_arm"))
                .with("right_leg", ref("zombie", "right_leg")).with("left_leg", ref("zombie", "left_leg"));
    }

    /** A cow torso and head on four legs of this mob, the front ones and the hind ones by these bones. */
    private static MinionBuild cowOn(String legs, String front, String hind, PieceRef head) {
        return MinionBuild.of(ref("cow", "body")).with("head", head)
                .with("right_front_leg", ref(legs, "right_" + front)).with("left_front_leg", ref(legs, "left_" + front))
                .with("right_hind_leg", ref(legs, "right_" + hind)).with("left_hind_leg", ref(legs, "left_" + hind));
    }

    /** The spec's spider (6.5): its torso with a zombie's arm in each of its eight leg sockets, under this head. */
    private static MinionBuild spiderOfArms(PieceRef head, int arms) {
        MinionBuild build = MinionBuild.of(ref("spider", "body1")).with("head", head);
        String[] sockets = {"right_front_leg", "left_front_leg", "right_middle_front_leg", "left_middle_front_leg", "right_middle_hind_leg",
                "left_middle_hind_leg", "right_hind_leg", "left_hind_leg"};
        for (int i = 0; i < arms; i++) {
            build = build.with(sockets[i], ref("zombie", i % 2 == 0 ? "right_arm" : "left_arm"));
        }
        return build;
    }

    private static Map<MinionTask, MinionFitness.Row> rows(MinionBuild build, MinionFitness.Context context) {
        Map<MinionTask, MinionFitness.Row> out = new EnumMap<>(MinionTask.class);
        MinionStats stats = MinionStats.of(PartsData.SERVER, build);
        for (MinionFitness.Row row : MinionFitness.rows(PartsData.SERVER, build, stats, context)) {
            out.put(row.task(), row);
        }
        return out;
    }

    private static Map<MinionTask, MinionFitness.Row> rows(MinionBuild build) {
        return rows(build, MinionFitness.Context.NONE);
    }

    /** A row as a line for a failure: its fitness, why it cannot or what it waits for, and each factor and where it came from. */
    static String describe(MinionFitness.Row row) {
        StringBuilder out = new StringBuilder(row.task().name()).append(' ');
        out.append(row.cannot().map(c -> "cannot (" + c + ")").orElse(Math.round(row.fitness() * 1000.0F) / 10.0F + "% raw " + row.raw()));
        row.waitsFor().ifPresent(w -> out.append(", ").append(w));
        row.withTool().ifPresent(w -> out.append(", with its tool ").append(w));
        row.main().ifPresent(f -> out.append("; ").append(f.of()).append(' ').append(f.stat()).append('=').append(f.value()).append(f.from()));
        row.second().ifPresent(f -> out.append("; ").append(f.of()).append(' ').append(f.stat()).append('=').append(f.value()).append(f.from()));
        out.append("; knack ").append(row.knack()).append(row.knackFrom()).append("; ").append(row.dispositionName()).append(' ').append(row.disposition());
        return out.toString();
    }

    /** Whether two numbers agree to within 1% of the second. */
    private static boolean near(float got, double want) {
        return Math.abs(got - want) <= 0.01 * Math.abs(want) + 1.0E-4;
    }

    private static double held(double raw) {
        return Math.max(MinionFitness.LEAST, Math.min(MinionFitness.MOST, raw));
    }

    /**
     * Each worked example of docs/NEXT.md 1.2, every task it can do worked out here by hand from the data it reads, equals
     * what {@code MinionFitness} gives to within 1%; the tasks it cannot do are those the example says. The numbers read:
     * a cow's torso is 15 health (a grazer's health half again) in 9 slots, its rig weight 0.8086, and its Beast of Burden
     * takes 15% off a drag's slowdown; rabbit legs 0.3 in front and 0.35 behind, their front ones paws, and the rabbit's
     * hide it keeps is Swift (5% faster); a cow's, wolf's and spider's heads see 16 blocks, a zombie's and villager's past
     * 32; a zombie's arm strikes for 2.5 and its legs walk at 0.23; a spider's torso on no legs slithers at 0.2; a wolf's
     * legs walk at 0.3, each Swift, summed over four to 20% faster, and its bite is 1.5. (docs/NEXT.md 1.2 had left the
     * traits out: its numbers are these now.)
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void fitnessMatchesItsFormula(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        double cowPull = Math.sqrt(0.80859375 / 0.8) / (1.0 - 0.15);
        // the design's cow on four rabbit legs with a cow's head (docile: fights 0.75, tends 1.25; grazer: herder and courier 1.25)
        double pace = 0.325 * 1.05 / 0.25;
        check(problems, "cow on rabbit legs", cowOn("rabbit", "front_leg", "haunch", ref("cow", "head")), Map.ofEntries(
                Map.entry(MinionTask.GUARD, 0.4 * Math.sqrt(0.75) * 0.75),
                Map.entry(MinionTask.SENTRY, 0.25 * 1.0 * 0.75),
                Map.entry(MinionTask.HUNTER, 0.4 * Math.sqrt(pace) * 0.75),
                Map.entry(MinionTask.MEDIC, 0.75 * 1.0 * 1.25),
                Map.entry(MinionTask.HERDER, pace * 1.0 * 1.25 * 1.25),
                Map.entry(MinionTask.TENDER, 1.0 * Math.sqrt(pace) * 1.25),
                Map.entry(MinionTask.COURIER, 1.0 * Math.sqrt(pace) * 1.25),
                Map.entry(MinionTask.HAULER, cowPull * Math.sqrt(pace)),
                Map.entry(MinionTask.FARMER, 0.75),
                Map.entry(MinionTask.FISHER, 0.6),
                Map.entry(MinionTask.BUTCHER, 0.35 * Math.sqrt(0.4)),
                Map.entry(MinionTask.BARTERER, 0.75),
                // a cow's mouth noses the ground out as well as any
                Map.entry(MinionTask.DIGGER, 1.0)), Set.of(MinionTask.SURGEON, MinionTask.SAPPER));
        // the spec's spider of eight zombie arms under a farmer's head: hands 1.6, its blows 60% more often; meek (fights 0.5,
        // tends 1.25); villager: surgeon 1.5, farmer 1.5, guard 1, the biped's courier 1.25; no legs, so it slithers at 0.2;
        // the spider's torso 16 health in 4 slots, rig weight 0.537
        double spiderCarry = 4.0 / 9.0;
        double crawl = 0.2 / 0.25;
        double spiderPull = Math.sqrt(0.5371094 / 0.8);
        check(problems, "spider of arms", spiderOfArms(villagerHead("farmer"), 8), Map.ofEntries(
                Map.entry(MinionTask.GUARD, 1.6 * Math.sqrt(0.8) * 0.5),
                Map.entry(MinionTask.SENTRY, 0.25 * Math.sqrt(2.0) * 0.5),
                Map.entry(MinionTask.HUNTER, 1.6 * Math.sqrt(crawl) * 0.5),
                Map.entry(MinionTask.SURGEON, 1.6 * Math.sqrt(2.0) * 1.5 * 1.25),
                Map.entry(MinionTask.MEDIC, 1.6 * Math.sqrt(2.0) * 1.25),
                Map.entry(MinionTask.HERDER, crawl * Math.sqrt(2.0) * 1.25),
                Map.entry(MinionTask.TENDER, spiderCarry * Math.sqrt(crawl) * 1.25),
                Map.entry(MinionTask.COURIER, spiderCarry * Math.sqrt(crawl) * 1.25),
                Map.entry(MinionTask.HAULER, spiderPull * Math.sqrt(crawl)),
                Map.entry(MinionTask.FARMER, 1.6 * Math.sqrt(2.0) * 1.5),
                Map.entry(MinionTask.FISHER, 0.35 * 1.6 * Math.sqrt(2.0)),
                Map.entry(MinionTask.BUTCHER, 1.6 * Math.sqrt(1.6)),
                Map.entry(MinionTask.BARTERER, 1.6),
                Map.entry(MinionTask.DIGGER, 1.0)), Set.of(MinionTask.SAPPER));
        // all zombie: loyal; humanoid: guard and courier 1.25; 20 health in 3 slots, rig weight 0.406, legs 0.23
        double zombiePace = 0.23 / 0.25;
        double zombieCarry = 3.0 / 9.0;
        double zombiePull = Math.sqrt(0.40625 / 0.8);
        check(problems, "all zombie", armed(ref("zombie", "head")), Map.ofEntries(
                Map.entry(MinionTask.GUARD, 1.0 * 1.0 * 1.25),
                Map.entry(MinionTask.SENTRY, 0.25 * Math.sqrt(2.0)),
                Map.entry(MinionTask.HUNTER, 1.0 * Math.sqrt(zombiePace)),
                Map.entry(MinionTask.SURGEON, 1.0 * Math.sqrt(2.0)),
                Map.entry(MinionTask.MEDIC, 1.0 * Math.sqrt(2.0)),
                Map.entry(MinionTask.HERDER, zombiePace * Math.sqrt(2.0)),
                Map.entry(MinionTask.TENDER, zombieCarry * Math.sqrt(zombiePace)),
                Map.entry(MinionTask.COURIER, zombieCarry * Math.sqrt(zombiePace) * 1.25),
                Map.entry(MinionTask.HAULER, zombiePull * Math.sqrt(zombiePace)),
                Map.entry(MinionTask.FARMER, Math.sqrt(2.0)),
                Map.entry(MinionTask.FISHER, 0.35 * Math.sqrt(2.0)),
                Map.entry(MinionTask.BUTCHER, 1.0),
                Map.entry(MinionTask.BARTERER, 1.0),
                Map.entry(MinionTask.DIGGER, 1.0)), Set.of(MinionTask.SAPPER));
        MinionFitness.Row withMe = MinionFitness.of(PartsData.SERVER, armed(ref("zombie", "head")), MinionStats.of(PartsData.SERVER, armed(ref("zombie", "head"))),
                MinionFitness.Context.NONE.at(MinionTask.Anchor.MAKER), MinionTask.GUARD);
        if (!near(withMe.fitness(), 1.25 * 1.25)) {
            problems.add("all zombie: Guard with me should be loyal's 156%: " + describe(withMe));
        }
        // a cow on wolf legs with a wolf's head: brave (fights 1.25); canid: hunter and herder 1.5, guard 1.25, the quadruped's
        // courier 1.25; its front legs paws
        double wolfPace = 0.3 * 1.2 / 0.25;
        check(problems, "cow on wolf legs", cowOn("wolf", "front_leg", "hind_leg", ref("wolf", "head/real_head")), Map.ofEntries(
                Map.entry(MinionTask.GUARD, 0.6 * Math.sqrt(0.75) * 1.25 * 1.25),
                Map.entry(MinionTask.SENTRY, 0.25 * 1.25),
                Map.entry(MinionTask.HUNTER, 0.6 * Math.sqrt(wolfPace) * 1.5 * 1.25),
                Map.entry(MinionTask.MEDIC, 0.75),
                Map.entry(MinionTask.HERDER, wolfPace * 1.5),
                Map.entry(MinionTask.TENDER, Math.sqrt(wolfPace)),
                Map.entry(MinionTask.COURIER, Math.sqrt(wolfPace) * 1.25),
                Map.entry(MinionTask.HAULER, cowPull * Math.sqrt(wolfPace)),
                Map.entry(MinionTask.FARMER, 0.75),
                Map.entry(MinionTask.FISHER, 0.6),
                Map.entry(MinionTask.BUTCHER, 0.35 * Math.sqrt(0.6)),
                Map.entry(MinionTask.BARTERER, 0.75),
                Map.entry(MinionTask.DIGGER, 1.0)), Set.of(MinionTask.SURGEON, MinionTask.SAPPER));
        if (!problems.isEmpty()) {
            helper.fail(String.join("\n", problems));
            return;
        }
        helper.succeed();
    }

    /** Every rated task of this build is the fitness worked out, held; the ones named cannot be done, and only those. */
    private static void check(List<String> problems, String name, MinionBuild build, Map<MinionTask, Double> worked, Set<MinionTask> cannot) {
        for (MinionFitness.Row row : rows(build).values()) {
            if (!row.task().rated()) {
                continue;
            }
            if (cannot.contains(row.task()) != !row.can()) {
                problems.add(name + ": " + (row.can() ? "should not be able to: " : "should be able to: ") + describe(row));
            } else if (row.can() && (!worked.containsKey(row.task()) || !near(row.fitness(), held(worked.get(row.task()))))) {
                problems.add(name + ": worked out " + worked.get(row.task()) + ", got " + describe(row));
            }
        }
    }

    /**
     * A stand-in body with every stat at its reference scores 100% at every task, and every lever gives today's constant:
     * catches 600 to 1200 ticks, strokes 15, looking gold over 120, tending a heart every 100, 25 mB a minute at work, and
     * stage C's: a sentry's shots and spread, a medic's throws and spread, a herder's wait, a courier's, farmer's and
     * tender's looks, a hauler's towing, a butcher's yield, a digger's finds, a blow a second and a surgeon's stump prices;
     * each moves with the fitness within its bounds. The shipped task and disposition files are the code's defaults, so a
     * missing one changes nothing.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void oneHundredIsToday(GameTestHelper helper) {
        PartsData.Store store = PartsData.SERVER;
        // a zombie's two arms (hands, blows of 2.5) and its legs under a head seeing 16 blocks, on a stand-in torso
        MinionBuild build = armed(ref("zombie", "head"));
        MinionStats real = MinionStats.of(store, build);
        MinionStats stats = new MinionStats(20.0F, real.knockbackResistance(), 9, real.reservoir(), "walk", 0.25F, real.biteDamage(), 0.0F, real.strikes(),
                false, false, false, false, real.width(), real.height(), real.lyingWidth(), real.lyingHeight(), 16.0F, 0.0F,
                MinionStats.Mount.SADDLE, false, real.holders(), 0.8F, Map.of(), "none");
        MinionFitness.Body body = MinionFitness.body(store, build, stats);
        MinionFitness.Body standIn = new MinionFitness.Body(stats, 0.25F, List.of(), 20.0F, List.of(), 2.5F, List.of(), 2, 1.0F, true, true, Optional.empty(),
                0, 0, List.of(), 0.0F, List.of(), true, List.of(), false, body.torso(), body.head(), Map.of(), MinionDisposition.NONE);
        for (MinionTask task : MinionTask.values()) {
            // a hand's reference at fishing is a rod in it; a sentry's ranged attack a bow
            ItemStack held = new ItemStack(task == MinionTask.FISHER ? Items.FISHING_ROD : Items.BOW);
            MinionFitness.Row row = MinionFitness.row(store, standIn, task, MinionFitness.Context.NONE.holding(held));
            if (!row.can() || Math.abs(row.fitness() - 1.0F) > 1.0E-4F) {
                helper.fail("At every reference it should be 100%: " + describe(row));
                return;
            }
        }
        int[] catches = MinionFitness.catchTicks(store.task(MinionTask.FISHER), 1.0F);
        if (catches[0] != 600 || catches[1] != 1200 || MinionFitness.strokeTicks(store.task(MinionTask.BUTCHER), 1.0F) != 15
                || MinionFitness.admireTicks(store.task(MinionTask.BARTERER), 1.0F) != 120 || MinionFitness.tendTicks(store.task(MinionTask.SURGEON), 1.0F) != 100
                || MinionFitness.workingDrain(1.0F) != MinionEntity.WORKING || MinionEntity.WORKING != 25.0F) {
            helper.fail("At 100% every lever should be today's: catches " + catches[0] + " to " + catches[1] + ", stroke "
                    + MinionFitness.strokeTicks(store.task(MinionTask.BUTCHER), 1.0F) + ", admire " + MinionFitness.admireTicks(store.task(MinionTask.BARTERER), 1.0F)
                    + ", tend " + MinionFitness.tendTicks(store.task(MinionTask.SURGEON), 1.0F) + ", " + MinionFitness.workingDrain(1.0F) + " mB");
            return;
        }
        // stage C's levers, each today's constant at 100% (docs/NEXT.md 1.2): a sentry's shots (a bow's second, a crossbow's
        // one to two, a trident's two) and its spread (14 less 4 a step of difficulty), a medic's throw every 3 s at a witch's
        // spread, a herder's 30 s after a stray, a courier's, farmer's and tender's look every second (a courier's and farmer's
        // was a one-in-ten chance each time its goal was asked, which is every other tick),
        // a hauler towing at a player's slowdown, a butcher's whole yield, a digger's minute or two, the fisher's floor, a
        // blow a second, and a surgeon's stump a bucket at 150% and over, two from 75%, three below
        MinionTask.Data sentry = store.task(MinionTask.SENTRY);
        MinionTask.Data medic = store.task(MinionTask.MEDIC);
        MinionTask.Data surgeon = store.task(MinionTask.SURGEON);
        int[] digs = MinionFitness.digTicks(store.task(MinionTask.DIGGER), 1.0F);
        if (MinionFitness.shotTicks(sentry.number("bow_every", 0.0F), 1.0F) != 20 || MinionFitness.shotTicks(sentry.number("crossbow_min", 0.0F), 1.0F) != 20
                || MinionFitness.shotTicks(sentry.number("crossbow_max", 0.0F), 1.0F) != 40 || MinionFitness.shotTicks(sentry.number("trident_every", 0.0F), 1.0F) != 40
                || MinionFitness.shotSpread(sentry, 2, 1.0F) != 6.0F || MinionFitness.shotSpread(sentry, 0, 1.0F) != 14.0F
                || MinionFitness.throwTicks(medic, 1.0F) != 60 || MinionFitness.throwSpread(medic, 1.0F) != 8.0F
                || MinionFitness.strayTicks(store.task(MinionTask.HERDER), 1.0F) != 600 || MinionFitness.lookTicks(store.task(MinionTask.COURIER), 1.0F) != 20
                || MinionFitness.lookTicks(store.task(MinionTask.FARMER), 1.0F) != 20 || MinionFitness.lookTicks(store.task(MinionTask.TENDER), 1.0F) != 20
                || MinionFitness.towing(store.task(MinionTask.HAULER), 0.3F, 1.0F) != 0.3F || MinionFitness.yieldShare(1.0F) != 1.0F
                || digs[0] != 1200 || digs[1] != 2400 || MinionFitness.catchLeast(store.task(MinionTask.FISHER)) != 100
                || com.avicagan.bloodandbones.minion.MinionGoals.BLOW_EVERY != 20 || MinionFitness.stumpBuckets(surgeon, 2.0F) != 1
                || MinionFitness.stumpBuckets(surgeon, 1.5F) != 1 || MinionFitness.stumpBuckets(surgeon, 1.0F) != 2 || MinionFitness.stumpBuckets(surgeon, 0.74F) != 3) {
            helper.fail("At 100% every lever should be today's");
            return;
        }
        // and the levers move as the design says, within their bounds: the lever's fitness held 25% to 200% (never more than
        // four times slower), a stroke never under 0.3 s, a catch never under a sixth, a shot never under half, a throw never
        // under a second, tending never under 2 s, a poor butcher's yield its fitness, a poor hauler slowed a player's ÷ its
        // fitness to at most 90% and a fit one no less than a player, blood 12.5 to 50
        MinionTask.Data hauler = store.task(MinionTask.HAULER);
        if (MinionFitness.strokeTicks(store.task(MinionTask.BUTCHER), 5.0F) != 8 || MinionFitness.strokeTicks(store.task(MinionTask.BUTCHER), 0.1F) != 60
                || MinionFitness.catchTicks(store.task(MinionTask.FISHER), 2.0F)[0] != 300 || MinionFitness.shotTicks(20.0F, 5.0F) != 10
                || MinionFitness.shotSpread(sentry, 2, 2.0F) != 3.0F || MinionFitness.throwTicks(medic, 5.0F) < 20 || MinionFitness.throwTicks(medic, 2.0F) != 30
                || MinionFitness.strayTicks(store.task(MinionTask.HERDER), 2.0F) != 1200 || MinionFitness.lookTicks(store.task(MinionTask.COURIER), 2.0F) != 10
                || MinionFitness.yieldShare(0.5F) != 0.5F || MinionFitness.yieldShare(2.0F) != 1.0F || MinionFitness.yieldShare(0.1F) != 0.25F
                || Math.abs(MinionFitness.towing(hauler, 0.2F, 0.5F) - 0.4F) > 1.0E-6F || MinionFitness.towing(hauler, 0.3F, 0.1F) != 0.9F
                || MinionFitness.towing(hauler, 0.3F, 2.0F) != 0.3F || MinionFitness.workingDrain(4.0F) != 12.5F || MinionFitness.workingDrain(0.1F) != 50.0F
                || MinionFitness.tendTicks(surgeon, 2.0F) != 50 || MinionFitness.tendTicks(surgeon.read(JsonParser.parseString(
                        "{\"numbers\": {\"tend_every\": 60}}").getAsJsonObject()), 2.0F) != 40) {
            helper.fail("The levers should follow the fitness within their bounds");
            return;
        }
        for (MinionTask task : MinionTask.values()) {
            if (!store.task(task).equals(task.defaults()) || !task.defaults().read(task.defaults().toJson()).equals(task.defaults())) {
                helper.fail("The shipped file for " + task.id + " should be the code's defaults: " + store.task(task));
                return;
            }
        }
        for (Map.Entry<String, MinionDisposition> e : MinionDisposition.DEFAULTS.entrySet()) {
            if (!store.disposition(e.getKey()).equals(e.getValue()) || !store.dispositions().containsKey(bb(e.getKey()))) {
                helper.fail("The shipped file for the " + e.getKey() + " disposition should be the code's: " + store.disposition(e.getKey()));
                return;
            }
        }
        helper.succeed();
    }

    /**
     * A headless, armless, legless cow torso cannot do what needs a body part it lacks, each for its reason: Surgeon,
     * Butcher, Farmer, Fisher, Medic, Barterer, Digger, Herder, Guard, Sentry, Hunter and Sapper. It can do Idle, Courier,
     * Hauler and Tender: anything can carry something, if only on its back. Folded arms never strike, so a villager's torso
     * and folded pair with no head cannot guard, keep a post or hunt, and has nothing to fight with; under a head it can,
     * biting with the head (docs/NEXT.md 1.7), and its fight goals agree ({@link MinionStats#fights}).
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void cannotOnlyWhenTheBodyCannot(GameTestHelper helper) {
        Map<MinionTask, MinionFitness.Row> rows = rows(MinionBuild.of(ref("cow", "body")));
        Map<MinionTask, String> reasons = Map.ofEntries(Map.entry(MinionTask.SURGEON, "hand"), Map.entry(MinionTask.BUTCHER, "blade"),
                Map.entry(MinionTask.FARMER, "pick"), Map.entry(MinionTask.FISHER, "catch"), Map.entry(MinionTask.MEDIC, "throw"),
                Map.entry(MinionTask.BARTERER, "pick"), Map.entry(MinionTask.DIGGER, "nose"), Map.entry(MinionTask.HERDER, "bait"),
                Map.entry(MinionTask.GUARD, "strike"), Map.entry(MinionTask.SENTRY, "strike"), Map.entry(MinionTask.HUNTER, "strike"),
                Map.entry(MinionTask.SAPPER, "detonator"));
        for (MinionFitness.Row row : rows.values()) {
            String reason = reasons.get(row.task());
            if (reason == null ? !row.can() : !row.cannot().equals(Optional.of("bloodandbones.minion.cannot." + reason))) {
                helper.fail("A bare cow torso " + (reason == null ? "should manage " : "cannot, for want of " + reason + ": ") + describe(row));
                return;
            }
        }
        if (!(rows.get(MinionTask.COURIER).fitness() > MinionFitness.LEAST) || rows.get(MinionTask.COURIER).waitsFor().isPresent()) {
            helper.fail("A bare torso still carries: " + describe(rows.get(MinionTask.COURIER)));
            return;
        }
        MinionBuild folded = MinionBuild.of(ref("villager", "body")).with("arms", ref("villager", "arms"));
        MinionBuild headed = folded.with("head", villagerHead("none"));
        Map<MinionTask, MinionFitness.Row> foldedRows = rows(folded);
        Map<MinionTask, MinionFitness.Row> headedRows = rows(headed);
        for (MinionTask task : List.of(MinionTask.GUARD, MinionTask.SENTRY, MinionTask.HUNTER)) {
            if (!foldedRows.get(task).cannot().equals(Optional.of("bloodandbones.minion.cannot.strike")) || !headedRows.get(task).can()) {
                helper.fail("Folded arms alone cannot fight, and under a head bite with it: " + describe(foldedRows.get(task)) + " / "
                        + describe(headedRows.get(task)));
                return;
            }
        }
        if (MinionStats.of(PartsData.SERVER, folded).fights() || !MinionStats.of(PartsData.SERVER, headed).fights()
                || !headedRows.get(MinionTask.GUARD).main().get().from().stream().anyMatch(f -> f.id().equals(bb("bite")))) {
            helper.fail("The fight goals should agree with the rows: folded arms alone fight nothing, under a head they bite: "
                    + describe(headedRows.get(MinionTask.GUARD)));
            return;
        }
        helper.succeed();
    }

    /**
     * A missing tool never shuts a task. A butcher with no blade, a herder with no food and a medic with no potions take the
     * task and wait, naming what for, and are shown at their fitness with it; given it, they wait no more. A fisher with no
     * rod fishes by hand, poorly, and is shown what a rod would make it; a sentry with no bow what a bow would.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void missingToolWaitsNotCannot(GameTestHelper helper) {
        MinionBuild build = armed(ref("zombie", "head"));
        Map<MinionTask, MinionFitness.Row> bare = rows(build);
        for (MinionTask task : List.of(MinionTask.BUTCHER, MinionTask.HERDER, MinionTask.MEDIC)) {
            MinionFitness.Row row = bare.get(task);
            if (!row.can() || !row.waitsFor().equals(Optional.of("bloodandbones.minion.wants." + task.id.getPath()))) {
                helper.fail("With nothing in hand it should take the task and wait for its tool: " + describe(row));
                return;
            }
        }
        Map<MinionTask, MinionFitness.Row> given = rows(build, MinionFitness.Context.NONE.holding(new ItemStack(BBItems.CLEAVER.get())));
        Map<MinionTask, MinionFitness.Row> fed = rows(build, MinionFitness.Context.NONE.holding(new ItemStack(Items.WHEAT)));
        Map<MinionTask, MinionFitness.Row> stocked = rows(build, MinionFitness.Context.NONE.carrying(List.of(PotionContents.createItemStack(Items.SPLASH_POTION, Potions.HEALING))));
        if (!given.get(MinionTask.BUTCHER).ready() || !fed.get(MinionTask.HERDER).ready() || !stocked.get(MinionTask.MEDIC).ready()
                || given.get(MinionTask.BUTCHER).fitness() != bare.get(MinionTask.BUTCHER).fitness()
                || stocked.get(MinionTask.MEDIC).fitness() != bare.get(MinionTask.MEDIC).fitness()) {
            helper.fail("Given its tool it should wait no more, at the fitness it was shown: " + describe(given.get(MinionTask.BUTCHER)) + " / "
                    + describe(fed.get(MinionTask.HERDER)) + " / " + describe(stocked.get(MinionTask.MEDIC)));
            return;
        }
        MinionFitness.Row fisher = bare.get(MinionTask.FISHER);
        MinionFitness.Row rod = rows(build, MinionFitness.Context.NONE.holding(new ItemStack(Items.FISHING_ROD))).get(MinionTask.FISHER);
        if (!fisher.ready() || fisher.withTool().isEmpty() || !near(fisher.withTool().get(), rod.fitness()) || !near(fisher.fitness() / rod.fitness(), 0.35)
                || rod.withTool().isPresent()) {
            helper.fail("A fisher with no rod should fish by hand at 35% of what a rod makes it, and be shown that: " + describe(fisher) + " / " + describe(rod));
            return;
        }
        MinionFitness.Row sentry = bare.get(MinionTask.SENTRY);
        MinionFitness.Row bow = rows(build, MinionFitness.Context.NONE.holding(new ItemStack(Items.BOW))).get(MinionTask.SENTRY);
        if (!sentry.ready() || sentry.withTool().isEmpty() || !near(sentry.withTool().get(), bow.fitness()) || !near(bow.fitness() / sentry.fitness(), 4.0)) {
            helper.fail("A sentry with no bow should hold its post, and be shown what a bow would make it: " + describe(sentry) + " / " + describe(bow));
            return;
        }
        MinionFitness.Row hunter = rows(build, MinionFitness.Context.NONE.griefing(false)).get(MinionTask.HUNTER);
        if (!hunter.can() || !hunter.waitsFor().equals(Optional.of("bloodandbones.minion.wants.griefing"))) {
            helper.fail("With mobGriefing off a hunter should wait for it: " + describe(hunter));
            return;
        }
        helper.succeed();
    }

    /**
     * With a zombie's arms, the brief's surgeons are the best: villager and pillager heads 200%, leaving a stump of one
     * bucket, as today; a witch's at least 150%; a zombie's between 75% and 150% (two buckets); a blind villager's below a
     * seeing one's; a headless body's three. A villager's head on a chicken's wings cannot hold the blade at all.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void villagerAndPillagerHeadsAreTheBestSurgeons(GameTestHelper helper) {
        Function<MinionBuild, MinionFitness.Row> surgeon = build -> rows(build).get(MinionTask.SURGEON);
        MinionFitness.Row villager = surgeon.apply(armed(villagerHead("none")));
        MinionFitness.Row pillager = surgeon.apply(armed(ref("pillager", "head")));
        MinionFitness.Row witch = surgeon.apply(armed(ref("witch", "head")));
        MinionFitness.Row zombie = surgeon.apply(armed(ref("zombie", "head")));
        MinionFitness.Row blind = surgeon.apply(armed(ref("villager", "head", Map.of("profession", "none", Surgery.ORGANS_TAKEN, "2"))));
        MinionFitness.Row headless = surgeon.apply(MinionBuild.of(ref("zombie", "body")).with("right_arm", ref("zombie", "right_arm"))
                .with("left_arm", ref("zombie", "left_arm")));
        if (villager.fitness() != MinionFitness.MOST || pillager.fitness() != MinionFitness.MOST || MinionFitness.stumpBuckets(villager.fitness()) != 1
                || MinionFitness.stumpBuckets(pillager.fitness()) != 1) {
            helper.fail("Villager and pillager heads should be 200% surgeons, leaving today's stump: " + describe(villager) + " / " + describe(pillager));
            return;
        }
        if (!(witch.fitness() >= MinionFitness.CLEAN_CUT) || !(zombie.fitness() >= MinionFitness.FAIR_CUT && zombie.fitness() < MinionFitness.CLEAN_CUT)
                || MinionFitness.stumpBuckets(zombie.fitness()) != 2 || !(blind.fitness() < villager.fitness()) || !blind.can()
                || MinionFitness.stumpBuckets(headless.fitness()) != 3) {
            helper.fail("A witch's should be at least 150%, a zombie's 75% to 150%, a blind villager's lower, a headless one's three buckets: "
                    + describe(witch) + " / " + describe(zombie) + " / " + describe(blind) + " / " + describe(headless));
            return;
        }
        MinionFitness.Row winged = surgeon.apply(MinionBuild.of(ref("chicken", "body")).with("head", villagerHead("none"))
                .with("right_wing", ref("chicken", "right_wing")).with("left_wing", ref("chicken", "left_wing")));
        if (!winged.cannot().equals(Optional.of("bloodandbones.minion.cannot.hand"))) {
            helper.fail("Wings hold no surgeon's blade: " + describe(winged));
            return;
        }
        helper.succeed();
    }

    /**
     * The owner's call (docs/NEXT.md 1.5), both ways. By default any surgeon with a hand may cut; with the surgeon task's
     * {@code "needs_surgeon_head": true} (a datapack's), only a head whose data says it is a surgeon's may: the villager and
     * illager families and the witch, not a zombie's. Tending is anyone's either way.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void surgeonHeadFlagDecidesWhoCuts(GameTestHelper helper) {
        PartsData.Store store = PartsData.SERVER;
        MinionTask.Data any = store.task(MinionTask.SURGEON);
        MinionTask.Data letter = any.read(JsonParser.parseString("{\"needs_surgeon_head\": true}").getAsJsonObject());
        Function<PieceRef, MinionFitness.Body> body = head -> MinionFitness.body(store, armed(head), MinionStats.of(store, armed(head)));
        if (any.needsSurgeonHead() || !letter.needsSurgeonHead()) {
            helper.fail("The flag should be off by default, and a file should turn it on");
            return;
        }
        for (PieceRef head : List.of(villagerHead("farmer"), ref("pillager", "head"), ref("vindicator", "head"), ref("witch", "head"), ref("zombie", "head"))) {
            boolean surgeonHead = !head.entity().getPath().equals("zombie");
            if (!MinionFitness.mayCut(any, body.apply(head)) || MinionFitness.mayCut(letter, body.apply(head)) != surgeonHead) {
                helper.fail("A " + head.entity() + " head should cut by default, and with the flag only if a surgeon's head: " + body.apply(head).surgeonHead());
                return;
            }
        }
        MinionFitness.Body winged = MinionFitness.body(store, MinionBuild.of(ref("chicken", "body")).with("head", villagerHead("none")),
                MinionStats.of(store, MinionBuild.of(ref("chicken", "body")).with("head", villagerHead("none"))));
        if (MinionFitness.mayCut(any, winged) || MinionFitness.mayCut(letter, winged)) {
            helper.fail("With no hand no head cuts, the flag or not");
            return;
        }
        helper.succeed();
    }

    /**
     * Each profession's head has a knack of 1.5 for its task and for surgeon (the carcass keeps the profession); an
     * unemployed villager's is a courier's. A nitwit's head is dim and no surgeon: lower than an unemployed villager's at
     * every task that tends, fetches or works. At the fights it is higher, as the disposition table has it: dim is 0.75
     * there and a villager's meekness 0.5.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void professionSetsItsKnack(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(5, 2, 5));
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FISHERMAN));
        String kept = CarcassLook.traits(villager).get("profession");
        villager.discard();
        if (!"fisherman".equals(kept)) {
            helper.fail("A villager's carcass should keep its profession: " + kept);
            return;
        }
        Map<String, String> expected = Map.ofEntries(Map.entry("farmer", "farmer"), Map.entry("fisherman", "fisher"), Map.entry("butcher", "butcher"),
                Map.entry("cleric", "medic"), Map.entry("shepherd", "herder"), Map.entry("fletcher", "sentry"), Map.entry("leatherworker", "hauler"),
                Map.entry("armorer", "guard"), Map.entry("weaponsmith", "guard"), Map.entry("toolsmith", "guard"), Map.entry("librarian", "courier"),
                Map.entry("cartographer", "courier"), Map.entry("mason", "courier"), Map.entry("none", "courier"));
        for (Map.Entry<String, String> e : expected.entrySet()) {
            MinionStats stats = MinionStats.of(PartsData.SERVER, armed(villagerHead(e.getKey())));
            if (stats.knack(bb(e.getValue())) != 1.5F || stats.knack(bb("surgeon")) != 1.5F || !"meek".equals(stats.disposition())) {
                helper.fail("A " + e.getKey() + "'s head should have knacks of 1.5 for " + e.getValue() + " and surgeon: " + stats.knacks());
                return;
            }
        }
        Map<MinionTask, MinionFitness.Row> nitwit = rows(armed(villagerHead("nitwit")));
        Map<MinionTask, MinionFitness.Row> unemployed = rows(armed(villagerHead("none")));
        for (MinionTask task : MinionTask.values()) {
            MinionFitness.Row dim = nitwit.get(task);
            MinionFitness.Row meek = unemployed.get(task);
            if (!task.rated() || !dim.can()) {
                continue;
            }
            boolean higher = task.kind == MinionTask.Kind.FIGHT;
            if (higher ? !(dim.raw() > meek.raw()) : !(dim.raw() < meek.raw())) {
                helper.fail("A nitwit's head should be " + (higher ? "braver" : "worse") + " than an unemployed villager's: " + describe(dim) + " / " + describe(meek));
                return;
            }
        }
        if (!"dim".equals(nitwit.get(MinionTask.GUARD).dispositionName())) {
            helper.fail("A nitwit's head is dim: " + nitwit.get(MinionTask.GUARD).dispositionName());
            return;
        }
        helper.succeed();
    }

    /**
     * Both eyes out of a head: farmer, fisher, surgeon, sentry and hunter are still possible but lower, and it sees 4
     * blocks. One eye out changes nothing. An echolocate sense (a bat's Echo Ear in it) or a tremor sense (a warden's head)
     * finds its way 12 blocks, eyes or none.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void blindHeadIsPoorNotBarred(GameTestHelper helper) {
        MinionBuild seeing = armed(villagerHead("farmer"));
        MinionBuild oneEye = armed(ref("villager", "head", Map.of("profession", "farmer", Surgery.ORGANS_TAKEN, "1")));
        MinionBuild blind = armed(ref("villager", "head", Map.of("profession", "farmer", Surgery.ORGANS_TAKEN, "2")));
        Map<MinionTask, MinionFitness.Row> sees = rows(seeing);
        Map<MinionTask, MinionFitness.Row> one = rows(oneEye);
        MinionFitness.Context bow = MinionFitness.Context.NONE.holding(new ItemStack(Items.BOW));
        Map<MinionTask, MinionFitness.Row> bowSees = rows(seeing, bow);
        Map<MinionTask, MinionFitness.Row> bowBlind = rows(blind, bow);
        Map<MinionTask, MinionFitness.Row> none = rows(blind);
        for (MinionTask task : List.of(MinionTask.FARMER, MinionTask.FISHER, MinionTask.SURGEON, MinionTask.SENTRY, MinionTask.HUNTER)) {
            // a sentry's sight is its second: with a bow in hand it is short of its cap
            Map<MinionTask, MinionFitness.Row> sighted = task == MinionTask.SENTRY ? bowSees : sees;
            Map<MinionTask, MinionFitness.Row> blinded = task == MinionTask.SENTRY ? bowBlind : none;
            if (!blinded.get(task).can() || task != MinionTask.HUNTER && !(blinded.get(task).raw() < sighted.get(task).raw())) {
                helper.fail("Blind, it should still " + task + ", but worse: " + describe(blinded.get(task)) + " / " + describe(sighted.get(task)));
                return;
            }
            if (one.get(task).raw() != sees.get(task).raw()) {
                helper.fail("One eye out should change nothing: " + describe(one.get(task)) + " / " + describe(sees.get(task)));
                return;
            }
        }
        MinionStats blindStats = MinionStats.of(PartsData.SERVER, blind);
        if (blindStats.sight() != MinionStats.BLIND_SIGHT || MinionStats.of(PartsData.SERVER, oneEye).sight() != MinionStats.of(PartsData.SERVER, seeing).sight()) {
            helper.fail("Blind it sees 4 blocks, one eye out as far as ever: " + blindStats.sight());
            return;
        }
        MinionBuild echo = blind.withOrgan(Optional.of(new CarcassArmour.Organ(bb("echo_ear"), mob("bat"), false)));
        MinionBuild tremor = armed(ref("warden", "bone/body/head", Map.of(Surgery.ORGANS_TAKEN, "2")));
        float echoSight = MinionStats.of(PartsData.SERVER, echo).sight();
        float tremorSight = MinionStats.of(PartsData.SERVER, tremor).sight();
        if (echoSight != MinionStats.SENSE_SIGHT || tremorSight != MinionStats.SENSE_SIGHT) {
            helper.fail("An echolocate or tremor sense should find its way 12 blocks: " + echoSight + " " + tremorSight);
            return;
        }
        helper.succeed();
    }

    /**
     * Eight zombie arms on a spider's torso hold with 1.6 times the hands of two (+15% for each past two, up to +60%) and
     * strike 60% more often: 1.6 times the two-armed fitness at Farmer before the cap, and at Butcher, whose second is its
     * blow, 1.6 × √1.6. More arms are always better, never beyond +60%.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void moreHandsWorkFaster(GameTestHelper helper) {
        PieceRef head = ref("zombie", "head");
        Map<MinionTask, MinionFitness.Row> two = rows(spiderOfArms(head, 2));
        Map<MinionTask, MinionFitness.Row> four = rows(spiderOfArms(head, 4));
        Map<MinionTask, MinionFitness.Row> eight = rows(spiderOfArms(head, 8));
        MinionFitness.Body body = MinionFitness.body(PartsData.SERVER, spiderOfArms(head, 8), MinionStats.of(PartsData.SERVER, spiderOfArms(head, 8)));
        if (!near(eight.get(MinionTask.FARMER).main().get().value(), 1.6) || !near(four.get(MinionTask.FARMER).main().get().value(), 1.3)
                || !near(eight.get(MinionTask.FARMER).raw() / two.get(MinionTask.FARMER).raw(), 1.6)
                || !near(eight.get(MinionTask.BUTCHER).raw() / two.get(MinionTask.BUTCHER).raw(), 1.6 * Math.sqrt(1.6))
                || !near(body.strikeRate(), 1.6)) {
            helper.fail("Eight arms should hold with 1.6 times the hands and strike 60% more often: " + describe(eight.get(MinionTask.FARMER)) + " / "
                    + describe(two.get(MinionTask.FARMER)) + " / " + describe(eight.get(MinionTask.BUTCHER)) + " / " + describe(two.get(MinionTask.BUTCHER))
                    + ", strike rate " + body.strikeRate());
            return;
        }
        helper.succeed();
    }

    /**
     * On a body of four legs, front paws and hooves do handwork as a last resort: a cow on wolf legs picks at 75%, on its own
     * legs at 45% (its mouth only 35%); neither holds a surgeon's blade. The legs walk as fast as ever. On two legs they do
     * no handwork: a zombie on a wolf's front legs has only its mouth.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void pawsAndHoovesPickPoorly(GameTestHelper helper) {
        MinionBuild wolfLegs = cowOn("wolf", "front_leg", "hind_leg", ref("cow", "head"));
        MinionBuild cowLegs = cowOn("cow", "front_leg", "hind_leg", ref("cow", "head"));
        Map<MinionTask, MinionFitness.Row> paws = rows(wolfLegs);
        Map<MinionTask, MinionFitness.Row> hooves = rows(cowLegs);
        if (!near(paws.get(MinionTask.FARMER).fitness(), 0.75) || !near(hooves.get(MinionTask.FARMER).fitness(), 0.45)
                || paws.get(MinionTask.SURGEON).can() || hooves.get(MinionTask.SURGEON).can()) {
            helper.fail("Paws should pick at 75%, hooves at 45%, neither hold a blade: " + describe(paws.get(MinionTask.FARMER)) + " / "
                    + describe(hooves.get(MinionTask.FARMER)) + " / " + describe(paws.get(MinionTask.SURGEON)));
            return;
        }
        MinionStats wolf = MinionStats.of(PartsData.SERVER, wolfLegs);
        MinionStats cow = MinionStats.of(PartsData.SERVER, cowLegs);
        if (Math.abs(wolf.speed() - 0.3F) > 0.001F || Math.abs(cow.speed() - 0.2F) > 0.001F) {
            helper.fail("Grips change nothing of how legs walk: " + wolf.speed() + " " + cow.speed());
            return;
        }
        MinionBuild biped = MinionBuild.of(ref("zombie", "body")).with("head", ref("cow", "head")).with("right_leg", ref("wolf", "right_front_leg"))
                .with("left_leg", ref("wolf", "left_front_leg"));
        MinionFitness.Row farmer = rows(biped).get(MinionTask.FARMER);
        if (!near(farmer.main().get().value(), 0.35)) {
            helper.fail("A body standing on its only two legs picks with its mouth: " + describe(farmer));
            return;
        }
        helper.succeed();
    }

    /**
     * Knacks merge key by key across a mob's layers, each variant it matches after its layer's own: a farmer's zombie
     * villager head has its own file's surgeon 0.5 and the villager family's farmer 1.5; a villager's keeps the biped's
     * courier 1.25 and has its own guard 1.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void knacksMergeAcrossLayers(GameTestHelper helper) {
        PartsData.Store store = PartsData.SERVER;
        Map<String, String> farmer = Map.of("profession", "farmer");
        Map<ResourceLocation, Float> zombie = MinionData.knacks(store.resolve(mob("zombie_villager"), false), farmer, "head");
        Map<ResourceLocation, Float> villager = MinionData.knacks(store.resolve(mob("villager"), false), farmer, "head");
        if (zombie.get(bb("surgeon")) != 0.5F || zombie.get(bb("farmer")) != 1.5F || villager.get(bb("courier")) != 1.25F || villager.get(bb("guard")) != 1.0F
                || villager.get(bb("surgeon")) != 1.5F || villager.get(bb("farmer")) != 1.5F) {
            helper.fail("Knacks should merge key by key: zombie villager " + zombie + ", villager " + villager);
            return;
        }
        // the build's knack is the head's: its arms and legs name none
        MinionStats shaky = MinionStats.of(store, armed(ref("zombie_villager", "head", farmer)));
        if (shaky.knack(bb("surgeon")) != 0.5F || shaky.knack(bb("farmer")) != 1.5F || shaky.knack(bb("hunter")) != 1.0F) {
            helper.fail("A build's knacks are its parts': " + shaky.knacks());
            return;
        }
        helper.succeed();
    }

    /**
     * Old data still saying {@code "jobs": [fisher, courier, companion]} reads as knacks: fisher 1.5, courier 1.25, companion
     * dropped; a bodyguard is a guard and a scavenger a courier. The file is logged once, however many lists it has.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void oldJobsListReadAsKnacks(GameTestHelper helper) {
        int before = MobGroup.OLD_JOBS.get();
        ResourceLocation id = TestTraits.mob(helper, "old_jobs", """
                {"parts": {"head": {"minion": {"jobs": ["bloodandbones:fisher", "bloodandbones:courier", "bloodandbones:companion"],
                  "variants": [{"if": {"trait": "variant", "equals": "odd"}, "jobs": ["bloodandbones:bodyguard", "bloodandbones:scavenger"]}]}}}}
                """);
        int warned = MobGroup.OLD_JOBS.get() - before;
        Map<ResourceLocation, Float> plain = MinionData.knacks(PartsData.SERVER.resolve(id, false), Map.of(), "head");
        Map<ResourceLocation, Float> odd = MinionData.knacks(PartsData.SERVER.resolve(id, false), Map.of("variant", "odd"), "head");
        if (warned != 1 || plain.get(bb("fisher")) != 1.5F || plain.get(bb("courier")) != 1.25F || plain.containsKey(bb("companion"))) {
            helper.fail("An old jobs list should read as knacks, logged once: " + warned + " warnings, " + plain);
            return;
        }
        if (odd.get(bb("guard")) != 1.5F || odd.get(bb("courier")) != 1.25F || odd.get(bb("fisher")) != 1.5F || odd.containsKey(bb("bodyguard"))) {
            helper.fail("A bodyguard is a guard and a scavenger a courier: " + odd);
            return;
        }
        helper.succeed();
    }

    /**
     * One body under different heads moves as the disposition table says: meek fights at 0.5 and tends at 1.25, brave fights
     * at 1.25, berserk fights at 1.5 and tends at 0.5; nocturnal is 1.25 at midnight and 0.75 at noon; loyal is 1.25 with
     * its maker and 1 at home; territorial guards at 1.25; no head is mindless.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void dispositionsScaleTheirKinds(GameTestHelper helper) {
        record Case(PieceRef head, String name, MinionTask task, MinionFitness.Context context, float multiplier) {
        }
        MinionFitness.Context noon = MinionFitness.Context.NONE;
        MinionFitness.Context midnight = MinionFitness.Context.NONE.atNight(true);
        MinionFitness.Context withMe = MinionFitness.Context.NONE.at(MinionTask.Anchor.MAKER);
        List<Case> cases = List.of(new Case(villagerHead("none"), "meek", MinionTask.GUARD, noon, 0.5F),
                new Case(villagerHead("none"), "meek", MinionTask.SURGEON, noon, 1.25F),
                new Case(villagerHead("none"), "meek", MinionTask.FARMER, noon, 1.0F),
                new Case(ref("pillager", "head"), "brave", MinionTask.HUNTER, noon, 1.25F),
                new Case(ref("pillager", "head"), "brave", MinionTask.COURIER, noon, 1.0F),
                new Case(ref("zoglin", "head"), "berserk", MinionTask.GUARD, noon, 1.5F),
                new Case(ref("zoglin", "head"), "berserk", MinionTask.MEDIC, noon, 0.5F),
                new Case(ref("zoglin", "head"), "berserk", MinionTask.COURIER, noon, 0.75F),
                new Case(ref("spider", "head"), "nocturnal", MinionTask.FARMER, midnight, 1.25F),
                new Case(ref("spider", "head"), "nocturnal", MinionTask.FARMER, noon, 0.75F),
                new Case(ref("zombie", "head"), "loyal", MinionTask.GUARD, withMe, 1.25F),
                new Case(ref("zombie", "head"), "loyal", MinionTask.GUARD, noon, 1.0F),
                new Case(ref("iron_golem", "head"), "territorial", MinionTask.GUARD, noon, 1.25F),
                new Case(ref("iron_golem", "head"), "territorial", MinionTask.HUNTER, noon, 1.0F));
        for (Case c : cases) {
            MinionFitness.Row row = rows(armed(c.head()), c.context()).get(c.task());
            if (!c.name().equals(row.dispositionName()) || Math.abs(row.disposition() - c.multiplier()) > 1.0E-4F) {
                helper.fail("A " + c.head().entity() + " head is " + c.name() + ", " + c.multiplier() + " at " + c.task() + ": " + describe(row));
                return;
            }
        }
        MinionFitness.Row mindless = rows(MinionBuild.of(ref("zombie", "body")).with("right_arm", ref("zombie", "right_arm"))).get(MinionTask.SURGEON);
        if (!MinionDisposition.MINDLESS.equals(mindless.dispositionName()) || mindless.disposition() != 0.25F) {
            helper.fail("No head is mindless, tending at a quarter: " + describe(mindless));
            return;
        }
        helper.succeed();
    }

    /**
     * A mob with only an archetype (a modded one sorted by its body shape, spec 3.7) gets every task its body allows on day
     * one, from its attributes (here none: the fallbacks), its archetype's knacks and its legs' grips: a four-legged one
     * with a head can do all but the surgeon's cut and the sapper's blast, courier with the quadruped's knack of 1.25,
     * picking with its front hooves.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void moddedMobWorksFromItsArchetype(GameTestHelper helper) {
        ResourceLocation id = TestTraits.mob(helper, "modded_beast", "{\"archetype\": \"bloodandbones:quadruped\"}");
        Rig wolf = RigManager.forEntity(mob("wolf")).orElseThrow();
        RigManager.addTestRig(new Rig(id, wolf.model(), wolf.layer(), wolf.texture(), wolf.variantNames(), wolf.passes(), wolf.scale(), wolf.weight(),
                wolf.rotTime(), wolf.bones(), wolf.baby()));
        PartsData.SERVER.invalidate();
        MinionBuild build = MinionBuild.of(ref(id, "body", Map.of())).with("head", ref(id, "head/real_head", Map.of()));
        for (String leg : new String[]{"right_front_leg", "left_front_leg", "right_hind_leg", "left_hind_leg"}) {
            build = build.with(leg, ref(id, leg, Map.of()));
        }
        Map<MinionTask, MinionFitness.Row> rows = rows(build);
        Set<MinionTask> cannot = EnumSet.noneOf(MinionTask.class);
        rows.values().stream().filter(r -> !r.can()).forEach(r -> cannot.add(r.task()));
        MinionFitness.Row courier = rows.get(MinionTask.COURIER);
        MinionFitness.Row farmer = rows.get(MinionTask.FARMER);
        if (!cannot.equals(EnumSet.of(MinionTask.SURGEON, MinionTask.SAPPER)) || courier.knack() != 1.25F || !"loyal".equals(courier.dispositionName())
                || !near(farmer.main().get().value(), 0.45) || !near(rows.get(MinionTask.HUNTER).second().get().value(), 0.22 / 0.25)) {
            helper.fail("A modded quadruped should do all its body allows from its archetype: cannot " + cannot + "; " + describe(courier) + " / " + describe(farmer)
                    + " / " + describe(rows.get(MinionTask.HUNTER)));
            return;
        }
        helper.succeed();
    }

    /**
     * An organ's knack for one task ({@code bloodandbones:task_knack}): the sniffer's Olfactory Bulb in a build makes it half
     * again as good a Digger, and changes no other task.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void taskKnackTraitCounts(GameTestHelper helper) {
        MinionBuild plain = armed(ref("zombie", "head"));
        MinionBuild bulb = plain.withOrgan(Optional.of(new CarcassArmour.Organ(bb("olfactory_bulb"), mob("sniffer"), false)));
        Map<MinionTask, MinionFitness.Row> without = rows(plain);
        Map<MinionTask, MinionFitness.Row> with = rows(bulb);
        for (MinionTask task : MinionTask.values()) {
            float ratio = with.get(task).knack() / without.get(task).knack();
            if (Math.abs(ratio - (task == MinionTask.DIGGER ? 1.5F : 1.0F)) > 1.0E-4F) {
                helper.fail("The Olfactory Bulb should make it half again as good a Digger and change nothing else: " + describe(with.get(task)) + " / "
                        + describe(without.get(task)));
                return;
            }
        }
        if (with.get(MinionTask.DIGGER).knackFrom().stream().noneMatch(p -> p.from().equals(bb("truffle_nose")))) {
            helper.fail("Its knack should say where it came from: " + with.get(MinionTask.DIGGER).knackFrom());
            return;
        }
        helper.succeed();
    }

    /**
     * What a part brings to a minion's tasks, as JEI's Body Parts page and a piece's tooltip (with Ctrl) show it
     * (docs/NEXT.md 1.4): a farmer villager's head has its knacks (Surgeon and Farmer ×1.5, the biped's Courier ×1.25; its
     * family's Guard of 1 is left out), the meek disposition and a surgeon's head; a nitwit's is dim; a cow's head has
     * Herder and Courier ×1.25 and is docile, and no surgeon's; a zombie's arm holds with a hand, a villager's folded pair
     * with two, and a wolf's front leg with a paw on a body of four legs or more. A carried piece's tooltip reads the same
     * facts, by the part its bone is.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void partFactsShowKnacksGripsAndDispositions(GameTestHelper helper) {
        PartsData.Store store = PartsData.SERVER;
        List<net.minecraft.network.chat.Component> farmer = TaskWords.partFacts(store.resolve(mob("villager"), false), Map.of("profession", "farmer"), "head");
        net.minecraft.network.chat.Component knacks = farmer.isEmpty() ? net.minecraft.network.chat.Component.empty() : farmer.get(0);
        if (!MinionTaskTests.names(knacks, "bloodandbones.minion.facts.knacks") || !MinionTaskTests.names(knacks, MinionTask.SURGEON.nameKey())
                || !MinionTaskTests.names(knacks, MinionTask.FARMER.nameKey()) || !MinionTaskTests.names(knacks, MinionTask.COURIER.nameKey())
                || MinionTaskTests.names(knacks, MinionTask.GUARD.nameKey())) {
            helper.fail("A farmer's head has knacks for Surgeon, Farmer and Courier, and none shown for Guard (1): " + farmer);
            return;
        }
        if (farmer.stream().noneMatch(line -> MinionTaskTests.names(line, "bloodandbones.minion.disposition.meek"))
                || farmer.stream().noneMatch(line -> MinionTaskTests.names(line, "bloodandbones.minion.facts.surgeon"))) {
            helper.fail("A villager's head is meek, and a surgeon's: " + farmer);
            return;
        }
        List<net.minecraft.network.chat.Component> nitwit = TaskWords.partFacts(store.resolve(mob("villager"), false), Map.of("profession", "nitwit"), "head");
        List<net.minecraft.network.chat.Component> cow = TaskWords.partFacts(store.resolve(mob("cow"), false), Map.of(), "head");
        if (nitwit.stream().noneMatch(line -> MinionTaskTests.names(line, "bloodandbones.minion.disposition.dim"))
                || cow.stream().noneMatch(line -> MinionTaskTests.names(line, "bloodandbones.minion.disposition.docile"))
                || cow.stream().noneMatch(line -> MinionTaskTests.names(line, MinionTask.HERDER.nameKey()) && MinionTaskTests.names(line, MinionTask.COURIER.nameKey()))
                || cow.stream().anyMatch(line -> MinionTaskTests.names(line, "bloodandbones.minion.facts.surgeon"))) {
            helper.fail("A nitwit's head is dim; a cow's docile, with Herder and Courier, and no surgeon's: " + nitwit + " / " + cow);
            return;
        }
        List<net.minecraft.network.chat.Component> arm = TaskWords.partFacts(store.resolve(mob("zombie"), false), Map.of(), "arm");
        List<net.minecraft.network.chat.Component> pair = TaskWords.partFacts(store.resolve(mob("villager"), false), Map.of(), "arm.pair");
        List<net.minecraft.network.chat.Component> paw = TaskWords.partFacts(store.resolve(mob("wolf"), false), Map.of(), "leg.front");
        if (arm.stream().noneMatch(line -> MinionTaskTests.names(line, "bloodandbones.minion.facts.grip") && MinionTaskTests.names(line, "bloodandbones.minion.grip.hand"))
                || pair.stream().noneMatch(line -> MinionTaskTests.names(line, "bloodandbones.minion.facts.grip_pair"))
                || paw.stream().noneMatch(line -> MinionTaskTests.names(line, "bloodandbones.minion.facts.leg_grip") && MinionTaskTests.names(line, "bloodandbones.minion.grip.paw"))) {
            helper.fail("A zombie's arm holds with a hand, a villager's pair with two, a wolf's front leg with a paw: " + arm + " / " + pair + " / " + paw);
            return;
        }
        ItemStack head = new ItemStack(com.avicagan.bloodandbones.registry.BBItems.CARCASS_PIECE.get());
        head.set(com.avicagan.bloodandbones.registry.BBDataComponents.PIECE.get(), new com.avicagan.bloodandbones.item.CarcassPieceItem.Piece(mob("villager"), "head",
                ResourceLocation.withDefaultNamespace("textures/entity/villager/villager.png"), List.of(), 1.0F, false, Map.of("profession", "farmer"), 0.0F, 0.0F, 0.0F, false));
        List<net.minecraft.network.chat.Component> tooltip = com.avicagan.bloodandbones.item.CarcassPieceItem.facts(com.avicagan.bloodandbones.item.CarcassPieceItem.piece(head));
        if (!tooltip.equals(farmer)) {
            helper.fail("A farmer villager's head piece should show its head's facts: " + tooltip);
            return;
        }
        // JEI's page, one per mob, reads a part as a new one of its mob has it: a new villager records its profession as
        // "none", which every villager's carcass keeps, so its head has Surgeon and Courier ×1.5 and no Farmer knack, and the
        // page says its profession changes them; a new witch records none, and its head's facts say nothing of one
        Villager fresh = EntityType.VILLAGER.create(helper.getLevel());
        Map<String, String> kept = fresh == null ? Map.of() : CarcassLook.traits(fresh);
        List<net.minecraft.network.chat.Component> page = TaskWords.mobFacts(store.resolve(mob("villager"), false), kept, "head");
        var witchMade = EntityType.WITCH.create(helper.getLevel());
        List<net.minecraft.network.chat.Component> witch = TaskWords.mobFacts(store.resolve(mob("witch"), false),
                witchMade == null ? Map.of() : CarcassLook.traits(witchMade), "head");
        if (!"none".equals(kept.get("profession")) || page.isEmpty() || !MinionTaskTests.names(page.get(0), MinionTask.COURIER.nameKey())
                || !MinionTaskTests.names(page.get(0), MinionTask.SURGEON.nameKey()) || MinionTaskTests.names(page.get(0), MinionTask.FARMER.nameKey())
                || page.stream().noneMatch(line -> MinionTaskTests.names(line, "bloodandbones.minion.facts.varies")
                && MinionTaskTests.names(line, "bloodandbones.minion.facts.trait.profession"))
                || witch.stream().anyMatch(line -> MinionTaskTests.names(line, "bloodandbones.minion.facts.varies"))) {
            helper.fail("JEI should show a villager's head as a new one has it, and say its profession changes that; a witch's says nothing of one: "
                    + kept + " " + page.stream().map(net.minecraft.network.chat.Component::getString).toList() + " / "
                    + witch.stream().map(net.minecraft.network.chat.Component::getString).toList());
            return;
        }
        helper.succeed();
    }

    /**
     * What a head's data says that could only fail later, in play, is left out as the data loads (and logged): a disposition
     * that is no id ("Brave") and a knack whose task is no id or whose value is no number, in the part's own map and its
     * variants. What is left counts: its good knacks stand, and where a bad one was, the layer under it shows through (here
     * its archetype's guard knack and disposition).
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void badMinionDataLeftOutAsItLoads(GameTestHelper helper) {
        ResourceLocation id = TestTraits.mob(helper, "bad_head", """
                {"parts": {"head": {"minion": {"disposition": "Brave", "knacks": {"bloodandbones:Surgeon": 1.5, "bloodandbones:farmer": 1.25,
                  "bloodandbones:guard": "lots"},
                  "variants": [{"if": {"trait": "variant", "equals": "odd"}, "disposition": "Not An Id", "knacks": {"Bad Key": 2.0, "bloodandbones:fisher": 1.5}}]}}}}
                """);
        var resolved = PartsData.SERVER.resolve(id, false);
        Map<ResourceLocation, Float> plain = MinionData.knacks(resolved, Map.of(), "head");
        Map<ResourceLocation, Float> odd = MinionData.knacks(resolved, Map.of("variant", "odd"), "head");
        Optional<com.google.gson.JsonElement> disposition = MinionData.field(resolved, Map.of("variant", "odd"), "head", "disposition");
        // the good knacks stand, the bad ones are gone (the guard's "lots" leaves its archetype's 1.25 to show through), and the
        // disposition is the archetype's, the file's own and its variant's being no ids
        boolean lowerCase = java.util.stream.Stream.concat(plain.keySet().stream(), odd.keySet().stream())
                .allMatch(k -> k.getPath().equals(k.getPath().toLowerCase(java.util.Locale.ROOT)));
        if (plain.get(bb("farmer")) != 1.25F || odd.get(bb("fisher")) != 1.5F || !lowerCase || plain.containsKey(bb("surgeon"))
                || !Float.valueOf(1.25F).equals(plain.get(bb("guard"))) || disposition.isEmpty() || !disposition.get().isJsonPrimitive()
                || !MinionDisposition.valid(disposition.get().getAsString())) {
            helper.fail("What is no id or no number should be left out as it loads, the rest kept: " + plain + " / " + odd + " / " + disposition);
            return;
        }
        // and nothing in play fails on such a name, should one get past: it is no disposition at all
        if (!MinionDisposition.id("Brave").equals(bb("none")) || !PartsData.SERVER.disposition("Not An Id").equals(MinionDisposition.NONE)
                || MinionDisposition.valid("Brave") || !MinionDisposition.valid("meek") || !MinionDisposition.valid("mypack:grumpy")
                || TaskWords.partFacts(resolved, Map.of("variant", "odd"), "head").isEmpty()) {
            helper.fail("A name that is no id should read as no disposition, never fail");
            return;
        }
        helper.succeed();
    }

    /**
     * A task file's kind, tool and anchors are what the game reads (docs/NEXT.md 1.6). A hauler's file saying it is a fight
     * makes a brave head haul at 1.25; a butcher's naming only the Flensing Knife makes a Cleaver no blade (its row waits, and
     * the goals ask the same test), and naming a stick makes no tool the goals could not use; a sentry's naming only the
     * crossbow leaves a bow no ranged attack, for the row and for the sentry's goals alike. A file may take an anchor away
     * but not add one its goals cannot work from: a farmer "with me" is left at home, a guard "with me" only stays so. Nor
     * can it change whether a task waits for its tool or carries it, which its goals decide (a butcher needs its blade, a
     * fisher fishes by hand, a medic throws what it carries), nor give a tool to a task that works with none. Where a file
     * narrows a tool, the words name its items: "waiting for Flensing Knife", "With Crossbow". Each file is set and set back
     * within the one tick, since the tests share one world.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void taskFileKindToolAndAnchorsCount(GameTestHelper helper) {
        PartsData.Store store = PartsData.SERVER;
        MinionBuild brave = armed(ref("pillager", "head"));
        MinionTask.Data hauler = store.task(MinionTask.HAULER);
        MinionTask.Data butcher = store.task(MinionTask.BUTCHER);
        MinionTask.Data sentry = store.task(MinionTask.SENTRY);
        float asFetch = rows(brave).get(MinionTask.HAULER).disposition();
        float asFight;
        MinionFitness.Row cleaver;
        MinionFitness.Row knife;
        boolean stick;
        MinionFitness.Row bow;
        MinionFitness.Row crossbow;
        Component waitsOwn = TaskWords.waits(store, MinionTask.BUTCHER, "bloodandbones.minion.wants.butcher");
        Component waitsKnife;
        Component withCrossbow;
        try {
            store.setTestTask(MinionTask.HAULER, hauler.read(JsonParser.parseString("{\"kind\": \"fight\"}").getAsJsonObject()));
            asFight = rows(brave).get(MinionTask.HAULER).disposition();
            store.setTestTask(MinionTask.BUTCHER, butcher.read(JsonParser.parseString("{\"tool\": {\"items\": \"bloodandbones:flensing_knife\"}}").getAsJsonObject()));
            cleaver = rows(brave, MinionFitness.Context.NONE.holding(new ItemStack(BBItems.CLEAVER.get()))).get(MinionTask.BUTCHER);
            knife = rows(brave, MinionFitness.Context.NONE.holding(new ItemStack(BBItems.FLENSING_KNIFE.get()))).get(MinionTask.BUTCHER);
            waitsKnife = TaskWords.waits(store, MinionTask.BUTCHER, cleaver.waitsFor().orElse(""));
            store.setTestTask(MinionTask.BUTCHER, butcher.read(JsonParser.parseString("{\"tool\": {\"items\": \"minecraft:stick\"}}").getAsJsonObject()));
            stick = MinionFitness.isTool(MinionTask.BUTCHER, store.task(MinionTask.BUTCHER), new ItemStack(Items.STICK));
            store.setTestTask(MinionTask.SENTRY, sentry.read(JsonParser.parseString("{\"tool\": {\"items\": \"minecraft:crossbow\"}}").getAsJsonObject()));
            bow = rows(brave, MinionFitness.Context.NONE.holding(new ItemStack(Items.BOW))).get(MinionTask.SENTRY);
            crossbow = rows(brave, MinionFitness.Context.NONE.holding(new ItemStack(Items.CROSSBOW))).get(MinionTask.SENTRY);
            withCrossbow = TaskWords.tool(store, MinionTask.SENTRY);
        } finally {
            store.setTestTask(MinionTask.HAULER, null);
            store.setTestTask(MinionTask.BUTCHER, null);
            store.setTestTask(MinionTask.SENTRY, null);
        }
        if (asFetch != 1.0F || asFight != 1.25F) {
            helper.fail("A hauler's file saying it is a fight should have a brave head haul at 1.25: " + asFetch + ", " + asFight);
            return;
        }
        if (cleaver.ready() || !cleaver.waitsFor().equals(Optional.of("bloodandbones.minion.wants.butcher")) || !knife.ready() || stick) {
            helper.fail("A butcher's file naming only the knife should leave a Cleaver no blade, and a stick none: " + describe(cleaver) + " / " + describe(knife));
            return;
        }
        if (bow.main().get().value() != MinionFitness.NO_RANGED || crossbow.main().get().value() != 1.0F) {
            helper.fail("A sentry's file naming only the crossbow should leave a bow no ranged attack: " + describe(bow) + " / " + describe(crossbow));
            return;
        }
        if (!MinionTaskTests.names(waitsOwn, "bloodandbones.minion.wants.butcher") || !MinionTaskTests.names(waitsKnife, "bloodandbones.minion.wants.items")
                || !MinionTaskTests.names(waitsKnife, BBItems.FLENSING_KNIFE.get().getDescriptionId()) || MinionTaskTests.names(waitsKnife, "bloodandbones.minion.wants.butcher")
                || !MinionTaskTests.names(withCrossbow, Items.CROSSBOW.getDescriptionId()) || !MinionTaskTests.names(TaskWords.tool(store, MinionTask.SENTRY),
                "bloodandbones.minion.tool.sentry") || !MinionTaskTests.names(TaskWords.items("#minecraft:logs"), "tag.item.minecraft.logs")) {
            helper.fail("The words should name the file's narrowed tool, and the task's own otherwise: " + waitsOwn.getString() + " / " + waitsKnife.getString() + " / "
                    + withCrossbow.getString());
            return;
        }
        ResourceLocation tools = bb("test_tools");
        MinionTask.Data butcherOptional = MinionTask.BUTCHER.checked(MinionTask.BUTCHER.defaults().read(JsonParser.parseString("{\"tool\": {\"required\": false}}")
                .getAsJsonObject()), tools);
        MinionTask.Data fisherRequired = MinionTask.FISHER.checked(MinionTask.FISHER.defaults().read(JsonParser.parseString("{\"tool\": {\"required\": true}}")
                .getAsJsonObject()), tools);
        MinionTask.Data medicHeld = MinionTask.MEDIC.checked(MinionTask.MEDIC.defaults().read(JsonParser.parseString("{\"tool\": {\"carried\": false,"
                + " \"items\": \"minecraft:splash_potion\"}}").getAsJsonObject()), tools);
        MinionTask.Data farmerTooled = MinionTask.FARMER.checked(MinionTask.FARMER.defaults().read(JsonParser.parseString("{\"tool\": {\"items\": \"minecraft:shears\","
                + " \"required\": true}}").getAsJsonObject()), tools);
        if (!butcherOptional.tool().orElseThrow().required() || fisherRequired.tool().orElseThrow().required() || !medicHeld.tool().orElseThrow().carried()
                || !medicHeld.tool().orElseThrow().items().equals(Optional.of("minecraft:splash_potion")) || farmerTooled.tool().isPresent()) {
            helper.fail("Whether a tool is required or carried is the goals', and a task with no tool gets none: " + butcherOptional.tool() + ", "
                    + fisherRequired.tool() + ", " + medicHeld.tool() + ", " + farmerTooled.tool());
            return;
        }
        ResourceLocation file = bb("test_anchors");
        MinionTask.Data farmer = MinionTask.FARMER.checked(MinionTask.FARMER.defaults().read(JsonParser.parseString("{\"anchors\": [\"home\", \"maker\"]}")
                .getAsJsonObject()), file);
        MinionTask.Data farmerNowhere = MinionTask.FARMER.checked(MinionTask.FARMER.defaults().read(JsonParser.parseString("{\"anchors\": [\"maker\"]}")
                .getAsJsonObject()), file);
        MinionTask.Data guard = MinionTask.GUARD.checked(MinionTask.GUARD.defaults().read(JsonParser.parseString("{\"anchors\": [\"maker\"]}")
                .getAsJsonObject()), file);
        if (!farmer.anchors().equals(List.of(MinionTask.Anchor.HOME)) || !farmerNowhere.anchors().equals(List.of(MinionTask.Anchor.HOME))
                || !guard.anchors().equals(List.of(MinionTask.Anchor.MAKER)) || guard.anchorFor(MinionTask.Anchor.HOME) != MinionTask.Anchor.MAKER
                || farmer.anchorFor(MinionTask.Anchor.MAKER) != MinionTask.Anchor.HOME) {
            helper.fail("A file may take an anchor away, never add one its goals cannot work from: " + farmer.anchors() + ", " + farmerNowhere.anchors() + ", "
                    + guard.anchors());
            return;
        }
        helper.succeed();
    }
}
