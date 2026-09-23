package com.avicagan.bloodandbones.item;

import com.avicagan.bloodandbones.carcass.CarcassAssembler;
import com.avicagan.bloodandbones.carcass.CarcassLook;
import com.avicagan.bloodandbones.carcass.CarcassSavedData;
import com.avicagan.bloodandbones.carcass.rig.Bone;
import com.avicagan.bloodandbones.carcass.rig.Rig;
import com.avicagan.bloodandbones.carcass.rig.RigManager;
import com.avicagan.bloodandbones.registry.BBDataComponents;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/** A light piece of carcass carried in the hand: a head, a leg, a whole chicken. Put it down to get the body back. */
public class CarcassPieceItem extends Item {
    /** One coat, as saved on an item. */
    public record Coat(String layer, ResourceLocation texture, int tint) {
        public static final Codec<Coat> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("layer").forGetter(Coat::layer),
                ResourceLocation.CODEC.fieldOf("texture").forGetter(Coat::texture),
                Codec.INT.fieldOf("tint").forGetter(Coat::tint)
        ).apply(i, Coat::new));
    }

    /**
     * A carried piece. It keeps its blood and how long it has been rotten, so putting it down and picking it
     * up again neither refills it nor resets its rot (pieces saved before these were kept come back
     * bloodless).
     */
    public record Piece(ResourceLocation entity, String bone, ResourceLocation texture, List<Coat> coats, float freshness,
                        boolean skinned, java.util.Map<String, String> traits, float blood, float bloodMax, float decay, boolean baby) {
        public static final Codec<Piece> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("entity").forGetter(Piece::entity),
                Codec.STRING.fieldOf("bone").forGetter(Piece::bone),
                ResourceLocation.CODEC.fieldOf("texture").forGetter(Piece::texture),
                Coat.CODEC.listOf().optionalFieldOf("coats", List.of()).forGetter(Piece::coats),
                Codec.FLOAT.optionalFieldOf("freshness", 1.0F).forGetter(Piece::freshness),
                Codec.BOOL.optionalFieldOf("skinned", false).forGetter(Piece::skinned),
                Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("traits", java.util.Map.of()).forGetter(Piece::traits),
                Codec.FLOAT.optionalFieldOf("blood", 0.0F).forGetter(Piece::blood),
                Codec.FLOAT.optionalFieldOf("blood_max", 0.0F).forGetter(Piece::bloodMax),
                Codec.FLOAT.optionalFieldOf("decay", 0.0F).forGetter(Piece::decay),
                Codec.BOOL.optionalFieldOf("baby", false).forGetter(Piece::baby)
        ).apply(i, Piece::new));

        public CarcassLook look() {
            return new CarcassLook(texture, coats.stream().map(c -> new CarcassLook.Coat(c.layer(), c.texture(), c.tint())).toList());
        }
    }

    public CarcassPieceItem(Properties properties) {
        super(properties);
    }

    public static ItemStack of(CarcassSavedData.Carcass carcass, String bone) {
        ItemStack stack = new ItemStack(com.avicagan.bloodandbones.registry.BBItems.CARCASS_PIECE.get());
        List<Coat> coats = carcass.look.passes().stream().map(c -> new Coat(c.layer(), c.texture(), c.tint())).toList();
        com.avicagan.bloodandbones.carcass.CarcassBleeding.ensureBlood(carcass);
        stack.set(BBDataComponents.PIECE.get(), new Piece(carcass.entity, bone, carcass.look.texture(), coats, carcass.freshness,
                carcass.skinned, java.util.Map.copyOf(carcass.traits), Math.max(0.0F, carcass.blood), Math.max(0.0F, carcass.bloodMax), carcass.decay, carcass.baby));
        return stack;
    }

    @Nullable
    public static Piece piece(ItemStack stack) {
        return stack.get(BBDataComponents.PIECE.get());
    }

    @Override
    public Component getName(ItemStack stack) {
        Piece piece = piece(stack);
        if (piece == null) {
            return super.getName(stack);
        }
        Optional<EntityType<?>> type = BuiltInRegistries.ENTITY_TYPE.getOptional(piece.entity());
        Component animal = type.map(EntityType::getDescription).orElse(Component.literal(piece.entity().getPath()));
        String part = piece.bone().substring(piece.bone().lastIndexOf('/') + 1).replace('_', ' ');
        return Component.translatable("item.bloodandbones.carcass_piece.named", animal, part);
    }

    /** How it is keeping: fresh, going off or rotting (the butchery thresholds), skinned, from a baby. */
    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, java.util.List<Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
        Piece piece = piece(stack);
        if (piece == null) {
            return;
        }
        int percent = Math.round(Math.max(0.0F, Math.min(1.0F, piece.freshness())) * 100.0F);
        String state = piece.freshness() >= com.avicagan.bloodandbones.registry.BBItemAttributes.FRESH ? "fresh"
                : piece.freshness() >= com.avicagan.bloodandbones.registry.BBItemAttributes.ROTTING ? "going_off" : "rotting";
        net.minecraft.ChatFormatting colour = switch (state) {
            case "fresh" -> net.minecraft.ChatFormatting.GREEN;
            case "going_off" -> net.minecraft.ChatFormatting.GOLD;
            default -> net.minecraft.ChatFormatting.DARK_RED;
        };
        tooltip.add(Component.translatable("item.bloodandbones.carcass_piece." + state, percent).withStyle(colour));
        if (piece.skinned()) {
            tooltip.add(Component.translatable("item.bloodandbones.carcass_piece.skinned").withStyle(net.minecraft.ChatFormatting.GRAY));
        }
        if (piece.baby()) {
            tooltip.add(Component.translatable("item.bloodandbones.carcass_piece.baby").withStyle(net.minecraft.ChatFormatting.GRAY));
        }
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Piece piece = piece(context.getItemInHand());
        if (piece == null || context.getPlayer() == null) {
            return InteractionResult.PASS;
        }
        if (!(context.getLevel() instanceof ServerLevel level)) {
            // the server puts it down; the client must not go on to use the other hand
            return InteractionResult.SUCCESS;
        }
        Rig rig = RigManager.forEntity(piece.entity(), piece.baby()).orElse(null);
        Bone bone = rig == null ? null : rig.bone(piece.bone()).orElse(null);
        if (bone == null) {
            return InteractionResult.FAIL;
        }
        Vec3 at = context.getClickLocation();
        // clicked on another body (a carcass, a ship): Sable gives the spot in that body's plot; put it down
        // where that spot is in the world
        dev.ryanhcode.sable.sublevel.SubLevel clicked = dev.ryanhcode.sable.Sable.HELPER.getContaining(level, context.getClickedPos());
        if (clicked != null) {
            org.joml.Vector3d world = clicked.logicalPose().transformPosition(new org.joml.Vector3d(at.x, at.y, at.z));
            at = new Vec3(world.x, world.y, world.z);
        }
        CarcassSavedData.Carcass carcass = CarcassAssembler.assemblePiece(level, rig, bone, piece.look(), piece.freshness(), at, context.getPlayer().getYRot());
        if (carcass == null) {
            return InteractionResult.FAIL;
        }
        carcass.skinned = piece.skinned();
        carcass.traits.putAll(piece.traits());
        carcass.blood = piece.blood();
        carcass.bloodMax = piece.bloodMax();
        carcass.decay = piece.decay();
        carcass.baby = piece.baby();
        // the cells were just made: tell clients its cut ends at once
        com.avicagan.bloodandbones.carcass.CarcassRot.sync(level, carcass, null);
        if (!context.getPlayer().getAbilities().instabuild) {
            context.getItemInHand().shrink(1);
        }
        return InteractionResult.CONSUME;
    }
}
