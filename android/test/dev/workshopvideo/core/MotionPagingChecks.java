package dev.workshopvideo.core;
import static dev.workshopvideo.core.CoreChecks.*;

final class MotionPagingChecks {
    static float[] rx(double a){float c=(float)Math.cos(a),s=(float)Math.sin(a);return new float[]{1,0,0,0,c,-s,0,s,c};}
    static float[] ry(double a){float c=(float)Math.cos(a),s=(float)Math.sin(a);return new float[]{c,0,s,0,1,0,-s,0,c};}
    static float[] rz(double a){float c=(float)Math.cos(a),s=(float)Math.sin(a);return new float[]{c,-s,0,s,c,0,0,0,1};}
    static float[] mul(float[] a,float[] b){float[] out=new float[9];for(int r=0;r<3;r++)for(int c=0;c<3;c++)for(int k=0;k<3;k++)out[r*3+c]+=a[r*3+k]*b[k*3+c];return out;}
    static boolean close(float a,float b){return Math.abs(a-b)<.0001f;}
    static void run(){
        for(double angle:new double[]{0,Math.toRadians(75),Math.toRadians(89.9),Math.PI/2,Math.toRadians(110)}){
            float[] base=mul(rz(.42),rx(angle));RelativeTilt tilt=new RelativeTilt();tilt.matrix(base,0);float[] up=tilt.matrix(mul(base,rx(.2)),0);check(Math.abs(up[0])<.0001f&&up[1]>.3f,"upright pitch stays on screen vertical axis at "+angle);float[] right=tilt.matrix(mul(base,ry(.2)),0);check(right[0]>.3f&&Math.abs(right[1])<.0001f,"upright horizontal tilt stays horizontal at "+angle);float[] twist=tilt.matrix(mul(base,rz(.2)),0);check(Math.abs(twist[0])<.0001f&&Math.abs(twist[1])<.0001f,"screen-normal twist causes no spurious pan");
        }
        RelativeTilt tilt=new RelativeTilt();float[] base=rx(1.4);tilt.matrix(base,0);float[] neutral=tilt.matrix(base,1);check(close(neutral[0],0)&&close(neutral[1],0),"display rotation recenters without an axis jump");float[] landscape=tilt.matrix(mul(base,rx(.2)),1);check(Math.abs(landscape[0])>.3f&&Math.abs(landscape[1])<.0001f,"landscape remaps device pitch to screen horizontal");tilt.reset();check(close(tilt.matrix(base,0)[0],0),"resume establishes fresh center");
        for(int turn=0;turn<4;turn++){tilt=new RelativeTilt();tilt.matrix(base,turn);float[] result=tilt.matrix(mul(base,rx(.2)),turn);float[] expected=turn==0?new float[]{0,1}:turn==1?new float[]{-1,0}:turn==2?new float[]{0,-1}:new float[]{1,0};check(Math.abs(result[0]-expected[0]*.3456f)<.001f&&Math.abs(result[1]-expected[1]*.3456f)<.001f,"pitch is remapped with correct sign at display rotation "+turn);}
        tilt=new RelativeTilt();tilt.matrix(ry(3.13),0);check(Math.abs(tilt.matrix(ry(-3.13),0)[0])<.05f,"relative matrix stays continuous across pi");check(tilt.matrix(new float[]{Float.NaN,0,0,0,1,0,0,0,1},0)==null,"invalid matrix rejected");
        tilt=new RelativeTilt();tilt.acceleration(new float[]{0,0,9.81f},0,.02f);float[] impulse=tilt.acceleration(new float[]{9.81f,0,9.81f},0,.02f);check(Math.abs(impulse[0])<.15f,"accelerometer fallback filters a brief translation impulse");

        LauncherPaging strict=new LauncherPaging();strict.pages(5);strict.system(0,.25f,0);
        for(long duration:new long[]{16,100,600}){strict.touch(.9f,.5f,0,0);strict.touch(.1f,.5f,2,duration);strict.touch(.1f,.5f,1,duration+10);check(strict.value()==0,"default paging does not predict a page from swipe speed "+duration);}
        strict.touch(.9f,.5f,0,1000);strict.system(.16f,.25f,1020);check(close(strict.value(),.16f),"background tracks actual in-flight launcher offset");strict.system(0,.25f,1100);strict.touch(.2f,.5f,1,1120);check(strict.value()==0,"aborted launcher page returns background to exact original position");
        LauncherPaging compat=new LauncherPaging();compat.pages(5);compat.gestureFallback(true);compat.system(1,-1,0);compat.touch(.7f,.5f,0,100);compat.touch(.63f,.5f,2,116);compat.touch(.63f,.5f,1,120);check(compat.value()==.75f,"quick short fling commits with both distance and recent speed");
        compat.system(.5f,.25f,200);compat.touch(.9f,.5f,0,300);compat.touch(.1f,.5f,2,320);compat.touch(.1f,.5f,1,340);check(compat.value()==.75f,"a previous system offset does not disable a later gesture without callbacks");
    }
}
