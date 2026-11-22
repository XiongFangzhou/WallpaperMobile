package dev.workshopvideo;
import android.media.MediaMetadataRetriever;
import android.graphics.Bitmap;
import android.os.*;
import android.util.LruCache;
import java.io.File;
import java.util.concurrent.*;
import java.util.function.Consumer;
import org.json.JSONObject;

/** Same eligibility result gates both menu entries and setup modes. */
final class WallpaperEligibility {
    record Result(boolean video,boolean still,WallpaperVideoProbe.Info info,String reason){}
    private final VideoApp app;private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newFixedThreadPool(2);private final LruCache<String,Result> cache=new LruCache<>(40);
    WallpaperEligibility(VideoApp app){this.app=app;android.content.SharedPreferences saved=WallpaperStore.prefs(app);if(!saved.getString("renderValidationVersion","").equals("0.5.1"))saved.edit().remove("rejectedKey").remove("rejectedReason").putString("renderValidationVersion","0.5.1").apply();}
    void check(JSONObject record,Consumer<Result> receive){
        if(!record.optString("state").equals("saved")||!record.optString("storage").equals("private")){receive.accept(new Result(false,false,null,"请先完成本地视频下载"));return;}
        if(record.has("compatible")&&!record.optBoolean("compatible")){receive.accept(new Result(false,false,null,record.optString("compatibilityReason","当前引擎不兼容此资源包")));return;}
        check(app.video(record.optString("id")),receive);
    }
    void check(File file,Consumer<Result> receive){
        android.app.WallpaperManager manager=android.app.WallpaperManager.getInstance(app);
        if(!manager.isWallpaperSupported()||!manager.isSetWallpaperAllowed()){receive.accept(new Result(false,false,null,"系统不允许设置壁纸"));return;}

        String key=WallpaperStore.sourceKey(file);if(key.equals(WallpaperStore.prefs(app).getString("rejectedKey",""))){receive.accept(new Result(false,true,null,WallpaperStore.prefs(app).getString("rejectedReason","视频壁纸播放失败")));return;}Result known=cache.get(key);if(known!=null){deliver(key,known,receive);return;}
        worker.submit(()->{Result value;try{String kind=WallpaperContent.kind(file);WallpaperVideoProbe.Info info;
            if(kind.equals("video"))info=WallpaperVideoProbe.inspect(file);else{Bitmap poster=CanvasWallpaper.poster(file,512);info=new WallpaperVideoProbe.Info(poster.getWidth(),poster.getHeight(),0,kind);poster.recycle();}
            WallpaperRenderer.verify(app,file);value=new Result(true,true,info,"");}catch(Exception|OutOfMemoryError e){boolean frame=false;try(MediaMetadataRetriever reader=new MediaMetadataRetriever()){reader.setDataSource(file.getAbsolutePath());Bitmap image=reader.getScaledFrameAtTime(0,MediaMetadataRetriever.OPTION_CLOSEST_SYNC,128,128);frame=image!=null;if(image!=null)image.recycle();}catch(Exception|OutOfMemoryError ignored){}value=new Result(false,frame,null,VideoApp.error(e));}if(!dev.workshopvideo.core.RenderFailure.transientSurface(value.reason()))cache.put(key,value);Result result=value;main.post(()->deliver(key,result,receive));});
    }
    void rejected(String key,String reason){if(dev.workshopvideo.core.RenderFailure.transientSurface(reason)){cache.remove(key);android.content.SharedPreferences saved=WallpaperStore.prefs(app);if(saved.getString("rejectedKey","").equals(key))saved.edit().remove("rejectedKey").remove("rejectedReason").apply();return;}WallpaperStore.prefs(app).edit().putString("rejectedKey",key).putString("rejectedReason",reason).apply();Result known=cache.get(key);cache.put(key,new Result(false,known==null||known.still(),known==null?null:known.info(),reason));}
    private void deliver(String key,Result result,Consumer<Result> receive){
        String rejected=WallpaperStore.prefs(app).getString("rejectedKey","");
        String systemReason=WallpaperStore.installed(app)?"":WallpaperSystemEntry.reason(app);
        if(key.equals(rejected)){receive.accept(new Result(false,result.still(),result.info(),WallpaperStore.prefs(app).getString("rejectedReason","视频壁纸实际播放失败")));return;}
        receive.accept(!systemReason.isEmpty()?new Result(false,result.still(),result.info(),systemReason):result);
    }
}
