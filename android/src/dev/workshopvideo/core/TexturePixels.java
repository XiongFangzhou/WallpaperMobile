package dev.workshopvideo.core;
import java.io.*;import java.nio.*;import java.nio.charset.StandardCharsets;
/** WE TEX subset: embedded JPEG/PNG and raw RGBA/RGB/R8/RG88/DXT1/3/5. */
public final class TexturePixels {
 public record Image(int width,int height,int imageWidth,int imageHeight,int format,byte[] bytes){}
 public static Image read(byte[] src)throws IOException {return read(src,2048);}
 public static Image read(byte[] src,int edge)throws IOException {
  try{
   if(edge<1||edge>4096)throw new IOException("纹理解码尺寸无效");
   ByteBuffer b=ByteBuffer.wrap(src).order(ByteOrder.LITTLE_ENDIAN);if(!string(b).equals("TEXV0005")||!string(b).equals("TEXI0001"))throw new IOException("不支持的 TEX 头部");
   int format=b.getInt(),flags=b.getInt(),tw=b.getInt(),th=b.getInt(),iw=b.getInt(),ih=b.getInt();b.getInt();String version=string(b);int count=b.getInt();
   if((flags&4)!=0)throw new IOException("当前尚不支持 TEX 精灵动画");if(count!=1)throw new IOException("当前尚不支持多图 TEX");
   int v=version.equals("TEXB0001")?1:version.equals("TEXB0002")?2:version.equals("TEXB0003")?3:version.equals("TEXB0004")?4:0;if(v==0)throw new IOException("不支持的 TEX 容器");
   if(v==3)b.getInt();if(v==4){int free=b.getInt(),video=b.getInt();if(free==-1&&video==1)throw new IOException("场景包含视频纹理，当前场景路径尚不支持");if(video!=0)throw new IOException("TEX 视频标记无效");v=3;}
   int mips=b.getInt();if(mips<1||mips>32)throw new IOException("TEX mip 数量无效");int topW=0,topH=0;Image chosen=null;
   for(int mip=0;mip<mips;mip++){
    int w=b.getInt(),h=b.getInt();if(w<=0||h<=0||w>16384||h>16384)throw new IOException("TEX mip 尺寸无效");if(mip==0){topW=w;topH=h;}
    int zip=v>=2?b.getInt():0,unc=v>=2?b.getInt():0,n=b.getInt();if(zip!=0&&zip!=1||n<0||n>b.remaining()||n>128*1024*1024||unc<0||unc>256*1024*1024)throw new IOException("TEX 数据越界");
    boolean eligible=(long)w*h<=8*1024*1024&&(zip==0?n:unc)<=ContentArchive.MAX_BLOCK;
    if(eligible&&(chosen==null||Math.max(chosen.width,chosen.height)>edge)){
     byte[] data=new byte[n];b.get(data);if(zip==1)data=ContentArchive.lz4(data,unc);
     int cropW=Math.min(w,Math.max(1,(int)Math.ceil(iw*w/(double)topW))),cropH=Math.min(h,Math.max(1,(int)Math.ceil(ih*h/(double)topH)));
     chosen=new Image(w,h,cropW,cropH,format,data);
    }else b.position(b.position()+n);
   }
   if(chosen==null)throw new IOException("TEX 没有符合手机内存预算的 mip 层");return chosen;
  }catch(BufferUnderflowException|IllegalArgumentException e){throw new IOException("TEX 资源截断",e);}
 }
 private static String string(ByteBuffer b)throws IOException{StringBuilder s=new StringBuilder();for(int i=0;i<16&&b.hasRemaining();i++){int c=b.get()&255;if(c==0)return s.toString();s.append((char)c);}throw new IOException("TEX 标识无效");}
 public static int[] pixels(Image img)throws IOException {
  int w=img.width,h=img.height,f=img.format;byte[] d=img.bytes;int[] out=new int[w*h];
  int step=f==0?4:f==1?3:f==8?2:f==9?1:0;
  if(step>0){if(d.length<(long)w*h*step)throw new IOException("TEX 像素不完整");for(int i=0,p=0;i<out.length;i++,p+=step){int r=d[p]&255,g=step>1?d[p+1]&255:r,blue=step>2?d[p+2]&255:r,a=step==4?d[p+3]&255:255;out[i]=a<<24|r<<16|g<<8|blue;}return out;}
  if(f!=4&&f!=6&&f!=7)throw new IOException("尚不支持的 TEX 编码："+f);
  int block=f==7?8:16,bx=(w+3)/4,by=(h+3)/4;if(d.length<(long)bx*by*block)throw new IOException("DXT 像素截断");
  for(int y=0;y<by;y++)for(int x=0;x<bx;x++){
   int at=(y*bx+x)*block,c=at+(f==7?0:8),c0=u16(d,c),c1=u16(d,c+2);int[] pal={rgb(c0),rgb(c1),0,0};boolean four=f!=7||c0>c1;
   pal[2]=mix(pal[0],pal[1],four?2:1,four?3:2);pal[3]=four?mix(pal[0],pal[1],1,3):0;
   long bits=u32(d,c+4);int[] alpha=new int[8];long ab=0;if(f==4){alpha[0]=d[at]&255;alpha[1]=d[at+1]&255;for(int k=2;k<8;k++){if(alpha[0]>alpha[1])alpha[k]=((8-k)*alpha[0]+(k-1)*alpha[1])/7;else alpha[k]=k<6?((6-k)*alpha[0]+(k-1)*alpha[1])/5:k==6?0:255;}for(int k=0;k<6;k++)ab|=(long)(d[at+2+k]&255)<<(8*k);}
   for(int k=0;k<16;k++){int px=x*4+k%4,py=y*4+k/4;if(px>=w||py>=h)continue;int color=pal[(int)(bits>>>(2*k))&3],a=f==4?alpha[(int)(ab>>>(3*k))&7]:f==6?((d[at+k/2]>>((k%2)*4))&15)*17:color>>>24;out[py*w+px]=(color&0xffffff)|a<<24;}
  }return out;
 }
 private static int u16(byte[] b,int p){return (b[p]&255)|((b[p+1]&255)<<8);}private static long u32(byte[] b,int p){return Integer.toUnsignedLong(u16(b,p)|(u16(b,p+2)<<16));}
 private static int rgb(int c){int r=(c>>11)&31,g=(c>>5)&63,b=c&31;return 0xff000000|((r*255/31)<<16)|((g*255/63)<<8)|(b*255/31);}
 private static int mix(int a,int b,int wa,int total){int out=0xff000000;for(int shift:new int[]{16,8,0})out|=((((a>>shift)&255)*wa+((b>>shift)&255)*(total-wa))/total)<<shift;return out;}
}
