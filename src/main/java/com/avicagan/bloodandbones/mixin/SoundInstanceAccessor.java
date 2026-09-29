package com.avicagan.bloodandbones.mixin;

import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A sound's volume and pitch as it was asked to play, before its sounds.json entry scales them (client/BloodlessSounds). */
@Mixin(AbstractSoundInstance.class)
public interface SoundInstanceAccessor {
    @Accessor("volume")
    float bloodandbones$volume();

    @Accessor("pitch")
    float bloodandbones$pitch();
}
