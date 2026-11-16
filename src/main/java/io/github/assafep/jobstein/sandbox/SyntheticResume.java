package io.github.assafep.jobstein.sandbox;

import java.io.IOException;
import java.nio.file.*;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.*;

public final class SyntheticResume {
    private SyntheticResume(){}
    public static Path create(Path directory,String slug,int version)throws IOException{
        if(!slug.matches("[a-z0-9]+([.-][a-z0-9]+)*")||version<1)throw new IllegalArgumentException("Invalid synthetic resume identity");
        Path folder=directory.resolve(slug);
        Files.createDirectories(folder);
        Path file=folder.resolve("synthetic resume "+version+".pdf");
        if(Files.exists(file))return file;
        Path temporary=Files.createTempFile(folder,"fixture-",".tmp");
        try{
            try(var document=new PDDocument()){
                var page=new PDPage();document.addPage(page);
                try(var content=new PDPageContentStream(document,page)){
                    content.beginText();
                    content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA),12);
                    content.newLineAtOffset(40,700);
                    content.showText("Synthetic applicant - "+slug+" - version "+version);
                    content.endText();
                }
                document.save(temporary.toFile());
            }
            Files.move(temporary,file,StandardCopyOption.ATOMIC_MOVE);
            return file;
        }finally{Files.deleteIfExists(temporary);}
    }
}
