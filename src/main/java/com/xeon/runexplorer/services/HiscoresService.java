package com.xeon.runexplorer.services;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.xeon.runexplorer.model.Game;
import com.xeon.runexplorer.model.HsMode;
import com.xeon.runexplorer.model.hiscores.HiscoreEntry;
import com.xeon.runexplorer.model.hiscores.HiscoresProfile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

public final class HiscoresService {

    private final HttpService http;

    private final Cache<String, HiscoresProfile> cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(3))
            .maximumSize(5_000)
            .build();

    public HiscoresService(HttpService http) {
        this.http = http;
    }

    public HiscoresProfile fetch(Game game, HsMode mode, String player) throws Exception {
        String key = game.wire() + ":" + mode.wire() + ":" + player.toLowerCase();
        HiscoresProfile cached = cache.getIfPresent(key);
        if (cached != null) return cached;

        String url = buildEndpoint(game, mode, player);
        String body = http.get(url);

        HiscoresProfile parsed = parseIndexLite(game, player, body);
        cache.put(key, parsed);
        return parsed;
    }

    // RS3: m=hiscore/index_lite.ws
    // OSRS: m=hiscore_oldschool*/index_lite.ws (documented on the API page) :contentReference[oaicite:3]{index=3}
    private static String buildEndpoint(Game game, HsMode mode, String player) {
        String p = URLEncoder.encode(player, StandardCharsets.UTF_8);

        if (game == Game.OSRS) {
            String base = switch (mode) {
                case IRONMAN -> "http://services.runescape.com/m=hiscore_oldschool_ironman/index_lite.ws?player=";
                case ULTIMATE -> "http://services.runescape.com/m=hiscore_oldschool_ultimate/index_lite.ws?player=";
                case HARDCORE -> "http://services.runescape.com/m=hiscore_oldschool_hardcore_ironman/index_lite.ws?player=";
                default -> "http://services.runescape.com/m=hiscore_oldschool/index_lite.ws?player=";
            };
            return base + p;
        } else {
            String base = switch (mode) {
                case IRONMAN -> "http://services.runescape.com/m=hiscore_ironman/index_lite.ws?player=";
                case HARDCORE -> "http://services.runescape.com/m=hiscore_hardcore_ironman/index_lite.ws?player=";
                default -> "http://services.runescape.com/m=hiscore/index_lite.ws?player=";
            };
            return base + p;
        }
    }

    private static HiscoresProfile parseIndexLite(Game game, String player, String body) {
        // index_lite is CSV lines: rank,level,xpOrScore
        // The ordering is fixed by game.
        HiscoresProfile prof = new HiscoresProfile(player);

        List<String> skillOrder = (game == Game.OSRS) ? osrsSkillOrder() : rs3SkillOrder();
        List<String> activityOrder = (game == Game.OSRS) ? osrsActivityOrder() : rs3ActivityOrder();

        String[] lines = body.split("\\r?\\n");
        int idx = 0;

        for (String skill : skillOrder) {
            if (idx >= lines.length) break;
            HiscoreEntry e = parseLine(lines[idx++]);
            prof.skills().put(skill, e);
        }

        for (String act : activityOrder) {
            if (idx >= lines.length) break;
            HiscoreEntry e = parseLine(lines[idx++]);
            prof.activities().put(act, e);
        }

        return prof;
    }

    private static HiscoreEntry parseLine(String line) {
        String[] parts = line.split(",");
        if (parts.length < 3) return new HiscoreEntry(-1, -1, -1);
        int rank = parseInt(parts[0]);
        int level = parseInt(parts[1]);
        long xp = parseLong(parts[2]);
        return new HiscoreEntry(rank, level, xp);
    }

    private static int parseInt(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return -1; }
    }

    private static long parseLong(String s) {
        try { return Long.parseLong(s.trim()); } catch (Exception e) { return -1L; }
    }

    private static List<String> osrsSkillOrder() {
        return List.of(
                "Overall",
                "Attack","Defence","Strength","Hitpoints","Ranged","Prayer","Magic",
                "Cooking","Woodcutting","Fletching","Fishing","Firemaking","Crafting","Smithing","Mining",
                "Herblore","Agility","Thieving","Slayer","Farming","Runecraft","Hunter","Construction", "Sailing"
        );
    }

    private static List<String> rs3SkillOrder() {
        return List.of(
                "Overall",
                "Attack","Defence","Strength","Constitution","Ranged","Prayer","Magic",
                "Cooking","Woodcutting","Fletching","Fishing","Firemaking","Crafting","Smithing","Mining",
                "Herblore","Agility","Thieving","Slayer","Farming","Runecrafting","Hunter","Construction",
                "Summoning","Dungeoneering","Divination","Invention","Archaeology","Necromancy"
        );
    }

    private static List<String> osrsActivityOrder() {
        // This list evolves over time. We keep a practical subset with common bosses + key activities.
        // Anything extra returned by the endpoint but not listed here is simply not displayed.
        return List.of(
                "League Points",
                "Bounty Hunter - Hunter","Bounty Hunter - Rogue",
                "Clue Scrolls (all)","Clue Scrolls (beginner)","Clue Scrolls (easy)","Clue Scrolls (medium)","Clue Scrolls (hard)","Clue Scrolls (elite)","Clue Scrolls (master)",
                "LMS - Rank",
                "Soul Wars Zeal",
                "Rifts Closed",
                "Abyssal Sire","Alchemical Hydra","Barrows Chests","Bryophyta","Callisto","Cerberus",
                "Chambers of Xeric","Chambers of Xeric: Challenge Mode",
                "Chaos Elemental","Chaos Fanatic","Commander Zilyana","Corporeal Beast","Crazy Archaeologist",
                "Dagannoth Prime","Dagannoth Rex","Dagannoth Supreme",
                "Deranged Archaeologist","General Graardor","Giant Mole","Grotesque Guardians",
                "Hespori","Kalphite Queen","King Black Dragon","Kraken","Kree'Arra","K'ril Tsutsaroth",
                "Mimic","Nex","Nightmare","Phosani's Nightmare","Obor",
                "Sarachnis","Scorpia","Skotizo","Tempoross","The Gauntlet","The Corrupted Gauntlet",
                "Theatre of Blood","Theatre of Blood: Hard Mode",
                "Thermonuclear Smoke Devil","Tombs of Amascut","Tombs of Amascut: Expert Mode",
                "TzKal-Zuk","TzTok-Jad",
                "Venenatis","Vet'ion","Vorkath","Wintertodt","Zalcano","Zulrah"
        );
    }

    private static List<String> rs3ActivityOrder() {
        // RS3 index_lite includes many minigames. Boss kill counts are not generally present here.
        return List.of(
                "Bounty Hunter",
                "Clue Scrolls (easy)","Clue Scrolls (medium)","Clue Scrolls (hard)","Clue Scrolls (elite)","Clue Scrolls (master)",
                "Dominion Tower","Duel Tournament","Fist of Guthix","Mobilising Armies","RuneScore"
        );
    }
}
