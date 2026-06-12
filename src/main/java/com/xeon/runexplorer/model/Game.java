package com.xeon.runexplorer.model;

public enum Game {
    OSRS("osrs"),
    RS3("rs3");

    private final String wire;

    Game(String wire) {
        this.wire = wire;
    }

    public String wire() {
        return wire;
    }

    public static Game fromWire(String v) {
        String s = (v == null ? "" : v.trim().toLowerCase());
        return switch (s) {
            case "rs3" -> RS3;
            case "osrs" -> OSRS;
            default -> OSRS;
        };
    }
}
