package com.enterpriseai.knowledge.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class TextExtractionService {
    public String extract(Path path, String originalName) throws IOException {
        return extractPages(path, originalName).stream()
                .map(ExtractedPage::text)
                .collect(Collectors.joining("\n"));
    }

    public List<ExtractedPage> extractPages(Path path, String originalName) throws IOException {
        String extension = extension(originalName);
        return switch (extension) {
            case "pdf" -> pagesFromPdf(path);
            case "docx" -> List.of(new ExtractedPage(null, fromDocx(path)));
            case "txt" -> List.of(new ExtractedPage(
                    null, Files.readString(path, StandardCharsets.UTF_8)));
            default -> throw new IllegalArgumentException("Unsupported file type: " + extension);
        };
    }

    private List<ExtractedPage> pagesFromPdf(Path path) throws IOException {
        try (PDDocument document = Loader.loadPDF(path.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            java.util.ArrayList<ExtractedPage> pages = new java.util.ArrayList<>();
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                pages.add(new ExtractedPage(page, stripper.getText(document)));
            }
            return pages;
        }
    }

    private String fromDocx(Path path) throws IOException {
        try (InputStream stream = Files.newInputStream(path);
             XWPFDocument document = new XWPFDocument(stream)) {
            return document.getParagraphs().stream()
                    .map(paragraph -> paragraph.getText())
                    .collect(Collectors.joining("\n"));
        }
    }

    public static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    public record ExtractedPage(Integer pageNumber, String text) {}
}
