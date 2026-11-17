package io.github.assafep.jobstein.google;
import org.junit.jupiter.api.Test;
import java.net.URI;
import static org.junit.jupiter.api.Assertions.*;

class LoopbackCallbackTest {
    @Test void validatesMethodPathStateAndSingleOutcome(){
        assertEquals("example+code",LoopbackCallback.parse("GET",URI.create("/callback?state=expected&code=example%2Bcode"),"expected").orElseThrow().code());
        assertTrue(LoopbackCallback.parse("GET",URI.create("/callback?state=expected&error=access_denied"),"expected").orElseThrow().denied());
        for(String path:new String[]{"/callback?state=wrong&code=x","/callback?state=expected&state=expected&code=x","/callback?state=expected&code=x&error=denied","/callback/other?state=expected&code=x","/callback?state=expected&code="}){
            assertTrue(LoopbackCallback.parse("GET",URI.create(path),"expected").isEmpty());
        }
        assertTrue(LoopbackCallback.parse("POST",URI.create("/callback?state=expected&code=x"),"expected").isEmpty());
        assertFalse(LoopbackCallback.parse("GET",URI.create("/callback?state=expected&code=secret"),"expected").orElseThrow().toString().contains("secret"));
    }
    @Test void bindsOnlyToIpv4Loopback(){
        assertEquals("127.0.0.1",LoopbackCallback.address(8765).getAddress().getHostAddress());
        assertEquals(8765,LoopbackCallback.address(8765).getPort());
        assertThrows(IllegalArgumentException.class,()->LoopbackCallback.address(65536));
    }
}
