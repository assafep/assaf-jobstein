package io.github.assafep.jobstein.storage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.*;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class ResumeDocumentTest {
    @TempDir Path root;
    @Test void extractsSyntheticPdfAndHashesOriginalBytes()throws Exception{
        Path file=root.resolve("fixture.pdf");
        try(var document=new PDDocument()){
            var page=new PDPage();document.addPage(page);
            try(var content=new PDPageContentStream(document,page)){
                content.beginText();content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA),12);
                content.newLineAtOffset(30,700);content.showText("Synthetic applicant Java");content.endText();
            }
            document.save(file.toFile());
        }
        var resume=ResumeDocument.read(file);
        assertTrue(resume.text().contains("Synthetic applicant Java"));
        assertEquals(ResumeDocument.sha256(Files.readAllBytes(file)),resume.sha256());
    }
    @Test void rejectsInvalidPdf()throws Exception{
        Path file=root.resolve("bad.pdf");Files.writeString(file,"not a PDF");
        assertThrows(java.io.IOException.class,()->ResumeDocument.read(file));
    }
}
