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

	static boolean touches(Vec3 centre, float yaw, float pitch, float roll, float scale, AABB player, double reach) {
		AABB box = player.inflate(reach);
		double radius = Math.max(TheHead.WIDTH, TheHead.HEIGHT) * scale;
		if (!box.intersects(centre.x - radius, centre.y - radius, centre.z - radius, centre.x + radius, centre.y + radius, centre.z + radius)) {
			return false;
		}
		Matrix3f pose = new Matrix3f().rotateY((float) Math.toRadians(-yaw)).rotateX((float) Math.toRadians(pitch))
			.rotateZ((float) Math.toRadians(roll)).scale(scale);
		Vector3f p = new Vector3f();
		for (int i = 0; i < POINTS.length; i += 3) {
			pose.transform(POINTS[i], POINTS[i + 1], POINTS[i + 2], p);
			if (box.contains(centre.x + p.x, centre.y + p.y, centre.z + p.z)) {
				return true;
			}
		}
		return false;
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
