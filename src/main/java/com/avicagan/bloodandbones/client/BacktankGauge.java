package com.avicagan.bloodandbones.client;

import com.avicagan.bloodandbones.backtank.FluidBacktankItem;
import com.avicagan.bloodandbones.body.BodyEffects;
import com.avicagan.bloodandbones.body.BodyPart;
import com.avicagan.bloodandbones.body.ImplantItem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.createmod.catnip.gui.element.GuiGameElement;
import net.createmod.catnip.theme.Color;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FastColor;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The Fluid Backtank's gauge, styled as Create's air gauge ({@code RemainingAirOverlay}) and in its place, to the
 * right of the hotbar's top: the tank worn, what it holds in buckets, and a bar of the fluid itself. Unlike Create's,
 * it is always up while a tank is worn, and while any powered implant is fitted (a dry one shows an empty tank). The
 * implants the tank runs sit under the bar, dimmed when they are not getting their fuel. While Create's own gauge is
 * up (diving on a Create backtank's air), this one moves up a row, so both read.
 */
public final class BacktankGauge {
    /** Width of the bar of fluid under the reading. */
    public static final int BAR = 30;
    /** Below this share of a full tank, with implants to run, the reading flashes red, as Create's does when air runs low. */
    public static final float LOW = 0.1F;
    /**
     * How far up it moves while Create's air gauge is in its place: past Create's tank icon, which Create draws 9 lower
     * for a netherite tank in lava, with this gauge's bar and strip of implants clear above it.
     */
    public static final int ABOVE_CREATE = 27;

    private static final Map<TextureAtlasSprite, Integer> AVERAGES = new WeakHashMap<>();

    private BacktankGauge() {
    }

    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.options.hideGui || mc.gameMode == null || mc.gameMode.getPlayerMode() == GameType.SPECTATOR) {
            return;
        }
        ItemStack tank = FluidBacktankItem.wornBy(player);
        List<ItemStack> implants = poweredImplants(player);
        if (tank.isEmpty() && implants.isEmpty()) {
            return;
        }
        FluidStack fluid = FluidBacktankItem.fluid(tank);
        int capacity = FluidBacktankItem.capacity(tank);
        float full = capacity <= 0 ? 0.0F : Math.min(1.0F, fluid.getAmount() / (float) capacity);

        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(graphics.guiWidth() / 2 + 90, graphics.guiHeight() - 53 - (createAirShowing(player) ? ABOVE_CREATE : 0), 0);
        // with nothing worn, the plainest tank, faded: the implants have nothing to run on
        ItemStack shown = tank.isEmpty() ? new ItemStack(com.avicagan.bloodandbones.registry.BBItems.backtank(com.avicagan.bloodandbones.backtank.BacktankTier.COPPER)) : tank;
        GuiGameElement.of(shown).at(0, 0).render(graphics);
        if (tank.isEmpty()) {
            graphics.fill(0, 0, 16, 16, 0x99000000);
        }

        int color = 0xFF_FFFFFF;
        if (!implants.isEmpty() && full < LOW && (player.tickCount / 10) % 2 == 0) {
            color = Color.mixColors(0xFF_FF0000, color, 0.25F);
        }
        graphics.drawString(mc.font, reading(fluid), 18, 0, color);

        // the bar: the fluid's own texture in its own colour, as far along as the tank is full
        graphics.fill(17, 10, 18 + BAR + 1, 14, 0xFF1A1A1A);
        int filled = Math.round(BAR * full);
        if (filled > 0 && !fluid.isEmpty()) {
            IClientFluidTypeExtensions look = IClientFluidTypeExtensions.of(fluid.getFluid());
            TextureAtlasSprite sprite = mc.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(look.getStillTexture(fluid));
            int tint = look.getTintColor(fluid);
            graphics.blit(18, 11, 0, filled, 2, sprite, FastColor.ARGB32.red(tint) / 255.0F, FastColor.ARGB32.green(tint) / 255.0F,
                    FastColor.ARGB32.blue(tint) / 255.0F, 1.0F);
        }

        // what it runs, half size, dimmed where the tank does not feed it
        pose.pushPose();
        pose.translate(18, 16, 0);
        pose.scale(0.5F, 0.5F, 1.0F);
        // on a dark strip, like the bar's, so the icons read over whatever is behind them (a fleshy arm, bright sky)
        if (!implants.isEmpty()) {
            graphics.fill(-1, -1, implants.size() * 18 - 1, 17, 0x77000000);
        }
        for (int i = 0; i < implants.size(); i++) {
            ItemStack implant = implants.get(i);
            graphics.renderItem(implant, i * 18, 0);
            if (!((ImplantItem) implant.getItem()).working(implant, player)) {
                pose.pushPose();
                pose.translate(0, 0, 200);
                graphics.fill(i * 18, 0, i * 18 + 16, 16, 0xAA000000);
                pose.popPose();
            }
        }
        pose.popPose();
        pose.popPose();
    }

    /**
     * Whether Create's air gauge is drawn where this one sits: the checks {@code RemainingAirOverlay} makes, in its
     * order. Create's diving helmet leaves the air it shows in the player's data while a backtank's air is being
     * breathed, under water or in lava.
     */
    public static boolean createAirShowing(LocalPlayer player) {
        if (player.isCreative() || !player.getPersistentData().contains("VisualBacktankAir")) {
            return false;
        }
        boolean isAir = player.getEyeInFluidType().isAir()
                || player.level().getBlockState(BlockPos.containing(player.getX(), player.getEyeY(), player.getZ())).is(Blocks.BUBBLE_COLUMN);
        boolean canBreathe = !player.canDrownInFluidType(player.getEyeInFluidType()) || MobEffectUtil.hasWaterBreathing(player)
                || player.getAbilities().invulnerable;
        return !(isAir || canBreathe) || player.isInLava();
    }

    /** The amount, in buckets to a tenth, as the gauge reads it. */
    public static Component reading(FluidStack fluid) {
        return Component.literal(String.format(java.util.Locale.ROOT, "%.1f B", fluid.getAmount() / 1000.0F));
    }

    /** The implants fitted that run on something from the tank, in body order. */
    public static List<ItemStack> poweredImplants(LocalPlayer player) {
        List<ItemStack> out = new ArrayList<>();
        if (!BodyEffects.altered(player)) {
            return out;
        }
        for (BodyPart part : BodyPart.values()) {
            ItemStack implant = BodyEffects.body(player).implant(part);
            if (implant.getItem() instanceof ImplantItem item && item.powered()) {
                out.add(implant);
            }
        }
        return out;
    }

    /**
     * The colour a fluid shows as, one colour for all of it: its texture's average, tinted as it is drawn. The
     * backtank's item bar uses it, so a tank of blood shows red, of water blue, of lava orange.
     */
    public static int colour(FluidStack fluid) {
        if (fluid.isEmpty()) {
            return 0xFF8A8A8A;
        }
        IClientFluidTypeExtensions look = IClientFluidTypeExtensions.of(fluid.getFluid());
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(look.getStillTexture(fluid));
        int average = AVERAGES.computeIfAbsent(sprite, BacktankGauge::average);
        return FastColor.ARGB32.multiply(average, look.getTintColor(fluid) | 0xFF000000);
    }

    /** A sprite's first frame, averaged over its opaque pixels (NativeImage keeps them as ABGR). */
    private static int average(TextureAtlasSprite sprite) {
        long r = 0;
        long g = 0;
        long b = 0;
        int n = 0;
        for (int y = 0; y < sprite.contents().height(); y++) {
            for (int x = 0; x < sprite.contents().width(); x++) {
                int abgr = sprite.getPixelRGBA(0, x, y);
                if ((abgr >>> 24) == 0) {
                    continue;
                }
                r += abgr & 0xFF;
                g += (abgr >> 8) & 0xFF;
                b += (abgr >> 16) & 0xFF;
                n++;
            }
        }
        return n == 0 ? 0xFFFFFFFF : FastColor.ARGB32.color(255, (int) (r / n), (int) (g / n), (int) (b / n));
    }
}
