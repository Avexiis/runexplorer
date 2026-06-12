package com.xeon.runexplorer.services;

import com.xeon.runexplorer.model.Game;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class NewsService {

    private final HttpService http;

    public NewsService(HttpService http) {
        this.http = http;
    }

    public List<NewsItem> fetchLatest(Game game, int limit) throws Exception {
        // We use the official archive page HTML and parse it.
        // The archive endpoint exists for OSRS and RS3.
        // Example OSRS archive URL: secure.runescape.com/m=news/a=373/archive?oldschool=1 :contentReference[oaicite:6]{index=6}
        String url = (game == Game.OSRS)
                ? "https://secure.runescape.com/m=news/a=373/archive?oldschool=1"
                : "https://secure.runescape.com/m=news/archive";

        String html = http.get(url);
        Document doc = Jsoup.parse(html, url);

        // The page structure can change. We do a broad selection: links that look like news posts.
        Elements links = doc.select("a[href*=/m=news/]");
        List<NewsItem> out = new ArrayList<>();

        for (Element a : links) {
            String href = a.absUrl("href");
            String title = a.text().trim();
            if (title.isBlank()) continue;

            // Keep only OSRS posts when oldschool=1 is present, and avoid archive page itself.
            if (game == Game.OSRS && !href.contains("oldschool=1")) continue;
            if (href.contains("archive")) continue;

            // Deduplicate by href
            boolean exists = out.stream().anyMatch(x -> x.url().equals(href));
            if (exists) continue;

            out.add(new NewsItem(title, href, null));
            if (out.size() >= limit) break;
        }

        return out;
    }

    public record NewsItem(String title, String url, LocalDate date) {
    }
}
