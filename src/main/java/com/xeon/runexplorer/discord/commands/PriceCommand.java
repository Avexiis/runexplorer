package com.xeon.runexplorer.discord.commands;

import com.xeon.runexplorer.discord.CommandContext;
import com.xeon.runexplorer.discord.DiscordOptions;
import com.xeon.runexplorer.discord.SlashCommand;
import com.xeon.runexplorer.model.Game;
import com.xeon.runexplorer.model.itemdb.ItemDetail;
import com.xeon.runexplorer.services.ItemPriceService;
import com.xeon.runexplorer.util.Embeds;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

public final class PriceCommand implements SlashCommand {

    private final ItemPriceService prices;

    public PriceCommand(ItemPriceService prices) {
        this.prices = prices;
    }

    @Override
    public String name() {
        return "price";
    }

    @Override
    public SlashCommandData buildCommandData() {
        return Commands.slash("price", "Lookup GE item price (OSRS/RS3)")
                .addOptions(
                        new OptionData(OptionType.STRING, "item", "Item name (or exact wiki page title)", true),
                        new OptionData(OptionType.INTEGER, "id", "Item id (optional, overrides name resolution)", false),
                        DiscordOptions.gameOption()
                );
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event, CommandContext ctx) throws Exception {
        event.deferReply(ctx.isEphemeral()).queue();

        Game game = CommandContext.getGameOrDefault(event);

        String itemName = event.getOption("item").getAsString();
        Integer id = event.getOption("id", null, opt -> opt.getAsInt());

        ItemDetail d = (id != null) ? prices.fetchById(game, id) : prices.fetchByName(game, itemName);

        var eb = Embeds.base(d.name() + " (" + game.name() + ")")
                .setDescription(d.description())
                .addField("Current", d.currentPrice(), true)
                .addField("Today", d.todayPrice(), true)
                .addField("30d", d.day30().isBlank() ? "n/a" : d.day30(), true)
                .addField("90d", d.day90().isBlank() ? "n/a" : d.day90(), true)
                .addField("180d", d.day180().isBlank() ? "n/a" : d.day180(), true)
                .addField("Item id", String.valueOf(d.id()), true);

        if (d.iconLarge() != null && !d.iconLarge().isBlank()) {
            eb.setThumbnail(d.iconLarge());
        } else if (d.icon() != null && !d.icon().isBlank()) {
            eb.setThumbnail(d.icon());
        }

        event.getHook().editOriginalEmbeds(eb.build()).queue();
    }
}
