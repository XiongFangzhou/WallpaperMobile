package dev.workshopvideo;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.widget.*;
import android.view.Gravity;

final class GeneralSettings {
    private GeneralSettings(){}
    static void show(Activity activity){
        AppSheet sheet=new AppSheet(activity,"通用设置","对所有壁纸生效",null);
        sheet.field(option(activity,"省电时保持静态画面","省电模式下暂停动画和体感",AppSettings.batteryStatic(activity),value->AppSettings.batteryStatic(activity,value)));
        sheet.field(option(activity,"桌面滑动兼容","桌面不报告翻页位置时，按滑动推算",AppSettings.gesturePaging(activity),value->AppSettings.gesturePaging(activity,value)));
        sheet.action("settings","精确桌面同步",LauncherSync.status(AppSettings.launcherSync(activity)),false,()->launcherSync(activity));
        sheet.action("download","Wallpaper Engine 素材",EngineAssets.status(activity),false,()->{sheet.dialog.dismiss();EngineAssets.open(activity);});
        sheet.footerButton("完成",true,()->sheet.dialog.dismiss());sheet.show();
    }
    private static void launcherSync(Activity activity){
        AppSheet sheet=new AppSheet(activity,"精确桌面同步",LauncherSync.status(AppSettings.launcherSync(activity)),null);
        sheet.field(option(activity,"辅助事件同步",LauncherSync.nativeObserved()?"系统已提供翻页位置，无需授权":"需在系统「无障碍」中开启，只读取桌面滚动位置",AppSettings.launcherSync(activity),value->AppSettings.launcherSync(activity,value)));
        if(!AppSettings.gesturePaging(activity)&&!LauncherSync.observed()&&!LauncherSync.nativeObserved())sheet.action("settings","启用手势兼容","",false,()->{AppSettings.gesturePaging(activity,true);show(activity);});
        sheet.action("settings",LauncherSync.connected()?"管理系统授权":"前往系统开启","",false,()->LauncherScrollService.settings(activity));
        sheet.footerButton("完成",true,()->sheet.dialog.dismiss());sheet.show();
    }
    private static LinearLayout option(Activity activity,String title,String detail,boolean enabled,java.util.function.Consumer<Boolean> changed){
        float density=activity.getResources().getDisplayMetrics().density;int padding=Math.round(14*density);
        LinearLayout card=new LinearLayout(activity);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(padding,padding,padding,padding);card.setBackground(AppSheet.background(Ui.RAISED,padding));
        Switch toggle=new Switch(activity);toggle.setText(title);toggle.setTextColor(Ui.TEXT);toggle.setTextSize(14);toggle.setGravity(Gravity.CENTER_VERTICAL);toggle.setMinHeight(Math.round(40*density));toggle.setChecked(enabled);
        toggle.setThumbTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{Ui.ACCENT,0xff81909f}));toggle.setTrackTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{0xff355c51,0xff303d4b}));toggle.setOnCheckedChangeListener((view,on)->changed.accept(on));card.addView(toggle,new LinearLayout.LayoutParams(-1,-2));
        TextView caption=new TextView(activity);caption.setText(detail);caption.setTextSize(12);caption.setTextColor(Ui.MUTED);caption.setPadding(0,Math.round(5*density),0,0);caption.setLineSpacing(Math.round(3*density),1);card.addView(caption,new LinearLayout.LayoutParams(-1,-2));return card;
    }
}
