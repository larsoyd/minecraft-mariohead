package com.larsoyd.mariohead.entity;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.Random;
import org.joml.Matrix3f;
import org.joml.Vector3f;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

// the collision box must hold every vertex of the head in every pose it can strike
class HeadHullTest {
	private static HeadMesh mesh;

	@BeforeAll
	static void load() throws Exception {
		try (InputStream in = HeadHullTest.class.getResourceAsStream("/assets/mariohead/mesh/the_head.mesh")) {
			mesh = HeadMesh.read(in.readAllBytes());
		}
	}

	private static float worst(float yaw, float pitch, float roll, boolean height) {
		Matrix3f body = new Matrix3f().rotateY((float) Math.toRadians(-yaw)).rotateX((float) Math.toRadians(pitch))
			.rotateZ((float) Math.toRadians(roll));
		float out = 0.0f;
		for (var lod : mesh.lods()) {
			for (var s : lod) {
				for (float joint : new float[] { -1.0f, 0.0f, 1.0f }) {
					Matrix3f m = new Matrix3f(body);
					if (s.part() == 1) {
						m.rotateX((float) Math.toRadians(HeadTuning.BROW_ANGRY_DEGREES * Math.max(0.0f, joint)));
					} else if (s.part() == 2) {
						m.rotateY((float) Math.toRadians(HeadTuning.EYE_PAN_DEGREES * joint));
					}
					float[] v = s.vertices();
					for (int i = 0; i < v.length; i += HeadMesh.VERTEX_FLOATS) {
						Vector3f p = m.transform(new Vector3f(v[i], v[i + 1], v[i + 2]));
						out = Math.max(out, height ? Math.abs(p.y) : Math.max(Math.abs(p.x), Math.abs(p.z)));
					}
				}
			}
		}
		return out;
	}

	@Test
	void everyPoseFitsTheBox() {
		Random random = new Random(7);
		float halfWidth = TheHead.WIDTH / 2.0f;
		float halfHeight = TheHead.HEIGHT / 2.0f;
		for (int i = 0; i < 400; i++) {
			float yaw = random.nextFloat() * 360.0f;
			float pitch = (random.nextFloat() * 2.0f - 1.0f) * HeadTuning.PITCH_MAX;
			float roll = (random.nextFloat() * 2.0f - 1.0f) * HeadTuning.ROLL_MAX;
			if (i < 8) {
				pitch = (i & 1) == 0 ? HeadTuning.PITCH_MAX : -HeadTuning.PITCH_MAX;
				roll = (i & 2) == 0 ? HeadTuning.ROLL_MAX : -HeadTuning.ROLL_MAX;
				yaw = 45.0f * (i >> 2);
			}
			float w = worst(yaw, pitch, roll, false);
			float h = worst(yaw, pitch, roll, true);
			assertTrue(w <= halfWidth, "yaw " + yaw + " pitch " + pitch + " roll " + roll + " reaches " + w + " past " + halfWidth);
			assertTrue(h <= halfHeight, "yaw " + yaw + " pitch " + pitch + " roll " + roll + " reaches " + h + " past " + halfHeight);
		}
	}

	@Test
	void theBoxIsNotWasteful() {
		assertTrue(worst(45.0f, 0.0f, 0.0f, false) > TheHead.WIDTH / 2.0f * 0.9f, "box much wider than the head");
		assertTrue(worst(0.0f, 0.0f, HeadTuning.ROLL_MAX, true) > TheHead.HEIGHT / 2.0f * 0.95f, "box much taller than the head");
	}
}
