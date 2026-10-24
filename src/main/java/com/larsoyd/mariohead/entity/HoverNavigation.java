package com.larsoyd.mariohead.entity;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;

// plans along the floors under a body that never touches them
class HoverNavigation extends GroundPathNavigation {
	HoverNavigation(Mob mob, Level level) {
		super(mob, level);
	}

	@Override
	protected boolean canUpdatePath() {
		return true;
	}
}
