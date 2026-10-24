package com.larsoyd.mariohead.test;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// a test that fails or times out must still let its mock players go, or they leak into later tests
public final class HeadTestSetup implements ModInitializer {
	static final String PLAYER_PREFIX = "ht";
	private static final Logger LOG = LoggerFactory.getLogger("mariohead-gametest");

	@Override
	public void onInitialize() {
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			var leaked = server.getPlayerList().getPlayers().stream()
				.map(ServerPlayer::getScoreboardName)
				.filter(name -> name.startsWith(PLAYER_PREFIX))
				.toList();
			LOG.info("[headtest] leaked_players={} names={}", leaked.size(), leaked);
			if (!leaked.isEmpty()) {
				Runtime.getRuntime().halt(70);
			}
		});
	}
}
