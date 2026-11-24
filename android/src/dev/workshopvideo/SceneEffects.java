package dev.workshopvideo;

import android.graphics.Bitmap;
import dev.workshopvideo.core.ContentArchive;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;
import org.json.*;

/** Author layer effects run from the shaders bundled in the workshop package.
 *  Conversion keeps the original sources, material values and textures; the vendored
 *  we-scene translator turns them into GLSL ES 3.00 once, before the scene is saved. */
final class SceneEffects {
    /** Wallpaper Engine HLSL dialect to GLSL ES 3.00; returns {vertex, fragment}. */
    interface Translator {String[] translate(String vertex,String fragment,JSONObject combos,JSONObject includes)throws Exception;}

    /** One effect pass on a layer. Slot 0 is the previous result; other slots hold a Bitmap or an engine texture name. */
    static final class Effect {
        final String name;String vertex,fragment;final JSONObject combos;final List<Object> textures;final Map<String,float[]> uniforms;
        // Original sources until translation; dropped afterwards to keep memory low.
        String sourceVertex,sourceFragment;JSONObject includes;
        // Runtime state owned by SceneGpu on its GL thread.
        int program;boolean failed;
        Effect(String name,String vertex,String fragment,JSONObject combos,List<Object> textures,Map<String,float[]> uniforms){this.name=name;this.vertex=vertex;this.fragment=fragment;this.combos=combos;this.textures=textures;this.uniforms=uniforms;}
        void recycle(){for(Object texture:textures)if(texture instanceof Bitmap bitmap&&!bitmap.isRecycled())bitmap.recycle();}
    }

    /** Engine textures that never ship in packages; SceneGpu synthesizes them. */
    static final Set<String> ENGINE_TEXTURES=Set.of("util/white","util/black","util/noise","util/noflow","util/clouds_256");
    private static final Pattern ANNOTATED=Pattern.compile("uniform\\s+(\\w+)\\s+(\\w+)\\s*;\\s*//\\s*(\\{[^\\n]*\\})");
    private static final Pattern TEXTURE_SLOT=Pattern.compile("g_Texture(\\d+)");
    private static final int MAX_EDGE=1024,MAX_SLOTS=8;

    static String kind(JSONObject instance){return instance.optString("file").replace("effects/","").replace("/effect.json","");}

    /** Shader headers the author bundled under shaders/, keyed the way #include names them. */
    static JSONObject includes(ContentArchive archive)throws Exception {
        JSONObject result=new JSONObject();
        for(String name:archive.names())if(name.startsWith("shaders/")&&name.endsWith(".h"))result.put(name.substring(8),stripBom(new String(archive.read(name,512*1024),StandardCharsets.UTF_8)));
        return result;
    }

    /** Reads one effect instance; throws with a user-readable reason when it cannot run on the phone. */
    static Effect read(ContentArchive archive,JSONObject instance,JSONObject includes)throws Exception {
        JSONObject effect=WallpaperContent.json(archive,ContentArchive.path(instance.getString("file")));
        JSONArray passes=effect.optJSONArray("passes");
        if(passes==null||passes.length()!=1)throw new IOException("多通道特效尚不支持");
        JSONObject pass=passes.getJSONObject(0);
        if(pass.length()!=1||!pass.has("material"))throw new IOException("依赖其他画面的特效尚不支持");
        JSONObject material=WallpaperContent.json(archive,ContentArchive.path(pass.getString("material"))).getJSONArray("passes").getJSONObject(0);
        String blending=material.optString("blending","normal");if(!blending.equals("normal")&&!blending.isEmpty())throw new IOException("特效混合方式尚不支持");
        String shader=material.getString("shader");
        String vertex=text(archive,"shaders/"+shader+".vert"),fragment=text(archive,"shaders/"+shader+".frag");
        JSONObject override=instance.optJSONArray("passes")==null||instance.getJSONArray("passes").length()==0?new JSONObject():instance.getJSONArray("passes").getJSONObject(0);

        // Texture slots: material defaults, then the instance's own textures, then annotation defaults.
        Object[] names=new Object[MAX_SLOTS];boolean[] bound=new boolean[MAX_SLOTS];
        fill(names,bound,material.optJSONArray("textures"));fill(names,bound,override.optJSONArray("textures"));
        Map<Integer,JSONObject> slotMeta=new HashMap<>();Map<String,JSONObject> uniformMeta=new LinkedHashMap<>();Map<String,String> types=new HashMap<>();
        for(String source:new String[]{vertex,fragment}){
            Matcher m=ANNOTATED.matcher(source);
            while(m.find()){JSONObject meta;try{meta=new JSONObject(m.group(3));}catch(JSONException ignored){continue;}
                Matcher slot=TEXTURE_SLOT.matcher(m.group(2));if(slot.matches())slotMeta.put(Integer.parseInt(slot.group(1)),meta);else{uniformMeta.put(m.group(2),meta);types.put(m.group(2),m.group(1));}}
        }
        JSONObject combos=new JSONObject();copy(material.optJSONObject("combos"),combos);
        for(Map.Entry<Integer,JSONObject> slot:slotMeta.entrySet()){
            int index=slot.getKey();if(index<1||index>=MAX_SLOTS)continue;String combo=slot.getValue().optString("combo");
            if(bound[index]&&!combo.isEmpty())combos.put(combo,1);
            if(names[index]==null&&!slot.getValue().optString("default").isEmpty())names[index]=slot.getValue().optString("default");
        }
        copy(override.optJSONObject("combos"),combos);

        List<Object> textures=new ArrayList<>();textures.add(null);int last=0;for(int i=1;i<MAX_SLOTS;i++)if(names[i]!=null)last=i;
        try{
            for(int i=1;i<=last;i++){
                Object name=names[i];
                if(name==null){textures.add("util/black");continue;}
                String value=String.valueOf(name);
                if(ENGINE_TEXTURES.contains(value)){textures.add(value);continue;}
                if(value.startsWith("util/")||value.startsWith("_rt_"))throw new IOException("特效依赖引擎贴图 "+value);
                textures.add(scaled(WallpaperContent.bitmap(archive.read(WallpaperContent.asset(archive,value),32*1024*1024))));
            }
        }catch(Exception|OutOfMemoryError failure){for(Object t:textures)if(t instanceof Bitmap b)b.recycle();throw failure;}

        // Material values bind to uniforms through the shader annotations' "material" names.
        JSONObject constants=new JSONObject();copy(material.optJSONObject("constantshadervalues"),constants);copy(override.optJSONObject("constantshadervalues"),constants);
        Map<String,float[]> uniforms=new LinkedHashMap<>();
        for(Map.Entry<String,JSONObject> entry:uniformMeta.entrySet()){
            JSONObject meta=entry.getValue();Object value=lookup(constants,meta.optString("material"));if(value==null)value=meta.opt("default");
            float[] parsed=numbers(value,width(types.get(entry.getKey())));if(parsed!=null)uniforms.put(entry.getKey(),parsed);
        }
        Effect result=new Effect(kind(instance),null,null,combos,textures,uniforms);result.sourceVertex=vertex;result.sourceFragment=fragment;result.includes=includes;return result;
    }

    /** Translate in place; false (with a reason) when the translator rejects the shader. */
    static boolean translate(Effect effect,Translator translator,List<String> failures){
        try{
            String[] glsl=translator.translate(effect.sourceVertex,effect.sourceFragment,effect.combos,effect.includes);
            if(glsl==null||glsl.length!=2||!glsl[0].startsWith("#version 300 es")||!glsl[1].startsWith("#version 300 es"))throw new IOException("翻译结果无效");
            effect.vertex=glsl[0];effect.fragment=glsl[1];return true;
        }catch(Exception failure){failures.add(effect.name+"："+(failure.getMessage()==null||failure.getMessage().isBlank()?failure.getClass().getSimpleName():failure.getMessage()));return false;}
        finally{effect.sourceVertex=null;effect.sourceFragment=null;effect.includes=null;}
    }

    // ---- Serialized form inside WallpaperMobileScene/1 packages ----
    static JSONObject describe(Effect effect,List<String> textureFiles)throws JSONException {
        JSONArray textures=new JSONArray();for(int i=0;i<effect.textures.size();i++){Object t=effect.textures.get(i);textures.put(t==null?JSONObject.NULL:t instanceof String?t:textureFiles.get(i));}
        JSONObject uniforms=new JSONObject();for(Map.Entry<String,float[]> u:effect.uniforms.entrySet()){JSONArray values=new JSONArray();for(float v:u.getValue())values.put((double)v);uniforms.put(u.getKey(),values);}
        return new JSONObject().put("name",effect.name).put("vertex",effect.vertex).put("fragment",effect.fragment).put("textures",textures).put("uniforms",uniforms);
    }
    static Effect parse(JSONObject value,ContentArchive archive,long[] pixels,long budget)throws Exception {
        String vertex=value.getString("vertex"),fragment=value.getString("fragment");
        if(!vertex.startsWith("#version 300 es")||!fragment.startsWith("#version 300 es")||vertex.length()>512*1024||fragment.length()>512*1024)throw new IOException("特效着色器无效");
        JSONArray list=value.getJSONArray("textures");if(list.length()<1||list.length()>MAX_SLOTS)throw new IOException("特效贴图数量无效");
        List<Object> textures=new ArrayList<>();textures.add(null);
        try{
            for(int i=1;i<list.length();i++){
                String name=list.getString(i);
                if(ENGINE_TEXTURES.contains(name)){textures.add(name);continue;}
                Bitmap bitmap=WallpaperContent.bitmap(archive.read(WallpaperContent.asset(archive,name),32*1024*1024));
                pixels[0]+=(long)bitmap.getWidth()*bitmap.getHeight();textures.add(bitmap);if(pixels[0]>budget)throw new IOException("场景解码内存超过预算");
            }
        }catch(Exception|OutOfMemoryError failure){for(Object t:textures)if(t instanceof Bitmap b)b.recycle();throw failure;}
        Map<String,float[]> uniforms=new LinkedHashMap<>();JSONObject u=value.optJSONObject("uniforms");
        if(u!=null)for(Iterator<String> keys=u.keys();keys.hasNext();){String key=keys.next();float[] parsed=numbers(u.get(key),0);if(parsed!=null&&key.matches("[A-Za-z_]\\w{0,63}"))uniforms.put(key,parsed);}
        return new Effect(value.optString("name","特效"),vertex,fragment,new JSONObject(),textures,uniforms);
    }

    private static String text(ContentArchive archive,String name)throws Exception {
        if(!archive.has(name))throw new IOException("特效缺少着色器 "+name);
        return stripBom(new String(archive.read(name,512*1024),StandardCharsets.UTF_8));
    }
    private static String stripBom(String text){return !text.isEmpty()&&text.charAt(0)==0xFEFF?text.substring(1):text;}
    private static void fill(Object[] names,boolean[] bound,JSONArray list){
        if(list==null)return;
        for(int i=0;i<Math.min(list.length(),names.length);i++)if(!list.isNull(i)){String name=list.optString(i,"");if(!name.isEmpty()){names[i]=name;bound[i]=true;}}
    }
    private static void copy(JSONObject from,JSONObject to){
        if(from==null)return;
        for(Iterator<String> keys=from.keys();keys.hasNext();){String key=keys.next();try{to.put(key,from.get(key));}catch(JSONException ignored){}}
    }
    private static Object lookup(JSONObject constants,String key){
        if(key.isEmpty())return null;if(constants.has(key))return constants.opt(key);
        for(Iterator<String> keys=constants.keys();keys.hasNext();){String k=keys.next();if(k.equalsIgnoreCase(key))return constants.opt(k);}
        return null;
    }
    private static int width(String type){
        if(type==null)return 0;
        return switch(type){case "float","int","bool","uint"->1;case "vec2"->2;case "vec3"->3;case "vec4"->4;default->0;};
    }
    /** Numbers, "x y z" strings, booleans and {"value":…} user bindings; scalars broadcast to the uniform width. */
    static float[] numbers(Object value,int width){
        if(value instanceof JSONObject object)return numbers(object.opt("value"),width);
        float[] values;
        try{
            if(value instanceof Boolean b)values=new float[]{b?1:0};
            else if(value instanceof Number n)values=new float[]{n.floatValue()};
            else if(value instanceof JSONArray a){values=new float[a.length()];for(int i=0;i<values.length;i++)values[i]=(float)a.getDouble(i);}
            else if(value instanceof String s&&!s.isBlank()){String[] parts=s.trim().split("[\\s,]+");values=new float[parts.length];for(int i=0;i<parts.length;i++)values[i]=Float.parseFloat(parts[i]);}
            else return null;
        }catch(Exception invalid){return null;}
        if(values.length==0||values.length>16)return null;for(float v:values)if(!Float.isFinite(v))return null;
        if(width<=0||width==values.length)return values;
        float[] fitted=new float[width];for(int i=0;i<width;i++)fitted[i]=values.length==1?values[0]:i<values.length?values[i]:0;return fitted;
    }
    private static Bitmap scaled(Bitmap source){
        float scale=Math.min(1,MAX_EDGE/(float)Math.max(source.getWidth(),source.getHeight()));if(scale>=1)return source;
        Bitmap smaller=Bitmap.createScaledBitmap(source,Math.max(1,Math.round(source.getWidth()*scale)),Math.max(1,Math.round(source.getHeight()*scale)),true);source.recycle();return smaller;
    }
    private SceneEffects(){}
}
