package com.kintil555.football.client;

import net.minecraft.client.renderer.entity.state.ThrownItemRenderState;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Render state of the football: the item model plus the procedural rotation and squash for this frame. */
public class FootballRenderState extends ThrownItemRenderState {
    public final Quaternionf orientation = new Quaternionf();
    public final Vector3f squashAxis = new Vector3f(0.0F, 1.0F, 0.0F);
    public float squash;
    /** Offset from the entity's own render position to the smoothed ball position (see BallAnimation#getVisualPosition). */
    public float offsetX;
    public float offsetY;
    public float offsetZ;
}
