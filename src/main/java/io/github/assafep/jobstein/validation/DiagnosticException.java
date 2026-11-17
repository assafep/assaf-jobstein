package io.github.assafep.jobstein.validation;

import java.io.IOException;
import java.util.Objects;

/** Only fixed, reviewed messages may cross the diagnostic output boundary. */
public final class DiagnosticException extends IOException {
    public enum Code {
        ACCOUNT_MISMATCH("Google account differs from the initialized user. Authorize the original account."),
        IDENTITY_UNAVAILABLE("Google identity could not be verified. Check authorization and retry."),
        PROFILE_INDEX_DIVERGED("Local and remote profile indexes differ. Preserve both copies and investigate before retrying."),
        DESCRIPTOR_INVALID("Descriptor structure or source evidence is invalid. Review the resume and regenerate the descriptor."),
        DESCRIPTOR_MISSING("No descriptor exists. Run profile descriptor regenerate."),
        RESUME_MISMATCH("Resume fingerprint differs from the expected version. Supply the correct resume."),
        APPROVAL_REQUIRED("Explicit descriptor approval is required. Review the descriptor before activation."),
        AI_CONSENT_REQUIRED("Resume processing requires explicit consent. Review the provider before authorizing AI processing."),
        AI_CREDENTIAL_REQUIRED("AI credentials are missing. Configure the integration with a valid secret reference."),
        AI_AUTH_FAILED("AI provider rejected authentication or access. Verify the configured credentials and permissions."),
        AI_RATE_LIMIT("AI provider rate limit or quota reached. Check usage and retry later."),
        AI_PROVIDER_UNAVAILABLE("AI provider is temporarily unavailable. Retry later."),
        AI_REQUEST_REJECTED("AI provider rejected the request. Check the model and provider options."),
        AI_RESPONSE_INVALID("AI response was missing or malformed. Retry generation and review provider configuration."),
        GOOGLE_AUTH_FAILED("Google authorization was rejected. Run google authorize with the initialized account."),
        GOOGLE_PERMISSION_DENIED("Google Workspace denied access. Check scopes, API enablement and resource permissions."),
        GOOGLE_RATE_LIMIT("Google Workspace quota or rate limit reached. Retry later."),
        GOOGLE_UNAVAILABLE("Google Workspace is temporarily unavailable. Retry later."),
        GOOGLE_RESOURCE_MISSING("Google Workspace resource was not found. Inspect resource provisioning and permissions."),
        GOOGLE_REQUEST_REJECTED("Google Workspace rejected the request. Check the environment and resource state."),
        GCP_APIS_MISSING("Required Google APIs are disabled. Run google configure."),
        GCP_COMMAND_FAILED("Google Cloud CLI operation failed. Check gcloud installation, authentication and project permissions."),
        GCP_TIMEOUT("Google Cloud CLI operation timed out. Check connectivity and retry."),
        SIMULATED_FAILURE("A sandbox checkpoint failed intentionally. Rerun onboard without failure injection.");
        private final String guidance;
        Code(String guidance){this.guidance=guidance;}
        public String guidance(){return guidance;}
    }
    private final Code code;
    public DiagnosticException(Code code){super(Objects.requireNonNull(code).guidance());this.code=code;}
    public DiagnosticException(Code code,Throwable cause){super(Objects.requireNonNull(code).guidance(),cause);this.code=code;}
    public Code code(){return code;}
}
