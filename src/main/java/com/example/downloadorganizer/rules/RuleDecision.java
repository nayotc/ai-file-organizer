package com.example.downloadorganizer.rules;

import java.nio.file.Path;

public class RuleDecision {
    private final FileCategory category;
    private final Path suggestedTargetFolder;
    private final String reason;

    public RuleDecision(FileCategory category, Path suggestedTargetFolder, String reason) {
        this.category = category;
        this.suggestedTargetFolder = suggestedTargetFolder;
        this.reason = reason;
    }

    public FileCategory getCategory() {
        return category;
    }

    public Path getSuggestedTargetFolder() {
        return suggestedTargetFolder;
    }

    public String getReason() {
        return reason;
    }

    @Override
    public String toString() {
        return "RuleDecision{" +
                "category=" + category +
                ", suggestedTargetFolder=" + suggestedTargetFolder +
                ", reason='" + reason + '\'' +
                '}';
    }
}