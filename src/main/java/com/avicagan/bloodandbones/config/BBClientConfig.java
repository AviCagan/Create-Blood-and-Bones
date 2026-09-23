package com.avicagan.bloodandbones.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client-side presentation settings. Nothing here changes gameplay. A server can force bloodless mode on
 * with the bloodandbonesBloodless game rule.
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

    /** The server's bloodandbonesBloodless game rule, as it last told us; false when not connected. */
    private static volatile boolean serverForced;

    /** Bloodless presentation: chosen here, or forced by the server. */
    public static boolean bloodless() {
        if (serverForced) {
            return true;
        }
        try {
            return SPEC.isLoaded() && BLOODLESS_MODE.get();
        } catch (IllegalStateException e) {
            return false;
        }
    }

    /** Client: the server's rule arrived (or we left it). Redraws the world when the answer changes. */
    public static void setServerForced(boolean forced) {
        boolean before = bloodless();
        serverForced = forced;
        if (before != bloodless()) {
            redraw();
        }
    }

    /** Blood stains are baked into chunk meshes, so a change needs the chunks rebuilt; names are reworded too. */
    public static void redraw() {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        mc.execute(() -> {
            com.avicagan.bloodandbones.client.BloodlessLanguage.refresh();
            if (mc.level != null) {
                mc.levelRenderer.allChanged();
            }
        });
    }
}
