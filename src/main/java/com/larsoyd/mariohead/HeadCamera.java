package com.larsoyd.mariohead;

import com.larsoyd.mariohead.entity.HeadSight;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

// the client says which camera it looks through, so he knows what a third person view sees
public record HeadCamera(int view) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<HeadCamera> TYPE = new CustomPacketPayload.Type<>(MarioHead.id("camera"));
	public static final StreamCodec<ByteBuf, HeadCamera> CODEC = ByteBufCodecs.VAR_INT.map(HeadCamera::new, HeadCamera::view);

	@Override
	public Type<HeadCamera> type() {
		return TYPE;
	}

	static void init() {
		PayloadTypeRegistry.serverboundPlay().register(TYPE, CODEC);
		ServerPlayNetworking.registerGlobalReceiver(TYPE, (payload, context) -> {
			HeadSight.View[] views = HeadSight.View.values();
			HeadSight.setView(context.player().getUUID(), views[Math.floorMod(payload.view(), views.length)]);
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> HeadSight.setView(handler.getPlayer().getUUID(), HeadSight.View.FIRST));
	}
}
