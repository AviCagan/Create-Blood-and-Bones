package com.avicagan.bloodandbones.gametest;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.carcass.CarcassAssembler;
import com.avicagan.bloodandbones.carcass.CarcassSavedData;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

/**
 * What carcasses cost the server (brief: "cheap enough that a dozen at once is fine"). The numbers only mean
 * something when this test runs on its own ({@code -Dbloodandbones.debug.only=dozenCarcasses}); with
 * {@code -Dbloodandbones.debug.repeat=N} N copies run side by side, so 12 N carcasses at once. In the full suite
 * it still checks that a dozen fall, settle and come to rest.
 */
@GameTestHolder(BloodAndBones.MOD_ID)
@PrefixGameTestTemplate(false)
public class CostTests {
    /** Server tick lengths in nanoseconds, by tick number, the last few thousand ticks. */
    private static final long[] TICK_NANOS = new long[8192];
    private static long tickStart;
    private static boolean listening;

    private static void listen() {
        if (listening) {
            return;
        }
        listening = true;
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Pre event) -> tickStart = System.nanoTime());
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) ->
                TICK_NANOS[event.getServer().getTickCount() & (TICK_NANOS.length - 1)] = System.nanoTime() - tickStart);
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

    /**
     * A dozen carcasses of mixed sizes, all at once: dropped from up to three blocks and knocked over as a kill
     * knocks them, then left to settle. Measures the server's tick with none, with all twelve awake (falling and
     * settling) and with all twelve resting; checks that all twelve do come to rest on the floor.
     */
    @GameTest(template = "empty", timeoutTicks = BASELINE + WINDOW + REST_BY + WINDOW + 40)
    public static void dozenCarcassesAtOnce(GameTestHelper helper) {
        listen();
        ServerLevel level = helper.getLevel();
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        List<CarcassSavedData.Carcass> dozen = new ArrayList<>();
        int[] marks = new int[5];
        helper.runAfterDelay(BASELINE, () -> {
            marks[0] = level.getServer().getTickCount() - BASELINE + 5;
            marks[1] = level.getServer().getTickCount();
            for (int i = 0; i < DOZEN.size(); i++) {
                // a grid of four by three, each dropped from its own height
                BlockPos at = new BlockPos(new int[]{1, 4, 6, 9}[i % 4], 3 + (i % 3), 2 + (i / 4) * 3);
                Mob mob = helper.spawn(DOZEN.get(i), at);
                mob.setYRot(i * 37.0F);
                mob.setYBodyRot(i * 37.0F);
                CarcassSavedData.Carcass carcass = CarcassAssembler.assemble(mob, null);
                mob.discard();
                if (carcass == null) {
                    helper.fail("Could not make a carcass of " + DOZEN.get(i));
                    return;
                }
                double angle = i * 1.7;
                CarcassAssembler.shove(level, carcass, new Vec3(Math.cos(angle), 0.0, Math.sin(angle)));
                dozen.add(carcass);
            }
            int bodies = dozen.stream().mapToInt(c -> c.bones.size()).sum();
            BloodAndBones.LOGGER.info("[dozen] {} carcasses awake: {} bodies ({} sub-levels in the world)", dozen.size(), bodies, container.getAllSubLevels().size());
        });
        int[] restedAt = {-1};
        helper.onEachTick(() -> {
            if (dozen.isEmpty()) {
                return;
            }
            int now = level.getServer().getTickCount();
            if (marks[2] == 0 && now >= marks[1] + WINDOW) {
                marks[2] = now;
                BloodAndBones.LOGGER.info("[dozen] resting after {} ticks awake: {} of {}", WINDOW, dozen.stream().filter(c -> c.resting).count(), dozen.size());
            }
            if (restedAt[0] < 0 && dozen.stream().allMatch(c -> c.resting)) {
                restedAt[0] = now;
                int bodies = dozen.stream().mapToInt(c -> c.bones.size()).sum();
                BloodAndBones.LOGGER.info("[dozen] all {} resting {} ticks after they were made: {} bodies ({} sub-levels in the world)", dozen.size(),
                        now - marks[1], bodies, container.getAllSubLevels().size());
            }
            if (restedAt[0] >= 0 && marks[3] == 0) {
                marks[3] = now + 5;
            }
            if (marks[3] > 0 && now >= marks[3] + WINDOW && marks[4] == 0) {
                marks[4] = now;
                BloodAndBones.LOGGER.info("[dozen] no carcasses: {}", window(marks[0], marks[1]));
                BloodAndBones.LOGGER.info("[dozen] {} awake, first {} ticks: {}", dozen.size(), WINDOW, window(marks[1] + 1, marks[1] + 1 + WINDOW));
                BloodAndBones.LOGGER.info("[dozen] {} settling until all rest: {}", dozen.size(), window(marks[1] + 1, restedAt[0]));
                BloodAndBones.LOGGER.info("[dozen] {} resting: {}", dozen.size(), window(marks[3], marks[3] + WINDOW));
                double floor = helper.absolutePos(new BlockPos(0, 2, 0)).getY() - 0.5;
                for (CarcassSavedData.Carcass carcass : dozen) {
                    Vector3d p = CarcassAssembler.boneWorldPosition(level, carcass, carcass.rootBone);
                    if (p == null || p.y < floor) {
                        helper.fail("The " + carcass.entity + " carcass should lie on the floor, is at " + p);
                        return;
                    }
                }
                helper.succeed();
            }
            if (restedAt[0] < 0 && now > marks[1] + REST_BY) {
                helper.fail("A dozen carcasses should all come to rest within " + REST_BY + " ticks; still awake: "
                        + dozen.stream().filter(c -> !c.resting).map(c -> c.entity.getPath()).toList());
            }
        });
    }
}
