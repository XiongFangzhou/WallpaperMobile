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
  int objects,images,particles,texts,sounds,models,scripts,bindings,xrays;
  String error="";
  void skip(String reason){skipped.put(reason,skipped.getOrDefault(reason,0)+1);}
  boolean usable(){return !scene.layers.isEmpty()&&error.isEmpty();}
  String describe(){
   StringBuilder s=new StringBuilder("原作：").append(objects).append(" 个对象，图片 ").append(images).append("、粒子 ").append(particles).append("、文字 ").append(texts).append("、音频 ").append(sounds).append("、模型 ").append(models).append("。\n");
   s.append("实际解码可保留：").append(scene.layers.size()).append(" 个图片图层（").append(scene.width).append(" × ").append(scene.height).append("）。\n");
   if(xrays>0)s.append("支持触摸 X-ray：").append(xrays).append(" 层；采用手机圆形揭示区域，保留作者的第二张图片。\n");
   if(!effects.isEmpty())s.append("不保留作者特效：").append(String.join("、",effects)).append("。\n");
   if(scripts>0||bindings>0)s.append("脚本 ").append(scripts).append(" 处、属性绑定 ").append(bindings).append(" 处；只取已存默认值，不执行脚本。\n");
   for(var e:skipped.entrySet())s.append("跳过 ").append(e.getValue()).append(" 层：").append(e.getKey()).append("。\n");
   s.append("适配版不保留未实现的粒子、动态文字、音频响应、3D 和后处理。可使用实际已解析的 X-ray、手机陀螺仪、触控及桌面滑动效果。");
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
   JSONObject general=root.optJSONObject("general"),projection=general==null?null:general.optJSONObject("orthogonalprojection");
   if(projection==null)throw new IOException("缺少正交画布尺寸，无法可靠恢复布局");
   r.scene.width=projection.optInt("width",0);r.scene.height=projection.optInt("height",0);
   if(r.scene.width<1||r.scene.height<1||r.scene.width>16384||r.scene.height>16384)throw new IOException("画布尺寸无效");
   JSONArray objects=root.getJSONArray("objects");r.objects=objects.length();if(r.objects>4096)throw new IOException("对象数量超出分析预算");
   long pixels=0;
   for(int i=0;i<objects.length();i++){
    if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("已停止适配");
    JSONObject o=objects.getJSONObject(i);String image=o.optString("image","");boolean imageObject=!image.isEmpty()&&!image.equals("null");
    if(imageObject)r.images++;if(o.has("particle")&&!o.isNull("particle"))r.particles++;if(o.has("text")&&!o.isNull("text"))r.texts++;if(o.has("sound")&&!o.isNull("sound"))r.sounds++;if(o.has("model")&&!o.isNull("model"))r.models++;
    if(!imageObject)continue;if(!visible(o)){r.skip("默认隐藏");continue;}
    Bitmap bitmap=null;
    try{
     if(r.scene.layers.size()>=32)throw new IOException("超过 32 层预算");
     if(o.has("parent")&&!o.isNull("parent")&&number(o.opt("parent"),-1)>=0)throw new IOException("父子变换尚未实现");
     if(o.has("animationlayers")&&!o.isNull("animationlayers"))throw new IOException("骨骼或动画图层");
     for(String key:new String[]{"origin","size","scale","angles"}){Object raw=o.opt(key);if(raw instanceof JSONObject&&((JSONObject)raw).isNull("value"))throw new IOException("图层变换没有已存默认值");}
     float[] crop=WallpaperContent.vector(o.opt("cropoffset"),0,0);for(float v:crop)if(Math.abs(v)>.001)throw new IOException("非零裁切偏移");
     JSONObject model=WallpaperContent.json(a,WallpaperContent.asset(a,image));scan(model,r);
     JSONObject mat=WallpaperContent.json(a,WallpaperContent.asset(a,model.getString("material")));scan(mat,r);
     JSONArray passes=mat.optJSONArray("passes");if(passes==null||passes.length()!=1)throw new IOException("多通道或非图片材质");
     JSONObject pass=passes.getJSONObject(0);String shader=pass.optString("shader");if(!shader.equals("genericimage2")&&!shader.equals("genericimage4"))throw new IOException("自定义着色器");
     // Blend modes other than normal need a compositor; don't render additive masks as opaque images.
     if(pass.optInt("blending",0)>1||o.optInt("colorBlendMode",0)!=0)throw new IOException("非标准混合模式");
     JSONArray textures=pass.optJSONArray("textures");if(textures==null||textures.length()!=1||!(textures.opt(0) instanceof String))throw new IOException("没有可解码的基础图片纹理");
     String texture=textures.getString(0);bitmap=WallpaperContent.bitmap(a.read(WallpaperContent.asset(a,texture),32*1024*1024));
     if(pixels+(long)bitmap.getWidth()*bitmap.getHeight()>16L*1024*1024)throw new IOException("超过 1600 万像素内存预算");
     float[] pos=WallpaperContent.vector(o.opt("origin"),r.scene.width/2f,r.scene.height/2f),sz=WallpaperContent.vector(o.opt("size"),bitmap.getWidth(),bitmap.getHeight()),scale=WallpaperContent.vector(o.opt("scale"),1,1),angles=WallpaperContent.vector(o.opt("angles"),0,0,0),depth=WallpaperContent.vector(o.opt("parallaxDepth"),1,1);
     if(pos.length<2||sz.length<2||scale.length<2||angles.length<3)throw new IOException("不完整的图层变换");
     if(scale[0]<=0||scale[1]<=0||Math.abs(angles[0])>.001||Math.abs(angles[1])>.001)throw new IOException("镜像或 3D 旋转");
     WallpaperContent.Layer layer=new WallpaperContent.Layer();layer.name=o.optString("name","图层 "+(i+1));layer.w=sz[0]*scale[0];layer.h=sz[1]*scale[1];layer.x=pos[0]-layer.w/2;layer.y=r.scene.height-pos[1]-layer.h/2;layer.rotation=-(float)Math.toDegrees(angles[2]);layer.depth=Math.max(-2,Math.min(2,depth[0]));layer.alpha=(float)Math.max(0,Math.min(1,number(o.opt("alpha"),1)));
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
      }catch(Exception failure){if(layer.reveal!=null){layer.reveal.recycle();layer.reveal=null;}r.error="X-ray 无法适配："+failure.getMessage();}
     }
     layer.bitmap=bitmap;bitmap=null;pixels+=(long)layer.bitmap.getWidth()*layer.bitmap.getHeight();r.scene.layers.add(layer);
    }catch(InterruptedIOException stop){throw stop;}catch(Exception e){r.skip(e.getMessage()==null?"图片无法解码":e.getMessage());}finally{if(bitmap!=null)bitmap.recycle();}
   }
   if(r.scene.layers.isEmpty()&&r.error.isEmpty())r.error="未找到可安全恢复的基础图片；不会使用封面代替场景";
   return r;
  }catch(Exception|OutOfMemoryError e){r.scene.close();throw e;}
 }
 static void write(Report report,File target)throws Exception {
  if(!report.usable())throw new IOException("此场景无法生成手机适配版");
  JSONObject root=new JSONObject().put("format","WallpaperMobileScene/1").put("width",report.scene.width).put("height",report.scene.height).put("adapted",true).put("adaptation",report.describe());
  JSONObject effects=EffectOptions.normalize(null);effects.put("gyro",true);effects.put("touch",true);root.put("effects",effects);JSONArray layers=new JSONArray();
  boolean done=false;try{
   try(ZipOutputStream zip=new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(target)))){int index=0;for(WallpaperContent.Layer l:report.scene.layers){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("适配已暂停");String name="layers/"+(index++)+".png";zip.putNextEntry(new ZipEntry(name));if(!l.bitmap.compress(Bitmap.CompressFormat.PNG,100,zip))throw new IOException("无法保存图层");zip.closeEntry();JSONObject layer=new JSONObject().put("file",name).put("name",l.name).put("x",l.x).put("y",l.y).put("width",l.w).put("height",l.h).put("rotation",l.rotation).put("depth",l.depth).put("opacity",l.alpha);if(l.reveal!=null){String reveal="layers/xray-"+index+".png";zip.putNextEntry(new ZipEntry(reveal));if(!l.reveal.compress(Bitmap.CompressFormat.PNG,100,zip))throw new IOException("无法保存 X-ray 图层");zip.closeEntry();layer.put("xrayFile",reveal).put("xraySize",l.revealSize);}layers.put(layer);}root.put("layers",layers);zip.putNextEntry(new ZipEntry("wallpaper-mobile.json"));zip.write(root.toString().getBytes(StandardCharsets.UTF_8));zip.closeEntry();}
   try(WallpaperContent.Scene verify=WallpaperContent.scene(target)){if(verify.layers.size()!=layers.length())throw new IOException("适配版校验未通过");}done=true;
  }finally{if(!done)target.delete();}
 }
 private SceneAdapter(){}
}
