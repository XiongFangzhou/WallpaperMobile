package dev.workshopvideo.core;

/** Phase-preserving frame budget evaluated at real display vsync timestamps. */
public final class FramePacing {
    private double next;
    private long last=Long.MIN_VALUE;
    private int rate;
    public void reset(){last=Long.MIN_VALUE;rate=0;}
    public boolean render(long frameTimeNanos,int requestedFps){
        int fps=Math.max(15,Math.min(120,requestedFps));double period=1_000_000_000d/fps;
        if(last==Long.MIN_VALUE||frameTimeNanos<last||rate!=fps){rate=fps;last=frameTimeNanos;next=frameTimeNanos+period;return true;}
        last=frameTimeNanos;
        if(frameTimeNanos+250_000<next)return false;
        next+=Math.max(1,Math.floor((frameTimeNanos-next)/period)+1)*period;
        if(next<frameTimeNanos)next=frameTimeNanos+period;
        return true;
    }
}
