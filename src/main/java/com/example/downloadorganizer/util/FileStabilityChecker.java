package com.example.downloadorganizer.util;


import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileStabilityChecker {

    public static boolean waitUntilStable(Path filePath, int attempts, long delayMillis)
            throws IOException, InterruptedException {

        if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            return false;
        }

        long previousSize = -1;

        for (int i = 0; i < attempts; i++) {
            long currentSize = Files.size(filePath);

            if (currentSize > 0 && currentSize == previousSize) {
                return true;
            }

            previousSize = currentSize;
            Thread.sleep(delayMillis);
        }

        return false;
    }
}
