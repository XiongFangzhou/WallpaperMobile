package dev.workshopvideo;

import java.util.concurrent.CopyOnWriteArrayList;

/** In-process launcher observations. No screen contents or navigation history are retained. */
final class LauncherSync {
    private static final String NATIVE_SCROLL_KEY="pref_key_wallpaper_screen_scrolled_span";
    interface Listener {void scrolled(float position,long eventTime);}
    private static final CopyOnWriteArrayList<Listener> listeners=new CopyOnWriteArrayList<>();
    private static boolean connected;
    private static float position=Float.NaN;
    private static long time;
    private static float nativeRaw=Float.NaN;
    private static boolean nativeObserved;
    static boolean connected(){return connected;}
    static float position(){return position;}
    static long time(){return time;}
    static boolean observed(){return connected&&Float.isFinite(position);}
    static boolean nativeObserved(){return nativeObserved;}
    static boolean nativeConfigured(android.content.Context context){try{return android.provider.Settings.Secure.getInt(context.getContentResolver(),NATIVE_SCROLL_KEY,-1)==1;}catch(SecurityException unavailable){return false;}}
    static void nativeOffset(float value,float step){
        if(!Float.isFinite(value)||value<0||value>1)return;
        if(dev.workshopvideo.core.WallpaperPaging.pages(step)>=2||Float.isFinite(nativeRaw)&&Float.compare(value,nativeRaw)!=0)nativeObserved=true;
        nativeRaw=value;
    }
    static void resetNative(){nativeObserved=false;nativeRaw=Float.NaN;}
    static String status(boolean enabled){return nativeObserved?"系统真实偏移可用":!enabled?"已暂停":!connected?"尚未开启":observed()?"已收到桌面滚动位置":"已授权 · 尚未收到桌面滚动位置";}
    static void connected(boolean value){connected=value;position=Float.NaN;time=0;}
    static void add(Listener listener){listeners.add(listener);}
    static void remove(Listener listener){listeners.remove(listener);}
    static void publish(float value,long eventTime){if(!connected||!Float.isFinite(value)||value<0||value>1||eventTime<time)return;position=value;time=eventTime;for(Listener listener:listeners)listener.scrolled(value,eventTime);}
}
