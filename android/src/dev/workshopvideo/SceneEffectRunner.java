package dev.workshopvideo;

import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.util.Log;
import dev.workshopvideo.core.TexturePixels;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.*;

/** Runs a layer's author effects as ping-pong passes on a GLES 3 context.
 *  The result keeps straight (non-premultiplied) alpha, matching Wallpaper Engine shaders. */
final class SceneEffectRunner implements AutoCloseable {
    private static final String TAG="SceneEffects";
    static final int MAX_EDGE=1440;
    private static final String COPY_VERTEX="attribute vec2 point;uniform vec4 region;varying mediump vec2 uv;void main(){uv=mix(region.xy,region.zw,point);gl_Position=vec4(point*2.0-1.0,0.0,1.0);}";
    private static final String COPY_FRAGMENT="precision mediump float;uniform sampler2D image;varying mediump vec2 uv;void main(){vec4 c=texture2D(image,uv);gl_FragColor=vec4(c.a>0.0001?c.rgb/c.a:vec3(0.0),c.a);}";
    /** Straight copy between effect buffers (both already hold straight alpha), for multi-pass "copy" steps. */
    private static final String PLAIN_FRAGMENT="precision mediump float;uniform sampler2D image;varying mediump vec2 uv;void main(){gl_FragColor=texture2D(image,uv);}";
    private static final float[] IDENTITY={1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1};

    private static final class Chain {final int[] frames=new int[2],textures=new int[2];int width,height;}
    private static final class Uniform {final int location,type;Uniform(int location,int type){this.location=location;this.type=type;}}
    private static final class Program {final int id,position,texCoord;final Map<String,Uniform> uniforms=new HashMap<>();Program(int id,int position,int texCoord){this.id=id;this.position=position;this.texCoord=texCoord;}}

    private final FloatBuffer unit=buffer(0,0,1,0,0,1,1,1),positions=buffer(-1,-1,0,1,-1,0,-1,1,0,1,1,0);
    private final IdentityHashMap<WallpaperContent.Layer,Chain> chains=new IdentityHashMap<>();
    private final IdentityHashMap<SceneEffects.Effect,Program> programs=new IdentityHashMap<>();
    private final IdentityHashMap<Bitmap,int[]> textures=new IdentityHashMap<>();
    private final Map<String,int[]> engine=new HashMap<>();
    /** Named buffers of multi-pass effects per layer: "name@scale" -> {frame, texture, width, height}. */
    private final IdentityHashMap<WallpaperContent.Layer,Map<String,int[]>> buffers=new IdentityHashMap<>();
    private int copy,copyPoint,copyRegion,copyImage,plain,plainPoint,plainRegion,plainImage;

    /** True when at least one of the layer's effects can still run. */
    static boolean active(WallpaperContent.Layer layer){for(SceneEffects.Effect e:layer.effects)if(!e.failed&&!e.off)return true;return false;}

    /** Processes the layer and returns the texture holding the result, or 0 when nothing ran. Leaves FBO 0 bound. */
    int apply(WallpaperContent.Layer layer,int source,int sourceWidth,int sourceHeight,TexturePixels.Frame region,double seconds,float pointerX,float pointerY){
        int[] previousViewport=new int[4];GLES20.glGetIntegerv(GLES20.GL_VIEWPORT,previousViewport,0);
        try{
            float scale=Math.min(1,MAX_EDGE/(float)Math.max(sourceWidth*(region.u1()-region.u0()),sourceHeight*(region.v1()-region.v0())));
            int w=Math.max(1,Math.round(sourceWidth*(region.u1()-region.u0())*scale)),h=Math.max(1,Math.round(sourceHeight*(region.v1()-region.v0())*scale));
            Chain chain=chain(layer,w,h);GLES20.glDisable(GLES20.GL_BLEND);GLES20.glViewport(0,0,w,h);
            copy(chain.frames[0],source,region);
            int current=0;boolean ran=false;List<SceneEffects.Effect> all=layer.effects;
            // Each effect is a group of passes; only the last writes the layer, earlier ones fill the effect's own buffers.
            for(int i=0;i<all.size();){
                int group=all.get(i).group,j=i;while(j<all.size()&&all.get(j).group==group)j++;
                List<SceneEffects.Effect> steps=all.subList(i,j);i=j;if(steps.get(0).off)continue;
                boolean ready=true;for(SceneEffects.Effect e:steps)if(e.failed||!e.copy&&program(e)==null){ready=false;break;}
                if(!ready){for(SceneEffects.Effect e:steps)e.failed=true;continue;}
                int input=chain.textures[current];Map<String,int[]> named=buffers(layer,steps.get(0).fbos,w,h);boolean done=true;
                for(SceneEffects.Effect e:steps){
                    int[] out=e.target==null?new int[]{chain.frames[1-current],chain.textures[1-current],w,h}:named.get(e.target);
                    if(out==null){done=false;break;}
                    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,out[0]);GLES20.glViewport(0,0,out[2],out[3]);
                    if(e.copy){int[] from=source(e,0,input,w,h,named);if(from==null){done=false;break;}plainCopy(from[2]);continue;}
                    if(!run(e,programs.get(e),input,w,h,named,out[2],out[3],seconds,pointerX,pointerY)){done=false;break;}
                }
                GLES20.glViewport(0,0,w,h);
                if(!done){for(SceneEffects.Effect e:steps)e.failed=true;continue;}
                current=1-current;ran=true;
            }
            return ran?chain.textures[current]:0;
        }finally{GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,0);GLES20.glViewport(previousViewport[0],previousViewport[1],previousViewport[2],previousViewport[3]);GLES20.glEnable(GLES20.GL_BLEND);}
    }

    static final String FULL_FRAME=SceneEffects.FULL_FRAME;
    /** Texture holding the picture drawn so far while a layer that reads it is processed, else 0. */
    int frame,frameWidth,frameHeight;
    /** Whether any effect of the layer samples the picture drawn so far; such layers are processed while drawing. */
    static boolean readsFrame(WallpaperContent.Layer layer){for(SceneEffects.Effect e:layer.effects)if(e.bind.containsValue(FULL_FRAME))return true;return false;}
    /** Texture for a slot of a pass: {width, height, texture}; bound buffers first, slot 0 defaults to the effect's input. */
    private int[] source(SceneEffects.Effect effect,int slot,int input,int w,int h,Map<String,int[]> named){
        String bound=effect.bind.get(slot);
        if(bound!=null){if(bound.equals("previous"))return new int[]{w,h,input};if(bound.equals(FULL_FRAME))return frame==0?engine("util/black"):new int[]{frameWidth,frameHeight,frame};int[] b=named.get(bound);return b==null?null:new int[]{b[2],b[3],b[1]};}
        if(slot==0)return new int[]{w,h,input};
        Object value=slot>0&&slot<effect.textures.size()?effect.textures.get(slot):null;return value instanceof Bitmap b?upload(b):engine(value instanceof String s?s:"util/black");
    }
    private boolean run(SceneEffects.Effect effect,Program program,int input,int w,int h,Map<String,int[]> named,int outW,int outH,double seconds,float pointerX,float pointerY){
        GLES20.glUseProgram(program.id);int unitIndex=0;
        for(Map.Entry<String,Uniform> entry:program.uniforms.entrySet()){
            String name=entry.getKey();Uniform u=entry.getValue();
            if(u.type==GLES20.GL_SAMPLER_2D){
                int slot=slot(name);int[] size=source(effect,slot,input,w,h,named);if(size==null)size=engine("util/black");int texture=size[2];
                GLES20.glActiveTexture(GLES20.GL_TEXTURE0+unitIndex);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,texture);GLES20.glUniform1i(u.location,unitIndex);unitIndex++;
                Uniform resolution=program.uniforms.get("g_Texture"+slot+"Resolution");if(resolution!=null)GLES20.glUniform4f(resolution.location,size[0],size[1],size[0],size[1]);
                continue;
            }
            float[] value=effect.uniforms.get(name);
            if(value==null)value=engineValue(name,seconds,pointerX,pointerY,outW,outH);
            if(value!=null)set(u,value);
        }
        GLES20.glVertexAttribPointer(program.position,3,GLES20.GL_FLOAT,false,12,positions);GLES20.glEnableVertexAttribArray(program.position);
        if(program.texCoord>=0){GLES20.glVertexAttribPointer(program.texCoord,2,GLES20.GL_FLOAT,false,8,unit);GLES20.glEnableVertexAttribArray(program.texCoord);}
        GLES20.glClearColor(0,0,0,0);GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);
        GLES20.glDisableVertexAttribArray(program.position);if(program.texCoord>=0)GLES20.glDisableVertexAttribArray(program.texCoord);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        int error=GLES20.glGetError();if(error!=GLES20.GL_NO_ERROR){Log.w(TAG,effect.name+" draw error 0x"+Integer.toHexString(error));effect.failed=true;return false;}
        return true;
    }

    private static float[] engineValue(String name,double seconds,float pointerX,float pointerY,int w,int h){
        return switch(name){
            case "g_Time"->new float[]{(float)(seconds%3600)};
            case "g_ModelViewProjectionMatrix","g_EffectTextureProjectionMatrix","g_EffectTextureProjectionMatrixInverse","g_ModelMatrix","g_ViewProjectionMatrix"->IDENTITY;
            case "g_PointerPosition","g_PointerPositionLast"->new float[]{pointerX,pointerY};
            case "g_ParallaxPosition"->new float[]{.5f,.5f};
            case "g_Screen"->new float[]{w,h,w/(float)Math.max(1,h)};
            case "g_Daytime"->{java.util.Calendar c=java.util.Calendar.getInstance();yield new float[]{(c.get(java.util.Calendar.HOUR_OF_DAY)*3600+c.get(java.util.Calendar.MINUTE)*60+c.get(java.util.Calendar.SECOND))/86400f};}
            case "g_Alpha","g_Brightness"->new float[]{1};
            case "g_Color","g_Color4"->new float[]{1,1,1,1};
            default->null;
        };
    }
    private static int slot(String name){if(!name.startsWith("g_Texture"))return -1;try{return Integer.parseInt(name.substring(9));}catch(NumberFormatException e){return -1;}}
    private static void set(Uniform u,float[] v){
        switch(u.type){
            case GLES20.GL_FLOAT->GLES20.glUniform1f(u.location,v[0]);
            case GLES20.GL_FLOAT_VEC2->GLES20.glUniform2f(u.location,v[0],v.length>1?v[1]:v[0]);
            case GLES20.GL_FLOAT_VEC3->GLES20.glUniform3f(u.location,v[0],v.length>1?v[1]:v[0],v.length>2?v[2]:v[0]);
            case GLES20.GL_FLOAT_VEC4->GLES20.glUniform4f(u.location,v[0],v.length>1?v[1]:v[0],v.length>2?v[2]:v[0],v.length>3?v[3]:(v.length==1?v[0]:1));
            case GLES20.GL_INT,GLES20.GL_BOOL->GLES20.glUniform1i(u.location,Math.round(v[0]));
            case GLES20.GL_FLOAT_MAT4->{if(v.length==16)GLES20.glUniformMatrix4fv(u.location,1,false,v,0);}
            default->{}
        }
    }

    private Program program(SceneEffects.Effect effect){
        Program existing=programs.get(effect);if(existing!=null)return existing;
        int vertex=0,fragment=0,id=0;
        try{
            vertex=shader(GLES20.GL_VERTEX_SHADER,effect.vertex);fragment=shader(GLES20.GL_FRAGMENT_SHADER,effect.fragment);
            id=GLES20.glCreateProgram();GLES20.glAttachShader(id,vertex);GLES20.glAttachShader(id,fragment);GLES20.glLinkProgram(id);
            int[] ok=new int[1];GLES20.glGetProgramiv(id,GLES20.GL_LINK_STATUS,ok,0);if(ok[0]==0)throw new IllegalStateException("link: "+GLES20.glGetProgramInfoLog(id));
            int position=GLES20.glGetAttribLocation(id,"a_Position");if(position<0)throw new IllegalStateException("no a_Position");
            Program program=new Program(id,position,GLES20.glGetAttribLocation(id,"a_TexCoord"));
            int[] count=new int[1];GLES20.glGetProgramiv(id,GLES20.GL_ACTIVE_UNIFORMS,count,0);int[] size=new int[1],type=new int[1];
            for(int i=0;i<count[0];i++){String name=GLES20.glGetActiveUniform(id,i,size,0,type,0);if(name==null||name.endsWith("]"))continue;int location=GLES20.glGetUniformLocation(id,name);if(location>=0)program.uniforms.put(name,new Uniform(location,type[0]));}
            programs.put(effect,program);return program;
        }catch(RuntimeException failure){
            Log.w(TAG,effect.name+" unavailable: "+failure.getMessage());effect.failed=true;if(id!=0)GLES20.glDeleteProgram(id);return null;
        }finally{if(vertex!=0)GLES20.glDeleteShader(vertex);if(fragment!=0)GLES20.glDeleteShader(fragment);}
    }
    private static int shader(int kind,String source){
        int shader=GLES20.glCreateShader(kind);GLES20.glShaderSource(shader,source);GLES20.glCompileShader(shader);
        int[] ok=new int[1];GLES20.glGetShaderiv(shader,GLES20.GL_COMPILE_STATUS,ok,0);
        if(ok[0]==0){String log=GLES20.glGetShaderInfoLog(shader);GLES20.glDeleteShader(shader);throw new IllegalStateException((kind==GLES20.GL_VERTEX_SHADER?"vertex: ":"fragment: ")+log);}
        return shader;
    }

    private void copy(int frame,int source,TexturePixels.Frame region){
        if(copy==0){
            int v=shader(GLES20.GL_VERTEX_SHADER,COPY_VERTEX),f=shader(GLES20.GL_FRAGMENT_SHADER,COPY_FRAGMENT);copy=GLES20.glCreateProgram();GLES20.glAttachShader(copy,v);GLES20.glAttachShader(copy,f);GLES20.glLinkProgram(copy);GLES20.glDeleteShader(v);GLES20.glDeleteShader(f);
            copyPoint=GLES20.glGetAttribLocation(copy,"point");copyRegion=GLES20.glGetUniformLocation(copy,"region");copyImage=GLES20.glGetUniformLocation(copy,"image");
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,frame);GLES20.glUseProgram(copy);GLES20.glActiveTexture(GLES20.GL_TEXTURE0);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,source);GLES20.glUniform1i(copyImage,0);
        GLES20.glUniform4f(copyRegion,region.u0(),region.v0(),region.u1(),region.v1());
        GLES20.glVertexAttribPointer(copyPoint,2,GLES20.GL_FLOAT,false,8,unit);GLES20.glEnableVertexAttribArray(copyPoint);
        GLES20.glClearColor(0,0,0,0);GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);GLES20.glDisableVertexAttribArray(copyPoint);
    }
    /** Buffers declared by an effect, sized from the layer's effect resolution by their scale; reused across frames. */
    private Map<String,int[]> buffers(WallpaperContent.Layer layer,org.json.JSONArray fbos,int w,int h){
        Map<String,int[]> all=buffers.computeIfAbsent(layer,l->new HashMap<>());Map<String,int[]> named=new HashMap<>();if(fbos==null)return named;
        for(int i=0;i<fbos.length();i++){
            org.json.JSONObject fbo=fbos.optJSONObject(i);if(fbo==null)continue;String name=fbo.optString("name");int scale=Math.max(1,Math.min(16,fbo.optInt("scale",1)));
            int bw=Math.max(1,w/scale),bh=Math.max(1,h/scale);String key=name+"@"+scale;int[] b=all.get(key);
            if(b==null||b[2]!=bw||b[3]!=bh){
                if(b!=null){GLES20.glDeleteFramebuffers(1,new int[]{b[0]},0);GLES20.glDeleteTextures(1,new int[]{b[1]},0);}
                int[] frame=new int[1],texture=new int[1];GLES20.glGenFramebuffers(1,frame,0);GLES20.glGenTextures(1,texture,0);
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,texture[0]);parameters(GLES20.GL_CLAMP_TO_EDGE);
                GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D,0,GLES20.GL_RGBA,bw,bh,0,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,null);
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,frame[0]);GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER,GLES20.GL_COLOR_ATTACHMENT0,GLES20.GL_TEXTURE_2D,texture[0],0);
                GLES20.glClearColor(0,0,0,0);GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);b=new int[]{frame[0],texture[0],bw,bh};all.put(key,b);
            }
            named.put(name,b);
        }
        return named;
    }
    private void plainCopy(int source){
        if(plain==0){
            int v=shader(GLES20.GL_VERTEX_SHADER,COPY_VERTEX),f=shader(GLES20.GL_FRAGMENT_SHADER,PLAIN_FRAGMENT);plain=GLES20.glCreateProgram();GLES20.glAttachShader(plain,v);GLES20.glAttachShader(plain,f);GLES20.glLinkProgram(plain);GLES20.glDeleteShader(v);GLES20.glDeleteShader(f);
            plainPoint=GLES20.glGetAttribLocation(plain,"point");plainRegion=GLES20.glGetUniformLocation(plain,"region");plainImage=GLES20.glGetUniformLocation(plain,"image");
        }
        GLES20.glUseProgram(plain);GLES20.glActiveTexture(GLES20.GL_TEXTURE0);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,source);GLES20.glUniform1i(plainImage,0);GLES20.glUniform4f(plainRegion,0,0,1,1);
        GLES20.glVertexAttribPointer(plainPoint,2,GLES20.GL_FLOAT,false,8,unit);GLES20.glEnableVertexAttribArray(plainPoint);
        GLES20.glClearColor(0,0,0,0);GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);GLES20.glDisableVertexAttribArray(plainPoint);
    }
    private Chain chain(WallpaperContent.Layer layer,int w,int h){
        Chain chain=chains.get(layer);if(chain!=null&&chain.width==w&&chain.height==h)return chain;
        if(chain!=null)release(chain);chain=new Chain();chain.width=w;chain.height=h;
        GLES20.glGenFramebuffers(2,chain.frames,0);GLES20.glGenTextures(2,chain.textures,0);
        for(int i=0;i<2;i++){
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,chain.textures[i]);parameters(GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D,0,GLES20.GL_RGBA,w,h,0,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,null);
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,chain.frames[i]);GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER,GLES20.GL_COLOR_ATTACHMENT0,GLES20.GL_TEXTURE_2D,chain.textures[i],0);
        }
        chains.put(layer,chain);return chain;
    }
    /** Returns {width, height, texture}. */
    private int[] upload(Bitmap bitmap){
        int[] existing=textures.get(bitmap);if(existing!=null)return existing;
        int[] id=new int[1];GLES20.glGenTextures(1,id,0);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,id[0]);parameters(GLES20.GL_REPEAT);GLUtils.texImage2D(GLES20.GL_TEXTURE_2D,0,bitmap,0);
        int[] result={bitmap.getWidth(),bitmap.getHeight(),id[0]};textures.put(bitmap,result);return result;
    }
    /** Engine textures that packages reference by name but never contain. */
    private int[] engine(String name){
        int[] existing=engine.get(name);if(existing!=null)return existing;
        int size=name.equals("util/noise")||name.equals("util/clouds_256")?256:1;ByteBuffer pixels=ByteBuffer.allocateDirect(size*size*4).order(ByteOrder.nativeOrder());
        Random random=new Random(431960);
        for(int i=0;i<size*size;i++)switch(name){
            case "util/white"->pixels.put((byte)255).put((byte)255).put((byte)255).put((byte)255);
            case "util/noflow"->pixels.put((byte)128).put((byte)128).put((byte)0).put((byte)255);
            case "util/noise","util/clouds_256"->pixels.put((byte)random.nextInt(256)).put((byte)random.nextInt(256)).put((byte)random.nextInt(256)).put((byte)random.nextInt(256));
            default->pixels.put((byte)0).put((byte)0).put((byte)0).put((byte)0);
        }
        pixels.position(0);int[] id=new int[1];GLES20.glGenTextures(1,id,0);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,id[0]);parameters(GLES20.GL_REPEAT);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D,0,GLES20.GL_RGBA,size,size,0,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,pixels);
        int[] result={size,size,id[0]};engine.put(name,result);return result;
    }
    private static void parameters(int wrap){
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_S,wrap);GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_T,wrap);
    }
    private static FloatBuffer buffer(float... values){FloatBuffer b=ByteBuffer.allocateDirect(values.length*4).order(ByteOrder.nativeOrder()).asFloatBuffer();b.put(values).position(0);return b;}
    private static void release(Chain chain){GLES20.glDeleteFramebuffers(2,chain.frames,0);GLES20.glDeleteTextures(2,chain.textures,0);}
    @Override public void close(){
        for(Chain c:chains.values())release(c);chains.clear();
        for(Map<String,int[]> named:buffers.values())for(int[] b:named.values()){GLES20.glDeleteFramebuffers(1,new int[]{b[0]},0);GLES20.glDeleteTextures(1,new int[]{b[1]},0);}buffers.clear();
        if(plain!=0)GLES20.glDeleteProgram(plain);plain=0;
        for(Program p:programs.values())GLES20.glDeleteProgram(p.id);programs.clear();
        for(int[] t:textures.values())GLES20.glDeleteTextures(1,new int[]{t[2]},0);textures.clear();
        for(int[] t:engine.values())GLES20.glDeleteTextures(1,new int[]{t[2]},0);engine.clear();
        if(copy!=0)GLES20.glDeleteProgram(copy);copy=0;
    }
}
