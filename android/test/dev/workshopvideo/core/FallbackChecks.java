package dev.workshopvideo.core;
import static dev.workshopvideo.core.CoreChecks.*;
import java.io.*;import java.nio.charset.StandardCharsets;import java.nio.file.*;import java.util.zip.*;
final class FallbackChecks {
    static void run()throws Exception{
        Path work=Files.createTempDirectory(Path.of(System.getProperty("java.io.tmpdir")),"fallback-");
        try{
            File pkg=work.resolve("scene.zip").toFile();
            try(ZipOutputStream zip=new ZipOutputStream(new FileOutputStream(pkg))){zip.putNextEntry(new ZipEntry("scene.json"));zip.write("{}".getBytes(StandardCharsets.UTF_8));zip.closeEntry();zip.putNextEntry(new ZipEntry("models/util/solidlayer.json"));zip.write("package".getBytes(StandardCharsets.UTF_8));zip.closeEntry();}
            Path engine=work.resolve("engine");Files.createDirectories(engine.resolve("models/util"));
            Files.writeString(engine.resolve("models/util/solidlayer.json"),"engine");Files.writeString(engine.resolve("models/util/composelayer.json"),"{\"passthrough\":true}");
            try(ContentArchive a=new ContentArchive(pkg)){
                check(!a.has("models/util/composelayer.json"),"without a fallback, engine files are missing");
                a.fallback(engine.toFile());
                check(a.has("models/util/composelayer.json")&&a.fromFallback("models/util/composelayer.json"),"engine assets fill in files the package lacks");
                check(new String(a.read("models/util/composelayer.json",1024),StandardCharsets.UTF_8).contains("passthrough"),"fallback files are readable");
                check(new String(a.read("models/util/solidlayer.json",1024),StandardCharsets.UTF_8).equals("package")&&!a.fromFallback("models/util/solidlayer.json"),"the package wins over engine assets");
                check(!a.has("../engine/models/util/composelayer.json")&&!a.names().contains("models/util/composelayer.json"),"fallback paths stay inside the engine folder and out of the package listing");
            }
        }finally{try(var files=Files.walk(work)){files.sorted(java.util.Comparator.reverseOrder()).forEach(p->p.toFile().delete());}}
    }
}
