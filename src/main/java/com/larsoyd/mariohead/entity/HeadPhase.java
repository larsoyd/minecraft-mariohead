package com.larsoyd.mariohead.entity;

public enum HeadPhase {
	SPAWN,
	LOOK,
	TALK,
	CHARGE;

	private static final HeadPhase[] BY_ID = values();

	public static HeadPhase byId(int id) {
		return BY_ID[Math.floorMod(id, BY_ID.length)];
	}
}
