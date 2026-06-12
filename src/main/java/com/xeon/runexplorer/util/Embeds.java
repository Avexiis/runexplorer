package com.xeon.runexplorer.util;

import net.dv8tion.jda.api.EmbedBuilder;

import java.time.Instant;

public final class Embeds {

    private Embeds() {
    }

    public static EmbedBuilder base(String title) {
        return new EmbedBuilder()
                .setTitle(title)
                .setTimestamp(Instant.now());
    }
}
