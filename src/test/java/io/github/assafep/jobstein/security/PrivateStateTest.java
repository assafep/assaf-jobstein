package io.github.assafep.jobstein.security;
import io.github.assafep.jobstein.config.ConfigurationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Map;
import io.github.assafep.jobstein.validation.DiagnosticException;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class PrivateStateTest {
    @TempDir Path root;
    @Test void configurationAndDirectoryAreOwnerOnly()throws Exception{
        assumeTrue(Files.getFileStore(root).supportsFileAttributeView("posix"));
        Path dir=root.resolve("state");
        new ConfigurationRepository(dir).save("descriptor",Map.of("synthetic",true));
        assertEquals(PosixFilePermissions.fromString("rwx------"),Files.getPosixFilePermissions(dir));
        assertEquals(PosixFilePermissions.fromString("rw-------"),Files.getPosixFilePermissions(dir.resolve("descriptor.yaml")));
    }

    @Test void configurationValidationDetectsLegacyPermissionsWithoutRewriting()throws Exception{
        assumeTrue(Files.getFileStore(root).supportsFileAttributeView("posix"));
        Path dir=root.resolve("legacy");
        var repository=new ConfigurationRepository(dir);
        repository.save("descriptor",Map.of("synthetic",true));
        Files.setPosixFilePermissions(dir.resolve("descriptor.yaml"),PosixFilePermissions.fromString("rw-r--r--"));
        var error=assertThrows(DiagnosticException.class,repository::validate);
        assertEquals(DiagnosticException.Code.LOCAL_CONFIGURATION_INVALID,error.code());
        assertEquals(PosixFilePermissions.fromString("rw-r--r--"),Files.getPosixFilePermissions(dir.resolve("descriptor.yaml")));
    }
}
