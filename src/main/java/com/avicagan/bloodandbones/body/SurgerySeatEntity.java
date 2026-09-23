package com.avicagan.bloodandbones.body;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * What a patient lies on: an invisible seat on the Surgery Table's top. It goes as soon as nobody is on it
 * or the table is gone. (Drawn sitting for now.)
 */
public class SurgerySeatEntity extends Entity {
    /** Height of the seat's box: the rider's hips come to its top, which is the table's top. */
    public static final float HEIGHT = 0.25F;
    /** The table's top, above its block. */
    public static final double TOP = 15.0 / 16.0;

    public SurgerySeatEntity(EntityType<?> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public static SurgerySeatEntity at(EntityType<? extends SurgerySeatEntity> type, Level level, BlockPos table) {
        SurgerySeatEntity seat = new SurgerySeatEntity(type, level);
        seat.setPos(table.getX() + 0.5, table.getY() + TOP - HEIGHT, table.getZ() + 0.5);
        return seat;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && (!isVehicle() || !(level().getBlockState(blockPosition()).getBlock() instanceof SurgeryTableBlock))) {
            discard();
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
