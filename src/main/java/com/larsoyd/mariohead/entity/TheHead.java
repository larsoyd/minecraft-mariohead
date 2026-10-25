package com.larsoyd.mariohead.entity;

import static com.larsoyd.mariohead.entity.HeadTuning.*;

import com.larsoyd.mariohead.HeadEntities;
import com.larsoyd.mariohead.HeadSounds;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class TheHead extends Monster {
	public static final float WIDTH = HeadShape.WIDTH;
	public static final float HEIGHT = HeadShape.HEIGHT;
	private static final double DT = 0.05;
	// each carries its start time, so late watchers keep in step and a repeat still reads as new
	private static final EntityDataAccessor<Long> DATA_PHASE = SynchedEntityData.defineId(TheHead.class, EntityDataSerializers.LONG);
	private static final EntityDataAccessor<Long> DATA_CUE = SynchedEntityData.defineId(TheHead.class, EntityDataSerializers.LONG);
	private static final EntityDataAccessor<Float> DATA_SQUEEZE = SynchedEntityData.defineId(TheHead.class, EntityDataSerializers.FLOAT);
	private static final double[] LOOKAHEAD = { 0.6, 1.2, 1.8 };
	// the client plays his voice through this, the common side cannot see sound instances
	public static BiConsumer<TheHead, HeadCue> voiceListener = (head, cue) -> {
	};

	private @Nullable Player chased;
	private int phaseTicks = -1;
	private int cueTicks = -1;
	private int revs;
	private double chargeSpeed;
	private double moveSpeed;
	private float moveYaw;
	private @Nullable Path path;
	private int pathIndex;
	private int replanIn;
	private int pathCells = 1;
	private Vec3 chasedLast = Vec3.ZERO;
	private Vec3 chasedVelocity = Vec3.ZERO;
	private double pace;
	private boolean climbing;
	private float planSqueeze;
	private boolean seen;
	private boolean halted;
	private boolean wedged;
	private @Nullable Vec3 escape;
	private @Nullable ChunkPos ticketChunk;

	private float roll;
	private float rollO;

	public TheHead(EntityType<? extends TheHead> type, Level level) {
		super(type, level);
		this.setNoGravity(true);
		this.xpReward = 0;
		this.setPathfindingMalus(PathType.WATER, 0.0f);
		this.setPathfindingMalus(PathType.WATER_BORDER, 0.0f);
		this.setPathfindingMalus(PathType.LEAVES, 0.0f);
	}

	@Override
	protected PathNavigation createNavigation(Level level) {
		return new HoverNavigation(this, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
			.add(Attributes.MAX_HEALTH, 20.0)
			.add(Attributes.FOLLOW_RANGE, PATH_RANGE)
			.add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_PHASE, HeadPhase.pack(HeadPhase.SPAWN, 0L));
		builder.define(DATA_CUE, HeadCue.pack(HeadCue.NONE, 0L));
		builder.define(DATA_SQUEEZE, 1.0f);
	}

	// his box is the hull of the whole head, scaled down to squeeze through what he has to
	@Override
	protected EntityDimensions getDefaultDimensions(Pose pose) {
		return super.getDefaultDimensions(pose).scale(this.planSqueeze > 0.0f ? this.planSqueeze : this.squeeze());
	}

	public float squeeze() {
		return this.entityData.get(DATA_SQUEEZE);
	}

	private void setSqueeze(float squeeze) {
		Vec3 c = this.centre();
		this.entityData.set(DATA_SQUEEZE, squeeze);
		this.resize();
		this.setPos(c.x, c.y - this.getBbHeight() / 2.0, c.z);
	}

	// a refresh without the vanilla nudge, which would move him off his centre
	private void resize() {
		boolean ghost = this.noPhysics;
		this.noPhysics = true;
		this.refreshDimensions();
		this.noPhysics = ghost;
	}

	public HeadPhase phase() {
		return HeadPhase.phaseOf(this.entityData.get(DATA_PHASE));
	}

	public HeadCue cue() {
		return HeadCue.cueOf(this.entityData.get(DATA_CUE));
	}

	public int revs() {
		return this.revs;
	}

	public double chargeSpeed() {
		return this.chargeSpeed;
	}

	public @Nullable Player chased() {
		return this.chased;
	}

	public void chase(Player player) {
		this.chased = player;
		this.chasedLast = player.position();
	}

	public Vec3 centre() {
		return this.position().add(0.0, this.getBbHeight() / 2.0, 0.0);
	}

	private void setPhase(HeadPhase phase) {
		this.entityData.set(DATA_PHASE, HeadPhase.pack(phase, this.level().getGameTime()));
		this.phaseTicks = -1;
	}

	private void setCue(HeadCue cue) {
		this.entityData.set(DATA_CUE, HeadCue.pack(cue, this.level().getGameTime()));
		this.cueTicks = -1;
	}

	private static double clipSeconds(HeadCue cue) {
		HeadVoice.Clip clip = cue.clip();
		return clip == null ? INHALE_SECONDS : (double) clip.frames() / HeadVoice.MOUTH_FPS;
	}

	// how many ticks something lasting this long holds for
	static int ticks(double seconds) {
		return (int) Math.ceil(seconds / DT - 1e-9);
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		this.phaseTicks++;
		this.cueTicks++;
		Player target = this.validTarget(level);
		HeadPhase phase = this.phase();
		this.seen = HeadSight.seen(level, this.getBoundingBox());
		this.fitSqueeze(phase == HeadPhase.CHARGE);
		if (target == null && (phase == HeadPhase.TALK || phase == HeadPhase.CHARGE)) {
			this.vanish(level);
			return;
		}
		switch (phase) {
			case SPAWN -> {
				if (this.phaseTicks == 0) {
					this.puff(level);
					if (target != null) {
						this.faceInstantly(target);
					}
				}
				this.hover();
				if (this.phaseTicks + 1 >= ticks(SPAWN_SECONDS)) {
					this.setPhase(HeadPhase.LOOK);
				}
			}
			case LOOK -> {
				float sway = (float) Math.sin(this.phaseTicks * DT * SWAY_RATE) * SWAY_DEGREES_PER_TICK;
				this.setFacing(this.getYRot() - sway, this.getXRot());
				this.hover();
				if (target == null) {
					this.acquire(level);
				} else if (this.phaseTicks + 1 >= ticks(LOOK_SECONDS)) {
					this.setPhase(HeadPhase.TALK);
					this.setCue(HeadCue.HELLO);
				}
			}
			case TALK -> {
				this.faceTowards(yawTo(this.centre(), target.position()), target);
				this.hover();
				if (this.cueTicks + 1 >= ticks(clipSeconds(HeadCue.HELLO))) {
					this.setPhase(HeadPhase.CHARGE);
					this.revs = -1;
					this.moveYaw = this.getYRot();
					this.setCue(HeadCue.INHALE);
				}
			}
			case CHARGE -> this.charge(level, target);
		}
		if (target != null && (this.tickCount % TICKET_REFRESH == 0 || !this.chunkPosition().equals(this.ticketChunk))) {
			this.ticketChunk = this.chunkPosition();
			level.getChunkSource().addTicketWithRadius(HeadEntities.CHASE_TICKET, this.ticketChunk, TICKET_RADIUS);
		}
	}

	private void charge(ServerLevel level, Player target) {
		HeadCue cue = this.cue();
		if (cue == HeadCue.INHALE && this.cueTicks >= ticks(INHALE_SECONDS)) {
			this.revs++;
			this.setCue(HeadCue.hum(this.revs));
		} else if (cue != HeadCue.INHALE && this.cueTicks >= ticks(clipSeconds(cue))) {
			this.setCue(HeadCue.INHALE);
		}
		cue = this.cue();
		double sag = cue == HeadCue.INHALE ? 1.0 : Math.clamp(this.cueTicks * DT / clipSeconds(cue), 0.0, 1.0);
		this.chargeSpeed = RevSpring.speed(this.revs, sag, this.trackPace(target));
		Vec3 goal = this.lead(target);

		Vec3 centre = this.centre();
		boolean straight = centre.distanceTo(target.position()) < GRAB_DISTANCE && this.clearLine(centre, target.getEyePosition());
		if (straight) {
			this.path = null;
			this.moveYaw = Mth.approachDegrees(this.moveYaw, yawTo(centre, target.position()), GRAB_TURN_DEGREES);
			this.moveSpeed = approach(this.moveSpeed, this.chargeSpeed);
		} else {
			if (--this.replanIn <= 0 || this.path == null) {
				this.replan(goal);
			}
			straight = !this.steer(centre, goal);
			if (straight) {
				this.moveYaw = Mth.approachDegrees(this.moveYaw, yawTo(centre, goal), TURN_DEGREES);
				this.moveSpeed = approach(this.moveSpeed, this.chargeSpeed);
			}
		}
		if (this.halted) {
			this.moveSpeed = approach(this.moveSpeed, 0.0);
		}
		this.fly(target, straight);
		this.faceTowards(this.moveYaw, target);
		if (this.touching(target)) {
			this.caught(level, target);
		}
	}

	private double trackPace(Player target) {
		Vec3 step = target.position().subtract(this.chasedLast).multiply(1.0, 0.0, 1.0);
		if (step.length() < PACE_TELEPORT) {
			this.pace += (step.length() - this.pace) / PACE_TICKS;
			this.chasedVelocity = this.chasedVelocity.add(step.subtract(this.chasedVelocity).scale(1.0 / LEAD_SMOOTH_TICKS));
		}
		this.chasedLast = target.position();
		return this.pace;
	}

	// where the player is running to, led only while they run away from him
	private Vec3 lead(Player target) {
		Vec3 at = target.position();
		Vec3 away = at.subtract(this.centre()).multiply(1.0, 0.0, 1.0);
		if (this.chasedVelocity.dot(away) <= 0.0) {
			return at;
		}
		double ticks = Math.min(LEAD_TICKS, away.length() / Math.max(this.moveSpeed, SPEED_START));
		Vec3 lift = new Vec3(0.0, target.getBbHeight() / 2.0, 0.0);
		Vec3 ahead = at.add(this.chasedVelocity.scale(ticks)).add(lift);
		HitResult hit = this.level().clip(new ClipContext(at.add(lift), ahead, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, target));
		return (hit.getType() == HitResult.Type.MISS ? ahead : hit.getLocation()).subtract(lift);
	}

	private static double approach(double from, double to) {
		return from < to ? Math.min(to, from + ACCEL) : Math.max(to, from - DECEL);
	}

	// he only changes size where nobody sees it, so in view a gap too small for him holds him back
	private void fitSqueeze(boolean moving) {
		Vec3 c = this.centre();
		float now = this.squeeze();
		float here = HeadRoom.at(this, c.x, c.z, c.y);
		this.halted = false;
		this.wedged = !HeadRoom.fits(this, c.x, c.y, c.z, now);
		if (this.wedged) {
			float size = this.seen ? now : Math.max(MIN_SQUEEZE, Math.min(now, here));
			if (size < now) {
				this.setSqueeze(size);
			}
			this.wedged = !HeadRoom.fits(this, c.x, c.y, c.z, size);
			if (this.wedged && (this.escape == null || !HeadRoom.fits(this, this.escape.x, this.escape.y, this.escape.z, size))) {
				this.escape = HeadRoom.nearestRoom(this, this.centre(), size, this.towardChased());
			}
			return;
		}
		this.escape = null;
		List<Vec3> ahead = moving ? this.ahead(c) : List.of();
		boolean tight = !this.fitsAhead(ahead, c.y, now);
		if (this.seen) {
			this.halted = tight;
			return;
		}
		float next = now;
		if (tight) {
			float lo = MIN_SQUEEZE;
			float hi = now;
			for (int i = 0; i < SIZE_SEARCH_STEPS; i++) {
				float mid = (lo + hi) / 2.0f;
				if (this.fitsAhead(ahead, c.y, mid)) {
					lo = mid;
				} else {
					hi = mid;
				}
			}
			next = Math.max(MIN_SQUEEZE, lo - FIT_MARGIN);
		} else if (now < here) {
			float grown = Math.min(here, now + SQUEEZE_STEP);
			if (HeadRoom.fits(this, c.x, c.y, c.z, grown) && this.fitsAhead(ahead, c.y, grown)) {
				next = grown;
			}
		}
		if (next != now) {
			this.setSqueeze(next);
		}
	}

	private Vec3 towardChased() {
		return this.chased == null ? Vec3.ZERO : this.chased.position().subtract(this.centre()).multiply(1.0, 0.0, 1.0).normalize();
	}

	// the close nodes of his path at their floors, or points straight ahead at his height when he has none
	private List<Vec3> ahead(Vec3 c) {
		Path p = this.path;
		List<Vec3> points = new ArrayList<>();
		if (p != null && this.pathIndex < p.getNodeCount()) {
			for (int i = this.pathIndex; i < p.getNodeCount() && points.size() < AHEAD_NODES; i++) {
				Vec3 node = this.nodeAt(p.getNode(i), p.getNode(i).y);
				if (node.subtract(c).horizontalDistance() > AHEAD_REACH) {
					break;
				}
				points.add(node);
			}
			return points;
		}
		double yaw = Math.toRadians(this.moveYaw);
		for (double d : LOOKAHEAD) {
			points.add(new Vec3(c.x - Math.sin(yaw) * d, Double.NaN, c.z + Math.cos(yaw) * d));
		}
		return points;
	}

	// a node is tried where he would float over its floor, a point ahead at his own height
	private boolean fitsAhead(List<Vec3> ahead, double y, float size) {
		for (Vec3 point : ahead) {
			double at = Double.isNaN(point.y) ? y : point.y + HOVER * size;
			if (!HeadRoom.fitsNear(this, point.x, point.z, at, size, this.moveYaw)) {
				return false;
			}
		}
		return true;
	}

	// a route at the size he has now, or a squeeze route when there is none or it is a long way round
	private void replan(Vec3 goal) {
		BlockPos to = BlockPos.containing(goal);
		float size = this.squeeze();
		Vec3 from = this.planStart();
		Path route = this.planAt(from, to, size);
		if (size > MIN_SQUEEZE && (!reaches(route) || !this.seen && this.longWay(route, goal))) {
			Path small = this.planAt(from, to, MIN_SQUEEZE);
			if (reaches(small) && (!reaches(route) || small.getNodeCount() * 2 < route.getNodeCount())) {
				route = small;
				size = MIN_SQUEEZE;
			}
		}
		this.path = route;
		this.pathCells = cells(size);
		this.pathIndex = 0;
		// a fresh path starts at a node he may already be past, so he does not turn back for it
		Vec3 c = this.centre();
		while (route != null && this.pathIndex + 1 < route.getNodeCount()
			&& this.nodeAt(route.getNode(this.pathIndex + 1), c.y).distanceTo(c) <= this.nodeAt(route.getNode(this.pathIndex), c.y).distanceTo(c)) {
			this.pathIndex++;
		}
		this.replanIn = reaches(route) ? REPLAN_TICKS : REPLAN_UNREACHABLE_TICKS;
	}

	// the node he is heading for while he can fly straight to it, so a new route carries on from there
	private @Nullable Vec3 planStart() {
		Path p = this.path;
		Vec3 c = this.centre();
		if (p != null && this.pathIndex < p.getNodeCount()) {
			Node node = p.getNode(this.pathIndex);
			Vec3 at = this.nodeAt(node, c.y);
			if (at.distanceTo(c) < PULL_REACH && HeadRoom.sweep(this, c, at, this.squeeze())) {
				return new Vec3(node.x + 0.5, node.y, node.z + 0.5);
			}
		}
		double floor = HeadRoom.surface(this, c, -FLOOR_SEARCH);
		return Double.isNaN(floor) ? null : new Vec3(c.x, floor, c.z);
	}

	// plans as a walker of that size standing at from, where the walk evaluator starts
	private @Nullable Path planAt(@Nullable Vec3 from, BlockPos to, float size) {
		Vec3 real = this.position();
		boolean grounded = this.onGround();
		this.planSqueeze = size <= MIN_SQUEEZE ? size : size + PLAN_MARGIN / WIDTH;
		this.resize();
		if (from != null) {
			this.setPos(from);
			this.setOnGround(true);
		}
		Path route = this.getNavigation().createPath(to, cells(size) - 1);
		this.planSqueeze = 0.0f;
		this.resize();
		this.setPos(real);
		this.setOnGround(grounded);
		return route;
	}

	private static boolean reaches(@Nullable Path route) {
		return route != null && route.canReach();
	}

	private boolean longWay(@Nullable Path route, Vec3 goal) {
		return reaches(route) && route.getNodeCount() > goal.subtract(this.centre()).horizontalDistance() * LONG_WAY + LONG_WAY_SLACK;
	}

	// the planner wants a cell of slack, he cannot line a full box up to a hundredth of a block
	private static int cells(float size) {
		return size <= MIN_SQUEEZE ? 1 : Mth.floor(WIDTH * size + PLAN_MARGIN + 1.0f);
	}

	// heads for the farthest node his box flies straight to, false once the path is used up
	private boolean steer(Vec3 centre, Vec3 goal) {
		Path p = this.path;
		if (p == null || this.pathIndex >= p.getNodeCount()) {
			return false;
		}
		int flat = this.pathIndex;
		while (flat + 1 < p.getNodeCount() && p.getNode(flat + 1).y == p.getNode(this.pathIndex).y
			&& this.nodeAt(p.getNode(flat + 1), centre.y).distanceTo(centre) < PULL_REACH) {
			flat++;
		}
		for (int i = flat; i > this.pathIndex; i--) {
			if (HeadRoom.sweep(this, centre, this.nodeAt(p.getNode(i), centre.y), this.squeeze())) {
				this.pathIndex = i;
				break;
			}
		}
		while (this.pathIndex < p.getNodeCount()) {
			boolean last = this.pathIndex == p.getNodeCount() - 1;
			Vec3 node = this.nodeAt(p.getNode(this.pathIndex), centre.y);
			if (node.subtract(centre).horizontalDistance() > (last ? WAYPOINT_REACH * 0.45 : WAYPOINT_REACH)) {
				break;
			}
			this.pathIndex++;
		}
		if (this.pathIndex >= p.getNodeCount()) {
			return false;
		}
		// the last node is a grid cell, the player is the real end of it
		Vec3 aim = this.pathIndex == p.getNodeCount() - 1 && p.canReach() ? goal : this.nodeAt(p.getNode(this.pathIndex), centre.y);
		float want = yawTo(centre, aim);
		this.moveYaw = Mth.approachDegrees(this.moveYaw, want, TURN_DEGREES);
		float turn = Math.abs(Mth.wrapDegrees(want - this.moveYaw));
		double speed = this.chargeSpeed;
		if (turn > SLOW_TURN_DEGREES) {
			speed *= Math.max(MIN_TURN_SPEED, Math.cos(Math.toRadians(turn)));
		}
		this.moveSpeed = approach(this.moveSpeed, speed);
		return true;
	}

	private Vec3 nodeAt(Node node, double y) {
		double middle = HeadNodeEvaluator.centre(this.pathCells);
		return new Vec3(node.x + middle, y, node.z + middle);
	}

	// rises before he pushes on when the floor ahead is higher than he floats, so a step never stalls him
	private void fly(Player target, boolean straight) {
		double yaw = Math.toRadians(this.moveYaw);
		double y = this.centre().y;
		double want = this.hoverHeight(straight ? target.getY() : this.nextFloor());
		if (this.climbing) {
			want = Math.max(want, y + 1.0);
		}
		Vec3 ahead = this.centre().add(-Math.sin(yaw) * AHEAD_SETTLE, 0.0, Math.cos(yaw) * AHEAD_SETTLE);
		double fit = HeadRoom.settle(this, ahead, want, this.squeeze());
		if (!Double.isNaN(fit)) {
			want = fit;
		}
		double speed = this.moveSpeed * Mth.clamp(1.0 - (want - y - CLIMB_FREE) / CLIMB_SLOW, MIN_TURN_SPEED, 1.0);
		double dx = -Math.sin(yaw) * speed;
		double dz = Math.cos(yaw) * speed;
		this.body(dx, Mth.clamp(want - y, -CLIMB, CLIMB), dz);
		Vec3 c = this.centre();
		// a wall he flies up when he has no path, a step only when a block higher clears it
		this.climbing = this.horizontalCollision && (straight || HeadRoom.fits(this, c.x + dx, c.y + STEP_UP, c.z + dz, this.squeeze()));
	}

	// a walk node is where a walker would stand, so its height is the floor he is about to cross
	private double nextFloor() {
		Path p = this.path;
		if (p == null || this.pathIndex >= p.getNodeCount()) {
			return Double.NaN;
		}
		double below = HeadRoom.surface(this, this.centre(), -FLOOR_SEARCH, this.squeeze());
		double node = p.getNode(this.pathIndex).y;
		return Double.isNaN(below) ? node : Math.max(below, node);
	}

	private void hover() {
		this.body(0.0, Mth.clamp(this.hoverHeight(Double.NaN) - this.centre().y, -CLIMB, CLIMB), 0.0);
	}

	// blocks stop him, unless something was built into him and he squirms out to the nearest open air
	private void body(double dx, double dy, double dz) {
		Vec3 before = this.position();
		if (this.wedged) {
			Vec3 way = this.escape == null ? Vec3.ZERO : this.escape.subtract(this.centre());
			double step = Math.min(way.length(), ESCAPE_SPEED);
			way = step > 1e-6 ? way.normalize().scale(step) : Vec3.ZERO;
			dx = way.x;
			dy = way.y;
			dz = way.z;
		}
		// a chunk still loading would stop him ticking for good, so he waits at its edge
		if (!((ServerLevel) this.level()).isPositionEntityTicking(BlockPos.containing(before.x + dx, before.y + dy, before.z + dz))) {
			dx = 0.0;
			dz = 0.0;
		}
		this.noPhysics = this.wedged;
		this.move(MoverType.SELF, new Vec3(dx, dy, dz));
		this.noPhysics = false;
		this.setDeltaMovement(this.position().subtract(before));
	}

	// his centre the hover height over the floor below, or over the given floor, kept off the ceiling
	private double hoverHeight(double floorGiven) {
		Vec3 c = this.centre();
		float s = this.squeeze();
		double floor = Double.isNaN(floorGiven) ? HeadRoom.surface(this, c, -FLOOR_SEARCH, s) : floorGiven;
		if (Double.isNaN(floor)) {
			floor = this.chased != null ? this.chased.getY() : c.y - HOVER * s;
		}
		double bob = Math.sin(this.tickCount * DT * BOB_RATE) * BOB_BLOCKS * s;
		double y = HeadRoom.settle(this, c, floor + HOVER * s + bob, s);
		return Double.isNaN(y) ? c.y : y;
	}

	private boolean clearLine(Vec3 from, Vec3 to) {
		return HeadRoom.clearLine(this, this.level(), from, to);
	}

	// his hull against the player's, with a line between their middles so walls stop him
	private boolean touching(Player target) {
		Vec3 c = this.centre();
		double reach = WIDTH * this.squeeze() / 2.0 + target.getBbWidth() / 2.0 + CONTACT_REACH;
		double dx = c.x - target.getX();
		double dz = c.z - target.getZ();
		double tall = HEIGHT * this.squeeze() / 2.0 + target.getBbHeight() / 2.0 + CONTACT_REACH;
		return dx * dx + dz * dz < reach * reach
			&& Math.abs(c.y - (target.getY() + target.getBbHeight() / 2.0)) < tall
			&& this.clearLine(c, target.getEyePosition());
	}

	private void caught(ServerLevel level, Player target) {
		DamageSource source = this.damageSources().source(HeadEntities.CAUGHT, this);
		target.hurtServer(level, source, Float.MAX_VALUE);
		this.vanish(level);
	}

	private void vanish(ServerLevel level) {
		this.puff(level);
		this.discard();
	}

	private void puff(ServerLevel level) {
		Vec3 c = this.centre();
		level.sendParticles(ParticleTypes.POOF, c.x, c.y, c.z, 24, 0.7, 0.7, 0.7, 0.02);
		level.playSound(null, c.x, c.y, c.z, HeadSounds.APPEAR, SoundSource.HOSTILE, 1.0f, 1.0f);
	}

	private void acquire(ServerLevel level) {
		Player near = level.getNearestPlayer(this.getX(), this.getY(), this.getZ(), ACQUIRE_RANGE, EntitySelector.NO_CREATIVE_OR_SPECTATOR);
		if (near != null) {
			this.chase(near);
		}
	}

	private @Nullable Player validTarget(ServerLevel level) {
		Player p = this.chased;
		if (p == null || p.isRemoved() || !p.isAlive() || p.isSpectator() || p.isCreative() || p.level() != level) {
			this.chased = null;
			return null;
		}
		return p;
	}

	private static float yawTo(Vec3 from, Vec3 to) {
		return (float) (Mth.atan2(to.z - from.z, to.x - from.x) * Mth.RAD_TO_DEG) - 90.0f;
	}

	private void faceInstantly(Player target) {
		this.moveYaw = yawTo(this.centre(), target.position());
		this.setFacing(this.moveYaw, 0.0f);
	}

	private void faceTowards(float yaw, Player target) {
		float pitch = (float) Mth.clamp((this.centre().y - (target.getY() + 1.2)) * PITCH_PER_BLOCK, -PITCH_MAX, PITCH_MAX);
		this.setFacing(Mth.approachDegrees(this.getYRot(), yaw, TURN_DEGREES),
			Mth.approach(this.getXRot(), pitch, PITCH_RATE));
	}

	private void setFacing(float yaw, float pitch) {
		this.setYRot(yaw);
		this.setYBodyRot(yaw);
		this.setYHeadRot(yaw);
		this.setXRot(pitch);
	}

	@Override
	public void tick() {
		super.tick();
		if (this.level().isClientSide()) {
			float turn = Mth.wrapDegrees(this.getYRot() - this.yRotO);
			this.rollO = this.roll;
			this.roll = Mth.approach(this.roll, Mth.clamp(turn * ROLL_PER_TURN, -ROLL_MAX, ROLL_MAX), ROLL_RATE);
		}
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
		super.onSyncedDataUpdated(accessor);
		if (!this.level().isClientSide()) {
			return;
		}
		if (DATA_SQUEEZE.equals(accessor)) {
			this.refreshDimensions();
		} else if (DATA_CUE.equals(accessor)) {
			HeadCue cue = this.cue();
			if (cue.clip() != null && HeadClock.voiceOnTime(this.level().getGameTime(), HeadCue.timeOf(this.entityData.get(DATA_CUE)))) {
				voiceListener.accept(this, cue);
			}
		}
	}

	public double phaseSeconds(float partialTick) {
		return HeadClock.seconds(this.level().getGameTime(), HeadPhase.timeOf(this.entityData.get(DATA_PHASE)), partialTick);
	}

	public double cueSeconds(float partialTick) {
		return HeadClock.seconds(this.level().getGameTime(), HeadCue.timeOf(this.entityData.get(DATA_CUE)), partialTick);
	}

	public float roll(float partialTick) {
		return Mth.lerp(partialTick, this.rollO, this.roll);
	}

	@Override
	public void travel(Vec3 input) {
	}

	@Override
	public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
		return !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean isPushedByFluid() {
		return false;
	}

	@Override
	protected void pushEntities() {
	}

	@Override
	public boolean canBeLeashed() {
		return false;
	}

	@Override
	public boolean removeWhenFarAway(double distSqr) {
		return false;
	}

	@Override
	public boolean canBreatheUnderwater() {
		return true;
	}

	@Override
	public boolean canUsePortal(boolean ignorePassenger) {
		return false;
	}

	@Override
	public boolean isAffectedByPotions() {
		return false;
	}
}
