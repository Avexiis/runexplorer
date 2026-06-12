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

public final class CompareCommand implements SlashCommand {

    private final HiscoresService hiscores;

    // Discord embed field limit is 1024 chars. Keep a small buffer so we never hit edge cases.
    private static final int FIELD_LIMIT_SAFE = 1000;

    public CompareCommand(HiscoresService hiscores) {
        this.hiscores = hiscores;
    }

    @Override
    public String name() {
        return "compare";
    }

    @Override
    public SlashCommandData buildCommandData() {
        return Commands.slash("compare", "Compare two players (OSRS/RS3)")
                .addOptions(
                        new OptionData(OptionType.STRING, "player1", "Player 1 name", true),
                        new OptionData(OptionType.STRING, "player2", "Player 2 name", true),
                        DiscordOptions.gameOption(),
                        DiscordOptions.modeOption()
                );
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event, CommandContext ctx) throws Exception {
        event.deferReply(ctx.isEphemeral()).queue();

        Game game = CommandContext.getGameOrDefault(event);
        HsMode mode = CommandContext.getModeOrDefault(event);

        String p1 = Strings.normalizePlayer(event.getOption("player1").getAsString());
        String p2 = Strings.normalizePlayer(event.getOption("player2").getAsString());

        HiscoresProfile a = hiscores.fetch(game, mode, p1);
        HiscoresProfile b = hiscores.fetch(game, mode, p2);

        int aTotal = a.getSkillLevel("Overall");
        int bTotal = b.getSkillLevel("Overall");

        long aXp = a.getSkillXp("Overall");
        long bXp = b.getSkillXp("Overall");

        int aCombat = -1;
        int bCombat = -1;
        if (game == Game.OSRS) {
            aCombat = CombatLevel.osrsCombat(
                    a.getSkillLevel("Attack"),
                    a.getSkillLevel("Strength"),
                    a.getSkillLevel("Defence"),
                    a.getSkillLevel("Hitpoints"),
                    a.getSkillLevel("Prayer"),
                    a.getSkillLevel("Ranged"),
                    a.getSkillLevel("Magic")
            );
            bCombat = CombatLevel.osrsCombat(
                    b.getSkillLevel("Attack"),
                    b.getSkillLevel("Strength"),
                    b.getSkillLevel("Defence"),
                    b.getSkillLevel("Hitpoints"),
                    b.getSkillLevel("Prayer"),
                    b.getSkillLevel("Ranged"),
                    b.getSkillLevel("Magic")
            );
        }

        StringBuilder leftHeader = new StringBuilder();
        leftHeader.append(emoji("overall")).append(" Overall - **").append(aTotal).append("** - ").append(aXp);
        if (aCombat >= 0) leftHeader.append("\n").append(emoji("combat")).append(" Combat - **").append(aCombat).append("**");

        StringBuilder rightHeader = new StringBuilder();
        rightHeader.append(emoji("overall")).append(" Overall - **").append(bTotal).append("** - ").append(bXp);
        if (bCombat >= 0) rightHeader.append("\n").append(emoji("combat")).append(" Combat - **").append(bCombat).append("**");

        String title = "Compare (" + game.name() + ", " + mode.name() + ")";

        List<SkillRow> rows = buildCompareSkillRows(game, a, b);

        // Build pages by true char budget, so we never clamp/truncate mid-line.
        List<SkillPage> skillPages = paginateSkillRows(rows);

        List<MessageEmbed> pages = new ArrayList<>();
        for (int i = 0; i < skillPages.size(); i++) {
            pages.add(buildComparePageEmbed(
                    title,
                    a.player(), leftHeader.toString(),
                    b.player(), rightHeader.toString(),
                    skillPages.get(i),
                    i,
                    skillPages.size()
            ));
        }

        String token = PaginationManager.newToken();
        long ownerId = event.getUser().getIdLong();
        int startPage = 0;

        event.getHook()
                .editOriginalEmbeds(pages.get(startPage))
                .setComponents(PaginationManager.components(token, startPage, pages.size()))
                .queue();

        PaginationManager.register(event.getHook(), token, ownerId, pages, startPage);
    }

    private static MessageEmbed buildComparePageEmbed(
            String title,
            String leftName,
            String leftHeader,
            String rightName,
            String rightHeader,
            SkillPage page,
            int pageIndex,
            int pageCount
    ) {
        String footer = "Page " + (pageIndex + 1) + " / " + pageCount;

        // Page 2+: do not show combat/total/overall headers at all.
        if (pageIndex >= 1) {
            return Embeds.base(title)
                    .addField("Skills (" + footer + ")", "", false)
                    .addField(leftName, page.leftText(), true)
                    .addField(rightName, page.rightText(), true)
                    .build();
        }

        // Page 1: include headers plus skill columns.
        return Embeds.base(title)
                .addField(leftName, clampTo1024(leftHeader), true)
                .addField(rightName, clampTo1024(rightHeader), true)
                .addField("Skills (" + footer + ")", "", false)
                .addField(leftName + " skills", page.leftText(), true)
                .addField(rightName + " skills", page.rightText(), true)
                .build();
    }

    private static List<SkillRow> buildCompareSkillRows(Game game, HiscoresProfile a, HiscoresProfile b) {
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

        List<SkillRow> out = new ArrayList<>(order.length);

        for (String displayName : order) {
            String lookupName = displayName;

            if (game == Game.RS3 && "Hitpoints".equals(displayName)) {
                lookupName = "Constitution";
            }
            if (game == Game.OSRS && "Runecrafting".equals(displayName)) {
                lookupName = "Runecraft";
            }

            int aLvl = a.getSkillLevel(lookupName);
            int bLvl = b.getSkillLevel(lookupName);

            String e = emojiForSkill(displayName);

            // Each player gets their own separate line in their own embed field.
            // Skill name is on both sides by definition (because each field prints the name).
            String leftLine = e + " " + displayName + " - **" + aLvl + "**";
            String rightLine = e + " " + displayName + " - **" + bLvl + "**";

            out.add(new SkillRow(leftLine, rightLine));
        }

        return out;
    }

    private static List<SkillPage> paginateSkillRows(List<SkillRow> rows) {
        List<SkillPage> pages = new ArrayList<>();

        StringBuilder left = new StringBuilder();
        StringBuilder right = new StringBuilder();

        for (SkillRow r : rows) {
            String l = r.leftLine() + "\n";
            String rr = r.rightLine() + "\n";

            // If adding this row would overflow either field, start a new page.
            if ((left.length() + l.length() > FIELD_LIMIT_SAFE) || (right.length() + rr.length() > FIELD_LIMIT_SAFE)) {
                if (left.length() == 0 && right.length() == 0) {
                    // Extremely defensive: if a single line is somehow too big, hard cut (should never happen).
                    pages.add(new SkillPage(
                            clampTo1024(r.leftLine()),
                            clampTo1024(r.rightLine())
                    ));
                } else {
                    pages.add(new SkillPage(
                            clampTo1024(trimTrailingNewline(left)),
                            clampTo1024(trimTrailingNewline(right))
                    ));
                    left.setLength(0);
                    right.setLength(0);

                    // Add the row to the new page.
                    left.append(l);
                    right.append(rr);
                }
            } else {
                left.append(l);
                right.append(rr);
            }
        }

        if (left.length() > 0 || right.length() > 0) {
            pages.add(new SkillPage(
                    clampTo1024(trimTrailingNewline(left)),
                    clampTo1024(trimTrailingNewline(right))
            ));
        }

        // Ensure at least 2 pages if you want the button UX to always be consistent.
        // If you prefer "no buttons when only one page", remove this block.
        if (pages.size() == 1) {
            pages.add(new SkillPage("", ""));
        }

        return pages;
    }

    private static String trimTrailingNewline(StringBuilder sb) {
        int len = sb.length();
        if (len == 0) return "";
        if (sb.charAt(len - 1) == '\n') return sb.substring(0, len - 1);
        return sb.toString();
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

    private record SkillRow(String leftLine, String rightLine) {
    }

    private record SkillPage(String leftText, String rightText) {
    }
}
