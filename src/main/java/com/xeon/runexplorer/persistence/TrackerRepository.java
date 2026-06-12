package com.xeon.runexplorer.persistence;

import org.intellij.lang.annotations.Language;

import java.sql.*;

public final class TrackerRepository {

    private final String jdbcUrl;

    @Language("SQLite")
    private static final String SQL_CREATE_TRACKED_PLAYERS = """
        CREATE TABLE IF NOT EXISTS tracked_players (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            game TEXT NOT NULL,
            mode TEXT NOT NULL,
            player TEXT NOT NULL,
            added_at INTEGER NOT NULL
        )
        """;

    @Language("SQLite")
    private static final String SQL_CREATE_SNAPSHOTS = """
        CREATE TABLE IF NOT EXISTS snapshots (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            game TEXT NOT NULL,
            mode TEXT NOT NULL,
            player TEXT NOT NULL,
            taken_at INTEGER NOT NULL,
            overall_xp INTEGER NOT NULL,
            total_level INTEGER NOT NULL
        )
        """;

    @Language("SQLite")
    private static final String SQL_CREATE_IDX = """
        CREATE INDEX IF NOT EXISTS idx_snapshots_player
        ON snapshots(game,mode,player,taken_at)
        """;

    public TrackerRepository(String filePath) {
        this.jdbcUrl = "jdbc:sqlite:" + filePath;
    }

    public void init() throws SQLException {
        try (Connection c = DriverManager.getConnection(jdbcUrl)) {
            try (Statement st = c.createStatement()) {
                st.executeUpdate(SQL_CREATE_TRACKED_PLAYERS);
                st.executeUpdate(SQL_CREATE_SNAPSHOTS);
                st.executeUpdate(SQL_CREATE_IDX);
            }
        }
    }

    public void addTracked(String game, String mode, String player, long now) throws SQLException {
        try (Connection c = DriverManager.getConnection(jdbcUrl)) {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO tracked_players(game,mode,player,added_at) VALUES(?,?,?,?)"
            )) {
                ps.setString(1, game);
                ps.setString(2, mode);
                ps.setString(3, player);
                ps.setLong(4, now);
                ps.executeUpdate();
            }
        }
    }

    public void removeTracked(String game, String mode, String player) throws SQLException {
        try (Connection c = DriverManager.getConnection(jdbcUrl)) {
            try (PreparedStatement ps = c.prepareStatement(
                    "DELETE FROM tracked_players WHERE game=? AND mode=? AND player=?"
            )) {
                ps.setString(1, game);
                ps.setString(2, mode);
                ps.setString(3, player);
                ps.executeUpdate();
            }
        }
    }

    public boolean isTracked(String game, String mode, String player) throws SQLException {
        try (Connection c = DriverManager.getConnection(jdbcUrl)) {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT 1 FROM tracked_players WHERE game=? AND mode=? AND player=? LIMIT 1"
            )) {
                ps.setString(1, game);
                ps.setString(2, mode);
                ps.setString(3, player);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        }
    }

    public void insertSnapshot(String game, String mode, String player, long now, long overallXp, int totalLevel) throws SQLException {
        try (Connection c = DriverManager.getConnection(jdbcUrl)) {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO snapshots(game,mode,player,taken_at,overall_xp,total_level) VALUES(?,?,?,?,?,?)"
            )) {
                ps.setString(1, game);
                ps.setString(2, mode);
                ps.setString(3, player);
                ps.setLong(4, now);
                ps.setLong(5, overallXp);
                ps.setInt(6, totalLevel);
                ps.executeUpdate();
            }
        }
    }

    public Snapshot latestSnapshot(String game, String mode, String player) throws SQLException {
        try (Connection c = DriverManager.getConnection(jdbcUrl)) {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT taken_at, overall_xp, total_level FROM snapshots WHERE game=? AND mode=? AND player=? ORDER BY taken_at DESC LIMIT 1"
            )) {
                ps.setString(1, game);
                ps.setString(2, mode);
                ps.setString(3, player);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return null;
                    return new Snapshot(rs.getLong(1), rs.getLong(2), rs.getInt(3));
                }
            }
        }
    }

    public record Snapshot(long takenAt, long overallXp, int totalLevel) {
    }
}
