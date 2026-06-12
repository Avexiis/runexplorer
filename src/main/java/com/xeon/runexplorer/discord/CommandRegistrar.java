package com.xeon.runexplorer.discord;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;

import java.util.ArrayList;
import java.util.List;

public final class CommandRegistrar {

    private CommandRegistrar() {
    }

    public static void registerGlobalCommands(JDA jda, SlashCommandRouter router) {
        List<CommandData> list = new ArrayList<>();
        for (SlashCommand c : router.allCommands()) {
            list.add(c.buildCommandData());
        }

        jda.updateCommands().addCommands(list).queue();
    }
}
