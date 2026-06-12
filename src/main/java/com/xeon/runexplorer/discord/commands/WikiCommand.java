package com.xeon.runexplorer.discord.commands;

import com.xeon.runexplorer.discord.CommandContext;
import com.xeon.runexplorer.discord.DiscordOptions;
import com.xeon.runexplorer.discord.SlashCommand;
import com.xeon.runexplorer.model.Game;
import com.xeon.runexplorer.model.wiki.WikiPageSummary;
import com.xeon.runexplorer.services.WikiService;
import com.xeon.runexplorer.util.Embeds;
import com.xeon.runexplorer.util.Strings;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

public final class WikiCommand implements SlashCommand {

    private final WikiService wiki;

    public WikiCommand(WikiService wiki) {
        this.wiki = wiki;
    }

    @Override
    public String name() {
        return "wiki";
    }

    @Override
    public SlashCommandData buildCommandData() {
        return Commands.slash("wiki", "Search the OSRS/RS3 wiki and return the best match")
                .addOptions(
                        new OptionData(OptionType.STRING, "query", "Any search text (npc, quest, item, mechanic, etc)", true),
                        DiscordOptions.gameOption()
                );
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event, CommandContext ctx) throws Exception {
        event.deferReply(ctx.isEphemeral()).queue();

        Game game = CommandContext.getGameOrDefault(event);
        String query = event.getOption("query").getAsString();

        WikiPageSummary best = wiki.searchBestSummary(game, query, 10);

        if (best == null) {
            event.getHook().editOriginalEmbeds(
                    Embeds.base("Wiki (" + game.name() + ")")
                            .setDescription("No results found for: " + query)
                            .build()
            ).queue();
            return;
        }

        var eb = Embeds.base(best.title() + " (" + game.name() + ")")
                .setDescription(Strings.clamp(best.extract(), 900));

        if (best.url() != null && !best.url().isBlank()) {
            eb.setUrl(best.url());
        }

        event.getHook().editOriginalEmbeds(eb.build()).queue();
    }
}
