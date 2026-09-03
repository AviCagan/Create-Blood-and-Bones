package com.avicagan.bloodandbones.registry;

import com.avicagan.bloodandbones.BloodAndBones;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class BBParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, BloodAndBones.MOD_ID);

    /** A drop of blood: falls, lands, lies there a moment. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> BLOOD_DROP = PARTICLES.register("blood_drop", () -> new SimpleParticleType(false));

    private BBParticles() {
    }
}
