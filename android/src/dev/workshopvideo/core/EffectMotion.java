package dev.workshopvideo.core;
/** Bounded motion geometry shared by preview and wallpaper; values are normalized. */
public final class EffectMotion {
    public record Frame(float x,float y,float zoom,float rotation){}
    public static float clamp(float x,float min,float max){return Float.isFinite(x)?Math.max(min,Math.min(max,x)):min;}
    public static float approach(float current,float target,float dt,float smooth){float k=1-(float)Math.exp(-clamp(dt,0,.2f)/Math.max(.015f,smooth));return current+(target-current)*k;}
    public static Frame compose(float gx,float gy,float tx,float ty,float offset,float strength,float touch,float scroll,float zoom,float x,float y,float rotation,boolean fit){
        float mx=clamp(gx,-1,1)*clamp(strength,0,1)+clamp(tx,-1,1)*clamp(touch,0,1)+(clamp(offset,0,1)-.5f)*2*clamp(scroll,0,1);
        float my=clamp(gy,-1,1)*clamp(strength,0,1)+clamp(ty,-1,1)*clamp(touch,0,1);
        float z=clamp(zoom,1,2.5f);float margin=fit?0:(1-1/z)*.48f;
        return new Frame(clamp(x+mx*margin,-margin,margin),clamp(y+my*margin,-margin,margin),z,clamp(rotation,-15,15));
    }
}
