package com.larsoyd.mariohead;

import com.larsoyd.mariohead.entity.HeadSpawner;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Collection;
import java.util.List;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class HeadCommand {
	private HeadCommand() {
	}

	static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context, Commands.CommandSelection selection) {
		dispatcher.register(Commands.literal("mariohead")
			.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.then(Commands.literal("summon")
				.executes(c -> summon(c, List.of(c.getSource().getPlayerOrException())))
				.then(Commands.argument("targets", EntityArgument.players())
					.executes(c -> summon(c, EntityArgument.getPlayers(c, "targets"))))));
	}

	private static int summon(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> targets) throws CommandSyntaxException {
		int spawned = 0;
		for (ServerPlayer player : targets) {
			if (HeadSpawner.summonFor(player) != null) {
				spawned++;
			} else {
				c.getSource().sendFailure(Component.translatable("commands.mariohead.summon.no_room", player.getDisplayName()));
			}
		}
		if (spawned > 0) {
			int count = spawned;
			c.getSource().sendSuccess(() -> Component.translatable("commands.mariohead.summon.success", count), true);
		}
		return spawned;
	}
}
