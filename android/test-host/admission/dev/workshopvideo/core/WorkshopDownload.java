package dev.workshopvideo.core;
import java.io.*;import java.nio.file.*;import java.security.*;import java.util.*;import in.dragonbra.javasteam.types.*;
/** Only Steam transport is substituted. Real service, archive parser and SceneDownload are under test. */
public class WorkshopDownload {
 public interface Progress{void update(long done,long total,String message);boolean cancelled();}
 public static Map<String,byte[]> payload=new LinkedHashMap<>();public static int resourceReads;
 public WorkshopDownload(SteamSession unused){}public void cancel(){}
 public DepotManifest manifest(long id,Progress p)throws Exception{DepotManifest m=new DepotManifest();for(var e:payload.entrySet()){FileData f=new FileData();f.setFileName(e.getKey());f.setTotalSize(e.getValue().length);f.setFileHash(MessageDigest.getInstance("SHA-1").digest(e.getValue()));if(e.getValue().length>0)f.getChunks().add(new ChunkData(new byte[20],0,0,e.getValue().length+32,e.getValue().length));m.getFiles().add(f);}return m;}
 public static FileData find(DepotManifest m,String name)throws IOException{return m.getFiles().stream().filter(f->f.getFileName().equals(name)).findFirst().orElseThrow(()->new IOException("missing"));}
 public static void validate(FileData f,long max)throws IOException{if(f.getTotalSize()>max)throw new IOException("size");}
 public void file(FileData f,File out,long limit)throws Exception{if(!f.getFileName().equals("project.json"))resourceReads++;byte[] b=payload.get(f.getFileName());if(b.length>limit)throw new IOException("limit");Files.write(out.toPath(),b);VideoFiles.verify(out,f.getTotalSize(),f.getFileHash());}
 public static byte[] hash(File f,String algorithm)throws Exception{return MessageDigest.getInstance(algorithm).digest(Files.readAllBytes(f.toPath()));}
}