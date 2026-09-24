package com.avicagan.bloodandbones.registry;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.item.CarcassPieceItem;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class BBDataComponents {
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, BloodAndBones.MOD_ID);

    /** Which animal, which part, what it wore and how fresh: a carried piece of carcass. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CarcassPieceItem.Piece>> PIECE = COMPONENTS.registerComponentType("piece",
            builder -> builder.persistent(CarcassPieceItem.Piece.CODEC).networkSynchronized(ByteBufCodecs.fromCodec(CarcassPieceItem.Piece.CODEC)));

    /** Game time a blade last drew blood; it shows bloody for a while after (Blood#BLOODY_TICKS). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Long>> BLOODIED_AT = COMPONENTS.registerComponentType("bloodied_at",
            builder -> builder.persistent(com.mojang.serialization.Codec.LONG).networkSynchronized(ByteBufCodecs.VAR_LONG));

    /** The fluid in a Fluid Backtank. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<net.neoforged.neoforge.fluids.SimpleFluidContent>> FLUID = COMPONENTS.registerComponentType("fluid",
            builder -> builder.persistent(net.neoforged.neoforge.fluids.SimpleFluidContent.CODEC).networkSynchronized(net.neoforged.neoforge.fluids.SimpleFluidContent.STREAM_CODEC));

    /** A minion being built on a carcass body: what it has so far. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<com.avicagan.bloodandbones.minion.MinionFrame.Frame>> FRAME = COMPONENTS.registerComponentType("minion_frame",
            builder -> builder.persistent(com.avicagan.bloodandbones.minion.MinionFrame.Frame.CODEC)
                    .networkSynchronized(ByteBufCodecs.fromCodecWithRegistries(com.avicagan.bloodandbones.minion.MinionFrame.Frame.CODEC)));

    /** How far an organic prosthetic has rotted from use, 0 to Necrosis.MAX. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> NECROSIS = COMPONENTS.registerComponentType("necrosis",
            builder -> builder.persistent(com.mojang.serialization.Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    /** The modules in a brass limb's slots, in slot order. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<java.util.List<com.avicagan.bloodandbones.cyber.Module>>> MODULES = COMPONENTS.registerComponentType("modules",
            builder -> builder.persistent(com.avicagan.bloodandbones.cyber.Module.CODEC.listOf())
                    .networkSynchronized(com.avicagan.bloodandbones.cyber.Module.STREAM_CODEC.apply(ByteBufCodecs.list())));

    private BBDataComponents() {
    }
}
