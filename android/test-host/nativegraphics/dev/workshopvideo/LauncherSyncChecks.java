package dev.workshopvideo;

import android.content.*;
import android.view.accessibility.AccessibilityEvent;
import java.lang.reflect.Field;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35,manifest=Config.NONE,application=android.app.Application.class)
public class LauncherSyncChecks {
    private AccessibilityEvent event(String owner,int x,int max,long time){AccessibilityEvent e=AccessibilityEvent.obtain(AccessibilityEvent.TYPE_VIEW_SCROLLED);e.setPackageName(owner);e.setScrollX(x);e.setMaxScrollX(max);e.setEventTime(time);return e;}
    @Test public void exactPositionsUseOnlySelectedLauncherNumericEvents()throws Exception{
        var controller=Robolectric.buildService(LauncherScrollService.class).create();try{
            LauncherScrollService service=controller.get();Field launcher=LauncherScrollService.class.getDeclaredField("launcher");launcher.setAccessible(true);launcher.set(service,"example.launcher");AppSettings.launcherSync(service,true);LauncherSync.connected(true);final int[] calls={0};LauncherSync.Listener listener=(position,time)->calls[0]++;LauncherSync.add(listener);
            try{
                service.onAccessibilityEvent(event("other.app",500,1000,1));service.onAccessibilityEvent(event("example.launcher",0,0,2));service.onAccessibilityEvent(event("example.launcher",1100,1000,3));assertTrue(Float.isNaN(LauncherSync.position()));assertEquals(0,calls[0]);
                service.onAccessibilityEvent(event("example.launcher",1178,1571,100));assertEquals(1178/1571f,LauncherSync.position(),.00001);assertEquals(100,LauncherSync.time());assertEquals(1,calls[0]);
                service.onAccessibilityEvent(event("example.launcher",0,1571,90));assertEquals(1,calls[0]);AppSettings.launcherSync(service,false);service.onAccessibilityEvent(event("example.launcher",0,1571,110));assertEquals(1,calls[0]);
                AppSettings.launcherSync(service,true);AccessibilityEvent other=event("example.launcher",0,1571,120);other.setEventType(AccessibilityEvent.TYPE_VIEW_CLICKED);service.onAccessibilityEvent(other);assertEquals(1,calls[0]);
            }finally{LauncherSync.remove(listener);LauncherSync.connected(false);AppSettings.prefs(service).edit().clear().commit();}
        }finally{controller.destroy();}
    }
    @Test public void exactSyncSettingsUseUnifiedSheetAndSeparateSystemAuthorization(){
        try(var controller=Robolectric.buildActivity(android.app.Activity.class).setup()){
            android.app.Activity activity=controller.get();GeneralSettings.show(activity);android.app.Dialog sheet=org.robolectric.shadows.ShadowDialog.getLatestDialog();android.view.View action=find(sheet.getWindow().getDecorView(),"精确桌面同步");assertNotNull(action);((android.view.View)action.getParent().getParent()).performClick();android.app.Dialog details=org.robolectric.shadows.ShadowDialog.getLatestDialog();assertNotNull(find(details.getWindow().getDecorView(),"使用精确桌面同步"));assertNotNull(find(details.getWindow().getDecorView(),"前往系统开启"));details.dismiss();
        }
    }
    @Test public void authorizationUsesPublicSettingsActionAndDeniedEntryDoesNotCrash(){
        try(var controller=Robolectric.buildActivity(android.app.Activity.class).setup()){
            android.app.Activity activity=controller.get();LauncherScrollService.settings(activity);Intent intent=org.robolectric.Shadows.shadowOf(activity).getNextStartedActivity();assertEquals(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS,intent.getAction());assertEquals(LauncherScrollService.class.getName(),ComponentName.unflattenFromString(intent.getStringExtra(":settings:fragment_args_key")).getClassName());
        }
        try(var controller=Robolectric.buildActivity(DeniedActivity.class).setup()){
            LauncherScrollService.settings(controller.get());assertTrue(org.robolectric.shadows.ShadowToast.getTextOfLatestToast().toString().contains("无障碍"));
        }
    }
    public static class DeniedActivity extends android.app.Activity {@Override public void startActivity(Intent intent){throw new SecurityException("test protected settings activity");}}
    private android.view.View find(android.view.View root,String title){if(root instanceof android.widget.TextView text&&text.getText().toString().equals(title))return root;if(root instanceof android.view.ViewGroup group)for(int i=0;i<group.getChildCount();i++){android.view.View result=find(group.getChildAt(i),title);if(result!=null)return result;}return null;}
}
