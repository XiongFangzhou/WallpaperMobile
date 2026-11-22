package dev.workshopvideo;

import android.view.Choreographer;
import dev.workshopvideo.core.FramePacing;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

/** One render callback per vsync, shared by decoding, offsets, touch and animation. */
final class WallpaperFrameClock implements Choreographer.FrameCallback {
    private final Choreographer choreographer;
    private final FramePacing pacing=new FramePacing();
    private final IntSupplier fps;
    private final BooleanSupplier running;
    private final Runnable render;
    private boolean pending;
    private long generation;
    WallpaperFrameClock(IntSupplier fps,BooleanSupplier running,Runnable render){this.choreographer=Choreographer.getInstance();this.fps=fps;this.running=running;this.render=render;}
    void request(){if(pending)return;pending=true;choreographer.postFrameCallback(this);}
    void cancel(){generation++;choreographer.removeFrameCallback(this);pending=false;pacing.reset();}
    @Override public void doFrame(long frameTimeNanos){
        if(!pending)return;pending=false;
        long epoch=generation;boolean draw=pacing.render(frameTimeNanos,fps.getAsInt());
        if(draw)render.run();
        if(epoch==generation&&(!draw||running.getAsBoolean()))request();
    }
}
