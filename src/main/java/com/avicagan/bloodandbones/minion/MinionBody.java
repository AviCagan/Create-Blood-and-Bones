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
