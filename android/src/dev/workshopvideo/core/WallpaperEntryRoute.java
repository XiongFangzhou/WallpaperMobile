package dev.workshopvideo.core;
import java.util.Locale;

/** Select a working public system route; denial of one route does not deny video wallpaper. */
public final class WallpaperEntryRoute {
    public enum Route { DIRECT, CHOOSER, NONE }
    public static boolean isMiui(String manufacturer,String brand){
        String names=(manufacturer+" "+brand).toLowerCase(Locale.ROOT);
        return names.contains("xiaomi")||names.contains("redmi")||names.contains("poco");
    }
    public static Route select(boolean directAvailable,boolean directRejected,boolean chooserAvailable,boolean chooserRejected){
        boolean direct=directAvailable&&!directRejected,chooser=chooserAvailable&&!chooserRejected;
        if(direct)return Route.DIRECT;
        return chooser?Route.CHOOSER:Route.NONE;
    }
}
