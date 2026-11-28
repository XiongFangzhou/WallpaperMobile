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
    /** Wallpaper Engine's name for the picture drawn so far, below the layer being processed. */
    static final String FULL_FRAME="_rt_FullFrameBuffer";
    static final class Effect {
        final String name;String vertex,fragment;final JSONObject combos;final List<Object> textures;final Map<String,float[]> uniforms;
        // Original sources until translation; dropped afterwards to keep memory low.
        String sourceVertex,sourceFragment;JSONObject includes;
        /** Multi-pass routing: passes of one effect share a group; target is a named buffer (null = the effect's result),
         *  bind maps texture slots to "previous" or buffer names, fbos (first pass only) declares the buffers {name, scale}.
         *  A copy pass has no shaders and copies bind slot 0 into its target. */
        int group;String target;final Map<Integer,String> bind=new HashMap<>();JSONArray fbos;boolean copy;
        /** Uniforms bound to author properties (uniform → property); the phone setting replaces their value. */
        final Map<String,String> bound=new HashMap<>();
        /** Texture slots bound to scenetexture author properties (passes[].usertextures): a picture chosen on the phone
         *  replaces the slot; originals keeps what the slot held before. */
        final Map<Integer,String> userTextures=new HashMap<>();final Map<Integer,Object> originals=new HashMap<>();
        /** Uniforms computed by scripts (key "fx.<effect id>.<pass>.<material name>" → uniform), and the script key of the
         *  effect's visibility; scriptOff is that script's last answer. */
        final Map<String,String> scripted=new HashMap<>();String scriptedVisible;boolean scriptOff;
        /** Off by an author switch or by its visibility script. */
        boolean disabled(){return off||scriptOff;}
        /** Follows the finger only (X-ray run from its own shader): without a touch its pointer is off the screen, as a phone has no hover. */
        boolean finger;
        /** Texture slots whose image clamps at its edges (TEX ClampUVs); the others repeat, as in Wallpaper Engine. */
        final Set<Integer> clamped=new TreeSet<>();
        /** Author-property switch for the whole effect [[name, condition or null]]; off when it does not hold. */
        JSONArray when;boolean off;
        // Runtime state owned by SceneGpu on its GL thread.
        int program;boolean failed;
        Effect(String name,String vertex,String fragment,JSONObject combos,List<Object> textures,Map<String,float[]> uniforms){this.name=name;this.vertex=vertex;this.fragment=fragment;this.combos=combos;this.textures=textures;this.uniforms=uniforms;}
        void recycle(){for(Object texture:textures)if(texture instanceof Bitmap bitmap&&!bitmap.isRecycled())bitmap.recycle();}
    }

    /** Engine textures that never ship in packages; SceneGpu synthesizes them. */
    static final Set<String> ENGINE_TEXTURES=Set.of("util/white","util/black","util/noise","util/noflow","util/clouds_256","particle/halo_6");
    private static final Pattern ANNOTATED=Pattern.compile("uniform\\s+(\\w+)\\s+(\\w+)\\s*;\\s*//\\s*(\\{[^\\n]*\\})");
    private static final Pattern TEXTURE_SLOT=Pattern.compile("g_Texture(\\d+)");
    /** Another layer's result (Wallpaper Engine's per-layer composite buffer), named by that layer's object id. */
    private static final Pattern COMPOSITE=Pattern.compile("_rt_imageLayerComposite_(\\d+)_a");
    /** Object id named by a layer-composite buffer, else -1. */
    static int compositeId(String name){if(name==null)return -1;Matcher m=COMPOSITE.matcher(name);if(!m.matches())return -1;try{return Integer.parseInt(m.group(1));}catch(NumberFormatException e){return -1;}}
    /** Object ids an effect instance names as layer composites (its texture overrides). */
    static Set<Integer> composites(JSONObject instance){Set<Integer> ids=new TreeSet<>();Matcher m=COMPOSITE.matcher(instance.toString());while(m.find())try{ids.add(Integer.parseInt(m.group(1)));}catch(NumberFormatException ignored){}return ids;}
    private static final int MAX_EDGE=1024,MAX_SLOTS=8;

    static String kind(JSONObject instance){return instance.optString("file").replace("effects/","").replace("/effect.json","");}

    /** Shader headers the author bundled under shaders/, keyed the way #include names them. */
    static JSONObject includes(ContentArchive archive)throws Exception {
        JSONObject result=new JSONObject();
        // Wallpaper Engine's original headers when its assets are available; the package's own headers win.
        java.io.File engine=archive.fallbackRoot()==null?null:new java.io.File(archive.fallbackRoot(),"shaders");
        java.io.File[] headers=engine==null?null:engine.listFiles((dir,name)->name.endsWith(".h"));
        if(headers!=null)for(java.io.File h:headers)if(h.length()<=512*1024)result.put(h.getName(),stripBom(new String(java.nio.file.Files.readAllBytes(h.toPath()),StandardCharsets.UTF_8)));
        for(String name:archive.names())if(name.startsWith("shaders/")&&name.endsWith(".h"))result.put(name.substring(8),stripBom(new String(archive.read(name,512*1024),StandardCharsets.UTF_8)));
        return result;
    }

    /** Reads one effect instance as its passes; throws with a user-readable reason when it cannot run on the phone. */
    static List<Effect> read(ContentArchive archive,JSONObject instance,JSONObject includes,int group)throws Exception {
        JSONObject effect=WallpaperContent.json(archive,ContentArchive.path(instance.getString("file")));
        JSONArray passes=effect.optJSONArray("passes");
        if(passes==null||passes.length()==0)throw new IOException("特效没有渲染步骤");if(passes.length()>12)throw new IOException("特效渲染步骤过多");
        Map<String,Integer> declared=new LinkedHashMap<>();JSONArray fbos=new JSONArray();JSONArray list=effect.optJSONArray("fbos");
        if(list!=null)for(int i=0;i<list.length();i++){
            JSONObject fbo=list.getJSONObject(i);String name=fbo.optString("name");if(!name.startsWith("_rt_")||declared.size()>=8)throw new IOException("特效缓冲声明无效");
            if(fbo.has("width")||fbo.has("height"))throw new IOException("固定尺寸的特效缓冲尚不支持");
            int scale=Math.max(1,Math.min(16,fbo.optInt("scale",1)));declared.put(name,scale);fbos.put(new JSONObject().put("name",name).put("scale",scale));
        }
        JSONArray overrides=instance.optJSONArray("passes");List<Effect> result=new ArrayList<>();
        try{
            for(int i=0;i<passes.length();i++){
                JSONObject pass=passes.getJSONObject(i);
                if(pass.has("conditions"))throw new IOException("带条件的特效步骤尚不支持");
                String target=pass.optString("target","");if(!target.isEmpty()&&!declared.containsKey(target))throw new IOException("特效依赖整幅画面 "+target);
                Map<Integer,String> bind=new HashMap<>();JSONArray binds=pass.optJSONArray("bind");
                if(binds!=null)for(int k=0;k<binds.length();k++){JSONObject b=binds.getJSONObject(k);String name=b.optString("name");int index=b.optInt("index",-1);
                    if(index<0||index>=MAX_SLOTS)throw new IOException("特效绑定槽位无效");if(!name.equals("previous")&&!declared.containsKey(name)&&compositeId(name)<0)throw new IOException("特效依赖整幅画面 "+name);bind.put(index,name);}
                Effect step;
                if(pass.has("command")){
                    if(!pass.optString("command").equals("copy"))throw new IOException("特效步骤 "+pass.optString("command")+" 尚不支持");
                    String source=pass.optString("source");if(!source.equals("previous")&&!declared.containsKey(source)||target.isEmpty())throw new IOException("特效复制步骤无效");
                    step=new Effect(kind(instance),null,null,new JSONObject(),new ArrayList<>(Collections.singletonList(null)),new LinkedHashMap<>());step.copy=true;bind.put(0,source);
                }else{
                    JSONObject override=overrides!=null&&i<overrides.length()&&overrides.optJSONObject(i)!=null?overrides.getJSONObject(i):new JSONObject();
                    step=pass(archive,instance,pass.getString("material"),override,includes,declared.keySet(),bind,i);
                }
                step.group=group;step.target=target.isEmpty()?null:target;step.bind.putAll(bind);if(i==0)step.fbos=fbos;result.add(step);
            }
            if(result.get(result.size()-1).target!=null)throw new IOException("特效最后一步没有输出到图层");
            return result;
        }catch(Exception|OutOfMemoryError failure){for(Effect e:result)e.recycle();throw failure;}
    }

    private static Effect pass(ContentArchive archive,JSONObject instance,String materialFile,JSONObject override,JSONObject includes,Set<String> buffers,Map<Integer,String> bind,int passIndex)throws Exception {
        JSONObject material=WallpaperContent.json(archive,ContentArchive.path(materialFile)).getJSONArray("passes").getJSONObject(0);
        String blending=material.optString("blending","normal");if(!blending.equals("normal")&&!blending.isEmpty())throw new IOException("特效混合方式尚不支持");
        String shader=material.getString("shader");
        String vertex=text(archive,"shaders/"+shader+".vert"),fragment=text(archive,"shaders/"+shader+".frag");

        // Texture slots: material defaults, then the instance's own textures, then annotation defaults.
        Object[] names=new Object[MAX_SLOTS];boolean[] bound=new boolean[MAX_SLOTS];
        fill(names,bound,material.optJSONArray("textures"));fill(names,bound,override.optJSONArray("textures"));
        for(int slot:bind.keySet())bound[slot]=true;
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
            if(names[index]==null&&!bind.containsKey(index)&&!slot.getValue().optString("default").isEmpty())names[index]=slot.getValue().optString("default");
        }
        copy(override.optJSONObject("combos"),combos);

        List<Object> textures=new ArrayList<>();Set<Integer> clamped=new TreeSet<>();textures.add(null);int last=0;for(int i=1;i<MAX_SLOTS;i++)if(names[i]!=null||bind.containsKey(i))last=i;
        try{
            for(int i=1;i<=last;i++){
                if(bind.containsKey(i)){textures.add("util/black");continue;}
                Object name=names[i];
                if(name==null){textures.add("util/black");continue;}
                String value=String.valueOf(name);
                if(value.startsWith("_rt_")){if(!buffers.contains(value)&&!value.equals(FULL_FRAME)&&compositeId(value)<0)throw new IOException("特效依赖整幅画面 "+value);bind.put(i,value);textures.add("util/black");continue;}
                // The real engine texture when Wallpaper Engine's assets are available, otherwise a synthesized stand-in.
                String asset=null;try{asset=WallpaperContent.asset(archive,value);}catch(IOException missing){asset=null;}
                if(asset!=null){byte[] raw=archive.read(asset,32*1024*1024);if(dev.workshopvideo.core.TexturePixels.clampUVs(raw))clamped.add(i);textures.add(scaled(WallpaperContent.bitmap(raw)));continue;}
                if(ENGINE_TEXTURES.contains(value)){textures.add(value);continue;}
                throw new IOException("特效依赖引擎贴图 "+value);
            }
        }catch(Exception|OutOfMemoryError failure){for(Object t:textures)if(t instanceof Bitmap b)b.recycle();throw failure;}

        // Material values bind to uniforms through the shader annotations' "material" names.
        JSONObject constants=new JSONObject();copy(material.optJSONObject("constantshadervalues"),constants);copy(override.optJSONObject("constantshadervalues"),constants);
        Map<String,float[]> uniforms=new LinkedHashMap<>();Map<String,String> boundUniforms=new HashMap<>(),scriptedUniforms=new HashMap<>();
        for(Map.Entry<String,JSONObject> entry:uniformMeta.entrySet()){
            JSONObject meta=entry.getValue();Object value=lookup(constants,meta.optString("material"));if(value==null)value=meta.opt("default");
            if(value instanceof JSONObject b&&b.opt("user") instanceof String property&&!property.isEmpty())boundUniforms.put(entry.getKey(),property);
            if(value instanceof JSONObject s&&s.opt("script") instanceof String)scriptedUniforms.put("fx."+instance.optInt("id",-1)+"."+passIndex+"."+meta.optString("material"),entry.getKey());
            float[] parsed=numbers(value,width(types.get(entry.getKey())));if(parsed!=null)uniforms.put(entry.getKey(),parsed);
        }
        Effect result=new Effect(kind(instance),null,null,combos,textures,uniforms);result.clamped.addAll(clamped);result.bound.putAll(boundUniforms);result.scripted.putAll(scriptedUniforms);
        JSONArray userTextures=override.optJSONArray("usertextures");
        if(userTextures!=null)for(int i=1;i<userTextures.length()&&i<textures.size();i++)if(userTextures.opt(i) instanceof String property&&!property.isEmpty())result.userTextures.put(i,property);result.sourceVertex=vertex;result.sourceFragment=fragment;result.includes=includes;return result;
    }

    /** Translate in place; false (with a reason) when the translator rejects the shader. */
    static boolean translate(Effect effect,Translator translator,List<String> failures){
        if(effect.copy)return true;
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
        JSONObject out=new JSONObject().put("name",effect.name).put("textures",textures).put("uniforms",uniforms).put("group",effect.group);
        if(effect.copy)out.put("copy",true);else out.put("vertex",effect.vertex).put("fragment",effect.fragment);if(effect.finger)out.put("finger",true);
        if(!effect.clamped.isEmpty())out.put("clamp",new JSONArray(effect.clamped));if(effect.target!=null)out.put("target",effect.target);if(effect.fbos!=null&&effect.fbos.length()>0)out.put("fbos",effect.fbos);
        if(!effect.bound.isEmpty())out.put("bound",new JSONObject(effect.bound));if(effect.when!=null)out.put("when",effect.when);
        if(!effect.scripted.isEmpty())out.put("scripted",new JSONObject(effect.scripted));if(effect.scriptedVisible!=null)out.put("scriptedVisible",effect.scriptedVisible).put("scriptOff",effect.scriptOff);
        if(!effect.userTextures.isEmpty()){JSONObject user=new JSONObject();for(Map.Entry<Integer,String> t:effect.userTextures.entrySet())user.put(Integer.toString(t.getKey()),t.getValue());out.put("userTextures",user);}
        if(!effect.bind.isEmpty()){JSONObject bind=new JSONObject();for(Map.Entry<Integer,String> b:effect.bind.entrySet())bind.put(Integer.toString(b.getKey()),b.getValue());out.put("bind",bind);}
        return out;
    }
    static Effect parse(JSONObject value,ContentArchive archive,long[] pixels,long budget)throws Exception {
        boolean copy=value.optBoolean("copy");String vertex=copy?null:value.getString("vertex"),fragment=copy?null:value.getString("fragment");
        if(!copy&&(!vertex.startsWith("#version 300 es")||!fragment.startsWith("#version 300 es")||vertex.length()>512*1024||fragment.length()>512*1024))throw new IOException("特效着色器无效");
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
        Effect effect=new Effect(value.optString("name","特效"),vertex,fragment,new JSONObject(),textures,uniforms);effect.copy=copy;effect.group=value.optInt("group",0);
        String target=value.optString("target","");if(!target.isEmpty()){if(!target.startsWith("_rt_"))throw new IOException("特效缓冲无效");effect.target=target;}
        JSONArray fbos=value.optJSONArray("fbos");if(fbos!=null){if(fbos.length()>8)throw new IOException("特效缓冲过多");effect.fbos=fbos;}
        effect.when=value.optJSONArray("when");
        JSONObject boundUniforms=value.optJSONObject("bound");
        if(boundUniforms!=null)for(Iterator<String> keys=boundUniforms.keys();keys.hasNext();){String key=keys.next();if(uniforms.containsKey(key))effect.bound.put(key,boundUniforms.optString(key));}
        JSONObject scriptedUniforms=value.optJSONObject("scripted");
        if(scriptedUniforms!=null)for(Iterator<String> keys=scriptedUniforms.keys();keys.hasNext();){String key=keys.next();String uniform=scriptedUniforms.optString(key);if(key.startsWith("fx.")&&uniforms.containsKey(uniform))effect.scripted.put(key,uniform);}
        if(value.optString("scriptedVisible","").startsWith("fx.")){effect.scriptedVisible=value.optString("scriptedVisible");effect.scriptOff=value.optBoolean("scriptOff");}
        JSONObject user=value.optJSONObject("userTextures");
        if(user!=null)for(Iterator<String> keys=user.keys();keys.hasNext();){String key=keys.next();try{int slot=Integer.parseInt(key);if(slot>0&&slot<textures.size())effect.userTextures.put(slot,user.getString(key));}catch(NumberFormatException ignored){}}
        effect.finger=value.optBoolean("finger");
        JSONArray clamp=value.optJSONArray("clamp");if(clamp!=null)for(int k=0;k<clamp.length();k++){int slot=clamp.optInt(k,-1);if(slot>0&&slot<textures.size())effect.clamped.add(slot);}
        JSONObject bind=value.optJSONObject("bind");
        if(bind!=null)for(Iterator<String> keys=bind.keys();keys.hasNext();){String key=keys.next();int slot;try{slot=Integer.parseInt(key);}catch(NumberFormatException e){continue;}String name=bind.getString(key);if(slot>=0&&slot<MAX_SLOTS&&(name.equals("previous")||name.startsWith("_rt_")))effect.bind.put(slot,name);}
        return effect;
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
