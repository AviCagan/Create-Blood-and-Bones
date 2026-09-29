package com.avicagan.bloodandbones.mixin;

import net.minecraft.gametest.framework.GameTestBatch;
import net.minecraft.gametest.framework.GameTestBatchFactory;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ArrayList;
import java.util.Collection;
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
            return GameTestBatchFactory.fromTestFunction(functions, level);
        }
        List<TestFunction> chosen = new ArrayList<>();
        for (int i = 0; i < repeat; i++) {
            for (TestFunction function : functions) {
                if (only.isBlank() || List.of(only.split(",")).stream().anyMatch(name -> function.testName().toLowerCase(Locale.ROOT).contains(name.trim()))) {
                    chosen.add(function);
                }
            }
        }
        return GameTestBatchFactory.fromTestFunction(chosen, level);
    }
}
