package com.avicagan.bloodandbones.parts.effect;

import com.avicagan.bloodandbones.carcass.Blood;
import com.avicagan.bloodandbones.minion.MinionBuild;
import com.avicagan.bloodandbones.minion.MinionEntity;
import com.avicagan.bloodandbones.minion.PieceRef;
import com.avicagan.bloodandbones.parts.ActiveTraits;
import com.avicagan.bloodandbones.parts.TraitContext;
import com.avicagan.bloodandbones.parts.TraitEffect;
import com.avicagan.bloodandbones.parts.TraitEvents;
import com.avicagan.bloodandbones.parts.Trigger;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.enchantment.LevelBasedValue;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Making something (docs/PARTS-AND-TRAITS.md section 5.4, produce): {@code count} of an {@code item}, or a roll of a
 * {@code loot_table} (a cat's morning gift), each time a tick entry comes up. A flesh minion puts it in what it carries
 * (on the ground if it is full) and pays {@code cost_mb} of blood; a brass minion makes nothing (spec 6.6). A player gets
 * it in their pack (on the ground if it is full), paying from their tank. With {@code consumes} it needs one of that
 * (an empty bucket, a bowl, a glass bottle) to fill, and makes nothing without; such a container used on the minion by
 * hand is filled there and then, for the same blood. {@code coloured}: a white item comes in the colour the minion's sheep
 * had (wool).
 */
public record ProduceEffect(Optional<Item> item, Optional<ResourceKey<LootTable>> lootTable, LevelBasedValue count, Optional<Item> consumes,
                            int costMb, boolean coloured, Optional<Holder<SoundEvent>> sound) implements TraitEffect.Effect {
    public static final MapCodec<ProduceEffect> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BuiltInRegistries.ITEM.byNameCodec().optionalFieldOf("item").forGetter(ProduceEffect::item),
            ResourceKey.codec(Registries.LOOT_TABLE).optionalFieldOf("loot_table").forGetter(ProduceEffect::lootTable),
            LevelBasedValue.CODEC.optionalFieldOf("count", LevelBasedValue.constant(1.0F)).forGetter(ProduceEffect::count),
            BuiltInRegistries.ITEM.byNameCodec().optionalFieldOf("consumes").forGetter(ProduceEffect::consumes),
            Codec.INT.optionalFieldOf("cost_mb", 0).forGetter(ProduceEffect::costMb),
            Codec.BOOL.optionalFieldOf("coloured", false).forGetter(ProduceEffect::coloured),
            SoundEvent.CODEC.optionalFieldOf("sound").forGetter(ProduceEffect::sound)
    ).apply(i, ProduceEffect::new));

    @Override
    public MapCodec<? extends TraitEffect.Effect> codec() {
        return CODEC;
    }

    @Override
    public void run(TraitContext ctx) {
        LivingEntity host = ctx.host();
        if (ctx.trigger() == Trigger.TICK && host.tickCount < ctx.facet().interval()) {
            // the first go comes a whole interval after it wakes or loads, so reloading its chunk is no way to milk it faster
            return;
        }
        if (host instanceof MinionEntity minion) {
            if (minion.cybernetic() || !takeFrom(minion, false)) {
                return;
            }
            if (!minion.usePower(costMb)) {
                return;
            }
            takeFrom(minion, true);
            for (ItemStack made : make(ctx.level(), minion, ctx.levelled(count))) {
                ItemStack left = minion.carry(made);
                if (!left.isEmpty()) {
                    minion.spawnAtLocation(left);
                }
            }
            squelch(ctx.level(), minion);
        } else if (host instanceof Player player) {
            if (!takeFrom(player, false) || !ctx.pay(costMb)) {
                return;
            }
            takeFrom(player, true);
            for (ItemStack made : make(ctx.level(), player, ctx.levelled(count))) {
                player.getInventory().placeItemBackInInventory(made);
            }
            squelch(ctx.level(), player);
        }
    }

    /**
     * A container one of the minion's produce effects fills, used on it by hand (a bucket on a milk udder): filled there
     * and then, the minion paying the blood, or null if nothing of it takes this.
     */
    @Nullable
    static InteractionResult byHand(MinionEntity minion, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (minion.cybernetic() || minion.poweredDown()) {
            return null;
        }
        for (ActiveTraits.Found<ProduceEffect> found : ActiveTraits.of(minion).find(ProduceEffect.class)) {
            ProduceEffect produce = found.effect();
            if (produce.consumes().isEmpty() || !held.is(produce.consumes().get())) {
                continue;
            }
            if (minion.level() instanceof ServerLevel level) {
                if (!TraitEvents.holds(minion, found.entry(), found.facet(), null) || !minion.usePower(produce.costMb())) {
                    return InteractionResult.FAIL;
                }
                List<ItemStack> made = produce.make(level, minion, (int) produce.count().calculate(found.entry().level()));
                for (int n = 0; n < made.size(); n++) {
                    if (n == 0) {
                        player.setItemInHand(hand, ItemUtils.createFilledResult(held, player, made.get(0)));
                    } else {
                        player.getInventory().placeItemBackInInventory(made.get(n));
                    }
                }
                produce.squelch(level, minion);
            }
            return InteractionResult.sidedSuccess(minion.level().isClientSide);
        }
        return null;
    }

    /** What one go makes: the item (a sheep's colour, if coloured), or the loot table's roll. */
    private List<ItemStack> make(ServerLevel level, LivingEntity host, int howMany) {
        List<ItemStack> out = new ArrayList<>();
        if (item.isPresent() && howMany > 0) {
            Item made = coloured ? dyed(item.get(), host) : item.get();
            int left = howMany;
            while (left > 0) {
                int n = Math.min(left, made.getDefaultMaxStackSize());
                out.add(new ItemStack(made, n));
                left -= n;
            }
        }
        if (lootTable.isPresent()) {
            LootTable table = level.getServer().reloadableRegistries().getLootTable(lootTable.get());
            LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, host.position())
                    .withParameter(LootContextParams.THIS_ENTITY, host).create(LootContextParamSets.GIFT);
            for (int n = 0; n < Math.max(1, howMany); n++) {
                out.addAll(table.getRandomItems(params));
            }
        }
        return out;
    }

    /**
     * A white item ("white_wool") in the colour a minion's sheep had, kept from its carcass as "wool"; white on anything
     * else, and on a player (armour scraps keep no colour).
     */
    static Item dyed(Item white, LivingEntity host) {
        if (!(host instanceof MinionEntity minion) || minion.build().isEmpty()) {
            return white;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(white);
        if (!id.getPath().startsWith("white_")) {
            return white;
        }
        MinionBuild build = minion.build().get();
        List<PieceRef> pieces = new ArrayList<>();
        pieces.add(build.torso());
        build.parts().forEach(fitted -> pieces.add(fitted.piece()));
        for (PieceRef piece : pieces) {
            String wool = piece.traits().get("wool");
            if (wool != null) {
                DyeColor colour = DyeColor.byName(wool, DyeColor.WHITE);
                ResourceLocation dyed = id.withPath(colour.getName() + id.getPath().substring("white".length()));
                return BuiltInRegistries.ITEM.getOptional(dyed).orElse(white);
            }
        }
        return white;
    }

    /** Whether the host has what this fills (nothing needed: yes), and with {@code take}, uses one up. */
    private boolean takeFrom(LivingEntity host, boolean take) {
        if (consumes.isEmpty()) {
            return true;
        }
        Item want = consumes.get();
        if (host instanceof MinionEntity minion) {
            for (int i = 0; i < minion.slots(); i++) {
                ItemStack stack = minion.inventory.getItem(i);
                if (stack.is(want)) {
                    if (take) {
                        stack.shrink(1);
                        minion.inventory.setChanged();
                    }
                    return true;
                }
            }
        } else if (host instanceof Player player) {
            if (player.hasInfiniteMaterials()) {
                return true;
            }
            for (ItemStack stack : player.getInventory().items) {
                if (stack.is(want)) {
                    if (take) {
                        stack.shrink(1);
                    }
                    return true;
                }
            }
        }
        return false;
    }

    /** Out it comes: its own sound (an egg's plop, a squirt of ink), a wet squelch under it and a drop or two of blood. */
    private void squelch(ServerLevel level, LivingEntity host) {
        sound.ifPresent(s -> level.playSound(null, host.getX(), host.getY(), host.getZ(), s, host.getSoundSource(), 0.8F, 0.9F + level.random.nextFloat() * 0.2F));
        level.playSound(null, host.getX(), host.getY(), host.getZ(), SoundEvents.SLIME_SQUISH_SMALL, host.getSoundSource(), 0.6F, 0.5F + level.random.nextFloat() * 0.2F);
        if (Blood.bleeds(host)) {
            Blood.burst(level, new Vector3d(host.getX(), host.getY(0.4), host.getZ()), 2, Blood.soul(BuiltInRegistries.ENTITY_TYPE.getKey(host.getType())));
        }
    }
}
