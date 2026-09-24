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
import java.util.Optional;

/**
 * What a build adds up to (docs/PARTS-AND-TRAITS.md section 6.3-6.4), worked out from its pieces and their
 * data alone, with no world: the torso sets size and health, the head the jobs and the bite, the arms the blows,
 * the legs the movement.
 *
 * @param speed     movement speed attribute; a cow on four rabbit legs 0.325, on its own 0.2
 * @param mode      walk, hop or crawl (a body with no legs uses its own way of moving; most crawl at 0.12)
 * @param slots     inventory slots, from the torso's size
 * @param reservoir mB of blood it holds when full (organic)
 * @param jobs      what its head lets it do, in its order, less what needs a hand it lacks or eyes taken out (it wakes to
 *                  the first it can do with nothing in hand, hunting aside: MinionJobs#wakeJob); a body with no head only keeps company
 * @param strikes   one per arm, in the order fitted: they take turns (a zombie arm and a bear's: a punch, then a maul)
 * @param climbs    at least half its legs climb (spider legs)
 * @param rideable  a saddle can go on: at least two rideable legs under a torso heavy enough to carry someone
 * @param flies     its torso flies, hovers or floats by itself (its legs, if any, dangle)
 * @param lyingWidth  its hitbox lying on its side, powered down: rolled a quarter turn, its width across becomes its height
 * @param sight     how far it notices things (its targets, what a job looks for), in blocks: its head's follow range, 4
 *                  for a head whose eyes were taken out, 8 with no head at all
 */
public record MinionStats(float health, float knockbackResistance, int slots, int reservoir, String mode, float speed,
                          float biteDamage, float biteKnockback, List<Strike> strikes, List<ResourceLocation> jobs, boolean mindless,
                          boolean climbs, boolean rideable, boolean flies,
                          float width, float height, float lyingWidth, float lyingHeight, float sight) {
    /** How one arm hits and holds: its style and damage, and its grip (hand, paw, claw, wing...; docs/PARTS-AND-TRAITS.md section 5.6). */
    public record Strike(String style, float damage, String grip) {
        /** A blow with no arm behind it: a bite. */
        public Strike(String style, float damage) {
            this(style, damage, "none");
        }
    }

    /** Modes a torso moves by on its own, legs or none: they win over legs, which dangle. */
    public static final List<String> SELF_FLYING = List.of("fly", "hover", "float");
    /** mB of soul blood in a Soul Canister. */
    public static final int CANISTER = 1000;
    /** A rideable minion's torso must be at least this share of its whole bulk, so a rabbit on horse legs cannot carry you. */
    public static final float RIDER_SHARE = 0.4F;

    public static final float MAX_HEALTH = 150.0F;
    public static final ResourceLocation COMPANION = BloodAndBones.asResource("companion");
    /**
     * The jobs built so far (docs/PARTS-AND-TRAITS.md section 6.9); others a head names are left out until they are
     * (the sapper waits for the detonate effect).
     */
    public static final List<ResourceLocation> JOBS = List.of(COMPANION, BloodAndBones.asResource("courier"), BloodAndBones.asResource("farmer"),
            BloodAndBones.asResource("bodyguard"), BloodAndBones.asResource("guard"), BloodAndBones.asResource("surgeon"),
            BloodAndBones.asResource("sentry"), BloodAndBones.asResource("scavenger"), BloodAndBones.asResource("herder"),
            BloodAndBones.asResource("fisher"), BloodAndBones.asResource("hunter"), BloodAndBones.asResource("hauler"),
            BloodAndBones.asResource("butcher"), BloodAndBones.asResource("medic"), BloodAndBones.asResource("barterer"),
            BloodAndBones.asResource("digger"));
    /**
     * Jobs that need a hand to do (section 5.6): a minion with no arm of hand grip is not offered them, but for the
     * farmer, whom a paw or claw does for too.
     */
    public static final List<ResourceLocation> HANDS = List.of(BloodAndBones.asResource("farmer"), BloodAndBones.asResource("surgeon"),
            BloodAndBones.asResource("butcher"), BloodAndBones.asResource("medic"));
    /** Grips that can harvest: a hand, a paw, a claw. An arm whose data names none has a hand. */
    public static final List<String> HARVESTING = List.of("hand", "paw", "claw");
    /** Jobs that need eyes: a head whose two eyes were taken out is not offered them (section 6.4). */
    public static final List<ResourceLocation> SIGHT = List.of(BloodAndBones.asResource("farmer"), BloodAndBones.asResource("sentry"),
            BloodAndBones.asResource("surgeon"), BloodAndBones.asResource("hunter"), BloodAndBones.asResource("fisher"));
    /** How far a blind head notices things, and a body with no head at all. */
    public static final float BLIND_SIGHT = 4.0F;
    public static final float MINDLESS_SIGHT = 8.0F;

    public static MinionStats of(PartsData.Store store, MinionBuild build) {
        PieceRef torso = build.torso();
        ResolvedMob torsoMob = store.resolve(torso.entity(), torso.baby());
        Optional<Rig> rig = store.rig(torso.entity(), torso.baby());
        float base = MinionData.scalar(torsoMob, "torso", "health", (float) MinionData.attribute(torso.entity(), Attributes.MAX_HEALTH, 10.0));
        float health = base * MinionData.scalar(torsoMob, "torso", "health_factor", 1.0F) * (torso.baby() ? 0.5F : 1.0F);
        health = Math.max(6.0F, Math.min(MAX_HEALTH, health));
        float weight = rig.map(Rig::weight).orElse(1.0F);
        float volume = rig.flatMap(r -> r.bone(torso.bone())).map(MinionStats::volume).orElse(0.2F);
        int slots = Math.max(3, Math.min(27, Math.round(18.0F * volume)));
        // flesh holds blood by its size; brass holds one soul canister, two in a big torso (over a block)
        int reservoir = build.cybernetic() ? (volume > 1.0F ? 2 : 1) * CANISTER : Math.round(250.0F + 1000.0F * volume);

        List<MinionBody.Socket> sockets = MinionBody.sockets(store, torso);
        long ownLegs = sockets.stream().filter(s -> s.slot() == PartSlot.LEG).count();
        List<Float> legSpeeds = new ArrayList<>();
        List<String> legModes = new ArrayList<>();
        int rideableLegs = 0;
        List<Strike> strikes = new ArrayList<>();
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
            bulk += volume(pieceRig.get().bone(piece.bone()));
            switch (slot.slot()) {
                case LEG -> {
                    float fallback = (float) Math.max(0.1, Math.min(0.35, MinionData.attribute(piece.entity(), Attributes.MOVEMENT_SPEED, 0.25)));
                    legSpeeds.add(MinionData.number(mob, slot.key(), "movement", "speed", fallback));
                    legModes.add(MinionData.text(mob, slot.key(), "movement", "mode", "walk"));
                    // rideable: "movement": {"rideable": true}, or "rideable": true on the leg
                    if (MinionData.flag(mob, slot.key(), "movement", "rideable") || MinionData.field(mob, slot.key(), "rideable")
                            .filter(com.google.gson.JsonElement::isJsonPrimitive).map(com.google.gson.JsonElement::getAsBoolean).orElse(false)) {
                        rideableLegs++;
                    }
                }
                case ARM -> {
                    float blow = (float) Math.max(1.0, Math.min(10.0, 1.0 + 0.5 * MinionData.attribute(piece.entity(), Attributes.ATTACK_DAMAGE, 1.0)));
                    String style = MinionData.text(mob, slot.key(), "strike", "style", "punch");
                    float mult = MinionData.number(mob, slot.key(), "strike", "damage_mult", STYLE_DAMAGE.getOrDefault(style, 1.0F));
                    String grip = MinionData.field(mob, slot.key(), "grip").filter(com.google.gson.JsonElement::isJsonPrimitive)
                            .map(com.google.gson.JsonElement::getAsString).orElse("hand");
                    strikes.add(new Strike(style, "pacifist".equals(style) ? 0.0F : blow * mult, grip));
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
        List<ResourceLocation> jobs = new ArrayList<>();
        float bite = 1.0F;
        float knock = 0.0F;
        boolean mindless = head == null;
        float sight = MINDLESS_SIGHT;
        if (head != null) {
            ResolvedMob headMob = store.resolve(head.entity(), head.baby());
            boolean blind = blind(headMob, head);
            boolean hand = strikes.stream().anyMatch(s -> "hand".equals(s.grip()));
            boolean harvests = strikes.stream().anyMatch(s -> HARVESTING.contains(s.grip()));
            // the head's own traits pick its variant (a villager's profession names its jobs)
            for (ResourceLocation job : MinionData.ids(headMob, head.traits(), "head", "jobs")) {
                boolean held = !HANDS.contains(job) || hand || harvests && job.equals(BloodAndBones.asResource("farmer"));
                if (JOBS.contains(job) && !jobs.contains(job) && held && !(blind && SIGHT.contains(job))) {
                    jobs.add(job);
                }
            }
            bite = MinionData.number(headMob, head.traits(), "head", "bite", "damage", 1.0F + 0.25F * (float) MinionData.attribute(head.entity(), Attributes.ATTACK_DAMAGE, 0.0));
            knock = MinionData.number(headMob, head.traits(), "head", "bite", "knockback", 0.0F);
            sight = blind ? BLIND_SIGHT : MinionData.scalar(headMob, head.traits(), "head", "follow_range",
                    (float) MinionData.attribute(head.entity(), Attributes.FOLLOW_RANGE, 16.0));
        }
        if (jobs.isEmpty()) {
            jobs.add(COMPANION);
        }
        MinionBody.Layout layout = MinionBody.layout(store, build);
        float across = (layout.max().x - layout.min().x) / 16.0F;
        float along = Math.max(layout.max().y - layout.min().y, layout.max().z - layout.min().z) / 16.0F;
        long climbing = legModes.stream().filter("climb"::equals).count();
        boolean climbs = !legModes.isEmpty() && climbing * 2 >= legModes.size();
        float torsoShare = bulk <= 0.0F ? 0.0F : volume(rig.flatMap(r -> r.bone(torso.bone()))) / bulk;
        boolean rideable = !flies && rideableLegs >= 2 && rideableLegs * 2 >= legModes.size() && torsoShare >= RIDER_SHARE;
        return new MinionStats(health, Math.max(0.0F, Math.min(0.9F, weight / 6.0F)), slots, reservoir, mode, speed, bite, knock, List.copyOf(strikes),
                List.copyOf(jobs), mindless, climbs, rideable, flies, Math.max(0.3F, Math.min(3.0F, layout.width())), Math.max(0.3F, Math.min(4.0F, layout.height())),
                Math.max(0.3F, Math.min(4.0F, along)), Math.max(0.3F, Math.min(3.0F, across)), sight);
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
     * Its head, which sets its jobs, bite and sight: the first piece fitted in a head or neck socket (of several heads,
     * the first sets the job: section 6.4), else a torso-like piece standing in for one; null for none (mindless).
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

    /** Whether it has an arm that hits at all. */
    public boolean fights() {
        return strikes.isEmpty() || strikes.stream().anyMatch(s -> !"pacifist".equals(s.style()));
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
