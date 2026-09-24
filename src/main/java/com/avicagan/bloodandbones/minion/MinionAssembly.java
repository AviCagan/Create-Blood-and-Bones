package com.avicagan.bloodandbones.minion;

import com.avicagan.bloodandbones.body.SurgeryTableBlockEntity;
import com.avicagan.bloodandbones.carcass.CarcassButchery;
import com.avicagan.bloodandbones.carcass.CarcassJoints;
import com.avicagan.bloodandbones.carcass.CarcassSavedData;
import com.avicagan.bloodandbones.carcass.rig.Rig;
import com.avicagan.bloodandbones.carcass.rig.RigManager;
import com.avicagan.bloodandbones.item.CarcassPieceItem;
import com.avicagan.bloodandbones.parts.PartSlot;
import com.avicagan.bloodandbones.parts.PartSlots;
import com.avicagan.bloodandbones.parts.PartsData;
import com.avicagan.bloodandbones.parts.SlotInfo;
import com.avicagan.bloodandbones.registry.BBFluids;
import com.avicagan.bloodandbones.registry.BBItems;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Building a minion on a Surgery Table with the Assembly Frame (docs/PARTS-AND-TRAITS.md section 6.2). The frame is
 * a torso: a carried one laid on the table, or a carcass lying over it claimed with an empty hand (a heavy body
 * can never be carried), whatever is still jointed to it coming along as parts already fitted. Pieces go into
 * the free socket they suit; a Cleaver takes back the last; a bucket of blood wakes it.
 */
public final class MinionAssembly {
    /** How far past the table's edges, and how high above it, a carcass counts as lying on it (the machines' rule). */
    public static final double SPREAD = 0.75;
    public static final double REACH = 2.5;
    /** The least freshness a torso may have to be woken. */
    public static final float FRESH_ENOUGH = 0.3F;

    private MinionAssembly() {
    }

    /** A carried piece that can be a frame: a torso, or a body whole in itself (a blaze, a slime). */
    public static boolean isFrame(ItemStack stack, net.minecraft.world.level.Level level) {
        CarcassPieceItem.Piece piece = CarcassPieceItem.piece(stack);
        if (piece == null) {
            return false;
        }
        PartsData.Store store = PartsData.of(level);
        return store.rig(piece.entity(), piece.baby()).map(rig -> PartSlots.of(store, piece.entity(), rig, piece.bone()).slot() == PartSlot.TORSO).orElse(false);
    }

    /** Lay a carried torso on the frame. */
    public static boolean layDown(SurgeryTableBlockEntity table, ItemStack stack, net.minecraft.world.level.Level level) {
        CarcassPieceItem.Piece piece = CarcassPieceItem.piece(stack);
        if (table.build().isPresent() || piece == null || !isFrame(stack, level)) {
            return false;
        }
        table.setBuild(MinionBuild.frame(PieceRef.of(piece)));
        return true;
    }

    /**
     * Brass Sheathing over the frame: a brass frame (a skinned torso, skinned pieces) can then be woken with soul blood;
     * a frame of hideless pieces only (a skeleton's bones) can go either way, and becomes brass.
     *
     * @return whether it went on
     */
    public static boolean sheathe(SurgeryTableBlockEntity table) {
        Optional<MinionBuild> maybe = table.build();
        if (maybe.isEmpty() || maybe.get().sheathed()) {
            return false;
        }
        MinionBuild build = maybe.get();
        boolean hideless = hideless(build.torso().entity()) && build.parts().stream().allMatch(f -> hideless(f.piece().entity()));
        if (!build.cybernetic() && !hideless) {
            return false;
        }
        table.setBuild(build.sheathe());
        return true;
    }

    /**
     * Claim a carcass lying on the table: its torso becomes the frame, and every bone still jointed to it a part
     * fitted in its own socket. The carcass leaves the world.
     *
     * @return whether one was claimed
     */
    public static boolean claim(ServerLevel level, SurgeryTableBlockEntity table) {
        if (table.build().isPresent()) {
            return false;
        }
        CarcassSavedData.Carcass carcass = nearestOn(level, table.getBlockPos());
        if (carcass == null) {
            return false;
        }
        PieceRef torso = ref(carcass, carcass.rootBone);
        MinionBuild build = MinionBuild.frame(torso);
        for (CarcassJoints.Spec joint : carcass.joints) {
            if (joint.parent().equals(carcass.rootBone) && CarcassButchery.isAttached(carcass, joint.child())) {
                build = build.with(joint.child(), ref(carcass, joint.child()));
            }
        }
        remove(level, carcass);
        table.setBuild(build);
        level.playSound(null, table.getBlockPos(), SoundEvents.SLIME_BLOCK_PLACE, SoundSource.BLOCKS, 1.0F, 0.6F);
        return true;
    }

    /** A carcass whose torso lies over the table, nearest first. */
    @Nullable
    static CarcassSavedData.Carcass nearestOn(ServerLevel level, BlockPos table) {
        AABB zone = new AABB(table.getX() - SPREAD, table.getY() + 0.5, table.getZ() - SPREAD, table.getX() + 1.0 + SPREAD, table.getY() + 1.0 + REACH,
                table.getZ() + 1.0 + SPREAD);
        CarcassSavedData.Carcass best = null;
        double bestDistance = Double.MAX_VALUE;
        for (CarcassSavedData.Carcass carcass : CarcassSavedData.get(level).all()) {
            Vector3d at = com.avicagan.bloodandbones.carcass.CarcassAssembler.boneWorldPosition(level, carcass, carcass.rootBone);
            if (at == null || !zone.contains(at.x, at.y, at.z) || com.avicagan.bloodandbones.carcass.CarcassDrag.isDraggingCarcass(carcass.id)) {
                continue;
            }
            double d = at.distanceSquared(table.getX() + 0.5, table.getY() + 1.0, table.getZ() + 0.5);
            if (d < bestDistance) {
                bestDistance = d;
                best = carcass;
            }
        }
        return best;
    }

    static PieceRef ref(CarcassSavedData.Carcass carcass, String bone) {
        List<CarcassPieceItem.Coat> coats = carcass.look.passes().stream().map(c -> new CarcassPieceItem.Coat(c.layer(), c.texture(), c.tint())).toList();
        return new PieceRef(carcass.entity, bone, carcass.look.texture(), coats, carcass.freshness, carcass.skinned, Map.copyOf(carcass.traits), carcass.baby);
    }

    /** Take the carcass out of the world: its bodies, and its record. */
    private static void remove(ServerLevel level, CarcassSavedData.Carcass carcass) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container != null) {
            for (UUID id : List.copyOf(carcass.bones.values())) {
                if (container.getSubLevel(id) instanceof ServerSubLevel body && !body.isRemoved()) {
                    container.removeSubLevel(body, dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason.REMOVED);
                }
            }
        }
        CarcassSavedData.get(level).forget(carcass);
    }

    /**
     * Put a carried piece into the frame: a head into the head socket, an arm or leg into the free limb socket it
     * suits best (arms high, legs low), a tail behind. An organic frame takes only pieces with their hide on (a
     * mob with no hide at all counts either way); a brass one only skinned pieces.
     *
     * @return what is wrong, or null if it went in
     */
    @Nullable
    public static Component fit(ServerLevel level, SurgeryTableBlockEntity table, ItemStack held) {
        Optional<MinionBuild> maybe = table.build();
        CarcassPieceItem.Piece piece = CarcassPieceItem.piece(held);
        if (maybe.isEmpty() || piece == null) {
            return Component.translatable("bloodandbones.minion.lay_body");
        }
        MinionBuild build = maybe.get();
        PartsData.Store store = PartsData.SERVER;
        Optional<Rig> rig = store.rig(piece.entity(), piece.baby());
        if (rig.isEmpty()) {
            return Component.translatable("bloodandbones.minion.no_fit");
        }
        if (!hideless(piece.entity()) && piece.skinned() != build.cybernetic()) {
            return Component.translatable(build.cybernetic() ? "bloodandbones.minion.needs_skinned" : "bloodandbones.minion.needs_hide");
        }
        SlotInfo slot = PartSlots.of(store, piece.entity(), rig.get(), piece.bone());
        MinionBody.Socket socket = freeSocket(store, build, slot.slot());
        if (socket == null) {
            return Component.translatable("bloodandbones.minion.no_socket");
        }
        MinionBuild next = build.with(socket.id(), PieceRef.of(piece));
        MinionStats stats = MinionStats.of(store, next);
        if (stats.width() >= 3.0F || stats.height() >= 4.0F) {
            return Component.translatable("bloodandbones.minion.too_big");
        }
        table.setBuild(next);
        level.playSound(null, table.getBlockPos(), SoundEvents.SLIME_BLOCK_PLACE, SoundSource.BLOCKS, 1.0F, 0.9F);
        com.avicagan.bloodandbones.carcass.Blood.burst(level, new Vector3d(table.getBlockPos().getX() + 0.5, table.getBlockPos().getY() + 1.1,
                table.getBlockPos().getZ() + 0.5), 4, false);
        return null;
    }

    /** The free socket for a piece of this slot: legs take the lowest, arms the highest, heads the head's. */
    @Nullable
    static MinionBody.Socket freeSocket(PartsData.Store store, MinionBuild build, PartSlot slot) {
        List<MinionBody.Socket> free = MinionBody.sockets(store, build.torso()).stream()
                .filter(s -> s.takes(slot) && build.in(s.id()).isEmpty()).toList();
        if (free.isEmpty()) {
            return null;
        }
        java.util.Comparator<MinionBody.Socket> byHeight = java.util.Comparator.comparingDouble(s -> s.pivot().y);
        // y grows downward in model space: legs want the largest y, arms the smallest; a limb goes to its own kind of socket first
        java.util.Comparator<MinionBody.Socket> order = slot == PartSlot.LEG ? byHeight.reversed() : byHeight;
        return free.stream().sorted(java.util.Comparator.<MinionBody.Socket>comparingInt(s -> s.slot() == slot ? 0 : 1).thenComparing(order)).findFirst().orElse(null);
    }

    /** A mob with no hide to take off (a skeleton, a spider) counts as skinned and unskinned alike. */
    static boolean hideless(net.minecraft.resources.ResourceLocation entity) {
        return com.avicagan.bloodandbones.carcass.butchery.ButcheryManager.forEntity(entity)
                .map(table -> table.hide().isEmpty() && table.parts().values().stream().flatMap(List::stream).noneMatch(y -> "hide".equals(y.kind()))).orElse(true);
    }

    /** A Cleaver takes the last piece back off (at the freshness it went in with), then the torso. */
    public static boolean takeBack(ServerLevel level, SurgeryTableBlockEntity table, Player player) {
        Optional<MinionBuild> maybe = table.build();
        if (maybe.isEmpty()) {
            return false;
        }
        MinionBuild build = maybe.get();
        ItemStack out;
        if (!build.parts().isEmpty()) {
            out = pieceItem(build.parts().get(build.parts().size() - 1).piece(), 1.0F);
            table.setBuild(build.withoutLast());
        } else {
            // the torso alone: a light one comes back as it was, a heavy one only as meat
            out = pieceItem(build.torso(), 1.0F);
            table.clearBuild();
            if (!light(build.torso())) {
                for (ItemStack meat : CarcassButchery.pieceYields(level, build.torso().toPiece())) {
                    player.getInventory().placeItemBackInInventory(meat);
                }
                out = ItemStack.EMPTY;
            }
        }
        if (!out.isEmpty()) {
            player.getInventory().placeItemBackInInventory(out);
        }
        level.playSound(null, table.getBlockPos(), com.avicagan.bloodandbones.registry.BBSounds.CARCASS_CUT.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
        return true;
    }

    /** Whether a piece is light enough to be carried. */
    static boolean light(PieceRef piece) {
        return RigManager.forEntity(piece.entity(), piece.baby()).flatMap(r -> r.bone(piece.bone()))
                .map(b -> MinionStats.volume(b) <= CarcassButchery.LIGHT_MASS).orElse(true);
    }

    /** A piece as a carried item, gone off by this much. */
    public static ItemStack pieceItem(PieceRef piece, float freshness) {
        ItemStack stack = new ItemStack(BBItems.CARCASS_PIECE.get());
        CarcassPieceItem.Piece p = piece.toPiece();
        stack.set(com.avicagan.bloodandbones.registry.BBDataComponents.PIECE.get(), new CarcassPieceItem.Piece(p.entity(), p.bone(), p.texture(), p.coats(),
                p.freshness() * freshness, p.skinned(), p.traits(), 0.0F, 0.0F, 0.0F, p.baby()));
        return stack;
    }

    /**
     * Wake it with a bucket of blood: it gets up off the table as a minion of whoever woke it, with that blood in it.
     *
     * @return the minion, or null (no frame, a rotten torso, over the cap)
     */
    @Nullable
    public static MinionEntity wake(ServerLevel level, Player maker, SurgeryTableBlockEntity table, ItemStack bucket) {
        Optional<MinionBuild> build = table.build();
        if (build.isEmpty()) {
            return null;
        }
        // flesh wakes on a bucket of blood; brass, sheathed, on a soul canister
        boolean brass = build.get().cybernetic();
        if (brass ? !bucket.is(BBItems.SOUL_CANISTER.get()) : !bucket.is(BBFluids.BLOOD.getBucket().get())) {
            return null;
        }
        if (brass && !build.get().sheathed()) {
            maker.displayClientMessage(Component.translatable("bloodandbones.minion.needs_sheathing").withStyle(ChatFormatting.RED), true);
            return null;
        }
        if (build.get().torso().freshness() < FRESH_ENOUGH) {
            maker.displayClientMessage(Component.translatable("bloodandbones.minion.too_rotten").withStyle(ChatFormatting.RED), true);
            return null;
        }
        if (!MinionCensus.mayMake(level.getServer(), maker.getUUID())) {
            maker.displayClientMessage(Component.translatable("bloodandbones.minion.cap").withStyle(ChatFormatting.RED), true);
            return null;
        }
        BlockPos pos = table.getBlockPos();
        MinionEntity minion = com.avicagan.bloodandbones.registry.BBEntities.MINION.get().create(level);
        if (minion == null) {
            return null;
        }
        minion.moveTo(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, maker.getYRot() + 180.0F, 0.0F);
        minion.setup(maker, pos, build.get(), brass ? MinionStats.CANISTER : 1000.0F);
        table.clearBuild();
        level.addFreshEntity(minion);
        MinionCensus.count(level.getServer(), maker.getUUID(), minion.getUUID());
        if (maker instanceof net.minecraft.server.level.ServerPlayer server) {
            net.minecraft.advancements.CriteriaTriggers.SUMMONED_ENTITY.trigger(server, minion);
        }
        if (!maker.hasInfiniteMaterials()) {
            bucket.shrink(1);
            maker.getInventory().placeItemBackInInventory(new ItemStack(brass ? BBItems.EMPTY_SOUL_CANISTER.get() : Items.BUCKET));
        }
        level.playSound(null, pos, SoundEvents.ZOMBIE_VILLAGER_CURE, SoundSource.BLOCKS, 0.8F, 0.6F);
        com.avicagan.bloodandbones.carcass.Blood.burst(level, new Vector3d(pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5), 16, false);
        return minion;
    }

    /** A line on what is built so far: its health, speed and what it would do. */
    public static Component status(PartsData.Store store, MinionBuild build) {
        MinionStats stats = MinionStats.of(store, build);
        MutableComponent line = Component.translatable("bloodandbones.minion.frame_stats", Math.round(stats.health()), String.format("%.2f", stats.speed()),
                Component.translatable(MinionEntity.jobKey(stats.jobs().get(0))), build.parts().size(), MinionBody.sockets(store, build.torso()).size());
        return line;
    }
}
