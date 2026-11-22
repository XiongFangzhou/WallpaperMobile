package dev.workshopvideo.core;
/** Finger displacement is provisional; real changing launcher offsets take over exactly once. */
public final class LauncherPaging {
 private float value=.5f,raw=Float.NaN,startX,startY,base,slop=.012f;private int count,configured=3;private boolean down,horizontal,systemInGesture,manual,gestureFallback;
 private final float[] samplesX=new float[32];private final long[] samplesT=new long[32];private int samples;private float flingSpeed=.45f;
 public void gestureFallback(boolean enabled){gestureFallback=enabled;down=horizontal=false;}
 public void pages(int fallback){configured=Math.max(1,Math.min(12,fallback));}
 public void manual(boolean on){manual=on;}
 public void slop(float value){slop=EffectMotion.clamp(value,.002f,.06f);}
 public void flingSpeed(float screensPerSecond){flingSpeed=EffectMotion.clamp(screensPerSecond,.3f,3);}
 /** Historical input timestamps must be retained even if rendering or delivery is delayed. */
 public void sample(float x,long now){if(!down)return;if(samples>0&&now<samplesT[samples-1])return;if(samples>0&&now==samplesT[samples-1]){samplesX[samples-1]=x;return;}if(samples==samplesX.length){System.arraycopy(samplesX,1,samplesX,0,--samples);System.arraycopy(samplesT,1,samplesT,0,samples);}samplesX[samples]=x;samplesT[samples++]=now;}
 public float velocity(){if(samples<2)return 0;long end=samplesT[samples-1];int from=samples-1;while(from>0&&end-samplesT[from-1]<=120)from--;if(samplesT[samples-1]-samplesT[from]<8)return 0;double sx=0,st=0,stt=0,stx=0;int n=samples-from;for(int i=from;i<samples;i++){double t=(samplesT[i]-end)/1000d;sx+=samplesX[i];st+=t;stt+=t*t;stx+=t*samplesX[i];}double denominator=n*stt-st*st;return denominator>0?(float)((n*stx-st*sx)/denominator):0;}
 /** A launcher observation corrects a guess without treating a vendor's fixed raw offset as movement. */
 public void confirmed(float x){value=pageCount()<=1?.5f:WallpaperPaging.offset(x);if(down)systemInGesture=true;}
 public int pageCount(){return !manual&&count>=2?count:configured;}
 public void position(float position){value=WallpaperPaging.offset(position);}
 public float value(){return value;}
 public boolean dragging(){return down&&horizontal&&!systemInGesture;}
 public void reset(){value=.5f;raw=Float.NaN;down=horizontal=systemInGesture=false;count=0;}
 public void system(float x,float step,long now){
  system(x,step,now,false);
 }
 public void system(float x,float step,long now,boolean trustedInitial){
  int n=WallpaperPaging.pages(step);if(n>=2)count=n;
  if(pageCount()<=1){value=.5f;raw=x;return;}
  if(!Float.isFinite(x)||x<0||x>1)return;
  // A native spring can advance less than .0005 per frame at high refresh rates.
  // Only an identical callback is a fixed vendor offset; retain every real float change.
  boolean moving=Float.isFinite(raw)&&Float.compare(x,raw)!=0;
  if(moving){value=x;if(down)systemInGesture=true;}
  else if(!Float.isFinite(raw)&&(trustedInitial||n>=2&&!manual))value=x;
  raw=x;
 }
 public boolean touch(float x,float y,int action,long now){
  if(pageCount()<=1){value=.5f;down=false;return false;}
  if(!gestureFallback){down=false;return false;}
  if(action==0){down=true;horizontal=systemInGesture=false;startX=x;startY=y;base=value;samples=0;sample(x,now);return false;}
  if(!down)return false;
  sample(x,now);float dx=x-startX,dy=y-startY;
  // Some launchers coalesce all MOVE events. Classify the final UP displacement too.
  if(!horizontal&&Math.abs(dx)>slop&&Math.abs(dx)>Math.abs(dy)*1.3f)horizontal=true;
  if(action==2){
   if(horizontal&&!systemInGesture){value=EffectMotion.clamp(base-dx/Math.max(1,pageCount()-1),0,1);return true;}return false;}
  if(action==1||action==3){down=false;if(horizontal&&!systemInGesture){int intervals=Math.max(1,pageCount()-1);float speed=velocity();boolean fling=Math.abs(dx)>=Math.max(.025f,slop*2)&&Math.abs(speed)>=flingSpeed&&speed*dx>0;value=action==3||(!fling&&Math.abs(dx)<.18f)||Math.abs(dx)<=Math.abs(dy)*1.3f?base:EffectMotion.clamp(base+(dx<0?1:-1)/(float)intervals,0,1);return true;}}
  return false;
 }
}
