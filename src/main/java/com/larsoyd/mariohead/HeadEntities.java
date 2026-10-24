package com.larsoyd.mariohead;

import com.larsoyd.mariohead.entity.TheHead;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class HeadEntities {
	public static final ResourceKey<EntityType<?>> THE_HEAD_KEY = ResourceKey.create(Registries.ENTITY_TYPE, MarioHead.id("the_head"));
	public static final EntityType<TheHead> THE_HEAD = Registry.register(
		BuiltInRegistries.ENTITY_TYPE,
		THE_HEAD_KEY,
		EntityType.Builder.<TheHead>of(TheHead::new, MobCategory.MONSTER)
			.sized(TheHead.WIDTH, TheHead.HEIGHT)
			.eyeHeight(TheHead.HEIGHT / 2.0f)
			.fireImmune()
			.noSave()
			.noLootTable()
			.notInPeaceful()
			.clientTrackingRange(10)
			.updateInterval(2)
			.build(THE_HEAD_KEY)
	);
	public static final ResourceKey<DamageType> CAUGHT = ResourceKey.create(Registries.DAMAGE_TYPE, MarioHead.id("the_head"));
	// keeps his chunk simulated while he chases, like a thrown ender pearl does
	public static final TicketType CHASE_TICKET = Registry.register(
		BuiltInRegistries.TICKET_TYPE,
		MarioHead.id("the_head"),
		new TicketType(40L, TicketType.FLAG_LOADING | TicketType.FLAG_SIMULATION)
	);

	private HeadEntities() {
	}

	static void init() {
		FabricDefaultAttributeRegistry.register(THE_HEAD, TheHead.createAttributes());
	}
}
