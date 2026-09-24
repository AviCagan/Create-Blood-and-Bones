package com.avicagan.bloodandbones.parts;

import com.avicagan.bloodandbones.carcass.rig.Bone;
import com.avicagan.bloodandbones.carcass.rig.Rig;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Which slot a bone of a rig is (docs/PARTS-AND-TRAITS.md section 2.2), by rules that are data
 * (`data/<ns>/bone_slot_rules/*.json`), matched on the last word of the bone's path: first a mob file's own
 * line for that bone, then the rig's root is the torso, then the name rules, first match wins. A bone no rule
 * names is decoration drawn with its parent. The side comes from left_ or right_.
 */
public final class PartSlots {
    /** One naming rule: a pattern over the bone's own name, the slot it gives, maybe a form. */
    public record Rule(Pattern match, PartSlot slot, String form, boolean notRoot) {
    }

    public record SubRule(Pattern match, String sub) {
    }

    public record Rules(List<Rule> rules, List<SubRule> subs) {
    }

    /** The rules when no file gives them (and the ones the shipped file gives). */
    public static final Rules DEFAULT = new Rules(List.of(
            new Rule(Pattern.compile("(^|_)(upper_body|lower_body|body\\d|ribcage|chest|thorax|abdomen)$"), PartSlot.TORSO_EXT, "", true),
            new Rule(Pattern.compile("^neck$"), PartSlot.NECK, "", false),
            new Rule(Pattern.compile("(^|_)head(_|$)"), PartSlot.HEAD, "", false),
            new Rule(Pattern.compile("(^|_)wing(_|$)"), PartSlot.ARM, "wing", false),
            new Rule(Pattern.compile("^arms$"), PartSlot.ARM, "pair", false),
            new Rule(Pattern.compile("(^|_)arm$"), PartSlot.ARM, "", false),
            new Rule(Pattern.compile("(^|_)(leg|haunch)$"), PartSlot.LEG, "", false),
            new Rule(Pattern.compile("^tentacle\\d*$"), PartSlot.LEG, "tentacle", false),
            new Rule(Pattern.compile("(^|_)(tail\\d*|fluke)(_|$)|^body_back$"), PartSlot.TAIL, "", false)),
            List.of(new SubRule(Pattern.compile("(middle|mid)_"), "mid"), new SubRule(Pattern.compile("front"), "front"),
                    new SubRule(Pattern.compile("(hind|haunch|back)"), "hind")));

    private PartSlots() {
    }

    /** Read one rules file. */
    public static Rules parse(JsonObject json) {
        List<Rule> rules = new ArrayList<>();
        for (JsonElement e : json.getAsJsonArray("rules")) {
            JsonObject o = e.getAsJsonObject();
            rules.add(new Rule(Pattern.compile(o.get("match").getAsString()), PartSlot.byName(o.get("slot").getAsString()),
                    o.has("form") ? o.get("form").getAsString() : "", o.has("not_root") && o.get("not_root").getAsBoolean()));
        }
        List<SubRule> subs = new ArrayList<>();
        if (json.has("sub_slots")) {
            for (JsonElement e : json.getAsJsonArray("sub_slots")) {
                JsonObject o = e.getAsJsonObject();
                subs.add(new SubRule(Pattern.compile(o.get("match").getAsString()), o.get("sub").getAsString()));
            }
        }
        return new Rules(List.copyOf(rules), List.copyOf(subs));
    }

    /** The slot of a bone of this mob's rig. */
    public static SlotInfo of(PartsData.Store store, ResourceLocation entity, Rig rig, String bone) {
        String name = bone.substring(bone.lastIndexOf('/') + 1).toLowerCase(java.util.Locale.ROOT);
        MobGroup file = store.mobFile(entity);
        if (file != null) {
            SlotInfo own = file.boneSlots().getOrDefault(bone, file.boneSlots().get(name));
            if (own != null) {
                return own;
            }
        }
        if (rig.root().name().equals(bone)) {
            return SlotInfo.of(PartSlot.TORSO);
        }
        Rules rules = store.slotRules();
        for (Rule rule : rules.rules()) {
            if (rule.match().matcher(name).find()) {
                if (rule.notRoot() && rig.root().name().equals(bone)) {
                    continue;
                }
                String sub = "";
                if (rule.slot() == PartSlot.LEG || rule.slot() == PartSlot.ARM) {
                    for (SubRule s : rules.subs()) {
                        if (s.match().matcher(name).find()) {
                            sub = s.sub();
                            break;
                        }
                    }
                }
                return new SlotInfo(rule.slot(), rule.form(), sub);
            }
        }
        // a bone the rules do not name is part of whatever it hangs from (a horn with its head, a mane with its neck)
        Optional<Bone> parent = rig.bone(bone).flatMap(Bone::parent).flatMap(rig::bone);
        if (parent.isPresent() && !parent.get().name().equals(bone)) {
            SlotInfo up = of(store, entity, rig, parent.get().name());
            return up.slot() == PartSlot.TORSO ? SlotInfo.of(PartSlot.EXTRA) : up;
        }
        return SlotInfo.of(PartSlot.EXTRA);
    }

    /** Whether the rig has no head of its own anywhere: its body is its head too (a blaze, a slime). */
    public static boolean selfContained(PartsData.Store store, ResourceLocation entity, Rig rig) {
        for (Bone bone : rig.bones()) {
            PartSlot slot = of(store, entity, rig, bone.name()).slot();
            if (slot == PartSlot.HEAD || slot == PartSlot.NECK) {
                return false;
            }
        }
        return true;
    }

    /** Whether the rig has any bone of this slot. */
    public static boolean has(PartsData.Store store, ResourceLocation entity, Rig rig, PartSlot slot) {
        for (Bone bone : rig.bones()) {
            if (of(store, entity, rig, bone.name()).slot() == slot) {
                return true;
            }
        }
        return false;
    }
}
