package dev.workshopvideo;

import android.graphics.*;
import android.graphics.drawable.*;
import android.os.*;
import android.util.LruCache;
import android.widget.ImageView;
import java.io.*;
import java.lang.ref.WeakReference;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.*;
import java.util.function.Consumer;
import dev.workshopvideo.core.PreviewFormat;

/** Cache original bytes so GIF and animated WebP never get flattened to JPEG. */
public final class PreviewImages {
    private final File directory;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService workers=Executors.newFixedThreadPool(3);
    private final ConcurrentHashMap<String,Object> locks=new ConcurrentHashMap<>();
    private final LruCache<String,byte[]> cache=new LruCache<String,byte[]>(12*1024*1024){@Override protected int sizeOf(String key,byte[] bytes){return bytes.length;}};
    public PreviewImages(File root){directory=new File(root,"preview-media");directory.mkdirs();}
    public void load(ImageView view,String url){load(view,url,null);}
    public void load(ImageView view,String url,Runnable fallback){
        if(url==null||url.isEmpty()){if(fallback!=null)fallback.run();return;}
        view.setTag(url);WeakReference<ImageView> target=new WeakReference<>(view);
        workers.submit(()->{
            Drawable drawable=null;
            try {
                byte[] raw=bytes(url);
                drawable=ImageDecoder.decodeDrawable(ImageDecoder.createSource(ByteBuffer.wrap(raw)),(decoder,info,source)->{
                    int width=info.getSize().getWidth(),height=info.getSize().getHeight();
                    if(width<=0||height<=0)throw new IllegalArgumentException("预览尺寸无效。");
                    float scale=Math.min(1f,720f/Math.max(width,height));decoder.setTargetSize(Math.max(1,Math.round(width*scale)),Math.max(1,Math.round(height*scale)));
                });
                if(drawable instanceof AnimatedImageDrawable)((AnimatedImageDrawable)drawable).setRepeatCount(AnimatedImageDrawable.REPEAT_INFINITE);
            }catch(Exception|OutOfMemoryError ignored){}
            Drawable result=drawable;
            main.post(()->{ImageView image=target.get();if(image==null||!url.equals(image.getTag()))return;if(result!=null)image.setImageDrawable(result);else if(fallback!=null)fallback.run();});
        });
    }
    private byte[] bytes(String url) throws Exception {
        if(!PreviewFormat.allowedUrl(url))throw new IOException("预览地址无效。");
        byte[] existing=cache.get(url);if(existing!=null)return existing;
        String name=hex(MessageDigest.getInstance("SHA-256").digest(url.getBytes(StandardCharsets.UTF_8)));
        synchronized(locks.computeIfAbsent(name,key->new Object())){
            existing=cache.get(url);if(existing!=null)return existing;
            File file=new File(directory,name+".media");
            if(file.isFile()&&file.length()>0&&file.length()<=PreviewFormat.MAX_BYTES) {
                try(InputStream input=new FileInputStream(file)){existing=PreviewFormat.read(input);}
            }else {
                existing=download(url);
                File stage=new File(file.getPath()+".tmp");
                try{try(FileOutputStream output=new FileOutputStream(stage)){output.write(existing);}if(!stage.renameTo(file))stage.delete();}finally{stage.delete();}
            }
            cache.put(url,existing);return existing;
        }
    }
    private byte[] download(String address) throws Exception {
        for(int redirects=0;redirects<5;redirects++){
            if(!PreviewFormat.allowedUrl(address))throw new IOException("预览地址无效。");
            HttpURLConnection connection=(HttpURLConnection)new URL(address).openConnection();
            connection.setConnectTimeout(10000);connection.setReadTimeout(15000);connection.setInstanceFollowRedirects(false);
            try{
                int status=connection.getResponseCode();
                if(status==301||status==302||status==303||status==307||status==308){
                    String location=connection.getHeaderField("Location");
                    if(location==null)throw new IOException("预览重定向无效。");
                    address=new URL(new URL(address),location).toString();continue;
                }
                if(status!=200)throw new IOException("预览响应："+status);
                try(InputStream input=connection.getInputStream()){return PreviewFormat.read(input);}
            }finally{connection.disconnect();}
        }
        throw new IOException("预览重定向过多。");
    }
    private static String hex(byte[] bytes){StringBuilder text=new StringBuilder();for(byte value:bytes)text.append(String.format(java.util.Locale.ROOT,"%02x",value&255));return text.toString();}
}
