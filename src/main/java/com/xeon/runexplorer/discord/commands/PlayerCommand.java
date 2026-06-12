package com.xeon.runexplorer.discord.commands;

import com.xeon.runexplorer.discord.CommandContext;
import com.xeon.runexplorer.discord.DiscordOptions;
import com.xeon.runexplorer.discord.PaginationManager;
import com.xeon.runexplorer.discord.SlashCommand;
import com.xeon.runexplorer.model.Game;
import com.xeon.runexplorer.model.HsMode;
import com.xeon.runexplorer.model.hiscores.HiscoresProfile;
import com.xeon.runexplorer.services.HiscoresService;
import com.xeon.runexplorer.util.CombatLevel;
import com.xeon.runexplorer.util.Embeds;
import com.xeon.runexplorer.util.Strings;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class PlayerCommand implements SlashCommand {

    private final HiscoresService hiscores;

    public PlayerCommand(HiscoresService hiscores) {
        this.hiscores = hiscores;
    }

    @Override
    public String name() {
        return "player";
    }

    @Override
    public SlashCommandData buildCommandData() {
        return Commands.slash("player", "Lookup a player hiscores profile (OSRS/RS3)")
                .addOptions(
                        new OptionData(OptionType.STRING, "name", "Player name", true),
                        DiscordOptions.gameOption(),
                        DiscordOptions.modeOption(),
                        new OptionData(OptionType.BOOLEAN, "bosses", "Include boss/activity section (OSRS best effort)", false)
                );
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event, CommandContext ctx) throws Exception {
        event.deferReply(ctx.isEphemeral()).queue();

        Game game = CommandContext.getGameOrDefault(event);
        HsMode mode = CommandContext.getModeOrDefault(event);

        String name = Strings.normalizePlayer(event.getOption("name").getAsString());
        boolean bosses = event.getOption("bosses", false, opt -> opt.getAsBoolean());

        HiscoresProfile p = hiscores.fetch(game, mode, name);

        int totalLevel = p.getSkillLevel("Overall");
        long totalXp = p.getSkillXp("Overall");

        int combat = -1;
        if (game == Game.OSRS) {
            combat = CombatLevel.osrsCombat(
                    p.getSkillLevel("Attack"),
                    p.getSkillLevel("Strength"),
                    p.getSkillLevel("Defence"),
                    p.getSkillLevel("Hitpoints"),
                    p.getSkillLevel("Prayer"),
                    p.getSkillLevel("Ranged"),
                    p.getSkillLevel("Magic")
            );
        }

        StringBuilder header = new StringBuilder();
        header.append(emoji("overall")).append(" Overall - **").append(totalLevel).append("** - ").append(totalXp);
        if (combat >= 0) {
            header.append("\n").append(emoji("combat")).append(" Combat - **").append(combat).append("**");
        }

        String title = p.player() + " (" + game.name() + ", " + mode.name() + ")";

        List<String> lines = buildPlayerSkillLines(game, p);

        List<MessageEmbed> pages = new ArrayList<>();
        pages.add(buildPlayerPageEmbed(title, header.toString(), lines, 0));
        pages.add(buildPlayerPageEmbed(title, header.toString(), lines, 1));

        String token = PaginationManager.newToken();
        long ownerId = event.getUser().getIdLong();
        int startPage = 0;

        event.getHook()
                .editOriginalEmbeds(pages.get(startPage))
                .setComponents(PaginationManager.components(token, startPage, pages.size()))
                .queue();

        PaginationManager.register(event.getHook(), token, ownerId, pages, startPage);

        if (bosses && game == Game.OSRS) {
            StringBuilder acts = new StringBuilder();
            int shown = 0;
            for (Map.Entry<String, com.xeon.runexplorer.model.hiscores.HiscoreEntry> e : p.activities().entrySet()) {
                long score = e.getValue().xpOrScore();
                if (score >= 0) {
                    acts.append(e.getKey()).append(": ").append(score).append("\n");
                    shown++;
                }
                if (shown >= 25) break;
            }
            String actText = acts.length() == 0 ? "No ranked activities found." : clampTo1024(acts.toString());

            event.getHook().sendMessageEmbeds(
                    Embeds.base("Bosses/activities (top shown)")
                            .setDescription(actText)
                            .build()
            ).queue();
        }
    }

    private static MessageEmbed buildPlayerPageEmbed(String title, String header, List<String> allLines, int pageIndex) {
        int start = (pageIndex == 0) ? 0 : 12;
        int end = (pageIndex == 0) ? Math.min(12, allLines.size()) : allLines.size();

        StringBuilder sb = new StringBuilder();
        for (int i = start; i < end; i++) {
            sb.append(allLines.get(i)).append("\n");
        }

        String footer = "Page " + (pageIndex + 1) + " / 2";

        return Embeds.base(title)
                .setDescription(header)
                .addField("Skills (" + footer + ")", clampTo1024(sb.toString()), false)
                .build();
    }

    private static List<String> buildPlayerSkillLines(Game game, HiscoresProfile p) {
        String[] order = new String[] {
                "Attack",
                "Defence",
                "Strength",
                "Hitpoints",
                "Ranged",
                "Prayer",
                "Magic",
                "Cooking",
                "Woodcutting",
                "Fletching",
                "Fishing",
                "Firemaking",
                "Crafting",
                "Smithing",
                "Mining",
                "Herblore",
                "Agility",
                "Thieving",
                "Slayer",
                "Farming",
                "Runecrafting",
                "Hunter",
                "Construction",
                "Sailing"
        };

        List<String> out = new ArrayList<>(order.length);

        for (String displayName : order) {
            String lookupName = displayName;

            if (game == Game.RS3 && "Hitpoints".equals(displayName)) {
                lookupName = "Constitution";
            }
            if (game == Game.OSRS && "Runecrafting".equals(displayName)) {
                lookupName = "Runecraft";
            }

            int level = p.getSkillLevel(lookupName);
            long xp = p.getSkillXp(lookupName);

            out.add(emojiForSkill(displayName) + " " + displayName + " - **" + level + "** - " + xp);
        }

        return out;
    }

    private static String clampTo1024(String s) {
        if (s == null) return "";
        if (s.length() <= 1024) return s;
        return s.substring(0, 1021) + "...";
    }

    private static String emojiForSkill(String displayName) {
        return switch (displayName) {
            case "Attack" -> emoji("attack");
            case "Defence" -> emoji("defence");
            case "Strength" -> emoji("strength");
            case "Hitpoints" -> emoji("hitpoints");
            case "Ranged" -> emoji("ranged");
            case "Prayer" -> emoji("prayer");
            case "Magic" -> emoji("magic");
            case "Cooking" -> emoji("cooking");
            case "Woodcutting" -> emoji("woodcutting");
            case "Fletching" -> emoji("fletching");
            case "Fishing" -> emoji("fishing");
            case "Firemaking" -> emoji("firemaking");
            case "Crafting" -> emoji("crafting");
            case "Smithing" -> emoji("smithing");
            case "Mining" -> emoji("mining");
            case "Herblore" -> emoji("herblore");
            case "Agility" -> emoji("agility");
            case "Thieving" -> emoji("thieving");
            case "Slayer" -> emoji("slayer");
            case "Farming" -> emoji("farming");
            case "Runecrafting" -> emoji("runecraft");
            case "Hunter" -> emoji("hunter");
            case "Construction" -> emoji("construction");
            case "Sailing" -> emoji("sailing");
            default -> "";
        };
    }

    private static String emoji(String key) {
        return switch (key) {
            case "agility" -> "<:agility:1457948048805335152>";
            case "attack" -> "<:attack:1457948050008834048>";
            case "construction" -> "<:construction:1457948805222895700>";
            case "cooking" -> "<:cooking:1457948858347688109>";
            case "crafting" -> "<:crafting:1457948808498643228>";
            case "defence" -> "<:defence:1457948859547521175>";
            case "farming" -> "<:farming:1457948811912810517>";
            case "firemaking" -> "<:firemaking:1457948794166579360>";
            case "fishing" -> "<:fishing:1457948795470872738>";
            case "fletching" -> "<:fletching:1457948797337604237>";
            case "herblore" -> "<:herblore:1457948798385914069>";
            case "hitpoints" -> "<:hitpoints:1457948799589814463>";
            case "hunter" -> "<:hunter:1457948801213136927>";
            case "magic" -> "<:magic:1457948803268346000>";
            case "mining" -> "<:mining:1457948804233035947>";
            case "overall" -> "<:overall:1457948031386255431>";
            case "prayer" -> "<:prayer:1457948033122828450>";
            case "ranged" -> "<:ranged:1457948036066967582>";
            case "runecraft" -> "<:runecraft:1457948040257212506>";
            case "slayer" -> "<:slayer:1457948041658241076>";
            case "smithing" -> "<:smithing:1457948042744565771>";
            case "strength" -> "<:strength:1457948044250320908>";
            case "thieving" -> "<:thieving:1457948045894488137>";
            case "woodcutting" -> "<:woodcutting:1457948047555170390>";
            case "combat" -> "<:combat:1457949365594230886>";
            case "sailing" -> "<:sailing:1457949648106029147>";
            default -> "";
        };
    }
}
