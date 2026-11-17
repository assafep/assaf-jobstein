package io.github.assafep.jobstein.google;

import java.io.IOException;
import java.util.Optional;

public final class GoogleAccountBinding {
    private GoogleAccountBinding(){}
    public static void verify(Optional<String> expected,String actual)throws IOException{
        if(actual==null||actual.isBlank())throw new IOException("Google account identity unavailable");
        if(expected.isPresent()&&!expected.get().equals(actual))throw new IOException("Google account differs from initialized user");
    }
}
