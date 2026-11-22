package dev.workshopvideo.core;

import java.io.*;
import java.net.URI;

public final class PreviewFormat {
    public static final int MAX_BYTES=20*1024*1024;
    public static boolean allowedUrl(String value){
        try {URI uri=new URI(value);String host=uri.getHost();return "https".equals(uri.getScheme())&&uri.getUserInfo()==null&&(uri.getPort()==-1||uri.getPort()==443)&&host!=null&&(host.endsWith(".steamusercontent.com")||host.endsWith(".steamstatic.com")||host.endsWith(".akamaihd.net"));}
        catch(Exception e){return false;}
    }
    public static byte[] read(InputStream input) throws IOException {
        ByteArrayOutputStream output=new ByteArrayOutputStream();byte[] buffer=new byte[16384];int n;
        while((n=input.read(buffer))!=-1){if(n>MAX_BYTES-output.size())throw new IOException("预览超过大小限制。");output.write(buffer,0,n);}
        if(output.size()==0)throw new IOException("预览内容为空。");return output.toByteArray();
    }
}
