package com.larsoyd.mariohead.showcase.mixin;

import com.larsoyd.mariohead.entity.TheHead;
import com.larsoyd.mariohead.entity.HeadCue;
import com.larsoyd.mariohead.entity.HeadPhase;
import com.larsoyd.mariohead.entity.HeadSpeech;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(TheHead.class)
public interface HeadActorAccess {
	@Invoker("setCue") void cue(HeadCue cue);
	@Invoker("setPhase") void phase(HeadPhase phase);
	@Invoker("body") void move(double x, double y, double z);
	@Invoker("faceTowards") void face(float yaw, Player player);
	@Invoker("touching") boolean showcaseTouching(Player player, Vec3 from);
	@Invoker("caught") void showcaseCaught(ServerLevel level, Player player);
	@Accessor("speech") HeadSpeech speech();
	@Invoker("tickDigDash") boolean showcaseDigDash(long now);
	@Invoker("setDigging") void showcaseDigging(boolean digging);
	@Invoker("faceChest") boolean showcaseFaceChest(Vec3 point);
}
