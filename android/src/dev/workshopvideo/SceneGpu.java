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
    /** point: position inside the layer rectangle (0..1, y down); texcoord: texture position, equal to point for rectangles and the mesh UV for skeletal layers. */
    static final String VERTEX="attribute vec2 point;attribute vec2 texcoord;uniform vec2 origin;uniform vec2 axisX;uniform vec2 axisY;uniform vec4 region;varying mediump vec2 uv;void main(){uv=mix(region.xy,region.zw,texcoord);gl_Position=vec4(origin+point.x*axisX+point.y*axisY,0.0,1.0);}";
    static final String FRAGMENT="precision mediump float;uniform sampler2D image;uniform mat3 colors;uniform vec3 colorBias;uniform float opacity;uniform float straight;uniform vec3 reveal;varying mediump vec2 uv;void main(){vec4 col=texture2D(image,uv);vec3 rgb=straight>0.5?col.rgb:(col.a>0.0001?col.rgb/col.a:vec3(0.0));float mask=reveal.z<0.0?1.0:1.0-smoothstep(reveal.z-0.5,reveal.z+0.5,distance(gl_FragCoord.xy,reveal.xy));float alpha=col.a*opacity*mask;gl_FragColor=vec4(clamp(colors*rgb+colorBias,0.0,1.0)*alpha,alpha);}";
    private final IdentityHashMap<Bitmap,Integer> textures=new IdentityHashMap<>();
    private final IdentityHashMap<Bitmap,int[]> sizes=new IdentityHashMap<>();
    private final boolean effectsSupported;private SceneEffectRunner effectRunner;
    private final FloatBuffer quad=ByteBuffer.allocateDirect(8*4).order(ByteOrder.nativeOrder()).asFloatBuffer();
    /** Skeletal meshes: static UVs and triangles, vertex positions rewritten each frame. */
    private static final class MeshBuffers {final FloatBuffer points,uvs;final java.nio.ShortBuffer indices;final int count;
        MeshBuffers(dev.workshopvideo.core.PuppetModel.Mesh mesh){points=ByteBuffer.allocateDirect(mesh.positions.length*4).order(ByteOrder.nativeOrder()).asFloatBuffer();uvs=ByteBuffer.allocateDirect(mesh.uvs.length*4).order(ByteOrder.nativeOrder()).asFloatBuffer();uvs.put(mesh.uvs).position(0);indices=ByteBuffer.allocateDirect(mesh.indices.length*2).order(ByteOrder.nativeOrder()).asShortBuffer();indices.put(mesh.indices).position(0);count=mesh.indices.length;}}
    private final IdentityHashMap<WallpaperContent.Layer,MeshBuffers> meshes=new IdentityHashMap<>();
    /** Uniform locations of one program; both programs bind point=0 and texcoord=1 so vertex pointers stay valid across switches. */
    private static final class Locations {final int program,image,origin,axisX,axisY,colors,bias,opacity,straight,reveal,region,backdrop,canvas,mode;
        Locations(int p){program=p;image=u(p,"image");origin=u(p,"origin");axisX=u(p,"axisX");axisY=u(p,"axisY");colors=u(p,"colors");bias=u(p,"colorBias");opacity=u(p,"opacity");straight=u(p,"straight");reveal=u(p,"reveal");region=u(p,"region");backdrop=u(p,"backdrop");canvas=u(p,"canvas");mode=u(p,"mode");}
        private static int u(int p,String name){return GLES20.glGetUniformLocation(p,name);}}
    private static final int POINT=0,TEXCOORD=1,COLOR=2;
    /** Particles: one textured quad per particle with a premultiplied colour; additive particles carry zero alpha. */
    static final String PARTICLE_VERTEX="attribute vec2 point;attribute vec2 texcoord;attribute vec4 color;uniform vec2 origin;uniform vec2 axisX;uniform vec2 axisY;varying mediump vec2 uv;varying lowp vec4 tint;void main(){uv=texcoord;tint=color;gl_Position=vec4(origin+point.x*axisX+point.y*axisY,0.0,1.0);}";
    static final String PARTICLE_FRAGMENT="precision mediump float;uniform sampler2D image;uniform float opacity;varying mediump vec2 uv;varying lowp vec4 tint;void main(){vec4 t=texture2D(image,uv);gl_FragColor=vec4(t.rgb*tint.rgb,t.a*tint.a)*opacity;}";
    private static final class ParticleBuffers {final float[] points,uvs,colors;final FloatBuffer pointBuffer,uvBuffer,colorBuffer;final java.nio.ShortBuffer indices;
        ParticleBuffers(int capacity){points=new float[capacity*8];uvs=new float[capacity*8];colors=new float[capacity*16];
            pointBuffer=ByteBuffer.allocateDirect(points.length*4).order(ByteOrder.nativeOrder()).asFloatBuffer();uvBuffer=ByteBuffer.allocateDirect(uvs.length*4).order(ByteOrder.nativeOrder()).asFloatBuffer();colorBuffer=ByteBuffer.allocateDirect(colors.length*4).order(ByteOrder.nativeOrder()).asFloatBuffer();
            indices=ByteBuffer.allocateDirect(capacity*12).order(ByteOrder.nativeOrder()).asShortBuffer();for(int q=0;q<capacity;q++){int v=q*4;indices.put(new short[]{(short)v,(short)(v+1),(short)(v+2),(short)v,(short)(v+2),(short)(v+3)});}indices.position(0);}}
    private final IdentityHashMap<WallpaperContent.Layer,ParticleBuffers> particleBuffers=new IdentityHashMap<>();
    private Locations particle;
    /** WE common_blending.h, extracted from the vendored WebWallGL renderer (assets/we-blending.glsl). */
    static volatile String blendingSource;
    static void loadBlending(android.content.Context context){if(blendingSource!=null)return;try(java.io.InputStream in=context.getAssets().open("we-blending.glsl")){blendingSource=new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}catch(java.io.IOException missing){blendingSource="";}}
    static final String BLEND_VERTEX="#version 300 es\nin vec2 point;in vec2 texcoord;uniform vec2 origin;uniform vec2 axisX;uniform vec2 axisY;uniform vec4 region;out mediump vec2 uv;void main(){uv=mix(region.xy,region.zw,texcoord);gl_Position=vec4(origin+point.x*axisX+point.y*axisY,0.0,1.0);}";
    /** Layer colour blend modes: the backdrop is a copy of what is already drawn, combined with ApplyBlending as WE's composite pass does. */
    static String blendFragment(String blending){return "#version 300 es\nprecision mediump float;uniform sampler2D image;uniform sampler2D backdrop;uniform vec2 canvas;uniform int mode;uniform mat3 colors;uniform vec3 colorBias;uniform float opacity;uniform float straight;in mediump vec2 uv;out vec4 fragColor;\n"+blending+"\nvoid main(){vec4 col=texture(image,uv);vec3 rgb=straight>0.5?col.rgb:(col.a>0.0001?col.rgb/col.a:vec3(0.0));rgb=clamp(colors*rgb+colorBias,0.0,1.0);float a=col.a*opacity;if(a<0.004)discard;vec3 dst=texture(backdrop,gl_FragCoord.xy/canvas).rgb;vec3 r=ApplyBlending(mode,dst,rgb,a);if(mode==5||mode==10)r=mix(dst,r,a);fragColor=vec4(r,1.0);}";}
    private Locations main,blend;private boolean blendTried;private int backdrop,backdropWidth,backdropHeight,revision=-1;
    private final int maxTexture;
    private float[] rgb;
    SceneGpu(int maxTexture){this(maxTexture,false);}
    /** Author effects need a GLES 3 context; on GLES 2 layers draw without them. */
    SceneGpu(int maxTexture,boolean gles3){this.maxTexture=maxTexture;this.effectsSupported=gles3;quad.put(new float[]{0,0,1,0,0,1,1,1}).position(0);}
    private static int shader(int kind,String source){int shader=GLES20.glCreateShader(kind);GLES20.glShaderSource(shader,source);GLES20.glCompileShader(shader);int[] ok=new int[1];GLES20.glGetShaderiv(shader,GLES20.GL_COMPILE_STATUS,ok,0);if(ok[0]==0){String message=GLES20.glGetShaderInfoLog(shader);GLES20.glDeleteShader(shader);throw new IllegalStateException("场景着色器编译失败："+message);}return shader;}
    private static int link(String vertexSource,String fragmentSource){
        int vertex=shader(GLES20.GL_VERTEX_SHADER,vertexSource),fragment=shader(GLES20.GL_FRAGMENT_SHADER,fragmentSource);
        int program=GLES20.glCreateProgram();GLES20.glAttachShader(program,vertex);GLES20.glAttachShader(program,fragment);GLES20.glBindAttribLocation(program,POINT,"point");GLES20.glBindAttribLocation(program,TEXCOORD,"texcoord");GLES20.glBindAttribLocation(program,COLOR,"color");GLES20.glLinkProgram(program);GLES20.glDeleteShader(vertex);GLES20.glDeleteShader(fragment);
        int[] ok=new int[1];GLES20.glGetProgramiv(program,GLES20.GL_LINK_STATUS,ok,0);if(ok[0]==0){String log=GLES20.glGetProgramInfoLog(program);GLES20.glDeleteProgram(program);throw new IllegalStateException("场景着色器连接失败："+log);}
        return program;
    }
    private void initialize(){if(main==null)main=new Locations(link(VERTEX,FRAGMENT));}
    /** Blend-mode program, GLES 3 only; when unavailable blended layers draw normally. */
    private Locations blendProgram(){
        if(!blendTried){blendTried=true;String source=blendingSource;if(effectsSupported&&source!=null&&!source.isEmpty())try{blend=new Locations(link(BLEND_VERTEX,blendFragment(source)));}catch(RuntimeException unsupported){android.util.Log.w("SceneGpu","blend modes unavailable",unsupported);blend=null;}}
        return blend;
    }
    private void use(Locations p,float[] matrix){GLES20.glUseProgram(p.program);GLES20.glUniform1i(p.image,0);GLES20.glUniformMatrix3fv(p.colors,1,false,matrix,0);GLES20.glUniform3f(p.bias,rgb[3],rgb[7],rgb[11]);}
    /** Copies what has been drawn so far into the backdrop texture on unit 1. */
    private void copyBackdrop(int width,int height){
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
        if(backdrop==0){int[] id=new int[1];GLES20.glGenTextures(1,id,0);backdrop=id[0];}
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,backdrop);
        if(backdropWidth!=width||backdropHeight!=height){GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_NEAREST);GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_NEAREST);GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D,0,GLES20.GL_RGBA,width,height,0,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,null);backdropWidth=width;backdropHeight=height;}
        GLES20.glCopyTexSubImage2D(GLES20.GL_TEXTURE_2D,0,0,0,0,0,width,height);GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
    }
    private int texture(Bitmap source){
        Integer existing=textures.get(source);if(existing!=null)return existing;
        int[] id=new int[1];GLES20.glGenTextures(1,id,0);textures.put(source,id[0]);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,id[0]);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE);
        float scale=Math.min(1,maxTexture/(float)Math.max(source.getWidth(),source.getHeight()));Bitmap upload=scale==1?source:Bitmap.createScaledBitmap(source,Math.max(1,Math.round(source.getWidth()*scale)),Math.max(1,Math.round(source.getHeight()*scale)),true);
        sizes.put(source,new int[]{upload.getWidth(),upload.getHeight()});
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
        initialize();scene.animate(seconds);
        // Effect chains render into their own framebuffers first, so the composition below keeps one GL state.
        int[] processed=new int[scene.layers.size()];
        if(effectsSupported)for(int i=0;i<processed.length;i++){
            WallpaperContent.Layer layer=scene.layers.get(i);if(!layer.shown(effects.options,scene.properties)||layer.effects.isEmpty()||!SceneEffectRunner.active(layer))continue;
            if(effectRunner==null)effectRunner=new SceneEffectRunner();int source=texture(layer.bitmap);int[] size=sizes.get(layer.bitmap);
            processed[i]=effectRunner.apply(layer,source,size[0],size[1],layer.region(seconds),seconds,effects.pointerX(),effects.pointerY());
        }
        GLES20.glViewport(0,0,width,height);GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        bindQuad();GLES20.glEnableVertexAttribArray(POINT);GLES20.glEnableVertexAttribArray(TEXCOORD);
        JSONObject o=effects.options;
        if(revision!=effects.optionsRevision){rgb=dev.workshopvideo.core.PictureColors.matrix((float)o.optDouble("brightness",1),(float)o.optDouble("contrast",1),(float)o.optDouble("saturation",1),(float)o.optDouble("hue",0));revision=effects.optionsRevision;}
        float[] matrix={rgb[0],rgb[4],rgb[8],rgb[1],rgb[5],rgb[9],rgb[2],rgb[6],rgb[10]};use(main,matrix);
        GLES20.glEnable(GLES20.GL_BLEND);GLES20.glBlendFunc(GLES20.GL_ONE,GLES20.GL_ONE_MINUS_SRC_ALPHA);
        try{for(int i=0;i<scene.layers.size();i++){
            WallpaperContent.Layer layer=scene.layers.get(i);if(!layer.shown(o,scene.properties)||layer.alpha<=0)continue;
            if(layer.particles!=null){drawParticles(layer,pose(scene,layer,i,o,camera,motion),EffectMotion.clamp(layer.alpha*(float)o.optDouble("opacity"+i,1),0,1),scene);use(main,matrix);continue;}
            Locations p=layer.blend!=0?blendProgram():null;if(p!=null){copyBackdrop(width,height);use(p,matrix);GLES20.glUniform1i(p.backdrop,1);GLES20.glUniform2f(p.canvas,width,height);GLES20.glUniform1i(p.mode,layer.blend);GLES20.glDisable(GLES20.GL_BLEND);}else p=main;
            if(layer.additive&&p==main)GLES20.glBlendFunc(GLES20.GL_ONE,GLES20.GL_ONE);
            SceneGeometry.Pose pose=pose(scene,layer,i,o,camera,motion);
            GLES20.glUniform2f(p.origin,pose.x(),pose.y());GLES20.glUniform2f(p.axisX,pose.xx(),pose.xy());GLES20.glUniform2f(p.axisY,pose.yx(),pose.yy());GLES20.glUniform1f(p.opacity,EffectMotion.clamp(layer.alpha*(float)o.optDouble("opacity"+i,1),0,1));
            var frame=processed[i]!=0?WallpaperContent.FULL:layer.region(seconds);GLES20.glUniform4f(p.region,frame.u0(),frame.v0(),frame.u1(),frame.v1());GLES20.glUniform1f(p.straight,processed[i]!=0?1:0);GLES20.glUniform3f(p.reveal,0,0,-1);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,processed[i]!=0?processed[i]:texture(layer.bitmap));
            drawGeometry(layer);
            if(p!=main){GLES20.glEnable(GLES20.GL_BLEND);use(main,matrix);}
            if(layer.additive)GLES20.glBlendFunc(GLES20.GL_ONE,GLES20.GL_ONE_MINUS_SRC_ALPHA);
            if(layer.reveal!=null&&o.optBoolean("xray",true)&&effects.pointerDown()){
                float radius=layer.w*(float)o.optDouble("xraySize",layer.revealSize)*.5f*camera.scaleX()*width/scene.width;
                GLES20.glUniform2f(main.origin,pose.x(),pose.y());GLES20.glUniform2f(main.axisX,pose.xx(),pose.xy());GLES20.glUniform2f(main.axisY,pose.yx(),pose.yy());
                GLES20.glUniform4f(main.region,0,0,1,1);GLES20.glUniform1f(main.straight,0);GLES20.glUniform3f(main.reveal,effects.touchX()*width,(1-effects.touchY())*height,Math.max(1,radius));GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,texture(layer.reveal));GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);
            }
        }}finally{GLES20.glDisable(GLES20.GL_BLEND);GLES20.glDisableVertexAttribArray(POINT);GLES20.glDisableVertexAttribArray(TEXCOORD);}
    }
    /** All live particles of a layer in one indexed draw. */
    private void drawParticles(WallpaperContent.Layer layer,SceneGeometry.Pose pose,float alpha,WallpaperContent.Scene scene){
        if(particle==null)particle=new Locations(link(PARTICLE_VERTEX,PARTICLE_FRAGMENT));
        ParticleBuffers b=particleBuffers.computeIfAbsent(layer,l->new ParticleBuffers(l.particles.capacity()));
        int quads=layer.particles.vertices(layer.particleTransform,scene.width,scene.height,1,b.points,b.uvs,b.colors);if(quads==0)return;
        b.pointBuffer.position(0);b.pointBuffer.put(b.points,0,quads*8).position(0);b.uvBuffer.position(0);b.uvBuffer.put(b.uvs,0,quads*8).position(0);b.colorBuffer.position(0);b.colorBuffer.put(b.colors,0,quads*16).position(0);
        GLES20.glUseProgram(particle.program);GLES20.glUniform1i(particle.image,0);GLES20.glUniform1f(particle.opacity,alpha);
        GLES20.glUniform2f(particle.origin,pose.x(),pose.y());GLES20.glUniform2f(particle.axisX,pose.xx(),pose.xy());GLES20.glUniform2f(particle.axisY,pose.yx(),pose.yy());
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,texture(layer.bitmap));
        GLES20.glVertexAttribPointer(POINT,2,GLES20.GL_FLOAT,false,8,b.pointBuffer);GLES20.glVertexAttribPointer(TEXCOORD,2,GLES20.GL_FLOAT,false,8,b.uvBuffer);
        GLES20.glVertexAttribPointer(COLOR,4,GLES20.GL_FLOAT,false,16,b.colorBuffer);GLES20.glEnableVertexAttribArray(COLOR);
        b.indices.position(0);GLES20.glDrawElements(GLES20.GL_TRIANGLES,quads*6,GLES20.GL_UNSIGNED_SHORT,b.indices);
        GLES20.glDisableVertexAttribArray(COLOR);bindQuad();
    }
    /** The layer rectangle, or its skeletal mesh at the current pose. */
    private void drawGeometry(WallpaperContent.Layer layer){
        if(layer.meshPoints==null){GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);return;}
        MeshBuffers mesh=meshes.computeIfAbsent(layer,l->new MeshBuffers(l.puppet.mesh));mesh.points.position(0);mesh.points.put(layer.meshPoints).position(0);
        GLES20.glVertexAttribPointer(POINT,2,GLES20.GL_FLOAT,false,8,mesh.points);GLES20.glVertexAttribPointer(TEXCOORD,2,GLES20.GL_FLOAT,false,8,mesh.uvs);
        GLES20.glDrawElements(GLES20.GL_TRIANGLES,mesh.count,GLES20.GL_UNSIGNED_SHORT,mesh.indices);bindQuad();
    }
    private void bindQuad(){quad.position(0);GLES20.glVertexAttribPointer(POINT,2,GLES20.GL_FLOAT,false,8,quad);quad.position(0);GLES20.glVertexAttribPointer(TEXCOORD,2,GLES20.GL_FLOAT,false,8,quad);}
    int textureCount(){return textures.size();}
    @Override public void close(){if(effectRunner!=null){effectRunner.close();effectRunner=null;}sizes.clear();meshes.clear();particleBuffers.clear();if(particle!=null)GLES20.glDeleteProgram(particle.program);particle=null;for(int texture:textures.values())GLES20.glDeleteTextures(1,new int[]{texture},0);textures.clear();if(main!=null)GLES20.glDeleteProgram(main.program);if(blend!=null)GLES20.glDeleteProgram(blend.program);main=null;blend=null;blendTried=false;if(backdrop!=0)GLES20.glDeleteTextures(1,new int[]{backdrop},0);backdrop=0;backdropWidth=backdropHeight=0;}
}
