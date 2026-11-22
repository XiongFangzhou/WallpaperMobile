package dev.workshopvideo.core;

import java.io.*;
import java.util.Arrays;
import static dev.workshopvideo.core.CoreChecks.*;

/** Original preview bytes must survive caching, including animated formats. */
final class PreviewChecks {
    static void run() throws Exception {
        check(PreviewFormat.allowedUrl("https://images.steamusercontent.com/ugc/123/456/"),"current preview CDN");
        check(PreviewFormat.allowedUrl("https://steamuserimages-a.akamaihd.net/ugc/123/456/?format=gif"),"legacy preview CDN");
        check(!PreviewFormat.allowedUrl("http://images.steamusercontent.com/ugc/1"),"reject cleartext preview redirect");
        check(!PreviewFormat.allowedUrl("https://images.steamusercontent.com.evil.example/"),"reject misleading CDN suffix");
        check(!PreviewFormat.allowedUrl("https://user:password@images.steamusercontent.com/"),"reject preview credentials");
        byte[] gif=java.util.Base64.getDecoder().decode("R0lGODlhAQABAIAAAAAAAP///yH5BAAAAAAALAAAAAABAAEAAAIBRAA7");
        check(Arrays.equals(gif,PreviewFormat.read(new ByteArrayInputStream(gif))),"preserve original GIF bytes");
        rejects(()->PreviewFormat.read(new ByteArrayInputStream(new byte[0])),"empty preview");
        InputStream oversized=new InputStream(){
            int remaining=PreviewFormat.MAX_BYTES+1;
            public int read(){return remaining-->0?0:-1;}
            public int read(byte[] b,int offset,int size){if(remaining==0)return -1;int n=Math.min(size,remaining);Arrays.fill(b,offset,offset+n,(byte)0);remaining-=n;return n;}
        };
        rejects(()->PreviewFormat.read(oversized),"oversized animated preview");
    }
}
