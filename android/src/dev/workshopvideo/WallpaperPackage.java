package dev.workshopvideo;

import dev.workshopvideo.core.*;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;

/** Normalize manifest-selected nested packages, retaining loose scene dependencies. */
final class WallpaperPackage {
    private record Source(ContentArchive archive,String name) {}
    static void normalize(File source,File output,File workspace)throws Exception {
        if(!WallpaperContent.archive(source)){try(InputStream in=new FileInputStream(source);OutputStream out=new FileOutputStream(output)){ContentArchive.transfer(in,out,WorkshopAssets.VIDEO_LIMIT);}return;}
        List<ContentArchive> opened=new ArrayList<>();List<File> temporary=new ArrayList<>();
        LinkedHashMap<String,Source> entries=new LinkedHashMap<>();long budget=WorkshopAssets.PACKAGE_LIMIT;
        try {
            ContentArchive current=new ContentArchive(source);opened.add(current);
            for(int level=0;level<4;level++) {
                for(String name:current.names())entries.put(name,new Source(current,name));
                if(entries.size()>ContentArchive.MAX_ENTRIES)throw new IOException("嵌套资源条目过多");
                if(current.has("wallpaper-mobile.json"))break;
                String project=WallpaperContent.project(current);if(project.isEmpty())break;
                JSONObject meta=WallpaperContent.json(current,project);String type=meta.optString("type").toLowerCase(Locale.ROOT);
                if(!List.of("video","image","scene").contains(type))throw new IOException("包内项目类型不受支持："+type);
                String declared=meta.optString("file");
                String main=type.equals("scene")&&declared.equals("scene.json")&&!current.has(declared)&&current.has("scene.pkg")?"scene.pkg":WallpaperContent.asset(current,declared);
                if(type.equals("scene")&&main.toLowerCase(Locale.ROOT).endsWith(".json"))break;
                File nested=File.createTempFile("nested-",".resource",workspace);temporary.add(nested);
                try(OutputStream out=new FileOutputStream(nested)){long copied=current.copy(main,out,budget);budget-=copied;}
                if(!WallpaperContent.archive(nested)) {
                    if(type.equals("scene"))throw new IOException("场景入口不是可解析的资源包或场景描述");
                    try(InputStream in=new FileInputStream(nested);OutputStream out=new FileOutputStream(output)){ContentArchive.transfer(in,out,WorkshopAssets.PACKAGE_LIMIT);}return;
                }
                if(level==3)throw new IOException("资源包嵌套超过 4 层");
                entries.remove(main);current=new ContentArchive(nested);opened.add(current);
            }
            try(ZipOutputStream zip=new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(output)))) {
                zip.setLevel(0);long written=0;
                for(var entry:entries.entrySet()) {
                    if(Thread.currentThread().isInterrupted())throw new InterruptedException("解析已暂停");
                    zip.putNextEntry(new ZipEntry(entry.getKey()));Source item=entry.getValue();written+=item.archive.copy(item.name,zip,WorkshopAssets.PACKAGE_LIMIT-written);zip.closeEntry();
                }
            }
        } finally {for(ContentArchive a:opened)a.close();for(File file:temporary)file.delete();}
    }
    private WallpaperPackage(){}
}
