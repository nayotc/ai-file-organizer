package com.example.downloadorganizer.model;

public class GeminiDecision {
    private String category;
    private String targetPath;
    private double confidence;
    private String reason;
    private boolean needsUserConfirmation;

    public GeminiDecision() {
    }

    public GeminiDecision(String category, String targetPath, double confidence, String reason, boolean needsUserConfirmation) {
        this.category = category;
        this.targetPath = targetPath;
        this.confidence = confidence;
        this.reason = reason;
        this.needsUserConfirmation = needsUserConfirmation;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getTargetPath() {
        return targetPath;
    }

    public void setTargetPath(String targetPath) {
        this.targetPath = targetPath;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public boolean isNeedsUserConfirmation() {
        return needsUserConfirmation;
    }

    public void setNeedsUserConfirmation(boolean needsUserConfirmation) {
        this.needsUserConfirmation = needsUserConfirmation;
    }

    @Override
    public String toString() {
        return "GeminiDecision{" +
                "category='" + category + '\'' +
                ", targetPath='" + targetPath + '\'' +
                ", confidence=" + confidence +
                ", reason='" + reason + '\'' +
                ", needsUserConfirmation=" + needsUserConfirmation +
                '}';
    }
}