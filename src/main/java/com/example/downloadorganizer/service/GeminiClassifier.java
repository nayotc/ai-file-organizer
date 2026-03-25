
package com.example.downloadorganizer.service;

import com.example.downloadorganizer.model.FileContext;
import com.example.downloadorganizer.model.GeminiDecision;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

public class GeminiClassifier {

    private static final String DEFAULT_MODEL = "gemini-2.5-flash";

    private final String apiKey;
    private final String model;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public GeminiClassifier() {
        this.apiKey = System.getenv("GEMINI_API_KEY");
        this.model = DEFAULT_MODEL;
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("GEMINI_API_KEY is not set.");
        }
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public GeminiDecision classify(FileContext fileContext, String ruleSuggestion)
            throws IOException, InterruptedException {

        String prompt = buildGeneralPrompt(fileContext, ruleSuggestion);
        return sendPrompt(prompt);
    }

    public GeminiDecision classifyPdf(FileContext fileContext, String extractedText)
            throws IOException, InterruptedException {

        String prompt = buildPdfPrompt(fileContext, extractedText);
        return sendPrompt(prompt);
    }

    private GeminiDecision sendPrompt(String prompt) throws IOException, InterruptedException {
        String requestBody = buildRequestBody(prompt);

        String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                + model + ":generateContent?key=" + apiKey;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Gemini API error. Status: " + response.statusCode() + ", body: " + response.body());
        }

        return extractDecisionFromResponse(response.body());
    }

    private String buildGeneralPrompt(FileContext fileContext, String ruleSuggestion) {
        return """
                You are a careful desktop file organization assistant.

                Your task is to suggest the best target folder for a downloaded file.

                Return only valid JSON in this exact format:
                {
                  "category": "string",
                  "targetPath": "string",
                  "confidence": 0.0,
                  "reason": "string",
                  "needsUserConfirmation": true
                }

                Hard requirements:
                - targetPath must be a RELATIVE folder path only.
                - Do NOT return an absolute path.
                - Do NOT return drive letters like C:\\.
                - Do NOT return usernames or placeholders like <username>.
                - Use Windows-style folder meaning, but return the path with forward slashes.
                - Examples of valid targetPath values:
                  - Documents/Career/Resumes
                  - Documents/Finance/Invoices
                  - Documents/University/Courses
                  - Pictures/Screenshots
                  - Downloads/ManualReview

                Decision rules:
                - Be careful and practical.
                - Prefer a specific useful folder over a generic one when the file is identifiable.
                - If the file type or purpose is unclear, return:
                  category = MANUAL_REVIEW
                  targetPath = Downloads/ManualReview
                - category may be any short descriptive label such as:
                  RESUME, INVOICE, COURSE_MATERIAL, SCREENSHOT, ARCHIVE, INSTALLER, PHOTO, MANUAL_REVIEW
                - Do not return markdown.
                - Do not return explanations outside JSON.

                File metadata:
                - fileName: %s
                - extension: %s
                - mimeType: %s
                - sizeBytes: %d
                - currentPath: %s
                - ruleSuggestion: %s
                """.formatted(
                escape(fileContext.getFileName()),
                escape(fileContext.getExtension()),
                escape(fileContext.getMimeType()),
                fileContext.getSizeBytes(),
                escape(fileContext.getFullPath().toString()),
                escape(ruleSuggestion)
        );
    }

    private String buildPdfPrompt(FileContext fileContext, String extractedText) {
        return """
                You are a careful desktop file organization assistant.

                Your task is to classify a PDF mainly by its content and suggest the best target folder.

                Return only valid JSON in this exact format:
                {
                  "category": "string",
                  "targetPath": "string",
                  "confidence": 0.0,
                  "reason": "string",
                  "needsUserConfirmation": true
                }

                Hard requirements:
                - targetPath must be a RELATIVE folder path only.
                - Do NOT return an absolute path.
                - Do NOT return drive letters like C:\\.
                - Do NOT return usernames or placeholders like <username>.
                - Use forward slashes in targetPath.
                - Examples of valid targetPath values:
                  - Documents/Career/Resumes
                  - Documents/Finance/Invoices
                  - Documents/Finance/Bank
                  - Documents/Legal/Contracts
                  - Documents/University/Assignments
                  - Documents/University/CourseMaterials
                  - Documents/Personal/Forms
                  - Downloads/ManualReview

                Decision rules:
                - Base the decision mainly on the PDF content, not only on the filename.
                - Prefer the most specific reasonable folder.
                - If the PDF is clearly a resume or CV, return:
                  category = RESUME
                  targetPath = Documents/Career/Resumes
                - If the PDF is clearly an invoice, prefer:
                  category = INVOICE
                  targetPath = Documents/Finance/Invoices
                - If the PDF is clearly a bank statement or banking document, prefer:
                  category = BANK_DOCUMENT
                  targetPath = Documents/Finance/Bank
                - If the PDF is clearly a contract or legal agreement, prefer:
                  category = CONTRACT
                  targetPath = Documents/Legal/Contracts
                - If the PDF is clearly academic study material, prefer:
                  category = COURSE_MATERIAL
                  targetPath = Documents/University/CourseMaterials
                - If the PDF is clearly an academic assignment, prefer:
                  category = ASSIGNMENT
                  targetPath = Documents/University/Assignments
                - If the PDF is clearly a form or personal record, prefer:
                  category = PERSONAL_FORM
                  targetPath = Documents/Personal/Forms
                - Only return MANUAL_REVIEW if the content is genuinely ambiguous.
                - If unsure, return:
                  category = MANUAL_REVIEW
                  targetPath = Downloads/ManualReview
                - Do not return markdown.
                - Do not return explanations outside JSON.

                File metadata:
                - fileName: %s
                - extension: %s
                - mimeType: %s
                - sizeBytes: %d

                Extracted PDF text:
                %s
                """.formatted(
                escape(fileContext.getFileName()),
                escape(fileContext.getExtension()),
                escape(fileContext.getMimeType()),
                fileContext.getSizeBytes(),
                escape(extractedText)
        );
    }

    private String buildRequestBody(String prompt) throws IOException {
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode textPart = objectMapper.createObjectNode().put("text", prompt);
        ObjectNode content = objectMapper.createObjectNode()
                .set("parts", objectMapper.createArrayNode().add(textPart));

        root.set("contents", objectMapper.createArrayNode().add(content));

        ObjectNode generationConfig = objectMapper.createObjectNode();
        generationConfig.put("responseMimeType", "application/json");
        root.set("generationConfig", generationConfig);

        return objectMapper.writeValueAsString(root);
    }

    private GeminiDecision extractDecisionFromResponse(String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);

        JsonNode candidates = root.path("candidates");
        if (!candidates.isArray() || candidates.isEmpty()) {
            throw new IOException("Gemini response did not contain candidates. Full response: " + responseBody);
        }

        JsonNode textNode = candidates.get(0)
                .path("content")
                .path("parts")
                .get(0)
                .path("text");

        if (textNode == null || textNode.isMissingNode() || textNode.asText().isBlank()) {
            throw new IOException("Gemini response did not contain text JSON. Full response: " + responseBody);
        }

        String jsonText = textNode.asText().trim();
        GeminiDecision decision = objectMapper.readValue(jsonText, GeminiDecision.class);

        if (decision.getCategory() == null || decision.getCategory().isBlank()) {
            decision.setCategory("MANUAL_REVIEW");
        }

        if (decision.getTargetPath() == null || decision.getTargetPath().isBlank()) {
            decision.setTargetPath("Downloads/ManualReview");
        }

        if (decision.getReason() == null || decision.getReason().isBlank()) {
            decision.setReason("No reason provided by model.");
        }

        return decision;
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", " ")
                .replace("\n", " ")
                .trim();
    }
}