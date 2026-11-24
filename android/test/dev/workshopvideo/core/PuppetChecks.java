package dev.workshopvideo.core;
import static dev.workshopvideo.core.CoreChecks.*;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
final class PuppetChecks {
    static void run()throws Exception{
        // Root at (100,-50); child bone rotated 90 degrees, 10 px up from the root; attachment 5 px along the child's x axis.
        byte[] mdl=model(new float[][]{translate(100,-50,0),translate(0,10,(float)(Math.PI/2))},new int[]{-1,0},1,translate(5,0,0));
        var points=PuppetSkeleton.attachments(mdl);var p=points.get("Attachment");
        check(p!=null&&Math.abs(p.x()-100)<1e-3&&Math.abs(p.y()-(-35))<1e-3,"attachment follows the bone chain: "+p);
        check(Math.abs(p.angle()-Math.PI/2)<1e-4,"attachment carries the bone rotation");
        CanvasCamera.Frame camera=new CanvasCamera.Frame(1.3f,.9f,.12f,-.05f,7);
        SceneGeometry.Pose spot=SceneGeometry.pose(camera,3903,2260,1000,500,0,0,0);float[] back=SceneGeometry.sceneFromScreen(camera,3903,2260,(spot.x()+1)/2,(1-spot.y())/2);
        check(Math.abs(back[0]-1000)<.05&&Math.abs(back[1]-(2260-500))<.05,"touch positions map back to scene coordinates: "+back[0]+","+back[1]);
        byte[] other=mdl.clone();int at=PuppetSkeleton.tag(other,"MDLS");other[at+7]='3';
        boolean rejected=false;try{PuppetSkeleton.attachments(other);}catch(java.io.IOException e){rejected=true;}
        check(rejected,"unknown skeleton versions are rejected instead of guessed");
        rejected=false;try{PuppetSkeleton.attachments(java.util.Arrays.copyOf(mdl,mdl.length-20));}catch(java.io.IOException e){rejected=true;}
        check(rejected,"truncated attachment tables are rejected");
    }
    private static float[] translate(float x,float y,float angle){float c=(float)Math.cos(angle),s=(float)Math.sin(angle);return new float[]{c,s,0,0,-s,c,0,0,0,0,1,0,x,y,0,1};}
    private static byte[] model(float[][] bones,int[] parents,int attachBone,float[] attach){
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        ascii(out,"MDLV0023");out.write(0);for(int i=0;i<16;i++)out.write(0x7f);
        ascii(out,"MDLS0004");out.write(0);ints(out,0,bones.length);
        for(int i=0;i<bones.length;i++){out.write(0);ints(out,1,parents[i],64);floats(out,bones[i]);ascii(out,"{\"tm\":100.0}");out.write(0);}
        ascii(out,"MDAT0001");out.write(0);ints(out,0);shorts(out,1,attachBone);ascii(out,"Attachment");out.write(0);floats(out,attach);
        ascii(out,"MDLA0006");out.write(0);ints(out,0,0);
        return out.toByteArray();
    }
    private static void ascii(ByteArrayOutputStream out,String s){byte[] b=s.getBytes(StandardCharsets.UTF_8);out.write(b,0,b.length);}
    private static void ints(ByteArrayOutputStream out,int... v){ByteBuffer b=ByteBuffer.allocate(4*v.length).order(ByteOrder.LITTLE_ENDIAN);for(int x:v)b.putInt(x);out.write(b.array(),0,b.capacity());}
    private static void shorts(ByteArrayOutputStream out,int... v){ByteBuffer b=ByteBuffer.allocate(2*v.length).order(ByteOrder.LITTLE_ENDIAN);for(int x:v)b.putShort((short)x);out.write(b.array(),0,b.capacity());}
    private static void floats(ByteArrayOutputStream out,float[] v){ByteBuffer b=ByteBuffer.allocate(4*v.length).order(ByteOrder.LITTLE_ENDIAN);for(float x:v)b.putFloat(x);out.write(b.array(),0,b.capacity());}
}
