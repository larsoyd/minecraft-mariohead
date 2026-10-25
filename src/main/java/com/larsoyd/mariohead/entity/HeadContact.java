package com.larsoyd.mariohead.entity;

import java.io.IOException;
import java.io.InputStream;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Vector3f;

// a catch is his visible head meeting the player, so it tests the mesh posed the way it is drawn
final class HeadContact {
	private static final String MESH = "/assets/mariohead/mesh/the_head.mesh";
	private static final float[] POINTS = load();

	private HeadContact() {
	}

	static boolean sweeps(Vec3 from, Vec3 to, float yaw, float pitch, float roll, float scale, AABB player, double reach) {
		AABB box = player.inflate(reach);
		double radius = Math.max(TheHead.WIDTH, TheHead.HEIGHT) * scale;
		AABB broad = box.inflate(radius);
		if (!broad.contains(from) && !broad.contains(to) && broad.clip(from, to).isEmpty()) {
			return false;
		}
		Matrix3f pose = new Matrix3f().rotateY((float) Math.toRadians(-yaw)).rotateX((float) Math.toRadians(pitch))
			.rotateZ((float) Math.toRadians(roll)).scale(scale);
		Vector3f p = new Vector3f();
		for (int i = 0; i < POINTS.length; i += 3) {
			pose.transform(POINTS[i], POINTS[i + 1], POINTS[i + 2], p);
			Vec3 start = from.add(p.x, p.y, p.z);
			Vec3 end = to.add(p.x, p.y, p.z);
			if (box.contains(start) || box.contains(end) || box.clip(start, end).isPresent()) {
				return true;
			}
		}
		return false;
	}

	static boolean touches(Vec3 centre, float yaw, float pitch, float roll, float scale, AABB player, double reach) {
		return sweeps(centre, centre, yaw, pitch, roll, scale, player, reach);
	}

	// the coarse level of detail, every part, so an open or shut mouth both count
	private static float[] load() {
		try (InputStream in = HeadContact.class.getResourceAsStream(MESH)) {
			if (in == null) {
				throw new IllegalStateException("head mesh " + MESH + " missing from the mod");
			}
			HeadMesh mesh = HeadMesh.read(in.readAllBytes());
			var coarse = mesh.lods().getLast();
			int count = coarse.stream().mapToInt(HeadMesh.Section::vertexCount).sum();
			float[] points = new float[count * 3];
			int o = 0;
			for (HeadMesh.Section s : coarse) {
				float[] v = s.vertices();
				for (int i = 0; i < v.length; i += HeadMesh.VERTEX_FLOATS) {
					points[o++] = v[i];
					points[o++] = v[i + 1];
					points[o++] = v[i + 2];
				}
			}
			return points;
		} catch (IOException e) {
			throw new IllegalStateException("head mesh " + MESH + " unreadable: " + e.getMessage(), e);
		}
	}
}
