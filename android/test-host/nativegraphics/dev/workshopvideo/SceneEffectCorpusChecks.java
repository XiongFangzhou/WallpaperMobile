package dev.workshopvideo;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

/** Optional: converts real workshop scenes with the bundled translator run by Node, as the phone runs it in its sandbox. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,manifest=Config.NONE,application=android.app.Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class SceneEffectCorpusChecks {
    /** Same contract as JsShaderTranslator, executed through tools/run_shader_translator.js. */
    static final class NodeTranslator implements SceneEffects.Translator {
        private final File root;NodeTranslator(File root){this.root=root;}
        public String[] translate(String vertex,String fragment,JSONObject combos,JSONObject includes)throws Exception{
            Path work=Files.createTempDirectory(Path.of(System.getProperty("java.io.tmpdir")),"translate-");
            try{
                Path request=work.resolve("request.json"),result=work.resolve("result.json");
                Files.writeString(request,new JSONArray().put(new JSONObject().put("vertex",vertex).put("fragment",fragment).put("combos",combos).put("includes",includes)).toString(),StandardCharsets.UTF_8);
                Process node=new ProcessBuilder("node",new File(root,"tools/run_shader_translator.js").getPath(),new File(root,"android/assets/we-shader-translator.js").getPath(),request.toString(),result.toString()).redirectErrorStream(true).start();
                String log=new String(node.getInputStream().readAllBytes(),StandardCharsets.UTF_8);if(node.waitFor()!=0)throw new IOException("node: "+log);
                JSONObject out=new JSONArray(Files.readString(result,StandardCharsets.UTF_8)).getJSONObject(0);if(!out.optBoolean("ok"))throw new IOException(out.optString("error"));
                return new String[]{out.getString("vertex"),out.getString("fragment")};
            }finally{try(var files=Files.walk(work)){files.sorted(Comparator.reverseOrder()).forEach(p->p.toFile().delete());}}
        }
    }
    private static File root(){File dir=new File(System.getProperty("graphics.work")).getAbsoluteFile();while(dir!=null&&!new File(dir,"tools/run_shader_translator.js").isFile())dir=dir.getParentFile();return dir;}
    private static boolean nodeAvailable(){try{return new ProcessBuilder("node","--version").start().waitFor()==0;}catch(Exception e){return false;}}

    /** 3737473202: a portrait scene that is a full-screen video texture plus tap scripts is kept as its video. */
    @Test public void videoOnlySceneBecomesVideo()throws Exception{
        String workshop=System.getenv("WVB_WORKSHOP_DIR");
        Assume.assumeTrue(workshop!=null&&!workshop.isBlank()&&new File(workshop,"3737473202/scene.pkg").isFile());
        File workspace=new File(System.getProperty("graphics.work"),"effect-corpus/video-scene");workspace.mkdirs();File target=new File(workspace,"3737473202.mp4");
        SceneDownload.Prepared prepared=SceneDownload.prepare(new File(workshop,"3737473202/scene.pkg"),target,workspace,()->{});
        assertEquals("video",prepared.kind());assertTrue(prepared.adapted());
        byte[] head=new byte[12];try(FileInputStream in=new FileInputStream(target)){assertEquals(12,in.read(head));}
        assertEquals("ftyp",new String(head,4,4,StandardCharsets.US_ASCII));assertTrue(target.length()>1_000_000);
    }
    /** 3602673806: skeletal layers draw their own meshes, play their clips, and attached hair follows the animated bone. */
    @Test public void skeletalSceneAnimates()throws Exception{
        String workshop=System.getenv("WVB_WORKSHOP_DIR");File root=root();
        Assume.assumeTrue(workshop!=null&&!workshop.isBlank()&&root!=null&&nodeAvailable()&&new File(workshop,"3602673806/scene.pkg").isFile());
        File workspace=new File(System.getProperty("graphics.work"),"effect-corpus");workspace.mkdirs();
        SceneAdapter.Report report=SceneAdapter.inspect(new File(workshop,"3602673806/scene.pkg"),workspace);
        System.out.println("3602673806: "+report.describe().replace('\n',' '));
        File adapted=new File(workspace,"3602673806-effects.zip");SceneAdapter.write(report,adapted,new NodeTranslator(root));report.scene.close();
        try(WallpaperContent.Scene scene=WallpaperContent.scene(adapted)){
            Map<String,WallpaperContent.Layer> byName=new HashMap<>();for(WallpaperContent.Layer l:scene.layers)byName.put(l.name,l);
            WallpaperContent.Layer body=byName.get("人体"),door=byName.get("门"),ponytail=byName.get("马尾"),side=byName.get("侧边"),forehead=byName.get("额头");
            assertNotNull(body);assertNotNull(door);assertNotNull(ponytail);assertNotNull(side);assertNotNull(forehead);
            assertNotNull("body draws its mesh",body.puppet);assertEquals("body plays both clips",2,body.tracks.size());assertTrue(scene.animated());
            int bodyIndex=scene.layers.indexOf(body);assertEquals(bodyIndex,ponytail.attachParent);assertEquals(bodyIndex,side.attachParent);assertEquals(bodyIndex,forehead.attachParent);
            scene.animate(0);float[] rest=body.meshPoints.clone();float restX=ponytail.x,restY=ponytail.y;
            float[] positions=body.puppet.mesh.positions;for(int v=0;v<positions.length/2;v+=97)assertEquals("rest pose keeps the stored mesh",.5f+positions[v*2]/body.puppetW,rest[v*2],.03f);
            float moved=0,ponyMoved=0;
            for(double t:new double[]{.5,1.25,2.5,3.75}){scene.animate(t);for(int v=0;v<rest.length;v++)moved=Math.max(moved,Math.abs(body.meshPoints[v]-rest[v])*(v%2==0?body.w:body.h));ponyMoved=Math.max(ponyMoved,Math.abs(ponytail.x-restX)+Math.abs(ponytail.y-restY));}
            System.out.println("3602673806 motion: body vertices move up to "+moved+" px, ponytail moves up to "+ponyMoved+" px");
            assertTrue("skeletal animation moves the mesh",moved>2&&moved<2000);
            for(double t:new double[]{0,1.25,2.5}){
                float ratio=960f/Math.max(scene.width,scene.height);android.graphics.Bitmap frame=android.graphics.Bitmap.createBitmap((int)(scene.width*ratio),(int)(scene.height*ratio),android.graphics.Bitmap.Config.ARGB_8888);
                android.graphics.Canvas c=new android.graphics.Canvas(frame);c.drawColor(android.graphics.Color.BLACK);c.scale(ratio,ratio);ScenePainter.layers(c,scene,new android.graphics.Paint(3),new JSONObject(),null,0,0,false,t);
                try(FileOutputStream out=new FileOutputStream(new File(workspace,"3602673806-t"+(int)(t*100)+".png"))){frame.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}
            }
        }
    }
    /** 3462491575: a foreground hidden through its parent's author switch stays hidden, and the switch can show it. */
    @Test public void authorSwitchesControlLayers()throws Exception{
        String workshop=System.getenv("WVB_WORKSHOP_DIR");File root=root();
        Assume.assumeTrue(workshop!=null&&!workshop.isBlank()&&root!=null&&nodeAvailable()&&new File(workshop,"3462491575/project.json").isFile());
        File workspace=new File(System.getProperty("graphics.work"),"effect-corpus");workspace.mkdirs();
        SceneAdapter.Report report=SceneAdapter.inspect(asDownloaded(new File(workshop,"3462491575"),workspace),workspace);
        assertFalse("no layer is dropped for the budget: "+report.skipped,report.skipped.keySet().stream().anyMatch(k->k.contains("预算")));
        assertTrue(report.properties.has("brcameraforeground"));
        File adapted=new File(workspace,"3462491575-switches.zip");SceneAdapter.write(report,adapted,new NodeTranslator(root));report.scene.close();
        try(WallpaperContent.Scene scene=WallpaperContent.scene(adapted)){
            WallpaperContent.Layer front=null;for(WallpaperContent.Layer l:scene.layers)if(l.name.equals("m3 前")&&l.when!=null&&l.when.toString().contains("\"0\""))front=l;
            assertNotNull(front);JSONObject options=new JSONObject();
            assertFalse("foreground is hidden by default, as on the PC",front.shown(options,scene.properties));
            options.put("user.brcameraforeground",true);assertTrue("turning the switch on shows it",front.shown(options,scene.properties));
            options.put("user.hrbbrmouseparallax","1");assertFalse("combo conditions are respected",front.shown(options,scene.properties));
        }
    }
    /** Diagnostic only: WVB_ROTATION_PROBE=id renders a full clip with both rotation signs for comparison with preview.gif. */
    @Test public void rotationProbe()throws Exception{
        String workshop=System.getenv("WVB_WORKSHOP_DIR"),id=System.getenv("WVB_ROTATION_PROBE");File root=root();
        Assume.assumeTrue(workshop!=null&&id!=null&&!id.isBlank()&&root!=null&&nodeAvailable());
        File workspace=new File(System.getProperty("graphics.work"),"rotation-probe/"+id);workspace.mkdirs();
        SceneAdapter.Report report=SceneAdapter.inspect(new File(workshop,id+"/scene.pkg"),workspace);File adapted=new File(workspace,"scene.zip");SceneAdapter.write(report,adapted,new NodeTranslator(root));report.scene.close();
        try(WallpaperContent.Scene scene=WallpaperContent.scene(adapted)){
            float ratio=384f/Math.min(scene.width,scene.height);
            for(float sign:new float[]{1,-1}){dev.workshopvideo.core.PuppetModel.rotationSign=sign;
                for(int f=0;f<150;f++){double t=f/30.0;android.graphics.Bitmap frame=android.graphics.Bitmap.createBitmap(Math.round(scene.width*ratio),Math.round(scene.height*ratio),android.graphics.Bitmap.Config.ARGB_8888);
                    android.graphics.Canvas c=new android.graphics.Canvas(frame);c.drawColor(android.graphics.Color.BLACK);c.scale(ratio,ratio);for(WallpaperContent.Layer l:scene.layers)l.pose=null;ScenePainter.layers(c,scene,new android.graphics.Paint(3),new JSONObject(),null,0,0,false,t);
                    try(FileOutputStream out=new FileOutputStream(new File(workspace,(sign>0?"p":"n")+String.format(java.util.Locale.ROOT,"%03d",f)+".png"))){frame.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}frame.recycle();}}
        }finally{dev.workshopvideo.core.PuppetModel.rotationSign=1;}
    }
    /** 3602673806: hair layers hang from the body skeleton's attachment point, about 665 px right of the body's centre. */
    @Test public void attachedChildrenFollowTheSkeleton()throws Exception{
        String workshop=System.getenv("WVB_WORKSHOP_DIR");
        Assume.assumeTrue(workshop!=null&&!workshop.isBlank()&&new File(workshop,"3602673806/scene.pkg").isFile());
        File workspace=new File(System.getProperty("graphics.work"),"effect-corpus");workspace.mkdirs();
        SceneAdapter.Report report=SceneAdapter.inspect(new File(workshop,"3602673806/scene.pkg"),workspace);
        try{
            WallpaperContent.Layer body=null,ponytail=null,side=null;for(WallpaperContent.Layer l:report.scene.layers){if(l.name.equals("人体"))body=l;if(l.name.equals("马尾"))ponytail=l;if(l.name.equals("侧边"))side=l;}
            assertNotNull(body);assertNotNull(ponytail);assertNotNull(side);
            float bodyX=body.x+body.w/2,bodyY=body.y+body.h/2,anchorX=bodyX+675.4f*.985f,anchorY=bodyY-41.6f*.985f;
            assertEquals("side hair has no origin, so it sits on the attachment",anchorX,side.x+side.w/2,2);assertEquals(anchorY,side.y+side.h/2,2);
            assertEquals("ponytail is offset from the attachment",anchorX+723.61298f*.985f,ponytail.x+ponytail.w/2,2);assertEquals(anchorY+139.63585f*.985f,ponytail.y+ponytail.h/2,2);
        }finally{report.scene.close();}
    }
    /** Diagnostic only: WVB_SCENE_IDS=id,id prints each scene's conversion report and writes its poster. */
    @Test public void diagnoseRequestedScenes()throws Exception{
        String workshop=System.getenv("WVB_WORKSHOP_DIR"),ids=System.getenv("WVB_SCENE_IDS");File root=root();
        Assume.assumeTrue(workshop!=null&&ids!=null&&!ids.isBlank()&&root!=null&&nodeAvailable());
        File workspace=new File(System.getProperty("graphics.work"),"effect-corpus");workspace.mkdirs();
        for(String id:ids.split(",")){
            File pkg=new File(workshop,id.trim()+"/scene.pkg");if(!pkg.isFile()){System.out.println(id+": no scene.pkg");continue;}
            pkg=asDownloaded(new File(workshop,id.trim()),workspace);
            try{
                SceneAdapter.Report report=SceneAdapter.inspect(pkg,workspace);System.out.println(id+": usable="+report.usable()+" error="+report.error+" | "+report.describe().replace('\n',' '));
                if(report.usable()){File adapted=new File(workspace,id+"-effects.zip");SceneAdapter.write(report,adapted,new NodeTranslator(root));
                    try(WallpaperContent.Scene scene=WallpaperContent.scene(adapted)){android.graphics.Bitmap poster=ScenePainter.poster(scene,960);try(FileOutputStream out=new FileOutputStream(new File(workspace,id+".png"))){poster.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}}}
                report.scene.close();
            }catch(Exception e){System.out.println(id+": failed "+e);}
        }
    }
    /** The item folder as the phone downloads it: project.json (author properties) next to scene.pkg, in one archive. */
    static File asDownloaded(File folder,File workspace)throws IOException{
        File project=new File(folder,"project.json"),pkg=new File(folder,"scene.pkg");if(!project.isFile())return pkg;
        File out=new File(workspace,folder.getName()+"-download.zip");
        try(java.util.zip.ZipOutputStream zip=new java.util.zip.ZipOutputStream(new FileOutputStream(out))){
            for(File f:new File[]{project,pkg}){java.util.zip.ZipEntry e=new java.util.zip.ZipEntry(f.getName());zip.putNextEntry(e);Files.copy(f.toPath(),zip);zip.closeEntry();}
        }
        return out;
    }
    @Test public void realCharacterAndLandscapeEffectsSurviveConversion()throws Exception{
        String workshop=System.getenv("WVB_WORKSHOP_DIR");File root=root();
        Assume.assumeTrue("Set WVB_WORKSHOP_DIR and install Node to convert real workshop effects",workshop!=null&&!workshop.isBlank()&&root!=null&&nodeAvailable());
        Map<String,String> expected=new LinkedHashMap<>();expected.put("2262867800","shake");expected.put("3213046325","shake");expected.put("1962375063","waterflow");expected.put("1132505365","waterripple");
        File workspace=new File(System.getProperty("graphics.work"),"effect-corpus");workspace.mkdirs();int converted=0;
        for(Map.Entry<String,String> sample:expected.entrySet()){
            File folder=new File(workshop,sample.getKey());File pkg=new File(folder,"scene.pkg");Assume.assumeTrue(pkg.isFile());
            SceneAdapter.Report report=SceneAdapter.inspect(pkg,workspace);
            if(!report.usable()){System.out.println(sample.getKey()+" not convertible: "+report.error);report.scene.close();continue;}
            converted++;File adapted=new File(workspace,sample.getKey()+"-effects.zip");SceneAdapter.write(report,adapted,new NodeTranslator(root));report.scene.close();
            String notes=report.describe();System.out.println(sample.getKey()+": "+notes.replace('\n',' '));
            assertTrue(sample.getKey()+" keeps "+sample.getValue()+": "+notes,report.keptEffects.containsKey(sample.getValue()));
            try(WallpaperContent.Scene scene=WallpaperContent.scene(adapted)){
                int effects=0;for(WallpaperContent.Layer layer:scene.layers)for(SceneEffects.Effect effect:layer.effects){effects++;assertTrue(effect.vertex.startsWith("#version 300 es"));assertTrue(effect.fragment.contains("void main"));}
                assertTrue(sample.getKey()+" stores effects",effects>0);assertTrue(scene.animated());
            }
        }
        assertEquals("character scenes convert once unsupported X-ray is dropped",expected.size(),converted);
    }
}
