package com.example.downloadorganizer.service;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RecentlyRestoredService {

    private final Map<Path, Instant> ignoredPaths = new ConcurrentHashMap<>();
    private final long ignoreMillis;

    public RecentlyRestoredService(long ignoreMillis) {
        this.ignoreMillis = ignoreMillis;
    }

    public void markRecentlyRestored(Path path) {
        ignoredPaths.put(path.toAbsolutePath().normalize(), Instant.now());
    }

    public boolean shouldIgnore(Path path) {
        cleanupExpired();

        Path normalized = path.toAbsolutePath().normalize();
        Instant markedAt = ignoredPaths.get(normalized);

        if (markedAt == null) {
            return false;
        }

        long ageMillis = Instant.now().toEpochMilli() - markedAt.toEpochMilli();
        return ageMillis <= ignoreMillis;
    }

    private void cleanupExpired() {
        Instant now = Instant.now();

        ignoredPaths.entrySet().removeIf(entry ->
                now.toEpochMilli() - entry.getValue().toEpochMilli() > ignoreMillis
        );
    }
}