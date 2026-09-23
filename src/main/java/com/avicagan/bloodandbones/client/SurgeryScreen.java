package com.avicagan.bloodandbones.client;

import com.avicagan.bloodandbones.body.Body;
import com.avicagan.bloodandbones.body.BodyEffects;
import com.avicagan.bloodandbones.body.BodyPart;
import com.avicagan.bloodandbones.body.Surgery;
import com.avicagan.bloodandbones.body.SurgerySeatEntity;
import com.avicagan.bloodandbones.body.SurgeryTableBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.EnumMap;
import java.util.Map;

/**
 * Lying on the Surgery Table: each part of your body, what it is now, and what the thing on the table would
 * do to it. It closes when you get off the table.
 */
public class SurgeryScreen extends Screen {
    private final BlockPos table;
    private final Map<BodyPart, Button> buttons = new EnumMap<>(BodyPart.class);

    /** Height of a part's row: nine parts fit a small window. */
    private static final int ROW = 20;

    private int top() {
        return Math.max(32, (height - BodyPart.values().length * ROW - 30) / 2 + 10);
    }

    public SurgeryScreen(BlockPos table) {
        super(Component.translatable("bloodandbones.surgery.title"));
        this.table = table;
    }

    @Override
    protected void init() {
        int top = top();
        int i = 0;
        for (BodyPart part : BodyPart.values()) {
            Button button = Button.builder(Component.empty(), b -> PacketDistributor.sendToServer(new Surgery.ActionPayload(table, part)))
                    .bounds(width / 2 + 10, top + i * ROW, 130, ROW - 2).build();
            buttons.put(part, addRenderableWidget(button));
            i++;
        }
        addRenderableWidget(Button.builder(Component.translatable("bloodandbones.surgery.done"), b -> onClose())
                .bounds(width / 2 - 50, top + BodyPart.values().length * ROW + 6, 100, 20).build());
        refresh();
    }

    private ItemStack tool() {
        return Minecraft.getInstance().level != null && Minecraft.getInstance().level.getBlockEntity(table) instanceof SurgeryTableBlockEntity be
                ? be.item() : ItemStack.EMPTY;
    }

    private void refresh() {
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        Body body = BodyEffects.body(player);
        ItemStack tool = tool();
        buttons.forEach((part, button) -> {
            Surgery.Action action = Surgery.action(body, tool, part);
            button.setMessage(Component.translatable(action.translationKey()));
            button.active = action != Surgery.Action.NONE;
        });
    }

    @Override
    public void tick() {
        Player player = Minecraft.getInstance().player;
        if (player == null || !(player.getVehicle() instanceof SurgerySeatEntity seat) || !seat.blockPosition().equals(table)) {
            onClose();
            return;
        }
        refresh();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        int top = top();
        graphics.drawCenteredString(font, title, width / 2, top - 28, 0xFFFFFF);
        ItemStack tool = tool();
        Component lying = tool.isEmpty() ? Component.translatable("bloodandbones.surgery.empty")
                : Component.translatable("bloodandbones.surgery.on_table", tool.getHoverName());
        graphics.drawCenteredString(font, lying, width / 2, top - 15, 0xC8C8C8);
        Body body = BodyEffects.body(player);
        int i = 0;
        for (BodyPart part : BodyPart.values()) {
            Component state = switch (body.state(part)) {
                case NATURAL -> Component.translatable("bloodandbones.surgery.state.natural");
                case MISSING -> Component.translatable("bloodandbones.surgery.state.missing");
                case IMPLANT -> body.works(part, player) ? body.implant(part).getHoverName()
                        : Component.translatable("bloodandbones.surgery.state.dead", body.implant(part).getHoverName());
            };
            int y = top + i * ROW + 5;
            graphics.drawString(font, Component.translatable(part.translationKey()), width / 2 - 140, y, 0xFFFFFF);
            graphics.drawString(font, state, width / 2 - 60, y, body.state(part) == Body.State.MISSING ? 0xD04040 : 0xA0A0A0);
            i++;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
