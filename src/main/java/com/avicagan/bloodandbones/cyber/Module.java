package com.avicagan.bloodandbones.cyber;

import com.avicagan.bloodandbones.body.BodyPart;
import com.mojang.serialization.Codec;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;

import java.util.Locale;

/**
 * What goes in a brass limb's slots (the design brief's cybernetic modules). Each fits one sort of limb and
 * is driven one of three ways: fired when the throttle is let go, run for as long as it is held, or always on.
 */
public enum Module implements StringRepresentable {
    /** Light things are pulled to you; heavy things pull you to them. */
    GRAPPLING_SPOOL(BodyPart.Kind.ARM, Mode.FIRE, 0),
    /** The arm drives a machine through a shaft that reaches out to it. */
    ROTATIONAL_COUPLER(BodyPart.Kind.ARM, Mode.HOLD, 0),
    /** A knockback strike, or a blow to the ground that launches you. */
    PISTON_RAM(BodyPart.Kind.ARM, Mode.FIRE, 0),
    /** Items drift to you; held, from further, and at the top, carcasses too. */
    MAGNET_COIL(BodyPart.Kind.ARM, Mode.HOLD, 1),
    /** Machines' speed and stress, read through walls. */
    ANALYTICAL_LENS(BodyPart.Kind.EYE, Mode.PASSIVE, 1),
    /** No fall damage, paid for by the height fallen. */
    GYROSCOPIC_STABILIZER(BodyPart.Kind.LEG, Mode.PASSIVE, 0),
    /** A brief hover, longer the further it was spooled. */
    BAROMETRIC_VENT(BodyPart.Kind.LEG, Mode.FIRE, 0);

    public enum Mode {
        /** Spool up while held, act once when let go. */
        FIRE,
        /** Act every tick while held, as hard as the spool allows. */
        HOLD,
        /** Always on; not driven by the throttle. */
        PASSIVE
    }

    public static final Codec<Module> CODEC = StringRepresentable.fromEnum(Module::values);
    public static final StreamCodec<net.minecraft.network.FriendlyByteBuf, Module> STREAM_CODEC = NeoForgeStreamCodecs.enumCodec(Module.class);

    private final BodyPart.Kind kind;
    private final Mode mode;
    private final int upkeep;

    Module(BodyPart.Kind kind, Mode mode, int upkeep) {
        this.kind = kind;
        this.mode = mode;
        this.upkeep = upkeep;
    }

    /** mB of soul blood a second it costs just to be fitted and working (the always-on part), on top of its limb's own. */
    public int upkeep() {
        return upkeep;
    }

    public BodyPart.Kind kind() {
        return kind;
    }

    public Mode mode() {
        return mode;
    }

    /** Driven by the throttle (fired or held), rather than always on. */
    public boolean driven() {
        return mode != Mode.PASSIVE;
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String translationKey() {
        return "item.bloodandbones." + getSerializedName();
    }
}
