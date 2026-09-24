package com.avicagan.bloodandbones.cyber;

import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** A Rotational Coupler's generator: the owner's arm turning a machine, at the RPM their throttle gives. */
public class CouplerBlockEntity extends GeneratingKineticBlockEntity {
    @Nullable
    private UUID owner;
    /** The owner's entity id, for clients to draw the shaft back to their arm. */
    private int ownerId = -1;
    private int rpm;
    /** Ticks since it appeared, for the shaft reaching out. */
    public int age;

    public CouplerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** Driven by this owner (a player's arm, a brass minion) at this RPM. */
    void drive(net.minecraft.world.entity.Entity driver, int rpm) {
        boolean changed = this.rpm != rpm || !driver.getUUID().equals(owner);
        owner = driver.getUUID();
        if (changed) {
            this.rpm = rpm;
            ownerId = driver.getId();
            updateGeneratedRotation();
            sendData();
        } else if (ownerId != driver.getId()) {
            // the same owner under a new entity id (it and this were unloaded and loaded again): clients draw the shaft to it
            ownerId = driver.getId();
            sendData();
        }
    }

    public int ownerId() {
        return ownerId;
    }

    @Nullable
    public UUID owner() {
        return owner;
    }

    @Override
    public float getGeneratedSpeed() {
        return rpm == 0 ? 0.0F : convertToDirection(rpm, getBlockState().getValue(CouplerBlock.FACING));
    }

    @Override
    public float calculateAddedStressCapacity() {
        lastCapacityProvided = Coupler.CAPACITY;
        return Coupler.CAPACITY;
    }

    @Override
    public void tick() {
        super.tick();
        age++;
        // gone with its owner's hold: let go, walked off, a reload
        if (level != null && !level.isClientSide && age > 2 && !Coupler.owns(owner, level, worldPosition)) {
            level.removeBlock(worldPosition, false);
        }
    }

    @Override
    public void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putInt("Rpm", rpm);
        tag.putInt("OwnerId", ownerId);
        if (owner != null && !clientPacket) {
            tag.putUUID("Owner", owner);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        rpm = tag.getInt("Rpm");
        ownerId = tag.getInt("OwnerId");
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : owner;
    }
}
