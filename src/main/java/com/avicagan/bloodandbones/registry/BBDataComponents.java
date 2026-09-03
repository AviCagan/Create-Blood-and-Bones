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

    private BBDataComponents() {
    }
}
