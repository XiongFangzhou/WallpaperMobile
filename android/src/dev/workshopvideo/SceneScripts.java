package dev.workshopvideo;

import android.content.Context;
import androidx.javascriptengine.JavaScriptIsolate;
import androidx.javascriptengine.JavaScriptSandbox;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Runs a scene's Wallpaper Engine scripts (assets/scene-script-runtime.js) in the system WebView's isolated
 * JavaScript sandbox. The render thread never waits: each frame it applies the last finished script frame and
 * hands over the next one. Touch stands in for the mouse.
 */
final class SceneScripts implements AutoCloseable {
    private static final String RUNTIME="scene-script-runtime.js";
    final WallpaperContent.Scene scene;
    private final ExecutorService starter=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"scene-scripts");t.setDaemon(true);return t;});
    private volatile JavaScriptSandbox sandbox;private volatile JavaScriptIsolate isolate;private volatile boolean closed,failed;
    private ListenableFuture<String> pending;private double lastRuntime=-1;private long lastSubmit;
    private final List<JSONObject> events=new ArrayList<>();
    /** Particle objects stopped by scripts; read by the particle renderer. */
    final java.util.Set<Integer> stopped=java.util.concurrent.ConcurrentHashMap.newKeySet();

    SceneScripts(Context context,WallpaperContent.Scene scene){
        this.scene=scene;Context app=context.getApplicationContext();
        starter.execute(()->{
            try{
                if(!JavaScriptSandbox.isSupported())throw new IllegalStateException("系统 WebView 不支持隔离的 JavaScript 环境");
                JavaScriptSandbox box=JavaScriptSandbox.createConnectedInstanceAsync(app).get(20,TimeUnit.SECONDS);sandbox=box;
                JavaScriptIsolate js=box.createIsolate();
                String runtime;try(InputStream in=app.getAssets().open(RUNTIME)){runtime=new String(in.readAllBytes(),StandardCharsets.UTF_8);}
                js.evaluateJavaScriptAsync(runtime).get(20,TimeUnit.SECONDS);
                String init=js.evaluateJavaScriptAsync("wmInit("+JSONObject.quote(scene.scripts)+")").get(30,TimeUnit.SECONDS);
                android.util.Log.i("SceneScripts","started: "+init);
                if(closed){js.close();box.close();return;}
                isolate=js;
            }catch(Exception|LinkageError e){failed=true;android.util.Log.w("SceneScripts","scripts unavailable",e);}
        });
    }

    /** Touch event in scene coordinates (y up); type is "down", "up" or "move". */
    void pointer(String type,float x,float y,boolean click){
        try{JSONObject e=new JSONObject().put("type",type).put("x",x).put("y",y);if(click)e.put("click",true);if(events.size()<64)events.add(e);}catch(org.json.JSONException ignored){}
    }

    /** Called on the render thread before drawing. cursor is the current pointer in scene coordinates. */
    void frame(double runtime,float[] cursor,boolean down){
        JavaScriptIsolate js=isolate;if(js==null||closed||failed)return;
        if(pending!=null){
            if(!pending.isDone())return;
            try{apply(pending.get());}catch(Exception e){android.util.Log.w("SceneScripts","frame failed",e);}
            pending=null;
        }
        long now=System.nanoTime();if(now-lastSubmit<15_000_000L)return;lastSubmit=now;
        try{
            JSONObject input=new JSONObject().put("runtime",runtime).put("frametime",lastRuntime<0?0:Math.max(0,runtime-lastRuntime)).put("down",down);
            if(cursor!=null)input.put("cursor",new JSONArray().put(cursor[0]).put(cursor[1]));
            JSONArray list=new JSONArray();for(JSONObject e:events)list.put(e);events.clear();input.put("events",list);
            lastRuntime=runtime;pending=js.evaluateJavaScriptAsync("wmFrame("+JSONObject.quote(input.toString())+")");
        }catch(Exception e){failed=true;android.util.Log.w("SceneScripts","stopped",e);}
    }

    private void apply(String result)throws Exception {
        JSONObject out=new JSONObject(result);JSONObject layers=out.optJSONObject("layers");
        if(layers!=null)for(Iterator<String> keys=layers.keys();keys.hasNext();){
            String key=keys.next();JSONArray v=layers.getJSONArray(key);float[] s=new float[v.length()];for(int i=0;i<s.length;i++)s[i]=(float)v.optDouble(i,0);
            int id;try{id=Integer.parseInt(key);}catch(NumberFormatException e){continue;}
            scene.applyScript(id,s);if(s.length>11){if(s[11]<.5f)stopped.add(id);else stopped.remove(id);}
        }
        JSONArray errors=out.optJSONArray("errors");if(errors!=null&&errors.length()>0)android.util.Log.d("SceneScripts","script errors: "+errors);
    }

    boolean running(){return isolate!=null&&!failed;}

    @Override public void close(){
        closed=true;starter.shutdownNow();JavaScriptIsolate js=isolate;isolate=null;JavaScriptSandbox box=sandbox;sandbox=null;
        if(js!=null)try{js.close();}catch(RuntimeException ignored){}
        if(box!=null)try{box.close();}catch(RuntimeException ignored){}
    }
}
