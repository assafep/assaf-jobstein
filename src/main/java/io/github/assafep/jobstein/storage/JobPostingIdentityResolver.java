package io.github.assafep.jobstein.storage;
import java.util.Locale;

public final class JobPostingIdentityResolver {
    public String canonical(String source,String tenant,String postingId){
        if(source==null||tenant==null||postingId==null||!source.matches("[a-zA-Z0-9-]+")
            ||!tenant.matches("[a-zA-Z0-9._-]+")||!postingId.matches("[a-zA-Z0-9._-]+"))
            throw new IllegalArgumentException("Stable source, tenant and posting ID required");
        return source.toLowerCase(Locale.ROOT)+":"+tenant.toLowerCase(Locale.ROOT)+":"+postingId;
    }
}
