package dev.workshopvideo;

import android.graphics.*;import org.json.*;import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.*;import java.io.*;import java.nio.file.*;import java.util.zip.*;import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35,manifest=Config.NONE,application=android.app.Application.class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
public class SceneCompositionChecks {
 private File fixture()throws Exception{
  File file=new File(System.getProperty("graphics.work"),"cache-gradient-scene.zip");Bitmap gradient=Bitmap.createBitmap(192,96,Bitmap.Config.ARGB_8888);for(int y=0;y<96;y++)for(int x=0;x<192;x++)gradient.setPixel(x,y,Color.rgb(x,255-x,y*2));ByteArrayOutputStream image=new ByteArrayOutputStream();gradient.compress(Bitmap.CompressFormat.PNG,100,image);gradient.recycle();JSONObject root=new JSONObject().put("format","WallpaperMobileScene/1").put("width",192).put("height",96).put("layers",new JSONArray().put(new JSONObject().put("file","gradient.png")));
  try(ZipOutputStream z=new ZipOutputStream(new FileOutputStream(file))){z.putNextEntry(new ZipEntry("wallpaper-mobile.json"));z.write(root.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));z.closeEntry();z.putNextEntry(new ZipEntry("gradient.png"));z.write(image.toByteArray());z.closeEntry();}return file;
 }
 @Test public void cachedCameraMatchesExistingCanvasAtEveryPageAndRotation()throws Exception{
  File file=fixture();WallpaperEffects effects=new WallpaperEffects();JSONObject o=new JSONObject().put("zoom",1).put("pan",true);effects.options(o);JSONObject config=new JSONObject().put("effects",effects.options).put("pan",true);try(var unused=new AutoCloseable(){public void close(){}}){
  CanvasWallpaper content=new CanvasWallpaper(null,64,128,file,config,new android.os.Handler(android.os.Looper.getMainLooper()),effects,null);
  try{Bitmap source=content.sourceTexture(2048);for(boolean fit:new boolean[]{false,true})for(float rotation:new float[]{0,12})for(float offset:new float[]{0,.25f,.5f,.75f,1}){
   config.put("fit",fit);effects.options(new JSONObject().put("pan",true).put("zoom",1.2).put("rotation",rotation));effects.offset=offset;Bitmap expected=Bitmap.createBitmap(64,128,Bitmap.Config.ARGB_8888),actual=Bitmap.createBitmap(64,128,Bitmap.Config.ARGB_8888);content.paint(new Canvas(expected),64,128);var motion=effects.frame(fit);var camera=dev.workshopvideo.core.CanvasCamera.frame(192,96,64,128,fit,true,offset,motion);Canvas canvas=new Canvas(actual);canvas.drawColor(Color.BLACK);canvas.translate(32+camera.x()*32,64-camera.y()*64);canvas.rotate(camera.angle());canvas.scale(camera.scaleX()*64/192,camera.scaleY()*128/96);canvas.translate(-96,-48);canvas.drawBitmap(source,0,0,new Paint(3));
   int differing=0;for(int y=0;y<128;y++)for(int x=0;x<64;x++){int a=actual.getPixel(x,y),b=expected.getPixel(x,y);if(Math.abs(Color.red(a)-Color.red(b))>2||Math.abs(Color.green(a)-Color.green(b))>2||Math.abs(Color.blue(a)-Color.blue(b))>2)differing++;}assertTrue("cached camera difference: "+differing+" fit="+fit+" rotation="+rotation+" page="+offset,differing<64);actual.recycle();expected.recycle();}
  }finally{content.close();}}
 }
 @Test public void pageMotionKeepsOneCompositionAndOnlyVisualChangesRepaint()throws Exception{
  WallpaperEffects effects=new WallpaperEffects();effects.options(new JSONObject().put("pan",true));CanvasWallpaper content=new CanvasWallpaper(null,64,128,fixture(),new JSONObject().put("pan",true),new android.os.Handler(android.os.Looper.getMainLooper()),effects,null);
  try{Bitmap original=content.sourceTexture(2048);int generation=original.getGenerationId();long cachedStart=System.nanoTime();for(int frame=0;frame<120;frame++){effects.offset=frame/119f;assertTrue(content.gpuComposition());assertSame(original,content.sourceTexture(2048));assertEquals(generation,original.getGenerationId());dev.workshopvideo.core.CanvasCamera.frame(192,96,64,128,false,true,effects.offset,effects.frame(false));}long cachedNs=System.nanoTime()-cachedStart;
   Bitmap output=Bitmap.createBitmap(64,128,Bitmap.Config.ARGB_8888);long fullStart=System.nanoTime();for(int frame=0;frame<120;frame++){effects.offset=frame/119f;content.paint(new Canvas(output),64,128);}long fullNs=System.nanoTime()-fullStart;output.recycle();System.out.println("Scene composition probe, 120 frames: cached preparation="+cachedNs/1e6+"ms, full Canvas redraw="+fullNs/1e6+"ms; host native graphics, not phone frame rate");
   effects.options(new JSONObject().put("pan",true).put("brightness",.5));assertSame(original,content.sourceTexture(2048));assertNotEquals(generation,original.getGenerationId());int current=original.getGenerationId();effects.options(new JSONObject().put("pan",true).put("brightness",.5).put("zoom",1.4));content.sourceTexture(2048);assertEquals(current,original.getGenerationId());
  }finally{content.close();}
 }
 @Test public void activeLayerInteractionsUseLiveCompositionAndPagingDoesNotDoubleDrag()throws Exception{
  WallpaperEffects effects=new WallpaperEffects();effects.options(new JSONObject().put("pan",true).put("gyro",true).put("touch",true));CanvasWallpaper content=new CanvasWallpaper(null,64,128,fixture(),new JSONObject().put("pan",true),new android.os.Handler(android.os.Looper.getMainLooper()),effects,null);
  try{assertTrue(content.gpuComposition());effects.tilt(.2f,0);assertFalse(content.gpuComposition());effects.options(new JSONObject().put("particles",true));assertFalse(content.gpuComposition());effects.options(new JSONObject().put("pan",true).put("touch",true));effects.touch(.8f,.5f,0);effects.touch(.2f,.5f,2);assertTrue(content.gpuComposition());effects.options(new JSONObject().put("touch",true));assertFalse(content.gpuComposition());}finally{content.close();}
 }
}
