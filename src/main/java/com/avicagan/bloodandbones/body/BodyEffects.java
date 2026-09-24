package com.avicagan.bloodandbones.body;

import com.avicagan.bloodandbones.BloodAndBones;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * What a body's missing and fitted parts do to its player. A hand without a working arm uses, places and
 * swings nothing (its empty hand still works the Surgery Table, so help is always within reach), and with
 * the main arm gone blocks break slowly. A leg without a working foot under it slows walking and weakens
 * the jump; with neither there is no sprinting.
 */
public final class BodyEffects {
    private static final ResourceLocation LEGS = BloodAndBones.asResource("legs");
    /** How well a leg that is not there walks and jumps, and how fast a missing arm breaks blocks. */
    public static final float MISSING_WALK = 0.2F;
    public static final float MISSING_JUMP = 0.4F;
    public static final float MISSING_WORK = 0.2F;

    private BodyEffects() {
    }

    public static Body body(LivingEntity entity) {
        return entity.getData(BBAttachments.BODY);
    }

    /** Whether this creature has lost or had fitted anything, without giving every mob a body to find out. */
    public static boolean altered(LivingEntity entity) {
        return entity.hasData(BBAttachments.BODY) && !entity.getData(BBAttachments.BODY).whole();
    }

    private static float leg(Body body, BodyPart part, LivingEntity wearer, boolean jump) {
        if (body.works(part, wearer)) {
            if (body.state(part) != Body.State.IMPLANT) {
                return 1.0F;
            }
            ImplantSpec spec = ((ImplantItem) body.implant(part).getItem()).spec();
            return jump ? spec.jump() : spec.walk();
        }
        return jump ? MISSING_JUMP : MISSING_WALK;
    }

    /** The working implant's spec in a part, if there is one. */
    @org.jetbrains.annotations.Nullable
    private static ImplantSpec working(Body body, BodyPart part, LivingEntity wearer) {
        return body.state(part) == Body.State.IMPLANT && body.works(part, wearer) ? ((ImplantItem) body.implant(part).getItem()).spec() : null;
    }

    /** How fast this body walks, flesh being 1: the two legs' figures, averaged. */
    public static float walk(Body body, LivingEntity wearer) {
        return (leg(body, BodyPart.LEFT_LEG, wearer, false) + leg(body, BodyPart.RIGHT_LEG, wearer, false)) / 2.0F;
    }

    public static float jump(Body body, LivingEntity wearer) {
        return (leg(body, BodyPart.LEFT_LEG, wearer, true) + leg(body, BodyPart.RIGHT_LEG, wearer, true)) / 2.0F;
    }

    /** The arm that holds what is in this hand. */
    public static BodyPart armFor(LivingEntity player, InteractionHand hand) {
        HumanoidArm main = player.getMainArm();
        return BodyPart.arm(hand == InteractionHand.MAIN_HAND ? main : main.getOpposite());
    }

    public static boolean handWorks(Player player, InteractionHand hand) {
        return body(player).works(armFor(player, hand), player);
    }

    /** How fast the main arm breaks blocks, flesh being 1. */
    public static float work(Player player) {
        Body body = body(player);
        BodyPart arm = armFor(player, InteractionHand.MAIN_HAND);
        if (!body.works(arm, player)) {
            return MISSING_WORK;
        }
        return body.state(arm) == Body.State.IMPLANT ? ((ImplantItem) body.implant(arm).getItem()).work() : 1.0F;
    }

    private static final ResourceLocation ARMS = BloodAndBones.asResource("arms");

    /**
     * Bring the creature's walking, jumping, hitting and reach in line with its limbs. A mob with an arm gone
     * hits half as hard, with both gone not at all.
     */
    public static void refresh(LivingEntity player) {
        Body body = body(player);
        apply(player.getAttribute(Attributes.MOVEMENT_SPEED), LEGS, walk(body, player) - 1.0F, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        apply(player.getAttribute(Attributes.JUMP_STRENGTH), LEGS, jump(body, player) - 1.0F, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        float safeFall = 0.0F;
        for (BodyPart leg : new BodyPart[]{BodyPart.LEFT_LEG, BodyPart.RIGHT_LEG}) {
            ImplantSpec spec = working(body, leg, player);
            safeFall += spec == null ? 0.0F : spec.safeFall() / 2.0F;
        }
        apply(player.getAttribute(Attributes.SAFE_FALL_DISTANCE), LEGS, safeFall, AttributeModifier.Operation.ADD_VALUE);
        ImplantSpec main = working(body, armFor(player, InteractionHand.MAIN_HAND), player);
        if (player instanceof Player) {
            apply(player.getAttribute(Attributes.ATTACK_DAMAGE), ARMS, main == null ? 0.0F : main.attack(), AttributeModifier.Operation.ADD_VALUE);
        } else {
            float arms = ((body.works(BodyPart.LEFT_ARM, player) ? 1 : 0) + (body.works(BodyPart.RIGHT_ARM, player) ? 1 : 0)) / 2.0F;
            apply(player.getAttribute(Attributes.ATTACK_DAMAGE), ARMS, arms - 1.0F, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        }
        float reach = 0.0F;
        for (BodyPart arm : new BodyPart[]{BodyPart.LEFT_ARM, BodyPart.RIGHT_ARM}) {
            ImplantSpec spec = working(body, arm, player);
            reach = Math.max(reach, spec == null ? 0.0F : spec.reach());
        }
        apply(player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE), ARMS, reach, AttributeModifier.Operation.ADD_VALUE);
        apply(player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE), ARMS, reach, AttributeModifier.Operation.ADD_VALUE);
    }

    private static void apply(AttributeInstance attribute, ResourceLocation id, float amount, AttributeModifier.Operation operation) {
        if (attribute == null) {
            return;
        }
        if (Math.abs(amount) < 1.0E-4F) {
            attribute.removeModifier(id);
        } else {
            attribute.addOrUpdateTransientModifier(new AttributeModifier(id, amount, operation));
        }
    }

    /** Whether the body can see: at least one working eye. */
    public static boolean sees(Body body, LivingEntity wearer) {
        return body.works(BodyPart.LEFT_EYE, wearer) || body.works(BodyPart.RIGHT_EYE, wearer);
    }

    /**
     * Once a second, on the server: powered implants take their fuel from the worn tank, and the organs do
     * what they do. A heart that is gone or dead leaves you weak and slow (it does not kill); lungs, winded
     * (no sprinting); no working eye, blind; no working stomach, you cannot eat.
     */
    public static void second(LivingEntity player) {
        Body body = body(player);
        if (body.whole()) {
            return;
        }
        drain(player);
        if (!sees(body, player)) {
            effect(player, net.minecraft.world.effect.MobEffects.BLINDNESS, 0, 60);
        }
        if (!body.works(BodyPart.HEART, player)) {
            effect(player, net.minecraft.world.effect.MobEffects.WEAKNESS, 1, 60);
            effect(player, net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 1, 60);
        }
        if (body.has(ImplantSpec.Ability.NIGHT_VISION, player)) {
            effect(player, net.minecraft.world.effect.MobEffects.NIGHT_VISION, 0, 260);
        }
        if (body.has(ImplantSpec.Ability.REGENERATION, player)) {
            effect(player, net.minecraft.world.effect.MobEffects.REGENERATION, 0, 60);
        }
        if (body.has(ImplantSpec.Ability.WATER_BREATHING, player)) {
            effect(player, net.minecraft.world.effect.MobEffects.WATER_BREATHING, 0, 60);
        }
        if (body.has(ImplantSpec.Ability.IRON_GUT, player)) {
            player.removeEffect(net.minecraft.world.effect.MobEffects.HUNGER);
            player.removeEffect(net.minecraft.world.effect.MobEffects.POISON);
        }
    }

    private static void effect(LivingEntity player, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect, int amplifier, int ticks) {
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(effect, ticks, amplifier, true, false, true));
    }

    /** Each working powered implant takes its fuel for a second from the worn tank. */
    public static void drain(LivingEntity player) {
        Body body = body(player);
        net.minecraft.world.item.ItemStack tank = com.avicagan.bloodandbones.backtank.FluidBacktankItem.wornBy(player);
        if (tank.isEmpty()) {
            return;
        }
        int total = 0;
        for (BodyPart part : BodyPart.values()) {
            ImplantSpec spec = working(body, part, player);
            if (spec != null && spec.fuel() != null) {
                total += spec.drain();
            }
        }
        if (total > 0) {
            take(player, total);
        }
    }

    /** Take up to this much from the worn tank; how much there was. */
    public static int take(LivingEntity player, int amount) {
        net.minecraft.world.item.ItemStack tank = com.avicagan.bloodandbones.backtank.FluidBacktankItem.wornBy(player);
        net.neoforged.neoforge.fluids.FluidStack fluid = com.avicagan.bloodandbones.backtank.FluidBacktankItem.fluid(tank);
        int taken = Math.min(amount, fluid.getAmount());
        if (taken > 0) {
            fluid.shrink(taken);
            com.avicagan.bloodandbones.backtank.FluidBacktankItem.setFluid(tank, fluid);
        }
        return taken;
    }

    /** After any change: the effects now, and everyone who can see the creature told. */
    public static void changed(LivingEntity entity) {
        refresh(entity);
        if (!entity.level().isClientSide) {
            BodySync.send(entity);
        }
    }

    /** Mobs that have been operated on: their legs, arms and organs, once a second. */
    @SubscribeEvent
    public static void onMobTick(net.neoforged.neoforge.event.tick.EntityTickEvent.Post event) {
        if (event.getEntity() instanceof LivingEntity living && !(living instanceof Player) && !living.level().isClientSide
                && living.tickCount % 20 == 0 && altered(living)) {
            refresh(living);
            second(living);
        }
    }

    // the checks run on both sides, from the body the client was sent, so the two agree

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.tickCount % 10 == 0) {
            refresh(player);
        }
        if (!player.level().isClientSide && player.tickCount % 20 == 0) {
            second(player);
        }
        Body body = body(player);
        if (player.isSprinting() && (walk(body, player) <= MISSING_WALK + 1.0E-4F || !body.works(BodyPart.LUNGS, player))) {
            player.setSprinting(false);
        }
    }

    /** No working stomach: nothing can be eaten. */
    @SubscribeEvent
    public static void onEat(net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent.Start event) {
        if (event.getEntity() instanceof Player player && event.getItem().has(net.minecraft.core.component.DataComponents.FOOD)
                && !body(player).works(BodyPart.STOMACH, player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onUseItem(PlayerInteractEvent.RightClickItem event) {
        if (!handWorks(event.getEntity(), event.getHand())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    @SubscribeEvent
    public static void onUseOnBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getItemStack().isEmpty() && !handWorks(event.getEntity(), event.getHand())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    @SubscribeEvent
    public static void onUseOnEntity(PlayerInteractEvent.EntityInteract event) {
        if (!event.getItemStack().isEmpty() && !handWorks(event.getEntity(), event.getHand())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    @SubscribeEvent
    public static void onUseOnEntityAt(PlayerInteractEvent.EntityInteractSpecific event) {
        if (!event.getItemStack().isEmpty() && !handWorks(event.getEntity(), event.getHand())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (!handWorks(event.getEntity(), InteractionHand.MAIN_HAND)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        float work = work(event.getEntity());
        if (work != 1.0F) {
            event.setNewSpeed(event.getNewSpeed() * work);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        changed(event.getEntity());
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        changed(event.getEntity());
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        changed(event.getEntity());
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof LivingEntity target && event.getEntity() instanceof ServerPlayer watcher
                && (target instanceof Player || altered(target))) {
            BodySync.sendTo(watcher, target);
        }
    }
}
