package io.github.assafep.jobstein.google;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

public final class WorkspaceClient implements io.github.assafep.jobstein.storage.WorkspaceStorage {
    private static final ObjectMapper JSON=new ObjectMapper();
    private static final int MAX_JSON_BYTES=5_000_000;
    private final GoogleOAuth oauth;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
    private final Optional<URI> testOrigin;
    public WorkspaceClient(GoogleOAuth oauth) { this.oauth=oauth; this.testOrigin=Optional.empty(); }
    WorkspaceClient(GoogleOAuth oauth,URI testOrigin){this.oauth=oauth;this.testOrigin=Optional.of(io.github.assafep.jobstein.testing.LoopbackOrigin.validate(testOrigin));}
    private URI endpoint(String url){
        URI original=URI.create(url);
        return testOrigin.map(origin->origin.resolve(original.getRawPath()+(original.getRawQuery()==null?"":"?"+original.getRawQuery()))).orElse(original);
    }
    private JsonNode request(String method,String url,byte[] body,String type) throws IOException,InterruptedException {
        return request(method,url,body,type,true);
    }
    private JsonNode request(String method,String url,byte[] body,String type,boolean retryRead)throws IOException,InterruptedException{
        String token=oauth.accessToken();
        var builder=HttpRequest.newBuilder(endpoint(url)).timeout(Duration.ofSeconds(60)).header("Authorization","Bearer "+token);
        if(body!=null) builder.header("Content-Type",type);
        var response=http.send(builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofByteArray(body)).build(),HttpResponse.BodyHandlers.ofInputStream());
        if(response.statusCode()==401){
            response.body().close();
            oauth.invalidateAccessToken(token);
            if(retryRead&&method.equals("GET"))return request(method,url,body,type,false);
            throw new WorkspaceHttpException(401, operation(method, URI.create(url).getPath()));
        }
        byte[] bytes;
        try(var input=response.body()){
            bytes=input.readNBytes(MAX_JSON_BYTES+1);
        }
        if(bytes.length>MAX_JSON_BYTES)throw new IOException("Workspace JSON response exceeds size limit");
        if(response.statusCode()<200 || response.statusCode()>=300)
            throw new WorkspaceHttpException(response.statusCode(), operation(method, URI.create(url).getPath()));
        String responseBody=new String(bytes,StandardCharsets.UTF_8);
        return responseBody.isBlank()?JSON.createObjectNode():JSON.readTree(responseBody);
    }
    private static String q(String s){return URLEncoder.encode(s,StandardCharsets.UTF_8);}
    private static String id(String s){if(s==null||!s.matches("[a-zA-Z0-9_-]+"))throw new IllegalArgumentException("Invalid resource ID");return s;}
    // Keep request URLs and provider bodies out of diagnostics. These labels contain no user data.
    private static String operation(String method, String path) {
        if (path.equals("/drive/v3/files/generateIds")) return "Allocate a Drive file ID";
        if (path.equals("/drive/v3/files"))
            return method.equals("POST") ? "Create a Drive folder" : "List Drive files or folders";
        if (path.startsWith("/drive/v3/files/")) return "Read a Drive file or folder";
        if (path.equals("/upload/drive/v3/files")) return "Upload a Drive file";
        if (path.startsWith("/upload/drive/v3/files/")) return "Update a Drive file";
        if (path.equals("/v1/userinfo")) return "Read Google account identity";
        return "Call Google Workspace";
    }
    public static final class WorkspaceHttpException extends IOException {
        private final int status;
        private final String operation;
        public int status(){return status;}
        public String operation(){return operation;}
        private WorkspaceHttpException(int status, String operation){
            super("Google Workspace request failed (HTTP "+status+")");
            this.status=status;
            this.operation=operation;
        }
    }
    public String ensureUpload(String parent,String name,String mime,byte[] data,String allocatedId)throws Exception{
        try{return uploadWithId(parent,name,mime,data,allocatedId);}
        catch(WorkspaceHttpException e){
            if(e.status!=409)throw e;
            var existing=file(allocatedId);
            if(!allocatedId.equals(existing.path("id").asText())||!existing.path("trashed").isBoolean()||existing.path("trashed").asBoolean()||!name.equals(existing.path("name").asText())||
                !mime.equals(existing.path("mimeType").asText())||!existing.path("capabilities").path("canEdit").asBoolean()||
                !java.util.stream.StreamSupport.stream(existing.path("parents").spliterator(),false).anyMatch(v->parent.equals(v.asText())))throw new IOException("Allocated Drive ID conflicts with unexpected resource");
            if(!io.github.assafep.jobstein.storage.ResumeDocument.sha256(data).equalsIgnoreCase(existing.path("sha256Checksum").asText()))
                throw new IOException("Recovered Drive resource checksum mismatch");
            return allocatedId;
        }
    }
    public JsonNode userInfo() throws Exception {return request("GET","https://openidconnect.googleapis.com/v1/userinfo",null,null);}
    public void validate() throws Exception {
        userInfo();
        request("GET","https://www.googleapis.com/drive/v3/files?pageSize=1&fields=files(id)",null,null);
        request("GET","https://gmail.googleapis.com/gmail/v1/users/me/messages?maxResults=1",null,null);
        request("GET","https://www.googleapis.com/calendar/v3/calendars/primary/events?maxResults=1",null,null);
    }
    public JsonNode file(String fileId) throws Exception {return request("GET","https://www.googleapis.com/drive/v3/files/"+id(fileId)+"?fields=id,name,mimeType,parents,trashed,appProperties,sha256Checksum,capabilities(canEdit),owners(emailAddress,me)",null,null);}
    public boolean hasActiveNamedResource(String parent,String name)throws Exception{
        String escaped=name.replace("\\","\\\\").replace("'","\\'");
        String query="trashed = false and '"+id(parent)+"' in parents and name = '"+escaped+"'";
        var found=request("GET","https://www.googleapis.com/drive/v3/files?q="+q(query)+"&fields=nextPageToken,files(id)&pageSize=1",null,null);
        var files=found.path("files");
        if(!files.isArray()||files.size()>1)throw new IOException("Invalid Drive resource discovery response");
        return files.size()>0||found.hasNonNull("nextPageToken");
    }
    public Optional<String> findFolder(String parent,String name,String marker) throws Exception {
        String escaped=marker.replace("\\","\\\\").replace("'","\\'");
        String query="trashed = false and mimeType = 'application/vnd.google-apps.folder' and '"+(parent==null?"root":id(parent))+"' in parents and appProperties has { key='jobsteinId' and value='"+escaped+"' }";
        var found=request("GET","https://www.googleapis.com/drive/v3/files?q="+q(query)+"&fields=nextPageToken,files(id)&pageSize=100",null,null);
        var list=found.path("files");
        if(!list.isArray()||list.size()>1 || found.has("nextPageToken")) throw new IOException("Ambiguous Jobstein folder: "+name);
        return list.size()==1?Optional.of(id(list.get(0).path("id").asText())):Optional.empty();
    }
    public String ensureFolder(String parent,String name,String marker)throws Exception{
        var existing=findFolder(parent,name,marker);
        if(existing.isPresent()){validateFolder(existing.get(),parent,name,marker);return existing.get();}
        String allocated=allocateFileId();
        String created=ensureFolderWithId(parent,name,marker,allocated);
        validateFolder(created,parent,name,marker);return created;
    }
    public String ensureFolderWithId(String parent,String name,String marker,String allocatedId)throws Exception{
        ObjectNode metadata=JSON.createObjectNode().put("name",name).put("mimeType","application/vnd.google-apps.folder");
        // Omitting parents creates the folder directly in My Drive.
        if(parent!=null) metadata.putArray("parents").add(id(parent));
        metadata.putObject("appProperties").put("jobsteinId",marker);
        metadata.put("id",id(allocatedId));
        try{
            String actual=request("POST","https://www.googleapis.com/drive/v3/files?fields=id",JSON.writeValueAsBytes(metadata),"application/json").path("id").asText();
            if(!allocatedId.equals(actual))throw new IOException("Unexpected allocated folder ID");
            return actual;
        }
        catch(WorkspaceHttpException e){
            if(e.status!=409)throw e;
            validateFolder(allocatedId,parent,name,marker);
            return allocatedId;
        }
    }
    public void validateFolder(String folderId,String parent,String name,String marker)throws Exception{
        var actual=file(folderId);
        if(!folderId.equals(actual.path("id").asText())||!actual.path("trashed").isBoolean()||actual.path("trashed").asBoolean()||!actual.path("capabilities").path("canEdit").asBoolean()||
            !"application/vnd.google-apps.folder".equals(actual.path("mimeType").asText())||
            !name.equals(actual.path("name").asText())||!marker.equals(actual.path("appProperties").path("jobsteinId").asText()))
            throw new IOException("Unexpected Drive folder metadata");
        // Verify root membership through a search for the app's folder under the root alias.
        // Reading metadata for My Drive itself requires access to a folder the app did not create.
        var parents = actual.path("parents");
        if (!parents.isArray() || parents.size() != 1 || !parents.get(0).isTextual() ||
                (parent == null ? findFolder(null,name,marker).filter(folderId::equals).isEmpty()
                    : !id(parent).equals(parents.get(0).asText())))
            throw new IOException("Unexpected Drive folder parent");
    }
    public String allocateFileId() throws Exception {
        return id(request("GET","https://www.googleapis.com/drive/v3/files/generateIds?count=1&space=drive",null,null).path("ids").path(0).asText());
    }
    public String upload(String parent,String name,String mime,byte[] data) throws Exception {
        return uploadWithId(parent,name,mime,data,null);
    }
    public String uploadWithId(String parent,String name,String mime,byte[] data,String allocatedId) throws Exception {
        String boundary="jobstein_"+UUID.randomUUID();
        var metadata=JSON.createObjectNode().put("name",name).put("mimeType",mime);
        metadata.putArray("parents").add(id(parent));
        if(allocatedId!=null)metadata.put("id",id(allocatedId));
        var bytes=new ByteArrayOutputStream();
        bytes.write(("--"+boundary+"\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n"+JSON.writeValueAsString(metadata)+"\r\n--"+boundary+"\r\nContent-Type: "+mime+"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        bytes.write(data);bytes.write(("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8));
        return request("POST","https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id",bytes.toByteArray(),"multipart/related; boundary="+boundary).path("id").asText();
    }
    public void updateJson(String fileId,JsonNode data) throws Exception {
        request("PATCH","https://www.googleapis.com/upload/drive/v3/files/"+id(fileId)+"?uploadType=media",JSON.writeValueAsBytes(data),"application/json");
    }
    public byte[] readBytes(String fileId,int limit)throws Exception{
        return readBytes(fileId,limit,true);
    }
    private byte[] readBytes(String fileId,int limit,boolean retry)throws Exception{
        if(limit<1||limit==Integer.MAX_VALUE)throw new IllegalArgumentException("Invalid download limit");
        String token=oauth.accessToken();
        var request=HttpRequest.newBuilder(endpoint("https://www.googleapis.com/drive/v3/files/"+id(fileId)+"?alt=media"))
            .timeout(Duration.ofSeconds(60)).header("Authorization","Bearer "+token).GET().build();
        var response=http.send(request,HttpResponse.BodyHandlers.ofInputStream());
        if(response.statusCode()==401){
            oauth.invalidateAccessToken(token);
            if(retry){response.body().close();return readBytes(fileId,limit,false);}
        }
        try(var input=response.body()){
            if(response.statusCode()<200||response.statusCode()>=300)
                throw new WorkspaceHttpException(response.statusCode(), "Download a Drive file");
            byte[] bytes=input.readNBytes(limit+1);
            if(bytes.length>limit)throw new IOException("Download exceeds size limit");
            return bytes;
        }
    }
    public JsonNode readJson(String fileId) throws Exception {return request("GET","https://www.googleapis.com/drive/v3/files/"+id(fileId)+"?alt=media",null,null);}
    public JsonNode document(String documentId) throws Exception {return request("GET","https://docs.googleapis.com/v1/documents/"+id(documentId),null,null);}
    public JsonNode updateDocument(String documentId,JsonNode requests) throws Exception {return request("POST","https://docs.googleapis.com/v1/documents/"+id(documentId)+":batchUpdate",JSON.writeValueAsBytes(requests),"application/json");}
    public JsonNode listMessages(String query) throws Exception {return request("GET","https://gmail.googleapis.com/gmail/v1/users/me/messages?q="+q(query)+"&maxResults=100",null,null);}
    public JsonNode message(String messageId) throws Exception {return request("GET","https://gmail.googleapis.com/gmail/v1/users/me/messages/"+id(messageId),null,null);}
    public JsonNode createDraft(String base64UrlMime) throws Exception {var body=JSON.createObjectNode();body.putObject("message").put("raw",base64UrlMime);return request("POST","https://gmail.googleapis.com/gmail/v1/users/me/drafts",JSON.writeValueAsBytes(body),"application/json");}
    public JsonNode sendDraft(String draftId) throws Exception {return request("POST","https://gmail.googleapis.com/gmail/v1/users/me/drafts/send",JSON.writeValueAsBytes(JSON.createObjectNode().put("id",id(draftId))),"application/json");}
    public JsonNode calendarEvents() throws Exception {return request("GET","https://www.googleapis.com/calendar/v3/calendars/primary/events?maxResults=100",null,null);}
}
