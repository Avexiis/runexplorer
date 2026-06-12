package com.xeon.runexplorer.model;

public enum HsMode {
    NORMAL("normal"),
    IRONMAN("ironman"),
    HARDCORE("hardcore"),
    ULTIMATE("ultimate");

    private final String wire;

    HsMode(String wire) {
        this.wire = wire;
    }

    public String wire() {
        return wire;
    }

    public static HsMode fromWire(String v) {
        String s = (v == null ? "" : v.trim().toLowerCase());
        return switch (s) {
            case "ironman" -> IRONMAN;
            case "hardcore" -> HARDCORE;
            case "ultimate" -> ULTIMATE;
            default -> NORMAL;
        };
    }
}
