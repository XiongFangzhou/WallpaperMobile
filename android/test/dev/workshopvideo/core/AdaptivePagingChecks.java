package dev.workshopvideo.core;
import static dev.workshopvideo.core.CoreChecks.*;

final class AdaptivePagingChecks {
    private static LauncherPaging compatible(){LauncherPaging p=new LauncherPaging();p.pages(5);p.gestureFallback(true);return p;}
    private static boolean close(float a,float b){return Math.abs(a-b)<.00001f;}
    private static void swipe(LauncherPaging p,float from,float to,long at,long duration){p.touch(from,.5f,0,at);p.touch(to,.5f,2,at+duration);p.touch(to,.5f,1,at+duration+1);}
    static void run(){
        LauncherPaging fixed=compatible();fixed.system(1,-1,0);swipe(fixed,.65f,.40f,100,120);check(close(fixed.value(),.75f),"ordinary quarter-screen swipe advances without a 45-percent drag");fixed.system(1,-1,230);check(close(fixed.value(),.75f),"fixed endpoint cannot undo a compatibility page");
        LauncherPaging once=compatible();once.system(0,.25f,0);once.system(.5f,.25f,10);swipe(once,.65f,.4f,1000,120);check(close(once.value(),.75f),"past system movement never permanently disables compatibility");
        LauncherPaging current=compatible();current.system(0,.25f,0);current.touch(.8f,.5f,0,100);current.touch(.5f,.5f,2,120);check(current.value()>0,"finger tracks before launcher reports its offset");current.system(.13f,.25f,125);check(close(current.value(),.13f),"actual current-gesture offset takes over provisional finger displacement");current.touch(.2f,.5f,2,130);check(close(current.value(),.13f),"finger delta is not added twice after system takes over");current.system(0,.25f,140);current.touch(.2f,.5f,1,150);check(current.value()==0,"actual cancelled launcher page overrides large guessed displacement");
        swipe(current,.7f,.45f,1000,150);check(close(current.value(),.25f),"a later gesture falls back if continuous callbacks disappear");current.system(.5f,.25f,1200);check(close(current.value(),.5f),"late actual destination corrects compatibility estimate");
        for(long duration:new long[]{16,80,600}){LauncherPaging p=compatible();swipe(p,.7f,.62f,100,duration);check(p.value()==.5f,"short movement returns to base regardless of speed "+duration);swipe(p,.7f,.45f,1000,duration);check(p.value()==.75f,"normal swipe advances one page independently of speed "+duration);}
        LauncherPaging coalesced=compatible();coalesced.touch(.7f,.5f,0,0);check(coalesced.touch(.4f,.5f,1,40)&&coalesced.value()==.75f,"DOWN-UP-only launcher gesture can advance without MOVE events");
        LauncherPaging returned=compatible();returned.position(.4f);returned.touch(.8f,.5f,0,0);returned.touch(.3f,.5f,2,100);returned.touch(.75f,.5f,1,200);check(close(returned.value(),.4f),"drag out and return restores exact unsnapped starting position");
        returned.touch(.8f,.5f,0,300);returned.touch(.2f,.5f,2,350);returned.touch(.2f,.5f,3,400);check(close(returned.value(),.4f),"cancel restores original position rather than committing guessed page");
        swipe(returned,.7f,.4f,500,150);check(close(returned.value(),.65f),"compatibility advances a constant segment from a fractional system position");
        LauncherPaging changedDirection=compatible();changedDirection.touch(.8f,.2f,0,0);changedDirection.touch(.75f,.2f,2,20);changedDirection.touch(.55f,.8f,1,150);check(changedDirection.value()==.5f,"initial horizontal jitter cannot commit a later vertical gesture");
        LauncherPaging strict=new LauncherPaging();strict.pages(5);strict.system(0,.25f,0);swipe(strict,.8f,.2f,100,120);check(strict.value()==0,"explicit system-only mode still ignores touch estimates");
        LauncherPaging vertical=compatible();vertical.touch(.8f,.2f,0,0);vertical.touch(.5f,.6f,2,50);vertical.touch(.5f,.6f,1,100);check(vertical.value()==.5f,"mostly vertical launcher gestures do not scroll background");
        LauncherPaging edges=compatible();edges.position(1);swipe(edges,.7f,.4f,100,100);check(edges.value()==1,"forward swipe remains clamped at final background page");swipe(edges,.4f,.7f,500,100);check(edges.value()==.75f,"reverse swipe leaves final page naturally");
    }
}
