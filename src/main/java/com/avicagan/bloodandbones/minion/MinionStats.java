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
 * @param jobs      what its head lets it do, first the one it starts with; a body with no head only keeps company
 * @param lyingWidth  its hitbox lying on its side, powered down: rolled a quarter turn, its width across becomes its height
 */
public record MinionStats(float health, float knockbackResistance, int slots, int reservoir, String mode, float speed,
                          float biteDamage, float biteKnockback, List<Float> arms, List<ResourceLocation> jobs, boolean mindless,
                          float width, float height, float lyingWidth, float lyingHeight) {
    public static final float MAX_HEALTH = 150.0F;
    public static final ResourceLocation COMPANION = BloodAndBones.asResource("companion");
    /** The jobs built so far; others a head names are left out until they are. */
    public static final List<ResourceLocation> JOBS = List.of(COMPANION, BloodAndBones.asResource("courier"), BloodAndBones.asResource("farmer"),
            BloodAndBones.asResource("bodyguard"), BloodAndBones.asResource("guard"), BloodAndBones.asResource("surgeon"));
    /** Jobs that need a hand to do: a minion with no arm fitted is not offered them. */
    public static final List<ResourceLocation> HANDS = List.of(BloodAndBones.asResource("farmer"), BloodAndBones.asResource("surgeon"));

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
        int reservoir = Math.round(250.0F + 1000.0F * volume);

        List<MinionBody.Socket> sockets = MinionBody.sockets(store, torso);
        long ownLegs = sockets.stream().filter(s -> s.slot() == PartSlot.LEG).count();
        List<Float> legSpeeds = new ArrayList<>();
        List<String> legModes = new ArrayList<>();
        List<Float> arms = new ArrayList<>();
        PieceRef head = null;
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
                    float fallback = (float) Math.max(0.1, Math.min(0.35, MinionData.attribute(piece.entity(), Attributes.MOVEMENT_SPEED, 0.25)));
                    legSpeeds.add(MinionData.number(mob, slot.key(), "movement", "speed", fallback));
                    legModes.add(MinionData.text(mob, slot.key(), "movement", "mode", "walk"));
                }
                case ARM -> arms.add((float) Math.max(1.0, Math.min(10.0, 1.0 + 0.5 * MinionData.attribute(piece.entity(), Attributes.ATTACK_DAMAGE, 1.0))));
                case HEAD, NECK -> head = piece;
                case TORSO -> head = head == null ? piece : head;
                default -> {
                }
            }
        }
        String mode;
        float speed;
        if (legSpeeds.isEmpty()) {
            // no legs: the body moves as it can on its own (a slime hops, a fish swims, most crawl)
            mode = MinionData.text(torsoMob, "torso", "self_move", "mode", "crawl");
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
        if (head != null) {
            ResolvedMob headMob = store.resolve(head.entity(), head.baby());
            for (ResourceLocation job : MinionData.ids(headMob, "head", "jobs")) {
                if (JOBS.contains(job) && !jobs.contains(job) && (!arms.isEmpty() || !HANDS.contains(job))) {
                    jobs.add(job);
                }
            }
            bite = MinionData.number(headMob, "head", "bite", "damage", 1.0F + 0.25F * (float) MinionData.attribute(head.entity(), Attributes.ATTACK_DAMAGE, 0.0));
            knock = MinionData.number(headMob, "head", "bite", "knockback", 0.0F);
        }
        if (jobs.isEmpty()) {
            jobs.add(COMPANION);
        }
        MinionBody.Layout layout = MinionBody.layout(store, build);
        float across = (layout.max().x - layout.min().x) / 16.0F;
        float along = Math.max(layout.max().y - layout.min().y, layout.max().z - layout.min().z) / 16.0F;
        return new MinionStats(health, Math.max(0.0F, Math.min(0.9F, weight / 6.0F)), slots, reservoir, mode, speed, bite, knock, List.copyOf(arms),
                List.copyOf(jobs), mindless, Math.max(0.3F, Math.min(3.0F, layout.width())), Math.max(0.3F, Math.min(4.0F, layout.height())),
                Math.max(0.3F, Math.min(4.0F, along)), Math.max(0.3F, Math.min(3.0F, across)));
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

    /** A bone's box in blocks cubed. */
    static float volume(Bone bone) {
        var size = bone.boxSize();
        return size.x * size.y * size.z / 4096.0F;
    }
}
