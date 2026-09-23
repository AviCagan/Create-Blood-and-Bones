package com.avicagan.bloodandbones.registry;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.carcass.rig.Rig;
import com.avicagan.bloodandbones.carcass.rig.RigManager;
import com.avicagan.bloodandbones.item.CarcassPieceItem;
import com.avicagan.bloodandbones.machine.CarcassMachineBlockEntity;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.api.registry.CreateRegistries;
import com.simibubi.create.content.logistics.item.filter.attribute.ItemAttribute;
import com.simibubi.create.content.logistics.item.filter.attribute.ItemAttributeType;
import com.simibubi.create.content.logistics.item.filter.attribute.SingletonItemAttribute;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Predicate;

/**
 * Carcass pieces in Create's Attribute Filter, so funnels, belts and frogports can sort meat: which mob a
 * piece came from, which part it is, whether it is still fresh or rotting, skinned, or from a baby. The
 * wording follows Create's (its keys are {@code create.item_attributes.<mod>.<name>}, as other addons do).
 */
public final class BBItemAttributes {
    private static final DeferredRegister<ItemAttributeType> TYPES = DeferredRegister.create(CreateRegistries.ITEM_ATTRIBUTE_TYPE, BloodAndBones.MOD_ID);

    /** Fresh enough that butchering it gives everything (see CarcassButchery#dropYields). */
    public static final float FRESH = 0.6F;
    /** Rotting: its meat comes out as rotten flesh. */
    public static final float ROTTING = 0.3F;

    public static final Holder<ItemAttributeType> FRESH_PIECE = singleton("fresh_piece", "is fresh meat", "is not fresh meat",
            piece -> piece.freshness() >= FRESH);
    public static final Holder<ItemAttributeType> ROTTING_PIECE = singleton("rotting_piece", "is rotting", "is not rotting",
            piece -> piece.freshness() < ROTTING);
    public static final Holder<ItemAttributeType> SKINNED_PIECE = singleton("skinned_piece", "is skinned", "is not skinned",
            CarcassPieceItem.Piece::skinned);
    public static final Holder<ItemAttributeType> BABY_PIECE = singleton("baby_piece", "is from a baby", "is not from a baby",
            CarcassPieceItem.Piece::baby);
    public static final Holder<ItemAttributeType> PIECE_OF = TYPES.register("piece_of", PieceOf.Type::new);
    public static final Holder<ItemAttributeType> PIECE_PART = TYPES.register("piece_part", PiecePart.Type::new);

    static {
        lang("piece_of", "is a piece of %1$s", "is not a piece of %1$s");
        lang("piece_part", "is a carcass %1$s", "is not a carcass %1$s");
        for (String kind : PiecePart.KINDS) {
            BloodAndBones.REGISTRATE.addRawLang("bloodandbones.piece_kind." + kind, kind);
        }
    }

    private BBItemAttributes() {
    }

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
    }

    private static void lang(String name, String description, String inverted) {
        String key = "create.item_attributes." + BloodAndBones.MOD_ID + "." + name;
        BloodAndBones.REGISTRATE.addRawLang(key, description);
        BloodAndBones.REGISTRATE.addRawLang(key + ".inverted", inverted);
    }

    private static Holder<ItemAttributeType> singleton(String name, String description, String inverted, Predicate<CarcassPieceItem.Piece> test) {
        lang(name, description, inverted);
        return TYPES.register(name, () -> new SingletonItemAttribute.Type(type -> new SingletonItemAttribute(type, (stack, level) -> {
            CarcassPieceItem.Piece piece = CarcassPieceItem.piece(stack);
            return piece != null && test.test(piece);
        }, BloodAndBones.MOD_ID + "." + name)));
    }

    /** A piece of one kind of mob. */
    public record PieceOf(ResourceLocation entity) implements ItemAttribute {
        public static final MapCodec<PieceOf> CODEC = ResourceLocation.CODEC.xmap(PieceOf::new, PieceOf::entity).fieldOf("value");
        public static final StreamCodec<ByteBuf, PieceOf> STREAM_CODEC = ResourceLocation.STREAM_CODEC.map(PieceOf::new, PieceOf::entity);

        @Override
        public boolean appliesTo(ItemStack stack, Level level) {
            CarcassPieceItem.Piece piece = CarcassPieceItem.piece(stack);
            return piece != null && piece.entity().equals(entity);
        }

        @Override
        public ItemAttributeType getType() {
            return PIECE_OF.value();
        }

        @Override
        public String getTranslationKey() {
            return BloodAndBones.MOD_ID + ".piece_of";
        }

        @Override
        public Object[] getTranslationParameters() {
            return new Object[]{BuiltInRegistries.ENTITY_TYPE.getOptional(entity).map(EntityType::getDescription)
                    .orElse(Component.literal(entity.toString()))};
        }

        public static class Type implements ItemAttributeType {
            @Override
            public @NotNull ItemAttribute createAttribute() {
                return new PieceOf(ResourceLocation.withDefaultNamespace("cow"));
            }

            @Override
            public List<ItemAttribute> getAllAttributes(ItemStack stack, Level level) {
                CarcassPieceItem.Piece piece = CarcassPieceItem.piece(stack);
                return piece == null ? List.of() : List.of(new PieceOf(piece.entity()));
            }

            @Override
            public MapCodec<? extends ItemAttribute> codec() {
                return CODEC;
            }

            @Override
            public StreamCodec<? super RegistryFriendlyByteBuf, ? extends ItemAttribute> streamCodec() {
                return STREAM_CODEC;
            }
        }
    }

    /** Which part a piece is: the head, the body, a limb (legs, arms, wings, fins) or a tail. */
    public record PiecePart(String kind) implements ItemAttribute {
        public static final List<String> KINDS = List.of("head", "body", "limb", "tail");
        public static final MapCodec<PiecePart> CODEC = Codec.STRING.xmap(PiecePart::new, PiecePart::kind).fieldOf("value");
        public static final StreamCodec<ByteBuf, PiecePart> STREAM_CODEC = ByteBufCodecs.STRING_UTF8.map(PiecePart::new, PiecePart::kind);

        /** The body is the rig's root; heads and necks by name, as the Beheader tells them; tails by name. */
        public static String kindOf(CarcassPieceItem.Piece piece, Level level) {
            java.util.Optional<Rig> rig = level.isClientSide ? RigManager.clientRig(piece.entity(), piece.baby())
                    : RigManager.forEntity(piece.entity(), piece.baby());
            if (rig.map(r -> r.root().name().equals(piece.bone())).orElse(piece.bone().equals("body"))) {
                return "body";
            }
            if (CarcassMachineBlockEntity.isHead(piece.bone())) {
                return "head";
            }
            return piece.bone().substring(piece.bone().lastIndexOf('/') + 1).contains("tail") ? "tail" : "limb";
        }

        @Override
        public boolean appliesTo(ItemStack stack, Level level) {
            CarcassPieceItem.Piece piece = CarcassPieceItem.piece(stack);
            return piece != null && kindOf(piece, level).equals(kind);
        }

        @Override
        public ItemAttributeType getType() {
            return PIECE_PART.value();
        }

        @Override
        public String getTranslationKey() {
            return BloodAndBones.MOD_ID + ".piece_part";
        }

        @Override
        public Object[] getTranslationParameters() {
            return new Object[]{Component.translatable("bloodandbones.piece_kind." + kind)};
        }

        public static class Type implements ItemAttributeType {
            @Override
            public @NotNull ItemAttribute createAttribute() {
                return new PiecePart("body");
            }

            @Override
            public List<ItemAttribute> getAllAttributes(ItemStack stack, Level level) {
                CarcassPieceItem.Piece piece = CarcassPieceItem.piece(stack);
                return piece == null ? List.of() : List.of(new PiecePart(kindOf(piece, level)));
            }

            @Override
            public MapCodec<? extends ItemAttribute> codec() {
                return CODEC;
            }

            @Override
            public StreamCodec<? super RegistryFriendlyByteBuf, ? extends ItemAttribute> streamCodec() {
                return STREAM_CODEC;
            }
        }
    }
}
