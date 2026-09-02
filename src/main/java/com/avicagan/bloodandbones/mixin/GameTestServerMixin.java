package com.avicagan.bloodandbones.mixin;

import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.util.RandomSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Vanilla's headless test server places tests at random coordinates up to fifteen million blocks out.
 * Sable's physics engine works in single precision, which cannot represent half a block out there, so
 * every physics test would be garbage. Pin the tests near the origin instead. Only loaded by the test server.
 */
@Mixin(GameTestServer.class)
public class GameTestServerMixin {
    @Redirect(method = "startTests", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/RandomSource;nextIntBetweenInclusive(II)I"))
    private int bloodandbones$nearOrigin(RandomSource random, int min, int max) {
        return random.nextIntBetweenInclusive(-64, 64);
    }
}
