package com.example.downloadorganizer.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;
import java.nio.file.Path;

public class PdfContentExtractor {

    public String extractText(Path pdfPath, int maxPages, int maxChars) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdfPath.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();

            int totalPages = document.getNumberOfPages();
            stripper.setStartPage(1);
            stripper.setEndPage(Math.min(maxPages, totalPages));

            String text = stripper.getText(document);
            text = normalize(text);

            if (text.length() > maxChars) {
                text = text.substring(0, maxChars);
            }

            return text;
        }
    }

    private String normalize(String text) {
        if (text == null) {
            return "";
        }

        return text
                .replace("\u0000", "")
                .replaceAll("\\s+", " ")
                .trim();
    }
}