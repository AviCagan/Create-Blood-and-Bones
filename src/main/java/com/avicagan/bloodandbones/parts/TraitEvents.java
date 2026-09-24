package com.avicagan.bloodandbones.parts;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.body.BBAttachments;
import com.avicagan.bloodandbones.registry.BBDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
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
import net.minecraft.world.item.ItemStack;
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
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
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

/**
 * Where traits act (docs/PARTS-AND-TRAITS.md section 5.2): each trigger on a NeoForge hook, each hook doing
 * nothing for a creature with no traits. A triggered effect does its own work ({@link TraitEffect.Effect#run});
 * this finds which are due, on a player in carcass armour ("armour") or a minion ("minion") alike. Also the base
 * stats of carcass armour, from its scraps' material, and the data's sync to clients. The activate trigger is in
 * {@link Activation}.
 */
public final class TraitEvents {
    /** The most traits can take off damage between them (a full set alone may take it all). */
    public static final float REDUCTION_FLOOR = 0.2F;

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

    /**
     * Armour, toughness, knockback resistance and the quirk of a carcass piece, from its material, with its
     * tier's bonus on top (each tier's in place of the one before). A chestplate with a backtank strapped on
     * has the better armour and toughness of the two.
     */
    @SubscribeEvent
    public static void onItemAttributes(ItemAttributeModifierEvent event) {
        CarcassArmour armour = CarcassArmourItem.armour(event.getItemStack());
        if (armour == null || !(event.getItemStack().getItem() instanceof CarcassArmourItem item)) {
            return;
        }
        PartsData.Store store = CarcassArmourItem.store();
        ScrapMaterial material = CarcassArmourItem.material(armour, store);
        ArmourTier tier = store.tier(armour.tier());
        double armourPoints = material.armourFor(armour.piece()) + (tier == null ? 0 : tier.bonusFor(armour.piece()));
        double toughness = material.toughness() + (tier == null ? 0.0F : tier.toughness());
        double knockback = material.knockbackResistance() + (tier == null ? 0.0F : tier.knockbackResistance());
        com.avicagan.bloodandbones.backtank.BacktankTier tank = event.getItemStack().get(com.avicagan.bloodandbones.registry.BBDataComponents.STRAPPED_TANK);
        if (tank != null) {
            armourPoints = Math.max(armourPoints, tank.defense());
            toughness = Math.max(toughness, tank.toughness());
        }
        EquipmentSlotGroup group = EquipmentSlotGroup.bySlot(item.getEquipmentSlot());
        String base = "carcass_armour." + armour.piece();
        add(event, Attributes.ARMOR, base + ".armour", armourPoints, group);
        add(event, Attributes.ARMOR_TOUGHNESS, base + ".toughness", toughness, group);
        add(event, Attributes.KNOCKBACK_RESISTANCE, base + ".knockback", knockback, group);
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

    /** A player's armour changed what it is made of: their traits again (other mobs get none from armour). */
    @SubscribeEvent
    public static void onEquipment(LivingEquipmentChangeEvent event) {
        CarcassArmour from = CarcassArmourItem.armour(event.getFrom());
        CarcassArmour to = CarcassArmourItem.armour(event.getTo());
        // the same piece worn a little more, or its strapped tank a little emptier, has the same traits
        boolean same = from != null && from.equals(to) && event.getFrom().getItem() == event.getTo().getItem();
        if (event.getSlot().getType() == EquipmentSlot.Type.HUMANOID_ARMOR && !event.getEntity().level().isClientSide
                && event.getEntity() instanceof Player && (from != null || to != null) && !same) {
            ActiveTraits.rebuild(event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ActiveTraits.forget(event.getEntity());
        Activation.forget(event.getEntity());
    }

    /** Back in the world (logged in, or home from the End): the pieces still cooling down show it again. */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        showCooldowns(event.getEntity());
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        showCooldowns(event.getEntity());
    }

    private static void showCooldowns(Player player) {
        long now = player.level().getGameTime();
        for (EquipmentSlot slot : ActiveTraits.PIECES) {
            ItemStack piece = player.getItemBySlot(slot);
            long until = 0L;
            for (long u : piece.getOrDefault(BBDataComponents.COOLDOWN_UNTIL.get(), Map.<String, Long>of()).values()) {
                until = Math.max(until, u);
            }
            if (until > now) {
                player.getCooldowns().addCooldown(piece.getItem(), (int) Math.min(Integer.MAX_VALUE, until - now));
            }
        }
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

    /**
     * Every half second (a player's tick, a minion's own), staggered by entity id: how long it has been dry,
     * conditional attributes, passive effects kept up, and tick effects that are due. A minion lying powered down
     * keeps its passives but runs no tick effects.
     */
    public static void tick(LivingEntity host) {
        ActiveTraits traits = ActiveTraits.of(host);
        if (traits.isEmpty()) {
            return;
        }
        ActiveTraits.updateDry(host);
        traits.applyPassiveAttributes(host, false);
        boolean down = host instanceof com.avicagan.bloodandbones.minion.MinionEntity minion && minion.poweredDown();
        for (ActiveTraits.Entry entry : traits.entries()) {
            List<TraitEffect> effects = entry.trait().effects();
            for (int i = 0; i < effects.size(); i++) {
                TraitEffect effect = effects.get(i);
                if (!traits.applies(effect)) {
                    continue;
                }
                if (effect.trigger() == Trigger.PASSIVE && TraitEffects.keepsUp(effect.effect()) && holds(host, entry, effect, null)) {
                    effect.effect().keepUp(new TraitContext(host, traits, entry, i, effect, Trigger.PASSIVE, null, null, 0.0F));
                } else if (effect.trigger() == Trigger.TICK && !down && host.tickCount % Math.max(20, effect.interval()) < 10
                        && goes(host, entry, i, effect, null)) {
                    effect.effect().run(new TraitContext(host, traits, entry, i, effect, Trigger.TICK, null, null, 0.0F));
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
                if (!own.applies(effect)) {
                    continue;
                }
                if (effect.effect() instanceof TraitEffects.ImmunityEffect immunity && !immunity.damageTags().isEmpty() && matches(source, immunity.damageTags())
                        && holds(victim, entry, effect, source)) {
                    event.setCanceled(true);
                    return;
                }
                if ((effect.trigger() == Trigger.HURT || effect.trigger() == Trigger.PASSIVE) && effect.effect() instanceof TraitEffects.DamageEffect damage
                        && "in".equals(damage.direction()) && matches(source, damage.damageTags()) && filter(source, effect.filter()) && holds(victim, entry, effect, source)) {
                    float m = strengthened(damage.multiplier().calculate(entry.level()));
                    if (m < 1.0F) {
                        reduction *= Math.max(0.0F, m);
                        setImmune |= m <= 0.0F && entry.fromSet();
                    } else {
                        multiplier *= m;
                    }
                }
            }
        }
        if (source.getEntity() instanceof LivingEntity attacker && blow(source)) {
            ActiveTraits theirs = ActiveTraits.peek(attacker);
            for (ActiveTraits.Entry entry : theirs.entries()) {
                for (TraitEffect effect : entry.trait().effects()) {
                    if (effect.trigger() == Trigger.ATTACK && effect.effect() instanceof TraitEffects.DamageEffect damage && "out".equals(damage.direction())
                            && theirs.applies(effect) && holds(attacker, entry, effect, source)) {
                        multiplier *= strengthened(damage.multiplier().calculate(entry.level()));
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

    /** After a hit lands: the victim's hurt effects (on itself, on whoever hit it, around it), the attacker's attack effects. */
    @SubscribeEvent
    public static void onDamaged(LivingDamageEvent.Post event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide || event.getNewDamage() <= 0.0F) {
            return;
        }
        LivingEntity attacker = event.getSource().getEntity() instanceof LivingEntity a ? a : null;
        fire(victim, Trigger.HURT, event.getSource(), attacker, event.getNewDamage());
        if (attacker != null && blow(event.getSource())) {
            fire(attacker, Trigger.ATTACK, event.getSource(), victim, event.getNewDamage());
        }
    }

    /**
     * Landing from a fall: the host's fall effects, and its fall damage changes (a fall-trigger damage effect scales the
     * fall's damage, reductions floored as any are). Server side; movement a client predicts is the flags' business.
     */
    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        LivingEntity host = event.getEntity();
        ActiveTraits traits = ActiveTraits.peek(host);
        if (host.level().isClientSide || traits.isEmpty()) {
            return;
        }
        float reduction = 1.0F;
        float multiplier = 1.0F;
        boolean setImmune = false;
        for (ActiveTraits.Entry entry : traits.entries()) {
            List<TraitEffect> effects = entry.trait().effects();
            for (int i = 0; i < effects.size(); i++) {
                TraitEffect effect = effects.get(i);
                if (effect.trigger() == Trigger.FALL && effect.effect() instanceof TraitEffects.DamageEffect damage && "in".equals(damage.direction())
                        && traits.applies(effect) && goes(host, entry, i, effect, null)) {
                    float m = strengthened(damage.multiplier().calculate(entry.level()));
                    if (m < 1.0F) {
                        reduction *= Math.max(0.0F, m);
                        setImmune |= m <= 0.0F && entry.fromSet();
                    } else {
                        multiplier *= m;
                    }
                }
            }
        }
        if (reduction < REDUCTION_FLOOR && !setImmune) {
            reduction = REDUCTION_FLOOR;
        }
        if (reduction * multiplier != 1.0F) {
            event.setDamageMultiplier(event.getDamageMultiplier() * reduction * multiplier);
        }
        fire(host, Trigger.FALL, null, null, event.getDistance());
    }

    /**
     * A lethal blow: first the host's lethal saves (any effect that is a {@link TraitEffect.DeathSaver}, of any trigger,
     * off cooldown, its chance come up and its condition holding), which call the death off; its cooldown starts only
     * when it saves. A minion's own lethal save is collapsing, which it does before this is asked. Nothing saves from
     * what gets past invulnerability (the void, /kill), as with a Totem of Undying.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity host = event.getEntity();
        ActiveTraits traits = ActiveTraits.peek(host);
        if (host.level().isClientSide || traits.isEmpty() || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }
        LivingEntity killer = event.getSource().getEntity() instanceof LivingEntity k ? k : null;
        for (ActiveTraits.Entry entry : traits.entries()) {
            List<TraitEffect> effects = entry.trait().effects();
            for (int i = 0; i < effects.size(); i++) {
                TraitEffect effect = effects.get(i);
                if (effect.effect() instanceof TraitEffect.DeathSaver saver && traits.applies(effect) && !coolingDown(host, entry, i)
                        && (effect.chance() >= 1.0F || host.getRandom().nextFloat() < effect.chance()) && holds(host, entry, effect, event.getSource())
                        && saver.save(new TraitContext(host, traits, entry, i, effect, effect.trigger(), killer, event.getSource(), 0.0F))) {
                    startCooldown(host, entry, i, effect.cooldown());
                    event.setCanceled(true);
                    return;
                }
            }
        }
    }

    /** Something died at the host's hand, and stayed dead: the host's kill effects. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onKill(LivingDeathEvent event) {
        if (!event.getEntity().level().isClientSide && event.getSource().getEntity() instanceof LivingEntity killer && killer != event.getEntity()) {
            fire(killer, Trigger.KILL, event.getSource(), event.getEntity(), event.getEntity().getMaxHealth());
        }
    }

    /** A mob setting its sights on the host: the host's targeted effects (a bolt of speed, say). */
    @SubscribeEvent
    public static void onTargeted(LivingChangeTargetEvent event) {
        if (event.getNewAboutToBeSetTarget() != null && !event.getEntity().level().isClientSide
                && !(event.getEntity() instanceof Mob mob && mob.getTarget() == event.getNewAboutToBeSetTarget())) {
            fire(event.getNewAboutToBeSetTarget(), Trigger.TARGETED, null, event.getEntity(), 0.0F);
        }
    }

    /**
     * Run the host's effects of one trigger (each doing its own work), those off cooldown, their chance come up and
     * their condition holding. Damage changes are not run here: they are applied where the damage is.
     *
     * @param amount the damage, or the distance fallen, for the effects to read
     */
    public static void fire(LivingEntity host, Trigger trigger, @Nullable DamageSource source, @Nullable LivingEntity other, float amount) {
        ActiveTraits traits = ActiveTraits.peek(host);
        for (ActiveTraits.Entry entry : traits.entries()) {
            List<TraitEffect> effects = entry.trait().effects();
            for (int i = 0; i < effects.size(); i++) {
                TraitEffect effect = effects.get(i);
                if (effect.trigger() != trigger || effect.effect() instanceof TraitEffects.DamageEffect || !traits.applies(effect)
                        || source != null && !filter(source, effect.filter())) {
                    continue;
                }
                if (goes(host, entry, i, effect, source)) {
                    effect.effect().run(new TraitContext(host, traits, entry, i, effect, trigger, other, source, amount));
                }
            }
        }
    }

    /** A damage multiplier at the server's trait strength: the change from 1 scaled (a third off at strength 0.5 is a sixth). */
    static float strengthened(float multiplier) {
        float strength = TraitEffects.strength();
        return strength == 1.0F ? multiplier : 1.0F + (multiplier - 1.0F) * strength;
    }

    // ---- immunity

    @SubscribeEvent
    public static void onEffectApplicable(MobEffectEvent.Applicable event) {
        LivingEntity host = event.getEntity();
        ActiveTraits traits = ActiveTraits.peek(host);
        for (ActiveTraits.Entry entry : traits.entries()) {
            for (TraitEffect effect : entry.trait().effects()) {
                if (effect.effect() instanceof TraitEffects.ImmunityEffect immunity && traits.applies(effect)
                        && immunity.mobEffects().contains(event.getEffectInstance().getEffect())) {
                    event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
                    return;
                }
            }
        }
    }

    @SubscribeEvent
    public static void onBreathe(LivingBreatheEvent event) {
        ActiveTraits traits = ActiveTraits.peek(event.getEntity());
        for (ActiveTraits.Entry entry : traits.entries()) {
            for (TraitEffect effect : entry.trait().effects()) {
                if (effect.effect() instanceof TraitEffects.ImmunityEffect immunity && immunity.breathe() && traits.applies(effect)) {
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
        ActiveTraits traits = ActiveTraits.of(player);
        for (ActiveTraits.Entry entry : traits.entries()) {
            for (TraitEffect effect : entry.trait().effects()) {
                if (effect.effect() instanceof TraitEffects.DietEffect diet && "graze".equals(diet.effect()) && traits.applies(effect)) {
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

    /**
     * Mobs a trait makes hunt, flee or defend whoever carries it get the goal as they join the world. A friendly
     * reaction needs no goal: the mobs are kept off the carrier where they take aim ({@code SocialEffects#onChangeTarget}).
     */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide || !(event.getEntity() instanceof Mob mob)) {
            return;
        }
        PartsData.Store store = PartsData.SERVER;
        for (Map.Entry<ResourceLocation, Trait> t : store.traits().entrySet()) {
            for (TraitEffect effect : t.getValue().effects()) {
                if (effect.effect() instanceof TraitEffects.ReactionEffect reaction && mob.getType().is(reaction.entities()) && TraitEffects.enabled(reaction)) {
                    ResourceLocation id = t.getKey();
                    if ("flee".equals(reaction.mode()) && mob instanceof PathfinderMob pathfinder) {
                        mob.goalSelector.addGoal(1, new AvoidEntityGoal<>(pathfinder, Player.class, reaction.radius(), 1.0, 1.3,
                                e -> e instanceof LivingEntity living && ActiveTraits.peek(living).level(id) > 0));
                    } else if ("hunt".equals(reaction.mode())) {
                        mob.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(mob, Player.class, 10, true, false,
                                living -> ActiveTraits.peek(living).level(id) > 0 && !((Player) living).isCreative()));
                    } else if ("defend".equals(reaction.mode())) {
                        mob.targetSelector.addGoal(1, new com.avicagan.bloodandbones.parts.effect.SocialGoals.DefendHost(mob, id, reaction.radius()));
                    }
                }
            }
        }
    }

    // ---- helpers

    /** Whether the effect's condition holds for the host now (always, when it has none). Server side only; false on a client. */
    public static boolean holds(LivingEntity host, ActiveTraits.Entry entry, TraitEffect effect, @Nullable DamageSource source) {
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

    /**
     * Whether one effect goes now: off cooldown, its condition holding, and then its chance come up. Its cooldown starts
     * only then, so a hit or a check where the condition fails (or the chance does not come up) uses none of it.
     */
    static boolean goes(LivingEntity host, ActiveTraits.Entry entry, int index, TraitEffect effect, @Nullable DamageSource source) {
        return !coolingDown(host, entry, index) && holds(host, entry, effect, source) && ready(host, entry, index, effect);
    }

    /**
     * Whether its chance comes up and its cooldown has run out; starts the cooldown if so. Asked last, once the effect's
     * condition is known to hold ({@link #goes}).
     */
    public static boolean ready(LivingEntity host, ActiveTraits.Entry entry, int index, TraitEffect effect) {
        if (effect.chance() < 1.0F && host.getRandom().nextFloat() >= effect.chance()) {
            return false;
        }
        if (effect.cooldown() <= 0) {
            return true;
        }
        if (coolingDown(host, entry, index)) {
            return false;
        }
        startCooldown(host, entry, index, effect.cooldown());
        return true;
    }

    /**
     * Whether one effect of one trait is still cooling down on the host. Cooldowns are kept as the game time they run out
     * (docs/PARTS-AND-TRAITS.md section 7.6), so they last through a relog or a reload: an Organ Ability's on the piece it
     * came from, everything else on the creature ({@code BBAttachments.TRAIT_COOLDOWNS}).
     */
    public static boolean coolingDown(LivingEntity host, ActiveTraits.Entry entry, int index) {
        ItemStack piece = piece(host, entry, index);
        Map<String, Long> kept = piece != null ? piece.getOrDefault(BBDataComponents.COOLDOWN_UNTIL.get(), Map.<String, Long>of())
                : host.hasData(BBAttachments.TRAIT_COOLDOWNS) ? host.getData(BBAttachments.TRAIT_COOLDOWNS) : Map.<String, Long>of();
        if (kept.isEmpty()) {
            return false;
        }
        Long until = kept.get(cooldownKey(entry, index));
        return until != null && host.level().getGameTime() < until;
    }

    /** Start one effect's cooldown on the host, or on the piece for an Organ Ability (nothing for none). Those run out are cleared. */
    public static void startCooldown(LivingEntity host, ActiveTraits.Entry entry, int index, int ticks) {
        if (ticks <= 0) {
            return;
        }
        long now = host.level().getGameTime();
        ItemStack piece = piece(host, entry, index);
        if (piece != null) {
            Map<String, Long> kept = new HashMap<>(piece.getOrDefault(BBDataComponents.COOLDOWN_UNTIL.get(), Map.<String, Long>of()));
            kept.values().removeIf(until -> until <= now);
            kept.put(cooldownKey(entry, index), now + ticks);
            piece.set(BBDataComponents.COOLDOWN_UNTIL.get(), Map.copyOf(kept));
        } else {
            Map<String, Long> kept = host.getData(BBAttachments.TRAIT_COOLDOWNS);
            kept.values().removeIf(until -> until <= now);
            kept.put(cooldownKey(entry, index), now + ticks);
        }
    }

    private static String cooldownKey(ActiveTraits.Entry entry, int index) {
        return entry.id() + "#" + index;
    }

    /** The piece an effect's cooldown is kept on: a player's Organ Ability, on the worn piece its trait counts from. Else null. */
    @Nullable
    private static ItemStack piece(LivingEntity host, ActiveTraits.Entry entry, int index) {
        if (!(host instanceof Player) || entry.slot() == null || entry.trait().effects().get(index).trigger() != Trigger.ACTIVATE) {
            return null;
        }
        ItemStack piece = host.getItemBySlot(entry.slot());
        return piece.isEmpty() ? null : piece;
    }

    /**
     * Whether damage is its causer's own hit, for the attack trigger (docs/PARTS-AND-TRAITS.md section 5.2): not thorns
     * sent back (a barbed hide's, which counts as the wearer's direct hit), magic or a blast, the hits vanilla's thorns never
     * answer either. A trait's beam is no blow as well: it strikes with no direct creature ({@code HitscanEffect}).
     */
    public static boolean blow(DamageSource source) {
        return !source.is(DamageTypeTags.AVOIDS_GUARDIAN_THORNS);
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
            case "projectile" -> source.is(DamageTypeTags.IS_PROJECTILE);
            case "fire" -> source.is(DamageTypeTags.IS_FIRE);
            default -> true;
        };
    }
}
