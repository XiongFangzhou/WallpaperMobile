package dev.workshopvideo.core;

/** Layer-local unit square to clip space, using the same camera order as Canvas. */
public final class SceneGeometry {
    private SceneGeometry(){}
    public record Pose(float x,float y,float xx,float xy,float yx,float yy){}
    public static Pose pose(CanvasCamera.Frame camera,int iw,int ih,float x,float y,float w,float h,float rotation){
        double a=Math.toRadians(rotation),c=Math.cos(a),s=Math.sin(a);
        float ox=x+w/2-(float)(c*w/2-s*h/2),oy=y+h/2-(float)(s*w/2+c*h/2);
        double b=Math.toRadians(camera.angle()),bc=Math.cos(b),bs=Math.sin(b);
        float kx=2*camera.scaleX()/iw,ky=2*camera.scaleY()/ih;
        float px=ox-iw/2f,py=oy-ih/2f;
        return new Pose(camera.x()+(float)(bc*px-bs*py)*kx,camera.y()-(float)(bs*px+bc*py)*ky,
            (float)(bc*c-bs*s)*w*kx,-(float)(bs*c+bc*s)*w*ky,
            (float)(-bc*s-bs*c)*h*kx,-(float)(-bs*s+bc*c)*h*ky);
    }
}
