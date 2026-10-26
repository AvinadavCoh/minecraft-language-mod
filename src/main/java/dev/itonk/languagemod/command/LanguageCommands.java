package dev.itonk.languagemod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.itonk.languagemod.LanguageMod;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/** /verify &lt;answer&gt; is the text input gateway; speech input calls the same ChallengeManager method. */
public final class LanguageCommands {
	private LanguageCommands() {}

	public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		dispatcher.register(CommandManager.literal("verify")
				.then(CommandManager.argument("answer", StringArgumentType.greedyString())
						.executes(ctx -> {
							ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
							String answer = StringArgumentType.getString(ctx, "answer");
							if (!LanguageMod.challenges().submitAnswer(player, answer)) {
								ctx.getSource().sendError(Text.literal("[Say It] Nothing to verify right now."));
								return 0;
							}
							return 1;
						})));

		dispatcher.register(CommandManager.literal("languagemod")
				.requires(CommandManager.requirePermissionLevel(CommandManager.GAMEMASTERS_CHECK))
				.then(CommandManager.literal("reload").executes(ctx -> {
					String result = LanguageMod.reload();
					ctx.getSource().sendFeedback(() -> Text.literal("[Say It] " + result), true);
					return 1;
				})));
	}
}
