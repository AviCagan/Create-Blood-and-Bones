package com.avicagan.bloodandbones.client.effect;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.parts.CarcassArmourItem;
import com.avicagan.bloodandbones.parts.effect.GlowEffect;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;

import java.util.Map;

/**
 * The glow trait on carcass armour (docs/PARTS-AND-TRAITS.md section 5.4, type 30): the pieces whose own traits glow are
 * drawn again with a speckle of photophores, lit as a spider's eyes are, so they shine in the dark and breathe slowly
 * brighter and dimmer. Nothing gory in it, so bloodless mode draws it the same.
 */
public class CarcassGlowLayer<T extends LivingEntity, M extends HumanoidModel<T>> extends RenderLayer<T, M> {
    private static final ResourceLocation OUTER = BloodAndBones.asResource("textures/models/armor/carcass_glow_layer_1.png");
    private static final ResourceLocation INNER = BloodAndBones.asResource("textures/models/armor/carcass_glow_layer_2.png");

    private static final RenderType OUTER_GLOW = glow(OUTER);
    private static final RenderType INNER_GLOW = glow(INNER);

    private final HumanoidModel<T> inner;
    private final HumanoidModel<T> outer;

    public CarcassGlowLayer(RenderLayerParent<T, M> parent, EntityModelSet models) {
        super(parent);
        this.inner = new HumanoidModel<>(models.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR));
        this.outer = new HumanoidModel<>(models.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR));
    }

    /**
     * The eyes render type (full bright, light added to what is under it) nudged towards the camera as armour is
     * ({@code armorCutoutNoCull} draws a hair nearer than its shape), or the armour under it would hide it.
     */
    private static RenderType glow(ResourceLocation texture) {
        return RenderType.create(BloodAndBones.MOD_ID + ":carcass_glow", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, false, true,
                RenderType.CompositeState.builder()
                        .setShaderState(RenderStateShard.RENDERTYPE_EYES_SHADER)
                        .setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
                        .setTransparencyState(RenderStateShard.ADDITIVE_TRANSPARENCY)
                        .setCullState(RenderStateShard.NO_CULL)
                        .setLayeringState(RenderStateShard.VIEW_OFFSET_Z_LAYERING)
                        .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                        .createCompositeState(false));
    }

    @Override
    public void render(PoseStack ms, MultiBufferSource buffers, int light, T wearer, float limbSwing, float limbSwingAmount, float partialTick,
                       float ageInTicks, float netHeadYaw, float headPitch) {
        if (wearer.isInvisible()) {
            return;
        }
        Map<EquipmentSlot, Integer> glowing = GlowEffect.pieces(wearer);
        if (glowing.isEmpty()) {
            return;
        }
        // a slow breath, each wearer out of step with the next
        float breath = 0.7F + 0.3F * Mth.sin((ageInTicks + wearer.getId() * 13) * 0.07F);
        for (Map.Entry<EquipmentSlot, Integer> e : glowing.entrySet()) {
            EquipmentSlot slot = e.getKey();
            if (!(wearer.getItemBySlot(slot).getItem() instanceof CarcassArmourItem)) {
                continue;
            }
            HumanoidModel<T> model = slot == EquipmentSlot.LEGS ? inner : outer;
            getParentModel().copyPropertiesTo(model);
            show(model, slot);
            int c = e.getValue();
            int colour = FastColor.ARGB32.color(255, (int) (FastColor.ARGB32.red(c) * breath), (int) (FastColor.ARGB32.green(c) * breath),
                    (int) (FastColor.ARGB32.blue(c) * breath));
            model.renderToBuffer(ms, buffers.getBuffer(slot == EquipmentSlot.LEGS ? INNER_GLOW : OUTER_GLOW), LightTexture.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY, colour);
        }
    }

    /** Only the parts of the model the piece covers, as the armour layer shows them. */
    private static void show(HumanoidModel<?> model, EquipmentSlot slot) {
        model.setAllVisible(false);
        switch (slot) {
            case HEAD -> {
                model.head.visible = true;
                model.hat.visible = true;
            }
            case CHEST -> {
                model.body.visible = true;
                model.rightArm.visible = true;
                model.leftArm.visible = true;
            }
            case LEGS -> {
                model.body.visible = true;
                model.rightLeg.visible = true;
                model.leftLeg.visible = true;
            }
            case FEET -> {
                model.rightLeg.visible = true;
                model.leftLeg.visible = true;
            }
            default -> {
            }
        }
    }
}
