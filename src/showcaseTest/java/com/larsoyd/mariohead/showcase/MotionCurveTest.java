package com.larsoyd.mariohead.showcase;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class MotionCurveTest {
	@Test
	void settlesAtBothEndsWithoutASpeedJump() {
		assertEquals(0, MotionCurve.progress(-1));
		assertEquals(1, MotionCurve.progress(2));
		assertTrue(MotionCurve.progress(0.001) < 0.00001);
		assertTrue(1 - MotionCurve.progress(0.999) < 0.00001);
	}

	@Test
	void turnsAcrossNorthWithoutSpinningAround() {
		assertEquals(180, MotionCurve.angle(179, -179, 0.5), 0.001);
		assertEquals(-180, MotionCurve.angle(-179, 179, 0.5), 0.001);
		assertEquals(181, MotionCurve.angle(179, -179, 1), 0.001);
	}

	@Test
	void progressesMonotonicallyAcrossAnUnevenFrameRate() {
		double previous = 0;
		for (double time : new double[] {0, .013, .035, .11, .29, .5, .72, .96, 1}) {
			double next = MotionCurve.progress(time);
			assertTrue(next >= previous && next <= 1);
			previous = next;
		}
		assertEquals(1, previous);
	}
}
