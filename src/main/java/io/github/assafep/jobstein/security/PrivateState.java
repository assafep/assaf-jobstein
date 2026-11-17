package io.github.assafep.jobstein.security;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.*;

public final class PrivateState {
    private PrivateState(){}
    public static FileAttribute<?> filePermissions(){
        return PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"));
    }
    public static void directory(Path directory)throws IOException{
        Path path=directory.toAbsolutePath().normalize();
        if(Files.notExists(path,LinkOption.NOFOLLOW_LINKS)){
            Files.createDirectories(path,PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        }
        if(!Files.isDirectory(path,LinkOption.NOFOLLOW_LINKS))throw new IOException("Private state must be a real directory");
        Files.setPosixFilePermissions(path,PosixFilePermissions.fromString("rwx------"));
    }
}
