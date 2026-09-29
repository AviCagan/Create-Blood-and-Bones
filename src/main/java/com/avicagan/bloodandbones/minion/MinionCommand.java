package com.avicagan.bloodandbones.minion;

import com.avicagan.bloodandbones.parts.PartsData;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /bloodandbones minion fitness}: the full breakdown of the minion its maker (or an operator) is looking at, every
 * task, for balancing with the owner (docs/NEXT.md 1.4): each task's fitness and word, or why it cannot; what it waits for;
 * each stat it reads with its value and where that came from; its knack and disposition; what the fitness makes of the
 * work; and the blood it uses at work. The same lines as the task screen's hover, with the number before it was held where
 * the cap took some off. Worked out on the server, where the task and disposition files are.
 */
public final class MinionCommand {
    /** How far off a minion may be looked at. */
    public static final double REACH = 16.0;

    private MinionCommand() {
    }

    public static void onRegisterCommands(net.neoforged.neoforge.event.RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("bloodandbones").then(Commands.literal("minion")
                .then(Commands.literal("fitness").executes(MinionCommand::fitness))));
    }

    private static int fitness(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        MinionEntity minion = lookedAt(player, REACH);
        if (minion == null) {
            ctx.getSource().sendFailure(Component.translatable("bloodandbones.command.minion.none"));
            return 0;
        }
        if (!minion.isMaker(player) && !ctx.getSource().hasPermission(2)) {
            ctx.getSource().sendFailure(Component.translatable("bloodandbones.command.minion.not_yours"));
            return 0;
        }
        List<Component> lines = fitness(minion);
        for (Component line : lines) {
            ctx.getSource().sendSuccess(() -> line, false);
        }
        return lines.size();
    }

    /** The minion this player's crosshair is on within this far, a block in the way stopping it (as a crosshair's pick). */
    @Nullable
    public static MinionEntity lookedAt(Player player, double reach) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1.0F).scale(reach));
        // a wall in the way cuts the look short
        var block = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 stop = block.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? end : block.getLocation();
        AABB box = player.getBoundingBox().expandTowards(stop.subtract(eye)).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, stop, box, e -> e instanceof MinionEntity && e.isAlive(), eye.distanceToSqr(stop));
        return hit != null && hit.getEntity() instanceof MinionEntity minion ? minion : null;
    }

    /**
     * Every task's lines for this minion: first what it is doing ("Farmer 120% at home"), then, task by task in the screen's
     * order, the lines its row shows at the anchor it works at now where the task allows it, indented under the first.
     */
    public static List<Component> fitness(MinionEntity minion) {
        PartsData.Store store = PartsData.of(minion.level());
        List<Component> out = new ArrayList<>();
        out.add(Component.translatable("bloodandbones.command.minion.title", minion.getDisplayName(), TaskWords.doing(minion)).withStyle(ChatFormatting.GOLD));
        for (MinionTask task : MinionTask.values()) {
            MinionTask.Anchor at = store.task(task).anchorFor(minion.anchor());
            MinionFitness.Row row = minion.row(task, at);
            List<Component> lines = TaskWords.lines(store, minion, row, at);
            for (int i = 0; i < lines.size(); i++) {
                Component line = lines.get(i);
                if (i == 0 && task.rated() && row.can() && Math.abs(row.raw() - row.fitness()) > 0.005F) {
                    // what the cap took off, for balancing
                    line = line.copy().append(" ").append(Component.translatable("bloodandbones.command.minion.raw", TaskWords.percent(row.raw()))
                            .withStyle(ChatFormatting.DARK_GRAY));
                }
                out.add(i == 0 ? line : Component.literal("  ").append(line));
            }
        }
        return out;
    }
}
