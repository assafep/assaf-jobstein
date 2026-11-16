package io.github.assafep.jobstein.environment;

import java.io.IOException;
import java.nio.channels.*;
import java.nio.file.*;

/** Exclusive, process-scoped lock for one local environment. Never delete the lock file. */
public final class EnvironmentLock implements AutoCloseable {
    public static final class BusyException extends IOException {
        public BusyException(){super("Environment is busy: another Jobstein command holds its lock");}
    }
    private final FileChannel channel;
    private final FileLock lock;
    private EnvironmentLock(FileChannel channel,FileLock lock){this.channel=channel;this.lock=lock;}
    public static EnvironmentLock acquire(Path directory)throws IOException{
        Files.createDirectories(directory);
        Path path=directory.resolve(".execution.lock");
        var channel=FileChannel.open(path,StandardOpenOption.CREATE,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS);
        try{
            FileLock lock;
            try{lock=channel.tryLock();}catch(OverlappingFileLockException e){throw new BusyException();}
            if(lock==null)throw new BusyException();
            return new EnvironmentLock(channel,lock);
        }catch(IOException|RuntimeException e){
            try{channel.close();}catch(IOException close){e.addSuppressed(close);}
            throw e;
        }
    }
    @Override public void close()throws IOException{
        try{lock.release();}finally{channel.close();}
    }
}
