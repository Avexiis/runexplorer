package com.xeon.runexplorer.services;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PopulationService {

    private final HttpService http;

    public PopulationService(HttpService http) {
        this.http = http;
    }

    public String fetchRaw() throws Exception {
        // player_count.js is documented here :contentReference[oaicite:5]{index=5}
        String url = "http://www.runescape.com/player_count.js?varname=iPlayerCount&callback=jQuery000000000000000_0000000000&_=0";
        return http.get(url);
    }

    public PopCounts parse(String js) {
        // Try to extract numbers in the payload.
        // This endpoint is a JSONP-ish JS snippet and may change format; keep parsing tolerant.
        // Commonly it includes 3 numbers (rs3, osrs, classic).
        Matcher m = Pattern.compile("(\\d+)").matcher(js);
        int[] nums = new int[10];
        int n = 0;
        while (m.find() && n < nums.length) {
            nums[n++] = Integer.parseInt(m.group(1));
        }

        int rs3 = n > 0 ? nums[0] : -1;
        int osrs = n > 1 ? nums[1] : -1;
        int classic = n > 2 ? nums[2] : -1;
        return new PopCounts(rs3, osrs, classic);
    }

    public record PopCounts(int rs3, int osrs, int classic) {
    }
}
