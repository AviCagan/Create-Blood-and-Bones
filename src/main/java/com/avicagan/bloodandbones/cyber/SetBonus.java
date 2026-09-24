package com.avicagan.bloodandbones.cyber;

import com.avicagan.bloodandbones.body.Body;
import com.avicagan.bloodandbones.body.BodyEffects;
import com.avicagan.bloodandbones.body.BodyPart;
import com.avicagan.bloodandbones.body.ImplantItem;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/**
 * The two set bonuses from the design brief: four or more flesh grafts (organic prosthetics, the ones that
 * run on blood) make you the monster, four or more brass modules make you the machine. A body with both
 * kinds in it gets neither, and nothing worse: experimenting is never punished. Crude prosthetics count for
 * neither side.
 */
public final class SetBonus {
    public static final int NEEDED = 4;
    /** The flesh bonus: this share of melee damage dealt comes back as health... */
    public static final float FLESH_LIFESTEAL = 0.15F;
    /** ...and necrosis builds this much as fast. */
    public static final float FLESH_NECROSIS = 0.5F;
    /** The brass bonus: the throttle and the stabilizer cost this share... */
    public static final float BRASS_DRAIN = 0.75F;
    /** ...and knockback is resisted this much. */
    public static final float BRASS_KNOCKBACK_RESISTANCE = 0.25F;

    private SetBonus() {
    }

    /** Organic prosthetics fitted, working or not. */
    public static int grafts(Body body) {
        int n = 0;
        for (BodyPart part : BodyPart.values()) {
            if (body.state(part) == Body.State.IMPLANT && body.implant(part).getItem() instanceof ImplantItem implant && implant.organic()) {
                n++;
            }
        }
        return n;
    }

    /** Cybernetics fitted (anything on soul blood, and every brass chassis), working or not. */
    public static int cybernetics(Body body) {
        int n = 0;
        for (BodyPart part : BodyPart.values()) {
            if (body.state(part) == Body.State.IMPLANT && body.implant(part).getItem() instanceof ImplantItem implant
                    && ("soul_blood".equals(implant.spec().fuel()) || implant.spec().slots() > 0)) {
                n++;
            }
        }
        return n;
    }

    public static boolean flesh(Body body) {
        return grafts(body) >= NEEDED && cybernetics(body) == 0;
    }

    public static boolean brass(Body body) {
        return Modules.count(body) >= NEEDED && grafts(body) == 0;
    }

    public static boolean flesh(LivingEntity entity) {
        return BodyEffects.altered(entity) && flesh(BodyEffects.body(entity));
    }

    public static boolean brass(LivingEntity entity) {
        return BodyEffects.altered(entity) && brass(BodyEffects.body(entity));
    }

    /** The flesh bonus: a melee hit feeds you. */
    @SubscribeEvent
    public static void onDamage(LivingDamageEvent.Post event) {
        if (event.getSource().getDirectEntity() instanceof Player attacker && event.getSource().getEntity() == attacker
                && event.getNewDamage() > 0.0F && flesh(attacker)) {
            attacker.heal(event.getNewDamage() * FLESH_LIFESTEAL);
        }
    }
}
