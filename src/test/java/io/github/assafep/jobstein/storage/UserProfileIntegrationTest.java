package io.github.assafep.jobstein.storage;

import io.github.assafep.jobstein.ai.*;
import io.github.assafep.jobstein.config.ConfigurationRepository;
import io.github.assafep.jobstein.domain.ApplicantProfile.Status;
import io.github.assafep.jobstein.sandbox.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import io.github.assafep.jobstein.provisioning.ResourceManifestRepository;

class UserProfileIntegrationTest {
    @TempDir Path root;
    private UserProfileService service(){
        var ai=new MockAiExecution(new AiConfiguration(1,"standard",Map.of("standard",new AiIntegrationConfig("mock-xai",Map.of("model","mock-grok"))),Map.of()));
        return new UserProfileService(new ConfigurationRepository(root.resolve("config")),new LocalWorkspaceStorage(root.resolve("workspace")),ai,"sandbox");
    }
    private Path pdf(String name,String text)throws Exception{
        Path path=root.resolve(name);
        try(var document=new PDDocument()){
            var page=new PDPage();document.addPage(page);
            try(var content=new PDPageContentStream(document,page)){
                content.beginText();content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA),12);
                content.newLineAtOffset(30,700);content.showText(text);content.endText();
            }
            document.save(path.toFile());
        }
        return path;
    }
    @Test void realProfileWorkflowWithOfflineAdapters()throws Exception{
        var service=service();
        var user=service.initialize("Example Applicant");
        assertEquals(user.id(),service().initialize("Example Applicant").id());
        var profile=service.create("Software Engineer").profile();
        assertEquals(profile.id(),service().create("Software Engineer").profile().id());
        var resume=pdf("fixture.pdf","Synthetic applicant");
        assertEquals(Status.RESUME_UPLOADED,service.upload(profile.slug(),resume).profile().status());
        assertEquals(1,service.upload(profile.slug(),resume).resumes().size());
        assertThrows(java.io.IOException.class,()->service.activate(profile.slug(),true));
        assertEquals(Status.AWAITING_REVIEW,service.generate(profile.slug(),resume).profile().status());
        service.activate(profile.slug(),true);
        assertEquals(Status.ACTIVE,service().get(profile.slug()).profile().status());
        assertTrue(service.descriptor(profile.slug()).path("descriptor").path("synthetic").asBoolean());
        var next=pdf("updated.pdf","Updated synthetic applicant");
        service.upload(profile.slug(),next);
        assertThrows(java.io.IOException.class,()->service.descriptor(profile.slug()));
        assertThrows(java.io.IOException.class,()->service.generate(profile.slug(),resume));
        var second=service.create("Product Manager").profile();
        var registry=service.applicationRegistry();
        assertTrue(registry.reserve("ats:example:1",Set.of(),profile.id()).isPresent());
        assertTrue(service().applicationRegistry().reserve("ats:example:1",Set.of(),second.id()).isEmpty());
        service.validateUser();
    }

    @Test void refusesNewIdentityWhenRemoteRootExistsWithoutLocalUser()throws Exception{
        var configuration=new ConfigurationRepository(root.resolve("config"));
        var storage=new LocalWorkspaceStorage(root.resolve("workspace"));
        var service=new UserProfileService(configuration,storage,
            new MockAiExecution(new AiConfiguration(1,"standard",Map.of("standard",new AiIntegrationConfig("mock-xai",Map.of("model","mock-grok"))),Map.of())),"sandbox");
        service.initialize("Example Applicant");
        var indexRepository=new ConfigurationRepository(root.resolve("workspace"));
        int resourceCount=indexRepository.load("index",LocalWorkspaceStorage.Index.class).orElseThrow().resources().size();
        configuration.remove("user");

        assertThrows(java.io.IOException.class,()->service.initialize("Different Applicant"));
        assertTrue(configuration.load("user",io.github.assafep.jobstein.domain.JobsteinUser.class).isEmpty());
        assertEquals(resourceCount,indexRepository.load("index",LocalWorkspaceStorage.Index.class).orElseThrow().resources().size());
    }

    @Test void validatesManifestSeedMetadataBeforeReuse()throws Exception{
        var configuration=new ConfigurationRepository(root.resolve("config"));
        var storage=new LocalWorkspaceStorage(root.resolve("workspace"));
        var service=new UserProfileService(configuration,storage,
            new MockAiExecution(new AiConfiguration(1,"standard",Map.of("standard",new AiIntegrationConfig("mock-xai",Map.of("model","mock-grok"))),Map.of())),"sandbox");
        service.initialize("Example Applicant");
        String id=new ResourceManifestRepository(configuration).load().resourceIds().get("user/metadata");
        var indexRepository=new ConfigurationRepository(root.resolve("workspace"));
        var index=indexRepository.load("index",LocalWorkspaceStorage.Index.class).orElseThrow();
        var resources=new HashMap<>(index.resources());
        var original=resources.get(id);
        resources.put(id,new LocalWorkspaceStorage.Resource(original.id(),"wrong-parent",original.name(),original.mime(),original.marker()));
        indexRepository.save("index",new LocalWorkspaceStorage.Index(resources));

        assertThrows(java.io.IOException.class,()->service.initialize("Example Applicant"));
    }

    @Test void refusesToAdoptAnUntrackedRemoteSeedInsteadOfCreatingADuplicate()throws Exception{
        var configuration=new ConfigurationRepository(root.resolve("config"));
        var storage=new LocalWorkspaceStorage(root.resolve("workspace"));
        var service=new UserProfileService(configuration,storage,
            new MockAiExecution(new AiConfiguration(1,"standard",Map.of("standard",new AiIntegrationConfig("mock-xai",Map.of("model","mock-grok"))),Map.of())),"sandbox");
        service.initialize("Example Applicant");
        var manifestRepository=new ResourceManifestRepository(configuration);
        var manifest=manifestRepository.load();
        var ids=new HashMap<>(manifest.resourceIds());
        String registryId=ids.remove("user/registry");
        var remoteRegistry=storage.readJson(registryId);
        configuration.save("resource-manifest",new ResourceManifestRepository.Manifest(1,ids));
        configuration.remove("upload-intents");
        var indexRepository=new ConfigurationRepository(root.resolve("workspace"));
        int resourceCount=indexRepository.load("index",LocalWorkspaceStorage.Index.class).orElseThrow().resources().size();

        assertThrows(java.io.IOException.class,()->service.initialize("Example Applicant"));
        assertEquals(remoteRegistry,storage.readJson(registryId));
        assertEquals(resourceCount,indexRepository.load("index",LocalWorkspaceStorage.Index.class).orElseThrow().resources().size());
    }
}
