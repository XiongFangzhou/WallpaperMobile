package dev.workshopvideo.core;
/** Android offsets cover the complete launcher range; xStep is 1/(pageCount-1). */
public final class WallpaperPaging {
 public static float offset(float x){return Float.isFinite(x)?Math.max(0,Math.min(1,x)):.5f;}
 public static int pages(float step){if(!Float.isFinite(step)||step<=0||step>1)return 0;int intervals=Math.round(1/step);return intervals>=1&&intervals<=99&&Math.abs(step*intervals-1)<.03f?intervals+1:0;}
 public static float atPage(int page,int count){return count<=1?.5f:Math.max(0,Math.min(count-1,page))/(float)(count-1);}
 private WallpaperPaging(){}
}
