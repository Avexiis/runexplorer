package com.xeon.runexplorer.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.xeon.runexplorer.model.Game;
import com.xeon.runexplorer.model.itemdb.ItemDetail;

import java.time.Duration;

public final class ItemPriceService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpService http;
    private final WikiService wiki;

    private final Cache<String, ItemDetail> cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(2))
            .maximumSize(10_000)
            .build();

    public ItemPriceService(HttpService http, WikiService wiki) {
        this.http = http;
        this.wiki = wiki;
    }

    public ItemDetail fetchByName(Game game, String itemName) throws Exception {
        Integer id = wiki.resolveItemId(game, itemName);
        if (id == null) {
            throw new IllegalArgumentException("Could not resolve item id from wiki. Try a more exact item name, or use /price with an id (example: \"Abyssal whip (id: 4151)\").");
        }
        return fetchById(game, id);
    }

    public ItemDetail fetchById(Game game, int id) throws Exception {
        String key = game.wire() + ":id:" + id;
        ItemDetail c = cache.getIfPresent(key);
        if (c != null) return c;

        String base = (game == Game.OSRS)
                ? "http://services.runescape.com/m=itemdb_oldschool/api/catalogue/detail.json?item="
                : "http://services.runescape.com/m=itemdb_rs/api/catalogue/detail.json?item=";

        // "detail.json?item=X" is documented for the RS3 endpoint (and OSRS uses the same shape). :contentReference[oaicite:4]{index=4}
        String body = http.get(base + id);
        JsonNode root = MAPPER.readTree(body);
        JsonNode item = root.path("item");

        ItemDetail detail = new ItemDetail(
                item.path("id").asInt(id),
                item.path("name").asText(""),
                item.path("description").asText(""),
                item.path("type").asText(""),
                item.path("icon").asText(""),
                item.path("icon_large").asText(""),
                item.path("members").asText(""),
                item.path("current").path("price").asText(""),
                item.path("today").path("price").asText(""),
                item.path("day30").path("change").asText(""),
                item.path("day90").path("change").asText(""),
                item.path("day180").path("change").asText("")
        );

        cache.put(key, detail);
        return detail;
    }
}
