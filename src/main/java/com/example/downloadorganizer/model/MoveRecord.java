package com.example.downloadorganizer.model;

import java.nio.file.Path;
import java.time.Instant;

public class MoveRecord {
    private final String fileName;
    private final Path sourcePath;
    private final Path targetPath;
    private final Instant movedAt;
    private final String reason;

    public MoveRecord(String fileName, Path sourcePath, Path targetPath, Instant movedAt, String reason) {
        this.fileName = fileName;
        this.sourcePath = sourcePath;
        this.targetPath = targetPath;
        this.movedAt = movedAt;
        this.reason = reason;
    }

    public String getFileName() {
        return fileName;
    }

    public Path getSourcePath() {
        return sourcePath;
    }

    public Path getTargetPath() {
        return targetPath;
    }

    public Instant getMovedAt() {
        return movedAt;
    }

    public String getReason() {
        return reason;
    }

    @Override
    public String toString() {
        return "MoveRecord{" +
                "fileName='" + fileName + '\'' +
                ", sourcePath=" + sourcePath +
                ", targetPath=" + targetPath +
                ", movedAt=" + movedAt +
                ", reason='" + reason + '\'' +
                '}';
    }
}