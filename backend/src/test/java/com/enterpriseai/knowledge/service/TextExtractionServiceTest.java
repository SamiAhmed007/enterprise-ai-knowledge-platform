package com.enterpriseai.knowledge.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TextExtractionServiceTest {
    @TempDir
    Path tempDirectory;

    @Test
    void detectsExtensionCaseInsensitively() {
        assertThat(TextExtractionService.extension("Quarterly.Report.PDF")).isEqualTo("pdf");
    }

    @Test
    void returnsEmptyExtensionWhenMissing() {
        assertThat(TextExtractionService.extension("README")).isEmpty();
    }

    @Test
    void preservesPdfPageNumbersDuringExtraction() throws Exception {
        Path pdf = tempDirectory.resolve("policy.pdf");
        try (PDDocument document = new PDDocument()) {
            addPage(document, "First page retention policy");
            addPage(document, "Second page escalation procedure");
            document.save(pdf.toFile());
        }

        var pages = new TextExtractionService().extractPages(pdf, "policy.pdf");

        assertThat(pages).extracting(TextExtractionService.ExtractedPage::pageNumber)
                .containsExactly(1, 2);
        assertThat(pages).extracting(TextExtractionService.ExtractedPage::text)
                .allSatisfy(text -> assertThat(text).isNotBlank());
        assertThat(pages.get(1).text()).contains("escalation procedure");
    }

    private void addPage(PDDocument document, String text) throws Exception {
        PDPage page = new PDPage();
        document.addPage(page);
        try (PDPageContentStream content = new PDPageContentStream(document, page)) {
            content.beginText();
            content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
            content.newLineAtOffset(72, 720);
            content.showText(text);
            content.endText();
        }
    }
}
