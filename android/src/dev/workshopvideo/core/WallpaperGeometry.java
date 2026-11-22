package dev.workshopvideo.core;

/** Normalized texture crop and output scale shared by preview and wallpaper. */
public final class WallpaperGeometry {
    public static boolean canPage(int iw,int ih,int w,int h,boolean fit,boolean pan,float zoom,float strength){if(iw<=0||ih<=0||w<=0||h<=0||fit||!pan)return false;float scale=Math.max(w/(float)iw,h/(float)ih)*EffectMotion.clamp(zoom,1,2.5f);return Math.max(0,iw*scale-w)>1;}
    public record Layout(float scaleX,float scaleY,float u0,float v0,float uSize,float vSize){}
    public static Layout layout(int videoWidth,int videoHeight,int screenWidth,int screenHeight,boolean fit,boolean pan,float offset){
        if(videoWidth<=0||videoHeight<=0||screenWidth<=0||screenHeight<=0)throw new IllegalArgumentException("Invalid wallpaper dimensions");
        double video=videoWidth/(double)videoHeight,screen=screenWidth/(double)screenHeight;
        float x=Float.isFinite(offset)?Math.max(0,Math.min(1,offset)):.5f;
        if(fit)return video>screen?new Layout(1,(float)(screen/video),0,0,1,1):new Layout((float)(video/screen),1,0,0,1,1);
        if(video>screen){float visible=(float)(screen/video);return new Layout(1,1,(1-visible)*(pan?x:.5f),0,visible,1);}
        float visible=(float)(video/screen);return new Layout(1,1,0,(1-visible)/2,1,visible);
    }
    public static Layout picture(int iw,int ih,int w,int h,String alignment,boolean pan,float offset,float zoom,float mx,float my){
        if("cover".equals(alignment))return moving(iw,ih,w,h,false,pan,offset,1,zoom,mx,my);
        if("fill".equals(alignment))return moving(w,h,w,h,false,pan,offset,1,zoom,mx,my);
        Layout fit=layout(iw,ih,w,h,true,false,.5f);return new Layout(fit.scaleX*zoom,fit.scaleY*zoom,0,0,1,1);
    }
    public static boolean canPagePicture(int iw,int ih,int w,int h,String alignment,boolean pan,float zoom){
        if(!pan||iw<1||ih<1||w<1||h<1)return false;
        if("free".equals(alignment))return layout(iw,ih,w,h,true,false,.5f).scaleX*zoom>1.001f;
        return "fill".equals(alignment)?zoom>1.001f:canPage(iw,ih,w,h,false,true,zoom,1);
    }
    public static float freePosition(float scale,float position,float motion,boolean pan,float offset){
        return position*2+motion*2+(pan?(1-2*WallpaperPaging.offset(offset))*Math.max(0,scale-1):0);
    }
    /** One crop calculation applies page offsets exactly once, including zoom overscan. */
    public static Layout moving(int videoWidth,int videoHeight,int screenWidth,int screenHeight,boolean fit,boolean pan,float offset,float scrollStrength,float zoom,float motionX,float motionY){
        Layout base=layout(videoWidth,videoHeight,screenWidth,screenHeight,fit,false,.5f);
        float z=EffectMotion.clamp(zoom,1,2.5f),us=base.uSize/z,vs=base.vSize/z;
        float page=fit||!pan?.5f:WallpaperPaging.offset(offset);
        float u=EffectMotion.clamp((1-us)*page-motionX,0,1-us),v=EffectMotion.clamp((1-vs)*.5f-motionY,0,1-vs);
        return new Layout(base.scaleX,base.scaleY,u,v,us,vs);
    }

}
