package com.larsoyd.mariohead.client;

import com.larsoyd.mariohead.HeadCamera;
import com.larsoyd.mariohead.HeadEntities;
import com.larsoyd.mariohead.entity.TheHead;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderers;

public final class MarioHeadClient implements ClientModInitializer {
	private int sentView = -1;

	@Override
	public void onInitializeClient() {
		EntityRenderers.register(HeadEntities.THE_HEAD, TheHeadRenderer::new);
		TheHead.voiceListener = HeadVoiceSound::play;
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> this.sentView = -1);
		ClientTickEvents.END_CLIENT_TICK.register(this::reportCamera);
	}

	// first person, behind or in front, in the server's order of views
	private void reportCamera(Minecraft client) {
		if (client.player == null || !ClientPlayNetworking.canSend(HeadCamera.TYPE)) {
			return;
		}
		CameraType camera = client.options.getCameraType();
		int view = camera.isFirstPerson() ? 0 : camera.isMirrored() ? 2 : 1;
		if (view != this.sentView) {
			ClientPlayNetworking.send(new HeadCamera(view));
			this.sentView = view;
		}
	}
}
