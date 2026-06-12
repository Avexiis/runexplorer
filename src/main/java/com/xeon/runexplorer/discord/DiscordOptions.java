package com.xeon.runexplorer.discord;

import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public final class DiscordOptions {

    private DiscordOptions() {
    }

    public static OptionData gameOption() {
        return new OptionData(OptionType.STRING, "game", "Game (defaults to OSRS)", false)
                .addChoice("OSRS", "osrs")
                .addChoice("RS3", "rs3");
    }

    public static OptionData modeOption() {
        return new OptionData(OptionType.STRING, "mode", "Hiscore mode (defaults to normal)", false)
                .addChoice("Normal", "normal")
                .addChoice("Ironman", "ironman")
                .addChoice("Hardcore", "hardcore")
                .addChoice("Ultimate", "ultimate");
    }

    public static OptionData newsLimitOption() {
        return new OptionData(OptionType.INTEGER, "limit", "How many links (1-5)", false);
    }

    public static OptionData trackActionOption() {
        return new OptionData(OptionType.STRING, "action", "add|remove|snapshot|diff", true)
                .addChoice("add", "add")
                .addChoice("remove", "remove")
                .addChoice("snapshot", "snapshot")
                .addChoice("diff", "diff");
    }
}
