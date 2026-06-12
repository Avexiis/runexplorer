// com.xeon.runexplorer.ui.Ansi
package com.xeon.runexplorer.ui;

public final class Ansi {

    private Ansi() {
    }

    private static final String ESC = "\u001B[";
    private static final String RESET = ESC + "0m";

    public static String reset() {
        return RESET;
    }

    public static String fgRed(String s) {
        return ESC + "31m" + s + RESET;
    }

    public static String fgGreen(String s) {
        return ESC + "32m" + s + RESET;
    }

    public static String fgYellow(String s) {
        return ESC + "33m" + s + RESET;
    }

    public static String fgBlue(String s) {
        return ESC + "34m" + s + RESET;
    }

    public static String fgMagenta(String s) {
        return ESC + "35m" + s + RESET;
    }

    public static String fgCyan(String s) {
        return ESC + "36m" + s + RESET;
    }

    public static String fgBrightBlack(String s) {
        return ESC + "90m" + s + RESET;
    }
}
