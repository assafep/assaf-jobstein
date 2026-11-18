package io.github.assafep.jobstein.environment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermissions;
import static org.junit.jupiter.api.Assertions.*;

class EnvironmentServiceTest {
    @TempDir Path root;

    @Test void createsAndListsIndependentEnvironments() throws Exception {
        var service = new EnvironmentService(root);
        assertTrue(service.list().isEmpty());
        service.create("production");
        service.create("development");
        service.create("production");
        assertEquals(java.util.List.of("development", "production"), service.list());
        assertNotEquals(service.validate("production"), service.validate("development"));
        assertEquals(PosixFilePermissions.fromString("rwx------"),Files.getPosixFilePermissions(root.resolve("production")));
    }

    @Test void rejectsTraversal() {
        var service = new EnvironmentService(root);
        assertThrows(IllegalArgumentException.class, () -> service.create("../outside"));
    }

    @Test void missingEnvironmentDoesNotValidate() {
        assertThrows(java.io.IOException.class,
            () -> new EnvironmentService(root).validate("production"));
    }
}
