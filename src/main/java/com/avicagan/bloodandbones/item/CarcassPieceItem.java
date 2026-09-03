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

    public record Piece(ResourceLocation entity, String bone, ResourceLocation texture, List<Coat> coats, float freshness) {
        public static final Codec<Piece> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("entity").forGetter(Piece::entity),
                Codec.STRING.fieldOf("bone").forGetter(Piece::bone),
                ResourceLocation.CODEC.fieldOf("texture").forGetter(Piece::texture),
                Coat.CODEC.listOf().optionalFieldOf("coats", List.of()).forGetter(Piece::coats),
                Codec.FLOAT.optionalFieldOf("freshness", 1.0F).forGetter(Piece::freshness)
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
        stack.set(BBDataComponents.PIECE.get(), new Piece(carcass.entity, bone, carcass.look.texture(), coats, carcass.freshness));
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

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Piece piece = piece(context.getItemInHand());
        if (piece == null || !(context.getLevel() instanceof ServerLevel level) || context.getPlayer() == null) {
            return InteractionResult.PASS;
        }
        Rig rig = RigManager.forEntity(piece.entity()).orElse(null);
        Bone bone = rig == null ? null : rig.bone(piece.bone()).orElse(null);
        if (bone == null) {
            return InteractionResult.FAIL;
        }
        Vec3 at = context.getClickLocation();
        CarcassSavedData.Carcass carcass = CarcassAssembler.assemblePiece(level, rig, bone, piece.look(), piece.freshness(), at, context.getPlayer().getYRot());
        if (carcass == null) {
            return InteractionResult.FAIL;
        }
        if (!context.getPlayer().getAbilities().instabuild) {
            context.getItemInHand().shrink(1);
        }
        return InteractionResult.CONSUME;
    }
}
