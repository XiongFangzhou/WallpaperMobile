package dev.workshopvideo.core;

import in.dragonbra.javasteam.enums.EResult;
import in.dragonbra.javasteam.protobufs.steamclient.SteammessagesContentsystemSteamclient.*;
import in.dragonbra.javasteam.rpc.service.ContentServerDirectory;
import in.dragonbra.javasteam.steam.cdn.Server;
import in.dragonbra.javasteam.steam.cdn.DepotChunk;
import in.dragonbra.javasteam.steam.handlers.steamapps.SteamApps;
import in.dragonbra.javasteam.types.DepotManifest;
import in.dragonbra.javasteam.types.FileData;
import in.dragonbra.javasteam.types.ChunkData;
import in.dragonbra.javasteam.util.Adler32;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Download selected workshop files directly from Steam CDN; no subscriptions. */
public final class WorkshopDownload {
    public interface Progress {
        void update(long done,long total,String message);
        boolean cancelled();
    }
    private final SteamSession session;
    private final ContentServerDirectory directory;
    private final SteamCdn cdn;
    private final List<Server> servers = new ArrayList<>();
    private final Map<String,String> auth = new HashMap<>();
    private byte[] key;
    private int depot,streamServer;
    private Progress progress;
    private volatile boolean cancelled;
    public void cancel(){cancelled=true;cdn.cancel();}

    public WorkshopDownload(SteamSession session) {
        this.session=session;
        directory=session.unified.createService(ContentServerDirectory.class);
        cdn=new SteamCdn(session.client.getConfiguration().getHttpClient());
    }

    public DepotManifest manifest(long itemId,Progress progress) throws Exception {
        this.progress=progress;
        progress.update(0,0,"读取作品清单…");
        var item=session.details(itemId);
        if(item.getHcontentFile()==0)
            throw new IOException("该作品未提供 Steam 内容清单，暂不支持直接下载。");
        // Wallpaper Engine's workshop depot uses the application ID. A denied
        // key or manifest request is surfaced instead of bypassing ownership.
        depot=WorkshopQuery.APP_ID;
        progress.update(0,0,"请求作品下载权限…");
        var depotKey=await(session.client.getHandler(SteamApps.class)
            .getDepotDecryptionKey(depot,WorkshopQuery.APP_ID));
        if(depotKey.getResult()!=EResult.OK)
            throw new IOException("Steam 未授予下载权限："+depotKey.getResult()+"。请确认此账号拥有 Wallpaper Engine。");
        key=depotKey.getDepotKey();
        progress.update(0,0,"请求作品清单授权…");
        var requestCode=await(directory.getManifestRequestCode(
            CContentServerDirectory_GetManifestRequestCode_Request.newBuilder()
                .setAppId(WorkshopQuery.APP_ID).setDepotId(depot).setManifestId(item.getHcontentFile()).build()));
        if(requestCode.getResult()!=EResult.OK || requestCode.getBody().getManifestRequestCode()==0)
            throw new IOException("Steam 没有授权此作品清单："+requestCode.getResult()+"。请检查购买资格和作品可见性。");
        progress.update(0,0,"获取 Steam 内容服务器…");
        var serverResponse=await(directory.getServersForSteamPipe(
            CContentServerDirectory_GetServersForSteamPipe_Request.newBuilder().setCellId(session.cellId()).setMaxServers(12).build()));
        if(serverResponse.getResult()!=EResult.OK) throw new IOException("Steam 内容服务器列表不可用。");
        servers.clear();
        for(var entry:serverResponse.getBody().getServersList()) {
            if(!entry.getType().equals("CDN") && !entry.getType().equals("SteamCache"))continue;
            if(entry.getHttpsSupport().equals("none"))continue;
            String host=entry.getVhost().isEmpty()?entry.getHost():entry.getVhost();
            if(host.isEmpty())continue;
            servers.add(Server.fromHostAndPort(host,443));
        }
        if(servers.isEmpty())throw new IOException("没有可用的 HTTPS 内容服务器，请稍后重试。");
        Exception last=null;List<String> failures=new ArrayList<>();int index=0;
        for(Server server:servers) {
            check();
            progress.update(0,0,"下载作品清单（"+(++index)+"/"+servers.size()+"）…");
            try {
                DepotManifest manifest;
                try {manifest=cdn.manifest(depot,item.getHcontentFile(),
                    requestCode.getBody().getManifestRequestCode(),server,key,auth.get(server.getHost()));}
                catch(SteamCdn.HttpFailure e) {
                    if(!e.needsToken())throw e;
                    auth.remove(server.getHost());
                    String signed=token(server);if(signed==null||signed.isEmpty())throw e;
                    manifest=cdn.manifest(depot,item.getHcontentFile(),
                        requestCode.getBody().getManifestRequestCode(),server,key,signed);
                }
                if(manifest.getDepotID()!=depot || manifest.getManifestGID()!=item.getHcontentFile())
                    throw new IOException("Steam 返回的清单与请求不一致。");
                // Reuse a working endpoint first for the following resource chunks.
                servers.remove(server);servers.add(0,server);return manifest;
            } catch(InterruptedException e){throw e;}
              catch(Exception e){ last=e;failures.add(server.getHost()+"："+SteamCdn.failure(e)); }
        }
        throw new IOException("作品清单下载失败（已尝试 "+servers.size()+" 个节点）。\n"+String.join("\n",failures),last);
    }

    private String token(Server server) throws Exception {
        String host=server.getHost();
        if(auth.containsKey(host)) return auth.get(host);
        var response=await(directory.getCDNAuthToken(
            CContentServerDirectory_GetCDNAuthToken_Request.newBuilder().setAppId(WorkshopQuery.APP_ID)
                .setDepotId(depot).setHostName(host).build()));
        // Public CDN endpoints may not require a token. Let the actual HTTPS
        // manifest/chunk response determine access when no token was issued.
        String token=response.getResult()==EResult.OK?response.getBody().getToken():null;
        auth.put(host,token); return token;
    }

    public static String normalize(String name) {
        String result=name.replace('\\','/');
        if(result.startsWith("/") || result.contains(":"))throw new IllegalArgumentException("资源包含不安全的文件路径。");
        for(String part:result.split("/"))if(part.equals("..") || part.equals("."))
            throw new IllegalArgumentException("资源包含不安全的文件路径。");
        return result;
    }

    public static FileData find(DepotManifest manifest,String relative) throws IOException {
        String name=normalize(relative);
        for(FileData file:manifest.getFiles())if(normalize(file.getFileName()).equals(name)) {
            if(file.getLinkTarget()!=null && !file.getLinkTarget().isEmpty())
                throw new IOException("不处理链接文件。");
            return file;
        }
        throw new IOException("资源清单中没有找到文件："+relative);
    }

    public void file(FileData file,File output,long maxSize) throws Exception {
        validate(file,maxSize);
        List<ChunkData> chunks=new ArrayList<>(file.getChunks());
        chunks.sort(Comparator.comparingLong(ChunkData::getOffset));
        long done=0;
        try(RandomAccessFile target=new RandomAccessFile(output,"rw")) {
            for(ChunkData chunk:chunks) {
                check();
                int length=chunk.getUncompressedLength();byte[] plain=null;
                boolean existing=target.length()>=chunk.getOffset()+length;
                if(existing) {
                    plain=new byte[length];target.seek(chunk.getOffset());target.readFully(plain);
                    existing=Adler32.calculate(plain)==chunk.getChecksum();
                }
                if(!existing) {
                    plain=chunk(chunk);
                    target.seek(chunk.getOffset());target.write(plain);
                }
                done+=plain.length;
                progress.update(done,file.getTotalSize(),"下载资源中…");
            }
            target.setLength(file.getTotalSize());target.getFD().sync();
        }
        progress.update(done,done,"正在校验完整资源…");
        if(file.getFileHash().length!=20 || !MessageDigest.isEqual(hash(output,"SHA-1"),file.getFileHash())) {
            // The partial is untrusted. Do not publish it to saved videos.
            if(!output.delete())throw new IOException("资源校验失败，且未能清理损坏的临时文件。");
            throw new IOException("资源完整性校验失败，临时文件已清理，请重试。");
        }
    }

    /** Authenticated random-access chunk reads; no temporary files. */
    public byte[] chunk(ChunkData chunk)throws Exception {
        if(chunk.getUncompressedLength()<=0||chunk.getUncompressedLength()>16*1024*1024||chunk.getCompressedLength()<=0||chunk.getCompressedLength()>20*1024*1024)throw new IOException("资源块大小无效");
        Exception last=null;
        for(int attempt=0;attempt<Math.min(6,servers.size()*2);attempt++) {
            check();int serverIndex=(streamServer+attempt)%servers.size();Server server=servers.get(serverIndex);
            try {
                byte[] encrypted;
                try{encrypted=cdn.chunk(depot,chunk.getChunkID(),chunk.getCompressedLength(),server,auth.get(server.getHost()));}
                catch(SteamCdn.HttpFailure e){if(!e.needsToken())throw e;auth.remove(server.getHost());String signed=token(server);if(signed==null||signed.isEmpty())throw e;encrypted=cdn.chunk(depot,chunk.getChunkID(),chunk.getCompressedLength(),server,signed);}
                byte[] plain=new byte[chunk.getUncompressedLength()];int written=DepotChunk.process(chunk,encrypted,plain,key);
                if(written!=plain.length)throw new IOException("资源块解码长度不一致");
                WorkshopRangeReader.verify(chunk,plain);check();streamServer=serverIndex;return plain;
            }catch(InterruptedException e){throw e;}catch(Exception e){check();last=e;}
        }
        throw new IOException("资源块读取失败："+SteamCdn.failure(last)+"，请检查网络后重试。",last);
    }
    public byte[] bytes(FileData file,int limit)throws Exception {
        validate(file,limit);byte[] result=new byte[(int)file.getTotalSize()];
        for(ChunkData chunk:file.getChunks()){check();byte[] plain=chunk(chunk);System.arraycopy(plain,0,result,(int)chunk.getOffset(),plain.length);}
        if(!MessageDigest.isEqual(MessageDigest.getInstance("SHA-1").digest(result),file.getFileHash()))throw new IOException("作品描述完整性校验失败");
        return result;
    }

    public static void validate(FileData file,long maxSize) throws IOException {
        normalize(file.getFileName());
        if(file.getTotalSize()<0 || file.getTotalSize()>maxSize)throw new IOException("文件超出可用存储空间或大小限制。");
        if(file.getLinkTarget()!=null && !file.getLinkTarget().isEmpty())throw new IOException("不支持链接文件。");
        List<ChunkData> chunks=new ArrayList<>(file.getChunks());
        chunks.sort(Comparator.comparingLong(ChunkData::getOffset));
        long offset=0;
        for(ChunkData c:chunks) {
            if(c.getOffset()!=offset || c.getUncompressedLength()<=0 || c.getUncompressedLength()>16*1024*1024
                || c.getCompressedLength()<=0 || c.getCompressedLength()>20*1024*1024)
                throw new IOException("资源清单的数据块范围无效。");
            offset+=c.getUncompressedLength();
            if(offset>file.getTotalSize())throw new IOException("资源数据块超出文件范围。");
        }
        if(offset!=file.getTotalSize())throw new IOException("资源清单不完整。");
    }

    private <T extends in.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackMsg> T await(in.dragonbra.javasteam.types.AsyncJobSingle<T> job)throws Exception {
        job.setTimeout(35000);var future=job.toFuture();long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(40);
        while(true){check();try{return future.get(200,TimeUnit.MILLISECONDS);}catch(java.util.concurrent.TimeoutException timeout){if(System.nanoTime()>=deadline)throw timeout;}}
    }
    private void check() throws InterruptedException {
        if(cancelled || progress!=null&&progress.cancelled() || Thread.currentThread().isInterrupted())throw new InterruptedException("下载已取消，可重试继续。");
    }
    public static byte[] hash(File file,String algorithm) throws Exception {
        MessageDigest digest=MessageDigest.getInstance(algorithm);
        try(InputStream input=new FileInputStream(file)) {
            byte[] buffer=new byte[1024*1024];int n;
            while((n=input.read(buffer))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedException("校验已暂停");digest.update(buffer,0,n);}
        }
        return digest.digest();
    }
}
