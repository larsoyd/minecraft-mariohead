package com.larsoyd.mariohead;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MarioHead implements ModInitializer {
	public static final String MOD_ID = "mariohead";
	public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		HeadSounds.init();
		HeadEntities.init();
		HeadItems.init();
		CommandRegistrationCallback.EVENT.register(HeadCommand::register);
	}
}
