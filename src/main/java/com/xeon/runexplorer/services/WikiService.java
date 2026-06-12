package com.xeon.runexplorer.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.xeon.runexplorer.model.Game;
import com.xeon.runexplorer.model.wiki.WikiPageSummary;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class WikiService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final HttpService http;

    private final Cache<String, WikiPageSummary> summaryCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofHours(6))
            .maximumSize(10_000)
            .build();

    private final Cache<String, Integer> itemIdCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofHours(24))
            .maximumSize(50_000)
            .build();

    private final Cache<String, List<WikiSearchHit>> searchCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(30))
            .maximumSize(10_000)
            .build();

    public WikiService(HttpService http) {
        this.http = http;
    }

    public WikiPageSummary fetchSummary(Game game, String pageTitle) throws Exception {
        String normalizedTitle = pageTitle == null ? "" : pageTitle.trim();
        if (normalizedTitle.isEmpty()) {
            throw new IllegalArgumentException("Wiki page title cannot be empty.");
        }

        String key = game.wire() + ":summary:" + normalizedTitle.toLowerCase(Locale.ROOT);
        WikiPageSummary c = summaryCache.getIfPresent(key);
        if (c != null) return c;

        String api = wikiApiBase(game);
        String title = URLEncoder.encode(normalizedTitle, StandardCharsets.UTF_8);

        String url = api
                + "?action=query"
                + "&format=json"
                + "&prop=extracts%7Cinfo"
                + "&inprop=url"
                + "&exintro=1"
                + "&explaintext=1"
                + "&redirects=1"
                + "&titles=" + title;

        JsonNode root = MAPPER.readTree(http.get(url));
        JsonNode pages = root.path("query").path("pages");

        JsonNode first = pages.elements().hasNext() ? pages.elements().next() : null;
        if (first == null || first.has("missing")) {
            throw new IllegalArgumentException("Wiki page not found: " + normalizedTitle);
        }

        String t = first.path("title").asText(normalizedTitle);
        String fullurl = first.path("fullurl").asText("");
        String extract = first.path("extract").asText("");

        WikiPageSummary s = new WikiPageSummary(t, fullurl, extract);
        summaryCache.put(key, s);
        return s;
    }

    public Integer resolveItemId(Game game, String itemName) throws Exception {
        String normalized = itemName == null ? "" : itemName.trim();
        if (normalized.isEmpty()) return null;

        String key = game.wire() + ":itemid:" + normalized.toLowerCase(Locale.ROOT);
        Integer cached = itemIdCache.getIfPresent(key);
        if (cached != null) return cached;

        String api = wikiApiBase(game);
        String title = URLEncoder.encode(normalized, StandardCharsets.UTF_8);

        String url = api
                + "?action=parse"
                + "&format=json"
                + "&prop=wikitext"
                + "&redirects=1"
                + "&page=" + title;

        JsonNode root = MAPPER.readTree(http.get(url));
        String wikitext = root.path("parse").path("wikitext").path("*").asText("");

        Integer found = extractFirstIntAfter(wikitext, "itemid")
                .or(() -> extractFirstIntAfter(wikitext, "item_id"))
                .or(() -> extractFirstIntAfter(wikitext, "id"))
                .orElse(null);

        if (found != null && found > 0) {
            itemIdCache.put(key, found);
        }
        return found;
    }

    public WikiPageSummary searchBestSummary(Game game, String query, int limit) throws Exception {
        String q = normalizeQuery(query);
        if (q.isEmpty()) return null;

        try {
            return fetchSummary(game, query);
        } catch (Exception ignored) {}

        List<WikiSearchHit> hits = search(game, q, clampLimit(limit));
        if (hits.isEmpty()) return null;

        WikiSearchHit best = pickBest(hits, q);
        if (best == null) return null;

        try {
            return fetchSummary(game, best.title());
        } catch (Exception ignored) {
            String url = wikiPageUrl(game, best.title());
            return new WikiPageSummary(best.title(), url, "");
        }
    }

    public List<WikiSearchHit> search(Game game, String query, int limit) throws Exception {
        String q = normalizeQuery(query);
        if (q.isEmpty()) return List.of();

        String key = game.wire() + ":search:" + q.toLowerCase(Locale.ROOT) + ":" + limit;
        List<WikiSearchHit> cached = searchCache.getIfPresent(key);
        if (cached != null) return cached;

        String api = wikiApiBase(game);
        String encoded = URLEncoder.encode(q, StandardCharsets.UTF_8);

        String url = api
                + "?action=query"
                + "&format=json"
                + "&list=search"
                + "&srsearch=" + encoded
                + "&srlimit=" + limit
                + "&srprop=snippet";

        JsonNode root = MAPPER.readTree(http.get(url));
        JsonNode arr = root.path("query").path("search");

        List<WikiSearchHit> out = new ArrayList<>();
        if (arr != null && arr.isArray()) {
            for (JsonNode n : arr) {
                String title = n.path("title").asText("");
                String snippet = n.path("snippet").asText("");
                if (!title.isBlank()) {
                    out.add(new WikiSearchHit(title, snippet));
                }
            }
        }

        searchCache.put(key, out);
        return out;
    }

    private static WikiSearchHit pickBest(List<WikiSearchHit> hits, String rawQuery) {
        String q = normalizeQuery(rawQuery).toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return null;

        Pattern tokenPattern = buildTokenRegex(q);

        int bestScore = Integer.MIN_VALUE;
        WikiSearchHit best = null;

        for (WikiSearchHit h : hits) {
            String title = h.title() == null ? "" : h.title().trim();
            if (title.isEmpty()) continue;

            String t = title.toLowerCase(Locale.ROOT);

            int score = 0;

            if (t.equals(q)) score += 2000;
            if (stripParens(t).equals(stripParens(q))) score += 1200;

            if (t.startsWith(q)) score += 900;
            if (t.contains(q)) score += 700;

            if (tokenPattern != null && tokenPattern.matcher(t).find()) score += 600;

            score += tokenPresenceBonus(t, q);

            if (t.contains("(disambiguation)") && !q.contains("disambiguation")) score -= 250;

            if (t.length() > 60 && q.length() < 20) score -= 50;

            String snip = h.snippet() == null ? "" : h.snippet().toLowerCase(Locale.ROOT);
            if (!snip.isEmpty() && snip.contains(q)) score += 75;

            if (score > bestScore) {
                bestScore = score;
                best = h;
            }
        }

        return best;
    }

    private static int tokenPresenceBonus(String titleLower, String queryLower) {
        String[] tokens = tokenize(queryLower);
        if (tokens.length == 0) return 0;

        int present = 0;
        for (String tok : tokens) {
            if (tok.isEmpty()) continue;
            if (titleLower.contains(tok)) present++;
        }

        return (present * 240) / Math.max(1, tokens.length);
    }

    private static Pattern buildTokenRegex(String queryLower) {
        String[] tokens = tokenize(queryLower);
        if (tokens.length == 0) return null;

        StringBuilder sb = new StringBuilder();
        sb.append(".*");
        for (String tok : tokens) {
            if (tok.isEmpty()) continue;
            sb.append(Pattern.quote(tok)).append("[^a-z0-9]*");
        }
        sb.append(".*");

        try {
            return Pattern.compile(sb.toString(), Pattern.CASE_INSENSITIVE);
        } catch (Exception e) {
            return null;
        }
    }

    private static String[] tokenize(String s) {
        if (s == null) return new String[0];
        String cleaned = s.toLowerCase(Locale.ROOT).trim();
        if (cleaned.isEmpty()) return new String[0];

        cleaned = cleaned.replaceAll("[^a-z0-9\\s]+", " ");
        cleaned = cleaned.replaceAll("\\s+", " ").trim();
        if (cleaned.isEmpty()) return new String[0];

        return cleaned.split(" ");
    }

    private static String normalizeQuery(String s) {
        if (s == null) return "";
        String t = s.trim();
        t = t.replaceAll("\\s+", " ");
        return t.trim();
    }

    private static int clampLimit(int limit) {
        if (limit < 1) return 1;
        if (limit > 20) return 20;
        return limit;
    }

    private static String stripParens(String s) {
        if (s == null) return "";
        return s.replaceAll("\\s*\\(.*?\\)\\s*", "").trim();
    }

    private static String wikiApiBase(Game game) {
        return (game == Game.OSRS)
                ? "https://oldschool.runescape.wiki/api.php"
                : "https://runescape.wiki/api.php";
    }

    private static String wikiPageBase(Game game) {
        return (game == Game.OSRS)
                ? "https://oldschool.runescape.wiki/w/"
                : "https://runescape.wiki/w/";
    }

    private static String wikiPageUrl(Game game, String title) {
        if (title == null) title = "";
        String t = title.trim().replace(" ", "_");
        return wikiPageBase(game) + t;
    }

    private static Optional<Integer> extractFirstIntAfter(String wikitext, String key) {
        String pattern = "\\|\\s*" + Pattern.quote(key) + "\\s*=\\s*(\\d+)";
        Matcher m = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(wikitext);
        if (m.find()) {
            try {
                return Optional.of(Integer.parseInt(m.group(1)));
            } catch (Exception e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    public record WikiSearchHit(String title, String snippet) {}
}
