package dev.workshopvideo;

import android.content.Context;
import android.content.SharedPreferences;

/** Global playback/launcher policy; independent of per-wallpaper visual options. */
final class AppSettings {
    static final String BATTERY_STATIC="generalBatteryStatic",GESTURE_PAGING="generalGesturePaging",LAUNCHER_SYNC="generalLauncherSync";
    static SharedPreferences prefs(Context context){return context.getSharedPreferences("wallpaper",Context.MODE_PRIVATE);}
    static boolean batteryStatic(Context context){return prefs(context).getBoolean(BATTERY_STATIC,true);}
    static boolean gesturePaging(Context context){return prefs(context).getBoolean(GESTURE_PAGING,true);}
    static boolean launcherSync(Context context){return prefs(context).getBoolean(LAUNCHER_SYNC,true);}
    static void launcherSync(Context context,boolean value){prefs(context).edit().putBoolean(LAUNCHER_SYNC,value).apply();}
    static void batteryStatic(Context context,boolean value){prefs(context).edit().putBoolean(BATTERY_STATIC,value).apply();}
    static void gesturePaging(Context context,boolean value){prefs(context).edit().putBoolean(GESTURE_PAGING,value).apply();}
}
