package com.xeon.runexplorer.discord;

import com.xeon.runexplorer.model.Game;
import com.xeon.runexplorer.model.HsMode;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

public record CommandContext(
        SlashCommandInteractionEvent event,
        boolean defaultEphemeral
) {
    public boolean isEphemeral() {
        return defaultEphemeral;
    }

    public static Game getGameOrDefault(SlashCommandInteractionEvent event) {
        String g = event.getOption("game", null, opt -> opt.getAsString());
        if (g == null || g.isBlank()) return Game.OSRS;
        return Game.fromWire(g);
    }

    public static HsMode getModeOrDefault(SlashCommandInteractionEvent event) {
        String m = event.getOption("mode", null, opt -> opt.getAsString());
        if (m == null || m.isBlank()) return HsMode.NORMAL;
        return HsMode.fromWire(m);
    }
}
