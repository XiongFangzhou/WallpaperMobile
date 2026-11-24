package dev.workshopvideo.core;

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A Wallpaper Engine puppet (.mdl): the deformable mesh drawn instead of the layer rectangle, its skeleton,
 * attachment points and baked bone animations. Model space is in pixels, y up, around the layer origin.
 * Transforms are 2D affines {a,b,c,d,tx,ty}: x'=a*x+c*y+tx, y'=b*x+d*y+ty.
 */
public final class PuppetModel {
    /** Mesh: positions (x,y) and texture coordinates (u,v, v down) per vertex, four bone indices and weights, triangle indices. */
    public static final class Mesh {
        public final float[] positions,uvs,weights;public final int[] bones;public final short[] indices;
        Mesh(float[] positions,float[] uvs,int[] bones,float[] weights,short[] indices){this.positions=positions;this.uvs=uvs;this.bones=bones;this.weights=weights;this.indices=indices;}
        public int vertexCount(){return positions.length/2;}
    }
    /** One baked clip: per bone and frame, translation x/y, rotation z (radians, counter-clockwise) and scale x/y. */
    public static final class Animation {
        public final int id;public final String name,mode;public final float fps;public final int frames;final float[][] keys;
        Animation(int id,String name,String mode,float fps,int frames,float[][] keys){this.id=id;this.name=name;this.mode=mode;this.fps=fps;this.frames=frames;this.keys=keys;}
    }
    /** How a layer plays a clip, from the scene object's "animationlayers". */
    public record Track(int animation,float rate,float blend,boolean additive){}
    public record Point(float x,float y,float angle){}

    /** Null when the mesh uses a vertex layout this reader does not know; the skeleton is still usable for attachments. */
    public final Mesh mesh;
    final int[] parents;final float[][] bindLocal,bindWorld,inverseBind;final float[][] bindParts;
    final Map<String,int[]> attachmentBones=new HashMap<>();final Map<String,float[]> attachmentLocal=new HashMap<>();
    public final Map<Integer,Animation> animations;
    /** Direction of animated bone rotation; +1 is counter-clockwise in y-up model space. Kept adjustable only for render comparisons. */
    public static volatile float rotationSign=1f;
    /** Why the clips were not read (empty when they were, or when the model has none); the mesh still draws at rest. */
    public String animationError="";

    private PuppetModel(Mesh mesh,int[] parents,float[][] bindLocal,Map<Integer,Animation> animations){
        this.mesh=mesh;this.parents=parents;this.bindLocal=bindLocal;this.animations=Collections.unmodifiableMap(animations);
        int n=parents.length;bindWorld=new float[n][];inverseBind=new float[n][];bindParts=new float[n][];
        for(int i=0;i<n;i++){bindWorld[i]=parents[i]<0?bindLocal[i]:multiply(bindWorld[parents[i]],bindLocal[i]);inverseBind[i]=invert(bindWorld[i]);float[] m=bindLocal[i];bindParts[i]=new float[]{m[4],m[5],(float)Math.atan2(m[1],m[0]),(float)Math.hypot(m[0],m[1]),(float)Math.hypot(m[2],m[3])};}
    }
    public int boneCount(){return parents.length;}

    public static PuppetModel parse(byte[] mdl)throws IOException {
        try{
            if(mdl.length<9||!new String(mdl,0,8,StandardCharsets.US_ASCII).equals("MDLV0023"))throw new IOException("不支持的骨骼模型版本");
            int skeleton=PuppetSkeleton.tag(mdl,"MDLS");if(skeleton<0||!version(mdl,skeleton).equals("0004"))throw new IOException("不支持的骨骼版本");
            ByteBuffer b=ByteBuffer.wrap(mdl).order(ByteOrder.LITTLE_ENDIAN);b.position(skeleton+9);b.getInt();
            int count=b.getInt();if(count<1||count>1024)throw new IOException("骨骼数量无效");
            int[] parents=new int[count];float[][] local=new float[count][];
            for(int i=0;i<count;i++){
                cstring(b);b.getInt();int parent=b.getInt(),size=b.getInt();if(size!=64)throw new IOException("骨骼矩阵格式无效");
                float[] m=new float[16];for(int k=0;k<16;k++)m[k]=b.getFloat();cstring(b);
                if(parent>=i)throw new IOException("骨骼父级顺序无效");parents[i]=parent;local[i]=new float[]{m[0],m[1],m[4],m[5],m[12],m[13]};
            }
            Mesh mesh=null;try{mesh=mesh(mdl,skeleton,count);}catch(IOException unsupported){mesh=null;}
            Map<Integer,Animation> animations=new HashMap<>();int clips=PuppetSkeleton.tag(mdl,"MDLA");
            String animationError="";
            if(clips>0){if(!version(mdl,clips).equals("0006"))animationError="不支持的骨骼动画版本";else try{animations=animations(mdl,clips,count);}catch(IOException|BufferUnderflowException|IllegalArgumentException|IndexOutOfBoundsException bad){animations=new HashMap<>();animationError=bad.getMessage()==null?"骨骼动画数据无效":bad.getMessage();}}
            PuppetModel model=new PuppetModel(mesh,parents,local,animations);model.animationError=animationError;
            int attach=PuppetSkeleton.tag(mdl,"MDAT");
            if(attach>0&&version(mdl,attach).equals("0001")){
                b.position(attach+9);b.getInt();int points=b.getShort()&0xffff;
                for(int i=0;i<points;i++){
                    int bone=b.getShort()&0xffff;String name=cstring(b);float[] m=new float[16];for(int k=0;k<16;k++)m[k]=b.getFloat();
                    if(bone>=count)throw new IOException("挂点引用了不存在的骨骼");
                    model.attachmentBones.put(name,new int[]{bone});model.attachmentLocal.put(name,new float[]{m[0],m[1],m[4],m[5],m[12],m[13]});
                }
            }
            return model;
        }catch(BufferUnderflowException|IllegalArgumentException|IndexOutOfBoundsException e){throw new IOException("骨骼模型数据截断",e);}
    }

    /** Layout 0x0180000F: position 3f, normal 3f, tangent 4f, bone indices 4u, weights 4f, uv 2f (80 bytes), then 16-bit triangle indices. */
    private static Mesh mesh(byte[] mdl,int skeleton,int bones)throws IOException {
        ByteBuffer b=ByteBuffer.wrap(mdl).order(ByteOrder.LITTLE_ENDIAN);b.position(9);b.getInt();b.getInt();b.getInt();cstring(b);
        int limit=Math.min(b.position()+64,skeleton-8),format=0;
        while(b.position()<=limit){int value=b.getInt(b.position());if((value&0xffff0000)==0x01800000){format=value;break;}b.position(b.position()+1);}
        if(format!=0x0180000f)throw new IOException("不支持的骨骼网格格式");b.getInt();
        int bytes=b.getInt();if(bytes<=0||bytes%80!=0||bytes>b.remaining())throw new IOException("骨骼网格顶点无效");
        int n=bytes/80;if(n>65535)throw new IOException("骨骼网格顶点过多");
        float[] positions=new float[n*2],uvs=new float[n*2],weights=new float[n*4];int[] indices=new int[n*4];
        for(int i=0;i<n;i++){
            int at=b.position();positions[i*2]=b.getFloat(at);positions[i*2+1]=b.getFloat(at+4);
            float sum=0;for(int k=0;k<4;k++){int bone=b.getInt(at+40+k*4);float w=b.getFloat(at+56+k*4);if(!(w>=0)||w>0&&(bone<0||bone>=bones))throw new IOException("骨骼权重无效");indices[i*4+k]=Math.max(0,Math.min(bones-1,bone));weights[i*4+k]=w;sum+=w;}
            if(sum>0)for(int k=0;k<4;k++)weights[i*4+k]/=sum;else weights[i*4]=1;
            uvs[i*2]=b.getFloat(at+72);uvs[i*2+1]=b.getFloat(at+76);b.position(at+80);
        }
        int indexBytes=b.getInt();if(indexBytes<=0||indexBytes%6!=0||indexBytes>b.remaining())throw new IOException("骨骼网格三角形无效");
        short[] triangles=new short[indexBytes/2];for(int i=0;i<triangles.length;i++){triangles[i]=b.getShort();if((triangles[i]&0xffff)>=n)throw new IOException("骨骼网格索引越界");}
        return new Mesh(positions,uvs,indices,weights,triangles);
    }

    private static Map<Integer,Animation> animations(byte[] mdl,int at,int bones)throws IOException {
        ByteBuffer b=ByteBuffer.wrap(mdl).order(ByteOrder.LITTLE_ENDIAN);b.position(at+9);int end=b.getInt(),count=b.getInt();
        if(count<0||count>256)throw new IOException("骨骼动画数量无效");Map<Integer,Animation> out=new HashMap<>();
        for(int a=0;a<count;a++){
            int id=b.getInt();b.getInt();String name=cstring(b),mode=cstring(b);float fps=b.getFloat();int frames=b.getInt();b.getInt();int tracks=b.getInt();
            if(!(fps>0&&fps<=240)||frames<1||frames>100000||tracks!=bones)throw new IOException("骨骼动画头无效");
            float[][] keys=new float[tracks][];
            for(int t=0;t<tracks;t++){
                b.getInt();int size=b.getInt();if(size<=0||size%36!=0||size>b.remaining())throw new IOException("骨骼动画关键帧无效");
                int n=size/36;float[] k=new float[n*5];
                for(int f=0;f<n;f++){float tx=b.getFloat(),ty=b.getFloat();b.getFloat();float rx=b.getFloat(),ry=b.getFloat(),rz=b.getFloat(),sx=b.getFloat(),sy=b.getFloat();b.getFloat();
                    if(Math.abs(rx)>1e-4||Math.abs(ry)>1e-4)throw new IOException("骨骼动画含 3D 旋转");k[f*5]=tx;k[f*5+1]=ty;k[f*5+2]=rz;k[f*5+3]=sx;k[f*5+4]=sy;}
                keys[t]=k;
            }
            b.position(b.position()+35);out.put(id,new Animation(id,name,mode,fps,frames,keys));
        }
        if(b.position()!=end)throw new IOException("骨骼动画长度不一致");
        return out;
    }

    /** Bone world transforms at a time, applying the tracks in order; additive tracks add their difference from the rest pose. */
    public float[][] pose(List<Track> tracks,double seconds){
        int n=parents.length;float[][] parts=new float[n][];for(int i=0;i<n;i++)parts[i]=bindParts[i].clone();
        boolean animated=false;
        for(Track track:tracks){
            Animation clip=animations.get(track.animation());if(clip==null||track.blend()<=0)continue;animated=true;
            double frame=seconds*clip.fps*track.rate();int keys=clip.keys[0].length/5;double span=Math.max(1,Math.min(clip.frames,keys-1));
            if(clip.mode.equals("mirror")){double period=span*2;frame=((frame%period)+period)%period;if(frame>span)frame=period-frame;}
            else if(clip.mode.equals("single"))frame=Math.max(0,Math.min(span,frame));
            else frame=((frame%span)+span)%span;
            int k0=(int)Math.floor(frame),k1=Math.min(keys-1,k0+1);float f=(float)(frame-k0),w=track.blend();
            for(int i=0;i<n;i++){
                float[] k=clip.keys[i],p=parts[i],rest=bindParts[i];float[] v=new float[5];
                for(int c=0;c<5;c++)v[c]=k[k0*5+c]+(k[k1*5+c]-k[k0*5+c])*f;v[2]*=rotationSign;
                if(track.additive()){p[0]+=w*(v[0]-rest[0]);p[1]+=w*(v[1]-rest[1]);p[2]+=w*(v[2]-rest[2]);
                    p[3]*=1+w*(rest[3]==0?0:v[3]/rest[3]-1);p[4]*=1+w*(rest[4]==0?0:v[4]/rest[4]-1);}
                else for(int c=0;c<5;c++)p[c]+=w*(v[c]-p[c]);
            }
        }
        float[][] world=new float[n][];
        for(int i=0;i<n;i++){
            float[] local;if(!animated)local=bindLocal[i];else{float[] p=parts[i];float c=(float)Math.cos(p[2]),s=(float)Math.sin(p[2]);local=new float[]{c*p[3],s*p[3],-s*p[4],c*p[4],p[0],p[1]};}
            world[i]=parents[i]<0?local:multiply(world[parents[i]],local);
        }
        return world;
    }

    /** Deformed vertex positions for a pose; equals the stored mesh at rest. */
    public void skin(float[][] world,float[] out){
        Mesh m=mesh;int n=m.vertexCount();float[][] skin=new float[world.length][];for(int i=0;i<world.length;i++)skin[i]=multiply(world[i],inverseBind[i]);
        for(int v=0;v<n;v++){
            float x=m.positions[v*2],y=m.positions[v*2+1],ox=0,oy=0;
            for(int k=0;k<4;k++){float w=m.weights[v*4+k];if(w==0)continue;float[] s=skin[m.bones[v*4+k]];ox+=w*(s[0]*x+s[2]*y+s[4]);oy+=w*(s[1]*x+s[3]*y+s[5]);}
            out[v*2]=ox;out[v*2+1]=oy;
        }
    }

    /** Attachment point in model space for a pose (or the rest pose when world is null). */
    public Point attachment(String name,float[][] world){
        int[] bone=attachmentBones.get(name);if(bone==null)return null;float[] m=multiply((world==null?bindWorld:world)[bone[0]],attachmentLocal.get(name));
        return new Point(m[4],m[5],(float)Math.atan2(m[1],m[0]));
    }
    public boolean hasAttachment(String name){return attachmentBones.containsKey(name);}
    public List<Integer> animationIds(){return new ArrayList<>(animations.keySet());}

    static float[] multiply(float[] p,float[] q){return new float[]{p[0]*q[0]+p[2]*q[1],p[1]*q[0]+p[3]*q[1],p[0]*q[2]+p[2]*q[3],p[1]*q[2]+p[3]*q[3],p[0]*q[4]+p[2]*q[5]+p[4],p[1]*q[4]+p[3]*q[5]+p[5]};}
    static float[] invert(float[] m){float det=m[0]*m[3]-m[1]*m[2];if(Math.abs(det)<1e-12)return new float[]{1,0,0,1,0,0};float a=m[3]/det,b=-m[1]/det,c=-m[2]/det,d=m[0]/det;return new float[]{a,b,c,d,-(a*m[4]+c*m[5]),-(b*m[4]+d*m[5])};}
    private static String version(byte[] data,int at){return new String(data,at+4,4,StandardCharsets.US_ASCII);}
    private static String cstring(ByteBuffer b)throws IOException{int start=b.position();while(b.get()!=0)if(b.position()-start>4096)throw new IOException("骨骼名称过长");return new String(b.array(),start,b.position()-start-1,StandardCharsets.UTF_8);}
}
