package com.xeon.runexplorer.config;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class BotConfig {

    private static final String CONFIG_PATH = "data/config.properties";
    private static BotConfig INSTANCE;

    public final String discordToken;
    public final Long ownerUserId;
    public final boolean defaultEphemeral;
    public final String databasePath;
    public final String httpUserAgent;
    public final int httpTimeoutMs;

    private BotConfig(Properties p) {
        this.discordToken = require(p, "discord.token");
        this.ownerUserId = optionalLong(p, "discord.ownerUserId");
        this.defaultEphemeral = Boolean.parseBoolean(p.getProperty("discord.defaultEphemeral", "false"));
        this.databasePath = p.getProperty("database.path", "data/bot.sqlite");
        this.httpUserAgent = require(p, "http.userAgent");
        this.httpTimeoutMs = Integer.parseInt(p.getProperty("http.timeoutMs", "10000"));
    }

    // Backwards-compatible alias for your existing code
    public static BotConfig fromEnv() {
        return load();
    }

    public static synchronized BotConfig load() {
        if (INSTANCE != null) {
            return INSTANCE;
        }

        Path path = Path.of(CONFIG_PATH);
        if (!Files.exists(path)) {
            throw new IllegalStateException(
                    "Missing config file: " + CONFIG_PATH + "\n" +
                            "Create it and add discord.token and http.userAgent"
            );
        }

        Properties p = new Properties();
        try (FileInputStream in = new FileInputStream(path.toFile())) {
            p.load(in);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read " + CONFIG_PATH, e);
        }

        INSTANCE = new BotConfig(p);
        return INSTANCE;
    }

    // Record-style accessors to match your current calls
    public String discordToken() {
        return discordToken;
    }

    public Long ownerUserId() {
        return ownerUserId;
    }

    public boolean defaultEphemeral() {
        return defaultEphemeral;
    }

    public String databasePath() {
        return databasePath;
    }

    public String httpUserAgent() {
        return httpUserAgent;
    }

    public int httpTimeoutMs() {
        return httpTimeoutMs;
    }

    private static String require(Properties p, String key) {
        String v = p.getProperty(key);
        if (v == null || v.isBlank()) {
            throw new IllegalStateException("Missing required config key: " + key);
        }
        return v.trim();
    }

    private static Long optionalLong(Properties p, String key) {
        String v = p.getProperty(key);
        if (v == null || v.isBlank()) return null;
        return Long.parseLong(v.trim());
    }
}
