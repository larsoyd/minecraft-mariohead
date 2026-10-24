package com.larsoyd.mariohead.client;

import net.minecraft.client.renderer.entity.state.EntityRenderState;

public final class TheHeadRenderState extends EntityRenderState {
	public float yaw;
	public float pitch;
	public float roll;
	public float scale = 1.0f;
	public boolean mouthOpen;
	public float browDegrees;
	public float eyeDegrees;
	public int lod;
}
