package dev.workshopvideo.core;
/** A wallpaper-sized default, never a claim to know a vendor launcher's private page count. */
public final class DesktopPages {
 public static int automatic(int iw,int ih,int sw,int sh,float zoom){if(iw<1||ih<1||sw<1||sh<1)return 1;double span=iw/(double)ih*sh/sw*EffectMotion.clamp(zoom,1,2.5f);return Math.max(1,Math.min(12,(int)Math.ceil(span-0.0001)));}
 public static int resolve(boolean manual,int requested,int reported,int iw,int ih,int sw,int sh,float zoom){return manual?Math.max(1,Math.min(12,requested)):reported>=2&&reported<=99?reported:automatic(iw,ih,sw,sh,zoom);}
 private DesktopPages(){}
}
