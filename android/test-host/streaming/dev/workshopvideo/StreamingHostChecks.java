package dev.workshopvideo;
import java.io.*;import java.nio.file.*;import java.util.*;import java.util.concurrent.atomic.*;
import dev.workshopvideo.core.*;import in.dragonbra.javasteam.types.*;import android.net.Uri;
import androidx.media3.common.C;import androidx.media3.datasource.*;import androidx.media3.extractor.*;import androidx.media3.extractor.mp4.*;
public class StreamingHostChecks {
 static int checks;static void check(boolean ok,String what){if(!ok)throw new AssertionError(what);checks++;}
 static DataSpec spec(long at,long size){return new DataSpec.Builder().setUri(Uri.parse("workshop://video/stream")).setPosition(at).setLength(size).build();}
 public static void main(String[] args)throws Exception{
  Path video=Path.of(Objects.requireNonNull(System.getenv("WVB_TEST_VIDEO"),"Set WVB_TEST_VIDEO to the streaming fixture"));
  DepotManifest manifest=DepotManifest.deserialize(Files.readAllBytes(Path.of(Objects.requireNonNull(System.getenv("WVB_TEST_MANIFEST"),"Set WVB_TEST_MANIFEST to its matching Steam manifest"))));
  FileData file=manifest.getFiles().stream().filter(f->f.getTotalSize()==6054215).findFirst().orElseThrow();
  AtomicInteger loads=new AtomicInteger(),closed=new AtomicInteger();AtomicBoolean fault=new AtomicBoolean(true);
  RandomAccessFile disk=new RandomAccessFile(video.toFile(),"r");
  WorkshopRangeReader reader=new WorkshopRangeReader(file,new WorkshopRangeReader.Source(){public byte[] load(ChunkData chunk)throws Exception{loads.incrementAndGet();if(fault.getAndSet(false))throw new IOException("injected transient CDN failure");byte[] b=new byte[chunk.getUncompressedLength()];disk.seek(chunk.getOffset());disk.readFully(b);return b;}public void close()throws IOException{closed.incrementAndGet();disk.close();}});
  WorkshopRangeDataSource source=new WorkshopRangeDataSource(new WorkshopRangeDataSource.Reader(){public long getSize()throws IOException{return reader.size();}public int readAt(long p,byte[] b,int o,int n)throws IOException{return reader.readAt(p,b,o,n);}});
  AtomicInteger starts=new AtomicInteger(),ends=new AtomicInteger(),transferred=new AtomicInteger();source.addTransferListener(new TransferListener(){public void onTransferInitializing(DataSource s,DataSpec d,boolean network){check(network,"Media3 recognizes this as a network source");}public void onTransferStart(DataSource s,DataSpec d,boolean network){starts.incrementAndGet();}public void onBytesTransferred(DataSource s,DataSpec d,boolean network,int n){transferred.addAndGet(n);}public void onTransferEnd(DataSource s,DataSpec d,boolean network){ends.incrementAndGet();}});
  try{
   source.open(spec(0,C.LENGTH_UNSET));boolean failed=false;try{source.read(new byte[16],0,16);}catch(IOException expected){failed=true;}check(failed,"temporary CDN error reaches Media3 retry layer");source.close();
   check(closed.get()==0,"Media3 transfer close retains Steam session for retry");
   source.open(spec(0,C.LENGTH_UNSET));DefaultExtractorInput input=new DefaultExtractorInput(source,0,reader.size());check(new Mp4Extractor().sniff(input),"actual Media3 MP4 extractor recognizes verified real Steam video bytes");source.close();
   byte[] real=Files.readAllBytes(video),buffer=new byte[4096];Random random=new Random(33);
   for(int i=0;i<20;i++){int at=i==0?real.length-1536:random.nextInt(real.length-4096);int wanted=Math.min(buffer.length,real.length-at);check(source.open(spec(at,wanted))==wanted,"range reopen has accurate byte count");int n=source.read(buffer,0,wanted);check(n==wanted&&Arrays.equals(Arrays.copyOf(buffer,n),Arrays.copyOfRange(real,at,at+n)),"real Steam range bytes verified after seek "+i);check(source.read(buffer,0,1)==C.RESULT_END_OF_INPUT,"finite range EOF");source.close();}
   source.open(spec(real.length,C.LENGTH_UNSET));check(source.read(buffer,0,1)==C.RESULT_END_OF_INPUT,"file-end seek returns EOF");source.close();source.close();check(starts.get()==ends.get(),"transfer events balanced across retries, seeks and repeated close");check(transferred.get()>0&&reader.cachedBytes()<=WorkshopRangeReader.CACHE_BYTES,"network byte events and memory limit enforced");check(loads.get()<20,"verified chunk cache reused across seek reopens");
  }finally{source.close();reader.close();}
  check(closed.get()==1,"final owner closes Steam reader exactly once");System.out.println("PASS: "+checks+" Media3 source/retry/seek checks against real Steam manifest + MP4; no Android decoder or live-network test");
 }
}
