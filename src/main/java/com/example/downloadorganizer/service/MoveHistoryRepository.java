package com.example.downloadorganizer.service;

import com.example.downloadorganizer.model.MoveRecord;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class MoveHistoryRepository {

    private final DatabaseService databaseService;

    public MoveHistoryRepository(DatabaseService databaseService) {
        this.databaseService = databaseService;
    }

    public void save(MoveRecord record) throws SQLException {
        String sql = """
                INSERT INTO move_history (file_name, source_path, target_path, moved_at, reason)
                VALUES (?, ?, ?, ?, ?)
                """;

        try (Connection conn = databaseService.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, record.getFileName());
            ps.setString(2, record.getSourcePath().toString());
            ps.setString(3, record.getTargetPath().toString());
            ps.setString(4, record.getMovedAt().toString());
            ps.setString(5, record.getReason());

            ps.executeUpdate();
        }
    }

    public List<MoveRecord> findAll() throws SQLException {
        String sql = """
                SELECT file_name, source_path, target_path, moved_at, reason
                FROM move_history
                ORDER BY id DESC
                """;

        List<MoveRecord> results = new ArrayList<>();

        try (Connection conn = databaseService.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                MoveRecord record = new MoveRecord(
                        rs.getString("file_name"),
                        java.nio.file.Paths.get(rs.getString("source_path")),
                        java.nio.file.Paths.get(rs.getString("target_path")),
                        Instant.parse(rs.getString("moved_at")),
                        rs.getString("reason")
                );

                results.add(record);
            }
        }

        return results;
    }
}