package dev.workshopvideo;
import android.graphics.*;import android.os.SystemClock;import org.json.JSONObject;import dev.workshopvideo.core.EffectMotion;import dev.workshopvideo.core.GestureDrag;import java.util.*;
/** Render-thread effect state. Used by both video GL overlays and image/scene Canvas rendering. */
final class WallpaperEffects {
 int optionsRevision;private ColorMatrixColorFilter cachedColors;
 JSONObject options=EffectOptions.normalize(null);float gx,gy,tx,ty,sx,sy,offset=.5f;private long last=SystemClock.elapsedRealtime(),touched;private float touchX=.5f,touchY=.5f;private boolean down;private final GestureDrag drag=new GestureDrag();
 /** Where the wallpaper's "mouse" is: the finger while touching, otherwise steered by tilt and desktop paging (see VirtualPointer). */
 private boolean everTouched;private float pointerX=.5f,pointerY=.5f;
 float pointerX(){return pointerX;}float pointerY(){return pointerY;}boolean pointerDown(){return down;}
 /** Finger position itself, for effects that only make sense under the finger (X-ray, ripples). */
 float touchX(){return touchX;}float touchY(){return touchY;}
 private void updatePointer(){
  float[] idle=dev.workshopvideo.core.VirtualPointer.idle(sx,sy,offset,options.optBoolean("pan"));
  float[] p=dev.workshopvideo.core.VirtualPointer.position(touchX,touchY,down,SystemClock.elapsedRealtime()-touched,everTouched,idle);pointerX=p[0];pointerY=p[1];
 }
 /** The virtual pointer drifts with tilt and paging, so scenes that read it keep redrawing. */
 boolean pointerMoving(){return !down&&(everTouched&&SystemClock.elapsedRealtime()-touched<dev.workshopvideo.core.VirtualPointer.HOLD_MS+dev.workshopvideo.core.VirtualPointer.RETURN_MS);}
 void options(JSONObject o){optionsRevision++;cachedColors=null;options=EffectOptions.normalize(o);if(!options.optBoolean("touch")||options.optBoolean("pan"))drag.reset();}
 void tilt(float x,float y){gx=x;gy=y;}
 /** Pointer events for scene scripts: {action 0 down / 1 up / 2 move, x, y (0..1, y down), click}. */
 private final java.util.ArrayList<float[]> pointerEvents=new java.util.ArrayList<>();private float pressX,pressY;private long pressAt;
 synchronized java.util.List<float[]> drainPointer(){java.util.List<float[]> out=new java.util.ArrayList<>(pointerEvents);pointerEvents.clear();return out;}
 private synchronized void record(float x,float y,int action){
  if(pointerEvents.size()>=64)pointerEvents.remove(0);long now=SystemClock.elapsedRealtime();
  if(action==0){pressX=x;pressY=y;pressAt=now;pointerEvents.add(new float[]{0,x,y,0});}
  else if(action==2)pointerEvents.add(new float[]{2,x,y,0});
  else if(action==1||action==3){boolean click=action==1&&now-pressAt<400&&Math.hypot(x-pressX,y-pressY)<.03;pointerEvents.add(new float[]{1,x,y,click?1:0});}
 }
 void touch(float x,float y,int action){record(Math.max(0,Math.min(1,x)),Math.max(0,Math.min(1,y)),action);touchX=Math.max(0,Math.min(1,x));touchY=Math.max(0,Math.min(1,y));if(action==0){touched=SystemClock.elapsedRealtime();down=true;everTouched=true;}if(action==1||action==3){down=false;touched=SystemClock.elapsedRealtime();}if(down)touched=SystemClock.elapsedRealtime();updatePointer();if(!options.optBoolean("pan"))drag.touch(touchX,touchY,action);}
 EffectMotion.Frame frame(boolean fit){long now=SystemClock.elapsedRealtime();float dt=(now-last)/1000f;last=now;drag.step(dt);tx=drag.x();ty=drag.y();float x=(options.optBoolean("gyro")?gx:0)*(options.optBoolean("reverseX")?-1:1),y=(options.optBoolean("gyro")?gy:0)*(options.optBoolean("reverseY")?-1:1);sx=EffectMotion.approach(sx,x,dt,(float)options.optDouble("smooth",.18));sy=EffectMotion.approach(sy,y,dt,(float)options.optDouble("smooth",.18));updatePointer();return EffectMotion.compose(sx,sy,options.optBoolean("touch")?tx:0,options.optBoolean("touch")?ty:0,offset,(float)options.optDouble("strength"),(float)options.optDouble("touchStrength"),0,(options.optBoolean("gyro")?Math.max(1.12f,(float)options.optDouble("zoom",1.12)):(float)options.optDouble("zoom",1.12)),(float)options.optDouble("x"),(float)options.optDouble("y"),(float)options.optDouble("rotation"),fit);}
 ColorMatrixColorFilter colorFilter(){if(cachedColors!=null)return cachedColors;float[] rgb=dev.workshopvideo.core.PictureColors.matrix((float)options.optDouble("brightness",1),(float)options.optDouble("contrast",1),(float)options.optDouble("saturation",1),(float)options.optDouble("hue",0));float[] matrix=new float[20];for(int r=0;r<3;r++){for(int c=0;c<3;c++)matrix[r*5+c]=rgb[r*4+c];matrix[r*5+4]=rgb[r*4+3]*255;}matrix[18]=1;return cachedColors=new ColorMatrixColorFilter(new ColorMatrix(matrix));}
 boolean overlayAnimating(){return options.optBoolean("particles")||options.optBoolean("ripple")&&SystemClock.elapsedRealtime()-touched<1400;}
 void overlay(Canvas canvas,int width,int height){long now=SystemClock.elapsedRealtime();float density=width/360f;Paint paint=new Paint(3);int color;try{color=Color.parseColor(options.optString("color","#a9efd5"));}catch(Exception e){color=0xffa9efd5;}paint.setColor(color);
  if(options.optBoolean("particles")){int count=options.optInt("particleCount",24);float t=now/1000f*(float)options.optDouble("speed",1);for(int i=0;i<count;i++){float seed=(i*0.618034f)%1,x=(seed+.035f*(float)Math.sin(t*.3+i))*width,y=(1-((i*.317f+t*(.008f+(i%5)*.002f))%1))*height;paint.setAlpha(55+i%120);canvas.drawCircle(x,y,(1+i%3)*density,paint);}}
  float age=(now-touched)/1000f;if(options.optBoolean("particles")&&age>=0&&age<1.1f){for(int i=0;i<16;i++){float a=i*(float)Math.PI/8;paint.setAlpha((int)(190*(1-age/1.1f)));canvas.drawCircle(touchX*width+(float)Math.cos(a)*age*width*.2f,touchY*height+(float)Math.sin(a)*age*width*.2f+age*age*height*.08f,2*density,paint);}}if(options.optBoolean("ripple")&&age>=0&&age<1.3f){paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.5f*density);for(int i=0;i<3;i++){float a=age-i*.17f;if(a<0)continue;paint.setAlpha((int)(Math.max(0,1-a/1.3f)*180));canvas.drawCircle(touchX*width,touchY*height,a*width*.45f,paint);}paint.setStyle(Paint.Style.FILL);}

 }
}
