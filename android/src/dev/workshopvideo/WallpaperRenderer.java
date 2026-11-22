package dev.workshopvideo;

import android.graphics.SurfaceTexture;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.media.*;
import android.opengl.*;
import android.os.*;
import android.view.Surface;
import dev.workshopvideo.core.WallpaperGeometry;
import org.json.JSONObject;
import java.nio.*;
import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** EGL and video decoder stay on one thread; frames render only while visible. */
final class WallpaperRenderer {
    // eglInitialize/eglTerminate affect the process display, not just one renderer.
    private static final Object DISPLAY_LOCK=new Object();
    private static int displayUsers;
    private boolean displayLease,displayed;
    private long lastFrameAt,visibleSince;
    private boolean framePending;private boolean powerSaving;private boolean staticInSaver;private final dev.workshopvideo.core.PageAnimation pageAnimation=new dev.workshopvideo.core.PageAnimation();private long pageTick,pagingUntil;private boolean powerStateApplied;
    private volatile float queuedOffset=.5f;private volatile boolean queuedImmediate;private final java.util.concurrent.atomic.AtomicBoolean offsetQueued=new java.util.concurrent.atomic.AtomicBoolean();
    private final Runnable offsetUpdate=this::updateOffsets;
    private void updateOffsets(){offsetQueued.set(false);if(frozen())return;pageAnimation.target(queuedOffset,queuedImmediate);offset=pageAnimation.value();effects.offset=offset;pagingUntil=SystemClock.elapsedRealtime()+250;dirtyCanvas();requestFrame();}
    private final Runnable watchdog=new Runnable(){public void run(){
        if(closed||failed||!visible||frozen())return;
        long now=SystemClock.elapsedRealtime();
        if(now-Math.max(lastFrameAt,visibleSince)>(ready?10000:20000)){error(new IllegalStateException("视频壁纸未继续出帧，正在恢复播放。"));return;}
        handler.postDelayed(this,2000);
    }};
    private final HandlerThread thread=new HandlerThread("wallpaper-video");
    interface Events {void rendered();void failed(String message);default void pagingAvailable(boolean available){}default void contentSize(int width,int height){}}
    private final java.util.concurrent.CompletableFuture<Void> released=new java.util.concurrent.CompletableFuture<>();
    private final Handler handler;private final android.content.Context context;private Events events;
    private WallpaperEffects effects=new WallpaperEffects();
    private SceneGpu sceneGpu;private CanvasWallpaper canvasContent;private Bitmap canvasBitmap;private boolean canvasMode;private long canvasDirtyUntil;private boolean usingSource;private Bitmap uploadedCanvas;private int uploadedGeneration,uploadedWidth,uploadedHeight,maxTextureSize=2048;private dev.workshopvideo.core.EffectMotion.Frame lastCanvasMotion;private Bitmap overlay;private int overlayProgram,overlayTexture;
    private WallpaperFrameClock frameClock;
    private final RenderStats renderStats=new RenderStats();
    private void requestFrame(){if(frameClock!=null&&!closed&&!failed&&visible)frameClock.request();}
    private boolean needsFrames(){if(!visible||closed||failed||frozen())return false;long now=SystemClock.elapsedRealtime();return pageAnimation.moving()||now<pagingUntil||effects.overlayAnimating()||canvasContent!=null&&canvasContent.animated()||(effects.options.optBoolean("gyro")||effects.options.optBoolean("touch"))&&now<canvasDirtyUntil;}
    private void stopFrames(){if(frameClock!=null)frameClock.cancel();}
    private void requestDisplayRate(){if(android.os.Build.VERSION.SDK_INT>=30&&renderSurface!=null&&renderSurface.isValid())try{renderSurface.setFrameRate(visible&&!frozen()?effects.options.optInt("fps",120):0,Surface.FRAME_RATE_COMPATIBILITY_DEFAULT);}catch(RuntimeException ignored){}}
    void settings(JSONObject options){if(!closed)handler.post(()->{effects.options(options);dirtyCanvas();applySpeed();applyPower();pagingAvailability();});}
    void configure(JSONObject next){if(!closed)handler.post(()->{if(closed||failed)return;config=next;effects.options(next.optJSONObject("effects"));VideoApp.put(effects.options,"pan",next.optBoolean("pan"));if(canvasContent!=null)canvasContent.options(next);dirtyCanvas();applySpeed();applyPower();pagingAvailability();});}
    void tilt(float x,float y){if(!closed)handler.post(()->{if(frozen())return;effects.tilt(x,y);dirtyCanvas();requestFrame();});}
    void touch(float x,float y,int action){if(!closed)handler.post(()->{if(frozen())return;effects.touch(x,y,action);dirtyCanvas();requestFrame();});}
    private void applySpeed(){if(player!=null&&ready)try{player.setPlaybackParams(player.getPlaybackParams().setSpeed((float)effects.options.optDouble("speed",1)));if(!visible||frozen()&&hasFrame)player.pause();}catch(Exception ignored){}}
    private boolean offscreen,announced;private int frames;

    private EGLDisplay display=EGL14.EGL_NO_DISPLAY;private EGLContext glContext=EGL14.EGL_NO_CONTEXT;private EGLSurface window=EGL14.EGL_NO_SURFACE;
    private SurfaceTexture texture;private Surface decoderSurface,renderSurface;private MediaPlayer player;
    private int program,textureId,width=1,height=1,videoWidth=1,videoHeight=1;
    private boolean visible,ready,hasFrame;private volatile boolean closed,failed;
    private float offset=.5f;private JSONObject config;private final float[] matrix=new float[16];
    private final FloatBuffer quad=ByteBuffer.allocateDirect(16*4).order(ByteOrder.nativeOrder()).asFloatBuffer();
    WallpaperRenderer(android.content.Context context){this(context,null);}
    WallpaperRenderer(android.content.Context context,Events events){this.context=context;this.events=events;android.os.PowerManager power=context.getSystemService(android.os.PowerManager.class);powerSaving=power!=null&&power.isPowerSaveMode();staticInSaver=AppSettings.batteryStatic(context);quad.put(new float[]{-1,-1,0,0, 1,-1,1,0, -1,1,0,1, 1,1,1,1}).position(0);thread.start();handler=new Handler(thread.getLooper());handler.post(()->frameClock=new WallpaperFrameClock(()->effects.options.optInt("fps",120),this::needsFrames,()->draw(framePending)));}
    void surface(Surface surface,int width,int height,JSONObject config,boolean visible,Events callbacks){if(!closed)handler.post(()->events=callbacks);surface(surface,width,height,config,visible);}
    void surface(Surface surface,int width,int height,JSONObject config,boolean visible){if(closed)return;handler.post(()->{if(closed)return;release();this.width=Math.max(1,width);this.height=Math.max(1,height);this.config=config;this.visible=visible;failed=false;announced=false;frames=0;offscreen=false;try{renderSurface=ownedSurface(surface);initialize(renderSurface,WallpaperStore.file(context,config));}catch(Exception|OutOfMemoryError e){error(e);}});}
    void surfaceFile(Surface surface,int width,int height,File file,JSONObject config,boolean visible){if(closed)return;handler.post(()->{if(closed)return;release();this.width=Math.max(1,width);this.height=Math.max(1,height);this.config=config;this.visible=visible;failed=false;announced=false;frames=0;offscreen=false;try{renderSurface=ownedSurface(surface);initialize(renderSurface,file);}catch(Exception|OutOfMemoryError e){error(e);}});}
    void replace(JSONObject next,boolean visible,Events callbacks){if(!closed)handler.post(()->{if(closed)return;try{releaseContent();this.config=next;this.events=callbacks;this.visible=visible;failed=false;announced=false;frames=0;initialize(renderSurface,WallpaperStore.file(context,next));}catch(Exception|OutOfMemoryError e){error(e);}});}
    void resize(int width,int height){if(!closed)handler.post(()->{this.width=Math.max(1,width);this.height=Math.max(1,height);dirtyCanvas();if(canvasContent!=null)resizeCanvas();pagingAvailability();requestFrame();});}
    void offset(float value){offset(value,false);}
    void offset(float value,boolean immediate){queuedOffset=value;queuedImmediate=immediate;if(!closed&&offsetQueued.compareAndSet(false,true))handler.post(offsetUpdate);}
    private boolean frozen(){return !offscreen&&dev.workshopvideo.core.PowerPlayback.freeze(powerSaving,staticInSaver);}
    void powerSaving(boolean enabled){if(!closed)handler.post(()->{boolean setting=AppSettings.batteryStatic(context);if(powerStateApplied&&powerSaving==enabled&&staticInSaver==setting)return;powerStateApplied=true;powerSaving=enabled;staticInSaver=setting;applyPower();});}
    private void applyPower(){if(closed)return;requestDisplayRate();stopFrames();handler.removeCallbacks(watchdog);boolean still=frozen();if(canvasContent!=null){canvasContent.frozen(still);canvasContent.visible(visible&&!still);}if(player!=null&&ready)try{if(dev.workshopvideo.core.PowerPlayback.play(visible,still,hasFrame))player.start();else player.pause();}catch(Exception e){error(e);return;}if(visible){dirtyCanvas();requestFrame();if(!still&&!canvasMode){visibleSince=lastFrameAt=SystemClock.elapsedRealtime();handler.postDelayed(watchdog,2000);}}}

    boolean hasFailed(){return failed;}
    void redraw(){if(!closed)handler.post(()->{dirtyCanvas();requestFrame();});}
    void visible(boolean value){if(!closed)handler.post(()->{
        boolean resumed=value&&!visible;visible=value;requestDisplayRate();handler.removeCallbacks(watchdog);stopFrames();if(canvasContent!=null)canvasContent.visible(value&&!frozen());if(value&&!frozen())requestFrame();
        if(value&&!canvasMode&&!frozen()){if(resumed)visibleSince=SystemClock.elapsedRealtime();handler.postDelayed(watchdog,2000);}
        if(player==null||!ready||failed)return;
        try{if(value){requestFrame();if(dev.workshopvideo.core.PowerPlayback.play(value,frozen(),hasFrame))player.start();else player.pause();}else player.pause();}catch(Exception e){error(e);}
    });}
    /** Each renderer owns its Surface client; scene textures and videos share its EGL connection. */
    private static Surface ownedSurface(Surface source){Parcel parcel=Parcel.obtain();try{source.writeToParcel(parcel,0);parcel.setDataPosition(0);return Surface.CREATOR.createFromParcel(parcel);}finally{parcel.recycle();}}
    private void initialize(Surface surface,File file) throws Exception {
        if(!file.isFile())throw new IllegalStateException("壁纸文件不存在，请重新设置。");
        canvasMode=!WallpaperContent.kind(file).equals("video");
        effects=new WallpaperEffects();effects.offset=offset;
        effects.options(canvasMode?config.optJSONObject("effects"):EffectOptions.video(config.optJSONObject("effects")));
        if(!canvasMode&&config.optBoolean("fit")&&(config.optJSONObject("effects")==null||!config.optJSONObject("effects").has("alignment")))VideoApp.put(effects.options,"alignment","free");
        if(!config.has("effects"))VideoApp.put(effects.options,"zoom",1);
        VideoApp.put(effects.options,"pan",config.optBoolean("pan"));

        if(display==EGL14.EGL_NO_DISPLAY){display=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        synchronized(DISPLAY_LOCK){if(displayUsers==0){int[] version=new int[2];if(!EGL14.eglInitialize(display,version,0,version,1))throw new IllegalStateException("无法初始化壁纸画面。");}displayUsers++;displayLease=true;}
        int[] attrs={EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_ALPHA_SIZE,8,EGL14.EGL_RENDERABLE_TYPE,EGL14.EGL_OPENGL_ES2_BIT,EGL14.EGL_SURFACE_TYPE,offscreen?EGL14.EGL_PBUFFER_BIT:EGL14.EGL_WINDOW_BIT,EGL14.EGL_NONE};
        EGLConfig[] choices=new EGLConfig[1];int[] count=new int[1];if(!EGL14.eglChooseConfig(display,attrs,0,choices,0,1,count,0)||count[0]==0)throw new IllegalStateException("手机不支持所需的壁纸渲染。");
        glContext=EGL14.eglCreateContext(display,choices[0],EGL14.EGL_NO_CONTEXT,new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE},0);
        if(glContext==EGL14.EGL_NO_CONTEXT)throw new IllegalStateException("无法创建壁纸 GL 上下文：0x"+Integer.toHexString(EGL14.eglGetError()));
        window=offscreen?EGL14.eglCreatePbufferSurface(display,choices[0],new int[]{EGL14.EGL_WIDTH,width,EGL14.EGL_HEIGHT,height,EGL14.EGL_NONE},0):EGL14.eglCreateWindowSurface(display,choices[0],surface,new int[]{EGL14.EGL_NONE},0);
        if(window==EGL14.EGL_NO_SURFACE||!EGL14.eglMakeCurrent(display,window,window,glContext))throw new IllegalStateException("无法连接壁纸显示区域，EGL=0x"+Integer.toHexString(EGL14.eglGetError()));
        }
        requestDisplayRate();visibleSince=SystemClock.elapsedRealtime();lastFrameAt=0;framePending=false;handler.removeCallbacks(watchdog);if(visible&&!canvasMode&&!frozen())handler.postDelayed(watchdog,2000);
        String vertex="attribute vec2 aPosition;attribute vec2 aUv;uniform vec2 scale;uniform float angle;uniform float aspect;uniform vec4 crop;uniform mat4 transform;uniform float flip;uniform vec2 position;varying mediump vec2 uv;void main(){vec2 pos=aPosition*scale;float c=cos(angle);float s=sin(angle);vec2 rotated=mat2(c,-s,s,c)*vec2(pos.x*aspect,pos.y);gl_Position=vec4(rotated.x/aspect+position.x,rotated.y+position.y,0.0,1.0);vec2 baseUv=vec2(mix(aUv.x,1.0-aUv.x,flip),aUv.y);uv=(transform*vec4(crop.xy+baseUv*crop.zw,0.0,1.0)).xy;}";
        String fragment="#extension GL_OES_EGL_image_external : require\nprecision mediump float;uniform samplerExternalOES video;uniform mat3 colors;uniform vec3 colorBias;varying mediump vec2 uv;void main(){vec4 col=texture2D(video,uv);gl_FragColor=vec4(clamp(colors*col.rgb+colorBias,0.0,1.0),col.a);}";
        if(canvasMode)fragment="precision mediump float;uniform sampler2D video;varying mediump vec2 uv;void main(){gl_FragColor=texture2D(video,vec2(uv.x,1.0-uv.y));}";
        int vs=shader(GLES20.GL_VERTEX_SHADER,vertex),fs=shader(GLES20.GL_FRAGMENT_SHADER,fragment);program=GLES20.glCreateProgram();GLES20.glAttachShader(program,vs);GLES20.glAttachShader(program,fs);GLES20.glLinkProgram(program);GLES20.glDeleteShader(vs);GLES20.glDeleteShader(fs);int[] linked=new int[1];GLES20.glGetProgramiv(program,GLES20.GL_LINK_STATUS,linked,0);if(linked[0]==0)throw new IllegalStateException("壁纸着色器连接失败："+GLES20.glGetProgramInfoLog(program));
        int[] maximum=new int[1];GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE,maximum,0);maxTextureSize=Math.max(1,Math.min(4096,maximum[0]));int[] ids=new int[1];GLES20.glGenTextures(1,ids,0);textureId=ids[0];int target=canvasMode?GLES20.GL_TEXTURE_2D:GLES11Ext.GL_TEXTURE_EXTERNAL_OES;GLES20.glBindTexture(target,textureId);
        GLES20.glTexParameteri(target,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);GLES20.glTexParameteri(target,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);
        GLES20.glTexParameteri(target,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);GLES20.glTexParameteri(target,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE);
        if(canvasMode){canvasContent=new CanvasWallpaper(null,width,height,file,config,handler,effects,null);resizeCanvas();canvasContent.frozen(frozen());canvasContent.visible(visible&&!frozen());dirtyCanvas();ready=hasFrame=true;pagingAvailability();if(visible)requestFrame();return;}
        texture=new SurfaceTexture(textureId);android.opengl.Matrix.setIdentityM(matrix,0);texture.setOnFrameAvailableListener(frame->{if(frame==texture){framePending=true;requestFrame();}},handler);decoderSurface=new Surface(texture);
        player=new MediaPlayer();player.setDataSource(file.getAbsolutePath());player.setSurface(decoderSurface);player.setVolume(0,0);player.setLooping(true);
        player.setOnPreparedListener(p->{if(p!=player||closed)return;ready=true;videoWidth=Math.max(1,p.getVideoWidth());videoHeight=Math.max(1,p.getVideoHeight());pagingAvailability();if(visible)try{applySpeed();p.start();}catch(Exception e){error(e);}});
        player.setOnErrorListener((p,what,extra)->{if(p!=player)return true;error(new IllegalStateException("视频壁纸解码失败 ("+what+"/"+extra+")，可改用静态画面。"));return true;});
        player.setOnVideoSizeChangedListener((p,w,h)->{if(p==player&&w>0&&h>0){videoWidth=w;videoHeight=h;pagingAvailability();}});
        player.prepareAsync();if(visible&&!frozen())requestFrame();if(!displayed){GLES20.glClearColor(0,0,0,1);GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);EGL14.eglSwapBuffers(display,window);}
    }
    /** Run the same player, external texture, shaders and draw path before offering video mode. */
    static void verify(android.content.Context context,File file) throws Exception {
        if(!WallpaperContent.kind(file).equals("video")){Bitmap poster=CanvasWallpaper.poster(file,512);if(poster==null)throw new java.io.IOException("无法显示壁纸资源");try{if(WallpaperContent.kind(file).equals("scene"))ScenePainter.verify(poster);}finally{poster.recycle();}return;}
        CountDownLatch done=new CountDownLatch(1);java.util.concurrent.atomic.AtomicReference<String> error=new java.util.concurrent.atomic.AtomicReference<>();
        WallpaperRenderer renderer=new WallpaperRenderer(context,new Events(){public void rendered(){done.countDown();}public void failed(String message){error.set(message);done.countDown();}});
        renderer.handler.post(()->{renderer.offscreen=true;renderer.visible=true;renderer.config=new JSONObject();
            android.util.DisplayMetrics display=context.getResources().getDisplayMetrics();renderer.width=Math.max(1,display.widthPixels);renderer.height=Math.max(1,display.heightPixels);
            try{renderer.initialize(null,file);}catch(Exception|OutOfMemoryError problem){renderer.error(problem);}
        });
        try{if(!done.await(18,TimeUnit.SECONDS))throw new java.io.IOException("视频壁纸播放检查超时，未收到可显示的视频帧。");if(error.get()!=null)throw new java.io.IOException(error.get());}
        finally{renderer.close();}
    }
    private void dirtyCanvas(){canvasDirtyUntil=SystemClock.elapsedRealtime()+Math.max(800,(long)(effects.options.optDouble("smooth",.18)*6000));}
    private void resizeCanvas(){if(canvasContent==null)return;float ratio=Math.min(1,Math.min(2560f/Math.max(width,height),(float)Math.sqrt(2073600d/((double)width*height))));int w=Math.max(1,Math.round(width*ratio)),h=Math.max(1,Math.round(height*ratio));if(canvasBitmap==null||canvasBitmap.getWidth()!=w||canvasBitmap.getHeight()!=h){Bitmap next=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);if(canvasBitmap!=null){if(frozen())new Canvas(next).drawBitmap(canvasBitmap,null,new android.graphics.Rect(0,0,w,h),new android.graphics.Paint(3));canvasBitmap.recycle();}canvasBitmap=next;}android.opengl.Matrix.setIdentityM(matrix,0);}
    private void pagingAvailability(){if(canvasMode){if(canvasContent!=null)canvasContent.reportPaging(width,height,events);return;}if(events!=null){events.contentSize(videoWidth,videoHeight);events.pagingAvailable(WallpaperGeometry.canPagePicture(videoWidth,videoHeight,width,height,effects.options.optString("alignment","cover"),effects.options.optBoolean("pan"),(float)effects.options.optDouble("zoom",1)));} }
    private int shader(int type,String code){int shader=GLES20.glCreateShader(type);GLES20.glShaderSource(shader,code);GLES20.glCompileShader(shader);int[] result=new int[1];GLES20.glGetShaderiv(shader,GLES20.GL_COMPILE_STATUS,result,0);if(result[0]==0){String reason=GLES20.glGetShaderInfoLog(shader);GLES20.glDeleteShader(shader);throw new IllegalStateException(reason);}return shader;}
    private void draw(boolean update){
        if(closed||failed||(!canvasMode&&texture==null)||!visible)return;
        try{
            long renderStart=System.nanoTime();
            if(update&&!canvasMode){texture.updateTexImage();texture.getTransformMatrix(matrix);hasFrame=true;framePending=false;lastFrameAt=SystemClock.elapsedRealtime();}if(!hasFrame)return;long now=SystemClock.elapsedRealtime();float delta=pageTick==0?.016f:(now-pageTick)/1000f;pageTick=now;offset=pageAnimation.step(delta);effects.offset=offset;
            if(!canvasMode&&!update&&announced&&!pageAnimation.moving()&&now>canvasDirtyUntil&&!effects.overlayAnimating())return;
            Bitmap upload=canvasBitmap;dev.workshopvideo.core.CanvasCamera.Frame camera=null;boolean layers=canvasMode&&canvasContent.gpuScene()!=null;
            if(canvasMode){
                if(!frozen()&&announced&&!pageAnimation.moving()&&now>canvasDirtyUntil&&!canvasContent.animated()&&!effects.overlayAnimating())return;
                if(!frozen()||!announced)usingSource=!layers&&canvasContent.gpuComposition();
                if(layers){if(!frozen()||lastCanvasMotion==null)lastCanvasMotion=effects.frame(config.optBoolean("fit"));camera=dev.workshopvideo.core.CanvasCamera.frame(canvasContent.contentWidth(),canvasContent.contentHeight(),width,height,config.optBoolean("fit"),effects.options.optBoolean("pan"),offset,lastCanvasMotion);}
                else if(usingSource){upload=canvasContent.sourceTexture(maxTextureSize);if(!frozen()||lastCanvasMotion==null)lastCanvasMotion=effects.frame(config.optBoolean("fit"));camera=dev.workshopvideo.core.CanvasCamera.frame(canvasContent.contentWidth(),canvasContent.contentHeight(),width,height,config.optBoolean("fit"),effects.options.optBoolean("pan"),offset,lastCanvasMotion);}
                else if(!frozen()||!announced){if(!pageAnimation.moving()&&announced&&now>canvasDirtyUntil&&!canvasContent.animated()&&!effects.overlayAnimating())return;canvasContent.paint(new Canvas(canvasBitmap),canvasBitmap.getWidth(),canvasBitmap.getHeight());}
            }
            dev.workshopvideo.core.EffectMotion.Frame motion=canvasMode?new dev.workshopvideo.core.EffectMotion.Frame(0,0,1,0):effects.frame(false);
            WallpaperGeometry.Layout fit=WallpaperGeometry.moving(videoWidth,videoHeight,width,height,config.optBoolean("fit"),effects.options.optBoolean("pan"),offset,(float)effects.options.optDouble("scrollStrength",.5),motion.zoom(),motion.x(),motion.y());
            String alignment=effects.options.optString("alignment",config.optBoolean("fit")?"free":"cover");
            if(!canvasMode)fit=WallpaperGeometry.picture(videoWidth,videoHeight,width,height,alignment,effects.options.optBoolean("pan"),offset,motion.zoom(),motion.x(),motion.y());
            if(canvasMode)fit=camera==null?new WallpaperGeometry.Layout(1,1,0,0,1,1):new WallpaperGeometry.Layout(camera.scaleX(),camera.scaleY(),0,0,1,1);
            float us=fit.uSize(),vs=fit.vSize(),u=fit.u0(),v=fit.v0();
            long preparedAt=System.nanoTime();
            int bg=android.graphics.Color.parseColor(effects.options.optString("backgroundColor","#000000"));GLES20.glViewport(0,0,width,height);GLES20.glClearColor(android.graphics.Color.red(bg)/255f,android.graphics.Color.green(bg)/255f,android.graphics.Color.blue(bg)/255f,1);GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);GLES20.glUseProgram(program);
            if(layers){if(sceneGpu==null)sceneGpu=new SceneGpu(maxTextureSize);sceneGpu.draw(canvasContent.gpuScene(),effects,camera,lastCanvasMotion,width,height);}
            else{
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);GLES20.glBindTexture(canvasMode?GLES20.GL_TEXTURE_2D:GLES11Ext.GL_TEXTURE_EXTERNAL_OES,textureId);if(canvasMode&&(uploadedCanvas!=upload||uploadedGeneration!=upload.getGenerationId())){if(uploadedWidth==upload.getWidth()&&uploadedHeight==upload.getHeight())GLUtils.texSubImage2D(GLES20.GL_TEXTURE_2D,0,0,0,upload);else GLUtils.texImage2D(GLES20.GL_TEXTURE_2D,0,upload,0);uploadedCanvas=upload;uploadedGeneration=upload.getGenerationId();uploadedWidth=upload.getWidth();uploadedHeight=upload.getHeight();}GLES20.glUniform1i(GLES20.glGetUniformLocation(program,"video"),0);
            float aspect=width/(float)height,cover=canvasMode||!alignment.equals("cover")?1:(float)(Math.abs(Math.cos(Math.toRadians(motion.rotation())))+Math.abs(Math.sin(Math.toRadians(motion.rotation())))*Math.max(aspect,1/aspect));GLES20.glUniform1f(GLES20.glGetUniformLocation(program,"aspect"),aspect);GLES20.glUniform2f(GLES20.glGetUniformLocation(program,"scale"),fit.scaleX()*cover,fit.scaleY()*cover);GLES20.glUniform4f(GLES20.glGetUniformLocation(program,"crop"),u,v,us,vs);GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program,"transform"),1,false,matrix,0);
            GLES20.glUniform1f(GLES20.glGetUniformLocation(program,"flip"),!canvasMode&&effects.options.optBoolean("flip")?1:0);
            GLES20.glUniform2f(GLES20.glGetUniformLocation(program,"position"),camera!=null?camera.x():!canvasMode&&alignment.equals("free")?WallpaperGeometry.freePosition(fit.scaleX(),(float)effects.options.optDouble("x"),effects.options.optBoolean("gyro")?effects.sx*(float)effects.options.optDouble("strength")*.06f:0,effects.options.optBoolean("pan"),offset):0,camera!=null?camera.y():!canvasMode&&alignment.equals("free")?-WallpaperGeometry.freePosition(fit.scaleY(),(float)effects.options.optDouble("y"),effects.options.optBoolean("gyro")?effects.sy*(float)effects.options.optDouble("strength")*.06f:0,false,.5f):0);
            float[] rgb=dev.workshopvideo.core.PictureColors.matrix((float)effects.options.optDouble("brightness",1),(float)effects.options.optDouble("contrast",1),(float)effects.options.optDouble("saturation",1),(float)effects.options.optDouble("hue",0));float[] colorMatrix={rgb[0],rgb[4],rgb[8],rgb[1],rgb[5],rgb[9],rgb[2],rgb[6],rgb[10]};GLES20.glUniformMatrix3fv(GLES20.glGetUniformLocation(program,"colors"),1,false,colorMatrix,0);GLES20.glUniform3f(GLES20.glGetUniformLocation(program,"colorBias"),rgb[3],rgb[7],rgb[11]);
            GLES20.glUniform1f(GLES20.glGetUniformLocation(program,"angle"),(float)Math.toRadians(camera==null?motion.rotation():camera.angle()));
            int position=GLES20.glGetAttribLocation(program,"aPosition"),uv=GLES20.glGetAttribLocation(program,"aUv");quad.position(0);GLES20.glVertexAttribPointer(position,2,GLES20.GL_FLOAT,false,16,quad);quad.position(2);GLES20.glVertexAttribPointer(uv,2,GLES20.GL_FLOAT,false,16,quad);GLES20.glEnableVertexAttribArray(position);GLES20.glEnableVertexAttribArray(uv);GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);
            }
            if(!canvasMode||layers||usingSource)drawOverlay();
            int glError=GLES20.glGetError();if(glError!=GLES20.GL_NO_ERROR)throw new IllegalStateException("壁纸绘制失败，GL=0x"+Integer.toHexString(glError));
            long swapAt=System.nanoTime();
            if(!EGL14.eglSwapBuffers(display,window))throw new IllegalStateException("壁纸显示区域已失效，EGL=0x"+Integer.toHexString(EGL14.eglGetError()));
            renderStats.frame(canvasMode?(layers?"scene-layers":usingSource?"scene-cache":"scene-canvas"):"video",effects.options.optInt("fps",120),renderStart,preparedAt,swapAt,System.nanoTime());
            displayed=true;if(frozen()){handler.removeCallbacks(watchdog);stopFrames();if(player!=null&&ready)player.pause();}
            if((update||canvasMode)&&++frames>=(offscreen?3:1)&&!announced){announced=true;if(events!=null)events.rendered();}
        }catch(Exception e){error(e);}
    }
    private void drawOverlay(){
        if(!effects.options.optBoolean("particles")&&!effects.options.optBoolean("ripple"))return;
        int ow=Math.min(480,width),oh=Math.max(1,(int)((long)height*ow/Math.max(1,width)));oh=Math.min(1600,oh);
        if(overlay==null||overlay.getWidth()!=ow||overlay.getHeight()!=oh){if(overlay!=null)overlay.recycle();overlay=Bitmap.createBitmap(ow,oh,Bitmap.Config.ARGB_8888);}
        overlay.eraseColor(0);effects.overlay(new Canvas(overlay),ow,oh);
        if(overlayProgram==0){String vert="attribute vec2 aPosition;attribute vec2 aUv;varying mediump vec2 uv;void main(){gl_Position=vec4(aPosition,0.0,1.0);uv=vec2(aUv.x,1.0-aUv.y);}";String frag="precision mediump float;uniform sampler2D layer;varying mediump vec2 uv;void main(){gl_FragColor=texture2D(layer,uv);}";int a=shader(GLES20.GL_VERTEX_SHADER,vert),b=shader(GLES20.GL_FRAGMENT_SHADER,frag);overlayProgram=GLES20.glCreateProgram();GLES20.glAttachShader(overlayProgram,a);GLES20.glAttachShader(overlayProgram,b);GLES20.glLinkProgram(overlayProgram);int[] linked=new int[1];GLES20.glGetProgramiv(overlayProgram,GLES20.GL_LINK_STATUS,linked,0);if(linked[0]==0)throw new IllegalStateException("叠加效果着色器连接失败");GLES20.glDeleteShader(a);GLES20.glDeleteShader(b);int[] ids=new int[1];GLES20.glGenTextures(1,ids,0);overlayTexture=ids[0];}
        GLES20.glUseProgram(overlayProgram);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,overlayTexture);GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE);GLUtils.texImage2D(GLES20.GL_TEXTURE_2D,0,overlay,0);GLES20.glUniform1i(GLES20.glGetUniformLocation(overlayProgram,"layer"),0);
        int p=GLES20.glGetAttribLocation(overlayProgram,"aPosition"),u=GLES20.glGetAttribLocation(overlayProgram,"aUv");quad.position(0);GLES20.glVertexAttribPointer(p,2,GLES20.GL_FLOAT,false,16,quad);quad.position(2);GLES20.glVertexAttribPointer(u,2,GLES20.GL_FLOAT,false,16,quad);GLES20.glEnableVertexAttribArray(p);GLES20.glEnableVertexAttribArray(u);GLES20.glEnable(GLES20.GL_BLEND);GLES20.glBlendFunc(GLES20.GL_ONE,GLES20.GL_ONE_MINUS_SRC_ALPHA);GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);GLES20.glDisable(GLES20.GL_BLEND);
    }
    private void error(Throwable problem){if(failed)return;failed=true;String message=VideoApp.error(problem);if(events!=null)events.failed(message);release();}
    private void releaseContent(){
        handler.removeCallbacks(watchdog);stopFrames();if(sceneGpu!=null){sceneGpu.close();sceneGpu=null;}if(canvasContent!=null){canvasContent.close();canvasContent=null;}if(canvasBitmap!=null){canvasBitmap.recycle();canvasBitmap=null;}if(overlay!=null){overlay.recycle();overlay=null;}ready=hasFrame=framePending=false;
        if(player!=null){MediaPlayer old=player;player=null;try{old.setSurface(null);old.release();}catch(Exception ignored){}}
        if(decoderSurface!=null){decoderSurface.release();decoderSurface=null;}if(texture!=null){texture.release();texture=null;}
        if(display!=EGL14.EGL_NO_DISPLAY){if(overlayProgram!=0)GLES20.glDeleteProgram(overlayProgram);if(overlayTexture!=0)GLES20.glDeleteTextures(1,new int[]{overlayTexture},0);if(program!=0)GLES20.glDeleteProgram(program);if(textureId!=0)GLES20.glDeleteTextures(1,new int[]{textureId},0);}
        overlayProgram=overlayTexture=program=textureId=0;canvasMode=false;usingSource=false;uploadedCanvas=null;uploadedWidth=uploadedHeight=uploadedGeneration=0;lastCanvasMotion=null;
    }
    private void release(){
        releaseContent();
        if(display!=EGL14.EGL_NO_DISPLAY){EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT);if(window!=EGL14.EGL_NO_SURFACE)EGL14.eglDestroySurface(display,window);if(glContext!=EGL14.EGL_NO_CONTEXT)EGL14.eglDestroyContext(display,glContext);synchronized(DISPLAY_LOCK){if(displayLease){displayLease=false;if(--displayUsers==0)EGL14.eglTerminate(display);}}EGL14.eglReleaseThread();}
        displayed=false;display=EGL14.EGL_NO_DISPLAY;glContext=EGL14.EGL_NO_CONTEXT;window=EGL14.EGL_NO_SURFACE;if(renderSurface!=null){renderSurface.release();renderSurface=null;}
    }
    void closeAsync(){if(closed)return;closed=true;handler.post(()->{try{release();}finally{thread.quitSafely();released.complete(null);}});}
    void closeAsync(Runnable finished){released.thenRun(finished);closeAsync();}
    void close(){closeAsync();try{released.get(1500,TimeUnit.MILLISECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}catch(java.util.concurrent.ExecutionException|java.util.concurrent.TimeoutException ignored){}}
}
