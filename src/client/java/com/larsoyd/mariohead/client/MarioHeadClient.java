package com.larsoyd.mariohead.client;

import com.larsoyd.mariohead.HeadEntities;
import com.larsoyd.mariohead.entity.TheHead;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.renderer.entity.EntityRenderers;

public final class MarioHeadClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityRenderers.register(HeadEntities.THE_HEAD, TheHeadRenderer::new);
		TheHead.voiceListener = HeadVoiceSound::play;
	}
}
