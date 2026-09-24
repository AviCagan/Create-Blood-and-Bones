package com.avicagan.bloodandbones.parts;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.backtank.FluidBacktankItem;
import com.avicagan.bloodandbones.minion.MinionEntity;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Optional;
import java.util.Set;

/**
 * Our four conditions for a trait's {@code requirements} (docs/PARTS-AND-TRAITS.md section 5.3), registered as vanilla
 * loot conditions, so they mix with vanilla's ({@code inverted}, {@code all_of}, {@code any_of}) and datapacks can use
 * them anywhere a loot condition goes. Each reads the host from the context's "this" entity.
 * <ul>
 *     <li>{@code bloodandbones:health_below} {fraction}: its health is under this share of its most</li>
 *     <li>{@code bloodandbones:power_below} {fraction}: a minion's blood or soul blood, or a player's worn tank, is under
 *     this share of full (no tank counts as empty)</li>
 *     <li>{@code bloodandbones:near} {entities or blocks, radius, count}: at least this many of these within the radius;
 *     each an id or a "#tag" ("#minecraft:zombies", "minecraft:cat"); blocks look at most 8 out</li>
 *     <li>{@code bloodandbones:dry_for} {seconds, at_most}: it has been out of water and rain this long (at_most: no
 *     longer than this)</li>
 * </ul>
 */
public final class TraitConditions {
    private static final DeferredRegister<LootItemConditionType> TYPES = DeferredRegister.create(Registries.LOOT_CONDITION_TYPE, BloodAndBones.MOD_ID);

    public static final DeferredHolder<LootItemConditionType, LootItemConditionType> HEALTH_BELOW = TYPES.register("health_below",
            () -> new LootItemConditionType(HealthBelow.CODEC));
    public static final DeferredHolder<LootItemConditionType, LootItemConditionType> POWER_BELOW = TYPES.register("power_below",
            () -> new LootItemConditionType(PowerBelow.CODEC));
    public static final DeferredHolder<LootItemConditionType, LootItemConditionType> NEAR = TYPES.register("near",
            () -> new LootItemConditionType(Near.CODEC));
    public static final DeferredHolder<LootItemConditionType, LootItemConditionType> DRY_FOR = TYPES.register("dry_for",
            () -> new LootItemConditionType(DryFor.CODEC));

    /** Blocks are counted this far out at most, whatever the radius: every block in the box is looked at. */
    public static final int MAX_BLOCK_RADIUS = 8;
    private static final Set<LootContextParam<?>> HOST = Set.of(LootContextParams.THIS_ENTITY);

    private TraitConditions() {
    }

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
    }

    /** Its health is under this share of its most (0.3: below 30%). */
    public record HealthBelow(float fraction) implements LootItemCondition {
        public static final MapCodec<HealthBelow> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.FLOAT.fieldOf("fraction").forGetter(HealthBelow::fraction)
        ).apply(i, HealthBelow::new));

        @Override
        public LootItemConditionType getType() {
            return HEALTH_BELOW.get();
        }

        @Override
        public Set<LootContextParam<?>> getReferencedContextParams() {
            return HOST;
        }

        @Override
        public boolean test(LootContext context) {
            return context.getParamOrNull(LootContextParams.THIS_ENTITY) instanceof LivingEntity host
                    && host.getHealth() < host.getMaxHealth() * fraction;
        }
    }

    /** A minion's blood (or soul blood), or a player's worn tank, is under this share of full. */
    public record PowerBelow(float fraction) implements LootItemCondition {
        public static final MapCodec<PowerBelow> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.FLOAT.fieldOf("fraction").forGetter(PowerBelow::fraction)
        ).apply(i, PowerBelow::new));

        @Override
        public LootItemConditionType getType() {
            return POWER_BELOW.get();
        }

        @Override
        public Set<LootContextParam<?>> getReferencedContextParams() {
            return HOST;
        }

        @Override
        public boolean test(LootContext context) {
            return context.getParamOrNull(LootContextParams.THIS_ENTITY) instanceof LivingEntity host && power(host) < fraction;
        }

        /** How full it is, 0 to 1: a minion's reservoir, or the tank worn (none is empty). */
        public static float power(LivingEntity host) {
            if (host instanceof MinionEntity minion) {
                return minion.powerShare();
            }
            ItemStack tank = FluidBacktankItem.wornBy(host);
            int capacity = FluidBacktankItem.capacity(tank);
            return capacity <= 0 ? 0.0F : FluidBacktankItem.fluid(tank).getAmount() / (float) capacity;
        }
    }

    /** An entity type or block by id, or a tag of them by "#id". */
    public record IdOrTag<T>(ResourceKey<? extends Registry<T>> registry, ResourceLocation id, boolean tag) {
        public static <T> Codec<IdOrTag<T>> codec(ResourceKey<? extends Registry<T>> registry) {
            return Codec.STRING.comapFlatMap(text -> {
                boolean tag = text.startsWith("#");
                return ResourceLocation.read(tag ? text.substring(1) : text).map(id -> new IdOrTag<>(registry, id, tag));
            }, v -> (v.tag ? "#" : "") + v.id);
        }

        public boolean matches(Holder<T> holder) {
            return tag ? holder.is(TagKey.create(registry, id)) : holder.is(id);
        }
    }

    /** At least {@code count} of these entities, or blocks, within {@code radius} of the host (itself not counted). */
    public record Near(Optional<IdOrTag<EntityType<?>>> entities, Optional<IdOrTag<Block>> blocks, float radius, int count) implements LootItemCondition {
        public static final MapCodec<Near> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                IdOrTag.<EntityType<?>>codec(Registries.ENTITY_TYPE).optionalFieldOf("entities").forGetter(Near::entities),
                IdOrTag.codec(Registries.BLOCK).optionalFieldOf("blocks").forGetter(Near::blocks),
                Codec.FLOAT.optionalFieldOf("radius", 8.0F).forGetter(Near::radius),
                Codec.INT.optionalFieldOf("count", 1).forGetter(Near::count)
        ).apply(i, Near::new));

        @Override
        public LootItemConditionType getType() {
            return NEAR.get();
        }

        @Override
        public Set<LootContextParam<?>> getReferencedContextParams() {
            return HOST;
        }

        @Override
        @SuppressWarnings("deprecation")
        public boolean test(LootContext context) {
            if (!(context.getParamOrNull(LootContextParams.THIS_ENTITY) instanceof Entity host)) {
                return false;
            }
            Level level = host.level();
            Vec3 at = host.position();
            if (entities.isPresent()) {
                IdOrTag<EntityType<?>> want = entities.get();
                int found = level.getEntities(host, host.getBoundingBox().inflate(radius),
                        e -> e.isAlive() && e.distanceToSqr(at) <= radius * radius && want.matches(e.getType().builtInRegistryHolder())).size();
                if (found < count) {
                    return false;
                }
            }
            if (blocks.isPresent()) {
                IdOrTag<Block> want = blocks.get();
                int reach = Math.min(MAX_BLOCK_RADIUS, (int) Math.ceil(radius));
                BlockPos centre = host.blockPosition();
                int found = 0;
                for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-reach, -reach, -reach), centre.offset(reach, reach, reach))) {
                    if (pos.distSqr(centre) <= radius * radius && want.matches(level.getBlockState(pos).getBlockHolder()) && ++found >= count) {
                        break;
                    }
                }
                return found >= count;
            }
            return entities.isPresent();
        }
    }

    /**
     * It has been out of water, rain and bubble columns for at least {@code seconds} (or, {@code at_most}, for no longer
     * than that), counted by the trait tick since it was last wet ({@link ActiveTraits#drySeconds}).
     */
    public record DryFor(int seconds, boolean atMost) implements LootItemCondition {
        public static final MapCodec<DryFor> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.INT.fieldOf("seconds").forGetter(DryFor::seconds),
                Codec.BOOL.optionalFieldOf("at_most", false).forGetter(DryFor::atMost)
        ).apply(i, DryFor::new));

        @Override
        public LootItemConditionType getType() {
            return DRY_FOR.get();
        }

        @Override
        public Set<LootContextParam<?>> getReferencedContextParams() {
            return HOST;
        }

        @Override
        public boolean test(LootContext context) {
            if (!(context.getParamOrNull(LootContextParams.THIS_ENTITY) instanceof LivingEntity host)) {
                return false;
            }
            float dry = ActiveTraits.drySeconds(host);
            return atMost ? dry <= seconds : dry >= seconds;
        }
    }
}
