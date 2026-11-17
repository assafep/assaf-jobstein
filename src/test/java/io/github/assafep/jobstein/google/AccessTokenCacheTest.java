package io.github.assafep.jobstein.google;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.io.IOException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class AccessTokenCacheTest {
    private static final class MutableClock extends Clock {
        Instant now=Instant.parse("2026-01-01T00:00:00Z");
        public ZoneId getZone(){return ZoneOffset.UTC;}
        public Clock withZone(ZoneId zone){return this;}
        public Instant instant(){return now;}
    }
    @Test void refreshesOnlyWhenNecessaryAndInvalidatesMatchingToken()throws Exception{
        var clock=new MutableClock();
        var cache=new AccessTokenCache(clock,Duration.ofSeconds(60));
        var count=new AtomicInteger();
        AccessTokenCache.Refresh refresh=()->new AccessTokenCache.Grant("synthetic-"+count.incrementAndGet(),Duration.ofHours(1));
        assertEquals("synthetic-1",cache.get(refresh));
        clock.now=clock.now.plusSeconds(3539);
        assertEquals("synthetic-1",cache.get(refresh));
        clock.now=clock.now.plusSeconds(1);
        assertEquals("synthetic-2",cache.get(refresh));
        cache.invalidate("synthetic-1");
        assertEquals("synthetic-2",cache.get(refresh));
        cache.invalidate("synthetic-2");
        assertEquals("synthetic-3",cache.get(refresh));
        cache.clear();
        assertThrows(IOException.class,()->cache.get(()->{throw new IOException("synthetic failure");}));
        assertEquals("synthetic-4",cache.get(refresh));
    }
    @Test void concurrentCallersShareRefresh()throws Exception{
        var cache=new AccessTokenCache(Clock.systemUTC(),Duration.ofSeconds(60));
        var count=new AtomicInteger();
        try(var pool=Executors.newFixedThreadPool(8)){
            var tasks=new java.util.ArrayList<Future<String>>();
            for(int i=0;i<20;i++)tasks.add(pool.submit(()->cache.get(()->new AccessTokenCache.Grant("synthetic-"+count.incrementAndGet(),Duration.ofHours(1)))));
            for(var result:tasks)assertEquals("synthetic-1",result.get(5,TimeUnit.SECONDS));
        }
        assertEquals(1,count.get());
    }
}
