package dev.workshopvideo.core;

/** Layer-local unit square to clip space, using the same camera order as Canvas. */
public final class SceneGeometry {
    private SceneGeometry(){}
    public record Pose(float x,float y,float xx,float xy,float yx,float yy){}
    public static Pose pose(CanvasCamera.Frame camera,int iw,int ih,float x,float y,float w,float h,float rotation){return pose(camera,iw,ih,x,y,w,h,rotation,null);}
    /**
     * shape: optional 2×2 matrix {m00, m01, m10, m11} (y down) applied to the layer's width and height vectors before its
     * rotation: mirroring and the orthographic projection of x/y rotations. Null is the identity.
     */
    public static Pose pose(CanvasCamera.Frame camera,int iw,int ih,float x,float y,float w,float h,float rotation,float[] shape){
        double a=Math.toRadians(rotation),c=Math.cos(a),s=Math.sin(a);
        double wx=w,wy=0,hx=0,hy=h;
        if(shape!=null&&shape.length==4){wx=shape[0]*w;wy=shape[2]*w;hx=shape[1]*h;hy=shape[3]*h;}
        double rwx=c*wx-s*wy,rwy=s*wx+c*wy,rhx=c*hx-s*hy,rhy=s*hx+c*hy;
        float ox=x+w/2-(float)((rwx+rhx)/2),oy=y+h/2-(float)((rwy+rhy)/2);
        double b=Math.toRadians(camera.angle()),bc=Math.cos(b),bs=Math.sin(b);
        float kx=2*camera.scaleX()/iw,ky=2*camera.scaleY()/ih;
        float px=ox-iw/2f,py=oy-ih/2f;
        return new Pose(camera.x()+(float)(bc*px-bs*py)*kx,camera.y()-(float)(bs*px+bc*py)*ky,
            (float)(bc*rwx-bs*rwy)*kx,-(float)(bs*rwx+bc*rwy)*ky,
            (float)(bc*rhx-bs*rhy)*kx,-(float)(bs*rhx+bc*rhy)*ky);
    }
    /**
     * The affine map {a, b, c, d, tx, ty} (x' = a·x + b·y + tx, y' = c·x + d·y + ty) taking a layer's rectangle, placed in
     * clip space by p, onto a whole buffer: its top-left corner to (-1,-1) and its far corner to (1,1), so the buffer's
     * first row holds the layer's top edge, as an uploaded image does. Null when the rectangle is degenerate.
     */
    public static float[] onto(Pose p){
        double det=(double)p.xx()*p.yy()-(double)p.yx()*p.xy();if(!(Math.abs(det)>1e-12))return null;
        double a=2*p.yy()/det,b=-2*p.yx()/det,c=-2*p.xy()/det,d=2*p.xx()/det;
        return new float[]{(float)a,(float)b,(float)c,(float)d,(float)(-(a*p.x()+b*p.y())-1),(float)(-(c*p.x()+d*p.y())-1)};
    }
    /** A pose seen through an affine map from {@link #onto}; null is the identity. */
    public static Pose map(float[] m,Pose p){
        if(m==null)return p;
        return new Pose(m[0]*p.x()+m[1]*p.y()+m[4],m[2]*p.x()+m[3]*p.y()+m[5],m[0]*p.xx()+m[1]*p.xy(),m[2]*p.xx()+m[3]*p.xy(),m[0]*p.yx()+m[1]*p.yy(),m[2]*p.yx()+m[3]*p.yy());
    }
    /**
     * g_EffectModelViewProjectionMatrix for effect passes on a layer placed by p, as glUniformMatrix4fv data. An effect
     * pass draws a quad with a_Position -1..1 (-1 at the layer's top edge, where its texture starts); translated shaders
     * compute transpose(M)·position (HLSL mul(position, M)), which this makes the clip position of that point on the
     * layer, so screen-space lookups (_rt_FullFrameBuffer) sample what lies under the layer.
     */
    public static float[] effectMatrix(Pose p){
        float cx=p.x()+(p.xx()+p.yx())/2,cy=p.y()+(p.xy()+p.yy())/2;
        return new float[]{p.xx()/2,p.yx()/2,0,cx,p.xy()/2,p.yy()/2,0,cy,0,0,1,0,0,0,0,1};
    }
    /**
     * g_ModelViewProjectionMatrixInverse for effect passes on a layer placed by p whose effect buffers are w × h, as
     * glUniformMatrix4fv data: a clip position to the layer's model space as Wallpaper Engine defines it (pixels of
     * those buffers from the layer's centre, y up). Effects use it to unproject the pointer (X-ray, cursor ripples).
     * Null when the placement is degenerate.
     */
    public static float[] pointerInverse(Pose p,float w,float h){
        double det=(double)p.xx()*p.yy()-(double)p.yx()*p.xy();if(!(Math.abs(det)>1e-12))return null;
        double i00=p.yy()/det,i01=-p.yx()/det,i10=-p.xy()/det,i11=p.xx()/det;
        return new float[]{(float)(w*i00),(float)(w*i01),0,(float)(w*(-(i00*p.x()+i01*p.y())-.5)),
            (float)(-h*i10),(float)(-h*i11),0,(float)(h*((i10*p.x()+i11*p.y())+.5)),0,0,1,0,0,0,0,1};
    }
    /** Screen position (0..1, y down) to scene coordinates (pixels, y up as Wallpaper Engine scripts see them); inverse of {@link #pose}. */
    public static float[] sceneFromScreen(CanvasCamera.Frame camera,int iw,int ih,float screenX,float screenY){
        double b=Math.toRadians(camera.angle()),bc=Math.cos(b),bs=Math.sin(b);
        float kx=2*camera.scaleX()/iw,ky=2*camera.scaleY()/ih;
        double u=(2*screenX-1-camera.x())/kx,v=(camera.y()-(1-2*screenY))/ky;
        double px=bc*u+bs*v,py=-bs*u+bc*v;
        return new float[]{(float)(px+iw/2.0),(float)(ih-(py+ih/2.0))};
    }
}
