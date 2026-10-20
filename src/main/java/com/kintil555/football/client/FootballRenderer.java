package com.kintil555.football.client;

import com.kintil555.football.entity.FootballEntity;
import com.kintil555.football.registry.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.ThrownItemRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Draws the football by rendering the football item model (a patterned cube) in the world. */
public class FootballRenderer extends EntityRenderer<FootballEntity, ThrownItemRenderState> {
    private static final float MODEL_SCALE = 2.0F;
    private static final double MODEL_Y_OFFSET = 0.0;

    private final ItemModelResolver itemModelResolver;
    /** Created lazily: item components are not bound yet when the renderer is constructed. */
    private ItemStack displayStack;

    public FootballRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemModelResolver = context.getItemModelResolver();
        this.shadowRadius = 0.25F;
    }

    @Override
    public ThrownItemRenderState createRenderState() {
        return new ThrownItemRenderState();
    }

    @Override
    public void extractRenderState(FootballEntity entity, ThrownItemRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        if (this.displayStack == null) {
            this.displayStack = new ItemStack(ModItems.FOOTBALL);
        }
        this.itemModelResolver.updateForNonLiving(state.item, this.displayStack, ItemDisplayContext.GROUND, entity);
    }

    @Override
    public void submit(ThrownItemRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.translate(0.0, MODEL_Y_OFFSET, 0.0);
        poseStack.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
        state.item.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }
}
