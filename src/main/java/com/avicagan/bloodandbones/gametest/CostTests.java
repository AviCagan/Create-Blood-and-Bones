package com.avicagan.bloodandbones.gametest;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.carcass.CarcassAssembler;
import com.avicagan.bloodandbones.carcass.CarcassDrag;
import com.avicagan.bloodandbones.carcass.CarcassRot;
import com.avicagan.bloodandbones.carcass.CarcassSavedData;
import com.avicagan.bloodandbones.carcass.ShackleHookBlock;
import com.avicagan.bloodandbones.carcass.ShackleHookBlockEntity;
import com.avicagan.bloodandbones.registry.BBBlocks;
import com.avicagan.bloodandbones.registry.BBItems;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * What carcasses cost the server (brief: "cheap enough that a dozen at once is fine"). In the full suite these only
 * check what they can: that a dozen dropped at once all come to rest on the floor, and that a dozen of every size hang
 * from their hooks. Their tick times mean nothing there, with everything else running beside them.
 * <p>
 * To measure, run one on its own with {@code -Dbloodandbones.debug.cost=N} (and
 * {@code -Dbloodandbones.debug.only=dozenCarcasses} or {@code =dozenHung}). Then {@code dozenCarcassesAtOnce} first makes
 * a dozen and clears them away, so the game has run everything once before it is timed, and then makes N dozen (up to
 * four) in its one arena in the same tick; and {@code dozenHungCarcasses} times its dozen hanging for a minute.
 */
@GameTestHolder(BloodAndBones.MOD_ID)
@PrefixGameTestTemplate(false)
public class CostTests {
    /** Server tick lengths in nanoseconds, by tick number, the last few thousand ticks. */
    private static final long[] TICK_NANOS = new long[8192];
    private static long tickStart;
    private static boolean listening;
    /** Dozens to make at once when measuring, or 0 in the full suite. */
    private static final int MEASURING = Math.max(0, Math.min(4, Integer.getInteger("bloodandbones.debug.cost", 0)));

    private static void listen() {
        if (listening) {
            return;
        }
        listening = true;
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Pre event) -> tickStart = System.nanoTime());
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) ->
                TICK_NANOS[event.getServer().getTickCount() & (TICK_NANOS.length - 1)] = System.nanoTime() - tickStart);
    }

    /** One server tick's length, in milliseconds. */
    private static double tick(int t) {
        return TICK_NANOS[t & (TICK_NANOS.length - 1)] / 1.0E6;
    }

    /** Mean, 95th percentile and longest tick, in milliseconds, of the server ticks numbered [from, to). */
    private static String window(int from, int to) {
        List<Long> ticks = new ArrayList<>();
        for (int t = from; t < to; t++) {
            ticks.add(TICK_NANOS[t & (TICK_NANOS.length - 1)]);
        }
        if (ticks.isEmpty()) {
            return "no ticks";
        }
        ticks.sort(Long::compare);
        double mean = ticks.stream().mapToLong(Long::longValue).average().orElse(0) / 1.0E6;
        double p95 = ticks.get(Math.min(ticks.size() - 1, (int) Math.floor(ticks.size() * 0.95))) / 1.0E6;
        double max = ticks.get(ticks.size() - 1) / 1.0E6;
        return String.format(java.util.Locale.ROOT, "mean %.2f ms, 95%% %.2f ms, max %.2f ms over %d ticks", mean, p95, max, ticks.size());
    }

    /** Twelve mobs of every size, from a chicken to a polar bear. */
    private static final List<EntityType<? extends Mob>> DOZEN = List.of(EntityType.CHICKEN, EntityType.RABBIT, EntityType.PIG, EntityType.SHEEP,
            EntityType.COW, EntityType.WOLF, EntityType.VILLAGER, EntityType.ZOMBIE, EntityType.SPIDER, EntityType.HORSE, EntityType.LLAMA,
            EntityType.POLAR_BEAR);

    /** Ticks each measured stretch lasts. */
    private static final int WINDOW = 100;
    /** Ticks before the dozen arrive: the empty arena, for comparison. */
    private static final int BASELINE = 40;
    /** How long a dozen may take to settle and all come to rest. */
    private static final int REST_BY = 1500;
    /** When measuring, the ticks the first dozen has before it is cleared away, and then before the empty arena is timed. */
    private static final int WARM_UP = 100;
    private static final int CLEARED = 60;

    /**
     * A dozen carcasses of mixed sizes, all at once, in a grid of four by three from this corner of the arena: dropped
     * from up to three blocks and knocked over as a kill knocks them.
     */
    private static List<CarcassSavedData.Carcass> dropDozen(GameTestHelper helper, int cornerX, int cornerZ) {
        ServerLevel level = helper.getLevel();
        List<CarcassSavedData.Carcass> dozen = new ArrayList<>();
        for (int i = 0; i < DOZEN.size(); i++) {
            BlockPos at = new BlockPos(cornerX + new int[]{1, 4, 6, 9}[i % 4], 3 + (i % 3), cornerZ + 2 + (i / 4) * 3);
            Mob mob = helper.spawn(DOZEN.get(i), at);
            mob.setYRot(i * 37.0F);
            mob.setYBodyRot(i * 37.0F);
            CarcassSavedData.Carcass carcass = CarcassAssembler.assemble(mob, null);
            mob.discard();
            if (carcass == null) {
                helper.fail("Could not make a carcass of " + DOZEN.get(i));
                return dozen;
            }
            double angle = i * 1.7;
            CarcassAssembler.blow(level, carcass, new Vec3(Math.cos(angle), 0.0, Math.sin(angle)));
            dozen.add(carcass);
        }
        return dozen;
    }

    /**
     * Dozens of carcasses of mixed sizes, all made in the same tick, then left to settle. Measures the server's tick with
     * none, in the tick they are made and the one after, with all of them awake (falling and settling) and with all of
     * them resting; checks that all do come to rest on the floor.
     */
    @GameTest(template = "empty_wide", timeoutTicks = WARM_UP + CLEARED + BASELINE + WINDOW + REST_BY + WINDOW + 40)
    public static void dozenCarcassesAtOnce(GameTestHelper helper) {
        listen();
        ServerLevel level = helper.getLevel();
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        int dozens = Math.max(1, MEASURING);
        int start = MEASURING > 0 ? WARM_UP + CLEARED : 0;
        if (MEASURING > 0) {
            // everything run once before anything is timed: a first dozen, cleared away with what it drops
            List<CarcassSavedData.Carcass> warm = new ArrayList<>();
            helper.runAfterDelay(1, () -> warm.addAll(dropDozen(helper, 0, 0)));
            helper.runAfterDelay(1 + WARM_UP, () -> {
                long cleared = warm.stream().filter(c -> CarcassRot.crumble(level, c)).count();
                AABB arena = new AABB(Vec3.atLowerCornerOf(helper.absolutePos(BlockPos.ZERO)), Vec3.atLowerCornerOf(helper.absolutePos(new BlockPos(33, 12, 33))));
                level.getEntitiesOfClass(ItemEntity.class, arena).forEach(ItemEntity::discard);
                BloodAndBones.LOGGER.info("[dozen] warm-up: {} of {} cleared away", cleared, warm.size());
            });
        }
        List<CarcassSavedData.Carcass> all = new ArrayList<>();
        int[] marks = new int[5];
        helper.runAfterDelay(start + BASELINE, () -> {
            marks[0] = level.getServer().getTickCount() - BASELINE + 5;
            marks[1] = level.getServer().getTickCount();
            for (int d = 0; d < dozens; d++) {
                all.addAll(dropDozen(helper, 16 * (d % 2), 16 * (d / 2)));
            }
            int bodies = all.stream().mapToInt(c -> c.bones.size()).sum();
            BloodAndBones.LOGGER.info("[dozen] {} carcasses awake: {} bodies ({} sub-levels in the world)", all.size(), bodies, container.getAllSubLevels().size());
        });
        int[] restedAt = {-1};
        helper.onEachTick(() -> {
            if (all.isEmpty()) {
                return;
            }
            int now = level.getServer().getTickCount();
            if (marks[2] == 0 && now >= marks[1] + WINDOW) {
                marks[2] = now;
                BloodAndBones.LOGGER.info("[dozen] resting after {} ticks awake: {} of {}", WINDOW, all.stream().filter(c -> c.resting).count(), all.size());
            }
            if (restedAt[0] < 0 && all.stream().allMatch(c -> c.resting)) {
                restedAt[0] = now;
                int bodies = all.stream().mapToInt(c -> c.bones.size()).sum();
                BloodAndBones.LOGGER.info("[dozen] all {} resting {} ticks after they were made: {} bodies ({} sub-levels in the world)", all.size(),
                        now - marks[1], bodies, container.getAllSubLevels().size());
            }
            if (restedAt[0] >= 0 && marks[3] == 0) {
                marks[3] = now + 5;
            }
            if (marks[3] > 0 && now >= marks[3] + WINDOW && marks[4] == 0) {
                marks[4] = now;
                BloodAndBones.LOGGER.info("[dozen] no carcasses: {}", window(marks[0], marks[1]));
                // the tick the game test makes them in (after the physics of that tick), and the first physics step with them
                BloodAndBones.LOGGER.info("[dozen] {} made: the tick they are made in {} ms, the next {} ms", all.size(),
                        String.format(java.util.Locale.ROOT, "%.2f", tick(marks[1])), String.format(java.util.Locale.ROOT, "%.2f", tick(marks[1] + 1)));
                BloodAndBones.LOGGER.info("[dozen] {} awake, first {} ticks after that: {}", all.size(), WINDOW, window(marks[1] + 2, marks[1] + 2 + WINDOW));
                BloodAndBones.LOGGER.info("[dozen] {} settling until all rest: {}", all.size(), window(marks[1] + 2, restedAt[0]));
                BloodAndBones.LOGGER.info("[dozen] {} resting: {}", all.size(), window(marks[3], marks[3] + WINDOW));
                double floor = helper.absolutePos(new BlockPos(0, 2, 0)).getY() - 0.5;
                for (CarcassSavedData.Carcass carcass : all) {
                    Vector3d p = CarcassAssembler.boneWorldPosition(level, carcass, carcass.rootBone);
                    if (p == null || p.y < floor) {
                        helper.fail("The " + carcass.entity + " carcass should lie on the floor, is at " + p);
                        return;
                    }
                }
                helper.succeed();
            }
            if (restedAt[0] < 0 && now > marks[1] + REST_BY) {
                helper.fail("Carcasses should all come to rest within " + REST_BY + " ticks; still awake: "
                        + all.stream().filter(c -> !c.resting).map(c -> c.entity.getPath()).toList());
            }
        });
    }

    /** How long the hung dozen is timed: a minute when measuring, five seconds in the full suite. */
    private static final int HUNG_WINDOW = MEASURING > 0 ? 1200 : WINDOW;
    private static final int HUNG_WINDOW_MAX = 1200;

    /**
     * A dozen carcasses of every size, each hung on its own Shackle Hook, from a chicken to a polar bear. A hung carcass
     * never rests (CarcassRest#isHeld), and the hook turns it belly-out with a spring every physics step, so all its
     * bodies stay awake for as long as it hangs. Measures the server's tick with none and with all twelve hanging;
     * checks that each is hoisted up to its hook without being flung, and hangs there.
     */
    @GameTest(template = "empty_wide", timeoutTicks = 200 + HUNG_WINDOW_MAX + 40)
    public static void dozenHungCarcasses(GameTestHelper helper) {
        listen();
        ServerLevel level = helper.getLevel();
        int[] hookX = {4, 12, 20, 28};
        int[] hookZ = {5, 16, 27};
        List<BlockPos> hooks = new ArrayList<>();
        for (int i = 0; i < DOZEN.size(); i++) {
            BlockPos hook = new BlockPos(hookX[i % 4], 6, hookZ[i / 4]);
            helper.setBlock(hook.above(), Blocks.STONE);
            helper.setBlock(hook, BBBlocks.SHACKLE_HOOK.get().defaultBlockState().setValue(ShackleHookBlock.FACING, Direction.UP));
            hooks.add(hook);
        }
        List<CarcassSavedData.Carcass> dozen = new ArrayList<>();
        int[] marks = new int[3];
        double[] fastest = {0.0};
        List<Vector3d> last = new ArrayList<>();
        helper.runAfterDelay(40, () -> {
            marks[0] = level.getServer().getTickCount();
            for (int i = 0; i < DOZEN.size(); i++) {
                // lying two blocks off its hook
                Mob mob = helper.spawn(DOZEN.get(i), hooks.get(i).offset(2, -4, 0));
                CarcassSavedData.Carcass carcass = CarcassAssembler.assemble(mob, null);
                mob.discard();
                if (carcass == null) {
                    helper.fail("Could not make a carcass of " + DOZEN.get(i));
                    return;
                }
                dozen.add(carcass);
            }
        });
        helper.runAfterDelay(80, () -> {
            // a stand-in player in the middle hooks each in turn and hangs it on its hook
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BBItems.MEAT_HOOK.get()));
            player.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(16, 2, 16))));
            player.setOldPosAndRot();
            ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
            for (int i = 0; i < dozen.size(); i++) {
                CarcassSavedData.Carcass carcass = dozen.get(i);
                boolean hooked = false;
                for (UUID id : List.copyOf(carcass.bones.values())) {
                    if (container.getSubLevel(id) instanceof ServerSubLevel body && !body.isRemoved()
                            && CarcassDrag.start(level, player, body.getPlot().getCenterBlock(), null)) {
                        hooked = true;
                        break;
                    }
                }
                if (!hooked || !((ShackleHookBlockEntity) level.getBlockEntity(helper.absolutePos(hooks.get(i)))).hang(level, player)) {
                    helper.fail("Could not hang the " + carcass.entity.getPath());
                    return;
                }
            }
        });
        helper.onEachTick(() -> {
            if (helper.getTick() < 80 || dozen.isEmpty()) {
                return;
            }
            // how fast any torso moves while it goes up
            for (int i = 0; i < dozen.size(); i++) {
                Vector3d at = CarcassAssembler.boneWorldPosition(level, dozen.get(i), dozen.get(i).rootBone);
                if (at == null) {
                    continue;
                }
                if (last.size() <= i) {
                    last.add(at);
                    continue;
                }
                fastest[0] = Math.max(fastest[0], at.distance(last.get(i)) * 20.0);
                last.set(i, at);
            }
            if (helper.getTick() == 200) {
                marks[1] = level.getServer().getTickCount();
                StringBuilder gaps = new StringBuilder();
                List<String> offTip = new ArrayList<>();
                double spin = 0.0;
                for (int i = 0; i < dozen.size(); i++) {
                    CarcassSavedData.Carcass carcass = dozen.get(i);
                    ShackleHookBlockEntity hook = (ShackleHookBlockEntity) level.getBlockEntity(helper.absolutePos(hooks.get(i)));
                    Vec3 tip = ShackleHookBlock.tip(helper.absolutePos(hooks.get(i)), hook.getBlockState());
                    ServerSubLevel torso = SubLevelContainer.getContainer(level).getSubLevel(carcass.bones.get(carcass.rootBone)) instanceof ServerSubLevel b ? b : null;
                    double gap = torso == null ? Double.NaN : torso.logicalPose().transformPosition(hook.hookedAnchor(), new Vector3d()).distance(tip.x, tip.y, tip.z);
                    gaps.append(String.format(java.util.Locale.ROOT, " %s %.2f (%.3f of %.3f, turning %.2f)", carcass.entity.getPath(), gap,
                            torso == null ? Double.NaN : torso.getMassTracker().getMass(), torso == null ? Double.NaN : ShackleHookBlockEntity.hoistedMass(level, carcass.id, torso),
                            torso == null ? Double.NaN : SubLevelContainer.getContainer(level).physicsSystem().getPhysicsHandle(torso).getAngularVelocity(new Vector3d()).length()));
                    if (!hook.isOccupied() || !(gap <= 0.35)) {
                        offTip.add(carcass.entity.getPath() + " " + gap);
                    }
                    if (torso != null) {
                        spin = Math.max(spin, SubLevelContainer.getContainer(level).physicsSystem().getPhysicsHandle(torso).getAngularVelocity(new Vector3d()).length());
                    }
                }
                BloodAndBones.LOGGER.info("[hung] fastest torso {} blocks a second, fastest turning now {} radians a second; from the tips (torso mass of carcass mass, radians a second):{}",
                        fastest[0], spin, gaps);
                if (!offTip.isEmpty()) {
                    helper.fail("Each should hang from its hook's tip; these are off by so many blocks: " + offTip);
                    return;
                }
                if (spin > 2.0) {
                    helper.fail("A hung torso should hang still, one turns at " + spin + " radians a second");
                    return;
                }
                if (fastest[0] > 10.0) {
                    helper.fail("Hoisting should not fling a carcass: a torso moved at " + fastest[0] + " blocks a second");
                }
            }
            if (marks[1] > 0 && level.getServer().getTickCount() == marks[1] + HUNG_WINDOW) {
                int bodies = dozen.stream().mapToInt(c -> c.bones.size()).sum();
                long resting = dozen.stream().filter(c -> c.resting).count();
                BloodAndBones.LOGGER.info("[hung] no carcasses: {}", window(marks[0] - 35, marks[0]));
                BloodAndBones.LOGGER.info("[hung] {} hanging, {} bodies, {} resting, for {} ticks: {}", dozen.size(), bodies, resting, HUNG_WINDOW,
                        window(marks[1], marks[1] + HUNG_WINDOW));
                // let them down, so they rest rather than stay awake for the rest of the run
                for (BlockPos hook : hooks) {
                    ((ShackleHookBlockEntity) level.getBlockEntity(helper.absolutePos(hook))).release(level);
                }
                helper.succeed();
            }
        });
    }
}
