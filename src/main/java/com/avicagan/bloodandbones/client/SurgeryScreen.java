package com.avicagan.bloodandbones.client;

import com.avicagan.bloodandbones.body.Body;
import com.avicagan.bloodandbones.body.BodyEffects;
import com.avicagan.bloodandbones.body.BodyPart;
import com.avicagan.bloodandbones.body.ImplantItem;
import com.avicagan.bloodandbones.body.Surgery;
import com.avicagan.bloodandbones.body.SurgeryTableBlockEntity;
import com.avicagan.bloodandbones.config.BBClientConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.widget.IconButton;
import net.createmod.catnip.gui.AbstractSimiScreen;
import net.createmod.catnip.gui.UIRenderHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Surgery, the brief's "select augments in a new UI". On yourself it is the outside view, you looking down at yourself on
 * the table from a little above, with two panels at the sides of the screen and nothing over the body between them:
 * <ul>
 *     <li>on the left, the body: a paper doll of the patient with each slot drawn as it is (their own, a stump and what
 *     fitting it costs, or the implant in it, dimmed if it is not working), and a green mark on each slot where
 *     something you carry fits; under it, the surgeon by the table, how fit it is and what a stump it cuts will cost,
 *     before any cut (docs/NEXT.md 1.5);</li>
 *     <li>on the right, the slot picked on the doll: what it is now, what lies on the table, the blood you carry, a row
 *     of every implant, prosthetic, limb and module you carry that fits a slot of this body, and a card for each thing
 *     that can be done to the slot, with nothing (an implant unclipped), with what is on the table, or with any of what
 *     you carry ({@link Surgery#options}); a card that cannot be done says why (no surgeon, not enough blood).</li>
 * </ul>
 * A click on a card sends the choice ({@link Surgery.ActionPayload}), which the server checks again. It is drawn as
 * Create's value boards are, dark with a brass frame, with Create's schedule cards and buttons. It closes when the
 * patient gets off the table, or when you walk away from it.
 */
public class SurgeryScreen extends AbstractSimiScreen {
    private final BlockPos table;
    private final int patientId;

    /** Ticks it waits for the table's seat to reach this client before closing for want of a patient on it. */
    private static final int SETTLING = 20;
    private int age;
    /** Whoever it is for has been seen on the table since it opened. */
    private boolean seen;

    // the two panels, at the sides; between them the body is left clear
    private static final int MARGIN = 6;
    private static final int PANEL = 140;
    /** The least left clear between the panels. */
    private static final int CLEAR = 110;
    private static final int PAD = 6;
    private static final int CARD = 22;
    private static final int ICON = 18;
    private static final float SMALL = 0.75F;
    // Create's colours: text on its cards, dim text, the brass of its strips and frames
    private static final int TEXT = 0xFFF2F2EE;
    private static final int DIM = 0xFF9A9A9A;
    private static final int BRASS = 0xFFBD905A;
    private static final int BRASS_LIGHT = 0xFFFFD27A;
    private static final int RULE = 0xFF6F5A3E;
    private static final int BOARD = 0xD0140E0C;
    private static final int PROBLEM = 0xFFD9654F;
    private static final int FITS = 0xFF7FD35A;

    /** The slot picked on the doll, whose options the right panel shows. */
    private BodyPart selected;
    /** A slot shown as hovered whatever the mouse does (the showcase's picture); null for the mouse's. */
    @Nullable
    private BodyPart pinnedHover;
    private float scroll;
    private IconButton done;

    public SurgeryScreen(BlockPos table, int patient) {
        super(Component.translatable("bloodandbones.surgery.title"));
        this.table = table;
        this.patientId = patient;
    }

    @Nullable
    private LivingEntity patient() {
        return Minecraft.getInstance().level != null && Minecraft.getInstance().level.getEntity(patientId) instanceof LivingEntity living ? living : null;
    }

    /** The camera before surgery, the head's tilt and whether the HUD was hidden, put back when the screen closes. */
    @Nullable
    private net.minecraft.client.CameraType before;
    private float pitchBefore;
    @Nullable
    private Boolean hudBefore;

    /**
     * On yourself, the brief's outside view: you look at yourself from a little above (the front camera with the
     * head tipped back, so the camera sits above and looks down; see BodyRendering's camera distance). The HUD goes while
     * the screen is up, on yourself or on someone else, so nothing but the panels stands in front of the body.
     */
    private void outsideView() {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (hudBefore == null) {
            hudBefore = mc.options.hideGui;
            mc.options.hideGui = true;
        }
        if (before == null && player != null && player.getId() == patientId) {
            before = mc.options.getCameraType();
            pitchBefore = player.getXRot();
            mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
            player.setXRot(-55.0F);
            player.xRotO = -55.0F;
        }
    }

    @Override
    public void removed() {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (before != null) {
            mc.options.setCameraType(before);
            if (player != null) {
                player.setXRot(pitchBefore);
                player.xRotO = pitchBefore;
            }
            before = null;
        }
        if (hudBefore != null) {
            mc.options.hideGui = hudBefore;
            hudBefore = null;
        }
        super.removed();
    }

    /** Whether the local player is on the table with this screen up, for the camera distance. */
    public static boolean operatingOnSelf() {
        return Minecraft.getInstance().screen instanceof SurgeryScreen screen && screen.before != null;
    }

    /** For the showcase: this slot is picked, as if clicked on the doll. */
    public void select(BodyPart part) {
        selected = part;
        scroll = 0;
    }

    /** For the showcase: this slot is shown hovered, its tooltip over it (null: the mouse's again). */
    public void pinHover(@Nullable BodyPart part) {
        pinnedHover = part;
    }

    @Override
    protected void init() {
        setWindowSize(width, height);
        super.init();
        clearWidgets();
        outsideView();
        done = new IconButton(right() + panelWidth() - PAD - 18, height - MARGIN - PAD - 18, AllIcons.I_CONFIRM);
        done.withCallback(this::onClose);
        done.setToolTip(Component.translatable("bloodandbones.surgery.done"));
        addRenderableWidget(done);
        if (selected == null) {
            selected = firstChoice();
        }
    }

    // ---- where things go

    private int panelWidth() {
        return Math.max(96, Math.min(PANEL, (width - 2 * MARGIN - CLEAR) / 2));
    }

    private int right() {
        return width - MARGIN - panelWidth();
    }

    /** The doll's scale: pixels to a pixel of the player's skin, as big as the left panel allows. */
    private int unit() {
        return Mth.clamp((height - 2 * MARGIN - 110) / 32, 2, 4);
    }

    private int dollLeft() {
        return MARGIN + (panelWidth() - 16 * unit()) / 2;
    }

    private int dollTop() {
        return MARGIN + 24;
    }

    /**
     * Where a slot is on the doll, in skin pixels from its top left (x, y, width, height): the patient faces you, so their
     * right side is on your left. The head holds the eyes, the torso the lungs, heart and stomach, top to bottom.
     */
    private static float[] slot(BodyPart part) {
        return switch (part) {
            case RIGHT_ARM -> new float[]{0, 8, 4, 12};
            case LEFT_ARM -> new float[]{12, 8, 4, 12};
            case RIGHT_LEG -> new float[]{4, 20, 4, 12};
            case LEFT_LEG -> new float[]{8, 20, 4, 12};
            case RIGHT_EYE -> new float[]{4.5F, 2.5F, 3, 2.5F};
            case LEFT_EYE -> new float[]{8.5F, 2.5F, 3, 2.5F};
            case LUNGS -> new float[]{5, 8.8F, 6, 3.2F};
            case HEART -> new float[]{6, 12.6F, 4, 3.2F};
            case STOMACH -> new float[]{5.5F, 16.4F, 5, 3.2F};
        };
    }

    /** A slot's place on the screen: x, y, width, height. */
    private int[] rect(BodyPart part) {
        float[] s = slot(part);
        int u = unit();
        return new int[]{dollLeft() + Math.round(s[0] * u), dollTop() + Math.round(s[1] * u), Math.round(s[2] * u), Math.round(s[3] * u)};
    }

    /** Where a click picks a slot: an eye's half of the head, an organ's band of the torso, a limb's own box. */
    @Nullable
    private BodyPart slotAt(double mx, double my) {
        int u = unit();
        double x = (mx - dollLeft()) / u;
        double y = (my - dollTop()) / u;
        if (y >= 0 && y < 8 && x >= 4 && x < 12) {
            return x < 8 ? BodyPart.RIGHT_EYE : BodyPart.LEFT_EYE;
        }
        if (y >= 8 && y < 20 && x >= 4 && x < 12) {
            return y < 12 ? BodyPart.LUNGS : y < 16 ? BodyPart.HEART : BodyPart.STOMACH;
        }
        for (BodyPart limb : new BodyPart[]{BodyPart.RIGHT_ARM, BodyPart.LEFT_ARM, BodyPart.RIGHT_LEG, BodyPart.LEFT_LEG}) {
            float[] s = slot(limb);
            if (x >= s[0] && x < s[0] + s[2] && y >= s[1] && y < s[1] + s[3]) {
                return limb;
            }
        }
        return null;
    }

    // ---- what the table and the surgeon offer

    private ItemStack onTable() {
        return Minecraft.getInstance().level != null && Minecraft.getInstance().level.getBlockEntity(table) instanceof SurgeryTableBlockEntity be
                ? be.item() : ItemStack.EMPTY;
    }

    private List<Surgery.Option> options(Body body, BodyPart part) {
        return Surgery.options(body, part, onTable(), Minecraft.getInstance().player);
    }

    /** Whether something the surgeon carries can be used on the slot (the doll's green mark). */
    private boolean offered(Body body, BodyPart part) {
        for (Surgery.Option option : options(body, part)) {
            if (option.source() >= 0) {
                return true;
            }
        }
        return false;
    }

    /** The slot picked when the screen opens: the first where something carried fits, else the first with anything. */
    private BodyPart firstChoice() {
        LivingEntity patient = patient();
        if (patient == null) {
            return BodyPart.LEFT_ARM;
        }
        Body body = BodyEffects.body(patient);
        for (BodyPart part : BodyPart.values()) {
            if (offered(body, part)) {
                return part;
            }
        }
        for (BodyPart part : BodyPart.values()) {
            if (!options(body, part).isEmpty()) {
                return part;
            }
        }
        return BodyPart.LEFT_ARM;
    }

    /**
     * Every implant, prosthetic, limb and module the surgeon carries (inventory, armour and off-hand) that fits some slot of
     * this body, each kind once, with the slot a click on it picks: an open one first.
     */
    private List<Carried> carried(Body body) {
        List<Carried> out = new ArrayList<>();
        Player operator = Minecraft.getInstance().player;
        if (operator == null) {
            return out;
        }
        var inventory = operator.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!(stack.getItem() instanceof ImplantItem || stack.getItem() instanceof com.avicagan.bloodandbones.body.SeveredLimbItem
                    || stack.getItem() instanceof com.avicagan.bloodandbones.cyber.ModuleItem)) {
                continue;
            }
            BodyPart first = null;
            for (BodyPart part : BodyPart.values()) {
                if (Surgery.action(body, stack, part) != Surgery.Action.NONE
                        && (first == null || body.state(first) != Body.State.MISSING && body.state(part) == Body.State.MISSING)) {
                    first = part;
                }
            }
            if (first == null || out.stream().anyMatch(c -> ItemStack.isSameItemSameComponents(c.stack, stack))) {
                continue;
            }
            out.add(new Carried(stack, first));
        }
        return out;
    }

    private record Carried(ItemStack stack, BodyPart part) {
    }

    // ---- ticking

    @Override
    public void tick() {
        super.tick();
        Player surgeon = Minecraft.getInstance().player;
        LivingEntity patient = patient();
        boolean may = surgeon != null && patient != null && Surgery.mayOperate(surgeon, patient, table);
        seen |= may;
        // the server opens it as it lays them on the table, and the seat they ride may reach this client a tick after it
        if (!may && (seen || ++age > SETTLING)) {
            onClose();
        }
    }

    // ---- drawing

    @Override
    protected void renderMenuBackground(GuiGraphics graphics) {
        // nothing over the world: the body is what the screen is about
    }

    @Override
    protected void renderWindowBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
    }

    @Override
    protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        LivingEntity patient = patient();
        if (patient == null) {
            return;
        }
        Body body = BodyEffects.body(patient);
        int pw = panelWidth();
        board(graphics, MARGIN, MARGIN, pw, height - 2 * MARGIN);
        board(graphics, right(), MARGIN, pw, height - 2 * MARGIN);
        renderBody(graphics, patient, body, mouseX, mouseY);
        renderChoices(graphics, patient, body, mouseX, mouseY);
    }

    /** A panel as Create's value boards are drawn: dark, in a brass frame. */
    private static void board(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + h, BOARD);
        AllGuiTextures.BRASS_FRAME_TL.render(graphics, x, y);
        AllGuiTextures.BRASS_FRAME_TR.render(graphics, x + w - 4, y);
        AllGuiTextures.BRASS_FRAME_BL.render(graphics, x, y + h - 4);
        AllGuiTextures.BRASS_FRAME_BR.render(graphics, x + w - 4, y + h - 4);
        UIRenderHelper.drawStretched(graphics, x, y + 4, 3, h - 8, 0, AllGuiTextures.BRASS_FRAME_LEFT);
        UIRenderHelper.drawStretched(graphics, x + w - 3, y + 4, 3, h - 8, 0, AllGuiTextures.BRASS_FRAME_RIGHT);
        UIRenderHelper.drawCropped(graphics, x + 4, y, w - 8, 3, 0, AllGuiTextures.BRASS_FRAME_TOP);
        UIRenderHelper.drawCropped(graphics, x + 4, y + h - 3, w - 8, 3, 0, AllGuiTextures.BRASS_FRAME_BOTTOM);
    }

    /** A heading in brass, with a rule under it to the panel's edge. */
    private void heading(GuiGraphics graphics, Component text, int x, int y, int w) {
        String shown = font.substrByWidth(text, w).getString();
        graphics.drawString(font, shown, x, y, BRASS, false);
        int after = x + font.width(shown) + 4;
        if (after < x + w) {
            graphics.fill(after, y + 4, x + w, y + 5, RULE);
        }
    }

    /** Text wrapped to a width, at most so many lines; where the next line would go. */
    private int wrapped(GuiGraphics graphics, Component text, int x, int y, int w, int color, int lines) {
        List<FormattedCharSequence> split = font.split(text, w);
        for (int i = 0; i < Math.min(lines, split.size()); i++) {
            graphics.drawString(font, split.get(i), x, y, color, false);
            y += 10;
        }
        return y;
    }

    /** Small print, wrapped; where the next line would go. */
    private int small(GuiGraphics graphics, Component text, int x, int y, int w, int color, int lines) {
        List<FormattedCharSequence> split = font.split(text, Math.round(w / SMALL));
        PoseStack pose = graphics.pose();
        for (int i = 0; i < Math.min(lines, split.size()); i++) {
            pose.pushPose();
            pose.translate(x, y, 0);
            pose.scale(SMALL, SMALL, 1.0F);
            graphics.drawString(font, split.get(i), 0, 0, color, false);
            pose.popPose();
            y += 7;
        }
        return y;
    }

    // -- the body

    private void renderBody(GuiGraphics graphics, LivingEntity patient, Body body, int mouseX, int mouseY) {
        int x = MARGIN + PAD;
        int w = panelWidth() - 2 * PAD;
        Component title = patient == Minecraft.getInstance().player ? this.title
                : Component.translatable("bloodandbones.surgery.title_other", patient.getName());
        heading(graphics, title, x, MARGIN + PAD, w);
        BodyPart hovered = hoveredSlot(mouseX, mouseY);
        doll(graphics, patient, body, hovered);
        // the slot under the mouse, or the one picked: its name and what it is now
        BodyPart shown = hovered != null ? hovered : selected;
        int y = dollTop() + 32 * unit() + 5;
        graphics.drawString(font, Component.translatable(shown.translationKey()), x, y, TEXT, false);
        y = small(graphics, state(body, shown, patient), x, y + 10, w, stateColor(body, shown, patient), 2);
        // a key to the green marks, while there are any
        boolean any = false;
        for (BodyPart part : BodyPart.values()) {
            any |= offered(body, part);
        }
        if (any) {
            graphics.fill(x, y + 3, x + 3, y + 6, FITS);
            small(graphics, Component.translatable("bloodandbones.surgery.fits_key"), x + 6, y + 2, w - 6, DIM, 1);
        }
        if (patient instanceof Player) {
            surgeonLines(graphics, patient, x, w);
        }
    }

    /**
     * The paper doll: head, torso, arms and legs as a player skin lays them out, each slot filled as it is. Their own is
     * skin (an organ its own colour); gone is a dark hole with the stump at its root, torn and dripping where it is ragged,
     * a drop for each bucket it costs to fit (clean in bloodless mode: a grey socket, grey studs); an implant is its own
     * colour (brass, flesh, iron) with its item on it, darkened if it is not working. The picked slot has a brass frame,
     * the hovered a white one, and a green mark where something carried fits.
     */
    private void doll(GuiGraphics graphics, LivingEntity patient, Body body, @Nullable BodyPart hovered) {
        int u = unit();
        int x = dollLeft();
        int y = dollTop();
        boolean clean = BBClientConfig.bloodless();
        // the head and the torso behind the organs and eyes; the neck line
        outlined(graphics, x + 4 * u, y, 8 * u, 8 * u, SKIN, SKIN_EDGE);
        outlined(graphics, x + 4 * u, y + 8 * u, 8 * u, 12 * u, SHIRT, SHIRT_EDGE);
        // a mouth, so the head reads as one
        graphics.fill(x + 6 * u, y + 6 * u, x + 10 * u, y + 6 * u + Math.max(1, u / 2), SKIN_EDGE);
        for (BodyPart part : BodyPart.values()) {
            int[] r = rect(part);
            switch (body.state(part)) {
                case NATURAL -> natural(graphics, part, r, clean);
                case MISSING -> missing(graphics, part, r, body.raggedBuckets(part), clean);
                case IMPLANT -> implant(graphics, part, r, body.implant(part), body.works(part, patient), clean);
            }
            if (offered(body, part)) {
                graphics.fill(r[0] + r[2] - 3, r[1] - 1, r[0] + r[2] + 1, r[1] + 3, 0xFF1C3314);
                graphics.fill(r[0] + r[2] - 2, r[1], r[0] + r[2], r[1] + 2, FITS);
            }
        }
        if (hovered != null && hovered != selected) {
            frame(graphics, rect(hovered), 0xFFFFFFFF);
        }
        frame(graphics, rect(selected), BRASS_LIGHT);
    }

    private static final int SKIN = 0xFFC8906A;
    private static final int SKIN_EDGE = 0xFF7A5238;
    private static final int SHIRT = 0xFF3C6E78;
    private static final int SHIRT_EDGE = 0xFF22434A;

    private static void outlined(GuiGraphics graphics, int x, int y, int w, int h, int fill, int edge) {
        graphics.fill(x, y, x + w, y + h, edge);
        graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
    }

    private static void frame(GuiGraphics graphics, int[] r, int color) {
        graphics.renderOutline(r[0] - 1, r[1] - 1, r[2] + 2, r[3] + 2, color);
    }

    /** An organ's own colour, or in bloodless mode the machine part bloodless mode calls it (a pump, bellows, a hopper). */
    private static int organColor(BodyPart part, boolean clean) {
        return switch (part) {
            case HEART -> clean ? 0xFF6F7F96 : 0xFF9E1C24;
            case LUNGS -> clean ? 0xFF9FB0BC : 0xFFD58A8E;
            case STOMACH -> clean ? 0xFFA89A7C : 0xFFC7937A;
            default -> SKIN;
        };
    }

    private static boolean organ(BodyPart part) {
        return part == BodyPart.HEART || part == BodyPart.LUNGS || part == BodyPart.STOMACH;
    }

    private static boolean eye(BodyPart part) {
        return part.kind() == BodyPart.Kind.EYE;
    }

    private static void natural(GuiGraphics graphics, BodyPart part, int[] r, boolean clean) {
        if (eye(part)) {
            graphics.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], 0xFFF4F4F4);
            int pupil = Math.max(1, r[2] / 2);
            graphics.fill(r[0] + r[2] - pupil, r[1], r[0] + r[2], r[1] + r[3], 0xFF3A2A5A);
        } else if (organ(part)) {
            outlined(graphics, r[0], r[1], r[2], r[3], organColor(part, clean), 0xFF3A1A1A);
        } else {
            // a sleeve on an arm, a trouser leg on a leg, skin at the end
            boolean arm = part.kind() == BodyPart.Kind.ARM;
            outlined(graphics, r[0], r[1], r[2], r[3], arm ? SKIN : 0xFF3A3A8C, arm ? SKIN_EDGE : 0xFF23234E);
            if (arm) {
                graphics.fill(r[0] + 1, r[1] + 1, r[0] + r[2] - 1, r[1] + r[3] / 3, SHIRT);
            } else {
                graphics.fill(r[0] + 1, r[1] + r[3] * 5 / 6, r[0] + r[2] - 1, r[1] + r[3] - 1, 0xFF5A5A5A);
            }
        }
    }

    /**
     * Gone: a dark hole, and for a limb its stump at the root, with a raw end (clean in bloodless mode). Ragged, the end is
     * torn and hangs in strips, with a drop under it for each bucket fitting there costs.
     */
    private static void missing(GuiGraphics graphics, BodyPart part, int[] r, int buckets, boolean clean) {
        int hole = clean ? 0xFF2A2D33 : 0xFF1C0C0C;
        int rim = clean ? 0xFF5A5F68 : 0xFF5A1414;
        int raw = clean ? 0xFF8A9099 : 0xFFB0262C;
        graphics.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], rim);
        graphics.fill(r[0] + 1, r[1] + 1, r[0] + r[2] - 1, r[1] + r[3] - 1, hole);
        if (eye(part) || organ(part)) {
            return;
        }
        boolean arm = part.kind() == BodyPart.Kind.ARM;
        int stub = Math.max(2, r[3] / 5);
        outlined(graphics, r[0], r[1], r[2], stub + 1, arm ? SHIRT : 0xFF3A3A8C, arm ? SHIRT_EDGE : 0xFF23234E);
        int end = r[1] + stub;
        graphics.fill(r[0], end, r[0] + r[2], end + 1, raw);
        if (buckets <= 0) {
            return;
        }
        // torn strips hanging from the end, longer the dearer the stump
        for (int i = 0; i < r[2]; i += 2) {
            int hang = 1 + (i * 7 + buckets * 3) % (2 + buckets);
            graphics.fill(r[0] + i, end + 1, r[0] + Math.min(r[2], i + 1), end + 1 + hang, raw);
        }
        // a drop for each bucket (in bloodless mode a stud)
        int drop = clean ? 0xFF9AA0A8 : 0xFFD8202A;
        int dy = end + 4 + buckets;
        for (int i = 0; i < buckets; i++) {
            int dx = r[0] + r[2] / 2 - 1;
            graphics.fill(dx, dy + i * 3, dx + 2, dy + i * 3 + 2, drop);
        }
    }

    /**
     * An implant: its kind's colour (brass for the machine, flesh for a graft, plated in bloodless mode, iron for a crude
     * one), its item drawn on it as large as the slot takes, darkened if it is not working.
     */
    private void implant(GuiGraphics graphics, BodyPart part, int[] r, ItemStack stack, boolean works, boolean clean) {
        int color = 0xFF8A8A8A;
        if (stack.getItem() instanceof ImplantItem implant) {
            color = implant.spec().slots() > 0 || "soul_blood".equals(implant.spec().fuel()) ? 0xFFB8853C
                    : implant.organic() ? (clean ? 0xFF7A8088 : 0xFF8E3A3A) : 0xFF8A8A8A;
        }
        outlined(graphics, r[0], r[1], r[2], r[3], color, 0xFF2A2218);
        float scale = Math.min(1.0F, Math.min(r[2], r[3]) / 16.0F);
        if (scale >= 0.35F) {
            PoseStack pose = graphics.pose();
            pose.pushPose();
            pose.translate(r[0] + r[2] / 2.0F - 8 * scale, r[1] + r[3] / 2.0F - 8 * scale, 0);
            pose.scale(scale, scale, 1.0F);
            graphics.renderItem(stack, 0, 0);
            pose.popPose();
        }
        if (!works) {
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 200);
            graphics.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], 0xA0000000);
            graphics.pose().popPose();
        }
    }

    /** What a slot is now, in words: their own, missing (a ragged stump and its price), or the implant (dry if not working). */
    private static Component state(Body body, BodyPart part, LivingEntity patient) {
        return switch (body.state(part)) {
            case NATURAL -> Component.translatable("bloodandbones.surgery.state.natural");
            case MISSING -> body.ragged(part) ? Component.translatable("bloodandbones.surgery.state.ragged_fit", Surgery.buckets(body.raggedBuckets(part)))
                    : Component.translatable("bloodandbones.surgery.state.missing");
            case IMPLANT -> body.works(part, patient) ? body.implant(part).getHoverName()
                    : Component.translatable("bloodandbones.surgery.state.dead", body.implant(part).getHoverName());
        };
    }

    private static int stateColor(Body body, BodyPart part, LivingEntity patient) {
        return switch (body.state(part)) {
            case NATURAL -> DIM;
            case MISSING -> PROBLEM;
            case IMPLANT -> body.works(part, patient) ? TEXT : 0xFFB08A60;
        };
    }

    /**
     * On a player, whose flesh comes off only by a surgeon minion's hand: the fittest surgeon by the table, how fit it is,
     * and what a stump it cuts will cost to fit later (docs/NEXT.md 1.5), shown before any cut; or that there is none. At
     * the foot of the body's panel.
     */
    private void surgeonLines(GuiGraphics graphics, LivingEntity patient, int x, int w) {
        com.avicagan.bloodandbones.minion.MinionEntity surgeon = Surgery.surgeonAt(patient.level(), table);
        int y = height - MARGIN - PAD - 30;
        graphics.fill(x, y - 4, x + w, y - 3, RULE);
        if (surgeon == null) {
            int next = wrapped(graphics, Component.translatable("bloodandbones.surgery.no_surgeon"), x, y, w, PROBLEM, 1);
            small(graphics, Component.translatable("bloodandbones.surgery.no_surgeon.hint"), x, next, w, DIM, 2);
            return;
        }
        int next = wrapped(graphics, Component.translatable("bloodandbones.surgery.surgeon_short", surgeonName(surgeon),
                com.avicagan.bloodandbones.minion.TaskWords.percent(surgeon.shownFitness())), x, y, w, TEXT, 2);
        int buckets = Surgery.stumpBuckets(surgeon);
        small(graphics, Component.translatable("bloodandbones.surgery.surgeon_price", Surgery.buckets(buckets)), x, next, w,
                buckets <= 1 ? DIM : buckets == 2 ? 0xFFE0A040 : PROBLEM, 2);
    }

    /** Its own name if it has one, else by its head: "Minion (Villager head)". */
    private static Component surgeonName(com.avicagan.bloodandbones.minion.MinionEntity surgeon) {
        if (surgeon.hasCustomName()) {
            return surgeon.getDisplayName();
        }
        var head = surgeon.build().map(build -> com.avicagan.bloodandbones.minion.MinionStats.head(
                com.avicagan.bloodandbones.parts.PartsData.of(surgeon.level()), build)).orElse(null);
        return head == null ? Component.translatable("bloodandbones.surgery.surgeon_headless")
                : Component.translatable("bloodandbones.surgery.surgeon_head", net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(head.entity()).getDescription());
    }

    // -- the choices

    private int cardsTop;
    private int cardsBottom;
    private int carriedTop;
    private final List<Carried> carriedShown = new ArrayList<>();
    private final List<Surgery.Option> optionsShown = new ArrayList<>();

    private void renderChoices(GuiGraphics graphics, LivingEntity patient, Body body, int mouseX, int mouseY) {
        int x = right() + PAD;
        int w = panelWidth() - 2 * PAD;
        int y = MARGIN + PAD;
        heading(graphics, Component.translatable(selected.translationKey()), x, y, w);
        y = small(graphics, state(body, selected, patient), x, y + 11, w, stateColor(body, selected, patient), 1);
        ItemStack onTable = onTable();
        y = small(graphics, onTable.isEmpty() ? Component.translatable("bloodandbones.surgery.table_empty")
                : Component.translatable("bloodandbones.surgery.on_table", onTable.getHoverName()), x, y + 1, w, DIM, 1);
        Player operator = Minecraft.getInstance().player;
        if (operator != null) {
            y = small(graphics, Component.translatable("bloodandbones.surgery.blood_carried", Surgery.amount(Surgery.bloodCarried(operator))),
                    x, y + 1, w, DIM, 1);
        }
        // what you carry that fits this body, a click picks its slot
        y += 4;
        heading(graphics, Component.translatable("bloodandbones.surgery.carried"), x, y, w);
        y += 11;
        carriedTop = y;
        carriedShown.clear();
        carriedShown.addAll(carried(body));
        int perRow = Math.max(1, w / ICON);
        int rows = Math.min(2, (carriedShown.size() + perRow - 1) / perRow);
        if (carriedShown.isEmpty()) {
            y = small(graphics, Component.translatable("bloodandbones.surgery.carried_none"), x, y + 1, w, DIM, 2) + 2;
        } else {
            for (int i = 0; i < Math.min(carriedShown.size(), rows * perRow); i++) {
                int ix = x + (i % perRow) * ICON;
                int iy = y + (i / perRow) * ICON;
                Carried c = carriedShown.get(i);
                boolean here = c.part == selected || Surgery.action(body, c.stack, selected) != Surgery.Action.NONE;
                graphics.fill(ix, iy, ix + ICON - 1, iy + ICON - 1, here ? 0xFF3A4A2A : 0xFF2A2420);
                graphics.renderItem(c.stack, ix + 1, iy + 1);
            }
            y += rows * ICON + 2;
        }
        // what can be done to the picked slot, a card each
        y += 2;
        heading(graphics, Component.translatable("bloodandbones.surgery.choices"), x, y, w);
        y += 11;
        cardsTop = y;
        cardsBottom = height - MARGIN - PAD - 22;
        optionsShown.clear();
        optionsShown.addAll(options(body, selected));
        if (optionsShown.isEmpty()) {
            small(graphics, Component.translatable("bloodandbones.surgery.nothing_here"), x, y + 1, w, DIM, 3);
            return;
        }
        scroll = Mth.clamp(scroll, 0, maxScroll());
        graphics.enableScissor(x, cardsTop, x + w, cardsBottom);
        int hovered = hoveredCard(mouseX, mouseY);
        for (int i = 0; i < optionsShown.size(); i++) {
            int cy = cardsTop + i * CARD - Math.round(scroll);
            if (cy + CARD < cardsTop || cy > cardsBottom) {
                continue;
            }
            card(graphics, patient, body, optionsShown.get(i), x, cy, w, i == hovered);
        }
        graphics.disableScissor();
    }

    private int maxScroll() {
        return Math.max(0, optionsShown.size() * CARD - (cardsBottom - cardsTop));
    }

    /** Why this cannot be done now, or null: no surgeon at the table to cut, not enough blood for a ragged stump. */
    @Nullable
    private Component problem(LivingEntity patient, Body body, Surgery.Option option) {
        return Surgery.blocked(patient.level(), patient, Minecraft.getInstance().player, table, body, option.action(), selected, option.stack());
    }

    /**
     * One thing that can be done, as Create's schedule cards are drawn: its item (the implant itself when unclipping), what
     * it does, and under it in small print what it uses and from where, or in red why it cannot be done.
     */
    private void card(GuiGraphics graphics, LivingEntity patient, Body body, Surgery.Option option, int x, int y, int w, boolean hovered) {
        Component problem = problem(patient, body, option);
        boolean can = problem == null;
        int h = CARD - 1;
        UIRenderHelper.drawStretched(graphics, x, y + 1, w, h - 2, 0, hovered && can ? AllGuiTextures.SCHEDULE_STRIP_DARK : AllGuiTextures.SCHEDULE_CARD_LIGHT);
        UIRenderHelper.drawStretched(graphics, x + 1, y, w - 2, h, 0, hovered && can ? AllGuiTextures.SCHEDULE_STRIP_DARK : AllGuiTextures.SCHEDULE_CARD_LIGHT);
        UIRenderHelper.drawStretched(graphics, x + 1, y + 1, w - 2, h - 2, 0, AllGuiTextures.SCHEDULE_CARD_DARK);
        UIRenderHelper.drawStretched(graphics, x + 2, y + 2, w - 4, h - 4, 0, can ? AllGuiTextures.SCHEDULE_CARD_MEDIUM : AllGuiTextures.SCHEDULE_CARD_DARK);
        ItemStack shown = option.source() == Surgery.BARE ? body.implant(selected) : option.stack();
        graphics.renderItem(shown, x + 3, y + 3);
        Component verb = Component.translatable(option.action().translationKey());
        graphics.drawString(font, font.substrByWidth(verb, w - 26).getString(), x + 22, y + 3, can ? TEXT : DIM, can);
        Component under = problem != null ? problem : from(option);
        small(graphics, under, x + 22, y + 13, w - 26, problem != null ? PROBLEM : 0xFFD8C8A8, 1);
    }

    /** What an option uses, and from where: "Hook Hand, carried", "Cleaver, on the table", or "back to you" for an unclip. */
    private static Component from(Surgery.Option option) {
        return switch (option.source()) {
            case Surgery.BARE -> Component.translatable("bloodandbones.surgery.from.bare");
            case Surgery.FROM_TABLE -> Component.translatable("bloodandbones.surgery.from.table", option.stack().getHoverName());
            default -> Component.translatable("bloodandbones.surgery.from.carried", option.stack().getHoverName());
        };
    }

    private int hoveredCard(int mouseX, int mouseY) {
        int x = right() + PAD;
        int w = panelWidth() - 2 * PAD;
        if (mouseX < x || mouseX >= x + w || mouseY < cardsTop || mouseY >= cardsBottom) {
            return -1;
        }
        int i = (int) Math.floor((mouseY - cardsTop + scroll) / CARD);
        return i >= 0 && i < optionsShown.size() ? i : -1;
    }

    private int hoveredCarried(int mouseX, int mouseY) {
        int x = right() + PAD;
        int w = panelWidth() - 2 * PAD;
        int perRow = Math.max(1, w / ICON);
        if (mouseX < x || mouseY < carriedTop) {
            return -1;
        }
        int column = (mouseX - x) / ICON;
        int row = (mouseY - carriedTop) / ICON;
        int i = row * perRow + column;
        return column < perRow && row < 2 && i < carriedShown.size() ? i : -1;
    }

    @Nullable
    private BodyPart hoveredSlot(int mouseX, int mouseY) {
        return pinnedHover != null ? pinnedHover : slotAt(mouseX, mouseY);
    }

    @Override
    protected void renderWindowForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        LivingEntity patient = patient();
        if (patient == null) {
            return;
        }
        Body body = BodyEffects.body(patient);
        List<Component> lines = new ArrayList<>();
        int tx = mouseX;
        int ty = mouseY;
        BodyPart slot = hoveredSlot(mouseX, mouseY);
        int carriedAt = hoveredCarried(mouseX, mouseY);
        if (slot != null) {
            lines.add(Component.translatable(slot.translationKey()));
            lines.add(state(body, slot, patient).copy().withStyle(ChatFormatting.GRAY));
            if (slot != selected) {
                lines.add(Component.translatable("bloodandbones.surgery.pick").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
            }
            if (pinnedHover != null) {
                int[] r = rect(slot);
                tx = r[0] + r[2] + 4;
                ty = r[1] + r[3] / 2;
            }
        } else if (carriedAt >= 0) {
            Carried c = carriedShown.get(carriedAt);
            lines.add(c.stack.getHoverName());
            lines.add(Component.translatable("bloodandbones.surgery.fits", Component.translatable(c.part.translationKey())).withStyle(ChatFormatting.GRAY));
        }
        if (lines.isEmpty()) {
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 400);
        graphics.renderTooltip(font, lines, Optional.empty(), tx, ty);
        graphics.pose().popPose();
    }

    // ---- input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            BodyPart slot = slotAt(mouseX, mouseY);
            if (slot != null) {
                playUiSound(SoundEvents.UI_BUTTON_CLICK.value());
                select(slot);
                return true;
            }
            int carriedAt = hoveredCarried((int) mouseX, (int) mouseY);
            if (carriedAt >= 0) {
                playUiSound(SoundEvents.UI_BUTTON_CLICK.value());
                select(carriedShown.get(carriedAt).part);
                return true;
            }
            int card = hoveredCard((int) mouseX, (int) mouseY);
            LivingEntity patient = patient();
            if (card >= 0 && patient != null) {
                Surgery.Option option = optionsShown.get(card);
                if (problem(patient, BodyEffects.body(patient), option) == null) {
                    playUiSound(SoundEvents.UI_BUTTON_CLICK.value());
                    PacketDistributor.sendToServer(Surgery.ActionPayload.of(table, selected, option));
                }
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
        if (mouseX >= right() && mouseY >= cardsTop && mouseY < cardsBottom) {
            scroll = Mth.clamp(scroll - (float) scrollY * CARD, 0, maxScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
