package dev.workshopvideo.core;
/** Finger displacement is provisional; real changing launcher offsets take over exactly once. */
public final class LauncherPaging {
 private float value=.5f,raw=Float.NaN,startX,startY,base,lastX,velocity,slop=.012f;private int count,configured=3;private boolean down,horizontal,systemInGesture,manual;private long lastAt;
 public void pages(int fallback){configured=Math.max(1,Math.min(12,fallback));}
 public void manual(boolean on){manual=on;}
 public void slop(float value){slop=EffectMotion.clamp(value,.002f,.06f);}
 public int pageCount(){return !manual&&count>=2?count:configured;}
 public void position(float position){value=WallpaperPaging.offset(position);}
 public float value(){return value;}
 public boolean dragging(){return down&&horizontal&&!systemInGesture;}
 public void reset(){value=.5f;raw=Float.NaN;down=horizontal=systemInGesture=false;count=0;}
 public void system(float x,float step,long now){
  int n=WallpaperPaging.pages(step);if(n>=2)count=n;
  if(pageCount()<=1){value=.5f;raw=x;return;}
  if(!Float.isFinite(x)||x<0||x>1)return;
  boolean moving=Float.isFinite(raw)&&Math.abs(x-raw)>.0005f;
  if(moving){value=x;if(down)systemInGesture=true;}
  else if(!Float.isFinite(raw)&&n>=2&&!manual)value=x;
  raw=x;
 }
 public boolean touch(float x,float y,int action,long now){
  if(pageCount()<=1){value=.5f;down=false;return false;}
  if(action==0){down=true;horizontal=systemInGesture=false;startX=lastX=x;startY=y;base=value;lastAt=now;velocity=0;return false;}
  if(!down)return false;
  float dx=x-startX,dy=y-startY;
  if(action==2){long elapsed=now-lastAt;if(elapsed>0)velocity=(x-lastX)*1000/elapsed;lastX=x;lastAt=now;if(!horizontal&&Math.abs(dx)>slop&&Math.abs(dx)>Math.abs(dy)*1.15f)horizontal=true;
   if(horizontal&&!systemInGesture){value=EffectMotion.clamp(base-dx/Math.max(1,pageCount()-1),0,1);return true;}return false;}
  if(action==1||action==3){down=false;if(horizontal&&!systemInGesture){int intervals=Math.max(1,pageCount()-1);boolean fling=now-lastAt<120&&Math.abs(velocity)>.45f&&Math.abs(dx)>.025f;value=action==3?base:Math.abs(dx)>=.12f||fling?EffectMotion.clamp((Math.round(base*intervals)+(dx<0?1:-1))/(float)intervals,0,1):Math.round(base*intervals)/(float)intervals;return true;}}
  return false;
 }
}
