
package com.example.downloadorganizer.config2;

import java.nio.file.Path;
import java.nio.file.Paths;

public class AppConfig  {

    private final Path downloadsPath;

    public AppConfig() {
        this.downloadsPath = Paths.get(System.getProperty("user.home"), "Downloads");
    }

    public Path getDownloadsPath() {
        return downloadsPath;
    }
}