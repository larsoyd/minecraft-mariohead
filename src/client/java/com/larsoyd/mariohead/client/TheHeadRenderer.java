package com.larsoyd.mariohead.client;

import com.larsoyd.mariohead.MarioHead;
import com.larsoyd.mariohead.entity.HeadMesh;
import com.larsoyd.mariohead.entity.HeadPose;
import com.larsoyd.mariohead.entity.HeadTuning;
import com.larsoyd.mariohead.entity.TheHead;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.io.IOException;
import java.io.InputStream;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.ARGB;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class TheHeadRenderer extends EntityRenderer<TheHead, TheHeadRenderState> {
	private static final Identifier MESH = MarioHead.id("mesh/the_head.mesh");
	private static final int BROW = 1;
	private static final int EYE = 2;
	private static final int MOUTH_CLOSED = 3;
	private static final int MOUTH_OPEN = 4;
	private static final RenderType[] TYPES = {
		RenderTypes.entityCutoutCull(MarioHead.id("textures/entity/the_head/skin.png")),
		RenderTypes.entityCutoutCull(MarioHead.id("textures/entity/the_head/cap.png")),
		RenderTypes.entityCutoutCull(MarioHead.id("textures/entity/the_head/iris.png")),
	};
	private final HeadMesh mesh;

	public TheHeadRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.mesh = load(context.getResourceManager());
		this.shadowRadius = 0.0f;
	}

	private static HeadMesh load(ResourceManager resources) {
		try (InputStream in = resources.getResourceOrThrow(MESH).open()) {
			HeadMesh mesh = HeadMesh.read(in.readAllBytes());
			MarioHead.LOG.info("[head] mesh={} lods={} quads={}", MESH, mesh.lods().size(),
				mesh.lods().stream().map(l -> l.stream().mapToInt(s -> s.quads().length / 4).sum()).toList());
			return mesh;
		} catch (IOException e) {
			throw new IllegalStateException("head mesh " + MESH + " unreadable: " + e.getMessage(), e);
		}
	}

	@Override
	public TheHeadRenderState createRenderState() {
		return new TheHeadRenderState();
	}

	@Override
	public void extractRenderState(TheHead head, TheHeadRenderState state, float partialTick) {
		super.extractRenderState(head, state, partialTick);
		double phaseSeconds = head.phaseSeconds(partialTick);
		state.yaw = head.getYRot(partialTick);
		state.pitch = head.getXRot(partialTick);
		state.roll = head.roll(partialTick);
		state.scale = head.squeeze() * HeadPose.spawnScale(head.phase(), phaseSeconds);
		state.mouthOpen = HeadPose.mouthOpen(head.phase(), head.cue(), head.cueSeconds(partialTick), phaseSeconds);
		state.browDegrees = HeadPose.browDegrees(head.phase());
		state.eyeDegrees = HeadPose.eyeDegrees(head.phase(), phaseSeconds);
		state.lod = state.distanceToCameraSq > HeadTuning.LOD_DISTANCE * HeadTuning.LOD_DISTANCE ? 1 : 0;
	}

	@Override
	public void submit(TheHeadRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		poseStack.pushPose();
		poseStack.translate(0.0f, state.boundingBoxHeight / 2.0f, 0.0f);
		poseStack.rotateDegrees(Axis.YP, -state.yaw);
		poseStack.rotateDegrees(Axis.XP, state.pitch);
		poseStack.rotateDegrees(Axis.ZP, state.roll);
		poseStack.scale(state.scale, state.scale, state.scale);
		int hidden = state.mouthOpen ? MOUTH_CLOSED : MOUTH_OPEN;
		for (HeadMesh.Section section : this.mesh.lods().get(Math.min(state.lod, this.mesh.lods().size() - 1))) {
			if (section.part() == hidden) {
				continue;
			}
			poseStack.pushPose();
			if (section.part() == BROW) {
				poseStack.rotateDegrees(Axis.XP, state.browDegrees);
			} else if (section.part() == EYE) {
				poseStack.rotateDegrees(Axis.YP, state.eyeDegrees);
			}
			int light = state.lightCoords;
			collector.submitCustomGeometry(poseStack, TYPES[section.texture()], (pose, buffer) -> emit(section, pose, buffer, light));
			poseStack.popPose();
		}
		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}

	private static void emit(HeadMesh.Section section, PoseStack.Pose pose, VertexConsumer buffer, int light) {
		Matrix4f matrix = pose.pose();
		Vector3f p = new Vector3f();
		Vector3f n = new Vector3f();
		float[] v = section.vertices();
		for (int index : section.quads()) {
			int o = index * HeadMesh.VERTEX_FLOATS;
			matrix.transformPosition(v[o], v[o + 1], v[o + 2], p);
			pose.transformNormal(v[o + 3], v[o + 4], v[o + 5], n);
			int colour = ARGB.colorFromFloat(v[o + 11], v[o + 8], v[o + 9], v[o + 10]);
			buffer.addVertex(p.x, p.y, p.z, colour, v[o + 6], v[o + 7], OverlayTexture.NO_OVERLAY, light, n.x, n.y, n.z);
		}
	}
}
