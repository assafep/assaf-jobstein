package io.github.assafep.jobstein.validation;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.NoSuchFileException;
import java.net.http.HttpTimeoutException;
import com.fasterxml.jackson.core.JsonProcessingException;

public final class Diagnostics {
    private Diagnostics(){}
    public static String describe(Exception error){
        if(error instanceof DiagnosticException known)return "["+known.code().name()+"] "+known.code().guidance();
        if(error instanceof io.github.assafep.jobstein.google.WorkspaceClient.WorkspaceHttpException http){var code=googleHttp(http.status());return "["+code.name()+"] "+code.guidance();}
        if(error instanceof AccessDeniedException)return "[LOCAL_ACCESS_DENIED] Check ownership and permissions of local Jobstein state.";
        if(error instanceof NoSuchFileException)return "[LOCAL_FILE_MISSING] Check required local files and environment initialization.";
        if(error instanceof HttpTimeoutException)return "[NETWORK_TIMEOUT] Check connectivity and retry.";
        if(error instanceof JsonProcessingException)return "[INVALID_DATA] Configuration or provider data could not be parsed. Review the relevant settings.";
        if(error instanceof IllegalArgumentException)return "[INVALID_INPUT] Check command arguments and configuration. Use --help.";
        if(error instanceof IOException)return "[IO_FAILURE] Check required files, configuration, permissions and connectivity.";
        return "[UNEXPECTED_FAILURE] Operation failed. Review configuration and report a reproducible issue without personal data.";
    }
    public static DiagnosticException.Code aiHttp(int status){
        if(status==401||status==403)return DiagnosticException.Code.AI_AUTH_FAILED;
        if(status==429)return DiagnosticException.Code.AI_RATE_LIMIT;
        if(status>=500)return DiagnosticException.Code.AI_PROVIDER_UNAVAILABLE;
        return DiagnosticException.Code.AI_REQUEST_REJECTED;
    }
    public static DiagnosticException.Code googleHttp(int status){
        if(status==401)return DiagnosticException.Code.GOOGLE_AUTH_FAILED;
        if(status==403)return DiagnosticException.Code.GOOGLE_PERMISSION_DENIED;
        if(status==404)return DiagnosticException.Code.GOOGLE_RESOURCE_MISSING;
        if(status==429)return DiagnosticException.Code.GOOGLE_RATE_LIMIT;
        if(status>=500)return DiagnosticException.Code.GOOGLE_UNAVAILABLE;
        return DiagnosticException.Code.GOOGLE_REQUEST_REJECTED;
    }
}
