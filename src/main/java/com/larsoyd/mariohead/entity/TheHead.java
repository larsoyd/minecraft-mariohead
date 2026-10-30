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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.cubemob.AbstractCubeMob;
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
	private final DashCadence cadence = new DashCadence();
	private final PearlPursuit pearlPursuit = new PearlPursuit();
	private double pearlBoost;
	private final ChaseTempo tempo = new ChaseTempo();
	private final HeadSpeech speech = new HeadSpeech();
	private final ChaseVoices voices = new ChaseVoices();
	private @Nullable EarlyStall stalls;
	private final StallGrace grace = new StallGrace();
	private long stallUntil = -1;
	private HeadCue stallVoice = HeadCue.NONE;
	private boolean stallFollowing;
	private boolean stallEscalated;
	private boolean cutoffPending;
	private boolean graceCutoffSpoken;
	private @Nullable Mob meal;
	private long mealUntil;
	private long mealReadyAt;
	private long afterMealAt = -1;
	private final DiveTaunt diveTaunt = new DiveTaunt();
	private @Nullable BlockPos waterEntry;
	private boolean wasInWater;
	private boolean waterSpeaking;
	private boolean waterPending;
	private boolean drownedSpoken;
	private long drowningUntil = -1;
	private final DashChatter dashChatter = new DashChatter();
	private HeadCue dashLine = HeadCue.NONE;
	private long nextDigDash = -1;
	private int digDashTicks = 1;
	private boolean chatterRoll;
	private HeadCue pendingVoice = HeadCue.NONE;
	private boolean teasing;
	private boolean tauntRolled;
	private long tauntReadyAt;
	private long fallTauntUntil = -1;
	private double dashBoost = 1.0;
	private final BehindPrank prank = new BehindPrank();
	private long prankDashUntil;
	private boolean prankDigging;
	private boolean prankBurrowing;
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
	private enum HomeStage { NONE, APPROACH, NICE, ASK, IGNITE, ENTER, LOOT, RAID_START, RAID, RETURN }
	private HomeStage homeStage = HomeStage.NONE;
	private boolean homeUsed;
	private boolean raidUsed;
	private long homeScanAt;
	private long raidUntil;
	private long entryUntil;
	private boolean homeEntered;
	private Vec3 homeEntry = Vec3.ZERO;
	private List<Vec3> homeApproach = List.of();
	private int homeApproachIndex;
	private long homeApproachUntil;
	private final List<BlockPos> homeChests = new ArrayList<>();
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
		if (cue.clip() != null && !cue.laugh()) this.stallFollowing = false;
		this.voices.played(cue);
		if (cue.taunt() && cue != HeadCue.HOME_RETURN) this.voices.follow(0.1, this.random.nextDouble());
		if (cue.clip() != null && !cue.spoken() && !this.graceFollowing()
			&& (this.stalls == null || !this.stalls.active(this.phaseTicks) || this.cadence.grows())) {
			this.tempo.hum();
		}
		if (cue.taunt()) {
			this.fallTauntUntil = -1;
			this.tempo.taunt();
			this.tauntReadyAt = now + (cue.clip().millis() + 49) / 50 + HeadClock.LATE_VOICE_TICKS + 600;
		}
		if (this.pendingVoice == cue) {
			this.pendingVoice = HeadCue.NONE;
		}
		this.entityData.set(DATA_CUE, HeadCue.pack(cue, this.level().getGameTime()));
		this.cueTicks = -1;
		if (cue.clip() != null && this.level() instanceof ServerLevel level) {
			this.voiceBeyondSight(level, cue);
			if (cue == HeadCue.UNDERGROUND || cue.home()) {
				level.getServer().getPlayerList().broadcastSystemMessage(Component.translatable("chat.mariohead.speech",
					this.getDisplayName(), Component.translatable("chat.mariohead.the_head." + (cue.home() ? cue.name().toLowerCase(java.util.Locale.ROOT) : "underground"))), false);
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
		HeadDread.spread(level, this);
		Player target = this.validTarget(level);
		if (target != null) {
			boolean grounded = this.onFoot(target)
				&& (target.onGround() || !Double.isNaN(HeadRoom.surface(this, target.position().add(0, 0.05, 0), -1.55)));
			this.tempo.observe(target.getRootVehicle() instanceof net.minecraft.world.entity.vehicle.boat.AbstractBoat,
				target.isFallFlying(), grounded, target.position().subtract(this.tempoLast).horizontalDistance());
			this.tempoLast = target.position();
		}
		HeadPhase phase = this.phase();
		this.seen = HeadSight.seen(level, this.getBoundingBox());
		if (this.burrowing) {
			this.sinkOutOfSight();
		} else if (this.digging) {
			this.wedged = false;
			this.halted = false;
		} else if (this.homeStage == HomeStage.NONE) {
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
					this.cadence.reset();
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

	public void pearlTeleported(Player player, Vec3 before) {
		if (this.chased == player) {
			this.stallEscalated = true;
			this.pearlPursuit.teleported(this.centre().distanceTo(before), this.centre().distanceTo(player.position()));
			this.replanIn = 0;
		}
	}

	private double chaseSpeed(double from, double to) {
		return this.pearlBoost > 0.0 && !this.teasing && from < to ? Math.min(to, from + 0.12) : approach(from, to);
	}

	private void charge(ServerLevel level, Player target) {
		Vec3 before = this.centre();
		if (this.touching(target, before)) {
			this.caught(level, target);
			return;
		}
		this.updateStalls(target);
		long now = level.getGameTime();
		Vec3 playerStep = target.position().subtract(this.chasedLast).multiply(1, 0, 1);
		double toward = playerStep.length() < PACE_TELEPORT
			? playerStep.dot(this.centre().subtract(target.position()).multiply(1, 0, 1).normalize()) : 0;
		if (this.grace.approached(this.centre().distanceTo(target.getEyePosition()), toward)) this.endGrace(true);
		if (this.grace.standing(this.centre().distanceTo(target.getEyePosition()), target.getX(), target.getY(), target.getZ())) this.endGrace(false);
		if (this.grace.tick(now)) this.resumeAfterGrace();
		this.updateDive(target);
		boolean mock = this.tickStall(level.getGameTime());
		if (this.grace.stage() == StallGrace.Stage.WITHDRAW && this.homeStage == HomeStage.NONE
			&& VoiceCurve.volume(this.centre().distanceTo(target.getEyePosition())) == 0 && this.prank.begin(now)) {
			this.grace.stalk();
			this.preparePrank();
			MarioHead.LOG.debug("[head] grace_stalk id={} gap={}", this.getId(), this.centre().distanceTo(target.getEyePosition()));
		}
		this.updateFallTaunt(target);
		if (!mock && !this.waterSpeaking && (this.tickPrank(level, target) || this.tickHome(level, target))) {
			if (!this.isRemoved() && this.touching(target, before)) this.caught(level, target);
			return;
		}
		this.trackPace(target);
		this.pearlBoost = this.pearlPursuit.boost(this.centre().distanceTo(target.position()));
		boolean water = !mock && this.tickWaterSpeech(target, this.submerged(target) ? this.random.nextDouble() : 1);
		boolean readyDash = this.cue() == HeadCue.INHALE && this.cueTicks >= ticks(INHALE_SECONDS)
			&& Math.abs(this.getXRot() - this.pitchTo(target)) <= PITCH_RATE
			&& this.speech.canStart(HeadCue.hum(this.cadence.beat() + 1), now);
		if (!mock && !water && this.grace.stage() == StallGrace.Stage.NONE && !this.teasing && !this.digging && !this.burrowing && this.pendingVoice == HeadCue.NONE) {
			if (readyDash) this.stalls.beginDash(this.phaseTicks);
			if (this.stalls.canRoll(this.phaseTicks) && (readyDash || this.cue().clip() != null && !this.cue().spoken())) {
				mock = this.tryStall(target, this.random.nextDouble());
			}
		}
		if (!mock && !water && !this.cutoffPending && this.homeStage != HomeStage.RETURN) this.tryTaunt(target, this.random.nextDouble());
		this.tickAfterMeal(now);
		HeadCue cue = this.cue();
		boolean undergroundRhythm = this.digging || this.burrowing || cue.chatter() || this.dashLine != HeadCue.NONE;
		boolean digDash = false;
		if (mock || water) {
			this.nextDigDash = -1;
		} else if (!this.waterSpeaking && this.tickCutoff(target) || this.tickLaugh(level.getGameTime())) {
			this.nextDigDash = -1;
		} else if (undergroundRhythm) {
			digDash = this.tickDigDash(level.getGameTime());
		} else if (this.pendingVoice != HeadCue.NONE) {
			this.setCue(this.pendingVoice);
		} else if (cue == HeadCue.INHALE && this.cueTicks >= ticks(INHALE_SECONDS)
			&& Math.abs(this.getXRot() - this.pitchTo(target)) <= PITCH_RATE
			&& this.speech.canStart(HeadCue.hum(this.cadence.beat() + 1), level.getGameTime())) {
			this.advanceDash();
			this.dashBoost = this.teasing ? 1.1 : 1.0;
			this.teasing = false;
			this.setCue(HeadCue.hum(this.cadence.beat()));
			if (this.graceFollowing()) {
				this.revs = Math.floorMod(this.revs, 3);
				this.tempo.stall();
			}
			this.chooseMeal(level, target);
		} else if (cue != HeadCue.INHALE && this.cueTicks >= ticks(this.dashSeconds(cue))) {
			if (!this.teasing && !cue.spoken()) {
				this.tauntRolled = false;
			}
			this.setCue(HeadCue.INHALE);
		}
		cue = this.cue();
		if (!undergroundRhythm) {
			this.nextDigDash = -1;
		}
		double sag = cue == HeadCue.INHALE ? 1.0 : Math.clamp(this.cueTicks * DT / this.dashSeconds(cue), 0.0, 1.0);
		if (undergroundRhythm && this.nextDigDash >= 0) {
			sag = Math.clamp(1.0 - (this.nextDigDash - level.getGameTime()) / (double) this.digDashTicks, 0.0, 1.0);
		}
		double boost = cue.clip() != null && !cue.spoken() ? this.dashBoost : 1.0;
		this.chargeSpeed = this.teasing ? this.pace : RevSpring.speed(this.revs, sag, this.pace) * this.tempo.multiplier() * this.tempo.humMultiplier() * boost + this.pearlBoost;
		if (mock || this.stallFollowing) this.chargeSpeed = this.pace * 0.97;
		if (this.graceFollowing()) this.chargeSpeed = this.grace.speed(now, this.centre().distanceTo(target.getEyePosition()), this.pace);
		if (this.grace.stage() == StallGrace.Stage.ENDING) {
			double gap = this.centre().subtract(target.getEyePosition()).horizontalDistance();
			double pursuit = Math.max(0.5, Math.max(this.chargeSpeed, this.pace * 1.15));
			this.chargeSpeed = Math.min(pursuit, Math.max(0, this.pace * 0.98 + (gap - 6) * 0.08));
		}
		if (this.homeStage == HomeStage.RETURN && !this.teasing) this.chargeSpeed *= 1.25;
		if (this.grace.stage() == StallGrace.Stage.WITHDRAW
			|| this.grace.stage() == StallGrace.Stage.ENDING && this.grace.retreating() && toward >= -0.02
				&& this.centre().distanceTo(target.getEyePosition()) <= 12.5) {
			double retreatSpeed = Math.clamp(12 - this.centre().distanceTo(target.getEyePosition()), 0, 0.45);
			this.moveAway(target, this.grace.stage() == StallGrace.Stage.WITHDRAW ? 0.18 : retreatSpeed);
			if (this.touching(target, before)) this.caught(level, target);
			return;
		}
		if (this.tickMeal(level, target)) {
			if (this.touching(target, before)) this.caught(level, target);
			return;
		}
		this.moveChase(level, target, cue, digDash);
	}

	private boolean canEat(double gap) {
		return this.grace.stage() == StallGrace.Stage.FOLLOW && this.homeStage == HomeStage.NONE
			&& VoiceCurve.volume(gap) > 0 && HeadMeal.clearOfPlayer(gap);
	}

	// his box rests on the floor the mob stands on, the hover height would carry him over a chicken
	private Vec3 mealAim(Mob mob) {
		return new Vec3(mob.getX(), mob.getY() + this.getBbHeight() / 2.0 + 0.05, mob.getZ());
	}

	private boolean edible(Mob mob, Player target) {
		if (mob instanceof TheHead || !mob.isAlive() || mob.isInvulnerable() || mob.getBbWidth() > this.getBbWidth() || mob.getBbHeight() > this.getBbHeight()
			|| !HeadMeal.inLine(this.centre(), target.position(), mob.position())) return false;
		Vec3 c = this.centre();
		Vec3 contact = HeadContact.firstContact(c, this.mealAim(mob), this.getYRot(), this.getXRot(), this.roll, this.squeeze(), mob.getBoundingBox(), HeadMeal.BITE);
		return contact != null && HeadRoom.sweep(this, c, contact, this.squeeze());
	}

	private void chooseMeal(ServerLevel level, Player target) {
		if (level.getGameTime() < this.mealReadyAt || !this.canEat(this.centre().distanceTo(target.getEyePosition()))) return;
		Vec3 c = this.centre();
		this.meal = level.getEntitiesOfClass(Mob.class, this.getBoundingBox().inflate(HeadMeal.REACH), mob -> this.edible(mob, target)).stream()
			.min(java.util.Comparator.comparingDouble(mob -> mob.distanceToSqr(c))).orElse(null);
		if (this.meal == null) return;
		this.mealUntil = level.getGameTime() + this.cue().clip().millis() / 50;
		this.path = null;
		this.replanIn = 0;
		MarioHead.LOG.debug("[head] meal id={} mob={} reach={} gap={}", this.getId(), this.meal.getType().toShortString(),
			this.meal.position().distanceTo(c), c.distanceTo(target.getEyePosition()));
	}

	// the stall still decides every tick, so he drops the meal the moment it ends
	private boolean tickMeal(ServerLevel level, Player target) {
		Mob mob = this.meal;
		if (mob == null) return false;
		Vec3 before = this.centre();
		if (!mob.isAlive() || level.getGameTime() >= this.mealUntil || !this.canEat(before.distanceTo(target.getEyePosition()))
			|| !HeadMeal.closes(before, target.position(), mob.position())) {
			MarioHead.LOG.debug("[head] meal_dropped id={} alive={} gap={} grace={} late={}", this.getId(), mob.isAlive(),
				before.distanceTo(target.getEyePosition()), this.grace.stage(), level.getGameTime() >= this.mealUntil);
			this.endMeal(level);
			return false;
		}
		Vec3 way = this.mealAim(mob).subtract(before);
		this.moveYaw = Mth.approachDegrees(this.moveYaw, yawTo(before, mob.position()), GRAB_TURN_DEGREES);
		this.moveSpeed = HeadMeal.dashSpeed(this.moveSpeed);
		this.faceTowards(this.moveYaw, target);
		Vec3 step = way.normalize().scale(Math.min(way.length(), this.moveSpeed));
		this.body(step.x, step.y, step.z);
		if (HeadContact.sweeps(before, this.centre(), this.getYRot(), this.getXRot(), this.roll, this.squeeze(), mob.getBoundingBox(), HeadMeal.BITE)) {
			this.eat(level, mob);
		}
		return true;
	}

	// the dash speed must not outlive the dash, only a stall holds him under the pace he chases at
	private void endMeal(ServerLevel level) {
		this.meal = null;
		this.mealReadyAt = level.getGameTime() + HeadMeal.COOLDOWN_TICKS;
		this.moveSpeed = Math.min(this.moveSpeed, this.chargeSpeed);
	}

	private void eat(ServerLevel level, Mob mob) {
		Vec3 at = mob.getBoundingBox().getCenter();
		this.endMeal(level);
		boolean silent = mob.isSilent();
		mob.setSilent(true);
		// a cube this size leaves nothing to split into
		if (mob instanceof AbstractCubeMob cube) cube.setSize(1, false);
		mob.hurtServer(level, this.damageSources().source(HeadEntities.CAUGHT, this), Float.MAX_VALUE);
		mob.setSilent(silent);
		// a squid reports a killing blow as a miss, so only the mob itself can say
		if (!mob.isDeadOrDying()) return;
		mob.discard();
		level.sendParticles(ParticleTypes.POOF, at.x, at.y, at.z, 8, 0.3, 0.3, 0.3, 0.02);
		long now = level.getGameTime();
		this.setCue(HeadCue.GULP);
		this.afterMealAt = HeadMeal.yummy(this.random.nextDouble()) ? now + HeadMeal.yummyDelay(HeadCue.GULP.clip()) : -1;
		MarioHead.LOG.debug("[head] meal_eaten id={} mob={} line={}", this.getId(), mob.getType().toShortString(), this.afterMealAt >= 0);
	}

	private void tickAfterMeal(long now) {
		if (this.afterMealAt < 0 || now < this.afterMealAt) return;
		// a house or a prank holds this tick back, by then the moment has passed
		boolean onTime = now == this.afterMealAt;
		this.afterMealAt = -1;
		if (onTime && this.grace.stage() == StallGrace.Stage.FOLLOW && this.homeStage == HomeStage.NONE) {
			this.setCue(this.voices.afterMeal(this.random.nextDouble()));
		}
	}

	private void advanceDash() {
		boolean alternate = this.stalls != null && this.stalls.active(this.phaseTicks) && !this.grace.completed()
			&& this.grace.stage() != StallGrace.Stage.STALK;
		this.revs = this.cadence.advance(this.revs, alternate);
		if (this.cadence.grows() && !this.graceFollowing()) this.tempo.dash();
		MarioHead.LOG.debug("[head] dash id={} age={} beat={} rev={} growth={} eligible={}",
			this.getId(), this.phaseTicks, this.cadence.beat() + 1, this.revs, this.cadence.grows(), alternate);
	}

	private boolean graceFollowing() {
		return this.grace.stage() == StallGrace.Stage.FOLLOW || this.grace.stage() == StallGrace.Stage.WITHDRAW;
	}

	private void resumeAfterGrace() {
		this.revs = PACE_FROM_REV;
		this.cadence.reset();
		this.tempo.stall();
		this.dashBoost = 1;
		this.stallFollowing = false;
		this.stalls.finished(this.phaseTicks);
		MarioHead.LOG.debug("[head] grace_finished id={} age={}", this.getId(), this.phaseTicks);
	}

	private void endGrace(boolean retreat) {
		if (this.grace.stage() == StallGrace.Stage.ENDING) return;
		if (this.grace.stage() == StallGrace.Stage.STALK) this.endPrank();
		this.stalls.expire(this.phaseTicks, true, true);
		this.grace.end(retreat);
		this.cutoffPending = true;
		this.graceCutoffSpoken = false;
		this.stallVoice = HeadCue.NONE;
		this.stallUntil = -1;
		this.stallFollowing = false;
		this.voices.follow(0, 0);
		MarioHead.LOG.debug("[head] grace_cutoff id={} retreat={}", this.getId(), retreat);
	}

	private void moveAway(Player target, double speed) {
		Vec3 c = this.centre();
		Vec3 away = c.subtract(target.position()).multiply(1, 0, 1).normalize().scale(speed);
		this.path = null;
		this.replanIn = 0;
		this.moveSpeed = 0;
		this.faceTowards(yawTo(c, target.position()), target);
		this.body(away.x, approach(0, this.hoverHeight(Double.NaN) - c.y), away.z);
	}

	private void moveChase(ServerLevel level, Player target, HeadCue cue, boolean digDash) {
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
			this.moveSpeed = this.chaseSpeed(this.moveSpeed, this.chargeSpeed);
			this.aimDistance = at.subtract(centre).horizontalDistance();
		} else {
			if (--this.replanIn <= 0 || this.path == null && !underway) {
				this.replan(goal, target);
			}
			straight = !underway && !this.steer(centre, goal);
			if (straight) {
				this.moveYaw = Mth.approachDegrees(this.moveYaw, yawTo(centre, goal), TURN_DEGREES);
				this.moveSpeed = this.chaseSpeed(this.moveSpeed, this.chargeSpeed);
				this.aimDistance = goal.subtract(centre).horizontalDistance();
			}
		}
		if (this.halted) {
			this.moveSpeed = approach(this.moveSpeed, 0.0);
		}
		if (this.teasing) {
			this.moveSpeed = Math.min(this.pace, Math.max(0.0, target.position().subtract(centre).horizontalDistance() - 3.0));
		}
		if (this.stallUntil >= 0 || this.stallFollowing || this.grace.stage() != StallGrace.Stage.NONE && this.grace.stage() != StallGrace.Stage.STALK) {
			this.moveSpeed = Math.min(this.moveSpeed, this.chargeSpeed);
		}
		this.faceTowards(this.moveYaw, target);
		if (this.burrowing) {
			this.burrow(target, cue);
		} else if (this.digging) {
			this.dig(level, target, cue, digDash);
		} else {
			this.fly(target, straight);
		}
		if (this.touching(target, centre)) {
			this.caught(level, target);
			return;
		}
		this.watchForSeal(level);
	}

	private boolean tickPrank(ServerLevel level, Player target) {
		if (this.prank.stage() == BehindPrank.Stage.DONE) return false;
		this.updateStalls(target);
		long now = level.getGameTime();
		boolean looking = target instanceof ServerPlayer player && HeadSight.seenBy(player, this.getBoundingBox());
		boolean eligible = this.homeStage == HomeStage.NONE && !this.stalls.active(this.phaseTicks) && this.grace.stage() == StallGrace.Stage.NONE
			&& !this.teasing && this.pendingVoice == HeadCue.NONE && !this.cue().spoken();
		boolean escaped = eligible && !looking && VoiceCurve.volume(this.centre().distanceTo(target.getEyePosition())) == 0;
		if (this.prank.observe(now, escaped, this.prank.readyToRoll(now) && escaped ? this.random.nextDouble() : 1)) {
			this.preparePrank();
		}
		BehindPrank.Stage stage = this.prank.stage();
		if (stage == BehindPrank.Stage.WAITING || stage == BehindPrank.Stage.DONE) return false;
		if (this.centre().distanceTo(target.getEyePosition()) > DIALOGUE_REACH
			&& (looking && (stage == BehindPrank.Stage.APPROACH || stage == BehindPrank.Stage.WARNING)
				|| stage == BehindPrank.Stage.WARNING && this.prank.answered()
				|| stage == BehindPrank.Stage.GREETING)) {
			this.endPrank();
			return false;
		}
		this.trackPace(target);
		if (stage == BehindPrank.Stage.APPROACH) {
			if (looking) this.prank.greeting();
			else if (this.prank.expired(now) || this.digging || this.burrowing) {
				this.endPrank();
				return false;
			} else if (!this.speech.canStart(HeadCue.INHALE, now)) {
				this.moveSpeed = 0;
				this.setDeltaMovement(Vec3.ZERO);
				return true;
			} else {
				double yaw = Math.toRadians(target.getYRot());
				Vec3 behind = target.position().add(Math.sin(yaw) * 8, 0, -Math.cos(yaw) * 8);
				Vec3 goal = behind.add(0, HOVER * this.squeeze(), 0);
				Vec3 c = this.centre();
				if (c.distanceTo(goal) <= 1.5 && c.distanceTo(target.getEyePosition()) >= 6
					&& this.clearLine(c, target.getEyePosition()) && this.speech.canStart(HeadCue.PRANK_WARNING, now)) {
					this.moveSpeed = 0;
					this.setDeltaMovement(Vec3.ZERO);
					this.setCue(HeadCue.PRANK_WARNING);
					this.prank.warning(now, (HeadCue.PRANK_WARNING.clip().millis() + 49) / 50);
				} else {
					this.chargeSpeed = Math.max(0.45, this.pace * 1.15);
					boolean direct = HeadRoom.sweep(this, c, goal, this.squeeze());
					if (direct) {
						this.path = null;
						this.moveSpeed = approach(this.moveSpeed, this.chargeSpeed);
						Vec3 delta = goal.subtract(c);
						Vec3 step = delta.normalize().scale(Math.min(delta.length(), this.moveSpeed));
						this.body(step.x, step.y, step.z);
					} else {
						if (--this.replanIn <= 0) this.replan(behind, target);
						if (this.steer(c, behind)) this.fly(target, false);
						else {
							this.moveSpeed = 0;
							this.setDeltaMovement(Vec3.ZERO);
						}
					}
				}
				this.faceTowards(yawTo(this.centre(), target.position()), target);
				return true;
			}
		}
		stage = this.prank.stage();
		if (stage == BehindPrank.Stage.GREETING) {
			if (this.cue() != HeadCue.TAUNT_026 && this.cue() != HeadCue.TAUNT_078) {
				HeadCue greeting = this.random.nextBoolean() ? HeadCue.TAUNT_026 : HeadCue.TAUNT_078;
				if (this.speech.canStart(greeting, now)) this.setCue(greeting);
			} else if (this.speech.canStart(HeadCue.INHALE, now)) {
				this.endPrank();
				return false;
			}
		} else if (stage == BehindPrank.Stage.WARNING) {
			if (looking) this.prank.sawPlayerLook();
			if (this.prank.answered() && this.speech.canStart(HeadCue.PRANK_RESPONSE, now)) {
				this.setCue(HeadCue.PRANK_RESPONSE);
				this.prank.response();
			} else if (!this.prank.answered() && this.prank.expired(now)) {
				this.startPrankDash(now, true);
			}
		} else if (stage == BehindPrank.Stage.RESPONSE && this.cueTicks >= 30) {
			this.startPrankDash(now, false);
		}
		if (this.prank.stage() == BehindPrank.Stage.DASH) {
			if (now >= this.prankDashUntil) {
				this.endPrank();
				return false;
			}
			double boost = this.prank.punishment() ? 1.5 : 1.0;
			this.chargeSpeed = RevSpring.speed(this.revs, 0, this.pace) * this.tempo.multiplier() * this.tempo.humMultiplier() * boost;
			this.moveChase(level, target, this.cue(), false);
		} else {
			this.moveSpeed = 0;
			this.setDeltaMovement(Vec3.ZERO);
			this.faceTowards(yawTo(this.centre(), target.position()), target);
		}
		return true;
	}

	private void startPrankDash(long now, boolean punishment) {
		this.prank.dash(punishment);
		this.advanceDash();
		HeadCue hum = HeadCue.hum(this.cadence.beat());
		this.prankDashUntil = now + ticks(clipSeconds(hum) * this.tempo.multiplier() * (punishment ? 1.5 : 1.0));
		this.dashBoost = 1;
		if (punishment) this.setCue(hum);
	}

	private void preparePrank() {
		this.prankDigging = this.digging;
		this.prankBurrowing = this.burrowing;
		this.setDigging(false);
		this.burrowing = false;
		this.setCue(HeadCue.NONE);
		this.path = null;
		this.replanIn = 0;
		this.moveSpeed = 0;
		this.dashLine = HeadCue.NONE;
		this.chatterRoll = false;
		this.voices.follow(0, 0);
	}

	private void endPrank() {
		if (this.grace.stage() == StallGrace.Stage.STALK) {
			this.stalls.expire(this.phaseTicks, true, true);
			this.grace.finish();
			this.resumeAfterGrace();
		}
		this.prank.finish();
		if (this.prankDigging) this.setDigging(true);
		this.burrowing |= this.prankBurrowing;
		this.path = null;
		this.replanIn = 0;
		this.dashBoost = 1;
		this.setCue(HeadCue.INHALE);
	}

	private boolean restoreHouseSize(ServerLevel level) {
		if (this.squeeze() == 1) return true;
		if (HeadSight.seen(level, this.getBoundingBox())) return false;
		Vec3 c = this.centre();
		Vec3 at = HeadRoom.nearestFit(this, c.x, c.z, c.y, 1);
		if (!HeadRoom.fits(this, at.x, at.y, at.z, 1)
			|| HeadSight.seen(level, HeadRoom.box(at.x, at.y, at.z, 1))) return false;
		this.setSqueeze(1);
		this.setPos(at.x, at.y - this.getBbHeight() / 2, at.z);
		this.path = null;
		this.replanIn = 0;
		return true;
	}

	private boolean tickHome(ServerLevel level, Player target) {
		long now = level.getGameTime();
		double ownerGap = this.centre().distanceTo(target.getEyePosition());
		if ((this.homeStage == HomeStage.APPROACH || this.homeStage == HomeStage.NICE) && ownerGap > HOUSE_REACH) {
			this.homeStage = HomeStage.NONE;
			this.homeUsed = false;
			if (!this.homeChests.isEmpty()) this.raidUsed = false;
			this.homeChests.clear();
			this.homeApproach = List.of();
			this.homeScanAt = now + 20;
			this.replanIn = 0;
			return false;
		}
		if (this.homeStage == HomeStage.NONE && (!this.homeUsed || !this.raidUsed) && now >= this.homeScanAt) {
			this.homeScanAt = now + 20;
			if (ownerGap <= HOUSE_REACH && this.underRoof(target) && this.pendingVoice == HeadCue.NONE) {
				HeadShelter room = HeadShelter.inspect(level, target.blockPosition(), BlockPos.containing(this.centre()));
				boolean home = !this.homeUsed && room.sheltered();
				boolean loot = !this.raidUsed && !room.chests().isEmpty() && level.getGameRules().get(GameRules.MOB_GRIEFING);
				if (home || loot) {
					if (!this.restoreHouseSize(level)) return false;
					this.burrowing = false;
					this.homeUsed |= home;
					this.roofed = true;
					this.raidUsed |= loot;
					if (loot) this.homeChests.addAll(room.chests());
					this.homeStage = home ? HomeStage.NICE : HomeStage.IGNITE;
					this.homeEntered = false;
					this.homeEntry = target.position().add(0, Math.max(target.getBbHeight() / 2.0, this.getBbHeight() / 2.0 + 0.1), 0);
					this.homeEntry = HeadHouseRoute.entry(this, this.homeEntry, room, target.blockPosition().getY());
					if (home) {
						this.homeApproach = HeadHouseRoute.approach(this, room.openings(), target.getEyePosition(), target.getY());
						this.homeApproachIndex = 0;
						this.homeApproachUntil = now + 300;
						if (!this.homeApproach.isEmpty()) this.homeStage = HomeStage.APPROACH;
					}
					this.teasing = false;
					this.dashLine = HeadCue.NONE;
					this.chatterRoll = false;
				}
			}
		}
		if (this.homeStage == HomeStage.NONE) return false;
		if (this.homeStage == HomeStage.APPROACH) {
			this.wedged = false;
			this.halted = false;
			Vec3 arrival = this.homeApproach.getLast();
			var arrivalBox = this.getBoundingBox().move(arrival.subtract(this.centre()));
			if (ownerGap <= WINDOW_SHORTCUT_REACH && this.centre().distanceTo(arrival) <= WINDOW_SHORTCUT_REACH
				&& !HeadSight.seen(level, this.getBoundingBox()) && !HeadSight.seen(level, arrivalBox)
				&& level.hasChunkAt(BlockPos.containing(arrival)) && level.noCollision(this, arrivalBox)) {
				this.setPos(arrival.x, arrival.y - this.getBbHeight() / 2, arrival.z);
				this.setDeltaMovement(Vec3.ZERO);
				this.moveSpeed = 0;
				this.homeStage = HomeStage.NICE;
				this.faceTowards(yawTo(this.centre(), target.position()), target);
				return true;
			}
			Vec3 to = this.homeApproach.get(this.homeApproachIndex);
			Vec3 way = to.subtract(this.centre());
			this.faceTowards(yawTo(this.centre(), target.position()), target);
			this.moveSpeed = 0.22;
			Vec3 step = way.normalize().scale(Math.min(way.length(), this.moveSpeed));
			this.body(step.x, step.y, step.z);
			if (now >= this.homeApproachUntil || this.centre().distanceTo(to) < 0.001 && ++this.homeApproachIndex == this.homeApproach.size()) {
				this.homeStage = HomeStage.NICE;
			}
			return true;
		}
		if (this.homeStage == HomeStage.ENTER || this.homeStage == HomeStage.LOOT || this.homeStage == HomeStage.RAID_START || this.homeStage == HomeStage.RAID) {
			this.homeChests.removeIf(pos -> !(level.getBlockState(pos).getBlock() instanceof net.minecraft.world.level.block.ChestBlock));
			if (this.homeStage == HomeStage.RAID && now >= this.raidUntil || this.homeChests.isEmpty()
				|| !level.getGameRules().get(GameRules.MOB_GRIEFING)) {
				this.homeChests.clear();
				this.homeStage = HomeStage.RETURN;
				this.replanIn = 0;
				this.path = null;
			}
		}
		if (this.homeStage == HomeStage.ENTER) {
			Vec3 c = this.centre();
			boolean visible = this.homeChests.stream().anyMatch(pos -> c.distanceTo(Vec3.atCenterOf(pos)) < 4
				&& level.clip(new ClipContext(c, Vec3.atCenterOf(pos), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getBlockPos().equals(pos));
			boolean fits = level.noCollision(this, this.getBoundingBox());
			this.homeEntered |= c.subtract(this.homeEntry).horizontalDistance() < 0.75 && c.distanceTo(this.homeEntry) < 2.5 && fits;
			if (this.homeEntered && visible && fits) {
				this.homeStage = HomeStage.LOOT;
			} else if (now >= this.entryUntil || this.burrowing) {
				this.homeChests.clear();
				this.homeStage = HomeStage.RETURN;
			} else {
				boolean dash = this.tickDigDash(now);
				this.chargeSpeed = Math.max(0.8, this.chargeSpeed);
				Vec3 goal = this.homeEntered ? HeadHouseRoute.chestView(this, this.homeChests.stream()
					.min(java.util.Comparator.comparingDouble(pos -> c.distanceToSqr(Vec3.atCenterOf(pos)))).orElseThrow()) : this.homeEntry;
				Vec3 to = HeadHouseRoute.clearOfChests(this, goal, this.homeChests);
				this.digToward(level, to, this.cue(), dash);
				return true;
			}
		}
		if (this.homeStage == HomeStage.RAID) {
			BlockPos chest = this.homeChests.stream().min(java.util.Comparator.comparingDouble(pos -> Vec3.atCenterOf(pos).distanceToSqr(this.centre()))).orElseThrow();
			Vec3 to = Vec3.atCenterOf(chest);
			if (!this.faceChest(to)) {
				this.body(0, 0, 0);
				return true;
			}
			boolean dash = this.tickDigDash(now);
			this.chargeSpeed = Math.max(0.8, RevSpring.speed(this.revs, 0.0, this.pace));
			this.digToward(level, to, this.cue(), dash);
			if (this.getBoundingBox().inflate(0.3).contains(to)) this.destroyRaidChest(level, chest);
			return true;
		}
		if (this.homeStage == HomeStage.RETURN && !this.teasing && this.centre().distanceTo(target.getEyePosition()) > DIALOGUE_REACH) return false;
		boolean chestDialogue = this.homeStage == HomeStage.RAID_START
			&& (this.cue() == HeadCue.HOME_HUNGRY || this.cue() == HeadCue.HOME_THIRSTY)
			&& this.cueTicks >= ticks(this.cue() == HeadCue.HOME_HUNGRY ? 1.65 : 2.25);
		if (this.homeStage == HomeStage.RETURN) this.teasing = true;
		if (this.homeStage == HomeStage.LOOT) {
			this.faceTowards(yawTo(this.centre(), target.position()), target);
			if (Math.abs(Mth.wrapDegrees(this.getYRot() - yawTo(this.centre(), target.position()))) > 1) {
				this.moveSpeed = 0;
				this.body(0, 0, 0);
				return true;
			}
		}
		if (chestDialogue) {
			Vec3 to = this.homeChests.stream().map(Vec3::atCenterOf)
				.min(java.util.Comparator.comparingDouble(this.centre()::distanceToSqr)).orElseThrow();
			if (!this.faceChest(to)) {
				this.moveSpeed = 0;
				this.body(0, 0, 0);
				return true;
			}
		}
		HeadCue line = switch (this.homeStage) {
			case NICE -> HeadCue.HOME_NICE;
			case ASK -> HeadCue.HOME_ASK;
			case LOOT -> this.random.nextBoolean() ? HeadCue.HOME_HUNGRY : HeadCue.HOME_THIRSTY;
			case RETURN -> HeadCue.HOME_RETURN;
			default -> HeadCue.digHum(this.cadence.beat() + 1);
		};
		if (this.speech.canStart(line, now)) {
			HomeStage stage = this.homeStage;
			if (stage == HomeStage.IGNITE || stage == HomeStage.RAID_START) {
				if (stage == HomeStage.IGNITE && !this.raidUsed && level.getGameRules().get(GameRules.MOB_GRIEFING)) {
					HeadShelter room = HeadShelter.inspect(level, target.blockPosition(), BlockPos.containing(this.centre()));
					this.homeChests.addAll(room.chests());
					this.raidUsed = !this.homeChests.isEmpty();
				}
				this.homeChests.removeIf(pos -> !(level.getBlockState(pos).getBlock() instanceof net.minecraft.world.level.block.ChestBlock));
				this.burrowing = false;
				this.setDigging(true);
				this.nextDigDash = -1;
				this.stalledBlasts = 0;
				this.lastBlastAt = null;
				boolean dash = this.tickDigDash(now);
				this.chargeSpeed = Math.max(0.8, this.chargeSpeed);
				if (stage == HomeStage.RAID_START && !this.homeChests.isEmpty()) {
					Vec3 to = this.homeChests.stream().map(Vec3::atCenterOf)
						.min(java.util.Comparator.comparingDouble(this.centre()::distanceToSqr)).orElseThrow();
					this.digToward(level, to, this.cue(), dash);
				} else {
					this.digToward(level, HeadHouseRoute.clearOfChests(this, this.homeEntry, this.homeChests), this.cue(), dash);
				}
				if (stage == HomeStage.RAID_START) {
					this.raidUntil = now + 100;
					this.homeStage = HomeStage.RAID;
				} else {
					this.homeStage = this.homeChests.isEmpty() ? HomeStage.NONE : HomeStage.ENTER;
					this.entryUntil = now + 400;
				}
				return true;
			}
			this.setCue(line);
			this.homeStage = switch (stage) {
				case NICE -> HomeStage.ASK;
				case ASK -> HomeStage.IGNITE;
				case LOOT -> HomeStage.RAID_START;
				default -> HomeStage.NONE;
			};
		}
		if (line == HeadCue.HOME_RETURN) return false;
		this.moveSpeed = 0;
		if (!chestDialogue) this.faceTowards(yawTo(this.centre(), target.position()), target);
		this.body(0, 0, 0);
		return true;
	}

	private boolean faceChest(Vec3 to) {
		Vec3 delta = to.subtract(this.centre());
		float yaw = yawTo(this.centre(), to);
		float pitch = (float) -Math.toDegrees(Math.atan2(delta.y, delta.horizontalDistance()));
		this.setFacing(Mth.approachDegrees(this.getYRot(), yaw, TURN_DEGREES), Mth.approach(this.getXRot(), pitch, PITCH_RATE));
		return Math.abs(Mth.wrapDegrees(this.getYRot() - yaw)) < 1 && Math.abs(this.getXRot() - pitch) < 1;
	}

	private void destroyRaidChest(ServerLevel level, BlockPos pos) {
		if (!level.getGameRules().get(GameRules.MOB_GRIEFING)) return;
		if (level.getBlockEntity(pos) instanceof net.minecraft.world.Container container) {
			if (container instanceof net.minecraft.world.RandomizableContainer loot) loot.setLootTable(null);
			container.clearContent();
		}
		level.destroyBlock(pos, false, this);
	}

	private boolean tickDigDash(long now) {
		if (this.tickLaugh(now)) return false;
		if (this.homeStage == HomeStage.RETURN && this.teasing) {
			this.nextDigDash = -1;
			return false;
		}
		boolean underground = (this.digging || this.burrowing) && this.denseTerrain();
		if (this.homeStage != HomeStage.NONE || !underground) {
			this.chatterRoll = false;
			this.dashLine = HeadCue.NONE;
		}
		if (this.pendingVoice != HeadCue.NONE) {
			this.setCue(this.pendingVoice);
			return false;
		}
		HeadCue cue = this.cue();
		HeadCue hum = HeadCue.digHum(this.cadence.beat() + 1);
		if (cue.spoken() && !cue.chatter() && !this.speech.canStart(hum, now)) {
			this.nextDigDash = -1;
			return false;
		}
		if (this.dashLine != HeadCue.NONE && this.speech.canStart(this.dashLine, now)) {
			HeadCue line = this.dashLine;
			this.dashLine = HeadCue.NONE;
			this.setCue(line);
			this.dashChatter.finishedAt(now + (line.clip().millis() + 49) / 50 + HeadClock.LATE_VOICE_TICKS);
		}
		if (now < this.nextDigDash) {
			return false;
		}
		if (this.nextDigDash < 0 && !this.speech.canStart(hum, now)) {
			return false;
		}
		if (this.chatterRoll) {
			this.chatterRoll = false;
			int line = this.dashChatter.choose(now, this.random.nextDouble(), this.random.nextInt(DashVoice.CLIPS.length));
			if (line >= 0) {
				this.dashLine = HeadCue.chatter(line);
			}
		}
		this.dashBoost = this.teasing ? 1.1 : 1.0;
		int interval = this.denseTerrain() ? DENSE_DASH_TICKS : (hum.clip().millis() + 49) / 50 + HeadClock.LATE_VOICE_TICKS;
		this.digDashTicks = (int) Math.ceil(interval * this.dashBoost);
		this.nextDigDash = now + this.digDashTicks;
		this.teasing = false;
		this.advanceDash();
		this.tauntRolled = false;
		boolean returning = this.homeStage == HomeStage.RETURN && this.chased != null
			&& this.centre().distanceTo(this.chased.getEyePosition()) <= DIALOGUE_REACH;
		if (this.dashLine == HeadCue.NONE && !returning && this.speech.canStart(hum, now)) {
			this.setCue(hum);
			this.chatterRoll = this.homeStage == HomeStage.NONE && underground;
		}
		return true;
	}

	private boolean denseTerrain() {
		Vec3 c = this.centre();
		Vec3 dir = this.chased == null ? this.getLookAngle() : this.chased.position().subtract(c).normalize();
		BlockPos ahead = BlockPos.containing(c.add(dir.scale(DIG_REACH)));
		CollisionContext context = CollisionContext.of(this);
		int solid = 0;
		for (BlockPos pos : BlockPos.betweenClosed(ahead.offset(-3, -3, -3), ahead.offset(3, 3, 3))) {
			BlockState state = this.level().getBlockState(pos);
			if (!state.is(Blocks.BARRIER) && !state.getCollisionShape(this.level(), pos, context).isEmpty() && ++solid >= DENSE_BLOCKS) {
				return true;
			}
		}
		return false;
	}

	private double dashSeconds(HeadCue cue) {
		return clipSeconds(cue) * (cue.clip() != null && !cue.spoken() ? this.tempo.multiplier() * this.dashBoost : 1.0);
	}

	public void playerFell(ServerPlayer player) {
		long now = this.level().getGameTime();
		if (this.chased == player && this.phase() == HeadPhase.CHARGE && player.isAlive()
			&& this.tauntsAllowed(player) && now >= this.tauntReadyAt && this.fallTauntUntil < now && !this.teasing
			&& VoiceCurve.volume(this.centre().distanceTo(player.getEyePosition())) > 0 && this.random.nextDouble() < 0.15) {
			this.fallTauntUntil = now + 200;
		}
	}

	private void updateFallTaunt(Player target) {
		if (this.fallTauntUntil < 0) return;
		long now = this.level().getGameTime();
		if (!this.tauntsAllowed(target) || now >= this.fallTauntUntil || now < this.tauntReadyAt || VoiceCurve.volume(this.centre().distanceTo(target.getEyePosition())) == 0) {
			this.fallTauntUntil = -1;
			if (this.pendingVoice == HeadCue.FALL_019) {
				this.pendingVoice = HeadCue.NONE;
				this.teasing = false;
			}
			return;
		}
		if (this.homeStage == HomeStage.NONE && (this.prank.stage() == BehindPrank.Stage.WAITING || this.prank.stage() == BehindPrank.Stage.DONE)
			&& !this.teasing && !this.cue().spoken() && this.pendingVoice == HeadCue.NONE) {
			this.teasing = true;
			this.pendingVoice = HeadCue.FALL_019;
			this.dashLine = HeadCue.NONE;
			this.chatterRoll = false;
		}
	}

	private boolean tryTaunt(Player target, double chance) {
		this.updateStalls(target);
		if (this.grace.stage() != StallGrace.Stage.NONE || this.teasing || this.stallUntil >= 0 || this.stallVoice != HeadCue.NONE || this.cutoffPending || this.cue().chatter() || this.dashLine != HeadCue.NONE || this.tauntRolled || this.pendingVoice != HeadCue.NONE || this.burrowing
			|| this.level().getGameTime() < this.tauntReadyAt || target.isPassenger() || target.isInWater() || target.isFallFlying()) {
			return false;
		}
		Vec3 centre = this.centre();
		double floor = HeadRoom.surface(this, target.position().add(0.0, 0.05, 0.0), -1.55);
		boolean onLand = target.onGround() || !Double.isNaN(floor);
		double gap = target.position().subtract(centre).horizontalDistance();
		boolean early = this.stalls.active(this.phaseTicks);
		if (!ChaseTempo.canTaunt(early, onLand, gap, 0.0)) {
			return false;
		}
		Vec3 goal = new Vec3(target.getX(), (Double.isNaN(floor) ? target.getY() : floor) + HOVER * this.squeeze(), target.getZ());
		if (Math.abs(goal.y - centre.y) > 2.0 || !HeadRoom.sweep(this, centre, goal, this.squeeze())) {
			return false;
		}
		this.tauntRolled = true;
		if (!ChaseTempo.canTaunt(early, onLand, gap, chance)) {
			return false;
		}
		this.teasing = true;
		this.setCue(this.voices.choose(ChaseVoices.TAUNTS, this.random.nextDouble()));
		return true;
	}

	private boolean tickLaugh(long now) {
		if (!this.voices.waiting()) return false;
		if (this.speech.canStart(HeadCue.VARIETY_LAUGH1, now)) {
			this.setCue(this.voices.laugh(this.random.nextDouble()));
		}
		return true;
	}

	private boolean submerged(Entity entity) {
		return this.level().getFluidState(BlockPos.containing(entity.getEyePosition())).is(net.minecraft.tags.FluidTags.WATER);
	}

	private void updateDive(Player target) {
		boolean wet = target.isInWater();
		if (wet && !this.wasInWater) {
			BlockPos entry = target.blockPosition();
			boolean different = this.waterEntry == null || WaterVisit.different(this.waterEntry, entry,
				pos -> !this.level().hasChunkAt(pos) ? -1 : this.level().getFluidState(pos).is(net.minecraft.tags.FluidTags.WATER) ? 1 : 0);
			this.diveTaunt.enter(different);
			if (different) this.waterEntry = entry;
		}
		this.wasInWater = wet;
		double step = target.position().distanceToSqr(this.chasedLast);
		this.diveTaunt.observe(target.isSwimming() && this.submerged(target) && step > 0.0004 && step < PACE_TELEPORT * PACE_TELEPORT);
		if (this.waterSpeaking && !this.voices.waiting() && this.speech.canStart(HeadCue.INHALE, this.level().getGameTime())) {
			this.waterSpeaking = false;
		}
	}

	public void playerDrowned(ServerPlayer player) {
		if (this.chased == player && player.isAlive() && !this.drownedSpoken && this.tauntsAllowed(player)) this.drowningUntil = this.level().getGameTime() + 200;
	}

	private boolean tauntsAllowed(Player target) {
		this.updateStalls(target);
		return !this.stalls.active(this.phaseTicks) && this.grace.stage() == StallGrace.Stage.NONE;
	}

	private boolean tickWaterSpeech(Player target, double chance) {
		if (!this.tauntsAllowed(target)) {
			this.waterPending = false;
			this.drowningUntil = -1;
			return false;
		}
		long now = this.level().getGameTime();
		if (this.drowningUntil >= 0 && (now >= this.drowningUntil || !this.submerged(target)
			|| VoiceCurve.volume(this.centre().distanceTo(target.getEyePosition())) == 0)) this.drowningUntil = -1;
		if (this.waterSpeaking || this.homeStage != HomeStage.NONE || this.stallUntil >= 0 || this.pendingVoice != HeadCue.NONE
			|| this.prank.stage() != BehindPrank.Stage.WAITING && this.prank.stage() != BehindPrank.Stage.DONE) return false;
		boolean drowning = this.drowningUntil >= 0 && !this.drownedSpoken;
		boolean eligible = !this.teasing && !this.voices.waiting() && this.submerged(target)
			&& this.submerged(this) && this.centre().distanceTo(target.getEyePosition()) <= 8
			&& this.clearLine(this.centre(), target.getEyePosition());
		if (!eligible) this.waterPending = false;
		if (!drowning && !this.waterPending) this.waterPending = this.diveTaunt.tryStart(now, eligible, chance);
		if (!drowning && !this.waterPending) return false;
		if (!this.speech.canStart(HeadCue.VARIETY_DROWNING, now)) return true;
		this.teasing = true;
		this.dashLine = HeadCue.NONE;
		this.chatterRoll = false;
		this.setCue(drowning ? HeadCue.VARIETY_DROWNING : this.voices.choose(ChaseVoices.WATER, this.random.nextDouble()));
		this.waterSpeaking = true;
		this.waterPending = false;
		this.voices.followWater(drowning, this.random.nextDouble());
		if (drowning) {
			this.drownedSpoken = true;
			this.drowningUntil = -1;
		} else {
			this.diveTaunt.played(now);
		}
		return true;
	}

	private boolean tryStall(Player target, double chance) {
		return this.tryStall(target, chance, null);
	}

	private boolean tryStall(Player target, double chance, @Nullable Vec3 step) {
		this.updateStalls(target);
		if (this.grace.stage() != StallGrace.Stage.NONE || !this.stalls.canRoll(this.phaseTicks)) return false;
		long now = this.level().getGameTime();
		if (this.prank.stage() != BehindPrank.Stage.WAITING && this.prank.stage() != BehindPrank.Stage.DONE) return false;
		if (this.teasing || this.stallUntil >= 0 || this.stallVoice != HeadCue.NONE || this.pendingVoice != HeadCue.NONE || this.voices.waiting()
			|| this.homeStage != HomeStage.NONE) return false;
		Vec3 c = this.centre();
		Vec3 direction = target.position().subtract(c).multiply(1, 0, 1).normalize();
		double speed = Math.max(this.moveSpeed, RevSpring.speed(this.revs, 0, this.pace) * this.tempo.multiplier() * this.tempo.humMultiplier());
		double closing = Math.max(0, speed - this.chasedVelocity.dot(direction));
		Vec3 contact = c.distanceTo(target.getEyePosition()) <= 8 && this.clearLine(c, target.getEyePosition())
			? HeadContact.firstContact(c, c.add(step == null ? direction.scale(closing * 8) : step), this.getYRot(), this.getXRot(), this.roll,
				this.squeeze(), target.getBoundingBox(), CONTACT_REACH) : null;
		boolean imminent = contact != null && HeadRoom.sweep(this, c, contact, this.squeeze());
		if (!imminent) return false;
		boolean guaranteed = this.stalls.guaranteed(this.phaseTicks);
		boolean selected = this.stalls.tryStart(this.phaseTicks, true, chance);
		MarioHead.LOG.debug("[head] stall_roll id={} age={} cutoff={} probability={} guaranteed={} chance={} selected={} sweep={}",
			this.getId(), this.phaseTicks, this.stalls.cutoff(), this.stalls.probability(this.phaseTicks), guaranteed, chance, selected, step != null);
		if (!selected) return false;
		this.stallVoice = this.voices.choose(ChaseVoices.STALLS, this.random.nextDouble());
		this.stallFollowing = true;
		this.revs = -1;
		this.cadence.reset();
		this.tempo.stall();
		this.dashBoost = 1;
		this.pearlBoost = 0;
		this.chargeSpeed = this.pace * 0.97;
		this.moveSpeed = Math.min(this.moveSpeed, this.chargeSpeed);
		return this.tickStall(now);
	}

	private boolean tickStall(long now) {
		if (this.stallVoice != HeadCue.NONE) {
			if (this.speech.canStart(this.stallVoice, now)) {
				HeadCue line = this.stallVoice;
				this.stallVoice = HeadCue.NONE;
				this.setCue(line);
				this.voices.follow(0.4, this.random.nextDouble());
				this.stallFollowing = true;
				this.stallUntil = now + (line.clip().millis() + 49) / 50;
				this.tauntReadyAt = this.stallUntil + HeadClock.LATE_VOICE_TICKS + 600;
				MarioHead.LOG.debug("[head] stall id={} cue={} ticks={} speed={}", this.getId(), line, this.stallUntil - now, this.chargeSpeed);
			}
			return true;
		}
		if (this.stallUntil < 0) return false;
		if (now < this.stallUntil) return true;
		this.stallUntil = -1;
		this.stalls.finished(this.phaseTicks);
		if (this.stalls.active(this.phaseTicks)) {
			this.grace.start(now, this.random.nextDouble(), this.random.nextDouble(), this.random.nextDouble());
			MarioHead.LOG.debug("[head] grace id={} age={} until={} duration={}", this.getId(), this.phaseTicks,
				now + this.grace.duration(), this.grace.duration());
		}
		return false;
	}

	private boolean onFoot(Player target) {
		return !target.isPassenger() && !target.isInWater() && !target.isFallFlying();
	}

	// lasting states only, a crouch or a bite of food must not end the stalls for good
	private boolean canSprint(Player target) {
		return !target.isMobilityRestricted() && (target.isPassenger() || target.getFoodData().hasEnoughFood());
	}

	private void updateStalls(Player target) {
		if (this.stalls == null) this.stalls = new EarlyStall(this.random.nextDouble());
		boolean wasActive = this.stalls.active(this.phaseTicks - 1);
		this.stallEscalated |= this.digging || this.burrowing || this.prankDigging || this.prankBurrowing || !this.canSprint(target)
			|| target.isFallFlying() || this.centre().distanceTo(target.getEyePosition()) > 64 && !this.onFoot(target);
		var inventory = target.getInventory();
		for (int slot = 0; !this.stallEscalated && slot < inventory.getContainerSize(); slot++) {
			var item = inventory.getItem(slot);
			this.stallEscalated = item.is(net.minecraft.world.item.Items.ENDER_PEARL) || item.is(net.minecraft.world.item.Items.ELYTRA)
				|| item.is(net.minecraft.world.item.Items.GOLDEN_APPLE) || item.is(net.minecraft.world.item.Items.ENCHANTED_GOLDEN_APPLE)
				|| item.is(net.minecraft.tags.ItemTags.HEAD_ARMOR) || item.is(net.minecraft.tags.ItemTags.CHEST_ARMOR)
				|| item.is(net.minecraft.tags.ItemTags.LEG_ARMOR) || item.is(net.minecraft.tags.ItemTags.FOOT_ARMOR);
		}
		Entity vehicle = target.getRootVehicle();
		if (vehicle instanceof net.minecraft.world.entity.vehicle.boat.AbstractBoat) {
			var box = vehicle.getBoundingBox();
			for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY - 0.01, box.minZ),
				BlockPos.containing(box.maxX, box.minY - 0.01, box.maxZ))) {
				this.stallEscalated |= this.level().getBlockState(pos).is(net.minecraft.tags.BlockTags.ICE);
			}
		}
		this.cutoffPending |= this.stalls.expire(this.phaseTicks, this.stallEscalated,
			VoiceCurve.volume(this.centre().distanceTo(target.getEyePosition())) > 0);
		if (wasActive && !this.stalls.active(this.phaseTicks)) {
			MarioHead.LOG.debug("[head] stall_disabled id={} age={} cutoff={} escalated={} gap={} digging={} flying={} passenger={} sprint={}",
				this.getId(), this.phaseTicks, this.stalls.cutoff(), this.stallEscalated, this.centre().distanceTo(target.getEyePosition()),
				this.digging || this.burrowing || this.prankDigging || this.prankBurrowing, target.isFallFlying(), target.isPassenger(), this.canSprint(target));
		}
		if (!this.stalls.active(this.phaseTicks) && this.grace.stage() != StallGrace.Stage.NONE
			&& this.grace.stage() != StallGrace.Stage.ENDING) this.endGrace(false);
		if (this.stallVoice != HeadCue.NONE && !this.stalls.active(this.phaseTicks)) {
			this.stallVoice = HeadCue.NONE;
			this.stallFollowing = false;
		}
		if (this.stallEscalated) {
			if (this.grace.stage() != StallGrace.Stage.ENDING) this.cutoffPending = false;
			if (this.stallFollowing) this.voices.follow(0, 0);
			this.stallFollowing = false;
			if (this.stallUntil >= 0) {
				this.stallUntil = -1;
				this.voices.follow(0, 0);
				this.stalls.finished(this.phaseTicks);
			}
		}
	}

	private boolean tickCutoff(Player target) {
		long now = this.level().getGameTime();
		boolean ending = this.grace.stage() == StallGrace.Stage.ENDING;
		if (ending && this.graceCutoffSpoken) {
			if (!this.speech.canStart(HeadCue.INHALE, now)) return true;
			this.grace.finish();
			this.resumeAfterGrace();
			this.setCue(HeadCue.INHALE);
			return true;
		}
		if (!this.cutoffPending) return false;
		if (ending && this.centre().distanceTo(target.getEyePosition()) > DIALOGUE_REACH) return false;
		if (VoiceCurve.volume(this.centre().distanceTo(target.getEyePosition())) == 0) {
			this.cutoffPending = false;
			return false;
		}
		if (this.speech.canStart(HeadCue.VARIETY_037, now)) {
			this.setCue(this.voices.choose(ChaseVoices.CUTOFF, this.random.nextDouble()));
			this.voices.follow(0, 0);
			this.cutoffPending = false;
			this.graceCutoffSpoken = ending;
		}
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
		boolean sealedLongEnough = this.sealedSince >= 0L && now - this.sealedSince >= ticks(SEAL_SECONDS);
		if (sealedLongEnough && !this.announced && !this.homeUsed) {
			this.announced = true;
			this.setCue(HeadCue.UNDERGROUND);
		}
		if (!this.burrowing && !this.digging && sealedLongEnough) {
			this.path = null;
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
		} else if (!this.homeUsed && this.digging && this.fitsWay && !this.roofed && this.chased != null && this.chased.distanceTo(this) > DIG_RELEASE) {
			this.setDigging(false);
		}
	}

	// cuts exactly the blocks his box sweeps through ahead, so the hole is his own shape and size
	private int tunnel(ServerLevel level, Vec3 c, Vec3 dir) {
		if (!level.getGameRules().get(GameRules.MOB_GRIEFING)) {
			return 0;
		}
		boolean decay = level.getGameRules().get(GameRules.MOB_EXPLOSION_DROP_DECAY);
		CollisionContext context = CollisionContext.of(this);
		Set<BlockPos> cut = new HashSet<>();
		for (double d = 0.0; d <= Math.ceil(Math.max(DIG_REACH, this.moveSpeed) / DIG_STEP) * DIG_STEP + 1e-9; d += DIG_STEP) {
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
			boolean chest = state.getBlock() instanceof net.minecraft.world.level.block.ChestBlock;
			if (chest && (this.homeStage == HomeStage.IGNITE || this.homeStage == HomeStage.ENTER)) continue;
			if (chest && (this.homeStage == HomeStage.RAID_START || this.homeStage == HomeStage.RAID)) {
				this.destroyRaidChest(level, pos);
			} else {
				level.destroyBlock(pos, !decay || this.random.nextFloat() < 1.0f / DIG_POWER, this);
			}
			broken++;
		}
		MarioHead.LOG.debug("[head] tunnel id={} at={} cut={} broken={}", this.getId(), this.blockPosition(), cut.size(), broken);
		return broken;
	}

	private void setDigging(boolean digging) {
		this.stallEscalated |= digging;
		this.digging = digging;
		this.entityData.set(DATA_DIGGING, digging);
	}

	// blast obstructing blocks, with phasing reserved for routes he cannot break
	private void dig(ServerLevel level, Player target, HeadCue cue, boolean dash) {
		Vec3 to = new Vec3(target.getX(), this.targetFloor(target) + HOVER * this.squeeze(), target.getZ());
		Vec3 c = this.centre();
		if (to.y > c.y + 0.1 && !this.underRoof(target)) {
			to = new Vec3(c.x, to.y, c.z);
		}
		this.digToward(level, to, cue, dash);
	}

	private void digToward(ServerLevel level, Vec3 to, HeadCue cue, boolean dash) {
		Vec3 c = this.centre();
		Vec3 way = to.subtract(c);
		Vec3 dir = way.length() > 1e-6 ? way.normalize() : Vec3.ZERO;
		this.moveYaw = Mth.approachDegrees(this.moveYaw, yawTo(c, to), TURN_DEGREES);
		this.moveSpeed = this.chaseSpeed(this.moveSpeed, cue == HeadCue.UNDERGROUND ? 0.0 : this.chargeSpeed);
		if (this.teasing && this.chased != null) {
			this.moveSpeed = Math.min(this.moveSpeed, Math.max(0, this.centre().distanceTo(this.chased.getEyePosition()) - 3));
		}
		Vec3 step = dir.scale(Math.min(way.length(), this.moveSpeed));
		if (dash || !HeadRoom.sweep(this, c, c.add(step), this.squeeze())) {
			int broken = this.tunnel(level, c, dir);
			if (broken > 0) {
				Vec3 at = c.add(dir.scale(WIDTH * this.squeeze() / 2.0));
				level.explode(this, null, BLAST, at.x, at.y, at.z, DIG_POWER, false, Level.ExplosionInteraction.NONE);
				this.lastBlastAt = c;
			}
			if (broken > 0) this.stalledBlasts = 0;
			else if (dash) this.stalledBlasts = HeadRoom.sweep(this, c, c.add(dir.scale(DIG_REACH)), this.squeeze()) ? 0 : this.stalledBlasts + 1;
			if (this.stalledBlasts >= DIG_STALLS) {
				this.setDigging(false);
				this.burrowing = true;
				MarioHead.LOG.info("[head] dig stalled id={} at={} blasts={}", this.getId(), this.blockPosition(), this.stalledBlasts);
			}
		}
		this.body(step.x, step.y, step.z);
	}

	// down under the player and up into them, holding still while he says it
	private void burrow(Player target, HeadCue cue) {
		Vec3 c = this.centre();
		Vec3 to = target.position().add(0.0, target.getBbHeight() / 2.0, 0.0);
		Vec3 aim = c.subtract(to).horizontalDistance() > BURROW_RISE ? new Vec3(to.x, Math.min(c.y, to.y) - BURROW_DEPTH, to.z) : to;
		this.moveSpeed = this.chaseSpeed(this.moveSpeed, cue == HeadCue.UNDERGROUND ? 0.0 : this.chargeSpeed);
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
			for (int x = -1; x <= 1; x++) {
				for (int z = -1; z <= 1; z++) {
					BlockPos nearby = pos.offset(x, 0, z);
					BlockState state = this.level().getBlockState(nearby);
					if (!state.is(Blocks.BARRIER) && !state.getCollisionShape(this.level(), nearby, context).isEmpty()) {
						return true;
					}
				}
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
		this.moveSpeed = this.chaseSpeed(this.moveSpeed, speed);
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
		if (this.phase() == HeadPhase.CHARGE && this.chased != null && !this.wedged && !this.digging && !this.burrowing) {
			Vec3 step = new Vec3(dx, dy, dz);
			if (this.stalls != null && this.stalls.canRoll(this.phaseTicks)) this.tryStall(this.chased, this.random.nextDouble(), step);
			if (this.stallVoice != HeadCue.NONE || this.stallUntil >= 0) {
				Vec3 c = this.centre();
				Vec3 contact = HeadContact.firstContact(c, c.add(step), this.getYRot(), this.getXRot(), this.roll,
					this.squeeze(), this.chased.getBoundingBox(), CONTACT_REACH);
				double limit = Math.min(step.length(), this.chargeSpeed);
				if (contact != null) limit = Math.min(limit, Math.max(0, c.distanceTo(contact) - 0.05));
				step = step.normalize().scale(limit);
				dx = step.x; dy = step.y; dz = step.z;
			}
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

	// a meal leaves nothing behind, a caught player still drops their things
	@Override
	public boolean killedEntity(ServerLevel level, LivingEntity entity, DamageSource source) {
		return !(entity instanceof Mob);
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
