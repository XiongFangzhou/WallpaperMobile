package dev.workshopvideo.core;
public final class FrameSelection {
    public static long clamp(long requestedUs,long durationMs){long last=Math.max(0,Math.min(Long.MAX_VALUE/1000,Math.max(0,durationMs))*1000-1);return Math.max(0,Math.min(requestedUs,last));}
    public static long atProgress(int progress,int max,long durationMs){if(max<=0||durationMs<=0)return 0;return clamp(Math.round(Math.max(0,Math.min(max,progress))/(double)max*durationMs*1000),durationMs);}
}
