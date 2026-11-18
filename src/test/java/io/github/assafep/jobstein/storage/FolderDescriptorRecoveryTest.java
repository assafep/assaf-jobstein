package io.github.assafep.jobstein.storage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.assafep.jobstein.ai.*;
import io.github.assafep.jobstein.config.ConfigurationRepository;
import io.github.assafep.jobstein.provisioning.ResourceManifestRepository;
import io.github.assafep.jobstein.sandbox.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FolderDescriptorRecoveryTest {
    @TempDir Path root;
    private static final class FaultStorage implements WorkspaceStorage {
        final LocalWorkspaceStorage delegate;
        boolean folderFailure;
        boolean descriptorBeforeUploadFailure;
        boolean descriptorFailure;
        boolean indexFailure;
        FaultStorage(LocalWorkspaceStorage delegate){this.delegate=delegate;}
        public JsonNode userInfo(){return delegate.userInfo();}
        public JsonNode file(String id)throws Exception{return delegate.file(id);}
        public String ensureFolder(String p,String n,String m)throws Exception{return delegate.ensureFolder(p,n,m);}
        public Optional<String> findFolder(String p,String n,String m)throws Exception{return delegate.findFolder(p,n,m);}
        public String ensureFolderWithId(String p,String n,String m,String id)throws Exception{
            String actual=delegate.ensureFolderWithId(p,n,m,id);
            if(folderFailure){folderFailure=false;throw new IOException("Lost folder response");}
            return actual;
        }
        public void validateFolder(String id,String p,String n,String m)throws Exception{delegate.validateFolder(id,p,n,m);}
        public String upload(String p,String n,String m,byte[] b)throws Exception{return delegate.upload(p,n,m,b);}
        public String allocateFileId(){return delegate.allocateFileId();}
        public String ensureUpload(String p,String n,String m,byte[] b,String id)throws Exception{
            if(descriptorBeforeUploadFailure&&n.startsWith("applicant-descriptor-")){descriptorBeforeUploadFailure=false;throw new IOException("Failed before descriptor upload");}
            String actual=delegate.ensureUpload(p,n,m,b,id);
            if(descriptorFailure&&n.startsWith("applicant-descriptor-")){descriptorFailure=false;throw new IOException("Lost descriptor response");}
            return actual;
        }
        public void updateJson(String id,JsonNode data)throws Exception{
            delegate.updateJson(id,data);
            if(indexFailure){indexFailure=false;throw new IOException("Lost index update response");}
        }
        public byte[] readBytes(String id,int limit)throws Exception{return delegate.readBytes(id,limit);}
        public JsonNode readJson(String id)throws Exception{return delegate.readJson(id);}
        public boolean hasActiveNamedResource(String parent,String name)throws Exception{return delegate.hasActiveNamedResource(parent,name);}
    }
    @Test void recoversFolderAndDescriptorWithoutRegenerating()throws Exception{
        var config=new ConfigurationRepository(root.resolve("config"));
        var storage=new FaultStorage(new LocalWorkspaceStorage(root.resolve("drive")));
        var ai=new MockAiExecution(new AiConfiguration(1,"standard",Map.of("standard",new AiIntegrationConfig("mock-xai",Map.of("model","mock-grok"))),Map.of()));
        var service=new UserProfileService(config,storage,ai,"sandbox");
        storage.folderFailure=true;
        assertThrows(IOException.class,()->service.initialize("Example Applicant"));
        var intents=new ResourceManifestRepository(config,"upload-intents");
        String folderId=intents.load().resourceIds().get("folder/root");
        assertNotNull(folderId);
        service.initialize("Example Applicant");
        assertEquals(folderId,new ResourceManifestRepository(config).load().resourceIds().get("root"));
        var profile=service.create("Software Engineer").profile();
        var pdf=SyntheticResume.create(root.resolve("fixtures"),profile.slug(),1);
        service.upload(profile.slug(),pdf);
        storage.descriptorFailure=true;
        assertThrows(IOException.class,()->service.generate(profile.slug(),pdf));
        var journal="descriptor-"+profile.id();
        var intent=config.load(journal,UserProfileService.DescriptorIntent.class).orElseThrow();
        assertThrows(IllegalArgumentException.class,()->new UserProfileService.DescriptorIntent("../invalid",intent.payload()));
        String expected=intents.load().resourceIds().get("descriptor/"+intent.operationId());
        AiExecution mustNotCallAi=new AiExecution(){
            public AiConfiguration load(){throw new AssertionError("AI must not run on retry");}
            public AiProviderAdapter adapter(AiIntegrationConfig ignored){throw new AssertionError("AI must not run on retry");}
        };
        var recovered=new UserProfileService(config,storage,mustNotCallAi,"sandbox");
        assertThrows(IOException.class,()->recovered.activate(profile.slug(),true));
        assertTrue(config.load(journal,UserProfileService.DescriptorIntent.class).isPresent());
        assertEquals(expected,recovered.generate(profile.slug(),pdf).descriptorId());
        assertTrue(config.load(journal,UserProfileService.DescriptorIntent.class).isEmpty());
        assertEquals(intent.payload(),recovered.descriptor(profile.slug()));
        assertNotEquals(expected,service.generate(profile.slug(),pdf).descriptorId());
        storage.indexFailure=true;
        assertThrows(IOException.class,()->service.generate(profile.slug(),pdf));
        var committed=config.load(journal,UserProfileService.DescriptorIntent.class).orElseThrow();
        String committedId=intents.load().resourceIds().get("descriptor/"+committed.operationId());
        var workflow=new ProfileOnboarding(recovered);
        var mismatchedPayload=(ObjectNode)committed.payload().deepCopy();
        mismatchedPayload.put("model","unexpected-model");
        config.save(journal,new UserProfileService.DescriptorIntent(committed.operationId(),mismatchedPayload));
        assertThrows(IOException.class,()->workflow.run("Software Engineer",Optional.empty(),()->{throw new AssertionError("Resume already stored");},()->{throw new AssertionError("AI result already stored");},d->true));
        assertTrue(config.load(journal,UserProfileService.DescriptorIntent.class).isPresent());
        config.save(journal,committed);
        var driveConfig=new ConfigurationRepository(root.resolve("drive"));
        var driveIndex=driveConfig.load("index",LocalWorkspaceStorage.Index.class).orElseThrow();
        var resources=new HashMap<>(driveIndex.resources());
        var originalResource=resources.get(committedId);
        resources.put(committedId,new LocalWorkspaceStorage.Resource(originalResource.id(),"wrong-parent",originalResource.name(),originalResource.mime(),originalResource.marker()));
        driveConfig.save("index",new LocalWorkspaceStorage.Index(resources));
        assertThrows(IOException.class,()->workflow.run("Software Engineer",Optional.empty(),()->{throw new AssertionError("Resume already stored");},()->{throw new AssertionError("AI result already stored");},d->true));
        assertTrue(config.load(journal,UserProfileService.DescriptorIntent.class).isPresent());
        resources.put(committedId,originalResource);
        driveConfig.save("index",new LocalWorkspaceStorage.Index(resources));
        var ready=workflow.run("Software Engineer",Optional.empty(),()->{throw new AssertionError("Resume already stored");},()->{throw new AssertionError("AI result already stored");},d->true);
        assertEquals(committedId,ready.descriptorId());
        assertEquals(io.github.assafep.jobstein.domain.ApplicantProfile.Status.ACTIVE,ready.profile().status());
        assertTrue(config.load(journal,UserProfileService.DescriptorIntent.class).isEmpty());
        var replacement=SyntheticResume.create(root.resolve("fixtures"),profile.slug(),2);
        assertEquals(2,recovered.upload(profile.slug(),replacement).resumes().size());
    }

    @Test void recoversPersistedDescriptorBeforeUploadWithoutCallingAi()throws Exception{
        var config=new ConfigurationRepository(root.resolve("config-before-upload"));
        var storage=new FaultStorage(new LocalWorkspaceStorage(root.resolve("drive-before-upload")));
        var ai=new MockAiExecution(new AiConfiguration(1,"standard",Map.of("standard",new AiIntegrationConfig("mock-xai",Map.of("model","mock-grok"))),Map.of()));
        var service=new UserProfileService(config,storage,ai,"sandbox");
        service.initialize("Example Applicant");
        var profile=service.create("Software Engineer").profile();
        var pdf=SyntheticResume.create(root.resolve("fixtures-before-upload"),profile.slug(),1);
        service.upload(profile.slug(),pdf);
        storage.descriptorBeforeUploadFailure=true;

        assertThrows(IOException.class,()->service.generate(profile.slug(),pdf));
        String journal="descriptor-"+profile.id();
        var intent=config.load(journal,UserProfileService.DescriptorIntent.class).orElseThrow();
        String allocated=new ResourceManifestRepository(config,"upload-intents").load().resourceIds().get("descriptor/"+intent.operationId());
        String profileFolder=new ResourceManifestRepository(config).load().resourceIds().get("profile/"+profile.slug());
        String name="applicant-descriptor-"+intent.payload().path("resumeSha256").asText().substring(0,12)+".json";
        assertNotNull(allocated);
        assertFalse(storage.delegate.hasActiveNamedResource(profileFolder,name));
        AiExecution mustNotCallAi=new AiExecution(){
            public AiConfiguration load(){throw new AssertionError("AI must not run on retry");}
            public AiProviderAdapter adapter(AiIntegrationConfig ignored){throw new AssertionError("AI must not run on retry");}
        };
        var recovered=new UserProfileService(config,storage,mustNotCallAi,"sandbox");
        assertThrows(IOException.class,()->recovered.activate(profile.slug(),true));
        var resumed=recovered.resumePendingDescriptor(profile.slug()).orElseThrow();
        assertEquals(allocated,resumed.descriptorId());
        assertTrue(config.load(journal,UserProfileService.DescriptorIntent.class).isEmpty());
        assertEquals(intent.payload(),recovered.descriptor(profile.slug()));
    }
}
