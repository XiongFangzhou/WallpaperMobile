package dev.workshopvideo;

import dev.workshopvideo.core.*;
import in.dragonbra.javasteam.protobufs.steamclient.SteammessagesPublishedfileSteamclient.PublishedFileDetails;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Recent Workshop pages for instant paging, a next-page prefetch and a startup snapshot. */
final class DiscoverResults {
    record Page(String key,long total,List<PublishedFileDetails> items){}
    private static final int MAGIC=0x57525031;
    private final QueryCache<Page> cache=new QueryCache<>(16,5*60_000L,System::currentTimeMillis);
    private final Set<String> pending=ConcurrentHashMap.newKeySet();
    private final File snapshot;

    DiscoverResults(File cacheDir){snapshot=new File(cacheDir,"discover-snapshot.bin");}

    Page cached(WorkshopQuery query){return cache.get(query.cacheKey());}

    /** Network fetch on the caller's worker thread; the result is cached for back/forward paging. */
    Page fetch(SteamSession session,WorkshopQuery query)throws Exception {
        String key=query.cacheKey();Page hit=cache.get(key);if(hit!=null)return hit;
        // A pasted workshop link or item ID opens that item directly.
        long id=WorkshopQuery.itemId(query.text);
        if(id>0){Page page=new Page(key,1,List.of(session.details(id)));cache.put(key,page);return page;}
        var response=session.query(query);List<PublishedFileDetails> found=new ArrayList<>();
        for(var item:response.getPublishedfiledetailsList())if(item.getResult()==1&&item.getConsumerAppid()==WorkshopQuery.APP_ID&&query.acceptsTitle(item.getTitle()))found.add(item);
        Page page=new Page(key,Integer.toUnsignedLong(response.getTotal()),List.copyOf(found));cache.put(key,page);return page;
    }

    /** Queue the next page behind current work; a user request for it then finds it cached. */
    void prefetch(ExecutorService worker,VideoApp app,WorkshopQuery next){
        String key=next.cacheKey();if(cache.get(key)!=null||!pending.add(key))return;
        try{worker.submit(()->{try{SteamSession session=app.session;if(session!=null&&session.isAuthenticated())fetch(session,next);}catch(Exception ignored){}finally{pending.remove(key);}});}
        catch(RejectedExecutionException ignored){pending.remove(key);}
    }

    /** First page of the saved filters, shown immediately on the next launch while Steam reconnects. */
    void save(Page page){
        File stage=new File(snapshot.getPath()+".tmp");
        try(DataOutputStream out=new DataOutputStream(new BufferedOutputStream(new FileOutputStream(stage)))){
            out.writeInt(MAGIC);out.writeUTF(page.key());out.writeLong(page.total());out.writeInt(page.items().size());
            for(PublishedFileDetails item:page.items()){byte[] bytes=item.toByteArray();out.writeInt(bytes.length);out.write(bytes);}
        }catch(IOException failure){stage.delete();return;}
        if(!stage.renameTo(snapshot)){snapshot.delete();if(!stage.renameTo(snapshot))stage.delete();}
    }
    Page restore(WorkshopQuery query){
        if(!snapshot.isFile()||snapshot.length()>8L*1024*1024)return null;
        try(DataInputStream in=new DataInputStream(new BufferedInputStream(new FileInputStream(snapshot)))){
            if(in.readInt()!=MAGIC||!in.readUTF().equals(query.cacheKey()))return null;
            long total=in.readLong();int count=in.readInt();if(count<0||count>200)return null;
            List<PublishedFileDetails> items=new ArrayList<>(count);
            for(int i=0;i<count;i++){int length=in.readInt();if(length<0||length>512*1024)return null;byte[] bytes=new byte[length];in.readFully(bytes);items.add(PublishedFileDetails.parseFrom(bytes));}
            return new Page(query.cacheKey(),total,List.copyOf(items));
        }catch(Exception unreadable){snapshot.delete();return null;}
    }
}
