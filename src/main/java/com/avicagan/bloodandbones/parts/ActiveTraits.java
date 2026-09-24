package com.avicagan.bloodandbones.parts;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.minion.MinionBuild;
import com.avicagan.bloodandbones.minion.MinionData;
import com.avicagan.bloodandbones.minion.MinionEntity;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.WeakHashMap;

/**
 * The traits working on one creature now (docs/PARTS-AND-TRAITS.md sections 5.1 and 5.9): a player's from the carcass
 * armour they wear, a minion's from the pieces and organ it is built of. The same trait from several places counts once
 * at its highest level, unless it sums; a full set of one mob adds that mob's bonus and drawback. Nothing else gets
 * traits: a zombie in carcass armour gets its armour points only. Built when what it is made of changes or data
 * reloads, never every tick.
 * <p>
 * Each host has a context, "armour" for a player and "minion" for a minion: a trait whose {@code contexts} leave it out
 * is never built in, and an effect whose {@code context} names the other one never runs ({@link #applies}).
 */
public final class ActiveTraits {
    public static final String ARMOUR = "armour";
    public static final String MINION = "minion";

    /**
     * One working trait. {@code fromSet}: it came with a full set, which alone may make damage vanish entirely.
     * {@code slot}: on a player, the piece it came from (the one giving it its level), for the Organ Ability's order
     * and its cooldown; null from a full set, and on a minion.
     */
    public record Entry(ResourceLocation id, Trait trait, int level, boolean fromSet, @Nullable EquipmentSlot slot) {
    }

    /** One effect of a type working on a host: the trait it came from, its place in that trait, its entry, and the effect. */
    public record Found<T extends TraitEffect.Effect>(Entry entry, int index, TraitEffect facet, T effect) {
    }

    // both sides look traits up (the client for movement it predicts), so the caches are shared between threads
    private static final Map<LivingEntity, ActiveTraits> CACHE = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<LivingEntity, Long> LAST_WET = Collections.synchronizedMap(new WeakHashMap<>());
    public static final ActiveTraits NONE = new ActiveTraits(List.of(), -1, Optional.empty(), null, "", null);
    /** The pieces in the order the Organ Ability goes through them. */
    public static final EquipmentSlot[] PIECES = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private final List<Entry> entries;
    private final int generation;
    private final Optional<ResolvedMob.FullSet> set;
    /** The mob of the full set worn, if one is. */
    @Nullable
    private final ResourceLocation setMob;
    private final String context;
    /** What it was built from (the four pieces' makeup, or the minion's build), to notice a change without an event. */
    @Nullable
    private final Object builtFrom;
    /** The attribute modifiers this put on the creature, to take off again on rebuild. */
    final Map<ResourceLocation, Holder<Attribute>> applied = new HashMap<>();

    private ActiveTraits(List<Entry> entries, int generation, Optional<ResolvedMob.FullSet> set, @Nullable ResourceLocation setMob, String context,
                         @Nullable Object builtFrom) {
        this.entries = entries;
        this.generation = generation;
        this.set = set;
        this.setMob = setMob;
        this.context = context;
        this.builtFrom = builtFrom;
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

    /** "armour" for a player, "minion" for a minion, "" for a creature with no traits. */
    public String context() {
        return context;
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

    /** Whether one of its traits' effects works here: in this host's context, and not switched off by the server. */
    public boolean applies(TraitEffect facet) {
        return facet.appliesIn(context) && TraitEffects.enabled(facet.effect());
    }

    /**
     * Every effect of this type working here, for the hooks that read them where something happens (a flag, a
     * visibility change, a deflection). Empty, and nothing made, for a creature with none.
     */
    public <T extends TraitEffect.Effect> List<Found<T>> find(Class<T> type) {
        List<Found<T>> out = null;
        for (Entry entry : entries) {
            List<TraitEffect> effects = entry.trait().effects();
            for (int i = 0; i < effects.size(); i++) {
                TraitEffect facet = effects.get(i);
                if (type.isInstance(facet.effect()) && applies(facet)) {
                    if (out == null) {
                        out = new ArrayList<>();
                    }
                    out.add(new Found<>(entry, i, facet, type.cast(facet.effect())));
                }
            }
        }
        return out == null ? List.of() : out;
    }

    /** Who gets traits: players from their armour, minions from their build; nobody else (null). */
    @Nullable
    public static String contextOf(LivingEntity host) {
        return host instanceof MinionEntity ? MINION : host instanceof Player ? ARMOUR : null;
    }

    /** The creature's traits, rebuilt if what it is made of or its data changed since. Never null; empty for most creatures. */
    public static ActiveTraits of(LivingEntity host) {
        if (contextOf(host) == null) {
            return NONE;
        }
        ActiveTraits cached = CACHE.get(host);
        PartsData.Store store = PartsData.of(host.level());
        if (cached == null || cached.generation != store.generation() || cached.stale(host)) {
            cached = rebuild(host);
        }
        return cached;
    }

    /** Only if already built: for hot paths that must not build (every damage event, every mob joining). */
    public static ActiveTraits peek(LivingEntity host) {
        ActiveTraits cached = CACHE.get(host);
        return cached == null ? NONE : cached;
    }

    /** Whether what it was built from is no longer what the creature is made of. */
    private boolean stale(LivingEntity host) {
        if (host instanceof MinionEntity minion) {
            MinionBuild build = minion.build().orElse(null);
            return build != builtFrom && !Objects.equals(build, builtFrom);
        }
        if (!(builtFrom instanceof CarcassArmour[] worn)) {
            return true;
        }
        for (int i = 0; i < PIECES.length; i++) {
            if (!Objects.equals(worn[i], CarcassArmourItem.armour(host.getItemBySlot(PIECES[i])))) {
                return true;
            }
        }
        return false;
    }

    /** Work out the creature's traits from what it wears or is built of, and put their attribute changes on it. */
    public static ActiveTraits rebuild(LivingEntity host) {
        ActiveTraits old = CACHE.remove(host);
        if (old != null) {
            old.removeModifiers(host);
        }
        if (contextOf(host) == null) {
            return NONE;
        }
        PartsData.Store store = PartsData.of(host.level());
        ActiveTraits built = host instanceof MinionEntity minion ? fromBuild(minion, store) : fromArmour(host, store);
        built.applyPassiveAttributes(host, true);
        CACHE.put(host, built);
        return built;
    }

    /** A player's: every worn piece's traits, and a full set's bonus and drawback. */
    private static ActiveTraits fromArmour(LivingEntity host, PartsData.Store store) {
        Map<ResourceLocation, Integer> levels = new LinkedHashMap<>();
        Map<ResourceLocation, EquipmentSlot> from = new HashMap<>();
        Map<ResourceLocation, Boolean> fromSet = new HashMap<>();
        CarcassArmour[] worn = new CarcassArmour[PIECES.length];
        ResourceLocation mob = null;
        boolean pure = true;
        int pieces = 0;
        for (int i = 0; i < PIECES.length; i++) {
            ItemStack stack = host.getItemBySlot(PIECES[i]);
            CarcassArmour armour = CarcassArmourItem.armour(stack);
            worn[i] = armour;
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
                // the piece a trait counts from is the one giving it its level (the first, for a tie or a sum)
                Integer before = levels.get(t.id());
                add(store, levels, t);
                if (before == null || !isSum(store, t.id()) && t.level() > before) {
                    from.put(t.id(), PIECES[i]);
                }
            }
        }
        Optional<ResolvedMob.FullSet> set = Optional.empty();
        if (pure && pieces == PIECES.length && mob != null) {
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
        List<Entry> entries = entries(host, store, levels, id -> fromSet.getOrDefault(id, false) ? null : from.get(id), fromSet, ARMOUR);
        return new ActiveTraits(entries, store.generation(), set, set.isPresent() ? mob : null, ARMOUR, worn);
    }

    /**
     * A minion's: every fitted piece's minion traits (the torso's too) and its organ's, from its data (MinionData#traits),
     * and on flesh the hide traits of each different mob it keeps the hide of, up to three (section 6.6; brass none).
     */
    private static ActiveTraits fromBuild(MinionEntity minion, PartsData.Store store) {
        MinionBuild build = minion.build().orElse(null);
        Map<ResourceLocation, Integer> levels = new LinkedHashMap<>();
        if (build != null) {
            for (List<TraitList.Resolved> source : MinionData.traits(store, build)) {
                for (TraitList.Resolved t : source) {
                    add(store, levels, t);
                }
            }
            for (ResourceLocation hide : minion.hides()) {
                for (TraitList.Resolved t : store.resolve(hide, false).hide()) {
                    add(store, levels, t);
                }
            }
        }
        List<Entry> entries = entries(minion, store, levels, id -> null, Map.of(), MINION);
        return new ActiveTraits(entries, store.generation(), Optional.empty(), null, MINION, build);
    }

    private static List<Entry> entries(LivingEntity host, PartsData.Store store, Map<ResourceLocation, Integer> levels,
                                       java.util.function.Function<ResourceLocation, EquipmentSlot> slot, Map<ResourceLocation, Boolean> fromSet, String context) {
        List<Entry> entries = new ArrayList<>();
        levels.forEach((id, level) -> {
            Trait trait = store.trait(id);
            if (trait == null) {
                if (!host.level().isClientSide) {
                    BloodAndBones.LOGGER.warn("{} names trait {}, which is not loaded", context.equals(MINION) ? "A minion's parts" : "Carcass armour", id);
                }
            } else if (trait.contexts().contains(context)) {
                entries.add(new Entry(id, trait, Math.min(level, Math.max(1, trait.maxLevel())), fromSet.getOrDefault(id, false), slot.apply(id)));
            }
        });
        return List.copyOf(entries);
    }

    private static boolean isSum(PartsData.Store store, ResourceLocation id) {
        Trait trait = store.trait(id);
        return trait != null && trait.sums();
    }

    private static void add(PartsData.Store store, Map<ResourceLocation, Integer> levels, TraitList.Resolved t) {
        if (isSum(store, t.id())) {
            levels.merge(t.id(), t.level(), Integer::sum);
        } else {
            levels.merge(t.id(), t.level(), Math::max);
        }
    }

    /** The id of the attribute modifier one passive attribute effect of one trait puts on its host. */
    public static ResourceLocation modifierId(ResourceLocation trait, int index) {
        return BloodAndBones.asResource("trait/" + trait.getNamespace() + "/" + trait.getPath() + "/" + index);
    }

    /**
     * Put the passive attribute effects on the creature, as transient modifiers, times the trait strength.
     * Unconditional ones always; ones with a condition only while it holds ({@code unconditionalOnly} skips
     * checking them, for the first build).
     */
    void applyPassiveAttributes(LivingEntity host, boolean unconditionalOnly) {
        for (Entry entry : entries) {
            List<TraitEffect> effects = entry.trait().effects();
            for (int i = 0; i < effects.size(); i++) {
                TraitEffect effect = effects.get(i);
                if (effect.trigger() != Trigger.PASSIVE || !(effect.effect() instanceof TraitEffects.AttributeEffect attribute) || !applies(effect)) {
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
                put(host, modifierId(entry.id(), i), attribute.attribute(), attribute.amount().calculate(entry.level()) * TraitEffects.strength(),
                        attribute.operation());
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
        LAST_WET.remove(host);
    }

    // ---- how long the host has been dry (the dry_for condition)

    /** Note whether the host is wet now: in water, rain or a bubble column. The trait tick calls this every half second. */
    public static void updateDry(LivingEntity host) {
        long now = host.level().getGameTime();
        if (wet(host)) {
            LAST_WET.put(host, now);
        } else {
            LAST_WET.putIfAbsent(host, now);
        }
    }

    /** Seconds since the host was last wet, counted from when it was first looked at. */
    public static float drySeconds(LivingEntity host) {
        long now = host.level().getGameTime();
        Long last = LAST_WET.get(host);
        if (last == null) {
            updateDry(host);
            return 0.0F;
        }
        return Math.max(0L, now - last) / 20.0F;
    }

    static boolean wet(LivingEntity host) {
        // the fluid check too, for a creature standing in water that has not ticked since it got there
        return host.isInWaterRainOrBubble() || host.level().getFluidState(host.blockPosition()).is(FluidTags.WATER);
    }
}
