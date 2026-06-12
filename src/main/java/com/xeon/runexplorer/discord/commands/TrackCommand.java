package com.xeon.runexplorer.discord.commands;

import com.xeon.runexplorer.discord.CommandContext;
import com.xeon.runexplorer.discord.DiscordOptions;
import com.xeon.runexplorer.discord.SlashCommand;
import com.xeon.runexplorer.model.Game;
import com.xeon.runexplorer.model.HsMode;
import com.xeon.runexplorer.model.hiscores.HiscoresProfile;
import com.xeon.runexplorer.persistence.TrackerRepository;
import com.xeon.runexplorer.services.HiscoresService;
import com.xeon.runexplorer.util.Embeds;
import com.xeon.runexplorer.util.Strings;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

public final class TrackCommand implements SlashCommand {

    private final HiscoresService hiscores;
    private final TrackerRepository repo;

    public TrackCommand(HiscoresService hiscores, TrackerRepository repo) {
        this.hiscores = hiscores;
        this.repo = repo;
    }

    @Override
    public String name() {
        return "track";
    }

    @Override
    public SlashCommandData buildCommandData() {
        return Commands.slash("track", "Track a player over time (stores snapshots locally)")
                .addOptions(
                        DiscordOptions.trackActionOption(),
                        new net.dv8tion.jda.api.interactions.commands.build.OptionData(OptionType.STRING, "name", "Player name", true),
                        DiscordOptions.gameOption(),
                        DiscordOptions.modeOption()
                );
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event, CommandContext ctx) throws Exception {
        event.deferReply(ctx.isEphemeral()).queue();

        String action = event.getOption("action").getAsString();
        Game game = CommandContext.getGameOrDefault(event);
        HsMode mode = CommandContext.getModeOrDefault(event);
        String name = Strings.normalizePlayer(event.getOption("name").getAsString());

        long now = System.currentTimeMillis();

        switch (action) {
            case "add" -> {
                if (!repo.isTracked(game.wire(), mode.wire(), name)) {
                    repo.addTracked(game.wire(), mode.wire(), name, now);
                }
                event.getHook().editOriginalEmbeds(
                        Embeds.base("Track").setDescription("Tracking enabled for " + name + " (" + game.name() + ", " + mode.name() + ").").build()
                ).queue();
            }
            case "remove" -> {
                repo.removeTracked(game.wire(), mode.wire(), name);
                event.getHook().editOriginalEmbeds(
                        Embeds.base("Track").setDescription("Tracking removed for " + name + " (" + game.name() + ", " + mode.name() + ").").build()
                ).queue();
            }
            case "snapshot" -> {
                HiscoresProfile p = hiscores.fetch(game, mode, name);
                repo.insertSnapshot(game.wire(), mode.wire(), name, now, p.getSkillXp("Overall"), p.getSkillLevel("Overall"));
                event.getHook().editOriginalEmbeds(
                        Embeds.base("Snapshot").setDescription("Saved snapshot for " + name + " (Total " + p.getSkillLevel("Overall") + ", XP " + p.getSkillXp("Overall") + ").").build()
                ).queue();
            }
            case "diff" -> {
                TrackerRepository.Snapshot prev = repo.latestSnapshot(game.wire(), mode.wire(), name);
                HiscoresProfile cur = hiscores.fetch(game, mode, name);

                if (prev == null) {
                    repo.insertSnapshot(game.wire(), mode.wire(), name, now, cur.getSkillXp("Overall"), cur.getSkillLevel("Overall"));
                    event.getHook().editOriginalEmbeds(
                            Embeds.base("Diff").setDescription("No previous snapshot. Saved one now; run /track diff again later.").build()
                    ).queue();
                    return;
                }

                long xpDiff = cur.getSkillXp("Overall") - prev.overallXp();
                int levelDiff = cur.getSkillLevel("Overall") - prev.totalLevel();

                event.getHook().editOriginalEmbeds(
                        Embeds.base("Diff: " + name + " (" + game.name() + ", " + mode.name() + ")")
                                .addField("Total level", prev.totalLevel() + " -> " + cur.getSkillLevel("Overall") + " (" + signed(levelDiff) + ")", true)
                                .addField("Overall XP", prev.overallXp() + " -> " + cur.getSkillXp("Overall") + " (" + signed(xpDiff) + ")", true)
                                .build()
                ).queue();

                repo.insertSnapshot(game.wire(), mode.wire(), name, now, cur.getSkillXp("Overall"), cur.getSkillLevel("Overall"));
            }
            default -> event.getHook().editOriginal("Unknown action: " + action).queue();
        }
    }

    private static String signed(long v) {
        if (v >= 0) return "+" + v;
        return String.valueOf(v);
    }

    private static String signed(int v) {
        if (v >= 0) return "+" + v;
        return String.valueOf(v);
    }
}
