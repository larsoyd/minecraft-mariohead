package com.larsoyd.mariohead.showcase;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class WalkingTest {
	@Test void suddenAimChangeCannotSnapOrReverseMouseVelocity() {
		assertEquals(5,Walking.rate(0,0,90,.02,8,120,250),1e-9);
		assertEquals(75,Walking.rate(80,0,-90,.02,8,120,250),1e-9);
		assertEquals(120,Walking.rate(120,0,90,.02,8,120,250),1e-9);
	}
	@Test void aLargeTurnSettlesWithoutSnappingAtNorth() {
		double angle=110,velocity=0;
		for(int i=0;i<240;i++) {
			velocity=Walking.rate(velocity,angle,-170,1.0/60,5,100,300);
			angle+=velocity/60;
		}
		assertEquals(0,Walking.wrap(-170-angle),.05);
		assertEquals(0,velocity,.05);
	}
	@Test void lookingBackReversesKeysWithoutReversingTravel() {
		assertEquals(0,Walking.direction(-90,-90));
		assertEquals(4,Walking.direction(90,-90));
		assertEquals(2,Walking.direction(180,-90));
	}
	@Test void followsAcrossNorthAndIsFrameRateIndependent() {
		double a=Walking.follow(179,-179,.02,12);
		assertTrue(a>179 && a<181);
		double b=Walking.follow(Walking.follow(30,90,.01,12),90,.01,12);
		assertEquals(Walking.follow(30,90,.02,12),b,1e-9);
	}
}
