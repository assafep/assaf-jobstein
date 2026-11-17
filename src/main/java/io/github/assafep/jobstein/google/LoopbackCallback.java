package io.github.assafep.jobstein.google;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Desktop OAuth callback parser; does not retain or log authorization codes. */
final class LoopbackCallback {
    private LoopbackCallback(){}
    record Response(String code,boolean denied){
        @Override public String toString(){return "Response[redacted]";}
    }
    static InetSocketAddress address(int port){
        if(port<0||port>65535)throw new IllegalArgumentException("Invalid callback port");
        return new InetSocketAddress("127.0.0.1",port);
    }
    static Optional<Response> parse(String method,URI uri,String state){
        if(!"GET".equals(method)||!"/callback".equals(uri.getRawPath())||uri.getRawQuery()==null)return Optional.empty();
        try{
            var parameters=new HashMap<String,String>();
            for(String pair:uri.getRawQuery().split("&")){
                String[] parts=pair.split("=",2);
                if(parts.length!=2)return Optional.empty();
                String key=URLDecoder.decode(parts[0],StandardCharsets.UTF_8);
                String value=URLDecoder.decode(parts[1],StandardCharsets.UTF_8);
                if(parameters.putIfAbsent(key,value)!=null)return Optional.empty();
            }
            String actual=parameters.getOrDefault("state","");
            if(state==null||state.isBlank()||!MessageDigest.isEqual(state.getBytes(StandardCharsets.UTF_8),actual.getBytes(StandardCharsets.UTF_8)))return Optional.empty();
            boolean hasError=parameters.containsKey("error"),hasCode=parameters.containsKey("code");
            if(hasError==hasCode)return Optional.empty();
            if(hasError)return Optional.of(new Response("",true));
            String code=parameters.get("code");
            return code.isBlank()?Optional.empty():Optional.of(new Response(code,false));
        }catch(IllegalArgumentException malformed){return Optional.empty();}
    }
}
