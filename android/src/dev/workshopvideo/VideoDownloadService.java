package dev.workshopvideo;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.os.*;
import dev.workshopvideo.core.*;
import org.json.*;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

public final class VideoDownloadService extends Service {
    private final DownloadTasks tasks=new DownloadTasks();
    static final String PAUSE="dev.workshopvideo.PAUSE_DOWNLOAD",CANCEL="dev.workshopvideo.CANCEL_DOWNLOAD";
    private final java.util.concurrent.atomic.AtomicInteger pending=new java.util.concurrent.atomic.AtomicInteger();
    private VideoApp app;
    private NotificationManager notifications;
    private volatile boolean timedOut=false;
    private volatile int lastStart;
    public static final int NOTICE=7;
    @Override public void onCreate() {
        super.onCreate();app=(VideoApp)getApplication();notifications=getSystemService(NotificationManager.class);
        notifications.createNotificationChannel(new NotificationChannel("download","壁纸下载",NotificationManager.IMPORTANCE_LOW));
    }
    private Notification notification(String message,long done,long total) {
        Intent open=new Intent(this,MainActivity.class).putExtra("tab",2);
        PendingIntent action=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder n=new Notification.Builder(this,"download").setSmallIcon(R.drawable.ic_download)
            .setContentTitle("WallpaperMobile").setContentText(message).setContentIntent(action).setOngoing(true)
            .setOnlyAlertOnce(true).setCategory(Notification.CATEGORY_PROGRESS);
        n.setProgress(1000,total>0?(int)Math.min(1000,done*1000/total):0,total<=0);
        return n.build();
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        lastStart=startId;String id=intent==null?null:intent.getStringExtra("id");
        if(id==null||!id.matches("[0-9a-f]{32}")){if(pending.get()==0)stopSelfResult(startId);return START_NOT_STICKY;}
        if(PAUSE.equals(intent.getAction())||CANCEL.equals(intent.getAction())){stopDownload(id,CANCEL.equals(intent.getAction()));return START_NOT_STICKY;}
        startForeground(NOTICE,notification("准备下载…",0,0),ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        if(!app.enqueued.add(id)){stopIfIdle();return START_NOT_STICKY;}
        pending.incrementAndGet();
        try{boolean submitted=tasks.submit(id,new DownloadTasks.Work(){
            public void run(DownloadTasks.Token token){runDownload(id,token);}
            public void finished(DownloadTasks.Token token,Throwable failure){finishTask(id,token.stop(),failure);}
        });if(!submitted)finishTask(id,DownloadTasks.Stop.PAUSE,new IllegalStateException("任务仍在结束处理中，请稍后重试"));}catch(RuntimeException failure){finishTask(id,DownloadTasks.Stop.PAUSE,failure);}
        return START_NOT_STICKY;
    }
    private void stopDownload(String id,boolean discard){
        try{synchronized(app){JSONObject record=app.job(id);if(record.optString("state").equals("saved")){stopIfIdle();return;}
            VideoApp.put(record,"cancelRequested",true);VideoApp.put(record,"discardRequested",discard||record.optBoolean("discardRequested"));
            VideoApp.put(record,"state",record.optBoolean("discardRequested")?"canceling":"pausing");VideoApp.put(record,"error","");VideoApp.put(record,"message",record.optBoolean("discardRequested")?"正在取消并清理…":"正在暂停…");app.save(record);}
            if(!tasks.request(id,discard?DownloadTasks.Stop.CANCEL:DownloadTasks.Stop.PAUSE))app.work.submit(()->{try{if(!app.enqueued.contains(id))settleStopped(id,discard?DownloadTasks.Stop.CANCEL:DownloadTasks.Stop.PAUSE);}finally{stopIfIdle();}});
        }catch(Exception ignored){stopIfIdle();}
    }
    private void settleStopped(String id,DownloadTasks.Stop stop){
        try{synchronized(app){JSONObject record=app.job(id);if(record.optString("state").equals("saved"))return;
            if(stop==DownloadTasks.Stop.CANCEL||record.optBoolean("discardRequested")){app.discard(id);return;}
            if(stop==DownloadTasks.Stop.PAUSE||record.optBoolean("cancelRequested")){VideoApp.put(record,"state","paused");VideoApp.put(record,"message","已暂停，可继续下载");VideoApp.put(record,"error","");app.save(record);}
        }}catch(Exception failure){try{app.change(id,"state","error");app.change(id,"error","停止任务后未能清理："+VideoApp.error(failure));}catch(Exception ignored){}}
    }
    private void finishTask(String id,DownloadTasks.Stop stop,Throwable failure){
        try{synchronized(app){app.enqueued.remove(id);if(failure!=null&&!app.job(id).optString("state").equals("saved")){app.change(id,"state","error");app.change(id,"error",VideoApp.error(failure));}settleStopped(id,stop);}}
        finally{pending.decrementAndGet();app.changed();stopIfIdle();}
    }
    private void stopIfIdle(){new Handler(Looper.getMainLooper()).post(()->{if(pending.get()==0&&stopSelfResult(lastStart))stopForeground(STOP_FOREGROUND_REMOVE);});}

    private boolean cancelled(String id) {
        return timedOut || Thread.currentThread().isInterrupted() || app.job(id).optBoolean("cancelRequested");
    }
    private void ensureRunning(String id) throws InterruptedException {
        if(cancelled(id))throw new InterruptedException("下载已暂停，可继续下载。");
    }
    private void runDownload(String id,DownloadTasks.Token token) {
        File project=null;File workspace=app.transfer(id);
        try {
            token.check();app.filesReady.get();ensureRunning(id);
            JSONObject record=app.job(id);
            if(record.optString("state").equals("saved"))return;
            if(record.optString("storage").equals("private")&&app.video(id).isFile()&&!record.optString("sha1").isEmpty()){ensureRunning(id);app.finishPrivateSave(record);WorkshopAssets.clear(workspace);return;}
            ensureRunning(id);app.change(id,"error","");app.change(id,"state","preparing");
            app.change(id,"message","正在恢复 Steam 会话…");
            SteamSession session=app.requireSession();
            if(app.guest)throw new IOException("请先用拥有 Wallpaper Engine 的 Steam 账号登录。");
            long itemId=Long.parseUnsignedLong(record.getString("itemId"));
            token.check();WorkshopDownload download=new WorkshopDownload(session);token.transport(download::cancel);token.check();
            long[] aggregate={0,0};String[] resource={""};
            WorkshopDownload.Progress progress=new WorkshopDownload.Progress(){
                long last=0;
                public void update(long done,long total,String message){
                    if(aggregate[1]>0){done=Math.min(aggregate[1],aggregate[0]+done);total=aggregate[1];message=resource[0]+" · "+message;}
                    long now=System.currentTimeMillis();if(now-last<500 && done<total)return;last=now;
                    synchronized(app){JSONObject current=app.job(id);VideoApp.put(current,"done",done);VideoApp.put(current,"total",total);
                        VideoApp.put(current,"message",message);app.save(current);}
                    notifications.notify(NOTICE,notification(message,done,total));
                }
                public boolean cancelled(){return VideoDownloadService.this.cancelled(id);}
            };
            var manifest=download.manifest(itemId,progress);
            var projectFile=WorkshopDownload.find(manifest,"project.json");
            project=new File(app.getCacheDir(),id+"-project.json");
            download.file(projectFile,project,2*1024*1024);
            JSONObject metadata=new JSONObject(new String(java.nio.file.Files.readAllBytes(project.toPath()),java.nio.charset.StandardCharsets.UTF_8).replaceFirst("^\uFEFF",""));
            if(!List.of("video","image","scene").contains(metadata.optString("type").toLowerCase(Locale.ROOT)))throw new SceneDownload.Rejected("不支持网页、应用或未知类型的壁纸运行环境",null);
            var plan=WorkshopAssets.plan(manifest,metadata.optString("type"),metadata.optString("file"));
            File temporary=app.partial(id);if(!workspace.isDirectory()&&!workspace.mkdirs())throw new IOException("无法创建下载暂存目录");
            long remaining=plan.bundle()?WorkshopAssets.remaining(plan,workspace):Math.max(0,plan.total()-temporary.length());
            long needed=remaining+(plan.bundle()?2*plan.total():0)+128L*1024*1024;
            if(app.getFilesDir().getUsableSpace()<needed)throw new IOException("手机剩余空间不足，无法下载并解析壁纸资源。");
            app.change(id,"kind",plan.type().equals("scene")?"scene":plan.type().equals("image")?"image":"video");
            app.change(id,"state","downloading");aggregate[0]=plan.files().get(0).getTotalSize();aggregate[1]=plan.total();
            File raw=plan.bundle()?new File(workspace,"original.bundle"):temporary;
            WorkshopAssets.download(plan,workspace,project,raw,new WorkshopAssets.Fetch(){
                public void file(in.dragonbra.javasteam.types.FileData file,File target,long limit)throws Exception {
                    resource[0]=file.getFileName();download.file(file,target,limit);aggregate[0]+=file.getTotalSize();
                }
                public void check()throws Exception {ensureRunning(id);}
            });
            ensureRunning(id);app.change(id,"state","saving");app.change(id,"message","正在解析资源与检查兼容性…");
            String kind=plan.type().equals("video")?"video":"image";boolean adapted=false;String adaptationReport="";
            JSONObject effects=EffectOptions.normalize(null);
            try {
                if(WallpaperContent.archive(raw)){
                    app.change(id,"message","正在自动适配手机壁纸…");
                    SceneDownload.Prepared prepared=SceneDownload.prepare(raw,temporary,workspace,()->ensureRunning(id));
                    kind=prepared.kind();effects=prepared.effects();adapted=prepared.adapted();adaptationReport=prepared.report();
                }else if(plan.bundle())throw new SceneDownload.Rejected("场景没有可解析的资源包",null);
                else kind=WallpaperContent.kind(temporary);
                if(kind.equals("scene")){WallpaperRenderer.verify(app,temporary);try(WallpaperContent.Scene scene=WallpaperContent.scene(temporary)){effects=EffectOptions.normalize(scene.defaults);}}
                else if(kind.equals("video")){effects=EffectOptions.video(null);if(!plan.type().equals("video"))WallpaperVideoProbe.inspect(temporary);}
                else {android.graphics.Bitmap image=WallpaperContent.image(temporary,512);image.recycle();}
            } catch(SceneDownload.Rejected rejected){throw rejected;}
              catch(Exception|OutOfMemoryError failure){ensureRunning(id);throw new SceneDownload.Rejected(VideoApp.error(failure),failure);}
            ensureRunning(id);
            String extension=kind.equals("scene")?".zip":kind.equals("video")?".video":".image";
            String originalExtension=plan.main().substring(Math.max(0,plan.main().lastIndexOf('.'))).toLowerCase(Locale.ROOT);
            if(!plan.bundle()&&originalExtension.matches("\\.(mp4|webm|mkv|mov|m4v|avi|png|jpg|jpeg|gif|webp|bmp|heic|avif)"))extension=originalExtension;
            String title=record.optString("title","wallpaper").replaceAll("[\\x00-\\x1f<>:\"/\\\\|?*]","_").trim();
            if(title.length()>80)title=title.substring(0,80);
            String name=(title.isEmpty()?"wallpaper":title)+"_"+record.getString("itemId")+extension;
            String sha1=VideoFiles.hex(WorkshopDownload.hash(temporary,"SHA-1"));
            synchronized(app){JSONObject saved=app.job(id);VideoApp.put(saved,"state","saving");
                VideoApp.put(saved,"storage","private");VideoApp.put(saved,"uri",app.videoUri(id).toString());
                VideoApp.put(saved,"kind",kind);VideoApp.put(saved,"compatible",true);VideoApp.put(saved,"compatibilityReason","");
                VideoApp.put(saved,"officialPackage",false);VideoApp.put(saved,"adapted",adapted);VideoApp.put(saved,"adaptationReport",adaptationReport);
                VideoApp.put(saved,"effects",effects);VideoApp.put(saved,"sourceType",plan.type());
                VideoApp.put(saved,"name",name);VideoApp.put(saved,"size",temporary.length());
                VideoApp.put(saved,"done",plan.total());VideoApp.put(saved,"total",plan.total());VideoApp.put(saved,"sha1",sha1);VideoApp.put(saved,"savedAt",System.currentTimeMillis());app.save(saved);}
            ensureRunning(id);WorkshopAssets.clear(workspace);ensureRunning(id);app.finishPrivateSave(app.job(id));
        } catch(Throwable e) {
            if(!app.job(id).optString("state").equals("saved")){
                boolean stopped=e instanceof InterruptedException||cancelled(id);if(stopped)Thread.interrupted();
                if(e instanceof SceneDownload.Rejected&&!stopped){try{WorkshopAssets.clear(workspace);java.nio.file.Files.deleteIfExists(app.partial(id).toPath());}catch(IOException cleanup){e.addSuppressed(cleanup);}}String state=stopped?app.job(id).optBoolean("discardRequested")?"canceling":"paused":"error";
                app.change(id,"state",state);app.change(id,"error",stopped?"":VideoApp.error(e));if(stopped)app.change(id,"message",state.equals("canceling")?"正在取消并清理…":"已暂停，可继续下载");
            }
        } finally {
            if(project!=null)project.delete();
            synchronized(app){JSONObject record=app.job(id);record.remove("pendingUri");app.save(record);}
        }
    }
    @Override public void onTimeout(int startId,int fgsType){timedOut=true;tasks.close();stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}
    @Override public void onDestroy(){timedOut=true;tasks.close();super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}
