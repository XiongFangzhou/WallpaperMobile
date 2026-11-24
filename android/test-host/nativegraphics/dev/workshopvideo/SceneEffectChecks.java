package dev.workshopvideo;

import android.graphics.*;
import dev.workshopvideo.core.ContentArchive;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

/** Author layer effects keep their package shaders, material values and textures through conversion. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,manifest=Config.NONE,application=android.app.Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class SceneEffectChecks {
    private static final String VERTEX="uniform mat4 g_ModelViewProjectionMatrix;\nuniform vec2 g_Bounds; // {\"material\":\"bounds\",\"default\":\"0 1\"}\nattribute vec3 a_Position;\nattribute vec2 a_TexCoord;\nvarying vec2 v_TexCoord;\nvoid main(){gl_Position=mul(vec4(a_Position,1.0),g_ModelViewProjectionMatrix);v_TexCoord=a_TexCoord;}\n";
    private static final String FRAGMENT="#include \"common.h\"\nuniform sampler2D g_Texture0; // {\"hidden\":true}\nuniform sampler2D g_Texture1; // {\"material\":\"mask\",\"combo\":\"MASK\",\"default\":\"util/white\"}\nuniform sampler2D g_Texture2; // {\"material\":\"noise\",\"default\":\"util/noise\"}\nuniform float g_Time;\nuniform float g_Speed; // {\"material\":\"speed\",\"default\":1}\nuniform float g_Amp; // {\"material\":\"strength\",\"default\":0.3}\nvarying vec2 v_TexCoord;\nvoid main(){gl_FragColor=texSample2D(g_Texture0,v_TexCoord);}\n";

    private static byte[] png(int color)throws IOException{Bitmap b=Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888);b.eraseColor(color);ByteArrayOutputStream out=new ByteArrayOutputStream();b.compress(Bitmap.CompressFormat.PNG,100,out);b.recycle();return out.toByteArray();}
    private static void put(ZipOutputStream zip,String name,byte[] data)throws IOException{zip.putNextEntry(new ZipEntry(name));zip.write(data);zip.closeEntry();}
    private static byte[] json(JSONObject value){return value.toString().getBytes(StandardCharsets.UTF_8);}
    private static File scene(File dir,boolean withMask)throws Exception{
        File file=new File(dir,"effect-scene-"+withMask+".zip");
        JSONObject instance=new JSONObject().put("file","effects/sway/effect.json").put("name","Hair").put("visible",true)
            .put("passes",new JSONArray().put(new JSONObject().put("constantshadervalues",new JSONObject().put("Speed",2.5)).put("textures",withMask?new JSONArray().put(JSONObject.NULL).put("materials/masks/hair.png"):new JSONArray())));
        JSONObject object=new JSONObject().put("image","models/art.json").put("name","人物").put("visible",true).put("origin","100 50 0").put("size","200 100").put("scale","1 1 1").put("angles","0 0 0").put("effects",new JSONArray().put(instance));
        JSONObject root=new JSONObject().put("general",new JSONObject().put("orthogonalprojection",new JSONObject().put("width",200).put("height",100))).put("objects",new JSONArray().put(object));
        try(ZipOutputStream zip=new ZipOutputStream(new FileOutputStream(file))){
            put(zip,"scene.json",json(root));
            put(zip,"models/art.json",json(new JSONObject().put("material","materials/art.json")));
            put(zip,"materials/art.json",json(new JSONObject().put("passes",new JSONArray().put(new JSONObject().put("shader","genericimage2").put("textures",new JSONArray().put("materials/art.png"))))));
            put(zip,"materials/art.png",png(Color.RED));
            put(zip,"materials/masks/hair.png",png(Color.WHITE));
            put(zip,"effects/sway/effect.json",json(new JSONObject().put("passes",new JSONArray().put(new JSONObject().put("material","materials/effects/sway.json")))));
            put(zip,"materials/effects/sway.json",json(new JSONObject().put("passes",new JSONArray().put(new JSONObject().put("shader","effects/sway").put("blending","normal")))));
            put(zip,"shaders/effects/sway.vert",VERTEX.getBytes(StandardCharsets.UTF_8));
            put(zip,"shaders/effects/sway.frag",FRAGMENT.getBytes(StandardCharsets.UTF_8));
        }
        return file;
    }
    private static SceneAdapter.Report inspect(File file)throws Exception{try(ContentArchive archive=new ContentArchive(file)){return SceneAdapter.inspect(archive);}}

    @Test public void effectsKeepMaterialValuesTexturesAndCombos()throws Exception{
        File dir=new File(System.getProperty("graphics.work"),"effects");dir.mkdirs();
        SceneAdapter.Report report=inspect(scene(dir,true));
        assertTrue(report.describe(),report.usable());assertEquals(Integer.valueOf(1),report.keptEffects.get("sway"));
        AtomicReference<JSONObject> combos=new AtomicReference<>();AtomicReference<String> fragmentSource=new AtomicReference<>();
        SceneEffects.Translator fake=(vertex,fragment,c,includes)->{combos.set(c);fragmentSource.set(fragment);return new String[]{"#version 300 es\n// v\nvoid main(){}","#version 300 es\n// f\nvoid main(){}"};};
        File adapted=new File(dir,"adapted.zip");SceneAdapter.write(report,adapted,fake);report.scene.close();
        assertEquals("bound mask switches its combo on",1,combos.get().optInt("MASK"));assertTrue(fragmentSource.get().contains("g_Amp"));
        assertTrue(report.describe(),report.describe().contains("保留作者特效：sway"));
        try(WallpaperContent.Scene scene=WallpaperContent.scene(adapted)){
            assertTrue(scene.animated());SceneEffects.Effect effect=scene.layers.get(0).effects.get(0);
            assertEquals("#version 300 es\n// f\nvoid main(){}",effect.fragment);
            assertEquals("instance value overrides the default, case-insensitively",2.5f,effect.uniforms.get("g_Speed")[0],0);
            assertEquals("unset value falls back to the annotation default",.3f,effect.uniforms.get("g_Amp")[0],1e-6);
            assertArrayEquals(new float[]{0,1},effect.uniforms.get("g_Bounds"),0);
            assertNull(effect.textures.get(0));assertTrue("bound mask is stored",effect.textures.get(1) instanceof Bitmap);assertEquals("util/noise",effect.textures.get(2));
        }
    }
    @Test public void unboundMaskUsesItsDefaultAndKeepsTheComboOff()throws Exception{
        File dir=new File(System.getProperty("graphics.work"),"effects");dir.mkdirs();
        SceneAdapter.Report report=inspect(scene(dir,false));AtomicReference<JSONObject> combos=new AtomicReference<>();
        File adapted=new File(dir,"adapted-default.zip");SceneAdapter.write(report,adapted,(v,f,c,i)->{combos.set(c);return new String[]{"#version 300 es\nvoid main(){}","#version 300 es\nvoid main(){}"};});report.scene.close();
        assertFalse(combos.get().has("MASK"));
        try(WallpaperContent.Scene scene=WallpaperContent.scene(adapted)){assertEquals("util/white",scene.layers.get(0).effects.get(0).textures.get(1));}
    }
    @Test public void withoutTranslatorOnlyTheEffectIsDropped()throws Exception{
        File dir=new File(System.getProperty("graphics.work"),"effects");dir.mkdirs();
        SceneAdapter.Report report=inspect(scene(dir,true));File adapted=new File(dir,"adapted-plain.zip");SceneAdapter.write(report,adapted,null);report.scene.close();
        assertTrue(report.keptEffects.isEmpty());assertTrue(report.describe(),report.describe().contains("sway（此设备不支持特效转换）"));
        try(WallpaperContent.Scene scene=WallpaperContent.scene(adapted)){assertEquals(1,scene.layers.size());assertTrue(scene.layers.get(0).effects.isEmpty());assertFalse(scene.animated());}
    }
    @Test public void rejectedTranslationKeepsTheLayer()throws Exception{
        File dir=new File(System.getProperty("graphics.work"),"effects");dir.mkdirs();
        SceneAdapter.Report report=inspect(scene(dir,true));File adapted=new File(dir,"adapted-rejected.zip");
        SceneAdapter.write(report,adapted,(v,f,c,i)->{throw new IOException("语法不支持");});report.scene.close();
        assertTrue(report.describe(),report.describe().contains("sway（语法不支持）"));
        try(WallpaperContent.Scene scene=WallpaperContent.scene(adapted)){assertEquals(1,scene.layers.size());assertTrue(scene.layers.get(0).effects.isEmpty());}
    }
}
