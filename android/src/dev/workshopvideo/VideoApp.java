package dev.workshopvideo;

import android.app.Application;
import android.app.Activity;
import android.net.*;
import android.os.*;
import android.content.*;
import android.net.Uri;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import dev.workshopvideo.core.*;
import android.database.Cursor;
import android.provider.MediaStore;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.json.*;
import java.io.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;

public final class VideoApp extends Application {
    public static final String UPDATE="dev.workshopvideo.UPDATE";
    public final ExecutorService work=Executors.newSingleThreadExecutor();
    public final Set<String> enqueued=ConcurrentHashMap.newKeySet();
    public volatile SteamSession session;
    public volatile String connection="尚未连接 Steam";
    public volatile boolean connecting=false, guest=true;
    private SharedPreferences prefs;
    public final Object sessionLock=new Object();
    public final CompletableFuture<Void> filesReady=new CompletableFuture<>();
    public PreviewImages previews;
    DiscoverResults results;
    WallpaperEligibility eligibility;
    // Parsed records are reused until the stored JSON changes; callers always receive copies.
    private String jobsRaw,favoritesRaw;
    private List<JSONObject> jobsCache=new ArrayList<>(),favoritesCache=new ArrayList<>();
    private Set<String> favoriteIds=new HashSet<>();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService recovery=Executors.newSingleThreadExecutor();
    private final java.util.concurrent.atomic.AtomicBoolean recovering=new java.util.concurrent.atomic.AtomicBoolean();
    private int visibleActivities,retryCount;
    private final Runnable recoveryAttempt=this::recoverConnection;
    private boolean online(){
        ConnectivityManager manager=getSystemService(ConnectivityManager.class);
        NetworkCapabilities network=manager.getNetworkCapabilities(manager.getActiveNetwork());
        return network!=null&&network.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }
    private void scheduleRecovery(long delay){
        main.post(()->{main.removeCallbacks(recoveryAttempt);if(visibleActivities>0&&canRecover())main.postDelayed(recoveryAttempt,delay);});
    }
    private void recoverConnection(){
        if(visibleActivities==0||!canRecover())return;
        if(!online()){connection="等待网络，联网后自动恢复";changed();return;}
        if(session!=null&&session.isAuthenticated()){retryCount=0;return;}
        if(connecting||!recovering.compareAndSet(false,true)){scheduleRecovery(1000);return;}
        recovery.submit(()->{
            boolean failed=false;
            try{requireSession();}
            catch(Exception error){failed=true;connection=VideoApp.error(error)+"；将自动重试";changed();}
            finally{
                recovering.set(false);boolean retry=failed;
                main.post(()->{if(retry){long[] waits={3000,10000,30000,60000};scheduleRecovery(waits[Math.min(retryCount++,waits.length-1)]);}else retryCount=0;});
            }
        });
    }

    public boolean canRecover(){return session!=null || hasToken();}
    // Called on a worker thread. UI and the foreground download service share
    // one restoration attempt, so neither opens a second login session.
    public SteamSession requireSession() throws Exception {
        synchronized(sessionLock) {
            if(session!=null&&session.isAuthenticated())return session;
            connecting=true;changed();
            try {
                if(session==null) {
                    if(!hasToken())throw new IllegalStateException("请先登录 Steam。");
                    SteamSession fresh=new SteamSession(this::status);
                    try {fresh.restore(lastUsername(),token());session=fresh;guest=false;}
                    catch(Exception e){fresh.close();throw e;}
                } else session.ensureAuthenticated();
                return session;
            } catch(Exception e) {
                Throwable cause=e;while(cause instanceof ExecutionException && cause.getCause()!=null)cause=cause.getCause();
                if(cause instanceof SteamSession.LoginRejected && ((SteamSession.LoginRejected)cause).invalidToken()) {
                    forget();SteamSession old=session;session=null;if(old!=null)old.close();
                    throw new IllegalStateException("Steam 已使登录凭据失效，请重新登录一次。",cause);
                }
                throw e;
            } finally {connecting=false;changed();}
        }
    }

    @Override public void onCreate() {
        super.onCreate();prefs=getSharedPreferences("video",MODE_PRIVATE);previews=new PreviewImages(getCacheDir());results=new DiscoverResults(getCacheDir());eligibility=new WallpaperEligibility(this);
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks(){
            public void onActivityStarted(Activity activity){visibleActivities++;if(visibleActivities==1){retryCount=0;scheduleRecovery(0);}}
            public void onActivityStopped(Activity activity){visibleActivities=Math.max(0,visibleActivities-1);if(visibleActivities==0)main.removeCallbacks(recoveryAttempt);}
            public void onActivityCreated(Activity a,Bundle b){} public void onActivityResumed(Activity a){}
            public void onActivityPaused(Activity a){} public void onActivitySaveInstanceState(Activity a,Bundle b){}
            public void onActivityDestroyed(Activity a){}
        });
        getSystemService(ConnectivityManager.class).registerDefaultNetworkCallback(new ConnectivityManager.NetworkCallback(){
            @Override public void onAvailable(Network network){scheduleRecovery(500);}
            @Override public void onCapabilitiesChanged(Network network,NetworkCapabilities caps){if(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))scheduleRecovery(500);}
        });
        // Use the bundled, current BC implementation inside this process.
        Security.removeProvider("BC");Security.addProvider(new BouncyCastleProvider());
        for(JSONObject job:jobs()) {
            String state=job.optString("state");
            if(!state.equals("saved")&&job.optBoolean("discardRequested")){put(job,"state","paused");save(job);continue;}
            if(state.equals("saving")&&job.optString("storage").equals("private")&&!job.optBoolean("cancelRequested"))continue;
            if(!state.equals("saved") && !state.equals("paused") && !state.equals("error")) {
                put(job,"state","paused");put(job,"error","上次下载中断，可继续下载。");save(job);
            }
        }
        // Remove unfinished MediaStore rows only when this app recorded ownership.
        for(JSONObject job:jobs())if(!job.optString("pendingUri").isEmpty()) {
            try {getContentResolver().delete(Uri.parse(job.getString("pendingUri")),null,null);}catch(Exception ignored){}
            job.remove("pendingUri");save(job);
        }
        work.submit(()->{try{migrateVideos();}finally{filesReady.complete(null);for(JSONObject job:jobs())if(!job.optString("state").equals("saved")&&job.optBoolean("discardRequested"))try{discard(job.optString("id"));}catch(Exception failure){put(job,"state","error");put(job,"error","取消后的资源清理失败："+error(failure));save(job);}changed();}});
    }

    public static void put(JSONObject object,String key,Object value) {
        try{object.put(key,value);}catch(JSONException e){throw new IllegalArgumentException(e);}
    }
    private static List<JSONObject> parse(String raw){
        List<JSONObject> result=new ArrayList<>();
        try {JSONArray values=new JSONArray(raw);for(int i=0;i<values.length();i++)result.add(values.getJSONObject(i));}catch(JSONException ignored){}
        return result;
    }
    private List<JSONObject> storedJobs(){String raw=prefs.getString("jobs","[]");if(!raw.equals(jobsRaw)){jobsCache=parse(raw);jobsRaw=raw;}return jobsCache;}
    private List<JSONObject> storedFavorites(){
        String raw=prefs.getString("favorites","[]");
        if(!raw.equals(favoritesRaw)){favoritesCache=parse(raw);Set<String> ids=new HashSet<>();for(JSONObject item:favoritesCache)ids.add(item.optString("itemId"));favoriteIds=ids;favoritesRaw=raw;}
        return favoritesCache;
    }
    static JSONObject copy(JSONObject source){JSONObject result=new JSONObject();for(Iterator<String> keys=source.keys();keys.hasNext();){String key=keys.next();put(result,key,copyValue(source.opt(key)));}return result;}
    private static Object copyValue(Object value){
        if(value instanceof JSONObject object)return copy(object);
        if(value instanceof JSONArray array){JSONArray result=new JSONArray();for(int i=0;i<array.length();i++)result.put(copyValue(array.opt(i)));return result;}
        return value;
    }
    public synchronized List<JSONObject> jobs() {
        List<JSONObject> result=new ArrayList<>();for(JSONObject job:storedJobs())result.add(copy(job));return result;
    }
    public synchronized JSONObject job(String id) {
        for(JSONObject job:storedJobs())if(job.optString("id").equals(id))return copy(job);
        throw new IllegalArgumentException("任务不存在。");
    }
    public synchronized void save(JSONObject changed) {
        JSONArray values=new JSONArray();List<JSONObject> next=new ArrayList<>();JSONObject stored=copy(changed);boolean found=false;
        for(JSONObject item:storedJobs()) {
            JSONObject value=item.optString("id").equals(changed.optString("id"))?stored:item;if(value==stored)found=true;values.put(value);next.add(value);
        }
        if(!found){values.put(stored);next.add(stored);}
        String raw=values.toString();
        if(!prefs.edit().putString("jobs",raw).commit())
            throw new IllegalStateException("无法保存任务记录，请检查手机存储空间。");
        jobsRaw=raw;jobsCache=next;
        changed();
    }
    public synchronized void change(String id,String key,Object value) {
        JSONObject record=job(id);put(record,key,value);save(record);
    }
    public synchronized List<JSONObject> favorites(){
        List<JSONObject> values=new ArrayList<>();for(JSONObject item:storedFavorites())values.add(copy(item));return values;
    }
    /** Cheap enough for every card bind: a set lookup on the cached favorite ids. */
    public synchronized boolean isFavorite(String itemId){storedFavorites();return favoriteIds.contains(itemId);}
    public synchronized void toggleFavorite(JSONObject item){
        JSONObject entry=new JSONObject();for(String key:new String[]{"itemId","title","previewUrl","description","video","type","size","created","score","votesUp","votesDown"})if(item.has(key))put(entry,key,item.opt(key));put(entry,"favoriteAt",System.currentTimeMillis());
        JSONArray next=new JSONArray();for(JSONObject value:FavoriteIndex.toggle(favorites(),entry,value->value.optString("itemId")))next.put(value);
        if(!prefs.edit().putString("favorites",next.toString()).commit())throw new IllegalStateException("无法保存收藏。");
        changed();
    }
    public synchronized JSONObject create(String itemId,String title,String previewUrl) {
        for(JSONObject j:jobs())if(j.optString("itemId").equals(itemId)){if(!previewUrl.isEmpty()&&!previewUrl.equals(j.optString("previewUrl"))){put(j,"previewUrl",previewUrl);save(j);}return j;}
        JSONObject record=new JSONObject();
        put(record,"id",UUID.randomUUID().toString().replace("-",""));
        put(record,"itemId",itemId);put(record,"title",title);put(record,"state","queued");put(record,"previewUrl",previewUrl);
        put(record,"created",System.currentTimeMillis());save(record);return record;
    }
    public synchronized JSONObject replacement(String oldId){JSONObject old=job(oldId);if(!old.optString("state").equals("saved"))throw new IllegalStateException("原资源尚未保存");for(JSONObject job:jobs())if(job.optString("replaces").equals(oldId)){if(job.optBoolean("discardRequested"))throw new IllegalStateException("旧适配任务正在取消，请稍后重试");if(!enqueued.contains(job.optString("id"))){put(job,"cancelRequested",false);put(job,"state","queued");put(job,"error","");save(job);}return job;}JSONObject record=new JSONObject();put(record,"id",UUID.randomUUID().toString().replace("-",""));for(String key:new String[]{"itemId","title","previewUrl","sourceType"})put(record,key,old.optString(key));put(record,"replaces",oldId);put(record,"state","queued");put(record,"created",System.currentTimeMillis());save(record);return record;}
    public File transfer(String id){if(!id.matches("[0-9a-f]{32}"))throw new IllegalArgumentException("资源 ID 无效");return new File(getFilesDir(),"transfers/"+id);}
    public synchronized void discard(String id) {
        if(!filesReady.isDone())throw new IllegalStateException("正在整理本地视频，请稍候。");
        if(enqueued.contains(id))throw new IllegalStateException("请先暂停下载。");
        JSONObject old=job(id);
        if(old.optString("state").equals("saved")) {
            try {
                if(!old.optString("storage").equals("private"))deleteLegacyVideo(old.getString("uri"));
                else if(!old.optString("legacyUri").isEmpty())deleteLegacyVideo(old.getString("legacyUri"));
            }catch(Exception e){throw new IllegalStateException(error(e),e);}
        }
        try{WorkshopAssets.clear(transfer(id));}catch(IOException e){throw new IllegalStateException(error(e),e);}
        File partial=partial(id);if(partial.exists()&&!partial.delete())throw new IllegalStateException("未能清理临时文件。");
        File privateFile=video(id);if(privateFile.exists()&&!privateFile.delete())throw new IllegalStateException("未能清理视频文件。");
        JSONArray values=new JSONArray();
        for(JSONObject j:jobs())if(!j.optString("id").equals(id))values.put(j);
        if(!prefs.edit().putString("jobs",values.toString()).commit())throw new IllegalStateException("清理记录失败。");
        File poster=preview(id);if(poster.exists())poster.delete();
        changed();
    }
    public File partial(String id) {
        if(!id.matches("[0-9a-f]{32}"))throw new IllegalArgumentException("任务 ID 无效。");
        File directory=new File(getFilesDir(),"transfers");directory.mkdirs();return new File(directory,id+".partial");
    }
    public File video(String id){return VideoFiles.path(new File(getFilesDir(),"videos"),id);}
    public File preview(String id){if(!id.matches("[0-9a-f]{32}"))throw new IllegalArgumentException("视频 ID 无效。");File directory=new File(getCacheDir(),"previews");directory.mkdirs();return new File(directory,id+".jpg");}
    public Uri videoUri(String id){video(id);return Uri.parse("content://"+getPackageName()+".videos/videos/"+id);}
    public static String mime(String name) {
        String value=name.toLowerCase(Locale.ROOT);
        if(value.endsWith(".webm"))return "video/webm";
        if(value.endsWith(".mkv"))return "video/x-matroska";
        if(value.endsWith(".avi"))return "video/x-msvideo";
        if(value.endsWith(".mov"))return "video/quicktime";
        return "video/mp4";
    }
    private boolean legacyExists(Uri uri) throws IOException {
        try(Cursor cursor=getContentResolver().query(uri,new String[]{MediaStore.MediaColumns._ID},null,null,null)) {
            if(cursor==null)throw new IOException("无法检查旧视频，原文件已保留。");
            return cursor.moveToFirst();
        }
    }
    private void deleteLegacyVideo(String value) throws IOException {
        Uri uri=Uri.parse(value);
        if(!"content".equals(uri.getScheme())||!"media".equals(uri.getAuthority()))throw new IOException("无法确认旧视频来源，原文件已保留。");
        if(!legacyExists(uri))return;
        requireOwnedMedia(uri);
        if(getContentResolver().delete(uri,null,null)!=1)throw new IOException("旧视频未能删除，请重试。");
    }
    private void requireOwnedMedia(Uri uri) throws IOException {
        if(!"content".equals(uri.getScheme())||!"media".equals(uri.getAuthority()))throw new IOException("无法确认旧视频来源，原文件已保留。");
        try(Cursor cursor=getContentResolver().query(uri,new String[]{MediaStore.MediaColumns.OWNER_PACKAGE_NAME},null,null,null)) {
            if(cursor==null||!cursor.moveToFirst()||!getPackageName().equals(cursor.getString(0)))
                throw new IOException("无法确认旧视频归属，原文件已保留。");
        }
    }
    private byte[] legacyHash(Uri uri) throws Exception {
        try(InputStream input=getContentResolver().openInputStream(uri)) {
            if(input==null)throw new IOException("无法读取旧视频。");
            MessageDigest hash=MessageDigest.getInstance("SHA-1");byte[] buffer=new byte[1024*1024];int n;
            while((n=input.read(buffer))!=-1)hash.update(buffer,0,n);return hash.digest();
        }
    }
    public void finishPrivateSave(JSONObject record) throws Exception {
        String id=record.getString("id");
        VideoFiles.promote(partial(id),video(id),record.getLong("size"),VideoFiles.unhex(record.getString("sha1")));
        synchronized(this){JSONObject current=job(id);if(current.optBoolean("cancelRequested")||Thread.currentThread().isInterrupted())throw new InterruptedException("保存已暂停");put(current,"state","saved");put(current,"message",current.optBoolean("compatible",true)?"已保存至应用内":"已保存，当前引擎不兼容此内容");put(current,"error","");save(current);String previous=current.optString("replaces");if(!previous.isEmpty()){try{discard(previous);current.remove("replaces");save(current);}catch(Exception cleanup){put(current,"message","已转换；旧文件清理失败："+error(cleanup));save(current);}}}
    }
    public void migrateVideos() {
        for(JSONObject original:jobs()) {
            if(!original.optString("state").equals("saved")&&!(original.optString("state").equals("saving")&&original.optString("storage").equals("private")))continue;
            String id=original.optString("id");
            try {
                JSONObject record=job(id);
                if(record.optString("state").equals("saving")){finishPrivateSave(record);WorkshopAssets.clear(transfer(id));continue;}
                if(!record.optString("storage").equals("private")) {
                    Uri uri=Uri.parse(record.getString("uri"));requireOwnedMedia(uri);
                    if(getFilesDir().getUsableSpace()<record.optLong("size")+32L*1024*1024)throw new IOException("迁移空间不足，原视频仍保留在相册中。");
                    byte[] sha1;
                    try(InputStream input=getContentResolver().openInputStream(uri)) {
                        if(input==null)throw new IOException("无法读取相册视频，原文件已保留。");
                        sha1=VideoFiles.importCopy(input,video(id),record.optLong("size",-1));
                    }
                    put(record,"legacyUri",uri.toString());put(record,"storage","private");put(record,"uri",videoUri(id).toString());
                    put(record,"sha1",VideoFiles.hex(sha1));put(record,"size",video(id).length());save(record);
                }
                if(!record.optString("legacyUri").isEmpty()) {
                    Uri old=Uri.parse(record.getString("legacyUri"));
                    VideoFiles.verify(video(id),record.getLong("size"),VideoFiles.unhex(record.getString("sha1")));
                    if(legacyExists(old)) {
                        requireOwnedMedia(old);
                        if(!MessageDigest.isEqual(legacyHash(old),VideoFiles.unhex(record.getString("sha1"))))throw new IOException("相册原视频已变化，原文件已保留。");
                        if(getContentResolver().delete(old,null,null)!=1)throw new IOException("视频已迁入应用，但相册旧副本未能清理。");
                    }
                    record.remove("legacyUri");
                }
                WorkshopAssets.clear(transfer(id));
                record.remove("migrationError");put(record,"message",record.optBoolean("compatible",true)?"已保存至应用内":"已保存，当前引擎不兼容此内容");save(record);
            }catch(Exception e) {
                JSONObject record=job(id);
                if(record.optString("state").equals("saving")){put(record,"state","paused");put(record,"error",error(e));}
                else put(record,"migrationError",error(e));
                save(record);
            }
        }
    }
    public void changed(){sendBroadcast(new Intent(UPDATE).setPackage(getPackageName()));}
    public void status(String value){connection=value;changed();if(session!=null&&!session.isAuthenticated())scheduleRecovery(1000);}

    public String lastUsername(){return prefs.getString("username","");}
    public WorkshopQuery loadFilters(){String saved=prefs.getString("filters","");return saved.isEmpty()?WeFilters.defaults():WeFilters.migrate(WorkshopQuery.fromSettings(saved));}
    public void saveFilters(WorkshopQuery filters) {
        if(!prefs.edit().putString("filters",filters.settings()).commit())throw new IllegalStateException("无法保存筛选配置，请检查存储空间。");
    }
    public boolean hasToken(){return prefs.contains("session");}
    private SecretKey key() throws Exception {
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        String alias="workshop-video-session";
        if(store.containsAlias(alias))return (SecretKey)store.getKey(alias,null);
        KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return generator.generateKey();
    }
    public synchronized void remember(String username,String token) throws Exception {
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());
        byte[] encrypted=cipher.doFinal(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String saved=Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)+":"+Base64.encodeToString(encrypted,Base64.NO_WRAP);
        if(!prefs.edit().putString("username",username).putString("session",saved).commit())
            throw new IllegalStateException("无法保存登录状态。");
    }
    public synchronized String token() throws Exception {
        String saved=prefs.getString("session","");String[] pieces=saved.split(":",2);
        if(pieces.length!=2)throw new IllegalStateException("没有保存的 Steam 会话。");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),
            new GCMParameterSpec(128,Base64.decode(pieces[0],Base64.NO_WRAP)));
        return new String(cipher.doFinal(Base64.decode(pieces[1],Base64.NO_WRAP)),java.nio.charset.StandardCharsets.UTF_8);
    }
    public synchronized void forget(){prefs.edit().remove("session").commit();}
    public static String error(Throwable problem) {
        Throwable root=problem;
        while(root.getCause()!=null && (root instanceof ExecutionException || root instanceof CompletionException))root=root.getCause();
        if(root instanceof TimeoutException)return "Steam 请求超时，请检查网络后重试。";
        if(root instanceof CancellationException)return "Steam 请求中断，请重新连接后重试。";
        String message=root.getMessage();
        return message==null || message.isBlank()?root.getClass().getSimpleName():message;
    }
}
