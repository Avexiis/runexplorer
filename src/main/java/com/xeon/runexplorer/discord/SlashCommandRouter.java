package com.xeon.runexplorer.discord;

import com.xeon.runexplorer.config.BotConfig;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SlashCommandRouter extends ListenerAdapter {

    private final Map<String, SlashCommand> commands = new LinkedHashMap<>();
    private final List<ComponentInteractionHandler> componentHandlers = new ArrayList<>();
    private BotConfig config;

    public void setConfig(BotConfig config) {
        this.config = config;
    }

    public void register(SlashCommand cmd) {
        commands.put(cmd.name(), cmd);
        if (cmd instanceof ComponentInteractionHandler handler) {
            componentHandlers.add(handler);
        }
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
            event.deferReply(true).queue(
                    hook -> hook.editOriginal("Unknown command: " + event.getName()).queue()
            );
            return;
        }

        boolean defaultEphemeral = (config != null) && config.defaultEphemeral();
        CommandContext ctx = new CommandContext(event, defaultEphemeral);

        try {
            cmd.onSlashCommand(event, ctx);
        } catch (Exception e) {
            String message = "Error: " + e.getMessage();
            if (event.isAcknowledged()) {
                event.getHook().editOriginal(message).queue();
            } else {
                event.deferReply(true).queue(
                        hook -> hook.editOriginal(message).queue()
                );
            }
        }
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String componentId = event.getComponentId();
        for (ComponentInteractionHandler handler : componentHandlers) {
            if (handler.handlesComponent(componentId)) {
                try {
                    handler.onButtonInteraction(event);
                } catch (Exception e) {
                    handleComponentError(event, e);
                }
                return;
            }
        }

        if (PaginationManager.onButton(event)) {
            return;
        }

        event.deferReply(true).queue(
                hook -> hook.editOriginal("Unknown button interaction.").queue()
        );
    }

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        String componentId = event.getComponentId();
        for (ComponentInteractionHandler handler : componentHandlers) {
            if (handler.handlesComponent(componentId)) {
                try {
                    handler.onStringSelectInteraction(event);
                } catch (Exception e) {
                    handleComponentError(event, e);
                }
                return;
            }
        }

        event.deferReply(true).queue(
                hook -> hook.editOriginal("Unknown select menu interaction.").queue()
        );
    }

    private static void handleComponentError(ButtonInteractionEvent event, Exception e) {
        String message = "Error: " + e.getMessage();
        if (event.isAcknowledged()) {
            event.getHook().editOriginal(message).queue();
        } else {
            event.deferReply(true).queue(
                    hook -> hook.editOriginal(message).queue()
            );
        }
    }

    private static void handleComponentError(StringSelectInteractionEvent event, Exception e) {
        String message = "Error: " + e.getMessage();
        if (event.isAcknowledged()) {
            event.getHook().editOriginal(message).queue();
        } else {
            event.deferReply(true).queue(
                    hook -> hook.editOriginal(message).queue()
            );
        }
    }
}
