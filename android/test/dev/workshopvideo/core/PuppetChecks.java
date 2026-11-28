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
        { // Mirrored / tilted layers: the shape matrix acts on the width and height vectors around the layer centre.
        CanvasCamera.Frame flat=new CanvasCamera.Frame(1,1,0,0,0);SceneGeometry.Pose plain=SceneGeometry.pose(flat,100,100,10,20,40,30,0),mirror=SceneGeometry.pose(flat,100,100,10,20,40,30,0,new float[]{-1,0,0,1});
        check(Math.abs(mirror.xx()+plain.xx())<1e-6&&Math.abs(mirror.yy()-plain.yy())<1e-6&&Math.abs((mirror.x()+mirror.xx()/2)-(plain.x()+plain.xx()/2))<1e-6,"mirror keeps the centre and flips the width axis");
        SceneGeometry.Pose tilt=SceneGeometry.pose(flat,100,100,10,20,40,30,0,new float[]{0.5f,0,0,1});check(Math.abs(tilt.xx()-plain.xx()*0.5f)<1e-6,"y tilt shortens the width");
        check(SceneGeometry.pose(flat,100,100,10,20,40,30,25,null).equals(SceneGeometry.pose(flat,100,100,10,20,40,30,25)),"no shape is the flat pose");}
        { // Bounded composition layers: their own rectangle fills their buffer; effect passes map back to the screen.
        for(float[] shape:new float[][]{null,{-1,0,0,1},{.6f,-.2f,0,.9f}}){
            SceneGeometry.Pose g=SceneGeometry.pose(camera,3840,2160,700,300,1600,900,17,shape);float[] m=SceneGeometry.onto(g);check(m!=null,"a placed rectangle has a buffer map");
            SceneGeometry.Pose own=SceneGeometry.map(m,g);
            check(Math.abs(own.x()+1)<1e-4&&Math.abs(own.y()+1)<1e-4&&Math.abs(own.xx()-2)<1e-4&&Math.abs(own.xy())<1e-4&&Math.abs(own.yx())<1e-4&&Math.abs(own.yy()-2)<1e-4,"the group's rectangle fills its buffer, top edge first: "+own);
            SceneGeometry.Pose child=SceneGeometry.pose(camera,3840,2160,900,500,200,100,-30,null),local=SceneGeometry.map(m,child);
            // A child's corner seen through the map lands where the group's own corners put it.
            float lx=local.x(),ly=local.y();float u=(lx+1)/2,v=(ly+1)/2;float sx=g.x()+u*g.xx()+v*g.yx(),sy=g.y()+u*g.xy()+v*g.yy();
            check(Math.abs(sx-child.x())<1e-4&&Math.abs(sy-child.y())<1e-4,"children keep their screen position through the group buffer");
            float[] e=SceneGeometry.effectMatrix(g);
            float tlx=e[0]*-1+e[1]*-1+e[3],tly=e[4]*-1+e[5]*-1+e[7],brx=e[0]+e[1]+e[3],bry=e[4]+e[5]+e[7];
            check(Math.abs(tlx-g.x())<1e-4&&Math.abs(tly-g.y())<1e-4&&Math.abs(brx-(g.x()+g.xx()+g.yx()))<1e-4&&Math.abs(bry-(g.y()+g.xy()+g.yy()))<1e-4,"the effect quad maps onto the layer on screen");
        }
        check(SceneGeometry.onto(new SceneGeometry.Pose(0,0,1,0,2,0))==null,"a degenerate rectangle has no buffer map");
        // The pointer, unprojected: the layer's corners and centre in its model space (pixels from the centre, y up).
        SceneGeometry.Pose g=SceneGeometry.pose(camera,3840,2160,700,300,1600,900,17,null);float[] inv=SceneGeometry.pointerInverse(g,400,225);
        float[][] corners={{g.x(),g.y(),-200,112.5f},{g.x()+g.xx()+g.yx(),g.y()+g.xy()+g.yy(),200,-112.5f},{g.x()+(g.xx()+g.yx())/2,g.y()+(g.xy()+g.yy())/2,0,0}};
        for(float[] c:corners){float mx=inv[0]*c[0]+inv[1]*c[1]+inv[3],my=inv[4]*c[0]+inv[5]*c[1]+inv[7];
            check(Math.abs(mx-c[2])<1e-2&&Math.abs(my-c[3])<1e-2,"pointer unprojection lands in model space: "+mx+","+my+" for "+c[2]+","+c[3]);}}
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
