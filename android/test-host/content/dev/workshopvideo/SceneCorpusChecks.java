package dev.workshopvideo;
import java.io.*;import java.nio.file.*;import java.util.*;import java.util.zip.*;import org.json.*;import dev.workshopvideo.core.*;
/** Local private corpus only: no author's assets are included in shipping sources/APK. */
public class SceneCorpusChecks {
 public static void main(String[] args)throws Exception {
  String samples=System.getenv("WVB_WORKSHOP_DIR");if(samples==null||samples.isBlank())throw new IllegalArgumentException("Set WVB_WORKSHOP_DIR to the optional workshop sample directory");
  Path output=Path.of(args[0]);Files.createDirectories(output);JSONArray records=new JSONArray();
  String[] ids={"1962375063","1888636115","1132505365","3483582101","3713073223","3601075812"};
  for(String id:ids){File pkg=new File(samples,id+"/scene.pkg");if(!pkg.isFile())throw new IOException("Missing authorized sample "+id);
   // Exercise the actual downloaded desktop bundle structure, including project.json -> scene.json inside scene.pkg.
   File bundle=output.resolve(id+"-source.zip").toFile();try(ZipOutputStream zip=new ZipOutputStream(new FileOutputStream(bundle))){for(String name:new String[]{"project.json","scene.pkg"}){zip.putNextEntry(new ZipEntry(name));Files.copy(pkg.toPath().getParent().resolve(name),zip);zip.closeEntry();}}
   long start=System.nanoTime();SceneAdapter.Report r=SceneAdapter.inspect(bundle,output.toFile());
   try{JSONObject item=new JSONObject().put("id",id).put("objects",r.objects).put("images",r.images).put("recovered",r.scene.layers.size()).put("usable",r.usable()).put("report",r.describe());
    if(r.usable()){File adapted=output.resolve(id+"-adapted.zip").toFile();SceneAdapter.write(r,adapted);try(WallpaperContent.Scene check=WallpaperContent.scene(adapted)){if(check.layers.size()!=r.scene.layers.size())throw new AssertionError("Converted layer mismatch");long pixels=0;for(var l:check.layers)pixels+=(long)l.bitmap.getWidth()*l.bitmap.getHeight();item.put("decodedPixels",pixels).put("adaptedBytes",adapted.length());}}
    File automatic=output.resolve(id+"-automatic.content").toFile();File work=output.resolve(id+"-automatic-work").toFile();work.mkdirs();SceneDownload.Prepared admitted=SceneDownload.prepare(bundle,automatic,work,()->{});try(WallpaperContent.Scene verified=WallpaperContent.scene(automatic)){if(!admitted.adapted()||verified.layers.size()!=r.scene.layers.size())throw new AssertionError("Automatic admission does not match decoded recovery");item.put("automaticAdmission",true);}if(work.list().length!=0)throw new AssertionError("Original conversion scratch retained");work.delete();
    item.put("milliseconds",(System.nanoTime()-start)/1000000);records.put(item);System.out.println(id+" recovered="+r.scene.layers.size()+" / "+r.images+" usable="+r.usable()+"\n"+r.describe());
   }finally{r.scene.close();bundle.delete();}
  }
  Files.writeString(output.resolve("results.json"),records.toString(2));
  if(records.getJSONObject(0).getInt("recovered")!=1||records.getJSONObject(1).getInt("recovered")!=1||records.getJSONObject(2).getInt("recovered")!=1)throw new AssertionError("Simple corpus background recovery regression");
  System.out.println("PASS: six real workshop packages normalized, inspected and adaptation validated using production parser");
 }
}
