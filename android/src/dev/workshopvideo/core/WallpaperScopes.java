package dev.workshopvideo.core;

/** Public Android home/lock flags, without a dependency on the Android runtime. */
public final class WallpaperScopes {
    public static final int HOME=1,LOCK=2,BOTH=3;
    public static int flags(int choice){return switch(choice){case 0->HOME;case 1->LOCK;case 2->BOTH;default->throw new IllegalArgumentException("无效的壁纸范围");};}
    public static int screen(int engineFlags,boolean locked){int flags=engineFlags&BOTH;return flags==HOME?HOME:flags==LOCK?LOCK:locked?LOCK:HOME;}
    public static boolean canUpdate(int requested,int installed){return requested>0&&(requested&~BOTH)==0&&(installed&requested)==requested;}
    public static int confirmedTargets(int requested,int installed){return requested&installed&BOTH;}
    public static String activeKey(int screen){return screen==LOCK?"activeLock":"activeHome";}
    public static String name(int flags){return switch(flags&BOTH){case HOME->"桌面";case LOCK->"锁屏";case BOTH->"桌面和锁屏";default->"所选屏幕";};}
}
