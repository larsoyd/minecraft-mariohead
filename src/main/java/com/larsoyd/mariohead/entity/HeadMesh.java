package com.larsoyd.mariohead.entity;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

// the head mesh file: quads by part and texture, one list of sections per level of detail
public record HeadMesh(List<List<Section>> lods) {
	public static final int VERSION = 1;
	public static final String[] PARTS = { "root", "brow", "eye", "mouth_closed", "mouth_open" };
	public static final String[] TEXTURES = { "skin", "cap", "iris" };
	public static final int VERTEX_FLOATS = 12;

	public record Section(int part, int texture, float[] vertices, int[] quads) {
		public int vertexCount() {
			return this.vertices.length / VERTEX_FLOATS;
		}
	}

	public static HeadMesh read(byte[] data) throws IOException {
		ByteBuffer in = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
		if (in.remaining() < 8 || in.get() != 'M' || in.get() != 'H' || in.get() != 'E' || in.get() != 'D') {
			throw new IOException("head mesh: bad magic");
		}
		int version = Short.toUnsignedInt(in.getShort());
		if (version != VERSION) {
			throw new IOException("head mesh: version " + version + ", expected " + VERSION);
		}
		int lodCount = Short.toUnsignedInt(in.getShort());
		List<List<Section>> lods = new ArrayList<>();
		for (int l = 0; l < lodCount; l++) {
			int count = Short.toUnsignedInt(in.getShort());
			List<Section> sections = new ArrayList<>();
			for (int s = 0; s < count; s++) {
				sections.add(readSection(in));
			}
			lods.add(List.copyOf(sections));
		}
		if (in.hasRemaining()) {
			throw new IOException("head mesh: " + in.remaining() + " trailing bytes");
		}
		return new HeadMesh(List.copyOf(lods));
	}

	// position xyz, normal xyz, uv, rgba as 0 to 1
	private static Section readSection(ByteBuffer in) throws IOException {
		int part = Byte.toUnsignedInt(in.get());
		int texture = Byte.toUnsignedInt(in.get());
		int nv = in.getInt();
		int nq = in.getInt();
		if (part >= PARTS.length || texture >= TEXTURES.length) {
			throw new IOException("head mesh: part " + part + " texture " + texture);
		}
		float[] v = new float[nv * VERTEX_FLOATS];
		for (int i = 0; i < nv; i++) {
			int o = i * VERTEX_FLOATS;
			v[o] = in.getFloat();
			v[o + 1] = in.getFloat();
			v[o + 2] = in.getFloat();
			v[o + 3] = in.get() / 127.0f;
			v[o + 4] = in.get() / 127.0f;
			v[o + 5] = in.get() / 127.0f;
			in.get();
			v[o + 6] = in.getFloat();
			v[o + 7] = in.getFloat();
			for (int c = 0; c < 4; c++) {
				v[o + 8 + c] = Byte.toUnsignedInt(in.get()) / 255.0f;
			}
		}
		int[] q = new int[nq * 4];
		for (int i = 0; i < q.length; i++) {
			q[i] = in.getInt();
			if (q[i] < 0 || q[i] >= nv) {
				throw new IOException("head mesh: index " + q[i] + " of " + nv + " in " + PARTS[part]);
			}
		}
		return new Section(part, texture, v, q);
	}
}
