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

    /** Author settings (user.NAME options) this instance was started with; a change restarts the scripts. */
    final String settings;
    static String settings(org.json.JSONObject options){java.util.TreeMap<String,String> all=new java.util.TreeMap<>();for(Iterator<String> k=options.keys();k.hasNext();){String key=k.next();if(key.startsWith("user."))all.put(key,String.valueOf(options.opt(key)));}return all.toString();}
    /** scripts.json with the phone's choices for author properties: script parameters bound to them and engine.userProperties. */
    static String withSettings(String scripts,org.json.JSONObject options){
        try{
            JSONObject root=new JSONObject(scripts);JSONObject properties=root.optJSONObject("properties");
            if(properties!=null)for(Iterator<String> k=options.keys();k.hasNext();){String key=k.next();if(key.startsWith("user."))properties.put(key.substring(5),dev.workshopvideo.core.UserValues.forScripts(options.opt(key)));}
            JSONArray objects=root.optJSONArray("objects");
            if(objects!=null)for(int i=0;i<objects.length();i++){
                JSONObject all=objects.getJSONObject(i).optJSONObject("scripts");if(all==null)continue;
                for(Iterator<String> p=all.keys();p.hasNext();){JSONObject values=all.getJSONObject(p.next()).optJSONObject("properties");if(values==null)continue;
                    for(Iterator<String> n=values.keys();n.hasNext();){String name=n.next();JSONObject v=values.optJSONObject(name);if(v!=null&&v.opt("user") instanceof String user&&options.has("user."+user))v.put("value",dev.workshopvideo.core.UserValues.forScripts(options.opt("user."+user)));
                        else if(v!=null&&v.opt("user") instanceof JSONObject cond&&options.has("user."+cond.optString("name")))v.put("value",String.valueOf(options.opt("user."+cond.optString("name"))).equals(String.valueOf(cond.opt("condition"))));}}
            }
            return root.toString();
        }catch(Exception invalid){return scripts;}
    }
    /**
     * The system binds one sandbox per process, so the live wallpaper and a preview (same process) share it; each scene
     * gets its own isolate. The sandbox closes when its last user does.
     */
    private static final Object SHARED=new Object();private static JavaScriptSandbox shared;private static int users;
    private static JavaScriptSandbox acquire(Context app)throws Exception{
        synchronized(SHARED){if(shared==null)shared=JavaScriptSandbox.createConnectedInstanceAsync(app).get(20,TimeUnit.SECONDS);users++;return shared;}
    }
    /** broken: the sandbox failed (its process died), so the next user binds a new one. */
    private static void release(JavaScriptSandbox box,boolean broken){
        synchronized(SHARED){if(box!=shared)return;users=Math.max(0,users-1);if(users==0||broken){try{box.close();}catch(RuntimeException ignored){}shared=null;users=0;}}
    }
    SceneScripts(Context context,WallpaperContent.Scene scene){this(context,scene,new org.json.JSONObject());}
    SceneScripts(Context context,WallpaperContent.Scene scene,org.json.JSONObject options){
        this.scene=scene;Context app=context.getApplicationContext();java.io.File ext=app.getExternalFilesDir(null);trace=ext!=null&&new java.io.File(ext,"trace").isFile()?ext:null;replayFile=new java.io.File(app.getFilesDir(),"replay-frame.json");settings=settings(options);String source=withSettings(scene.scripts,options);
        for(Iterator<String> k=options.keys();k.hasNext();){String key=k.next();if(key.startsWith("user."))sentUser.put(key.substring(5),dev.workshopvideo.core.UserValues.forScripts(options.opt(key)));}
        starter.execute(()->{
            try{
                if(!JavaScriptSandbox.isSupported())throw new IllegalStateException("系统 WebView 不支持隔离的 JavaScript 环境");
                JavaScriptSandbox box=acquire(app);sandbox=box;
                JavaScriptIsolate js;try{js=box.createIsolate();}catch(RuntimeException dead){sandbox=null;release(box,true);throw dead;}
                String runtime;try(InputStream in=app.getAssets().open(RUNTIME)){runtime=new String(dev.workshopvideo.core.VideoFiles.readAll(in),StandardCharsets.UTF_8);}
                js.evaluateJavaScriptAsync(runtime).get(20,TimeUnit.SECONDS);
                String init=js.evaluateJavaScriptAsync("wmInit("+JSONObject.quote(source)+")").get(30,TimeUnit.SECONDS);
                android.util.Log.i("SceneScripts","started: "+init);
                if(closed){js.close();if(sandbox==box){sandbox=null;release(box,false);}return;}
                isolate=js;
            }catch(Exception|LinkageError e){failed=true;android.util.Log.w("SceneScripts","scripts unavailable",e);}
        });
    }

    /** Touch event in scene coordinates (y up); type is "down", "up" or "move". */
    void pointer(String type,float x,float y,boolean click){
        try{JSONObject e=new JSONObject().put("type",type).put("x",x).put("y",y);if(click)e.put("click",true);if(events.size()<64)events.add(e);}catch(org.json.JSONException ignored){}
    }

    /** Called on the render thread before drawing. cursor is the current pointer in scene coordinates. */
    /** Author settings last sent to the scripts (name → value as scripts see it). */
    private final java.util.Map<String,Object> sentUser=new java.util.HashMap<>();private JSONObject pendingUser;
    /** Queues changed author settings (options user.NAME) for applyUserProperties; called every frame, cheap when unchanged. */
    void user(JSONObject options){lastOptions=options;
        JSONObject changed=new JSONObject();
        for(Iterator<String> k=options.keys();k.hasNext();){String key=k.next();if(!key.startsWith("user."))continue;String name=key.substring(5);Object v=dev.workshopvideo.core.UserValues.forScripts(options.opt(key));
            if(!java.util.Objects.equals(String.valueOf(sentUser.get(name)),String.valueOf(v))){sentUser.put(name,v);try{changed.put(name,v);}catch(org.json.JSONException ignored){}}}
        if(changed.length()==0)return;
        if(pendingUser==null)pendingUser=changed;else for(Iterator<String> k=changed.keys();k.hasNext();){String n=k.next();try{pendingUser.put(n,changed.opt(n));}catch(org.json.JSONException ignored){}}
    }
    void frame(double runtime,float[] cursor,boolean down){
        // Diagnostics without a script engine (e.g. old emulators): replay a traced frame (trace/replay-frame.json) once.
        if(failed&&trace!=null&&!replayed){replayed=true;java.io.File f=replayFile.isFile()?replayFile:new java.io.File(trace,"replay-frame.json");
            if(f.isFile())try(java.io.InputStream in=new java.io.FileInputStream(f)){apply(new JSONObject(new String(dev.workshopvideo.core.VideoFiles.readAll(in),StandardCharsets.UTF_8)).getJSONObject("output").toString());android.util.Log.i("SceneScripts","replayed "+f);}catch(Exception e){android.util.Log.w("SceneScripts","replay failed",e);}}
        JavaScriptIsolate js=isolate;if(js==null||closed||failed)return;
        if(pending!=null){
            if(!pending.isDone())return;
            try{apply(pending.get());}catch(Exception e){android.util.Log.w("SceneScripts","frame failed",e);}
            pending=null;
        }
        long now=System.nanoTime();if(now-lastSubmit<15_000_000L)return;lastSubmit=now;
        try{
            JSONObject input=new JSONObject().put("timeOfDay",java.time.LocalTime.now().toNanoOfDay()/8.64e13).put("runtime",runtime).put("frametime",lastRuntime<0?0:Math.max(0,runtime-lastRuntime)).put("down",down);
            if(pendingUser!=null){input.put("user",pendingUser);pendingUser=null;}
            if(cursor!=null)input.put("cursor",new JSONArray().put(cursor[0]).put(cursor[1]));
            JSONArray list=new JSONArray();for(JSONObject e:events)list.put(e);events.clear();input.put("events",list);
            lastRuntime=runtime;if(trace!=null&&traced<3)traceInput=input.toString();pending=js.evaluateJavaScriptAsync("wmFrame("+JSONObject.quote(input.toString())+")");
        }catch(Exception e){failed=true;android.util.Log.w("SceneScripts","stopped",e);}
    }

    /** Opt-in diagnostics (a file named "trace" in the app's external files folder, created over adb): the first script
     *  frames and the resulting text-layer state are written next to it. */
    private final java.io.File trace;private int traced;private boolean replayed;private final java.io.File replayFile;private String traceInput;private volatile JSONObject lastOptions;
    private void trace(String result){
        try(java.io.FileWriter w=new java.io.FileWriter(new java.io.File(trace,"script-frame-"+traced+".json"))){
            JSONArray state=new JSONArray();JSONObject o=lastOptions==null?new JSONObject():lastOptions;
            for(WallpaperContent.Layer l:scene.layers)if(l.text!=null)state.put(new JSONObject().put("id",l.objectId).put("name",l.name).put("value",l.text.value).put("x",l.x).put("y",l.y).put("w",l.w).put("h",l.h).put("visible",l.visible).put("shown",l.shown(o,scene.properties)).put("opacity",l.opacity(o)).put("group",l.group).put("scriptPlaced",l.scriptPlaced).put("bitmap",l.bitmap==null?"null":l.bitmap.getWidth()+"x"+l.bitmap.getHeight()));
            w.write(new JSONObject().put("input",traceInput==null?"":traceInput).put("output",new JSONObject(result)).put("texts",state).put("width",scene.width).put("height",scene.height).toString());
        }catch(Exception ignored){}
        traced++;
    }
    private void apply(String result)throws Exception {
        JSONObject out=new JSONObject(result);JSONObject layers=out.optJSONObject("layers");
        if(layers!=null)for(Iterator<String> keys=layers.keys();keys.hasNext();){
            String key=keys.next();JSONArray v=layers.getJSONArray(key);float[] s=new float[v.length()];for(int i=0;i<s.length;i++)s[i]=(float)v.optDouble(i,0);
            int id;try{id=Integer.parseInt(key);}catch(NumberFormatException e){continue;}
            scene.applyScript(id,s);if(s.length>11){if(s[11]<.5f)stopped.add(id);else stopped.remove(id);}
        }
        JSONObject fx=out.optJSONObject("fx");
        if(fx!=null)for(Iterator<String> ids=fx.keys();ids.hasNext();){String id=ids.next();JSONObject values=fx.optJSONObject(id);if(values==null)continue;int objectId;try{objectId=Integer.parseInt(id);}catch(NumberFormatException e){continue;}
            for(Iterator<String> keys=values.keys();keys.hasNext();){String key=keys.next();JSONArray v=values.optJSONArray(key);if(v==null||v.length()<1||v.length()>4)continue;float[] f=new float[v.length()];for(int i=0;i<f.length;i++)f[i]=(float)v.optDouble(i,0);scene.applyEffectValue(objectId,key,f);}}
        JSONArray bloom=out.optJSONArray("bloom");if(bloom!=null&&bloom.length()>=6)scene.applyBloom(bloom.optDouble(0,0)>.5,(float)bloom.optDouble(1,Double.NaN),(float)bloom.optDouble(2,Double.NaN),new float[]{(float)bloom.optDouble(3,1),(float)bloom.optDouble(4,1),(float)bloom.optDouble(5,1)});
        JSONArray camera=out.optJSONArray("camera");if(camera!=null&&camera.length()>=3)scene.applyCamera((float)camera.optDouble(0,0),(float)camera.optDouble(1,0),(float)camera.optDouble(2,1));
        JSONObject texts=out.optJSONObject("texts");
        if(texts!=null)for(java.util.Iterator<String> k=texts.keys();k.hasNext();){String id=k.next();JSONArray t=texts.optJSONArray(id);if(t==null||t.length()<5)continue;
            try{scene.applyText(Integer.parseInt(id),t.optString(0),(float)t.optDouble(1,Double.NaN),new float[]{(float)t.optDouble(2,1),(float)t.optDouble(3,1),(float)t.optDouble(4,1)});}catch(NumberFormatException ignored){}}
        JSONArray errors=out.optJSONArray("errors");if(errors!=null&&errors.length()>0)android.util.Log.d("SceneScripts","script errors: "+errors);
        if(trace!=null&&traced<3)trace(result);
    }

    boolean running(){return isolate!=null&&!failed;}

    @Override public void close(){
        closed=true;starter.shutdownNow();JavaScriptIsolate js=isolate;isolate=null;JavaScriptSandbox box=sandbox;sandbox=null;
        if(js!=null)try{js.close();}catch(RuntimeException ignored){}
        if(box!=null)release(box,false);
    }
}
