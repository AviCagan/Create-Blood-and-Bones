package com.avicagan.bloodandbones.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client-side presentation settings. Nothing here changes gameplay.
 */
public class BBClientConfig {
    public static final ModConfigSpec SPEC;
    /** Hide blood, gore and wet textures. Carcasses and machines still work exactly the same. */
    public static final ModConfigSpec.BooleanValue BLOODLESS_MODE;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("presentation");
        BLOODLESS_MODE = builder
                .comment("Bloodless mode: hides blood, gore and wet textures. Purely visual, gameplay is unchanged.")
                .define("bloodless_mode", false);
        builder.pop();
        SPEC = builder.build();
    }

    public static boolean bloodless() {
        try {
            return SPEC.isLoaded() && BLOODLESS_MODE.get();
        } catch (IllegalStateException e) {
            return false;
        }
    }
}
