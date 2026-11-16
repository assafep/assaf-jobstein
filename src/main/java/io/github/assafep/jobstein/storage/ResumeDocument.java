package io.github.assafep.jobstein.storage;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.HexFormat;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;

public record ResumeDocument(byte[] bytes,String sha256,String text) {
    public static final int MAX_BYTES=20_000_000;
    public ResumeDocument { bytes=bytes.clone(); }
    @Override public byte[] bytes(){return bytes.clone();}
    public static String sha256(byte[] data){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));}
        catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    public static ResumeDocument read(Path path)throws IOException{
        byte[] bytes;
        try(var input=Files.newInputStream(path)){bytes=input.readNBytes(MAX_BYTES+1);}
        if(bytes.length>MAX_BYTES)throw new IOException("PDF exceeds 20 MB");
        try(var pdf=Loader.loadPDF(bytes)){
            String text=new PDFTextStripper().getText(pdf);
            if(text.isBlank())throw new IOException("PDF has no extractable text");
            return new ResumeDocument(bytes,sha256(bytes),text);
        }
    }
}
