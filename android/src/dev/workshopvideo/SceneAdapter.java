package dev.workshopvideo;

import android.graphics.Bitmap;
import dev.workshopvideo.core.ContentArchive;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

/** Explicit, lossy image-layer adaptation. Never executes author scripts or uses workshop covers. */
final class SceneAdapter {
 static final class Report {
  final WallpaperContent.Scene scene=new WallpaperContent.Scene();
  final LinkedHashMap<String,Integer> skipped=new LinkedHashMap<>();
  final LinkedHashSet<String> effects=new LinkedHashSet<>();
  int objects,images,particles,texts,sounds,models,scripts,bindings,xrays,animations,puppets,puppetMotions,puppetStatic,blended,particleSystems,particleApproximate,particleChildren;
  /** Author effects that run from the package's own shaders, and the ones left out with their reason. */
  final LinkedHashMap<String,Integer> keptEffects=new LinkedHashMap<>();final LinkedHashMap<String,String> droppedEffects=new LinkedHashMap<>();
  void keep(String name){keptEffects.merge(name,1,Integer::sum);}
  void drop(String name,String reason){droppedEffects.putIfAbsent(name,reason);}
  void unkeep(String name){keptEffects.computeIfPresent(name,(k,v)->v>1?v-1:null);}
  String error="";
  /** A visible video-texture layer covering most of the canvas: kept as a video wallpaper when no image layer survives. */
  String videoTexture="";float videoCover;
  /** Every scene object with its saved properties and scripts, run on the phone by assets/scene-script-runtime.js. */
  JSONObject scriptScene;String scriptSources="";int scriptedHidden;
  /** Author properties (project.json) the converted layers depend on, offered as switches on the phone. */
  final JSONObject properties=new JSONObject();int propertyLayers;JSONObject general=new JSONObject();
  void skip(String reason){skipped.put(reason,skipped.getOrDefault(reason,0)+1);}
  boolean usable(){return !scene.layers.isEmpty()&&error.isEmpty();}
  String describe(){
   StringBuilder s=new StringBuilder("原作：").append(objects).append(" 个对象，图片 ").append(images).append("、粒子 ").append(particles).append("、文字 ").append(texts).append("、音频 ").append(sounds).append("、模型 ").append(models).append("。\n");
   s.append("实际解码可保留：").append(scene.layers.size()).append(" 个图片图层（").append(scene.width).append(" × ").append(scene.height).append("）。\n");
   if(puppets>0)s.append("骨骼图层：").append(puppets).append(" 个按原作网格绘制").append(puppetMotions>0?"，其中 "+puppetMotions+" 个播放骨骼动画":"").append("。\n");
   if(blended>0)s.append("混合模式图层：").append(blended).append(" 个（叠加、变亮、滤色等）按原作方式合成。\n");
   if(puppetStatic>0)s.append("骨骼图层：").append(puppetStatic).append(" 个网格格式暂不支持，以原图矩形显示。\n");
   if(animations>0)s.append("保留帧动画：").append(animations).append(" 个动图图层，按原作的帧时长循环播放。\n");
   if(xrays>0)s.append("支持触摸 X-ray：").append(xrays).append(" 层；采用手机圆形揭示区域，保留作者的第二张图片。\n");
   if(!keptEffects.isEmpty()){s.append("保留作者特效：");boolean first=true;for(var e:keptEffects.entrySet()){if(!first)s.append("、");first=false;s.append(e.getKey());if(e.getValue()>1)s.append(" ×").append(e.getValue());}s.append("（运行作品自带的着色器）。\n");}
   if(!droppedEffects.isEmpty()){s.append("未能保留的特效：");boolean first=true;for(var e:droppedEffects.entrySet()){if(!first)s.append("；");first=false;s.append(e.getKey()).append("（").append(e.getValue()).append("）");}s.append("。\n");}
   else if(!effects.isEmpty())s.append("不保留作者特效：").append(String.join("、",effects)).append("。\n");
   if(scripts>0&&scriptScene!=null)s.append("脚本 ").append(scripts).append(" 处：在手机的隔离环境中运行，触摸代替鼠标").append(scriptedHidden>0?"；保留 "+scriptedHidden+" 个由脚本控制显示的隐藏图层":"").append("。\n");
   if(propertyLayers>0)s.append("作者开关：").append(properties.length()).append(" 个（").append(propertyLayers).append(" 个图层受控），可在「效果调节 → 作品设置」中切换。\n");
   if(bindings>0)s.append("作者属性绑定 ").append(bindings).append(" 处：使用作者设定的默认值。\n");
   for(var e:skipped.entrySet())s.append("跳过 ").append(e.getValue()).append(" 层：").append(e.getKey()).append("。\n");
   if(particleSystems>0)s.append("粒子：").append(particleSystems).append(" 套按原作参数模拟").append(particleApproximate>0?"，其中 "+particleApproximate+" 套使用近似贴图（引擎自带贴图）":"").append(particleChildren>0?"；"+particleChildren+" 套的子粒子未保留":"").append("。\n");
   s.append("适配版不保留动态文字、音频响应和 3D 模型。可使用已保留的特效、X-ray、手机陀螺仪、触控及桌面滑动效果。");
   if(!error.isEmpty())s.append("\n无法适配：").append(error);
   return s.toString();
  }
 }
 static Object value(Object v){return v instanceof JSONObject?((JSONObject)v).opt("value"):v;}
 static double number(Object v,double fallback){try{return Double.parseDouble(String.valueOf(value(v)));}catch(Exception e){return fallback;}}
 static boolean visible(JSONObject o){Object v=value(o.opt("visible"));return v==null||v==JSONObject.NULL||!Boolean.FALSE.equals(v)&&!"false".equalsIgnoreCase(String.valueOf(v));}
 static void scan(Object value,Report r){
  if(value instanceof JSONObject){JSONObject o=(JSONObject)value;
   Object script=o.opt("script");if(script!=null&&script!=JSONObject.NULL&&!String.valueOf(script).isBlank())r.scripts++;
   Object user=o.opt("user");if(user!=null&&user!=JSONObject.NULL&&!String.valueOf(user).isBlank())r.bindings++;
   JSONArray effects=o.optJSONArray("effects");if(effects!=null)for(int i=0;i<effects.length();i++){JSONObject e=effects.optJSONObject(i);if(e!=null&&visible(e)){String file=e.optString("file");if(!file.isEmpty())r.effects.add(file.replace("effects/","").replace("/effect.json",""));}}
   for(Iterator<String> keys=o.keys();keys.hasNext();)scan(o.opt(keys.next()),r);
  }else if(value instanceof JSONArray){JSONArray a=(JSONArray)value;for(int i=0;i<a.length();i++)scan(a.opt(i),r);}
 }
 static Report inspect(File source,File workspace)throws Exception {
  workspace.mkdirs();File normalized=File.createTempFile("scene-analysis-",".zip",workspace);
  try {WallpaperPackage.normalize(source,normalized,workspace);try(ContentArchive a=new ContentArchive(normalized)){return inspect(a);}}
  finally{normalized.delete();}
 }
 static Report inspect(ContentArchive a)throws Exception {
  Report r=new Report();try{
   if(a.has("wallpaper-mobile.json"))throw new IOException("已经是手机场景，无需适配");
   String entry="scene.json";if(a.has("project.json")){String declared=WallpaperContent.json(a,"project.json").optString("file");if(declared.endsWith(".json")&&a.has(declared))entry=declared;}
   JSONObject root=WallpaperContent.json(a,entry);scan(root,r);
   JSONObject projectProperties=projectProperties(a);resolve(root,projectProperties);
   r.general=root.optJSONObject("general")==null?new JSONObject():root.optJSONObject("general");
   JSONObject general=root.optJSONObject("general"),projection=general==null?null:general.optJSONObject("orthogonalprojection");
   if(projection==null)throw new IOException("缺少正交画布尺寸，无法可靠恢复布局");
   r.scene.width=projection.optInt("width",0);r.scene.height=projection.optInt("height",0);
   if(r.scene.width<1||r.scene.height<1||r.scene.width>16384||r.scene.height>16384)throw new IOException("画布尺寸无效");
   JSONArray objects=root.getJSONArray("objects");r.objects=objects.length();if(r.objects>4096)throw new IOException("对象数量超出分析预算");
   long pixels=0;JSONObject includes=null;
   if(r.scripts>0){StringBuilder sources=new StringBuilder();r.scriptScene=scriptScene(a,objects,r.scene.width,r.scene.height,sources);r.scriptSources=sources.toString();}
   java.util.HashMap<Integer,Integer> outputIndex=new java.util.HashMap<>();java.util.IdentityHashMap<WallpaperContent.Layer,Integer> attachSources=new java.util.IdentityHashMap<>();
   java.util.HashMap<Integer,JSONObject> byId=new java.util.HashMap<>();for(int i=0;i<objects.length();i++){JSONObject o=objects.optJSONObject(i);if(o!=null&&o.has("id"))byId.put(o.optInt("id"),o);}
   for(int i=0;i<objects.length();i++){
    if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("已停止适配");
    JSONObject o=objects.getJSONObject(i);String image=o.optString("image","");boolean imageObject=!image.isEmpty()&&!image.equals("null");
    if(imageObject)r.images++;if(o.has("particle")&&!o.isNull("particle"))r.particles++;if(o.has("text")&&!o.isNull("text"))r.texts++;if(o.has("sound")&&!o.isNull("sound"))r.sounds++;if(o.has("model")&&!o.isNull("model"))r.models++;
    if(!imageObject&&o.has("particle")&&!o.isNull("particle")){
     Visibility shown=visibility(o,byId,projectProperties);
     if(!shown.fixed&&(r.scriptScene==null||!scriptedVisibility(o,r.scriptSources))){r.skip("默认隐藏");continue;}
     try{WallpaperContent.Layer layer=particleLayer(a,o,byId,r);layer.when=shown.when;usedProperties(shown.when,projectProperties,r);if(!shown.fixed)layer.visible=false;if(o.has("id"))outputIndex.put(o.optInt("id"),r.scene.layers.size());r.scene.layers.add(layer);r.particleSystems++;pixels+=(long)layer.bitmap.getWidth()*layer.bitmap.getHeight();}
     catch(InterruptedIOException stop){throw stop;}catch(Exception e){r.skip("粒子："+(e.getMessage()==null?"无法读取":e.getMessage()));}
     continue;
    }
    if(!imageObject)continue;boolean startHidden=false;
    Visibility shown=visibility(o,byId,projectProperties);
    if(!shown.fixed){if(r.scriptScene==null||!scriptedVisibility(o,r.scriptSources)){r.skip("默认隐藏");continue;}startHidden=true;}
    Bitmap bitmap=null;
    try{
     if(imageLayers(r.scene)>=48)throw new IOException("超过 48 个图片图层预算");
     // Skeletal layers keep their rest pose; the image quad is the puppet's bind pose.
     dev.workshopvideo.core.PuppetModel puppetModel=null;byte[] puppetBytes=null;
     for(String key:new String[]{"origin","size","scale","angles"}){Object raw=o.opt(key);if(raw instanceof JSONObject&&((JSONObject)raw).isNull("value"))throw new IOException("图层变换没有已存默认值");}
     float[] crop=WallpaperContent.vector(o.opt("cropoffset"),0,0);for(float v:crop)if(Math.abs(v)>.001)throw new IOException("非零裁切偏移");
     JSONObject model=WallpaperContent.json(a,WallpaperContent.asset(a,image));scan(model,r);
     String puppetPath=model.optString("puppet","");
     if(!puppetPath.isEmpty()){
      try{puppetBytes=a.read(ContentArchive.path(puppetPath),64*1024*1024);puppetModel=dev.workshopvideo.core.PuppetModel.parse(puppetBytes);if(puppetModel.mesh==null)throw new IOException("骨骼网格格式不支持");}
      catch(InterruptedIOException stop){throw stop;}catch(IOException unsupported){puppetModel=null;puppetBytes=null;r.puppetStatic++;}
     }
     JSONObject mat=WallpaperContent.json(a,WallpaperContent.asset(a,model.getString("material")));scan(mat,r);
     JSONArray passes=mat.optJSONArray("passes");if(passes==null||passes.length()!=1)throw new IOException("多通道或非图片材质");
     JSONObject pass=passes.getJSONObject(0);String shader=pass.optString("shader");if(!shader.equals("genericimage2")&&!shader.equals("genericimage4"))throw new IOException("自定义着色器");
     // Blend modes other than normal need a compositor; don't render additive masks as opaque images.
     Object passBlending=pass.opt("blending");if(passBlending instanceof Number&&((Number)passBlending).intValue()>1)throw new IOException("非标准混合模式");
     int colorBlend=(int)number(o.opt("colorBlendMode"),0);if(colorBlend<0||colorBlend>32)throw new IOException("未知的混合模式");boolean additive="additive".equals(pass.optString("blending"));
     JSONArray textures=pass.optJSONArray("textures");if(textures==null||textures.length()!=1||!(textures.opt(0) instanceof String))throw new IOException("没有可解码的基础图片纹理");
     String texture=textures.getString(0);byte[] raw=a.read(WallpaperContent.asset(a,texture),32*1024*1024);
     if(dev.workshopvideo.core.TexturePixels.video(raw)){
      float[] vs=WallpaperContent.vector(o.opt("size"),r.scene.width,r.scene.height),vk=WallpaperContent.vector(o.opt("scale"),1,1);
      float cover=vs.length>=2&&vk.length>=2?Math.abs(vs[0]*vk[0]*vs[1]*vk[1])/((float)r.scene.width*r.scene.height):0;
      if(r.videoTexture.isEmpty()&&cover>=.9f&&!o.has("parent")){r.videoTexture=WallpaperContent.asset(a,texture);r.videoCover=cover;}
      throw new IOException("视频纹理图层尚不支持");
     }
     java.util.List<dev.workshopvideo.core.TexturePixels.Frame> frames=dev.workshopvideo.core.TexturePixels.sprite(raw)?dev.workshopvideo.core.TexturePixels.frames(raw):java.util.List.of();
     bitmap=WallpaperContent.bitmap(raw,!frames.isEmpty());raw=null;
     bitmap=toDisplayDensity(bitmap,o,r.scene.width,r.scene.height,frames.isEmpty()?1:Math.max(1,frames.size()));
     if(pixels+(long)bitmap.getWidth()*bitmap.getHeight()>16L*1024*1024)throw new IOException("超过 1600 万像素内存预算");
     boolean child=o.has("parent")&&!o.isNull("parent");float[] localOrigin=null;float localAngle=0;
     float[] pos=child?WallpaperContent.vector(o.opt("origin"),0,0):WallpaperContent.vector(o.opt("origin"),r.scene.width/2f,r.scene.height/2f),sz=WallpaperContent.vector(o.opt("size"),bitmap.getWidth(),bitmap.getHeight()),scale=WallpaperContent.vector(o.opt("scale"),1,1),angles=WallpaperContent.vector(o.opt("angles"),0,0,0),depth=WallpaperContent.vector(o.opt("parallaxDepth"),1,1);
     if(pos.length>=2&&angles.length>=3){localOrigin=new float[]{pos[0],pos[1]};localAngle=angles[2];}
     if(pos.length>=2&&scale.length>=2&&angles.length>=3){float[] world=world(a,o,byId,pos,scale,angles[2],r.scene.width,r.scene.height);pos=new float[]{world[0],world[1]};scale=new float[]{world[2],world[3]};angles=new float[]{angles[0],angles[1],world[4]};}
     if(pos.length<2||sz.length<2||scale.length<2||angles.length<3)throw new IOException("不完整的图层变换");
     if(scale[0]<=0||scale[1]<=0||Math.abs(angles[0])>.001||Math.abs(angles[1])>.001)throw new IOException("镜像或 3D 旋转");
     WallpaperContent.Layer layer=new WallpaperContent.Layer();layer.name=o.optString("name","图层 "+(i+1));layer.w=sz[0]*scale[0];layer.h=sz[1]*scale[1];layer.x=pos[0]-layer.w/2;layer.y=r.scene.height-pos[1]-layer.h/2;layer.rotation=-(float)Math.toDegrees(angles[2]);layer.depth=Math.max(-2,Math.min(2,depth[0]));layer.alpha=(float)Math.max(0,Math.min(1,number(o.opt("alpha"),1)));layer.frames=frames;layer.objectId=o.has("id")?o.optInt("id"):-1;layer.visible=!startHidden;layer.baseW=sz[0];layer.baseH=sz[1];
     if(child&&r.scriptScene!=null)layer.frame=world(a,o,byId,new float[]{0,0},new float[]{1,1},0,r.scene.width,r.scene.height);
     if(startHidden)r.scriptedHidden++;layer.when=shown.when;usedProperties(shown.when,projectProperties,r);layer.blend=colorBlend;layer.additive=additive;if(colorBlend!=0||additive)r.blended++;
     if(puppetModel!=null){
      layer.puppet=puppetModel;layer.puppetData=puppetBytes;layer.puppetW=sz[0];layer.puppetH=sz[1];r.puppets++;
      JSONArray clips=o.optJSONArray("animationlayers");java.util.ArrayList<dev.workshopvideo.core.PuppetModel.Track> tracks=new java.util.ArrayList<>();
      if(clips!=null)for(int t=0;t<clips.length();t++){JSONObject clip=clips.optJSONObject(t);if(clip==null||!clip.optBoolean("visible",true))continue;int id=clip.optInt("animation",-1);if(!puppetModel.animations.containsKey(id))continue;
       tracks.add(new dev.workshopvideo.core.PuppetModel.Track(id,(float)number(clip.opt("rate"),1),(float)Math.max(0,Math.min(1,number(clip.opt("blend"),1))),clip.optBoolean("additive",true)));}
      layer.tracks=java.util.List.copyOf(tracks);if(!tracks.isEmpty())r.puppetMotions++;
     }
     if(child&&!o.optString("attachment","").isEmpty()&&localOrigin!=null){layer.attachName=o.optString("attachment");layer.attachX=localOrigin[0];layer.attachY=localOrigin[1];layer.attachAngle=localAngle;attachSources.put(layer,(int)number(o.opt("parent"),-1));}
     if(!Float.isFinite(layer.x)||!Float.isFinite(layer.y)||!Float.isFinite(layer.w)||!Float.isFinite(layer.h)||!Float.isFinite(layer.rotation)||layer.w<=0||layer.h<=0||layer.w>65536||layer.h>65536||Math.abs(layer.x)>65536||Math.abs(layer.y)>65536)throw new IOException("图层尺寸或位置无效");
     JSONArray objectEffects=o.optJSONArray("effects");if(objectEffects!=null)for(int e=0;e<objectEffects.length();e++){
      JSONObject effect=objectEffects.optJSONObject(e);if(effect==null||!visible(effect)||!effect.optString("file").toLowerCase(Locale.ROOT).contains("xray"))continue;
      try{
       if(!effect.optString("file").equals("effects/xray/effect.json")||layer.reveal!=null)throw new IOException("自定义或多重 X-ray");
       JSONArray ep=effect.optJSONArray("passes");if(ep==null||ep.length()!=1)throw new IOException("X-ray 通道数量");JSONObject passX=ep.getJSONObject(0);JSONArray texX=passX.optJSONArray("textures");
       if(texX==null||texX.length()<2||!(texX.opt(1) instanceof String))throw new IOException("X-ray 没有第二张图片");
       String reveal=texX.getString(1);if(reveal.startsWith("_rt_"))throw new IOException("X-ray 依赖其他图层的合成结果");
       JSONObject combos=passX.optJSONObject("combos");if(combos!=null&&combos.optInt("BLENDMODE",0)!=0)throw new IOException("X-ray 混合模式尚不支持");
       if(texX.length()>3&&!texX.isNull(3)&&!texX.optString(3).equals("util/white"))throw new IOException("X-ray 自定义不透明遮罩尚不支持");
       String sprite=texX.optString(2,"particle/halo_6");if(!sprite.equals("particle/halo_6")&&!sprite.equals("util/white")&&!sprite.equals("null"))throw new IOException("X-ray 自定义指针遮罩尚不支持");
       JSONObject constants=passX.optJSONObject("constantshadervalues");layer.revealSize=(float)Math.max(.01,Math.min(1,constants==null?.2:number(constants.opt("size"),.2)));
       Bitmap second=WallpaperContent.bitmap(a.read(WallpaperContent.asset(a,reveal),32*1024*1024));if(pixels+(long)bitmap.getWidth()*bitmap.getHeight()+(long)second.getWidth()*second.getHeight()>16L*1024*1024){second.recycle();throw new IOException("X-ray 纹理超过内存预算");}
       layer.reveal=second;pixels+=(long)second.getWidth()*second.getHeight();r.xrays++;r.effects.remove("xray");
      }catch(Exception failure){if(layer.reveal!=null){layer.reveal.recycle();layer.reveal=null;}r.drop("xray",failure.getMessage()==null?"无法读取":failure.getMessage());}
     }
     if(objectEffects!=null)for(int e=0;e<objectEffects.length();e++){
      JSONObject effect=objectEffects.optJSONObject(e);if(effect==null||!visible(effect)||effect.optString("file").toLowerCase(Locale.ROOT).contains("xray"))continue;
      String name=SceneEffects.kind(effect);
      try{if(layer.effects.size()>=8)throw new IOException("单个图层特效过多");if(includes==null)includes=SceneEffects.includes(a);layer.effects.add(SceneEffects.read(a,effect,includes));r.keep(name);}
      catch(InterruptedIOException stop){throw stop;}catch(Exception|OutOfMemoryError failure){r.drop(name,failure.getMessage()==null?"无法读取":failure.getMessage());}
     }
     layer.bitmap=bitmap;bitmap=null;pixels+=(long)layer.bitmap.getWidth()*layer.bitmap.getHeight();if(o.has("id"))outputIndex.put(o.optInt("id"),r.scene.layers.size());r.scene.layers.add(layer);if(layer.frames.size()>1)r.animations++;
    }catch(InterruptedIOException stop){throw stop;}catch(Exception e){r.skip(e.getMessage()==null?"图片无法解码":e.getMessage());}finally{if(bitmap!=null)bitmap.recycle();}
   }
   // Children follow their parent's animated attachment only when that parent is drawn earlier as a skeletal mesh.
   for(var pending:attachSources.entrySet()){
    Integer parentIndex=outputIndex.get(pending.getValue());WallpaperContent.Layer child=pending.getKey();int childIndex=r.scene.layers.indexOf(child);
    if(parentIndex!=null&&childIndex>parentIndex){WallpaperContent.Layer parent=r.scene.layers.get(parentIndex);if(parent.puppet!=null&&parent.puppet.hasAttachment(child.attachName))child.attachParent=parentIndex;}
   }
   r.effects.removeIf(name->r.keptEffects.containsKey(name)&&!r.droppedEffects.containsKey(name));
   if(r.scene.layers.isEmpty()&&r.error.isEmpty())r.error="未找到可安全恢复的基础图片；不会使用封面代替场景";
   return r;
  }catch(Exception|OutOfMemoryError e){r.scene.close();throw e;}
 }
 /** Composes parent transforms: a child's origin is in its parent's scaled, rotated space. Returns {x,y,scaleX,scaleY,angle}. */
 /** A particle system as a full-canvas layer: its texture, simulation parameters and emitter transform in scene space. */
 static WallpaperContent.Layer particleLayer(ContentArchive a,JSONObject o,java.util.Map<Integer,JSONObject> byId,Report r)throws Exception {
  if(r.scene.layers.size()>=64)throw new IOException("超过 64 层预算");
  JSONObject system=WallpaperContent.json(a,ContentArchive.path(o.getString("particle")));
  JSONArray children=system.optJSONArray("children");if(children!=null&&children.length()>0)r.particleChildren++;
  JSONArray renderers=system.optJSONArray("renderer");String renderer=renderers==null||renderers.length()==0?"sprite":renderers.optJSONObject(0).optString("name");
  if(!renderer.equals("sprite")&&!renderer.equals("spritetrail")&&!renderer.equals("rope"))throw new IOException("粒子渲染方式尚不支持："+renderer);
  JSONObject mat=WallpaperContent.json(a,WallpaperContent.asset(a,system.getString("material")));JSONArray passes=mat.optJSONArray("passes");if(passes==null||passes.length()==0)throw new IOException("粒子材质无效");
  JSONObject pass=passes.getJSONObject(0);JSONArray textures=pass.optJSONArray("textures");if(textures==null||textures.length()==0||!(textures.opt(0) instanceof String))throw new IOException("粒子没有贴图");
  String texture=textures.getString(0);Bitmap bitmap;JSONArray frames=null;boolean approximate=false;
  String path=null;try{path=WallpaperContent.asset(a,texture);}catch(IOException missing){path=null;}
  if(path!=null){
   byte[] raw=a.read(path,32*1024*1024);
   if(dev.workshopvideo.core.TexturePixels.sprite(raw)){java.util.List<dev.workshopvideo.core.TexturePixels.Frame> list=dev.workshopvideo.core.TexturePixels.frames(raw);frames=new JSONArray();for(var f:list)frames.put(new JSONArray().put(f.u0()).put(f.v0()).put(f.u1()).put(f.v1()));bitmap=WallpaperContent.bitmap(raw,true);}
   else bitmap=WallpaperContent.bitmap(raw);
  }else{bitmap=SceneParticles.builtIn(texture);if(bitmap==null)throw new IOException("贴图是引擎自带素材，手机上没有："+texture);approximate=true;}
  int edge=Math.max(bitmap.getWidth(),bitmap.getHeight());if(edge>512){float k=512f/edge;Bitmap small=Bitmap.createScaledBitmap(bitmap,Math.max(1,Math.round(bitmap.getWidth()*k)),Math.max(1,Math.round(bitmap.getHeight()*k)),true);if(small!=bitmap)bitmap.recycle();bitmap=small;}
  if(approximate)r.particleApproximate++;
  boolean child=o.has("parent")&&!o.isNull("parent");int w=r.scene.width,h=r.scene.height;
  float[] origin=child?WallpaperContent.vector(o.opt("origin"),0,0):WallpaperContent.vector(o.opt("origin"),w/2f,h/2f),scale=WallpaperContent.vector(o.opt("scale"),1,1),angles=WallpaperContent.vector(o.opt("angles"),0,0,0);
  if(origin.length<2||scale.length<2)throw new IOException("粒子变换不完整");float angle=angles.length>=3?angles[2]:0;
  float[] world=world(a,o,byId,origin,scale,angle,w,h);
  WallpaperContent.Layer layer=new WallpaperContent.Layer();layer.name=o.optString("name","粒子");layer.x=0;layer.y=0;layer.w=w;layer.h=h;layer.bitmap=bitmap;
  layer.depth=Math.max(-2,Math.min(2,WallpaperContent.vector(o.opt("parallaxDepth"),1,1)[0]));layer.alpha=(float)Math.max(0,Math.min(1,number(o.opt("alpha"),1)));
  layer.objectId=o.has("id")?o.optInt("id"):-1;layer.visible=visible(o);
  if(child&&r.scriptScene!=null)layer.frame=world(a,o,byId,new float[]{0,0},new float[]{1,1},0,w,h);
  JSONObject data=new JSONObject().put("system",system).put("additive","additive".equals(pass.optString("blending"))).put("transform",new JSONArray().put(world[0]).put(world[1]).put(world[2]).put(world[3]).put(world[4]));
  JSONObject override=o.optJSONObject("instanceoverride");if(override!=null)data.put("override",override);if(frames!=null)data.put("frames",frames);
  layer.particleData=data.toString();return layer;
 }
 /** Longest canvas edge the phone needs at full detail: a 2400 px screen, with room for zoom and paging. */
 static final int DISPLAY_EDGE=3000;
 /**
  * Large canvases (4K and up) shown on a phone never need more pixels per layer than the canvas is drawn at, so a layer
  * is decoded at most at DISPLAY_EDGE / canvas edge of its on-canvas size. Keeps many-layer 4K scenes inside the budget
  * instead of dropping whole characters.
  */
 static Bitmap toDisplayDensity(Bitmap bitmap,JSONObject o,int canvasWidth,int canvasHeight,int atlasFrames){
  float density=Math.min(1,DISPLAY_EDGE/(float)Math.max(canvasWidth,canvasHeight));
  float[] size=WallpaperContent.vector(o.opt("size"),bitmap.getWidth(),bitmap.getHeight()),scale=WallpaperContent.vector(o.opt("scale"),1,1);
  if(size.length<2||scale.length<2)return bitmap;
  // A sprite sheet holds several frames side by side; its useful size is per frame, so keep the atlas proportionally larger.
  float shown=Math.abs(size[0]*scale[0])*density*(float)Math.sqrt(atlasFrames),factor=Math.min(1,shown/Math.max(1,bitmap.getWidth()));
  factor=Math.min(factor,Math.min(1,Math.abs(size[1]*scale[1])*density*(float)Math.sqrt(atlasFrames)/Math.max(1,bitmap.getHeight())));
  if(!(factor<.98f)||!Float.isFinite(factor))return bitmap;
  factor=Math.max(factor,.05f);
  Bitmap smaller=Bitmap.createScaledBitmap(bitmap,Math.max(1,Math.round(bitmap.getWidth()*factor)),Math.max(1,Math.round(bitmap.getHeight()*factor)),true);
  if(smaller!=bitmap)bitmap.recycle();return smaller;
 }
 /** Image layers in the scene; particle layers have small textures and are counted separately. */
 static int imageLayers(WallpaperContent.Scene scene){int n=0;for(WallpaperContent.Layer l:scene.layers)if(l.particleData==null)n++;return n;}
 /**
  * The scene's mouse-driven camera becomes the phone's tilt parallax: on/off and influence come from the author's
  * camera settings (resolved from their properties), and a camera zoom above 1 becomes the default zoom.
  */
 static void camera(JSONObject general,JSONObject effects)throws JSONException {
  Object parallax=value(general.opt("cameraparallax"));if(parallax!=null&&parallax!=JSONObject.NULL)effects.put("gyro",truthy(parallax));
  double influence=number(general.opt("cameraparallaxmouseinfluence"),-1),amount=number(general.opt("cameraparallaxamount"),-1);
  if(influence>=0)effects.put("strength",Math.max(.1,Math.min(1,influence*2)));
  if(amount>0)effects.put("layerDepth",Math.max(.25,Math.min(2,amount*10)));
  double zoom=number(general.opt("zoom"),1);if(zoom>1.01)effects.put("zoom",Math.min(2.5,zoom));
 }
 /** Author properties from project.json: name → {type, value, text, options}. */
 static JSONObject projectProperties(ContentArchive a){
  try{if(!a.has("project.json"))return new JSONObject();JSONObject general=WallpaperContent.json(a,"project.json").optJSONObject("general");JSONObject all=general==null?null:general.optJSONObject("properties");return all==null?new JSONObject():all;}
  catch(Exception unreadable){return new JSONObject();}
 }
 /**
  * Bound values ({"user": name, "value": saved}) take the property's value from project.json, as Wallpaper Engine does;
  * combo conditions ({"user": {"name", "condition"}}) become true when the property equals the condition.
  */
 static void resolve(Object value,JSONObject properties)throws JSONException {
  if(value instanceof JSONObject o){
   Object user=o.opt("user");
   if(user instanceof String name){JSONObject p=properties.optJSONObject(name);if(p!=null&&p.has("value")&&!"group".equals(p.optString("type"))&&!"text".equals(p.optString("type")))o.put("value",p.opt("value"));}
   else if(user instanceof JSONObject bound){JSONObject p=properties.optJSONObject(bound.optString("name"));if(p!=null&&p.has("value"))o.put("value",String.valueOf(p.opt("value")).equals(bound.optString("condition")));}
   for(java.util.Iterator<String> keys=o.keys();keys.hasNext();)resolve(o.opt(keys.next()),properties);
  }else if(value instanceof JSONArray list)for(int i=0;i<list.length();i++)resolve(list.opt(i),properties);
 }
 /** Whether an object is drawn: fixed visibility along its parents, plus the author-property conditions that can change it. */
 static final class Visibility {boolean fixed=true;final JSONArray when=new JSONArray();}
 static Visibility visibility(JSONObject o,java.util.Map<Integer,JSONObject> byId,JSONObject properties)throws JSONException {
  Visibility v=new Visibility();JSONObject current=o;
  for(int depth=0;current!=null&&depth<32;depth++){
   Object raw=current.opt("visible");
   if(raw instanceof JSONObject bound&&bound.opt("user")!=null&&bound.opt("user")!=JSONObject.NULL){
    Object user=bound.opt("user");String name=user instanceof JSONObject b?b.optString("name"):String.valueOf(user);
    // Only properties the author actually declares become switches; otherwise the saved state is final.
    if(properties.optJSONObject(name)==null){if(!visible(current))v.fixed=false;}
    else if(user instanceof JSONObject b)v.when.put(new JSONArray().put(name).put(b.optString("condition")));
    else v.when.put(new JSONArray().put(name).put(JSONObject.NULL));
   }else if(!visible(current))v.fixed=false;
   if(current.optBoolean("disablepropagation")||!current.has("parent")||current.isNull("parent"))break;
   current=byId.get((int)number(current.opt("parent"),-1));
  }
  if(!v.fixed)return v;
  // Property-bound visibility: kept so the phone can switch it, hidden now when its default is off.
  return v;
 }
 /** The default (project.json) state of property conditions. */
 static boolean conditionsMet(JSONArray when,JSONObject properties){
  for(int i=0;i<when.length();i++){JSONArray c=when.optJSONArray(i);JSONObject p=properties.optJSONObject(c.optString(0));if(p==null)continue;Object value=p.opt("value");
   if(c.isNull(1)?!truthy(value):!String.valueOf(value).equals(c.optString(1)))return false;}
  return true;
 }
 static boolean truthy(Object v){return v!=null&&v!=JSONObject.NULL&&!Boolean.FALSE.equals(v)&&!"false".equalsIgnoreCase(String.valueOf(v))&&!"0".equals(String.valueOf(v))&&!String.valueOf(v).isEmpty();}
 /** Records the switch-like properties layers depend on, with a readable label. */
 static void usedProperties(JSONArray when,JSONObject properties,Report r)throws JSONException {
  if(when.length()>0)r.propertyLayers++;
  for(int i=0;i<when.length();i++){
   String name=when.getJSONArray(i).optString(0);if(r.properties.has(name))continue;JSONObject p=properties.optJSONObject(name);if(p==null)continue;
   String type=p.optString("type");if(!type.equals("bool")&&!type.equals("combo"))continue;
   JSONObject out=new JSONObject().put("type",type).put("value",p.opt("value")).put("label",label(p.optString("text",name)));
   if(type.equals("combo")){JSONArray options=new JSONArray();JSONArray list=p.optJSONArray("options");if(list!=null)for(int k=0;k<list.length();k++){JSONObject op=list.optJSONObject(k);if(op!=null)options.put(new JSONArray().put(label(op.optString("label"))).put(String.valueOf(op.opt("value"))));}out.put("options",options);}
   out.put("order",p.optInt("order",0));r.properties.put(name,out);
  }
 }
 /** Property text is HTML; keep the first readable line (authors often put Chinese and English on two lines). */
 static String label(String html){
  String s=html.replaceAll("(?i)<br\\s*/?>|<hr\\s*/?>|</p>|</div>","\n").replaceAll("<[^>]*>","").replace("&nbsp;"," ").replace("&amp;","&").replace("&lt;","<").replace("&gt;",">");
  for(String line:s.split("\n")){String t=line.replaceAll("[\\p{So}\\p{Cn}]","").trim();if(!t.isEmpty())return t.length()>40?t.substring(0,40):t;}
  return "作者设置";
 }
 /** True when a script can show this hidden layer: its own visibility is scripted, or a script names it. */
 static boolean scriptedVisibility(JSONObject o,String sources){
  Object v=o.opt("visible");if(v instanceof JSONObject&&((JSONObject)v).opt("script") instanceof String)return true;
  String name=o.optString("name","");return !name.isEmpty()&&(sources.contains("'"+name+"'")||sources.contains("\""+name+"\"")||sources.contains("`"+name+"`"));
 }
 private static JSONArray array(float[] v)throws JSONException{JSONArray out=new JSONArray();for(float x:v)out.put(Float.isFinite(x)?(double)x:0);return out;}
 /** Saved state of every object, its property scripts and the author's user-property defaults. */
 static JSONObject scriptScene(ContentArchive a,JSONArray objects,int width,int height,StringBuilder sources)throws Exception {
  JSONArray list=new JSONArray();
  for(int i=0;i<objects.length();i++){
   JSONObject o=objects.optJSONObject(i);if(o==null)continue;boolean child=o.has("parent")&&!o.isNull("parent");
   JSONObject d=new JSONObject().put("id",o.optInt("id",-1)).put("name",o.optString("name",""));if(child)d.put("parent",(int)number(o.opt("parent"),-1));
   d.put("origin",array(child?WallpaperContent.vector(o.opt("origin"),0,0,0):WallpaperContent.vector(o.opt("origin"),width/2f,height/2f,0)));
   d.put("scale",array(WallpaperContent.vector(o.opt("scale"),1,1,1))).put("angles",array(WallpaperContent.vector(o.opt("angles"),0,0,0))).put("size",array(WallpaperContent.vector(o.opt("size"),0,0)));
   d.put("alpha",number(o.opt("alpha"),1)).put("visible",visible(o)).put("solid",o.optBoolean("solid"));
   d.put("kind",o.has("particle")&&!o.isNull("particle")?"particle":o.has("text")&&!o.isNull("text")?"text":o.has("sound")&&!o.isNull("sound")?"sound":o.has("image")&&!o.isNull("image")?"image":"other");
   JSONObject scripts=new JSONObject();
   for(java.util.Iterator<String> keys=o.keys();keys.hasNext();){
    String key=keys.next();Object v=o.opt(key);if(!(v instanceof JSONObject))continue;Object source=((JSONObject)v).opt("script");
    if(!(source instanceof String)||((String)source).isBlank())continue;
    JSONObject properties=((JSONObject)v).optJSONObject("scriptproperties");scripts.put(key,new JSONObject().put("source",source).put("properties",properties==null?new JSONObject():properties));sources.append(source).append('\n');
   }
   if(scripts.length()>0)d.put("scripts",scripts);list.put(d);
  }
  JSONObject properties=new JSONObject();
  try{if(a.has("project.json")){JSONObject general=WallpaperContent.json(a,"project.json").optJSONObject("general");JSONObject all=general==null?null:general.optJSONObject("properties");
   if(all!=null)for(java.util.Iterator<String> keys=all.keys();keys.hasNext();){String key=keys.next();JSONObject p=all.optJSONObject(key);if(p!=null&&p.has("value"))properties.put(key,p.opt("value"));}}}catch(Exception unreadable){}
  return new JSONObject().put("width",width).put("height",height).put("properties",properties).put("objects",list);
 }
 /** Rest-pose attachment points of a puppet layer's skeleton, by attachment name. */
 static java.util.Map<String,dev.workshopvideo.core.PuppetSkeleton.Point> attachments(ContentArchive archive,JSONObject layer)throws Exception {
  JSONObject model=WallpaperContent.json(archive,WallpaperContent.asset(archive,layer.getString("image")));String puppet=model.optString("puppet","");
  if(puppet.isEmpty())throw new IOException("父图层没有骨骼挂点");
  return dev.workshopvideo.core.PuppetSkeleton.attachments(archive.read(ContentArchive.path(puppet),64*1024*1024));
 }
 static float[] world(ContentArchive archive,JSONObject o,java.util.Map<Integer,JSONObject> byId,float[] origin,float[] scale,float angle,int canvasWidth,int canvasHeight)throws Exception {
  float x=origin[0],y=origin[1],sx=scale[0],sy=scale[1],a=angle;JSONObject current=o;
  for(int depth=0;current.has("parent")&&!current.isNull("parent");depth++){
   if(depth>16)throw new IOException("父子层级过深");
   JSONObject parent=byId.get((int)number(current.opt("parent"),-1));if(parent==null)throw new IOException("找不到父图层");
   for(String key:new String[]{"origin","scale","angles"}){Object raw=parent.opt(key);if(raw instanceof JSONObject&&((JSONObject)raw).isNull("value"))throw new IOException("父图层变换没有已存默认值");}
   // Attached children sit relative to a point of the parent's skeleton rather than its centre.
   String attachment=current.optString("attachment","");
   if(!attachment.isEmpty()){
    var point=attachments(archive,parent).get(attachment);if(point==null)throw new IOException("父图层没有挂点："+attachment);
    double c=Math.cos(point.angle()),s=Math.sin(point.angle());float ax=x,ay=y;x=point.x()+(float)(ax*c-ay*s);y=point.y()+(float)(ax*s+ay*c);a+=point.angle();
   }
   boolean nested=parent.has("parent")&&!parent.isNull("parent");
   float[] po=nested?WallpaperContent.vector(parent.opt("origin"),0,0):WallpaperContent.vector(parent.opt("origin"),canvasWidth/2f,canvasHeight/2f),ps=WallpaperContent.vector(parent.opt("scale"),1,1),pa=WallpaperContent.vector(parent.opt("angles"),0,0,0);
   if(po.length<2||ps.length<2||pa.length<3)throw new IOException("父图层变换不完整");
   if(ps[0]<=0||ps[1]<=0||Math.abs(pa[0])>.001||Math.abs(pa[1])>.001)throw new IOException("父图层镜像或 3D 旋转");
   double c=Math.cos(pa[2]),s=Math.sin(pa[2]);float lx=x*ps[0],ly=y*ps[1];
   x=po[0]+(float)(lx*c-ly*s);y=po[1]+(float)(lx*s+ly*c);sx*=ps[0];sy*=ps[1];a+=pa[2];current=parent;
  }
  return new float[]{x,y,sx,sy,a};
 }
 static void write(Report report,File target)throws Exception {write(report,target,null);}
 /** Effects are translated here; without a translator, or when one is rejected, only that effect is left out. */
 static void write(Report report,File target,SceneEffects.Translator translator)throws Exception {
  if(!report.usable())throw new IOException("此场景无法生成手机适配版");
  for(WallpaperContent.Layer l:report.scene.layers)for(java.util.Iterator<SceneEffects.Effect> it=l.effects.iterator();it.hasNext();){
   SceneEffects.Effect effect=it.next();if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("适配已暂停");
   java.util.ArrayList<String> failures=new java.util.ArrayList<>();
   if(translator!=null&&SceneEffects.translate(effect,translator,failures))continue;
   it.remove();effect.recycle();report.unkeep(effect.name);report.drop(effect.name,translator==null?"此设备不支持特效转换":failures.isEmpty()?"转换失败":failures.get(0).substring(effect.name.length()+1));
  }
  fit(report.scene,LOAD_BUDGET);
  JSONObject root=new JSONObject().put("format","WallpaperMobileScene/1").put("width",report.scene.width).put("height",report.scene.height).put("adapted",true).put("adaptation",report.describe());
  JSONObject effects=EffectOptions.normalize(null);effects.put("gyro",true);effects.put("touch",true);camera(report.general,effects);root.put("effects",effects);if(report.properties.length()>0)root.put("properties",report.properties);JSONArray layers=new JSONArray();
  boolean done=false;try{
   try(ZipOutputStream zip=new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(target)))){int index=0;for(WallpaperContent.Layer l:report.scene.layers){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("适配已暂停");String name="layers/"+(index++)+".png";zip.putNextEntry(new ZipEntry(name));if(!l.bitmap.compress(Bitmap.CompressFormat.PNG,100,zip))throw new IOException("无法保存图层");zip.closeEntry();JSONObject layer=new JSONObject().put("file",name).put("name",l.name).put("x",l.x).put("y",l.y).put("width",l.w).put("height",l.h).put("rotation",l.rotation).put("depth",l.depth).put("opacity",l.alpha);if(l.frames.size()>1){JSONArray frames=new JSONArray();for(var f:l.frames)frames.put(new JSONArray().put(f.u0()).put(f.v0()).put(f.u1()).put(f.v1()).put(f.seconds()));layer.put("frames",frames);}if(!l.effects.isEmpty())writeEffects(zip,l,index-1,layer);
    if(l.puppet!=null){String mdl="layers/"+(index-1)+".mdl";zip.putNextEntry(new ZipEntry(mdl));zip.write(l.puppetData);zip.closeEntry();layer.put("puppet",mdl).put("puppetSize",new JSONArray().put(l.puppetW).put(l.puppetH));
     JSONArray tracks=new JSONArray();for(var t:l.tracks)tracks.put(new JSONArray().put(t.animation()).put(t.rate()).put(t.blend()).put(t.additive()));if(tracks.length()>0)layer.put("tracks",tracks);}
    if(l.particleData!=null){String file="layers/"+(index-1)+"-particles.json";zip.putNextEntry(new ZipEntry(file));zip.write(l.particleData.getBytes(StandardCharsets.UTF_8));zip.closeEntry();layer.put("particlesFile",file);}
    if(report.scriptScene!=null){layer.put("objectId",l.objectId).put("base",new JSONArray().put(l.baseW).put(l.baseH));if(!l.visible)layer.put("visible",false);if(l.frame!=null)layer.put("frame",array(l.frame));}
    if(l.when!=null&&l.when.length()>0)layer.put("when",l.when);
    if(l.blend!=0)layer.put("blend",l.blend);if(l.additive)layer.put("additive",true);
    if(l.attachParent>=0)layer.put("attach",new JSONObject().put("layer",l.attachParent).put("name",l.attachName).put("x",l.attachX).put("y",l.attachY).put("angle",l.attachAngle));if(l.reveal!=null){String reveal="layers/xray-"+index+".png";zip.putNextEntry(new ZipEntry(reveal));if(!l.reveal.compress(Bitmap.CompressFormat.PNG,100,zip))throw new IOException("无法保存 X-ray 图层");zip.closeEntry();layer.put("xrayFile",reveal).put("xraySize",l.revealSize);}layers.put(layer);}root.put("layers",layers);if(report.scriptScene!=null){zip.putNextEntry(new ZipEntry("scripts.json"));zip.write(report.scriptScene.toString().getBytes(StandardCharsets.UTF_8));zip.closeEntry();root.put("scripts","scripts.json");}zip.putNextEntry(new ZipEntry("wallpaper-mobile.json"));zip.write(root.toString().getBytes(StandardCharsets.UTF_8));zip.closeEntry();}
   try(WallpaperContent.Scene verify=WallpaperContent.scene(target)){if(verify.layers.size()!=layers.length())throw new IOException("适配版校验未通过");}done=true;
  }finally{if(!done)target.delete();}
 }
 private static void writeEffects(ZipOutputStream zip,WallpaperContent.Layer l,int index,JSONObject layer)throws Exception {
  JSONArray list=new JSONArray();int number=0;
  for(SceneEffects.Effect effect:l.effects){
   java.util.ArrayList<String> files=new java.util.ArrayList<>();
   for(int slot=0;slot<effect.textures.size();slot++){
    Object texture=effect.textures.get(slot);if(!(texture instanceof Bitmap bitmap)){files.add(null);continue;}
    String name="layers/fx-"+index+"-"+number+"-"+slot+".png";zip.putNextEntry(new ZipEntry(name));if(!bitmap.compress(Bitmap.CompressFormat.PNG,100,zip))throw new IOException("无法保存特效贴图");zip.closeEntry();files.add(name);
   }
   list.put(SceneEffects.describe(effect,files));number++;
  }
  String file="layers/"+index+"-effects.json";zip.putNextEntry(new ZipEntry(file));zip.write(list.toString().getBytes(StandardCharsets.UTF_8));zip.closeEntry();layer.put("effectsFile",file);
 }
 /** Pixels the phone may decode for one scene (layers, X-ray images and effect masks), as WallpaperContent enforces. */
 static final long LOAD_BUDGET=16L*1024*1024;
 /** Shrinks effect masks first (they only weight motion), then all images evenly, until the scene fits the load budget. */
 static void fit(WallpaperContent.Scene scene,long budget){
  long target=budget*15/16;
  for(int maskEdge:new int[]{1024,512,256}){if(pixels(scene)<=target)return;for(WallpaperContent.Layer l:scene.layers)for(SceneEffects.Effect e:l.effects)for(int i=0;i<e.textures.size();i++)if(e.textures.get(i) instanceof Bitmap b)e.textures.set(i,shrink(b,maskEdge/(float)Math.max(b.getWidth(),b.getHeight())));}
  for(int round=0;round<4&&pixels(scene)>target;round++){
   float factor=(float)Math.sqrt(target/(double)pixels(scene))*.98f;
   for(WallpaperContent.Layer l:scene.layers){l.bitmap=shrink(l.bitmap,factor);if(l.reveal!=null)l.reveal=shrink(l.reveal,factor);}
  }
 }
 private static long pixels(WallpaperContent.Scene scene){
  long total=0;for(WallpaperContent.Layer l:scene.layers){total+=(long)l.bitmap.getWidth()*l.bitmap.getHeight();if(l.reveal!=null)total+=(long)l.reveal.getWidth()*l.reveal.getHeight();for(SceneEffects.Effect e:l.effects)for(Object t:e.textures)if(t instanceof Bitmap b)total+=(long)b.getWidth()*b.getHeight();}
  return total;
 }
 private static Bitmap shrink(Bitmap source,float factor){
  if(factor>=1)return source;Bitmap smaller=Bitmap.createScaledBitmap(source,Math.max(1,Math.round(source.getWidth()*factor)),Math.max(1,Math.round(source.getHeight()*factor)),true);if(smaller!=source)source.recycle();return smaller;
 }
 private SceneAdapter(){}
}
