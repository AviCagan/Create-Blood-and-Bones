package com.avicagan.bloodandbones.minion;

import com.avicagan.bloodandbones.BloodAndBones;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.Optional;

/** The minion's build as synced entity data, so every client draws the same body. */
public final class MinionSerializers {
    private static final DeferredRegister<EntityDataSerializer<?>> SERIALIZERS = DeferredRegister.create(NeoForgeRegistries.Keys.ENTITY_DATA_SERIALIZERS, BloodAndBones.MOD_ID);

    public static final DeferredHolder<EntityDataSerializer<?>, EntityDataSerializer<Optional<MinionBuild>>> BUILD = SERIALIZERS.register("minion_build",
            () -> EntityDataSerializer.forValueType(ByteBufCodecs.optional(MinionBuild.STREAM_CODEC)));

    private MinionSerializers() {
    }

    public static void register(IEventBus modBus) {
        SERIALIZERS.register(modBus);
    }
}
