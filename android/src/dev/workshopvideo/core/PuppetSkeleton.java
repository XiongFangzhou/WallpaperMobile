package dev.workshopvideo.core;

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Rest-pose attachment points of a Wallpaper Engine puppet model (.mdl): the MDLS skeleton and MDAT attachments.
 * Child layers with an "attachment" are placed relative to that point of their parent's skeleton.
 */
public final class PuppetSkeleton {
    /** 2D rest transform of an attachment in model space (y up, pixels): translation and rotation in radians. */
    public record Point(float x,float y,float angle){}
    private PuppetSkeleton(){}

    /** Rest-pose attachment points by name, read through the full model so both stay consistent. */
    public static Map<String,Point> attachments(byte[] mdl)throws IOException {
        PuppetModel model=PuppetModel.parse(mdl);Map<String,Point> out=new HashMap<>();
        for(String name:model.attachmentBones.keySet()){PuppetModel.Point p=model.attachment(name,null);if(!Float.isFinite(p.x())||!Float.isFinite(p.y()))throw new IOException("挂点位置无效");out.put(name,new Point(p.x(),p.y(),p.angle()));}
        if(out.isEmpty())throw new IOException("骨骼模型没有挂点");return out;
    }

    /** Offset of "NAMExxxx\0" where xxxx are digits, or -1. */
    static int tag(byte[] data,String name){
        byte[] n=name.getBytes(StandardCharsets.US_ASCII);
        outer:for(int i=0;i+9<=data.length;i++){
            for(int k=0;k<4;k++)if(data[i+k]!=n[k])continue outer;
            for(int k=4;k<8;k++)if(data[i+k]<'0'||data[i+k]>'9')continue outer;
            if(data[i+8]==0)return i;
        }
        return -1;
    }
    private static String version(byte[] data,int at){return new String(data,at+4,4,StandardCharsets.US_ASCII);}
    private static String cstring(ByteBuffer b)throws IOException{int start=b.position();while(b.get()!=0)if(b.position()-start>4096)throw new IOException("骨骼名称过长");return new String(b.array(),start,b.position()-start-1,StandardCharsets.UTF_8);}
    private static float[] matrix(ByteBuffer b){float[] m=new float[16];for(int i=0;i<16;i++)m[i]=b.getFloat();return m;}
    /** Column-major 4x4 product a*b. */
    static float[] multiply(float[] a,float[] b){
        float[] r=new float[16];
        for(int c=0;c<4;c++)for(int row=0;row<4;row++){float sum=0;for(int k=0;k<4;k++)sum+=a[k*4+row]*b[c*4+k];r[c*4+row]=sum;}
        return r;
    }
}
