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

    /** The invisible seat a patient lies on at the Surgery Table. */
    public static final EntityEntry<com.avicagan.bloodandbones.body.SurgerySeatEntity> SURGERY_SEAT = BloodAndBones.REGISTRATE
            .<com.avicagan.bloodandbones.body.SurgerySeatEntity>entity("surgery_seat", com.avicagan.bloodandbones.body.SurgerySeatEntity::new, MobCategory.MISC)
            .properties(b -> b.sized(0.25f, com.avicagan.bloodandbones.body.SurgerySeatEntity.HEIGHT).clientTrackingRange(8).fireImmune().noSummon())
            .renderer(() -> net.minecraft.client.renderer.entity.NoopRenderer::new)
            .lang("Surgery Table")
            .register();

    public static void register() {
    }
}
