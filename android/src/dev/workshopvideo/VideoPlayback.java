package dev.workshopvideo;

import android.content.Context;
import android.media.*;
import android.net.Uri;
import android.view.Surface;
import androidx.media3.common.*;
import androidx.media3.datasource.*;
import androidx.media3.exoplayer.*;
import androidx.media3.exoplayer.source.*;
import androidx.media3.exoplayer.upstream.*;
import androidx.media3.exoplayer.trackselection.*;
import java.io.*;

/** Existing local playback plus a network-aware progressive streaming backend. */
final class VideoPlayback {
 interface Prepared{void call(VideoPlayback player);}interface Info{boolean call(VideoPlayback player,int what,int extra);}interface Size{void call(VideoPlayback player,int width,int height);}interface Error{boolean call(VideoPlayback player,int what,int extra);}
 private final Context context;private final MediaPlayer local;private ExoPlayer online;private WorkshopVideoSource source;private Surface surface;
 private Prepared prepared,completed;private Info info;private Size size;private Error error;private boolean announced,released;private String failure="";
 VideoPlayback(Context context,boolean streaming){this.context=context;local=streaming?null:new MediaPlayer();}
 void setAudioAttributes(android.media.AudioAttributes attributes){if(local!=null)local.setAudioAttributes(attributes);}
 void setSurface(Surface surface){this.surface=surface;if(local!=null)local.setSurface(surface);}
 void setDataSource(WorkshopVideoSource source){this.source=source;}
 void setDataSource(String path)throws IOException {local.setDataSource(path);}
 void setDataSource(Context context,Uri uri)throws IOException {local.setDataSource(context,uri);}
 void setOnPreparedListener(Prepared value){prepared=value;if(local!=null)local.setOnPreparedListener(value==null?null:p->value.call(this));}
 void setOnInfoListener(Info value){info=value;if(local!=null)local.setOnInfoListener(value==null?null:(p,w,e)->value.call(this,w,e));}
 void setOnVideoSizeChangedListener(Size value){size=value;if(local!=null)local.setOnVideoSizeChangedListener(value==null?null:(p,w,h)->value.call(this,w,h));}
 void setOnCompletionListener(Prepared value){completed=value;if(local!=null)local.setOnCompletionListener(value==null?null:p->value.call(this));}
 void setOnErrorListener(Error value){error=value;if(local!=null)local.setOnErrorListener(value==null?null:(p,w,e)->value.call(this,w,e));}
 void prepareAsync(){
  if(local!=null){local.prepareAsync();return;}
  DefaultLoadControl load=new DefaultLoadControl.Builder().setBufferDurationsMs(8000,30000,2000,4000).setTargetBufferBytes(24*1024*1024).setPrioritizeTimeOverSizeThresholds(false).build();
  online=new ExoPlayer.Builder(context).setLoadControl(load).build();online.setVideoSurface(surface);
  online.setAudioAttributes(new androidx.media3.common.AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),false);
  online.setVideoScalingMode(C.VIDEO_SCALING_MODE_SCALE_TO_FIT);online.setSeekParameters(SeekParameters.CLOSEST_SYNC);
  online.addListener(new Player.Listener(){
   public void onPlaybackStateChanged(int state){if(released)return;
    if(state==Player.STATE_BUFFERING){if(info!=null)info.call(VideoPlayback.this,MediaPlayer.MEDIA_INFO_BUFFERING_START,0);}
    else if(state==Player.STATE_READY){if(!announced){announced=true;if(prepared!=null)prepared.call(VideoPlayback.this);}if(info!=null)info.call(VideoPlayback.this,MediaPlayer.MEDIA_INFO_BUFFERING_END,0);}
    else if(state==Player.STATE_ENDED&&completed!=null)completed.call(VideoPlayback.this);
   }
   public void onVideoSizeChanged(VideoSize dimensions){if(!released&&size!=null)size.call(VideoPlayback.this,dimensions.width,dimensions.height);}
   public void onPlayerError(PlaybackException problem){if(released)return;failure=problem.getErrorCodeName();Throwable cause=problem.getCause();if(cause!=null&&cause.getMessage()!=null)failure+=" · "+cause.getMessage();if(error!=null)error.call(VideoPlayback.this,problem.errorCode,0);}
  });
  DataSource.Factory factory=()->new WorkshopRangeDataSource(source);
  ProgressiveMediaSource media=new ProgressiveMediaSource.Factory(factory).setLoadErrorHandlingPolicy(new DefaultLoadErrorHandlingPolicy(3)).createMediaSource(new MediaItem.Builder().setUri("workshop://video/stream").setMimeType(source.mime()).build());
  online.setMediaSource(media);online.prepare();
 }
 String failure(){return failure;}
 int getDuration(){return local!=null?local.getDuration():(int)Math.max(0,Math.min(Integer.MAX_VALUE,online.getDuration()==C.TIME_UNSET?0:online.getDuration()));}
 int getCurrentPosition(){return local!=null?local.getCurrentPosition():(int)Math.min(Integer.MAX_VALUE,online.getCurrentPosition());}
 int getVideoWidth(){return local!=null?local.getVideoWidth():online.getVideoSize().width;}
 int getVideoHeight(){return local!=null?local.getVideoHeight():online.getVideoSize().height;}
 boolean isPlaying(){return local!=null?local.isPlaying():online!=null&&online.getPlayWhenReady();}
 void seekTo(long position,int mode){if(local!=null)local.seekTo(position,mode);else online.seekTo(position);}
 void start(){if(local!=null)local.start();else online.play();}
 void pause(){if(local!=null)local.pause();else if(online!=null)online.pause();}
 void setVolume(float left,float right){if(local!=null)local.setVolume(left,right);else if(online!=null)online.setVolume((left+right)/2);}
 void setPlaybackParams(PlaybackParams params){if(local!=null)local.setPlaybackParams(params);else online.setPlaybackParameters(new PlaybackParameters(params.getSpeed(),params.getPitch()));}
 void release(){released=true;if(local!=null)local.release();if(online!=null){online.release();online=null;}}
}
