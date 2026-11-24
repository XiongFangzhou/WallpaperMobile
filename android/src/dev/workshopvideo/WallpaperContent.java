package dev.workshopvideo;
import android.graphics.*;import android.graphics.drawable.*;import org.json.*;
import dev.workshopvideo.core.*;import java.io.*;import java.nio.*;import java.nio.charset.StandardCharsets;import java.util.*;
/** File signatures determine media type. Archive previews never count as scene content. */
final class WallpaperContent {
 static boolean archive(File f){try(RandomAccessFile r=new RandomAccessFile(f,"r")){int n=r.readInt();if(n==0x504b0304)return true;r.seek(4);byte[] b=new byte[4];r.readFully(b);return new String(b,StandardCharsets.US_ASCII).equals("PKGM")||new String(b,StandardCharsets.US_ASCII).equals("PKGV");}catch(Exception e){return false;}}
 static String kind(File f){if(archive(f))return "scene";try{ImageDecoder.ImageInfo[] info=new ImageDecoder.ImageInfo[1];Drawable d=ImageDecoder.decodeDrawable(ImageDecoder.createSource(f),(decoder,i,source)->{info[0]=i;decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);decoder.setTargetSampleSize(Math.max(1,Math.max(i.getSize().getWidth(),i.getSize().getHeight())/256));});return d instanceof AnimatedImageDrawable?"animated":"image";}catch(Exception|OutOfMemoryError e){return "video";}}
 static Bitmap image(File f,int edge)throws Exception {return ImageDecoder.decodeBitmap(ImageDecoder.createSource(f),(d,i,s)->{d.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);float ratio=Math.min(1,edge/(float)Math.max(i.getSize().getWidth(),i.getSize().getHeight()));d.setTargetSize(Math.max(1,(int)(i.getSize().getWidth()*ratio)),Math.max(1,(int)(i.getSize().getHeight()*ratio)));});}
 static Bitmap bitmap(byte[] data)throws Exception {return bitmap(data,false);}
 /** Decode a layer image; sprite sheets are accepted only when their frame table is kept alongside. */
 static Bitmap bitmap(byte[] data,boolean sprite)throws Exception {
  if(data.length>9&&new String(data,0,8,StandardCharsets.US_ASCII).equals("TEXV0005")){TexturePixels.Image tex=sprite?TexturePixels.readSprite(data,2048):TexturePixels.read(data);BitmapFactory.Options embeddedBounds=new BitmapFactory.Options();embeddedBounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(tex.bytes(),0,tex.bytes().length,embeddedBounds);if(embeddedBounds.outWidth>0){embeddedBounds.inJustDecodeBounds=false;embeddedBounds.inSampleSize=Math.max(1,(Math.max(embeddedBounds.outWidth,embeddedBounds.outHeight)+2047)/2048);Bitmap embedded=BitmapFactory.decodeByteArray(tex.bytes(),0,tex.bytes().length,embeddedBounds);if(embedded==null)throw new IOException("TEX 内嵌图片无法解码");return embedded;}Bitmap all=Bitmap.createBitmap(TexturePixels.pixels(tex),tex.width(),tex.height(),Bitmap.Config.ARGB_8888);if(tex.imageWidth()!=tex.width()||tex.imageHeight()!=tex.height()){Bitmap crop=Bitmap.createBitmap(all,0,0,tex.imageWidth(),tex.imageHeight());if(crop!=all)all.recycle();return crop;}return all;}
  BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(data,0,data.length,options);if(options.outWidth<=0||options.outHeight<=0)throw new IOException("无法解码场景图层");options.inSampleSize=Math.max(1,Math.max(options.outWidth,options.outHeight)/2048);options.inJustDecodeBounds=false;Bitmap b=BitmapFactory.decodeByteArray(data,0,data.length,options);if(b==null)throw new IOException("无法解码场景图层");return b;
 }
 static JSONObject json(ContentArchive a,String name)throws Exception{return new JSONObject(new String(a.read(name,2*1024*1024),StandardCharsets.UTF_8).replaceFirst("^\uFEFF",""));}
 static String project(ContentArchive a){for(String n:a.names())if(n.equals("project.json"))return n;return "";}
 static String asset(ContentArchive a,String name)throws Exception {String value=ContentArchive.path(name);if(a.has(value))return value;if(a.has(value+".tex"))return value+".tex";if(a.has("materials/"+value+".tex"))return "materials/"+value+".tex";throw new IOException("场景缺少素材："+name);}
 static String mediaEntry(ContentArchive a)throws Exception {
  String p=project(a);if(p.isEmpty())return "";JSONObject meta=json(a,p);String type=meta.optString("type");
  if(type.equalsIgnoreCase("video")||type.equalsIgnoreCase("image")||type.equalsIgnoreCase("scene")&&meta.optString("file").toLowerCase(java.util.Locale.ROOT).endsWith(".pkg")){String name=meta.optString("file");return asset(a,name);}return "";
 }
 static final class Layer {Bitmap bitmap,reveal;float revealSize=.3f;String name="";float x,y,w,h,depth=1,alpha=1,rotation;String text;List<TexturePixels.Frame> frames=List.of();final List<SceneEffects.Effect> effects=new ArrayList<>();
  /** Skeletal layer: the mesh replaces the rectangle; model units map to the rectangle through puppetW/puppetH (the layer's unscaled size). */
  /** WE colour blend mode (0 normal … 32, numbering of common_blending.h) and additive material blending. */
  int blend;boolean additive;
  /** Scripted scenes: the source object, whether it is shown, its unscaled size and the static transform of its parents (x, y, sx, sy, radians; y up). */
  int objectId=-1;boolean visible=true;float baseW,baseH;float[] frame;
  /** Author-property conditions [[name, expected value or null for on/off], …] that must all hold for the layer to show. */
  JSONArray when;
  /** Drawn now: not hidden by a script and every property condition holds (phone choice in options as user.NAME, else the author's default). */
  boolean shown(JSONObject options,JSONObject properties){
   if(!visible)return false;if(when==null)return true;
   for(int i=0;i<when.length();i++){JSONArray c=when.optJSONArray(i);if(c==null)continue;String name=c.optString(0);
    Object value=options!=null&&options.has("user."+name)?options.opt("user."+name):properties==null||properties.optJSONObject(name)==null?null:properties.optJSONObject(name).opt("value");
    if(value==null)continue;
    if(c.isNull(1)){if(Boolean.FALSE.equals(value)||"false".equalsIgnoreCase(String.valueOf(value))||"0".equals(String.valueOf(value)))return false;}
    else if(!String.valueOf(value).equals(c.optString(1)))return false;}
   return true;
  }
  /** Particle system drawn instead of the bitmap rectangle; transform is the emitter in scene space {x, y, sx, sy, radians; y up}. */
  String particleData;Particles particles;float[] particleTransform;
  PuppetModel puppet;byte[] puppetData;float puppetW,puppetH;List<PuppetModel.Track> tracks=List.of();
  /** Child hanging from a puppet attachment: parent layer index, attachment, and its own origin/angle in that attachment's frame. */
  int attachParent=-1;String attachName="";float attachX,attachY,attachAngle;
  /** Per-frame state written by Scene.animate: bone poses and mesh vertices in rectangle coordinates (0..1, y down). */
  float[][] pose;float[] meshPoints;private float[] skinned;
  boolean moving(){return puppet!=null&&!tracks.isEmpty();}
  /** Source region of the bitmap at the given animation time, in 0..1 coordinates. */
  TexturePixels.Frame region(double seconds){return frames.isEmpty()?FULL:frames.get(TexturePixels.frameAt(frames,seconds));}}
 static final TexturePixels.Frame FULL=new TexturePixels.Frame(0,0,1,1,1);
 static List<TexturePixels.Frame> frames(JSONArray list)throws IOException {
  if(list==null||list.length()==0)return List.of();if(list.length()>1024)throw new IOException("图层帧数过多");ArrayList<TexturePixels.Frame> result=new ArrayList<>();
  for(int i=0;i<list.length();i++){JSONArray f=list.optJSONArray(i);if(f==null||f.length()!=5)throw new IOException("图层帧格式无效");float u0=(float)f.optDouble(0,-1),v0=(float)f.optDouble(1,-1),u1=(float)f.optDouble(2,-1),v1=(float)f.optDouble(3,-1),s=(float)f.optDouble(4,0);
   if(!(u0>=0&&v0>=0&&u1<=1&&v1<=1&&u1>u0&&v1>v0&&s>0&&s<=60))throw new IOException("图层帧区域无效");result.add(new TexturePixels.Frame(u0,v0,u1,v1,s));}
  return List.copyOf(result);
 }
 static final class Scene implements AutoCloseable {int width=1080,height=2400;final ArrayList<Layer> layers=new ArrayList<>();JSONObject defaults=new JSONObject();
  /** scripts.json of an adapted scene, run by SceneScripts; null when the scene has no scripts. */
  String scripts;private double particleClock=-1;
  /** Author switches offered on the phone: name → {type bool|combo, value, label, options}. */
  JSONObject properties=new JSONObject();
  /** Finger in scene coordinates (y up) for emitters that follow the pointer; set by the renderer each frame. */
  volatile float cursorX,cursorY;volatile boolean cursorDown;
  boolean animated(){if(scripts!=null)return true;for(Layer l:layers)if(l.frames.size()>1||!l.effects.isEmpty()||l.moving()||l.particles!=null)return true;return false;}
  /** Applies a script frame for one source object: origin, scale, angles (x,y,z each), alpha, visible, playing — in WE scene space. */
  void applyScript(int objectId,float[] s){
   for(Layer l:layers){
    if(l.objectId!=objectId||s.length<11)continue;
    l.alpha=EffectMotion.clamp(s[9],0,1);l.visible=s[10]>.5f;
    if(l.particles!=null){
     if(s.length>11)l.particles.emitting=s[11]>.5f;
     float[] f=l.frame==null?new float[]{0,0,1,1,0}:l.frame;double c=Math.cos(f[4]),sn=Math.sin(f[4]);float lx=s[0]*f[2],ly=s[1]*f[3];
     l.particleTransform=new float[]{(float)(f[0]+lx*c-ly*sn),(float)(f[1]+lx*sn+ly*c),s[3]*f[2],s[4]*f[3],s[8]+f[4]};continue;
    }
    if(l.attachParent>=0||!(l.baseW>0&&l.baseH>0))continue;
    float[] f=l.frame==null?new float[]{0,0,1,1,0}:l.frame;double c=Math.cos(f[4]),sn=Math.sin(f[4]);float lx=s[0]*f[2],ly=s[1]*f[3];
    double wx=f[0]+lx*c-ly*sn,wy=f[1]+lx*sn+ly*c;float w=Math.abs(l.baseW*s[3]*f[2]),h=Math.abs(l.baseH*s[4]*f[3]);
    if(!(w>0&&h>0)||!Double.isFinite(wx)||!Double.isFinite(wy)||w>65536||h>65536)continue;
    l.w=w;l.h=h;l.x=(float)(wx-w/2);l.y=(float)(height-wy-h/2);l.rotation=-(float)Math.toDegrees(s[8]+f[4]);
   }
  }
  /** Poses skeletal layers for the time and moves attached children with them; safe to call every frame. */
  void animate(double seconds){
   double dt=particleClock<0||seconds<particleClock?0:Math.min(.25,seconds-particleClock);particleClock=seconds;
   for(Layer l:layers)if(l.particles!=null&&l.particles.spec.followCursor&&l.particleTransform!=null){
    float[] t=l.particleTransform;double c=Math.cos(-t[4]),s=Math.sin(-t[4]);float dx=cursorX-t[0],dy=cursorY-t[1];
    l.particles.pointer((float)((dx*c-dy*s)/(t[2]==0?1:t[2])),(float)((dx*s+dy*c)/(t[3]==0?1:t[3])),cursorDown);
   }
   if(dt>0)for(Layer l:layers)if(l.particles!=null&&l.visible)l.particles.advance(dt);
   for(Layer l:layers){
    if(l.puppet==null)continue;
    if(l.pose!=null&&!l.moving())continue;
    l.pose=l.puppet.pose(l.tracks,seconds);
    if(l.puppet.mesh!=null){int n=l.puppet.mesh.vertexCount();if(l.skinned==null){l.skinned=new float[n*2];l.meshPoints=new float[n*2];}l.puppet.skin(l.pose,l.skinned);
     for(int v=0;v<n;v++){l.meshPoints[v*2]=.5f+l.skinned[v*2]/l.puppetW;l.meshPoints[v*2+1]=.5f-l.skinned[v*2+1]/l.puppetH;}}
   }
   for(Layer child:layers){
    if(child.attachParent<0||child.attachParent>=layers.size())continue;Layer parent=layers.get(child.attachParent);if(parent.puppet==null||parent.pose==null)continue;
    PuppetModel.Point point=parent.puppet.attachment(child.attachName,parent.pose);if(point==null)continue;
    double ac=Math.cos(point.angle()),as=Math.sin(point.angle());double mx=point.x()+child.attachX*ac-child.attachY*as,my=point.y()+child.attachX*as+child.attachY*ac;
    double pa=-Math.toRadians(parent.rotation),pc=Math.cos(pa),ps=Math.sin(pa),lx=mx*parent.w/parent.puppetW,ly=my*parent.h/parent.puppetH;
    double cx=parent.x+parent.w/2+lx*pc-ly*ps,cy=height-(parent.y+parent.h/2)+lx*ps+ly*pc;
    child.x=(float)(cx-child.w/2);child.y=(float)(height-cy-child.h/2);child.rotation=-(float)Math.toDegrees(child.attachAngle+point.angle()+pa);
   }
  }public void close(){for(Layer l:layers){for(SceneEffects.Effect e:l.effects)e.recycle();if(l.bitmap!=null&&!l.bitmap.isRecycled())l.bitmap.recycle();if(l.reveal!=null&&!l.reveal.isRecycled())l.reveal.recycle();}}}
 static float[] vector(Object value,float...fallback){try{if(value instanceof JSONObject)value=((JSONObject)value).opt("value");if(value instanceof JSONArray){JSONArray a=(JSONArray)value;float[] f=new float[a.length()];for(int i=0;i<f.length;i++)f[i]=(float)a.getDouble(i);return f;}String[] s=String.valueOf(value).trim().split("[ ,]+");float[] f=new float[s.length];for(int i=0;i<s.length;i++)f[i]=Float.parseFloat(s[i]);return f;}catch(Exception e){return fallback;}}
 static Scene scene(File file)throws Exception {
  Scene result=new Scene();try(ContentArchive a=new ContentArchive(file)){
   boolean own=a.has("wallpaper-mobile.json");String entry=own?"wallpaper-mobile.json":"scene.json";
   if(!own){String p=project(a);if(!p.isEmpty()){String declared=json(a,p).optString("file",entry);if(declared.toLowerCase(Locale.ROOT).endsWith(".json")||!a.has(entry))entry=ContentArchive.path(declared);}}
   if(!a.has(entry))throw new IOException("包内没有可运行的场景描述；当前支持基础图层场景，不支持全部 WE 特效/脚本");
   JSONObject root=json(a,entry);if(hasRuntime(root))throw new IOException("该场景依赖尚不兼容的脚本、作者属性绑定或特效，不能完整运行");if(own&&!root.optString("format").equals("WallpaperMobileScene/1"))throw new IOException("场景格式版本不支持");
   if(own&&root.optJSONObject("properties")!=null)result.properties=root.getJSONObject("properties");
   if(own&&root.has("scripts")){byte[] scripts=a.read(asset(a,root.getString("scripts")),16*1024*1024);result.scripts=new String(scripts,StandardCharsets.UTF_8);}
   if(own){result.width=root.optInt("width",1080);result.height=root.optInt("height",2400);result.defaults=root.optJSONObject("effects");if(result.defaults==null)result.defaults=new JSONObject();}
   else {JSONObject general=root.optJSONObject("general");JSONObject projection=general==null?null:general.optJSONObject("orthogonalprojection");if(projection!=null){result.width=projection.optInt("width",1080);result.height=projection.optInt("height",2400);}}
   if(result.width<1||result.height<1||result.width>16384||result.height>16384)throw new IOException("场景尺寸无效");JSONArray list=root.optJSONArray(own?"layers":"objects");if(list==null||list.length()<1||list.length()>(own?64:32))throw new IOException("场景需要 1–"+(own?64:32)+" 个图层");long pixels=0;
   for(int i=0;i<list.length();i++){
    JSONObject object=list.getJSONObject(i);if(!own&&!object.optBoolean("visible",true))continue;
    if(!own&&(hasRuntime(object)||object.has("particle")&&!object.isNull("particle")))throw new IOException("该场景包含尚不兼容的 WE 脚本、粒子或特效，不能完整运行");
    Layer l=new Layer();l.name=object.optString("name","");result.layers.add(l);String image=object.optString(own?"file":"image","");
    if(!own&&!image.isEmpty()&&!image.equals("null")){
     JSONObject model=json(a,asset(a,image));if(hasRuntime(model))throw new IOException("场景模型依赖尚不兼容的特效");String material=model.optString("material");JSONObject mat=json(a,asset(a,material));JSONArray passes=mat.optJSONArray("passes");if(passes==null||passes.length()!=1)throw new IOException("当前只支持单通道基础材质");JSONObject pass=passes.getJSONObject(0);String shader=pass.optString("shader");if(!shader.isEmpty()&&!shader.equals("genericimage2"))throw new IOException("场景使用自定义着色器，当前不能完整运行");JSONArray textures=pass.optJSONArray("textures");if(textures==null||textures.length()!=1)throw new IOException("场景材质纹理不兼容");image=textures.getString(0);
    }
    if(!image.isEmpty()&&!image.equals("null")){l.bitmap=bitmap(a.read(asset(a,image),32*1024*1024));pixels+=(long)l.bitmap.getWidth()*l.bitmap.getHeight();if(pixels>16*1024*1024)throw new IOException("场景解码内存超过预算");}
    else if(own&&object.has("text"))l.text=object.optString("text");else throw new IOException("场景包含尚不支持的非图片对象");
    if(own&&object.has("xrayFile")){l.reveal=bitmap(a.read(asset(a,object.getString("xrayFile")),32*1024*1024));pixels+=(long)l.reveal.getWidth()*l.reveal.getHeight();if(pixels>16*1024*1024)throw new IOException("场景解码内存超过预算");l.revealSize=(float)Math.max(.01,Math.min(1,object.optDouble("xraySize",.3)));}
    if(own&&object.has("effectsFile")){
     JSONArray fx=new JSONArray(new String(a.read(asset(a,object.getString("effectsFile")),4*1024*1024),StandardCharsets.UTF_8));if(fx.length()>16)throw new IOException("图层特效过多");
     long[] budget={pixels};for(int e=0;e<fx.length();e++)l.effects.add(SceneEffects.parse(fx.getJSONObject(e),a,budget,16L*1024*1024));pixels=budget[0];
    }
    if(own&&object.has("particlesFile")){
     JSONObject data=new JSONObject(new String(a.read(asset(a,object.getString("particlesFile")),8*1024*1024),StandardCharsets.UTF_8));
     float[][] frames=null;JSONArray list2=data.optJSONArray("frames");if(list2!=null&&list2.length()>0){frames=new float[Math.min(1024,list2.length())][];for(int f=0;f<frames.length;f++){JSONArray q=list2.getJSONArray(f);frames[f]=new float[]{(float)q.optDouble(0),(float)q.optDouble(1),(float)q.optDouble(2),(float)q.optDouble(3)};}}
     l.particles=new Particles(SceneParticles.spec(data.getJSONObject("system"),data.optJSONObject("override"),data.optBoolean("additive"),frames),0x5eed0000L+i);
     JSONArray t=data.getJSONArray("transform");l.particleTransform=new float[5];for(int k=0;k<5;k++)l.particleTransform[k]=(float)t.optDouble(k,k==2||k==3?1:0);
    }
    if(own&&object.has("puppet")){
     l.puppetData=a.read(asset(a,object.getString("puppet")),64*1024*1024);l.puppet=PuppetModel.parse(l.puppetData);if(l.puppet.mesh==null)throw new IOException("骨骼网格格式不支持");
     JSONArray size=object.optJSONArray("puppetSize");l.puppetW=size==null?0:(float)size.optDouble(0,0);l.puppetH=size==null?0:(float)size.optDouble(1,0);if(!(l.puppetW>0&&l.puppetH>0))throw new IOException("骨骼图层尺寸无效");
     JSONArray tracks=object.optJSONArray("tracks");ArrayList<PuppetModel.Track> clips=new ArrayList<>();
     if(tracks!=null)for(int t=0;t<tracks.length()&&t<8;t++){JSONArray tr=tracks.getJSONArray(t);int id=tr.getInt(0);if(!l.puppet.animations.containsKey(id))continue;clips.add(new PuppetModel.Track(id,(float)EffectMotion.clamp((float)tr.optDouble(1,1),0,10),(float)EffectMotion.clamp((float)tr.optDouble(2,1),0,1),tr.optBoolean(3,true)));}
     l.tracks=List.copyOf(clips);
    }
    if(own)l.when=object.optJSONArray("when");
    if(own){l.blend=Math.max(0,Math.min(32,object.optInt("blend",0)));l.additive=object.optBoolean("additive");l.objectId=object.optInt("objectId",-1);l.visible=object.optBoolean("visible",true);
     JSONArray base=object.optJSONArray("base");if(base!=null){l.baseW=(float)base.optDouble(0,0);l.baseH=(float)base.optDouble(1,0);}
     JSONArray frame=object.optJSONArray("frame");if(frame!=null&&frame.length()==5){l.frame=new float[5];for(int f=0;f<5;f++)l.frame[f]=(float)frame.optDouble(f,0);}}
    if(own&&object.has("attach")){JSONObject at=object.getJSONObject("attach");l.attachParent=at.optInt("layer",-1);l.attachName=at.optString("name");l.attachX=(float)at.optDouble("x",0);l.attachY=(float)at.optDouble("y",0);l.attachAngle=(float)at.optDouble("angle",0);if(l.attachParent<0||l.attachParent>=i)l.attachParent=-1;}
    if(own){l.x=(float)object.optDouble("x",0);l.y=(float)object.optDouble("y",0);l.w=(float)object.optDouble("width",result.width);l.h=(float)object.optDouble("height",result.height);l.depth=(float)object.optDouble("depth",1);l.rotation=(float)object.optDouble("rotation",0);l.alpha=(float)object.optDouble("opacity",1);l.frames=frames(object.optJSONArray("frames"));}
    else {float[] origin=vector(object.opt("origin"),result.width/2f,result.height/2f),size=vector(object.opt("size"),l.bitmap.getWidth(),l.bitmap.getHeight()),scale=vector(object.opt("scale"),1,1),depth=vector(object.opt("parallaxDepth"),1,1),angle=vector(object.opt("angles"),0,0,0);l.w=size[0]*scale[0];l.h=size[1]*scale[Math.min(1,scale.length-1)];l.x=origin[0]-l.w/2;l.y=result.height-origin[1]-l.h/2;l.depth=depth[0];l.rotation=-(float)Math.toDegrees(angle[angle.length-1]);l.alpha=(float)object.optDouble("alpha",1);}
    if(!Float.isFinite(l.x)||!Float.isFinite(l.y)||!Float.isFinite(l.rotation)||Math.abs(l.x)>65536||Math.abs(l.y)>65536||!Float.isFinite(l.w)||!Float.isFinite(l.h)||l.w<=0||l.h<=0||l.w>65536||l.h>65536)throw new IOException("场景图层尺寸无效");l.depth=EffectMotion.clamp(l.depth,-2,2);l.alpha=EffectMotion.clamp(l.alpha,0,1);
   }if(result.layers.isEmpty())throw new IOException("场景没有可显示的图层");return result;
  }catch(Exception|OutOfMemoryError error){result.close();throw error;}
 }
 static boolean hasRuntime(JSONObject o){String text=o.toString();return text.contains("\"script\"")||text.contains("\"user\"")||o.has("effects")&&!o.isNull("effects")&&o.optJSONArray("effects")!=null&&o.optJSONArray("effects").length()>0;}
}
