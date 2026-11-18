package io.github.assafep.jobstein.security;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.Objects;
import java.util.Set;

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

    /** Validate existing state without changing permissions or following aliases. */
    public static void validateDirectory(Path directory)throws IOException{
        Path path=Objects.requireNonNull(directory).toAbsolutePath().normalize();
        if(Files.notExists(path,LinkOption.NOFOLLOW_LINKS))return;
        if(Files.isSymbolicLink(path)||!Files.isDirectory(path,LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Private state must be a real directory");
        validateOwner(path);
        validatePermissions(path,PosixFilePermissions.fromString("rwx------"));
    }

    public static void validateFile(Path file)throws IOException{
        Path path=Objects.requireNonNull(file).toAbsolutePath().normalize();
        if(Files.isSymbolicLink(path)||!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Private state must be a regular file");
        validateOwner(path);
        validatePermissions(path,PosixFilePermissions.fromString("rw-------"));
    }

    private static void validateOwner(Path path)throws IOException{
        try{
            UserPrincipal owner=Files.getOwner(path,LinkOption.NOFOLLOW_LINKS);
            String current=System.getProperty("user.name","");
            if(current.isBlank()||owner==null||!current.equals(owner.getName()))
                throw new IOException("Private state is not owned by the current user");
        }catch(UnsupportedOperationException|IllegalArgumentException e){
            throw new IOException("Private state ownership validation is unavailable",e);
        }
    }

    private static void validatePermissions(Path path,Set<PosixFilePermission> expected)throws IOException{
        try{
            if(!Files.getPosixFilePermissions(path,LinkOption.NOFOLLOW_LINKS).equals(expected))
                throw new IOException("Private state permissions are not owner-only");
        }catch(UnsupportedOperationException|IllegalArgumentException e){
            throw new IOException("POSIX private-state permissions are unavailable",e);
        }
    }
}
