package dev.workshopvideo;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.view.accessibility.AccessibilityEvent;

/** Optional numeric scroll observations, restricted to the selected HOME package. */
public final class LauncherScrollService extends AccessibilityService {
    private String launcher="";
    @Override protected void onServiceConnected(){
        Intent home=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        ResolveInfo resolved=getPackageManager().resolveActivity(home,android.content.pm.PackageManager.MATCH_DEFAULT_ONLY);
        launcher=resolved!=null&&resolved.activityInfo!=null?resolved.activityInfo.packageName:"";
        AccessibilityServiceInfo info=getServiceInfo();
        if(info==null)info=new AccessibilityServiceInfo();
        info.eventTypes=launcher.isEmpty()?0:AccessibilityEvent.TYPE_VIEW_SCROLLED;
        info.packageNames=new String[]{launcher};info.feedbackType=AccessibilityServiceInfo.FEEDBACK_GENERIC;info.notificationTimeout=0;info.flags=0;
        setServiceInfo(info);LauncherSync.connected(!launcher.isEmpty());
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event){
        if(!AppSettings.launcherSync(this)||event==null||event.getEventType()!=AccessibilityEvent.TYPE_VIEW_SCROLLED||!launcher.contentEquals(event.getPackageName()==null?"":event.getPackageName()))return;
        int x=event.getScrollX(),max=event.getMaxScrollX();
        if(max<=0||x<0||x>max)return;
        // Do not retrieve the event source or a window tree. HyperOS exposes this numeric range directly.
        float position=x/(float)max;LauncherSync.publish(position,event.getEventTime());
        android.util.Log.i("WMLauncher","scroll="+x+"/"+max+" position="+position+" t="+event.getEventTime());
    }
    @Override public void onInterrupt(){}
    @Override public void onDestroy(){LauncherSync.connected(false);super.onDestroy();}
    static void settings(android.app.Activity activity){
        String component=new ComponentName(activity,LauncherScrollService.class).flattenToString();
        // The details action requires a privileged permission on newer Android versions.
        Intent intent=new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).putExtra(":settings:fragment_args_key",component);
        android.os.Bundle arguments=new android.os.Bundle();arguments.putString(":settings:fragment_args_key",component);intent.putExtra(":settings:show_fragment_args",arguments);
        try{activity.startActivity(intent);}catch(android.content.ActivityNotFoundException|SecurityException problem){android.widget.Toast.makeText(activity,"请在系统设置 → 无障碍中开启 WallpaperMobile 精确桌面同步",android.widget.Toast.LENGTH_LONG).show();}
    }
}
