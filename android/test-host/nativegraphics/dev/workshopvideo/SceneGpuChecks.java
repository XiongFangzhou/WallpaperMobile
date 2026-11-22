package dev.workshopvideo;

import android.graphics.*;
import dev.workshopvideo.core.*;
import java.io.*;
import java.nio.file.Files;
import java.util.Base64;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35,manifest=Config.NONE,application=android.app.Application.class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
public class SceneGpuChecks {
    private static String pixels(Bitmap image,boolean premultiply){
        byte[] out=new byte[image.getWidth()*image.getHeight()*4];int i=0;
        for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++){int c=image.getPixel(x,y),a=Color.alpha(c);out[i++]=(byte)(Color.red(c)*(premultiply?a/255f:1));out[i++]=(byte)(Color.green(c)*(premultiply?a/255f:1));out[i++]=(byte)(Color.blue(c)*(premultiply?a/255f:1));out[i++]=(byte)a;}
        return Base64.getEncoder().encodeToString(out);
    }
    private WallpaperContent.Scene scene(){
        WallpaperContent.Scene scene=new WallpaperContent.Scene();scene.width=192;scene.height=96;
        WallpaperContent.Layer back=new WallpaperContent.Layer();back.x=-3;back.y=-2;back.w=198;back.h=100;back.depth=.4f;back.bitmap=Bitmap.createBitmap(192,96,Bitmap.Config.ARGB_8888);
        for(int y=0;y<96;y++)for(int x=0;x<192;x++)back.bitmap.setPixel(x,y,Color.rgb(x,255-x,y*2));scene.layers.add(back);
        WallpaperContent.Layer front=new WallpaperContent.Layer();front.x=72;front.y=12;front.w=48;front.h=68;front.rotation=17;front.depth=1.5f;front.alpha=.73f;front.bitmap=Bitmap.createBitmap(48,68,Bitmap.Config.ARGB_8888);front.reveal=Bitmap.createBitmap(48,68,Bitmap.Config.ARGB_8888);
        for(int y=0;y<68;y++)for(int x=0;x<48;x++){front.bitmap.setPixel(x,y,Color.argb(x<12?100:220,230,y*3,30));front.reveal.setPixel(x,y,Color.argb(180,15,200,x*5));}scene.layers.add(front);return scene;
    }
    @Test public void layerPosesMatchNativeCanvasAndExportRealShaderCases()throws Exception{
        JSONArray cases=new JSONArray();
        try(WallpaperContent.Scene scene=scene()){
            JSONArray textures=new JSONArray();for(WallpaperContent.Layer l:scene.layers){textures.put(new JSONObject().put("width",l.bitmap.getWidth()).put("height",l.bitmap.getHeight()).put("pixels",pixels(l.bitmap,true)));if(l.reveal!=null)textures.put(new JSONObject().put("width",l.reveal.getWidth()).put("height",l.reveal.getHeight()).put("pixels",pixels(l.reveal,true)));}
            for(float page:new float[]{0,.25f,.5f,.75f,1})for(boolean reveal:new boolean[]{false,true})for(boolean colored:new boolean[]{false,true}){
                int width=64,height=128;WallpaperEffects effects=new WallpaperEffects();effects.options(new JSONObject().put("pan",true).put("zoom",1.23).put("x",.018).put("y",-.012).put("rotation",colored?12:0).put("brightness",colored?.83:1).put("contrast",colored?.8:1).put("layerX1",.015).put("layerY1",-.03).put("opacity1",.8).put("layerDepth",.7).put("xraySize",.8));effects.offset=page;effects.touch(.52f,.48f,reveal?0:1);
                EffectMotion.Frame motion=effects.frame(false);CanvasCamera.Frame camera=CanvasCamera.frame(scene.width,scene.height,width,height,false,true,page,motion);
                Bitmap expected=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888),actual=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
                Canvas canvas=new Canvas(expected);canvas.drawColor(Color.BLACK);canvas.translate(width/2f,height/2f);canvas.rotate(motion.rotation());
                float aspect=width/(float)height,scale=Math.max(width/(float)scene.width,height/(float)scene.height)*motion.zoom();double angle=Math.toRadians(motion.rotation());scale*=Math.abs(Math.cos(angle))+Math.abs(Math.sin(angle))*Math.max(aspect,1/aspect);
                float marginX=(scene.width*scale-width)/2,marginY=(scene.height*scale-height)/2;canvas.translate(EffectMotion.clamp(motion.x()*width*2-(page-.5f)*(scene.width*scale-width),-marginX,marginX),EffectMotion.clamp(motion.y()*height*2,-marginY,marginY));canvas.scale(scale,scale);canvas.translate(-scene.width/2f,-scene.height/2f);
                Paint paint=new Paint(3);paint.setColorFilter(effects.colorFilter());ScenePainter.layers(canvas,scene,paint,effects.options,motion,.52f*width,.48f*height,reveal);
                Canvas gpu=new Canvas(actual);gpu.drawColor(Color.BLACK);JSONArray commands=new JSONArray();int texture=0;
                for(int i=0;i<scene.layers.size();i++){
                    WallpaperContent.Layer layer=scene.layers.get(i);SceneGeometry.Pose pose=SceneGpu.pose(scene,layer,i,effects.options,camera,motion);
                    float ox=(pose.x()+1)*width/2,oy=(1-pose.y())*height/2;float[] points={ox,oy,ox+pose.xx()*width/2,oy-pose.xy()*height/2,ox+pose.yx()*width/2,oy-pose.yy()*height/2};
                    Matrix transform=new Matrix();transform.setPolyToPoly(new float[]{0,0,layer.bitmap.getWidth(),0,0,layer.bitmap.getHeight()},0,points,0,3);
                    paint.setAlpha((int)(layer.alpha*effects.options.optDouble("opacity"+i,1)*255));gpu.drawBitmap(layer.bitmap,transform,paint);
                    JSONArray axes=new JSONArray(new float[]{pose.x(),pose.y(),pose.xx(),pose.xy(),pose.yx(),pose.yy()});float opacity=paint.getAlpha()/255f;
                    commands.put(new JSONObject().put("texture",texture++).put("pose",axes).put("opacity",opacity).put("reveal",new JSONArray(new float[]{0,0,-1})));
                    if(layer.reveal!=null){if(reveal){float radius=layer.w*.8f*.5f*camera.scaleX()*width/scene.width;gpu.save();Path circle=new Path();circle.addCircle(.52f*width,.48f*height,radius,Path.Direction.CW);gpu.clipPath(circle);gpu.drawBitmap(layer.reveal,transform,paint);gpu.restore();commands.put(new JSONObject().put("texture",texture).put("pose",axes).put("opacity",opacity).put("reveal",new JSONArray(new float[]{.52f*width,.52f*height,radius})));}texture++;}
                }
                int differing=0;for(int y=0;y<height;y++)for(int x=0;x<width;x++){int a=expected.getPixel(x,y),b=actual.getPixel(x,y);if(Math.abs(Color.red(a)-Color.red(b))>3||Math.abs(Color.green(a)-Color.green(b))>3||Math.abs(Color.blue(a)-Color.blue(b))>3)differing++;}
                assertTrue("native layer pose mismatch page="+page+" reveal="+reveal+" colored="+colored+" pixels="+differing,differing<width*height*.04);
                float[] rgb=PictureColors.matrix(colored?.83f:1,colored?.8f:1,1,0);cases.put(new JSONObject().put("page",page).put("reveal",reveal).put("colored",colored).put("width",width).put("height",height).put("textures",textures).put("commands",commands).put("colors",new JSONArray(rgb)).put("expected",pixels(expected,false)));actual.recycle();expected.recycle();
            }
        }
        Files.writeString(new File(System.getProperty("graphics.work"),"scene-gpu-cases.json").toPath(),cases.toString());assertEquals(20,cases.length());
    }
}
