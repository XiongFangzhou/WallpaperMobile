package dev.workshopvideo.core;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

final class VideoFilesChecks {
    static void run() throws Exception {
        Path directory=Files.createTempDirectory("wallpaper-private-videos-");
        try {
            byte[] bytes="original-video-bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            byte[] sha1=MessageDigest.getInstance("SHA-1").digest(bytes);
            File original=directory.resolve("gallery-original.mp4").toFile();Files.write(original.toPath(),bytes);
            File target=VideoFiles.path(directory.resolve("private").toFile(),"0123456789abcdef0123456789abcdef");
            byte[] imported;
            try(InputStream input=new FileInputStream(original)){imported=VideoFiles.importCopy(input,target,bytes.length);}
            CoreChecks.check(Arrays.equals(imported,sha1)&&Arrays.equals(Files.readAllBytes(target.toPath()),bytes),"private import preserves content");
            CoreChecks.check(original.exists()&&Arrays.equals(Files.readAllBytes(original.toPath()),bytes),"migration copy retains source until caller commits");
            CoreChecks.check(!new File(target.getPath()+".importing").exists(),"successful migration removes staging file");
            VideoFiles.importCopy(new ByteArrayInputStream(bytes),target,bytes.length);
            CoreChecks.check(target.length()==bytes.length,"migration retry reuses verified private file");
            CoreChecks.rejects(()->VideoFiles.importCopy(new ByteArrayInputStream(new byte[bytes.length]),target,bytes.length),"do not overwrite conflicting saved video");
            CoreChecks.check(Arrays.equals(Files.readAllBytes(target.toPath()),bytes),"existing video survives rejected overwrite");
            File interrupted=VideoFiles.path(target.getParentFile(),"1123456789abcdef0123456789abcdef");
            InputStream broken=new InputStream(){int n=0;public int read() throws IOException {if(n++>=3)throw new IOException("disconnected");return 1;}};
            CoreChecks.rejects(()->VideoFiles.importCopy(broken,interrupted,bytes.length),"interrupted migration");
            CoreChecks.check(!interrupted.exists()&&!new File(interrupted.getPath()+".importing").exists()&&original.exists(),"failed import preserves original and removes unfinished copy");
            CoreChecks.rejects(()->VideoFiles.importCopy(new ByteArrayInputStream(bytes),interrupted,bytes.length+1),"truncated gallery read");
            File partial=directory.resolve("download.partial").toFile();Files.write(partial.toPath(),bytes);
            VideoFiles.promote(partial,interrupted,bytes.length,sha1);
            CoreChecks.check(!partial.exists()&&interrupted.exists(),"verified download moves into private storage");
            VideoFiles.promote(partial,interrupted,bytes.length,sha1);
            CoreChecks.check(interrupted.exists(),"recover save after move but before job commit");
            File invalid=directory.resolve("invalid.partial").toFile();Files.write(invalid.toPath(),new byte[bytes.length]);
            File destination=VideoFiles.path(target.getParentFile(),"2123456789abcdef0123456789abcdef");
            CoreChecks.rejects(()->VideoFiles.promote(invalid,destination,bytes.length,sha1),"corrupt download is not published");
            CoreChecks.check(invalid.exists()&&!destination.exists(),"failed promotion retains resumable download");
            CoreChecks.rejects(()->VideoFiles.path(target.getParentFile(),"../../secret"),"private path traversal");
            CoreChecks.check(Arrays.equals(VideoFiles.unhex(VideoFiles.hex(sha1)),sha1),"persisted checksum roundtrip");
        } finally {
            try(var paths=Files.walk(directory)){for(Path path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}
        }
    }
}
