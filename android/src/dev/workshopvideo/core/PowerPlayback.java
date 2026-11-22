package dev.workshopvideo.core;
/** First frame is required before a paused decoder can serve as a static wallpaper. */
public final class PowerPlayback {
 public static boolean freeze(boolean powerSave,boolean enabled){return powerSave&&enabled;}
 public static boolean play(boolean visible,boolean freeze,boolean hasFrame){return visible&&(!freeze||!hasFrame);}
 public static boolean animate(boolean visible,boolean freeze){return visible&&!freeze;}
 private PowerPlayback(){}
}
