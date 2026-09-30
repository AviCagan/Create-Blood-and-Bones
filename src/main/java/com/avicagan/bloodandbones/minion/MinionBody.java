package com.avicagan.bloodandbones.minion;

import com.avicagan.bloodandbones.carcass.rig.Bone;
import com.avicagan.bloodandbones.carcass.rig.Rig;
import com.avicagan.bloodandbones.parts.PartSlot;
import com.avicagan.bloodandbones.parts.PartSlots;
import com.avicagan.bloodandbones.parts.PartsData;
import com.avicagan.bloodandbones.parts.SlotInfo;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Where a minion's pieces go (docs/PARTS-AND-TRAITS.md section 6.10): the torso as its own mob held it, and each
 * fitted piece at a socket of the torso (the pivot of the bone it stands in for) in the piece's own rest turn,
 * at its own size. Nothing is fitted to anything: a rabbit's leg under a cow is a rabbit's leg. The whole is
 * lowered or raised so its lowest drawn box stands on the ground. The server works out the hitbox with this and
 * the client draws with it, so the two agree. Positions are in model pixels, y down, the ground at y = 24.
 */
public final class MinionBody {
    public static final float GROUND = 24.0F;

    /** A place on the torso a piece can go: a head, a limb (high for arms, low for legs), a tail, an extension. */
    public record Socket(String id, PartSlot slot, String sub, Vector3f pivot) {
        public boolean takes(PartSlot piece) {
            return switch (slot) {
                case HEAD, NECK -> piece == PartSlot.HEAD || piece == PartSlot.NECK || piece == PartSlot.TORSO;
                case ARM, LEG -> piece == PartSlot.ARM || piece == PartSlot.LEG;
                case TAIL -> piece == PartSlot.TAIL;
                case TORSO_EXT -> piece == PartSlot.TORSO_EXT;
                default -> false;
            };
        }
    }

    /** One piece drawn: its rig and bone, and where (the pose, before the lift). */
    public record Placement(PieceRef piece, Rig rig, Bone bone, Matrix4f pose, @Nullable String socket, SlotInfo slot) {
    }

    /** The whole body: its pieces, how far it is lifted, and the box round all of it (before the lift is applied to y). */
    public record Layout(List<Placement> pieces, List<Socket> sockets, float lift, Vector3f min, Vector3f max) {
        public float width() {
            return Math.max(max.x - min.x, max.z - min.z) / 16.0F;
        }

        public float height() {
            return (max.y - min.y) / 16.0F;
        }
    }

    private MinionBody() {
    }

    /** The torso's sockets: its own rig's head, limb, tail and extension bones; a blob gets a made-up set. */
    public static List<Socket> sockets(PartsData.Store store, PieceRef torso) {
        Optional<Rig> maybe = store.rig(torso.entity(), torso.baby());
        List<Socket> out = new ArrayList<>();
        if (maybe.isEmpty()) {
            return out;
        }
        Rig rig = maybe.get();
        for (Bone bone : rig.bones()) {
            if (!bone.parent().map(torso.bone()::equals).orElse(false)) {
                continue;
            }
            SlotInfo slot = PartSlots.of(store, torso.entity(), rig, bone.name());
            switch (slot.slot()) {
                case HEAD, NECK, ARM, LEG, TAIL, TORSO_EXT -> out.add(new Socket(bone.name(), slot.slot(), slot.sub(), new Vector3f(bone.offset())));
                default -> {
                }
            }
        }
        long limbs = out.stream().filter(s -> s.slot() == PartSlot.ARM || s.slot() == PartSlot.LEG).count();
        boolean head = out.stream().anyMatch(s -> s.slot() == PartSlot.HEAD || s.slot() == PartSlot.NECK);
        if (!head || limbs < 2) {
            blob(rig, rig.bone(torso.bone()).orElse(rig.root()), out, !head, limbs < 2);
        }
        return out;
    }

    /** A made-up set of sockets on a body with none of its own: a head at the top front, four limbs at the lower corners, a tail behind. */
    private static void blob(Rig rig, Bone torso, List<Socket> out, boolean head, boolean limbs) {
        Vector3f[] box = corners(new Matrix4f().translate(torso.offset()).rotate(torso.rotation()), torso);
        Vector3f min = new Vector3f(Float.MAX_VALUE);
        Vector3f max = new Vector3f(-Float.MAX_VALUE);
        for (Vector3f c : box) {
            min.min(c);
            max.max(c);
        }
        if (head) {
            out.add(new Socket("blob_head", PartSlot.HEAD, "", new Vector3f((min.x + max.x) / 2, min.y, min.z)));
        }
        if (limbs) {
            out.add(new Socket("blob_front_right", PartSlot.LEG, "front", new Vector3f(min.x, max.y, min.z)));
            out.add(new Socket("blob_front_left", PartSlot.LEG, "front", new Vector3f(max.x, max.y, min.z)));
            out.add(new Socket("blob_hind_right", PartSlot.LEG, "hind", new Vector3f(min.x, max.y, max.z)));
            out.add(new Socket("blob_hind_left", PartSlot.LEG, "hind", new Vector3f(max.x, max.y, max.z)));
        }
        out.add(new Socket("blob_tail", PartSlot.TAIL, "", new Vector3f((min.x + max.x) / 2, (min.y + max.y) / 2, max.z)));
    }

    /** Lay the body out. */
    public static Layout layout(PartsData.Store store, MinionBuild build) {
        List<Placement> pieces = new ArrayList<>();
        List<Socket> sockets = sockets(store, build.torso());
        Optional<Rig> torsoRig = store.rig(build.torso().entity(), build.torso().baby());
        if (torsoRig.isPresent()) {
            Bone torso = torsoRig.get().bone(build.torso().bone()).orElse(torsoRig.get().root());
            pieces.add(new Placement(build.torso(), torsoRig.get(), torso, new Matrix4f().translate(torso.offset()).rotate(torso.rotation()), null,
                    SlotInfo.of(PartSlot.TORSO)));
        }
        for (MinionBuild.Fitted fitted : build.parts()) {
            Socket socket = sockets.stream().filter(s -> s.id().equals(fitted.socket())).findFirst().orElse(null);
            Optional<Rig> rig = store.rig(fitted.piece().entity(), fitted.piece().baby());
            if (socket == null || rig.isEmpty()) {
                continue;
            }
            Optional<Bone> bone = rig.get().bone(fitted.piece().bone());
            if (bone.isEmpty()) {
                continue;
            }
            SlotInfo slot = PartSlots.of(store, fitted.piece().entity(), rig.get(), bone.get().name());
            pieces.add(new Placement(fitted.piece(), rig.get(), bone.get(), new Matrix4f().translate(socket.pivot()).rotate(bone.get().rotation()),
                    socket.id(), slot));
        }
        Vector3f min = new Vector3f(Float.MAX_VALUE);
        Vector3f max = new Vector3f(-Float.MAX_VALUE);
        for (Placement p : pieces) {
            for (Vector3f c : corners(p.pose(), p.bone())) {
                min.min(c);
                max.max(c);
            }
        }
        if (pieces.isEmpty()) {
            min.set(-4, 16, -4);
            max.set(4, GROUND, 4);
        }
        // the lowest drawn point goes to the ground; shorter legs dangle, a body with none lies on its belly
        float lift = GROUND - max.y;
        return new Layout(pieces, sockets, lift, min, max);
    }

    /**
     * The middle of the top of its torso, where a saddle sits and a rider is seated: in blocks, in the frame the
     * renderer turns with the body (x and y back the right way up), standing. The same on both sides.
     */
    public static Vector3f saddlePoint(Layout layout) {
        for (Placement placement : layout.pieces()) {
            if (placement.socket() == null) {
                Vector3f min = new Vector3f(Float.MAX_VALUE);
                Vector3f max = new Vector3f(-Float.MAX_VALUE);
                for (Vector3f c : corners(placement.pose(), placement.bone())) {
                    min.min(c);
                    max.max(c);
                }
                float centreX = (layout.min().x + layout.max().x) / 2.0F;
                float centreZ = (layout.min().z + layout.max().z) / 2.0F;
                // model space is drawn flipped in x and y: back to the entity's frame
                return new Vector3f(-((min.x + max.x) / 2.0F - centreX) / 16.0F, (GROUND - (min.y + layout.lift())) / 16.0F,
                        ((min.z + max.z) / 2.0F - centreZ) / 16.0F);
            }
        }
        return new Vector3f(0.0F, layout.height(), 0.0F);
    }

    /**
     * Where each of its riders sits, front first, in the frame {@link #saddlePoint} gives: one on the saddle, or several
     * spread along the torso's back (a camel's two), each in the middle of its share of the torso's length.
     */
    public static List<Vector3f> seats(Layout layout, int count) {
        Vector3f saddle = saddlePoint(layout);
        if (count <= 1) {
            return List.of(saddle);
        }
        float length = 0.0F;
        for (Placement placement : layout.pieces()) {
            if (placement.socket() == null) {
                float min = Float.MAX_VALUE;
                float max = -Float.MAX_VALUE;
                for (Vector3f c : corners(placement.pose(), placement.bone())) {
                    min = Math.min(min, c.z);
                    max = Math.max(max, c.z);
                }
                length = (max - min) / 16.0F;
            }
        }
        List<Vector3f> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            // model space's front is towards -z, as the saddle point's frame keeps it
            out.add(new Vector3f(saddle.x, saddle.y, saddle.z + length * ((i + 0.5F) / count - 0.5F)));
        }
        return List.copyOf(out);
    }

    /**
     * Where it holds and wears things, as the layout's pieces go (the same on both sides): the piece that holds what is
     * in its hand and the point it holds it at, in that piece's own frame in pixels (the far end of its first arm of hand
     * grip, as a zombie holds a sword; the middle front of a pair of arms, as a villager holds its wares; else the mouth
     * of its head, as a fox holds what it finds), and the head that wears a helmet. -1 for none.
     *
     * @param right whether the holding arm is on its right side (drawn as a right hand's item)
     * @param how   "hand", "pair" or "mouth"
     * @param other where it holds a second thing, its other hand's (a butcher's sample beside its blade): a second arm of
     *              hand grip, else the other side of a pair of arms, else its mouth; {@link Hold#NONE} with nowhere else
     */
    public record Anchors(int hold, Vector3f holdAt, boolean right, String how, int head, Hold other) {
        public static final Anchors NONE = new Anchors(-1, new Vector3f(), true, "hand", -1, Hold.NONE);
    }

    /** One place it holds something: the piece, the point in its own frame in pixels, which side, and how ("hand", "pair", "mouth"). */
    public record Hold(int piece, Vector3f at, boolean right, String how) {
        public static final Hold NONE = new Hold(-1, new Vector3f(), true, "hand");
    }

    /** Where this build holds and wears things (see {@link Anchors}). */
    public static Anchors anchors(PartsData.Store store, MinionBuild build, Layout layout) {
        PieceRef headPiece = MinionStats.head(store, build);
        int head = -1;
        int hand = -1;
        int second = -1;
        String how = "hand";
        for (int i = 0; i < layout.pieces().size(); i++) {
            Placement placement = layout.pieces().get(i);
            if (placement.socket() == null) {
                continue;
            }
            if (head < 0 && placement.piece() == headPiece) {
                head = i;
            }
            if ((hand < 0 || second < 0) && placement.slot().slot() == PartSlot.ARM) {
                var mob = store.resolve(placement.piece().entity(), placement.piece().baby());
                String grip = MinionData.field(mob, placement.slot().key(), "grip").filter(com.google.gson.JsonElement::isJsonPrimitive)
                        .map(com.google.gson.JsonElement::getAsString).orElse("hand");
                if ("hand".equals(grip)) {
                    if (hand < 0) {
                        hand = i;
                        how = "pair".equals(placement.slot().form()) ? "pair" : "hand";
                    } else if (!"pair".equals(how) && !"pair".equals(placement.slot().form())) {
                        second = i;
                    }
                }
            }
        }
        if (hand >= 0) {
            Placement arm = layout.pieces().get(hand);
            boolean right = arm.pose().getTranslation(new Vector3f()).x < 0.0F;
            Hold other = Hold.NONE;
            if (second >= 0) {
                Placement arm2 = layout.pieces().get(second);
                other = new Hold(second, grip(arm2.bone()), arm2.pose().getTranslation(new Vector3f()).x < 0.0F, "hand");
            } else if ("pair".equals(how)) {
                // a pair of folded arms holds the second thing on the other side of its middle from the first, a little out from
                // the arms so a small thing is not lost in them. The middle is the pair's own (x = 0): a villager's arms have
                // the box of one arm only, off to one side, where what it holds sits. With its box in the middle, the second
                // goes to one side of it (and what is in its hand, drawn beside it, to the other).
                Vector3f at = front(arm.bone());
                Vector3f lo = arm.bone().boxMin();
                Vector3f hi = arm.bone().boxMax();
                float side = Math.abs(at.x) > 1.0F ? -at.x : at.x + (hi.x - lo.x) * 0.3F;
                other = new Hold(hand, new Vector3f(side, at.y, at.z - 2.0F), !right, "pair");
            } else if (head >= 0) {
                other = new Hold(head, mouth(layout.pieces().get(head).bone()), true, "mouth");
            }
            return new Anchors(hand, "pair".equals(how) ? front(arm.bone()) : grip(arm.bone()), right, how, head, other);
        }
        if (head >= 0) {
            return new Anchors(head, mouth(layout.pieces().get(head).bone()), true, "mouth", head, Hold.NONE);
        }
        return Anchors.NONE;
    }

    /**
     * Where a hand grips, in its bone's own frame: the far end of its box along its length (from the joint), at the
     * middle across and at its front, as a humanoid arm's hand is 10 pixels down it and 2 forward.
     */
    static Vector3f grip(Bone bone) {
        Vector3f lo = bone.boxMin();
        Vector3f hi = bone.boxMax();
        Vector3f size = new Vector3f(hi).sub(lo);
        Vector3f at = new Vector3f(lo).add(hi).mul(0.5F);
        if (size.y >= size.x && size.y >= size.z) {
            at.y = Math.abs(hi.y) >= Math.abs(lo.y) ? hi.y : lo.y;
            at.z = lo.z;
        } else if (size.x >= size.z) {
            at.x = Math.abs(hi.x) >= Math.abs(lo.x) ? hi.x : lo.x;
        } else {
            at.z = Math.abs(hi.z) >= Math.abs(lo.z) ? hi.z : lo.z;
        }
        return at;
    }

    /** The middle of a box's front face, where a pair of folded arms holds something. */
    static Vector3f front(Bone bone) {
        Vector3f lo = bone.boxMin();
        Vector3f hi = bone.boxMax();
        return new Vector3f((lo.x + hi.x) / 2.0F, (lo.y + hi.y) / 2.0F, lo.z);
    }

    /** A head's mouth: low on its front face, a pixel out from it. */
    static Vector3f mouth(Bone bone) {
        Vector3f lo = bone.boxMin();
        Vector3f hi = bone.boxMax();
        return new Vector3f((lo.x + hi.x) / 2.0F, lo.y + (hi.y - lo.y) * 0.85F, lo.z - 1.0F);
    }

    /** The eight corners of a bone's box, placed. */
    public static Vector3f[] corners(Matrix4f pose, Bone bone) {
        Vector3f lo = bone.boxMin();
        Vector3f hi = bone.boxMax();
        Vector3f[] out = new Vector3f[8];
        for (int i = 0; i < 8; i++) {
            Vector3f c = new Vector3f((i & 1) == 0 ? lo.x : hi.x, (i & 2) == 0 ? lo.y : hi.y, (i & 4) == 0 ? lo.z : hi.z);
            out[i] = pose.transformPosition(c);
        }
        return out;
    }
}
