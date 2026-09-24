package com.avicagan.bloodandbones.gametest;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.carcass.rig.Bone;
import com.avicagan.bloodandbones.carcass.rig.Rig;
import com.avicagan.bloodandbones.carcass.rig.RigManager;
import com.avicagan.bloodandbones.parts.MobGroup;
import com.avicagan.bloodandbones.parts.PartSlots;
import com.avicagan.bloodandbones.parts.PartsData;
import com.avicagan.bloodandbones.parts.ResolvedMob;
import com.avicagan.bloodandbones.parts.ScrapMaterial;
import com.avicagan.bloodandbones.parts.Trait;
import com.avicagan.bloodandbones.parts.TraitList;
import com.avicagan.bloodandbones.parts.TraitRef;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Parts and traits for every one of the 79 mobs (docs/PARTS-AND-TRAITS.md sections 2 and 3): each resolves to the
 * archetype, family and overlays the spec's table gives, every bone of every rig has a slot by name, every trait,
 * material and name the data uses is loaded, and every parts file parses.
 */
@GameTestHolder(BloodAndBones.MOD_ID)
@PrefixGameTestTemplate(false)
public class BreadthTests {
    /** Spec 3.5: mob, archetype, family (empty for none), overlays. The data lints go through the same 79. */
    static final String[][] MOBS = {
            {"allay", "floater", "spirit", "bloodless_mob,fall"}, {"armadillo", "quadruped", "shellback", ""},
            {"axolotl", "quadruped", "amphibian", "aquatic,breath"}, {"bat", "flier", "skyborne", "fall"},
            {"bee", "arthropod", "skyborne", "arthropod,bloodless_mob,fall"}, {"blaze", "floater", "elemental", "nether,bloodless_mob,fall"},
            {"bogged", "biped", "humanoid", "skeletal,undead,bloodless_mob,breath"},
            {"breeze", "floater", "elemental", "bloodless_mob,fall,deflect"}, {"camel", "quadruped", "grazer", ""},
            {"cat", "quadruped", "feline", "fall"}, {"cave_spider", "arthropod", "arachnid", "arthropod"}, {"chicken", "bird", "fowl", "fall"},
            {"cod", "fish", "marine", "aquatic,breath"}, {"cow", "quadruped", "grazer", ""}, {"creeper", "quadruped", "volatile", "bloodless_mob"},
            {"dolphin", "fish", "marine", "aquatic"}, {"donkey", "quadruped", "equine", ""},
            {"drowned", "biped", "humanoid", "rotting,undead,breath"}, {"elder_guardian", "fish", "guardian", "aquatic,breath"},
            {"enderman", "biped", "humanoid", "ender"}, {"endermite", "arthropod", "vermin", "arthropod,ender,bloodless_mob,snow"},
            {"evoker", "biped", "illager", "raider"}, {"fox", "quadruped", "canid", "snow"}, {"frog", "biped", "amphibian", "breath"},
            {"ghast", "tentacled", "elemental", "nether,bloodless_mob,fall"}, {"glow_squid", "tentacled", "cephalopod", "aquatic,breath"},
            {"goat", "quadruped", "grazer", ""}, {"guardian", "fish", "guardian", "aquatic,breath"}, {"hoglin", "quadruped", "swine", "nether"},
            {"horse", "quadruped", "equine", ""}, {"husk", "biped", "humanoid", "rotting,undead,breath"},
            {"illusioner", "biped", "illager", "raider"}, {"iron_golem", "biped", "golem", "bloodless_mob,fall"},
            {"llama", "quadruped", "grazer", ""}, {"magma_cube", "blob", "slime", "nether,bloodless_mob,fall"},
            {"mooshroom", "quadruped", "grazer", ""}, {"mule", "quadruped", "equine", ""}, {"ocelot", "quadruped", "feline", "fall"},
            {"panda", "quadruped", "bear", ""}, {"parrot", "bird", "fowl", "fall"}, {"phantom", "flier", "skyborne", "undead,fall,breath"},
            {"pig", "quadruped", "swine", ""}, {"piglin", "biped", "piglin", "nether"}, {"piglin_brute", "biped", "piglin", "nether"},
            {"pillager", "biped", "illager", "raider"}, {"polar_bear", "quadruped", "bear", "frozen"},
            {"pufferfish", "fish", "marine", "aquatic,breath"}, {"rabbit", "quadruped", "small_prey", "snow"},
            {"ravager", "quadruped", "behemoth", "raider"}, {"salmon", "fish", "marine", "aquatic,breath"}, {"sheep", "quadruped", "grazer", ""},
            {"shulker", "shelled", "shellback", "ender,bloodless_mob,fall"}, {"silverfish", "arthropod", "vermin", "arthropod,bloodless_mob,snow"},
            {"skeleton", "biped", "humanoid", "skeletal,undead,bloodless_mob,breath"},
            {"skeleton_horse", "quadruped", "equine", "skeletal,undead,bloodless_mob,breath"}, {"slime", "blob", "slime", "bloodless_mob"},
            {"sniffer", "quadruped", "behemoth", ""}, {"snow_golem", "biped", "golem", "frozen,bloodless_mob,fall"},
            {"spider", "arthropod", "arachnid", "arthropod"}, {"squid", "tentacled", "cephalopod", "aquatic,breath"},
            {"stray", "biped", "humanoid", "skeletal,undead,frozen,bloodless_mob,breath"}, {"strider", "biped", "", "nether"},
            {"tadpole", "fish", "amphibian", "aquatic,breath"}, {"trader_llama", "quadruped", "grazer", ""},
            {"turtle", "quadruped", "shellback", "aquatic,breath"}, {"vex", "floater", "spirit", "bloodless_mob"},
            {"villager", "biped", "villager", ""}, {"vindicator", "biped", "illager", "raider"}, {"wandering_trader", "biped", "villager", ""},
            {"warden", "biped", "sculk", "boss"}, {"witch", "biped", "villager", "raider"},
            {"wither", "colossus", "", "undead,nether,frozen,boss,bloodless_mob,fall,breath"},
            {"wither_skeleton", "biped", "humanoid", "skeletal,undead,nether,bloodless_mob,breath"}, {"wolf", "quadruped", "canid", ""},
            {"zoglin", "quadruped", "swine", "rotting,undead,nether,breath"}, {"zombie", "biped", "humanoid", "rotting,undead,breath"},
            {"zombie_horse", "quadruped", "equine", "rotting,undead,breath"}, {"zombie_villager", "biped", "villager", "rotting,undead,breath"},
            {"zombified_piglin", "biped", "piglin", "rotting,undead,nether,breath"}};

    /** Overlays whose group id is not the spec's name: an archetype already has the id "arthropod". */
    private static final Map<String, String> OVERLAY_IDS = Map.of("arthropod", "arthropod_overlay");
    /** Overlays not built yet: none now (snow came with the powder_snow flag). */
    private static final Set<String> PENDING_OVERLAYS = Set.of();
    /** Spec 2.2 rule 2: the rigs with no head of their own, whose body is its head too. */
    private static final Set<String> SELF_CONTAINED = Set.of("blaze", "breeze", "guardian", "elder_guardian", "squid", "glow_squid", "pufferfish",
            "tadpole", "bee", "slime", "magma_cube", "strider", "ghast");
    /** Spec 2.6: bones whose slot the table names, as mob, bone, the slot's key. */
    private static final String[][] SLOTS = {
            {"silverfish", "segment2", "torso"}, {"silverfish", "segment1", "neck"}, {"silverfish", "segment0", "head"},
            {"silverfish", "segment3", "tail"}, {"silverfish", "segment6", "tail"}, {"endermite", "segment1", "torso"},
            {"endermite", "segment0", "head"}, {"endermite", "segment2", "tail"}, {"endermite", "segment3", "tail"},
            {"shulker", "base", "torso"}, {"shulker", "head", "head"}, {"shulker", "lid", "arm.shell"},
            {"wolf", "body", "torso"}, {"wolf", "upper_body", "torso_ext"}, {"wolf", "head/real_head", "head"}, {"wolf", "tail/real_tail", "tail"},
            {"snow_golem", "lower_body", "torso"}, {"snow_golem", "upper_body", "torso_ext"}, {"snow_golem", "right_arm", "arm"},
            {"spider", "body1", "torso"}, {"spider", "body0", "torso_ext"}, {"spider", "left_front_leg", "leg.front"},
            {"spider", "left_middle_front_leg", "leg.mid"}, {"spider", "right_middle_hind_leg", "leg.mid"}, {"spider", "right_hind_leg", "leg.hind"},
            {"villager", "arms", "arm.pair"}, {"witch", "arms", "arm.pair"}, {"wandering_trader", "arms", "arm.pair"},
            {"ghast", "tentacle0", "leg.tentacle"}, {"squid", "tentacle7", "leg.tentacle"},
            {"phantom", "body/right_wing_base", "arm.wing"}, {"phantom", "body/tail_base", "tail"}, {"phantom", "body/head", "head"},
            {"chicken", "left_wing", "arm.wing"}, {"parrot", "right_wing", "arm.wing"}, {"ravager", "neck", "neck"}, {"ravager", "neck/head", "head"},
            {"salmon", "body_front", "torso"}, {"salmon", "body_back", "tail"}, {"dolphin", "body/tail", "tail"}, {"rabbit", "left_haunch", "leg.hind"},
            {"sniffer", "root/bone/left_mid_leg", "leg.mid"}, {"horse", "head_parts", "head"}, {"armadillo", "body/head/head_cube", "head"},
            {"cat", "tail1", "tail"}, {"guardian", "head", "torso"}, {"guardian", "head/tail0", "tail"}, {"frog", "root/body/right_arm", "arm"},
            {"frog", "root/left_leg", "leg"}, {"wither", "shoulders", "torso"}, {"wither", "ribcage", "torso_ext"}, {"wither", "center_head", "head"},
            {"wither", "tail", "tail"}, {"warden", "bone/body/head", "head"}, {"blaze", "head", "torso"}};
    private static final Pattern SEGMENT = Pattern.compile("^segment\\d+$");

    private static ResourceLocation bb(String id) {
        return BloodAndBones.asResource(id);
    }

    private static ResourceLocation mob(String name) {
        return ResourceLocation.withDefaultNamespace(name);
    }

    /** The problems once each, cut short to fit the test's report (a book page holds 1024 characters). */
    static String shortList(List<String> problems) {
        List<String> once = List.copyOf(new LinkedHashSet<>(problems));
        String text = once.size() + ": " + String.join("; ", once);
        return text.length() > 700 ? text.substring(0, 700) + "..." : text;
    }

    /** Every one of the 79 mobs resolves to its archetype, its family (or none) and at least its overlays. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void allMobsResolve(GameTestHelper helper) {
        PartsData.Store store = PartsData.SERVER;
        List<String> problems = new ArrayList<>();
        for (String[] row : MOBS) {
            ResourceLocation entity = mob(row[0]);
            ResolvedMob resolved = store.resolve(entity, false);
            store.resolve(entity, true);
            ResourceLocation archetype = null;
            List<ResourceLocation> families = new ArrayList<>();
            Set<ResourceLocation> overlays = new LinkedHashSet<>();
            for (ResourceLocation layer : resolved.layers()) {
                MobGroup group = store.groups().get(layer);
                if (group == null) {
                    continue;
                }
                switch (group.kind()) {
                    case ARCHETYPE -> archetype = archetype == null ? layer : archetype;
                    case FAMILY -> families.add(layer);
                    case OVERLAY -> overlays.add(layer);
                    default -> {
                    }
                }
            }
            if (resolved.layers().isEmpty() || !resolved.layers().get(0).equals(bb(row[1])) || !bb(row[1]).equals(archetype)) {
                problems.add(row[0] + " archetype " + archetype + " (layers " + resolved.layers() + "), not " + row[1]);
            }
            List<ResourceLocation> family = row[2].isEmpty() ? List.of() : List.of(bb(row[2]));
            if (!families.equals(family)) {
                problems.add(row[0] + " family " + families + ", not " + family);
            }
            for (String overlay : row[3].isEmpty() ? new String[0] : row[3].split(",")) {
                if (!PENDING_OVERLAYS.contains(overlay) && !overlays.contains(bb(OVERLAY_IDS.getOrDefault(overlay, overlay)))) {
                    problems.add(row[0] + " lacks overlay " + overlay + " (has " + overlays + ")");
                }
            }
        }
        if (MOBS.length != 79 || !problems.isEmpty()) {
            helper.fail(MOBS.length + " mobs, resolved wrong " + shortList(problems));
            return;
        }
        helper.succeed();
    }

    /** Whether a slot rule, the mob's own file or its place (root, segment) names the bone, not only what it hangs from. */
    private static boolean named(PartsData.Store store, Rig rig, String bone) {
        String name = bone.substring(bone.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
        if (rig.root().name().equals(bone)) {
            return true;
        }
        MobGroup file = store.mobFile(rig.entity());
        if (file != null && (file.boneSlots().containsKey(bone) || file.boneSlots().containsKey(name))) {
            return true;
        }
        String root = rig.root().name().substring(rig.root().name().lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
        if (SEGMENT.matcher(name).matches() && SEGMENT.matcher(root).matches()) {
            return true;
        }
        for (PartSlots.Rule rule : store.slotRules().rules()) {
            if (rule.match().matcher(name).find()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Every bone of every rig gets its slot by name, not by falling through to its parent (the spec calls no bone
     * of the 79 decoration); the self-contained mobs are exactly the spec's; the slots the spec's table names.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void everyBoneHasASlot(GameTestHelper helper) {
        PartsData.Store store = PartsData.SERVER;
        List<String> problems = new ArrayList<>();
        for (String[] row : MOBS) {
            if (RigManager.forEntity(mob(row[0])).isEmpty()) {
                problems.add(row[0] + " has no rig");
            }
        }
        for (Rig rig : RigManager.all().values()) {
            for (Bone bone : rig.bones()) {
                if (!named(store, rig, bone.name())) {
                    problems.add(rig.entity() + " " + bone.name() + " falls through to " + PartSlots.of(store, rig.entity(), rig, bone.name()));
                }
            }
            String path = rig.entity().getPath();
            boolean selfContained = PartSlots.selfContained(store, rig.entity(), rig);
            if (rig.entity().getNamespace().equals("minecraft") && selfContained != SELF_CONTAINED.contains(path)) {
                problems.add(rig.entity() + (selfContained ? " has no head, but the spec gives it one" : " has a head, but the spec makes it self-contained"));
            }
        }
        for (String[] row : SLOTS) {
            Optional<Rig> rig = RigManager.forEntity(mob(row[0]));
            if (rig.isEmpty() || rig.get().bone(row[1]).isEmpty()) {
                problems.add(row[0] + " has no bone " + row[1]);
                continue;
            }
            String key = PartSlots.of(store, mob(row[0]), rig.get(), row[1]).key();
            if (!key.equals(row[2])) {
                problems.add(row[0] + " " + row[1] + " is " + key + ", not " + row[2]);
            }
        }
        if (!problems.isEmpty()) {
            helper.fail("Bones without a proper slot " + shortList(problems));
            return;
        }
        helper.succeed();
    }

    /** The ids a list names, added or removed. */
    private static void ids(TraitList list, List<ResourceLocation> out) {
        for (TraitRef ref : list.add()) {
            out.add(ref.id());
        }
        out.addAll(list.remove());
    }

    /** The traits a layer's minion data names ("traits" in any part's minion object), or a problem if it will not read. */
    private static void minionIds(String where, JsonElement minion, List<ResourceLocation> out, List<String> problems) {
        if (!minion.isJsonObject() || !minion.getAsJsonObject().has("traits")) {
            return;
        }
        TraitList.CODEC.parse(JsonOps.INSTANCE, minion.getAsJsonObject().get("traits"))
                .resultOrPartial(error -> problems.add(where + " minion traits will not read: " + error)).ifPresent(list -> ids(list, out));
    }

    private static void check(PartsData.Store store, String where, List<ResourceLocation> ids, List<String> problems) {
        for (ResourceLocation id : ids) {
            if (store.trait(id) == null) {
                problems.add(where + " names " + id);
            }
        }
    }

    /**
     * Every trait any loaded group or mob file names is loaded: in every layer as written (added or removed), and in
     * the resolved view of all 79 mobs (armour, minion, hide, organs, full set).
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void everyTraitReferenceExists(GameTestHelper helper) {
        PartsData.Store store = PartsData.SERVER;
        List<String> problems = new ArrayList<>();
        List<MobGroup> layers = new ArrayList<>(store.groups().values());
        layers.addAll(store.mobFiles().values());
        for (MobGroup layer : layers) {
            List<ResourceLocation> ids = new ArrayList<>();
            for (Map.Entry<String, MobGroup.PartEntry> part : layer.parts().entrySet()) {
                part.getValue().armour().ifPresent(list -> ids(list, ids));
                part.getValue().armourPieces().values().forEach(list -> ids(list, ids));
                part.getValue().minion().ifPresent(json -> minionIds(layer.id() + " " + part.getKey(), json, ids, problems));
            }
            layer.hide().ifPresent(list -> ids(list, ids));
            for (MobGroup.OrganEntry organ : layer.organTraits().values()) {
                organ.minion().ifPresent(list -> ids(list, ids));
                organ.armour().ifPresent(list -> ids(list, ids));
            }
            layer.fullSet().ifPresent(set -> {
                ids(set.bonus(), ids);
                ids(set.drawback(), ids);
            });
            check(store, layer.kind().name().toLowerCase(Locale.ROOT) + " " + layer.id(), ids, problems);
        }
        for (String[] row : MOBS) {
            ResolvedMob resolved = store.resolve(mob(row[0]), false);
            List<ResourceLocation> ids = new ArrayList<>();
            for (ResolvedMob.Part part : resolved.parts().values()) {
                part.armour().forEach(t -> ids.add(t.id()));
                part.pieces().values().forEach(list -> list.forEach(t -> ids.add(t.id())));
            }
            for (Map.Entry<String, List<JsonElement>> part : resolved.minion().entrySet()) {
                for (JsonElement json : part.getValue()) {
                    minionIds(row[0] + " " + part.getKey(), json, ids, problems);
                }
            }
            resolved.hide().forEach(t -> ids.add(t.id()));
            for (ResolvedMob.Organ organ : resolved.organs().values()) {
                organ.minion().forEach(t -> ids.add(t.id()));
                organ.armour().forEach(t -> ids.add(t.id()));
            }
            resolved.fullSet().ifPresent(set -> {
                set.bonus().forEach(t -> ids.add(t.id()));
                set.drawback().forEach(t -> ids.add(t.id()));
            });
            check(store, "resolved " + row[0], ids, problems);
        }
        if (!problems.isEmpty()) {
            helper.fail("Traits named but not loaded " + shortList(problems));
            return;
        }
        helper.succeed();
    }

    /**
     * Every scrap material a group or mob file names is loaded, with its four armour textures; each of the 79 mobs
     * resolves to a loaded material.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void everyMaterialLoads(GameTestHelper helper) {
        PartsData.Store store = PartsData.SERVER;
        List<String> problems = new ArrayList<>();
        List<MobGroup> layers = new ArrayList<>(store.groups().values());
        layers.addAll(store.mobFiles().values());
        for (MobGroup layer : layers) {
            layer.scrapMaterial().filter(m -> !store.materials().containsKey(m)).ifPresent(m -> problems.add(layer.id() + " names material " + m));
        }
        for (String[] row : MOBS) {
            ResourceLocation material = store.resolve(mob(row[0]), false).material();
            if (!store.materials().containsKey(material)) {
                problems.add(row[0] + " is made of " + material + ", which is not loaded");
            }
        }
        for (Map.Entry<ResourceLocation, ScrapMaterial> material : store.materials().entrySet()) {
            for (String sheet : new String[]{"_layer_1", "_layer_2", "_clean_layer_1", "_clean_layer_2"}) {
                String path = "/assets/bloodandbones/textures/models/armor/carcass_" + material.getValue().look() + sheet + ".png";
                if (BloodAndBones.class.getResource(path) == null) {
                    problems.add(material.getKey() + " has no texture " + path);
                }
            }
        }
        if (!problems.isEmpty()) {
            helper.fail("Materials missing " + shortList(problems));
            return;
        }
        helper.succeed();
    }

    /** Every parts file parses: none of any kind is left out for being written wrong. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void everyPartsFileParses(GameTestHelper helper) {
        PartsData.Store store = PartsData.SERVER;
        List<String> problems = new ArrayList<>();
        for (ResourceLocation id : store.raw(PartsData.Kind.MOB_GROUP).keySet()) {
            if (!store.groups().containsKey(id)) {
                problems.add("mob_group " + id);
            }
        }
        for (ResourceLocation id : store.raw(PartsData.Kind.TRAIT).keySet()) {
            if (store.trait(id) == null) {
                problems.add("trait " + id);
            }
        }
        for (ResourceLocation id : store.raw(PartsData.Kind.SCRAP_MATERIAL).keySet()) {
            if (!store.materials().containsKey(id)) {
                problems.add("scrap_material " + id);
            }
        }
        if (store.raw(PartsData.Kind.MOB_TRAITS).size() != store.mobFiles().size()) {
            problems.add(store.raw(PartsData.Kind.MOB_TRAITS).size() - store.mobFiles().size() + " mob_traits files");
        }
        if (!problems.isEmpty() || store.groups().isEmpty() || store.traits().isEmpty()) {
            helper.fail("Parts files that did not parse " + shortList(problems));
            return;
        }
        helper.succeed();
    }

    /** Every loaded trait, every material and every full set of the 79 mobs has an English name. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void everyNameTranslated(GameTestHelper helper) {
        PartsData.Store store = PartsData.SERVER;
        JsonObject read;
        try (var in = BloodAndBones.class.getResourceAsStream("/assets/bloodandbones/lang/en_us.json")) {
            read = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            helper.fail("Could not read the language file: " + e);
            return;
        }
        JsonObject lang = read;
        List<String> problems = new ArrayList<>();
        for (Map.Entry<ResourceLocation, Trait> trait : store.traits().entrySet()) {
            if (!lang.has(trait.getValue().name())) {
                problems.add(trait.getKey() + " (" + trait.getValue().name() + ")");
            }
        }
        for (ResourceLocation material : store.materials().keySet()) {
            String key = "scrap_material." + material.getNamespace() + "." + material.getPath();
            if (!lang.has(key)) {
                problems.add(key);
            }
        }
        for (String[] row : MOBS) {
            store.resolve(mob(row[0]), false).fullSet().filter(set -> !lang.has(set.name())).ifPresent(set -> problems.add(row[0] + "'s set " + set.name()));
        }
        if (!problems.isEmpty()) {
            helper.fail("No English name for " + shortList(problems));
            return;
        }
        helper.succeed();
    }
}
