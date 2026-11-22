package dev.workshopvideo.core;

/** Surface ownership/resource failures do not establish media incompatibility. */
public final class RenderFailure {
    private RenderFailure(){}
    public static boolean transientSurface(String message){
        if(message==null)return false;
        String lower=message.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("egl=0x3003")||lower.contains("egl=0x300b")||lower.contains("egl=0x300d")||lower.contains("egl=0x300e")
            ||lower.contains("egl_bad_alloc")||lower.contains("already connected")||lower.contains("显示区域已失效");
    }
}
