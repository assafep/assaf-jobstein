package io.github.assafep.jobstein.google;

import java.io.IOException;
import java.util.Optional;
import io.github.assafep.jobstein.validation.DiagnosticException;

public final class GoogleAccountBinding {
    private GoogleAccountBinding(){}
    public static void verify(Optional<String> expected,String actual)throws IOException{
        if(actual==null||actual.isBlank())throw new DiagnosticException(DiagnosticException.Code.IDENTITY_UNAVAILABLE);
        if(expected.isPresent()&&!expected.get().equals(actual))throw new DiagnosticException(DiagnosticException.Code.ACCOUNT_MISMATCH);
    }
}
