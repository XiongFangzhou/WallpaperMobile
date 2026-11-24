package dev.workshopvideo;

import android.app.Activity;

/** Explicit sign-out is the only menu action that can remove a logged-in account. */
final class AccountMenu {
    private AccountMenu(){}
    static void show(Activity activity,String title,String status,boolean signedIn,boolean connecting,Runnable login,Runnable logout,Runnable about){
        AppSheet sheet=new AppSheet(activity,title,status,null);
        if(!signedIn)sheet.action("account",connecting?"正在连接 Steam":"登录 Steam",connecting?"":"登录后可下载和在线播放",false,login);
        sheet.action("settings","通用设置","省电与桌面翻页",false,()->GeneralSettings.show(activity));
        sheet.action("info","关于 WallpaperMobile","版本 0.5.10",false,about);
        if(signedIn)sheet.action("logout","退出 Steam 登录","清除本机登录，收藏与已保存内容保留",true,()->AppSheet.confirm(activity,"退出 Steam 登录？","将清除此手机保存的登录凭据，收藏和已保存内容会保留。","退出登录",true,logout));
        sheet.show();
    }
}
