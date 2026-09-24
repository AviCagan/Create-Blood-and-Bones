package com.avicagan.bloodandbones.minion;

import com.avicagan.bloodandbones.registry.BBDataComponents;
import com.avicagan.bloodandbones.registry.BBEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;

import java.util.List;

/**
 * A powered-down minion folded up to carry (docs/PARTS-AND-TRAITS.md section 6.8): its maker crouch-holds an
 * empty hand on it for a few seconds. It keeps everything: build, what it carries, name, maker, even its blood
 * (none). Used on a block it unfolds there, still powered down, until it is given blood.
 */
public class DormantMinionItem extends Item {
    public DormantMinionItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    /** Fold a powered-down minion into its maker's hands. */
    public static void fold(MinionEntity minion, Player maker) {
        ItemStack stack = new ItemStack(com.avicagan.bloodandbones.registry.BBItems.DORMANT_MINION.get());
        CompoundTag tag = new CompoundTag();
        minion.saveWithoutId(tag);
        stack.set(BBDataComponents.DORMANT.get(), CustomData.of(tag));
        if (minion.hasCustomName()) {
            stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, minion.getCustomName());
        }
        maker.getInventory().placeItemBackInInventory(stack);
        minion.level().playSound(null, minion.blockPosition(), SoundEvents.SLIME_BLOCK_BREAK, SoundSource.NEUTRAL, 1.0F, 0.6F);
        minion.discard();
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }
        CustomData data = context.getItemInHand().get(BBDataComponents.DORMANT.get());
        MinionEntity minion = BBEntities.MINION.get().create(level);
        if (data == null || minion == null) {
            return InteractionResult.FAIL;
        }
        minion.load(data.copyTag());
        BlockPos at = context.getClickedPos().relative(context.getClickedFace());
        minion.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, context.getRotation(), 0.0F);
        level.addFreshEntity(minion);
        minion.powerDown();
        context.getItemInHand().shrink(1);
        level.playSound(null, at, SoundEvents.SLIME_BLOCK_PLACE, SoundSource.NEUTRAL, 1.0F, 0.6F);
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("bloodandbones.minion.dormant").withStyle(ChatFormatting.GRAY));
    }
}
