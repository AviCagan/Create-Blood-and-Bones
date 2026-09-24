package com.avicagan.bloodandbones.body;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * What an implant is and does. A basic one runs on nothing; a powered one runs on a fluid from the Fluid
 * Backtank worn with it (blood for organic prosthetics, soul blood for cybernetics), a little every second,
 * and stops working, as if the part were missing, when the tank runs out of it.
 *
 * @param kind     the sort of part it replaces, either side
 * @param walk     how well a leg walks, flesh being 1
 * @param jump     how well a leg jumps, flesh being 1
 * @param work     how fast an arm breaks blocks, flesh being 1
 * @param attack   extra attack damage, on the main arm
 * @param reach    extra reach, blocks, on either arm
 * @param safeFall extra blocks a leg falls without harm
 * @param fuel     what it runs on: null (nothing), "blood", "soul_blood", or "any" (whatever is in the tank, used only as it goes)
 * @param drain    mB of its fuel a second while working
 * @param ability  what else it does
 * @param texture  drawn in the part's place on the player, laid out like a skin; for an eye, the base of a
 *                 _left and a _right texture drawn on the face; null for an organ inside
 * @param slots    how many modules it takes: a brass limb is a chassis for modules (see cyber.Module), anything else takes none
 */
public record ImplantSpec(BodyPart.Kind kind, float walk, float jump, float work, float attack, float reach, float safeFall,
                          @Nullable String fuel, int drain, Ability ability, @Nullable ResourceLocation texture, int slots) {
    public enum Ability {
        NONE,
        /** An arm that sprays what is in the tank. */
        VENT,
        /** An arm that plugs into a Backtank Port. */
        PORT,
        /** An eye that sees in the dark. */
        NIGHT_VISION,
        /** A heart that heals. */
        REGENERATION,
        /** Lungs that breathe water. */
        WATER_BREATHING,
        /** A stomach that keeps anything down. */
        IRON_GUT
    }

    public static ImplantSpec basic(BodyPart.Kind kind, float walk, float work, @Nullable ResourceLocation texture) {
        return new ImplantSpec(kind, walk, walk, work, 0, 0, 0, null, 0, Ability.NONE, texture, 0);
    }

    public static ImplantSpec powered(BodyPart.Kind kind, String fuel, int drain, float walk, float jump, float work, float attack,
                                      float reach, float safeFall, Ability ability, @Nullable ResourceLocation texture) {
        return new ImplantSpec(kind, walk, jump, work, attack, reach, safeFall, fuel, drain, ability, texture, 0);
    }

    /** The same, as a brass chassis taking this many modules. */
    public ImplantSpec withSlots(int slots) {
        return new ImplantSpec(kind, walk, jump, work, attack, reach, safeFall, fuel, drain, ability, texture, slots);
    }
}
