package com.avicagan.bloodandbones.gametest;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.config.BloodlessWords;
import com.avicagan.bloodandbones.minion.MinionEntity;
import com.avicagan.bloodandbones.parts.ActiveTraits;
import com.avicagan.bloodandbones.parts.MobGroup;
import com.avicagan.bloodandbones.parts.PartsData;
import com.avicagan.bloodandbones.parts.ResolvedMob;
import com.avicagan.bloodandbones.parts.Trait;
import com.avicagan.bloodandbones.parts.TraitEffect;
import com.avicagan.bloodandbones.parts.TraitEffects;
import com.avicagan.bloodandbones.parts.TraitList;
import com.avicagan.bloodandbones.parts.TraitRef;
import com.avicagan.bloodandbones.parts.Trigger;
import com.avicagan.bloodandbones.parts.effect.FlagEffect;
import com.avicagan.bloodandbones.parts.effect.HitscanEffect;
import com.avicagan.bloodandbones.parts.effect.MendEffect;
import com.avicagan.bloodandbones.parts.effect.PowerEffect;
import com.avicagan.bloodandbones.parts.effect.ProjectileEffect;
import com.avicagan.bloodandbones.parts.effect.SenseEffect;
import com.avicagan.bloodandbones.parts.effect.StorageEffect;
import com.avicagan.bloodandbones.parts.effect.TeleportEffect;
import com.avicagan.bloodandbones.registry.BBEntities;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The data lints of docs/PARTS-AND-TRAITS.md section 5.8, over every trait and every group and mob file loaded, and the
 * signature lint of section 9 (slice 3, green from slice 9): what each of the 79 mobs' own files claim as their signature
 * is there, and what a signature still waits for is written down and logged every run.
 */
@GameTestHolder(BloodAndBones.MOD_ID)
@PrefixGameTestTemplate(false)
public class DataLintTests {
    /** The organs every body of a kind has; any other organ is a special one (the brief: every mob has at least one). */
    private static final Set<String> GENERIC_ORGANS = Set.of("heart", "lungs", "stomach", "eye", "core");
    /** Words bloodless mode must never show. */
    private static final Pattern BLOODY = Pattern.compile("(?i)(?<![a-z])(blood\\w*|bleed\\w*|gor[ey]|guts?|organs?)(?![a-z])");
    /** An organ's bloodless name is a machine part (spec 7.10): none of these either. */
    private static final Pattern FLESHY = Pattern.compile("(?i)(?<![a-z])(blood\\w*|bleed\\w*|gor[ey]|guts?|organs?|glands?|sacs?|hearts?|lungs"
            + "|stomachs?|bladders?|marrow|flesh)(?![a-z])");
    /** The flags each kind of host reads (spec 5.6): a minion has no worn armour's hooks, a player no minion's legs. */
    private static final Set<String> MINION_FLAGS = Set.of("climb", "bounce", "ender_mask", "silent_steps", "inverted_healing", "trample", "lava_walk");
    private static final Set<String> ARMOUR_FLAGS = Set.of("climb", "glide", "bounce", "powder_snow", "ender_mask", "piglin_neutral", "silent_steps",
            "quick_draw", "inverted_healing");

    private static ResourceLocation mob(String name) {
        return ResourceLocation.withDefaultNamespace(name);
    }

    private static JsonObject lang(GameTestHelper helper) {
        try (var in = BloodAndBones.class.getResourceAsStream("/assets/bloodandbones/lang/en_us.json")) {
            return JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            helper.fail("Could not read the language file: " + e);
            return null;
        }
    }

    /** What bloodless mode shows for a key: its own wording, or the general rewording of the normal one. */
    private static String bloodless(JsonObject lang, String key) {
        return lang.has("bloodless." + key) ? lang.get("bloodless." + key).getAsString() : BloodlessWords.soften(lang.get(key).getAsString());
    }

    /**
     * Whether an effect does anything on this kind of host: an attribute the host has, a flag it reads, a diet a minion can
     * forage, storage and power only a minion has, a passive shot only a minion fires, a reveal outline only a player sees.
     */
    private static boolean supports(TraitEffect facet, boolean onMinion, LivingEntity minion, LivingEntity player) {
        TraitEffect.Effect effect = facet.effect();
        if (effect instanceof TraitEffects.AttributeEffect attribute) {
            return (onMinion ? minion : player).getAttributes().hasAttribute(attribute.attribute());
        }
        if (effect instanceof FlagEffect flag) {
            return (onMinion ? MINION_FLAGS : ARMOUR_FLAGS).contains(flag.flag());
        }
        if (effect instanceof TraitEffects.DietEffect diet) {
            return !onMinion || diet.forageMb() > 0 && !diet.foods().isEmpty();
        }
        if (effect instanceof StorageEffect) {
            return onMinion;
        }
        if (effect instanceof PowerEffect power) {
            // on a player only the drain (blood upkeep) and a kill's top-up mean anything
            return onMinion || power.drainMult().calculate(1) != 1.0F || power.feedOnKillMb() > 0;
        }
        if (effect instanceof MendEffect mend) {
            return !onMinion || "item".equals(mend.mode());
        }
        if ((effect instanceof ProjectileEffect || effect instanceof HitscanEffect) && facet.trigger() == Trigger.PASSIVE) {
            return onMinion;
        }
        if (effect instanceof TeleportEffect teleport && "to_owner".equals(teleport.mode())) {
            return onMinion;
        }
        if (effect instanceof SenseEffect sense && "reveal".equals(sense.kind())) {
            return !onMinion;
        }
        return true;
    }

    /** One trait named in a layer: where, for which kind of host, and how. */
    private record Named(String where, String context, TraitRef ref) {
    }

    private static void named(String where, String context, TraitList list, List<Named> out, List<ResourceLocation> removed) {
        list.add().forEach(ref -> out.add(new Named(where, context, ref)));
        removed.addAll(list.remove());
    }

    /** Every trait a layer names, with the kind of host each list is for: minion lists, and armour's (pieces, hide, sets). */
    private static void named(MobGroup layer, List<Named> out, List<ResourceLocation> removed, List<String> problems) {
        String id = layer.kind().name().toLowerCase(java.util.Locale.ROOT) + " " + layer.id();
        for (Map.Entry<String, MobGroup.PartEntry> part : layer.parts().entrySet()) {
            String where = id + " " + part.getKey();
            part.getValue().armour().ifPresent(list -> named(where + " armour", ActiveTraits.ARMOUR, list, out, removed));
            part.getValue().armourPieces().forEach((piece, list) -> named(where + " " + piece, ActiveTraits.ARMOUR, list, out, removed));
            part.getValue().minion().filter(JsonElement::isJsonObject).map(JsonElement::getAsJsonObject).filter(o -> o.has("traits"))
                    .ifPresent(o -> TraitList.CODEC.parse(JsonOps.INSTANCE, o.get("traits"))
                            .resultOrPartial(error -> problems.add(where + " minion traits will not read: " + error))
                            .ifPresent(list -> named(where + " minion", ActiveTraits.MINION, list, out, removed)));
        }
        layer.hide().ifPresent(list -> named(id + " hide", ActiveTraits.ARMOUR, list, out, removed));
        layer.organTraits().forEach((organ, entry) -> {
            entry.minion().ifPresent(list -> named(id + " " + organ + " minion", ActiveTraits.MINION, list, out, removed));
            entry.armour().ifPresent(list -> named(id + " " + organ + " armour", ActiveTraits.ARMOUR, list, out, removed));
        });
        layer.fullSet().ifPresent(set -> {
            named(id + " set bonus", ActiveTraits.ARMOUR, set.bonus(), out, removed);
            named(id + " set drawback", ActiveTraits.ARMOUR, set.drawback(), out, removed);
        });
    }

    /**
     * Spec 5.8's lints. Every trait: cooldowns 0 or at least 20 ticks and tick intervals at least 20; no damage_item; no
     * effect in a context it does not work in (a minion has no luck, a player no forage...). Every trait a group or mob file
     * names: loaded, meant for the kind of host its list is for, at a level no higher than its most, with a name that reads
     * right in bloodless mode. Every vanilla mob: its resolved levels within the most, and at least one special organ with
     * an ability, named, and named as a machine part in bloodless mode.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void dataLints(GameTestHelper helper) {
        JsonObject lang = lang(helper);
        if (lang == null) {
            return;
        }
        PartsData.Store store = PartsData.SERVER;
        List<String> problems = new ArrayList<>();
        MinionEntity minion = BBEntities.MINION.get().create(helper.getLevel());
        LivingEntity player = helper.makeMockPlayer(GameType.SURVIVAL);
        // every trait, as loaded and as written
        for (Map.Entry<ResourceLocation, Trait> e : store.traits().entrySet()) {
            Trait trait = e.getValue();
            if (trait.maxLevel() < 1 || trait.contexts().isEmpty() || !List.of(ActiveTraits.MINION, ActiveTraits.ARMOUR).containsAll(trait.contexts())) {
                problems.add(e.getKey() + " has max level " + trait.maxLevel() + " and contexts " + trait.contexts());
            }
            for (int i = 0; i < trait.effects().size(); i++) {
                TraitEffect facet = trait.effects().get(i);
                String what = e.getKey() + " effect " + i + " (" + TraitEffects.typeId(facet.effect()) + ")";
                if (facet.cooldown() != 0 && facet.cooldown() < 20) {
                    problems.add(what + " has a cooldown of " + facet.cooldown() + " ticks");
                }
                if (facet.trigger() == Trigger.TICK && facet.interval() < 20) {
                    problems.add(what + " ticks every " + facet.interval());
                }
                boolean any = false;
                for (String context : trait.contexts()) {
                    if (!facet.appliesIn(context)) {
                        continue;
                    }
                    any = true;
                    if (!supports(facet, ActiveTraits.MINION.equals(context), minion, player)) {
                        problems.add(what + " does nothing on " + (ActiveTraits.MINION.equals(context) ? "a minion" : "armour"));
                    }
                }
                if (!any) {
                    problems.add(what + " is limited to " + facet.context().orElse("") + ", which the trait never is");
                }
            }
        }
        store.raw(PartsData.Kind.TRAIT).forEach((id, text) -> {
            if (text.contains("minecraft:damage_item")) {
                problems.add(id + " uses damage_item");
            }
        });
        // every trait every layer names
        List<MobGroup> layers = new ArrayList<>(store.groups().values());
        layers.addAll(store.mobFiles().values());
        List<Named> names = new ArrayList<>();
        List<ResourceLocation> removed = new ArrayList<>();
        for (MobGroup layer : layers) {
            named(layer, names, removed, problems);
        }
        for (ResourceLocation id : removed) {
            if (store.trait(id) == null) {
                problems.add("a layer removes " + id + ", which is not loaded");
            }
        }
        for (Named n : names) {
            Trait trait = store.trait(n.ref().id());
            if (trait == null) {
                problems.add(n.where() + " names " + n.ref().id() + ", which is not loaded");
                continue;
            }
            if (!trait.contexts().contains(n.context())) {
                problems.add(n.where() + " names " + n.ref().id() + ", which is not for " + n.context());
            }
            int most = n.ref().level().from().isPresent() ? n.ref().level().max() : n.ref().level().fixed();
            if (most > trait.maxLevel() || n.ref().level().min() < 1) {
                problems.add(n.where() + " names " + n.ref().id() + " at " + most + ", past its most, " + trait.maxLevel());
            }
            String key = trait.name();
            if (!lang.has(key)) {
                problems.add(n.ref().id() + " has no name (" + key + ")");
                continue;
            }
            for (String shown : lang.has(key + ".desc") ? List.of(key, key + ".desc") : List.of(key)) {
                if (BLOODY.matcher(bloodless(lang, shown)).find()) {
                    problems.add("in bloodless mode " + shown + " still says: " + bloodless(lang, shown));
                }
            }
        }
        // every vanilla mob, resolved
        for (String[] row : BreadthTests.MOBS) {
            ResolvedMob resolved = store.resolve(mob(row[0]), false);
            List<TraitList.Resolved> all = new ArrayList<>(resolved.hide());
            resolved.parts().values().forEach(p -> {
                all.addAll(p.armour());
                p.pieces().values().forEach(all::addAll);
            });
            resolved.organs().values().forEach(o -> {
                all.addAll(o.minion());
                all.addAll(o.armour());
            });
            resolved.fullSet().ifPresent(s -> {
                all.addAll(s.bonus());
                all.addAll(s.drawback());
            });
            for (TraitList.Resolved t : all) {
                Trait trait = store.trait(t.id());
                if (trait != null && t.level() > trait.maxLevel()) {
                    problems.add(row[0] + " has " + t.id() + " at " + t.level() + ", past its most, " + trait.maxLevel());
                }
            }
            int special = 0;
            for (Map.Entry<ResourceLocation, ResolvedMob.Organ> organ : resolved.organs().entrySet()) {
                if (GENERIC_ORGANS.contains(organ.getKey().getPath()) || organ.getValue().minion().isEmpty() && organ.getValue().armour().isEmpty()) {
                    continue;
                }
                special++;
                String key = "organ." + organ.getKey().getNamespace() + "." + organ.getKey().getPath();
                if (!lang.has(key) || !lang.has("bloodless." + key)) {
                    problems.add(row[0] + "'s " + organ.getKey() + " has no name, or no bloodless one");
                } else if (FLESHY.matcher(lang.get("bloodless." + key).getAsString()).find()) {
                    problems.add("in bloodless mode " + key + " is still " + lang.get("bloodless." + key).getAsString());
                }
            }
            if (special == 0) {
                problems.add(row[0] + " has no special organ with an ability");
            }
            resolved.fullSet().filter(set -> !lang.has(set.name()) || BLOODY.matcher(bloodless(lang, set.name())).find())
                    .ifPresent(set -> problems.add(row[0] + "'s set " + set.name() + " has no name, or a bloody one"));
        }
        minion.discard();
        if (!problems.isEmpty()) {
            helper.fail("Data lints " + BreadthTests.shortList(problems));
            return;
        }
        helper.succeed();
    }

    /**
     * The mobs whose whole signature is their family's, so they need no file of their own: the horse's legs, saddle and
     * tail are the equine family's, the guardian's beam, spikes and tail the guardian family's.
     */
    private static final Set<String> FAMILY_SIGNATURE = Set.of("horse", "guardian");

    /** What each mob's signature (spec 8.2) still waits for: a job, a movement mode, a variant capture, a mount... */
    private static final Map<String, String> SIGNATURE_WAITS = Map.ofEntries(
            Map.entry("allay", "the scavenger job (fetching what matches its held item from 32 blocks)"),
            Map.entry("armadillo", "Scute Plating's durability x1.5 (a hide cannot change durability)"),
            Map.entry("axolotl", "the hunter job for axolotl prey; its Regrowth Gland at twice the family's rate"),
            Map.entry("bee", "the sting strike that spends the limb for 60 s (the Stinger's on-hit poison stands in)"),
            Map.entry("bogged", "held bows and poison-tipped arrows (it shoots plain innate arrows)"),
            Map.entry("camel", "its second seat (the mount type)"),
            Map.entry("cod", "the fisher job (fishing with its mouth)"),
            Map.entry("creeper", "the sapper job; a charged creeper's bigger blast (variant capture)"),
            Map.entry("dolphin", "the Melon sensing underwater only, to 32 blocks (Echo Sense II stands in)"),
            Map.entry("drowned", "a thrown held trident; walking the seabed (sink mode)"),
            Map.entry("enderman", "carrying blocks; a minion arm's reach; Voidwalker's halved blink cooldowns"),
            Map.entry("evoker", "the row of fangs; the Totem Gland costing a minion half its power"),
            Map.entry("fox", "the pounce (Leap stands in); the snow fox's insulated hide (variant capture)"),
            Map.entry("frog", "eating small slimes into froglights; warm and cold legs (variant capture)"),
            Map.entry("ghast", "float mode for its tentacles; slow falling only while sneaking (Featherfall stands in)"),
            Map.entry("illusioner", "held bows and blindness arrows"),
            Map.entry("iron_golem", "sink mode; crusher boots; Hardy V (capped at III)"),
            Map.entry("llama", "the caravan job"),
            Map.entry("magma_cube", "a landing that sets what is within 2 alight (Searing stands in on the minion)"),
            Map.entry("panda", "temperaments by gene (variant capture)"),
            Map.entry("parrot", "the mimic alarm; wing lift 0.06 (flight from wings)"),
            Map.entry("phantom", "the pounce from above (Leap stands in)"),
            Map.entry("pig", "carrot-on-a-stick steering (the mount type)"),
            Map.entry("piglin", "the barterer job and its double roll; a held crossbow"),
            Map.entry("piglin_brute", "the brute guard (half again with an axe)"),
            Map.entry("pillager", "a held crossbow on a minion (Quick Draw is the shoulders' alone)"),
            Map.entry("rabbit", "the killer bunny (variant capture)"),
            Map.entry("ravager", "the rideable torso and its two seats (the mount type)"),
            Map.entry("salmon", "swimming up waterfalls; the fisher job"),
            Map.entry("skeleton", "held bows (it shoots innate arrows)"),
            Map.entry("skeleton_horse", "the seafloor steed: ridden underwater, sink mode"),
            Map.entry("sniffer", "the digger job; outlining suspicious sand and gravel (Blood Scent stands in)"),
            Map.entry("snow_golem", "rolling (roll mode)"),
            Map.entry("squid", "its head's underwater sense"),
            Map.entry("stray", "held bows and Slowness-tipped arrows (it shoots plain innate arrows)"),
            Map.entry("strider", "riding with a warped fungus on a stick; the Lava Bladder only while in lava"),
            Map.entry("tadpole", "being scooped into a bucket"),
            Map.entry("trader_llama", "the trader's guard job"),
            Map.entry("turtle", "the homing job; the helmet's Water Breathing; Thick Hide IV (capped at III); immunity to drying out"),
            Map.entry("vex", "passing through its targets as it dashes"),
            Map.entry("vindicator", "the axeman (twice the damage with an axe); a head named Johnny (name capture)"),
            Map.entry("witch", "the medic job"),
            Map.entry("wither", "skull-firing heads (a minion's extra mouths)"),
            Map.entry("wolf", "the mood tail (drawn)"),
            Map.entry("zoglin", "berserk: attacking every mob, half again as hard"),
            Map.entry("zombie", "the grab strike, breaking doors"),
            Map.entry("zombie_horse", "the undead steed walking the seabed (sink mode)"),
            Map.entry("zombie_villager", "the shaky surgeon"));

    /** Facets of the families and overlays (spec 3.3, 3.4) that wait the same way. */
    private static final List<String> GROUP_WAITS = List.of(
            "equine: Centaur's Jump Boost II for your mount",
            "swine: the Glutton drawback (no condition reads a player's food)",
            "canid: Alpha's Strength for tamed wolves near you",
            "feline: Nine Lives' 20-minute cooldown (undying's is 5) and the set's halved one",
            "behemoth: crusher boots",
            "amphibian: drying out after 120 s (dry_out waits 60)",
            "shellback: turtles and shulkers shedding scutes (scute_shed sheds an armadillo's)",
            "vermin: Infestation's kin with arthropods",
            "spirit: Ethereal's projectiles passing through you",
            "traits: rideable (the mount type), keen_butcher (the butchery_yield attribute)");

    /**
     * Spec 8.2, as built: each mob's own file lists its signature ("parts.&lt;key&gt;", "organs.&lt;id&gt;", "hide",
     * "full_set"), and each of those is in that file. A mob with no signature of its own has its family's, or a note of what
     * it waits for; the notes, and the families' and overlays', are logged every run as the report of what is missing.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void signatureLint(GameTestHelper helper) {
        PartsData.Store store = PartsData.SERVER;
        List<String> problems = new ArrayList<>();
        int signed = 0;
        for (String[] row : BreadthTests.MOBS) {
            String name = row[0];
            MobGroup file = store.mobFile(mob(name));
            String raw = store.raw(PartsData.Kind.MOB_TRAITS).get(BloodAndBones.asResource("minecraft/" + name));
            JsonArray signature = raw == null ? new JsonArray() : JsonParser.parseString(raw).getAsJsonObject().has("signature")
                    ? JsonParser.parseString(raw).getAsJsonObject().getAsJsonArray("signature") : new JsonArray();
            for (JsonElement e : signature) {
                String facet = e.getAsString();
                boolean there = switch (facet.contains(".") ? facet.substring(0, facet.indexOf('.')) : facet) {
                    case "parts" -> file.parts().containsKey(facet.substring("parts.".length()));
                    case "organs" -> file.organTraits().containsKey(ResourceLocation.parse(facet.substring("organs.".length())));
                    case "hide" -> file.hide().isPresent();
                    case "full_set" -> file.fullSet().isPresent();
                    default -> false;
                };
                if (!there) {
                    problems.add(name + "'s signature names " + facet + ", which its file does not have");
                }
            }
            if (!signature.isEmpty()) {
                signed++;
            } else if (!FAMILY_SIGNATURE.contains(name) && !SIGNATURE_WAITS.containsKey(name)) {
                problems.add(name + " has no signature of its own and no note of what it waits for");
            }
        }
        for (String name : SIGNATURE_WAITS.keySet()) {
            if (java.util.Arrays.stream(BreadthTests.MOBS).noneMatch(row -> row[0].equals(name))) {
                problems.add("a note for " + name + ", which is not one of the 79");
            }
        }
        BloodAndBones.LOGGER.info("Signature lint: {} of {} mobs sign their own file, {} by their family; still waiting:", signed,
                BreadthTests.MOBS.length, FAMILY_SIGNATURE.size());
        new java.util.TreeMap<>(SIGNATURE_WAITS).forEach((name, waits) -> BloodAndBones.LOGGER.info("  {}: {}", name, waits));
        GROUP_WAITS.forEach(waits -> BloodAndBones.LOGGER.info("  {}", waits));
        if (!problems.isEmpty()) {
            helper.fail("Signatures " + BreadthTests.shortList(problems));
            return;
        }
        helper.succeed();
    }
}
