package com.example.downloadorganizer.service;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseService {

    private final String dbUrl;

    public DatabaseService() {
        Path dbPath = Paths.get(System.getProperty("user.home"), "download_organizer.db");
        this.dbUrl = "jdbc:sqlite:" + dbPath.toAbsolutePath();
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(dbUrl);
    }

    public void initialize() throws SQLException {
        String sql = """
                CREATE TABLE IF NOT EXISTS move_history (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    file_name TEXT NOT NULL,
                    source_path TEXT NOT NULL,
                    target_path TEXT NOT NULL,
                    moved_at TEXT NOT NULL,
                    reason TEXT NOT NULL
                );
                """;

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }
}