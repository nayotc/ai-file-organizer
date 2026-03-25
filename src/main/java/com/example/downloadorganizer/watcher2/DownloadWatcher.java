package com.example.downloadorganizer.watcher2;

import com.example.downloadorganizer.model.FileContext;
import com.example.downloadorganizer.model.GeminiDecision;
import com.example.downloadorganizer.rules.FileCategory;
import com.example.downloadorganizer.rules.RuleDecision;
import com.example.downloadorganizer.rules.RuleEngine;
import com.example.downloadorganizer.service.DatabaseService;
import com.example.downloadorganizer.service.FileActionService;
import com.example.downloadorganizer.service.FileInspector;
import com.example.downloadorganizer.service.GeminiClassifier;
import com.example.downloadorganizer.service.MoveHistoryRepository;
import com.example.downloadorganizer.service.MoveHistoryService;
import com.example.downloadorganizer.service.PendingMoveService;
import com.example.downloadorganizer.service.PdfContentExtractor;
import com.example.downloadorganizer.service.RecentlyRestoredService;
import com.example.downloadorganizer.util.FileStabilityChecker;

import java.io.IOException;
import java.nio.file.FileSystemException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

                        RuleDecision ruleDecision = ruleEngine.decide(context);

                        System.out.println("Rule decision:");
                        System.out.println(ruleDecision);

                        String finalReason = ruleDecision.getReason();
                        Path finalTargetFolder = ruleDecision.getSuggestedTargetFolder();

                        GeminiDecision generalGeminiDecision = null;

                        if (isGeminiAvailable()) {
                            try {
                                generalGeminiDecision = geminiClassifier.classify(
                                        context,
                                        ruleDecision.getCategory().name()
                                );

                                System.out.println("Gemini decision:");
                                System.out.println(generalGeminiDecision);
                            } catch (Exception e) {
                                System.err.println("Gemini classification failed:");
                                e.printStackTrace();
                            }
                        }

                        if ("pdf".equalsIgnoreCase(context.getExtension()) && isGeminiAvailable()) {
                            boolean handledByPdfAi = tryHandlePdfWithAi(context, generalGeminiDecision);
                            if (handledByPdfAi) {
                                continue;
                            }
                        }

                        if (canUseGeneralGeminiDecision(generalGeminiDecision)) {
                            try {
                                finalTargetFolder = resolveAiTargetPath(generalGeminiDecision.getTargetPath());
                                finalReason = "Gemini: " + generalGeminiDecision.getReason();
                                System.out.println("Using general Gemini target path: " + finalTargetFolder);
                            } catch (Exception e) {
                                System.err.println("General Gemini target path resolution failed. Falling back to rule decision.");
                                e.printStackTrace();
                            }
                        }

                        if (finalTargetFolder == null) {
                            if (ruleDecision.getCategory() == FileCategory.MANUAL_REVIEW) {
                                System.out.println("Skipping move (manual review).");
                            } else {
                                System.out.println("No target folder resolved. Skipping move.");
                            }
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

    private boolean tryHandlePdfWithAi(FileContext context, GeminiDecision generalGeminiDecision) {
        try {
            String extractedText = pdfContentExtractor.extractText(context.getFullPath(), 5, 12000);

            if (extractedText == null || extractedText.isBlank()) {
                System.out.println("PDF text extraction returned empty text.");

                if (canUseGeneralGeminiDecision(generalGeminiDecision)) {
                    Path generalTarget = resolveAiTargetPath(generalGeminiDecision.getTargetPath());

                    Path movedTo = fileActionService.moveFileToFolder(
                            context.getFullPath(),
                            generalTarget
                    );

                    moveHistoryService.recordMove(
                            context.getFullPath(),
                            movedTo,
                            "Fallback to general Gemini decision: " + generalGeminiDecision.getReason()
                    );

                    System.out.println("PDF moved using general Gemini fallback to: " + movedTo);
                    return true;
                }

                System.out.println("No strong general Gemini fallback available. Falling back to normal flow.");
                return false;
            }

            GeminiDecision pdfDecision = geminiClassifier.classifyPdf(context, extractedText);

            System.out.println("Gemini PDF decision:");
            System.out.println(pdfDecision);

            if (canUseGeminiDecision(pdfDecision)) {
                Path aiTarget = resolveAiTargetPath(pdfDecision.getTargetPath());

                Path movedTo = fileActionService.moveFileToFolder(
                        context.getFullPath(),
                        aiTarget
                );

                moveHistoryService.recordMove(
                        context.getFullPath(),
                        movedTo,
                        "Gemini PDF classification: " + pdfDecision.getReason()
                );

                System.out.println("PDF moved by Gemini PDF decision to: " + movedTo);
                return true;
            }

            System.out.println("Gemini PDF decision not confident enough.");

            if (canUseGeneralGeminiDecision(generalGeminiDecision)) {
                Path generalTarget = resolveAiTargetPath(generalGeminiDecision.getTargetPath());

                Path movedTo = fileActionService.moveFileToFolder(
                        context.getFullPath(),
                        generalTarget
                );

                moveHistoryService.recordMove(
                        context.getFullPath(),
                        movedTo,
                        "Fallback to general Gemini decision: " + generalGeminiDecision.getReason()
                );

                System.out.println("PDF moved using general Gemini fallback to: " + movedTo);
                return true;
            }

            System.out.println("General Gemini decision also not usable. Falling back to normal flow.");
            return false;

        } catch (Exception e) {
            System.err.println("PDF AI classification failed.");

            try {
                if (canUseGeneralGeminiDecision(generalGeminiDecision)) {
                    Path generalTarget = resolveAiTargetPath(generalGeminiDecision.getTargetPath());

                    Path movedTo = fileActionService.moveFileToFolder(
                            context.getFullPath(),
                            generalTarget
                    );

                    moveHistoryService.recordMove(
                            context.getFullPath(),
                            movedTo,
                            "Fallback to general Gemini decision after PDF failure: " + generalGeminiDecision.getReason()
                    );

                    System.out.println("PDF moved using general Gemini fallback after PDF failure to: " + movedTo);
                    return true;
                }
            } catch (Exception fallbackException) {
                System.err.println("General Gemini fallback also failed.");
                fallbackException.printStackTrace();
            }

            e.printStackTrace();
            return false;
        }
    }

    private boolean canUseGeminiDecision(GeminiDecision decision) {
        return decision != null
                && decision.getTargetPath() != null
                && !decision.getTargetPath().isBlank()
                && decision.getConfidence() >= 0.75
                && !isManualReviewDecision(decision);
    }

    private boolean canUseGeneralGeminiDecision(GeminiDecision decision) {
        return decision != null
                && decision.getTargetPath() != null
                && !decision.getTargetPath().isBlank()
                && decision.getConfidence() >= 0.70
                && !isManualReviewDecision(decision);
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

        String drive = normalizedSlashes.substring(0, 2);
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