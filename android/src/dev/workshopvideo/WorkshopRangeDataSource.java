package dev.workshopvideo;
import android.net.Uri;import androidx.media3.common.C;import androidx.media3.datasource.*;import java.io.*;
/** Each Media3 seek/retry reopens a transfer while retaining the session and verified memory chunks. */
final class WorkshopRangeDataSource extends BaseDataSource {
  interface Reader {long getSize()throws IOException;int readAt(long position,byte[] buffer,int offset,int size)throws IOException;}
  private final Reader source;private Uri uri;private long at,remaining;private boolean opened;
  WorkshopRangeDataSource(Reader source){super(true);this.source=source;}
  public long open(DataSpec spec)throws IOException {transferInitializing(spec);long size=source.getSize();if(spec.position>size)throw new IOException("读取超出视频长度");uri=spec.uri;at=spec.position;remaining=spec.length==C.LENGTH_UNSET?size-at:Math.min(spec.length,size-at);opened=true;transferStarted(spec);return remaining;}
  public int read(byte[] buffer,int offset,int length)throws IOException {if(length==0)return 0;if(remaining==0)return C.RESULT_END_OF_INPUT;int count=source.readAt(at,buffer,offset,(int)Math.min(length,remaining));if(count>0){at+=count;remaining-=count;bytesTransferred(count);}return count;}
  public Uri getUri(){return uri;}
  public void close(){uri=null;if(opened){opened=false;transferEnded();}}
 }
