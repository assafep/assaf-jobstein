package io.github.assafep.jobstein.security;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.*;

/** Linux Secret Service adapter using secret-tool. Secret bytes travel only through pipes. */
public final class LinuxSecretServiceStore implements SecretStore {
    record Result(int exit,byte[] output){}
    @FunctionalInterface interface Runner {Result execute(List<String> arguments,byte[] input)throws IOException;}
    private final String namespace;
    private final Runner runner;
    public LinuxSecretServiceStore(Path directory){
        this(directory,LinuxSecretServiceStore::run);
    }
    LinuxSecretServiceStore(Path directory,Runner runner){
        this.namespace=namespace(directory);
        this.runner=Objects.requireNonNull(runner);
    }
    private static String namespace(Path directory){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(directory.toAbsolutePath().normalize().toString().getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    private List<String> attributes(String reference){
        if(reference==null||!reference.matches("secret://[a-zA-Z0-9][a-zA-Z0-9_-]{0,63}(/[a-zA-Z0-9][a-zA-Z0-9_-]{0,63})*"))
            throw new IllegalArgumentException("Invalid secret reference");
        return List.of("application","jobstein","environment",namespace,"reference",reference);
    }
    private Result execute(String operation,String reference,byte[] input)throws IOException{
        var args=new ArrayList<String>();
        args.add(operation);
        if(operation.equals("store"))args.add("--label=Jobstein credential");
        args.addAll(attributes(reference));
        return runner.execute(List.copyOf(args),input);
    }
    @Override public void put(String reference,byte[] value)throws IOException{
        Objects.requireNonNull(value);
        byte[] encoded=Base64.getEncoder().encode(value);
        try{
            requireSuccess("store",reference,encoded,"Native credential store write failed");
            var stored=get(reference).orElseThrow(()->new IOException("Native credential write not verified"));
            try{if(!Arrays.equals(value,stored))throw new IOException("Native credential write not verified");}
            finally{Arrays.fill(stored,(byte)0);}
        }finally{Arrays.fill(encoded,(byte)0);}
    }
    @Override public Optional<byte[]> get(String reference)throws IOException{
        var result=execute("lookup",reference,new byte[0]);
        try {
            // secret-tool uses exit 1 both for no match and some backend errors.
            // Fail closed instead of treating an unavailable keyring as an empty store.
            if(result.exit()!=0)throw new IOException("Native credential lookup failed or entry is missing");
            // Trim the command's ASCII whitespace without creating an immutable secret String.
            byte[] output=result.output();
            int start=0,end=output.length;
            while(start<end && isAsciiWhitespace(output[start]))start++;
            while(end>start && isAsciiWhitespace(output[end-1]))end--;
            byte[] encoded=Arrays.copyOfRange(output,start,end);
            try{return Optional.of(Base64.getDecoder().decode(encoded));}
            catch(IllegalArgumentException e){throw new IOException("Invalid native credential encoding");}
            finally{Arrays.fill(encoded,(byte)0);}
        }finally{Arrays.fill(result.output(),(byte)0);}
    }
    private static boolean isAsciiWhitespace(byte value){
        return value==' ' || (value>='\t' && value<='\r');
    }
    @Override public void remove(String reference)throws IOException{
        requireSuccess("clear",reference,new byte[0],"Native credential removal failed");
    }
    private void requireSuccess(String operation,String reference,byte[] input,String message)throws IOException{
        var result=execute(operation,reference,input);
        try{if(result.exit()!=0)throw new IOException(message);}
        finally{Arrays.fill(result.output(),(byte)0);}
    }
    private static Result run(List<String> arguments,byte[] input)throws IOException{
        var command=new ArrayList<String>();command.add("secret-tool");command.addAll(arguments);
        var process=new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        try(var executor=Executors.newVirtualThreadPerTaskExecutor()){
            var output=executor.submit(()->{try(var stream=process.getInputStream()){return stream.readNBytes(1_000_001);}});
            try{
                try(var stdin=process.getOutputStream()){stdin.write(input);}
                if(!process.waitFor(30,TimeUnit.SECONDS))throw new IOException("Native credential operation timed out");
                byte[] bytes=output.get(5,TimeUnit.SECONDS);
                if(bytes.length>1_000_000)throw new IOException("Native credential output exceeds limit");
                return new Result(process.exitValue(),bytes);
            }catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException("Native credential operation interrupted");}
            catch(ExecutionException|TimeoutException e){throw new IOException("Native credential operation failed");}
            finally{if(process.isAlive())process.destroyForcibly();output.cancel(true);}
        }
    }
}
