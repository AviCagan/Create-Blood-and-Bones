package com.avicagan.bloodandbones.registry;

import com.avicagan.bloodandbones.BloodAndBones;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.block.SoundType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.util.DeferredSoundType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The mod's own sounds, so subtitles say what happened ("Carcass thuds", not "Slime squishes"). Each
 * plays vanilla sounds (see assets/bloodandbones/sounds.json), and a resource pack can give them new ones.
 *
 * <p>Every wet, fleshy sound has a bloodless twin ({@code bloodless.<name>}): a clank of metal, vanilla sounds
 * pitched in sounds.json, for real files to replace later. The game always plays the wet one; a client in bloodless
 * mode hears the twin instead, with its layer on top where it has one (client/BloodlessSounds). So no game code
 * asks which mode anyone is in, as the brief's rule 4 has it: presentation only.
 */
public final class BBSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, BloodAndBones.MOD_ID);

    /** A wet sound, its bloodless twin, and a second sound played over the twin (null for none). */
    public record Twin(DeferredHolder<SoundEvent, SoundEvent> wet, DeferredHolder<SoundEvent, SoundEvent> clean,
                       @Nullable DeferredHolder<SoundEvent, SoundEvent> layer) {
    }

    private static final Map<ResourceLocation, Twin> TWINS = new LinkedHashMap<>();

    /** A metal rattle laid over the heavier clanks. */
    public static final DeferredHolder<SoundEvent, SoundEvent> BLOODLESS_RATTLE = sound("bloodless.rattle", "Metal rattles");

    public static final DeferredHolder<SoundEvent, SoundEvent> CARCASS_CUT = wet("carcass.cut", "Meat is cut", "Plating is cut", false);
    public static final DeferredHolder<SoundEvent, SoundEvent> CARCASS_SEVER = wet("carcass.sever", "Bone snaps", "Joint snaps", true);
    public static final DeferredHolder<SoundEvent, SoundEvent> CARCASS_SKIN = wet("carcass.skin", "Hide peels", "Plating peels", false);
    public static final DeferredHolder<SoundEvent, SoundEvent> CARCASS_PICK_UP = wet("carcass.pick_up", "Meat is picked up", "Wreckage is picked up", false);
    public static final DeferredHolder<SoundEvent, SoundEvent> CARCASS_THUD = wet("carcass.thud", "Carcass thuds", "Wreck clanks", true);
    public static final DeferredHolder<SoundEvent, SoundEvent> CARCASS_CLATTER = wet("carcass.clatter", "Bones clatter", "Frame clatters", false);
    public static final DeferredHolder<SoundEvent, SoundEvent> CARCASS_CRUMBLE = wet("carcass.crumble", "Carcass falls apart", "Wreck falls apart", true);
    public static final DeferredHolder<SoundEvent, SoundEvent> MACHINE_BLADE = sound("machine.blade", "Blade falls");
    public static final DeferredHolder<SoundEvent, SoundEvent> CYBERNETIC_SPOOL = sound("cybernetic.spool", "Cybernetic spools up");
    public static final DeferredHolder<SoundEvent, SoundEvent> CYBERNETIC_CHOKE = sound("cybernetic.choke", "Cybernetic sputters");

    // flesh, where the mod used to play slime and honey: squelches, slaps and slides
    public static final DeferredHolder<SoundEvent, SoundEvent> FLESH_SQUISH = wet("flesh.squish", "Flesh squelches", "Metal clanks", false);
    public static final DeferredHolder<SoundEvent, SoundEvent> FLESH_SQUISH_SMALL = wet("flesh.squish_small", "Flesh squishes", "Metal ticks", false);
    public static final DeferredHolder<SoundEvent, SoundEvent> FLESH_SLIDE = wet("flesh.slide", "Flesh slides", "Metal grinds", false);
    public static final DeferredHolder<SoundEvent, SoundEvent> FLESH_BREAK = wet("flesh.break", "Flesh tears", "Metal parts", false);
    public static final DeferredHolder<SoundEvent, SoundEvent> FLESH_STEP = wet("flesh.step", "Flesh squelches underfoot", "Metal clinks underfoot", false);
    public static final DeferredHolder<SoundEvent, SoundEvent> FLESH_PLACE = wet("flesh.place", "Flesh slaps down", "Metal is set down", false);
    public static final DeferredHolder<SoundEvent, SoundEvent> FLESH_HIT = wet("flesh.hit", "Flesh is struck", "Metal is struck", false);
    public static final DeferredHolder<SoundEvent, SoundEvent> FLESH_FALL = wet("flesh.fall", "Flesh slaps", "Metal clanks", false);
    // the trait effects' shoves: a body that lunges, one thrown up in the air, a meaty blow that knocks things back
    public static final DeferredHolder<SoundEvent, SoundEvent> FLESH_LUNGE = wet("flesh.lunge", "Flesh lunges", "Metal springs", false);
    public static final DeferredHolder<SoundEvent, SoundEvent> FLESH_FLING = wet("flesh.fling", "Flesh is flung", "Metal is flung", false);
    public static final DeferredHolder<SoundEvent, SoundEvent> FLESH_SLAP = wet("flesh.slap", "Flesh smacks", "Metal bangs", false);
    /** A wound dripping (Bleeding); bloodless, a construct leaking oil. */
    public static final DeferredHolder<SoundEvent, SoundEvent> BLOOD_DRIP = wet("blood.drip", "Blood drips", "Oil drips", false);
    // a blood stain underfoot; bloodless, it is drawn as nothing, so its twin is barely a sound
    public static final DeferredHolder<SoundEvent, SoundEvent> STAIN_STEP = wet("stain.step", "Blood squelches underfoot", "Something damp underfoot", false);
    public static final DeferredHolder<SoundEvent, SoundEvent> STAIN_BREAK = wet("stain.break", "Blood is scuffed away", "Something damp is scuffed", false);

    /** Guts and gore underfoot and in the hand: the Gut Chain's sounds. */
    public static final SoundType FLESH = new DeferredSoundType(1.0F, 1.0F, FLESH_BREAK, FLESH_STEP, FLESH_PLACE, FLESH_HIT, FLESH_FALL);
    /** A blood stain's. */
    public static final SoundType STAIN = new DeferredSoundType(0.6F, 1.0F, STAIN_BREAK, STAIN_STEP, STAIN_BREAK, STAIN_STEP, STAIN_STEP);

    private BBSounds() {
    }

    /** The bloodless twin of a sound, by the wet sound's id; null if it has none (it is not a wet sound). */
    @Nullable
    public static Twin twin(ResourceLocation wet) {
        return TWINS.get(wet);
    }

    /** Every wet sound and its twin, in order. */
    public static Map<ResourceLocation, Twin> twins() {
        return java.util.Collections.unmodifiableMap(TWINS);
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name, String subtitle) {
        BloodAndBones.REGISTRATE.addRawLang("subtitles." + BloodAndBones.MOD_ID + "." + name, subtitle);
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(BloodAndBones.asResource(name)));
    }

    /** A wet sound and its bloodless twin, {@code bloodless.<name>}; {@code rattle} lays the metal rattle over the twin. */
    private static DeferredHolder<SoundEvent, SoundEvent> wet(String name, String subtitle, String cleanSubtitle, boolean rattle) {
        DeferredHolder<SoundEvent, SoundEvent> wet = sound(name, subtitle);
        DeferredHolder<SoundEvent, SoundEvent> clean = sound("bloodless." + name, cleanSubtitle);
        TWINS.put(BloodAndBones.asResource(name), new Twin(wet, clean, rattle ? BLOODLESS_RATTLE : null));
        return wet;
    }

    public static void register(IEventBus modBus) {
        SOUNDS.register(modBus);
    }
}
