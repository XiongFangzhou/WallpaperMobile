package dev.workshopvideo;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.os.Build;
import dev.workshopvideo.core.WallpaperEntryRoute;

/** Validate the app service and select an available public system entry. */
final class WallpaperSystemEntry {
    static final String DIRECT=WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER;
    static final String CHOOSER=WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER;
    static final String EXIT_REASON="请在本应用的系统权限页面中，将“设置壁纸”改为允许，然后返回设置视频壁纸。";
    private record Entry(String action,ComponentName component,String key,String reason){}

    private static Entry inspect(Context context,String action){
        try{
            WallpaperManager manager=WallpaperManager.getInstance(context);
            if(!manager.isWallpaperSupported()||!manager.isSetWallpaperAllowed())return new Entry(action,null,"","系统当前不允许更改壁纸");
            PackageManager pm=context.getPackageManager();
            if(!pm.hasSystemFeature(PackageManager.FEATURE_LIVE_WALLPAPER))return new Entry(action,null,"","系统未声明支持动态壁纸");
            ComponentName component=new ComponentName(context,VideoWallpaperService.class);
            ServiceInfo service=pm.getServiceInfo(component,PackageManager.GET_META_DATA);
            if(!service.applicationInfo.enabled||!service.enabled||!service.exported||!"android.permission.BIND_WALLPAPER".equals(service.permission))return new Entry(action,null,"","视频壁纸服务声明无效");
            ResolveInfo wrapper=new ResolveInfo();wrapper.serviceInfo=service;
            if(!component.equals(new WallpaperInfo(context,wrapper).getComponent()))return new Entry(action,null,"","视频壁纸元数据无效");
            ResolveInfo handler=pm.resolveActivity(new Intent(action),PackageManager.MATCH_DEFAULT_ONLY);
            if(handler==null||handler.activityInfo==null)return new Entry(action,null,"","系统未提供此动态壁纸入口");
            ActivityInfo activity=handler.activityInfo;
            if(!activity.enabled||!activity.exported)return new Entry(action,null,"","系统动态壁纸入口不可访问");
            if(activity.permission!=null&&context.checkSelfPermission(activity.permission)!=PackageManager.PERMISSION_GRANTED)return new Entry(action,null,"","系统动态壁纸入口需要额外权限");
            ComponentName target=new ComponentName(activity.packageName,activity.name);
            String key=Build.FINGERPRINT+"|"+target.flattenToString()+"|"+pm.getPackageInfo(activity.packageName,0).lastUpdateTime;
            return new Entry(action,target,key,"");
        }catch(Exception error){return new Entry(action,null,"","无法核验系统视频壁纸入口："+VideoApp.error(error));}
    }
    private static String keyPrefix(String action){return CHOOSER.equals(action)?"chooser":"entry";}
    private static boolean rejected(Context c,Entry entry){if(DIRECT.equals(entry.action())&&isMiui())return false;return !entry.key().isEmpty()&&entry.key().equals(WallpaperStore.prefs(c).getString(keyPrefix(entry.action())+"RejectedBuild",""));}
    private static WallpaperEntryRoute.Route route(Context c,Entry direct,Entry chooser){
        return WallpaperEntryRoute.select(direct.reason().isEmpty(),rejected(c,direct),chooser.reason().isEmpty(),rejected(c,chooser));
    }
    static String preferredAction(Context c){
        Entry direct=inspect(c,DIRECT),chooser=inspect(c,CHOOSER);
        return route(c,direct,chooser)==WallpaperEntryRoute.Route.CHOOSER?CHOOSER:DIRECT;
    }
    static String reason(Context c){
        Entry direct=inspect(c,DIRECT),chooser=inspect(c,CHOOSER);
        if(route(c,direct,chooser)!=WallpaperEntryRoute.Route.NONE)return "";
        if(rejected(c,chooser))return WallpaperStore.prefs(c).getString("chooserRejectedReason","系统列表未接入视频壁纸");
        if(!direct.reason().isEmpty())return direct.reason();
        return WallpaperStore.prefs(c).getString("entryRejectedReason",EXIT_REASON);
    }
    static Intent intent(Context c,String action){
        Entry entry=inspect(c,action);
        if(!entry.reason().isEmpty()||entry.component()==null)throw new IllegalStateException(entry.reason());
        if(rejected(c,entry))throw new IllegalStateException(WallpaperStore.prefs(c).getString(keyPrefix(action)+"RejectedReason",EXIT_REASON));
        Intent intent=new Intent(action);
        // Leave Android's resolver to resolve multiple handlers itself.
        if(!entry.component().getClassName().endsWith("ResolverActivity"))intent.setComponent(entry.component());
        if(DIRECT.equals(action))intent.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,new ComponentName(c,VideoWallpaperService.class));
        return intent;
    }
    static boolean isMiui(){return WallpaperEntryRoute.isMiui(Build.MANUFACTURER,Build.BRAND);}
    static boolean permissionNeeded(Context c){return isMiui()&&WallpaperStore.prefs(c).getBoolean("wallpaperPermissionNeeded",false);}
    static void returnedFromPermissions(Context c){WallpaperStore.prefs(c).edit().putBoolean("wallpaperPermissionNeeded",false).remove("entryRejectedBuild").remove("entryRejectedReason").apply();}
    static void reject(Context c,String action,String reason){
        if(DIRECT.equals(action)&&isMiui()){WallpaperStore.prefs(c).edit().putBoolean("wallpaperPermissionNeeded",true).putString("entryRejectedReason",EXIT_REASON).apply();return;}
        Entry entry=inspect(c,action);if(entry.key().isEmpty())return;
        String prefix=keyPrefix(action);
        WallpaperStore.prefs(c).edit().putString(prefix+"RejectedBuild",entry.key()).putString(prefix+"RejectedReason",reason).commit();
    }
    static void confirmed(Context c){WallpaperStore.prefs(c).edit().putBoolean("wallpaperPermissionNeeded",false).remove("entryRejectedBuild").remove("entryRejectedReason").remove("chooserRejectedBuild").remove("chooserRejectedReason").apply();}
    static Intent permissionSettings(Context c){
        Intent editor=new Intent("miui.intent.action.APP_PERM_EDITOR").putExtra("extra_pkgname",c.getPackageName());
        return editor.resolveActivity(c.getPackageManager())!=null?editor:new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:"+c.getPackageName()));
    }
}
