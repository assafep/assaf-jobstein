package io.github.assafep.jobstein.storage;
import io.github.assafep.jobstein.config.ConfigurationRepository;
import io.github.assafep.jobstein.ai.*;
import io.github.assafep.jobstein.sandbox.*;
import io.github.assafep.jobstein.domain.ApplicantProfile.Status;
import io.github.assafep.jobstein.validation.DiagnosticException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ProfileOnboardingTest {
    @TempDir Path root;
    @Test void resumesStoredPdfAndReusesExistingDescriptorWithoutRepeatedConsent()throws Exception{
        var integration=new AiIntegrationConfig("mock-xai",Map.of("model","mock-grok"));
        var ai=new MockAiExecution(new AiConfiguration(1,"standard",Map.of("standard",integration),Map.of()));
        var service=new UserProfileService(new ConfigurationRepository(root.resolve("config")),new LocalWorkspaceStorage(root.resolve("drive")),ai,"sandbox");
        service.initialize("Example Applicant");
        var progress=new ArrayList<String>();
        var workflow=new ProfileOnboarding(service,progress::add);
        Path pdf=SyntheticResume.create(root.resolve("fixtures"),"software.engineer",1);
        assertThrows(DiagnosticException.class,()->workflow.run("Software Engineer",Optional.of(pdf),()->pdf,()->false,d->false));
        assertEquals(Status.RESUME_UPLOADED,service.get("software.engineer").profile().status());
        assertTrue(progress.isEmpty());
        Files.delete(pdf);
        var pending=workflow.run("Software Engineer",Optional.empty(),()->{throw new AssertionError("Already uploaded");},()->true,d->false);
        assertEquals(Status.AWAITING_REVIEW,pending.profile().status());
        assertEquals(List.of(
            "Preparing the stored resume for descriptor generation...",
            "Waiting for the AI provider to generate the descriptor. This can take a few minutes.",
            "The AI provider responded. Checking the descriptor against the resume...",
            "Saving the validated descriptor for review..."
        ),progress);
        progress.clear();
        var approved=workflow.run("Software Engineer",Optional.empty(),()->{throw new AssertionError("Already uploaded");},()->{throw new AssertionError("Already generated");},d->true);
        assertEquals(Status.ACTIVE,approved.profile().status());
        assertTrue(progress.isEmpty());
        assertEquals(pending.descriptorId(),approved.descriptorId());
        assertEquals(1,approved.resumes().size());
        var active=workflow.run("Software Engineer",Optional.empty(),()->{throw new AssertionError();},()->{throw new AssertionError();},d->{throw new AssertionError();});
        assertEquals(approved,active);
    }
}
