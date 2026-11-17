package io.github.assafep.jobstein.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.assafep.jobstein.config.ConfigurationRepository;
import io.github.assafep.jobstein.provisioning.ResourceManifestRepository;
import io.github.assafep.jobstein.sandbox.LocalWorkspaceStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class ProfileIndexRecoveryTest {
    @TempDir Path root;
    @Test void previewsAndBacksUpBeforeExplicitReconciliation()throws Exception{
        var config=new ConfigurationRepository(root.resolve("config"));
        var storage=new LocalWorkspaceStorage(root.resolve("drive"));
        var service=new UserProfileService(config,storage,null,"sandbox");
        service.initialize("Example Applicant");
        service.create("Software Engineer");
        var recovery=service.recovery();
        String id=new ResourceManifestRepository(config).load().resourceIds().get("user/profiles-index");
        var expected=storage.readJson(id);
        var empty=new ObjectMapper().createObjectNode();empty.putObject("entries");
        storage.updateJson(id,empty);
        assertFalse(recovery.status().consistent());
        assertThrows(IOException.class,recovery::validate);
        assertFalse(recovery.reconcile(ProfileIndexRecovery.Source.LOCAL,false).applied());
        assertEquals(empty,storage.readJson(id));
        var repaired=recovery.reconcile(ProfileIndexRecovery.Source.LOCAL,true);
        assertTrue(repaired.applied());
        var backup=config.load(repaired.backup().orElseThrow(),ProfileIndexRecovery.Snapshot.class).orElseThrow();
        assertEquals(empty,backup.remote());
        assertEquals(expected,backup.local());
        assertTrue(recovery.validate().consistent());
        config.save("profiles",empty);
        assertTrue(recovery.reconcile(ProfileIndexRecovery.Source.REMOTE,true).applied());
        assertEquals(1,service.profiles().entries().size());
    }
}
