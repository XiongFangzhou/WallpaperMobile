package dev.workshopvideo;

import android.media.MediaDataSource;
import dev.workshopvideo.core.*;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/** Authenticated online video source. Project and video chunks exist only in memory. */
final class WorkshopVideoSource extends MediaDataSource implements WorkshopRangeDataSource.Reader {
    private final WorkshopRangeReader reader;private final String mime;private volatile String failure="";
    private WorkshopVideoSource(WorkshopRangeReader reader,String mime){this.reader=reader;this.mime=mime;}
    String mime(){return mime;}
    static WorkshopVideoSource open(VideoApp app,String itemId,WorkshopDownload.Progress progress,Consumer<WorkshopDownload> owner)throws Exception {
        SteamSession session=app.requireSession();if(progress.cancelled())throw new InterruptedException("已停止在线播放");
        if(app.guest)throw new IOException("在线播放需要登录拥有 Wallpaper Engine 的 Steam 账号。");
        WorkshopDownload download=new WorkshopDownload(session);owner.accept(download);
        try {
            if(progress.cancelled())throw new InterruptedException("已停止在线播放");
            var manifest=download.manifest(Long.parseUnsignedLong(itemId),progress);
            byte[] project=download.bytes(WorkshopDownload.find(manifest,"project.json"),2*1024*1024);
            JSONObject meta=new JSONObject(new String(project,StandardCharsets.UTF_8).replaceFirst("^\uFEFF",""));
            if(!meta.optString("type").equalsIgnoreCase("video"))throw new IOException("此作品不包含可直接在线播放的原始视频。");
            var file=WorkshopDownload.find(manifest,meta.optString("file"));
            WorkshopRangeReader reader=new WorkshopRangeReader(file,new WorkshopRangeReader.Source(){
                public byte[] load(in.dragonbra.javasteam.types.ChunkData chunk)throws Exception{return download.chunk(chunk);}
                public void close(){download.cancel();}
            });
            if(progress.cancelled()){reader.close();throw new InterruptedException("已停止在线播放");}
            return new WorkshopVideoSource(reader,VideoApp.mime(meta.optString("file")));
        }catch(Exception|OutOfMemoryError e){download.cancel();throw e;}
    }
    String failure(){return failure;}
    @Override public int readAt(long position,byte[] buffer,int offset,int size)throws IOException {
        try{int count=reader.readAt(position,buffer,offset,size);failure="";return count;}catch(IOException e){failure=e.getMessage()==null?"在线播放读取失败":e.getMessage();throw e;}
    }
    @Override public long getSize()throws IOException{return reader.size();}
    @Override public void close(){try{reader.close();}catch(IOException ignored){}}
}
