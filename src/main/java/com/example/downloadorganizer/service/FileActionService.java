package com.example.downloadorganizer.service;

import java.io.IOException;
import java.nio.file.*;

public class FileActionService {

    public Path moveFileToFolder(Path sourceFile, Path targetFolder) throws IOException {
        if (!Files.exists(sourceFile) || !Files.isRegularFile(sourceFile)) {
            throw new IllegalArgumentException("Source file does not exist or is not a regular file: " + sourceFile);
        }

        if (!Files.exists(targetFolder)) {
            Files.createDirectories(targetFolder);
        }

        Path targetFile = resolveUniqueTarget(targetFolder, sourceFile.getFileName().toString());

        return Files.move(sourceFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
    }

    private Path resolveUniqueTarget(Path targetFolder, String originalFileName) {
        Path candidate = targetFolder.resolve(originalFileName);

        if (!Files.exists(candidate)) {
            return candidate;
        }

        String baseName = extractBaseName(originalFileName);
        String extension = extractExtensionWithDot(originalFileName);

        int counter = 1;
        while (true) {
            String newName = baseName + " (" + counter + ")" + extension;
            candidate = targetFolder.resolve(newName);

            if (!Files.exists(candidate)) {
                return candidate;
            }

            counter++;
        }
    }

    private String extractBaseName(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot <= 0) {
            return fileName;
        }
        return fileName.substring(0, lastDot);
    }

    private String extractExtensionWithDot(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot <= 0 || lastDot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(lastDot);
    }
}