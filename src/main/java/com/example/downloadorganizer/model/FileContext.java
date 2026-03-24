package com.example.downloadorganizer.model;

import java.nio.file.Path;
import java.time.Instant;

public class FileContext {
    private final String fileName;
    private final String extension;
    private final long sizeBytes;
    private final Path fullPath;
    private final String mimeType;
    private final Instant createdAt;

    public FileContext(String fileName,
                       String extension,
                       long sizeBytes,
                       Path fullPath,
                       String mimeType,
                       Instant createdAt) {
        this.fileName = fileName;
        this.extension = extension;
        this.sizeBytes = sizeBytes;
        this.fullPath = fullPath;
        this.mimeType = mimeType;
        this.createdAt = createdAt;
    }

    public String getFileName() {
        return fileName;
    }

    public String getExtension() {
        return extension;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public Path getFullPath() {
        return fullPath;
    }

    public String getMimeType() {
        return mimeType;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public String toString() {
        return "FileContext{" +
                "fileName='" + fileName + '\'' +
                ", extension='" + extension + '\'' +
                ", sizeBytes=" + sizeBytes +
                ", fullPath=" + fullPath +
                ", mimeType='" + mimeType + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}
