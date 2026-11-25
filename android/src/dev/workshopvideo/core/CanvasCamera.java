package dev.workshopvideo.core;

/** Same outer transform as Canvas: center, rotate, translate, scale, source center. */
public final class CanvasCamera {
    private CanvasCamera(){}
    public record Frame(float scaleX,float scaleY,float x,float y,float angle){}
    public static Frame frame(int iw,int ih,int w,int h,boolean fit,boolean pan,float offset,EffectMotion.Frame motion){return frame(iw,ih,w,h,fit,pan,offset,motion,0,0,1);}
    /** cameraX/Y: the scene camera's offset from the canvas centre in content units (y up), unclamped like Wallpaper Engine's camera; cameraZoom multiplies. */
    public static Frame frame(int iw,int ih,int w,int h,boolean fit,boolean pan,float offset,EffectMotion.Frame motion,float cameraX,float cameraY,float cameraZoom){
        float scale=fit?Math.min(w/(float)iw,h/(float)ih):Math.max(w/(float)iw,h/(float)ih);
        scale*=motion.zoom()*(cameraZoom>0&&Float.isFinite(cameraZoom)?cameraZoom:1);float aspect=w/(float)h;
        double angle=Math.toRadians(motion.rotation()),c=Math.cos(angle),s=Math.sin(angle);
        if(!fit)scale*=Math.abs(c)+Math.abs(s)*Math.max(aspect,1/aspect);
        float marginX=Math.max(0,(iw*scale-w)/2),marginY=Math.max(0,(ih*scale-h)/2);
        float scroll=pan&&!fit?(offset-.5f)*Math.max(0,iw*scale-w):0;
        float dx=EffectMotion.clamp(motion.x()*w*2-scroll,-marginX,marginX)-cameraX*scale,dy=EffectMotion.clamp(motion.y()*h*2,-marginY,marginY)+cameraY*scale;
        return new Frame(iw*scale/w,ih*scale/h,(float)(c*dx-s*dy)*2/w,-(float)(s*dx+c*dy)*2/h,motion.rotation());
    }
}
