package dev.workshopvideo.core;

/** Playback settings and gesture math, independent of Android's media lifecycle. */
public final class PlayerControls {
    private float selectedSpeed=1f,zoom=1f;
    private boolean holding;
    public float selectedSpeed(){return selectedSpeed;}
    public float speed(){return holding?2f:selectedSpeed;}
    public boolean holding(){return holding;}
    public void setSpeed(float speed){selectedSpeed=clamp(speed,.25f,3f);}
    public void hold(boolean value){holding=value;}
    public float zoom(){return zoom;}
    public void setZoom(float value){zoom=clamp(value,1f,4f);}
    public void scale(float factor){if(Float.isFinite(factor)&&factor>0)setZoom(zoom*factor);}
    public static float clamp(float value,float low,float high){return Float.isFinite(value)?Math.max(low,Math.min(high,value)):low;}
    public static float verticalChange(float start,float current,int height){return height<=0?0:(start-current)/(height*.65f);}
    public static float brightness(float start,float change){return clamp(start+change,.02f,1f);}
    public static int volume(int start,int maximum,float change){return maximum<=0?0:Math.round(clamp(start+change*maximum,0,maximum));}
    public static int seekPosition(int progress,int maximum,int duration){return maximum<=0||duration<=0?0:(int)(Math.max(0,Math.min(maximum,progress))*(long)duration/maximum);}
    public static int seekProgress(int position,int duration,int maximum){return duration<=0||maximum<=0?0:(int)(Math.max(0,Math.min(duration,position))*(long)maximum/duration);}
    public static int swipeAxis(float dx,float dy,int slop) {
        if(Math.abs(dx)>slop&&Math.abs(dx)>Math.abs(dy)*1.2f)return 1;
        if(Math.abs(dy)>slop&&Math.abs(dy)>Math.abs(dx)*1.2f)return 2;
        return 0;
    }
    public static int swipePosition(int start,float dx,int width,int duration) {
        if(width<=0||duration<=0||!Float.isFinite(dx))return Math.max(0,Math.min(Math.max(0,duration),start));
        long change=Math.round(Math.max(-1d,Math.min(1d,dx/(double)width))*Math.min(60000,duration));
        return (int)Math.max(0L,Math.min((long)duration,start+change));
    }
    public static float[] fit(int viewWidth,int viewHeight,int videoWidth,int videoHeight,float zoom){
        if(viewWidth<=0||viewHeight<=0||videoWidth<=0||videoHeight<=0)return new float[]{1,1};
        float scale=Math.min(viewWidth/(float)videoWidth,viewHeight/(float)videoHeight)*clamp(zoom,1,4);
        return new float[]{videoWidth*scale/viewWidth,videoHeight*scale/viewHeight};
    }
    public static String time(int milliseconds){
        int total=Math.max(0,milliseconds)/1000,seconds=total%60,minutes=total/60%60,hours=total/3600;
        return hours>0?String.format(java.util.Locale.ROOT,"%d:%02d:%02d",hours,minutes,seconds):String.format(java.util.Locale.ROOT,"%02d:%02d",minutes,seconds);
    }
}
