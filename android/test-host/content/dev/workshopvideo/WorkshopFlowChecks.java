package dev.workshopvideo;
import dev.workshopvideo.core.*;import in.dragonbra.javasteam.types.*;import org.json.*;
import java.io.*;import java.nio.file.*;import java.nio.charset.StandardCharsets;import java.security.*;import java.util.*;import java.util.zip.*;
final class WorkshopFlowChecks {
 static int checks;static Path root;static byte[] png;
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);checks++;}
 static byte[] bytes(String s){return s.getBytes(StandardCharsets.UTF_8);}
 static byte[] pkg(Map<String,byte[]> entries)throws Exception {ByteArrayOutputStream table=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(table);out.writeInt(Integer.reverseBytes(8));out.write(bytes("PKGV0014"));out.writeInt(Integer.reverseBytes(entries.size()));int offset=0;for(var e:entries.entrySet()){byte[] name=bytes(e.getKey());out.writeInt(Integer.reverseBytes(name.length));out.write(name);out.writeInt(Integer.reverseBytes(offset));out.writeInt(Integer.reverseBytes(e.getValue().length));offset+=e.getValue().length;}for(byte[] value:entries.values())out.write(value);return table.toByteArray();}
 static FileData data(String name,byte[] bytes)throws Exception {FileData f=new FileData();f.setFileName(name);f.setTotalSize(bytes.length);f.setFileHash(MessageDigest.getInstance("SHA-1").digest(bytes));if(bytes.length>0)f.getChunks().add(new ChunkData(new byte[20],0,0,bytes.length+32,bytes.length));return f;}
 static File bundle(Map<String,byte[]> files,String type,String main,String id)throws Exception {
  DepotManifest manifest=new DepotManifest();for(var e:files.entrySet())manifest.getFiles().add(data(e.getKey(),e.getValue()));File project=root.resolve(id+"-project").toFile();Files.write(project.toPath(),files.get("project.json"));File bundle=root.resolve(id+"-raw").toFile();
  WorkshopAssets.download(WorkshopAssets.plan(manifest,type,main),root.resolve(id+"-parts").toFile(),project,bundle,(file,target,max)->{byte[] value=files.get(file.getFileName());Files.write(target.toPath(),value);VideoFiles.verify(target,file.getTotalSize(),file.getFileHash());});return bundle;
 }
 static JSONObject project(String type,String file)throws Exception{return new JSONObject().put("type",type).put("file",file);}
 static void run(Path path,byte[] image)throws Exception {
  root=path;png=image;
  Map<String,byte[]> inside=new LinkedHashMap<>();inside.put("scene.json",bytes("{\"general\":{\"orthogonalprojection\":{\"width\":1080,\"height\":2400}},\"objects\":[{\"image\":\"model.json\",\"origin\":\"540 1200 0\",\"size\":\"1080 2400\"}]}"));inside.put("model.json",bytes("{\"material\":\"materials/base.json\"}"));inside.put("materials/base.json",bytes("{\"passes\":[{\"shader\":\"genericimage2\",\"textures\":[\"materials/loose.png\"]}]}"));
  Map<String,byte[]> files=new LinkedHashMap<>();files.put("project.json",bytes(project("scene","scene.pkg").toString()));files.put("scene.pkg",pkg(inside));files.put("materials/loose.png",png);
  File original=bundle(files,"scene","scene.pkg","packed");File normalized=root.resolve("packed-normalized").toFile();WallpaperPackage.normalize(original,normalized,root.toFile());
  try(WallpaperContent.Scene scene=WallpaperContent.scene(normalized)){check(scene.layers.size()==1,"discovery manifest to PKGV to decoded live scene");check(scene.layers.get(0).bitmap.getWidth()==2,"loose dependency outside scene.pkg resolves");check(scene.width==1080&&scene.height==2400,"projected dimensions survive discovery flow");}
  try(ContentArchive archive=new ContentArchive(normalized)){check(!archive.has("scene.pkg")&&archive.has("scene.json"),"runtime package is flattened once rather than extracted each frame");check(Arrays.equals(archive.read("materials/loose.png",10000),png),"normalized package preserves loose image bytes");}
  check(original.isFile(),"original verified resource bundle survives normalization until commit");
  Map<String,byte[]> imagePackage=new LinkedHashMap<>();imagePackage.put("project.json",bytes(project("image","actual.png").toString()));imagePackage.put("actual.png",png);imagePackage.put("preview.jpg",bytes("wrong preview"));files.put("project.json",bytes(project("scene","wallpaper.mpkg").toString()));files.remove("scene.pkg");files.put("wallpaper.mpkg",pkg(imagePackage));
  File wrapped=bundle(files,"scene","wallpaper.mpkg","media");File selected=root.resolve("selected-image").toFile();WallpaperPackage.normalize(wrapped,selected,root.toFile());check(Arrays.equals(Files.readAllBytes(selected.toPath()),png),"discovery mpkg media selects its declared original image rather than preview");check(WallpaperContent.kind(selected).equals("image"),"downloaded mpkg can produce image saved category");
  files.clear();files.put("project.json",bytes(project("scene","scenes/main.json").toString()));files.putAll(inside);files.put("scenes/main.json",files.remove("scene.json"));files.put("materials/loose.png",png);File loose=bundle(files,"scene","scenes/main.json","loose");File looseOutput=root.resolve("loose-normalized").toFile();WallpaperPackage.normalize(loose,looseOutput,root.toFile());try(WallpaperContent.Scene scene=WallpaperContent.scene(looseOutput)){check(scene.layers.size()==1,"fully loose scene dependencies work without a scene.pkg");}
  files.put("scene.json",bytes("{\"script\":\"unrelated root scene\"}"));File selectedEntry=bundle(files,"scene","scenes/main.json","declared"),selectedEntryOutput=root.resolve("declared-normalized").toFile();WallpaperPackage.normalize(selectedEntry,selectedEntryOutput,root.toFile());try(WallpaperContent.Scene scene=WallpaperContent.scene(selectedEntryOutput)){check(scene.layers.size()==1,"project-declared scene wins over unrelated root scene.json");}files.remove("scene.json");
  JSONObject script=new JSONObject(new String(files.get("scenes/main.json"),StandardCharsets.UTF_8)).put("script","author runtime");files.put("scenes/main.json",bytes(script.toString()));File scripted=bundle(files,"scene","scenes/main.json","scripted"),scriptOutput=root.resolve("script-normalized").toFile();WallpaperPackage.normalize(scripted,scriptOutput,root.toFile());boolean rejected=false;try(WallpaperContent.Scene ignored=WallpaperContent.scene(scriptOutput)){}catch(Exception e){rejected=true;}check(rejected,"downloaded script scene remains incompatible rather than showing a fake image");
  check(scripted.isFile(),"original is temporary input before automatic download admission");
  files.remove("materials/loose.png");files.put("scenes/main.json",inside.get("scene.json"));File missing=bundle(files,"scene","scenes/main.json","missing"),missingOutput=root.resolve("missing-normalized").toFile();WallpaperPackage.normalize(missing,missingOutput,root.toFile());rejected=false;try(WallpaperContent.Scene ignored=WallpaperContent.scene(missingOutput)){}catch(Exception e){rejected=true;}check(rejected,"missing real material cannot fall back to cover art");
  // Automatic preparation is the same entry point used by the actual download service.
  File automatic=root.resolve("automatic.content").toFile();File work=root.resolve("automatic-work").toFile();work.mkdirs();
  SceneDownload.Prepared ready=SceneDownload.prepare(original,automatic,work,()->{});
  check(ready.kind().equals("scene")&&ready.adapted(),"desktop scene is automatically stored as a mobile runtime package");
  try(WallpaperContent.Scene scene=WallpaperContent.scene(automatic)){check(scene.layers.size()==1,"admitted content opens directly as wallpaper");}
  ready=SceneDownload.prepare(scripted,automatic,work,()->{});
  check(ready.adapted()&&ready.kind().equals("scene")&&ready.report().contains("脚本"),"script/effect scene automatically becomes a usable adaptation with stored limitations");
  try(ContentArchive archive=new ContentArchive(automatic)){check(archive.has("wallpaper-mobile.json")&&!archive.has("project.json")&&!archive.has("scene.pkg")&&!archive.has("scenes/main.json"),"only runtime adaptation is published, not original resources or author scripts");}
  check(work.list().length==0,"normalization and conversion scratch files are deleted after success");
  boolean denied=false;try{SceneDownload.prepare(missing,automatic,work,()->{});}catch(SceneDownload.Rejected expected){denied=true;}
  check(denied&&!automatic.exists(),"unrecoverable scene is rejected without publishing a package");
  check(work.list().length==0,"failed conversion leaves no parser or conversion scratch files");
  java.util.concurrent.atomic.AtomicInteger stops=new java.util.concurrent.atomic.AtomicInteger();denied=false;
  try{SceneDownload.prepare(original,automatic,work,()->{if(stops.incrementAndGet()>=2)throw new InterruptedException("paused");});}catch(InterruptedException expected){denied=true;}
  check(denied&&!automatic.exists()&&original.exists(),"pause during preparation publishes nothing and leaves resumable input");
  check(work.list().length==0,"paused conversion clears its own scratch files");
  ready=SceneDownload.prepare(wrapped,automatic,work,()->{});check(ready.kind().equals("image")&&!ready.adapted()&&Arrays.equals(Files.readAllBytes(automatic.toPath()),png),"scene package containing declared image publishes only real usable media");
  // A Web project must be rejected before any author resource fetch.
  Map<String,byte[]> webFiles=new LinkedHashMap<>();webFiles.put("project.json",bytes(project("web","index.html").toString()));webFiles.put("index.html",bytes("<html></html>"));DepotManifest web=new DepotManifest();for(var e:webFiles.entrySet())web.getFiles().add(data(e.getKey(),e.getValue()));denied=false;try{WorkshopAssets.plan(web,"web","index.html");}catch(IOException expected){denied=true;}check(denied,"Web type cannot reach resource download plan");
  System.out.println("PASS: "+checks+" production discovery manifest/package/parser integration checks (synthetic CDN fixtures)");
 }
}
