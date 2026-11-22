package dev.workshopvideo.core;
import in.dragonbra.javasteam.types.*;import in.dragonbra.javasteam.enums.EDepotFileFlag;
import java.io.*;import java.nio.file.*;import java.util.*;import java.security.MessageDigest;
import static dev.workshopvideo.core.CoreChecks.*;
final class WorkshopAssetChecks {
 static FileData file(String name,byte[] bytes)throws Exception{FileData f=new FileData();f.setFileName(name);f.setFileHash(MessageDigest.getInstance("SHA-1").digest(bytes));f.setTotalSize(bytes.length);if(bytes.length>0)f.getChunks().add(new ChunkData(new byte[20],0,0,bytes.length+32,bytes.length));return f;}
 static void run()throws Exception {
  DepotManifest m=new DepotManifest();byte[] p="metadata".getBytes(),v="original media".getBytes();m.getFiles().add(file("project.json",p));m.getFiles().add(file("scene.pkg",v));m.getFiles().add(file("materials/loose.png",v));
  FileData dir=new FileData();dir.setFileName("materials/");dir.setFlags(EnumSet.of(EDepotFileFlag.Directory));m.getFiles().add(dir);
  var scene=WorkshopAssets.plan(m,"Scene","scene.pkg");check(scene.bundle()&&scene.files().size()==3,"scene plan keeps packed and loose dependencies and skips directories");check(scene.total()==p.length+2*v.length,"scene aggregate transfer size");
  check(WorkshopAssets.plan(m,"Scene","scene.json").main().equals("scene.pkg"),"real desktop scene.json entry resolves canonical scene.pkg manifest file");
  var media=WorkshopAssets.plan(m,"Video","scene.pkg");check(!media.bundle()&&media.files().size()==2,"media plan selects declared file without unrelated resources");
  rejects(()->WorkshopAssets.plan(m,"Scene","missing.pkg"),"missing scene entry");rejects(()->WorkshopAssets.plan(m,"Web","scene.pkg"),"unsupported web runtime");rejects(()->WorkshopAssets.plan(m,"Application","scene.pkg"),"unsupported application runtime");
  FileData duplicate=file("materials\\loose.png",v);m.getFiles().add(duplicate);rejects(()->WorkshopAssets.plan(m,"scene","scene.pkg"),"normalized duplicate manifest paths");m.getFiles().remove(duplicate);
  FileData unsafe=file("../escape.png",v);m.getFiles().add(unsafe);rejects(()->WorkshopAssets.plan(m,"scene","scene.pkg"),"scene dependency traversal");m.getFiles().remove(unsafe);
  FileData link=file("link.png",v);link.setLinkTarget("/outside");m.getFiles().add(link);rejects(()->WorkshopAssets.plan(m,"scene","scene.pkg"),"scene link dependency");m.getFiles().remove(link);
  FileData large=file("large",v);large.setTotalSize(WorkshopAssets.PACKAGE_LIMIT+1);m.getFiles().add(large);rejects(()->WorkshopAssets.plan(m,"scene","scene.pkg"),"aggregate scene size budget");m.getFiles().remove(large);
  Path root=Files.createTempDirectory("workshop-assets-");try {
   File project=root.resolve("project.json").toFile();Files.write(project.toPath(),p);File parts=root.resolve("parts").toFile(),bundle=root.resolve("bundle").toFile();
   check(WorkshopAssets.remaining(scene,parts)==scene.total(),"remaining bytes before transfer");
   WorkshopAssets.Fetch fetch=(f,target,limit)->{check(target.getParentFile().equals(parts),"author paths never become local filesystem paths");Files.write(target.toPath(),v);};
   WorkshopAssets.download(scene,parts,project,bundle,fetch);
   try(ContentArchive a=new ContentArchive(bundle)){check(a.names().equals(Set.of("project.json","scene.pkg","materials/loose.png")),"scene download bundles the actual manifest dependencies");check(Arrays.equals(a.read("project.json",100),p),"project metadata preserved exactly");check(Arrays.equals(a.read("materials/loose.png",100),v),"loose dependency bytes preserved");}
   check(WorkshopAssets.remaining(scene,parts)==p.length,"existing resource chunks reduce remaining download bytes");
   File raw=root.resolve("raw").toFile();WorkshopAssets.download(media,parts,project,raw,(f,target,limit)->{check(target.equals(raw),"single media downloads directly into resumable partial");Files.write(target.toPath(),v);});check(Arrays.equals(Files.readAllBytes(raw.toPath()),v),"single media output preserves exact original");
   Files.write(project.toPath(),v);rejects(()->WorkshopAssets.download(scene,parts,project,bundle,fetch),"changed project cannot select another resource after download");
   WorkshopAssets.clear(parts);check(!parts.exists(),"completed/discarded scene staging is removed");
  }finally{try(var paths=Files.walk(root)){for(Path path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}}
 }
}
