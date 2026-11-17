package io.github.assafep.jobstein.environment;

import java.io.IOException;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Exclusive, process-scoped lock for one local environment. Never delete the lock file. */
public final class EnvironmentLock implements AutoCloseable {
    public static final class BusyException extends IOException {
        public BusyException(){super("Environment is busy: another Jobstein command holds its lock");}
    }
    private static final Set<Path> HELD=ConcurrentHashMap.newKeySet();
    private final Path path;
    private final FileChannel channel;
    private final FileLock lock;
    private boolean closed;
    private EnvironmentLock(Path path,FileChannel channel,FileLock lock){
        this.path=path;this.channel=channel;this.lock=lock;
    }
    public static EnvironmentLock acquire(Path directory)throws IOException{
        io.github.assafep.jobstein.security.PrivateState.directory(directory);
        Path path=directory.toRealPath().resolve(".execution.lock");
        // On POSIX, closing a second channel to the same file can release process locks.
        // Reserve in the JVM before opening another channel.
        if(!HELD.add(path))throw new BusyException();
        FileChannel channel=null;
        try{
            channel=FileChannel.open(path,StandardOpenOption.CREATE,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS);
            FileLock lock;
            try{lock=channel.tryLock();}catch(OverlappingFileLockException e){throw new BusyException();}
            if(lock==null)throw new BusyException();
            return new EnvironmentLock(path,channel,lock);
        }catch(IOException|RuntimeException e){
            try{if(channel!=null)channel.close();}catch(IOException close){e.addSuppressed(close);}
            finally{HELD.remove(path);}
            throw e;
        }
    }
    @Override public synchronized void close()throws IOException{
        if(closed)return;
        closed=true;
        try{try{lock.release();}finally{channel.close();}}
        finally{HELD.remove(path);}
    }
}
