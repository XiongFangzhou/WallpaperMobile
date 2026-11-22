package dev.workshopvideo.core;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;

/** Verified, recoverable storage transactions for private videos. */
public final class VideoFiles {
    public static File path(File directory,String id) {
        if(!id.matches("[0-9a-f]{32}"))throw new IllegalArgumentException("视频 ID 无效。");
        return new File(directory,id+".video");
    }
    public static String hex(byte[] bytes) {
        StringBuilder value=new StringBuilder();for(byte b:bytes)value.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return value.toString();
    }
    public static byte[] unhex(String value) {
        if(!value.matches("[0-9a-f]{40}"))throw new IllegalArgumentException("视频校验信息无效。");
        byte[] result=new byte[20];for(int i=0;i<result.length;i++)result[i]=(byte)Integer.parseInt(value.substring(i*2,i*2+2),16);return result;
    }
    public static void verify(File file,long size,byte[] sha1) throws Exception {
        if(!file.isFile()||file.length()!=size||!MessageDigest.isEqual(WorkshopDownload.hash(file,"SHA-1"),sha1))
            throw new IOException("视频完整性校验失败，原文件已保留。");
    }
    public static synchronized void promote(File source,File target,long size,byte[] sha1) throws Exception {
        if(!target.getParentFile().isDirectory()&&!target.getParentFile().mkdirs())throw new IOException("无法创建应用视频目录。");
        if(target.exists()) {verify(target,size,sha1);if(source.exists()&&!source.delete())throw new IOException("视频已保存，临时副本未能清理。");return;}
        verify(source,size,sha1);
        Files.move(source.toPath(),target.toPath());
        verify(target,size,sha1);
    }
    public static byte[] importCopy(InputStream source,File target,long expectedSize) throws Exception {
        if(!target.getParentFile().isDirectory()&&!target.getParentFile().mkdirs())throw new IOException("无法创建应用视频目录。");
        File stage=new File(target.getPath()+".importing");
        MessageDigest hash=MessageDigest.getInstance("SHA-1");long size=0;
        try {
            try(FileOutputStream output=new FileOutputStream(stage)) {
                byte[] buffer=new byte[1024*1024];int n;
                while((n=source.read(buffer))!=-1) {
                    if(Thread.currentThread().isInterrupted())throw new InterruptedException("视频迁移已暂停。");
                    size+=n;if(expectedSize>=0&&size>expectedSize)throw new IOException("原视频大小已变化，保留相册文件。");
                    output.write(buffer,0,n);hash.update(buffer,0,n);
                }
                output.getFD().sync();
            }
            if(expectedSize>=0&&size!=expectedSize)throw new IOException("原视频读取不完整，保留相册文件。");
            byte[] sha1=hash.digest();promote(stage,target,size,sha1);return sha1;
        } finally {if(stage.exists())stage.delete();}
    }
}
