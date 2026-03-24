package com.example.downloadorganizer.service;

import com.example.downloadorganizer.model.MoveRecord;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class MoveHistoryService {

    private final List<MoveRecord> history = new ArrayList<>();
    private final RecentlyRestoredService recentlyRestoredService;
    private final MoveHistoryRepository repository;

    public MoveHistoryService(RecentlyRestoredService recentlyRestoredService,
                              MoveHistoryRepository repository) {
        this.recentlyRestoredService = recentlyRestoredService;
        this.repository = repository;
    }

    public void recordMove(Path sourcePath, Path targetPath, String reason) {
        MoveRecord record = new MoveRecord(
                targetPath.getFileName().toString(),
                sourcePath,
                targetPath,
                Instant.now(),
                reason
        );

        history.add(record);

        try {
            repository.save(record);
        } catch (Exception e) {
            System.err.println("Failed to save move history to database:");
            e.printStackTrace();
        }
    }

    public List<MoveRecord> getHistory() {
        return Collections.unmodifiableList(history);
    }

    public List<MoveRecord> getPersistentHistory() {
        try {
            return repository.findAll();
        } catch (Exception e) {
            System.err.println("Failed to load move history from database:");
            e.printStackTrace();
            return List.of();
        }
    }

    public Optional<MoveRecord> getLastMove() {
        if (history.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(history.get(history.size() - 1));
    }

    public boolean undoLastMove() throws IOException {
        Optional<MoveRecord> optionalRecord = getLastMove();
        if (optionalRecord.isEmpty()) {
            return false;
        }

        MoveRecord record = optionalRecord.get();

        if (!Files.exists(record.getTargetPath())) {
            history.remove(history.size() - 1);
            return false;
        }

        Path originalParent = record.getSourcePath().getParent();
        if (originalParent != null && !Files.exists(originalParent)) {
            Files.createDirectories(originalParent);
        }

        Path undoTarget = resolveUniqueTarget(record.getSourcePath());

        recentlyRestoredService.markRecentlyRestored(undoTarget);

        Files.move(record.getTargetPath(), undoTarget, StandardCopyOption.REPLACE_EXISTING);

        history.remove(history.size() - 1);
        return true;
    }

    private Path resolveUniqueTarget(Path desiredPath) {
        if (!Files.exists(desiredPath)) {
            return desiredPath;
        }

        String fileName = desiredPath.getFileName().toString();
        String baseName = extractBaseName(fileName);
        String extension = extractExtensionWithDot(fileName);

        Path parent = desiredPath.getParent();
        int counter = 1;

        while (true) {
            String newName = baseName + " (" + counter + ")" + extension;
            Path candidate = parent.resolve(newName);

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