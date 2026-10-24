package com.larsoyd.mariohead.entity;

public enum HeadPhase {
	SPAWN,
	LOOK,
	TALK,
	CHARGE;

	private static final HeadPhase[] BY_ID = values();
	private static final int PHASE_BITS = 2;

	public static long pack(HeadPhase phase, long time) {
		return time << PHASE_BITS | phase.ordinal();
	}

	public static HeadPhase phaseOf(long packed) {
		return byId((int) (packed & (1 << PHASE_BITS) - 1));
	}

	public static long timeOf(long packed) {
		return packed >>> PHASE_BITS;
	}

	public static HeadPhase byId(int id) {
		return BY_ID[Math.floorMod(id, BY_ID.length)];
	}
}
