package dev.workshopvideo;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.widget.*;
import android.view.Gravity;

final class GeneralSettings {
    private GeneralSettings(){}
    static void show(Activity activity){
        AppSheet sheet=new AppSheet(activity,"通用设置","对所有壁纸生效",null);
        sheet.field(option(activity,"省电时保持静态画面","进入系统省电模式后暂停播放和体感，退出后自动恢复。",AppSettings.batteryStatic(activity),value->AppSettings.batteryStatic(activity,value)));
        sheet.field(option(activity,"桌面滑动兼容","自动补偿未提供连续偏移的桌面，当前翻页有系统偏移时优先跟随系统。关闭后仅使用系统偏移。",AppSettings.gesturePaging(activity),value->AppSettings.gesturePaging(activity,value)));
        sheet.action("settings","精确桌面同步",LauncherSync.connected()&&AppSettings.launcherSync(activity)?"已连接 · 使用桌面实际滚动位置":"可选开启 · 校正短滑翻页和背景位置",false,()->launcherSync(activity));
        sheet.footerButton("完成",true,()->sheet.dialog.dismiss());sheet.show();
    }
    private static void launcherSync(Activity activity){
        AppSheet sheet=new AppSheet(activity,"精确桌面同步","让背景跟随桌面实际翻页结果",null);
        sheet.field(option(activity,"使用精确桌面同步","需在系统无障碍中开启 WallpaperMobile 精确桌面同步。仅接收当前桌面的滚动位置，不读取页面内容，也不会操作手机。",AppSettings.launcherSync(activity),value->AppSettings.launcherSync(activity,value)));
        sheet.action("settings","前往系统开启",LauncherSync.connected()?"服务已连接，可在系统中管理授权":"选择 WallpaperMobile 精确桌面同步并开启",false,()->LauncherScrollService.settings(activity));
        sheet.footerButton("完成",true,()->sheet.dialog.dismiss());sheet.show();
    }
    private static LinearLayout option(Activity activity,String title,String detail,boolean enabled,java.util.function.Consumer<Boolean> changed){
        float density=activity.getResources().getDisplayMetrics().density;int padding=Math.round(14*density);
        LinearLayout card=new LinearLayout(activity);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(padding,padding,padding,padding);card.setBackground(AppSheet.background(Ui.PANEL,padding));
        Switch toggle=new Switch(activity);toggle.setText(title);toggle.setTextColor(Ui.TEXT);toggle.setTextSize(14);toggle.setGravity(Gravity.CENTER_VERTICAL);toggle.setMinHeight(Math.round(40*density));toggle.setChecked(enabled);
        toggle.setThumbTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{Ui.ACCENT,0xff81909f}));toggle.setTrackTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{0xff355c51,0xff303d4b}));toggle.setOnCheckedChangeListener((view,on)->changed.accept(on));card.addView(toggle,new LinearLayout.LayoutParams(-1,-2));
        TextView caption=new TextView(activity);caption.setText(detail);caption.setTextSize(12);caption.setTextColor(Ui.MUTED);caption.setPadding(0,Math.round(5*density),0,0);caption.setLineSpacing(Math.round(3*density),1);card.addView(caption,new LinearLayout.LayoutParams(-1,-2));return card;
    }
}
