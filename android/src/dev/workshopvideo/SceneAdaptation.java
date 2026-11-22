package dev.workshopvideo;
import android.app.Activity;import android.content.Intent;import android.widget.*;import org.json.*;import java.io.*;import java.nio.file.*;import dev.workshopvideo.core.*;
/** Upgrade a legacy incompatible saved scene in place; no manual generation flow or extra record. */
final class SceneAdaptation {
 static void open(Activity activity,VideoApp app,JSONObject original){
  if(original.optBoolean("officialPackage")){AppSheet sheet=new AppSheet(activity,original.optString("title"),"这是 Hydrogen 保存的官方 MPKG。主线使用自有引擎，需要从工坊重新下载适配；成功后替换原文件，失败或取消会保留。",null);sheet.action("download","重新下载为主线版本","可在已保存中查看进度",false,()->{try{JSONObject next=app.replacement(original.optString("id"));activity.startForegroundService(new Intent(activity,VideoDownloadService.class).putExtra("id",next.optString("id")));}catch(Exception error){Toast.makeText(activity,VideoApp.error(error),1).show();}});sheet.show();return;}

  AppSheet sheet=new AppSheet(activity,original.optString("title"),"正在自动适配旧版场景…",null);sheet.show();
  app.work.submit(()->{
   String id=original.optString("id");File workspace=new File(app.getCacheDir(),"scene-upgrade-"+id);File stage=null;
   try{
    workspace.mkdirs();stage=File.createTempFile("runtime-",".content",workspace);
    SceneDownload.Prepared prepared=SceneDownload.prepare(app.video(id),stage,workspace,()->{if(Thread.currentThread().isInterrupted())throw new InterruptedException("适配已停止");});
    WallpaperRenderer.verify(app,stage);
    JSONObject record=app.job(id);long size=stage.length();String sha1=VideoFiles.hex(WorkshopDownload.hash(stage,"SHA-1"));
    // Replace only after validation; failure before this point leaves the legacy entry recoverable.
    Files.move(stage.toPath(),app.video(id).toPath(),StandardCopyOption.REPLACE_EXISTING);
    VideoApp.put(record,"kind",prepared.kind());VideoApp.put(record,"effects",prepared.effects());VideoApp.put(record,"adapted",prepared.adapted());VideoApp.put(record,"adaptationReport",prepared.report());VideoApp.put(record,"compatible",true);VideoApp.put(record,"compatibilityReason","");VideoApp.put(record,"size",size);VideoApp.put(record,"sha1",sha1);VideoApp.put(record,"message","已适配，可直接使用");app.save(record);app.preview(id).delete();
    activity.runOnUiThread(()->{if(activity.isFinishing()||activity.isDestroyed())return;sheet.dialog.dismiss();activity.startActivity(new Intent(activity,WallpaperSetupActivity.class).putExtra("id",id));});
   }catch(Exception|OutOfMemoryError e){String failure=VideoApp.error(e);if(e instanceof SceneDownload.Rejected){try{app.discard(id);failure+="；无法使用的旧版原始资源已清理";}catch(Exception cleanup){failure+="；资源清理失败："+VideoApp.error(cleanup);}}String message=failure;activity.runOnUiThread(()->{if(activity.isFinishing()||activity.isDestroyed())return;sheet.detail(message);sheet.footerButton("关闭",false,()->sheet.dialog.dismiss());});}
   finally{try{WorkshopAssets.clear(workspace);}catch(IOException ignored){}}
  });
 }
 private SceneAdaptation(){}
}
