package dev.workshopvideo;
import android.graphics.*;import org.json.JSONObject;import dev.workshopvideo.core.EffectMotion;
/** Shared scene transforms for posters, live surfaces and Android pixel regression checks. */
final class ScenePainter {
 static void layers(Canvas c,WallpaperContent.Scene scene,Paint p,JSONObject options,EffectMotion.Frame frame,float pointerX,float pointerY,boolean down){layers(c,scene,p,options,frame,pointerX,pointerY,down,0);}
 static void layers(Canvas c,WallpaperContent.Scene scene,Paint p,JSONObject options,EffectMotion.Frame frame,float pointerX,float pointerY,boolean down,double seconds){
  scene.applyUser(options);scene.animate(seconds);
  // Replaced text bitmaps are left to the garbage collector: the canvas may still reference them.
  scene.refreshTexts(new java.util.ArrayList<>());
  for(int i=0;i<scene.layers.size();i++)if(scene.layers.get(i).group<0)paint(c,scene,p,options,frame,pointerX,pointerY,down,seconds,i);
  p.setBlendMode(BlendMode.SRC_OVER);
 }
 /** One layer; a composition layer draws its children together, at its own opacity. */
 private static void paint(Canvas c,WallpaperContent.Scene scene,Paint p,JSONObject options,EffectMotion.Frame frame,float pointerX,float pointerY,boolean down,double seconds,int i){
  WallpaperContent.Layer l=scene.layers.get(i);if(!l.shown(options,scene.properties))return;
  if(l.compose){int alpha=(int)(EffectMotion.clamp(l.opacity(options)*(float)options.optDouble("opacity"+i,1),0,1)*255);int saved=c.saveLayerAlpha(null,alpha);
   if(l.bounded){
    // Wallpaper Engine renders a composition layer at its size: children outside its rectangle are cut off.
    float dx=(float)options.optDouble("layerX"+i,0)*scene.width,dy=(float)options.optDouble("layerY"+i,0)*scene.height,depth=(float)options.optDouble("depth"+i,l.depth)*(float)options.optDouble("layerDepth",1);
    if(frame!=null){dx+=frame.x()*scene.width*.5f*depth;dy+=frame.y()*scene.height*.5f*depth;}
    float cx=l.x+l.w/2,cy=l.y+l.h/2;Matrix m=new Matrix();m.setTranslate(dx,dy);m.preRotate(l.rotation,cx,cy);
    if(l.shape!=null){Matrix s=new Matrix();s.setValues(new float[]{l.shape[0],l.shape[1],0,l.shape[2],l.shape[3],0,0,0,1});m.preTranslate(cx,cy);m.preConcat(s);m.preTranslate(-cx,-cy);}
    Path clip=new Path();clip.addRect(l.x,l.y,l.x+l.w,l.y+l.h,Path.Direction.CW);clip.transform(m);c.clipPath(clip);
   }
   for(int j=i+1;j<scene.layers.size();j++)if(scene.layers.get(j).group==i)paint(c,scene,p,options,frame,pointerX,pointerY,down,seconds,j);
   c.restoreToCount(saved);return;}
  c.save();
   float dx=(float)options.optDouble("layerX"+i,0)*scene.width,dy=(float)options.optDouble("layerY"+i,0)*scene.height;
   float depth=(float)options.optDouble("depth"+i,l.depth)*(float)options.optDouble("layerDepth",1);
   if(frame!=null){dx+=frame.x()*scene.width*.5f*depth;dy+=frame.y()*scene.height*.5f*depth;}
   c.translate(dx,dy);c.rotate(l.rotation,l.x+l.w/2,l.y+l.h/2);
   if(l.shape!=null){android.graphics.Matrix m=new android.graphics.Matrix();m.setValues(new float[]{l.shape[0],l.shape[1],0,l.shape[2],l.shape[3],0,0,0,1});float cx=l.x+l.w/2,cy=l.y+l.h/2;c.translate(cx,cy);c.concat(m);c.translate(-cx,-cy);}p.setAlpha((int)(l.opacity(options)*options.optDouble("opacity"+i,1)*255));
   RectF target=new RectF(l.x,l.y,l.x+l.w,l.y+l.h);ColorFilter savedFilter=p.getColorFilter();if(l.tint[0]!=1||l.tint[1]!=1||l.tint[2]!=1)p.setColorFilter(tinted(savedFilter,l.tint));p.setBlendMode(l.additive?BlendMode.PLUS:blendMode(l.blend));
   if(l.particles!=null&&l.bitmap!=null)particles(c,scene,l,p);
   else if(l.bitmap!=null&&l.meshPoints!=null)mesh(c,l,p,seconds);
   else if(l.bitmap!=null){Rect source=null;if(!l.frames.isEmpty()){var r=l.region(seconds);int bw=l.bitmap.getWidth(),bh=l.bitmap.getHeight();source=new Rect(Math.round(r.u0()*bw),Math.round(r.v0()*bh),Math.max(1,Math.round(r.u1()*bw)),Math.max(1,Math.round(r.v1()*bh)));}c.drawBitmap(l.bitmap,source,target,p);}
   // X-ray is exposed only for the explicitly supported simple two-image variant.
   if(l.reveal!=null&&options.optBoolean("xray",true)&&down){
    c.save();Path circle=new Path();float radius=l.w*(float)options.optDouble("xraySize",l.revealSize)*.5f;float[] local={pointerX,pointerY};Matrix transform=new Matrix();c.getMatrix(transform);if(transform.invert(transform)){transform.mapPoints(local);circle.addCircle(local[0],local[1],Math.max(1,radius),Path.Direction.CW);c.clipPath(circle);c.drawBitmap(l.reveal,null,target,p);}c.restore();
   }
   p.setColorFilter(savedFilter);c.restore();
 }
 /** The paint's colour adjustment followed by the layer colour. */
 static ColorFilter tinted(ColorFilter base,float[] tint){
  ColorMatrix m=new ColorMatrix();if(base instanceof ColorMatrixColorFilter cm)cm.getColorMatrix(m);
  ColorMatrix scale=new ColorMatrix();scale.setScale(tint[0],tint[1],tint[2],1);
  ColorMatrix out=new ColorMatrix(m);out.preConcat(scale);return new ColorMatrixColorFilter(out);
 }
 /** Nearest Android blend mode for posters and the canvas fallback; the GL path applies WE's exact formulas. */
 static BlendMode blendMode(int mode){
  return switch(mode){case 1,5->BlendMode.DARKEN;case 2->BlendMode.MULTIPLY;case 3->BlendMode.COLOR_BURN;case 6,10->BlendMode.LIGHTEN;case 7->BlendMode.SCREEN;case 8->BlendMode.COLOR_DODGE;case 9,31->BlendMode.PLUS;case 11->BlendMode.OVERLAY;case 12->BlendMode.SOFT_LIGHT;case 13->BlendMode.HARD_LIGHT;case 18->BlendMode.DIFFERENCE;case 19->BlendMode.EXCLUSION;case 26->BlendMode.HUE;case 27->BlendMode.SATURATION;case 28->BlendMode.COLOR;case 29->BlendMode.LUMINOSITY;default->BlendMode.SRC_OVER;};
 }
 /** Particle layer: every live particle as a textured, tinted quad. */
 private static void particles(Canvas c,WallpaperContent.Scene scene,WallpaperContent.Layer l,Paint p){
  int capacity=l.particles.capacity();float[] points=new float[capacity*8],uvs=new float[capacity*8],colors=new float[capacity*16];
  int quads=l.particles.vertices(l.particleTransform,scene.width,scene.height,p.getAlpha()/255f,points,uvs,colors);if(quads==0)return;
  float[] verts=new float[quads*8],texs=new float[quads*8];int[] tints=new int[quads*4];short[] indices=new short[quads*6];int bw=l.bitmap.getWidth(),bh=l.bitmap.getHeight();boolean additive=l.particles.spec.additive;
  for(int v=0;v<quads*4;v++){verts[v*2]=l.x+points[v*2]*l.w;verts[v*2+1]=l.y+points[v*2+1]*l.h;texs[v*2]=uvs[v*2]*bw;texs[v*2+1]=uvs[v*2+1]*bh;
   float a=colors[v*4+3];if(additive)tints[v]=Color.argb(255,clamp(colors[v*4]),clamp(colors[v*4+1]),clamp(colors[v*4+2]));else tints[v]=a<=0?0:Color.argb(clamp(a),clamp(colors[v*4]/a),clamp(colors[v*4+1]/a),clamp(colors[v*4+2]/a));}
  for(int q=0;q<quads;q++){int v=q*4,k=q*6;indices[k]=(short)v;indices[k+1]=(short)(v+1);indices[k+2]=(short)(v+2);indices[k+3]=(short)v;indices[k+4]=(short)(v+2);indices[k+5]=(short)(v+3);}
  Paint paint=new Paint(p);paint.setAlpha(255);paint.setShader(new BitmapShader(l.bitmap,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP));if(additive)paint.setBlendMode(BlendMode.PLUS);
  c.drawVertices(Canvas.VertexMode.TRIANGLES,verts.length,verts,0,texs,0,tints,0,indices,0,indices.length,paint);
 }
 private static int clamp(float v){return Math.max(0,Math.min(255,Math.round(v*255)));}
 /** Skeletal layer: textured triangles at the current pose, inside the layer rectangle. */
 private static void mesh(Canvas c,WallpaperContent.Layer l,Paint p,double seconds){
  var m=l.puppet.mesh;int n=m.vertexCount();float[] verts=new float[n*2],texs=new float[n*2];var r=l.region(seconds);int bw=l.bitmap.getWidth(),bh=l.bitmap.getHeight();
  for(int v=0;v<n;v++){verts[v*2]=l.x+l.meshPoints[v*2]*l.w;verts[v*2+1]=l.y+l.meshPoints[v*2+1]*l.h;texs[v*2]=(r.u0()+m.uvs[v*2]*(r.u1()-r.u0()))*bw;texs[v*2+1]=(r.v0()+m.uvs[v*2+1]*(r.v1()-r.v0()))*bh;}
  Paint paint=new Paint(p);paint.setShader(new BitmapShader(l.bitmap,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP));
  c.drawVertices(Canvas.VertexMode.TRIANGLES,verts.length,verts,0,texs,0,null,0,m.indices,0,m.indices.length,paint);
 }
 static Bitmap poster(WallpaperContent.Scene scene,int edge){float ratio=Math.min(1,edge/(float)Math.max(scene.width,scene.height));Bitmap out=Bitmap.createBitmap(Math.max(1,(int)(scene.width*ratio)),Math.max(1,(int)(scene.height*ratio)),Bitmap.Config.ARGB_8888);Canvas c=new Canvas(out);c.drawColor(Color.BLACK);c.scale(ratio,ratio);layers(c,scene,new Paint(3),new JSONObject(),null,0,0,false);return out;}
 static void verify(Bitmap image)throws java.io.IOException {int w=image.getWidth(),h=image.getHeight(),lit=0,total=0;int[] row=new int[w];for(int y=0;y<h;y++){image.getPixels(row,0,w,0,y,w,1);for(int color:row){total++;if((color>>>24)>8&&((color>>16&255)>8||(color>>8&255)>8||(color&255)>8))lit++;}}if(lit<Math.max(1,total/10000))throw new java.io.IOException("场景默认画面为全黑或透明，未通过画面检查；可能依赖尚未支持的遮罩、合成或脚本");}
 private ScenePainter(){}
}
