package dev.workshopvideo;

import android.app.Activity;
import android.content.Context;
import android.widget.TextView;
import dev.workshopvideo.core.SteamSession;
import dev.workshopvideo.core.WorkshopDownload;
import in.dragonbra.javasteam.types.DepotManifest;
import in.dragonbra.javasteam.types.FileData;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Wallpaper Engine's own shipped assets (shaders, util models and materials, engine textures, built-in effects),
 * downloaded from Steam with the user's own Wallpaper Engine licence into app-private storage. Never bundled or shared.
 */
final class EngineAssets {
    private EngineAssets(){}
    static final String PREFIX="assets/";
    static File root(Context c){return new File(c.getFilesDir(),"engine-assets");}
    private static File marker(Context c){return new File(root(c),".manifest");}
    /** Manifest id of the installed copy, or 0 when none is complete. */
    static long installed(Context c){try{return Long.parseLong(new String(Files.readAllBytes(marker(c).toPath()),StandardCharsets.UTF_8).trim());}catch(Exception e){return 0;}}
    static String status(Context c){long id=installed(c);return id==0?"未下载，部分场景使用近似素材":"已下载（版本 "+id+"）";}

    static void open(Activity activity){
        VideoApp app=(VideoApp)activity.getApplication();
        AppSheet sheet=new AppSheet(activity,"Wallpaper Engine 素材",status(activity),null);
        TextView detail=Ui.text(activity,"用你的 Steam 账号从 Steam 下载 Wallpaper Engine 自带的着色器、内置图层和贴图（约 80 MB），只保存在本应用中。下载后场景转换会使用原版素材。",13,Ui.MUTED);
        sheet.field(detail);
        sheet.footerButton("关闭",false,()->sheet.dialog.dismiss());
        sheet.footerButton(installed(activity)==0?"下载":"检查更新",true,()->{
            if(!app.canRecover()||app.guest){activity.runOnUiThread(()->sheet.detail("需要登录拥有 Wallpaper Engine 的 Steam 账号。"));return;}
            sheet.detail("正在连接 Steam…");
            app.work.submit(()->{
                try{String result=download(activity,app.requireSession(),message->activity.runOnUiThread(()->{if(sheet.dialog.isShowing())sheet.detail(message);}));activity.runOnUiThread(()->sheet.detail(result));}
                catch(Exception|OutOfMemoryError e){android.util.Log.w("EngineAssets","download failed",e);String message="下载失败："+VideoApp.error(e);activity.runOnUiThread(()->sheet.detail(message));}
            });
        });
        sheet.show();
    }

    /** Downloads every file under assets/ of the engine depot; files already complete are verified by chunk checksums and kept. */
    static String download(Context c,SteamSession session,java.util.function.Consumer<String> status)throws Exception {
        WorkshopDownload download=new WorkshopDownload(session);
        status.accept("读取 Wallpaper Engine 版本…");
        long manifestId=download.engineManifestId();
        long[] last={0};
        DepotManifest manifest=download.engineManifest(manifestId,new WorkshopDownload.Progress(){
            public void update(long done,long total,String message){long now=System.currentTimeMillis();if(now-last[0]>250||done==total){last[0]=now;status.accept(message!=null&&!message.isEmpty()?message:total>0?String.format(java.util.Locale.ROOT,"%.1f / %.1f MB",done/1048576d,total/1048576d):"下载中…");}}
            public boolean cancelled(){return false;}
        });
        List<FileData> files=new ArrayList<>();long total=0;int all=0;
        for(FileData file:manifest.getFiles()){
            all++;String name=WorkshopDownload.normalize(file.getFileName());
            if(!name.toLowerCase(java.util.Locale.ROOT).startsWith(PREFIX)||file.getChunks().isEmpty()||file.getLinkTarget()!=null&&!file.getLinkTarget().isEmpty())continue;
            files.add(file);total+=file.getTotalSize();
        }
        android.util.Log.i("EngineAssets","manifest "+manifestId+": "+all+" files, "+files.size()+" under assets/, "+total+" bytes");
        if(files.isEmpty())throw new IOException("Steam 清单里没有 assets 目录（共 "+all+" 个文件）。");
        File root=root(c);root.mkdirs();Files.deleteIfExists(marker(c).toPath());SceneAdapter.engineRoot=null;long done=0;int index=0;
        for(FileData file:files){
            String relative=WorkshopDownload.normalize(file.getFileName()).substring(PREFIX.length());
            File out=new File(root,relative);if(!out.getCanonicalPath().startsWith(root.getCanonicalPath()+File.separator))throw new IOException("素材路径不安全："+relative);
            out.getParentFile().mkdirs();
            if(!(out.isFile()&&out.length()==file.getTotalSize()))download.file(file,out,64L*1024*1024);
            done+=file.getTotalSize();index++;
            if(index%20==0||index==files.size())status.accept(String.format(java.util.Locale.ROOT,"正在下载素材 %d/%d（%.1f / %.1f MB）",index,files.size(),done/1048576d,total/1048576d));
        }
        Files.write(marker(c).toPath(),Long.toString(manifestId).getBytes(StandardCharsets.UTF_8));
        SceneAdapter.engineRoot=root;
        if(c.getApplicationContext() instanceof VideoApp app)SceneReconversion.schedule(app);
        String summary=String.format(java.util.Locale.ROOT,"已下载 %d 个文件，%.1f MB（版本 %d）。",files.size(),total/1048576d,manifestId);
        android.util.Log.i("EngineAssets",summary);return summary;
    }
}
