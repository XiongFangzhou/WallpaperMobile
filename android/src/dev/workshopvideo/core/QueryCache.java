package dev.workshopvideo.core;

import java.util.LinkedHashMap;
import java.util.function.LongSupplier;

/** Small least-recently-used cache whose entries expire after a fixed age. */
public final class QueryCache<V> {
    private record Entry<V>(V value,long stored){}
    private final int capacity;
    private final long maxAgeMs;
    private final LongSupplier clock;
    private final LinkedHashMap<String,Entry<V>> entries=new LinkedHashMap<>(16,.75f,true);

    public QueryCache(int capacity,long maxAgeMs,LongSupplier clock){
        if(capacity<1||maxAgeMs<1)throw new IllegalArgumentException("缓存参数无效。");
        this.capacity=capacity;this.maxAgeMs=maxAgeMs;this.clock=clock;
    }
    public synchronized V get(String key){
        Entry<V> entry=entries.get(key);
        if(entry==null)return null;
        if(clock.getAsLong()-entry.stored()>maxAgeMs){entries.remove(key);return null;}
        return entry.value();
    }
    public synchronized void put(String key,V value){
        entries.put(key,new Entry<>(value,clock.getAsLong()));
        while(entries.size()>capacity)entries.remove(entries.keySet().iterator().next());
    }
    public synchronized int size(){return entries.size();}
    public synchronized void clear(){entries.clear();}
}
