package dev.workshopvideo;
import android.content.Context;import android.view.Surface;import java.io.File;import org.json.JSONObject;
final class WallpaperRenderer {
 interface Events {void rendered();void failed(String message);default void pagingAvailable(boolean available){}default void contentSize(int w,int h){}}
 static int connections,attached;static java.util.List<WallpaperRenderer> instances=new java.util.ArrayList<>();
 static void reset(){connections=attached=0;instances.clear();}
 final Events events;JSONObject options;boolean visible,connected;int closeCalls;Runnable finished;
 WallpaperRenderer(Context context,Events events){this.events=events;instances.add(this);}
 void surfaceFile(Surface surface,int w,int h,File file,JSONObject options,boolean visible){if(attached!=0)throw new AssertionError("EGL_BAD_ALLOC: window still attached to previous renderer");this.options=options;this.visible=visible;connected=true;connections++;attached++;}
 boolean hasFailed(){return false;}
 void configure(JSONObject options){this.options=options;}
 void visible(boolean visible){this.visible=visible;}
 void offset(float value,boolean immediate){}void offset(float value){}void powerSaving(boolean value){}void tilt(float x,float y){}void touch(float x,float y,int action){}void resize(int w,int h){}
 void closeAsync(Runnable finished){closeCalls++;this.finished=finished;}
 void closeAsync(){completeClose();}
 void completeClose(){if(connected){connected=false;attached--;}Runnable callback=finished;finished=null;if(callback!=null)callback.run();}
}
