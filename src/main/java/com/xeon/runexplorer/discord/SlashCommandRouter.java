package com.xeon.runexplorer.discord;

import com.xeon.runexplorer.config.BotConfig;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.LinkedHashMap;
import java.util.Map;

public final class SlashCommandRouter extends ListenerAdapter {

    private final Map<String, SlashCommand> commands = new LinkedHashMap<>();
    private BotConfig config;

    public void setConfig(BotConfig config) {
        this.config = config;
    }

    public void register(SlashCommand cmd) {
        commands.put(cmd.name(), cmd);
    }

    public int commandCount() {
        return commands.size();
    }

    public Iterable<SlashCommand> allCommands() {
        return commands.values();
    }

    public SlashCommand get(String name) {
        return commands.get(name);
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        SlashCommand cmd = commands.get(event.getName());
        if (cmd == null) {
            event.reply("Unknown command: " + event.getName()).setEphemeral(true).queue();
            return;
        }

        boolean defaultEphemeral = (config != null) && config.defaultEphemeral();
        CommandContext ctx = new CommandContext(event, defaultEphemeral);

        try {
            cmd.onSlashCommand(event, ctx);
        } catch (Exception e) {
            event.getHook().editOriginal("Error: " + e.getMessage()).queue(
                    ok -> {},
                    fail -> event.reply("Error: " + e.getMessage()).setEphemeral(true).queue()
            );
        }
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        PaginationManager.onButton(event);
    }
}
