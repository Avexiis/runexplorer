package com.xeon.runexplorer.services;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class HttpService {

    private final HttpClient client;
    private final String userAgent;
    private final int timeoutMs;

    public HttpService(String userAgent, int timeoutMs) {
        this.userAgent = userAgent;
        this.timeoutMs = timeoutMs;

        this.client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .build();
    }

    public String get(String url) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMillis(timeoutMs))
                .header("User-Agent", userAgent)
                .header("Accept", "text/html,application/json,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .GET()
                .build();

        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() < 200 || res.statusCode() >= 300) {
            throw new IOException("HTTP " + res.statusCode() + " for " + url);
        }
        return res.body();
    }
}
