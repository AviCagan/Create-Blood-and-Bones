package com.avicagan.bloodandbones.body;

import com.avicagan.bloodandbones.backtank.FluidBacktankItem;
import com.avicagan.bloodandbones.registry.BBDataComponents;
import com.avicagan.bloodandbones.registry.BBFluids;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Necrosis, from the brief: organic prosthetics rot from use (swinging and mining with a Flesh Arm, running
 * on a Sinew Leg, eating with a Furnace Stomach), never from time passing. Blood from the worn backtank
 * perfuses them, clearing it cheaply and routinely. At the most it stops the part giving its bonus (it works
 * as a missing part, a small penalty); it never falls off and never kills. It is kept on the fitted implant.
 */
public final class Necrosis {
    public static final int MAX = 100;
    /** Rot from one swing (a hit or a block broken), from one block run, from one meal. */
    public static final int SWING = 1;
    public static final double BLOCKS_PER_POINT = 4.0;
    public static final int MEAL = 3;
    /** Each second a rotting part takes this much blood from the tank and clears this much rot. */
    public static final int PERFUSE_MB = 1;
    public static final int PERFUSE_POINTS = 2;

    private static final Map<Player, Vec3> LAST = new WeakHashMap<>();
    private static final Map<Player, Double> RUN = new WeakHashMap<>();

    private Necrosis() {
    }

    public static int of(ItemStack stack) {
        return stack.getOrDefault(BBDataComponents.NECROSIS, 0);
    }

    /** Add rot to an organic implant in a part, if there is one; tells everyone when it crosses the line. */
    public static void use(LivingEntity wearer, BodyPart part, int amount) {
        if (wearer.level().isClientSide) {
            return;
        }
        Body body = BodyEffects.body(wearer);
        ItemStack implant = body.implant(part);
        if (!(implant.getItem() instanceof ImplantItem item) || !item.organic()) {
            return;
        }
        // the flesh set bonus: a body given over to flesh rots half as fast
        if (com.avicagan.bloodandbones.cyber.SetBonus.flesh(body) && wearer.getRandom().nextFloat() >= com.avicagan.bloodandbones.cyber.SetBonus.FLESH_NECROSIS) {
            return;
        }
        int before = of(implant);
        int after = Math.min(MAX, before + amount);
        if (after != before) {
            implant.set(BBDataComponents.NECROSIS, after);
            if (after == MAX || after / 10 != before / 10) {
                BodyEffects.changed(wearer);
            }
        }
    }

    /**
     * Once a second: blood from the worn tank clears rot from every organic implant that has some.
     *
     * @return whether anything changed
     */
    public static boolean perfuse(LivingEntity wearer) {
        Body body = BodyEffects.body(wearer);
        boolean changed = false;
        for (BodyPart part : BodyPart.values()) {
            ItemStack implant = body.implant(part);
            if (!(implant.getItem() instanceof ImplantItem item) || !item.organic() || of(implant) <= 0) {
                continue;
            }
            if (!FluidBacktankItem.fluid(FluidBacktankItem.wornBy(wearer)).is(BBFluids.blood())) {
                continue;
            }
            if (BodyEffects.take(wearer, PERFUSE_MB) < PERFUSE_MB) {
                continue;
            }
            implant.set(BBDataComponents.NECROSIS, Math.max(0, of(implant) - PERFUSE_POINTS));
            changed = true;
        }
        return changed;
    }

    @SubscribeEvent
    public static void onHit(AttackEntityEvent event) {
        use(event.getEntity(), BodyEffects.armFor(event.getEntity(), InteractionHand.MAIN_HAND), SWING);
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        use(event.getPlayer(), BodyEffects.armFor(event.getPlayer(), InteractionHand.MAIN_HAND), SWING);
    }

    @SubscribeEvent
    public static void onEaten(LivingEntityUseItemEvent.Finish event) {
        if (event.getItem().has(net.minecraft.core.component.DataComponents.FOOD)) {
            use(event.getEntity(), BodyPart.STOMACH, MEAL);
        }
    }

    /** Running wears the legs: distance covered on the ground, sprinting or walking. */
    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer) || !BodyEffects.altered(player)) {
            return;
        }
        Vec3 now = player.position();
        Vec3 last = LAST.put(player, now);
        if (last == null || !player.onGround() || player.isPassenger()) {
            return;
        }
        double moved = Math.min(1.0, Math.sqrt((now.x - last.x) * (now.x - last.x) + (now.z - last.z) * (now.z - last.z)));
        double run = RUN.getOrDefault(player, 0.0) + moved * (player.isSprinting() ? 1.5 : 1.0);
        while (run >= BLOCKS_PER_POINT) {
            run -= BLOCKS_PER_POINT;
            use(player, BodyPart.LEFT_LEG, 1);
            use(player, BodyPart.RIGHT_LEG, 1);
        }
        RUN.put(player, run);
    }
}
