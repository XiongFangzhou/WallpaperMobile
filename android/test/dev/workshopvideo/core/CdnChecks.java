package dev.workshopvideo.core;

import in.dragonbra.javasteam.steam.cdn.Server;
import in.dragonbra.javasteam.types.*;
import okhttp3.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;

final class CdnChecks {
    static void run() throws Exception {
        long manifestId=Long.parseUnsignedLong("18446744073709551614");
        long requestCode=Long.parseUnsignedLong("18446744073709551613");
        Server server=Server.fromHostAndPort("cdn.example.com",443);
        String token="auth=a%2Fb%2Bc%3D&expires=2000000000";
        var url=SteamCdn.url(server,SteamCdn.manifestPath(431960,manifestId,requestCode),token);
        CoreChecks.check(url.toString().equals("https://cdn.example.com/depot/431960/manifest/18446744073709551614/5/18446744073709551613?"+token),"unsigned manifest IDs and signed CDN query");
        CoreChecks.check(SteamCdn.url(server,"path","?"+token).encodedQuery().equals(token),"Steam query prefix");
        CoreChecks.check(SteamCdn.manifestPath(431960,1,0).equals("depot/431960/manifest/1/5"),"manifest URL without request code");
        DepotManifest manifest=new DepotManifest();manifest.setDepotID(431960);manifest.setManifestGID(manifestId);
        FileData file=new FileData();file.setFileName("视频.mp4");file.setFileHash(new byte[20]);manifest.getFiles().add(file);
        ByteArrayOutputStream raw=new ByteArrayOutputStream();manifest.serialize(raw);
        CoreChecks.check(SteamCdn.decodeManifest(raw.toByteArray()).getManifestGID()==manifestId,"raw manifest decoding");
        ByteArrayOutputStream packed=new ByteArrayOutputStream();
        try(ZipOutputStream zip=new ZipOutputStream(packed)) {zip.putNextEntry(new ZipEntry("manifest"));zip.write(raw.toByteArray());zip.closeEntry();}
        final byte[] zipped=packed.toByteArray();
        OkHttpClient http=new OkHttpClient.Builder().addInterceptor(chain->{
            Request request=chain.request();
            CoreChecks.check(request.url().toString().equals(url.toString()),"actual transport URL preserves signature");
            return new Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(ResponseBody.create(null,zipped)).build();
        }).build();
        DepotManifest received=new SteamCdn(http).manifest(431960,manifestId,requestCode,server,new byte[32],token);
        CoreChecks.check(received.getFiles().size()==1&&received.getFiles().get(0).getFileName().equals("视频.mp4"),"CDN manifest HTTP and ZIP decode");
        byte[] chunk=new byte[]{1,2,3,4};
        OkHttpClient chunks=new OkHttpClient.Builder().addInterceptor(chain->{
            CoreChecks.check(chain.request().url().encodedPath().equals("/depot/431960/chunk/01abff"),"chunk URL hex encoding");
            return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(ResponseBody.create(null,chunk)).build();
        }).build();
        CoreChecks.check(Arrays.equals(new SteamCdn(chunks).chunk(431960,new byte[]{1,(byte)0xab,(byte)0xff},4,server,null),chunk),"CDN chunk transport");
        CoreChecks.rejects(()->new SteamCdn(chunks).chunk(431960,new byte[]{1,(byte)0xab,(byte)0xff},3,server,null),"oversized chunk body");
        OkHttpClient denied=new OkHttpClient.Builder().addInterceptor(chain->new Response.Builder().request(chain.request())
            .protocol(Protocol.HTTP_1_1).code(403).message("Forbidden").body(ResponseBody.create(null,new byte[0])).build()).build();
        try {new SteamCdn(denied).manifest(431960,manifestId,requestCode,server,new byte[32],token);throw new AssertionError("403 accepted");}
        catch(IOException e){CoreChecks.check(SteamCdn.failure(e).equals("HTTP 403")&&!e.getMessage().contains(token),"HTTP failure without credential leak");}
        CoreChecks.check(SteamCdn.failure(new java.net.UnknownHostException("secret-host")).equals("DNS 解析失败"),"DNS diagnostics");
        CoreChecks.check(SteamCdn.failure(new java.util.concurrent.ExecutionException(new java.net.SocketTimeoutException())).equals("请求超时"),"nested timeout diagnostics");
        java.util.concurrent.CountDownLatch entered=new java.util.concurrent.CountDownLatch(1);java.util.concurrent.ExecutorService waiting=java.util.concurrent.Executors.newSingleThreadExecutor();
        OkHttpClient shared=new OkHttpClient.Builder().addInterceptor(chain->{
            if(chain.request().url().encodedPath().equals("/blocked")){entered.countDown();long end=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(3);while(!chain.call().isCanceled()&&System.nanoTime()<end){try{new java.util.concurrent.CountDownLatch(1).await(10,java.util.concurrent.TimeUnit.MILLISECONDS);}catch(InterruptedException e){throw new IOException(e);}}if(chain.call().isCanceled())throw new IOException("canceled");throw new IOException("test request timeout");}
            return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(ResponseBody.create(null,new byte[]{7})).build();
        }).build();
        SteamCdn owner=new SteamCdn(shared),other=new SteamCdn(shared);
        java.util.concurrent.Future<?> inFlight=waiting.submit(()->{try{owner.read(SteamCdn.url(server,"blocked",null),10);throw new AssertionError("cancelled CDN returned data");}catch(IOException expected){}});
        try{CoreChecks.check(entered.await(2,java.util.concurrent.TimeUnit.SECONDS),"CDN owned request begins before cancel");owner.cancel();inFlight.get(2,java.util.concurrent.TimeUnit.SECONDS);CoreChecks.check(other.read(SteamCdn.url(server,"okay",null),10)[0]==7,"cancelling one transfer does not cancel shared-client session or another stream");CoreChecks.rejects(()->owner.read(SteamCdn.url(server,"okay",null),10),"cancelled CDN cannot issue another request");}finally{owner.cancel();other.cancel();waiting.shutdownNow();}
        CoreChecks.check(new SteamSession.LoginRejected(in.dragonbra.javasteam.enums.EResult.Expired).invalidToken(),"expired token requires login");
        CoreChecks.check(!new SteamSession.LoginRejected(in.dragonbra.javasteam.enums.EResult.ServiceUnavailable).invalidToken(),"Steam outage keeps saved token");
    }
}
