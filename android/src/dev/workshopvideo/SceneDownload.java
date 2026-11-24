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
 /** Receives a video texture and writes only its MP4 payload, without holding the video in memory. */
 static final class Mp4Out extends FilterOutputStream {
  private final ByteArrayOutputStream head=new ByteArrayOutputStream();private long from=-1,to,at,written;
  Mp4Out(OutputStream out){super(out);}
  @Override public void write(int b)throws IOException{write(new byte[]{(byte)b},0,1);}
  @Override public void write(byte[] b,int off,int len)throws IOException{
   if(from<0){int take=Math.min(len,512-head.size());head.write(b,off,take);if(head.size()<512)return;byte[] h=head.toByteArray();long[] range=dev.workshopvideo.core.TexturePixels.mp4Range(h);from=range[0];to=range[0]+range[1];at=0;pass(h,0,h.length);pass(b,off+take,len-take);return;}
   pass(b,off,len);
  }
  private void pass(byte[] b,int off,int len)throws IOException{long start=Math.max(at,from),end=Math.min(at+len,to);if(start<end){out.write(b,off+(int)(start-at),(int)(end-start));written+=end-start;}at+=len;}
  /** Fails unless the whole MP4 arrived; small textures that never filled the header buffer are handled here. */
  void finish()throws IOException{if(from<0){byte[] h=head.toByteArray();long[] range=dev.workshopvideo.core.TexturePixels.mp4Range(h);from=range[0];to=range[0]+range[1];at=0;pass(h,0,h.length);}if(written!=to-from)throw new IOException("视频纹理数据不完整");out.flush();}
 }
 static Prepared prepare(File source,File target,File workspace,Guard guard)throws Exception {return prepare(source,target,workspace,guard,null);}
 static Prepared prepare(File source,File target,File workspace,Guard guard,SceneEffects.Translator translator)throws Exception {
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
      if(!analysis.usable()&&!analysis.videoTexture.isEmpty()){
       // Mobile-only scenes that are a full-screen video plus scripts: keep the video itself as the wallpaper.
       guard.check();converted=File.createTempFile("scene-video-",".content",workspace);
       try(ContentArchive archive=new ContentArchive(normalized);OutputStream out=new BufferedOutputStream(new FileOutputStream(converted))){
        Mp4Out mp4=new Mp4Out(out);archive.copy(analysis.videoTexture,mp4,dev.workshopvideo.core.WorkshopAssets.VIDEO_LIMIT);mp4.finish();
       }
       if(!WallpaperContent.kind(converted).equals("video"))throw new IOException("场景中的视频无法解码");
       kind="video";adapted=true;report="这个场景的画面是一段全屏视频，已作为视频壁纸保存。场景脚本（如点击切换画面）不会执行。";
      }else{
      if(!analysis.usable())throw new IOException(analysis.error.isEmpty()?"没有可用图片图层":analysis.error);
      guard.check();converted=File.createTempFile("scene-adapted-",".content",workspace);SceneAdapter.write(analysis,converted,translator);guard.check();
      try(WallpaperContent.Scene verified=WallpaperContent.scene(converted)){effects=EffectOptions.normalize(verified.defaults);}
      adapted=true;report=analysis.describe();
      }
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
