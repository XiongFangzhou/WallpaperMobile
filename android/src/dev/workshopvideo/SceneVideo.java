package dev.workshopvideo;

import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.view.Surface;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * A video layer on the scene's GL thread. MediaPlayer decodes the MP4 stored in the converted scene (read in place) into
 * a SurfaceTexture; each new frame is copied into an ordinary 2D texture, upright like an uploaded bitmap, so the layer
 * and its author effects sample it exactly as they sample images. Silent and looping, as Wallpaper Engine plays video
 * textures. Until the first frame arrives the layer keeps drawing its converted first-frame bitmap.
 */
final class SceneVideo implements AutoCloseable {
    /** point: 0..1 with y down (image top first); framebuffer row 0 receives the image's top row, as GLUtils uploads bitmaps. */
    static final String VERTEX="attribute vec2 point;uniform mat4 transform;varying mediump vec2 uv;void main(){uv=(transform*vec4(point.x,1.0-point.y,0.0,1.0)).xy;gl_Position=vec4(point*2.0-1.0,0.0,1.0);}";
    static final String FRAGMENT="#extension GL_OES_EGL_image_external : require\nprecision mediump float;uniform samplerExternalOES video;varying mediump vec2 uv;void main(){gl_FragColor=vec4(texture2D(video,uv).rgb,1.0);}";
    private final FloatBuffer quad=ByteBuffer.allocateDirect(8*4).order(ByteOrder.nativeOrder()).asFloatBuffer();
    private final float[] transform=new float[16];
    private MediaPlayer player;private SurfaceTexture frames;private Surface surface;
    private int external,framebuffer,texture,width,height;private boolean drawn;
    /** Set by the decoder's callbacks, which may arrive on another thread. */
    private volatile boolean fresh,prepared,failed,playing;

    SceneVideo(WallpaperContent.Layer layer,boolean play){
        quad.put(new float[]{0,0,1,0,0,1,1,1}).position(0);playing=play;
        try{
            int[] id=new int[1];GLES20.glGenTextures(1,id,0);external=id[0];GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,external);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,0);
            frames=new SurfaceTexture(external);frames.setOnFrameAvailableListener(t->fresh=true);surface=new Surface(frames);
            MediaPlayer p=new MediaPlayer();player=p;
            // The player duplicates the descriptor, so the stream can close once the source is set.
            try(FileInputStream in=new FileInputStream(layer.video)){p.setDataSource(in.getFD(),layer.videoOffset,layer.videoLength);}
            p.setSurface(surface);p.setVolume(0,0);p.setLooping(true);
            p.setOnPreparedListener(m->{if(m!=player)return;prepared=true;if(playing)try{m.start();}catch(IllegalStateException e){failed=true;}});
            p.setOnErrorListener((m,what,extra)->{if(m==player){failed=true;android.util.Log.w("SceneVideo","layer video stopped ("+what+"/"+extra+")");}return true;});
            p.prepareAsync();
        }catch(Exception|LinkageError unavailable){failed=true;android.util.Log.w("SceneVideo","layer video unavailable",unavailable);}
    }
    /** Pauses or resumes decoding (wallpaper hidden, power saving, layer hidden). */
    void playing(boolean on){
        if(on==playing)return;playing=on;
        MediaPlayer p=player;if(!prepared||failed||p==null)return;
        try{if(on)p.start();else p.pause();}catch(IllegalStateException e){failed=true;}
    }
    /** Copies a newly decoded frame into the texture. GL thread; the bound framebuffer, viewport and blending are restored. */
    void update(int program,int maxTexture){
        if(failed||frames==null||!fresh)return;fresh=false;
        try{
            frames.updateTexImage();frames.getTransformMatrix(transform);
            int vw=player.getVideoWidth(),vh=player.getVideoHeight();if(vw<=0||vh<=0)return;
            float k=Math.min(1,maxTexture/(float)Math.max(vw,vh));int w=Math.max(1,Math.round(vw*k)),h=Math.max(1,Math.round(vh*k));
            int[] bound=new int[1],view=new int[4];GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING,bound,0);GLES20.glGetIntegerv(GLES20.GL_VIEWPORT,view,0);boolean blending=GLES20.glIsEnabled(GLES20.GL_BLEND);
            if(texture==0||w!=width||h!=height){
                if(texture==0){int[] t=new int[1];GLES20.glGenTextures(1,t,0);texture=t[0];}
                if(framebuffer==0){int[] f=new int[1];GLES20.glGenFramebuffers(1,f,0);framebuffer=f[0];}
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,texture);
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE);
                GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D,0,GLES20.GL_RGBA,w,h,0,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,null);
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,framebuffer);GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER,GLES20.GL_COLOR_ATTACHMENT0,GLES20.GL_TEXTURE_2D,texture,0);
                width=w;height=h;
            }
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,framebuffer);GLES20.glViewport(0,0,w,h);GLES20.glDisable(GLES20.GL_BLEND);
            GLES20.glUseProgram(program);GLES20.glActiveTexture(GLES20.GL_TEXTURE0);GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,external);
            GLES20.glUniform1i(GLES20.glGetUniformLocation(program,"video"),0);GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program,"transform"),1,false,transform,0);
            quad.position(0);GLES20.glVertexAttribPointer(0,2,GLES20.GL_FLOAT,false,8,quad);GLES20.glEnableVertexAttribArray(0);GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,0);
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,bound[0]);GLES20.glViewport(view[0],view[1],view[2],view[3]);if(blending)GLES20.glEnable(GLES20.GL_BLEND);
            drawn=true;
        }catch(RuntimeException failure){failed=true;android.util.Log.w("SceneVideo","layer video frame not copied",failure);}
    }
    /** The latest frame as a 2D texture, 0 until one has been copied. */
    int texture(){return drawn?texture:0;}
    int width(){return width;}
    int height(){return height;}
    @Override public void close(){
        MediaPlayer p=player;player=null;if(p!=null)try{p.setSurface(null);p.release();}catch(Exception ignored){}
        if(surface!=null){surface.release();surface=null;}if(frames!=null){frames.release();frames=null;}
        if(framebuffer!=0)GLES20.glDeleteFramebuffers(1,new int[]{framebuffer},0);
        for(int t:new int[]{external,texture})if(t!=0)GLES20.glDeleteTextures(1,new int[]{t},0);
        framebuffer=texture=external=0;drawn=false;
    }
}
