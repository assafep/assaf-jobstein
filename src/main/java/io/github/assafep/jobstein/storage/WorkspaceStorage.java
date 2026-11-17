package io.github.assafep.jobstein.storage;

import com.fasterxml.jackson.databind.JsonNode;

/** Storage and identity operations required by user/profile provisioning. */
public interface WorkspaceStorage {
    JsonNode userInfo() throws Exception;
    JsonNode file(String fileId) throws Exception;
    String ensureFolder(String parent, String name, String marker) throws Exception;
    java.util.Optional<String> findFolder(String parent,String name,String marker)throws Exception;
    String ensureFolderWithId(String parent,String name,String marker,String allocatedId)throws Exception;
    void validateFolder(String folderId,String parent,String name,String marker)throws Exception;
    String upload(String parent, String name, String mime, byte[] data) throws Exception;
    String allocateFileId() throws Exception;
    String ensureUpload(String parent,String name,String mime,byte[] data,String allocatedId) throws Exception;
    void updateJson(String fileId, JsonNode data) throws Exception;
    JsonNode readJson(String fileId) throws Exception;
}
