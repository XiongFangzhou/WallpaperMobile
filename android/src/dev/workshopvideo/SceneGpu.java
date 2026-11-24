package dev.workshopvideo;

import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import dev.workshopvideo.core.CanvasCamera;
import dev.workshopvideo.core.EffectMotion;
import dev.workshopvideo.core.SceneGeometry;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.IdentityHashMap;
import org.json.JSONObject;

/** Immutable layer textures; paging, parallax and reveal use uniforms, never full-frame uploads. */
final class SceneGpu implements AutoCloseable {
    static final String VERTEX="attribute vec2 point;uniform vec2 origin;uniform vec2 axisX;uniform vec2 axisY;uniform vec4 region;varying mediump vec2 uv;void main(){uv=mix(region.xy,region.zw,point);gl_Position=vec4(origin+point.x*axisX+point.y*axisY,0.0,1.0);}";
    static final String FRAGMENT="precision mediump float;uniform sampler2D image;uniform mat3 colors;uniform vec3 colorBias;uniform float opacity;uniform vec3 reveal;varying mediump vec2 uv;void main(){vec4 col=texture2D(image,uv);vec3 rgb=col.a>0.0001?col.rgb/col.a:vec3(0.0);float mask=reveal.z<0.0?1.0:1.0-smoothstep(reveal.z-0.5,reveal.z+0.5,distance(gl_FragCoord.xy,reveal.xy));float alpha=col.a*opacity*mask;gl_FragColor=vec4(clamp(colors*rgb+colorBias,0.0,1.0)*alpha,alpha);}";
    private final IdentityHashMap<Bitmap,Integer> textures=new IdentityHashMap<>();
    private final FloatBuffer quad=ByteBuffer.allocateDirect(8*4).order(ByteOrder.nativeOrder()).asFloatBuffer();
    private int program,point,origin,axisX,axisY,colors,bias,opacity,reveal,region,revision=-1;
    private final int maxTexture;
    private float[] rgb;
    SceneGpu(int maxTexture){this.maxTexture=maxTexture;quad.put(new float[]{0,0,1,0,0,1,1,1}).position(0);}
    private static int shader(int kind,String source){int shader=GLES20.glCreateShader(kind);GLES20.glShaderSource(shader,source);GLES20.glCompileShader(shader);int[] ok=new int[1];GLES20.glGetShaderiv(shader,GLES20.GL_COMPILE_STATUS,ok,0);if(ok[0]==0){String message=GLES20.glGetShaderInfoLog(shader);GLES20.glDeleteShader(shader);throw new IllegalStateException("场景着色器编译失败："+message);}return shader;}
    private void initialize(){
        if(program!=0)return;
        int vertex=shader(GLES20.GL_VERTEX_SHADER,VERTEX),fragment=shader(GLES20.GL_FRAGMENT_SHADER,FRAGMENT);
        program=GLES20.glCreateProgram();GLES20.glAttachShader(program,vertex);GLES20.glAttachShader(program,fragment);GLES20.glLinkProgram(program);GLES20.glDeleteShader(vertex);GLES20.glDeleteShader(fragment);
        int[] ok=new int[1];GLES20.glGetProgramiv(program,GLES20.GL_LINK_STATUS,ok,0);if(ok[0]==0)throw new IllegalStateException("场景着色器连接失败："+GLES20.glGetProgramInfoLog(program));
        point=GLES20.glGetAttribLocation(program,"point");origin=GLES20.glGetUniformLocation(program,"origin");axisX=GLES20.glGetUniformLocation(program,"axisX");axisY=GLES20.glGetUniformLocation(program,"axisY");colors=GLES20.glGetUniformLocation(program,"colors");bias=GLES20.glGetUniformLocation(program,"colorBias");opacity=GLES20.glGetUniformLocation(program,"opacity");reveal=GLES20.glGetUniformLocation(program,"reveal");region=GLES20.glGetUniformLocation(program,"region");
    }
    private int texture(Bitmap source){
        Integer existing=textures.get(source);if(existing!=null)return existing;
        int[] id=new int[1];GLES20.glGenTextures(1,id,0);textures.put(source,id[0]);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,id[0]);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE);
        float scale=Math.min(1,maxTexture/(float)Math.max(source.getWidth(),source.getHeight()));Bitmap upload=scale==1?source:Bitmap.createScaledBitmap(source,Math.max(1,Math.round(source.getWidth()*scale)),Math.max(1,Math.round(source.getHeight()*scale)),true);
        try{GLUtils.texImage2D(GLES20.GL_TEXTURE_2D,0,upload,0);}finally{if(upload!=source)upload.recycle();}return id[0];
    }
    static SceneGeometry.Pose pose(WallpaperContent.Scene scene,WallpaperContent.Layer layer,int index,JSONObject options,CanvasCamera.Frame camera,EffectMotion.Frame motion){
        float depth=(float)options.optDouble("depth"+index,layer.depth)*(float)options.optDouble("layerDepth",1);
        float dx=(float)options.optDouble("layerX"+index,0)*scene.width+motion.x()*scene.width*.5f*depth,dy=(float)options.optDouble("layerY"+index,0)*scene.height+motion.y()*scene.height*.5f*depth;
        return SceneGeometry.pose(camera,scene.width,scene.height,layer.x+dx,layer.y+dy,layer.w,layer.h,layer.rotation);
    }
    void draw(WallpaperContent.Scene scene,WallpaperEffects effects,CanvasCamera.Frame camera,EffectMotion.Frame motion,int width,int height){draw(scene,effects,camera,motion,width,height,0);}
    /** Animated layers only change the sampled region; their atlas texture is uploaded once. */
    void draw(WallpaperContent.Scene scene,WallpaperEffects effects,CanvasCamera.Frame camera,EffectMotion.Frame motion,int width,int height,double seconds){
        initialize();GLES20.glUseProgram(program);GLES20.glActiveTexture(GLES20.GL_TEXTURE0);GLES20.glUniform1i(GLES20.glGetUniformLocation(program,"image"),0);
        quad.position(0);GLES20.glVertexAttribPointer(point,2,GLES20.GL_FLOAT,false,8,quad);GLES20.glEnableVertexAttribArray(point);
        JSONObject o=effects.options;
        if(revision!=effects.optionsRevision){rgb=dev.workshopvideo.core.PictureColors.matrix((float)o.optDouble("brightness",1),(float)o.optDouble("contrast",1),(float)o.optDouble("saturation",1),(float)o.optDouble("hue",0));revision=effects.optionsRevision;}
        float[] matrix={rgb[0],rgb[4],rgb[8],rgb[1],rgb[5],rgb[9],rgb[2],rgb[6],rgb[10]};GLES20.glUniformMatrix3fv(colors,1,false,matrix,0);GLES20.glUniform3f(bias,rgb[3],rgb[7],rgb[11]);
        GLES20.glEnable(GLES20.GL_BLEND);GLES20.glBlendFunc(GLES20.GL_ONE,GLES20.GL_ONE_MINUS_SRC_ALPHA);
        try{for(int i=0;i<scene.layers.size();i++){
            WallpaperContent.Layer layer=scene.layers.get(i);
            SceneGeometry.Pose pose=pose(scene,layer,i,o,camera,motion);
            GLES20.glUniform2f(origin,pose.x(),pose.y());GLES20.glUniform2f(axisX,pose.xx(),pose.xy());GLES20.glUniform2f(axisY,pose.yx(),pose.yy());GLES20.glUniform1f(opacity,EffectMotion.clamp(layer.alpha*(float)o.optDouble("opacity"+i,1),0,1));
            var frame=layer.region(seconds);GLES20.glUniform4f(region,frame.u0(),frame.v0(),frame.u1(),frame.v1());GLES20.glUniform3f(reveal,0,0,-1);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,texture(layer.bitmap));GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);
            if(layer.reveal!=null&&o.optBoolean("xray",true)&&effects.pointerDown()){
                float radius=layer.w*(float)o.optDouble("xraySize",layer.revealSize)*.5f*camera.scaleX()*width/scene.width;
                GLES20.glUniform4f(region,0,0,1,1);GLES20.glUniform3f(reveal,effects.pointerX()*width,(1-effects.pointerY())*height,Math.max(1,radius));GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,texture(layer.reveal));GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);
            }
        }}finally{GLES20.glDisable(GLES20.GL_BLEND);GLES20.glDisableVertexAttribArray(point);}
    }
    int textureCount(){return textures.size();}
    @Override public void close(){for(int texture:textures.values())GLES20.glDeleteTextures(1,new int[]{texture},0);textures.clear();if(program!=0)GLES20.glDeleteProgram(program);program=0;}
}
