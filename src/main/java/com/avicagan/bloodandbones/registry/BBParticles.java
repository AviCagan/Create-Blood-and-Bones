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
    /** The same drop of a nether mob's Soul Blood, dark teal. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SOUL_BLOOD_DROP = PARTICLES.register("soul_blood_drop", () -> new SimpleParticleType(false));

    /** A scrap of meat thrown off a carcass being cut up. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> GIB = PARTICLES.register("gib", () -> new SimpleParticleType(false));

    /** A fly, drawn to rotting meat. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> FLY = PARTICLES.register("fly", () -> new SimpleParticleType(false));

    private BBParticles() {
    }
}
