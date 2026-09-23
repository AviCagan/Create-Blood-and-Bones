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
 * and on what lies on the table: a blade takes a limb of flesh off, an implant or a severed limb goes where
 * one is missing, and an implant comes out with nothing needed. Nothing can go wrong.
 */
public final class Surgery {
    public enum Action {
        NONE, TAKE_OFF, FIT, REATTACH, UNCLIP;

        public String translationKey() {
            return "bloodandbones.surgery.action." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    private Surgery() {
    }

    public static boolean isBlade(ItemStack stack) {
        return stack.getItem() instanceof CleaverItem;
    }

    /** What the table takes to lie on it: a blade, an implant or a severed limb. */
    public static boolean accepts(ItemStack stack) {
        return isBlade(stack) || stack.getItem() instanceof ImplantItem || stack.getItem() instanceof SeveredLimbItem;
    }

    /** What would be done to this part, with this on the table. The same on both sides, for the screen. */
    public static Action action(Body body, ItemStack tool, BodyPart part) {
        return switch (body.state(part)) {
            case IMPLANT -> Action.UNCLIP;
            case NATURAL -> isBlade(tool) ? Action.TAKE_OFF : Action.NONE;
            case MISSING -> tool.getItem() instanceof ImplantItem implant && implant.fits(part) ? Action.FIT
                    : tool.getItem() instanceof SeveredLimbItem limb && limb.fits(part) ? Action.REATTACH : Action.NONE;
        };
    }

    /**
     * Do it: the patient lies on the table.
     *
     * @return what was done ({@link Action#NONE} for nothing)
     */
    public static Action operate(ServerLevel level, Player patient, SurgeryTableBlockEntity table, BodyPart part) {
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
                SeveredLimbItem severed = part.kind() == BodyPart.Kind.ARM ? com.avicagan.bloodandbones.registry.BBItems.SEVERED_ARM.get()
                        : com.avicagan.bloodandbones.registry.BBItems.SEVERED_LEG.get();
                give(patient, severed.of(patient), pos);
                com.avicagan.bloodandbones.carcass.Blood.burst(level, at, 12);
                com.avicagan.bloodandbones.carcass.Blood.stain(level, at, 3);
                level.playSound(null, pos, com.avicagan.bloodandbones.registry.BBSounds.CARCASS_CUT.get(), SoundSource.PLAYERS, 1.0F, 0.9F);
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
                give(patient, body.unclip(part), pos);
                level.playSound(null, pos, SoundEvents.ARMOR_EQUIP_CHAIN.value(), SoundSource.PLAYERS, 1.0F, 1.2F);
            }
        }
        patient.setData(BBAttachments.BODY, body);
        BodyEffects.changed(patient);
        return action;
    }

    private static void give(Player player, ItemStack stack, BlockPos pos) {
        if (!stack.isEmpty() && !player.getInventory().add(stack)) {
            net.minecraft.world.level.block.Block.popResource(player.level(), pos.above(), stack);
        }
    }

    /** The server tells a player lying on a table to show the surgery screen. */
    public record OpenPayload(BlockPos pos) implements CustomPacketPayload {
        public static final Type<OpenPayload> TYPE = new Type<>(BloodAndBones.asResource("surgery_open"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenPayload> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, OpenPayload::pos, OpenPayload::new);

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

    /** From the screen: only for a player lying on that table. */
    public static void handle(ServerPlayer player, ActionPayload payload) {
        if (!lyingOn(player, payload.pos()) || !(player.level().getBlockEntity(payload.pos()) instanceof SurgeryTableBlockEntity table)) {
            return;
        }
        if (operate(player.serverLevel(), player, table, payload.part()) == Action.NONE) {
            player.displayClientMessage(Component.translatable("bloodandbones.surgery.nothing"), true);
        }
    }
}
