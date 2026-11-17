package io.github.assafep.jobstein.testing;
import com.sun.net.httpserver.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

public final class MockHttp implements AutoCloseable {
    private final HttpServer server;
    private final AtomicReference<Throwable> failure=new AtomicReference<>();
    public MockHttp(HttpHandler handler)throws IOException{
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",exchange->{
            try{handler.handle(exchange);}
            catch(Throwable error){failure.compareAndSet(null,error);try{reply(exchange,500,"{}");}catch(IOException ignored){}}
            finally{exchange.close();}
        });
        server.start();
    }
    public URI origin(){return URI.create("http://127.0.0.1:"+server.getAddress().getPort());}
    public static void reply(HttpExchange exchange,int status,String body)throws IOException{
        byte[] bytes=body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type","application/json");
        exchange.sendResponseHeaders(status,bytes.length);
        exchange.getResponseBody().write(bytes);
    }
    public static String body(HttpExchange exchange)throws IOException{return new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);}
    @Override public void close(){server.stop(0);if(failure.get()!=null)throw new AssertionError("Mock server assertion failed",failure.get());}
}
