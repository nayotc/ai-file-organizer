package com.example.downloadorganizer.rules;

import com.example.downloadorganizer.model.FileContext;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

public class RuleEngine {

    private static final Set<String> DOCUMENT_EXTENSIONS = Set.of(
            "pdf", "doc", "docx", "txt", "rtf", "xls", "xlsx", "ppt", "pptx"
    );

    private static final Set<String> IMAGE_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "gif", "bmp", "webp"
    );

    private static final Set<String> ARCHIVE_EXTENSIONS = Set.of(
            "zip", "rar", "7z", "tar", "gz"
    );

    private static final Set<String> INSTALLER_EXTENSIONS = Set.of(
            "exe", "msi"
    );

    public RuleDecision decide(FileContext context) {
        String ext = context.getExtension().toLowerCase();
        Path userHome = Paths.get(System.getProperty("user.home"));

        if (DOCUMENT_EXTENSIONS.contains(ext)) {
            return new RuleDecision(
                    FileCategory.DOCUMENTS,
                    userHome.resolve("Documents"),
                    "Matched by document extension: ." + ext
            );
        }

        if (IMAGE_EXTENSIONS.contains(ext)) {
            return new RuleDecision(
                    FileCategory.PICTURES,
                    userHome.resolve("Pictures"),
                    "Matched by image extension: ." + ext
            );
        }

        if (ARCHIVE_EXTENSIONS.contains(ext)) {
            return new RuleDecision(
                    FileCategory.ARCHIVES,
                    userHome.resolve("Archives"),
                    "Matched by archive extension: ." + ext
            );
        }

        if (INSTALLER_EXTENSIONS.contains(ext)) {
            return new RuleDecision(
                    FileCategory.INSTALLERS,
                    userHome.resolve("Installers"),
                    "Matched by installer extension: ." + ext
            );
        }

        return new RuleDecision(
                FileCategory.MANUAL_REVIEW,
                userHome.resolve("ManualReview"),
                "No matching rule for extension: ." + ext
        );
    }
}