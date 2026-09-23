package com.avicagan.bloodandbones.registry;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.carcass.trolley.ShackleTrolleyEntity;
import com.avicagan.bloodandbones.carcass.trolley.ShackleTrolleyRenderer;
import com.tterrag.registrate.util.entry.EntityEntry;
import net.minecraft.world.entity.MobCategory;

public class BBEntities {
    /** The trolley that carries a hanging carcass along a Create chain conveyor. */
    public static final EntityEntry<ShackleTrolleyEntity> SHACKLE_TROLLEY = BloodAndBones.REGISTRATE
            .<ShackleTrolleyEntity>entity("shackle_trolley", ShackleTrolleyEntity::new, MobCategory.MISC)
            .properties(b -> b.sized(0.4f, 0.4f).clientTrackingRange(10).updateInterval(1).fireImmune().noSummon())
            .renderer(() -> ShackleTrolleyRenderer::new)
            .lang("Shackle Trolley")
            .register();

    public static void register() {
    }
}
