package io.github.assafep.jobstein.provisioning;
import io.github.assafep.jobstein.config.ConfigurationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.io.IOException;
import java.util.*;
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
}
