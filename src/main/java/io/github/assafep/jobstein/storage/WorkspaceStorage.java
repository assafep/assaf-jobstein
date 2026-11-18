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
    byte[] readBytes(String fileId,int limit)throws Exception;
    JsonNode readJson(String fileId) throws Exception;

    default void validateJsonResource(String fileId, String parent, String name) throws Exception {
        var metadata = file(fileId);
        if (!fileId.equals(metadata.path("id").asText())
            || !name.equals(metadata.path("name").asText())
            || !"application/json".equals(metadata.path("mimeType").asText())
            || !metadata.path("trashed").isBoolean() || metadata.path("trashed").asBoolean()
            || !metadata.path("capabilities").path("canEdit").asBoolean()
            || !java.util.stream.StreamSupport.stream(metadata.path("parents").spliterator(), false)
                .anyMatch(value -> parent.equals(value.asText()))) {
            throw new java.io.IOException("Unexpected Drive JSON resource metadata");
        }
        String email = userInfo().path("email").asText("");
        boolean ownedByCurrentUser = java.util.stream.StreamSupport
            .stream(metadata.path("owners").spliterator(), false)
            .anyMatch(owner -> owner.path("me").asBoolean()
                || (!email.isBlank() && email.equalsIgnoreCase(owner.path("emailAddress").asText(""))));
        if (!ownedByCurrentUser) throw new java.io.IOException("Drive JSON resource is not owned by the current user");
    }
}
