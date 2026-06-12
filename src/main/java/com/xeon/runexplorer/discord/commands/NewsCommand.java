package com.xeon.runexplorer.discord.commands;

import com.xeon.runexplorer.discord.CommandContext;
import com.xeon.runexplorer.discord.DiscordOptions;
import com.xeon.runexplorer.discord.SlashCommand;
import com.xeon.runexplorer.model.Game;
import com.xeon.runexplorer.services.NewsService;
import com.xeon.runexplorer.util.Embeds;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.List;

public final class NewsCommand implements SlashCommand {

    private final NewsService news;

    public NewsCommand(NewsService news) {
        this.news = news;
    }

    @Override
    public String name() {
        return "news";
    }

    @Override
    public SlashCommandData buildCommandData() {
        return Commands.slash("news", "Show latest official news links (OSRS/RS3)")
                .addOptions(
                        DiscordOptions.gameOption(),
                        DiscordOptions.newsLimitOption()
                );
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event, CommandContext ctx) throws Exception {
        event.deferReply(ctx.isEphemeral()).queue();

        Game game = CommandContext.getGameOrDefault(event);
        int limit = event.getOption("limit", 3, opt -> opt.getAsInt());
        if (limit < 1) limit = 1;
        if (limit > 5) limit = 5;

        List<NewsService.NewsItem> items = news.fetchLatest(game, limit);

        var eb = Embeds.base("Latest news (" + game.name() + ")");

        if (items.isEmpty()) {
            eb.setDescription("No items found (page layout may have changed).");
        } else {
            StringBuilder sb = new StringBuilder();
            for (NewsService.NewsItem it : items) {
                sb.append("- ").append("[").append(it.title()).append("]").append("(").append(it.url()).append(")").append("\n");
            }
            eb.setDescription(sb.toString());
        }

        event.getHook().editOriginalEmbeds(eb.build()).queue();
    }
}
