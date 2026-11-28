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
  int objects,images,particles,texts,sounds,models,scripts,bindings,xrays,animations,puppets,puppetMotions,puppetStatic,blended,solidLayers,composeLayers,fullscreenLayers,textLayers,shapeLayers,shaped,videos,sourceLayers,xrayShaders,effectGroups,multiPass,particleSystems,particleApproximate,particleChildren;
  /** Fonts text layers name that neither the scene nor Wallpaper Engine's assets provide. */
  final java.util.TreeSet<String> missingFonts=new java.util.TreeSet<>();
  /** Fonts read during conversion, shared by every text layer that uses them. */
  final java.util.HashMap<String,byte[]> fonts=new java.util.HashMap<>();final java.util.HashMap<String,android.graphics.Typeface> typefaces=new java.util.HashMap<>();
  /** Author effects that run from the package's own shaders, and the ones left out with their reason. */
  final LinkedHashMap<String,Integer> keptEffects=new LinkedHashMap<>();final LinkedHashMap<String,String> droppedEffects=new LinkedHashMap<>();
  void keep(String name){keptEffects.merge(name,1,Integer::sum);}
  void drop(String name,String reason){droppedEffects.putIfAbsent(name,reason);}
  void unkeep(String name){keptEffects.computeIfPresent(name,(k,v)->v>1?v-1:null);}
  String error="";
  /** A visible video-texture layer covering most of the canvas: kept as a video wallpaper when no image layer survives. */
  String videoTexture="";float videoCover;
  /** Wallpaper Engine's own assets were available to fill in engine models, textures and headers. */
  boolean engineAssets;
  /** The scene's camera object (zoom and centre), when the author placed one. */
  JSONObject cameraObject;
  /** Every scene object with its saved properties and scripts, run on the phone by assets/scene-script-runtime.js. */
  JSONObject scriptScene;String scriptSources="";int scriptedHidden;
  /** scene.json as read, to tell settings the scene never uses (they do nothing on the PC either). */
  String sceneText="";
  /** All author properties (project.json), and the share of full resolution images keep so every layer fits the phone. */
  JSONObject declared=new JSONObject();float density=1;boolean bloom;
  /** Object id → parent object id, from scene.json. */
  final java.util.HashMap<Integer,Integer> parentOf=new java.util.HashMap<>();
  /** Objects whose scripts reach other layers or scripts (shared state, getLayer…): their parameters matter even when the object itself is not drawn. */
  final java.util.Set<Integer> reachingScripts=new java.util.HashSet<>();
  /** Author properties (project.json) the converted layers depend on, offered as switches on the phone. */
  final JSONObject properties=new JSONObject();int propertyLayers;JSONObject general=new JSONObject();
  void skip(String reason){skipped.put(reason,skipped.getOrDefault(reason,0)+1);}
  boolean usable(){return !scene.layers.isEmpty()&&error.isEmpty();}
  String describe(){
   StringBuilder s=new StringBuilder("原作：").append(objects).append(" 个对象，图片 ").append(images).append("、粒子 ").append(particles).append("、文字 ").append(texts).append("、音频 ").append(sounds).append("、模型 ").append(models).append("。\n");
   s.append("实际解码可保留：").append(scene.layers.size()).append(" 个图片图层（").append(scene.width).append(" × ").append(scene.height).append("）。\n");
   if(puppets>0)s.append("骨骼图层：").append(puppets).append(" 个按原作网格绘制").append(puppetMotions>0?"，其中 "+puppetMotions+" 个播放骨骼动画":"").append("。\n");
   if(density<.995f)s.append("为在本机内存内保留全部图层，图片清晰度按比例降低到原来的 ").append(Math.round(density*100)).append("%。").append((char)10);
   if(multiPass>0)s.append("多步骤特效（模糊、光线、辉光等）：").append(multiPass).append(" 个按原作的渲染步骤运行。\n");
   if(shaped>0)s.append("镜像或倾斜的图层：").append(shaped).append(" 个，按正交投影绘制（透视图层近似）。").append((char)10);
   if(videos>0)s.append("视频图层：").append(videos).append(" 个，在手机上静音循环播放，作者特效逐帧作用于视频。").append((char)10);
   if(xrayShaders>0)s.append("X-ray（作者着色器，含遮罩或引用其他图层）：").append(xrayShaders).append(" 个，手指按住处显示。").append((char)10);
   if(sourceLayers>0)s.append("被其他图层特效引用的隐藏图层：").append(sourceLayers).append(" 个，只作为特效素材渲染，不直接显示。").append((char)10);
   if(shapeLayers>0)s.append("形状层：").append(shapeLayers).append(" 个（如光束），由作者特效绘制。").append((char)10);
   if(textLayers>0)s.append("文字图层：").append(textLayers).append(" 个，时钟、日期等由作者脚本实时更新").append(missingFonts.isEmpty()?"":"；缺少字体 "+String.join("、",missingFonts)+"，用系统字体代替").append("。").append((char)10);
   if(bloom)s.append("泛光：按 Wallpaper Engine 的泛光通道（阈值、强度、色调）在手机上渲染。").append((char)10);
   if(fullscreenLayers>0)s.append("全屏后期层：").append(fullscreenLayers).append(" 个，作者特效作用于其下方的整个画面。").append((char)10);
   if(composeLayers>0)s.append("合成层：").append(composeLayers).append(" 个，其中的图层合并后再应用作者特效。").append((char)10);
   if(solidLayers>0)s.append("纯色图层：").append(solidLayers).append(" 个。\n");
   s.append(engineAssets?"已使用 Wallpaper Engine 原版素材补齐内置图层、贴图和着色器头文件。\n":"未下载 Wallpaper Engine 素材：内置图层和引擎贴图可能缺失或使用近似版，可在「通用设置 → Wallpaper Engine 素材」下载后重新下载此场景。\n");
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
   s.append("适配版不保留音频响应和 3D 模型。可使用已保留的特效、X-ray、手机陀螺仪、触控及桌面滑动效果。");
   if(!error.isEmpty())s.append("\n无法适配：").append(error);
   return s.toString();
  }
 }
 static Object value(Object v){return v instanceof JSONObject?((JSONObject)v).opt("value"):v;}
 static double number(Object v,double fallback){try{return Double.parseDouble(String.valueOf(value(v)));}catch(Exception e){return fallback;}}
 /**
  * Whether an object hidden by default still renders, as the texture of another layer's effect: it or an ancestor is
  * named as a layer composite, and nothing between it and that ancestor is hidden on its own (children hidden inside
  * a group are not part of its result).
  */
 static boolean composited(JSONObject o,java.util.Map<Integer,JSONObject> byId,java.util.Set<Integer> referenced){
  JSONObject current=o;boolean hiddenBelow=false;
  for(int depth=0;current!=null&&depth<32;depth++){
   if(current.has("id")&&referenced.contains(current.optInt("id")))return !hiddenBelow;
   if(!visible(current))hiddenBelow=true;
   if(!current.has("parent")||current.isNull("parent"))break;current=byId.get((int)number(current.opt("parent"),-1));
  }
  return false;
 }
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
 /** Raise whenever conversion output improves; saved scenes made by an older version are converted again from their originals. */
 static final int VERSION=47;
 /** Wallpaper Engine's own assets (downloaded on the phone, or the desktop install in checks); null when unavailable. */
 static volatile File engineRoot;
 static Report inspect(ContentArchive a)throws Exception {
  if(a.fallbackRoot()==null&&engineRoot!=null)a.fallback(engineRoot);
  Report r=new Report();r.engineAssets=a.fallbackRoot()!=null;dev.workshopvideo.core.SceneBudget budget=dev.workshopvideo.core.SceneBudget.current();try{
   if(a.has("wallpaper-mobile.json"))throw new IOException("已经是手机场景，无需适配");
   String entry="scene.json";if(a.has("project.json")){String declared=WallpaperContent.json(a,"project.json").optString("file");if(declared.endsWith(".json")&&a.has(declared))entry=declared;}
   JSONObject root=WallpaperContent.json(a,entry);scan(root,r);
   JSONObject projectProperties=projectProperties(a);resolve(root,projectProperties);r.declared=projectProperties;r.sceneText=root.toString();
   r.general=root.optJSONObject("general")==null?new JSONObject():root.optJSONObject("general");cameraSettings(root,projectProperties,r);
   JSONObject general=root.optJSONObject("general"),projection=general==null?null:general.optJSONObject("orthogonalprojection");
   if(projection==null)throw new IOException("缺少正交画布尺寸，无法可靠恢复布局");
   r.scene.width=projection.optInt("width",0);r.scene.height=projection.optInt("height",0);
   if(r.scene.width<1||r.scene.height<1||r.scene.width>16384||r.scene.height>16384)throw new IOException("画布尺寸无效");
   JSONArray objects=root.getJSONArray("objects");r.objects=objects.length();if(r.objects>4096)throw new IOException("对象数量超出分析预算");
   long pixels=0;JSONObject includes=null;
   if(r.scripts>0){StringBuilder sources=new StringBuilder();r.scriptScene=scriptScene(a,objects,r.scene.width,r.scene.height,sources,projectProperties,r);r.scriptSources=sources.toString();}
   java.util.HashMap<Integer,Integer> outputIndex=new java.util.HashMap<>();java.util.IdentityHashMap<WallpaperContent.Layer,Integer> attachSources=new java.util.IdentityHashMap<>();
   java.util.HashMap<Integer,JSONObject> byId=new java.util.HashMap<>();for(int i=0;i<objects.length();i++){JSONObject o=objects.optJSONObject(i);if(o!=null&&o.has("id")){byId.put(o.optInt("id"),o);if(o.has("parent")&&!o.isNull("parent"))r.parentOf.put(o.optInt("id"),(int)number(o.opt("parent"),-1));}}
   // Objects whose rendered result other layers' effects sample (_rt_imageLayerComposite_<id>_a): kept even when hidden.
   java.util.Set<Integer> referenced=new java.util.HashSet<>();
   for(int i=0;i<objects.length();i++){JSONObject o=objects.optJSONObject(i);JSONArray fx=o==null?null:o.optJSONArray("effects");if(fx!=null)for(int e=0;e<fx.length();e++){JSONObject effect=fx.optJSONObject(e);if(effect!=null)referenced.addAll(SceneEffects.composites(effect));}}
   for(int i=0;i<objects.length();i++){
    if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("已停止适配");
    JSONObject o=objects.getJSONObject(i);String image=o.optString("image","");boolean imageObject=!image.isEmpty()&&!image.equals("null");
    if(imageObject)r.images++;if(o.has("particle")&&!o.isNull("particle"))r.particles++;if(o.has("text")&&!o.isNull("text"))r.texts++;if(o.has("sound")&&!o.isNull("sound"))r.sounds++;if(o.has("model")&&!o.isNull("model"))r.models++;
    if(!imageObject&&o.has("particle")&&!o.isNull("particle")){
     Visibility shown=visibility(o,byId,projectProperties);
     if(!shown.fixed&&(r.scriptScene==null||!scriptedVisibility(o,r.scriptSources,byId))){r.skip("默认隐藏");continue;}
     try{WallpaperContent.Layer layer=particleLayer(a,o,byId,r);layer.when=shown.when;usedProperties(shown.when,projectProperties,r);if(!shown.fixed)layer.visible=false;if(o.has("id"))outputIndex.put(o.optInt("id"),r.scene.layers.size());r.scene.layers.add(layer);r.particleSystems++;pixels+=(long)layer.bitmap.getWidth()*layer.bitmap.getHeight();}
     catch(InterruptedIOException stop){throw stop;}catch(Exception e){r.skip("粒子："+(e.getMessage()==null?"无法读取":e.getMessage()));}
     continue;
    }
    boolean textObject=!imageObject&&o.has("text")&&!o.isNull("text");
    // Shape objects ("shape": "quad", e.g. the editor's light shafts preset) have no image: their effects draw everything.
    // Without a saved size they span the canvas.
    boolean shapeObject=!imageObject&&!textObject&&"quad".equals(o.optString("shape"));if(shapeObject&&!o.has("size"))o.put("size",r.scene.width+" "+r.scene.height);
    if(!imageObject&&!textObject&&!shapeObject)continue;boolean startHidden=false;
    Visibility shown=visibility(o,byId,projectProperties);
    boolean sourceOnly=false;
    if(!shown.fixed){if(composited(o,byId,referenced))sourceOnly=true;else if(r.scriptScene==null||!scriptedVisibility(o,r.scriptSources,byId)){r.skip("默认隐藏");continue;}startHidden=true;}
    Bitmap bitmap=null;SceneText text=null;
    try{
     // Skeletal layers keep their rest pose; the image quad is the puppet's bind pose.
     dev.workshopvideo.core.PuppetModel puppetModel=null;byte[] puppetBytes=null,videoData=null;
     for(String key:new String[]{"origin","size","scale","angles"}){Object raw=o.opt(key);if(raw instanceof JSONObject&&((JSONObject)raw).isNull("value"))throw new IOException("图层变换没有已存默认值");}
     float[] crop=WallpaperContent.vector(o.opt("cropoffset"),0,0);for(float v:crop)if(Math.abs(v)>.001)throw new IOException("非零裁切偏移");
     JSONObject model=textObject||shapeObject?new JSONObject():WallpaperContent.json(a,WallpaperContent.asset(a,image));scan(model,r);
     String puppetPath=model.optString("puppet","");
     if(!puppetPath.isEmpty()){
      try{puppetBytes=a.read(ContentArchive.path(puppetPath),64*1024*1024);puppetModel=dev.workshopvideo.core.PuppetModel.parse(puppetBytes);if(puppetModel.mesh==null)throw new IOException("骨骼网格格式不支持");}
      catch(InterruptedIOException stop){throw stop;}catch(IOException unsupported){puppetModel=null;puppetBytes=null;r.puppetStatic++;}
     }
     // Engine layer models: groups only arrange their children; full-screen layers post-process the whole frame.
     boolean compose=model.optBoolean("passthrough"),fullscreen=compose&&model.optBoolean("fullscreen");if(fullscreen)r.fullscreenLayers++;else if(compose)r.composeLayers++;
     boolean solid=model.optBoolean("solidlayer");if(solid)r.solidLayers++;
     JSONObject pass=new JSONObject();
     if(!compose&&!textObject&&!shapeObject){
     JSONObject mat=WallpaperContent.json(a,WallpaperContent.asset(a,model.getString("material")));scan(mat,r);
     JSONArray passes=mat.optJSONArray("passes");if(passes==null||passes.length()!=1)throw new IOException("多通道或非图片材质");
     pass=passes.getJSONObject(0);String shader=pass.optString("shader");if(!solid&&!shader.equals("genericimage2")&&!shader.equals("genericimage4"))throw new IOException("自定义着色器");
     }
     // Blend modes other than normal need a compositor; don't render additive masks as opaque images.
     Object passBlending=pass.opt("blending");if(passBlending instanceof Number&&((Number)passBlending).intValue()>1)throw new IOException("非标准混合模式");
     int colorBlend=(int)number(o.opt("colorBlendMode"),0);if(colorBlend<0||colorBlend>32)throw new IOException("未知的混合模式");boolean additive="additive".equals(pass.optString("blending"));
     java.util.List<dev.workshopvideo.core.TexturePixels.Frame> frames;
     // Instanced models (official solidlayer_instance materials) take their textures from the object: instance.textures
     // replaces the material's, and instance.usertextures binds a slot to an author property (a picture chosen by the user).
     JSONObject instance=o.optJSONObject("instance");JSONArray instanceTextures=instance==null?null:instance.optJSONArray("textures");
     String instanceTexture=instanceTextures!=null&&instanceTextures.opt(0) instanceof String t0&&!t0.isEmpty()&&!t0.equals("util/white")?t0:null;
     JSONArray userTextures=instance==null?null:instance.optJSONArray("usertextures");
     String textureProperty=userTextures!=null&&userTextures.opt(0) instanceof String u0?declared(u0,projectProperties):null;
     if(shapeObject){frames=java.util.List.of();bitmap=clearBitmap(o);r.shapeLayers++;}
     else if(textObject){frames=java.util.List.of();text=text(a,o,projectProperties,r);bitmap=text.draw();r.textLayers++;}
     else if(solid&&instanceTexture==null||compose){frames=java.util.List.of();bitmap=compose?Bitmap.createBitmap(new int[]{0},1,1,Bitmap.Config.ARGB_8888):solidBitmap(o);}
     else{
     JSONArray textures=pass.optJSONArray("textures");if(instanceTexture==null&&(textures==null||textures.length()!=1||!(textures.opt(0) instanceof String)))throw new IOException("没有可解码的基础图片纹理");
     String texture=instanceTexture!=null?instanceTexture:textures.getString(0);byte[] raw=a.read(WallpaperContent.asset(a,texture),32*1024*1024);
     if(dev.workshopvideo.core.TexturePixels.video(raw)){
      float[] vs=WallpaperContent.vector(o.opt("size"),r.scene.width,r.scene.height),vk=WallpaperContent.vector(o.opt("scale"),1,1);
      float cover=vs.length>=2&&vk.length>=2?Math.abs(vs[0]*vk[0]*vs[1]*vk[1])/((float)r.scene.width*r.scene.height):0;
      if(r.videoTexture.isEmpty()&&cover>=.9f&&!o.has("parent")){r.videoTexture=WallpaperContent.asset(a,texture);r.videoCover=cover;}
      // The layer plays its MP4 on the phone; the first frame stands in until the decoder delivers one.
      videoData=dev.workshopvideo.core.TexturePixels.mp4(raw);raw=null;if(videoData.length>WallpaperContent.VIDEO_LIMIT)throw new IOException("图层视频超过 64 MB");
      frames=java.util.List.of();bitmap=videoPoster(videoData,vs.length>=2?vs[0]:r.scene.width,vs.length>=2?vs[1]:r.scene.height);r.videos++;
     }else{
     frames=dev.workshopvideo.core.TexturePixels.sprite(raw)?dev.workshopvideo.core.TexturePixels.frames(raw):java.util.List.of();
     bitmap=WallpaperContent.bitmap(raw,!frames.isEmpty());raw=null;
     }
     }
     bitmap=toDisplayDensity(bitmap,o,r.scene.width,r.scene.height,frames.isEmpty()?1:Math.max(1,frames.size()));
     // Every layer is kept: when the scene outgrows this phone's memory, all images step down in resolution together.
     bitmap=shrink(bitmap,r.density);
     if(pixels+(long)bitmap.getWidth()*bitmap.getHeight()>budget.pixels()){
      float f=Math.max(.1f,(float)Math.sqrt(budget.pixels()*.85/(double)(pixels+(long)bitmap.getWidth()*bitmap.getHeight())));r.density*=f;
      for(WallpaperContent.Layer kept:r.scene.layers){kept.bitmap=shrink(kept.bitmap,f);if(kept.reveal!=null)kept.reveal=shrink(kept.reveal,f);}
      bitmap=shrink(bitmap,f);pixels=pixels(r.scene);
     }
     boolean child=o.has("parent")&&!o.isNull("parent");float[] localOrigin=null;float localAngle=0;
     float[] pos=child?WallpaperContent.vector(o.opt("origin"),0,0):WallpaperContent.vector(o.opt("origin"),r.scene.width/2f,r.scene.height/2f),sz=WallpaperContent.vector(o.opt("size"),bitmap.getWidth(),bitmap.getHeight()),scale=WallpaperContent.vector(o.opt("scale"),1,1),angles=WallpaperContent.vector(o.opt("angles"),0,0,0),depth=WallpaperContent.vector(o.opt("parallaxDepth"),1,1);
     if(text!=null)sz=new float[]{text.blockW,text.blockH};
     // A composition layer without a saved size covers the canvas.
     if(compose&&!o.has("size"))sz=new float[]{r.scene.width,r.scene.height};
     if(pos.length>=2&&angles.length>=3){localOrigin=new float[]{pos[0],pos[1]};localAngle=angles[2];}
     if(pos.length>=2&&scale.length>=2&&angles.length>=3){float[] world=world(a,o,byId,pos,scale,angles[2],r.scene.width,r.scene.height);pos=new float[]{world[0],world[1]};scale=new float[]{world[2],world[3]};angles=new float[]{angles[0],angles[1],world[4]};}
     if(pos.length<2||sz.length<2||scale.length<2||angles.length<3)throw new IOException("不完整的图层变换");
     // Mirroring (negative scale) and x/y rotation project, orthographically, to a 2×2 matrix on the layer's axes (Euler order z·y·x).
     if(Math.abs(scale[0])<1e-6||Math.abs(scale[1])<1e-6)throw new IOException("图层缩放为零");
     float[] shape=null;float sgnX=Math.signum(scale[0]),sgnY=Math.signum(scale[1]);double tiltX=angles[0],tiltY=angles[1];
     if(sgnX<0||sgnY<0||Math.abs(tiltX)>.001||Math.abs(tiltY)>.001){shape=new float[]{(float)(sgnX*Math.cos(tiltY)),(float)(-sgnY*Math.sin(tiltX)*Math.sin(tiltY)),0,(float)(sgnY*Math.cos(tiltX))};r.shaped++;}
     scale=new float[]{Math.abs(scale[0]),Math.abs(scale[1])};
     WallpaperContent.Layer layer=new WallpaperContent.Layer();layer.name=o.optString("name","图层 "+(i+1));layer.w=sz[0]*scale[0];layer.h=sz[1]*scale[1];layer.x=pos[0]-layer.w/2;layer.y=r.scene.height-pos[1]-layer.h/2;layer.rotation=-(float)Math.toDegrees(angles[2]);layer.depth=Math.max(-2,Math.min(2,depth[0]));layer.alpha=(float)Math.max(0,Math.min(1,number(o.opt("alpha"),1)));layer.frames=frames;layer.compose=compose;layer.fullscreen=fullscreen;
     float[] tint=WallpaperContent.vector(o.opt("color"),1,1,1);if(tint.length>=3)layer.tint=new float[]{tint[0],tint[1],tint[2]};
     if(text!=null){layer.text=text;layer.fontData=text.fontData;layer.fontName=text.fontName;text.fontData=null;if(text.opaque)layer.tint=new float[]{1,1,1};}
     String tintProperty=text!=null&&text.opaque?null:declared(bound(o.opt("color")),projectProperties);if(tintProperty!=null){layer.tintProperty=tintProperty;offer(tintProperty,new JSONObject().put("kind","color"),projectProperties,r);}
     layer.baseW=sz[0];layer.baseH=sz[1];transformChain(o,byId,layer,projectProperties,r,r.scene.width,r.scene.height);
     layer.shape=shape;layer.videoData=videoData;
     if(textureProperty!=null&&frames.isEmpty()){layer.textureProperty=textureProperty;offer(textureProperty,new JSONObject().put("kind","texture"),projectProperties,r);}
     String alphaProperty=bound(o.opt("alpha"));if(alphaProperty!=null&&projectProperties.optJSONObject(alphaProperty)!=null){layer.alphaProperty=alphaProperty;offer(alphaProperty,new JSONObject().put("kind","alpha"),projectProperties,r);}layer.objectId=o.has("id")?o.optInt("id"):-1;layer.visible=!startHidden;layer.baseW=sz[0];layer.baseH=sz[1];
     if(child&&r.scriptScene!=null)layer.frame=world(a,o,byId,new float[]{0,0},new float[]{1,1},0,r.scene.width,r.scene.height);
     if(startHidden&&!sourceOnly)r.scriptedHidden++;layer.sourceOnly=sourceOnly;if(sourceOnly)r.sourceLayers++;layer.bounded=compose&&!fullscreen;layer.when=shown.when;usedProperties(shown.when,projectProperties,r);layer.blend=colorBlend;layer.additive=additive;if(colorBlend!=0||additive)r.blended++;
     if(puppetModel!=null){
      layer.puppet=puppetModel;layer.puppetData=puppetBytes;layer.puppetW=sz[0];layer.puppetH=sz[1];r.puppets++;
      JSONArray clips=o.optJSONArray("animationlayers");java.util.ArrayList<dev.workshopvideo.core.PuppetModel.Track> tracks=new java.util.ArrayList<>();
      if(clips!=null)for(int t=0;t<clips.length();t++){JSONObject clip=clips.optJSONObject(t);if(clip==null||!clip.optBoolean("visible",true))continue;int id=clip.optInt("animation",-1);if(!puppetModel.animations.containsKey(id))continue;
       tracks.add(new dev.workshopvideo.core.PuppetModel.Track(id,(float)number(clip.opt("rate"),1),(float)Math.max(0,Math.min(1,number(clip.opt("blend"),1))),clip.optBoolean("additive",true)));}
      layer.tracks=java.util.List.copyOf(tracks);if(!tracks.isEmpty())r.puppetMotions++;
     }
     if(child&&!o.optString("attachment","").isEmpty()&&localOrigin!=null){layer.attachName=o.optString("attachment");layer.attachX=localOrigin[0];layer.attachY=localOrigin[1];layer.attachAngle=localAngle;attachSources.put(layer,(int)number(o.opt("parent"),-1));}
     // A zero-size layer has no area to draw; its scripts still run in the script scene.
     if(layer.w==0||layer.h==0)throw new IOException("尺寸为 0 的空图层，无内容可画");
     if(!Float.isFinite(layer.x)||!Float.isFinite(layer.y)||!Float.isFinite(layer.w)||!Float.isFinite(layer.h)||!Float.isFinite(layer.rotation)||layer.w<=0||layer.h<=0||layer.w>65536||layer.h>65536||Math.abs(layer.x)>65536||Math.abs(layer.y)>65536)throw new IOException("图层尺寸或位置无效");
     // X-ray variants the built-in reveal does not cover (masks, other layers' results, custom sprites) run from their own shader.
     java.util.Set<JSONObject> genericXray=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
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
       Bitmap second=WallpaperContent.bitmap(a.read(WallpaperContent.asset(a,reveal),32*1024*1024));if(pixels+(long)bitmap.getWidth()*bitmap.getHeight()+(long)second.getWidth()*second.getHeight()>budget.pixels()){second.recycle();throw new IOException("X-ray 纹理超过内存预算");}
       layer.reveal=second;pixels+=(long)second.getWidth()*second.getHeight();r.xrays++;r.effects.remove("xray");
      }catch(Exception failure){if(layer.reveal!=null){layer.reveal.recycle();layer.reveal=null;}genericXray.add(effect);}
     }
     if(objectEffects!=null)for(int e=0;e<objectEffects.length();e++){
      JSONObject effect=objectEffects.optJSONObject(e);if(effect==null||effect.optString("file").toLowerCase(Locale.ROOT).contains("xray")&&!genericXray.contains(effect))continue;
      // Effects switched by an author property are kept and follow the phone setting; others honour their saved state.
      JSONArray effectWhen=userCondition(effect.opt("visible"),projectProperties);boolean scriptedFx=r.scriptScene!=null&&effect.opt("visible") instanceof JSONObject sv&&sv.opt("script") instanceof String;
      if(effectWhen==null&&!scriptedFx&&!visible(effect))continue;
      String name=SceneEffects.kind(effect);
      try{if(layer.effects.size()>=24)throw new IOException("单个图层特效过多");if(includes==null)includes=SceneEffects.includes(a);java.util.List<SceneEffects.Effect> steps=SceneEffects.read(a,effect,includes,++r.effectGroups);if(layer.effects.size()+steps.size()>24){for(SceneEffects.Effect s:steps)s.recycle();throw new IOException("单个图层特效过多");}layer.effects.addAll(steps);if(genericXray.contains(effect)){for(SceneEffects.Effect s:steps)s.finger=true;r.xrayShaders++;}if(scriptedFx)for(SceneEffects.Effect step:steps){step.scriptedVisible="fx."+effect.optInt("id",-1)+".visible";step.scriptOff=!visible(effect);}
       if(effectWhen!=null){for(SceneEffects.Effect step:steps)step.when=effectWhen;offer(effectWhen.getJSONArray(0).optString(0),new JSONObject().put("kind","effect"),projectProperties,r);}for(SceneEffects.Effect step:steps){for(String property:step.bound.values())offer(property,new JSONObject().put("kind","uniform"),projectProperties,r);
       for(java.util.Iterator<java.util.Map.Entry<Integer,String>> it=step.userTextures.entrySet().iterator();it.hasNext();){java.util.Map.Entry<Integer,String> t=it.next();String p=declared(t.getValue(),projectProperties);if(p==null)it.remove();else offer(p,new JSONObject().put("kind","texture"),projectProperties,r);}}r.keep(name);if(steps.size()>1)r.multiPass++;}
      catch(InterruptedIOException stop){throw stop;}catch(Exception|OutOfMemoryError failure){r.drop(name,failure.getMessage()==null?"无法读取":failure.getMessage());}
     }
     layer.bitmap=bitmap;bitmap=null;pixels+=(long)layer.bitmap.getWidth()*layer.bitmap.getHeight();if(o.has("id"))outputIndex.put(o.optInt("id"),r.scene.layers.size());r.scene.layers.add(layer);if(layer.frames.size()>1)r.animations++;
    }catch(InterruptedIOException stop){throw stop;}catch(Exception e){r.skip(e.getMessage()==null?"图片无法解码":e.getMessage());}finally{if(bitmap!=null)bitmap.recycle();}
   }
   // Layers inside a composition layer are drawn into it: the nearest composition ancestor drawn earlier is their group.
   for(int k=0;k<r.scene.layers.size();k++){
    WallpaperContent.Layer l=r.scene.layers.get(k);JSONObject cur=byId.get(l.objectId);
    for(int d=0;cur!=null&&d<32&&cur.has("parent")&&!cur.isNull("parent");d++){
     cur=byId.get((int)number(cur.opt("parent"),-1));if(cur==null)break;Integer g=outputIndex.get(cur.optInt("id",-2));
     if(g!=null&&g<k&&r.scene.layers.get(g).compose){l.group=g;break;}
    }
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
  if(override!=null)for(java.util.Iterator<String> k=override.keys();k.hasNext();){String n=declared(bound(override.opt(k.next())),r.declared);if(n!=null)offer(n,new JSONObject().put("kind","particle"),r.declared,r);}
  String alphaProperty=declared(bound(o.opt("alpha")),r.declared);if(alphaProperty!=null){layer.alphaProperty=alphaProperty;offer(alphaProperty,new JSONObject().put("kind","alpha"),r.declared,r);}
  transformChain(o,byId,layer,r.declared,r,w,h);
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
 static final String GROUP_LAYER="合成层只用于分组，其中的图层照常显示";
 /**
  * First frame of a layer video, decoded by Android's retriever; where it cannot be decoded (no codec, host checks) a
  * transparent placeholder with the layer's proportions, replaced by real frames once the layer plays.
  */
 static Bitmap videoPoster(byte[] mp4,float w,float h){
  try(android.media.MediaMetadataRetriever m=new android.media.MediaMetadataRetriever()){
   m.setDataSource(new android.media.MediaDataSource(){
    @Override public int readAt(long position,byte[] buffer,int offset,int size){if(position>=mp4.length)return -1;int n=(int)Math.min(size,mp4.length-position);System.arraycopy(mp4,(int)position,buffer,offset,n);return n;}
    @Override public long getSize(){return mp4.length;}
    @Override public void close(){}
   });
   android.media.MediaMetadataRetriever.BitmapParams params=new android.media.MediaMetadataRetriever.BitmapParams();params.setPreferredConfig(Bitmap.Config.ARGB_8888);
   Bitmap frame=m.getFrameAtTime(0,android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC,params);
   if(frame!=null&&frame.getWidth()>0&&frame.getHeight()>0){if(frame.getConfig()==Bitmap.Config.ARGB_8888)return frame;Bitmap copy=frame.copy(Bitmap.Config.ARGB_8888,false);frame.recycle();if(copy!=null)return copy;}
  }catch(Exception|LinkageError unavailable){}
  float k=Math.min(1,64f/Math.max(1,Math.max(w,h)));
  return Bitmap.createBitmap(Math.max(1,Math.round(Math.max(1,w)*k)),Math.max(1,Math.round(Math.max(1,h)*k)),Bitmap.Config.ARGB_8888);
 }
 /** Engine solid-colour layer: a flat rectangle in the object's colour; effects on it run as on any image. */
 /** Transparent base of a shape object, at most 1024 px on its long side so its effects keep their detail. */
 static Bitmap clearBitmap(JSONObject o){
  float[] size=WallpaperContent.vector(o.opt("size"),1024,1024);float w=Math.max(1,size.length>0?size[0]:1024),h=Math.max(1,size.length>1?size[1]:1024),k=Math.min(1,1024f/Math.max(w,h));
  return Bitmap.createBitmap(Math.max(1,Math.round(w*k)),Math.max(1,Math.round(h*k)),Bitmap.Config.ARGB_8888);
 }
 static Bitmap solidBitmap(JSONObject o){
  float[] size=WallpaperContent.vector(o.opt("size"),256,256),color={1,1,1};
  float w=Math.max(1,size.length>0?size[0]:256),h=Math.max(1,size.length>1?size[1]:256),k=Math.min(1,256f/Math.max(w,h));
  int bw=Math.max(1,Math.round(w*k)),bh=Math.max(1,Math.round(h*k));
  int argb=0xff000000|channel(color,0)<<16|channel(color,1)<<8|channel(color,2);int[] pixels=new int[bw*bh];java.util.Arrays.fill(pixels,argb);
  return Bitmap.createBitmap(pixels,bw,bh,Bitmap.Config.ARGB_8888);
 }
 private static int channel(float[] c,int i){float v=c.length>i?c[i]:1;return Math.max(0,Math.min(255,Math.round(v*255)));}
 /** Image layers in the scene; particle layers have small textures and are counted separately. */
 static int imageLayers(WallpaperContent.Scene scene){int n=0;for(WallpaperContent.Layer l:scene.layers)if(l.particleData==null)n++;return n;}
 /**
  * The scene's mouse-driven camera becomes the phone's tilt parallax: on/off and influence come from the author's
  * camera settings (resolved from their properties), and a camera zoom above 1 becomes the default zoom.
  */
 static void camera(Report report,JSONObject effects)throws JSONException {
  JSONObject general=report.general;JSONObject cam=report.cameraObject;
  if(cam!=null){double cz=number(cam.opt("zoom"),1);if(cz>1.01)effects.put("zoom",Math.min(2.5,cz));
  }
  Object parallax=value(general.opt("cameraparallax"));if(parallax!=null&&parallax!=JSONObject.NULL)effects.put("gyro",truthy(parallax));
  double influence=number(general.opt("cameraparallaxmouseinfluence"),-1),amount=number(general.opt("cameraparallaxamount"),-1);
  if(influence>=0)effects.put("strength",Math.max(.1,Math.min(1,influence*2)));
  if(amount>0)effects.put("layerDepth",Math.max(.25,Math.min(2,amount*10)));
  double zoom=number(general.opt("zoom"),1);if(zoom>1.01)effects.put("zoom",Math.min(2.5,zoom));
  // The scene's clear colour fills what no layer covers (general.clearcolor when clearenabled).
  if(!general.has("clearenabled")||truthy(value(general.opt("clearenabled")))){float[] c=WallpaperContent.vector(value(general.opt("clearcolor")),0,0,0);if(c.length>=3)effects.put("backgroundColor",String.format(Locale.ROOT,"#%02X%02X%02X",Math.round(Math.max(0,Math.min(1,c[0]))*255),Math.round(Math.max(0,Math.min(1,c[1]))*255),Math.round(Math.max(0,Math.min(1,c[2]))*255)));}
 }
 /** Camera shake (general.camerashake*), kept with the scene for the renderer. */
 static JSONObject cameraBlock(Report report)throws JSONException {
  JSONObject g=report.general==null?new JSONObject():report.general;if(!truthy(value(g.opt("camerashake"))))return null;
  return new JSONObject().put("shake",new JSONObject().put("speed",number(g.opt("camerashakespeed"),3)).put("amplitude",number(g.opt("camerashakeamplitude"),.5)).put("roughness",number(g.opt("camerashakeroughness"),1)));
 }
 /**
  * Scene bloom (general.bloom, bloomstrength, bloomthreshold, bloomtint), with the author properties any of them are
  * bound to. Kept whenever bloom is on or switchable by a property.
  */
 static JSONObject bloomBlock(Report report,JSONObject projectProperties)throws JSONException {
  JSONObject g=report.general==null?new JSONObject():report.general;String onProperty=declared(bound(g.opt("bloom")),projectProperties);
  if(!truthy(value(g.opt("bloom")))&&onProperty==null)return null;
  JSONObject b=new JSONObject().put("enabled",truthy(value(g.opt("bloom")))).put("strength",number(g.opt("bloomstrength"),2)).put("threshold",number(g.opt("bloomthreshold"),.65));
  float[] tint=WallpaperContent.vector(value(g.opt("bloomtint")),1,1,1);b.put("tint",array(tint.length>=3?tint:new float[]{1,1,1}));
  String[][] keys={{"bloom","enabledProperty"},{"bloomstrength","strengthProperty"},{"bloomthreshold","thresholdProperty"},{"bloomtint","tintProperty"}};
  for(String[] k:keys){String p=declared(bound(g.opt(k[0])),projectProperties);if(p!=null){b.put(k[1],p);offer(p,new JSONObject().put("kind","bloom"),projectProperties,report);}}
  report.bloom=true;return b;
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
   if(user instanceof String name){JSONObject p=properties.optJSONObject(name);if(p!=null&&p.has("value")&&!"group".equals(p.optString("type"))&&!"text".equals(p.optString("type")))o.put("value",shaped(o.opt("value"),p.opt("value")));}
   else if(user instanceof JSONObject bound){JSONObject p=properties.optJSONObject(bound.optString("name"));if(p!=null&&p.has("value"))o.put("value",String.valueOf(p.opt("value")).equals(bound.optString("condition")));}
   for(java.util.Iterator<String> keys=o.keys();keys.hasNext();)resolve(o.opt(keys.next()),properties);
  }else if(value instanceof JSONArray list)for(int i=0;i<list.length();i++)resolve(list.opt(i),properties);
 }
 /** A scalar slider bound to a vector ("x y z") applies to every component, as Wallpaper Engine does. */
 static Object shaped(Object saved,Object value){
  if(!(saved instanceof String text)||!(value instanceof Number||value instanceof String v&&v.trim().matches("-?[0-9.eE+-]+")))return value;
  String[] parts=text.trim().split("[\\s,]+");if(parts.length<2)return value;
  String one=String.valueOf(value).trim();StringBuilder out=new StringBuilder();for(int i=0;i<parts.length;i++){if(i>0)out.append(' ');out.append(one);}return out.toString();
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
  for(int i=0;i<when.length();i++)offer(when.getJSONArray(i).optString(0),new JSONObject().put("kind","visible"),properties,r);
 }
 /**
  * Every property the author declared (project.json), as Wallpaper Engine's settings panel lists them, with what each
  * drives on the phone. Script parameters count only for objects the phone draws; text notes and shortcuts are left out.
  */
 static JSONObject offered(Report report)throws JSONException {
  // Script parameters count when their object is drawn or is a parent of a drawn layer (scripts move the children too).
  java.util.Set<Integer> drawn=new java.util.HashSet<>();for(WallpaperContent.Layer l:report.scene.layers)for(Integer id=l.objectId,d=0;id!=null&&d<33&&drawn.add(id);d++)id=report.parentOf.get(id);
  // The scene camera follows its object's scripts (camera position sliders), so its parameters count too.
  if(report.cameraObject!=null&&report.cameraObject.has("id"))drawn.add(report.cameraObject.optInt("id"));
  // Settings other settings' display conditions refer to ("calendar.value == true"): they open sections of the panel.
  java.util.Set<String> reveal=new java.util.HashSet<>();
  for(java.util.Iterator<String> k=report.declared.keys();k.hasNext();){JSONObject p=report.declared.optJSONObject(k.next());if(p==null)continue;
   java.util.regex.Matcher m=java.util.regex.Pattern.compile("([A-Za-z_][\\w]*)\\.value").matcher(p.optString("condition",""));while(m.find())reveal.add(m.group(1));}
  JSONObject out=new JSONObject();
  for(java.util.Iterator<String> k=report.declared.keys();k.hasNext();){
   String name=k.next();JSONObject p=report.declared.optJSONObject(name);if(p==null)continue;String type=p.optString("type");
   if(type.isEmpty()||name.equals("schemecolor"))continue;
   // Authors draw section dividers as colour or text properties whose label is only an image.
   String rawText=p.optString("text","");if(rawText.toLowerCase(java.util.Locale.ROOT).contains("<img")&&label(rawText).equals("作者设置")&&(type.equals("color")||type.equals("text"))){out.put(name,new JSONObject().put("type","divider").put("order",p.optInt("order",0)).put("label",""));continue;}
   String text=type.equals("text")?note(p.optString("text","")):label(p.optString("text",name));if(type.equals("text")&&text.isEmpty())continue;
   JSONObject o=new JSONObject().put("type",type).put("value",p.opt("value")).put("label",text).put("order",p.optInt("order",0));
   if(!p.optString("condition").isBlank())o.put("condition",p.optString("condition"));
   if(type.equals("combo")){JSONArray options=new JSONArray();JSONArray list=p.optJSONArray("options");if(list!=null)for(int i=0;i<list.length();i++){JSONObject op=list.optJSONObject(i);if(op!=null)options.put(new JSONArray().put(label(op.optString("label"))).put(String.valueOf(op.opt("value"))));}o.put("options",options);}
   if(type.equals("slider")){double min=p.optDouble("min",0),max=p.optDouble("max",1);if(!(max>min))continue;o.put("min",min).put("max",max);}
   JSONArray kept=new JSONArray();JSONObject drives=report.properties.optJSONObject(name);JSONArray targets=drives==null?null:drives.optJSONArray("targets");
   if(targets!=null)for(int i=0;i<targets.length();i++){JSONObject t=targets.getJSONObject(i);if(!t.optString("kind").equals("parameter")||drawn.contains(t.optInt("object",-2))||report.reachingScripts.contains(t.optInt("object",-2)))kept.put(t);}
   // Settings nothing in the scene or its scripts refers to change nothing on the PC either: not a phone limitation.
   boolean unused=kept.length()==0&&!report.sceneText.contains("\""+name+"\"")&&!report.scriptSources.contains(name);
   if(unused)o.put("unused",true);
   if(kept.length()==0&&!unused&&reveal.contains(name))kept.put(new JSONObject().put("kind","panel"));
   o.put("targets",kept).put("effective",kept.length()>0||unused);out.put(name,o);
  }
  return out;
 }
 /** Name of the author property a value is bound to ({"user": name, "value": …}), or null. */
 static String bound(Object v){return v instanceof JSONObject b&&b.opt("user") instanceof String n&&!n.isEmpty()?n:null;}
 /** Records what an author property drives on the phone; the settings list itself comes from the declarations in offered(). */
 static void offer(String name,JSONObject target,JSONObject properties,Report r)throws JSONException {
  if(name==null||properties.optJSONObject(name)==null)return;
  JSONObject out=r.properties.optJSONObject(name);if(out==null){out=new JSONObject().put("targets",new JSONArray());r.properties.put(name,out);}
  JSONArray targets=out.getJSONArray("targets");for(int i=0;i<targets.length();i++)if(targets.optJSONObject(i).toString().equals(target.toString()))return;
  targets.put(target);
 }
 /** The property name when a value is bound to a property the author declared, else null. */
 static String declared(String name,JSONObject properties){return name!=null&&properties.optJSONObject(name)!=null?name:null;}
 /** [[name, condition or null]] for a visibility bound to a declared property, else null. */
 static JSONArray userCondition(Object visible,JSONObject properties)throws JSONException {
  if(!(visible instanceof JSONObject b))return null;Object user=b.opt("user");
  if(user instanceof JSONObject c){String n=declared(c.optString("name"),properties);return n==null?null:new JSONArray().put(new JSONArray().put(n).put(c.optString("condition")));}
  String n=declared(bound(visible),properties);return n==null?null:new JSONArray().put(new JSONArray().put(n).put(JSONObject.NULL));
 }
 /**
  * Local transforms from the object up to the scene root, kept when any of them is bound to an author property so the
  * phone can recompose the layer's place when the setting changes. Layers placed on a skeleton are positioned by it instead.
  */
 static void transformChain(JSONObject o,java.util.Map<Integer,JSONObject> byId,WallpaperContent.Layer layer,JSONObject props,Report r,int w,int h)throws JSONException {
  java.util.List<float[]> nodes=new java.util.ArrayList<>();java.util.List<String[]> binds=new java.util.ArrayList<>();JSONObject cur=o;
  for(int d=0;cur!=null&&d<32;d++){
   if(!cur.optString("attachment","").isEmpty())return;
   boolean child=cur.has("parent")&&!cur.isNull("parent");
   float[] origin=child?WallpaperContent.vector(cur.opt("origin"),0,0):WallpaperContent.vector(cur.opt("origin"),w/2f,h/2f),scale=WallpaperContent.vector(cur.opt("scale"),1,1),angles=WallpaperContent.vector(cur.opt("angles"),0,0,0);
   if(origin.length<2||scale.length<2)return;
   nodes.add(new float[]{origin[0],origin[1],scale[0],scale[1],angles.length>=3?angles[2]:0});
   binds.add(new String[]{declared(bound(cur.opt("origin")),props),declared(bound(cur.opt("scale")),props),declared(bound(cur.opt("angles")),props)});
   if(!child)break;cur=byId.get((int)number(cur.opt("parent"),-1));if(cur==null)return;
  }
  boolean any=false;for(String[] b:binds)for(String n:b)if(n!=null){any=true;offer(n,new JSONObject().put("kind","transform"),props,r);}
  if(any){layer.chain=nodes.toArray(new float[0][]);layer.chainBind=binds.toArray(new String[0][]);}
 }
 /** Mouse-parallax, zoom and camera-position settings become the phone's tilt parallax, zoom and picture position. */
 static void cameraSettings(JSONObject root,JSONObject props,Report r)throws JSONException {
  JSONObject g=r.general;Object parallax=g.opt("cameraparallax");
  if(parallax instanceof JSONObject b&&b.opt("user") instanceof JSONObject c)offer(c.optString("name"),new JSONObject().put("kind","option").put("key","gyro").put("condition",c.optString("condition")),props,r);
  else offer(bound(parallax),new JSONObject().put("kind","option").put("key","gyro"),props,r);
  offer(bound(g.opt("cameraparallaxmouseinfluence")),new JSONObject().put("kind","option").put("key","strength"),props,r);
  offer(bound(g.opt("zoom")),new JSONObject().put("kind","option").put("key","zoom"),props,r);
  JSONArray objects=root.optJSONArray("objects");if(objects==null)return;
  for(int i=0;i<objects.length();i++){
   JSONObject o=objects.optJSONObject(i);if(o==null||!o.has("camera"))continue;r.cameraObject=o;
   offer(bound(o.opt("zoom")),new JSONObject().put("kind","option").put("key","zoom"),props,r);
   break;
  }
 }
 /** An author note (HTML): its readable lines, links and images dropped, at most three lines. */
 static String note(String html){
  String s=html.replaceAll("(?i)<br\\s*/?>|<hr\\s*/?>|</p>|</div>|</h[1-6]>|</center>",String.valueOf((char)10)).replaceAll("<[^>]*>","").replaceAll("&nbsp;?"," ").replace("&amp;","&").replace("&lt;","<").replace("&gt;",">");
  StringBuilder out=new StringBuilder();int lines=0;
  for(String line:s.split(String.valueOf((char)10))){String t=line.trim();if(t.isEmpty()||t.matches("(?i).*https?://.*"))continue;if(out.length()>0)out.append((char)10);out.append(t.length()>60?t.substring(0,60):t);if(++lines==3)break;}
  return out.toString();
 }
 /** A text object as Wallpaper Engine saves it (text layer fields), with its font file when the scene or engine has it. */
 private static SceneText text(ContentArchive a,JSONObject o,JSONObject projectProperties,Report r)throws Exception {
  SceneText t=new SceneText();Object raw=o.opt("text");Object v=value(raw);t.value=v==null||v==JSONObject.NULL?"":String.valueOf(v);
  String valueProperty=declared(bound(raw),projectProperties);if(valueProperty!=null){t.valueProperty=valueProperty;offer(valueProperty,new JSONObject().put("kind","text"),projectProperties,r);}
  t.pointsize=(float)Math.max(.1,Math.min(1000,number(o.opt("pointsize"),32)));
  String sizeProperty=declared(bound(o.opt("pointsize")),projectProperties);if(sizeProperty!=null){t.sizeProperty=sizeProperty;offer(sizeProperty,new JSONObject().put("kind","text"),projectProperties,r);}
  t.align=o.optString("horizontalalign","center").toLowerCase(Locale.ROOT);t.valign=o.optString("verticalalign","center").toLowerCase(Locale.ROOT);
  t.limitrows=dev.workshopvideo.core.PropertyCondition.truthy(value(o.opt("limitrows")));t.maxrows=(int)Math.max(1,Math.min(1000,number(o.opt("maxrows"),1)));
  t.limitwidth=dev.workshopvideo.core.PropertyCondition.truthy(value(o.opt("limitwidth")));t.maxwidth=(float)Math.max(1,number(o.opt("maxwidth"),500));
  t.opaque=dev.workshopvideo.core.PropertyCondition.truthy(value(o.opt("opaquebackground")));
  float[] bg=WallpaperContent.vector(o.opt("backgroundcolor"),0,0,0),fg=WallpaperContent.vector(o.opt("color"),1,1,1);if(bg.length>=3)t.background=bg;if(fg.length>=3)t.color=fg;
  String font=String.valueOf(value(o.opt("font")));t.fontName=font;
  if(!font.isEmpty()&&!font.equals("null")&&!font.startsWith("systemfont_"))try{t.fontData=r.fonts.get(font);if(t.fontData==null){t.fontData=a.read(ContentArchive.path(font),SceneText.MAX_FONT_BYTES);r.fonts.put(font,t.fontData);}}catch(InterruptedIOException stop){throw stop;}catch(IOException missing){if(r.missingFonts.size()<6)r.missingFonts.add(font.substring(font.lastIndexOf('/')+1));}
  android.graphics.Typeface shared=r.typefaces.get(font);if(shared==null){shared=SceneText.typeface(t.fontData,font);r.typefaces.put(font,shared);}t.typeface=shared;
  return t;
 }
 /** Property text is HTML; keep the first readable line (authors often put Chinese and English on two lines). */
 static String label(String html){
  String s=html.replaceAll("(?i)<br\\s*/?>|<hr\\s*/?>|</p>|</div>|</h[1-6]>",String.valueOf((char)10)).replaceAll("<[^>]*>","").replaceAll("&nbsp;?"," ").replace("&amp;","&").replace("&lt;","<").replace("&gt;",">");
  StringBuilder out=new StringBuilder();int lines=0;
  for(String line:s.split(String.valueOf((char)10))){String t=line.replaceAll("[\\p{So}\\p{Cn}\\uFE0F]","").trim();if(t.isEmpty())continue;if(out.length()>0)out.append((char)10);out.append(t.length()>40?t.substring(0,40):t);if(++lines==2)break;}
  return out.length()==0?"作者设置":out.toString();
 }
 /** True when a script can show this hidden layer: its own visibility is scripted, or a script names it. */
 private static final java.util.regex.Pattern SELF_VISIBLE=java.util.regex.Pattern.compile("thisLayer\\s*\\.\\s*visible\\s*=[^=]");
 /** Name prefixes scripts build layer names from: `Launcher ${i + 1}` or 'Launcher ' + i. */
 private static final java.util.regex.Pattern NAME_TEMPLATE=java.util.regex.Pattern.compile("`([^`$\\n]{2,60})\\$\\{|[\"']([^\"'\\n]{2,60})[\"']\\s*\\+\\s*[A-Za-z_(]");
 static boolean scriptedVisibility(JSONObject o,String sources,java.util.Map<Integer,JSONObject> byId){
  if(scriptedVisibility(o,sources))return true;
  String name=o.optString("name","");
  if(!name.isEmpty()){java.util.regex.Matcher m=NAME_TEMPLATE.matcher(sources);
   while(m.find()){String prefix=m.group(1)!=null?m.group(1):m.group(2);if(name.startsWith(prefix)&&name.length()>prefix.length()&&name.substring(prefix.length()).matches("\\d{1,4}"))return true;}}
  // A parent whose script walks its children (getChildren) and sets their visibility.
  JSONObject cur=o;
  for(int d=0;d<16&&cur!=null&&cur.has("parent")&&!cur.isNull("parent");d++){
   cur=byId.get((int)number(cur.opt("parent"),-1));if(cur==null)break;
   for(java.util.Iterator<String> k=cur.keys();k.hasNext();){Object p=cur.opt(k.next());if(p instanceof JSONObject j&&j.opt("script") instanceof String src&&src.contains("getChildren")&&src.contains(".visible"))return true;}
  }
  return false;
 }
 static boolean scriptedVisibility(JSONObject o,String sources){
  Object v=o.opt("visible");if(v instanceof JSONObject&&((JSONObject)v).opt("script") instanceof String)return true;
  // A property script of the layer itself that sets thisLayer.visible (e.g. day/night switches on a hidden layer).
  for(java.util.Iterator<String> k=o.keys();k.hasNext();){Object p=o.opt(k.next());if(p instanceof JSONObject&&((JSONObject)p).opt("script") instanceof String src&&SELF_VISIBLE.matcher(src).find())return true;}
  String name=o.optString("name","");return !name.isEmpty()&&(sources.contains("'"+name+"'")||sources.contains("\""+name+"\"")||sources.contains("`"+name+"`"));
 }
 private static JSONArray array(float[] v)throws JSONException{JSONArray out=new JSONArray();for(float x:v)out.put(Float.isFinite(x)?(double)x:0);return out;}
 /** Saved state of every object, its property scripts and the author's user-property defaults. */
 static JSONObject scriptScene(ContentArchive a,JSONArray objects,int width,int height,StringBuilder sources,JSONObject projectProperties,Report report)throws Exception {
  JSONArray list=new JSONArray();
  for(int i=0;i<objects.length();i++){
   JSONObject o=objects.optJSONObject(i);if(o==null)continue;boolean child=o.has("parent")&&!o.isNull("parent");
   JSONObject d=new JSONObject().put("id",o.optInt("id",-1)).put("name",o.optString("name",""));if(child)d.put("parent",(int)number(o.opt("parent"),-1));
   d.put("origin",array(child?WallpaperContent.vector(o.opt("origin"),0,0,0):WallpaperContent.vector(o.opt("origin"),width/2f,height/2f,0)));
   d.put("scale",array(WallpaperContent.vector(o.opt("scale"),1,1,1))).put("angles",array(WallpaperContent.vector(o.opt("angles"),0,0,0))).put("size",array(WallpaperContent.vector(o.opt("size"),0,0)));
   d.put("alpha",number(o.opt("alpha"),1)).put("visible",visible(o)).put("solid",o.optBoolean("solid"));
   if(o.has("text")&&!o.isNull("text")){Object tv=value(o.opt("text"));d.put("text",tv==null||tv==JSONObject.NULL?"":String.valueOf(tv)).put("pointsize",number(o.opt("pointsize"),32)).put("color",array(WallpaperContent.vector(o.opt("color"),1,1,1)));
    for(String k:new String[]{"font","horizontalalign","verticalalign","anchor"})d.put(k,o.optString(k,""));d.put("padding",number(o.opt("padding"),0)).put("maxrows",number(o.opt("maxrows"),1)).put("maxwidth",number(o.opt("maxwidth"),500));}
   if(!o.optString("attachment","").isEmpty())d.put("attachment",o.optString("attachment"));
   JSONObject boundFields=new JSONObject();for(String f:new String[]{"text","pointsize","color","alpha"}){String p=declared(bound(o.opt(f)),projectProperties);if(p!=null)boundFields.put(f,p);}if(boundFields.length()>0)d.put("bound",boundFields);
   if(o.has("camera")){d.put("zoom",number(o.opt("zoom"),1)).put("fov",number(o.opt("fov"),50));}
   d.put("kind",o.has("camera")?"camera":o.has("particle")&&!o.isNull("particle")?"particle":o.has("text")&&!o.isNull("text")?"text":o.has("sound")&&!o.isNull("sound")?"sound":o.has("image")&&!o.isNull("image")?"image":"other");
   JSONObject scripts=new JSONObject();
   for(java.util.Iterator<String> keys=o.keys();keys.hasNext();){
    String key=keys.next();Object v=o.opt(key);if(!(v instanceof JSONObject))continue;Object source=((JSONObject)v).opt("script");
    if(!(source instanceof String)||((String)source).isBlank())continue;
    JSONObject properties=((JSONObject)v).optJSONObject("scriptproperties");
    String text=(String)source;
    if(text.contains("shared.")||text.contains("getLayer")||text.contains("enumerateLayers")||text.contains("getChildren"))report.reachingScripts.add(o.optInt("id",-1));
    if(text.contains("userProperties")||text.contains("applyUserProperties"))for(java.util.Iterator<String> names=projectProperties.keys();names.hasNext();){
     String n=names.next();if(n.length()<3)continue;String q=java.util.regex.Pattern.quote(n);
     if(java.util.regex.Pattern.compile("\\."+q+"\\b|\\[\\s*['\"]"+q+"['\"]\\s*\\]").matcher(text).find())offer(n,new JSONObject().put("kind","parameter").put("object",o.optInt("id",-1)),projectProperties,report);
    }
    if(properties!=null)for(java.util.Iterator<String> pk=properties.keys();pk.hasNext();)offer(bound(properties.opt(pk.next())),new JSONObject().put("kind","parameter").put("object",o.optInt("id",-1)),projectProperties,report);scripts.put(key,new JSONObject().put("source",source).put("properties",properties==null?new JSONObject():properties));sources.append(source).append('\n');
   }
   // Scripts on effect values (constantshadervalues) and on effect visibility: "fx.<effect id>.<pass>.<name>" / "fx.<effect id>.visible".
   JSONArray fxList=o.optJSONArray("effects");
   if(fxList!=null)for(int f=0;f<fxList.length();f++){JSONObject ef=fxList.optJSONObject(f);if(ef==null)continue;int fid=ef.optInt("id",-1);
    java.util.LinkedHashMap<String,JSONObject> found=new java.util.LinkedHashMap<>();
    if(ef.opt("visible") instanceof JSONObject vj&&vj.opt("script") instanceof String)found.put("fx."+fid+".visible",vj);
    JSONArray ps=ef.optJSONArray("passes");
    if(ps!=null)for(int pi=0;pi<ps.length();pi++){JSONObject pass=ps.optJSONObject(pi);JSONObject c=pass==null?null:pass.optJSONObject("constantshadervalues");if(c==null)continue;
     for(java.util.Iterator<String> ck=c.keys();ck.hasNext();){String cn=ck.next();if(c.opt(cn) instanceof JSONObject cj&&cj.opt("script") instanceof String)found.put("fx."+fid+"."+pi+"."+cn,cj);}}
    for(java.util.Map.Entry<String,JSONObject> item:found.entrySet()){
     String text=item.getValue().getString("script");if(text.isBlank())continue;JSONObject props=item.getValue().optJSONObject("scriptproperties");
     if(props!=null)for(java.util.Iterator<String> pk=props.keys();pk.hasNext();)offer(bound(props.opt(pk.next())),new JSONObject().put("kind","parameter").put("object",o.optInt("id",-1)),projectProperties,report);
     scripts.put(item.getKey(),new JSONObject().put("source",text).put("properties",props==null?new JSONObject():props).put("value",item.getValue().opt("value")));sources.append(text).append((char)10);
    }
   }
   if(scripts.length()>0)d.put("scripts",scripts);list.put(d);
  }
  JSONObject properties=new JSONObject();
  try{if(a.has("project.json")){JSONObject general=WallpaperContent.json(a,"project.json").optJSONObject("general");JSONObject all=general==null?null:general.optJSONObject("properties");
   if(all!=null)for(java.util.Iterator<String> keys=all.keys();keys.hasNext();){String key=keys.next();JSONObject p=all.optJSONObject(key);if(p!=null&&p.has("value"))properties.put(key,p.opt("value"));}}}catch(Exception unreadable){}
  // Scene fields scripts read through thisScene (IScene), with bound values resolved.
  JSONObject general=new JSONObject();JSONObject saved=report.general==null?new JSONObject():report.general;
  for(java.util.Iterator<String> k=saved.keys();k.hasNext();){String key=k.next();Object v=value(saved.opt(key));if(v instanceof String||v instanceof Number||v instanceof Boolean)general.put(key,v);}
  return new JSONObject().put("width",width).put("height",height).put("properties",properties).put("general",general).put("objects",list);
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
  // A multi-pass effect runs only when every one of its passes translated.
  for(WallpaperContent.Layer l:report.scene.layers){
   java.util.Set<Integer> failed=new java.util.HashSet<>();
   for(SceneEffects.Effect effect:l.effects){
    if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("适配已暂停");if(failed.contains(effect.group))continue;
    java.util.ArrayList<String> failures=new java.util.ArrayList<>();
    if(translator!=null&&SceneEffects.translate(effect,translator,failures))continue;
    failed.add(effect.group);report.unkeep(effect.name);report.drop(effect.name,translator==null?"此设备不支持特效转换":failures.isEmpty()?"转换失败":failures.get(0).substring(Math.min(failures.get(0).length(),effect.name.length()+1)));
   }
   for(java.util.Iterator<SceneEffects.Effect> it=l.effects.iterator();it.hasNext();){SceneEffects.Effect effect=it.next();if(failed.contains(effect.group)){it.remove();effect.recycle();}}
  }
  fit(report.scene,dev.workshopvideo.core.SceneBudget.current().pixels());
  JSONObject root=new JSONObject().put("format","WallpaperMobileScene/1").put("width",report.scene.width).put("height",report.scene.height).put("adapted",true).put("adaptation",report.describe());
  JSONObject effects=EffectOptions.normalize(null);effects.put("gyro",true);effects.put("touch",true);camera(report,effects);root.put("effects",effects);JSONObject cameraBlock=cameraBlock(report);if(cameraBlock!=null)root.put("camera",cameraBlock);JSONObject bloomBlock=bloomBlock(report,report.declared);if(bloomBlock!=null)root.put("bloom",bloomBlock);JSONObject offered=offered(report);if(offered.length()>0)root.put("properties",offered);JSONArray layers=new JSONArray();
  boolean done=false;try{
   Counting counted=new Counting(new BufferedOutputStream(new FileOutputStream(target)));
   try(ZipOutputStream zip=new ZipOutputStream(counted)){java.util.IdentityHashMap<byte[],String> fontFiles=new java.util.IdentityHashMap<>();int index=0;for(WallpaperContent.Layer l:report.scene.layers){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("适配已暂停");String name="layers/"+(index++)+".png";zip.putNextEntry(new ZipEntry(name));if(!l.bitmap.compress(Bitmap.CompressFormat.PNG,100,zip))throw new IOException("无法保存图层");zip.closeEntry();JSONObject layer=new JSONObject().put("file",name).put("name",l.name).put("x",l.x).put("y",l.y).put("width",l.w).put("height",l.h).put("rotation",l.rotation).put("depth",l.depth).put("opacity",l.alpha);if(l.frames.size()>1){JSONArray frames=new JSONArray();for(var f:l.frames)frames.put(new JSONArray().put(f.u0()).put(f.v0()).put(f.u1()).put(f.v1()).put(f.seconds()));layer.put("frames",frames);}if(!l.effects.isEmpty())writeEffects(zip,l,index-1,layer);
    if(l.puppet!=null){String mdl="layers/"+(index-1)+".mdl";zip.putNextEntry(new ZipEntry(mdl));zip.write(l.puppetData);zip.closeEntry();layer.put("puppet",mdl).put("puppetSize",new JSONArray().put(l.puppetW).put(l.puppetH));
     JSONArray tracks=new JSONArray();for(var t:l.tracks)tracks.put(new JSONArray().put(t.animation()).put(t.rate()).put(t.blend()).put(t.additive()));if(tracks.length()>0)layer.put("tracks",tracks);}
    if(l.videoData!=null){
     // Stored uncompressed so the phone's player reads it in place (WallpaperContent.storedRange); MP4 does not deflate anyway.
     String video="layers/"+(index-1)+".mp4";ZipEntry entry=new ZipEntry(video);entry.setMethod(ZipEntry.STORED);entry.setSize(l.videoData.length);entry.setCompressedSize(l.videoData.length);
     java.util.zip.CRC32 crc=new java.util.zip.CRC32();crc.update(l.videoData);entry.setCrc(crc.getValue());
     long header=counted.count;zip.putNextEntry(entry);zip.write(l.videoData);zip.closeEntry();layer.put("videoFile",video).put("videoHeader",header);
    }
    if(l.particleData!=null){String file="layers/"+(index-1)+"-particles.json";zip.putNextEntry(new ZipEntry(file));zip.write(l.particleData.getBytes(StandardCharsets.UTF_8));zip.closeEntry();layer.put("particlesFile",file);}
    layer.put("objectId",l.objectId).put("base",new JSONArray().put(l.baseW).put(l.baseH));if(!l.visible)layer.put("visible",false);if(l.frame!=null)layer.put("frame",array(l.frame));
    if(l.tint[0]!=1||l.tint[1]!=1||l.tint[2]!=1)layer.put("tint",array(l.tint));if(l.tintProperty!=null)layer.put("tintProperty",l.tintProperty);if(l.textureProperty!=null)layer.put("textureProperty",l.textureProperty);if(l.shape!=null)layer.put("shape",array(l.shape));
    if(l.chain!=null){JSONArray chain=new JSONArray();for(int c=0;c<l.chain.length;c++){JSONArray b=new JSONArray();for(String n:l.chainBind[c])b.put(n==null?JSONObject.NULL:n);chain.put(new JSONArray().put(array(l.chain[c])).put(b));}layer.put("chain",chain);}
    if(l.when!=null&&l.when.length()>0)layer.put("when",l.when);
    if(l.alphaProperty!=null)layer.put("alphaProperty",l.alphaProperty);
    if(l.text!=null){
     l.text.density=l.text.blockW>0?l.bitmap.getWidth()/l.text.blockW:1;layer.put("textLayer",l.text.json());if(l.fontName!=null)layer.put("fontName",l.fontName);
     if(l.fontData!=null){String file=fontFiles.get(l.fontData);if(file==null){file="layers/"+(index-1)+"-font";zip.putNextEntry(new ZipEntry(file));zip.write(l.fontData);zip.closeEntry();fontFiles.put(l.fontData,file);}layer.put("fontFile",file);}
    }
    if(l.compose)layer.put("compose",true);if(l.fullscreen)layer.put("fullscreen",true);if(l.bounded)layer.put("bounded",true);if(l.sourceOnly)layer.put("source",true);if(l.group>=0)layer.put("group",l.group);
    if(l.blend!=0)layer.put("blend",l.blend);if(l.additive)layer.put("additive",true);
    if(l.attachParent>=0)layer.put("attach",new JSONObject().put("layer",l.attachParent).put("name",l.attachName).put("x",l.attachX).put("y",l.attachY).put("angle",l.attachAngle));if(l.reveal!=null){String reveal="layers/xray-"+index+".png";zip.putNextEntry(new ZipEntry(reveal));if(!l.reveal.compress(Bitmap.CompressFormat.PNG,100,zip))throw new IOException("无法保存 X-ray 图层");zip.closeEntry();layer.put("xrayFile",reveal).put("xraySize",l.revealSize);}layers.put(layer);}root.put("layers",layers);if(report.scriptScene!=null){zip.putNextEntry(new ZipEntry("scripts.json"));zip.write(report.scriptScene.toString().getBytes(StandardCharsets.UTF_8));zip.closeEntry();root.put("scripts","scripts.json");}zip.putNextEntry(new ZipEntry("wallpaper-mobile.json"));zip.write(root.toString().getBytes(StandardCharsets.UTF_8));zip.closeEntry();}
   try(WallpaperContent.Scene verify=WallpaperContent.scene(target)){if(verify.layers.size()!=layers.length())throw new IOException("适配版校验未通过");
    for(int k=0;k<verify.layers.size();k++)if(report.scene.layers.get(k).videoData!=null&&verify.layers.get(k).video==null)throw new IOException("图层视频校验未通过");}done=true;
  }finally{if(!done)target.delete();}
 }
 /** Bytes written so far: the offset of the next zip entry's local header once the previous entry is closed. */
 private static final class Counting extends FilterOutputStream {long count;Counting(OutputStream out){super(out);}
  @Override public void write(int b)throws IOException{out.write(b);count++;}
  @Override public void write(byte[] b,int off,int len)throws IOException{out.write(b,off,len);count+=len;}}
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
 static long loadBudget(){return dev.workshopvideo.core.SceneBudget.current().pixels();}
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
