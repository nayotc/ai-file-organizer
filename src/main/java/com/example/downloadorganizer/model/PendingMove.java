package com.example.downloadorganizer.model;

import java.nio.file.Path;

public class PendingMove {
    private final Path sourcePath;
    private final Path targetFolder;
    private final String reason;
    private int attempts;

    public PendingMove(Path sourcePath, Path targetFolder, String reason) {
        this.sourcePath = sourcePath;
        this.targetFolder = targetFolder;
        this.reason = reason;
        this.attempts = 0;
    }

    public Path getSourcePath() {
        return sourcePath;
    }

    public Path getTargetFolder() {
        return targetFolder;
    }

    public String getReason() {
        return reason;
    }

    public int getAttempts() {
        return attempts;
    }

    public void incrementAttempts() {
        this.attempts++;
    }

    @Override
    public String toString() {
        return "PendingMove{" +
                "sourcePath=" + sourcePath +
                ", targetFolder=" + targetFolder +
                ", reason='" + reason + '\'' +
                ", attempts=" + attempts +
                '}';
    }
}