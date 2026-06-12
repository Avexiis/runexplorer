package com.xeon.runexplorer.util;

public final class Strings {

    private Strings() {
    }

    public static String clamp(String s, int max) {
        if (s == null) return "";
        if (s.length() <= max) return s;
        return s.substring(0, Math.max(0, max - 3)) + "...";
    }

    public static String normalizePlayer(String name) {
        if (name == null) return "";
        return name.trim();
    }
}
