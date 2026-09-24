package com.avicagan.bloodandbones.body;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.item.CleaverItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;
import org.joml.Vector3d;

/**
 * Operations at the Surgery Table on whoever lies on it. What can be done to a part depends on the part
 * and on what lies on the table: a blade takes a part of flesh out (a limb, an eye, an organ); an implant
 * that fits takes a part of flesh's place in one go, or goes where one is missing; a part of flesh goes back
 * where one is missing; and an implant comes out with nothing needed. Nothing can go wrong.
 */
public final class Surgery {
    public enum Action {
        NONE, TAKE_OFF, REPLACE, SWAP, FIT, REATTACH, UNCLIP;

        public String translationKey() {
            return "bloodandbones.surgery.action." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    private Surgery() {
    }

    public static boolean isBlade(ItemStack stack) {
        return stack.getItem() instanceof CleaverItem;
    }

    /** What the table takes to lie on it: a blade, an implant, a part, or a carcass piece to take organs from. */
    public static boolean accepts(ItemStack stack) {
        return isBlade(stack) || stack.getItem() instanceof ImplantItem || stack.getItem() instanceof SeveredLimbItem
                || stack.is(com.avicagan.bloodandbones.registry.BBItems.CARCASS_PIECE.get());
    }

    /** What would be done to this part, with this on the table. The same on both sides, for the screen. */
    public static Action action(Body body, ItemStack tool, BodyPart part) {
        boolean fits = tool.getItem() instanceof ImplantItem implant && implant.fits(part)
                || tool.getItem() instanceof SeveredLimbItem limb && limb.fits(part);
        return switch (body.state(part)) {
            // an implant is swapped for what fits on the table, or unclipped; a heart is never left empty
            case IMPLANT -> fits ? Action.SWAP : part == BodyPart.HEART ? Action.NONE : Action.UNCLIP;
            // flesh comes out with a blade (not the heart: that is only ever replaced), or is swapped straight out
            case NATURAL -> fits ? Action.REPLACE : isBlade(tool) && part != BodyPart.HEART ? Action.TAKE_OFF : Action.NONE;
            case MISSING -> tool.getItem() instanceof ImplantItem implant && implant.fits(part) ? Action.FIT
                    : tool.getItem() instanceof SeveredLimbItem limb && limb.fits(part) ? Action.REATTACH : Action.NONE;
        };
    }

    /** On yourself. */
    public static Action operate(ServerLevel level, Player patient, SurgeryTableBlockEntity table, BodyPart part) {
        return operate(level, patient, patient, table, part);
    }

    /**
     * Do it: the patient lies on the table, and the surgeon (the patient, or someone else) works on them.
     * What comes out goes to the surgeon.
     *
     * @return what was done ({@link Action#NONE} for nothing)
     */
    public static Action operate(ServerLevel level, net.minecraft.world.entity.LivingEntity patient, @org.jetbrains.annotations.Nullable Player surgeon,
                                 SurgeryTableBlockEntity table, BodyPart part) {
        Body body = BodyEffects.body(patient);
        Action action = action(body, table.item(), part);
        BlockPos pos = table.getBlockPos();
        Vector3d at = new Vector3d(pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5);
        switch (action) {
            case NONE -> {
                return action;
            }
            case TAKE_OFF -> {
                body.lose(part);
                ItemStack blade = table.item();
                com.avicagan.bloodandbones.carcass.Blood.bloody(blade, level);
                table.setChanged();
                table.sendData();
                cutOut(level, patient, surgeon, part, pos, at);
            }
            case REPLACE -> {
                cutOut(level, patient, surgeon, part, pos, at);
                put(body, part, table.take());
                level.playSound(null, pos, SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.PLAYERS, 1.0F, 0.8F);
            }
            case SWAP -> {
                give(surgeon, body.unclip(part), pos, level);
                put(body, part, table.take());
                level.playSound(null, pos, SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.PLAYERS, 1.0F, 0.8F);
            }
            case FIT -> {
                body.fit(part, table.take());
                level.playSound(null, pos, SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.PLAYERS, 1.0F, 0.8F);
            }
            case REATTACH -> {
                table.take();
                body.restore(part);
                com.avicagan.bloodandbones.carcass.Blood.burst(level, at, 4);
                level.playSound(null, pos, SoundEvents.SLIME_BLOCK_PLACE, SoundSource.PLAYERS, 1.0F, 0.8F);
            }
            case UNCLIP -> {
                give(surgeon, body.unclip(part), pos, level);
                level.playSound(null, pos, SoundEvents.ARMOR_EQUIP_CHAIN.value(), SoundSource.PLAYERS, 1.0F, 1.2F);
            }
        }
        patient.setData(BBAttachments.BODY, body);
        BodyEffects.changed(patient);
        return action;
    }

    /** What came off the table goes in: an implant, or a part of flesh (flesh again). */
    private static void put(Body body, BodyPart part, ItemStack item) {
        if (item.getItem() instanceof ImplantItem) {
            body.fit(part, item);
        } else {
            body.restore(part);
        }
    }

    /** The part of flesh comes out, bloodily, into the surgeon's hands. */
    private static void cutOut(ServerLevel level, net.minecraft.world.entity.LivingEntity patient, @org.jetbrains.annotations.Nullable Player surgeon,
                               BodyPart part, BlockPos pos, Vector3d at) {
        give(surgeon, com.avicagan.bloodandbones.registry.BBItems.partItem(part.kind()).of(patient), pos, level);
        com.avicagan.bloodandbones.carcass.Blood.burst(level, at, 12);
        com.avicagan.bloodandbones.carcass.Blood.stain(level, at, 3);
        level.playSound(null, pos, com.avicagan.bloodandbones.registry.BBSounds.CARCASS_CUT.get(), SoundSource.PLAYERS, 1.0F, 0.9F);
    }

    private static void give(@org.jetbrains.annotations.Nullable Player player, ItemStack stack, BlockPos pos, net.minecraft.world.level.Level level) {
        if (!stack.isEmpty() && (player == null || !player.getInventory().add(stack))) {
            net.minecraft.world.level.block.Block.popResource(level, pos.above(), stack);
        }
    }

    /** Whoever lies on the table there, if anyone. */
    @org.jetbrains.annotations.Nullable
    public static net.minecraft.world.entity.LivingEntity patientAt(net.minecraft.world.level.Level level, BlockPos table) {
        for (SurgerySeatEntity seat : level.getEntitiesOfClass(SurgerySeatEntity.class, new net.minecraft.world.phys.AABB(table))) {
            for (net.minecraft.world.entity.Entity rider : seat.getPassengers()) {
                if (rider instanceof net.minecraft.world.entity.LivingEntity living) {
                    return living;
                }
            }
        }
        return null;
    }

    /** How far from the table a surgeon may work on someone else. */
    public static final double REACH = 6.0;

    /** Whether this surgeon may work on whoever lies on this table: it is them, or they stand by it. */
    public static boolean mayOperate(Player surgeon, net.minecraft.world.entity.LivingEntity patient, BlockPos table) {
        return surgeon == patient ? lyingOn(surgeon, table)
                : patient.getVehicle() instanceof SurgerySeatEntity seat && seat.blockPosition().equals(table)
                && surgeon.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(table)) <= REACH * REACH && surgeon.isAlive();
    }

    /**
     * A Cleaver on a carcass piece lying on the table takes out its organs, one a cut: a body's heart, lungs
     * and stomach, a head's eyes, each named for the animal. A mob with no blood has none.
     *
     * @return whether an organ came out
     */
    public static boolean harvest(ServerLevel level, Player surgeon, SurgeryTableBlockEntity table, ItemStack blade) {
        ItemStack stack = table.item();
        com.avicagan.bloodandbones.item.CarcassPieceItem.Piece piece = com.avicagan.bloodandbones.item.CarcassPieceItem.piece(stack);
        if (piece == null) {
            return false;
        }
        var type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getOptional(piece.entity());
        if (type.isEmpty() || type.get().is(com.avicagan.bloodandbones.registry.BBTags.BLOODLESS)) {
            surgeon.displayClientMessage(Component.translatable("bloodandbones.surgery.no_organs"), true);
            return false;
        }
        String kind = com.avicagan.bloodandbones.registry.BBItemAttributes.PiecePart.kindOf(piece, level);
        java.util.List<BodyPart.Kind> organs = switch (kind) {
            case "body" -> java.util.List.of(BodyPart.Kind.HEART, BodyPart.Kind.LUNGS, BodyPart.Kind.STOMACH);
            case "head" -> java.util.List.of(BodyPart.Kind.EYE, BodyPart.Kind.EYE);
            default -> java.util.List.of();
        };
        int taken = organsTaken(piece);
        if (taken >= organs.size()) {
            surgeon.displayClientMessage(Component.translatable("bloodandbones.surgery.no_organs"), true);
            return false;
        }
        java.util.Map<String, String> traits = new java.util.HashMap<>(piece.traits());
        traits.put(ORGANS_TAKEN, Integer.toString(taken + 1));
        stack.set(com.avicagan.bloodandbones.registry.BBDataComponents.PIECE.get(), new com.avicagan.bloodandbones.item.CarcassPieceItem.Piece(
                piece.entity(), piece.bone(), piece.texture(), piece.coats(), piece.freshness(), piece.skinned(), java.util.Map.copyOf(traits),
                piece.blood(), piece.bloodMax(), piece.decay(), piece.baby()));
        table.notifyUpdate();
        BlockPos pos = table.getBlockPos();
        // a Deployer's stand-in would hold it and stall: it drops on the table, as the Butcher's Table's cuts do
        give(surgeon instanceof net.neoforged.neoforge.common.util.FakePlayer ? null : surgeon,
                com.avicagan.bloodandbones.registry.BBItems.partItem(organs.get(taken)).of(type.get().getDescription()), pos, level);
        com.avicagan.bloodandbones.carcass.Blood.bloody(blade, level);
        Vector3d at = new Vector3d(pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5);
        com.avicagan.bloodandbones.carcass.Blood.burst(level, at, 8, com.avicagan.bloodandbones.carcass.Blood.soul(piece.entity()));
        level.playSound(null, pos, com.avicagan.bloodandbones.registry.BBSounds.CARCASS_CUT.get(), SoundSource.PLAYERS, 1.0F, 1.1F);
        return true;
    }

    /** The trait on a carcass piece counting the organs already taken from it. */
    public static final String ORGANS_TAKEN = "organs_taken";

    public static int organsTaken(com.avicagan.bloodandbones.item.CarcassPieceItem.Piece piece) {
        try {
            return Integer.parseInt(piece.traits().getOrDefault(ORGANS_TAKEN, "0"));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** The server tells a surgeon to show the surgery screen for whoever lies on the table (themselves, or another). */
    public record OpenPayload(BlockPos pos, int patient) implements CustomPacketPayload {
        public static final Type<OpenPayload> TYPE = new Type<>(BloodAndBones.asResource("surgery_open"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenPayload> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, OpenPayload::pos, net.minecraft.network.codec.ByteBufCodecs.VAR_INT, OpenPayload::patient, OpenPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** A button on the surgery screen: do what the table allows to this part. */
    public record ActionPayload(BlockPos pos, BodyPart part) implements CustomPacketPayload {
        public static final Type<ActionPayload> TYPE = new Type<>(BloodAndBones.asResource("surgery_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ActionPayload> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, ActionPayload::pos, NeoForgeStreamCodecs.enumCodec(BodyPart.class), ActionPayload::part, ActionPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Whether this player is lying on the table there. */
    public static boolean lyingOn(Player player, BlockPos table) {
        return player.getVehicle() instanceof SurgerySeatEntity seat && seat.blockPosition().equals(table);
    }

    /** From the screen: for whoever lies on that table, by them or by someone standing by it. */
    public static void handle(ServerPlayer player, ActionPayload payload) {
        if (!player.level().isLoaded(payload.pos()) || !(player.level().getBlockEntity(payload.pos()) instanceof SurgeryTableBlockEntity table)) {
            return;
        }
        net.minecraft.world.entity.LivingEntity patient = patientAt(player.level(), payload.pos());
        if (patient == null || !mayOperate(player, patient, payload.pos())
                || SurgeryTableBlock.attachment(player.level(), payload.pos()) != TableAttachment.SURGICAL) {
            return;
        }
        if (operate(player.serverLevel(), patient, player, table, payload.part()) == Action.NONE) {
            player.displayClientMessage(Component.translatable("bloodandbones.surgery.nothing"), true);
        }
    }
}
