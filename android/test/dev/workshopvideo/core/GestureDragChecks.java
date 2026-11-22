package dev.workshopvideo.core;
import static dev.workshopvideo.core.CoreChecks.*;
final class GestureDragChecks {
 static void settle(GestureDrag d){for(int i=0;i<90;i++)d.step(.016f);}
 static void run(){
  GestureDrag a=new GestureDrag(),b=new GestureDrag();a.touch(.1f,.2f,0);b.touch(.7f,.8f,0);settle(a);settle(b);check(a.x()==0&&b.y()==0,"stationary press never jumps to absolute pointer");
  a.touch(.3f,.3f,2);b.touch(.9f,.9f,2);settle(a);settle(b);check(Math.abs(a.x()-b.x())<.00001f&&Math.abs(a.y()-b.y())<.00001f,"equal swipe displacement independent of starting coordinates");check(a.x()>.39f&&a.y()>.19f,"drag follows displacement direction");
  float before=a.x();a.touch(.8f,.8f,0);a.step(.016f);check(Math.abs(a.x()-before)<.00001,"starting another gesture preserves current smoothed offset");
  a.touch(.803f,.801f,2);settle(a);check(Math.abs(a.x()-before)<.00001,"small jitter ignored");a.touch(.8f,.8f,1);a.step(.016f);check(a.x()>0&&a.x()<before,"release returns smoothly rather than snapping");settle(a);check(a.x()<.001f,"release returns to neutral");
  b.touch(1,0,3);settle(b);check(Math.abs(b.x())<.001f&&Math.abs(b.y())<.001f,"cancel returns to neutral");a.reset();a.touch(Float.NaN,Float.POSITIVE_INFINITY,0);a.touch(1,1,2);settle(a);check(Float.isFinite(a.x())&&a.x()<=1,"nonfinite input and extremes stay bounded");
  check(!WallpaperGeometry.canPage(1080,2400,1080,2400,false,true,1,.5f),"same-as-screen content has no inert paging control");
  check(WallpaperGeometry.canPage(1080,2400,1080,2400,false,true,1.12f,.5f),"zoom overscan enables paging");
  check(WallpaperGeometry.canPage(1920,1080,1080,2400,false,true,1,.5f),"wide crop enables paging");
  check(!WallpaperGeometry.canPage(1920,1080,1080,2400,true,true,1.12f,.5f)&&!WallpaperGeometry.canPage(1920,1080,1080,2400,false,false,1.12f,.5f),"fit and disabled paging hide ineffective simulation");
 }
}
