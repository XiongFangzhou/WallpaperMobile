package dev.workshopvideo.core;

final class PlayerControlsChecks {
    private static boolean near(float a,float b){return Math.abs(a-b)<.0001f;}
    static void run(){
        PlayerControls controls=new PlayerControls();
        controls.setSpeed(1.5f);controls.hold(true);
        CoreChecks.check(near(controls.speed(),2)&&near(controls.selectedSpeed(),1.5f),"hold uses 2x without changing selected speed");
        controls.hold(false);CoreChecks.check(near(controls.speed(),1.5f),"release restores user's previous speed");
        controls.hold(false);CoreChecks.check(near(controls.speed(),1.5f),"cancel and second release preserve speed");
        controls.setSpeed(.75f);controls.hold(true);controls.hold(false);CoreChecks.check(near(controls.speed(),.75f),"hold release restores a slow playback speed");
        controls.setSpeed(3);controls.hold(true);CoreChecks.check(near(controls.speed(),2),"hold remains exactly 2x at higher selected speeds");controls.hold(false);
        controls.scale(2);controls.scale(2);controls.scale(2);CoreChecks.check(near(controls.zoom(),4),"pinch upper bound");
        controls.scale(.01f);CoreChecks.check(near(controls.zoom(),1),"pinch out returns to fit size");
        controls.scale(Float.NaN);controls.scale(-1);CoreChecks.check(near(controls.zoom(),1),"invalid pinch input does not corrupt transform");
        float[] portrait=PlayerControls.fit(1080,2400,1920,1080,1);
        CoreChecks.check(near(portrait[0],1)&&near(portrait[1],.253125f),"landscape video fits portrait without stretching");
        float[] landscape=PlayerControls.fit(2400,1080,1920,1080,1);
        CoreChecks.check(near(landscape[0],.8f)&&near(landscape[1],1),"rotating refits video and preserves aspect");
        float[] tall=PlayerControls.fit(2400,1080,1080,1920,1);
        CoreChecks.check(near(tall[0],.253125f)&&near(tall[1],1),"portrait video fits landscape without stretching");
        float[] notReady=PlayerControls.fit(0,0,0,0,1);CoreChecks.check(near(notReady[0],1)&&near(notReady[1],1),"unprepared or zero-sized surface does not divide by zero");
        CoreChecks.check(near(PlayerControls.brightness(.5f,-1),.02f)&&near(PlayerControls.brightness(.5f,1),1),"brightness stays visible and inside window bounds");
        CoreChecks.check(PlayerControls.volume(7,15,-2)==0&&PlayerControls.volume(7,15,2)==15,"volume clamp supports mute and max volume");
        CoreChecks.check(PlayerControls.volume(7,0,1)==0,"fixed-volume device remains valid");
        CoreChecks.check(PlayerControls.verticalChange(500,200,1000)>0&&PlayerControls.verticalChange(200,500,1000)<0,"up swipe raises level and down swipe lowers it");
        CoreChecks.check(PlayerControls.seekPosition(5000,10000,7200000)==3600000,"long video seek avoids integer overflow");
        CoreChecks.check(PlayerControls.seekProgress(3600000,7200000,10000)==5000,"long video seek progress roundtrip");
        CoreChecks.check(PlayerControls.seekPosition(10001,10000,123456)==123456&&PlayerControls.seekPosition(-1,10000,123456)==0,"seek cannot leave video range");
        CoreChecks.check(PlayerControls.seekProgress(1000,0,10000)==0,"seek before media preparation is safe");
        CoreChecks.check(PlayerControls.time(3723000).equals("1:02:03")&&PlayerControls.time(65000).equals("01:05"),"player timestamp formatting");
        CoreChecks.check(PlayerControls.swipePosition(120000,500,1000,3600000)==150000,"right swipe advances thirty seconds over half the screen");
        CoreChecks.check(PlayerControls.swipePosition(120000,-500,1000,3600000)==90000,"left swipe rewinds thirty seconds over half the screen");
        CoreChecks.check(PlayerControls.swipePosition(5000,-1000,1000,60000)==0,"rewind clamps to beginning");
        CoreChecks.check(PlayerControls.swipePosition(55000,1000,1000,60000)==60000,"advance clamps to ending");
        CoreChecks.check(PlayerControls.swipePosition(5000,500,1000,10000)==10000,"short video gesture uses video duration");
        CoreChecks.check(PlayerControls.swipePosition(120000,2000,1000,3600000)==180000,"overswipe limited to one sixty-second gesture");
        CoreChecks.check(PlayerControls.swipePosition(Integer.MAX_VALUE-1000,1000,1000,Integer.MAX_VALUE)==Integer.MAX_VALUE,"gesture seek never overflows long videos");
        CoreChecks.check(PlayerControls.swipePosition(120000,0,1000,3600000)==120000,"no horizontal movement keeps current position");
        CoreChecks.check(PlayerControls.swipePosition(120000,100,0,3600000)==120000,"zero-sized surface cannot change position");
        CoreChecks.check(PlayerControls.swipePosition(120000,Float.NaN,1000,3600000)==120000,"invalid gesture leaves position intact");
        CoreChecks.check(PlayerControls.swipeAxis(40,8,10)==1&&PlayerControls.swipeAxis(-40,8,10)==1,"both horizontal directions select seeking");
        CoreChecks.check(PlayerControls.swipeAxis(8,40,10)==2&&PlayerControls.swipeAxis(8,-40,10)==2,"vertical gestures keep brightness or volume mode");
        CoreChecks.check(PlayerControls.swipeAxis(5,4,10)==0,"tap jitter cannot seek or change levels");
        CoreChecks.check(PlayerControls.swipeAxis(40,40,10)==0,"ambiguous diagonal motion waits for clear direction");
    }
}
