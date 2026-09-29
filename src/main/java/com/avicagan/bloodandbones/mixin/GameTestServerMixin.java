package com.avicagan.bloodandbones.mixin;

import com.avicagan.bloodandbones.gametest.RigComparison;
import net.minecraft.gametest.framework.GameTestBatch;
import net.minecraft.gametest.framework.GameTestBatchFactory;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Vanilla's headless test server places tests at random coordinates up to fifteen million blocks out.
 * Sable's physics engine works in single precision, which cannot represent half a block out there, so
 * every physics test would be garbage. Pin the tests near the origin instead. Only loaded by the test server.
 * <p>
 * For chasing a test that fails now and then: {@code -Dbloodandbones.debug.only=name,name} runs only the tests whose
 * names contain one of those (any case), and {@code -Dbloodandbones.debug.repeat=N} runs each of them N times.
 */
@Mixin(GameTestServer.class)
public class GameTestServerMixin {
    @Redirect(method = "startTests", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/RandomSource;nextIntBetweenInclusive(II)I"))
    private int bloodandbones$nearOrigin(RandomSource random, int min, int max) {
        return random.nextIntBetweenInclusive(-64, 64);
    }

    @Redirect(method = "initServer", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/gametest/framework/GameTestBatchFactory;fromTestFunction(Ljava/util/Collection;Lnet/minecraft/server/level/ServerLevel;)Ljava/util/Collection;"))
    private Collection<GameTestBatch> bloodandbones$only(Collection<TestFunction> functions, ServerLevel level) {
        String only = System.getProperty("bloodandbones.debug.only", "").toLowerCase(Locale.ROOT);
        int repeat = Math.max(1, Integer.getInteger("bloodandbones.debug.repeat", 1));
        if (only.isBlank() && repeat == 1) {
            return costLast(GameTestBatchFactory.fromTestFunction(functions, level));
        }
        List<TestFunction> chosen = new ArrayList<>();
        for (int i = 0; i < repeat; i++) {
            for (TestFunction function : functions) {
                if (only.isBlank() || List.of(only.split(",")).stream().anyMatch(name -> function.testName().toLowerCase(Locale.ROOT).contains(name.trim()))) {
                    chosen.add(function);
                }
            }
        }
        return costLast(GameTestBatchFactory.fromTestFunction(chosen, level));
    }

    /**
     * A batch named {@value #ALONE_FIRST} (a test that changes what every test sees, as one that takes a mob's rig away)
     * runs before any other, so nothing any other test made is in the world while it runs.
     */
    @Unique
    private static final String ALONE_FIRST = "bloodandbones_alone_first";

    /**
     * The physics measurement's cost batches (RigComparisonTests, only there with its switch on) time the server's ticks,
     * so they go last, one after another with nothing else running beside them, in RigComparison's order.
     */
    @Unique
    private static Collection<GameTestBatch> costLast(Collection<GameTestBatch> batches) {
        List<String> order = RigComparison.COST_ORDER;
        List<GameTestBatch> out = new ArrayList<>();
        for (GameTestBatch batch : batches) {
            if (batch.name().startsWith(ALONE_FIRST + ":")) {
                out.add(batch);
            }
        }
        batches = batches.stream().filter(batch -> !batch.name().startsWith(ALONE_FIRST + ":")).toList();
        List<GameTestBatch> cost = new ArrayList<>();
        for (GameTestBatch batch : batches) {
            (order.stream().anyMatch(name -> batch.name().startsWith(name + ":")) ? cost : out).add(batch);
        }
        cost.sort(Comparator.comparingInt((GameTestBatch batch) -> order.indexOf(batch.name().substring(0, batch.name().indexOf(':'))))
                .thenComparing(GameTestBatch::name));
        out.addAll(cost);
        return out;
    }
}
