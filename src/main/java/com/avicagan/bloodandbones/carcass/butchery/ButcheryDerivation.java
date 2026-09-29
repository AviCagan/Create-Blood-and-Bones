package com.avicagan.bloodandbones.carcass.butchery;

import com.avicagan.bloodandbones.carcass.rig.Bone;
import com.avicagan.bloodandbones.carcass.rig.ButcheryTarget;
import com.avicagan.bloodandbones.carcass.rig.Rig;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Spreads butchery settings over the rig's bones: meat and bone by each bone's volume, offal and fat from the torso by the
 * whole animal's weight, hide by weight. Counts are expected values; the fraction is rolled as a chance when the carcass
 * is taken apart. Every mob's table is worked out with it from its groups' settings and its own mob file's
 * (ButcheryManager), unless a datapack gives it a table file of its own.
 */
public final class ButcheryDerivation {
    private ButcheryDerivation() {
    }

    /** A part extra naming a bone the rig lacks is left out: a group's settings are shared by many shapes of mob. */
    public static ButcheryTable derive(Rig rig, ButcheryTarget target) {
        Map<String, List<Yield>> parts = new LinkedHashMap<>();
        Bone torso = rig.root();
        for (Bone bone : rig.bones()) {
            List<Yield> yields = new ArrayList<>();
            Vector3f size = bone.boxSize();
            float volume = (size.x / 16.0F) * (size.y / 16.0F) * (size.z / 16.0F);
            if (!isNone(target.meat())) {
                yields.add(new Yield(target.meat(), round(volume * target.meatPerBlock()), "meat"));
            }
            if (!isNone(target.bone())) {
                yields.add(new Yield(target.bone(), round(Math.max(target.boneMin(), volume * target.bonePerBlock())), "bone"));
            }
            if (bone == torso) {
                if (target.offalPerWeight() > 0) {
                    yields.add(new Yield("bloodandbones:offal", round(rig.weight() * target.offalPerWeight()), "offal"));
                }
                if (target.fatPerWeight() > 0) {
                    yields.add(new Yield("bloodandbones:animal_fat", round(rig.weight() * target.fatPerWeight()), "fat"));
                }
            }
            yields.addAll(target.partExtras().getOrDefault(bone.name(), List.of()));
            yields.removeIf(y -> y.count() <= 0.0F);
            parts.put(bone.name(), yields);
        }
        List<Yield> hide = new ArrayList<>();
        if (!isNone(target.hide())) {
            hide.add(new Yield(target.hide(), round(Math.max(1.0F, rig.weight() * target.hidePerWeight())), "hide"));
        }
        hide.addAll(target.hideExtras());
        return new ButcheryTable(rig.entity(), hide, parts);
    }

    private static boolean isNone(String item) {
        return item == null || item.isEmpty() || item.equals("none");
    }

    private static float round(float value) {
        return Math.round(value * 100.0F) / 100.0F;
    }
}
