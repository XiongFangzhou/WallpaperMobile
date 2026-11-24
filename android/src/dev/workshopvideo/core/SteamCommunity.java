package dev.workshopvideo.core;

import com.google.protobuf.Empty;
import in.dragonbra.javasteam.base.ClientMsgProtobuf;
import in.dragonbra.javasteam.base.PacketClientMsgProtobuf;
import in.dragonbra.javasteam.enums.EMsg;
import in.dragonbra.javasteam.enums.EResult;
import in.dragonbra.javasteam.steam.handlers.steamunifiedmessages.SteamUnifiedMessages;
import in.dragonbra.javasteam.steam.handlers.steamunifiedmessages.UnifiedService;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Raw calls to Steam's "Community" unified service, which JavaSteam has no generated classes for.
 * The request body is an empty message followed by hand-encoded bytes; the response keeps every field as unknown
 * fields of an empty message, so re-serializing it returns the original body bytes.
 */
public final class SteamCommunity extends UnifiedService {
    private final Map<Long,CompletableFuture<byte[]>> pending=new ConcurrentHashMap<>();
    volatile in.dragonbra.javasteam.steam.steamclient.SteamClient client;
    public SteamCommunity(SteamUnifiedMessages unifiedMessages){super(unifiedMessages);}
    @Override public String getServiceName(){return "Community";}

    /** Sends {@code body} to {@code method} ("Community.Name#1") and returns the raw response body. */
    public byte[] call(String method,byte[] body)throws Exception{return call(method,body,35);}
    public byte[] call(String method,byte[] body,int seconds)throws Exception{
        var client=this.client;if(client==null)throw new IllegalStateException("Steam 尚未连接");
        ClientMsgProtobuf<Empty.Builder> message=new ClientMsgProtobuf<>(Empty.class,client.getSteamID()==null?EMsg.ServiceMethodCallFromClientNonAuthed:EMsg.ServiceMethodCallFromClient);
        var jobId=client.getNextJobID();long job=jobId.getValue();message.setSourceJobID(jobId);message.getProtoHeader().setTargetJobName(method);message.getPayload().write(body,0,body.length);
        CompletableFuture<byte[]> response=new CompletableFuture<>();pending.put(job,response);
        try{client.send(message);return response.get(seconds,TimeUnit.SECONDS);}
        catch(TimeoutException timeout){throw new IllegalStateException("Steam 响应超时，请稍后重试。");}
        catch(ExecutionException failed){throw failed.getCause() instanceof Exception e?e:failed;}
        finally{pending.remove(job);}
    }

    @Override public void handleResponseMsg(String methodName,PacketClientMsgProtobuf packet){
        CompletableFuture<byte[]> response=pending.get(packet.getTargetJobID());if(response==null)return;
        ClientMsgProtobuf<Empty.Builder> message=new ClientMsgProtobuf<>(Empty.class,packet);
        EResult result=EResult.from(message.getProtoHeader().getEresult());
        if(result!=EResult.OK)response.completeExceptionally(new IllegalStateException("Steam 拒绝了请求："+result));
        else response.complete(message.getBody().build().toByteArray());
    }
    @Override public void handleNotificationMsg(String methodName,PacketClientMsgProtobuf packet){}
}
