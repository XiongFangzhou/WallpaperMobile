package dev.workshopvideo.core;
import static dev.workshopvideo.core.CoreChecks.*;
final class QueryCacheChecks {
    static void run() throws Exception {
        long[] now={1000};
        QueryCache<String> cache=new QueryCache<>(2,500,()->now[0]);
        cache.put("a","first");cache.put("b","second");
        check("first".equals(cache.get("a")),"fresh entry is returned");
        cache.put("c","third");
        check(cache.get("b")==null&&"first".equals(cache.get("a"))&&cache.size()==2,"least recently used entry is evicted at capacity");
        now[0]+=501;
        check(cache.get("a")==null&&cache.size()==1,"expired entry is dropped on read");
        rejects(()->new QueryCache<String>(0,1,()->0),"zero capacity");

        WorkshopQuery one=new WorkshopQuery();one.text="海";one.page=2;
        WorkshopQuery same=one.copy();
        check(one.cacheKey().equals(same.cacheKey()),"equal queries share a cache key despite the settings timestamp");
        WorkshopQuery next=one.copy();next.page=3;
        check(!one.cacheKey().equals(next.cacheKey()),"page is part of the cache key");
        WorkshopQuery other=one.copy();other.text="山";
        check(!one.cacheKey().equals(other.cacheKey()),"search text is part of the cache key");
    }
}
