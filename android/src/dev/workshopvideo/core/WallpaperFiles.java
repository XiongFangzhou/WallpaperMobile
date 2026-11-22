package dev.workshopvideo.core;
import java.io.*;
import java.util.*;

/** Wallpaper copies have their own lifetime, separate from downloaded library files. */
public final class WallpaperFiles {
    public static File path(File root,String name){if(name==null||!name.matches("[0-9a-f]{32}\\.video"))throw new IllegalArgumentException("没有可用的壁纸视频。");return new File(root,name);}
    public static File copy(File root,File source) throws Exception {
        if(!source.isFile()||source.length()==0)throw new IOException("请先完整下载视频。");
        root.mkdirs();if(root.getUsableSpace()<source.length()+32L*1024*1024)throw new IOException("设置壁纸需要额外的视频副本，请先释放空间。");
        File target=path(root,UUID.randomUUID().toString().replace("-","")+".video");
        try(InputStream input=new FileInputStream(source)){VideoFiles.importCopy(input,target,source.length());}
        return target;
    }
    public static void cleanup(File root,Set<String> retained){File[] files=root.listFiles();if(files!=null)for(File file:files)if(file.isFile()&&file.getName().matches("[0-9a-f]{32}\\.video")&&!retained.contains(file.getName()))file.delete();}
}
