package com.avicagan.bloodandbones.parts;

import com.avicagan.bloodandbones.BloodAndBones;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/**
 * The traits working on one creature now (docs/PARTS-AND-TRAITS.md section 5.1): from the carcass armour it
 * wears (and, later, a minion's fitted parts). The same trait from several pieces counts once at its highest
 * level, unless it sums; a full set of one mob adds that mob's bonus and drawback. Built when equipment
 * changes or data reloads, never every tick.
 */
public final class ActiveTraits {
    /** One working trait. {@code fromSet}: it came with a full set, which alone may make damage vanish entirely. */
    public record Entry(ResourceLocation id, Trait trait, int level, boolean fromSet) {
    }

    private static final Map<LivingEntity, ActiveTraits> CACHE = new WeakHashMap<>();
    public static final ActiveTraits NONE = new ActiveTraits(List.of(), -1, Optional.empty(), null);
    private static final EquipmentSlot[] ARMOUR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private final List<Entry> entries;
    private final int generation;
    private final Optional<ResolvedMob.FullSet> set;
    /** The mob of the full set worn, if one is. */
    @Nullable
    private final ResourceLocation setMob;
    /** The attribute modifiers this put on the creature, to take off again on rebuild. */
    final Map<ResourceLocation, Holder<Attribute>> applied = new HashMap<>();

    private ActiveTraits(List<Entry> entries, int generation, Optional<ResolvedMob.FullSet> set, @Nullable ResourceLocation setMob) {
        this.entries = entries;
        this.generation = generation;
        this.set = set;
        this.setMob = setMob;
    }

    public List<Entry> entries() {
        return entries;
    }

    public Optional<ResolvedMob.FullSet> set() {
        return set;
    }

    @Nullable
    public ResourceLocation setMob() {
        return setMob;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /** The level of a trait working here, 0 if none. */
    public int level(ResourceLocation trait) {
        for (Entry e : entries) {
            if (e.id().equals(trait)) {
                return e.level();
            }
        }
        return 0;
    }

    /** The creature's traits, rebuilt if its data changed since. Never null; empty for most creatures. */
    public static ActiveTraits of(LivingEntity host) {
        ActiveTraits cached = CACHE.get(host);
        PartsData.Store store = PartsData.of(host.level());
        if (cached == null || cached.generation != store.generation()) {
            cached = rebuild(host);
        }
        return cached;
    }

    /** Only if already built: for hot paths that must not build (every damage event, every mob joining). */
    public static ActiveTraits peek(LivingEntity host) {
        ActiveTraits cached = CACHE.get(host);
        return cached == null ? NONE : cached;
    }

    /** Work out the creature's traits from what it wears, and put their attribute changes on it. */
    public static ActiveTraits rebuild(LivingEntity host) {
        PartsData.Store store = PartsData.of(host.level());
        ActiveTraits old = CACHE.remove(host);
        if (old != null) {
            old.removeModifiers(host);
        }
        Map<ResourceLocation, Integer> levels = new LinkedHashMap<>();
        Map<ResourceLocation, Boolean> fromSet = new HashMap<>();
        ResourceLocation mob = null;
        boolean pure = true;
        int pieces = 0;
        for (EquipmentSlot slot : ARMOUR) {
            ItemStack worn = host.getItemBySlot(slot);
            CarcassArmour armour = CarcassArmourItem.armour(worn);
            if (armour == null) {
                pure = false;
                continue;
            }
            pieces++;
            if (mob == null) {
                mob = armour.body();
            }
            pure &= armour.pure(mob);
            for (TraitList.Resolved t : armour.traits(store)) {
                add(store, levels, t);
            }
        }
        Optional<ResolvedMob.FullSet> set = Optional.empty();
        if (pure && pieces == ARMOUR.length && mob != null) {
            set = store.resolve(mob, false).fullSet();
            set.ifPresent(s -> {
                for (TraitList.Resolved t : s.bonus()) {
                    add(store, levels, t);
                    fromSet.put(t.id(), true);
                }
                for (TraitList.Resolved t : s.drawback()) {
                    add(store, levels, t);
                    fromSet.put(t.id(), true);
                }
            });
        }
        List<Entry> entries = new ArrayList<>();
        levels.forEach((id, level) -> {
            Trait trait = store.trait(id);
            if (trait != null) {
                entries.add(new Entry(id, trait, Math.min(level, Math.max(1, trait.maxLevel())), fromSet.getOrDefault(id, false)));
            } else if (!host.level().isClientSide) {
                BloodAndBones.LOGGER.warn("Carcass armour names trait {}, which is not loaded", id);
            }
        });
        ActiveTraits built = new ActiveTraits(List.copyOf(entries), store.generation(), set, set.isPresent() ? mob : null);
        built.applyPassiveAttributes(host, true);
        CACHE.put(host, built);
        return built;
    }

    private static void add(PartsData.Store store, Map<ResourceLocation, Integer> levels, TraitList.Resolved t) {
        Trait trait = store.trait(t.id());
        if (trait != null && trait.sums()) {
            levels.merge(t.id(), t.level(), Integer::sum);
        } else {
            levels.merge(t.id(), t.level(), Math::max);
        }
    }

    /** The modifier id of one effect of one trait. */
    static ResourceLocation modifierId(ResourceLocation trait, int index) {
        return BloodAndBones.asResource("trait/" + trait.getNamespace() + "/" + trait.getPath() + "/" + index);
    }

    /**
     * Put the passive attribute effects on the creature. Unconditional ones always; ones with a condition
     * only while it holds ({@code conditions} false skips checking them, for the first build).
     */
    void applyPassiveAttributes(LivingEntity host, boolean unconditionalOnly) {
        for (Entry entry : entries) {
            List<TraitEffect> effects = entry.trait().effects();
            for (int i = 0; i < effects.size(); i++) {
                TraitEffect effect = effects.get(i);
                if (effect.trigger() != Trigger.PASSIVE || !(effect.effect() instanceof TraitEffects.AttributeEffect attribute) || !effect.appliesIn("armour")) {
                    continue;
                }
                if (effect.requirements().isPresent()) {
                    if (unconditionalOnly) {
                        continue;
                    }
                    boolean holds = TraitEvents.holds(host, entry, effect, null);
                    ResourceLocation id = modifierId(entry.id(), i);
                    if (!holds) {
                        AttributeInstance instance = host.getAttribute(attribute.attribute());
                        if (instance != null && instance.getModifier(id) != null) {
                            instance.removeModifier(id);
                        }
                        applied.remove(id);
                        continue;
                    }
                }
                put(host, modifierId(entry.id(), i), attribute.attribute(), attribute.amount().calculate(entry.level()), attribute.operation());
            }
        }
    }

    private void put(LivingEntity host, ResourceLocation id, Holder<Attribute> attribute, float amount, AttributeModifier.Operation operation) {
        AttributeInstance instance = host.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        AttributeModifier current = instance.getModifier(id);
        if (current == null || current.amount() != amount || current.operation() != operation) {
            instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, operation));
        }
        applied.put(id, attribute);
    }

    void removeModifiers(LivingEntity host) {
        applied.forEach((id, attribute) -> {
            AttributeInstance instance = host.getAttribute(attribute);
            if (instance != null) {
                instance.removeModifier(id);
            }
        });
        applied.clear();
    }

    /** Forget a creature (it left). */
    public static void forget(LivingEntity host) {
        CACHE.remove(host);
    }
}
