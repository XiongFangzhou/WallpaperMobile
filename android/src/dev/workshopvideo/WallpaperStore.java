package dev.workshopvideo;

import android.content.*;
import dev.workshopvideo.core.WallpaperFiles;
import dev.workshopvideo.core.WallpaperScopes;
import org.json.*;
import java.io.*;
import java.util.*;

/** Pinned, independent home/lock videos. The system still owns component selection. */
final class WallpaperStore {
    static final String PREFS="wallpaper";
    static android.content.SharedPreferences prefs(Context c){return c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);}
    static JSONObject read(Context c,String key){try{return new JSONObject(prefs(c).getString(key,"{}"));}catch(Exception e){return new JSONObject();}}
    static File directory(Context c){File root=new File(c.getFilesDir(),"wallpaper");root.mkdirs();return root;}
    static File file(Context c,JSONObject config){return WallpaperFiles.path(directory(c),config.optString("file"));}
    private static boolean available(Context c,JSONObject config){try{return file(c,config).isFile();}catch(Exception e){return false;}}
    static JSONObject active(Context c,int screen){String key=WallpaperScopes.activeKey(screen);if(prefs(c).contains(key))return read(c,key);JSONObject legacy=read(c,"active");return (legacy.optInt("which",3)&screen)!=0?legacy:new JSONObject();}
    /** Only the system preview can use a candidate; a running engine reads its committed screen. */
    static JSONObject engineConfiguration(Context c,int screen,boolean preview){return preview?read(c,"pending"):active(c,screen);}
    static boolean matchesActive(Context c,int targets,String file){if(file.isEmpty()||targets<1||targets>3)return false;for(int screen:new int[]{1,2})if((targets&screen)!=0&&!file.equals(active(c,screen).optString("file")))return false;return true;}
    static boolean matchesRendered(Context c,int targets,String file){if(file.isEmpty()||targets<1||targets>3)return false;for(int screen:new int[]{1,2})if((targets&screen)!=0&&!file.equals(prefs(c).getString("renderedFile"+screen,"")))return false;return true;}
    private static JSONObject snapshot(JSONObject config,int which) throws JSONException {JSONObject out=new JSONObject();for(String key:new String[]{"file","title","fit","pan","sourceKey","effects"})if(config.has(key))out.put(key,config.get(key));out.put("which",which);boolean pan=(which&1)!=0&&(!out.optBoolean("fit")||out.optJSONObject("effects")!=null&&out.optJSONObject("effects").optString("alignment").equals("free"))&&out.optBoolean("pan");out.put("pan",pan);if(out.optJSONObject("effects")!=null){JSONObject options=new JSONObject(out.getJSONObject("effects").toString());options.put("pan",pan);out.put("effects",options);}return out;}
    private static void migrate(Context c,SharedPreferences.Editor edit) throws JSONException {
        JSONObject legacy=read(c,"active");for(int screen:new int[]{1,2})if(!prefs(c).contains(WallpaperScopes.activeKey(screen))&&(legacy.optInt("which",3)&screen)!=0&&available(c,legacy))edit.putString(WallpaperScopes.activeKey(screen),snapshot(legacy,screen).toString());
        edit.remove("previous").remove("previousHome").remove("previousLock").remove("previousComponent");
    }
    static synchronized JSONObject stage(Context c,File source,String title,boolean fit,boolean pan,int which) throws Exception {return stage(c,source,title,fit,pan,which,null);}
    static synchronized JSONObject stage(Context c,File source,String title,boolean fit,boolean pan,int which,JSONObject effects) throws Exception {
        File target=WallpaperFiles.copy(directory(c),source);
        try{
            JSONObject config=new JSONObject();config.put("file",target.getName());config.put("title",title);config.put("fit",fit);config.put("pan",pan);config.put("sourceKey",sourceKey(source));config.put("which",which);if(effects!=null)config.put("effects",effects);
            config.put("beforeMask",installedMask(c));config.put("beforeIds",systemIds(c));
            SharedPreferences.Editor edit=prefs(c).edit();migrate(c,edit);
            if(!edit.putString("pending",config.toString()).commit())throw new IOException("无法保存壁纸配置。");
            cleanup(c);return config;
        }catch(Exception problem){target.delete();throw problem;}
    }
    static synchronized void activate(Context c,JSONObject config) throws Exception {
        if(!available(c,config))throw new IOException("壁纸视频不存在。");
        int installed=installedMask(c),targets=WallpaperScopes.confirmedTargets(config.optInt("which",3),installed);
        if(targets==0)throw new IOException("系统尚未在所选屏幕启用 WallpaperMobile，请在系统确认页选择相同范围。");
        SharedPreferences.Editor save=prefs(c).edit();migrate(c,save);
        for(int screen:new int[]{1,2}){
            JSONObject current=active(c,screen);
            if((targets&screen)!=0){
                save.putString(WallpaperScopes.activeKey(screen),snapshot(config,screen).toString());
            }else if(!prefs(c).contains(WallpaperScopes.activeKey(screen))&&!available(c,current)){
                save.putString(WallpaperScopes.activeKey(screen),"{}");
            }
        }
        save.putString("active",snapshot(config,targets).toString());
        if(read(c,"pending").optString("file").equals(config.optString("file")))save.remove("pending");
        if(!save.commit())throw new IOException("无法保存壁纸选择。");
        // Decoders may still hold old files: cleanup happens only at the next staging.
    }
    static synchronized void cancel(Context c,JSONObject expected){
        if(read(c,"pending").optString("file").equals(expected.optString("file")))prefs(c).edit().remove("pending").commit();
        cleanup(c);
    }
    static String sourceKey(File file){return file.getAbsolutePath()+":"+file.length()+":"+file.lastModified();}
    static int installedMask(Context c){
        android.app.WallpaperManager manager=android.app.WallpaperManager.getInstance(c);int mask=0;
        android.content.ComponentName service=new android.content.ComponentName(c,VideoWallpaperService.class);
        android.app.WallpaperInfo home=manager.getWallpaperInfo();if(home!=null&&service.equals(home.getComponent()))mask|=1;
        if(android.os.Build.VERSION.SDK_INT>=34){android.app.WallpaperInfo lock=manager.getWallpaperInfo(android.app.WallpaperManager.FLAG_LOCK);if(lock!=null&&service.equals(lock.getComponent()))mask|=2;}
        return mask;
    }
    static boolean installed(Context c){return installedMask(c)!=0;}
    static String systemIds(Context c){android.app.WallpaperManager manager=android.app.WallpaperManager.getInstance(c);return manager.getWallpaperId(1)+":"+manager.getWallpaperId(2);}
    static synchronized void record(Context c,String stage,JSONObject config,String detail){
        try{JSONArray old=new JSONArray(prefs(c).getString("diagnostics","[]")),next=new JSONArray();for(int i=Math.max(0,old.length()-23);i<old.length();i++)next.put(old.get(i));JSONObject line=new JSONObject();line.put("time",System.currentTimeMillis());line.put("stage",stage);line.put("file",config.optString("file"));line.put("detail",detail);next.put(line);prefs(c).edit().putString("diagnostics",next.toString()).apply();}catch(Exception ignored){}
    }
    static boolean serviceReached(Context c,String file){
        try{JSONArray events=new JSONArray(prefs(c).getString("diagnostics","[]"));for(int i=events.length()-1;i>=0;i--){JSONObject event=events.getJSONObject(i);if(!file.equals(event.optString("file")))continue;String stage=event.optString("stage");if(stage.equals("picker_open"))return false;if(stage.equals("service_created")||stage.equals("preview_bound")||stage.equals("wallpaper_bound")||stage.equals("preview_frame")||stage.equals("wallpaper_frame"))return true;}}catch(Exception ignored){}return false;
    }
    static synchronized void cleanup(Context c){
        Set<String> retained=new HashSet<>();for(String key:new String[]{"active","pending","activeHome","activeLock"}){JSONObject value=read(c,key);retained.add(value.optString("file"));}
        WallpaperFiles.cleanup(directory(c),retained);
    }
}
