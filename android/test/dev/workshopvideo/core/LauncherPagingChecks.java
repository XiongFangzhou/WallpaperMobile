package dev.workshopvideo.core;
import static dev.workshopvideo.core.CoreChecks.*;
final class LauncherPagingChecks {
 static boolean close(float a,float b){return Math.abs(a-b)<.00001;}
 static void swipe(LauncherPaging p,float from,float to,long time){p.touch(from,.5f,0,time);p.touch(to,.5f,2,time+200);p.touch(to,.5f,1,time+300);}
 static void run(){
  LauncherPaging fixed=new LauncherPaging();fixed.pages(5);fixed.system(1,-1,1000);check(close(fixed.value(),.5f),"untrusted constant endpoint never pins initial wallpaper right");
  swipe(fixed,.8f,.2f,2000);check(close(fixed.value(),.75f),"relative fallback advances one of five pages");fixed.system(1,-1,2400);check(close(fixed.value(),.75f),"repeated fixed callback cannot undo swipe fallback");swipe(fixed,.8f,.2f,3000);check(close(fixed.value(),1),"fallback reaches last page");swipe(fixed,.2f,.8f,4000);check(close(fixed.value(),.75f),"fallback reverses from last page");
  fixed.touch(.4f,.4f,0,5000);fixed.touch(.4f,.9f,2,5200);fixed.touch(.4f,.9f,1,5300);check(close(fixed.value(),.75f),"vertical gestures do not change page");fixed.touch(.9f,.5f,0,6000);fixed.touch(.7f,.5f,2,6200);fixed.touch(.7f,.5f,3,6300);check(close(fixed.value(),.75f),"canceled gesture restores original page");
  LauncherPaging normal=new LauncherPaging();normal.system(0,.25f,1000);check(normal.pageCount()==5&&normal.value()==0,"valid launcher metadata initializes exact first page");normal.touch(.9f,.5f,0,2000);normal.system(.125f,.25f,2050);normal.touch(.5f,.5f,2,2200);check(close(normal.value(),.125f),"real system offsets take precedence without duplicate swipe delta");normal.system(.25f,.25f,2250);normal.touch(.1f,.5f,1,2300);check(close(normal.value(),.25f),"release does not override authoritative launcher segment");
  normal.system(1,.25f,3000);swipe(normal,.1f,.8f,4000);check(close(normal.value(),.75f),"launcher with valid step but frozen offset can still fall back");normal.system(.5f,.25f,4500);check(close(normal.value(),.5f),"real offsets regain authority immediately");normal.system(Float.NaN,-1,4600);check(close(normal.value(),.5f),"invalid metadata cannot poison current offset");
  for(int n:new int[]{2,3,5,8}){LauncherPaging p=new LauncherPaging();p.pages(n);p.system(0,0,1000);for(int i=0;i<n+2;i++)swipe(p,.9f,.1f,2000+i*1000);check(p.value()==1,"fallback right endpoint for "+n+" pages");for(int i=0;i<n+2;i++)swipe(p,.1f,.9f,20000+i*1000);check(p.value()==0,"fallback left endpoint for "+n+" pages");}
 }
}
