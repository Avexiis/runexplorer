package com.xeon.runexplorer.model.itemdb;

public record ItemDetail(
        int id,
        String name,
        String description,
        String type,
        String icon,
        String iconLarge,
        String members,
        String currentPrice,
        String todayPrice,
        String day30,
        String day90,
        String day180
) {
}
