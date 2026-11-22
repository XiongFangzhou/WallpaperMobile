package dev.workshopvideo;
import dev.workshopvideo.core.ContentArchive;
import org.json.JSONObject;
import java.io.*;
import java.nio.file.*;

/** Download admission produces only validated runtime content; author bundles remain temporary. */
final class SceneDownload {
 interface Guard {void check()throws Exception;}
 record Prepared(String kind,JSONObject effects,boolean adapted,String report){}
 static final class Rejected extends IOException {Rejected(String reason,Throwable cause){super("无法转为手机壁纸："+reason,cause);}}
 static Prepared prepare(File source,File target,File workspace,Guard guard)throws Exception {
  File normalized=null,converted=null;boolean done=false;
  try {
   guard.check();if(!WallpaperContent.archive(source))throw new IOException("场景没有可解析的资源包");
   normalized=File.createTempFile("scene-normalized-",".content",workspace);
   WallpaperPackage.normalize(source,normalized,workspace);guard.check();
   String kind=WallpaperContent.kind(normalized);JSONObject effects=EffectOptions.normalize(null);boolean adapted=false;String report="";
   if(kind.equals("scene")){
    boolean mobile;try(ContentArchive archive=new ContentArchive(normalized)){mobile=archive.has("wallpaper-mobile.json");}
    if(mobile){try(WallpaperContent.Scene scene=WallpaperContent.scene(normalized)){effects=EffectOptions.normalize(scene.defaults);}}
    else {
     guard.check();SceneAdapter.Report analysis;
     try(ContentArchive archive=new ContentArchive(normalized)){analysis=SceneAdapter.inspect(archive);}
     try {
      if(!analysis.usable())throw new IOException(analysis.error.isEmpty()?"没有可用图片图层":analysis.error);
      guard.check();converted=File.createTempFile("scene-adapted-",".content",workspace);SceneAdapter.write(analysis,converted);guard.check();
      try(WallpaperContent.Scene verified=WallpaperContent.scene(converted)){effects=EffectOptions.normalize(verified.defaults);}
      adapted=true;report=analysis.describe();
     }finally{analysis.scene.close();}
    }
   }else if(kind.equals("image")||kind.equals("animated")){android.graphics.Bitmap image=WallpaperContent.image(normalized,512);image.recycle();}
   else if(!kind.equals("video"))throw new IOException("资源包没有可运行的壁纸内容");
   guard.check();Files.move((adapted?converted:normalized).toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING);guard.check();done=true;
   return new Prepared(kind,effects,adapted,report);
  }catch(Rejected e){throw e;}catch(Exception|OutOfMemoryError failure){guard.check();throw new Rejected(failure.getMessage()==null?failure.getClass().getSimpleName():failure.getMessage(),failure);}
  finally{if(normalized!=null)Files.deleteIfExists(normalized.toPath());if(converted!=null)Files.deleteIfExists(converted.toPath());if(!done)Files.deleteIfExists(target.toPath());}
 }
 private SceneDownload(){}
}
