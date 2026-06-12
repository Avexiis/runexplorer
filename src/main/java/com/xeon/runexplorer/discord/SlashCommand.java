package com.xeon.runexplorer.discord;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

public interface SlashCommand {
    String name();
    SlashCommandData buildCommandData();
    void onSlashCommand(SlashCommandInteractionEvent event, CommandContext ctx) throws Exception;
}
