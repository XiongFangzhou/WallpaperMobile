package dev.workshopvideo.core;

import in.dragonbra.javasteam.types.*;
import in.dragonbra.javasteam.util.Adler32;
import java.io.*;
import java.security.*;
import java.util.*;

/** Seekable verified Steam bytes with a bounded memory LRU, never a disk download. */
public final class WorkshopRangeReader implements Closeable {
    public static final int CACHE_BYTES=32*1024*1024,MAX_READ=1024*1024;
    public interface Source extends Closeable {byte[] load(ChunkData chunk)throws Exception;}
    private final List<ChunkData> chunks=new ArrayList<>();private final Source source;private final long size;private final int capacity;
    private final Object reads=new Object(),cacheLock=new Object();
    private final LinkedHashMap<Integer,byte[]> cache=new LinkedHashMap<>(16,.75f,true);
    private final java.util.concurrent.atomic.AtomicBoolean closed=new java.util.concurrent.atomic.AtomicBoolean();private int cached;private long fetched;
    public WorkshopRangeReader(FileData file,Source source)throws IOException {this(file,source,CACHE_BYTES);}
    public WorkshopRangeReader(FileData file,Source source,int capacity)throws IOException {
        WorkshopDownload.validate(file,WorkshopAssets.VIDEO_LIMIT);if(file.getTotalSize()==0)throw new IOException("视频内容为空");
        if(capacity<=0||capacity>CACHE_BYTES)throw new IllegalArgumentException("缓存上限无效");this.capacity=capacity;this.source=source;size=file.getTotalSize();
        for(ChunkData c:file.getChunks())chunks.add(new ChunkData(c.getChunkID().clone(),c.getChecksum(),c.getOffset(),c.getCompressedLength(),c.getUncompressedLength()));
        chunks.sort(Comparator.comparingLong(ChunkData::getOffset));
    }
    public static void verify(ChunkData chunk,byte[] bytes)throws IOException {
        if(bytes==null||bytes.length!=chunk.getUncompressedLength()||Adler32.calculate(bytes)!=chunk.getChecksum())throw new IOException("视频资源块校验失败");
        try{if(chunk.getChunkID().length!=20||!MessageDigest.isEqual(MessageDigest.getInstance("SHA-1").digest(bytes),chunk.getChunkID()))throw new IOException("视频资源块 SHA-1 校验失败");}catch(NoSuchAlgorithmException e){throw new IOException(e);}
    }
    private void check()throws IOException {if(closed.get()||Thread.currentThread().isInterrupted())throw new InterruptedIOException("在线播放已停止");}
    public long size()throws IOException {check();return size;}
    public int cachedBytes(){synchronized(cacheLock){return cached;}}
    public long fetchedBytes(){synchronized(cacheLock){return fetched;}}
    public int readAt(long position,byte[] buffer,int offset,int length)throws IOException {
        if(position<0||buffer==null||offset<0||length<0||offset>buffer.length-length)throw new IOException("视频读取范围无效");
        check();if(length==0)return 0;if(position>=size)return -1;
        synchronized(reads){
            check();int wanted=(int)Math.min(Math.min(length,MAX_READ),size-position),written=0;
            while(written<wanted){check();long at=position+written;int index=index(at);ChunkData chunk=chunks.get(index);byte[] bytes;
                synchronized(cacheLock){bytes=cache.get(index);}
                if(bytes==null){
                    try{bytes=source.load(chunk);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new InterruptedIOException("在线播放已停止");}catch(IOException e){throw e;}catch(Exception e){throw new IOException("视频资源读取失败",e);}
                    check();verify(chunk,bytes);
                    synchronized(cacheLock){check();fetched+=bytes.length;
                        if(bytes.length<=capacity){while(cached>capacity-bytes.length&&!cache.isEmpty()){var first=cache.entrySet().iterator();var entry=first.next();cached-=entry.getValue().length;first.remove();}cache.put(index,bytes);cached+=bytes.length;}
                    }
                }
                int inside=(int)(at-chunk.getOffset()),count=Math.min(wanted-written,bytes.length-inside);
                System.arraycopy(bytes,inside,buffer,offset+written,count);written+=count;
            }
            check();return written;
        }
    }
    private int index(long position)throws IOException {
        int low=0,high=chunks.size()-1;
        while(low<=high){int mid=(low+high)>>>1;ChunkData chunk=chunks.get(mid);if(position<chunk.getOffset())high=mid-1;else if(position-chunk.getOffset()>=chunk.getUncompressedLength())low=mid+1;else return mid;}
        throw new IOException("视频清单缺少此位置的资源块");
    }
    @Override public void close()throws IOException {
        if(!closed.compareAndSet(false,true))return;
        synchronized(cacheLock){cache.clear();cached=0;}
        source.close();
    }
}
