package dev.workshopvideo.core;

import in.dragonbra.javasteam.types.*;
import in.dragonbra.javasteam.enums.EDepotFileFlag;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

/** Complete scene dependencies, original media bytes and resumable verified CDN files. */
public final class WorkshopAssets {
    public static final long PACKAGE_LIMIT=2L*1024*1024*1024,VIDEO_LIMIT=128L*1024*1024*1024;
    public record Plan(String type,String main,List<FileData> files,long total,boolean bundle) {}
    public interface Fetch {
        void file(FileData file,File target,long limit)throws Exception;
        default void check()throws Exception {if(Thread.currentThread().isInterrupted())throw new InterruptedException("下载已暂停");}
    }
    public static Plan plan(DepotManifest manifest,String type,String main)throws IOException {
        type=type.toLowerCase(Locale.ROOT);main=ContentArchive.path(main);
        if(!List.of("video","image","scene").contains(type))throw new IOException("当前不支持 "+type+" 类型的运行环境，仅支持视频、图片和可解析场景。");
        boolean bundle=type.equals("scene");Map<String,FileData> index=new LinkedHashMap<>();
        if(manifest.getFiles().size()>ContentArchive.MAX_ENTRIES)throw new IOException("作品文件数量超出限制");
        for(FileData f:manifest.getFiles()) {
            if(f.getFlags().contains(EDepotFileFlag.Directory))continue;
            String name=ContentArchive.path(f.getFileName());
            if(index.putIfAbsent(name,f)!=null)throw new IOException("作品包含重复文件路径");
        }
        // Desktop Scene project.json names scene.json inside the canonical scene.pkg.
        if(bundle&&!index.containsKey(main)&&main.equals("scene.json")&&index.containsKey("scene.pkg"))main="scene.pkg";
        if(!index.containsKey("project.json")||!index.containsKey(main))throw new IOException("作品描述引用的文件不在 Steam 清单中："+main);
        List<FileData> files=new ArrayList<>();files.add(index.get("project.json"));
        if(bundle){for(var e:index.entrySet())if(!e.getKey().equals("project.json"))files.add(e.getValue());}
        else if(!main.equals("project.json"))files.add(index.get(main));
        long limit=type.equals("video")?VIDEO_LIMIT:PACKAGE_LIMIT,total=0;
        for(FileData f:files){WorkshopDownload.validate(f,limit);if(f.getFileHash().length!=20)throw new IOException("资源缺少 SHA-1 校验");if(f.getTotalSize()>limit-total)throw new IOException("作品大小超出当前格式的下载限制");total+=f.getTotalSize();}
        return new Plan(type,main,List.copyOf(files),total,bundle);
    }
    public static File part(File workspace,FileData file)throws Exception {
        String name=ContentArchive.path(file.getFileName());
        String hash=VideoFiles.hex(MessageDigest.getInstance("SHA-1").digest(name.getBytes(StandardCharsets.UTF_8)));
        return new File(workspace,hash+"-"+VideoFiles.hex(file.getFileHash())+".part");
    }
    public static long remaining(Plan plan,File workspace)throws Exception {
        long remaining=0;for(FileData file:plan.files)remaining+=Math.max(0,file.getTotalSize()-part(workspace,file).length());return remaining;
    }
    public static void download(Plan plan,File workspace,File project,File output,Fetch fetch)throws Exception {
        if(!workspace.isDirectory()&&!workspace.mkdirs())throw new IOException("无法创建下载暂存目录");
        if(!plan.bundle){
            FileData metadata=plan.files.get(0);VideoFiles.verify(project,metadata.getTotalSize(),metadata.getFileHash());
            FileData main=plan.files.stream().filter(f->normalizedName(f).equals(plan.main)).findFirst().orElseThrow();
            fetch.check();fetch.file(main,output,main.getTotalSize());return;
        }
        Map<String,File> ready=new LinkedHashMap<>();
        for(FileData file:plan.files){fetch.check();String name=ContentArchive.path(file.getFileName());File target=part(workspace,file);
            if(name.equals("project.json")){VideoFiles.verify(project,file.getTotalSize(),file.getFileHash());ready.put(name,project);}
            else {fetch.file(file,target,file.getTotalSize());ready.put(name,target);}
        }
        fetch.check();
        try(ZipOutputStream zip=new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(output)))){
            zip.setLevel(0);
            for(var entry:ready.entrySet()){fetch.check();zip.putNextEntry(new ZipEntry(entry.getKey()));try(InputStream in=new FileInputStream(entry.getValue())){copy(in,zip,fetch);}zip.closeEntry();}
        }
    }
    private static String normalizedName(FileData file){return file.getFileName().replace('\\','/');}
    private static void copy(InputStream in,OutputStream out,Fetch fetch)throws Exception {
        byte[] buffer=new byte[1024*1024];int n;while((n=in.read(buffer))!=-1){fetch.check();out.write(buffer,0,n);}
    }
    /** Workspace is a flat directory of opaque filenames, never author-controlled paths. */
    public static void clear(File workspace)throws IOException {
        File[] files=workspace.listFiles();if(files==null)return;
        for(File file:files){if(file.isDirectory()||!file.delete())throw new IOException("未能清理资源下载暂存文件");}
        if(!workspace.delete())throw new IOException("未能清理资源下载暂存目录");
    }
    private WorkshopAssets(){}
}
