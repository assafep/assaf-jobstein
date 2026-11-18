package io.github.assafep.jobstein.storage;
import io.github.assafep.jobstein.config.ConfigurationRepository;
import io.github.assafep.jobstein.sandbox.LocalWorkspaceStorage;
import io.github.assafep.jobstein.provisioning.ResourceManifestRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

class UploadRecoveryTest {
    @TempDir Path root;
    @Test void resumesInitializationAfterLostUploadResponse()throws Exception{
        var local=new LocalWorkspaceStorage(root.resolve("drive"));
        var config=new ConfigurationRepository(root.resolve("config"));
        WorkspaceStorage failing=new WorkspaceStorage(){
            boolean fail=true;
            public JsonNode userInfo(){return local.userInfo();}
            public JsonNode file(String id)throws Exception{return local.file(id);}
            public String ensureFolder(String p,String n,String m)throws Exception{return local.ensureFolder(p,n,m);}
            public java.util.Optional<String> findFolder(String p,String n,String m)throws Exception{return local.findFolder(p,n,m);}
            public String ensureFolderWithId(String p,String n,String m,String id)throws Exception{return local.ensureFolderWithId(p,n,m,id);}
            public void validateFolder(String id,String p,String n,String m)throws Exception{local.validateFolder(id,p,n,m);}
            public String upload(String p,String n,String m,byte[] b)throws Exception{return local.upload(p,n,m,b);}
            public String allocateFileId(){return local.allocateFileId();}
            public String ensureUpload(String p,String n,String m,byte[] b,String id)throws Exception{
                String result=local.ensureUpload(p,n,m,b,id);
                if(fail){fail=false;throw new IOException("Simulated lost response after creation");}
                return result;
            }
            public void updateJson(String id,JsonNode data)throws Exception{local.updateJson(id,data);}
            public byte[] readBytes(String id,int limit)throws Exception{return local.readBytes(id,limit);}
        public JsonNode readJson(String id)throws Exception{return local.readJson(id);}
            public boolean hasActiveNamedResource(String parent,String name)throws Exception{return local.hasActiveNamedResource(parent,name);}
        };
        // AI is not used by user initialization.
        var service=new UserProfileService(config,failing,null,"sandbox");
        assertThrows(IOException.class,()->service.initialize("Example Applicant"));
        String allocated=new ResourceManifestRepository(config,"upload-intents").load().resourceIds().get("seed/user/metadata");
        assertNotNull(allocated);
        assertEquals("user.json",local.file(allocated).path("name").asText());
        var recovered=new UserProfileService(config,local,null,"sandbox");
        recovered.initialize("Example Applicant");
        assertEquals(allocated,new ResourceManifestRepository(config).load().resourceIds().get("user/metadata"));
        recovered.validateUser();
    }
}
