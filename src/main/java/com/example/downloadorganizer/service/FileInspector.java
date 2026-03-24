package com.example.downloadorganizer.service;


import  com.example.downloadorganizer.model.FileContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;

public class FileInspector {

    public FileContext inspect(Path filePath) throws IOException {
        String fileName = filePath.getFileName().toString();
        String extension = extractExtension(fileName);
        long sizeBytes = Files.size(filePath);

        String mimeType = Files.probeContentType(filePath);
        if (mimeType == null) {
            mimeType = "unknown";
        }

        BasicFileAttributes attrs = Files.readAttributes(filePath, BasicFileAttributes.class);
        Instant createdAt = attrs.creationTime().toInstant();

        return new FileContext(
                fileName,
                extension,
                sizeBytes,
                filePath.toAbsolutePath(),
                mimeType,
                createdAt
        );
    }

    private String extractExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot == -1 || lastDot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(lastDot + 1).toLowerCase();
    }
}