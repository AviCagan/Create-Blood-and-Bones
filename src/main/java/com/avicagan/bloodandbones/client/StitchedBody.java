package com.avicagan.bloodandbones.client;

import com.avicagan.bloodandbones.carcass.CarcassLook;
import com.avicagan.bloodandbones.carcass.rig.Bone;
import com.avicagan.bloodandbones.carcass.rig.Rig;
import com.avicagan.bloodandbones.minion.MinionBody;
import com.avicagan.bloodandbones.minion.MinionBuild;
import com.avicagan.bloodandbones.minion.PieceRef;
import com.avicagan.bloodandbones.parts.PartSlot;
import com.avicagan.bloodandbones.parts.PartsData;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Drawing a minion's body (docs/PARTS-AND-TRAITS.md section 6.10): each piece of carcass it was built of, in the
 * look its own mob had, at the place {@link MinionBody} gives it. Shared by the walking minion and the one being
 * built on the Surgery Table. Everything here is in entity model space: pixels, y down, the ground at y = 24.
 */
public final class StitchedBody {
    /** How it is moving this frame: the walk cycle, where its head looks, how it gets about. */
    public record Motion(float walkPosition, float walkSpeed, float headYaw, float headPitch, String mode, float age) {
        public static final Motion STILL = new Motion(0, 0, 0, 0, "walk", 0);
    }

    /** What drawing a piece needs that never changes: its coats, whether it bleeds, its raw cut ends, the socket's slot it moves as. */
    private record Look(List<CarcassLook.Coat> coats, boolean bloody, List<String> cuts, PartSlot slot) {
    }

    /** A build laid out, with each piece's look (in the layout's order) and the torso's middle, front to back. */
    private record Cached(int generation, MinionBody.Layout layout, List<Look> looks, float torsoZ) {
    }

    private static final Map<MinionBuild, Cached> LAYOUTS = new ConcurrentHashMap<>();

    private StitchedBody() {
    }

    /** The layout of a build, worked out once per build and data load. */
    public static MinionBody.Layout layout(MinionBuild build) {
        return cached(build).layout();
    }

    private static Cached cached(MinionBuild build) {
        PartsData.Store store = PartsData.CLIENT;
        Cached cached = LAYOUTS.get(build);
        if (cached == null || cached.generation() != store.generation()) {
            if (LAYOUTS.size() > 256) {
                LAYOUTS.clear();
            }
            MinionBody.Layout layout = MinionBody.layout(store, build);
            List<Look> looks = new ArrayList<>();
            for (MinionBody.Placement placement : layout.pieces()) {
                looks.add(look(layout, placement));
            }
            cached = new Cached(store.generation(), layout, List.copyOf(looks), torsoCentreZ(layout));
            LAYOUTS.put(build, cached);
        }
        return cached;
    }

    /**
     * Draw the body. Standing, its lowest point is on the ground; lying (powered down, on the table), it is
     * rolled onto its side and its lowest point is on the ground again.
     *
     * @param tint ARGB laid over every piece (a hurt flash), -1 for none
     */
    public static void draw(MinionBuild build, Motion motion, boolean lying, int tint, PoseStack ms, MultiBufferSource buffers, int light) {
        Cached cached = cached(build);
        MinionBody.Layout layout = cached.layout();
        ms.pushPose();
        // centred over where it stands, so the hitbox (as wide as its widest side) holds all of it
        float centreX = (layout.min().x + layout.max().x) / 2.0F;
        float centreZ = (layout.min().z + layout.max().z) / 2.0F;
        if (lying) {
            // roll a quarter turn onto its side: x becomes height, so the widest side is what it lies on
            ms.translate((layout.min().y + layout.max().y) / 32.0F, (MinionBody.GROUND - layout.max().x) / 16.0F, -centreZ / 16.0F);
            ms.mulPose(com.mojang.math.Axis.ZP.rotation(Mth.HALF_PI));
        } else {
            float bob = 0.0F;
            if ("hop".equals(motion.mode()) && motion.walkSpeed() > 0.05F) {
                // a hopper bounds: the body leaves the ground in time with its legs
                bob = -Math.abs(Mth.sin(motion.walkPosition() * 0.3331F)) * 3.0F * Math.min(1.0F, motion.walkSpeed() * 1.5F);
            } else if ("crawl".equals(motion.mode())) {
                // something with no legs drags itself, rocking
                ms.mulPose(com.mojang.math.Axis.ZP.rotation(Mth.sin(motion.walkPosition() * 0.6F) * 0.12F * Math.min(1.0F, motion.walkSpeed() * 2.0F)));
            }
            ms.translate(-centreX / 16.0F, (layout.lift() + bob) / 16.0F, -centreZ / 16.0F);
        }
        for (int i = 0; i < layout.pieces().size(); i++) {
            MinionBody.Placement placement = layout.pieces().get(i);
            Look look = cached.looks().get(i);
            Matrix4f pose = animate(placement, look.slot(), motion, lying, cached.torsoZ());
            ms.pushPose();
            ms.scale(1 / 16.0F, 1 / 16.0F, 1 / 16.0F);
            ms.mulPose(pose);
            ms.scale(16.0F, 16.0F, 16.0F);
            drawPiece(placement, look, tint, ms, buffers, light);
            ms.popPose();
        }
        ms.popPose();
    }

    /** Where the torso's middle is, front to back: legs in front of it are front legs. */
    private static float torsoCentreZ(MinionBody.Layout layout) {
        for (MinionBody.Placement placement : layout.pieces()) {
            if (placement.socket() == null) {
                Vector3f lo = placement.bone().boxMin();
                Vector3f hi = placement.bone().boxMax();
                return placement.pose().transformPosition(new Vector3f((lo.x + hi.x) / 2, (lo.y + hi.y) / 2, (lo.z + hi.z) / 2)).z;
            }
        }
        return 0.0F;
    }

    /**
     * A piece's pose this frame: limbs swing about their socket as a walking animal's do (legs on one side
     * against the other, front against hind, arms against legs; a hopper's all together), a head turns to look.
     */
    private static Matrix4f animate(MinionBody.Placement placement, PartSlot slot, Motion motion, boolean lying, float centreZ) {
        if (placement.socket() == null || lying) {
            return placement.pose();
        }
        Vector3f pivot = placement.pose().getTranslation(new Vector3f());
        Matrix4f turn = new Matrix4f();
        if (slot == PartSlot.HEAD || slot == PartSlot.NECK) {
            turn.rotateY(Mth.clamp(motion.headYaw(), -60.0F, 60.0F) * Mth.DEG_TO_RAD).rotateX(Mth.clamp(motion.headPitch(), -40.0F, 40.0F) * Mth.DEG_TO_RAD);
        } else if (slot == PartSlot.LEG || slot == PartSlot.ARM) {
            boolean right = pivot.x < 0.0F;
            boolean front = pivot.z < centreZ;
            boolean arm = slot == PartSlot.ARM;
            float phase;
            float reach;
            if ("hop".equals(motion.mode())) {
                phase = front ? Mth.PI : 0.0F;
                reach = 1.1F;
            } else if (arm) {
                phase = right ? Mth.PI : 0.0F;
                reach = 1.0F;
            } else {
                phase = (right ^ front) ? 0.0F : Mth.PI;
                reach = 1.4F;
            }
            float swing = Mth.cos(motion.walkPosition() * 0.6662F + phase) * reach * Math.min(1.0F, motion.walkSpeed());
            turn.rotateX(swing);
        } else if (slot == PartSlot.TAIL) {
            turn.rotateY(Mth.sin(motion.age() * 0.15F) * 0.25F);
        } else {
            return placement.pose();
        }
        return new Matrix4f().translate(pivot).mul(turn).translate(-pivot.x, -pivot.y, -pivot.z).mul(placement.pose());
    }

    /** One piece in its own mob's look, gone off as far as it had when fitted, with its cut ends raw. */
    private static void drawPiece(MinionBody.Placement placement, Look look, int tint, PoseStack ms, MultiBufferSource buffers, int light) {
        PieceRef piece = placement.piece();
        int color = CarcassModels.rotColor(piece.freshness());
        if (tint != -1) {
            color = FastColor.ARGB32.multiply(color, tint);
        }
        CarcassModels.drawBone(placement.rig(), placement.bone(), piece.texture(), look.coats(), color, ms, buffers, light);
        CarcassModels.drawMaggots(placement.rig(), placement.bone(), piece.freshness(), ms, buffers, light);
        if (look.bloody()) {
            WoundCaps.draw(placement.rig(), placement.bone(), look.cuts(), color, ms, buffers, light);
        }
    }

    /** Worked out once per layout: a piece's coats, whether its mob bleeds, where it is cut raw, the slot it moves as. */
    private static Look look(MinionBody.Layout layout, MinionBody.Placement placement) {
        PieceRef piece = placement.piece();
        Rig rig = placement.rig();
        Bone bone = placement.bone();
        List<CarcassLook.Coat> coats = piece.coats().stream().map(c -> new CarcassLook.Coat(c.layer(), c.texture(), c.tint())).toList();
        boolean bloody = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getOptional(piece.entity())
                .map(type -> !type.is(com.avicagan.bloodandbones.registry.BBTags.BLOODLESS)).orElse(true);
        // it moves as the socket it is in: an arm stitched in for a leg walks
        PartSlot slot = layout.sockets().stream().filter(s -> s.id().equals(placement.socket())).findFirst()
                .map(MinionBody.Socket::slot).orElse(placement.slot().slot());
        List<String> cuts = new ArrayList<>();
        if (placement.socket() != null) {
            // a fitted piece: raw where it was cut from its own mob, and where anything that hung off it was
            bone.parent().ifPresent(parent -> cuts.add(parent + ">" + bone.name()));
            for (Bone other : rig.bones()) {
                if (other.parent().filter(bone.name()::equals).isPresent()) {
                    cuts.add(bone.name() + ">" + other.name());
                }
            }
        } else {
            // the torso: a raw stump at each of its own sockets left empty
            for (MinionBody.Socket socket : layout.sockets()) {
                boolean filled = layout.pieces().stream().anyMatch(p -> socket.id().equals(p.socket()));
                if (!filled && rig.bone(socket.id()).isPresent()) {
                    cuts.add(bone.name() + ">" + socket.id());
                }
            }
        }
        return new Look(coats, bloody, List.copyOf(cuts), slot);
    }
}
