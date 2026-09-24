package com.avicagan.bloodandbones.parts;

import com.avicagan.bloodandbones.BloodAndBones;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootContext;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingBreatheEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Where traits act (docs/PARTS-AND-TRAITS.md section 5.2): each trigger on a NeoForge hook, each hook doing
 * nothing for a creature with no traits. Also the base stats of carcass armour, from its scraps' material,
 * and the data's sync to clients.
 */
public final class TraitEvents {
    /** The most traits can take off damage between them (a full set alone may take it all). */
    public static final float REDUCTION_FLOOR = 0.2F;

    private static final Map<LivingEntity, Map<String, Long>> COOLDOWNS = new WeakHashMap<>();

    private TraitEvents() {
    }

    // ---- data

    @SubscribeEvent
    public static void onSync(OnDatapackSyncEvent event) {
        List<PartsData.SyncPayload> payloads = PartsData.payloads();
        event.getRelevantPlayers().forEach(player -> payloads.forEach(p -> PacketDistributor.sendToPlayer(player, p)));
    }

    @SubscribeEvent
    public static void onTags(TagsUpdatedEvent event) {
        (event.getUpdateCause() == TagsUpdatedEvent.UpdateCause.CLIENT_PACKET_RECEIVED ? PartsData.CLIENT : PartsData.SERVER).invalidate();
    }

    // ---- base stats

    /** Armour, toughness, knockback resistance and the quirk of a carcass piece, from its material. */
    @SubscribeEvent
    public static void onItemAttributes(ItemAttributeModifierEvent event) {
        CarcassArmour armour = CarcassArmourItem.armour(event.getItemStack());
        if (armour == null || !(event.getItemStack().getItem() instanceof CarcassArmourItem item)) {
            return;
        }
        PartsData.Store store = CarcassArmourItem.store();
        ScrapMaterial material = CarcassArmourItem.material(armour, store);
        EquipmentSlotGroup group = EquipmentSlotGroup.bySlot(item.getEquipmentSlot());
        String base = "carcass_armour." + armour.piece();
        add(event, Attributes.ARMOR, base + ".armour", material.armourFor(armour.piece()), group);
        add(event, Attributes.ARMOR_TOUGHNESS, base + ".toughness", material.toughness(), group);
        add(event, Attributes.KNOCKBACK_RESISTANCE, base + ".knockback", material.knockbackResistance(), group);
        for (int i = 0; i < material.quirk().size(); i++) {
            ScrapMaterial.Quirk quirk = material.quirk().get(i);
            event.addModifier(quirk.attribute(), new AttributeModifier(BloodAndBones.asResource(base + ".quirk." + i), quirk.amount(), quirk.operation()), group);
        }
    }

    private static void add(ItemAttributeModifierEvent event, Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, String id, double amount,
                            EquipmentSlotGroup group) {
        if (amount != 0.0) {
            event.addModifier(attribute, new AttributeModifier(BloodAndBones.asResource(id), amount, AttributeModifier.Operation.ADD_VALUE), group);
        }
    }

    // ---- building

    @SubscribeEvent
    public static void onEquipment(LivingEquipmentChangeEvent event) {
        if (event.getSlot().getType() == EquipmentSlot.Type.HUMANOID_ARMOR && !event.getEntity().level().isClientSide
                && (CarcassArmourItem.armour(event.getFrom()) != null || CarcassArmourItem.armour(event.getTo()) != null)) {
            ActiveTraits.rebuild(event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ActiveTraits.forget(event.getEntity());
    }

    // ---- passive and tick

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide || (player.tickCount + player.getId()) % 10 != 0) {
            return;
        }
        tick(player);
    }

    /** Every half second: conditional attributes, kept-up effects, and tick effects that are due. */
    public static void tick(LivingEntity host) {
        ActiveTraits traits = ActiveTraits.of(host);
        if (traits.isEmpty()) {
            return;
        }
        traits.applyPassiveAttributes(host, false);
        for (ActiveTraits.Entry entry : traits.entries()) {
            List<TraitEffect> effects = entry.trait().effects();
            for (int i = 0; i < effects.size(); i++) {
                TraitEffect effect = effects.get(i);
                if (!effect.appliesIn(ActiveTraits.context(host))) {
                    continue;
                }
                if (effect.trigger() == Trigger.PASSIVE && effect.effect() instanceof TraitEffects.MobEffectEffect mob && holds(host, entry, effect, null)) {
                    // kept up quietly; night vision past the point where it starts to flicker
                    int duration = mob.effect().is(MobEffects.NIGHT_VISION) ? 220 : Math.max(40, mob.duration());
                    host.addEffect(new MobEffectInstance(mob.effect(), duration, (int) mob.amplifier().calculate(entry.level()), true, false, true));
                } else if (effect.trigger() == Trigger.TICK && host.tickCount % Math.max(20, effect.interval()) < 10
                        && ready(host, entry, i, effect) && holds(host, entry, effect, null)) {
                    run(host, entry, effect, host, null);
                }
            }
        }
    }

    // ---- hurt and attack

    /** Damage made smaller or larger by the victim's hurt traits and the attacker's attack traits; immunity cancels it. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        DamageSource source = event.getSource();
        float multiplier = 1.0F;
        float reduction = 1.0F;
        boolean setImmune = false;
        ActiveTraits own = ActiveTraits.peek(victim);
        for (ActiveTraits.Entry entry : own.entries()) {
            List<TraitEffect> effects = entry.trait().effects();
            for (int i = 0; i < effects.size(); i++) {
                TraitEffect effect = effects.get(i);
                if (!effect.appliesIn(ActiveTraits.context(victim))) {
                    continue;
                }
                if (effect.effect() instanceof TraitEffects.ImmunityEffect immunity && matches(source, immunity.damageTags()) && !immunity.damageTags().isEmpty()) {
                    event.setCanceled(true);
                    return;
                }
                if ((effect.trigger() == Trigger.HURT || effect.trigger() == Trigger.PASSIVE) && effect.effect() instanceof TraitEffects.DamageEffect damage
                        && "in".equals(damage.direction()) && matches(source, damage.damageTags()) && filter(source, effect.filter()) && holds(victim, entry, effect, source)) {
                    float m = damage.multiplier().calculate(entry.level());
                    if (m < 1.0F) {
                        reduction *= Math.max(0.0F, m);
                        setImmune |= m <= 0.0F && entry.fromSet();
                    } else {
                        multiplier *= m;
                    }
                }
            }
        }
        if (source.getEntity() instanceof LivingEntity attacker) {
            for (ActiveTraits.Entry entry : ActiveTraits.peek(attacker).entries()) {
                for (TraitEffect effect : entry.trait().effects()) {
                    if (effect.trigger() == Trigger.ATTACK && effect.effect() instanceof TraitEffects.DamageEffect damage && "out".equals(damage.direction())
                            && effect.appliesIn(ActiveTraits.context(attacker)) && holds(attacker, entry, effect, source)) {
                        multiplier *= damage.multiplier().calculate(entry.level());
                    }
                }
            }
        }
        if (reduction < REDUCTION_FLOOR && !setImmune) {
            reduction = REDUCTION_FLOOR;
        }
        float factor = multiplier * reduction;
        if (factor != 1.0F) {
            event.setAmount(event.getAmount() * factor);
        }
    }

    /** After a hit lands: the victim's hurt effects (on itself, on whoever hit it, around it). */
    @SubscribeEvent
    public static void onDamaged(LivingDamageEvent.Post event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide || event.getNewDamage() <= 0.0F) {
            return;
        }
        LivingEntity attacker = event.getSource().getEntity() instanceof LivingEntity a ? a : null;
        fire(victim, Trigger.HURT, event.getSource(), attacker);
        if (attacker != null) {
            fire(attacker, Trigger.ATTACK, event.getSource(), victim);
        }
    }

    /** A mob setting its sights on the host: the host's targeted effects (a bolt of speed, say). */
    @SubscribeEvent
    public static void onTargeted(LivingChangeTargetEvent event) {
        if (event.getNewAboutToBeSetTarget() != null && !event.getEntity().level().isClientSide
                && !(event.getEntity() instanceof Mob mob && mob.getTarget() == event.getNewAboutToBeSetTarget())) {
            fire(event.getNewAboutToBeSetTarget(), Trigger.TARGETED, null, event.getEntity());
        }
    }

    /** Run the host's effects of one trigger that are not damage changes. */
    private static void fire(LivingEntity host, Trigger trigger, @Nullable DamageSource source, @Nullable LivingEntity other) {
        ActiveTraits traits = ActiveTraits.peek(host);
        for (ActiveTraits.Entry entry : traits.entries()) {
            List<TraitEffect> effects = entry.trait().effects();
            for (int i = 0; i < effects.size(); i++) {
                TraitEffect effect = effects.get(i);
                if (effect.trigger() != trigger || effect.effect() instanceof TraitEffects.DamageEffect || !effect.appliesIn(ActiveTraits.context(host))
                        || source != null && !filter(source, effect.filter())) {
                    continue;
                }
                if (ready(host, entry, i, effect) && holds(host, entry, effect, source)) {
                    run(host, entry, effect, other, source);
                }
            }
        }
    }

    /** Do one effect now. */
    private static void run(LivingEntity host, ActiveTraits.Entry entry, TraitEffect effect, @Nullable LivingEntity other, @Nullable DamageSource source) {
        if (effect.effect() instanceof TraitEffects.MobEffectEffect mob) {
            MobEffectInstance instance = new MobEffectInstance(mob.effect(), mob.duration(), (int) mob.amplifier().calculate(entry.level()));
            switch (mob.target()) {
                case "self" -> host.addEffect(instance);
                case "attacker", "victim", "target" -> {
                    if (other != null && other != host) {
                        other.addEffect(instance, host);
                    }
                }
                case "area" -> host.level().getEntitiesOfClass(LivingEntity.class, host.getBoundingBox().inflate(mob.radius()), e -> e != host && e.isAlive())
                        .forEach(e -> e.addEffect(new MobEffectInstance(instance), host));
                default -> {
                }
            }
        }
    }

    // ---- immunity

    @SubscribeEvent
    public static void onEffectApplicable(MobEffectEvent.Applicable event) {
        LivingEntity host = event.getEntity();
        for (ActiveTraits.Entry entry : ActiveTraits.peek(host).entries()) {
            for (TraitEffect effect : entry.trait().effects()) {
                if (effect.effect() instanceof TraitEffects.ImmunityEffect immunity && effect.appliesIn(ActiveTraits.context(host))
                        && immunity.mobEffects().contains(event.getEffectInstance().getEffect())) {
                    event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
                    return;
                }
            }
        }
    }

    @SubscribeEvent
    public static void onBreathe(LivingBreatheEvent event) {
        for (ActiveTraits.Entry entry : ActiveTraits.peek(event.getEntity()).entries()) {
            for (TraitEffect effect : entry.trait().effects()) {
                if (effect.effect() instanceof TraitEffects.ImmunityEffect immunity && immunity.breathe() && effect.appliesIn(ActiveTraits.context(event.getEntity()))) {
                    event.setCanBreathe(true);
                    event.setRefillAirAmount(Math.max(event.getRefillAirAmount(), 4));
                    return;
                }
            }
        }
    }

    // ---- diet

    /** Graze: crouch and use grass with an empty hand to eat it (the grass is gone to dirt). */
    @SubscribeEvent
    public static void onUseBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (!player.isShiftKeyDown() || !event.getItemStack().isEmpty() || !player.getFoodData().needsFood()) {
            return;
        }
        BlockPos pos = event.getPos();
        if (!event.getLevel().getBlockState(pos).is(Blocks.GRASS_BLOCK)) {
            return;
        }
        for (ActiveTraits.Entry entry : ActiveTraits.of(player).entries()) {
            for (TraitEffect effect : entry.trait().effects()) {
                if (effect.effect() instanceof TraitEffects.DietEffect diet && "graze".equals(diet.effect()) && effect.appliesIn("armour")) {
                    if (!event.getLevel().isClientSide) {
                        event.getLevel().setBlockAndUpdate(pos, Blocks.DIRT.defaultBlockState());
                        player.getFoodData().eat(diet.hunger(), 0.1F);
                        event.getLevel().playSound(null, pos, SoundEvents.GENERIC_EAT, SoundSource.PLAYERS, 0.8F, 0.9F);
                    }
                    event.setCanceled(true);
                    event.setCancellationResult(InteractionResult.SUCCESS);
                    return;
                }
            }
        }
    }

    // ---- reactions

    /** Mobs a trait makes hunt or flee whoever carries it get the goal as they join the world. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide || !(event.getEntity() instanceof Mob mob)) {
            return;
        }
        PartsData.Store store = PartsData.SERVER;
        for (Map.Entry<ResourceLocation, Trait> t : store.traits().entrySet()) {
            for (TraitEffect effect : t.getValue().effects()) {
                if (effect.effect() instanceof TraitEffects.ReactionEffect reaction && mob.getType().is(reaction.entities())) {
                    ResourceLocation id = t.getKey();
                    if ("flee".equals(reaction.mode()) && mob instanceof PathfinderMob pathfinder) {
                        mob.goalSelector.addGoal(1, new AvoidEntityGoal<>(pathfinder, Player.class, reaction.radius(), 1.0, 1.3,
                                e -> e instanceof LivingEntity living && ActiveTraits.peek(living).level(id) > 0));
                    } else if ("hunt".equals(reaction.mode())) {
                        mob.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(mob, Player.class, 10, true, false,
                                living -> ActiveTraits.peek(living).level(id) > 0 && !((Player) living).isCreative()));
                    }
                }
            }
        }
    }

    // ---- helpers

    /** Whether the effect's condition holds for the host now (always, when it has none). */
    static boolean holds(LivingEntity host, ActiveTraits.Entry entry, TraitEffect effect, @Nullable DamageSource source) {
        if (effect.requirements().isEmpty() || !(host.level() instanceof ServerLevel level)) {
            return effect.requirements().isEmpty();
        }
        LootContext context = source != null ? Enchantment.damageContext(level, entry.level(), host, source)
                : Enchantment.entityContext(level, entry.level(), host, host.position());
        try {
            return effect.requirements().get().test(context);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Whether its chance comes up and its cooldown has run out; starts the cooldown if so. */
    private static boolean ready(LivingEntity host, ActiveTraits.Entry entry, int index, TraitEffect effect) {
        if (effect.chance() < 1.0F && host.getRandom().nextFloat() >= effect.chance()) {
            return false;
        }
        if (effect.cooldown() <= 0) {
            return true;
        }
        String key = entry.id() + "#" + index;
        Map<String, Long> own = COOLDOWNS.computeIfAbsent(host, h -> new HashMap<>());
        long now = host.level().getGameTime();
        Long until = own.get(key);
        if (until != null && now < until) {
            return false;
        }
        own.put(key, now + effect.cooldown());
        return true;
    }

    private static boolean matches(DamageSource source, List<TagKey<DamageType>> tags) {
        if (tags.isEmpty()) {
            return true;
        }
        for (TagKey<DamageType> tag : tags) {
            if (source.is(tag)) {
                return true;
            }
        }
        return false;
    }

    /** A hurt effect's filter: melee (a direct hit by a creature), projectile, fire, or any. */
    private static boolean filter(DamageSource source, String filter) {
        return switch (filter) {
            case "melee" -> source.getDirectEntity() instanceof LivingEntity && source.getDirectEntity() == source.getEntity();
            case "projectile" -> source.is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE);
            case "fire" -> source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE);
            default -> true;
        };
    }
}
