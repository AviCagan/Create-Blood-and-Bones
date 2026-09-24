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

    /** A length of Gut Chain hung from a Create chain conveyor, riding it. */
    public static final EntityEntry<com.avicagan.bloodandbones.decoration.HangingGutChainEntity> HANGING_GUT_CHAIN = BloodAndBones.REGISTRATE
            .<com.avicagan.bloodandbones.decoration.HangingGutChainEntity>entity("hanging_gut_chain", com.avicagan.bloodandbones.decoration.HangingGutChainEntity::new, MobCategory.MISC)
            .properties(b -> b.sized(0.4f, 0.4f).clientTrackingRange(10).updateInterval(1).fireImmune().noSummon())
            .renderer(() -> com.avicagan.bloodandbones.decoration.HangingGutChainRenderer::new)
            .lang("Hanging Gut Chain")
            .register();

    /** The invisible seat a patient lies on at the Surgery Table. */
    public static final EntityEntry<com.avicagan.bloodandbones.body.SurgerySeatEntity> SURGERY_SEAT = BloodAndBones.REGISTRATE
            .<com.avicagan.bloodandbones.body.SurgerySeatEntity>entity("surgery_seat", com.avicagan.bloodandbones.body.SurgerySeatEntity::new, MobCategory.MISC)
            .properties(b -> b.sized(0.25f, com.avicagan.bloodandbones.body.SurgerySeatEntity.HEIGHT).clientTrackingRange(8).fireImmune().noSummon())
            .renderer(() -> net.minecraft.client.renderer.entity.NoopRenderer::new)
            .lang("Surgery Table")
            .register();

    /** A body stitched together from carcass pieces on the Surgery Table and woken with blood. */
    public static final EntityEntry<com.avicagan.bloodandbones.minion.MinionEntity> MINION = BloodAndBones.REGISTRATE
            .<com.avicagan.bloodandbones.minion.MinionEntity>entity("minion", com.avicagan.bloodandbones.minion.MinionEntity::new, MobCategory.MISC)
            .properties(b -> b.sized(0.6f, 1.8f).clientTrackingRange(10))
            .attributes(com.avicagan.bloodandbones.minion.MinionEntity::createAttributes)
            .renderer(() -> com.avicagan.bloodandbones.client.StitchedMinionRenderer::new)
            .lang("Minion")
            .register();

    public static void register() {
    }
}
