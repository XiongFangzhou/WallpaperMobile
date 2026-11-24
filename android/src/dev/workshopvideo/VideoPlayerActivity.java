package dev.workshopvideo;

import android.app.*;
import android.content.*;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.media.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import dev.workshopvideo.core.*;
import java.util.concurrent.*;
import org.json.JSONObject;
import java.util.Locale;

/** Local and authenticated streaming video player. All gestures stay inside the video area. */
public final class VideoPlayerActivity extends Activity implements TextureView.SurfaceTextureListener {
    private static final int TAP=0,BRIGHTNESS=1,VOLUME=2,PINCH=3,HOLD=4,HORIZONTAL=5;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final PlayerControls settings=new PlayerControls();
    private VideoApp app;
    private String id,onlineItem,title,streamFailure="";private boolean online;
    private final ExecutorService streamWorkers=Executors.newSingleThreadExecutor();
    private StreamRequest opening;private WorkshopVideoSource stream;
    private TextView loadingText;

    private TextureView texture;
    private Surface surface;
    private VideoPlayback player;
    private AudioManager audio;
    private AudioFocusRequest focusRequest;
    private FrameLayout root;
    private LinearLayout top,bottom;
    private TextView hud,clock;
    private Button speed;private ImageView play;
    private SeekBar seek;
    private ProgressBar loading;
    private GestureDetector taps;
    private ScaleGestureDetector scales;
    private boolean foreground,ready,playWanted=true,resumeWanted=true,seeking,focusHeld,errorShown;
    private int duration,position,videoWidth,videoHeight,gestureMode,pointerCount,startVolume,slop,startPosition,swipeTarget;
    private float startX,startY,startBrightness;
    private final Runnable hideControls=()->{if(ready&&playWanted&&!seeking)setControls(false);};
    private final Runnable hideHud=()->hud.setVisibility(View.GONE);
    private final Runnable holdAction=()->{
        try{if(foreground&&ready&&gestureMode==TAP&&pointerCount==1&&player!=null&&player.isPlaying()) {
            gestureMode=HOLD;cancelTap();settings.hold(true);applySpeed();if(settings.holding()){setControls(false);main.removeCallbacks(hideHud);hud.setVisibility(View.GONE);}
        }}catch(IllegalStateException e){playbackError();}
    };
    private final Runnable tick=new Runnable(){public void run(){
        if(!foreground)return;
        if(ready&&player!=null&&!seeking)try{position=player.getCurrentPosition();updateProgress(position);}catch(IllegalStateException ignored){}
        main.postDelayed(this,250);
    }};

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);app=(VideoApp)getApplication();
        try {
            onlineItem=getIntent().getStringExtra("workshopId");online=onlineItem!=null;
            if(online){long item=Long.parseUnsignedLong(onlineItem);if(item==0)throw new IllegalArgumentException("作品编号无效");title=getIntent().getStringExtra("title");}
            else{id=getIntent().getStringExtra("id");JSONObject record=app.job(id);if(!record.optString("state").equals("saved"))throw new IllegalStateException("视频尚未下载完成。");position=(int)Math.max(0,Math.min(Integer.MAX_VALUE,record.optLong("playbackPosition")));title=record.optString("title","视频");}
            settings.setSpeed(getSharedPreferences("player",MODE_PRIVATE).getFloat("speed",1));
            if(state!=null){position=state.getInt("position",position);resumeWanted=state.getBoolean("playing",true);settings.setSpeed(state.getFloat("speed",settings.selectedSpeed()));settings.setZoom(state.getFloat("zoom",1));}
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
            setVolumeControlStream(AudioManager.STREAM_MUSIC);
            audio=getSystemService(AudioManager.class);
            AudioAttributes attributes=new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build();
            focusRequest=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(attributes)
                .setWillPauseWhenDucked(true).setOnAudioFocusChangeListener(this::audioFocusChanged,main).build();
            slop=ViewConfiguration.get(this).getScaledTouchSlop();
            build((online?"在线播放 · ":"")+(title==null?"视频":title));immersive();
        }catch(Exception e){Toast.makeText(this,VideoApp.error(e),Toast.LENGTH_LONG).show();finish();}
    }
    private int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private LinearLayout column(){LinearLayout view=new LinearLayout(this);view.setOrientation(LinearLayout.VERTICAL);return view;}
    private TextView label(String value,int size){TextView view=new TextView(this);view.setText(value);view.setTextSize(size);view.setTextColor(0xfff2f5f8);return view;}
    private Button action(String value,Runnable click){
        Button button=new Button(this);button.setText(value);button.setTextColor(0xfff2f5f8);button.setTextSize(13);button.setAllCaps(false);
        button.setMinWidth(0);button.setMinimumWidth(0);button.setMinHeight(0);button.setMinimumHeight(0);button.setPadding(dp(12),0,dp(12),0);
        button.setBackgroundColor(Color.TRANSPARENT);button.setOnClickListener(v->click.run());return button;
    }
    private ImageView icon(String name,String description,Runnable click){
        ImageView view=new ImageView(this);view.setImageDrawable(new Ui.Icon(name,0xfff2f5f8,dp(24)));view.setScaleType(ImageView.ScaleType.CENTER);view.setContentDescription(description);
        view.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x33ffffff),null,null));view.setOnClickListener(v->click.run());return view;
    }
    /** The play button shows the action it performs; disabled states keep a readable description. */
    private void playState(String state){
        String icon=switch(state){case "暂停"->"pause";case "重播"->"refresh";default->"play";};
        play.setImageDrawable(new Ui.Icon(icon,0xfff2f5f8,dp(24)));play.setContentDescription(state);play.setEnabled(!state.equals("载入中")&&!state.equals("未能播放"));play.setAlpha(play.isEnabled()?1f:.45f);
    }
    private void build(String title) {
        getWindow().setStatusBarColor(Color.BLACK);getWindow().setNavigationBarColor(Color.BLACK);
        root=new FrameLayout(this);root.setBackgroundColor(Color.BLACK);
        texture=new TextureView(this);texture.setSurfaceTextureListener(this);root.addView(texture,new FrameLayout.LayoutParams(-1,-1));
        loading=new ProgressBar(this);FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(dp(44),dp(44),Gravity.CENTER);root.addView(loading,lp);
        loadingText=label(online?"正在连接 Steam…":"正在载入…",12);loadingText.setGravity(Gravity.CENTER);loadingText.setMaxLines(3);loadingText.setPadding(dp(24),0,dp(24),0);FrameLayout.LayoutParams ltp=new FrameLayout.LayoutParams(-1,-2,Gravity.CENTER);ltp.topMargin=dp(100);root.addView(loadingText,ltp);
        top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0xb3000000,0x00000000}));
        ImageView back=icon("previous",online?"返回工坊作品":"返回视频列表",this::finish);top.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));
        TextView heading=label(title,15);heading.setMaxLines(1);heading.setEllipsize(android.text.TextUtils.TruncateAt.END);heading.setTypeface(null,Typeface.BOLD);heading.setShadowLayer(dp(4),0,0,0x99000000);top.addView(heading,new LinearLayout.LayoutParams(0,-2,1));heading.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(top,new FrameLayout.LayoutParams(-1,-2,Gravity.TOP));
        bottom=column();bottom.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,new int[]{0xcc000000,0x00000000}));
        seek=new SeekBar(this);seek.setMax(10000);seek.setContentDescription("播放进度");seek.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));seek.setThumbTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));seek.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0x66ffffff));
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,dp(32));sp.topMargin=dp(18);bottom.addView(seek,sp);
        LinearLayout actions=new LinearLayout(this);actions.setGravity(Gravity.CENTER_VERTICAL);
        play=icon("pause","暂停",this::togglePlay);actions.addView(play,new LinearLayout.LayoutParams(dp(48),dp(48)));
        clock=label("00:00 / 00:00",12);clock.setPadding(dp(4),0,0,0);actions.addView(clock,new LinearLayout.LayoutParams(0,-2,1));
        speed=action(speedText(settings.selectedSpeed()),this::chooseSpeed);speed.setContentDescription("播放速度");speed.setTypeface(null,Typeface.BOLD);actions.addView(speed,new LinearLayout.LayoutParams(-2,dp(48)));
        actions.addView(icon("fit","画面适配屏幕",()->{settings.setZoom(1);transform();hint("画面已适配屏幕",true);scheduleHide();}),new LinearLayout.LayoutParams(dp(48),dp(48)));
        ImageView rotate=icon("rotate","横竖屏切换，长按恢复自动旋转",()->{settings.setZoom(1);setRequestedOrientation(getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE?ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT:ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);scheduleHide();});
        rotate.setOnLongClickListener(v->{setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);hint("已启用自动旋转",true);return true;});
        actions.addView(rotate,new LinearLayout.LayoutParams(dp(48),dp(48)));bottom.addView(actions);
        root.addView(bottom,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));
        hud=label("",16);hud.setGravity(Gravity.CENTER);hud.setPadding(dp(22),dp(13),dp(22),dp(13));
        GradientDrawable panel=new GradientDrawable();panel.setColor(0xc51b2027);panel.setCornerRadius(dp(12));hud.setBackground(panel);hud.setVisibility(View.GONE);
        root.addView(hud,new FrameLayout.LayoutParams(-2,-2,Gravity.CENTER));
        root.setOnApplyWindowInsetsListener((v,insets)->{
            int left=0,right=0,above=0,below=0;
            if(Build.VERSION.SDK_INT>=30){android.graphics.Insets safe=insets.getInsets(WindowInsets.Type.displayCutout()|WindowInsets.Type.systemGestures());left=safe.left;right=safe.right;above=safe.top;below=safe.bottom;}
            else {DisplayCutout cutout=insets.getDisplayCutout();if(cutout!=null){left=cutout.getSafeInsetLeft();right=cutout.getSafeInsetRight();above=cutout.getSafeInsetTop();}below=insets.getSystemWindowInsetBottom();}
            top.setPadding(left+dp(4),above+dp(4),right+dp(12),dp(24));
            bottom.setPadding(left+dp(6),0,right+dp(6),below+dp(6));return insets;
        });
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onStartTrackingTouch(SeekBar bar){if(!ready)return;seeking=true;main.removeCallbacks(hideControls);cancelHold();try{if(player.isPlaying())player.pause();}catch(IllegalStateException ignored){}}
            public void onProgressChanged(SeekBar bar,int value,boolean fromUser){if(fromUser&&ready)clock.setText(PlayerControls.time(PlayerControls.seekPosition(value,bar.getMax(),duration))+" / "+PlayerControls.time(duration));}
            public void onStopTrackingTouch(SeekBar bar){if(!ready){seeking=false;return;}position=PlayerControls.seekPosition(bar.getProgress(),bar.getMax(),duration);seeking=false;try{player.seekTo(position,MediaPlayer.SEEK_CLOSEST);if(playWanted)startPlayback();}catch(IllegalStateException ignored){}updateProgress(position);scheduleHide();}
        });
        taps=new GestureDetector(this,new GestureDetector.SimpleOnGestureListener(){
            @Override public boolean onDown(android.view.MotionEvent e){return true;}
            @Override public boolean onSingleTapConfirmed(android.view.MotionEvent e){setControls(top.getVisibility()!=View.VISIBLE);scheduleHide();return true;}
            @Override public boolean onDoubleTap(android.view.MotionEvent e){togglePlay();return true;}
        });taps.setIsLongpressEnabled(false);
        scales=new ScaleGestureDetector(this,new ScaleGestureDetector.SimpleOnScaleGestureListener(){
            @Override public boolean onScaleBegin(ScaleGestureDetector detector){if(gestureMode==HORIZONTAL)finishSwipe(false);gestureMode=PINCH;cancelHold();cancelTap();return ready;}
            @Override public boolean onScale(ScaleGestureDetector detector){settings.scale(detector.getScaleFactor());transform();hint(String.format(Locale.ROOT,"画面 %.0f%%",settings.zoom()*100),false);return true;}
        });
        scales.setQuickScaleEnabled(false);
        texture.setOnTouchListener((v,event)->touch(event));setContentView(root);
    }
    private void immersive(){
        if(Build.VERSION.SDK_INT>=30){getWindow().setDecorFitsSystemWindows(false);WindowInsetsController controller=getWindow().getInsetsController();
            if(controller!=null){controller.hide(WindowInsets.Type.systemBars());controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);}}
        else getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }
    @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(focus)immersive();}
    @Override public void onConfigurationChanged(Configuration configuration){super.onConfigurationChanged(configuration);if(gestureMode==HORIZONTAL)finishSwipe(false);cancelHold();gestureMode=PINCH;cancelTap();settings.setZoom(1);root.post(this::transform);immersive();}
    @Override protected void onStart(){super.onStart();if(texture==null)return;foreground=true;playWanted=resumeWanted;prepare();main.removeCallbacks(tick);main.post(tick);}
    @Override protected void onPause(){
        if(texture!=null){resumeWanted=playWanted;playWanted=false;if(gestureMode==HORIZONTAL)finishSwipe(false);capturePosition();pausePlayer();cancelHold();main.removeCallbacks(hideControls);main.removeCallbacks(tick);abandonFocus();persistPosition();}
        super.onPause();
    }
    @Override protected void onStop(){foreground=false;releasePlayer();super.onStop();}
    @Override protected void onDestroy(){foreground=false;main.removeCallbacksAndMessages(null);releasePlayer();streamWorkers.shutdownNow();if(surface!=null){surface.release();surface=null;}super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle state){capturePosition();state.putInt("position",position);state.putBoolean("playing",resumeWanted);state.putFloat("speed",settings.selectedSpeed());state.putFloat("zoom",settings.zoom());super.onSaveInstanceState(state);}
    @Override public void onSurfaceTextureAvailable(SurfaceTexture value,int width,int height){surface=new Surface(value);prepare();transform();}
    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture value,int width,int height){transform();}
    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture value){capturePosition();releasePlayer();if(surface!=null){surface.release();surface=null;}return true;}
    @Override public void onSurfaceTextureUpdated(SurfaceTexture value){}
    private void loading(boolean show,String message){loading.setVisibility(show?View.VISIBLE:View.GONE);loadingText.setVisibility(show?View.VISIBLE:View.GONE);if(show&&message!=null)loadingText.setText(message);}
    private final class StreamRequest implements Runnable {
        volatile boolean cancelled;volatile WorkshopDownload download;volatile WorkshopVideoSource result;Future<?> future;
        void cancel(){cancelled=true;if(download!=null)download.cancel();if(result!=null)result.close();if(future!=null)future.cancel(true);}
        public void run(){try{
            WorkshopDownload.Progress progress=new WorkshopDownload.Progress(){public boolean cancelled(){return StreamRequest.this.cancelled||Thread.currentThread().isInterrupted();}public void update(long done,long total,String message){main.post(()->{if(opening==StreamRequest.this&&!cancelled)loading(true,message);});}};
            WorkshopVideoSource created=WorkshopVideoSource.open(app,onlineItem,progress,value->{download=value;if(cancelled)value.cancel();});result=created;
            if(cancelled){created.close();return;}
            main.post(()->{if(opening!=this||cancelled||!foreground||surface==null){created.close();return;}opening=null;result=null;stream=created;attachPlayer(null);});
        }catch(Exception|OutOfMemoryError failure){if(result!=null)result.close();if(cancelled)return;main.post(()->{if(opening!=this||!foreground)return;opening=null;streamFailure=VideoApp.error(failure);playbackError();});}}
    }
    private void prepare(){
        if(!foreground||surface==null||player!=null||opening!=null)return;
        ready=false;errorShown=false;streamFailure="";loading(true,online?"正在连接 Steam…":"正在载入…");playState("载入中");
        if(online){StreamRequest request=new StreamRequest();opening=request;request.future=streamWorkers.submit(request);return;}
        try{JSONObject record=app.job(id);if(!record.optString("state").equals("saved"))throw new IllegalStateException("视频不存在。");attachPlayer(record);}catch(Exception problem){playbackError();}
    }
    private void attachPlayer(JSONObject record){
        try {
            VideoPlayback created=new VideoPlayback(this,online);player=created;
            created.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build());
            created.setSurface(surface);
            if(online)created.setDataSource(stream);
            else if(record.optString("storage").equals("private"))created.setDataSource(app.video(id).getAbsolutePath());
            else created.setDataSource(this,Uri.parse(record.getString("uri")));
            created.setOnPreparedListener(p->{
                if(p!=player)return;ready=true;loading(false,null);duration=p.getDuration();videoWidth=p.getVideoWidth();videoHeight=p.getVideoHeight();
                if(position>=duration-500)position=0;
                if(position>0)p.seekTo(position,MediaPlayer.SEEK_CLOSEST);
                applySpeed();transform();updateProgress(position);
                if(playWanted)startPlayback();else updatePlayButton();scheduleHide();
            });
            created.setOnInfoListener((p,what,extra)->{if(p!=player)return false;if(what==MediaPlayer.MEDIA_INFO_BUFFERING_START)loading(true,"正在缓冲…");else if(what==MediaPlayer.MEDIA_INFO_BUFFERING_END||what==MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START)loading(false,null);return false;});
            created.setOnVideoSizeChangedListener((p,width,height)->{if(p==player){videoWidth=width;videoHeight=height;transform();}});
            created.setOnCompletionListener(p->{if(p==player){loading(false,null);position=duration;playWanted=false;resumeWanted=false;updatePlayButton();updateProgress(position);setControls(true);abandonFocus();}});
            created.setOnErrorListener((p,what,extra)->{if(p==player)playbackError();return true;});
            created.prepareAsync();
        }catch(Exception problem){if(online)streamFailure=VideoApp.error(problem);playbackError();}
    }
    private void playbackError(){
        ready=false;loading(false,null);playWanted=false;pausePlayer();abandonFocus();setControls(true);playState("未能播放");
        if(errorShown||!foreground||isFinishing())return;errorShown=true;
        if(online){String reason=!streamFailure.isEmpty()?streamFailure:stream!=null&&!stream.failure().isEmpty()?stream.failure():player!=null&&!player.failure().isEmpty()?player.failure():"网络缓冲中断，或此视频编码不受手机支持。";if(stream!=null)stream.close();AppSheet sheet=new AppSheet(this,"暂时无法在线播放",reason,null);sheet.footerButton("关闭",false,()->{sheet.dialog.dismiss();finish();});sheet.footerButton("重试",true,()->{sheet.dialog.dismiss();releasePlayer();playWanted=true;resumeWanted=true;prepare();});sheet.show();return;}
        new AlertDialog.Builder(this).setTitle("无法播放此视频").setMessage("视频可能已被移除，或此编码不受系统播放器支持。")
            .setPositiveButton("重试",(d,w)->{releasePlayer();playWanted=true;resumeWanted=true;prepare();})
            .setNeutralButton("外部播放器",(d,w)->openExternal()).setNegativeButton("关闭",(d,w)->finish()).show();
    }
    private void openExternal(){try{JSONObject record=app.job(id);Uri uri=Uri.parse(record.getString("uri"));Intent intent=new Intent(Intent.ACTION_VIEW).setDataAndType(uri,VideoApp.mime(record.optString("name"))).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);intent.setClipData(ClipData.newRawUri("视频",uri));startActivity(intent);}catch(Exception e){Toast.makeText(this,"没有可用的外部播放器。",Toast.LENGTH_LONG).show();}}
    private void startPlayback(){
        if(!ready||player==null||!foreground||seeking)return;
        if(!focusHeld){focusHeld=audio.requestAudioFocus(focusRequest)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED;if(!focusHeld){playWanted=false;hint("音频被其他应用占用",true);updatePlayButton();return;}}
        try{player.setVolume(1,1);player.start();playWanted=true;resumeWanted=true;getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);updatePlayButton();scheduleHide();}catch(IllegalStateException e){playbackError();}
    }
    private void togglePlay(){
        if(!ready){setControls(true);return;}cancelHold();
        if(playWanted){playWanted=false;resumeWanted=false;pausePlayer();abandonFocus();setControls(true);}
        else{try{if(position>=duration-350){position=0;player.seekTo(0,MediaPlayer.SEEK_CLOSEST);}playWanted=true;startPlayback();}catch(IllegalStateException e){playbackError();}}
        updatePlayButton();
    }
    private void pausePlayer(){if(player!=null)try{if(player.isPlaying())player.pause();}catch(IllegalStateException ignored){}getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);}
    private void updatePlayButton(){if(play!=null)playState(playWanted?"暂停":position>=duration-350&&duration>0?"重播":"播放");}
    private void capturePosition(){if(ready&&player!=null)try{position=player.getCurrentPosition();}catch(IllegalStateException ignored){}}
    private void persistPosition(){if(online||app==null||id==null)return;final int value=position;app.work.submit(()->{try{app.change(id,"playbackPosition",value);}catch(Exception ignored){}});}
    private void releasePlayer(){capturePosition();ready=false;if(opening!=null){StreamRequest old=opening;opening=null;old.cancel();}if(stream!=null){stream.close();stream=null;}if(player!=null){VideoPlayback old=player;player=null;old.setOnPreparedListener(null);old.setOnErrorListener(null);old.release();}abandonFocus();getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);}
    private void abandonFocus(){if(focusHeld&&audio!=null){audio.abandonAudioFocusRequest(focusRequest);focusHeld=false;}}
    private void audioFocusChanged(int change){
        if(change==AudioManager.AUDIOFOCUS_LOSS||change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT||change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK){
            playWanted=false;resumeWanted=false;pausePlayer();cancelHold();setControls(true);updatePlayButton();abandonFocus();
        }
    }
    private static String speedText(float value){return String.format(Locale.ROOT,"%.2f",value).replaceAll("0+$","").replaceAll("\\.$","")+"×";}
    private void chooseSpeed(){
        main.removeCallbacks(hideControls);cancelHold();float[] values={.5f,.75f,1f,1.25f,1.5f,1.75f,2f,3f};String[] labels=new String[values.length];int selected=2;
        for(int i=0;i<values.length;i++){labels[i]=speedText(values[i]);if(Math.abs(values[i]-settings.selectedSpeed())<.001)selected=i;}
        new AlertDialog.Builder(this).setTitle("播放速度").setSingleChoiceItems(labels,selected,(dialog,index)->{
            settings.setSpeed(values[index]);getSharedPreferences("player",MODE_PRIVATE).edit().putFloat("speed",settings.selectedSpeed()).apply();applySpeed();dialog.dismiss();scheduleHide();
        }).setOnDismissListener(d->scheduleHide()).setNegativeButton("取消",null).show();
    }
    private void applySpeed(){
        if(speed!=null)speed.setText(speedText(settings.speed()));if(!ready||player==null)return;
        boolean wasPlaying=false;
        try{wasPlaying=player.isPlaying();if(!wasPlaying)player.setVolume(0,0);player.setPlaybackParams(new PlaybackParams().setSpeed(settings.speed()).setPitch(1));if(!wasPlaying)player.pause();}
        catch(IllegalStateException|IllegalArgumentException e){settings.hold(false);settings.setSpeed(1);try{player.setPlaybackParams(new PlaybackParams().setSpeed(1));if(!wasPlaying)player.pause();}catch(Exception ignored){}speed.setText("1×");hint("此视频暂不支持变速",true);}
        finally{if(player!=null)try{player.setVolume(1,1);}catch(IllegalStateException ignored){}}
    }
    private void transform(){if(texture==null)return;float[] fit=PlayerControls.fit(texture.getWidth(),texture.getHeight(),videoWidth,videoHeight,settings.zoom());Matrix matrix=new Matrix();matrix.setScale(fit[0],fit[1],texture.getWidth()/2f,texture.getHeight()/2f);texture.setTransform(matrix);}
    private void updateProgress(int value){if(clock==null)return;clock.setText(PlayerControls.time(value)+" / "+PlayerControls.time(duration));if(!seeking)seek.setProgress(PlayerControls.seekProgress(value,duration,seek.getMax()));}
    private void setControls(boolean visible){if(top==null)return;top.setVisibility(visible?View.VISIBLE:View.GONE);bottom.setVisibility(visible?View.VISIBLE:View.GONE);}
    private void scheduleHide(){main.removeCallbacks(hideControls);if(foreground&&playWanted&&!seeking)main.postDelayed(hideControls,2000);}
    private void hint(String text,boolean temporary){if(hud==null)return;main.removeCallbacks(hideHud);hud.setText(text);hud.setVisibility(View.VISIBLE);if(temporary)main.postDelayed(hideHud,850);}
    private void cancelTap(){if(taps!=null){long now=SystemClock.uptimeMillis();MotionEvent cancel=MotionEvent.obtain(now,now,MotionEvent.ACTION_CANCEL,0,0,0);taps.onTouchEvent(cancel);cancel.recycle();}}
    private void cancelHold(){main.removeCallbacks(holdAction);if(settings.holding()){settings.hold(false);applySpeed();}}
    private float brightness(){
        float value=getWindow().getAttributes().screenBrightness;if(value>=0)return PlayerControls.brightness(value,0);
        int maximum=255;
        try{android.content.res.Resources system=android.content.res.Resources.getSystem();int key=system.getIdentifier("config_screenBrightnessSettingMaximum","integer","android");if(key!=0)maximum=Math.max(1,system.getInteger(key));}catch(Exception ignored){}
        return PlayerControls.brightness(Settings.System.getInt(getContentResolver(),Settings.System.SCREEN_BRIGHTNESS,maximum/2)/(float)maximum,0);
    }
    private void finishSwipe(boolean commit){
        seeking=false;
        if(ready&&player!=null)try {
            if(commit){position=swipeTarget;player.seekTo(position,MediaPlayer.SEEK_CLOSEST);}
            else position=startPosition;
            updateProgress(position);if(playWanted)startPlayback();
        }catch(IllegalStateException e){playbackError();}
    }
    private boolean touch(MotionEvent event){
        int action=event.getActionMasked();pointerCount=event.getPointerCount();
        if(action==MotionEvent.ACTION_DOWN){gestureMode=TAP;capturePosition();startPosition=position;swipeTarget=position;startX=event.getX();startY=event.getY();startBrightness=brightness();startVolume=audio.getStreamVolume(AudioManager.STREAM_MUSIC);main.removeCallbacks(hideControls);main.removeCallbacks(holdAction);main.postDelayed(holdAction,ViewConfiguration.getLongPressTimeout());}
        if(action==MotionEvent.ACTION_POINTER_DOWN){if(gestureMode==HORIZONTAL)finishSwipe(false);main.removeCallbacks(hideControls);main.removeCallbacks(hideHud);hud.setVisibility(View.GONE);gestureMode=PINCH;cancelHold();cancelTap();}
        scales.onTouchEvent(event);
        if(action==MotionEvent.ACTION_MOVE&&ready&&pointerCount==1&&gestureMode!=PINCH&&gestureMode!=HOLD){
            float dx=event.getX()-startX,dy=event.getY()-startY;
            if(Math.abs(dx)>slop||Math.abs(dy)>slop)main.removeCallbacks(holdAction);
            if(gestureMode==TAP){
                int axis=PlayerControls.swipeAxis(dx,dy,slop);
                if(axis==1){gestureMode=HORIZONTAL;cancelTap();seeking=true;try{if(player.isPlaying())player.pause();}catch(IllegalStateException ignored){}setControls(true);}
                else if(axis==2){gestureMode=startX<texture.getWidth()/2f?BRIGHTNESS:VOLUME;cancelTap();}
            }
            float change=PlayerControls.verticalChange(startY,event.getY(),texture.getHeight());
            if(gestureMode==HORIZONTAL){
                swipeTarget=PlayerControls.swipePosition(startPosition,dx,texture.getWidth(),duration);
                int delta=swipeTarget-startPosition;hint((delta<0?"后退 ":"前进 ")+Math.round(Math.abs(delta)/1000f)+" 秒\n"+PlayerControls.time(swipeTarget)+" / "+PlayerControls.time(duration),false);
                clock.setText(PlayerControls.time(swipeTarget)+" / "+PlayerControls.time(duration));seek.setProgress(PlayerControls.seekProgress(swipeTarget,duration,seek.getMax()));
            }
            else if(gestureMode==BRIGHTNESS){float value=PlayerControls.brightness(startBrightness,change);WindowManager.LayoutParams params=getWindow().getAttributes();params.screenBrightness=value;getWindow().setAttributes(params);hint("亮度 "+Math.round(value*100)+"%",false);}
            else if(gestureMode==VOLUME){int maximum=audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);int value=PlayerControls.volume(startVolume,maximum,change);try{audio.setStreamVolume(AudioManager.STREAM_MUSIC,value,0);hint("音量 "+Math.round(value*100f/Math.max(1,maximum))+"%",false);}catch(SecurityException e){hint("系统暂不允许调整音量",true);}}
        }
        if(gestureMode==TAP)taps.onTouchEvent(event);
        if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){int ended=gestureMode;if(ended==HORIZONTAL)finishSwipe(action==MotionEvent.ACTION_UP);cancelHold();pointerCount=0;gestureMode=TAP;if(ended!=TAP)main.postDelayed(hideHud,650);scheduleHide();}
        return true;
    }
}
