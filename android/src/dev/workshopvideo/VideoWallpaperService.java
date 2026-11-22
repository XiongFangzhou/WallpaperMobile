package dev.workshopvideo;

import android.app.KeyguardManager;
import android.content.*;
import android.os.*;
import android.service.wallpaper.WallpaperService;
import android.view.SurfaceHolder;
import dev.workshopvideo.core.LauncherPaging;import dev.workshopvideo.core.WallpaperScopes;import dev.workshopvideo.core.WallpaperPaging;
import org.json.JSONObject;

/** Scope-aware engines with bounded recovery; preview never promotes a candidate. */
public final class VideoWallpaperService extends WallpaperService {
    @Override public void onCreate(){super.onCreate();WallpaperStore.record(this,"service_created",WallpaperStore.read(this,"pending"),"wallpaper service process reached");}
    @Override public Engine onCreateEngine(){return new VideoEngine();}
    private final class VideoEngine extends Engine implements SharedPreferences.OnSharedPreferenceChangeListener {
        private final Handler main=new Handler(Looper.getMainLooper());
        private WallpaperRenderer renderer;private MotionInput motionInput;private JSONObject preview,current;private boolean surfaceReady,destroyed,watchingScreen;private int generation,retries;private float lastOffset=.5f;private final LauncherPaging paging=new LauncherPaging();private long offsetLog,loadStarted;private int contentWidth,contentHeight;private String pagingFile="";
        private boolean saving(){PowerManager p=getSystemService(PowerManager.class);return p!=null&&p.isPowerSaveMode();}
        private boolean staticMode(){return saving()&&current!=null&&EffectOptions.normalize(current.optJSONObject("effects")).optBoolean("batteryStatic",true);}
        private void updatePower(){if(renderer!=null){renderer.powerSaving(saving());if(!staticMode())renderer.offset(screen()==WallpaperScopes.HOME?lastOffset:.5f,true);}if(motionInput!=null)motionInput.active(isVisible()&&!staticMode()&&current!=null&&current.optJSONObject("effects")!=null&&current.optJSONObject("effects").optBoolean("gyro"));}
        private void configurePages(){JSONObject o=current==null?null:current.optJSONObject("effects");boolean manual=o!=null&&o.optString("pageMode").equals("manual");android.util.DisplayMetrics d=getResources().getDisplayMetrics();int n=dev.workshopvideo.core.DesktopPages.resolve(manual,o==null?3:o.optInt("fallbackPages",3),WallpaperStore.prefs(VideoWallpaperService.this).getInt("launcherPages",0),contentWidth,contentHeight,d.widthPixels,d.heightPixels,o==null?1:(float)o.optDouble("zoom",1));paging.pages(n);paging.manual(manual);if(paging.pageCount()<=1){paging.position(.5f);lastOffset=.5f;if(renderer!=null)renderer.offset(.5f,true);}paging.slop(android.view.ViewConfiguration.get(VideoWallpaperService.this).getScaledTouchSlop()/(float)Math.max(1,d.widthPixels));}
        private int flags(){return Build.VERSION.SDK_INT>=34?getWallpaperFlags():3;}
        private int screen(){KeyguardManager guard=getSystemService(KeyguardManager.class);return WallpaperScopes.screen(flags(),guard!=null&&guard.isKeyguardLocked());}
        private final BroadcastReceiver screenEvents=new BroadcastReceiver(){public void onReceive(Context c,Intent i){updatePower();if(isVisible()){checkConfiguration();main.removeCallbacks(checkScope);main.postDelayed(checkScope,300);}}};
        private final Runnable checkScope=new Runnable(){public void run(){if(destroyed||!isVisible()||isPreview())return;checkConfiguration();if((flags()&3)==3)main.postDelayed(this,500);}};
        private final Runnable suspend=()->{if(!destroyed&&!isVisible()&&renderer!=null){generation++;renderer.closeAsync();renderer=null;}};
        private final Runnable recover=()->{if(!destroyed&&surfaceReady&&isVisible()){start();}};
        private final Runnable changed=()->{if(!destroyed&&!isPreview())checkConfiguration();};
        @Override public void onCreate(SurfaceHolder holder){
            super.onCreate(holder);holder.setFormat(android.graphics.PixelFormat.RGBA_8888);setOffsetNotificationsEnabled(true);setTouchEventsEnabled(true);motionInput=new MotionInput(VideoWallpaperService.this,(x,y)->{if(renderer!=null)renderer.tilt(x,y);},message->WallpaperStore.record(VideoWallpaperService.this,"sensor_unavailable",configuration(),message));preview=WallpaperStore.read(VideoWallpaperService.this,"pending");WallpaperStore.prefs(VideoWallpaperService.this).registerOnSharedPreferenceChangeListener(this);
            {IntentFilter filter=new IntentFilter();filter.addAction(Intent.ACTION_SCREEN_ON);filter.addAction(Intent.ACTION_SCREEN_OFF);filter.addAction(Intent.ACTION_USER_PRESENT);filter.addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);if(Build.VERSION.SDK_INT>=33)registerReceiver(screenEvents,filter,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(screenEvents,filter);watchingScreen=true;}
            WallpaperStore.record(VideoWallpaperService.this,isPreview()?"preview_bound":"wallpaper_bound",configuration(),"flags="+flags());
        }
        private JSONObject configuration(){
            if(isPreview())return preview;
            return WallpaperStore.engineConfiguration(VideoWallpaperService.this,screen(),false);
        }
        private void checkConfiguration(){JSONObject next=configuration();if(current==null||!next.optString("file").equals(current.optString("file"))||next.optBoolean("fit")!=current.optBoolean("fit")||next.optBoolean("pan")!=current.optBoolean("pan")||!String.valueOf(next.optJSONObject("effects")).equals(String.valueOf(current.optJSONObject("effects")))){retries=0;start();}}
        private void start(){
            if(!surfaceReady||destroyed||!getSurfaceHolder().getSurface().isValid())return;
            main.removeCallbacks(recover);int attempt=++generation,renderScreen=screen();JSONObject config=configuration();current=config;boolean previewEngine=isPreview();
            if(config.optString("file").isEmpty()){if(renderer!=null){renderer.closeAsync();renderer=null;}if(motionInput!=null)motionInput.active(false);return;}
            loadStarted=SystemClock.elapsedRealtime();JSONObject opts=config.optJSONObject("effects");if(!pagingFile.equals(config.optString("file"))){pagingFile=config.optString("file");paging.reset();lastOffset=.5f;contentWidth=contentHeight=0;}configurePages();
            WallpaperRenderer.Events callbacks=new WallpaperRenderer.Events(){
                public void contentSize(int w,int h){main.post(()->{if(attempt!=generation||destroyed)return;contentWidth=w;contentHeight=h;configurePages();});}
                public void rendered(){main.post(()->{
                    if(destroyed||attempt!=generation)return;
                    WallpaperStore.record(VideoWallpaperService.this,previewEngine?"preview_frame":"wallpaper_frame",config,"first frame displayed; flags="+flags()+" screen="+screen()+" loadMs="+(SystemClock.elapsedRealtime()-loadStarted));
                    SharedPreferences.Editor save=WallpaperStore.prefs(VideoWallpaperService.this).edit().putString(previewEngine?"previewRenderedFile":"activeRenderedFile",config.optString("file"));
                    if(!previewEngine)save.putString("renderedFile"+renderScreen,config.optString("file"));
                    if(config.optString("file").equals(WallpaperStore.prefs(VideoWallpaperService.this).getString("errorFile","")))save.remove("error").remove("errorFile");save.apply();

                });}
                public void failed(String message){main.post(()->{
                    if(destroyed||attempt!=generation)return;
                    WallpaperStore.record(VideoWallpaperService.this,previewEngine?"preview_failed":"wallpaper_failed",config,message+"; attempt="+retries);
                    WallpaperStore.prefs(VideoWallpaperService.this).edit().putString("error",message).putString("errorFile",config.optString("file")).apply();
                    // Surface/decoder loss can be temporary. Never permanently reject the file here.
                    if(!previewEngine&&isVisible()&&retries<3){retries++;main.postDelayed(recover,600L*retries);}
                });}
            };
            boolean reuse=renderer!=null&&!renderer.hasFailed();if(renderer==null)renderer=new WallpaperRenderer(VideoWallpaperService.this,callbacks);
            android.graphics.Rect area=getSurfaceHolder().getSurfaceFrame();WallpaperStore.record(VideoWallpaperService.this,"surface",config,area.width()+"x"+area.height()+" RGBA8888 preview="+previewEngine);if(reuse)renderer.replace(config,isVisible(),callbacks);else renderer.surface(getSurfaceHolder().getSurface(),area.width(),area.height(),config,isVisible(),callbacks);renderer.offset(screen()==WallpaperScopes.HOME?lastOffset:.5f);renderer.powerSaving(saving());if(motionInput!=null)motionInput.active(isVisible()&&!staticMode()&&config.optJSONObject("effects")!=null&&config.optJSONObject("effects").optBoolean("gyro"));if(!isVisible()){main.removeCallbacks(suspend);main.postDelayed(suspend,30000);}
        }
        @Override public void onSurfaceCreated(SurfaceHolder holder){super.onSurfaceCreated(holder);surfaceReady=true;retries=0;start();}
        @Override public void onSurfaceChanged(SurfaceHolder holder,int format,int width,int height){super.onSurfaceChanged(holder,format,width,height);if(renderer!=null)renderer.resize(width,height);}
        @Override public void onSurfaceRedrawNeeded(SurfaceHolder holder){super.onSurfaceRedrawNeeded(holder);if(renderer!=null)renderer.redraw();}
        @Override public void onVisibilityChanged(boolean visible){
            super.onVisibilityChanged(visible);main.removeCallbacks(checkScope);main.removeCallbacks(suspend);
            if(visible){retries=0;checkConfiguration();if(renderer==null||renderer.hasFailed())start();if(!isPreview())main.post(checkScope);}else {main.removeCallbacks(recover);main.postDelayed(suspend,30000);}
            if(renderer!=null){renderer.powerSaving(saving());renderer.visible(visible);}if(motionInput!=null)motionInput.active(visible&&!staticMode()&&current!=null&&current.optJSONObject("effects")!=null&&current.optJSONObject("effects").optBoolean("gyro"));
        }
        @Override public void onWallpaperFlagsChanged(int which){checkConfiguration();main.removeCallbacks(checkScope);if(isVisible()&&!isPreview())main.post(checkScope);}
        @Override public void onTouchEvent(android.view.MotionEvent event){if(renderer==null||staticMode())return;android.graphics.Rect area=getSurfaceHolder().getSurfaceFrame();float x=event.getX()/Math.max(1,Math.min(area.width(),getResources().getDisplayMetrics().widthPixels)),y=event.getY()/Math.max(1,Math.min(area.height(),getResources().getDisplayMetrics().heightPixels));int action=event.getActionMasked();if(!isPreview()&&screen()==WallpaperScopes.HOME&&current!=null&&current.optBoolean("pan")&&paging.touch(x,y,action,SystemClock.elapsedRealtime())){lastOffset=paging.value();renderer.offset(lastOffset,action==android.view.MotionEvent.ACTION_MOVE);}renderer.touch(x,y,action);}

        @Override public void onOffsetsChanged(float x,float y,float xs,float ys,int xp,int yp){float previous=lastOffset;if(isPreview()){lastOffset=WallpaperPaging.offset(x);}else if(screen()==WallpaperScopes.HOME){paging.system(x,xs,SystemClock.elapsedRealtime());lastOffset=paging.value();int pages=WallpaperPaging.pages(xs);if(pages>=2&&WallpaperStore.prefs(VideoWallpaperService.this).getInt("launcherPages",0)!=pages)WallpaperStore.prefs(VideoWallpaperService.this).edit().putInt("launcherPages",pages).apply();long now=SystemClock.elapsedRealtime();if(now-offsetLog>2000){offsetLog=now;android.util.Log.i("WMWallpaper","offset x="+x+" step="+xs+" effective="+lastOffset+" pages="+paging.pageCount()+" flags="+flags());}}if(renderer!=null&&Math.abs(lastOffset-previous)>.00001f)renderer.offset(screen()==WallpaperScopes.HOME?lastOffset:.5f,Math.abs(lastOffset-previous)<.08f);}

        @Override public void onSurfaceDestroyed(SurfaceHolder holder){surfaceReady=false;generation++;if(motionInput!=null)motionInput.active(false);main.removeCallbacks(recover);if(renderer!=null){renderer.closeAsync();renderer=null;}super.onSurfaceDestroyed(holder);}
        @Override public void onSharedPreferenceChanged(SharedPreferences prefs,String key){if(!isPreview()&&("active".equals(key)||"activeHome".equals(key)||"activeLock".equals(key))){main.removeCallbacks(changed);main.postDelayed(changed,40);}}
        @Override public void onDestroy(){destroyed=true;generation++;if(motionInput!=null)motionInput.active(false);main.removeCallbacksAndMessages(null);WallpaperStore.prefs(VideoWallpaperService.this).unregisterOnSharedPreferenceChangeListener(this);if(watchingScreen)unregisterReceiver(screenEvents);if(renderer!=null){renderer.closeAsync();renderer=null;}super.onDestroy();}
    }
}
