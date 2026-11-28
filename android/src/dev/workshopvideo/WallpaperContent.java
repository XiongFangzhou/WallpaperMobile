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
 static final class Layer {Bitmap bitmap,reveal;float revealSize=.3f;String name="";float x,y,w,h,depth=1,alpha=1,rotation;SceneText text;byte[] fontData;String fontName;List<TexturePixels.Frame> frames=List.of();final List<SceneEffects.Effect> effects=new ArrayList<>();
  /** Skeletal layer: the mesh replaces the rectangle; model units map to the rectangle through puppetW/puppetH (the layer's unscaled size). */
  /** WE colour blend mode (0 normal … 32, numbering of common_blending.h) and additive material blending. */
  int blend;boolean additive;
  /** Author property bound to this layer's opacity; the phone setting (user.NAME) replaces the saved value. */
  String alphaProperty;
  float opacity(JSONObject options){if(alphaProperty!=null&&options!=null&&options.has("user."+alphaProperty))return EffectMotion.clamp((float)options.optDouble("user."+alphaProperty,alpha),0,1);return alpha;}
  /** Scripted scenes: the source object, whether it is shown, its unscaled size and the static transform of its parents (x, y, sx, sy, radians; y up). */
  int objectId=-1;boolean visible=true;float baseW,baseH;float[] frame;
  /** Visibility as converted (restored when scripts report "as saved", -1); whether scripts place the layer. */
  boolean savedVisible=true,scriptPlaced;
  /** Author-property conditions [[name, expected value or null for on/off], …] that must all hold for the layer to show. */
  JSONArray when;
  /** Drawn now: not hidden by a script and every property condition holds (phone choice in options as user.NAME, else the author's default). */
  boolean shown(JSONObject options,JSONObject properties){return visible&&Scene.conditions(when,options,properties);}
  /** Layer colour (multiplies the image) and the author property bound to it. */
  float[] tint={1,1,1};String tintProperty;
  /** scenetexture author property whose chosen picture replaces the layer image; original keeps the converted image. */
  String textureProperty;Bitmap original;
  /** Mirroring and x/y rotation as a 2×2 matrix on the width/height vectors (SceneGeometry.pose); null when flat. */
  float[] shape;
  /** Video layer: the MP4 while converting (videoData); once loaded, the scene file and the range it plays from (video); bitmap is its first frame. */
  byte[] videoData;File video;long videoOffset,videoLength;
  boolean isVideo(){return video!=null||videoData!=null;}
  /** Composition layer: its children (group == its index) are drawn together and its effects apply to the result. */
  boolean compose;int group=-1;
  /** Fullscreen layer (a composition layer that starts from everything drawn below it, which its effects then replace). */
  boolean fullscreen;
  /**
   * Composition layer drawn as Wallpaper Engine does, inside its own rectangle: children render into a buffer of the
   * layer's size (clipped to it) and its effects work in that space. Scenes converted before this drew groups over the
   * whole screen.
   */
  boolean bounded;
  /**
   * Hidden by default but another layer's effect samples its result (_rt_imageLayerComposite_<id>_a): rendered only as
   * that texture, never drawn to the screen while hidden.
   */
  boolean sourceOnly;
  /** Rendered as an effect texture now: kept only for that, and every property condition holds. */
  boolean source(JSONObject options,JSONObject properties){return sourceOnly&&Scene.conditions(when,options,properties);}
  /** Local transforms from this object up to the scene root {ox, oy, sx, sy, radians} and the author properties bound to origin, scale and angles of each. */
  float[][] chain;String[][] chainBind;
  /** Particle system drawn instead of the bitmap rectangle; transform is the emitter in scene space {x, y, sx, sy, radians; y up}. */
  String particleData;Particles particles;float[] particleTransform;
  /** Kept so author settings bound to the system's instance overrides can rebuild it. */
  JSONObject particleSystem,particleOverride;float[][] particleFrames;boolean particleAdditive;
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
  /** Value of an author property: the phone's choice (options user.NAME), else the author's default. */
  static Object user(String name,JSONObject options,JSONObject properties){
   if(options!=null&&options.has("user."+name))return options.opt("user."+name);
   JSONObject p=properties==null?null:properties.optJSONObject(name);return p==null?null:p.opt("value");
  }
  /** All [[name, expected value or null for on/off]] conditions hold. */
  static boolean conditions(JSONArray when,JSONObject options,JSONObject properties){
   if(when==null)return true;
   for(int i=0;i<when.length();i++){JSONArray c=when.optJSONArray(i);if(c==null)continue;Object value=user(c.optString(0),options,properties);if(value==null)continue;
    if(c.isNull(1)?!PropertyCondition.truthy(value):!String.valueOf(value).equals(c.optString(1)))return false;}
   return true;
  }
  private String userKey;
  /**
   * Applies author settings to everything bound to them: layer place (origin, scale, angles along the parent chain),
   * colour, effect switches and parameters, and particle settings. Cheap when nothing changed.
   */
  void applyUser(JSONObject options){
   StringBuilder key=new StringBuilder();if(options!=null)for(Iterator<String> k=options.keys();k.hasNext();){String n=k.next();if(n.startsWith("user."))key.append(n).append('=').append(options.opt(n)).append(';');}
   String now=key.toString();if(now.equals(userKey))return;userKey=now;
   if(bloomOnProperty!=null){Object v=user(bloomOnProperty,options,properties);if(v!=null)bloom=PropertyCondition.truthy(v);}
   if(bloomStrengthProperty!=null){float[] v=UserValues.vector(user(bloomStrengthProperty,options,properties),1);if(v!=null)bloomStrength=v[0];}
   if(bloomThresholdProperty!=null){float[] v=UserValues.vector(user(bloomThresholdProperty,options,properties),1);if(v!=null)bloomThreshold=v[0];}
   if(bloomTintProperty!=null){float[] v=UserValues.vector(user(bloomTintProperty,options,properties),3);if(v!=null)bloomTint=new float[]{v[0],v[1],v[2]};}
   for(Layer l:layers){
    // Layers placed by scripts keep the scripts' position; setting-driven placement is for the others.
    if(l.chain!=null&&!l.scriptPlaced)place(l,options);
    if(l.tintProperty!=null){float[] c=UserValues.vector(user(l.tintProperty,options,properties),3);if(c!=null)l.tint=new float[]{c[0],c[1],c[2]};}
    if(l.textureProperty!=null){Bitmap chosen=picture(user(l.textureProperty,options,properties));
     if(chosen!=null&&l.bitmap!=chosen){if(l.original==null)l.original=l.bitmap;l.bitmap=chosen;}
     else if(chosen==null&&l.original!=null){l.bitmap=l.original;l.original=null;}}
    for(SceneEffects.Effect e:l.effects){
     for(Map.Entry<Integer,String> t:e.userTextures.entrySet()){int slot=t.getKey();Bitmap chosen=picture(user(t.getValue(),options,properties));
      if(chosen!=null){if(!e.originals.containsKey(slot))e.originals.put(slot,e.textures.get(slot));e.textures.set(slot,chosen);}
      else if(e.originals.containsKey(slot))e.textures.set(slot,e.originals.remove(slot));}
     if(e.when!=null)e.off=!conditions(e.when,options,properties);
     for(Map.Entry<String,String> b:e.bound.entrySet()){float[] old=e.uniforms.get(b.getKey());float[] v=UserValues.vector(user(b.getValue(),options,properties),old==null?0:old.length);if(v!=null&&(old==null||v.length==old.length))e.uniforms.put(b.getKey(),v);}
    }
    if(l.particleSystem!=null&&l.particleOverride!=null)rebuildParticles(l,options);
    if(l.text!=null){
     if(l.text.valueProperty!=null){Object v=user(l.text.valueProperty,options,properties);if(v!=null)l.text.value=String.valueOf(v);}
     if(l.text.sizeProperty!=null){Object v=user(l.text.sizeProperty,options,properties);if(v instanceof Number n&&Double.isFinite(n.doubleValue()))l.text.pointsize=n.floatValue();else if(v!=null)try{l.text.pointsize=Float.parseFloat(String.valueOf(v));}catch(NumberFormatException ignored){}}
    }
   }
  }
  /** Effect values and effect visibility computed by scripts (key fx.<effect id>.<pass>.<name> / fx.<effect id>.visible). */
  void applyEffectValue(int objectId,String key,float[] value){
   for(Layer l:layers){if(l.objectId!=objectId)continue;
    for(SceneEffects.Effect e:l.effects){
     if(key.equals(e.scriptedVisible))e.scriptOff=value[0]<.5f;
     String uniform=e.scripted.get(key);if(uniform==null)continue;float[] old=e.uniforms.get(uniform);
     if(old==null||old.length==value.length)e.uniforms.put(uniform,value.clone());
     else{float[] fit=old.clone();for(int i=0;i<fit.length;i++)fit[i]=value[Math.min(i,value.length-1)];e.uniforms.put(uniform,fit);}
    }}
  }
  /** Script-side changes to a text layer (ITextLayer text, pointsize, color). */
  void applyText(int objectId,String value,float pointsize,float[] color){
   for(Layer l:layers){if(l.objectId!=objectId||l.text==null)continue;if(value!=null)l.text.value=value;if(Float.isFinite(pointsize)&&pointsize>0)l.text.pointsize=pointsize;if(color!=null&&color.length>=3&&!l.text.opaque)l.tint=new float[]{color[0],color[1],color[2]};}
  }
  /**
   * Redraws text layers whose text or size changed. The box follows the text, keeping its aligned edge in place;
   * replaced bitmaps go to retired so the renderer can free them.
   */
  void refreshTexts(List<Bitmap> retired){
   for(Layer l:layers){
    if(l.text==null||!l.text.stale())continue;
    Bitmap next;try{next=l.text.draw();}catch(RuntimeException|OutOfMemoryError failed){continue;}
    float sx=l.baseW>0?l.w/l.baseW:1,sy=l.baseH>0?l.h/l.baseH:1,nw=l.text.blockW*sx,nh=l.text.blockH*sy;
    l.x-=(nw-l.w)*l.text.shiftX();l.y-=(nh-l.h)*l.text.shiftY();l.w=nw;l.h=nh;l.baseW=l.text.blockW;l.baseH=l.text.blockH;
    if(l.bitmap!=null)retired.add(l.bitmap);l.bitmap=next;
   }
  }
  private void place(Layer l,JSONObject options){
   float x=0,y=0,sx=1,sy=1,a=0;
   for(int n=0;n<l.chain.length;n++){
    float[] node=l.chain[n].clone();String[] b=l.chainBind[n];
    if(b[0]!=null){float[] v=UserValues.vector(user(b[0],options,properties),2);if(v!=null){node[0]=v[0];node[1]=v[1];}}
    if(b[1]!=null){float[] v=UserValues.vector(user(b[1],options,properties),2);if(v!=null){node[2]=v[0];node[3]=v[1];}}
    if(b[2]!=null){float[] v=UserValues.vector(user(b[2],options,properties),3);if(v!=null)node[4]=v[v.length-1];}
    if(n==0){x=node[0];y=node[1];sx=node[2];sy=node[3];a=node[4];continue;}
    double c=Math.cos(node[4]),s=Math.sin(node[4]);float lx=x*node[2],ly=y*node[3];
    x=node[0]+(float)(lx*c-ly*s);y=node[1]+(float)(lx*s+ly*c);sx*=node[2];sy*=node[3];a+=node[4];
   }
   if(!Float.isFinite(x)||!Float.isFinite(y)||!(sx>0)||!(sy>0))return;
   if(l.particles!=null){l.particleTransform=new float[]{x,y,sx,sy,a};return;}
   float w=l.baseW*sx,h=l.baseH*sy;if(!(w>0&&h>0)||w>65536||h>65536)return;
   l.w=w;l.h=h;l.x=x-w/2;l.y=height-y-h/2;l.rotation=-(float)Math.toDegrees(a);
  }
  private void rebuildParticles(Layer l,JSONObject options){
   try{
    JSONObject override=new JSONObject(l.particleOverride.toString());boolean changed=false;
    for(Iterator<String> k=l.particleOverride.keys();k.hasNext();){String n=k.next();Object v=l.particleOverride.opt(n);
     if(v instanceof JSONObject b&&b.opt("user") instanceof String u){Object chosen=user(u,options,properties);if(chosen!=null){override.put(n,new JSONObject().put("value",chosen instanceof String c&&c.startsWith("#")?UserValues.forScripts(c):chosen));changed=true;}}}
    if(!changed)return;Particles next=new Particles(SceneParticles.spec(l.particleSystem,override,l.particleAdditive,l.particleFrames),System.nanoTime());
    next.emitting=l.particles.emitting;l.particles=next;
   }catch(Exception ignored){}
  }
  /** Author switches offered on the phone: name → {type bool|combo, value, label, options}. */
  JSONObject properties=new JSONObject();
  /** Finger in scene coordinates (y up) for emitters that follow the pointer; set by the renderer each frame. */
  volatile float cursorX,cursorY;volatile boolean cursorDown;
  boolean animated(){if(scripts!=null)return true;for(Layer l:layers)if(l.frames.size()>1||!l.effects.isEmpty()||l.moving()||l.particles!=null||l.isVideo())return true;return false;}
  /** Applies a script frame for one source object: origin, scale, angles (x,y,z each), alpha, visible, playing — in WE scene space. */
  /** 2D camera from scripts (scene units from the canvas centre, y up; zoom relative) and the scene's camera shake. */
  /** Scene bloom (SceneBloom) and the author properties its fields follow; scripts may change it too. */
  volatile boolean bloom;volatile float bloomStrength=2,bloomThreshold=.65f;volatile float[] bloomTint={1,1,1};String bloomOnProperty,bloomStrengthProperty,bloomThresholdProperty,bloomTintProperty;
  void applyBloom(boolean on,float strength,float threshold,float[] tint){bloom=on;if(Float.isFinite(strength))bloomStrength=Math.max(0,Math.min(16,strength));if(Float.isFinite(threshold))bloomThreshold=Math.max(0,Math.min(1,threshold));if(tint!=null&&tint.length>=3)bloomTint=tint;}
  volatile float cameraX,cameraY,cameraZoom=1;float shakeSpeed,shakeAmplitude,shakeRoughness;boolean shake;
  void applyCamera(float x,float y,float zoom){if(Float.isFinite(x)&&Float.isFinite(y)&&Math.abs(x)<1e6&&Math.abs(y)<1e6){cameraX=x;cameraY=y;}if(Float.isFinite(zoom)&&zoom>.01f&&zoom<100)cameraZoom=zoom;}
  /**
   * Camera offset at a time, scripted position plus shake. Wallpaper Engine documents the shake fields but not their
   * units; here amplitude is a share of 2% of the canvas' short side and speed/roughness set two noise frequencies.
   */
  float[] cameraAt(double seconds){
   float x=cameraX,y=cameraY;
   if(shake&&shakeAmplitude>0){double t=seconds*Math.max(.01,shakeSpeed),a=shakeAmplitude*.02*Math.min(width,height),r=Math.max(0,Math.min(4,shakeRoughness));
    x+=(float)(a*(Math.sin(t*1.31)*.6+Math.sin(t*2.77+1.7)*.4+r*.25*Math.sin(t*7.3+.4)));y+=(float)(a*(Math.sin(t*1.07+2.1)*.6+Math.sin(t*3.19+.3)*.4+r*.25*Math.sin(t*6.1+2.9)));}
   return new float[]{x,y,cameraZoom};
  }
  void applyScript(int objectId,float[] s){
   for(Layer l:layers){
    if(l.objectId!=objectId||s.length<11)continue;
    l.alpha=EffectMotion.clamp(s[9],0,1);l.visible=s[10]<0?l.savedVisible:s[10]>.5f;
    if(l.particles!=null){
     if(s.length>11)l.particles.emitting=s[11]>.5f;
     if(s.length>=17){l.particleTransform=new float[]{s[12],s[13],s[14],s[15],s[16]};continue;}
     float[] f=l.frame==null?new float[]{0,0,1,1,0}:l.frame;double c=Math.cos(f[4]),sn=Math.sin(f[4]);float lx=s[0]*f[2],ly=s[1]*f[3];
     l.particleTransform=new float[]{(float)(f[0]+lx*c-ly*sn),(float)(f[1]+lx*sn+ly*c),s[3]*f[2],s[4]*f[3],s[8]+f[4]};continue;
    }
    if(l.attachParent>=0||!(l.baseW>0&&l.baseH>0))continue;
    // Newer runtimes report the world transform through the parents' current (scripted) transforms.
    float[] f=l.frame==null||s.length>=17?new float[]{0,0,1,1,0}:l.frame;boolean world=s.length>=17;
    float ox=world?s[12]:s[0],oy=world?s[13]:s[1],ssx=world?s[14]:s[3],ssy=world?s[15]:s[4],angle=world?s[16]:s[8];
    double c=Math.cos(f[4]),sn=Math.sin(f[4]);float lx=ox*f[2],ly=oy*f[3];
    double wx=f[0]+lx*c-ly*sn,wy=f[1]+lx*sn+ly*c;float w=Math.abs(l.baseW*ssx*f[2]),h=Math.abs(l.baseH*ssy*f[3]);
    if(!(w>0&&h>0)||!Double.isFinite(wx)||!Double.isFinite(wy)||w>65536||h>65536)continue;
    l.scriptPlaced=true;l.w=w;l.h=h;l.x=(float)(wx-w/2);l.y=(float)(height-wy-h/2);l.rotation=-(float)Math.toDegrees(angle+f[4]);
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
  }
  /** Pictures chosen for scenetexture settings, by file. */
  private final Map<String,Bitmap> pictures=new HashMap<>();
  /** The picture a scenetexture setting holds: a private copy made by UserPictures (…/user-textures/<sha1>.png); else null. */
  Bitmap picture(Object value){
   if(!(value instanceof String path)||path.isEmpty())return null;
   Bitmap cached=pictures.get(path);if(cached!=null&&!cached.isRecycled())return cached;
   File f=new File(path);File parent=f.getParentFile();
   if(parent==null||!parent.getName().equals("user-textures")||!f.getName().matches("[0-9a-f]{40}\\.png")||!f.isFile()||f.length()>64L*1024*1024)return null;
   try{Bitmap b=bitmap(java.nio.file.Files.readAllBytes(f.toPath()));pictures.put(path,b);return b;}catch(Exception|OutOfMemoryError unreadable){return null;}
  }
  public void close(){
   // Converted images come back first so each bitmap is recycled once; chosen pictures are recycled with the cache.
   for(Layer l:layers){if(l.original!=null){l.bitmap=l.original;l.original=null;}for(SceneEffects.Effect e:l.effects){for(Map.Entry<Integer,Object> o:e.originals.entrySet())e.textures.set(o.getKey(),o.getValue());e.originals.clear();}}
   for(Bitmap b:pictures.values())if(!b.isRecycled())b.recycle();pictures.clear();
   for(Layer l:layers){for(SceneEffects.Effect e:l.effects)e.recycle();if(l.bitmap!=null&&!l.bitmap.isRecycled())l.bitmap.recycle();if(l.reveal!=null&&!l.reveal.isRecycled())l.reveal.recycle();}}}
 static float[] vector(Object value,float...fallback){try{if(value instanceof JSONObject)value=((JSONObject)value).opt("value");if(value instanceof JSONArray){JSONArray a=(JSONArray)value;float[] f=new float[a.length()];for(int i=0;i<f.length;i++)f[i]=(float)a.getDouble(i);return f;}String[] s=String.valueOf(value).trim().split("[ ,]+");float[] f=new float[s.length];for(int i=0;i<s.length;i++)f[i]=Float.parseFloat(s[i]);return f;}catch(Exception e){return fallback;}}
 /** Largest layer video kept in a converted scene. */
 static final int VIDEO_LIMIT=64*1024*1024;
 /**
  * Layer videos are stored uncompressed in the converted scene and played in place. The scene records the entry's local
  * header offset; this reads that header back and returns {data offset, length} only when it is a stored entry of that
  * name whose data is an MP4 inside the file.
  */
 static long[] storedRange(File zip,long header,String name)throws IOException {
  byte[] want=name.getBytes(StandardCharsets.UTF_8);
  try(RandomAccessFile f=new RandomAccessFile(zip,"r")){
   if(header<0||header>f.length()-30)throw new IOException("图层视频位置无效");
   byte[] h=new byte[30];f.seek(header);f.readFully(h);ByteBuffer b=ByteBuffer.wrap(h).order(ByteOrder.LITTLE_ENDIAN);
   if(b.getInt(0)!=0x04034b50||b.getShort(8)!=0||(b.getShort(6)&8)!=0)throw new IOException("图层视频不是未压缩条目");
   long size=Integer.toUnsignedLong(b.getInt(22));int n=b.getShort(26)&0xffff,m=b.getShort(28)&0xffff;
   if(size!=Integer.toUnsignedLong(b.getInt(18))||size<16||size>VIDEO_LIMIT||n!=want.length)throw new IOException("图层视频条目大小无效");
   byte[] got=new byte[n];f.readFully(got);if(!Arrays.equals(got,want))throw new IOException("图层视频条目名称不符");
   long data=header+30+n+m;if(data>f.length()-size)throw new IOException("图层视频条目越界");
   byte[] head=new byte[8];f.seek(data);f.readFully(head);if(head[4]!='f'||head[5]!='t'||head[6]!='y'||head[7]!='p')throw new IOException("图层视频不是 MP4");
   return new long[]{data,size};
  }
 }
 /**
  * A scene repackaged after conversion (imported again, normalized) no longer holds its videos where the scene says, or
  * compresses them: the MP4 is then copied once into the app cache (java.io.tmpdir), named by the scene file's path, size
  * and date and the entry, and played from there. Copies unused for a week are removed.
  */
 static File videoCopy(ContentArchive a,String entry,File scene)throws Exception {
  File dir=new File(System.getProperty("java.io.tmpdir"),"scene-video");if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("无法创建视频缓存目录");
  byte[] digest=java.security.MessageDigest.getInstance("SHA-1").digest((scene.getAbsolutePath()+"|"+scene.length()+"|"+scene.lastModified()+"|"+entry).getBytes(StandardCharsets.UTF_8));
  StringBuilder name=new StringBuilder();for(byte b:digest)name.append(String.format(Locale.ROOT,"%02x",b));File out=new File(dir,name+".mp4");
  long now=System.currentTimeMillis();File[] old=dir.listFiles();if(old!=null)for(File f:old)if(!f.equals(out)&&now-f.lastModified()>7L*24*3600*1000)f.delete();
  if(out.isFile()&&out.length()>=16){out.setLastModified(now);return out;}
  File part=new File(dir,name+".part");
  try(OutputStream o=new BufferedOutputStream(new FileOutputStream(part))){a.copy(entry,o,VIDEO_LIMIT);}
  byte[] head=new byte[8];try(InputStream in=new FileInputStream(part)){if(in.read(head)!=8)head[4]=0;}
  if(part.length()<16||head[4]!='f'||head[5]!='t'||head[6]!='y'||head[7]!='p'||!part.renameTo(out)){part.delete();throw new IOException("图层视频无法读取");}
  return out;
 }
 static Scene scene(File file)throws Exception {
  Scene result=new Scene();try(ContentArchive a=new ContentArchive(file)){
   boolean own=a.has("wallpaper-mobile.json");String entry=own?"wallpaper-mobile.json":"scene.json";
   if(!own){String p=project(a);if(!p.isEmpty()){String declared=json(a,p).optString("file",entry);if(declared.toLowerCase(Locale.ROOT).endsWith(".json")||!a.has(entry))entry=ContentArchive.path(declared);}}
   if(!a.has(entry))throw new IOException("包内没有可运行的场景描述；当前支持基础图层场景，不支持全部 WE 特效/脚本");
   JSONObject root=json(a,entry);if(hasRuntime(root))throw new IOException("该场景依赖尚不兼容的脚本、作者属性绑定或特效，不能完整运行");if(own&&!root.optString("format").equals("WallpaperMobileScene/1"))throw new IOException("场景格式版本不支持");
   if(own&&root.optJSONObject("properties")!=null)result.properties=root.getJSONObject("properties");
   if(own&&root.has("scripts")){byte[] scripts=a.read(asset(a,root.getString("scripts")),16*1024*1024);result.scripts=new String(scripts,StandardCharsets.UTF_8);}
   if(own){result.width=root.optInt("width",1080);result.height=root.optInt("height",2400);result.defaults=root.optJSONObject("effects");if(result.defaults==null)result.defaults=new JSONObject();
    JSONObject shake=root.optJSONObject("camera")==null?null:root.getJSONObject("camera").optJSONObject("shake");
    JSONObject bloom=root.optJSONObject("bloom");
    if(bloom!=null){result.bloom=bloom.optBoolean("enabled");result.bloomStrength=(float)bloom.optDouble("strength",2);result.bloomThreshold=(float)bloom.optDouble("threshold",.65);float[] t=vector(bloom.opt("tint"),1,1,1);if(t.length>=3)result.bloomTint=t;
     result.bloomOnProperty=bloom.has("enabledProperty")?bloom.optString("enabledProperty"):null;result.bloomStrengthProperty=bloom.has("strengthProperty")?bloom.optString("strengthProperty"):null;
     result.bloomThresholdProperty=bloom.has("thresholdProperty")?bloom.optString("thresholdProperty"):null;result.bloomTintProperty=bloom.has("tintProperty")?bloom.optString("tintProperty"):null;}
    if(shake!=null){result.shake=true;result.shakeSpeed=(float)shake.optDouble("speed",3);result.shakeAmplitude=(float)Math.max(0,Math.min(10,shake.optDouble("amplitude",.5)));result.shakeRoughness=(float)shake.optDouble("roughness",1);}}
   else {JSONObject general=root.optJSONObject("general");JSONObject projection=general==null?null:general.optJSONObject("orthogonalprojection");if(projection!=null){result.width=projection.optInt("width",1080);result.height=projection.optInt("height",2400);}}
   if(result.width<1||result.height<1||result.width>16384||result.height>16384)throw new IOException("场景尺寸无效");JSONArray list=root.optJSONArray(own?"layers":"objects");if(list==null||list.length()<1||list.length()>(own?4096:32))throw new IOException("场景图层数过多");long pixels=0;
   HashMap<String,Typeface> typefaces=new HashMap<>();
   for(int i=0;i<list.length();i++){
    JSONObject object=list.getJSONObject(i);if(!own&&!object.optBoolean("visible",true))continue;
    if(!own&&(hasRuntime(object)||object.has("particle")&&!object.isNull("particle")))throw new IOException("该场景包含尚不兼容的 WE 脚本、粒子或特效，不能完整运行");
    Layer l=new Layer();l.name=object.optString("name","");result.layers.add(l);String image=object.optString(own?"file":"image","");
    if(!own&&!image.isEmpty()&&!image.equals("null")){
     JSONObject model=json(a,asset(a,image));if(hasRuntime(model))throw new IOException("场景模型依赖尚不兼容的特效");String material=model.optString("material");JSONObject mat=json(a,asset(a,material));JSONArray passes=mat.optJSONArray("passes");if(passes==null||passes.length()!=1)throw new IOException("当前只支持单通道基础材质");JSONObject pass=passes.getJSONObject(0);String shader=pass.optString("shader");if(!shader.isEmpty()&&!shader.equals("genericimage2"))throw new IOException("场景使用自定义着色器，当前不能完整运行");JSONArray textures=pass.optJSONArray("textures");if(textures==null||textures.length()!=1)throw new IOException("场景材质纹理不兼容");image=textures.getString(0);
    }
    if(!image.isEmpty()&&!image.equals("null")){l.bitmap=bitmap(a.read(asset(a,image),32*1024*1024));pixels+=(long)l.bitmap.getWidth()*l.bitmap.getHeight();if(pixels>SceneBudget.current().pixels())throw new IOException("场景解码内存超过预算");}
    else if(own&&object.has("text"))l.bitmap=Bitmap.createBitmap(1,1,Bitmap.Config.ARGB_8888);else throw new IOException("场景包含尚不支持的非图片对象");
    if(own&&object.has("xrayFile")){l.reveal=bitmap(a.read(asset(a,object.getString("xrayFile")),32*1024*1024));pixels+=(long)l.reveal.getWidth()*l.reveal.getHeight();if(pixels>SceneBudget.current().pixels())throw new IOException("场景解码内存超过预算");l.revealSize=(float)Math.max(.01,Math.min(1,object.optDouble("xraySize",.3)));}
    if(own&&object.has("effectsFile")){
     JSONArray fx=new JSONArray(new String(a.read(asset(a,object.getString("effectsFile")),4*1024*1024),StandardCharsets.UTF_8));if(fx.length()>24)throw new IOException("图层特效过多");
     long[] budget={pixels};for(int e=0;e<fx.length();e++)l.effects.add(SceneEffects.parse(fx.getJSONObject(e),a,budget,SceneBudget.current().pixels()));pixels=budget[0];
    }
    // An unreadable video leaves its first frame showing; conversion checks that every saved video loads.
    if(own&&object.has("videoFile")){String videoEntry=ContentArchive.path(object.getString("videoFile"));
     try{long[] range=storedRange(file,object.optLong("videoHeader",-1),videoEntry);l.video=file;l.videoOffset=range[0];l.videoLength=range[1];}
     catch(IOException moved){
      try{File copy=videoCopy(a,videoEntry,file);l.video=copy;l.videoOffset=0;l.videoLength=copy.length();}
      catch(InterruptedIOException stop){throw stop;}catch(Exception unreadable){android.util.Log.w("WallpaperContent","layer video not loaded: "+unreadable.getMessage());}
     }}
    if(own&&object.has("particlesFile")){
     JSONObject data=new JSONObject(new String(a.read(asset(a,object.getString("particlesFile")),8*1024*1024),StandardCharsets.UTF_8));
     float[][] frames=null;JSONArray list2=data.optJSONArray("frames");if(list2!=null&&list2.length()>0){frames=new float[Math.min(1024,list2.length())][];for(int f=0;f<frames.length;f++){JSONArray q=list2.getJSONArray(f);frames[f]=new float[]{(float)q.optDouble(0),(float)q.optDouble(1),(float)q.optDouble(2),(float)q.optDouble(3)};}}
     l.particleSystem=data.getJSONObject("system");l.particleOverride=data.optJSONObject("override");l.particleFrames=frames;l.particleAdditive=data.optBoolean("additive");
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
    if(own){l.compose=object.optBoolean("compose");l.fullscreen=l.compose&&object.optBoolean("fullscreen");l.bounded=l.compose&&!l.fullscreen&&object.optBoolean("bounded");l.sourceOnly=object.optBoolean("source");int g=object.optInt("group",-1);l.group=g>=0&&g<i?g:-1;}
    if(own){l.when=object.optJSONArray("when");l.tintProperty=object.has("tintProperty")?object.optString("tintProperty"):null;l.textureProperty=object.has("textureProperty")?object.optString("textureProperty"):null;
     JSONArray shape=object.optJSONArray("shape");if(shape!=null&&shape.length()==4){l.shape=new float[4];for(int k=0;k<4;k++)l.shape[k]=(float)shape.optDouble(k,k==0||k==3?1:0);}
     JSONArray tint=object.optJSONArray("tint");if(tint!=null&&tint.length()>=3)l.tint=new float[]{(float)tint.optDouble(0,1),(float)tint.optDouble(1,1),(float)tint.optDouble(2,1)};
     JSONArray chain=object.optJSONArray("chain");
     if(chain!=null&&chain.length()>0&&chain.length()<=32){l.chain=new float[chain.length()][];l.chainBind=new String[chain.length()][];
      for(int c=0;c<chain.length();c++){JSONArray node=chain.getJSONArray(c),v=node.getJSONArray(0),b=node.getJSONArray(1);l.chain[c]=new float[5];for(int k=0;k<5;k++)l.chain[c][k]=(float)v.optDouble(k,k==2||k==3?1:0);l.chainBind[c]=new String[3];for(int k=0;k<3&&k<b.length();k++)l.chainBind[c][k]=b.isNull(k)?null:b.optString(k);}}l.alphaProperty=object.has("alphaProperty")?object.optString("alphaProperty"):null;}
    if(own){l.blend=Math.max(0,Math.min(32,object.optInt("blend",0)));l.additive=object.optBoolean("additive");l.objectId=object.optInt("objectId",-1);l.visible=object.optBoolean("visible",true);l.savedVisible=l.visible;
     JSONArray base=object.optJSONArray("base");if(base!=null){l.baseW=(float)base.optDouble(0,0);l.baseH=(float)base.optDouble(1,0);}
     JSONArray frame=object.optJSONArray("frame");if(frame!=null&&frame.length()==5){l.frame=new float[5];for(int f=0;f<5;f++)l.frame[f]=(float)frame.optDouble(f,0);}}
    if(own&&object.optJSONObject("textLayer")!=null){
     l.text=SceneText.from(object.getJSONObject("textLayer"));String fontFile=object.optString("fontFile",""),fontKey=fontFile+"|"+object.optString("fontName");
     Typeface face=typefaces.get(fontKey);if(face==null){face=SceneText.typeface(fontFile.isEmpty()?null:a.read(asset(a,fontFile),SceneText.MAX_FONT_BYTES),object.optString("fontName"));typefaces.put(fontKey,face);}
     l.text.typeface=face;l.text.drawn(l.baseW,l.baseH);
    }
    if(own&&object.has("attach")){JSONObject at=object.getJSONObject("attach");l.attachParent=at.optInt("layer",-1);l.attachName=at.optString("name");l.attachX=(float)at.optDouble("x",0);l.attachY=(float)at.optDouble("y",0);l.attachAngle=(float)at.optDouble("angle",0);if(l.attachParent<0||l.attachParent>=i)l.attachParent=-1;}
    if(own){l.x=(float)object.optDouble("x",0);l.y=(float)object.optDouble("y",0);l.w=(float)object.optDouble("width",result.width);l.h=(float)object.optDouble("height",result.height);l.depth=(float)object.optDouble("depth",1);l.rotation=(float)object.optDouble("rotation",0);l.alpha=(float)object.optDouble("opacity",1);l.frames=frames(object.optJSONArray("frames"));}
    else {float[] origin=vector(object.opt("origin"),result.width/2f,result.height/2f),size=vector(object.opt("size"),l.bitmap.getWidth(),l.bitmap.getHeight()),scale=vector(object.opt("scale"),1,1),depth=vector(object.opt("parallaxDepth"),1,1),angle=vector(object.opt("angles"),0,0,0);l.w=size[0]*scale[0];l.h=size[1]*scale[Math.min(1,scale.length-1)];l.x=origin[0]-l.w/2;l.y=result.height-origin[1]-l.h/2;l.depth=depth[0];l.rotation=-(float)Math.toDegrees(angle[angle.length-1]);l.alpha=(float)object.optDouble("alpha",1);}
    if(!Float.isFinite(l.x)||!Float.isFinite(l.y)||!Float.isFinite(l.rotation)||Math.abs(l.x)>65536||Math.abs(l.y)>65536||!Float.isFinite(l.w)||!Float.isFinite(l.h)||l.w<=0||l.h<=0||l.w>65536||l.h>65536)throw new IOException("场景图层尺寸无效");l.depth=EffectMotion.clamp(l.depth,-2,2);l.alpha=EffectMotion.clamp(l.alpha,0,1);
   }if(result.layers.isEmpty())throw new IOException("场景没有可显示的图层");return result;
  }catch(Exception|OutOfMemoryError error){result.close();throw error;}
 }
 static boolean hasRuntime(JSONObject o){String text=o.toString();return text.contains("\"script\"")||text.contains("\"user\"")||o.has("effects")&&!o.isNull("effects")&&o.optJSONArray("effects")!=null&&o.optJSONArray("effects").length()>0;}
}
