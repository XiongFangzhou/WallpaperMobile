package dev.workshopvideo.core;
import java.util.*;
import java.util.function.Function;

/** Favorites use workshop IDs only; local transfer IDs never participate. */
public final class FavoriteIndex {
    public static <T> boolean contains(List<T> entries,String id,Function<T,String> key){return entries.stream().anyMatch(item->id.equals(key.apply(item)));}
    public static <T> List<T> toggle(List<T> entries,T item,Function<T,String> key){
        String id=key.apply(item);if(id==null||!id.matches("[0-9]{1,20}"))throw new IllegalArgumentException("作品 ID 无效。");
        try{if(Long.parseUnsignedLong(id)==0)throw new IllegalArgumentException("作品 ID 无效。");}catch(NumberFormatException e){throw new IllegalArgumentException("作品 ID 无效。",e);}
        List<T> next=new ArrayList<>();boolean found=false;for(T entry:entries){if(id.equals(key.apply(entry)))found=true;else next.add(entry);}if(!found)next.add(item);return next;
    }
}
