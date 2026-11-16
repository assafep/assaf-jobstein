package io.github.assafep.jobstein.google;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

public final class WorkspaceClient {
    private static final ObjectMapper JSON=new ObjectMapper();
    private final GoogleOAuth oauth;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
    public WorkspaceClient(GoogleOAuth oauth) { this.oauth=oauth; }
    private JsonNode request(String method,String url,byte[] body,String type) throws IOException,InterruptedException {
        var builder=HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60)).header("Authorization","Bearer "+oauth.accessToken());
        if(body!=null) builder.header("Content-Type",type);
        var response=http.send(builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofByteArray(body)).build(),HttpResponse.BodyHandlers.ofString());
        if(response.statusCode()<200 || response.statusCode()>=300) throw new IOException("Google Workspace request failed (HTTP "+response.statusCode()+")");
        return response.body().isBlank()?JSON.createObjectNode():JSON.readTree(response.body());
    }
    private static String q(String s){return URLEncoder.encode(s,StandardCharsets.UTF_8);}
    private static String id(String s){if(s==null||!s.matches("[a-zA-Z0-9_-]+"))throw new IllegalArgumentException("Invalid resource ID");return s;}
    public JsonNode userInfo() throws Exception {return request("GET","https://openidconnect.googleapis.com/v1/userinfo",null,null);}
    public void validate() throws Exception {
        userInfo();
        request("GET","https://www.googleapis.com/drive/v3/files?pageSize=1&fields=files(id)",null,null);
        request("GET","https://gmail.googleapis.com/gmail/v1/users/me/messages?maxResults=1",null,null);
        request("GET","https://www.googleapis.com/calendar/v3/calendars/primary/events?maxResults=1",null,null);
    }
    public JsonNode file(String fileId) throws Exception {return request("GET","https://www.googleapis.com/drive/v3/files/"+id(fileId)+"?fields=id,name,mimeType,parents,trashed,capabilities(canEdit)",null,null);}
    public String ensureFolder(String parent,String name,String marker) throws Exception {
        String escaped=marker.replace("\\","\\\\").replace("'","\\'");
        String query="trashed = false and mimeType = 'application/vnd.google-apps.folder' and '"+(parent==null?"root":id(parent))+"' in parents and appProperties has { key='jobsteinId' and value='"+escaped+"' }";
        var found=request("GET","https://www.googleapis.com/drive/v3/files?q="+q(query)+"&fields=nextPageToken,files(id)&pageSize=100",null,null);
        var list=found.path("files");
        if(list.size()>1 || found.has("nextPageToken")) throw new IOException("Ambiguous Jobstein folder: "+name);
        if(list.size()==1)return list.get(0).path("id").asText();
        ObjectNode metadata=JSON.createObjectNode().put("name",name).put("mimeType","application/vnd.google-apps.folder");
        metadata.putArray("parents").add(parent==null?"root":parent);
        metadata.putObject("appProperties").put("jobsteinId",marker);
        return request("POST","https://www.googleapis.com/drive/v3/files?fields=id",JSON.writeValueAsBytes(metadata),"application/json").path("id").asText();
    }
    public String upload(String parent,String name,String mime,byte[] data) throws Exception {
        String boundary="jobstein_"+UUID.randomUUID();
        var metadata=JSON.createObjectNode().put("name",name).put("mimeType",mime);
        metadata.putArray("parents").add(id(parent));
        var bytes=new ByteArrayOutputStream();
        bytes.write(("--"+boundary+"\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n"+JSON.writeValueAsString(metadata)+"\r\n--"+boundary+"\r\nContent-Type: "+mime+"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        bytes.write(data);bytes.write(("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8));
        return request("POST","https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id",bytes.toByteArray(),"multipart/related; boundary="+boundary).path("id").asText();
    }
    public void updateJson(String fileId,JsonNode data) throws Exception {
        request("PATCH","https://www.googleapis.com/upload/drive/v3/files/"+id(fileId)+"?uploadType=media",JSON.writeValueAsBytes(data),"application/json");
    }
    public JsonNode readJson(String fileId) throws Exception {return request("GET","https://www.googleapis.com/drive/v3/files/"+id(fileId)+"?alt=media",null,null);}
    public JsonNode document(String documentId) throws Exception {return request("GET","https://docs.googleapis.com/v1/documents/"+id(documentId),null,null);}
    public JsonNode updateDocument(String documentId,JsonNode requests) throws Exception {return request("POST","https://docs.googleapis.com/v1/documents/"+id(documentId)+":batchUpdate",JSON.writeValueAsBytes(requests),"application/json");}
    public JsonNode listMessages(String query) throws Exception {return request("GET","https://gmail.googleapis.com/gmail/v1/users/me/messages?q="+q(query),null,null);}
    public JsonNode message(String messageId) throws Exception {return request("GET","https://gmail.googleapis.com/gmail/v1/users/me/messages/"+id(messageId),null,null);}
    public JsonNode createDraft(String base64UrlMime) throws Exception {var body=JSON.createObjectNode();body.putObject("message").put("raw",base64UrlMime);return request("POST","https://gmail.googleapis.com/gmail/v1/users/me/drafts",JSON.writeValueAsBytes(body),"application/json");}
    public JsonNode sendDraft(String draftId) throws Exception {return request("POST","https://gmail.googleapis.com/gmail/v1/users/me/drafts/send",JSON.writeValueAsBytes(JSON.createObjectNode().put("id",id(draftId))),"application/json");}
    public JsonNode calendarEvents() throws Exception {return request("GET","https://www.googleapis.com/calendar/v3/calendars/primary/events",null,null);}
}
