package io.github.assafep.jobstein.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.assafep.jobstein.config.ConfigurationRepository;
import io.github.assafep.jobstein.domain.ApplicantProfile;
import io.github.assafep.jobstein.sandbox.LocalWorkspaceStorage;
import io.github.assafep.jobstein.validation.DiagnosticException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ProfileIndexSyncTest {
    @TempDir Path root;
    @Test void recoversKnownInterruptedWritesAndRefusesUnrelatedRemoteChanges()throws Exception{
        var config=new ConfigurationRepository(root.resolve("config"));
        var drive=new LocalWorkspaceStorage(root.resolve("drive"));
        var service=new UserProfileService(config,drive,null,"sandbox");
        var user=service.initialize("Example Applicant");
        var manifest=new io.github.assafep.jobstein.provisioning.ResourceManifestRepository(config);
        String id=manifest.load().resourceIds().get("user/profiles-index");
        var mapper=new ObjectMapper();
        var before=drive.readJson(id);
        var profile=new ApplicantProfile(UUID.randomUUID(),user.id(),"Engineer","engineer",ApplicantProfile.Status.INITIALIZING);
        var after=mapper.valueToTree(new UserProfileService.Profiles(Map.of("engineer",new UserProfileService.ProfileState(profile,List.of(),null))));
        var sync=new ProfileIndexSync(config,drive,id);
        config.save(ProfileIndexSync.JOURNAL,new ProfileIndexSync.Intent(id,before,after));
        drive.updateJson(id,after);
        assertThrows(DiagnosticException.class,sync::validate);
        sync.recoverAndValidate();
        assertEquals(profile.id(),service.get("engineer").profile().id());
        assertTrue(config.load(ProfileIndexSync.JOURNAL,ProfileIndexSync.Intent.class).isEmpty());
        config.save(ProfileIndexSync.JOURNAL,new ProfileIndexSync.Intent(id,after,before));
        var external=mapper.createObjectNode();external.putObject("entries");external.put("external",true);
        drive.updateJson(id,external);
        assertThrows(DiagnosticException.class,()->service.create("Another Role"));
        assertEquals(external,drive.readJson(id));
        assertEquals(profile.id(),service.get("engineer").profile().id());
        assertTrue(config.load(ProfileIndexSync.JOURNAL,ProfileIndexSync.Intent.class).isPresent());
    }
}
