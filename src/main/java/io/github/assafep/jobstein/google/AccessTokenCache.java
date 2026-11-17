package io.github.assafep.jobstein.google;

import java.io.IOException;
import java.time.*;
import java.util.Objects;
import java.util.Optional;

/** In-memory, per-OAuth-instance cache. Never serializes access tokens. */
public final class AccessTokenCache {
    public record Grant(String token,Duration lifetime){
        public Grant{
            if(token==null||token.isBlank()||lifetime==null||lifetime.isZero()||lifetime.isNegative())
                throw new IllegalArgumentException("Invalid access token grant");
        }
        @Override public String toString(){return "Grant[redacted]";}
    }
    @FunctionalInterface public interface Refresh {Grant run()throws IOException,InterruptedException;}
    private record Cached(String token,Instant refreshAt){}
    private final Clock clock;
    private final Duration margin;
    private Optional<Cached> cached=Optional.empty();
    public AccessTokenCache(Clock clock,Duration margin){
        this.clock=Objects.requireNonNull(clock);this.margin=Objects.requireNonNull(margin);
        if(margin.isNegative())throw new IllegalArgumentException("Negative refresh margin");
    }
    public synchronized String get(Refresh refresh)throws IOException,InterruptedException{
        Instant started=clock.instant();
        if(cached.isPresent()&&started.isBefore(cached.get().refreshAt()))return cached.get().token();
        cached=Optional.empty();
        Grant grant=refresh.run();
        // Include elapsed network time in the conservative expiration estimate.
        Duration advance=margin.compareTo(grant.lifetime().dividedBy(2))<0?margin:grant.lifetime().dividedBy(2);
        Instant expiry=started.plus(grant.lifetime());
        if(!clock.instant().isBefore(expiry))throw new IOException("Access token expired during refresh");
        cached=Optional.of(new Cached(grant.token(),expiry.minus(advance)));
        return grant.token();
    }
    public synchronized void invalidate(String rejected){
        if(cached.filter(value->value.token().equals(rejected)).isPresent())cached=Optional.empty();
    }
    public synchronized void clear(){cached=Optional.empty();}
}
