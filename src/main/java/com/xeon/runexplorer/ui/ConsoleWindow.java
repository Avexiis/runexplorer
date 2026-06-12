// com.xeon.runexplorer.ui.ConsoleWindow
package com.xeon.runexplorer.ui;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class ConsoleWindow {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JFrame frame;
    private final JTextPane pane;
    private final JButton clearButton;
    private final JCheckBox autoScroll;
    private final JLabel statusLabel;

    private final PrintStream originalOut;
    private final PrintStream originalErr;

    private final Runnable onClose;

    private volatile boolean started;

    public ConsoleWindow(String title, Runnable onClose) {
        this.originalOut = System.out;
        this.originalErr = System.err;
        this.onClose = onClose;

        this.frame = new JFrame(title);
        this.pane = new JTextPane();
        this.clearButton = new JButton("Clear");
        this.autoScroll = new JCheckBox("Auto-scroll", true);
        this.statusLabel = new JLabel("Ready");

        this.pane.setEditable(false);
        this.pane.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        // Use LAF defaults (FlatDarkLaf will provide dark background)
        Color bg = UIManager.getColor("TextArea.background");
        Color fg = UIManager.getColor("TextArea.foreground");
        if (bg != null) pane.setBackground(bg);
        if (fg != null) pane.setForeground(fg);

        JScrollPane scroll = new JScrollPane(pane);
        scroll.setPreferredSize(new Dimension(900, 520));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(clearButton);
        top.add(autoScroll);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(statusLabel, BorderLayout.WEST);

        clearButton.addActionListener(e -> pane.setText(""));

        frame.setLayout(new BorderLayout());
        frame.add(top, BorderLayout.NORTH);
        frame.add(scroll, BorderLayout.CENTER);
        frame.add(bottom, BorderLayout.SOUTH);

        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                appendAnsiLine("\u001B[33m[UI]\u001B[0m Window closing. Shutting down...");
                try {
                    stopCapturingStdout();
                } catch (Throwable ignored) {
                }
                if (onClose != null) {
                    try {
                        onClose.run();
                    } catch (Throwable ignored) {
                    }
                } else {
                    frame.dispose();
                }
            }
        });

        frame.pack();
        frame.setLocationRelativeTo(null);
    }

    public void showWindow() {
        SwingUtilities.invokeLater(() -> frame.setVisible(true));
    }

    public void setStatus(String status) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(status));
    }

    public void startCapturingStdout() {
        if (started) return;
        started = true;

        ConsoleOutputStream outStream = new ConsoleOutputStream(this::appendAnsiRaw);
        ConsoleOutputStream errStream = new ConsoleOutputStream(this::appendAnsiRaw);

        PrintStream psOut = new PrintStream(outStream, true, StandardCharsets.UTF_8);
        PrintStream psErr = new PrintStream(errStream, true, StandardCharsets.UTF_8);

        System.setOut(psOut);
        System.setErr(psErr);

        appendAnsiLine("\u001B[90m[UI]\u001B[0m Capturing System.out/System.err at " + now());
    }

    public void stopCapturingStdout() {
        System.setOut(originalOut);
        System.setErr(originalErr);
        appendAnsiLine("\u001B[90m[UI]\u001B[0m Restored System.out/System.err at " + now());
    }

    public void appendAnsiLine(String line) {
        appendAnsiRaw(line + "\n");
    }

    private String now() {
        return LocalDateTime.now().format(TS);
    }

    private void appendAnsiRaw(String text) {
        SwingUtilities.invokeLater(() -> {
            StyledDocument doc = pane.getStyledDocument();
            AnsiState state = new AnsiState();

            try {
                insertAnsi(doc, text, state);
            } catch (Throwable ignored) {
                try {
                    doc.insertString(doc.getLength(), text, null);
                } catch (BadLocationException ignored2) {
                }
            }

            if (autoScroll.isSelected()) {
                pane.setCaretPosition(doc.getLength());
            }
        });
    }

    private static void insertAnsi(StyledDocument doc, String text, AnsiState state) throws BadLocationException {
        int i = 0;
        while (i < text.length()) {
            int esc = text.indexOf('\u001B', i);
            if (esc < 0) {
                insertWithStyle(doc, text.substring(i), state);
                return;
            }

            // Insert plain text before escape
            if (esc > i) {
                insertWithStyle(doc, text.substring(i, esc), state);
            }

            // Parse CSI: ESC [ ... m
            int start = esc;
            if (start + 1 >= text.length() || text.charAt(start + 1) != '[') {
                // Not CSI, just print it
                insertWithStyle(doc, String.valueOf(text.charAt(start)), state);
                i = start + 1;
                continue;
            }

            int mPos = text.indexOf('m', start + 2);
            if (mPos < 0) {
                // Incomplete escape, print remainder
                insertWithStyle(doc, text.substring(start), state);
                return;
            }

            String seq = text.substring(start + 2, mPos); // like "31" or "1;32"
            applySgr(state, seq);

            i = mPos + 1;
        }
    }

    private static void insertWithStyle(StyledDocument doc, String s, AnsiState state) throws BadLocationException {
        if (s == null || s.isEmpty()) return;

        SimpleAttributeSet as = new SimpleAttributeSet();
        if (state.fg != null) {
            StyleConstants.setForeground(as, state.fg);
        }
        StyleConstants.setBold(as, state.bold);

        doc.insertString(doc.getLength(), s, as);
    }

    private static void applySgr(AnsiState state, String seq) {
        if (seq == null || seq.isEmpty()) {
            // ESC[m means reset
            state.reset();
            return;
        }

        String[] parts = seq.split(";");
        for (String p : parts) {
            int code;
            try {
                code = Integer.parseInt(p.trim());
            } catch (Exception ignored) {
                continue;
            }

            if (code == 0) {
                state.reset();
            } else if (code == 1) {
                state.bold = true;
            } else if (code >= 30 && code <= 37) {
                state.fg = AnsiPalette.basic(code - 30);
            } else if (code >= 90 && code <= 97) {
                state.fg = AnsiPalette.bright(code - 90);
            } else if (code == 39) {
                state.fg = null;
            }
        }
    }

    private static final class AnsiState {
        boolean bold;
        Color fg;

        void reset() {
            bold = false;
            fg = null;
        }
    }

    private static final class AnsiPalette {
        // Terminal-ish colors that look good on dark backgrounds.
        static Color basic(int idx) {
            return switch (idx) {
                case 0 -> new Color(0xCC, 0xCC, 0xCC); // black (use light gray so it's visible)
                case 1 -> new Color(0xFF, 0x55, 0x55); // red
                case 2 -> new Color(0x50, 0xFA, 0x7B); // green
                case 3 -> new Color(0xF1, 0xFA, 0x8C); // yellow
                case 4 -> new Color(0xBD, 0x93, 0xF9); // blue
                case 5 -> new Color(0xFF, 0x79, 0xC6); // magenta
                case 6 -> new Color(0x8B, 0xE9, 0xFD); // cyan
                case 7 -> new Color(0xF8, 0xF8, 0xF2); // white
                default -> null;
            };
        }

        static Color bright(int idx) {
            return switch (idx) {
                case 0 -> new Color(0x62, 0x72, 0xA4); // bright black (gray)
                case 1 -> new Color(0xFF, 0x6E, 0x6E); // bright red
                case 2 -> new Color(0x69, 0xFF, 0x94); // bright green
                case 3 -> new Color(0xFF, 0xFF, 0xA5); // bright yellow
                case 4 -> new Color(0xD6, 0xAC, 0xFF); // bright blue
                case 5 -> new Color(0xFF, 0x92, 0xDF); // bright magenta
                case 6 -> new Color(0xA4, 0xFF, 0xFF); // bright cyan
                case 7 -> new Color(0xFF, 0xFF, 0xFF); // bright white
                default -> null;
            };
        }
    }
}
