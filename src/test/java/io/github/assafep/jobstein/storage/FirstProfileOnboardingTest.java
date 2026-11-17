package io.github.assafep.jobstein.storage;

import io.github.assafep.jobstein.config.ConfigurationRepository;
import io.github.assafep.jobstein.ai.*;
import io.github.assafep.jobstein.sandbox.*;
import io.github.assafep.jobstein.provisioning.*;
import io.github.assafep.jobstein.validation.DiagnosticException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FirstProfileOnboardingTest {
    @TempDir Path root;
    private ConfigurationRepository config;
    private UserProfileService service;
    private ProvisioningStateRepository repository;
    private FirstProfileOnboarding workflow;
    private MockAiExecution ai;
    private void setup()throws Exception{
        config=new ConfigurationRepository(root.resolve("config"));
        var integration=new AiIntegrationConfig("mock-xai",Map.of("model","mock-grok"));
        ai=new MockAiExecution(new AiConfiguration(1,"standard",Map.of("standard",integration),Map.of()));
        service=new UserProfileService(config,new LocalWorkspaceStorage(root.resolve("drive")),ai,"sandbox");
        service.initialize("Example Applicant");repository=new ProvisioningStateRepository(config);
        workflow=new FirstProfileOnboarding(repository,service);
    }
    private Path pdf()throws Exception{return SyntheticResume.create(root.resolve("fixtures"),"software.engineer",1);}
    private void retry(boolean approve)throws Exception{
        workflow.run(Optional.empty(),()->{throw new AssertionError("Role already selected");},Optional.empty(),
            ()->{throw new AssertionError("Resume already uploaded");},()->{throw new AssertionError("Descriptor already generated");},d->approve);
    }
    @Test void interruptedResumeAndDeclinedReviewRetainIdentityDespiteOtherActiveProfile()throws Exception{
        setup();
        new ProfileOnboarding(service).run("Product Manager",Optional.of(pdf()),this::pdf,()->true,d->true);
        assertThrows(IOException.class,()->workflow.run(Optional.of("Software Engineer"),()->{throw new AssertionError();},Optional.empty(),
            ()->{throw new IOException("Synthetic interrupted resume selection");},()->true,d->true));
        var selected=repository.firstProfile().orElseThrow();
        assertEquals(selected.id(),service.get(selected.slug()).profile().id());
        assertThrows(DiagnosticException.class,()->workflow.run(Optional.empty(),()->{throw new AssertionError();},Optional.of(pdf()),this::pdf,()->true,d->false));
        assertFalse(repository.completed("first-profile"));
        assertThrows(DiagnosticException.class,()->retry(false));
        retry(true);
        assertEquals(selected.id(),service.get(selected.slug()).profile().id());
        assertEquals(1,service.get(selected.slug()).resumes().size());
    }
    @Test void completedCheckpointIsReadOnlyAndRejectsDeactivationOrChangedUuid()throws Exception{
        setup();workflow.run(Optional.of("Software Engineer"),()->{throw new AssertionError();},Optional.of(pdf()),this::pdf,()->true,d->true);
        repository.record("first-profile",ProvisioningStateRepository.Status.COMPLETED);
        retry(false); // No review callback is needed for an already active profile.
        assertThrows(IOException.class,()->workflow.run(Optional.of("Product Manager"),()->"Product Manager",Optional.empty(),this::pdf,()->true,d->true));
        service.activate("software.engineer",false);
        assertThrows(DiagnosticException.class,()->retry(true));
        var selected=repository.firstProfile().orElseThrow();
        config.save("onboarding-first-profile",new ProvisioningStateRepository.FirstProfile(selected.role(),selected.slug(),UUID.randomUUID()));
        assertThrows(IOException.class,()->retry(true));
    }
    @Test void legacyCompletedCheckpointAdoptsOnlyUnambiguousProfileAndDoesNotAskForResume()throws Exception{
        setup();new ProfileOnboarding(service).run("Software Engineer",Optional.of(pdf()),this::pdf,()->true,d->true);
        repository.record("first-profile",ProvisioningStateRepository.Status.COMPLETED);
        retry(false);
        assertEquals(service.get("software.engineer").profile().id(),repository.firstProfile().orElseThrow().id());
        config.remove("onboarding-first-profile");
        new ProfileOnboarding(service).run("Product Manager",Optional.of(pdf()),this::pdf,()->true,d->true);
        assertThrows(IOException.class,()->workflow.run(Optional.empty(),()->{throw new IOException("Explicit role required");},Optional.empty(),this::pdf,()->true,d->true));
        assertTrue(repository.firstProfile().isEmpty());
        workflow.run(Optional.of("Software Engineer"),()->{throw new AssertionError();},Optional.empty(),this::pdf,()->true,d->true);
    }
    @Test void completedCheckpointDoesNotRecreateMissingProfile()throws Exception{
        setup();repository.selectFirstProfile(new ProvisioningStateRepository.FirstProfile("Software Engineer","software.engineer",UUID.randomUUID()));
        repository.record("first-profile",ProvisioningStateRepository.Status.COMPLETED);
        assertThrows(IOException.class,()->retry(true));
        assertTrue(service.profiles().entries().isEmpty());
    }
    @Test void slugCollisionDoesNotPersistAnUnusableSelection()throws Exception{
        setup();service.create("Software Engineer");
        assertThrows(IOException.class,()->workflow.run(Optional.of("Software-Engineer"),()->{throw new AssertionError();},Optional.empty(),this::pdf,()->true,d->true));
        assertTrue(repository.firstProfile().isEmpty());
    }
    @Test void lostFolderResponseBeforeIndexSaveReusesAllocatedProfileIdentity()throws Exception{
        setup();
        var delegate=new LocalWorkspaceStorage(root.resolve("drive"));
        boolean[] failOnce={true};
        var storage=(WorkspaceStorage)java.lang.reflect.Proxy.newProxyInstance(WorkspaceStorage.class.getClassLoader(),new Class<?>[]{WorkspaceStorage.class},(proxy,method,args)->{
            Object result;
            try{result=method.invoke(delegate,args);}catch(java.lang.reflect.InvocationTargetException e){throw e.getCause();}
            if(method.getName().equals("ensureFolderWithId")&&failOnce[0]){failOnce[0]=false;throw new IOException("Synthetic lost profile folder response");}
            return result;
        });
        workflow=new FirstProfileOnboarding(repository,new UserProfileService(config,storage,ai,"sandbox"));
        assertThrows(IOException.class,()->workflow.run(Optional.of("Software Engineer"),()->{throw new AssertionError();},Optional.of(pdf()),this::pdf,()->true,d->true));
        var selected=repository.firstProfile().orElseThrow();
        assertTrue(service.profiles().entries().isEmpty());
        workflow=new FirstProfileOnboarding(new ProvisioningStateRepository(config),service);
        workflow.run(Optional.empty(),()->{throw new AssertionError("Already selected");},Optional.of(pdf()),this::pdf,()->true,d->true);
        assertEquals(selected.id(),service.get(selected.slug()).profile().id());
    }
}
