package dev.workshopvideo;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.widget.*;
import android.view.Gravity;

final class GeneralSettings {
    private GeneralSettings(){}
    static void show(Activity activity){
        AppSheet sheet=new AppSheet(activity,"通用设置","对所有壁纸生效",null);
        sheet.field(option(activity,"省电时保持静态画面","手机开启省电模式时，壁纸停在当前画面，暂停播放和体感；关闭省电后自动恢复。",AppSettings.batteryStatic(activity),value->AppSettings.batteryStatic(activity,value)));
        sheet.field(option(activity,"桌面滑动兼容","有些桌面不会告诉壁纸翻到了哪一页。开启后根据你的滑动推算位置；桌面能提供真实位置时始终以桌面为准。",AppSettings.gesturePaging(activity),value->AppSettings.gesturePaging(activity,value)));
        sheet.action("settings","精确桌面同步",LauncherSync.status(AppSettings.launcherSync(activity)),false,()->launcherSync(activity));
        sheet.footerButton("完成",true,()->sheet.dialog.dismiss());sheet.show();
    }
    private static void launcherSync(Activity activity){
        AppSheet sheet=new AppSheet(activity,"精确桌面同步",LauncherSync.status(AppSettings.launcherSync(activity)),null);
        sheet.field(option(activity,"辅助事件同步",LauncherSync.nativeObserved()?"系统已直接提供翻页位置，无需额外授权。此开关只影响辅助服务，不影响系统同步。":"在系统「无障碍」中开启「WallpaperMobile 精确桌面同步」后，壁纸会读取桌面的真实翻页位置。只读取滚动位置，不读取屏幕内容，也不会操作手机。",AppSettings.launcherSync(activity),value->AppSettings.launcherSync(activity,value)));
        if(!AppSettings.gesturePaging(activity)&&!LauncherSync.observed()&&!LauncherSync.nativeObserved())sheet.action("settings","启用手势兼容","桌面还没有提供位置时，先按滑动手势跟随",false,()->{AppSettings.gesturePaging(activity,true);show(activity);});
        sheet.action("settings",LauncherSync.connected()?"管理系统授权":"前往系统开启",LauncherSync.connected()?"服务已开启；如果桌面不提供数据，可继续使用滑动推算":"在列表中找到「WallpaperMobile 精确桌面同步」并开启",false,()->LauncherScrollService.settings(activity));
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
