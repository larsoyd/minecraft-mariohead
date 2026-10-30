package com.larsoyd.mariohead.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class HeadMealTest {
	private static final Vec3 HEAD = new Vec3(0, 2, 0);
	private static final Vec3 PLAYER = new Vec3(20, 0, 0);

	@Test
	void aMobBetweenHimAndThePlayerIsInLine() {
		assertTrue(HeadMeal.inLine(HEAD, PLAYER, new Vec3(8, 0.5, 0)));
	}

	@Test
	void aMobBehindHimNeverIs() {
		assertFalse(HeadMeal.inLine(HEAD, PLAYER, new Vec3(-8, 0.5, 0)));
		assertFalse(HeadMeal.inLine(HEAD, PLAYER, new Vec3(-0.1, 0.5, 0)));
	}

	@Test
	void aMobOffToTheSideIsNot() {
		assertTrue(HeadMeal.inLine(HEAD, PLAYER, new Vec3(8, 0.5, 4.5)));
		assertFalse(HeadMeal.inLine(HEAD, PLAYER, new Vec3(8, 0.5, 5)));
		assertFalse(HeadMeal.inLine(HEAD, PLAYER, new Vec3(0, 0.5, 8)));
	}

	@Test
	void everyMobInLineMovesHimCloserToThePlayer() {
		for (Vec3 player : new Vec3[] { PLAYER, new Vec3(10, 0, 0), new Vec3(-7, 0, 9), new Vec3(0, 0, -30) }) {
			for (double x = -30; x <= 40; x += 0.5) {
				for (double z = -30; z <= 30; z += 0.5) {
					Vec3 mob = new Vec3(x, 0.5, z);
					if (HeadMeal.inLine(HEAD, player, mob)) {
						assertTrue(flat(mob, player) < flat(HEAD, player), "dash at " + mob + " leads away from " + player);
					}
				}
			}
		}
	}

	@Test
	void aMobBesideOrPastThePlayerWouldBeACatchSoItIsNot() {
		assertTrue(HeadMeal.inLine(HEAD, PLAYER, new Vec3(14, 0.5, 0)));
		assertFalse(HeadMeal.inLine(HEAD, PLAYER, new Vec3(14.1, 0.5, 0)));
		assertFalse(HeadMeal.inLine(HEAD, PLAYER, new Vec3(19, 0.5, 0)));
		assertFalse(HeadMeal.inLine(HEAD, PLAYER, new Vec3(26, 0.5, 0)));
		assertFalse(HeadMeal.inLine(HEAD, new Vec3(10, 0, 0), new Vec3(16, 0.5, 0)));
	}

	@Test
	void aMobTooFarOrAlreadyInHisFaceIsNot() {
		Vec3 far = new Vec3(40, 0, 0);
		assertTrue(HeadMeal.inLine(HEAD, far, new Vec3(16, 0.5, 0)));
		assertFalse(HeadMeal.inLine(HEAD, far, new Vec3(16.1, 0.5, 0)));
		assertTrue(HeadMeal.inLine(HEAD, PLAYER, new Vec3(3, 0.5, 0)));
		assertFalse(HeadMeal.inLine(HEAD, PLAYER, new Vec3(2.9, 0.5, 0)));
	}

	@Test
	void aMobWellAboveOrBelowHimIsNot() {
		assertTrue(HeadMeal.inLine(HEAD, PLAYER, new Vec3(8, 5, 0)));
		assertFalse(HeadMeal.inLine(HEAD, PLAYER, new Vec3(8, 5.1, 0)));
		assertFalse(HeadMeal.inLine(HEAD, PLAYER, new Vec3(8, -1.1, 0)));
	}

	@Test
	void aPlayerWhoRanRoundTheMobCallsTheDashOff() {
		assertTrue(HeadMeal.closes(HEAD, PLAYER, new Vec3(8, 0.5, 0)));
		assertFalse(HeadMeal.closes(HEAD, new Vec3(-20, 0, 0), new Vec3(8, 0.5, 0)));
	}

	@Test
	void aPlayerCloseEnoughToReachCallsTheDashOff() {
		assertTrue(HeadMeal.clearOfPlayer(6));
		assertFalse(HeadMeal.clearOfPlayer(5.99));
	}

	@Test
	void mealsAreThreeSecondsApart() {
		assertEquals(60, HeadMeal.COOLDOWN_TICKS);
	}

	@Test
	void yummyFollowsFortyPercentOfMeals() {
		assertTrue(HeadMeal.yummy(0));
		assertTrue(HeadMeal.yummy(0.399));
		assertFalse(HeadMeal.yummy(0.4));
		assertFalse(HeadMeal.yummy(0.999));
	}

	@Test
	void yummyStartsPointThreeSecondsAfterTheGulpEnds() {
		assertEquals(18, HeadMeal.yummyDelay(HeadCue.GULP.clip()));
	}

	@Test
	void aDashFromTheFurthestMobLandsInsideTheShortestHum() {
		int ticks = 0;
		double speed = 0, left = 16;
		while (left > 0 && ticks < 100) {
			speed = HeadMeal.dashSpeed(speed);
			left -= speed;
			ticks++;
		}
		assertTrue(ticks < HeadCue.MMM1.clip().millis() / 50, "dash took " + ticks);
	}

	private static double flat(Vec3 a, Vec3 b) {
		return a.subtract(b).horizontalDistance();
	}
}
