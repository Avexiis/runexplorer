package com.xeon.runexplorer.ui;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

public final class ConsoleOutputStream extends OutputStream {

    private final Consumer<String> sink;
    private final StringBuilder buffer = new StringBuilder(256);

    public ConsoleOutputStream(Consumer<String> sink) {
        this.sink = sink;
    }

    @Override
    public synchronized void write(int b) {
        char c = (char) (b & 0xFF);

        if (c == '\n') {
            flushLine();
            return;
        }

        buffer.append(c);

        // If no newline for a long time, flush anyway so you see progress
        if (buffer.length() >= 2048) {
            flushLine();
        }
    }

    @Override
    public synchronized void write(byte[] b, int off, int len) {
        if (b == null) return;
        if (len <= 0) return;

        String s = new String(b, off, len, StandardCharsets.UTF_8);
        for (int i = 0; i < s.length(); i++) {
            write((int) s.charAt(i));
        }
    }

    @Override
    public synchronized void flush() {
        flushLine();
    }

    private void flushLine() {
        if (buffer.length() == 0) return;
        sink.accept(buffer.toString() + "\n");
        buffer.setLength(0);
    }
}
