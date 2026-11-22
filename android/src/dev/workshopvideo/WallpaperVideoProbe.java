package dev.workshopvideo;
import android.media.*;
import android.graphics.Bitmap;
import java.io.*;

/** Check the actual local container, decoder and a decoded frame, not just suffix. */
final class WallpaperVideoProbe {
    record Info(int width,int height,long duration,String mime){}
    static Info inspect(File file) throws Exception {
        if(!file.isFile()||file.length()==0)throw new IOException("请先完整下载视频。");
        MediaExtractor extractor=new MediaExtractor();String mime=null;
        try{extractor.setDataSource(file.getAbsolutePath());for(int i=0;i<extractor.getTrackCount();i++){MediaFormat format=extractor.getTrackFormat(i);String type=format.getString(MediaFormat.KEY_MIME);if(type!=null&&type.startsWith("video/")){
            if(new MediaCodecList(MediaCodecList.REGULAR_CODECS).findDecoderForFormat(format)==null)throw new IOException("手机没有适合这个视频编码或分辨率的解码器。");mime=type;break;
        }}}finally{extractor.release();}
        if(mime==null)throw new IOException("这个文件没有可播放的视频轨道。");
        MediaPlayer player=new MediaPlayer();int width,height;long duration;
        try{player.setDataSource(file.getAbsolutePath());player.setVolume(0,0);player.prepare();width=player.getVideoWidth();height=player.getVideoHeight();duration=player.getDuration();}finally{player.release();}
        try(MediaMetadataRetriever reader=new MediaMetadataRetriever()){reader.setDataSource(file.getAbsolutePath());Bitmap frame=reader.getScaledFrameAtTime(0,MediaMetadataRetriever.OPTION_CLOSEST_SYNC,128,128);if(frame==null)throw new IOException("无法解码视频画面，暂不能设为壁纸。");frame.recycle();String rotate=reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);if("90".equals(rotate)||"270".equals(rotate)){int swap=width;width=height;height=swap;}}
        if(width<=0||height<=0||duration<=0)throw new IOException("视频尺寸或时长无效。");return new Info(width,height,duration,mime);
    }
}
