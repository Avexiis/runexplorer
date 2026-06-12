package com.xeon.runexplorer.discord.commands;

import com.xeon.runexplorer.config.BotConfig;
import com.xeon.runexplorer.discord.CommandRegistrar;
import com.xeon.runexplorer.discord.CommandContext;
import com.xeon.runexplorer.discord.SlashCommand;
import com.xeon.runexplorer.discord.SlashCommandRouter;
import com.xeon.runexplorer.util.Embeds;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

public final class ReloadCommand implements SlashCommand {

    private final BotConfig config;
    private final SlashCommandRouter router;

    public ReloadCommand(BotConfig config, SlashCommandRouter router) {
        this.config = config;
        this.router = router;
        this.router.setConfig(config);
    }

    @Override
    public String name() {
        return "reload";
    }

    @Override
    public SlashCommandData buildCommandData() {
        return Commands.slash("reload", "Re-register global slash commands (owner-only if configured)");
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event, CommandContext ctx) {
        event.deferReply(true).queue();

        if (config.ownerUserId() != null) {
            long uid = event.getUser().getIdLong();
            if (uid != config.ownerUserId()) {
                event.getHook().editOriginalEmbeds(
                        Embeds.base("Reload").setDescription("Not authorized.").build()
                ).queue();
                return;
            }
        }

        CommandRegistrar.registerGlobalCommands(event.getJDA(), router);

        event.getHook().editOriginalEmbeds(
                Embeds.base("Reload").setDescription("Re-registered " + router.commandCount() + " global commands.").build()
        ).queue();
    }
}
