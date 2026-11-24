package dev.workshopvideo.core;

import in.dragonbra.javasteam.networking.steam3.*;
import okhttp3.*;
import okio.ByteString;
import java.net.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Use the same Android-compatible HTTP stack for Steam HTTPS and WebSocket. */
public final class SteamWebSocket extends in.dragonbra.javasteam.networking.steam3.Connection {
    private final OkHttpClient http;
    private final AtomicBoolean ended=new AtomicBoolean();
    private volatile WebSocket socket;
    private InetSocketAddress endpoint;
    public SteamWebSocket(OkHttpClient http){this.http=http;}
    @Override public void connect(InetSocketAddress endpoint,int timeout){
        this.endpoint=endpoint;
        Request request=new Request.Builder().url("https://"+endpoint.getHostString()+":"+endpoint.getPort()+"/cmsocket/").build();
        socket=http.newWebSocket(request,new WebSocketListener(){
            @Override public void onOpen(WebSocket ws,Response response){if(!ended.get())onConnected();}
            @Override public void onMessage(WebSocket ws,ByteString bytes){
                if(bytes.size()>32*1024*1024){disconnect(false);return;}
                if(!ended.get())onNetMsgReceived(new NetMsgEventArgs(bytes.toByteArray(),endpoint));
            }
            @Override public void onClosing(WebSocket ws,int code,String reason){ws.close(code,reason);finish(false);}
            @Override public void onClosed(WebSocket ws,int code,String reason){finish(false);}
            @Override public void onFailure(WebSocket ws,Throwable error,Response response){finish(false);}
        });
    }
    private void finish(boolean user){if(ended.compareAndSet(false,true))onDisconnected(user);}
    @Override public void disconnect(boolean user){finish(user);WebSocket ws=socket;if(ws!=null)ws.cancel();}
    @Override public void send(byte[] data){WebSocket ws=socket;if(ws==null||!ws.send(ByteString.of(data)))finish(false);}
    @Override public InetAddress getLocalIP(){return InetAddress.getLoopbackAddress();}
    @Override public InetSocketAddress getCurrentEndPoint(){return endpoint;}
    @Override public ProtocolTypes getProtocolTypes(){return ProtocolTypes.WEB_SOCKET;}
}
