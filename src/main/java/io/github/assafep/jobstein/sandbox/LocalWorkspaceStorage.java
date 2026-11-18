package io.github.assafep.jobstein.sandbox;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.assafep.jobstein.config.AtomicFiles;
import io.github.assafep.jobstein.config.ConfigurationRepository;
import io.github.assafep.jobstein.storage.WorkspaceStorage;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class LocalWorkspaceStorage implements WorkspaceStorage {
    private static final ObjectMapper JSON=new ObjectMapper();
    private static final String FOLDER="application/vnd.google-apps.folder";
    private final Path root;
    private final ConfigurationRepository repository;
    public record Resource(String id,String parent,String name,String mime,String marker){}
    public record Index(Map<String,Resource> resources){public Index{resources=Map.copyOf(resources);}}
    public LocalWorkspaceStorage(Path root){
        this.root=root.toAbsolutePath().normalize();
        this.repository=new ConfigurationRepository(this.root);
    }
    private Index index()throws IOException{return repository.load("index",Index.class).orElse(new Index(Map.of()));}
    private Resource resource(String id)throws IOException{
        var resource=index().resources().get(id);
        if(resource==null)throw new IOException("Unknown simulated resource");
        return resource;
    }
    private void parent(String id)throws IOException{
        if(id!=null&&!FOLDER.equals(resource(id).mime()))throw new IOException("Parent is not a folder");
    }
    private void remember(Resource resource)throws IOException{
        var next=new HashMap<>(index().resources());next.put(resource.id(),resource);
        repository.save("index",new Index(next));
    }
    private Path content(String id){return root.resolve("content").resolve(UUID.fromString(id).toString());}
    @Override public JsonNode userInfo(){return JSON.createObjectNode().put("sub","synthetic-user").put("email","applicant@example.invalid");}
    @Override public synchronized JsonNode file(String fileId)throws IOException{
        var item=resource(fileId);
        var result=JSON.createObjectNode().put("id",item.id()).put("name",item.name()).put("mimeType",item.mime()).put("trashed",false);
        result.putObject("capabilities").put("canEdit",true);
        result.putArray("owners").addObject().put("emailAddress", "applicant@example.invalid").put("me", true);
        if(item.parent()!=null)result.putArray("parents").add(item.parent());
        result.putObject("appProperties").put("jobsteinId",item.marker());
        return result;
    }
    @Override public synchronized Optional<String> findFolder(String parent,String name,String marker)throws IOException{
        parent(parent);
        var matches=index().resources().values().stream().filter(r->FOLDER.equals(r.mime())&&Objects.equals(parent,r.parent())&&Objects.equals(marker,r.marker())).toList();
        if(matches.size()>1)throw new IOException("Ambiguous simulated folder");
        return matches.isEmpty()?Optional.empty():Optional.of(matches.getFirst().id());
    }
    @Override public synchronized String ensureFolder(String parent,String name,String marker)throws IOException{
        var existing=findFolder(parent,name,marker);
        return existing.isPresent()?existing.get():ensureFolderWithId(parent,name,marker,allocateFileId());
    }
    @Override public synchronized String ensureFolderWithId(String parent,String name,String marker,String id)throws IOException{
        parent(parent);
        if(index().resources().containsKey(id)){validateFolder(id,parent,name,marker);return id;}
        remember(new Resource(id,parent,name,FOLDER,marker));return id;
    }
    @Override public synchronized void validateFolder(String id,String parent,String name,String marker)throws IOException{
        var actual=resource(id);
        if(!FOLDER.equals(actual.mime())||!Objects.equals(parent,actual.parent())||!name.equals(actual.name())||!marker.equals(actual.marker()))throw new IOException("Unexpected local folder metadata");
    }
    @Override public String allocateFileId(){return UUID.randomUUID().toString();}
    @Override public synchronized String upload(String parent,String name,String mime,byte[] data)throws IOException{
        return ensureUpload(parent,name,mime,data,allocateFileId());
    }
    @Override public synchronized String ensureUpload(String parent,String name,String mime,byte[] data,String id)throws IOException{
        parent(parent);
        var existing=index().resources().get(id);
        if(existing!=null){
            if(!Objects.equals(parent,existing.parent())||!name.equals(existing.name())||!mime.equals(existing.mime()))
                throw new IOException("Allocated ID conflicts with unexpected resource");
            if(!Arrays.equals(data,Files.readAllBytes(content(id))))throw new IOException("Recovered local resource checksum mismatch");
            return id;
        }
        AtomicFiles.write(content(id),data);
        remember(new Resource(id,parent,name,mime,""));
        return id;
    }
    @Override public synchronized void updateJson(String fileId,JsonNode data)throws IOException{
        if(!"application/json".equals(resource(fileId).mime()))throw new IOException("Not a JSON resource");
        AtomicFiles.write(content(fileId),JSON.writeValueAsBytes(data));
    }
    @Override public synchronized byte[] readBytes(String id,int limit)throws IOException{
        resource(id);
        if(limit<1)throw new IllegalArgumentException("Invalid download limit");
        try(var input=Files.newInputStream(content(id))){
            byte[] bytes=input.readNBytes(limit+1);
            if(bytes.length>limit)throw new IOException("Download exceeds size limit");
            return bytes;
        }
    }
    @Override public synchronized JsonNode readJson(String fileId)throws IOException{
        if(!"application/json".equals(resource(fileId).mime()))throw new IOException("Not a JSON resource");
        return JSON.readTree(Files.readAllBytes(content(fileId)));
    }
}
