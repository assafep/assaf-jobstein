package io.github.assafep.jobstein.provisioning;
import io.github.assafep.jobstein.config.ConfigurationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.io.IOException;
import java.util.*;
import io.github.assafep.jobstein.validation.DiagnosticException;
import static org.junit.jupiter.api.Assertions.*;

class OnboardingServiceTest {
    @TempDir Path root;
    @Test void retriesFailedStepAndSkipsCompletedSteps()throws Exception{
        var state=new ProvisioningStateRepository(new ConfigurationRepository(root));
        var calls=new ArrayList<String>();
        var workflow=new OnboardingService(state,message->{});
        assertThrows(IOException.class,()->workflow.run(new OnboardingService.Steps(
            ()->calls.add("google"),()->calls.add("oauth"),()->{throw new IOException("synthetic failure");},
            ()->calls.add("user"),()->calls.add("profile"))));
        assertEquals(List.of("google","oauth"),calls);
        assertEquals(ProvisioningStateRepository.Status.FAILED,state.load().steps().get("ai-configure").status());
        workflow.run(new OnboardingService.Steps(()->fail("already completed"),()->fail("already completed"),
            ()->calls.add("ai"),()->calls.add("user"),()->calls.add("profile")));
        assertEquals(List.of("google","oauth","ai","user","profile"),calls);
        assertTrue(state.completed("first-profile"));
    }

    @Test void persistsFirstProfileSelectionAndRejectsReplacement()throws Exception{
        var state=new ProvisioningStateRepository(new ConfigurationRepository(root));
        var selected=new ProvisioningStateRepository.FirstProfile("Software Engineer","software.engineer",UUID.randomUUID());
        state.selectFirstProfile(selected);
        state.record("first-profile",ProvisioningStateRepository.Status.FAILED);
        assertEquals(selected,new ProvisioningStateRepository(new ConfigurationRepository(root)).firstProfile().orElseThrow());
        state.selectFirstProfile(selected);
        assertThrows(IOException.class,()->state.selectFirstProfile(new ProvisioningStateRepository.FirstProfile("Product Manager","product.manager",UUID.randomUUID())));
    }
    @Test void completedFirstProfileIsValidatedWithoutRerunningProvisioning()throws Exception{
        var repository=new ProvisioningStateRepository(new ConfigurationRepository(root));
        var workflow=new OnboardingService(repository,message->{});
        var steps=new OnboardingService.Steps(()->{},()->{},()->{},()->{},()->{});
        workflow.run(steps);
        var noWrites=new OnboardingService.Steps(()->fail(),()->fail(),()->fail(),()->fail(),()->fail());
        assertThrows(IOException.class,()->workflow.run(noWrites,()->{throw new IOException("Selected profile is no longer ready");}));
        assertTrue(repository.completed("first-profile"));
    }

    @Test void checkpointValidationRejectsUnknownStepWithoutWriting()throws Exception{
        var repository=new ProvisioningStateRepository(new ConfigurationRepository(root));
        var config=new ConfigurationRepository(root);
        config.save("provisioning-state",new ProvisioningStateRepository.State(1,Map.of(
            "unexpected",new ProvisioningStateRepository.Checkpoint(ProvisioningStateRepository.Status.FAILED,"2026-01-01T00:00:00Z"))));
        var error=assertThrows(DiagnosticException.class,repository::validate);
        assertEquals(DiagnosticException.Code.ONBOARDING_CHECKPOINT_INVALID,error.code());
        assertEquals(ProvisioningStateRepository.Status.FAILED,repository.load().steps().get("unexpected").status());
    }

    @Test void checkpointValidationRequiresSelectionForCompletedFirstProfile()throws Exception{
        var repository=new ProvisioningStateRepository(new ConfigurationRepository(root));
        repository.record("first-profile",ProvisioningStateRepository.Status.COMPLETED);
        var error=assertThrows(DiagnosticException.class,repository::validate);
        assertEquals(DiagnosticException.Code.ONBOARDING_CHECKPOINT_INVALID,error.code());
    }
}
