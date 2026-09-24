package com.avicagan.bloodandbones.registry;

import com.avicagan.bloodandbones.BloodAndBones;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The mod's own sounds, so subtitles say what happened ("Carcass thuds", not "Slime squishes"). Each
 * plays vanilla sounds (see assets/bloodandbones/sounds.json), and a resource pack can give them new ones.
 */
public final class BBSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, BloodAndBones.MOD_ID);

    public static final DeferredHolder<SoundEvent, SoundEvent> CARCASS_CUT = sound("carcass.cut", "Meat is cut");
    public static final DeferredHolder<SoundEvent, SoundEvent> CARCASS_SEVER = sound("carcass.sever", "Bone snaps");
    public static final DeferredHolder<SoundEvent, SoundEvent> CARCASS_SKIN = sound("carcass.skin", "Hide peels");
    public static final DeferredHolder<SoundEvent, SoundEvent> CARCASS_PICK_UP = sound("carcass.pick_up", "Meat is picked up");
    public static final DeferredHolder<SoundEvent, SoundEvent> CARCASS_THUD = sound("carcass.thud", "Carcass thuds");
    public static final DeferredHolder<SoundEvent, SoundEvent> CARCASS_CLATTER = sound("carcass.clatter", "Bones clatter");
    public static final DeferredHolder<SoundEvent, SoundEvent> CARCASS_CRUMBLE = sound("carcass.crumble", "Carcass falls apart");
    public static final DeferredHolder<SoundEvent, SoundEvent> MACHINE_BLADE = sound("machine.blade", "Blade falls");
    public static final DeferredHolder<SoundEvent, SoundEvent> CYBERNETIC_SPOOL = sound("cybernetic.spool", "Cybernetic spools up");
    public static final DeferredHolder<SoundEvent, SoundEvent> CYBERNETIC_CHOKE = sound("cybernetic.choke", "Cybernetic sputters");

    private BBSounds() {
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name, String subtitle) {
        BloodAndBones.REGISTRATE.addRawLang("subtitles." + BloodAndBones.MOD_ID + "." + name, subtitle);
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(BloodAndBones.asResource(name)));
    }

    public static void register(IEventBus modBus) {
        SOUNDS.register(modBus);
    }
}
