package dev.workshopvideo.core;

import in.dragonbra.javasteam.steam.cdn.Server;
import in.dragonbra.javasteam.types.DepotManifest;
import okhttp3.*;
import java.io.*;
import java.util.Locale;
import java.util.zip.ZipInputStream;

/** Synchronous HTTPS transport with unsigned Steam IDs and verbatim signed queries. */
public final class SteamCdn {
    public static final class HttpFailure extends IOException {
        public final int status;
        HttpFailure(int status){super("HTTP "+status);this.status=status;}
        public boolean needsToken(){return status==401||status==403;}
    }
    private final OkHttpClient http;
    private final java.util.Set<Call> calls=java.util.concurrent.ConcurrentHashMap.newKeySet();
    private volatile boolean cancelled;
    public void cancel(){cancelled=true;for(Call call:calls)call.cancel();}

    public SteamCdn(OkHttpClient http){this.http=http;}

    public static String manifestPath(int depot,long manifest,long code) {
        return "depot/"+Integer.toUnsignedString(depot)+"/manifest/"+Long.toUnsignedString(manifest)+"/5"
            +(code==0?"":"/"+Long.toUnsignedString(code));
    }
    public static HttpUrl url(Server server,String path,String token) {
        HttpUrl.Builder builder=new HttpUrl.Builder().scheme("https").host(server.getHost()).port(443).addPathSegments(path);
        // Steam signs this already encoded query. Re-encoding % or + breaks it.
        if(token!=null&&!token.isEmpty())builder.encodedQuery(token.startsWith("?")?token.substring(1):token);
        return builder.build();
    }
    public DepotManifest manifest(int depot,long manifest,long code,Server server,byte[] key,String token) throws IOException {
        byte[] body=read(url(server,manifestPath(depot,manifest,code),token),32*1024*1024);
        DepotManifest result=decodeManifest(body);
        if(result.getFilenamesEncrypted()&&!result.decryptFilenames(key))throw new IOException("清单文件名解密失败。");
        return result;
    }
    public static DepotManifest decodeManifest(byte[] body) throws IOException {
        if(body.length>=2&&body[0]=='P'&&body[1]=='K') {
            try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(body))) {
                if(zip.getNextEntry()==null)throw new IOException("Steam 清单压缩包为空。");
                return DepotManifest.deserialize(bounded(zip,64*1024*1024));
            }
        }
        return DepotManifest.deserialize(body);
    }
    public byte[] chunk(int depot,byte[] id,int expected,Server server,String token) throws IOException {
        StringBuilder hex=new StringBuilder();for(byte b:id)hex.append(String.format(Locale.ROOT,"%02x",b&255));
        byte[] body=read(url(server,"depot/"+Integer.toUnsignedString(depot)+"/chunk/"+hex,token),expected);
        if(body.length!=expected)throw new IOException("Steam 视频数据块长度不一致。");
        return body;
    }
    byte[] read(HttpUrl url,int maximum) throws IOException {
        if(cancelled)throw new java.io.InterruptedIOException("请求已停止");
        Call call=http.newCall(new Request.Builder().url(url).build());calls.add(call);
        try {
            if(cancelled){call.cancel();throw new java.io.InterruptedIOException("请求已停止");}
            try(Response response=call.execute()) {
            if(!response.isSuccessful())throw new HttpFailure(response.code());
            if(response.body()==null)throw new IOException("Steam 返回空响应。");
            if(response.body().contentLength()>maximum)throw new IOException("Steam 响应超出大小限制。");
            return bounded(response.body().byteStream(),maximum);
            }
        }finally{calls.remove(call);}
    }
    private static byte[] bounded(InputStream input,int maximum) throws IOException {
        ByteArrayOutputStream output=new ByteArrayOutputStream();byte[] buffer=new byte[32768];int n;
        while((n=input.read(buffer))!=-1) {
            if(output.size()>maximum-n)throw new IOException("Steam 响应超出大小限制。");
            output.write(buffer,0,n);
        }
        return output.toByteArray();
    }
    public static String failure(Throwable error) {
        Throwable cause=error;
        while(cause.getCause()!=null&&(cause instanceof java.util.concurrent.ExecutionException||cause instanceof java.util.concurrent.CompletionException))cause=cause.getCause();
        if(cause instanceof java.net.UnknownHostException)return "DNS 解析失败";
        if(cause instanceof javax.net.ssl.SSLException)return "HTTPS/TLS 连接失败";
        if(cause instanceof java.net.SocketTimeoutException||cause instanceof java.util.concurrent.TimeoutException)return "请求超时";
        if(cause instanceof java.net.ConnectException)return "无法连接服务器";
        String message=cause.getMessage();
        // Never expose signed CDN URLs or session credentials in UI diagnostics.
        if(message!=null&&message.matches("HTTP [0-9]{3}"))return message;
        return cause.getClass().getSimpleName();
    }
}
