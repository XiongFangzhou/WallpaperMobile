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
    static final String FRAGMENT="precision mediump float;uniform sampler2D image;uniform mat3 colors;uniform vec3 colorBias;uniform float opacity;uniform float straight;uniform vec3 reveal;uniform vec3 tint;varying mediump vec2 uv;void main(){vec4 col=texture2D(image,uv);vec3 rgb=(straight>0.5?col.rgb:(col.a>0.0001?col.rgb/col.a:vec3(0.0)))*tint;float mask=reveal.z<0.0?1.0:1.0-smoothstep(reveal.z-0.5,reveal.z+0.5,distance(gl_FragCoord.xy,reveal.xy));float alpha=col.a*opacity*mask;gl_FragColor=vec4(clamp(colors*rgb+colorBias,0.0,1.0)*alpha,alpha);}";
    private final IdentityHashMap<Bitmap,Integer> textures=new IdentityHashMap<>();
    /** Text bitmaps replaced this frame, freed with their textures. */
    private final java.util.ArrayList<Bitmap> retired=new java.util.ArrayList<>();
    private final IdentityHashMap<Bitmap,int[]> sizes=new IdentityHashMap<>();
    private final boolean effectsSupported;private SceneEffectRunner effectRunner;
    private final FloatBuffer quad=ByteBuffer.allocateDirect(8*4).order(ByteOrder.nativeOrder()).asFloatBuffer();
    /** Skeletal meshes: static UVs and triangles, vertex positions rewritten each frame. */
    private static final class MeshBuffers {final FloatBuffer points,uvs;final java.nio.ShortBuffer indices;final int count;
        MeshBuffers(dev.workshopvideo.core.PuppetModel.Mesh mesh){points=ByteBuffer.allocateDirect(mesh.positions.length*4).order(ByteOrder.nativeOrder()).asFloatBuffer();uvs=ByteBuffer.allocateDirect(mesh.uvs.length*4).order(ByteOrder.nativeOrder()).asFloatBuffer();uvs.put(mesh.uvs).position(0);indices=ByteBuffer.allocateDirect(mesh.indices.length*2).order(ByteOrder.nativeOrder()).asShortBuffer();indices.put(mesh.indices).position(0);count=mesh.indices.length;}}
    private final IdentityHashMap<WallpaperContent.Layer,MeshBuffers> meshes=new IdentityHashMap<>();
    /** Uniform locations of one program; both programs bind point=0 and texcoord=1 so vertex pointers stay valid across switches. */
    private static final class Locations {final int program,image,origin,axisX,axisY,colors,bias,opacity,straight,reveal,region,backdrop,canvas,mode,tint;
        Locations(int p){program=p;image=u(p,"image");origin=u(p,"origin");axisX=u(p,"axisX");axisY=u(p,"axisY");colors=u(p,"colors");bias=u(p,"colorBias");opacity=u(p,"opacity");straight=u(p,"straight");reveal=u(p,"reveal");region=u(p,"region");backdrop=u(p,"backdrop");canvas=u(p,"canvas");mode=u(p,"mode");tint=u(p,"tint");}
        private static int u(int p,String name){return GLES20.glGetUniformLocation(p,name);}}
    private static final int POINT=0,TEXCOORD=1,COLOR=2;
    /** Particles: one textured quad per particle with a premultiplied colour; additive particles carry zero alpha. */
    static final String PARTICLE_VERTEX="attribute vec2 point;attribute vec2 texcoord;attribute vec4 color;uniform vec2 origin;uniform vec2 axisX;uniform vec2 axisY;varying mediump vec2 uv;varying lowp vec4 tint;void main(){uv=texcoord;tint=color;gl_Position=vec4(origin+point.x*axisX+point.y*axisY,0.0,1.0);}";
    static final String PARTICLE_FRAGMENT="precision mediump float;uniform sampler2D image;uniform float opacity;varying mediump vec2 uv;varying lowp vec4 tint;void main(){vec4 t=texture2D(image,uv);gl_FragColor=vec4(t.rgb*tint.rgb,t.a*tint.a)*opacity;}";
    private static final class ParticleBuffers {final int capacity;final float[] points,uvs,colors;final FloatBuffer pointBuffer,uvBuffer,colorBuffer;final java.nio.ShortBuffer indices;
        ParticleBuffers(int capacity){this.capacity=capacity;points=new float[capacity*8];uvs=new float[capacity*8];colors=new float[capacity*16];
            pointBuffer=ByteBuffer.allocateDirect(points.length*4).order(ByteOrder.nativeOrder()).asFloatBuffer();uvBuffer=ByteBuffer.allocateDirect(uvs.length*4).order(ByteOrder.nativeOrder()).asFloatBuffer();colorBuffer=ByteBuffer.allocateDirect(colors.length*4).order(ByteOrder.nativeOrder()).asFloatBuffer();
            indices=ByteBuffer.allocateDirect(capacity*12).order(ByteOrder.nativeOrder()).asShortBuffer();for(int q=0;q<capacity;q++){int v=q*4;indices.put(new short[]{(short)v,(short)(v+1),(short)(v+2),(short)v,(short)(v+2),(short)(v+3)});}indices.position(0);}}
    private final IdentityHashMap<WallpaperContent.Layer,ParticleBuffers> particleBuffers=new IdentityHashMap<>();
    private Locations particle;
    /** Effect steps above which layer effects update every other frame. */
    static final int HEAVY_EFFECT_STEPS=32;private long frameCount;private final IdentityHashMap<WallpaperContent.Layer,Integer> lastEffect=new IdentityHashMap<>();
    /** WE common_blending.h, extracted from the vendored WebWallGL renderer (assets/we-blending.glsl). */
    static volatile String blendingSource;
    static void loadBlending(android.content.Context context){if(blendingSource!=null)return;try(java.io.InputStream in=context.getAssets().open("we-blending.glsl")){blendingSource=new String(dev.workshopvideo.core.VideoFiles.readAll(in),java.nio.charset.StandardCharsets.UTF_8);}catch(java.io.IOException missing){blendingSource="";}}
    static final String BLEND_VERTEX="#version 300 es\nin vec2 point;in vec2 texcoord;uniform vec2 origin;uniform vec2 axisX;uniform vec2 axisY;uniform vec4 region;out mediump vec2 uv;void main(){uv=mix(region.xy,region.zw,texcoord);gl_Position=vec4(origin+point.x*axisX+point.y*axisY,0.0,1.0);}";
    /** Layer colour blend modes: the backdrop is a copy of what is already drawn, combined with ApplyBlending as WE's composite pass does. */
    static String blendFragment(String blending){return "#version 300 es\nprecision mediump float;uniform sampler2D image;uniform sampler2D backdrop;uniform vec2 canvas;uniform int mode;uniform mat3 colors;uniform vec3 colorBias;uniform float opacity;uniform float straight;uniform vec3 tint;in mediump vec2 uv;out vec4 fragColor;\n"+blending+"\nvoid main(){vec4 col=texture(image,uv);vec3 rgb=(straight>0.5?col.rgb:(col.a>0.0001?col.rgb/col.a:vec3(0.0)))*tint;rgb=clamp(colors*rgb+colorBias,0.0,1.0);float a=col.a*opacity;if(a<0.004)discard;vec3 dst=texture(backdrop,gl_FragCoord.xy/canvas).rgb;vec3 r=ApplyBlending(mode,dst,rgb,a);if(mode==5||mode==10)r=mix(dst,r,a);fragColor=vec4(r,1.0);}";}
    private Locations main,blend;private boolean blendTried;private int backdrop,backdropWidth,backdropHeight,revision=-1;
    private final int maxTexture;
    private float[] rgb;
    SceneGpu(int maxTexture){this(maxTexture,false);}
    /** Author effects need a GLES 3 context; on GLES 2 layers draw without them. */
    SceneGpu(int maxTexture,boolean gles3){this.maxTexture=maxTexture;this.effectsSupported=gles3;quad.put(new float[]{0,0,1,0,0,1,1,1}).position(0);}
    private static int shader(int kind,String source){int shader=GLES20.glCreateShader(kind);GLES20.glShaderSource(shader,source);GLES20.glCompileShader(shader);int[] ok=new int[1];GLES20.glGetShaderiv(shader,GLES20.GL_COMPILE_STATUS,ok,0);if(ok[0]==0){String message=GLES20.glGetShaderInfoLog(shader);GLES20.glDeleteShader(shader);throw new IllegalStateException("场景着色器编译失败："+message);}return shader;}
    static int link(String vertexSource,String fragmentSource){
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
    /** Playing layer videos, created when their layer is first shown; the copy program, -1 where it cannot be built. */
    private final IdentityHashMap<WallpaperContent.Layer,SceneVideo> videos=new IdentityHashMap<>();private int videoProgram;private boolean videosPlaying=true;
    private int videoProgram(){
        if(videoProgram==0)try{videoProgram=link(SceneVideo.VERTEX,SceneVideo.FRAGMENT);}catch(RuntimeException unsupported){videoProgram=-1;android.util.Log.w("SceneGpu","layer videos unavailable",unsupported);}
        return Math.max(0,videoProgram);
    }
    /** Layer videos decode only while the wallpaper is shown and not frozen for power saving. */
    void playing(boolean on){videosPlaying=on;for(SceneVideo v:videos.values())v.playing(on);}
    /** What a layer samples: its video's latest frame once one has been decoded, else its bitmap; {texture, width, height}. */
    private int[] source(WallpaperContent.Layer layer){
        SceneVideo v=layer.video==null?null:videos.get(layer);
        if(v!=null&&v.texture()!=0)return new int[]{v.texture(),v.width(),v.height()};
        int t=texture(layer.bitmap);int[] size=sizes.get(layer.bitmap);return new int[]{t,size[0],size[1]};
    }
    static SceneGeometry.Pose pose(WallpaperContent.Scene scene,WallpaperContent.Layer layer,int index,JSONObject options,CanvasCamera.Frame camera,EffectMotion.Frame motion){
        float depth=(float)options.optDouble("depth"+index,layer.depth)*(float)options.optDouble("layerDepth",1);
        float dx=(float)options.optDouble("layerX"+index,0)*scene.width+motion.x()*scene.width*.5f*depth,dy=(float)options.optDouble("layerY"+index,0)*scene.height+motion.y()*scene.height*.5f*depth;
        return SceneGeometry.pose(camera,scene.width,scene.height,layer.x+dx,layer.y+dy,layer.w,layer.h,layer.rotation,layer.shape);
    }
    void draw(WallpaperContent.Scene scene,WallpaperEffects effects,CanvasCamera.Frame camera,EffectMotion.Frame motion,int width,int height){draw(scene,effects,camera,motion,width,height,0);}
    /** Animated layers only change the sampled region; their atlas texture is uploaded once. */
    void draw(WallpaperContent.Scene scene,WallpaperEffects effects,CanvasCamera.Frame camera,EffectMotion.Frame motion,int width,int height,double seconds){
        initialize();scene.applyUser(effects.options);scene.animate(seconds);
        retired.clear();scene.refreshTexts(retired);
        for(Bitmap old:retired){Integer t=textures.remove(old);if(t!=null)GLES20.glDeleteTextures(1,new int[]{t},0);sizes.remove(old);old.recycle();}
        // Layer videos: newly decoded frames are copied into their textures before anything samples them.
        for(WallpaperContent.Layer l:scene.layers){
            if(l.video==null)continue;boolean shown=l.shown(effects.options,scene.properties);SceneVideo v=videos.get(l);
            if(v==null){if(!shown||videoProgram()==0)continue;v=new SceneVideo(l,videosPlaying);videos.put(l,v);}
            v.playing(videosPlaying&&shown);v.update(videoProgram,maxTexture);
        }

        // Effect chains render into their own framebuffers first, so the composition below keeps one GL state.
        int[] processed=new int[scene.layers.size()];
        if(effectsSupported){if(effectRunner==null)effectRunner=new SceneEffectRunner();effectRunner.touch(effects.touchX(),effects.touchY(),effects.pointerDown());}
        // Heavy scenes keep every layer and effect but refresh layer effects on alternating frames, reusing the last result in between.
        int steps=0;for(WallpaperContent.Layer l:scene.layers)steps+=l.effects.size();boolean alternate=steps>HEAVY_EFFECT_STEPS;frameCount++;
        if(effectsSupported)for(int i=0;i<processed.length;i++){
            WallpaperContent.Layer layer=scene.layers.get(i);if(layer.compose||!(layer.shown(effects.options,scene.properties)||layer.source(effects.options,scene.properties))||layer.effects.isEmpty()||!SceneEffectRunner.active(layer)||SceneEffectRunner.readsFrame(layer)||SceneEffectRunner.readsComposite(layer))continue;
            if(effectRunner==null)effectRunner=new SceneEffectRunner();int[] src=source(layer);int source=src[0];int[] size={src[1],src[2]};
            Integer previous=lastEffect.get(layer);if(alternate&&previous!=null&&((frameCount+i)&1)==1){processed[i]=previous;continue;}
            effectRunner.place(pose(scene,layer,i,effects.options,camera,motion));
            try{processed[i]=effectRunner.apply(layer,source,size[0],size[1],layer.region(seconds),seconds,effects.pointerX(),effects.pointerY());}finally{effectRunner.place(null);}if(processed[i]!=0)lastEffect.put(layer,processed[i]);else lastEffect.remove(layer);
        }
        GLES20.glViewport(0,0,width,height);GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        bindQuad();GLES20.glEnableVertexAttribArray(POINT);GLES20.glEnableVertexAttribArray(TEXCOORD);
        JSONObject o=effects.options;
        if(revision!=effects.optionsRevision){rgb=dev.workshopvideo.core.PictureColors.matrix((float)o.optDouble("brightness",1),(float)o.optDouble("contrast",1),(float)o.optDouble("saturation",1),(float)o.optDouble("hue",0));revision=effects.optionsRevision;}
        float[] matrix={rgb[0],rgb[4],rgb[8],rgb[1],rgb[5],rgb[9],rgb[2],rgb[6],rgb[10]};use(main,matrix);
        GLES20.glEnable(GLES20.GL_BLEND);GLES20.glBlendFunc(GLES20.GL_ONE,GLES20.GL_ONE_MINUS_SRC_ALPHA);
        dScene=scene;dOptions=o;dCamera=camera;dMotion=motion;dWidth=width;dHeight=height;dProcessed=processed;dMatrix=matrix;dSeconds=seconds;dEffects=effects;dScreenW=width;dScreenH=height;view=null;sourcing=0;
        try{for(int i=0;i<scene.layers.size();i++){if(scene.layers.get(i).group<0)drawSafely(i,0);
        }
            if(scene.bloom&&scene.bloomStrength>0)try{if(bloom==null)bloom=new SceneBloom();bloom.apply(0,width,height,scene.bloomStrength,scene.bloomThreshold,scene.bloomTint);}
            catch(RuntimeException failure){if(!bloomFailed){bloomFailed=true;android.util.Log.w("SceneGpu","bloom unavailable",failure);}scene.bloom=false;}
        }finally{GLES20.glDisable(GLES20.GL_BLEND);GLES20.glDisableVertexAttribArray(POINT);GLES20.glDisableVertexAttribArray(TEXCOORD);}
    }
    /** State of the frame being drawn, shared by drawAt and drawGroup. */
    /** Screen size of the frame; the map from screen clip space into the bounded group being filled, null on screen. */
    private int dScreenW,dScreenH;private float[] view;
    /** Depth of composition layers being rendered only as another layer's effect texture; hidden source layers draw only then. */
    private int sourcing;
    /** Frame each composition layer was last rendered in, and its result (renderGroup). */
    private final IdentityHashMap<WallpaperContent.Layer,Long> rendered=new IdentityHashMap<>();private final IdentityHashMap<WallpaperContent.Layer,int[]> groupResults=new IdentityHashMap<>();
    /** Object id to layer index for the scene being drawn. */
    private final java.util.HashMap<Integer,Integer> byObject=new java.util.HashMap<>();private WallpaperContent.Scene byObjectScene;private int byObjectCount=-1;
    private WallpaperContent.Scene dScene;private JSONObject dOptions;private CanvasCamera.Frame dCamera;private EffectMotion.Frame dMotion;private int dWidth,dHeight;private int[] dProcessed;private float[] dMatrix;private double dSeconds;private WallpaperEffects dEffects;
    /** Composition layers: children are drawn into this buffer {framebuffer, texture, width, height}, then the group's effects run on it. */
    private final IdentityHashMap<WallpaperContent.Layer,int[]> groups=new IdentityHashMap<>();
    private SceneBloom bloom;private boolean bloomFailed;
    /** Layers whose drawing failed, reported once; the rest of the frame is still drawn. */
    private final java.util.Set<WallpaperContent.Layer> failedLayers=java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    /** One layer failing (a GL-side bug in that layer) must not stop every layer above it from being drawn. */
    private void drawSafely(int i,int target){
        try{drawAt(i,target);}
        catch(RuntimeException failure){
            WallpaperContent.Layer l=dScene.layers.get(i);if(failedLayers.add(l))android.util.Log.w("SceneGpu","layer "+i+" ("+l.name+") not drawn",failure);
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,target);GLES20.glViewport(0,0,dWidth,dHeight);GLES20.glActiveTexture(GLES20.GL_TEXTURE0);bindQuad();GLES20.glEnableVertexAttribArray(POINT);GLES20.glEnableVertexAttribArray(TEXCOORD);use(main,dMatrix);GLES20.glEnable(GLES20.GL_BLEND);GLES20.glBlendFunc(GLES20.GL_ONE,GLES20.GL_ONE_MINUS_SRC_ALPHA);
        }
    }
    /** Draws layer i into the framebuffer target. */
    private void drawAt(int i,int target){
        WallpaperContent.Scene scene=dScene;JSONObject o=dOptions;CanvasCamera.Frame camera=dCamera;EffectMotion.Frame motion=dMotion;int width=dWidth,height=dHeight;int[] processed=dProcessed;float[] matrix=dMatrix;double seconds=dSeconds;WallpaperEffects effects=dEffects;
            WallpaperContent.Layer layer=scene.layers.get(i);if(!(layer.shown(o,scene.properties)||sourcing>0&&layer.source(o,scene.properties))||layer.opacity(o)<=0)return;
            if(layer.compose){drawGroup(i,target);return;}
            boolean frameFx=SceneEffectRunner.readsFrame(layer),compositeFx=SceneEffectRunner.readsComposite(layer);
            if(effectsSupported&&!layer.effects.isEmpty()&&SceneEffectRunner.active(layer)&&(frameFx||compositeFx)){
                // Effects that sample the picture below the layer, or other layers' results, run now, once those exist.
                if(effectRunner==null)effectRunner=new SceneEffectRunner();int[] src=source(layer);int source=src[0];int[] size={src[1],src[2]};
                if(compositeFx)composites(layer,target);
                effectRunner.place(placed(i));
                try{java.util.function.IntSupplier run=()->effectRunner.apply(layer,source,size[0],size[1],layer.region(seconds),seconds,effects.pointerX(),effects.pointerY());
                    if(frameFx)processed[i]=withFrame(target,run);else{processed[i]=run.getAsInt();restore(target);}}
                finally{effectRunner.place(null);}
            }
            if(layer.particles!=null){drawParticles(layer,placed(i),EffectMotion.clamp(layer.opacity(o)*(float)o.optDouble("opacity"+i,1),0,1),scene);use(main,matrix);return;}
            Locations p=layer.blend!=0?blendProgram():null;if(p!=null){copyBackdrop(width,height);use(p,matrix);GLES20.glUniform1i(p.backdrop,1);GLES20.glUniform2f(p.canvas,width,height);GLES20.glUniform1i(p.mode,layer.blend);GLES20.glDisable(GLES20.GL_BLEND);}else p=main;
            if(layer.additive&&p==main)GLES20.glBlendFunc(GLES20.GL_ONE,GLES20.GL_ONE);
            SceneGeometry.Pose pose=placed(i);
            GLES20.glUniform2f(p.origin,pose.x(),pose.y());GLES20.glUniform2f(p.axisX,pose.xx(),pose.xy());GLES20.glUniform2f(p.axisY,pose.yx(),pose.yy());GLES20.glUniform1f(p.opacity,EffectMotion.clamp(layer.opacity(o)*(float)o.optDouble("opacity"+i,1),0,1));
            var frame=processed[i]!=0?WallpaperContent.FULL:layer.region(seconds);GLES20.glUniform4f(p.region,frame.u0(),frame.v0(),frame.u1(),frame.v1());GLES20.glUniform1f(p.straight,processed[i]!=0?1:0);GLES20.glUniform3f(p.tint,layer.tint[0],layer.tint[1],layer.tint[2]);GLES20.glUniform3f(p.reveal,0,0,-1);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,processed[i]!=0?processed[i]:source(layer)[0]);
            drawGeometry(layer);
            if(p!=main){GLES20.glEnable(GLES20.GL_BLEND);use(main,matrix);}
            if(layer.additive)GLES20.glBlendFunc(GLES20.GL_ONE,GLES20.GL_ONE_MINUS_SRC_ALPHA);
            if(layer.reveal!=null&&o.optBoolean("xray",true)&&effects.pointerDown()){
                float radius=layer.w*(float)o.optDouble("xraySize",layer.revealSize)*.5f*camera.scaleX()*width/scene.width;
                GLES20.glUniform2f(main.origin,pose.x(),pose.y());GLES20.glUniform2f(main.axisX,pose.xx(),pose.xy());GLES20.glUniform2f(main.axisY,pose.yx(),pose.yy());
                GLES20.glUniform4f(main.region,0,0,1,1);GLES20.glUniform1f(main.straight,0);GLES20.glUniform3f(main.reveal,effects.touchX()*width,(1-effects.touchY())*height,Math.max(1,radius));GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,texture(layer.reveal));GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);
            }
    }
    /** Runs effects with the picture drawn so far into target available as _rt_FullFrameBuffer, then restores the drawing state. */
    private int withFrame(int target,java.util.function.IntSupplier effects){
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,target);GLES20.glViewport(0,0,dWidth,dHeight);copyBackdrop(dWidth,dHeight);
        effectRunner.frame=backdrop;effectRunner.frameWidth=dWidth;effectRunner.frameHeight=dHeight;
        try{return effects.getAsInt();}
        finally{effectRunner.frame=0;GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,target);GLES20.glViewport(0,0,dWidth,dHeight);GLES20.glActiveTexture(GLES20.GL_TEXTURE0);bindQuad();GLES20.glEnableVertexAttribArray(POINT);GLES20.glEnableVertexAttribArray(TEXCOORD);use(main,dMatrix);GLES20.glEnable(GLES20.GL_BLEND);GLES20.glBlendFunc(GLES20.GL_ONE,GLES20.GL_ONE_MINUS_SRC_ALPHA);}
    }
    /** Layer i placed in the target being drawn: its screen pose, seen through the bounded group being filled. */
    private SceneGeometry.Pose placed(int i){return SceneGeometry.map(view,pose(dScene,dScene.layers.get(i),i,dOptions,dCamera,dMotion));}
    /** Back to drawing layers into target after other framebuffers or programs were used. */
    private void restore(int target){GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,target);GLES20.glViewport(0,0,dWidth,dHeight);GLES20.glActiveTexture(GLES20.GL_TEXTURE0);bindQuad();GLES20.glEnableVertexAttribArray(POINT);GLES20.glEnableVertexAttribArray(TEXCOORD);use(main,dMatrix);GLES20.glEnable(GLES20.GL_BLEND);GLES20.glBlendFunc(GLES20.GL_ONE,GLES20.GL_ONE_MINUS_SRC_ALPHA);}
    private Integer objectIndex(int id){
        if(byObjectScene!=dScene||byObjectCount!=dScene.layers.size()){byObject.clear();for(int k=0;k<dScene.layers.size();k++)byObject.putIfAbsent(dScene.layers.get(k).objectId,k);byObjectScene=dScene;byObjectCount=dScene.layers.size();}
        return byObject.get(id);
    }
    /**
     * Gives the effect runner the results of the layers this layer's effects sample (_rt_imageLayerComposite_<id>_a): an
     * image layer's processed or original texture, a composition layer's buffer. A composition layer not rendered yet this
     * frame (later in the drawing order, or hidden and kept only as this texture) is rendered first.
     */
    private void composites(WallpaperContent.Layer layer,int target){
        boolean drew=false;
        for(SceneEffects.Effect e:layer.effects)for(String name:e.bind.values()){
            int id=SceneEffects.compositeId(name);if(id<0)continue;Integer k=objectIndex(id);
            if(k==null){effectRunner.composites.remove(name);continue;}
            WallpaperContent.Layer src=dScene.layers.get(k);
            if(src.compose){
                Long at=rendered.get(src);
                if(at==null||at!=frameCount){sourcing++;try{renderGroup(k,target);}catch(RuntimeException failure){if(failedLayers.add(src))android.util.Log.w("SceneGpu","layer "+k+" ("+src.name+") not rendered as a texture",failure);}finally{sourcing--;}drew=true;}
                int[] g=groupResults.get(src);if(g!=null)effectRunner.composites.put(name,new int[]{g[2],g[3],g[0]});else effectRunner.composites.remove(name);
            }else{
                int[] size=dProcessed[k]!=0?effectRunner.resultSize(src):null;
                if(size!=null)effectRunner.composites.put(name,new int[]{size[0],size[1],dProcessed[k]});
                else{int[] s=source(src);effectRunner.composites.put(name,new int[]{s[1],s[2],s[0]});}
            }
        }
        if(drew)restore(target);
    }
    /** Size of a bounded group's buffer: its rectangle at screen resolution, within the texture limit and twice the screen's pixels. */
    private int[] groupSize(SceneGeometry.Pose p){
        int edge=Math.min(maxTexture,4096);double w=Math.hypot(p.xx()*dScreenW/2.0,p.xy()*dScreenH/2.0),h=Math.hypot(p.yx()*dScreenW/2.0,p.yy()*dScreenH/2.0);
        double k=Math.min(1,Math.min(edge/Math.max(1,Math.max(w,h)),Math.sqrt(2.0*dScreenW*dScreenH/Math.max(1,w*h))));
        return new int[]{(int)Math.max(1,Math.min(edge,Math.round(w*k))),(int)Math.max(1,Math.min(edge,Math.round(h*k)))};
    }
    /**
     * Renders composition layer i, its children and then its own effects, into its buffer and records the result
     * {texture, straight 0/1, width, height, bounded 0/1, target width, target height}. target is where it will be drawn:
     * fullscreen layers start from its picture and effects reading the frame see it. A bounded layer renders inside its
     * rectangle (children outside it are cut off, as in Wallpaper Engine); others cover the whole target.
     */
    private int[] renderGroup(int i,int target){
        WallpaperContent.Scene scene=dScene;WallpaperContent.Layer g=scene.layers.get(i);rendered.put(g,frameCount);
        int outerW=dWidth,outerH=dHeight;float[] outerView=view;
        SceneGeometry.Pose screen=pose(scene,g,i,dOptions,dCamera,dMotion);float[] inner=g.bounded?SceneGeometry.onto(screen):null;
        int bw=outerW,bh=outerH;if(inner!=null){int[] size=groupSize(screen);bw=size[0];bh=size[1];}
        int[] b=groups.get(g);
        // A bounded buffer keeps its size while it stays close, so paging and parallax do not reallocate it every frame.
        boolean fits=b!=null&&(inner==null?b[4]==0&&b[2]==bw&&b[3]==bh:b[4]==1&&b[2]>=bw*.8f&&b[2]<=bw*1.25f&&b[3]>=bh*.8f&&b[3]<=bh*1.25f);
        if(!fits){
            if(b!=null){GLES20.glDeleteFramebuffers(1,new int[]{b[0]},0);GLES20.glDeleteTextures(1,new int[]{b[1]},0);}
            int[] f=new int[1],t=new int[1];GLES20.glGenFramebuffers(1,f,0);GLES20.glGenTextures(1,t,0);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,t[0]);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D,0,GLES20.GL_RGBA,bw,bh,0,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,null);
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,f[0]);GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER,GLES20.GL_COLOR_ATTACHMENT0,GLES20.GL_TEXTURE_2D,t[0],0);
            b=new int[]{f[0],t[0],bw,bh,inner!=null?1:0};groups.put(g,b);
        }
        bw=b[2];bh=b[3];
        if(g.fullscreen){
            // A fullscreen layer starts from the picture drawn so far (Wallpaper Engine's _rt_FullFrameBuffer).
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,target);GLES20.glActiveTexture(GLES20.GL_TEXTURE0);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,b[1]);
            GLES20.glCopyTexSubImage2D(GLES20.GL_TEXTURE_2D,0,0,0,0,0,bw,bh);GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,b[0]);GLES20.glViewport(0,0,bw,bh);
        }else{GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,b[0]);GLES20.glViewport(0,0,bw,bh);GLES20.glClearColor(0,0,0,0);GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);}
        if(inner!=null){view=inner;dWidth=bw;dHeight=bh;}
        try{for(int j=i+1;j<scene.layers.size();j++)if(scene.layers.get(j).group==i){drawSafely(j,b[0]);GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,b[0]);}}
        finally{view=outerView;dWidth=outerW;dHeight=outerH;}
        int result=b[1],rw=bw,rh=bh;boolean straight=false;
        if(effectsSupported&&!g.effects.isEmpty()&&SceneEffectRunner.active(g)){
            if(effectRunner==null)effectRunner=new SceneEffectRunner();
            if(SceneEffectRunner.readsComposite(g))composites(g,target);
            int source=b[1],sw=bw,sh=bh;
            // A bounded buffer is the layer's own space, as an image's; a whole-target buffer is the target itself.
            effectRunner.place(inner!=null?SceneGeometry.map(outerView,screen):null);
            try{java.util.function.IntSupplier run=()->effectRunner.apply(g,source,sw,sh,WallpaperContent.FULL,dSeconds,dEffects.pointerX(),dEffects.pointerY());
                int r=SceneEffectRunner.readsFrame(g)?withFrame(target,run):run.getAsInt();
                if(r!=0){result=r;straight=true;int[] size=effectRunner.resultSize(g);if(size!=null){rw=size[0];rh=size[1];}}}
            finally{effectRunner.place(null);}
        }
        int[] out={result,straight?1:0,rw,rh,inner!=null?1:0,outerW,outerH};groupResults.put(g,out);return out;
    }
    /** A composition layer, as Wallpaper Engine renders it: its children together, then its own effects on the result. */
    private void drawGroup(int i,int target){
        WallpaperContent.Layer g=dScene.layers.get(i);Long at=rendered.get(g);int[] r=at!=null&&at==frameCount?groupResults.get(g):null;
        // Rendered earlier this frame as another layer's texture: reused, unless it covers a target of another size.
        if(r==null||r[4]==0&&(r[5]!=dWidth||r[6]!=dHeight))r=renderGroup(i,target);
        // The effect runner disables the vertex arrays it used (the same indices as POINT/TEXCOORD); without them this
        // composite and every later layer would draw nothing.
        restore(target);
        float alpha=EffectMotion.clamp(g.opacity(dOptions)*(float)dOptions.optDouble("opacity"+i,1),0,1);
        if(r[4]==1){
            // A bounded buffer holds the layer's rectangle, top edge first, like an image.
            SceneGeometry.Pose pose=placed(i);GLES20.glUniform2f(main.origin,pose.x(),pose.y());GLES20.glUniform2f(main.axisX,pose.xx(),pose.xy());GLES20.glUniform2f(main.axisY,pose.yx(),pose.yy());GLES20.glUniform4f(main.region,0,0,1,1);
        }else{
            GLES20.glUniform2f(main.origin,-1,1);GLES20.glUniform2f(main.axisX,2,0);GLES20.glUniform2f(main.axisY,0,-2);
            // Framebuffer rows run bottom-up, so the full-screen quad samples them flipped.
            GLES20.glUniform4f(main.region,0,1,1,0);
        }
        // The fullscreen result already contains what is below it, so it replaces the picture, mixed by the layer's opacity.
        if(g.fullscreen){GLES20.glBlendFunc(GLES20.GL_CONSTANT_ALPHA,GLES20.GL_ONE_MINUS_CONSTANT_ALPHA);GLES20.glBlendColor(0,0,0,alpha);alpha=1;}
        GLES20.glUniform1f(main.opacity,alpha);GLES20.glUniform1f(main.straight,r[1]);GLES20.glUniform3f(main.tint,1,1,1);GLES20.glUniform3f(main.reveal,0,0,-1);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,r[0]);GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);
        if(g.fullscreen)GLES20.glBlendFunc(GLES20.GL_ONE,GLES20.GL_ONE_MINUS_SRC_ALPHA);
    }
    /** All live particles of a layer in one indexed draw. */
    private void drawParticles(WallpaperContent.Layer layer,SceneGeometry.Pose pose,float alpha,WallpaperContent.Scene scene){
        if(particle==null)particle=new Locations(link(PARTICLE_VERTEX,PARTICLE_FRAGMENT));
        // Author settings can rebuild a system with more particles (WallpaperContent.rebuildParticles): grow the buffers with it.
        ParticleBuffers b=particleBuffers.get(layer);if(b==null||b.capacity<layer.particles.capacity()){b=new ParticleBuffers(layer.particles.capacity());particleBuffers.put(layer,b);}
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
    @Override public void close(){for(SceneVideo v:videos.values())v.close();videos.clear();if(videoProgram>0)GLES20.glDeleteProgram(videoProgram);videoProgram=0;if(bloom!=null){bloom.close();bloom=null;}if(effectRunner!=null){effectRunner.close();effectRunner=null;}sizes.clear();meshes.clear();lastEffect.clear();for(int[] b:groups.values()){GLES20.glDeleteFramebuffers(1,new int[]{b[0]},0);GLES20.glDeleteTextures(1,new int[]{b[1]},0);}groups.clear();rendered.clear();groupResults.clear();byObject.clear();byObjectScene=null;byObjectCount=-1;view=null;sourcing=0;particleBuffers.clear();if(particle!=null)GLES20.glDeleteProgram(particle.program);particle=null;for(int texture:textures.values())GLES20.glDeleteTextures(1,new int[]{texture},0);textures.clear();if(main!=null)GLES20.glDeleteProgram(main.program);if(blend!=null)GLES20.glDeleteProgram(blend.program);main=null;blend=null;blendTried=false;if(backdrop!=0)GLES20.glDeleteTextures(1,new int[]{backdrop},0);backdrop=0;backdropWidth=backdropHeight=0;}
}
