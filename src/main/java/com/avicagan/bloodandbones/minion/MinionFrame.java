package com.avicagan.bloodandbones.minion;

import com.avicagan.bloodandbones.body.Body;
import com.avicagan.bloodandbones.body.BodyPart;
import com.avicagan.bloodandbones.body.ImplantItem;
import com.avicagan.bloodandbones.body.SeveredLimbItem;
import com.avicagan.bloodandbones.body.Surgery;
import com.avicagan.bloodandbones.body.SurgeryTableBlockEntity;
import com.avicagan.bloodandbones.item.CarcassPieceItem;
import com.avicagan.bloodandbones.registry.BBDataComponents;
import com.avicagan.bloodandbones.registry.BBItemAttributes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * A minion being built on the Surgery Table: a carcass body laid on it is the frame. It has the organs that
 * were not taken out of it and nothing else. A carcass head goes on it (with the eyes that were not taken
 * out of the head); severed limbs, organs and implants go where a part is missing, one a click. A bucket of
 * soul blood wakes it once it has a head and a heart.
 */
public final class MinionFrame {
    /** What is built so far: the body, and whose head it has. */
    public record Frame(Body body, Optional<ResourceLocation> head) {
        public static final Codec<Frame> CODEC = RecordCodecBuilder.create(i -> i.group(
                Body.CODEC.fieldOf("body").forGetter(Frame::body),
                ResourceLocation.CODEC.optionalFieldOf("head").forGetter(Frame::head)
        ).apply(i, Frame::new));
    }

    private MinionFrame() {
    }

    /** Whether this is a carcass body, which can be a frame. */
    public static boolean isBody(ItemStack stack, net.minecraft.world.level.Level level) {
        CarcassPieceItem.Piece piece = CarcassPieceItem.piece(stack);
        return piece != null && BBItemAttributes.PiecePart.kindOf(piece, level).equals("body");
    }

    /** The frame a carcass body is, started fresh if nothing has been built on it yet. */
    public static Frame frame(ItemStack stack) {
        Frame frame = stack.get(BBDataComponents.FRAME.get());
        if (frame != null) {
            return new Frame(frame.body().copy(), frame.head());
        }
        Body body = new Body();
        for (BodyPart part : List.of(BodyPart.LEFT_ARM, BodyPart.RIGHT_ARM, BodyPart.LEFT_LEG, BodyPart.RIGHT_LEG, BodyPart.LEFT_EYE, BodyPart.RIGHT_EYE)) {
            body.lose(part);
        }
        CarcassPieceItem.Piece piece = CarcassPieceItem.piece(stack);
        int taken = piece == null ? 0 : Surgery.organsTaken(piece);
        List<BodyPart> organs = List.of(BodyPart.HEART, BodyPart.LUNGS, BodyPart.STOMACH);
        for (int i = 0; i < Math.min(taken, organs.size()); i++) {
            body.lose(organs.get(i));
        }
        return new Frame(body, Optional.empty());
    }

    private static void save(ItemStack stack, Frame frame, SurgeryTableBlockEntity table) {
        stack.set(BBDataComponents.FRAME.get(), frame);
        table.notifyUpdate();
    }

    /**
     * Put what the builder holds on the frame: a carcass head, or a part or implant where one of its kind is
     * missing. The held item is used up.
     *
     * @return whether it went on
     */
    public static boolean fit(ServerLevel level, Player builder, SurgeryTableBlockEntity table, ItemStack held) {
        ItemStack stack = table.item();
        if (!isBody(stack, level)) {
            return false;
        }
        Frame frame = frame(stack);
        BlockPos pos = table.getBlockPos();
        CarcassPieceItem.Piece piece = CarcassPieceItem.piece(held);
        if (piece != null) {
            if (frame.head().isPresent() || !BBItemAttributes.PiecePart.kindOf(piece, level).equals("head")) {
                return false;
            }
            Body body = frame.body();
            int eyesTaken = Surgery.organsTaken(piece);
            if (eyesTaken < 1) {
                body.restore(BodyPart.LEFT_EYE);
            }
            if (eyesTaken < 2) {
                body.restore(BodyPart.RIGHT_EYE);
            }
            save(stack, new Frame(body, Optional.of(piece.entity())), table);
            held.consume(1, builder);
            level.playSound(null, pos, SoundEvents.SLIME_BLOCK_PLACE, SoundSource.BLOCKS, 1.0F, 0.7F);
            return true;
        }
        for (BodyPart part : BodyPart.values()) {
            if (frame.body().state(part) != Body.State.MISSING || (part.kind() == BodyPart.Kind.EYE && frame.head().isEmpty())) {
                continue;
            }
            if (held.getItem() instanceof ImplantItem implant && implant.fits(part)) {
                frame.body().fit(part, held.copyWithCount(1));
            } else if (held.getItem() instanceof SeveredLimbItem limb && limb.fits(part)) {
                frame.body().restore(part);
            } else {
                continue;
            }
            save(stack, frame, table);
            held.consume(1, builder);
            level.playSound(null, pos, SoundEvents.SLIME_BLOCK_PLACE, SoundSource.BLOCKS, 1.0F, 0.9F);
            return true;
        }
        return false;
    }

    /** What is still needed before it can be woken, or empty if nothing. */
    public static List<Component> missing(Frame frame) {
        java.util.List<Component> out = new java.util.ArrayList<>();
        if (frame.head().isEmpty()) {
            out.add(Component.translatable("bloodandbones.minion.needs.head"));
        }
        if (frame.body().state(BodyPart.HEART) == Body.State.MISSING) {
            out.add(Component.translatable("bloodandbones.minion.needs.heart"));
        }
        return out;
    }

    /** A line on what is built so far and what it would do. */
    public static Component status(Frame frame) {
        MutableComponent line = Component.translatable("bloodandbones.minion.frame", Component.translatable(MinionJob.of(frame.body(), null).translationKey()));
        List<Component> missing = missing(frame);
        if (!missing.isEmpty()) {
            MutableComponent needs = Component.empty();
            for (int i = 0; i < missing.size(); i++) {
                needs.append(i == 0 ? Component.empty() : Component.literal(", ")).append(missing.get(i));
            }
            line.append(Component.literal(" ")).append(Component.translatable("bloodandbones.minion.needs", needs).withStyle(ChatFormatting.RED));
        } else {
            line.append(Component.literal(" ")).append(Component.translatable("bloodandbones.minion.ready").withStyle(ChatFormatting.GREEN));
        }
        return line;
    }

    /**
     * Wake it with a bucket of soul blood: it gets up off the table as a minion of whoever woke it.
     *
     * @return the minion, or null if it is not ready
     */
    @Nullable
    public static MinionEntity wake(ServerLevel level, Player maker, SurgeryTableBlockEntity table, ItemStack bucket) {
        ItemStack stack = table.item();
        if (!isBody(stack, level) || !bucket.is(com.avicagan.bloodandbones.registry.BBFluids.SOUL_BLOOD.getBucket().get())) {
            return null;
        }
        Frame frame = frame(stack);
        if (!missing(frame).isEmpty()) {
            maker.displayClientMessage(status(frame), true);
            return null;
        }
        BlockPos pos = table.getBlockPos();
        MinionEntity minion = com.avicagan.bloodandbones.registry.BBEntities.MINION.get().create(level);
        if (minion == null) {
            return null;
        }
        minion.moveTo(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, maker.getYRot() + 180.0F, 0.0F);
        minion.setup(maker, pos, frame);
        table.take();
        level.addFreshEntity(minion);
        com.avicagan.bloodandbones.body.BodyEffects.changed(minion);
        if (maker instanceof net.minecraft.server.level.ServerPlayer server) {
            net.minecraft.advancements.CriteriaTriggers.SUMMONED_ENTITY.trigger(server, minion);
        }
        if (!maker.hasInfiniteMaterials()) {
            bucket.shrink(1);
            maker.getInventory().placeItemBackInInventory(new ItemStack(Items.BUCKET));
        }
        level.playSound(null, pos, SoundEvents.ZOMBIE_VILLAGER_CURE, SoundSource.BLOCKS, 0.8F, 0.6F);
        com.avicagan.bloodandbones.carcass.Blood.burst(level, new org.joml.Vector3d(pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5), 16, true);
        return minion;
    }
}
