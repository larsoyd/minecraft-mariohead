package com.larsoyd.mariohead.entity;

public final class VoiceSelection {
	private final long[] counts;
	private final int[] all;
	private int last = -1;

	public VoiceSelection(int size) {
		this.counts = new long[size];
		this.all = java.util.stream.IntStream.range(0, size).toArray();
	}

	public int choose(double random) {
		return this.choose(this.all, random);
	}

	public int choose(int[] pool, double random) {
		long least = Long.MAX_VALUE;
		int ties = 0;
		for (int id : pool) {
			if (id == this.last) continue;
			if (this.counts[id] < least) {
				least = this.counts[id];
				ties = 0;
			}
			if (this.counts[id] == least) ties++;
		}
		int choice = (int) (random * ties);
		for (int id : pool) {
			if (id != this.last && this.counts[id] == least && choice-- == 0) return id;
		}
		return -1;
	}

	public void played(int id) {
		this.counts[id]++;
		this.last = id;
	}
}
