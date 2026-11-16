package io.github.assafep.jobstein.validation;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

class DoctorServiceTest {
    @Test void continuesAfterFailureWithoutDeclaringReady()throws Exception{
        var report=new DoctorService().inspect(List.of(new DoctorService.Probe("bad",()->{throw new IOException("private details");}),new DoctorService.Probe("good",()->{})));
        assertFalse(report.ready());
        assertEquals(ValidationResult.Status.FAIL,report.checks().getFirst().status());
        assertEquals("IOException",report.checks().getFirst().message());
        assertEquals(ValidationResult.Status.PASS,report.checks().getLast().status());
    }
}
