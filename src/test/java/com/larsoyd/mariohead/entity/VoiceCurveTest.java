package com.larsoyd.mariohead.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VoiceCurveTest {
	@Test
	void fullVolumeUpClose() {
		assertEquals(1.0f, VoiceCurve.volume(0.0), 1e-6);
		assertEquals(1.0f, VoiceCurve.volume(2.5), 1e-6);
	}

	@Test
	void measuredPoints() {
		assertEquals(0.41f, VoiceCurve.volume(7.0), 0.005);
		assertEquals(0.355f, VoiceCurve.volume(10.0), 0.005);
		assertEquals(0.089f, VoiceCurve.volume(30.0), 0.005);
	}

	@Test
	void silentFarAway() {
		assertEquals(0.0f, VoiceCurve.volume(50.0), 1e-6);
		assertEquals(0.0f, VoiceCurve.volume(400.0), 1e-6);
	}

	@Test
	void neverLouderFurtherOut() {
		float last = 2.0f;
		for (double d = 0.0; d < 55.0; d += 0.05) {
			float v = VoiceCurve.volume(d);
			assertTrue(v <= last + 1e-6f, "louder at " + d);
			assertTrue(v >= 0.0f && v <= 1.0f, "out of range at " + d);
			last = v;
		}
	}
}
