package dev.workshopvideo;
import android.graphics.*;import org.json.JSONObject;import dev.workshopvideo.core.EffectMotion;
/** Shared scene transforms for posters, live surfaces and Android pixel regression checks. */
final class ScenePainter {
 static void layers(Canvas c,WallpaperContent.Scene scene,Paint p,JSONObject options,EffectMotion.Frame frame,float pointerX,float pointerY,boolean down){int index=0;
  for(WallpaperContent.Layer l:scene.layers){int i=index++;c.save();
   float dx=(float)options.optDouble("layerX"+i,0)*scene.width,dy=(float)options.optDouble("layerY"+i,0)*scene.height;
   float depth=(float)options.optDouble("depth"+i,l.depth)*(float)options.optDouble("layerDepth",1);
   if(frame!=null){dx+=frame.x()*scene.width*.5f*depth;dy+=frame.y()*scene.height*.5f*depth;}
   c.translate(dx,dy);c.rotate(l.rotation,l.x+l.w/2,l.y+l.h/2);p.setAlpha((int)(l.alpha*options.optDouble("opacity"+i,1)*255));
   RectF target=new RectF(l.x,l.y,l.x+l.w,l.y+l.h);
   if(l.bitmap!=null)c.drawBitmap(l.bitmap,null,target,p);else{p.setColor(Color.WHITE);p.setTextSize(l.h);c.drawText(l.text,l.x,l.y+l.h,p);}
   // X-ray is exposed only for the explicitly supported simple two-image variant.
   if(l.reveal!=null&&options.optBoolean("xray",true)&&down){
    c.save();Path circle=new Path();float radius=l.w*(float)options.optDouble("xraySize",l.revealSize)*.5f;float[] local={pointerX,pointerY};Matrix transform=new Matrix();c.getMatrix(transform);if(transform.invert(transform)){transform.mapPoints(local);circle.addCircle(local[0],local[1],Math.max(1,radius),Path.Direction.CW);c.clipPath(circle);c.drawBitmap(l.reveal,null,target,p);}c.restore();
   }
   c.restore();
  }
 }
 static Bitmap poster(WallpaperContent.Scene scene,int edge){float ratio=Math.min(1,edge/(float)Math.max(scene.width,scene.height));Bitmap out=Bitmap.createBitmap(Math.max(1,(int)(scene.width*ratio)),Math.max(1,(int)(scene.height*ratio)),Bitmap.Config.ARGB_8888);Canvas c=new Canvas(out);c.drawColor(Color.BLACK);c.scale(ratio,ratio);layers(c,scene,new Paint(3),new JSONObject(),null,0,0,false);return out;}
 static void verify(Bitmap image)throws java.io.IOException {int w=image.getWidth(),h=image.getHeight(),lit=0,total=0;int[] row=new int[w];for(int y=0;y<h;y++){image.getPixels(row,0,w,0,y,w,1);for(int color:row){total++;if((color>>>24)>8&&((color>>16&255)>8||(color>>8&255)>8||(color&255)>8))lit++;}}if(lit<Math.max(1,total/10000))throw new java.io.IOException("场景默认画面为全黑或透明，未通过画面检查；可能依赖尚未支持的遮罩、合成或脚本");}
 private ScenePainter(){}
}
