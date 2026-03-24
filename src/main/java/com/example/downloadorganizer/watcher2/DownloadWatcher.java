// package com.example.downloadorganizer.watcher2;

// import com.example.downloadorganizer.model.FileContext;
// import com.example.downloadorganizer.model.GeminiDecision;
// import com.example.downloadorganizer.rules.*;
// import com.example.downloadorganizer.service.*;
// import com.example.downloadorganizer.util.FileStabilityChecker;

// import java.io.IOException;
// import java.nio.file.*;
// import java.util.Scanner;
// import java.nio.file.FileSystemException;

// import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
// import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

// public class DownloadWatcher {

//     private final Path downloadsPath;
//     private final FileInspector fileInspector;
//     private final RuleEngine ruleEngine;
//     private final FileActionService fileActionService;
//     private final MoveHistoryService moveHistoryService;
//     private final RecentlyRestoredService recentlyRestoredService;
//     private final PendingMoveService pendingMoveService;
//     private final GeminiClassifier geminiClassifier;
//     private final PdfContentExtractor pdfContentExtractor;

//         public DownloadWatcher(Path downloadsPath, FileInspector fileInspector) {
//         this.downloadsPath = downloadsPath;
//         this.fileInspector = fileInspector;
//         this.ruleEngine = new RuleEngine();
//         this.fileActionService = new FileActionService();
//         this.recentlyRestoredService = new RecentlyRestoredService(10_000);

//         try {
//             DatabaseService databaseService = new DatabaseService();
//             databaseService.initialize();
//             MoveHistoryRepository repository = new MoveHistoryRepository(databaseService);
//             this.moveHistoryService = new MoveHistoryService(recentlyRestoredService, repository);
//             this.pendingMoveService = new PendingMoveService(fileActionService, moveHistoryService);
//             this.geminiClassifier = new GeminiClassifier();
//             this.pdfContentExtractor = new PdfContentExtractor();
//         } catch (Exception e) {
//             throw new RuntimeException("Failed to initialize database", e);
//         }
//     }

//     public void start() throws IOException, InterruptedException {
//     if (!Files.exists(downloadsPath) || !Files.isDirectory(downloadsPath)) {
//         throw new IllegalArgumentException("Downloads folder does not exist: " + downloadsPath);
//     }

//     startConsoleListener();
//     startPendingProcessor();

//     try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
//         downloadsPath.register(watchService, ENTRY_CREATE);

//         while (true) {
//             WatchKey key = watchService.take();

//             for (WatchEvent<?> event : key.pollEvents()) {
//                 WatchEvent.Kind<?> kind = event.kind();

//                 if (kind == OVERFLOW) {
//                     System.out.println("WatchService overflow occurred.");
//                     continue;
//                 }

//                 WatchEvent<Path> ev = cast(event);
//                 Path fileName = ev.context();
//                 Path fullPath = downloadsPath.resolve(fileName);

//                 if (shouldIgnore(fileName.toString())) {
//                     System.out.println("Ignoring temporary/system file: " + fileName);
//                     continue;
//                 }

//                 if (recentlyRestoredService.shouldIgnore(fullPath)) {
//                     System.out.println("Ignoring recently restored file: " + fileName);
//                     continue;
//                 }

//                 System.out.println("Detected new file: " + fileName);

//                 try {
//                     boolean stable = FileStabilityChecker.waitUntilStable(fullPath, 10, 1000);

//                     if (!stable) {
//                         System.out.println("File is not stable yet, skipping: " + fileName);
//                         continue;
//                     }

//                     // בדיקה שנייה — חשובה נגד race condition
//                     if (recentlyRestoredService.shouldIgnore(fullPath)) {
//                         System.out.println("Ignoring recently restored file after stabilization: " + fileName);
//                         continue;
//                     }

//                     if (!Files.exists(fullPath) || !Files.isRegularFile(fullPath)) {
//                         System.out.println("Not a regular file, skipping: " + fileName);
//                         continue;
//                     }

//                     FileContext context = fileInspector.inspect(fullPath);

//                     System.out.println("File inspected successfully:");
//                     System.out.println(context);

//                     RuleDecision decision = ruleEngine.decide(context);
//                     try {
//                     GeminiDecision geminiDecision = geminiClassifier.classify(
//                             context,
//                             decision.getCategory().name()
//                     );

//                     System.out.println("Gemini decision:");
//                     System.out.println(geminiDecision);

//                 } catch (Exception e) {
//                     System.err.println("Gemini classification failed:");
//                     e.printStackTrace();
//                 }
//                     System.out.println("Rule decision:");
//                     FileCategory finalCategory = decision.getCategory();
//                     String reason = decision.getReason();
//                     Path targetFolder = decision.getSuggestedTargetFolder();

//                     if (decision.getCategory() == FileCategory.MANUAL_REVIEW && geminiClassifier.isConfigured()) {
//                         try {
//                             GeminiDecision geminiDecision = geminiClassifier.classify(
//                                     context,
//                                     decision.getCategory().name()
//                             );

//                             System.out.println("Gemini decision:");
//                             System.out.println(geminiDecision);

//                             if (geminiDecision.getConfidence() >= 0.7 &&
//                                     !"MANUAL_REVIEW".equalsIgnoreCase(geminiDecision.getCategory())) {

//                                 finalCategory = FileCategory.valueOf(geminiDecision.getCategory());

//                                 targetFolder = Path.of(geminiDecision.getTargetPath());
//                                 reason = "Gemini: " + geminiDecision.getReason();

//                             } else {
//                                 System.out.println("Gemini not confident, keeping manual review.");
//                             }

//                         } catch (Exception e) {
//                             System.err.println("Gemini failed, fallback to manual review.");
//                         }
//                     }

//                     if (finalCategory == FileCategory.MANUAL_REVIEW) {
//                         System.out.println("Skipping move (manual review).");
//                         continue;
//                     }
//                     if ("pdf".equalsIgnoreCase(context.getExtension()) && geminiClassifier.isConfigured()) {
//                     try {
//                         String extractedText = pdfContentExtractor.extractText(context.getFullPath(), 5, 12000);

//                         if (extractedText.isBlank()) {
//                             System.out.println("PDF text extraction returned empty text. Keeping manual review for now.");
//                             continue;
//                         }

//                         GeminiDecision geminiDecision = geminiClassifier.classifyPdf(context, extractedText);

//                         System.out.println("Gemini PDF decision:");
//                         System.out.println(geminiDecision);

//                         if (geminiDecision.getConfidence() < 0.75 ||
//                                 "MANUAL_REVIEW".equalsIgnoreCase(geminiDecision.getCategory())) {
//                             System.out.println("Gemini not confident enough for PDF. Skipping move.");
//                             continue;
//                         }

//                         Path aiTarget = resolveAiTargetPath(geminiDecision);

//                         Path movedTo = fileActionService.moveFileToFolder(
//                                 context.getFullPath(),
//                                 aiTarget
//                         );

//                         moveHistoryService.recordMove(
//                                 context.getFullPath(),
//                                 movedTo,
//                                 "Gemini PDF classification: " + geminiDecision.getReason()
//                         );

//                         System.out.println("PDF moved by Gemini decision to: " + movedTo);
//                         continue;

//                     } catch (Exception e) {
//                         System.err.println("PDF AI classification failed, leaving file for manual review.");
//                         e.printStackTrace();
//                         continue;
//                     }
//                 }

//                 Path originalPath = context.getFullPath();

//                 try {
//                     Path movedTo = fileActionService.moveFileToFolder(
//                             context.getFullPath(),
//                             targetFolder
//                     );

//                     moveHistoryService.recordMove(
//                             context.getFullPath(),
//                             movedTo,
//                             reason
//                     );

//                     System.out.println("File moved successfully to: " + movedTo);
//                     System.out.println("You can type 'undo' in the console to revert the last move.");

//                 } catch (FileSystemException e) {
//                     System.out.println("File is currently in use. Adding to pending queue...");
//                     pendingMoveService.addPendingMove(
//                             originalPath,
//                             decision.getSuggestedTargetFolder(),
//                             decision.getReason()
//                     );
//                 }


//                 } catch (Exception e) {
//                     System.err.println("Failed processing file: " + fileName);
//                     e.printStackTrace();
//                 }
//             }

//             boolean valid = key.reset();
//             if (!valid) {
//                 System.err.println("WatchKey is no longer valid. Stopping watcher.");
//                 break;
//             }
//         }
//     }
// }

//     private void startConsoleListener() {
//         Thread consoleThread = new Thread(() -> {
//             Scanner scanner = new Scanner(System.in);

//             while (true) {
//                 try {
//                     String input = scanner.nextLine().trim();

//                     if (input.equalsIgnoreCase("undo")) {
//                         boolean undone = moveHistoryService.undoLastMove();

//                         if (undone) {
//                             System.out.println("Last move was undone successfully.");
//                         } else {
//                             System.out.println("Nothing to undo, or undo failed.");
//                         }
//                     } else if (input.equalsIgnoreCase("history")) {
//                         System.out.println("Persistent move history:");
//                         moveHistoryService.getPersistentHistory().forEach(System.out::println);
//                     }
//                     else if (input.equalsIgnoreCase("pending")) {
//                          pendingMoveService.printPendingMoves();
//                         }}
                    
//                  catch (Exception e) {
//                     System.err.println("Console listener error:");
//                     e.printStackTrace();
//                 }
//             }
//         });

//         consoleThread.setDaemon(true);
//         consoleThread.start();
//     }
    
//     private void startPendingProcessor() {
//         Thread pendingThread = new Thread(() -> {
//             while (true) {
//                 try {
//                     Thread.sleep(5000);
//                     pendingMoveService.processPendingMoves();
//                 } catch (Exception e) {
//                     System.err.println("Pending processor error:");
//                     e.printStackTrace();
//                 }
//             }
//         });

//         pendingThread.setDaemon(true);
//         pendingThread.start();
//     }

//     private boolean shouldIgnore(String fileName) {
//         String lower = fileName.toLowerCase();

//         return lower.endsWith(".crdownload")
//                 || lower.endsWith(".part")
//                 || lower.endsWith(".tmp")
//                 || lower.endsWith(".download")
//                 || lower.equals("desktop.ini");
//     }

//     @SuppressWarnings("unchecked")
//     private WatchEvent<Path> cast(WatchEvent<?> event) {
//         return (WatchEvent<Path>) event;

//     }

//     private Path resolveAiTargetPath(GeminiDecision geminiDecision) {
//     String userHome = System.getProperty("user.home");

//     String category = geminiDecision.getCategory();
//     if (category == null) {
//         return Path.of(userHome, "Downloads", "ManualReview");
//     }

//     return switch (category.toUpperCase()) {
//         case "DOCUMENTS" -> Path.of(userHome, "Documents");
//         case "PERSONAL_FORM" -> Path.of(userHome, "Documents", "Personal", "Resumes");
//         case "PICTURES" -> Path.of(userHome, "Pictures");
//         case "VIDEOS" -> Path.of(userHome, "Videos");
//         case "MUSIC" -> Path.of(userHome, "Music");
//         case "SPREADSHEETS" -> Path.of(userHome, "Documents", "Spreadsheets");
//         case "PRESENTATIONS" -> Path.of(userHome, "Documents", "Presentations");
//         default -> Path.of(userHome, "Downloads", "ManualReview");
//     };
// }
// }
package com.example.downloadorganizer.watcher2;

import com.example.downloadorganizer.model.FileContext;
import com.example.downloadorganizer.model.GeminiDecision;
import com.example.downloadorganizer.rules.FileCategory;
import com.example.downloadorganizer.rules.RuleDecision;
import com.example.downloadorganizer.rules.RuleEngine;
import com.example.downloadorganizer.service.*;
import com.example.downloadorganizer.util.FileStabilityChecker;

import java.io.IOException;
import java.nio.file.FileSystemException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

public class DownloadWatcher {

    private final Path downloadsPath;
    private final FileInspector fileInspector;
    private final RuleEngine ruleEngine;
    private final FileActionService fileActionService;
    private final MoveHistoryService moveHistoryService;
    private final RecentlyRestoredService recentlyRestoredService;
    private final PendingMoveService pendingMoveService;
    private final GeminiClassifier geminiClassifier; // may be null if not configured
    private final PdfContentExtractor pdfContentExtractor;

    public DownloadWatcher(Path downloadsPath, FileInspector fileInspector) {
        this.downloadsPath = downloadsPath;
        this.fileInspector = fileInspector;
        this.ruleEngine = new RuleEngine();
        this.fileActionService = new FileActionService();
        this.recentlyRestoredService = new RecentlyRestoredService(10_000);

        try {
            DatabaseService databaseService = new DatabaseService();
            databaseService.initialize();

            MoveHistoryRepository repository = new MoveHistoryRepository(databaseService);
            this.moveHistoryService = new MoveHistoryService(recentlyRestoredService, repository);
            this.pendingMoveService = new PendingMoveService(fileActionService, moveHistoryService);
            this.pdfContentExtractor = new PdfContentExtractor();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize database", e);
        }

        GeminiClassifier tempGemini;
        try {
            tempGemini = new GeminiClassifier();
            System.out.println("Gemini classifier initialized successfully.");
        } catch (Exception e) {
            System.out.println("Gemini classifier is unavailable. Continuing without AI classification.");
            tempGemini = null;
        }
        this.geminiClassifier = tempGemini;
    }

    public void start() throws IOException, InterruptedException {
        if (!Files.exists(downloadsPath) || !Files.isDirectory(downloadsPath)) {
            throw new IllegalArgumentException("Downloads folder does not exist: " + downloadsPath);
        }

        startConsoleListener();
        startPendingProcessor();

        try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
            downloadsPath.register(watchService, ENTRY_CREATE);

            while (true) {
                WatchKey key = watchService.take();

                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();

                    if (kind == OVERFLOW) {
                        System.out.println("WatchService overflow occurred.");
                        continue;
                    }

                    WatchEvent<Path> ev = cast(event);
                    Path fileName = ev.context();
                    Path fullPath = downloadsPath.resolve(fileName);

                    if (shouldIgnore(fileName.toString())) {
                        System.out.println("Ignoring temporary/system file: " + fileName);
                        continue;
                    }

                    if (recentlyRestoredService.shouldIgnore(fullPath)) {
                        System.out.println("Ignoring recently restored file: " + fileName);
                        continue;
                    }

                    System.out.println("Detected new file: " + fileName);

                    try {
                        boolean stable = FileStabilityChecker.waitUntilStable(fullPath, 10, 1000);
                        if (!stable) {
                            System.out.println("File is not stable yet, skipping: " + fileName);
                            continue;
                        }

                        if (recentlyRestoredService.shouldIgnore(fullPath)) {
                            System.out.println("Ignoring recently restored file after stabilization: " + fileName);
                            continue;
                        }

                        if (!Files.exists(fullPath) || !Files.isRegularFile(fullPath)) {
                            System.out.println("Not a regular file, skipping: " + fileName);
                            continue;
                        }

                        FileContext context = fileInspector.inspect(fullPath);

                        System.out.println("File inspected successfully:");
                        System.out.println(context);

                        RuleDecision decision = ruleEngine.decide(context);

                        System.out.println("Rule decision:");
                        System.out.println(decision);

                        FileCategory finalCategory = decision.getCategory();
                        String finalReason = decision.getReason();
                        Path finalTargetFolder = decision.getSuggestedTargetFolder();

                        GeminiDecision generalGeminiDecision = null;

                        if (isGeminiAvailable()) {
                            try {
                                generalGeminiDecision = geminiClassifier.classify(
                                        context,
                                        decision.getCategory().name()
                                );

                                System.out.println("Gemini decision:");
                                System.out.println(generalGeminiDecision);
                            } catch (Exception e) {
                                System.err.println("Gemini classification failed:");
                                e.printStackTrace();
                            }
                        }

                        if ("pdf".equalsIgnoreCase(context.getExtension()) && isGeminiAvailable()) {
                            boolean handledByPdfAi = tryHandlePdfWithAi(context);
                            if (handledByPdfAi) {
                                continue;
                            }
                        }

                        if (finalCategory == FileCategory.MANUAL_REVIEW && generalGeminiDecision != null) {
                            try {
                                if (generalGeminiDecision.getConfidence() >= 0.70
                                        && !isManualReviewDecision(generalGeminiDecision)) {

                                    finalTargetFolder = resolveAiTargetPath(generalGeminiDecision.getTargetPath());
                                    finalReason = "Gemini: " + generalGeminiDecision.getReason();

                                    System.out.println("Using Gemini target path: " + finalTargetFolder);
                                } else {
                                    System.out.println("Gemini not confident enough, keeping manual review.");
                                }
                            } catch (Exception e) {
                                System.err.println("Gemini target path resolution failed. Keeping manual review.");
                                e.printStackTrace();
                            }
                        }

                        if (finalCategory == FileCategory.MANUAL_REVIEW && finalTargetFolder == null) {
                            System.out.println("Skipping move (manual review).");
                            continue;
                        }

                        if (finalTargetFolder == null) {
                            System.out.println("No target folder resolved. Skipping move.");
                            continue;
                        }

                        Path originalPath = context.getFullPath();

                        try {
                            Path movedTo = fileActionService.moveFileToFolder(
                                    context.getFullPath(),
                                    finalTargetFolder
                            );

                            moveHistoryService.recordMove(
                                    context.getFullPath(),
                                    movedTo,
                                    finalReason
                            );

                            System.out.println("File moved successfully to: " + movedTo);
                            System.out.println("You can type 'undo' in the console to revert the last move.");

                        } catch (FileSystemException e) {
                            System.out.println("File is currently in use. Adding to pending queue...");
                            pendingMoveService.addPendingMove(
                                    originalPath,
                                    finalTargetFolder,
                                    finalReason
                            );
                        }

                    } catch (Exception e) {
                        System.err.println("Failed processing file: " + fileName);
                        e.printStackTrace();
                    }
                }

                boolean valid = key.reset();
                if (!valid) {
                    System.err.println("WatchKey is no longer valid. Stopping watcher.");
                    break;
                }
            }
        }
    }

    private boolean tryHandlePdfWithAi(FileContext context) {
        try {
            String extractedText = pdfContentExtractor.extractText(context.getFullPath(), 5, 12000);

            // if (extractedText == null || extractedText.isBlank()) {
            //     System.out.println("PDF text extraction returned empty text. Falling back to normal flow.");
            //     return false;
            // }

            GeminiDecision geminiDecision = geminiClassifier.classifyPdf(context, extractedText);

            System.out.println("Gemini PDF decision:");
            System.out.println(geminiDecision);

            if (geminiDecision == null) {
                System.out.println("Gemini PDF decision is null. Falling back to normal flow.");
                return false;
            }

            if (geminiDecision.getConfidence() < 0.75 || isManualReviewDecision(geminiDecision)) {
                System.out.println("Gemini PDF decision not confident enough. Falling back to normal flow.");
                return false;
            }

            Path aiTarget = resolveAiTargetPath(geminiDecision.getTargetPath());

            Path movedTo = fileActionService.moveFileToFolder(
                    context.getFullPath(),
                    aiTarget
            );

            moveHistoryService.recordMove(
                    context.getFullPath(),
                    movedTo,
                    "Gemini PDF classification: " + geminiDecision.getReason()
            );

            System.out.println("PDF moved by Gemini decision to: " + movedTo);
            return true;

        } catch (Exception e) {
            System.err.println("PDF AI classification failed. Falling back to normal flow.");
            e.printStackTrace();
            return false;
        }
    }

    private void startConsoleListener() {
        Thread consoleThread = new Thread(() -> {
            Scanner scanner = new Scanner(System.in);

            while (true) {
                try {
                    String input = scanner.nextLine().trim();

                    if (input.equalsIgnoreCase("undo")) {
                        boolean undone = moveHistoryService.undoLastMove();

                        if (undone) {
                            System.out.println("Last move was undone successfully.");
                        } else {
                            System.out.println("Nothing to undo, or undo failed.");
                        }

                    } else if (input.equalsIgnoreCase("history")) {
                        System.out.println("Persistent move history:");
                        moveHistoryService.getPersistentHistory().forEach(System.out::println);

                    } else if (input.equalsIgnoreCase("pending")) {
                        pendingMoveService.printPendingMoves();
                    }

                } catch (Exception e) {
                    System.err.println("Console listener error:");
                    e.printStackTrace();
                }
            }
        });

        consoleThread.setDaemon(true);
        consoleThread.start();
    }

    private void startPendingProcessor() {
        Thread pendingThread = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(5000);
                    pendingMoveService.processPendingMoves();
                } catch (Exception e) {
                    System.err.println("Pending processor error:");
                    e.printStackTrace();
                }
            }
        });

        pendingThread.setDaemon(true);
        pendingThread.start();
    }

    private boolean shouldIgnore(String fileName) {
        String lower = fileName.toLowerCase();

        return lower.endsWith(".crdownload")
                || lower.endsWith(".part")
                || lower.endsWith(".tmp")
                || lower.endsWith(".download")
                || lower.equals("desktop.ini");
    }

    private boolean isGeminiAvailable() {
        return geminiClassifier != null && geminiClassifier.isConfigured();
    }

    private boolean isManualReviewDecision(GeminiDecision decision) {
        if (decision == null || decision.getCategory() == null) {
            return true;
        }
        return "MANUAL_REVIEW".equalsIgnoreCase(decision.getCategory());
    }

    @SuppressWarnings("unchecked")
    private WatchEvent<Path> cast(WatchEvent<?> event) {
        return (WatchEvent<Path>) event;
    }

    private Path resolveAiTargetPath(String rawTargetPath) {
        if (rawTargetPath == null || rawTargetPath.isBlank()) {
            throw new IllegalArgumentException("AI target path is empty");
        }

        String userHome = System.getProperty("user.home");
        String userName = System.getProperty("user.name");

        String normalized = rawTargetPath.trim();

        normalized = normalized.replace("\"", "");
        normalized = normalized.replace("<username>", userName);
        normalized = normalized.replace("%USERNAME%", userName);
        normalized = normalized.replace("{username}", userName);

        String userHomeForward = userHome.replace("\\", "/");

        normalized = normalized.replace("C:\\Users\\<username>", userHome);
        normalized = normalized.replace("C:/Users/<username>", userHomeForward);

        normalized = normalized.replace("C:\\Users\\" + userName, userHome);
        normalized = normalized.replace("C:/Users/" + userName, userHomeForward);

        boolean looksAbsoluteWindows = normalized.matches("^[A-Za-z]:[\\\\/].*");
        Path candidate;

        if (looksAbsoluteWindows) {
            candidate = buildSanitizedWindowsPath(normalized);
        } else {
            Path relative = buildSanitizedRelativePath(normalized);
            candidate = Path.of(userHome).resolve(relative).normalize();
        }

        Path allowedRoot = Path.of(userHome).normalize();

        if (!candidate.startsWith(allowedRoot)) {
            throw new IllegalArgumentException("AI target path points outside the user home directory: " + candidate);
        }

        return candidate;
    }

    private Path buildSanitizedWindowsPath(String rawPath) {
        String normalizedSlashes = rawPath.replace("/", "\\");

        String drive = normalizedSlashes.substring(0, 2); // e.g. C:
        String rest = normalizedSlashes.substring(2);

        String[] parts = rest.split("\\\\+");
        List<String> cleanedParts = new ArrayList<>();

        for (String part : parts) {
            if (part == null || part.isBlank()) {
                continue;
            }

            String cleaned = sanitizePathSegment(part);
            if (!cleaned.isBlank()) {
                cleanedParts.add(cleaned);
            }
        }

        Path result = Paths.get(drive + "\\");
        for (String part : cleanedParts) {
            result = result.resolve(part);
        }

        return result.normalize();
    }

    private Path buildSanitizedRelativePath(String rawPath) {
        String normalizedSlashes = rawPath.replace("\\", "/");

        while (normalizedSlashes.startsWith("/")) {
            normalizedSlashes = normalizedSlashes.substring(1);
        }

        String[] parts = normalizedSlashes.split("/+");
        List<String> cleanedParts = new ArrayList<>();

        for (String part : parts) {
            if (part == null || part.isBlank()) {
                continue;
            }

            String cleaned = sanitizePathSegment(part);
            if (!cleaned.isBlank()) {
                cleanedParts.add(cleaned);
            }
        }

        if (cleanedParts.isEmpty()) {
            throw new IllegalArgumentException("AI target path produced no valid path segments");
        }

        Path result = Path.of(cleanedParts.get(0));
        for (int i = 1; i < cleanedParts.size(); i++) {
            result = result.resolve(cleanedParts.get(i));
        }

        return result.normalize();
    }

    private String sanitizePathSegment(String segment) {
        String cleaned = segment.trim();

        cleaned = cleaned.replaceAll("[<>:\"|?*]", "");
        cleaned = cleaned.replaceAll("[\\r\\n\\t]", " ");
        cleaned = cleaned.replaceAll("\\s{2,}", " ").trim();

        if (cleaned.equals(".") || cleaned.equals("..")) {
            return "";
        }

        return cleaned;
    }
}