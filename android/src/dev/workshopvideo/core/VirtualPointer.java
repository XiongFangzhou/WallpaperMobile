package dev.workshopvideo.core;

/**
 * The mouse a desktop wallpaper expects, on a phone. While a finger is down (and briefly after) the pointer is the
 * finger; when idle it rests where the phone's tilt and the desktop page put it, so cursor-driven parallax, eyes and
 * followers keep reacting without touching the screen. Coordinates are 0..1 of the screen, y down.
 */
public final class VirtualPointer {
    /** How long the pointer stays where the finger left it, and how long it then takes to drift back. */
    public static final long HOLD_MS=2500,RETURN_MS=700;
    /** Share of the screen the pointer travels for a full tilt, and for paging from the first to the last page. */
    public static final float TILT_REACH=.35f,PAGE_REACH=.5f;
    private VirtualPointer(){}

    /** Resting position from tilt (−1..1 each) and the desktop page offset (0..1, used only when paging is followed). */
    public static float[] idle(float tiltX,float tiltY,float pageOffset,boolean paging){
        float x=.5f+TILT_REACH*clamp(tiltX,-1,1)+(paging?(clamp(pageOffset,0,1)-.5f)*PAGE_REACH:0),y=.5f+TILT_REACH*clamp(tiltY,-1,1);
        return new float[]{clamp(x,0,1),clamp(y,0,1)};
    }

    /** Pointer for this frame: the finger while down or recently lifted, easing into the idle position afterwards. */
    public static float[] position(float touchX,float touchY,boolean down,long sinceTouchMs,boolean everTouched,float[] idle){
        if(down)return new float[]{touchX,touchY};
        if(!everTouched)return idle.clone();
        float t=sinceTouchMs<=HOLD_MS?0:clamp((sinceTouchMs-HOLD_MS)/(float)RETURN_MS,0,1);t=t*t*(3-2*t);
        return new float[]{touchX+(idle[0]-touchX)*t,touchY+(idle[1]-touchY)*t};
    }

    private static float clamp(float v,float lo,float hi){return Float.isFinite(v)?Math.max(lo,Math.min(hi,v)):(lo+hi)/2;}
}
