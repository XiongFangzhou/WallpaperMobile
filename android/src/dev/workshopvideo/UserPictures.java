package dev.workshopvideo;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.net.Uri;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.security.MessageDigest;

/**
 * Pictures chosen on the phone for scenetexture author settings (Wallpaper Engine's "pick an image" setting): a private
 * PNG copy, at most 2048 px on its long side, named by its SHA-1 under files/user-textures. The setting stores that path;
 * WallpaperContent.Scene.picture only accepts files of this form.
 */
final class UserPictures {
    private static final int MAX_EDGE=2048;
    private UserPictures(){}

    static File directory(Context context){File dir=new File(context.getFilesDir(),"user-textures");dir.mkdirs();return dir;}

    /** Decodes, scales and stores the picture; returns the stored file. Runs off the UI thread. */
    static File importPicture(Context context,Uri uri)throws Exception {
        Bitmap bitmap=ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.getContentResolver(),uri),(decoder,info,source)->{
            int w=info.getSize().getWidth(),h=info.getSize().getHeight();float k=Math.min(1,MAX_EDGE/(float)Math.max(w,h));
            if(k<1)decoder.setTargetSize(Math.max(1,Math.round(w*k)),Math.max(1,Math.round(h*k)));
            decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
        });
        try{
            ByteArrayOutputStream png=new ByteArrayOutputStream();if(!bitmap.compress(Bitmap.CompressFormat.PNG,100,png))throw new IllegalStateException("无法保存图片。");
            byte[] data=png.toByteArray();StringBuilder name=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-1").digest(data))name.append(String.format(java.util.Locale.ROOT,"%02x",b));
            File target=new File(directory(context),name+".png");
            if(!target.isFile()){File stage=new File(target.getPath()+".tmp");try(FileOutputStream out=new FileOutputStream(stage)){out.write(data);}if(!stage.renameTo(target)){stage.delete();throw new IllegalStateException("无法保存图片。");}}
            return target;
        }finally{bitmap.recycle();}
    }
}
