package com.larsoyd.mariohead.showcase;

import java.util.ArrayList;
import java.util.List;

public final class Pointer {
	public static final int OUTLINE = 0xFFFFFFFF;
	public static final int FILL = 0xFF000000;
	private static final String[] ROWS = {
		"o",
		"oo",
		"o#o",
		"o##o",
		"o###o",
		"o####o",
		"o#####o",
		"o######o",
		"o#######o",
		"o########o",
		"o#####ooooo",
		"o##o##o",
		"o#o o##o",
		"oo  o##o",
		"o    o##o",
		"     o##o",
		"      oo",
	};
	public static final int WIDTH = 11;
	public static final int HEIGHT = ROWS.length;

	private Pointer() {}

	public static int pixel(int x, int y) {
		if (y < 0 || y >= HEIGHT || x < 0 || x >= ROWS[y].length()) return 0;
		return switch (ROWS[y].charAt(x)) {
			case 'o' -> OUTLINE;
			case '#' -> FILL;
			default -> 0;
		};
	}

	// horizontal runs of one colour as x0, y, x1, colour
	public static List<int[]> runs() {
		List<int[]> runs = new ArrayList<>();
		for (int y = 0; y < HEIGHT; y++) {
			int x = 0;
			while (x < WIDTH) {
				int colour = pixel(x, y);
				int end = x + 1;
				while (end < WIDTH && pixel(end, y) == colour) end++;
				if (colour != 0) runs.add(new int[] {x, y, end, colour});
				x = end;
			}
		}
		return runs;
	}
}
