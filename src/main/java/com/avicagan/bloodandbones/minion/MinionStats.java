package com.avicagan.bloodandbones.minion;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.carcass.rig.Bone;
import com.avicagan.bloodandbones.carcass.rig.Rig;
import com.avicagan.bloodandbones.parts.PartSlot;
import com.avicagan.bloodandbones.parts.PartsData;
import com.avicagan.bloodandbones.parts.ResolvedMob;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * What a build adds up to (docs/PARTS-AND-TRAITS.md section 6.3-6.4), worked out from its pieces and their
 * data alone, with no world: the torso sets size and health, the head the bite, its sight, its knacks and its
 * disposition, the arms the blows, the legs the movement, and what it holds things with comes from all of them. How
 * well it does each task is worked out from these ({@link MinionFitness}).
 *
 * @param speed     movement speed attribute; a cow on four rabbit legs 0.325, on its own 0.2
 * @param mode      walk, hop or crawl (a body with no legs uses its own way of moving; most crawl at 0.12)
 * @param slots     inventory slots, from the torso's size
 * @param reservoir mB of blood it holds when full (organic)
 * @param strikes   one per arm, in the order fitted: they take turns (a zombie arm and a bear's: a punch, then a maul)
 * @param climbs    at least half its legs climb (spider legs)
 * @param rideable  a saddle can go on: at least two rideable legs under a torso heavy enough to carry someone (at least
 *                  {@link #RIDER_SHARE} of it and its legs)
 * @param flies     it keeps itself up in the air: its torso flies, hovers or floats by itself (its legs, if any, dangle), its
 *                  legs float (a ghast's tentacles), or its wings lift more than its torso weighs
 * @param lyingWidth  its hitbox lying on its side, powered down: rolled a quarter turn, its width across becomes its height
 * @param sight     how far it notices things (its targets, what a task looks for), in blocks: its head's follow range and
 *                  what its traits add to it (Keen Eye, Relentless); 4 for a head whose eyes were taken out, 2 with no head at
 *                  all (it feels its way); an echolocate or tremor sense finds its way at least 12
 * @param lift      what its wings (arms with a "lift") hold up together, in blocks cubed of torso: at least the torso's
 *                  own it flies, less it only falls slowly (docs/PARTS-AND-TRAITS.md section 6.4)
 * @param mount     how it carries riders: its seats and what steers it
 * @param berserk   its head's disposition is berserk: it goes for every creature but its own side, half again as hard
 * @param holders   what it holds things with, for the handwork of its tasks (docs/NEXT.md 1.2): each arm's grip (a pair of
 *                  arms two hands), on a body of four legs or more its legs' grips (a wolf's front paws), and its head's mouth
 * @param torsoWeight its torso mob's rig weight, which its pull is read from
 * @param knacks    how much better (or worse) than usual it is at each task it has a knack for, by task id: for each part
 *                  slot the best its pieces give (the first head only), multiplied together with its traits' knacks, held
 *                  between 0.25 and 2.5 (docs/NEXT.md 1.6); a task not named is 1
 * @param disposition its head's disposition ("meek"), "none" if its data names none, "mindless" with no head
 */
public record MinionStats(float health, float knockbackResistance, int slots, int reservoir, String mode, float speed,
                          float biteDamage, float biteKnockback, List<Strike> strikes, boolean mindless,
                          boolean climbs, boolean rideable, boolean flies,
                          float width, float height, float lyingWidth, float lyingHeight, float sight, float lift, Mount mount,
                          boolean berserk, List<Holder> holders, float torsoWeight, Map<ResourceLocation, Float> knacks, String disposition) {
    /**
     * Something it holds things with (docs/NEXT.md 1.2): its grip (hand, paw, claw, hoof, tentacle, wing, or "mouth"), how many
     * of it the piece has (a villager's pair of arms is two hands), and the piece: its mob and the part it is ("arm",
     * "leg.front", "head"). A grip counts for handwork only, never for blows or held weapons.
     */
    public record Holder(String grip, int count, ResourceLocation mob, String part) {
    }

    /** How one arm hits and holds: its style and damage, and its grip (hand, paw, claw, wing...; docs/PARTS-AND-TRAITS.md section 5.6). */
    public record Strike(String style, float damage, String grip) {
        /** A blow with no arm behind it: a bite. */
        public Strike(String style, float damage) {
            this(style, damage, "none");
        }
    }

    /**
     * How it carries riders (docs/PARTS-AND-TRAITS.md section 5.4, mount): how many at once (a camel's torso or a
     * ravager's two), and what its rider must hold to steer it: nothing but the saddle, or an item on a stick (a pig's
     * head a carrot on a stick, strider legs a warped fungus on a stick), which takes {@code wear} each time it spurs it
     * on, as a pig's or a strider's does.
     */
    public record Mount(int seats, java.util.Optional<ResourceLocation> steer, int wear) {
        public static final Mount SADDLE = new Mount(1, java.util.Optional.empty(), 0);
    }

    /** Modes a torso moves by on its own, legs or none: they win over legs, which dangle. */
    public static final List<String> SELF_FLYING = List.of("fly", "hover", "float");
    /** mB of soul blood in a Soul Canister. */
    public static final int CANISTER = 1000;
    /** How fast a body flies on wings alone, with no legs to say otherwise: a bat's pace. */
    public static final float WING_SPEED = 0.2F;
    /** A berserk head's blows land half again as hard (docs/PARTS-AND-TRAITS.md section 8.2: the zoglin). */
    public static final float BERSERK_DAMAGE = 1.5F;
    /**
     * A rideable minion's torso must be at least this share of it and its legs together, so a rabbit on horse legs cannot
     * carry you (a ravager's great head and neck are not what its legs are judged by).
     */
    public static final float RIDER_SHARE = 0.4F;

    /** A minion's health, its traits' included, stays within these (docs/PARTS-AND-TRAITS.md section 5.8). */
    public static final float MIN_HEALTH = 6.0F;
    public static final float MAX_HEALTH = 150.0F;
    /** How far a blind head notices things, and a body with no head at all (it feels its way). */
    public static final float BLIND_SIGHT = 4.0F;
    public static final float MINDLESS_SIGHT = 2.0F;
    /** How far an echolocate or tremor sense lets it notice things at least, eyes or none. */
    public static final float SENSE_SIGHT = 12.0F;
    /** Legs lend their grips (paws, hooves, claws) to handwork only on a body standing on at least this many. */
    public static final int GRIPPING_LEGS = 4;
    /** A build's knack for a task is held between these (docs/NEXT.md 1.6). */
    public static final float KNACK_MIN = 0.25F;
    public static final float KNACK_MAX = 2.5F;

    public static MinionStats of(PartsData.Store store, MinionBuild build) {
        PieceRef torso = build.torso();
        ResolvedMob torsoMob = store.resolve(torso.entity(), torso.baby());
        Optional<Rig> rig = store.rig(torso.entity(), torso.baby());
        float base = MinionData.scalar(torsoMob, "torso", "health", (float) MinionData.attribute(torso.entity(), Attributes.MAX_HEALTH, 10.0));
        float health = base * MinionData.scalar(torsoMob, "torso", "health_factor", 1.0F) * (torso.baby() ? 0.5F : 1.0F);
        health = Math.max(MIN_HEALTH, Math.min(MAX_HEALTH, health));
        float weight = rig.map(Rig::weight).orElse(1.0F);
        float volume = rig.flatMap(r -> r.bone(torso.bone())).map(MinionStats::volume).orElse(0.2F);
        int slots = Math.max(3, Math.min(27, Math.round(18.0F * volume)));
        // flesh holds blood by its size; brass holds one soul canister, two in a big torso (over a block)
        int reservoir = build.cybernetic() ? (volume > 1.0F ? 2 : 1) * CANISTER : Math.round(250.0F + 1000.0F * volume);
        if (!build.cybernetic()) {
            // power traits (a camel's hump) hold more blood; brass holds whole canisters
            reservoir = Math.round(reservoir * com.avicagan.bloodandbones.parts.effect.PowerEffect.capacity(store, build));
        }

        List<MinionBody.Socket> sockets = MinionBody.sockets(store, torso);
        long ownLegs = sockets.stream().filter(s -> s.slot() == PartSlot.LEG).count();
        List<Float> legSpeeds = new ArrayList<>();
        List<String> legModes = new ArrayList<>();
        int rideableLegs = 0;
        // what its rideable legs are steered with, one each (none: the saddle alone)
        List<Optional<com.google.gson.JsonObject>> legSteers = new ArrayList<>();
        List<Strike> strikes = new ArrayList<>();
        List<Holder> holders = new ArrayList<>();
        List<Holder> legGrips = new ArrayList<>();
        float lift = 0.0F;
        float bulk = volume(rig.flatMap(r -> r.bone(torso.bone())));
        PieceRef head = head(store, build);
        for (MinionBuild.Fitted fitted : build.parts()) {
            PieceRef piece = fitted.piece();
            Optional<Rig> pieceRig = store.rig(piece.entity(), piece.baby());
            if (pieceRig.isEmpty()) {
                continue;
            }
            var slot = com.avicagan.bloodandbones.parts.PartSlots.of(store, piece.entity(), pieceRig.get(), piece.bone());
            ResolvedMob mob = store.resolve(piece.entity(), piece.baby());
            switch (slot.slot()) {
                case LEG -> {
                    // what the legs carry is the torso: it must be heavy enough against them to seat a rider
                    bulk += volume(pieceRig.get().bone(piece.bone()));
                    float fallback = (float) Math.max(0.1, Math.min(0.35, MinionData.attribute(piece.entity(), Attributes.MOVEMENT_SPEED, 0.25)));
                    legSpeeds.add(MinionData.number(mob, slot.key(), "movement", "speed", fallback));
                    legModes.add(MinionData.text(mob, slot.key(), "movement", "mode", "walk"));
                    // rideable: "movement": {"rideable": true}, or "rideable": true on the leg
                    if (MinionData.flag(mob, slot.key(), "movement", "rideable") || MinionData.field(mob, slot.key(), "rideable")
                            .filter(com.google.gson.JsonElement::isJsonPrimitive).map(com.google.gson.JsonElement::getAsBoolean).orElse(false)) {
                        rideableLegs++;
                        legSteers.add(steer(mob, piece.traits(), slot.key()));
                    }
                    // a front paw, hoof or claw works by hand as a last resort, on a body with legs enough to stand on the rest
                    MinionData.field(mob, piece.traits(), slot.key(), "grip").filter(com.google.gson.JsonElement::isJsonPrimitive)
                            .map(com.google.gson.JsonElement::getAsString).filter(g -> !"none".equals(g))
                            .ifPresent(g -> legGrips.add(new Holder(g, 1, piece.entity(), slot.key())));
                }
                case ARM -> {
                    float blow = (float) Math.max(1.0, Math.min(10.0, 1.0 + 0.5 * MinionData.attribute(piece.entity(), Attributes.ATTACK_DAMAGE, 1.0)));
                    String style = MinionData.text(mob, slot.key(), "strike", "style", "punch");
                    float mult = MinionData.number(mob, slot.key(), "strike", "damage_mult", STYLE_DAMAGE.getOrDefault(style, 1.0F));
                    String grip = MinionData.field(mob, slot.key(), "grip").filter(com.google.gson.JsonElement::isJsonPrimitive)
                            .map(com.google.gson.JsonElement::getAsString).orElse("hand");
                    strikes.add(new Strike(style, "pacifist".equals(style) ? 0.0F : blow * mult, grip));
                    if (!"none".equals(grip)) {
                        // folded arms hold things too; a pair of arms is two hands
                        holders.add(new Holder(grip, Math.max(1, Math.round(MinionData.scalar(mob, piece.traits(), slot.key(), "hands", 1.0F))), piece.entity(), slot.key()));
                    }
                    // a wing holds up its share of the body
                    lift += Math.max(0.0F, MinionData.scalar(mob, piece.traits(), slot.key(), "lift", 0.0F));
                }
                default -> {
                }
            }
        }
        String mode;
        float speed;
        String own = MinionData.text(torsoMob, "torso", "self_move", "mode", "crawl");
        boolean flies = SELF_FLYING.contains(own);
        if (legSpeeds.isEmpty() || flies) {
            // no legs: the body moves as it can on its own (a slime hops, a fish swims, most crawl)
            mode = own;
            // a mob's speed counts about squared in how fast it goes: 0.12 is a slow drag, 0.05 would barely move
            speed = MinionData.number(torsoMob, "torso", "self_move", "speed", 0.12F);
        } else {
            float mean = (float) legSpeeds.stream().mapToDouble(Float::doubleValue).average().orElse(0.2);
            speed = mean * Math.min(1.0F, legSpeeds.size() / (float) Math.max(2, ownLegs));
            mode = majority(legModes, legSpeeds);
        }
        // wings strong enough for the torso they are stitched to fly it; weaker ones only slow its fall
        boolean winged = lift > 0.0F && lift >= volume;
        if (winged && !SELF_FLYING.contains(mode)) {
            mode = "fly";
            speed = Math.max(speed, WING_SPEED);
        }
        flies = SELF_FLYING.contains(mode);
        float bite = 1.0F;
        float knock = 0.0F;
        boolean mindless = head == null;
        float sight = MINDLESS_SIGHT;
        Optional<com.google.gson.JsonObject> headSteer = Optional.empty();
        boolean berserk = false;
        String disposition = MinionDisposition.MINDLESS;
        boolean senses = false;
        boolean seeing = false;
        if (legSpeeds.size() >= GRIPPING_LEGS) {
            holders.addAll(legGrips);
        }
        if (head != null) {
            ResolvedMob headMob = store.resolve(head.entity(), head.baby());
            boolean blind = blind(headMob, head);
            seeing = !blind;
            bite = MinionData.number(headMob, head.traits(), "head", "bite", "damage", 1.0F + 0.25F * (float) MinionData.attribute(head.entity(), Attributes.ATTACK_DAMAGE, 0.0));
            knock = MinionData.number(headMob, head.traits(), "head", "bite", "knockback", 0.0F);
            sight = blind ? BLIND_SIGHT : MinionData.scalar(headMob, head.traits(), "head", "follow_range",
                    (float) MinionData.attribute(head.entity(), Attributes.FOLLOW_RANGE, 16.0));
            headSteer = steer(headMob, head.traits(), "head");
            disposition = MinionData.field(headMob, head.traits(), "head", "disposition").filter(com.google.gson.JsonElement::isJsonPrimitive)
                    .map(com.google.gson.JsonElement::getAsString).orElse("none");
            berserk = "berserk".equals(disposition);
            // any head holds things in its mouth
            holders.add(new Holder("mouth", 1, head.entity(), "head"));
            List<ResourceLocation> headSenses = MinionData.ids(headMob, head.traits(), "head", "senses");
            senses = headSenses.contains(BloodAndBones.asResource("echolocate")) || headSenses.contains(BloodAndBones.asResource("tremor"));
        }
        // what its traits add to a seeing head's follow range (Keen Eye, Relentless), as its attribute would take them (no
        // eyes see no farther for them); a sense that finds its way without eyes (echolocation, tremor) at least so far
        Map<ResourceLocation, Integer> levels = MinionData.levels(store, build);
        if (seeing) {
            sight = (float) MinionData.attributed(sight, Attributes.FOLLOW_RANGE, MinionData.effects(store, levels,
                    com.avicagan.bloodandbones.parts.TraitEffects.AttributeEffect.class));
        }
        senses |= MinionData.effects(store, levels, com.avicagan.bloodandbones.parts.effect.SenseEffect.class).stream()
                .anyMatch(f -> f.always() && ("echolocate".equals(f.effect().kind()) || "tremor".equals(f.effect().kind())));
        if (senses) {
            sight = Math.max(sight, SENSE_SIGHT);
        }
        MinionBody.Layout layout = MinionBody.layout(store, build);
        float across = (layout.max().x - layout.min().x) / 16.0F;
        float along = Math.max(layout.max().y - layout.min().y, layout.max().z - layout.min().z) / 16.0F;
        long climbing = legModes.stream().filter("climb"::equals).count();
        boolean climbs = !legModes.isEmpty() && climbing * 2 >= legModes.size();
        float torsoShare = bulk <= 0.0F ? 0.0F : volume(rig.flatMap(r -> r.bone(torso.bone()))) / bulk;
        boolean rideable = !flies && rideableLegs >= 2 && rideableLegs * 2 >= legModes.size() && torsoShare >= RIDER_SHARE;
        int seats = Math.max(1, Math.min(4, Math.round(MinionData.scalar(torsoMob, torso.traits(), "torso", "seats", 1.0F))));
        // the head's steering first (a pig's carrot), then what most of its rideable legs share (a strider's fungus)
        Optional<com.google.gson.JsonObject> steer = headSteer.isPresent() ? headSteer : shared(legSteers);
        Mount mount = steer.filter(o -> o.has("item")).map(o -> new Mount(seats, Optional.ofNullable(ResourceLocation.tryParse(o.get("item").getAsString())),
                o.has("wear") ? Math.max(0, o.get("wear").getAsInt()) : 1)).orElse(new Mount(seats, Optional.empty(), 0));
        return new MinionStats(health, Math.max(0.0F, Math.min(0.9F, weight / 6.0F)), slots, reservoir, mode, speed, bite, knock, List.copyOf(strikes),
                mindless, climbs, rideable, flies, Math.max(0.3F, Math.min(3.0F, layout.width())), Math.max(0.3F, Math.min(4.0F, layout.height())),
                Math.max(0.3F, Math.min(4.0F, along)), Math.max(0.3F, Math.min(3.0F, across)), sight, lift, mount, berserk, List.copyOf(holders), weight,
                knacks(store, build, head, levels), disposition);
    }

    /** A body not yet built (a minion saved before minions were built of parts): mindless, crawling. */
    public static final MinionStats NOTHING = new MinionStats(10, 0, 3, 250, "crawl", 0.12F, 1, 0, List.of(), true, false, false, false,
            0.6F, 0.6F, 0.6F, 0.6F, MINDLESS_SIGHT, 0.0F, Mount.SADDLE, false, List.of(), 1.0F, Map.of(), MinionDisposition.MINDLESS);

    /**
     * Where one part of a build's knack for a task comes from: the best piece of a part slot ({@code slot} "head", "torso",
     * "arm", "leg" or "tail", {@code from} its mob), or a trait's {@code task_knack} ({@code slot} "trait", {@code from} the
     * trait), and what it multiplies the knack by.
     */
    public record KnackPart(String slot, ResourceLocation from, float value) {
    }

    /**
     * A build's knacks (docs/NEXT.md 1.6): for each part slot (head, torso, arms, legs, tail) the best knack its pieces give
     * each task, a piece naming none counting 1, and of heads the first only; these multiplied together, and by its traits'
     * knacks ({@code task_knack}), each held between {@link #KNACK_MIN} and {@link #KNACK_MAX}.
     */
    static Map<ResourceLocation, Float> knacks(PartsData.Store store, MinionBuild build, @org.jetbrains.annotations.Nullable PieceRef head,
                                               Map<ResourceLocation, Integer> levels) {
        Map<ResourceLocation, Float> out = new java.util.TreeMap<>();
        knackParts(store, build, head, levels).forEach((task, parts) -> {
            float knack = 1.0F;
            for (KnackPart part : parts) {
                knack *= part.value();
            }
            out.put(task, Math.max(KNACK_MIN, Math.min(KNACK_MAX, knack)));
        });
        return java.util.Collections.unmodifiableMap(out);
    }

    /** The parts of a build's knack for each task it has one for, where each comes from (for the task screen's reasons). */
    public static Map<ResourceLocation, List<KnackPart>> knackParts(PartsData.Store store, MinionBuild build) {
        return knackParts(store, build, head(store, build), MinionData.levels(store, build));
    }

    private static Map<ResourceLocation, List<KnackPart>> knackParts(PartsData.Store store, MinionBuild build, @org.jetbrains.annotations.Nullable PieceRef head,
                                                                    Map<ResourceLocation, Integer> levels) {
        // by slot, each piece's knacks
        Map<String, List<Map.Entry<ResourceLocation, Map<ResourceLocation, Float>>>> slots = new java.util.LinkedHashMap<>();
        PieceRef torso = build.torso();
        slots.computeIfAbsent("torso", k -> new ArrayList<>())
                .add(Map.entry(torso.entity(), MinionData.knacks(store.resolve(torso.entity(), torso.baby()), torso.traits(), "torso")));
        for (MinionBuild.Fitted fitted : build.parts()) {
            PieceRef piece = fitted.piece();
            Optional<Rig> rig = store.rig(piece.entity(), piece.baby());
            if (rig.isEmpty()) {
                continue;
            }
            var slot = com.avicagan.bloodandbones.parts.PartSlots.of(store, piece.entity(), rig.get(), piece.bone());
            String group;
            String key;
            if (piece == head) {
                group = "head";
                key = "head";
            } else {
                switch (slot.slot()) {
                    case TORSO, TORSO_EXT -> {
                        group = "torso";
                        key = "torso";
                    }
                    case ARM, LEG, TAIL -> {
                        group = slot.slot().getSerializedName();
                        key = slot.key();
                    }
                    default -> {
                        // a second head, a neck, a stray bit: nothing
                        continue;
                    }
                }
            }
            slots.computeIfAbsent(group, k -> new ArrayList<>())
                    .add(Map.entry(piece.entity(), MinionData.knacks(store.resolve(piece.entity(), piece.baby()), piece.traits(), key)));
        }
        Map<ResourceLocation, List<KnackPart>> out = new java.util.TreeMap<>();
        slots.forEach((group, pieces) -> {
            java.util.Set<ResourceLocation> tasks = new java.util.LinkedHashSet<>();
            pieces.forEach(p -> tasks.addAll(p.getValue().keySet()));
            for (ResourceLocation task : tasks) {
                // the best piece of the slot; one naming none counts 1
                float best = 0.0F;
                ResourceLocation from = null;
                for (Map.Entry<ResourceLocation, Map<ResourceLocation, Float>> piece : pieces) {
                    float knack = piece.getValue().getOrDefault(task, 1.0F);
                    if (knack > best) {
                        best = knack;
                        from = piece.getKey();
                    }
                }
                if (best != 1.0F) {
                    out.computeIfAbsent(task, t -> new ArrayList<>()).add(new KnackPart(group, from, best));
                }
            }
        });
        // an organ's or a hide's knack for one task
        for (MinionData.Found<com.avicagan.bloodandbones.parts.effect.TaskKnackEffect> found
                : MinionData.effects(store, levels, com.avicagan.bloodandbones.parts.effect.TaskKnackEffect.class)) {
            if (found.always()) {
                out.computeIfAbsent(found.effect().task(), t -> new ArrayList<>()).add(new KnackPart("trait", found.trait(), found.effect().at(found.level())));
            }
        }
        return out;
    }

    /** Its knack for this task: 1 for one it has none for. */
    public float knack(ResourceLocation task) {
        return knacks.getOrDefault(task, 1.0F);
    }

    /** A part's "steer" ({"item": ..., "wear": ...}), if its data gives one. */
    private static Optional<com.google.gson.JsonObject> steer(ResolvedMob mob, java.util.Map<String, String> traits, String key) {
        return MinionData.field(mob, traits, key, "steer").filter(com.google.gson.JsonElement::isJsonObject).map(com.google.gson.JsonElement::getAsJsonObject);
    }

    /** What at least half of these name, the first such (none if they disagree or most name nothing). */
    private static Optional<com.google.gson.JsonObject> shared(List<Optional<com.google.gson.JsonObject>> steers) {
        for (Optional<com.google.gson.JsonObject> steer : steers) {
            if (steer.isPresent() && steers.stream().filter(steer::equals).count() * 2 >= steers.size()) {
                return steer;
            }
        }
        return Optional.empty();
    }

    /**
     * Whether this head is blind: both its eyes were taken out at the Surgical Rig (the piece counts the organs taken),
     * and no sense of its data (echolocate, tremor) stands in for sight.
     */
    static boolean blind(ResolvedMob headMob, PieceRef head) {
        if (!com.avicagan.bloodandbones.machine.CarcassMachineBlockEntity.isHead(head.bone())
                || com.avicagan.bloodandbones.body.Surgery.organsTaken(head.toPiece()) < 2) {
            return false;
        }
        List<ResourceLocation> senses = MinionData.ids(headMob, head.traits(), "head", "senses");
        return !senses.contains(BloodAndBones.asResource("echolocate")) && !senses.contains(BloodAndBones.asResource("tremor"));
    }

    /**
     * Its head, which sets its bite, sight, knacks and disposition: the first piece fitted in a head or neck socket (of
     * several heads, the first counts: section 6.4), else a torso-like piece standing in for one; null for none (mindless).
     */
    @org.jetbrains.annotations.Nullable
    public static PieceRef head(PartsData.Store store, MinionBuild build) {
        PieceRef stand = null;
        for (MinionBuild.Fitted fitted : build.parts()) {
            PieceRef piece = fitted.piece();
            Optional<Rig> rig = store.rig(piece.entity(), piece.baby());
            if (rig.isEmpty()) {
                continue;
            }
            PartSlot slot = com.avicagan.bloodandbones.parts.PartSlots.of(store, piece.entity(), rig.get(), piece.bone()).slot();
            if (slot == PartSlot.HEAD || slot == PartSlot.NECK) {
                return piece;
            }
            if (slot == PartSlot.TORSO && stand == null) {
                stand = piece;
            }
        }
        return stand;
    }

    /** The mode most legs share; a tie goes to the slower. */
    private static String majority(List<String> modes, List<Float> speeds) {
        String best = modes.get(0);
        int bestCount = 0;
        float bestSpeed = Float.MAX_VALUE;
        for (String mode : modes) {
            int count = 0;
            float slowest = Float.MAX_VALUE;
            for (int i = 0; i < modes.size(); i++) {
                if (modes.get(i).equals(mode)) {
                    count++;
                    slowest = Math.min(slowest, speeds.get(i));
                }
            }
            if (count > bestCount || count == bestCount && slowest < bestSpeed) {
                best = mode;
                bestCount = count;
                bestSpeed = slowest;
            }
        }
        return best;
    }

    /** How hard each strike style hits against a plain blow (section 5.6): scrabble is fast and light, a slam heavy. */
    public static final java.util.Map<String, Float> STYLE_DAMAGE = java.util.Map.of("scrabble", 0.5F, "slam", 1.2F, "flap", 0.0F, "pacifist", 0.0F,
            "claw", 0.9F, "ram", 1.3F, "kick", 1.1F);

    /** Its legs walk the bottom of water at full speed, never paddling up (a drowned's, an iron golem's). */
    public boolean sinks() {
        return "sink".equals(mode);
    }

    /** It has wings, but too weak to fly its torso: they slow its fall instead. */
    public boolean slowFalls() {
        return lift > 0.0F && !flies;
    }

    /** Whether it has an arm that hits at all, or no arm and a head to bite with (a body with neither has nothing to fight with). */
    public boolean fights() {
        return strikes.isEmpty() ? !mindless : strikes.stream().anyMatch(s -> !"pacifist".equals(s.style()));
    }

    private static float volume(Optional<Bone> bone) {
        return bone.map(MinionStats::volume).orElse(0.0F);
    }

    /** A bone's box in blocks cubed. */
    static float volume(Bone bone) {
        var size = bone.boxSize();
        return size.x * size.y * size.z / 4096.0F;
    }
}
