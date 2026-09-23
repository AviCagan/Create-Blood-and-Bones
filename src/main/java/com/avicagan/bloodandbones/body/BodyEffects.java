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

    public static Body body(Player player) {
        return player.getData(BBAttachments.BODY);
    }

    private static float leg(Body body, BodyPart part, LivingEntity wearer, boolean jump) {
        if (body.works(part, wearer)) {
            return body.state(part) == Body.State.IMPLANT ? ((ImplantItem) body.implant(part).getItem()).walk() : 1.0F;
        }
        return jump ? MISSING_JUMP : MISSING_WALK;
    }

    /** How fast this body walks, flesh being 1: the two legs' figures, averaged. */
    public static float walk(Body body, LivingEntity wearer) {
        return (leg(body, BodyPart.LEFT_LEG, wearer, false) + leg(body, BodyPart.RIGHT_LEG, wearer, false)) / 2.0F;
    }

    public static float jump(Body body, LivingEntity wearer) {
        return (leg(body, BodyPart.LEFT_LEG, wearer, true) + leg(body, BodyPart.RIGHT_LEG, wearer, true)) / 2.0F;
    }

    /** The arm that holds what is in this hand. */
    public static BodyPart armFor(Player player, InteractionHand hand) {
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

    /** Bring the player's walking and jumping in line with their legs. */
    public static void refresh(Player player) {
        Body body = body(player);
        apply(player.getAttribute(Attributes.MOVEMENT_SPEED), walk(body, player));
        apply(player.getAttribute(Attributes.JUMP_STRENGTH), jump(body, player));
    }

    private static void apply(AttributeInstance attribute, float factor) {
        if (attribute == null) {
            return;
        }
        if (Math.abs(factor - 1.0F) < 1.0E-4F) {
            attribute.removeModifier(LEGS);
        } else {
            attribute.addOrUpdateTransientModifier(new AttributeModifier(LEGS, factor - 1.0F, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    /** After any change: the effects now, and everyone who can see the player told. */
    public static void changed(Player player) {
        refresh(player);
        if (player instanceof ServerPlayer server) {
            BodySync.send(server);
        }
    }

    // the checks run on both sides, from the body the client was sent, so the two agree

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.tickCount % 10 == 0) {
            refresh(player);
        }
        if (player.isSprinting() && walk(body(player), player) <= MISSING_WALK + 1.0E-4F) {
            player.setSprinting(false);
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
        if (event.getTarget() instanceof Player target && event.getEntity() instanceof ServerPlayer watcher) {
            BodySync.sendTo(watcher, target);
        }
    }
}
