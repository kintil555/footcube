package com.kintil555.football.client;

import com.kintil555.football.entity.BallAnimation;
import com.kintil555.football.entity.FootballEntity;
import com.kintil555.football.registry.ModEntities;
import com.kintil555.football.registry.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;

/** Draws the football by rendering the football item model (a patterned cube) in the world. */
public class FootballRenderer extends EntityRenderer<FootballEntity, FootballRenderState> {
    /** The cube item model renders 0.25 blocks wide in the GROUND context, with its center 3/16 above the origin. */
    private static final float MODEL_SCALE = ModEntities.BALL_SIZE / 0.25F;

    private final ItemModelResolver itemModelResolver;
    /** Created lazily: item components are not bound yet when the renderer is constructed. */
    private ItemStack displayStack;

    public FootballRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemModelResolver = context.getItemModelResolver();
        this.shadowRadius = ModEntities.BALL_SIZE / 2.0F;
    }

    @Override
    public FootballRenderState createRenderState() {
        return new FootballRenderState();
    }

    @Override
    public void extractRenderState(FootballEntity entity, FootballRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        if (this.displayStack == null) {
            this.displayStack = new ItemStack(ModItems.FOOTBALL);
        }
        this.itemModelResolver.updateForNonLiving(state.item, this.displayStack, ItemDisplayContext.GROUND, entity);
        BallAnimation animation = entity.getAnimation();
        animation.getOrientation(partialTicks, state.orientation);
        state.squash = animation.getSquash(partialTicks);
        state.squashAxis.set(animation.getSquashAxis());
    }

    @Override
    public void submit(FootballRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        float radius = ModEntities.BALL_SIZE / 2.0F;
        // Pivot around the ball's center; keep the ball resting on the ground while it is squashed.
        poseStack.translate(0.0F, radius - radius * state.squash * Math.abs(state.squashAxis.y), 0.0F);
        // Squash along the impact direction, volume preserved (flatter along the axis, wider across it).
        float along = 1.0F - state.squash;
        float across = (float) (1.0 / Math.sqrt(along));
        Quaternionf toAxis = new Quaternionf().rotationTo(0.0F, 1.0F, 0.0F,
                state.squashAxis.x, state.squashAxis.y, state.squashAxis.z);
        poseStack.mulPose(toAxis);
        poseStack.scale(across, along, across);
        poseStack.mulPose(toAxis.conjugate(new Quaternionf()));
        poseStack.mulPose(state.orientation);
        poseStack.translate(0.0F, (float) (-0.1875 * MODEL_SCALE), 0.0F);
        poseStack.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
        state.item.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }
}
