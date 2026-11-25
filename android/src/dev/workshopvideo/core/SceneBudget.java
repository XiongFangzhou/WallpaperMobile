package dev.workshopvideo.core;

/**
 * How many decoded pixels (layers, X-ray images and effect masks) a scene may hold on this phone. Every layer is
 * kept; when a scene needs more, all images are stored at a lower resolution instead of dropping layers.
 * Chosen once from the phone's memory; conversion and loading on the same phone use the same value.
 */
public record SceneBudget(long pixels) {
    private static final long GB=1024L*1024*1024,MEGAPIXEL=1024L*1024;
    /** Used by desktop checks and phones that report no memory. */
    public static final SceneBudget BASELINE=new SceneBudget(16*MEGAPIXEL);
    private static volatile SceneBudget current=BASELINE;
    public static SceneBudget current(){return current;}
    public static void use(SceneBudget budget){current=budget==null?BASELINE:budget;}

    /** About a sixtieth of RAM for decoded pixels (each needs 4 bytes in memory and again as a GPU texture); low-RAM phones get half the baseline. */
    public static SceneBudget forMemory(long totalBytes,boolean lowRam){
        if(totalBytes<=0)return BASELINE;
        if(lowRam||totalBytes<3*GB)return new SceneBudget(8*MEGAPIXEL);
        return new SceneBudget(Math.max(16*MEGAPIXEL,Math.min(40*MEGAPIXEL,totalBytes/60/4)));
    }
    public String describe(){return String.format(java.util.Locale.ROOT,"%d 万像素",pixels/10000);}
}
