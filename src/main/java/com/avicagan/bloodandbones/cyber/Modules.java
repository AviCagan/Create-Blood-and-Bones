package com.avicagan.bloodandbones.cyber;

import com.avicagan.bloodandbones.body.Body;
import com.avicagan.bloodandbones.body.BodyEffects;
import com.avicagan.bloodandbones.body.BodyPart;
import com.avicagan.bloodandbones.body.ImplantItem;
import com.avicagan.bloodandbones.registry.BBDataComponents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The modules in brass limbs: which a limb holds, and which a body has working. A module works while the
 * limb it sits in works (fitted, with soul blood in the worn tank).
 */
public final class Modules {
    /** The order the throttle steps through the limbs, the main arm's side first. */
    private static final BodyPart[] ORDER = {BodyPart.RIGHT_ARM, BodyPart.LEFT_ARM, BodyPart.RIGHT_LEG, BodyPart.LEFT_LEG,
            BodyPart.RIGHT_EYE, BodyPart.LEFT_EYE};

    /** One fitted module: the limb it is in, its slot, and what it is. */
    public record Fitted(BodyPart part, int slot, Module module) {
    }

    private Modules() {
    }

    /** How many modules this implant takes: none unless it is a brass chassis. */
    public static int slots(ItemStack implant) {
        return implant.getItem() instanceof ImplantItem item ? item.spec().slots() : 0;
    }

    public static List<Module> of(ItemStack implant) {
        return implant.getOrDefault(BBDataComponents.MODULES.get(), List.of());
    }

    public static void set(ItemStack implant, List<Module> modules) {
        if (modules.isEmpty()) {
            implant.remove(BBDataComponents.MODULES.get());
        } else {
            implant.set(BBDataComponents.MODULES.get(), List.copyOf(modules));
        }
    }

    /** Whether this module can go in this implant's slots: the right sort of limb, a chassis, and a slot free or one to swap. */
    public static boolean fits(ItemStack implant, Module module) {
        return slots(implant) > 0 && implant.getItem() instanceof ImplantItem item && item.kind() == module.kind();
    }

    /** Every module working in this body, limb by limb (the right side first), slot by slot. */
    public static List<Fitted> working(LivingEntity wearer) {
        if (!BodyEffects.altered(wearer)) {
            return List.of();
        }
        Body body = BodyEffects.body(wearer);
        List<Fitted> out = new ArrayList<>();
        for (BodyPart part : ORDER) {
            if (body.state(part) != Body.State.IMPLANT || !body.works(part, wearer)) {
                continue;
            }
            List<Module> modules = of(body.implant(part));
            for (int i = 0; i < modules.size(); i++) {
                out.add(new Fitted(part, i, modules.get(i)));
            }
        }
        return out;
    }

    /** The modules the throttle drives, in the order it steps through them. */
    public static List<Fitted> driven(LivingEntity wearer) {
        return working(wearer).stream().filter(f -> f.module().driven()).toList();
    }

    /** Whether a working limb of this body holds this module. */
    public static boolean has(LivingEntity wearer, Module module) {
        return find(wearer, module) != null;
    }

    @Nullable
    public static Fitted find(LivingEntity wearer, Module module) {
        for (Fitted fitted : working(wearer)) {
            if (fitted.module() == module) {
                return fitted;
            }
        }
        return null;
    }

    /** Whether that limb still holds that module in that slot and works. */
    public static boolean still(LivingEntity wearer, BodyPart part, int slot, Module module) {
        Body body = BodyEffects.body(wearer);
        if (body.state(part) != Body.State.IMPLANT || !body.works(part, wearer)) {
            return false;
        }
        List<Module> modules = of(body.implant(part));
        return slot >= 0 && slot < modules.size() && modules.get(slot) == module;
    }

    /** How many modules are fitted in all this body's limbs, working or not (for the brass set bonus). */
    public static int count(Body body) {
        int n = 0;
        for (BodyPart part : BodyPart.values()) {
            if (body.state(part) == Body.State.IMPLANT) {
                n += of(body.implant(part)).size();
            }
        }
        return n;
    }
}
