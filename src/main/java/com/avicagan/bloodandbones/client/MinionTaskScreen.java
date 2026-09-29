package com.avicagan.bloodandbones.client;

import com.avicagan.bloodandbones.minion.MinionEntity;
import com.avicagan.bloodandbones.minion.MinionTask;
import com.avicagan.bloodandbones.minion.MinionTasks;
import com.avicagan.bloodandbones.minion.TaskWords;
import com.avicagan.bloodandbones.network.MinionTaskPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.widget.IconButton;
import com.simibubi.create.foundation.gui.widget.Label;
import com.simibubi.create.foundation.gui.widget.ScrollInput;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.gui.AbstractSimiScreen;
import net.createmod.catnip.gui.UIRenderHelper;
import net.createmod.catnip.gui.element.ScreenElement;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A minion's task screen (docs/NEXT.md 1.3), opened by its maker crouching with an empty hand on it awake: every task in
 * the list's order in four groups (fight, tend, fetch, work), each with its fitness as a bar, a percentage and a word
 * (Hopeless, Fair, Able, Good, Born to it) or "Cannot" and why; hovering a row shows each stat it read and where it came
 * from; its task now is marked. A click on a row sets that task at once. Below: at home or with me, for the tasks that
 * allow it; its reach; and "Home here". The rows come from the server ({@link MinionTaskPayload.Open}), which checks every
 * request and sends them again. It is drawn in Create's own schedule frame and cards, with Create's buttons and scroll
 * input, so it reads as one of Create's screens.
 */
public class MinionTaskScreen extends AbstractSimiScreen {
    private static final AllGuiTextures FRAME = AllGuiTextures.SCHEDULE;
    // the frame's dark list area, as Create's schedule screen uses it
    private static final int LIST_LEFT = 16;
    private static final int LIST_TOP = 16;
    private static final int LIST_WIDTH = 220;
    private static final int LIST_HEIGHT = 173;
    private static final int CARD_X = 25;
    private static final int CARD_WIDTH = 195;
    private static final int ROW = 12;
    /** A row it cannot do is taller: its reason goes under its name, in small print. */
    private static final int CANNOT_ROW = 19;
    private static final int GAP = 1;
    private static final int HEADING = 11;
    private static final int PAD = 5;
    // the columns of a row, from its card's left
    private static final int BAR_X = 60;
    private static final int BAR_WIDTH = 44;
    private static final int PERCENT_RIGHT = 132;
    private static final int WORD_X = 136;
    private static final float SMALL = 0.75F;
    // Create's colours: card text, the brass of its strips, and labels on the light frame
    private static final int TEXT = 0xFFF2F2EE;
    private static final int DIM = 0xFF9A9A9A;
    private static final int BRASS = 0xFFBD905A;
    private static final int LABEL = 0x505050;
    /** The bar's colour for each word, Hopeless to Born to it. */
    private static final int[] BAND = {0xFFB5483A, 0xFFCC8A3C, 0xFFC9BE4A, 0xFF84B34E, 0xFF4FB3A6};
    private static final int CANNOT = 0xFFD9654F;
    private static final int WAITING = 0xFFFFB55A;

    private MinionTaskPayload.Open data;
    private final LerpedFloat scroll = LerpedFloat.linear().startWithValue(0);
    /** Each row's place down the list, by task, from the list's top, and its height. */
    private final int[] rowY = new int[MinionTask.values().length];
    private final int[] rowHeight = new int[MinionTask.values().length];
    private final List<int[]> headings = new ArrayList<>();
    private int contentHeight;
    private IconButton atHome;
    private IconButton withMe;
    private IconButton homeHere;
    private ScrollInput reach;
    private Label reachLabel;
    /** A reach scrolled to but not yet sent, and when it last changed. */
    private boolean reachDirty;
    private int reachChangedAt;
    private int ticks;
    /** A row shown as hovered whatever the mouse does (the showcase's picture); -1 for none. */
    private int pinnedHover = -1;

    public MinionTaskScreen(MinionTaskPayload.Open data) {
        super(Component.translatable("bloodandbones.minion.screen.title", data.name()));
        this.data = data;
    }

    /** From the server: open the screen, or bring the open one on the same minion up to date. */
    public static void receive(MinionTaskPayload.Open open) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof MinionTaskScreen screen && screen.data.minion() == open.minion()) {
            screen.update(open);
        } else if (!open.refresh()) {
            mc.setScreen(new MinionTaskScreen(open));
        }
    }

    /** For the showcase: this task's row is shown hovered, its reasons over it (null: the mouse's again). */
    public void pinHover(@Nullable MinionTask task) {
        pinnedHover = task == null ? -1 : task.ordinal();
        if (task != null) {
            // and in view
            scroll.startWithValue(Mth.clamp(rowY[task.ordinal()] - LIST_HEIGHT / 3, 0, maxScroll()));
        }
    }

    /** For the showcase: scrolled to the bottom of the list. */
    public void scrollToEnd() {
        scroll.startWithValue(maxScroll());
    }

    @Override
    protected void init() {
        setWindowSize(FRAME.getWidth(), FRAME.getHeight());
        super.init();
        clearWidgets();
        layout();
        int y = guiTop + FRAME.getHeight() - 30;
        atHome = new IconButton(guiLeft + 21, y, item(new ItemStack(Items.RED_BED)));
        atHome.withCallback(() -> request(data.task(), MinionTask.Anchor.HOME.ordinal(), reachSet(), false));
        withMe = new IconButton(guiLeft + 39, y, item(new ItemStack(Items.PLAYER_HEAD)));
        withMe.withCallback(() -> request(data.task(), MinionTask.Anchor.MAKER.ordinal(), reachSet(), false));
        reachLabel = new Label(guiLeft + 100, y + 5, Component.empty()).withShadow();
        reach = new ScrollInput(guiLeft + 96, y + 1, 30, 16);
        reach.titled(Component.translatable("bloodandbones.minion.screen.reach").copy())
                .format(n -> Component.literal(Integer.toString(n)))
                .calling(n -> {
                    reachDirty = true;
                    reachChangedAt = ticks;
                })
                .writingTo(reachLabel);
        homeHere = new IconButton(guiLeft + 134, y, AllIcons.I_TARGET);
        homeHere.withCallback(() -> request(data.task(), data.anchor(), reachSet(), true));
        homeHere.setToolTip(Component.translatable("bloodandbones.minion.screen.home_here"));
        homeHere.getToolTip().add(Component.translatable("bloodandbones.minion.screen.home_here.hint").withStyle(ChatFormatting.GRAY));
        IconButton confirm = new IconButton(guiLeft + FRAME.getWidth() - 42, y, AllIcons.I_CONFIRM);
        confirm.withCallback(this::onClose);
        addRenderableWidgets(atHome, withMe, reachLabel, reach, homeHere, confirm);
        refreshWidgets();
        // its task now in view
        scroll.startWithValue(Mth.clamp(rowY[data.task()] - LIST_HEIGHT / 3, 0, maxScroll()));
    }

    private static ScreenElement item(ItemStack stack) {
        return (graphics, x, y) -> graphics.renderItem(stack, x, y);
    }

    /**
     * Where each row and heading goes: Idle first, then the four groups, each under its heading, the tasks in the list's
     * order within them. A task's group is its file's kind, which a datapack may change, so the server sends it.
     */
    private void layout() {
        headings.clear();
        int y = PAD;
        for (MinionTask.Kind kind : MinionTask.Kind.values()) {
            boolean first = true;
            for (MinionTaskPayload.Row row : data.rows()) {
                if (row.kind() != kind.ordinal()) {
                    continue;
                }
                if (first && kind != MinionTask.Kind.NONE) {
                    y += 2;
                    headings.add(new int[]{kind.ordinal(), y});
                    y += HEADING;
                }
                first = false;
                rowY[row.task()] = y;
                rowHeight[row.task()] = row.can() ? ROW : CANNOT_ROW;
                y += rowHeight[row.task()] + GAP;
            }
        }
        contentHeight = y + PAD;
    }

    private int maxScroll() {
        return Math.max(0, contentHeight - LIST_HEIGHT);
    }

    /** New rows from the server: its task, anchor and reach now, and the numbers at that anchor. */
    private void update(MinionTaskPayload.Open open) {
        data = open;
        layout();
        refreshWidgets();
    }

    private MinionTaskPayload.Row current() {
        return data.rows().get(data.task());
    }

    /** The reach its maker set, as the scroll input has it: 0 when it is the task's own. */
    private int reachSet() {
        MinionTaskPayload.Row row = current();
        return reach == null || reach.getState() == row.reach() ? 0 : reach.getState();
    }

    private void refreshWidgets() {
        MinionTaskPayload.Row row = current();
        boolean home = data.anchor() == MinionTask.Anchor.HOME.ordinal();
        atHome.green = home;
        withMe.green = !home;
        withMe.active = row.allows(MinionTask.Anchor.MAKER.ordinal());
        atHome.active = row.allows(MinionTask.Anchor.HOME.ordinal());
        atHome.setToolTip(Component.translatable("bloodandbones.minion.screen.at_home"));
        atHome.getToolTip().add(Component.translatable("bloodandbones.minion.screen.at_home.hint").withStyle(ChatFormatting.GRAY));
        withMe.setToolTip(Component.translatable("bloodandbones.minion.screen.with_me"));
        withMe.getToolTip().add(Component.translatable(withMe.active ? "bloodandbones.minion.screen.with_me.hint" : "bloodandbones.minion.screen.with_me.never",
                TaskWords.name(MinionTask.values()[data.task()])).withStyle(ChatFormatting.GRAY));
        if (!reachDirty) {
            reach.withRange(MinionTasks.LEAST_REACH, row.maxReach() + 1);
            reach.addHint(Component.translatable("bloodandbones.minion.screen.reach.hint", MinionTasks.LEAST_REACH, row.maxReach(), row.reach()));
            reach.setState(data.reach() <= 0 ? row.reach() : data.reach());
        }
    }

    /** A request to the server, which checks it and sends the rows again. */
    private void request(int task, int anchor, int reachSet, boolean homeHere) {
        reachDirty = false;
        PacketDistributor.sendToServer(new MinionTaskPayload.Set(data.minion(), task, anchor, reachSet, homeHere));
    }

    @Override
    public void tick() {
        super.tick();
        ticks++;
        scroll.tickChaser();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !(mc.level.getEntity(data.minion()) instanceof MinionEntity minion) || !minion.isAlive()
                || minion.poweredDown() || mc.player.distanceToSqr(minion) > (MinionTasks.SCREEN_REACH + 1.0) * (MinionTasks.SCREEN_REACH + 1.0)) {
            onClose();
            return;
        }
        // a reach scrolled to is sent once it has been left alone half a second
        if (reachDirty && ticks - reachChangedAt >= 10) {
            request(data.task(), data.anchor(), reachSet(), false);
        }
    }

    @Override
    public void removed() {
        if (reachDirty) {
            request(data.task(), data.anchor(), reachSet(), false);
        }
        super.removed();
    }

    // ---- drawing

    @Override
    protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        FRAME.render(graphics, guiLeft, guiTop);
        int centre = guiLeft + (FRAME.getWidth() - 8) / 2;
        graphics.drawString(font, title, centre - font.width(title) / 2, guiTop + 4, LABEL, false);
        graphics.drawString(font, Component.translatable("bloodandbones.minion.screen.reach"), guiLeft + 62, guiTop + FRAME.getHeight() - 25, LABEL, false);
        // the reach's field, as Create's inputs sit in a dark well
        int fy = guiTop + FRAME.getHeight() - 29;
        graphics.fill(guiLeft + 96, fy, guiLeft + 126, fy + 16, 0xFF373737);
        graphics.fill(guiLeft + 97, fy + 1, guiLeft + 126, fy + 16, 0xFFFFFFFF);
        graphics.fill(guiLeft + 97, fy + 1, guiLeft + 125, fy + 15, 0xFF8B8B8B);
        graphics.drawString(font, Component.translatable("bloodandbones.minion.screen.home_here"), guiLeft + 155, guiTop + FRAME.getHeight() - 25, LABEL, false);

        float offset = -scroll.getValue(partialTicks);
        graphics.enableScissor(guiLeft + LIST_LEFT, guiTop + LIST_TOP, guiLeft + LIST_LEFT + LIST_WIDTH, guiTop + LIST_TOP + LIST_HEIGHT);
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(0, offset, 0);
        for (int[] heading : headings) {
            MinionTask.Kind kind = MinionTask.Kind.values()[heading[0]];
            int y = guiTop + LIST_TOP + heading[1];
            Component name = Component.translatable("bloodandbones.minion.screen.group." + kind.key());
            graphics.drawString(font, name, guiLeft + CARD_X + 2, y + 2, BRASS, false);
            int after = guiLeft + CARD_X + 6 + font.width(name);
            graphics.fill(after, y + 5, guiLeft + CARD_X + CARD_WIDTH, y + 6, 0xFF6F5A3E);
        }
        int hovered = hovered(mouseX, mouseY);
        for (MinionTaskPayload.Row row : data.rows()) {
            renderRow(graphics, row, guiLeft + CARD_X, guiTop + LIST_TOP + rowY[row.task()], rowHeight[row.task()], row.task() == hovered);
        }
        pose.popPose();
        graphics.disableScissor();
        // the current task's pointer, kept at the edge when its row is scrolled out of view
        float expected = guiTop + LIST_TOP + rowY[data.task()] + offset - 2;
        float shown = Mth.clamp(expected, guiTop + LIST_TOP + 2, guiTop + LIST_TOP + LIST_HEIGHT - 18);
        pose.pushPose();
        pose.translate(0, shown, 0);
        (expected == shown ? AllGuiTextures.SCHEDULE_POINTER : AllGuiTextures.SCHEDULE_POINTER_OFFSCREEN).render(graphics, guiLeft, 0);
        pose.popPose();
        // the list fades out at its top and bottom, as the schedule's does
        graphics.fillGradient(guiLeft + LIST_LEFT, guiTop + LIST_TOP, guiLeft + LIST_LEFT + LIST_WIDTH, guiTop + LIST_TOP + 8, 200, 0x77000000, 0x00000000);
        graphics.fillGradient(guiLeft + LIST_LEFT, guiTop + LIST_TOP + LIST_HEIGHT - 8, guiLeft + LIST_LEFT + LIST_WIDTH, guiTop + LIST_TOP + LIST_HEIGHT, 200,
                0x00000000, 0x77000000);
    }

    /** One task's card: its name, then its bar, percentage and word, or "Cannot" with why under it. */
    private void renderRow(GuiGraphics graphics, MinionTaskPayload.Row row, int x, int y, int h, boolean hovered) {
        boolean current = row.task() == data.task();
        AllGuiTextures light = AllGuiTextures.SCHEDULE_CARD_LIGHT;
        AllGuiTextures medium = AllGuiTextures.SCHEDULE_CARD_MEDIUM;
        AllGuiTextures dark = AllGuiTextures.SCHEDULE_CARD_DARK;
        // Create's schedule card: a light rim, a dark line inside it, and the card within
        UIRenderHelper.drawStretched(graphics, x, y + 1, CARD_WIDTH, h - 2, 0, hovered && row.can() ? AllGuiTextures.SCHEDULE_STRIP_DARK : light);
        UIRenderHelper.drawStretched(graphics, x + 1, y, CARD_WIDTH - 2, h, 0, hovered && row.can() ? AllGuiTextures.SCHEDULE_STRIP_DARK : light);
        UIRenderHelper.drawStretched(graphics, x + 1, y + 1, CARD_WIDTH - 2, h - 2, 0, dark);
        UIRenderHelper.drawStretched(graphics, x + 2, y + 2, CARD_WIDTH - 4, h - 4, 0, !row.can() ? dark : current ? light : medium);
        if (current) {
            // the brass strip down its side, as the schedule marks its steps
            UIRenderHelper.drawStretched(graphics, x + 2, y + 2, 2, h - 4, 0, AllGuiTextures.SCHEDULE_STRIP_LIGHT);
        }
        MinionTask task = MinionTask.values()[row.task()];
        Component name = TaskWords.name(task);
        int ty = y + 2;
        graphics.drawString(font, name, x + 6, ty, row.can() ? TEXT : DIM, row.can());
        if (!task.rated()) {
            graphics.drawString(font, "-", x + PERCENT_RIGHT - font.width("-"), ty, DIM, false);
            return;
        }
        if (!row.can()) {
            Component cannot = Component.translatable("bloodandbones.minion.screen.cannot");
            graphics.drawString(font, cannot, x + WORD_X, ty, CANNOT, false);
            // why, in small print under its name
            Component why = row.lines().size() > 1 ? row.lines().get(1) : Component.empty();
            PoseStack pose = graphics.pose();
            pose.pushPose();
            pose.translate(x + 6, y + 11, 0);
            pose.scale(SMALL, SMALL, 1.0F);
            graphics.drawString(font, font.substrByWidth(why, Math.round((CARD_WIDTH - 12) / SMALL)).getString(), 0, 0, DIM, false);
            pose.popPose();
            return;
        }
        int band = TaskWords.band(row.fitness());
        // the bar: full at 200%
        int filled = Math.round(Mth.clamp(row.fitness() / 2.0F, 0.0F, 1.0F) * BAR_WIDTH);
        graphics.fill(x + BAR_X, y + 4, x + BAR_X + BAR_WIDTH, y + 8, 0xFF2A2A2A);
        graphics.fill(x + BAR_X, y + 4, x + BAR_X + filled, y + 8, BAND[band]);
        // today's mark, 100%
        graphics.fill(x + BAR_X + BAR_WIDTH / 2, y + 3, x + BAR_X + BAR_WIDTH / 2 + 1, y + 9, 0x99FFFFFF);
        String percent = TaskWords.percent(row.fitness());
        graphics.drawString(font, percent, x + PERCENT_RIGHT - font.width(percent), ty, TEXT, true);
        graphics.drawString(font, Component.translatable(TaskWords.wordKey(row.fitness())), x + WORD_X, ty, BAND[band], true);
        if (row.waiting()) {
            // it would wait for something: a small amber mark at the card's end (the reasons say for what)
            graphics.fill(x + CARD_WIDTH - 6, y + 4, x + CARD_WIDTH - 3, y + 8, WAITING);
        }
    }

    /** The row under the mouse, or the pinned one; -1 for none. */
    private int hovered(int mouseX, int mouseY) {
        if (pinnedHover >= 0) {
            return pinnedHover;
        }
        if (mouseX < guiLeft + CARD_X || mouseX >= guiLeft + CARD_X + CARD_WIDTH || mouseY < guiTop + LIST_TOP || mouseY >= guiTop + LIST_TOP + LIST_HEIGHT) {
            return -1;
        }
        float y = mouseY - guiTop - LIST_TOP + scroll.getValue(0);
        for (MinionTask task : MinionTask.values()) {
            if (y >= rowY[task.ordinal()] && y < rowY[task.ordinal()] + rowHeight[task.ordinal()]) {
                return task.ordinal();
            }
        }
        return -1;
    }

    @Override
    protected void renderWindowForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        int hovered = hovered(mouseX, mouseY);
        if (hovered < 0) {
            return;
        }
        MinionTaskPayload.Row row = data.rows().get(hovered);
        List<Component> lines = new ArrayList<>(row.lines());
        if (row.can() && hovered != data.task()) {
            lines.add(Component.translatable("bloodandbones.minion.screen.click").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        } else if (hovered == data.task()) {
            lines.add(Component.translatable("bloodandbones.minion.screen.now").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        }
        int x = pinnedHover >= 0 ? guiLeft + CARD_X + 60 : mouseX;
        int y = pinnedHover >= 0 ? guiTop + LIST_TOP + rowY[hovered] - Math.round(scroll.getValue(partialTicks)) + rowHeight[hovered] : mouseY;
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 400);
        graphics.renderTooltip(font, lines, Optional.empty(), x, y);
        graphics.pose().popPose();
    }

    // ---- input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int hovered = pinnedHover >= 0 ? -1 : hovered((int) mouseX, (int) mouseY);
        if (button == 0 && hovered >= 0) {
            MinionTaskPayload.Row row = data.rows().get(hovered);
            if (row.can() && hovered != data.task()) {
                playUiSound(SoundEvents.UI_BUTTON_CLICK.value());
                // at the anchor it works at now if the task allows it, else the first the task allows (home, unless a datapack
                // took that away), reaching as far as the task's own
                int anchor = row.allows(data.anchor()) ? data.anchor()
                        : row.allows(MinionTask.Anchor.HOME.ordinal()) ? MinionTask.Anchor.HOME.ordinal() : MinionTask.Anchor.MAKER.ordinal();
                request(hovered, anchor, 0, false);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private static void playUiSound(net.minecraft.sounds.SoundEvent sound) {
        Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(sound, 1.0F));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= guiLeft + LIST_LEFT && mouseX < guiLeft + LIST_LEFT + LIST_WIDTH && mouseY >= guiTop + LIST_TOP
                && mouseY < guiTop + LIST_TOP + LIST_HEIGHT) {
            float target = Mth.clamp(scroll.getChaseTarget() - (float) scrollY * 12.0F, 0, maxScroll());
            scroll.chase(target, 0.7F, LerpedFloat.Chaser.EXP);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    /** The minion this screen is open on. */
    @Nullable
    public MinionEntity minion() {
        return Minecraft.getInstance().level != null && Minecraft.getInstance().level.getEntity(data.minion()) instanceof MinionEntity minion ? minion : null;
    }
}
