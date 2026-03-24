package  com.example.downloadorganizer;

import com.example.downloadorganizer.config2.AppConfig;
import com.example.downloadorganizer.model.FileContext;
import com.example.downloadorganizer.service.FileInspector;
import com.example.downloadorganizer.util.FileStabilityChecker;
import com.example.downloadorganizer.watcher2.DownloadWatcher;





public class App {
    AppConfig config;
    public static void main(String[] args) {
        try {
            AppConfig config = new AppConfig();
            FileInspector fileInspector = new FileInspector();

            DownloadWatcher watcher = new DownloadWatcher(
                    config.getDownloadsPath(),
                    fileInspector
            );

            System.out.println("=== Download Organizer Started ===");
            System.out.println("Watching folder: " + config.getDownloadsPath());
            watcher.start();

        } catch (Exception e) {
            System.err.println("Application failed to start: " + e.getMessage());
            e.printStackTrace();
        }
    }
}