package dev.workshopvideo;

import android.graphics.SurfaceTexture;
import android.view.Surface;
import android.view.TextureView;
import java.lang.reflect.*;
import java.io.File;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

/** Actual setup Activity methods, with a delayed native-window backend double. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,manifest=Config.NONE,application=android.app.Application.class)
public class PreviewLifecycleChecks {
    private WallpaperSetupActivity activity;
    private Surface surface;
    private SurfaceTexture texture;
    private void set(String name,Object value)throws Exception{Field f=WallpaperSetupActivity.class.getDeclaredField(name);f.setAccessible(true);f.set(activity,value);}
    private Object get(String name)throws Exception{Field f=WallpaperSetupActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(activity);}
    private void call(String name)throws Exception{Method m=WallpaperSetupActivity.class.getDeclaredMethod(name);m.setAccessible(true);m.invoke(activity);}
    @Before public void create()throws Exception{
        WallpaperRenderer.reset();activity=Robolectric.buildActivity(WallpaperSetupActivity.class).get();
        texture=new SurfaceTexture(0);surface=new Surface(texture);set("movieSurface",surface);set("movie",new TextureView(activity));set("source",new File("generated-preview.video"));
        set("effects",EffectOptions.video(new JSONObject().put("pan",true)));set("resumed",true);set("canVideo",true);call("startMovie");set("movieReady",true);
    }
    @After public void release()throws Exception{
        set("dead",true);call("stopMovie");for(WallpaperRenderer renderer:WallpaperRenderer.instances)renderer.completeClose();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();surface.release();texture.release();
    }
    @Test public void switchingScopeReconfiguresOneWindowInsteadOfReconnecting()throws Exception{
        WallpaperRenderer original=(WallpaperRenderer)get("movieRenderer");Method choose=WallpaperSetupActivity.class.getDeclaredMethod("changeScope",int.class);choose.setAccessible(true);
        for(int scope:new int[]{1,0,2,1,0}){choose.invoke(activity,scope);assertSame(original,get("movieRenderer"));assertEquals(scope!=1,original.options.optBoolean("pan"));assertEquals(scope!=1,original.options.getJSONObject("effects").optBoolean("pan"));assertTrue((Boolean)get("movieReady"));}
        assertEquals(1,WallpaperRenderer.connections);assertEquals(0,original.closeCalls);
    }
    @Test public void foregroundRoundTripPausesAndReusesTheSameWindow()throws Exception{
        WallpaperRenderer original=(WallpaperRenderer)get("movieRenderer");call("pauseMovie");assertFalse(original.visible);call("startMovie");assertTrue(original.visible);assertSame(original,get("movieRenderer"));assertEquals(1,WallpaperRenderer.connections);assertEquals(0,original.closeCalls);
    }
    @Test public void aNewPreviewWaitsForNativeReleaseCompletion()throws Exception{
        WallpaperRenderer original=(WallpaperRenderer)get("movieRenderer");call("stopMovie");call("startMovie");assertNull(get("movieRenderer"));assertEquals(1,WallpaperRenderer.connections);assertTrue((Boolean)get("releasingMovie"));
        original.completeClose();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();assertNotNull(get("movieRenderer"));assertNotSame(original,get("movieRenderer"));assertEquals(2,WallpaperRenderer.connections);assertFalse((Boolean)get("releasingMovie"));
    }
    @Test public void transientWindowFailureKeepsMediaEligibleAndOffersRetry()throws Exception{
        WallpaperRenderer original=(WallpaperRenderer)get("movieRenderer");original.events.failed("无法连接壁纸显示区域，EGL=0x3003");Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();assertTrue((Boolean)get("canVideo"));assertTrue((Boolean)get("videoSelected"));assertTrue((Boolean)get("previewFailed"));assertFalse((Boolean)get("movieReady"));original.completeClose();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();assertNull(get("movieRenderer"));set("previewFailed",false);call("startMovie");assertEquals(2,WallpaperRenderer.connections);
    }
}
