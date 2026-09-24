package com.avicagan.bloodandbones.minion;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.registry.BBBlocks;
import com.simibubi.create.api.registry.CreateRegistries;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPoint;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPointType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The Charging Cradle as a Mechanical Arm point (docs/PARTS-AND-TRAITS.md section 6.7), a type of Create's
 * {@code arm_interaction_point_type} registry as Create's own (AllArmInteractionPointTypes) are. The arm goes through the
 * cradle's item handler, the one funnels and hoppers use: set to put things there, it puts full Soul Canisters and brass
 * sheets in; set to take from it, it takes the empties out; nothing else either way. It reaches for the cradle's top,
 * where the canisters sit.
 */
public class CradleArmPoint extends ArmInteractionPoint {
    private static final DeferredRegister<ArmInteractionPointType> TYPES = DeferredRegister.create(CreateRegistries.ARM_INTERACTION_POINT_TYPE, BloodAndBones.MOD_ID);
    public static final DeferredHolder<ArmInteractionPointType, Type> TYPE = TYPES.register("charging_cradle", Type::new);

    public CradleArmPoint(ArmInteractionPointType type, Level level, BlockPos pos, BlockState state) {
        super(type, level, pos, state);
    }

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
    }

    @Override
    protected Vec3 getInteractionPositionVector() {
        return Vec3.atLowerCornerOf(pos).add(0.5, 10.0 / 16.0, 0.5);
    }

    public static class Type extends ArmInteractionPointType {
        @Override
        public boolean canCreatePoint(Level level, BlockPos pos, BlockState state) {
            return BBBlocks.CHARGING_CRADLE.has(state);
        }

        @Override
        public ArmInteractionPoint createPoint(Level level, BlockPos pos, BlockState state) {
            return new CradleArmPoint(this, level, pos, state);
        }
    }
}
