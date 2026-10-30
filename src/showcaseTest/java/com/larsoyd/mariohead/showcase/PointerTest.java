package com.larsoyd.mariohead.showcase;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class PointerTest {
	@Test
	void tipSitsOnTheHotspot() {
		assertEquals(Pointer.OUTLINE, Pointer.pixel(0, 0));
		assertEquals(0, Pointer.pixel(1, 0));
	}

	@Test
	void everyFilledPixelIsEnclosedByOutline() {
		for (int y = 0; y < Pointer.HEIGHT; y++)
			for (int x = 0; x < Pointer.WIDTH; x++)
				if (Pointer.pixel(x, y) == Pointer.FILL)
					for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}})
						assertNotEquals(0, Pointer.pixel(x + d[0], y + d[1]), "fill leaks at " + x + "," + y);
	}

	@Test
	void runsCoverExactlyThePaintedPixels() {
		int painted = 0;
		for (int y = 0; y < Pointer.HEIGHT; y++)
			for (int x = 0; x < Pointer.WIDTH; x++)
				if (Pointer.pixel(x, y) != 0) painted++;
		int covered = 0;
		for (int[] run : Pointer.runs()) covered += run[2] - run[0];
		assertEquals(painted, covered);
	}
}
