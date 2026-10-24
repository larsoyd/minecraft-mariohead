package com.larsoyd.mariohead.entity;

import static com.larsoyd.mariohead.entity.HeadTuning.*;

import com.larsoyd.mariohead.HeadEntities;
import com.larsoyd.mariohead.HeadSounds;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class TheHead extends Monster {
	public static final float WIDTH = HeadShape.WIDTH;
	public static final float HEIGHT = HeadShape.HEIGHT;
	private static final double DT = 0.05;
	private static final EntityDataAccessor<Byte> DATA_PHASE = SynchedEntityData.defineId(TheHead.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Integer> DATA_CUE = SynchedEntityData.defineId(TheHead.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Float> DATA_SQUEEZE = SynchedEntityData.defineId(TheHead.class, EntityDataSerializers.FLOAT);
	private static final double[] LOOKAHEAD = { 0.6, 1.2, 1.8 };
	// the client plays his voice through this, the common side cannot see sound instances
	public static BiConsumer<TheHead, HeadCue> voiceListener = (head, cue) -> {
	};

	private @Nullable Player chased;
	private int phaseTicks = -1;
	private int cueTicks = -1;
	private int cueSeq;
	private int revs;
	private double chargeSpeed;
	private double moveSpeed;
	private float moveYaw;
	private @Nullable Path path;
	private int pathIndex;
	private int replanIn;
	private Vec3 chasedLast = Vec3.ZERO;
	private double pace;
	private boolean climbing;
	private boolean planning;
	private boolean wedged;
	private @Nullable Vec3 escape;
	private @Nullable ChunkPos ticketChunk;

	private int clientPhaseTick;
	private int clientCueTick;
	private float roll;
	private float rollO;

	public TheHead(EntityType<? extends TheHead> type, Level level) {
		super(type, level);
		this.setNoGravity(true);
		this.xpReward = 0;
		this.setPathfindingMalus(PathType.WATER, 0.0f);
		this.setPathfindingMalus(PathType.WATER_BORDER, 0.0f);
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
		builder.define(DATA_PHASE, (byte) HeadPhase.SPAWN.ordinal());
		builder.define(DATA_CUE, HeadCue.pack(HeadCue.NONE, 0));
		builder.define(DATA_SQUEEZE, 1.0f);
	}

	// his box is the hull of the whole head, scaled down to squeeze through what he has to
	@Override
	protected EntityDimensions getDefaultDimensions(Pose pose) {
		return super.getDefaultDimensions(pose).scale(this.planning ? MIN_SQUEEZE : this.squeeze());
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
		return HeadPhase.byId(this.entityData.get(DATA_PHASE));
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
		this.entityData.set(DATA_PHASE, (byte) phase.ordinal());
		this.phaseTicks = -1;
	}

	private void setCue(HeadCue cue) {
		this.cueSeq++;
		this.entityData.set(DATA_CUE, HeadCue.pack(cue, this.cueSeq));
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

		Vec3 centre = this.centre();
		boolean straight = centre.distanceTo(target.position()) < GRAB_DISTANCE && this.clearLine(centre, target.getEyePosition());
		if (straight) {
			this.path = null;
			this.moveYaw = Mth.approachDegrees(this.moveYaw, yawTo(centre, target.position()), GRAB_TURN_DEGREES);
			this.moveSpeed = approach(this.moveSpeed, this.chargeSpeed);
		} else {
			if (--this.replanIn <= 0 || this.path == null) {
				this.replan(level, target);
			}
			straight = !this.steer(centre);
			if (straight) {
				this.moveYaw = Mth.approachDegrees(this.moveYaw, yawTo(centre, target.position()), TURN_DEGREES);
				this.moveSpeed = approach(this.moveSpeed, this.chargeSpeed);
			}
		}
		this.fly(target, straight);
		this.faceTowards(this.moveYaw, target);
		if (this.touching(target)) {
			this.caught(level, target);
		}
	}

	private double trackPace(Player target) {
		double step = target.position().subtract(this.chasedLast).horizontalDistance();
		if (step < PACE_TELEPORT) {
			this.pace += (step - this.pace) / PACE_TICKS;
		}
		this.chasedLast = target.position();
		return this.pace;
	}

	private static double approach(double from, double to) {
		return from < to ? Math.min(to, from + ACCEL) : Math.max(to, from - DECEL);
	}

	// shrinks ahead of a tight spot so he is small enough on arrival, grows back where there is room
	private void fitSqueeze(boolean moving) {
		Vec3 c = this.centre();
		float now = this.squeeze();
		float here = HeadRoom.at(this, c.x, c.z, c.y);
		this.wedged = here <= 0.0f;
		if (this.wedged) {
			if (now > MIN_SQUEEZE) {
				this.setSqueeze(MIN_SQUEEZE);
			}
			if (this.escape == null || !HeadRoom.fits(this, this.escape.x, this.escape.y, this.escape.z, MIN_SQUEEZE)) {
				this.escape = HeadRoom.nearestRoom(this, this.centre());
			}
			return;
		}
		this.escape = null;
		float want = here;
		if (moving) {
			double yaw = Math.toRadians(this.moveYaw);
			for (double d : LOOKAHEAD) {
				float ahead = HeadRoom.at(this, c.x - Math.sin(yaw) * d, c.z + Math.cos(yaw) * d, c.y);
				if (ahead > 0.0f) {
					want = Math.min(want, ahead);
				}
			}
		}
		float next = want < now ? Math.max(want, now - SHRINK_RATE) : Math.min(want, now + GROW_RATE);
		if (next > now && !HeadRoom.fits(this, c.x, c.y, c.z, next)) {
			next = now;
		}
		if (next != now) {
			this.setSqueeze(next);
		}
	}

	// plans as the smallest walker on the floor under him, where the walk evaluator starts from
	private void replan(ServerLevel level, Player target) {
		this.pathIndex = 0;
		Vec3 real = this.position();
		boolean grounded = this.onGround();
		double floor = HeadRoom.surface(this, this.centre(), -FLOOR_SEARCH);
		this.planning = true;
		this.resize();
		if (!Double.isNaN(floor)) {
			this.setPos(real.x, floor, real.z);
			this.setOnGround(true);
		}
		this.path = this.getNavigation().createPath(target, 0);
		this.planning = false;
		this.resize();
		this.setPos(real);
		this.setOnGround(grounded);
		this.replanIn = this.path != null && this.path.canReach() ? REPLAN_TICKS : REPLAN_UNREACHABLE_TICKS;
	}

	// follows the path, skips ahead on clear lines, slows in sharp turns, false when used up
	private boolean steer(Vec3 centre) {
		Path p = this.path;
		if (p == null || p.getNodeCount() == 0) {
			return false;
		}
		if (this.pathIndex + 1 < p.getNodeCount() && this.clearLine(centre, nodeAt(p.getNode(this.pathIndex + 1), centre.y))) {
			this.pathIndex++;
		}
		while (this.pathIndex < p.getNodeCount()) {
			boolean last = this.pathIndex == p.getNodeCount() - 1;
			Vec3 node = nodeAt(p.getNode(this.pathIndex), centre.y);
			if (node.subtract(centre).horizontalDistance() > (last ? WAYPOINT_REACH * 0.45 : WAYPOINT_REACH)) {
				break;
			}
			this.pathIndex++;
		}
		if (this.pathIndex >= p.getNodeCount()) {
			return false;
		}
		float want = yawTo(centre, nodeAt(p.getNode(this.pathIndex), centre.y));
		this.moveYaw = Mth.approachDegrees(this.moveYaw, want, TURN_DEGREES);
		float turn = Math.abs(Mth.wrapDegrees(want - this.moveYaw));
		double speed = this.chargeSpeed;
		if (turn > SLOW_TURN_DEGREES) {
			speed *= Math.max(MIN_TURN_SPEED, Math.cos(Math.toRadians(turn)));
		}
		this.moveSpeed = approach(this.moveSpeed, speed);
		return true;
	}

	private static Vec3 nodeAt(Node node, double y) {
		return new Vec3(node.x + 0.5, y, node.z + 0.5);
	}

	private void fly(Player target, boolean straight) {
		double yaw = Math.toRadians(this.moveYaw);
		double dx = -Math.sin(yaw) * this.moveSpeed;
		double dz = Math.cos(yaw) * this.moveSpeed;
		double y = this.centre().y;
		double want = this.hoverHeight(straight ? target.getY() : this.nextFloor());
		if (straight && this.climbing) {
			want = Math.max(want, y + 1.0);
		}
		this.body(dx, Mth.clamp(want - y, -CLIMB, CLIMB), dz);
		this.climbing = this.horizontalCollision;
	}

	// a walk node is where a walker would stand, so its height is the floor he is about to cross
	private double nextFloor() {
		Path p = this.path;
		if (p == null || this.pathIndex >= p.getNodeCount()) {
			return Double.NaN;
		}
		double below = HeadRoom.surface(this, this.centre(), -FLOOR_SEARCH);
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
		double floor = Double.isNaN(floorGiven) ? HeadRoom.surface(this, c, -FLOOR_SEARCH) : floorGiven;
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
		if (p == null || p.isRemoved() || !p.isAlive() || p.isSpectator() || p.level() != level) {
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
		} else if (DATA_PHASE.equals(accessor)) {
			this.clientPhaseTick = this.tickCount;
		} else if (DATA_CUE.equals(accessor)) {
			this.clientCueTick = this.tickCount;
			HeadCue cue = this.cue();
			if (cue.clip() != null && this.tickCount > 0) {
				voiceListener.accept(this, cue);
			}
		}
	}

	public double clientPhaseSeconds(float partialTick) {
		return (this.tickCount - this.clientPhaseTick + partialTick) * DT;
	}

	public double clientCueSeconds(float partialTick) {
		return (this.tickCount - this.clientCueTick + partialTick) * DT;
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
