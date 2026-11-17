package io.github.assafep.jobstein.testing;
import java.net.URI;

/** Restricts package-private HTTP test seams to an explicit IPv4 loopback origin. */
public final class LoopbackOrigin {
    private LoopbackOrigin(){}
    public static URI validate(URI uri){
        if(uri==null||!"http".equals(uri.getScheme())||!"127.0.0.1".equals(uri.getHost())||
            uri.getPort()<1||uri.getPort()>65535||uri.getRawUserInfo()!=null||uri.getRawQuery()!=null||
            uri.getRawFragment()!=null||!(uri.getRawPath().isEmpty()||"/".equals(uri.getRawPath())))
            throw new IllegalArgumentException("Only a loopback test origin is permitted");
        return uri;
    }
}
