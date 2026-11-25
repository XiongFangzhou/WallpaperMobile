package dev.workshopvideo;

import dev.workshopvideo.core.VideoFiles;
import dev.workshopvideo.core.WorkshopDownload;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONObject;

/**
 * Keeps converted scenes current: each scene keeps its original workshop package, and when the converter or the
 * downloaded Wallpaper Engine assets are newer than what produced the saved scene, it is converted again in the
 * background. The old file stays until the new one has passed the same checks as a fresh download.
 */
final class SceneReconversion {
    private SceneReconversion(){}
    private static final ExecutorService worker=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"scene-reconvert");t.setPriority(Thread.MIN_PRIORITY);t.setDaemon(true);return t;});
    private static final AtomicBoolean queued=new AtomicBoolean();

    /** What produced a converted scene: converter version and engine asset manifest. */
    static void stamp(JSONObject record,long engine){VideoApp.put(record,"converter",SceneAdapter.VERSION);VideoApp.put(record,"engineManifest",engine);}
    private static String attempt(long engine){return SceneAdapter.VERSION+":"+engine;}

    static boolean outdated(VideoApp app,JSONObject job,long engine){
        if(!job.optString("state").equals("saved")||!job.optString("storage").equals("private"))return false;
        String id=job.optString("id");if(!id.matches("[0-9a-f]{32}")||!app.original(id).isFile())return false;
        if(job.optString("reconvertAttempt").equals(attempt(engine)))return false;
        return job.optInt("converter",0)<SceneAdapter.VERSION||engine!=0&&job.optLong("engineManifest",0)!=engine;
    }

    /** Queues a pass over all saved scenes; repeated calls while one is pending are merged. */
    static void schedule(VideoApp app){
        if(!queued.compareAndSet(false,true))return;
        worker.execute(()->{
            try{
                app.filesReady.get();long engine=EngineAssets.installed(app);
                // Scenes due for conversion are marked first: the library shows them as converting, not ready to set, until done.
                java.util.List<String> due=new java.util.ArrayList<>();
                synchronized(app){for(JSONObject listed:app.jobs()){String id=listed.optString("id");JSONObject job=app.job(id);boolean now=outdated(app,job,engine);if(now)due.add(id);
                    if(job.optBoolean("converting")!=now){if(now)VideoApp.put(job,"converting",true);else job.remove("converting");app.save(job);}}}
                app.changed();
                for(String id:due)reconvert(app,id,engine);
            }catch(Exception e){android.util.Log.w("SceneReconversion","pass stopped",e);}
            finally{queued.set(false);}
        });
    }

    private static void reconvert(VideoApp app,String id,long engine){
        File workspace=new File(app.getCacheDir(),"reconvert-"+id),stage=null;
        try{
            workspace.mkdirs();stage=File.createTempFile("scene-",".content",workspace);
            SceneDownload.Guard guard=()->{if(app.enqueued.contains(id)||Thread.currentThread().isInterrupted())throw new InterruptedException("下载任务正在使用这个作品");};
            SceneDownload.Prepared prepared;
            try(JsShaderTranslator translator=new JsShaderTranslator(app)){prepared=SceneDownload.prepare(app.original(id),stage,workspace,guard,translator);}
            if(prepared.kind().equals("scene"))WallpaperRenderer.verify(app,stage);
            String sha1=VideoFiles.hex(WorkshopDownload.hash(stage,"SHA-1"));long size=stage.length();
            synchronized(app){
                JSONObject record=app.job(id);if(!record.optString("state").equals("saved")||app.enqueued.contains(id))return;
                Files.move(stage.toPath(),app.video(id).toPath(),StandardCopyOption.REPLACE_EXISTING);stage=null;
                boolean sameKind=record.optString("kind").equals(prepared.kind());
                VideoApp.put(record,"kind",prepared.kind());VideoApp.put(record,"adapted",prepared.adapted());VideoApp.put(record,"adaptationReport",prepared.report());
                VideoApp.put(record,"effects",sameKind?merge(prepared.effects(),record.optJSONObject("effects")):prepared.effects());
                VideoApp.put(record,"size",size);VideoApp.put(record,"sha1",sha1);VideoApp.put(record,"compatible",true);VideoApp.put(record,"compatibilityReason","");
                VideoApp.put(record,"message","已用新版转换器更新");stamp(record,engine);record.remove("reconvertAttempt");record.remove("converting");app.save(record);
            }
            app.changed();android.util.Log.i("SceneReconversion","updated "+id);
        }catch(Exception|OutOfMemoryError e){
            // Keep the working older conversion and do not retry until the converter or engine assets change again.
            android.util.Log.w("SceneReconversion","kept previous conversion of "+id,e);
            if(!(e instanceof InterruptedException))synchronized(app){try{JSONObject record=app.job(id);VideoApp.put(record,"reconvertAttempt",attempt(engine));app.save(record);}catch(Exception ignored){}}
        }finally{
            synchronized(app){try{JSONObject record=app.job(id);if(record.has("converting")){record.remove("converting");app.save(record);}}catch(Exception ignored){}}app.changed();
            if(stage!=null)stage.delete();
            try{dev.workshopvideo.core.WorkshopAssets.clear(workspace);}catch(Exception ignored){}workspace.delete();
        }
    }

    /** The user's choices win over the new defaults, except per-layer values: layer indices may differ after conversion. */
    static JSONObject merge(JSONObject defaults,JSONObject chosen){
        JSONObject out;try{out=new JSONObject(defaults.toString());}catch(Exception e){return defaults;}
        if(chosen==null)return out;
        for(Iterator<String> keys=chosen.keys();keys.hasNext();){
            String key=keys.next();if(key.matches("(depth|opacity|layerX|layerY)\\d+"))continue;VideoApp.put(out,key,chosen.opt(key));
        }
        return EffectOptions.normalize(out);
    }
}
