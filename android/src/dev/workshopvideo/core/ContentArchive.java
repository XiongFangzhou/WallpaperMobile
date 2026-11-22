package dev.workshopvideo.core;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

/** Read-only bounded ZIP / WE PKGV / PKGM directory reader. Never extracts paths to disk. */
public final class ContentArchive implements AutoCloseable {
    public record Entry(String name,long offset,long length){}
    private RandomAccessFile file;private ZipFile zip;private final LinkedHashMap<String,Entry> entries=new LinkedHashMap<>();
    public static final int MAX_ENTRIES=20000,MAX_BLOCK=32*1024*1024;
    public ContentArchive(File source)throws IOException {
        try{
            file=new RandomAccessFile(source,"r");int signature=file.readInt();file.seek(0);
            if(signature==0x504b0304||signature==0x504b0506){zip=new ZipFile(source);Enumeration<? extends ZipEntry> all=zip.entries();while(all.hasMoreElements()){ZipEntry e=all.nextElement();if(!e.isDirectory())add(new Entry(path(e.getName()),0,e.getSize()));}return;}
            int size=le();if(size<8||size>32)throw new IOException("不支持的资源包头部");byte[] magic=new byte[size];file.readFully(magic);String version=new String(magic,StandardCharsets.US_ASCII);if(!version.matches("PKG[VM][0-9]{4}"))throw new IOException("不支持的资源包版本："+version);
            int count=le();if(count<=0||count>MAX_ENTRIES)throw new IOException("资源包条目数无效");List<Entry> table=new ArrayList<>();
            for(int i=0;i<count;i++){int n=le();if(n<=0||n>1024)throw new IOException("资源路径长度无效");byte[] name=new byte[n];file.readFully(name);table.add(new Entry(path(new String(name,StandardCharsets.UTF_8)),Integer.toUnsignedLong(le()),Integer.toUnsignedLong(le())));}
            long base=file.getFilePointer(),length=file.length();List<Entry> sorted=new ArrayList<>();
            for(Entry e:table){if(e.offset>length-base||e.length>length-base-e.offset)throw new IOException("资源包条目越界");Entry absolute=new Entry(e.name,base+e.offset,e.length);Entry prior=entries.get(absolute.name);if(prior==null)add(absolute);else if(!samePacked(prior,absolute))throw new IOException("资源包同名条目内容冲突");sorted.add(absolute);}
            sorted.sort(Comparator.comparingLong(Entry::offset));long last=base;for(Entry e:sorted){if(e.length>0&&e.offset<last)throw new IOException("资源包条目重叠");last=Math.max(last,e.offset+e.length);}
        }catch(IOException|RuntimeException error){close();throw error;}
    }
    // Real PKGV0023 can contain byte-identical repeated font assets. ZIP duplicates remain rejected.
    private boolean samePacked(Entry first,Entry second)throws IOException {
        if(first.length!=second.length||first.length>MAX_BLOCK)return false;
        byte[] a=new byte[65536],b=new byte[65536];long at=0;
        while(at<first.length){int n=(int)Math.min(a.length,first.length-at);file.seek(first.offset+at);file.readFully(a,0,n);file.seek(second.offset+at);file.readFully(b,0,n);for(int i=0;i<n;i++)if(a[i]!=b[i])return false;at+=n;}
        return true;
    }
    private void add(Entry e)throws IOException{if(entries.size()>=MAX_ENTRIES||entries.putIfAbsent(e.name,e)!=null)throw new IOException("资源包条目重复或过多");}
    private int le()throws IOException{return Integer.reverseBytes(file.readInt());}
    private long u64()throws IOException{long lo=Integer.toUnsignedLong(le()),hi=Integer.toUnsignedLong(le());if(hi>0x7fffffffL)throw new IOException("资源长度溢出");return lo+(hi<<32);}
    public static String path(String value)throws IOException {String name=value.replace('\\','/');if(name.isEmpty()||name.startsWith("/")||name.contains(":")||name.indexOf(0)>=0)throw new IOException("资源路径无效");for(String bit:name.split("/",-1))if(bit.isEmpty()||bit.equals("..")||bit.equals("."))throw new IOException("资源路径越界");return name;}
    public Set<String> names(){return Collections.unmodifiableSet(entries.keySet());}
    public boolean has(String name){return entries.containsKey(name);}
    private long compressedSize(Entry e)throws IOException{
        if(e.length<16)return -1;file.seek(e.offset);long lo=Integer.toUnsignedLong(le()),hi=Integer.toUnsignedLong(le());if(hi!=0)return -1;long expected=lo;if(expected<=e.length||expected>2L*1024*1024*1024)return -1;long written=0,end=e.offset+e.length;
        while(written<expected){if(file.getFilePointer()>end-8)return -1;int unc=le(),comp=le();if(unc<=0||comp<=0||unc>MAX_BLOCK||comp>MAX_BLOCK||file.getFilePointer()>end-comp)return -1;written+=unc;file.seek(file.getFilePointer()+comp);}
        return written==expected&&file.getFilePointer()==end?expected:-1;
    }
    public synchronized byte[] read(String name,int limit)throws IOException {ByteArrayOutputStream out=new ByteArrayOutputStream();copy(name,out,limit);return out.toByteArray();}
    public synchronized long copy(String name,OutputStream out,long limit)throws IOException {
        Entry e=entries.get(name);if(e==null)throw new FileNotFoundException(name);
        if(zip!=null){ZipEntry z=zip.getEntry(name);if(z==null){for(Enumeration<? extends ZipEntry> all=zip.entries();all.hasMoreElements();){ZipEntry item=all.nextElement();if(!item.isDirectory()&&path(item.getName()).equals(name)){z=item;break;}}}if(z==null)throw new FileNotFoundException(name);try(InputStream in=zip.getInputStream(z)){return transfer(in,out,limit);}}
        long original=compressedSize(e);if(original>limit||original<0&&e.length>limit)throw new IOException("资源超过允许大小");
        file.seek(e.offset);if(original<0){long left=e.length;byte[] block=new byte[65536];while(left>0){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("解析已停止");int n=(int)Math.min(left,block.length);file.readFully(block,0,n);out.write(block,0,n);left-=n;}return e.length;}
        u64();long total=0;while(total<original){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("解析已停止");int unc=le(),comp=le();byte[] input=new byte[comp];file.readFully(input);byte[] data=lz4(input,unc);out.write(data);total+=data.length;}return total;
    }
    public static long transfer(InputStream in,OutputStream out,long max)throws IOException {long total=0;byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("解析已停止");total+=n;if(total>max)throw new IOException("资源超过允许大小");out.write(b,0,n);}return total;}
    public static byte[] lz4(byte[] src,int size)throws IOException {
        if(size<0||size>MAX_BLOCK)throw new IOException("解压块过大");byte[] dst=new byte[size];int i=0,o=0;
        while(i<src.length){int token=src[i++]&255,n=token>>>4;if(n==15){int x;do{if(i>=src.length)throw new IOException("LZ4 文本截断");x=src[i++]&255;n+=x;}while(x==255);}
            if(n>src.length-i||n>size-o)throw new IOException("LZ4 文本越界");System.arraycopy(src,i,dst,o,n);i+=n;o+=n;if(i==src.length)break;
            if(i+2>src.length)throw new IOException("LZ4 偏移截断");int offset=(src[i++]&255)|((src[i++]&255)<<8);if(offset<=0||offset>o)throw new IOException("LZ4 偏移无效");n=token&15;if(n==15){int x;do{if(i>=src.length)throw new IOException("LZ4 匹配截断");x=src[i++]&255;n+=x;}while(x==255);}n+=4;if(n>size-o)throw new IOException("LZ4 匹配越界");for(int k=0;k<n;k++){dst[o]=dst[o-offset];o++;}
        }if(o!=size)throw new IOException("LZ4 长度不匹配");return dst;
    }
    public void close(){if(zip!=null)try{zip.close();}catch(Exception ignored){}if(file!=null)try{file.close();}catch(Exception ignored){}}
}
