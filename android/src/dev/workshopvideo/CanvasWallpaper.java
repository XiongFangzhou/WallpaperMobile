package dev.workshopvideo;
import android.content.Context;import android.graphics.*;import android.graphics.drawable.*;import android.os.*;import android.view.Surface;import org.json.JSONObject;import dev.workshopvideo.core.EffectMotion;import java.io.*;
/** Static/animated images and layered scenes render through the same effect state as video. */
final class CanvasWallpaper implements Drawable.Callback {
 private final Handler handler;private final WallpaperRenderer.Events events;private final WallpaperEffects effects;private Surface surface;private int width,height;private boolean visible,closed,announced,frozen;private long frozenAt;private Drawable image;private Movie gif;private WallpaperContent.Scene scene;private long began=SystemClock.elapsedRealtime();private JSONObject config;
 private final Runnable tick=()->draw();
 CanvasWallpaper(Surface surface,int width,int height,File file,JSONObject config,Handler handler,WallpaperEffects effects,WallpaperRenderer.Events events)throws Exception{
  this.surface=surface;this.width=width;this.height=height;this.config=config;this.handler=handler;this.effects=effects;this.events=events;
  String kind=WallpaperContent.kind(file);
  if(kind.equals("scene"))scene=WallpaperContent.scene(file);else{
   try(InputStream in=new FileInputStream(file)){gif=Movie.decodeStream(in);}
   if(gif==null){image=ImageDecoder.decodeDrawable(ImageDecoder.createSource(file),(decoder,info,source)->{decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);int max=Math.max(info.getSize().getWidth(),info.getSize().getHeight());decoder.setTargetSampleSize(Math.max(1,max/2048));});image.setCallback(this);}
  }
 }
 void frozen(boolean value){if(value&&!frozen)frozenAt=SystemClock.elapsedRealtime();if(!value&&frozen)began+=SystemClock.elapsedRealtime()-frozenAt;frozen=value;}
 void reportPaging(int w,int h,WallpaperRenderer.Events callbacks){if(callbacks==null)return;int iw=scene!=null?scene.width:gif!=null?gif.width():image.getIntrinsicWidth(),ih=scene!=null?scene.height:gif!=null?gif.height():image.getIntrinsicHeight();callbacks.contentSize(iw,ih);callbacks.pagingAvailable(dev.workshopvideo.core.WallpaperGeometry.canPage(iw,ih,w,h,config.optBoolean("fit"),effects.options.optBoolean("pan"),(float)effects.options.optDouble("zoom",1),1));}
 private void pagingAvailability(){if(events==null)return;int iw=scene!=null?scene.width:gif!=null?gif.width():image.getIntrinsicWidth(),ih=scene!=null?scene.height:gif!=null?gif.height():image.getIntrinsicHeight();events.pagingAvailable(dev.workshopvideo.core.WallpaperGeometry.canPage(iw,ih,width,height,config.optBoolean("fit"),effects.options.optBoolean("pan"),(float)effects.options.optDouble("zoom",1),(float)effects.options.optDouble("scrollStrength",.5)));}
 void options(JSONObject config){this.config=config;pagingAvailability();draw();}
 void size(int w,int h){width=w;height=h;pagingAvailability();draw();}
 void visible(boolean value){visible=value;handler.removeCallbacks(tick);if(image instanceof Animatable){if(value)((Animatable)image).start();else ((Animatable)image).stop();}if(value){pagingAvailability();draw();}}
 private void draw(){if(closed||!visible||surface==null||!surface.isValid())return;Canvas c=null;try{c=surface.lockHardwareCanvas();paint(c,width,height);surface.unlockCanvasAndPost(c);c=null;if(!announced){announced=true;if(events!=null)events.rendered();}handler.removeCallbacks(tick);handler.postDelayed(tick,1000/Math.max(15,effects.options.optInt("fps",30)));}catch(Exception|OutOfMemoryError e){if(events!=null)events.failed(VideoApp.error(e));visible=false;}finally{if(c!=null)try{surface.unlockCanvasAndPost(c);}catch(Exception ignored){}}}
 void paint(Canvas c,int w,int h){c.drawColor(Color.BLACK);EffectMotion.Frame frame=effects.frame(config.optBoolean("fit"));int iw=scene!=null?scene.width:gif!=null?gif.width():image.getIntrinsicWidth(),ih=scene!=null?scene.height:gif!=null?gif.height():image.getIntrinsicHeight();float scale=config.optBoolean("fit")?Math.min(w/(float)iw,h/(float)ih):Math.max(w/(float)iw,h/(float)ih);scale*=frame.zoom();if(!config.optBoolean("fit")){float a=w/(float)h;double radians=Math.toRadians(frame.rotation());scale*=Math.abs(Math.cos(radians))+Math.abs(Math.sin(radians))*Math.max(a,1/a);}
  c.save();c.translate(w/2f,h/2f);c.rotate(frame.rotation());float scroll=effects.options.optBoolean("pan")&&!config.optBoolean("fit")?(effects.offset-.5f)*Math.max(0,iw*scale-w):0;float marginX=Math.max(0,(iw*scale-w)/2),marginY=Math.max(0,(ih*scale-h)/2);c.translate(EffectMotion.clamp(frame.x()*w*2-scroll,-marginX,marginX),EffectMotion.clamp(frame.y()*h*2,-marginY,marginY));c.scale(scale,scale);c.translate(-iw/2f,-ih/2f);Paint p=new Paint(3);p.setColorFilter(effects.colorFilter());
  if(scene!=null){ScenePainter.layers(c,scene,p,effects.options,frame,effects.pointerX()*w,effects.pointerY()*h,effects.pointerDown());}
  else if(gif!=null){int duration=Math.max(1,gif.duration());gif.setTime((int)((((frozen?frozenAt:SystemClock.elapsedRealtime())-began)*effects.options.optDouble("speed",1))%duration));gif.draw(c,0,0,p);}
  else {image.setBounds(0,0,iw,ih);image.setColorFilter(effects.colorFilter());image.draw(c);}c.restore();effects.overlay(c,w,h);
 }
 static Bitmap poster(File file,int edge)throws Exception {String kind=WallpaperContent.kind(file);if(!kind.equals("scene"))return WallpaperContent.image(file,edge);try(WallpaperContent.Scene scene=WallpaperContent.scene(file)){return ScenePainter.poster(scene,edge);}}

 boolean animated(){return gif!=null||image instanceof Animatable;}
 void close(){closed=true;handler.removeCallbacksAndMessages(this);handler.removeCallbacks(tick);if(image instanceof Animatable)((Animatable)image).stop();if(image!=null)image.setCallback(null);if(scene!=null)scene.close();}
 public void invalidateDrawable(Drawable d){}
 public void scheduleDrawable(Drawable d,Runnable r,long when){handler.postAtTime(r,this,when);}
 public void unscheduleDrawable(Drawable d,Runnable r){handler.removeCallbacks(r);}
}
