package com.avicagan.bloodandbones.parts;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.body.SeveredLimbItem;
import com.avicagan.bloodandbones.carcass.rig.Rig;
import com.avicagan.bloodandbones.registry.BBDataComponents;
import com.avicagan.bloodandbones.registry.BBItems;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Organs as items (docs/PARTS-AND-TRAITS.md sections 4.9 and 7.1). Each organ the mob data names has a file saying what
 * item it comes out as: the special ones (a rumen, a powder sac, an olfactory bulb) as the Gland, carrying the organ's id,
 * the heart, lungs, stomach and eyes as their own items, and a few as vanilla items (a rabbit's foot, an ink sac). Every
 * one is stamped with the mob it came out of ({@code bloodandbones:source}), except a vanilla item that the
 * {@code organ_sources} data map already gives to that mob, which is left plain so it stacks with the ones mobs drop (a
 * spider eye is a spider's eye either way).
 * <p>
 * Where organs come from: each part of a mob lists the organs a piece of it holds ("torso": heart, lungs, stomach, and a
 * cow's rumen; "head": two eyes; a rabbit's "leg.hind": its foot), in order, layered as traits are (a plain list adds,
 * {"add", "remove", "replace"} edits). A body that is its own head (a blaze, a slime) holds its head's organs too.
 */
public final class Organs {
    /** Whose organ an unstamped item is: a rabbit's foot is a rabbit's, a glow ink sac a glow squid's glow sac. */
    public record OrganSource(ResourceLocation entity, ResourceLocation organ) {
        public static final Codec<OrganSource> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("entity").forGetter(OrganSource::entity),
                ResourceLocation.CODEC.fieldOf("organ").forGetter(OrganSource::organ)
        ).apply(i, OrganSource::new));
    }

    public static final DataMapType<Item, OrganSource> SOURCES = DataMapType.builder(BloodAndBones.asResource("organ_sources"), Registries.ITEM, OrganSource.CODEC)
            .synced(OrganSource.CODEC, false).build();

    private Organs() {
    }

    /** An organ's file; for an organ nobody wrote one for (a datapack's), a plain Gland of its name that fits a chestplate. */
    public static OrganKind kind(PartsData.Store store, ResourceLocation organ) {
        OrganKind kind = store.organ(organ);
        return kind != null ? kind : new OrganKind(BBItems.GLAND.get(), "organ." + organ.getNamespace() + "." + organ.getPath(), Optional.empty(),
                "gland", 0x9A4A4A, List.of("chestplate"), List.of());
    }

    /** The organ's name: its file's name, or in bloodless mode its bloodless one (a machine part: the Rumen is a Fermenter). */
    public static MutableComponent name(PartsData.Store store, ResourceLocation organ) {
        OrganKind kind = kind(store, organ);
        return Component.translatable(com.avicagan.bloodandbones.config.BBClientConfig.bloodless() && kind.bloodlessName().isPresent()
                ? kind.bloodlessName().get() : kind.name());
    }

    /**
     * The organ as it comes out of this mob: its file's item, stamped with the mob and the part it was in (a Gland also
     * with the organ's id), or a vanilla item left plain where the data map already says it is this mob's.
     */
    public static ItemStack stack(PartsData.Store store, ResourceLocation organ, ResourceLocation entity, boolean baby) {
        return stack(store, organ, entity, baby, Map.of());
    }

    /** A fitted organ as an item again, as it was when it went in (a charged creeper's sac still charged). */
    public static ItemStack stack(PartsData.Store store, CarcassArmour.Organ organ) {
        return stack(store, organ.organ(), organ.entity(), organ.baby(), organ.traits());
    }

    /**
     * The same, out of one particular mob: what its carcass kept ({@code traits}: a creeper's charge) goes with the organ
     * where its data's variants read it for this organ, and makes the item a stamped one.
     */
    public static ItemStack stack(PartsData.Store store, ResourceLocation organ, ResourceLocation entity, boolean baby, Map<String, String> traits) {
        // a vanilla item the data map gives to this very organ of this mob is what it is, whatever the organ's file says
        // (a spider's eye is a Spider Eye), so one fitted comes back as it went in
        Item item = mapped(entity, organ).orElse(kind(store, organ).item());
        Map<String, String> kept = traits.isEmpty() ? Map.of() : store.resolve(entity, baby).organTraitsKept(organ, traits);
        ItemStack stack;
        if (item instanceof SeveredLimbItem limb) {
            stack = limb.of(entity, baby);
            if (!organ.equals(store.organFor(item))) {
                // an organ of a datapack's that comes out as a heart or an eye: the item alone would read as the plain one
                stack.set(BBDataComponents.ORGAN.get(), organ);
            }
        } else {
            stack = new ItemStack(item);
            OrganSource mapped = item instanceof GlandItem ? null : stack.getItemHolder().getData(SOURCES);
            if (mapped == null || !mapped.entity().equals(entity) || !mapped.organ().equals(organ) || baby || !kept.isEmpty()) {
                stack.set(BBDataComponents.ORGAN.get(), organ);
                stack.set(BBDataComponents.SOURCE.get(), new Source(entity, partOf(store, entity, baby, organ), baby));
            }
        }
        if (!kept.isEmpty()) {
            stack.set(BBDataComponents.ORGAN_TRAITS.get(), kept);
        }
        return stack;
    }

    /** The item the organ_sources data map gives to this organ of this mob (a spider's eye: the Spider Eye), if any. */
    private static Optional<Item> mapped(ResourceLocation entity, ResourceLocation organ) {
        for (Map.Entry<net.minecraft.resources.ResourceKey<Item>, OrganSource> e : BuiltInRegistries.ITEM.getDataMap(SOURCES).entrySet()) {
            if (e.getValue().entity().equals(entity) && e.getValue().organ().equals(organ)) {
                return BuiltInRegistries.ITEM.getOptional(e.getKey());
            }
        }
        return Optional.empty();
    }

    /**
     * What an item is as an organ, or null if it is none: a stamped organ (a Gland, a heart cut out of a mob, a glow squid's
     * ink sac), or a vanilla item the data map gives to a mob. A player's own heart has no mob on it and is no organ here.
     */
    @Nullable
    public static CarcassArmour.Organ of(ItemStack stack, PartsData.Store store) {
        if (stack.isEmpty()) {
            return null;
        }
        Source source = stack.get(BBDataComponents.SOURCE.get());
        ResourceLocation organ = stack.get(BBDataComponents.ORGAN.get());
        Map<String, String> traits = stack.getOrDefault(BBDataComponents.ORGAN_TRAITS.get(), Map.of());
        if (organ != null) {
            return source == null ? null : new CarcassArmour.Organ(organ, source.entity(), source.baby(), traits);
        }
        if (stack.getItem() instanceof SeveredLimbItem) {
            ResourceLocation id = store.organFor(stack.getItem());
            return id == null || source == null ? null : new CarcassArmour.Organ(id, source.entity(), source.baby(), traits);
        }
        OrganSource mapped = stack.getItemHolder().getData(SOURCES);
        return mapped == null ? null : new CarcassArmour.Organ(mapped.organ(), mapped.entity(), false);
    }

    /** Whether a piece of carcass armour ("helmet", "chestplate"...) takes this organ: its file's "armour_pieces". */
    public static boolean fits(PartsData.Store store, ResourceLocation organ, String piece) {
        return kind(store, organ).armourPieces().contains(piece);
    }

    /**
     * The organs a piece of this mob holds, in the order a Cleaver takes them out: its slot's list ("leg"), then its form's
     * and sub-slot's ("leg.hind"); a body with no head of its own (a blaze, a slime) holds its head's organs as well. A
     * neck, a torso extension or decoration holds none unless the data lists some for it.
     */
    public static List<ResourceLocation> held(PartsData.Store store, ResourceLocation entity, boolean baby, String bone) {
        Optional<Rig> rig = store.rig(entity, baby).or(() -> store.rig(entity, false));
        if (rig.isEmpty() || rig.get().bone(bone).isEmpty()) {
            return List.of();
        }
        SlotInfo slot = PartSlots.of(store, entity, rig.get(), bone);
        ResolvedMob mob = store.resolve(entity, baby);
        String base = slot.slot().getSerializedName();
        List<ResourceLocation> out = new ArrayList<>(mob.organList(base));
        if (!slot.form().isEmpty()) {
            out.addAll(mob.organList(base + "." + slot.form()));
        }
        if (!slot.sub().isEmpty()) {
            out.addAll(mob.organList(base + "." + slot.sub()));
        }
        if (slot.slot() == PartSlot.TORSO && PartSlots.selfContained(store, entity, rig.get())) {
            out.addAll(mob.organList("head"));
        }
        return out;
    }

    /** The part of this mob an organ is cut out of ("torso", "head", "leg"...), by the first list naming it; torso if none does. */
    public static String partOf(PartsData.Store store, ResourceLocation entity, boolean baby, ResourceLocation organ) {
        for (Map.Entry<String, List<ResourceLocation>> e : store.resolve(entity, baby).organLists().entrySet()) {
            if (e.getValue().contains(organ)) {
                String key = e.getKey();
                String base = key.contains(".") ? key.substring(0, key.indexOf('.')) : key;
                try {
                    return PartSlot.byName(base).scrapPart();
                } catch (IllegalArgumentException notASlot) {
                    return base;
                }
            }
        }
        return "torso";
    }

    /** What comes out with the organ, rolled: a creeper's sac spills a gunpowder or two. */
    public static List<ItemStack> extras(OrganKind kind, RandomSource random) {
        List<ItemStack> out = new ArrayList<>();
        for (OrganKind.ExtraDrop drop : kind.extraDrops()) {
            int count = drop.max() <= drop.min() ? drop.min() : drop.min() + random.nextInt(drop.max() - drop.min() + 1);
            if (count > 0) {
                out.add(new ItemStack(drop.item(), count));
            }
        }
        return out;
    }

    /** Every rigged mob that holds this organ somewhere, by id; the first names the Gland shown in creative and JEI. */
    public static List<ResourceLocation> mobsWith(PartsData.Store store, ResourceLocation organ) {
        List<ResourceLocation> out = new ArrayList<>();
        for (ResourceLocation entity : rigged(store)) {
            if (store.resolve(entity, false).organLists().values().stream().anyMatch(list -> list.contains(organ))) {
                out.add(entity);
            }
        }
        return out;
    }

    /** Every mob with a rig on this side, by id (vanilla first, then by namespace). */
    public static List<ResourceLocation> rigged(PartsData.Store store) {
        return BuiltInRegistries.ENTITY_TYPE.keySet().stream().filter(id -> store.rig(id, false).isPresent())
                .sorted(java.util.Comparator.comparing((ResourceLocation id) -> !id.getNamespace().equals("minecraft")).thenComparing(ResourceLocation::toString))
                .toList();
    }

    /** The Gland (or other item) of an organ as the first mob holding it gives it; empty if no mob holds it. */
    public static ItemStack example(PartsData.Store store, ResourceLocation organ) {
        List<ResourceLocation> mobs = mobsWith(store, organ);
        return mobs.isEmpty() ? ItemStack.EMPTY : stack(store, organ, mobs.get(0), false);
    }

    // ---- words

    /**
     * An organ's lines on an item: whose it is, what it gives fitted into carcass armour (and which pieces take it), and
     * what it gives fitted into a minion, from that mob's {@code organ_traits}; what each trait does while Ctrl is held.
     */
    public static void describe(PartsData.Store store, CarcassArmour.Organ organ, List<Component> tooltip, TooltipFlag flag) {
        OrganKind kind = kind(store, organ.organ());
        ResolvedMob mob = store.resolve(organ.entity(), organ.baby());
        tooltip.add(Component.translatable("bloodandbones.organ.of", ScrapsItem.mobName(organ.entity())).withStyle(ChatFormatting.GRAY));
        MutableComponent pieces = Component.empty();
        for (int i = 0; i < kind.armourPieces().size(); i++) {
            pieces.append(i == 0 ? Component.empty() : Component.literal(", ")).append(Component.translatable("bloodandbones.piece." + kind.armourPieces().get(i)));
        }
        // with what its variant adds (a charged creeper's sac blasts harder)
        boolean described = line(store, tooltip, flag, Component.translatable("bloodandbones.organ.armour", pieces), mob.organArmour(organ.organ(), organ.traits()));
        described |= line(store, tooltip, flag, Component.translatable("bloodandbones.organ.minion"), mob.organMinion(organ.organ(), organ.traits()));
        if (described && !flag.hasControlDown()) {
            tooltip.add(Component.translatable("bloodandbones.carcass_armour.hold_ctrl").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    /** "In armour (Chestplate): Blast" and, with Ctrl, what each does. Whether any has words for what it does. */
    private static boolean line(PartsData.Store store, List<Component> tooltip, TooltipFlag flag, MutableComponent label, List<TraitList.Resolved> traits) {
        MutableComponent list = Component.empty();
        for (int i = 0; i < traits.size(); i++) {
            list.append(i == 0 ? Component.empty() : Component.literal(", ")).append(Traits.describe(store, traits.get(i)));
        }
        tooltip.add(label.withStyle(ChatFormatting.GRAY).append(traits.isEmpty()
                ? Component.translatable("bloodandbones.organ.nothing").withStyle(ChatFormatting.DARK_GRAY) : list.withStyle(ChatFormatting.DARK_AQUA)));
        boolean described = false;
        for (TraitList.Resolved trait : traits) {
            String description = Traits.descriptionKey(store, trait);
            if (description != null) {
                described = true;
                if (flag.hasControlDown()) {
                    tooltip.add(Component.literal("  ").append(Traits.describe(store, trait)).append(": ").append(Component.translatable(description))
                            .withStyle(ChatFormatting.GRAY));
                }
            }
        }
        return described;
    }

    /**
     * Organ lines on every organ item (the Gland, a heart cut out of a cow, a plain rabbit's foot), and on a carcass piece
     * the organs still in it, so a butcher knows what the Surgical Rig will take out.
     */
    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        PartsData.Store store = CarcassArmourItem.store();
        CarcassArmour.Organ organ = of(stack, store);
        if (organ != null) {
            describe(store, organ, event.getToolTip(), event.getFlags());
            return;
        }
        com.avicagan.bloodandbones.item.CarcassPieceItem.Piece piece = com.avicagan.bloodandbones.item.CarcassPieceItem.piece(stack);
        if (piece != null) {
            List<ResourceLocation> held = held(store, piece.entity(), piece.baby(), piece.bone());
            int taken = com.avicagan.bloodandbones.body.Surgery.organsTaken(piece);
            if (taken < held.size()) {
                MutableComponent names = Component.empty();
                for (int i = taken; i < held.size(); i++) {
                    names.append(i == taken ? Component.empty() : Component.literal(", ")).append(name(store, held.get(i)));
                }
                event.getToolTip().add(Component.translatable("bloodandbones.organ.inside", names).withStyle(ChatFormatting.GRAY));
            }
        }
    }
}
