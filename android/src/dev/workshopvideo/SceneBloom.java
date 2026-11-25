package dev.workshopvideo;

import android.opengl.GLES20;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Scene bloom (general.bloom), following Wallpaper Engine's own passes in assets/materials/util:
 * downsample_quarter_bloom (threshold, strength, tint at 1/4 size), downsample_eighth_blur_v and blur_h_bloom
 * (13-tap gaussians at 1/8 size, along x then y, eight full-frame texels apart) and combine (added to the frame).
 * The official shaders take g_TexelSize; here it is the full frame's texel, so a step of eight is one 1/8-size texel.
 */
final class SceneBloom implements AutoCloseable {
    private static final String VERTEX="attribute vec2 point;varying vec2 uv;void main(){uv=point;gl_Position=vec4(point*2.0-1.0,0.0,1.0);}";
    private static final String DOWNSAMPLE="precision mediump float;varying vec2 uv;uniform sampler2D image;uniform vec2 texel;uniform float strength,threshold;uniform vec3 tint;\n"+
        "void main(){vec3 a=(texture2D(image,uv-texel).rgb+texture2D(image,uv+texel).rgb+texture2D(image,uv+vec2(-texel.x,texel.y)).rgb+texture2D(image,uv+vec2(texel.x,-texel.y)).rgb)*0.25;\n"+
        "float scale=max(max(a.x,a.y),a.z);a*=clamp(scale-threshold,0.0,1.0);float gray=dot(vec3(0.2989,0.5870,0.1140),a);a=-gray+a*2.0;\n"+
        "gl_FragColor=vec4(max(vec3(0.0),a*strength*tint),1.0);}";
    private static final String BLUR="precision mediump float;varying vec2 uv;uniform sampler2D image;uniform vec2 step;\n"+
        "void main(){float w[7];w[0]=0.171834;w[1]=0.156756;w[2]=0.119007;w[3]=0.075189;w[4]=0.039533;w[5]=0.017298;w[6]=0.006299;\n"+
        "vec3 a=texture2D(image,uv).rgb*w[0];for(int i=1;i<7;i++){a+=(texture2D(image,uv+step*float(i)).rgb+texture2D(image,uv-step*float(i)).rgb)*w[i];}\n"+
        "gl_FragColor=vec4(a,1.0);}";
    private static final String COPY="precision mediump float;varying vec2 uv;uniform sampler2D image;void main(){gl_FragColor=vec4(texture2D(image,uv).rgb,1.0);}";

    private final FloatBuffer quad=ByteBuffer.allocateDirect(8*4).order(ByteOrder.nativeOrder()).asFloatBuffer();
    private int down,blur,copy,width,height;
    /** {texture} for the frame copy; {framebuffer, texture} for the quarter and two eighth-size buffers. */
    private int frame;private final int[][] buffers=new int[3][];

    SceneBloom(){quad.put(new float[]{0,0,1,0,0,1,1,1}).position(0);}

    /** Adds bloom to what is drawn in target (width × height); point/texcoord are attribute indices 0 and 1. */
    void apply(int target,int w,int h,float strength,float threshold,float[] tint){
        if(w<8||h<8)return;
        if(down==0){down=program(DOWNSAMPLE);blur=program(BLUR);copy=program(COPY);}
        if(w!=width||h!=height)allocate(w,h);
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,target);GLES20.glActiveTexture(GLES20.GL_TEXTURE0);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,frame);
        GLES20.glCopyTexSubImage2D(GLES20.GL_TEXTURE_2D,0,0,0,0,0,w,h);
        quad.position(0);GLES20.glVertexAttribPointer(0,2,GLES20.GL_FLOAT,false,8,quad);GLES20.glEnableVertexAttribArray(0);
        float tx=1f/w,ty=1f/h;
        pass(buffers[0],down,frame);GLES20.glUniform2f(GLES20.glGetUniformLocation(down,"texel"),tx,ty);
        GLES20.glUniform1f(GLES20.glGetUniformLocation(down,"strength"),strength);GLES20.glUniform1f(GLES20.glGetUniformLocation(down,"threshold"),threshold);
        GLES20.glUniform3f(GLES20.glGetUniformLocation(down,"tint"),tint[0],tint[1],tint[2]);draw();
        pass(buffers[1],blur,buffers[0][1]);GLES20.glUniform2f(GLES20.glGetUniformLocation(blur,"step"),tx*8,0);draw();
        pass(buffers[2],blur,buffers[1][1]);GLES20.glUniform2f(GLES20.glGetUniformLocation(blur,"step"),0,ty*8);draw();
        // combine: frame + bloom; the frame's alpha is kept.
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,target);GLES20.glViewport(0,0,w,h);GLES20.glUseProgram(copy);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,buffers[2][1]);GLES20.glUniform1i(GLES20.glGetUniformLocation(copy,"image"),0);
        GLES20.glEnable(GLES20.GL_BLEND);GLES20.glBlendFuncSeparate(GLES20.GL_ONE,GLES20.GL_ONE,GLES20.GL_ZERO,GLES20.GL_ONE);draw();
        GLES20.glBlendFunc(GLES20.GL_ONE,GLES20.GL_ONE_MINUS_SRC_ALPHA);
    }
    private void pass(int[] out,int program,int source){
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,out[0]);GLES20.glViewport(0,0,out[2],out[3]);GLES20.glUseProgram(program);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,source);GLES20.glUniform1i(GLES20.glGetUniformLocation(program,"image"),0);
    }
    private void draw(){GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);}
    private void allocate(int w,int h){
        release();width=w;height=h;frame=texture(w,h);
        int[][] sizes={{Math.max(1,w/4),Math.max(1,h/4)},{Math.max(1,w/8),Math.max(1,h/8)},{Math.max(1,w/8),Math.max(1,h/8)}};
        for(int i=0;i<3;i++){int t=texture(sizes[i][0],sizes[i][1]);int[] f=new int[1];GLES20.glGenFramebuffers(1,f,0);GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,f[0]);
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER,GLES20.GL_COLOR_ATTACHMENT0,GLES20.GL_TEXTURE_2D,t,0);buffers[i]=new int[]{f[0],t,sizes[i][0],sizes[i][1]};}
    }
    private static int texture(int w,int h){
        int[] t=new int[1];GLES20.glGenTextures(1,t,0);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,t[0]);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D,0,GLES20.GL_RGBA,w,h,0,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,null);return t[0];
    }
    private static int program(String fragment){
        int v=shader(GLES20.GL_VERTEX_SHADER,VERTEX),f=shader(GLES20.GL_FRAGMENT_SHADER,fragment),p=GLES20.glCreateProgram();
        GLES20.glAttachShader(p,v);GLES20.glAttachShader(p,f);GLES20.glBindAttribLocation(p,0,"point");GLES20.glLinkProgram(p);GLES20.glDeleteShader(v);GLES20.glDeleteShader(f);
        int[] ok=new int[1];GLES20.glGetProgramiv(p,GLES20.GL_LINK_STATUS,ok,0);if(ok[0]==0){String log=GLES20.glGetProgramInfoLog(p);GLES20.glDeleteProgram(p);throw new IllegalStateException("泛光着色器连接失败："+log);}
        return p;
    }
    private static int shader(int type,String source){
        int s=GLES20.glCreateShader(type);GLES20.glShaderSource(s,source);GLES20.glCompileShader(s);int[] ok=new int[1];GLES20.glGetShaderiv(s,GLES20.GL_COMPILE_STATUS,ok,0);
        if(ok[0]==0){String log=GLES20.glGetShaderInfoLog(s);GLES20.glDeleteShader(s);throw new IllegalStateException("泛光着色器编译失败："+log);}return s;
    }
    private void release(){
        if(frame!=0)GLES20.glDeleteTextures(1,new int[]{frame},0);frame=0;
        for(int i=0;i<3;i++)if(buffers[i]!=null){GLES20.glDeleteFramebuffers(1,new int[]{buffers[i][0]},0);GLES20.glDeleteTextures(1,new int[]{buffers[i][1]},0);buffers[i]=null;}
        width=height=0;
    }
    @Override public void close(){release();for(int p:new int[]{down,blur,copy})if(p!=0)GLES20.glDeleteProgram(p);down=blur=copy=0;}
}
