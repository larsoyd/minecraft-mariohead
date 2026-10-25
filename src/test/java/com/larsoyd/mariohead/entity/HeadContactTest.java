package com.larsoyd.mariohead.entity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

// a catch is the visible head meeting the player, not the corners of the box around every pose
class HeadContactTest {
	private static final Vec3 CENTRE = new Vec3(10.0, 70.0, -4.0);
	// the tip of his nose, his widest point at the back of a cheek, the bottom of his open jaw
	private static final Vec3 NOSE = new Vec3(0.0, -0.098, 1.438);
	private static final Vec3 CHEEK = new Vec3(-1.322, -0.293, -0.58);
	private static final Vec3 CHIN = new Vec3(0.0, -1.115, 0.631);

	// a standing player's box just beyond a point of his, on the side the step points to
	private static AABB beyond(Vec3 point, Vec3 step, double gap) {
		Vec3 at = CENTRE.add(point).add(step.scale(gap + 0.3));
		return new AABB(at.x - 0.3, at.y - 0.9, at.z - 0.3, at.x + 0.3, at.y + 0.9, at.z + 0.3).move(step.scale(step.y != 0.0 ? 0.6 : 0.0));
	}

	@Test
	void theNoseCatches() {
		Vec3 ahead = new Vec3(0.0, 0.0, 1.0);
		assertTrue(HeadContact.touches(CENTRE, 0.0f, 0.0f, 0.0f, 1.0f, beyond(NOSE, ahead, 0.03), 0.05));
		assertFalse(HeadContact.touches(CENTRE, 0.0f, 0.0f, 0.0f, 1.0f, beyond(NOSE, ahead, 0.15), 0.05));
	}

	@Test
	void aFastDashCannotSkipAcrossThePlayer() {
		AABB player = new AABB(-0.3, -1.6, -0.3, 0.3, 0.2, 0.3);
		for (double distance : new double[] { 3.0, 10.0, 80.0, 200.0 }) {
			Vec3 from = new Vec3(0, 0, -distance);
			Vec3 to = new Vec3(0, 0, distance);
			assertFalse(HeadContact.touches(from, 0, 0, 0, 1, player, 0.05));
			assertFalse(HeadContact.touches(to, 0, 0, 0, 1, player, 0.05));
			assertTrue(HeadContact.sweeps(from, to, 0, 0, 0, 1, player, 0.05));
			assertFalse(HeadContact.sweeps(from.add(5, 0, 0), to.add(5, 0, 0), 0, 0, 0, 1, player, 0.05));
		}
	}

	@Test
	void aBoxCornerDoesNot() {
		AABB aboveBehind = new AABB(CENTRE.x - 0.3, CENTRE.y + 1.2, CENTRE.z - 2.1, CENTRE.x + 0.3, CENTRE.y + 3.0, CENTRE.z - 1.5);
		assertFalse(HeadContact.touches(CENTRE, 0.0f, 0.0f, 0.0f, 1.0f, aboveBehind, 0.05));
	}

	@Test
	void heTurnsHisNoseWithHim() {
		Vec3 west = new Vec3(-1.0, 0.0, 0.0);
		Vec3 noseWest = new Vec3(-NOSE.z, NOSE.y, 0.0);
		assertTrue(HeadContact.touches(CENTRE, 90.0f, 0.0f, 0.0f, 1.0f, beyond(noseWest, west, 0.03), 0.05));
		assertFalse(HeadContact.touches(CENTRE, 0.0f, 0.0f, 0.0f, 1.0f, beyond(noseWest, west, 0.03), 0.05));
		assertTrue(HeadContact.touches(CENTRE, 0.0f, 0.0f, 0.0f, 1.0f, beyond(CHEEK, west, 0.03), 0.05));
	}

	@Test
	void theJawReachesDown() {
		Vec3 down = new Vec3(0.0, -1.0, 0.0);
		assertTrue(HeadContact.touches(CENTRE, 0.0f, 0.0f, 0.0f, 1.0f, beyond(CHIN, down, 0.03), 0.05));
		assertFalse(HeadContact.touches(CENTRE, 0.0f, 0.0f, 0.0f, 1.0f, beyond(CHIN, down, 0.15), 0.05));
	}

	@Test
	void aSqueezedHeadIsSmaller() {
		Vec3 ahead = new Vec3(0.0, 0.0, 1.0);
		assertFalse(HeadContact.touches(CENTRE, 0.0f, 0.0f, 0.0f, 0.32f, beyond(NOSE, ahead, 0.03), 0.05));
		assertTrue(HeadContact.touches(CENTRE, 0.0f, 0.0f, 0.0f, 0.32f, beyond(NOSE.scale(0.32), ahead, 0.03), 0.05));
	}

	@Test
	void hisLeanAndPitchChangeWhatHeTouches() {
		int leaned = 0;
		int pitched = 0;
		for (int a = 0; a < 36; a++) {
			for (double r = 1.0; r < 2.0; r += 0.1) {
				for (double y = -2.5; y < 1.5; y += 0.25) {
					Vec3 at = CENTRE.add(Math.sin(a * 0.1745) * r, y, Math.cos(a * 0.1745) * r);
					AABB box = new AABB(at.x - 0.3, at.y, at.z - 0.3, at.x + 0.3, at.y + 1.8, at.z + 0.3);
					boolean level = HeadContact.touches(CENTRE, 0.0f, 0.0f, 0.0f, 1.0f, box, 0.05);
					leaned += level != HeadContact.touches(CENTRE, 0.0f, 0.0f, HeadTuning.ROLL_MAX, 1.0f, box, 0.05) ? 1 : 0;
					pitched += level != HeadContact.touches(CENTRE, 0.0f, HeadTuning.PITCH_MAX, 0.0f, 1.0f, box, 0.05) ? 1 : 0;
				}
			}
		}
		assertTrue(leaned > 20 && pitched > 20, "lean changed " + leaned + " spots, pitch " + pitched);
	}
}
