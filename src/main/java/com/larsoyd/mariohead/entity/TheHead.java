package com.larsoyd.mariohead.entity;

import static com.larsoyd.mariohead.entity.HeadTuning.*;

import com.larsoyd.mariohead.HeadEntities;
import com.larsoyd.mariohead.HeadSounds;
import com.larsoyd.mariohead.MarioHead;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.HashSet;
import java.util.Set;
import com.larsoyd.mariohead.mixin.ChunkMapAccess;
import com.larsoyd.mariohead.mixin.TrackedEntityAccess;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerPlayerConnection;
import net.minecraft.sounds.SoundEvent;
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
import net.minecraft.world.entity.ai.control.LookControl;
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
	private static final EntityDataAccessor<Boolean> DATA_DIGGING = SynchedEntityData.defineId(TheHead.class, EntityDataSerializers.BOOLEAN);
	// the blast is all flash and bang, the tunnel is cut separately and nobody gets hurt
	private static final ExplosionDamageCalculator BLAST = new ExplosionDamageCalculator() {
		@Override
		public boolean shouldDamageEntity(Explosion explosion, Entity entity) {
			return false;
		}

		@Override
		public float getKnockbackMultiplier(Entity entity) {
			return 0.0f;
		}
	};
	private static final double[] LOOKAHEAD = { 0.6, 1.2, 1.8 };
	// the client plays his voice through this, the common side cannot see sound instances
	public static BiConsumer<TheHead, HeadCue> voiceListener = (head, cue) -> {
	};

	private @Nullable Player chased;
	private int phaseTicks = -1;
	private int cueTicks = -1;
	private int revs;
	private final ChaseTempo tempo = new ChaseTempo();
	private final HeadSpeech speech = new HeadSpeech();
	private HeadCue pendingVoice = HeadCue.NONE;
	private boolean teasing;
	private boolean tauntRolled;
	private long tauntReadyAt;
	private double dashBoost = 1.0;
	private double chargeSpeed;
	private double moveSpeed;
	private float moveYaw;
	private double aimDistance;
	private @Nullable Path path;
	private int pathIndex;
	private int replanIn;
	private int pathCells = 1;
	private boolean cellRoute;
	private Vec3 chasedLast = Vec3.ZERO;
	private Vec3 tempoLast = Vec3.ZERO;
	private Vec3 chasedVelocity = Vec3.ZERO;
	private double pace;
	private boolean climbing;
	private float planSqueeze;
	private boolean seen;
	private boolean halted;
	private boolean wedged;
	private int blockedTicks;
	private boolean sealed;
	private long sealedSince = -1L;
	private boolean burrowing;
	private boolean digging;
	private boolean roofed;
	private boolean fitsWay = true;
	private int stalledBlasts;
	private @Nullable Vec3 lastBlastAt;
	private boolean announced;
	private @Nullable Vec3 escape;
	private @Nullable ChunkPos ticketChunk;

	private float roll;
	private float rollO;

	public TheHead(EntityType<? extends TheHead> type, Level level) {
		super(type, level);
		this.lookControl = new LookControl(this) {
			@Override
			protected boolean resetXRotOnTick() {
				return false;
			}
		};
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
		builder.define(DATA_DIGGING, false);
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
		float was = this.squeeze();
		if (squeeze < was || squeeze == 1.0f && was < 1.0f) {
			MarioHead.LOG.info("[head] size id={} from={} to={} seen={} at={}", this.getId(), String.format("%.2f", was), String.format("%.2f", squeeze),
				this.seen, this.blockPosition().toShortString());
		}
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

	public boolean burrowing() {
		return this.burrowing;
	}

	public boolean digging() {
		return this.entityData.get(DATA_DIGGING);
	}

	public @Nullable Player chased() {
		return this.chased;
	}

	public void chase(Player player) {
		this.chased = player;
		this.chasedLast = player.position();
		this.tempoLast = player.position();
	}

	public Vec3 centre() {
		return this.position().add(0.0, this.getBbHeight() / 2.0, 0.0);
	}

	private void setPhase(HeadPhase phase) {
		this.entityData.set(DATA_PHASE, HeadPhase.pack(phase, this.level().getGameTime()));
		this.phaseTicks = -1;
	}

	private void setCue(HeadCue cue) {
		long now = this.level().getGameTime();
		if (cue.clip() != null && !this.speech.canStart(cue, now)) {
			this.pendingVoice = cue;
			return;
		}
		this.speech.started(cue, now);
		if (cue.taunt()) {
			this.tauntReadyAt = now + (cue.clip().millis() + 49) / 50 + HeadClock.LATE_VOICE_TICKS + 600;
		}
		if (this.pendingVoice == cue) {
			this.pendingVoice = HeadCue.NONE;
		}
		this.entityData.set(DATA_CUE, HeadCue.pack(cue, this.level().getGameTime()));
		this.cueTicks = -1;
		if (cue.clip() != null && this.level() instanceof ServerLevel level) {
			this.voiceBeyondSight(level, cue);
			if (cue == HeadCue.UNDERGROUND) {
				level.getServer().getPlayerList().broadcastSystemMessage(Component.translatable("chat.mariohead.speech",
					this.getDisplayName(), Component.translatable("chat.mariohead.the_head.underground")), false);
			}
		}
	}

	// a client that does not track him cannot play his voice, so it gets the sound sent with his reach
	private void voiceBeyondSight(ServerLevel level, HeadCue cue) {
		Object tracked = ((ChunkMapAccess) level.getChunkSource().chunkMap).mariohead$entityMap().get(this.getId());
		Set<ServerPlayerConnection> seenBy = tracked == null ? Set.of() : ((TrackedEntityAccess) tracked).mariohead$seenBy();
		Vec3 c = this.centre();
		SoundEvent sound = this.digging && !cue.spoken() ? HeadSounds.loud(cue) : HeadSounds.of(cue);
		for (ServerPlayer player : level.players()) {
			if (player.distanceToSqr(c) < VOICE_REACH * VOICE_REACH && !seenBy.contains(player.connection)) {
				player.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound), SoundSource.HOSTILE,
					c.x, c.y, c.z, (float) (VOICE_REACH / 16.0), 1.0f, this.random.nextLong()));
			}
		}
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
		if (target != null) {
			boolean onFoot = !target.isPassenger() && !target.isInWater() && !target.isFallFlying()
				&& (target.onGround() || !Double.isNaN(HeadRoom.surface(this, target.position().add(0, 0.05, 0), -1.55)));
			this.tempo.observe(target.getRootVehicle() instanceof net.minecraft.world.entity.vehicle.boat.AbstractBoat,
				target.isFallFlying(), onFoot, target.position().subtract(this.tempoLast).horizontalDistance());
			this.tempoLast = target.position();
		}
		HeadPhase phase = this.phase();
		this.seen = HeadSight.seen(level, this.getBoundingBox());
		if (this.burrowing) {
			this.sinkOutOfSight();
		} else if (this.digging) {
			this.wedged = false;
			this.halted = false;
		} else {
			this.fitSqueeze(phase == HeadPhase.CHARGE);
		}
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
		this.trackPace(target);
		this.tryTaunt(target, this.random.nextDouble());
		HeadCue cue = this.cue();
		HeadCue before = cue;
		if (this.pendingVoice != HeadCue.NONE) {
			this.setCue(this.pendingVoice);
		} else if (cue == HeadCue.INHALE && this.cueTicks >= ticks(INHALE_SECONDS)
			&& Math.abs(this.getXRot() - this.pitchTo(target)) <= PITCH_RATE
			&& this.speech.canStart(HeadCue.hum(this.revs + 1), level.getGameTime())) {
			this.revs++;
			this.tempo.dash();
			this.dashBoost = this.teasing ? 1.1 : 1.0;
			this.teasing = false;
			this.setCue(HeadCue.hum(this.revs));
		} else if (cue != HeadCue.INHALE && this.cueTicks >= ticks(this.dashSeconds(cue))) {
			if (!this.teasing && !cue.spoken()) {
				this.tauntRolled = false;
			}
			this.setCue(HeadCue.INHALE);
		}
		cue = this.cue();
		double sag = cue == HeadCue.INHALE ? 1.0 : Math.clamp(this.cueTicks * DT / this.dashSeconds(cue), 0.0, 1.0);
		double boost = cue.clip() != null && !cue.spoken() ? this.dashBoost : 1.0;
		this.chargeSpeed = this.teasing ? this.pace : RevSpring.speed(this.revs, sag, this.pace) * this.tempo.multiplier() * boost;
		Vec3 goal = this.lead(target);

		Vec3 centre = this.centre();
		boolean underway = this.burrowing || this.digging;
		Vec3 flightGoal = goal.add(0.0, HOVER * this.squeeze(), 0.0);
		boolean openFlight = !underway && centre.distanceTo(flightGoal) <= PATH_RANGE
			&& HeadRoom.sweep(this, centre, flightGoal, this.squeeze());
		boolean straight = openFlight || !underway && centre.distanceTo(target.position()) < GRAB_DISTANCE && this.clearLine(centre, target.getEyePosition());
		if (straight) {
			this.path = null;
			if (openFlight) {
				this.halted = false;
				this.sealed = false;
				this.fitsWay = true;
				this.roofed = this.underRoof(target);
			}
			Vec3 at = centre.distanceTo(target.position()) < GRAB_DISTANCE ? this.closeIn(target) : goal;
			this.moveYaw = Mth.approachDegrees(this.moveYaw, yawTo(centre, at), GRAB_TURN_DEGREES);
			this.moveSpeed = approach(this.moveSpeed, this.chargeSpeed);
			this.aimDistance = at.subtract(centre).horizontalDistance();
		} else {
			if (--this.replanIn <= 0 || this.path == null && !underway) {
				this.replan(goal, target);
			}
			straight = !underway && !this.steer(centre, goal);
			if (straight) {
				this.moveYaw = Mth.approachDegrees(this.moveYaw, yawTo(centre, goal), TURN_DEGREES);
				this.moveSpeed = approach(this.moveSpeed, this.chargeSpeed);
				this.aimDistance = goal.subtract(centre).horizontalDistance();
			}
		}
		if (this.halted) {
			this.moveSpeed = approach(this.moveSpeed, 0.0);
		}
		if (this.teasing) {
			this.moveSpeed = Math.min(this.pace, Math.max(0.0, target.position().subtract(centre).horizontalDistance() - 3.0));
		}
		this.faceTowards(this.moveYaw, target);
		if (this.burrowing) {
			this.burrow(target, cue);
		} else if (this.digging) {
			this.dig(level, target, cue, cue != before && cue.clip() != null && !cue.spoken());
		} else {
			this.fly(target, straight);
		}
		if (!this.teasing && this.touching(target, centre)) {
			this.caught(level, target);
			return;
		}
		this.watchForSeal(level);
	}

	private double dashSeconds(HeadCue cue) {
		return clipSeconds(cue) * (cue.clip() != null && !cue.spoken() ? this.tempo.multiplier() * this.dashBoost : 1.0);
	}

	private boolean tryTaunt(Player target, double chance) {
		if (this.teasing || this.tauntRolled || this.pendingVoice != HeadCue.NONE || this.burrowing || this.digging
			|| this.level().getGameTime() < this.tauntReadyAt || target.isPassenger() || target.isInWater() || target.isFallFlying()) {
			return false;
		}
		Vec3 centre = this.centre();
		double floor = HeadRoom.surface(this, target.position().add(0.0, 0.05, 0.0), -1.55);
		boolean onLand = target.onGround() || !Double.isNaN(floor);
		double gap = target.position().subtract(centre).horizontalDistance();
		if (!ChaseTempo.canTaunt(this.phaseTicks, onLand, gap, 0.0)) {
			return false;
		}
		Vec3 goal = new Vec3(target.getX(), (Double.isNaN(floor) ? target.getY() : floor) + HOVER * this.squeeze(), target.getZ());
		if (Math.abs(goal.y - centre.y) > 2.0 || !HeadRoom.sweep(this, centre, goal, this.squeeze())) {
			return false;
		}
		this.tauntRolled = true;
		if (!ChaseTempo.canTaunt(this.phaseTicks, onLand, gap, chance)) {
			return false;
		}
		this.teasing = true;
		this.setCue(HeadCue.taunt(this.random.nextInt(TauntVoice.CLIPS.length)));
		return true;
	}

	// sealed in or under rock with no way in at his size, he says so once and makes his own way
	private void watchForSeal(ServerLevel level) {
		long now = level.getGameTime();
		boolean stuck = this.sealed || this.roofed && !this.fitsWay;
		if (!stuck) {
			this.sealedSince = -1L;
		} else if (this.sealedSince < 0L) {
			this.sealedSince = now;
		}
		if (!this.burrowing && !this.digging && this.sealedSince >= 0L && now - this.sealedSince >= ticks(SEAL_SECONDS)) {
			this.path = null;
			if (!this.announced) {
				this.announced = true;
				this.setCue(HeadCue.UNDERGROUND);
			}
			boolean griefing = level.getGameRules().get(GameRules.MOB_GRIEFING);
			this.setDigging(this.roofed && griefing);
			this.burrowing = !this.digging;
			this.stalledBlasts = 0;
			this.lastBlastAt = null;
			MarioHead.LOG.info("[head] underground id={} at={} stuck_ticks={} roofed={} sealed={} griefing={} digging={}",
				this.getId(), this.blockPosition(), now - this.sealedSince, this.roofed, this.sealed, griefing, this.digging);
		} else if (this.burrowing && !this.sealed) {
			Vec3 c = this.centre();
			this.burrowing = !HeadRoom.fits(this, c.x, c.y, c.z, this.squeeze());
		} else if (this.digging && this.fitsWay && !this.roofed && this.chased != null && this.chased.distanceTo(this) > DIG_RELEASE) {
			this.setDigging(false);
		}
	}

	// cuts exactly the blocks his box sweeps through ahead, so the hole is his own shape and size
	private void tunnel(ServerLevel level, Vec3 c, Vec3 dir) {
		if (!level.getGameRules().get(GameRules.MOB_GRIEFING)) {
			return;
		}
		boolean decay = level.getGameRules().get(GameRules.MOB_EXPLOSION_DROP_DECAY);
		CollisionContext context = CollisionContext.of(this);
		Set<BlockPos> cut = new HashSet<>();
		for (double d = 0.0; d <= DIG_REACH + 1e-9; d += DIG_STEP) {
			Vec3 p = c.add(dir.scale(d));
			BlockPos.betweenClosedStream(HeadRoom.box(p.x, p.y, p.z, this.squeeze()).deflate(1e-4)).forEach(pos -> cut.add(pos.immutable()));
		}
		int broken = 0;
		for (BlockPos pos : cut) {
			BlockState state = level.getBlockState(pos);
			if (state.getCollisionShape(level, pos, context).isEmpty() || state.getDestroySpeed(level, pos) < 0.0f
				|| state.getBlock().getExplosionResistance() >= HARD_BLOCK) {
				continue;
			}
			level.destroyBlock(pos, !decay || this.random.nextFloat() < 1.0f / DIG_POWER, this);
			broken++;
		}
		MarioHead.LOG.debug("[head] tunnel id={} at={} cut={} broken={}", this.getId(), this.blockPosition(), cut.size(), broken);
	}

	private void setDigging(boolean digging) {
		this.digging = digging;
		this.entityData.set(DATA_DIGGING, digging);
	}

	// straight at the player, a blast on every dash, and through the rock once blasts stop working
	private void dig(ServerLevel level, Player target, HeadCue cue, boolean dash) {
		Vec3 c = this.centre();
		Vec3 to = target.position().add(0.0, target.getBbHeight() / 2.0, 0.0);
		Vec3 way = to.subtract(c);
		Vec3 dir = way.length() > 1e-6 ? way.normalize() : Vec3.ZERO;
		this.moveYaw = Mth.approachDegrees(this.moveYaw, yawTo(c, to), TURN_DEGREES);
		this.moveSpeed = approach(this.moveSpeed, cue == HeadCue.UNDERGROUND ? 0.0 : this.chargeSpeed);
		if (dash) {
			Vec3 at = c.add(dir.scale(WIDTH * this.squeeze() / 2.0));
			level.explode(this, null, BLAST, at.x, at.y, at.z, DIG_POWER, false, Level.ExplosionInteraction.NONE);
			this.tunnel(level, c, dir);
			this.stalledBlasts = this.lastBlastAt != null && this.lastBlastAt.distanceTo(c) < DIG_PROGRESS ? this.stalledBlasts + 1 : 0;
			this.lastBlastAt = c;
			if (this.stalledBlasts >= DIG_STALLS) {
				this.setDigging(false);
				this.burrowing = true;
				MarioHead.LOG.info("[head] dig stalled id={} at={} blasts={}", this.getId(), this.blockPosition(), this.stalledBlasts);
			}
		}
		Vec3 step = dir.scale(Math.min(way.length(), this.moveSpeed));
		this.body(step.x, step.y, step.z);
	}

	// down under the player and up into them, holding still while he says it
	private void burrow(Player target, HeadCue cue) {
		Vec3 c = this.centre();
		Vec3 to = target.position().add(0.0, target.getBbHeight() / 2.0, 0.0);
		Vec3 aim = c.subtract(to).horizontalDistance() > BURROW_RISE ? new Vec3(to.x, Math.min(c.y, to.y) - BURROW_DEPTH, to.z) : to;
		this.moveSpeed = approach(this.moveSpeed, cue == HeadCue.UNDERGROUND ? 0.0 : this.chargeSpeed);
		this.moveYaw = Mth.approachDegrees(this.moveYaw, yawTo(c, to), TURN_DEGREES);
		Vec3 way = aim.subtract(c);
		Vec3 step = way.length() > 1e-6 ? way.normalize().scale(Math.min(way.length(), this.moveSpeed)) : Vec3.ZERO;
		this.body(step.x, step.y, step.z);
	}

	// in the ground nobody sees him, so that is where he gets small enough for any hole he comes up in
	private void sinkOutOfSight() {
		this.wedged = false;
		this.halted = false;
		if (!this.seen && this.squeeze() > MIN_SQUEEZE) {
			this.setSqueeze(MIN_SQUEEZE);
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

	// over the player where his box fits, so one against a wall is met from the open side
	private Vec3 closeIn(Player target) {
		Vec3 at = target.position();
		Vec3 fit = HeadRoom.nearestFit(this, at.x, at.z, at.y + HOVER * this.squeeze(), this.squeeze());
		return new Vec3(fit.x, at.y, fit.z);
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
		if (this.seen || this.roofed) {
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
				Vec3 node = this.nodeAt(p.getNode(i), this.cellRoute ? p.getNode(i).y + 0.5 - HOVER * MIN_SQUEEZE : p.getNode(i).y);
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
		// close to the player the spot he has to get down to counts too, at the player's own height
		Player target = this.chased;
		if (target != null && target.position().subtract(c).horizontalDistance() < GRAB_DISTANCE) {
			points.add(target.position());
		}
		return points;
	}

	// a node is tried where he would float over its floor, a point ahead at his own height
	private boolean fitsAhead(List<Vec3> ahead, double y, float size) {
		for (Vec3 point : ahead) {
			double at = Double.isNaN(point.y) ? y : point.y + HOVER * size;
			// pressed against something for a while, room off to the side no longer counts, only the spot itself
			if (!HeadRoom.fitsNear(this, point.x, point.z, at, size, this.moveYaw, this.blockedTicks > BLOCKED_TICKS)) {
				return false;
			}
		}
		return true;
	}

	// a route at the size he has now, or a squeeze route when there is none or it is a long way round
	private void replan(Vec3 goal, Player target) {
		BlockPos to = BlockPos.containing(goal);
		float size = this.squeeze();
		Vec3 from = this.planStart();
		Path route = this.planAt(from, to, size, target);
		this.fitsWay = reaches(route);
		this.roofed = this.underRoof(target);
		if (size > MIN_SQUEEZE && (!reaches(route) || !this.seen && this.longWay(route, goal))) {
			Path small = this.planAt(from, to, MIN_SQUEEZE, target);
			if (reaches(small) && (!reaches(route) || small.getNodeCount() * 2 < route.getNodeCount())) {
				route = small;
				size = MIN_SQUEEZE;
			}
		}
		this.sealed = false;
		this.cellRoute = false;
		if (!reaches(route)) {
			HeadCells.Route cells = HeadCells.search(this, BlockPos.containing(target.getX(), target.getY() + 0.5, target.getZ()), this.getBoundingBox(), CELL_LIMIT);
			this.sealed = cells.kind() == HeadCells.Kind.SEALED;
			if (cells.kind() == HeadCells.Kind.FOUND) {
				route = new Path(cells.cells().stream().map(p -> new Node(p.getX(), p.getY(), p.getZ())).toList(), target.blockPosition(), true);
				size = MIN_SQUEEZE;
				this.cellRoute = true;
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
			if (at.distanceTo(c) < PULL_REACH && this.levelWith(node, c) && HeadRoom.sweep(this, c, at, this.squeeze())) {
				return new Vec3(node.x + 0.5, node.y, node.z + 0.5);
			}
		}
		double floor = HeadRoom.surface(this, c, -FLOOR_SEARCH, this.squeeze());
		return Double.isNaN(floor) ? null : new Vec3(c.x, floor, c.z);
	}

	// plans as a walker of that size standing at from, where the walk evaluator starts
	private @Nullable Path planAt(@Nullable Vec3 from, BlockPos to, float size, Player target) {
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
		return route != null && route.canReach() && !this.opensOnto(route.getEndNode(), cells(size), target) ? stopsShort(route) : route;
	}

	// a wide route may end a few blocks off, which only counts with nothing between its end and the player
	private boolean opensOnto(Node end, int cells, Player target) {
		double middle = HeadNodeEvaluator.centre(cells);
		return this.clearLine(new Vec3(end.x + middle, end.y + 0.5, end.z + middle), target.getEyePosition());
	}

	// rock or a ceiling over the player, but not leaves or the barriers a map is fenced with
	private boolean underRoof(Player target) {
		BlockPos.MutableBlockPos pos = BlockPos.containing(target.getEyePosition()).mutable();
		CollisionContext context = CollisionContext.of(this);
		for (int i = 0; i < ROOF_REACH && pos.getY() < this.level().getMaxY(); i++) {
			pos.move(0, 1, 0);
			BlockState state = this.level().getBlockState(pos);
			if (!state.is(Blocks.BARRIER) && !state.getCollisionShape(this.level(), pos, context).isEmpty()) {
				return true;
			}
		}
		return false;
	}

	private static Path stopsShort(Path route) {
		List<Node> nodes = new ArrayList<>();
		for (int i = 0; i < route.getNodeCount(); i++) {
			nodes.add(route.getNode(i));
		}
		return new Path(nodes, route.getTarget(), false);
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
		while (this.levelWith(p.getNode(this.pathIndex), centre) && flat + 1 < p.getNodeCount() && p.getNode(flat + 1).y == p.getNode(this.pathIndex).y
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
			if (node.subtract(centre).horizontalDistance() > (last ? WAYPOINT_REACH * 0.45 : WAYPOINT_REACH) || !this.levelWith(p.getNode(this.pathIndex), centre)) {
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
		this.aimDistance = aim.subtract(centre).horizontalDistance();
		this.moveYaw = Mth.approachDegrees(this.moveYaw, want, TURN_DEGREES);
		float turn = Math.abs(Mth.wrapDegrees(want - this.moveYaw));
		double speed = this.chargeSpeed;
		if (turn > SLOW_TURN_DEGREES) {
			speed *= Math.max(MIN_TURN_SPEED, Math.cos(Math.toRadians(turn)));
		}
		this.moveSpeed = approach(this.moveSpeed, speed);
		return true;
	}

	// only a stretch he is flying at the height of can be cut straight, not a tunnel under his feet
	private boolean levelWith(Node node, Vec3 centre) {
		double height = this.cellRoute ? node.y + 0.5 : node.y + HOVER * this.squeeze();
		return Math.abs(centre.y - height) < PULL_LEVEL;
	}

	private Vec3 nodeAt(Node node, double y) {
		double middle = HeadNodeEvaluator.centre(this.pathCells);
		return new Vec3(node.x + middle, y, node.z + middle);
	}

	// rises before he pushes on when the floor ahead is higher than he floats, so a step never stalls him
	private void fly(Player target, boolean straight) {
		double yaw = Math.toRadians(this.moveYaw);
		double y = this.centre().y;
		double want = straight ? this.hoverHeight(this.targetFloor(target)) : this.cellRoute ? this.cellHeight() : this.hoverHeight(this.nextFloor());
		if (this.climbing) {
			want = Math.max(want, y + 1.0);
		}
		double reach = Math.min(AHEAD_SETTLE, this.aimDistance);
		Vec3 ahead = this.centre().add(-Math.sin(yaw) * reach, 0.0, Math.cos(yaw) * reach);
		double fit = HeadRoom.settle(this, ahead, want, this.squeeze());
		if (!Double.isNaN(fit)) {
			want = fit;
		}
		// never past the point he is making for, or he circles a shaft he has to drop down
		double speed = Math.min(this.aimDistance, this.moveSpeed * Mth.clamp(1.0 - (want - y - CLIMB_FREE) / CLIMB_SLOW, MIN_TURN_SPEED, 1.0));
		double dx = -Math.sin(yaw) * speed;
		double dz = Math.cos(yaw) * speed;
		Vec3 before = this.position();
		this.body(dx, Mth.clamp(want - y, -CLIMB, CLIMB), dz);
		this.blockedTicks = this.horizontalCollision && this.position().subtract(before).horizontalDistance() < speed * 0.5 ? this.blockedTicks + 1 : 0;
		Vec3 c = this.centre();
		// a wall he flies up when he has no path, a step only when a block higher clears it
		this.climbing = this.horizontalCollision && (straight || HeadRoom.fits(this, c.x + dx, c.y + STEP_UP, c.z + dz, this.squeeze()));
	}

	// a cell node is where his middle goes, a walk node is a floor he floats over
	private double cellHeight() {
		Path p = this.path;
		Vec3 c = this.centre();
		if (p == null || this.pathIndex >= p.getNodeCount()) {
			return c.y;
		}
		double y = HeadRoom.settle(this, c, p.getNode(this.pathIndex).y + 0.5, this.squeeze());
		return Double.isNaN(y) ? c.y : y;
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

	private double targetFloor(Player target) {
		if (target.isFallFlying() || target.isInWater() || target.isPassenger()) {
			return target.getY();
		}
		// ordinary jumps stay tied to their floor, higher flight still draws him upward
		double floor = HeadRoom.surface(this, target.position().add(0.0, 0.05, 0.0), -1.55);
		return Double.isNaN(floor) ? target.getY() : floor;
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
		this.noPhysics = this.wedged || this.burrowing;
		this.move(MoverType.SELF, new Vec3(dx, dy, dz));
		this.noPhysics = false;
		this.setDeltaMovement(this.position().subtract(before));
	}

	// his centre the hover height over the floor below, or over the given floor, kept off the ceiling
	private double hoverHeight(double floorGiven) {
		Vec3 c = this.centre();
		float s = this.squeeze();
		double floor = Double.isNaN(floorGiven) ? HeadRoom.surface(this, c, -FLOOR_SEARCH, s) : floorGiven;
		if (Double.isNaN(floorGiven)) {
			double water = HeadRoom.surface(this, c, -FLOOR_SEARCH, ClipContext.Fluid.ANY);
			if (!Double.isNaN(water)) {
				floor = Double.isNaN(floor) ? water : Math.max(floor, water);
			}
		}
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

	// his head as drawn against the player's box, with a line between their middles so walls stop him
	private boolean touching(Player target, Vec3 from) {
		Vec3 c = this.centre();
		return HeadContact.sweeps(from, c, this.getYRot(), this.getXRot(), this.roll, this.squeeze(), target.getBoundingBox(), CONTACT_REACH)
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
		this.setFacing(Mth.approachDegrees(this.getYRot(), yaw, TURN_DEGREES),
			Mth.approach(this.getXRot(), this.pitchTo(target), PITCH_RATE));
	}

	private float pitchTo(Player target) {
		Vec3 c = this.centre();
		double elevation = c.y - target.getY() - HOVER * this.squeeze();
		return Math.abs(elevation) <= BOB_BLOCKS * this.squeeze() + 0.1 ? 0.0f
			: (float) Math.toDegrees(Math.atan2(c.y - target.getEyeY(), target.position().subtract(c).horizontalDistance()));
	}

	private void setFacing(float yaw, float pitch) {
		this.setYRot(yaw);
		this.setYBodyRot(yaw);
		this.setYHeadRot(yaw);
		this.setXRot(pitch);
	}

	// both sides lean him, the client to draw it and the server to catch with it
	@Override
	public void tick() {
		super.tick();
		float turn = Mth.wrapDegrees(this.getYRot() - this.yRotO);
		this.rollO = this.roll;
		this.roll = Mth.approach(this.roll, Mth.clamp(turn * ROLL_PER_TURN, -ROLL_MAX, ROLL_MAX), ROLL_RATE);
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

	// boats and minecarts would hold him off their riders
	@Override
	public boolean canCollideWith(Entity entity) {
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
