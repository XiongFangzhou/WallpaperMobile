package dev.workshopvideo;

/** Sparse phase timings for USB investigation; contains no content or account identifiers. */
final class RenderStats {
    private long started,last,prepare,submit,swap,worst;
    private int frames;
    private String path="";
    void frame(String mode,int cap,long begin,long prepared,long swapped,long end){
        if(!mode.equals(path)||begin-last>250_000_000L){started=begin;frames=0;prepare=submit=swap=worst=0;path=mode;}
        last=begin;frames++;prepare+=prepared-begin;submit+=swapped-prepared;swap+=end-swapped;worst=Math.max(worst,end-begin);
        if(end-started<1_000_000_000L)return;
        android.util.Log.i("WMRender",String.format(java.util.Locale.ROOT,"path=%s cap=%d activeFps=%.1f prepareMs=%.2f submitMs=%.2f swapMs=%.2f worstMs=%.2f frames=%d",path,cap,frames*1e9/(end-started),prepare/1e6/frames,submit/1e6/frames,swap/1e6/frames,worst/1e6,frames));
        started=end;frames=0;prepare=submit=swap=worst=0;
    }
}
