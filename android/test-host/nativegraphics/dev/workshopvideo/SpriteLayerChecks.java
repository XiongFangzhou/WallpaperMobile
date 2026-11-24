package dev.workshopvideo;

import android.graphics.*;
import dev.workshopvideo.core.ContentArchive;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.zip.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

/** Imported GIF layers (TEX sprite sheets) survive adaptation and animate in the Canvas path. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,manifest=Config.NONE,application=android.app.Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class SpriteLayerChecks {
    /** 4x2 RGBA atlas: left 2x2 red, right 2x2 green; two 0.5 s frames. */
    private static byte[] spriteTexture(){
        ByteBuffer b=ByteBuffer.allocate(512).order(ByteOrder.LITTLE_ENDIAN);
        b.put("TEXV0005\0TEXI0001\0".getBytes(StandardCharsets.US_ASCII));for(int n:new int[]{0,4|2,4,2,4,2,0})b.putInt(n);
        b.put("TEXB0003\0".getBytes(StandardCharsets.US_ASCII));for(int n:new int[]{1,0,1,4,2,0,0,32})b.putInt(n);
        for(int y=0;y<2;y++)for(int x=0;x<4;x++){boolean red=x<2;b.put((byte)(red?255:0)).put((byte)(red?0:255)).put((byte)0).put((byte)255);}
        b.put("TEXS0003\0".getBytes(StandardCharsets.US_ASCII));b.putInt(2);b.putInt(2);b.putInt(2);
        for(int frame=0;frame<2;frame++){b.putInt(0);b.putFloat(.5f);b.putFloat(frame*2);b.putFloat(0);b.putFloat(2);b.putFloat(0);b.putFloat(0);b.putFloat(2);}
        byte[] out=new byte[b.position()];b.flip();b.get(out);return out;
    }
    private static void put(ZipOutputStream zip,String name,byte[] data)throws IOException{zip.putNextEntry(new ZipEntry(name));zip.write(data);zip.closeEntry();}
    private static File workshopScene(File dir)throws Exception{
        File file=new File(dir,"sprite-scene.zip");
        JSONObject object=new JSONObject().put("image","models/bird.json").put("name","飞鸟").put("visible",true).put("origin","100 50 0").put("size","200 100").put("scale","1 1 1").put("angles","0 0 0");
        JSONObject scene=new JSONObject().put("general",new JSONObject().put("orthogonalprojection",new JSONObject().put("width",200).put("height",100))).put("objects",new JSONArray().put(object));
        try(ZipOutputStream zip=new ZipOutputStream(new FileOutputStream(file))){
            put(zip,"scene.json",scene.toString().getBytes(StandardCharsets.UTF_8));
            put(zip,"models/bird.json",new JSONObject().put("material","materials/bird.json").toString().getBytes(StandardCharsets.UTF_8));
            put(zip,"materials/bird.json",new JSONObject().put("passes",new JSONArray().put(new JSONObject().put("shader","genericimage2").put("textures",new JSONArray().put("bird")))).toString().getBytes(StandardCharsets.UTF_8));
            put(zip,"materials/bird.tex",spriteTexture());
        }
        return file;
    }
    private static int centerAt(WallpaperContent.Scene scene,double seconds){
        Bitmap out=Bitmap.createBitmap(200,100,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(out);c.drawColor(Color.BLACK);
        Paint paint=new Paint();ScenePainter.layers(c,scene,paint,new JSONObject(),null,0,0,false,seconds);int color=out.getPixel(100,50);out.recycle();return color;
    }
    @Test public void spriteLayerIsKeptSerializedAndAnimated()throws Exception{
        File dir=new File(System.getProperty("graphics.work"),"sprite");dir.mkdirs();
        SceneAdapter.Report report;try(ContentArchive archive=new ContentArchive(workshopScene(dir))){report=SceneAdapter.inspect(archive);}
        assertTrue(report.describe(),report.usable());assertEquals(1,report.animations);assertTrue(report.describe().contains("帧动画"));
        File adapted=new File(dir,"adapted.zip");SceneAdapter.write(report,adapted);report.scene.close();
        try(WallpaperContent.Scene scene=WallpaperContent.scene(adapted)){
            assertTrue(scene.animated());assertEquals(2,scene.layers.get(0).frames.size());
            int first=centerAt(scene,0),second=centerAt(scene,.6),looped=centerAt(scene,1.1);
            assertTrue("first frame red: "+Integer.toHexString(first),Color.red(first)>200&&Color.green(first)<60);
            assertTrue("second frame green: "+Integer.toHexString(second),Color.green(second)>200&&Color.red(second)<60);
            assertEquals("animation loops",first,looped);
        }
    }
}
