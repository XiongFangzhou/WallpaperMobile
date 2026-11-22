package dev.workshopvideo.core;
import java.util.List;

/** Reads 0.2.2 evidence without confusing its 700 ms reconciliation delay with picker time. */
public final class WallpaperPickerHistory {
    public record Event(long time,String stage,String file,String detail){}
    public static long immediateExit(List<Event> events,long now,boolean selected){
        if(selected)return -1;
        int opened=-1;
        for(int i=events.size()-1;i>=0;i--)if(events.get(i).stage().equals("picker_open")){opened=i;break;}
        if(opened<0)return -1;
        Event open=events.get(opened),returned=null;boolean reached=false;
        if(!open.detail().startsWith("beforeMask=0 ids="))return -1;
        String ids=open.detail().substring("beforeMask=0 ids=".length());
        if(ids.isEmpty()||ids.contains(" "))return -1; // Only the old 0.2.2 schema.
        for(int i=opened+1;i<events.size();i++){
            Event event=events.get(i);if(!event.file().equals(open.file()))continue;
            if(event.stage().equals("picker_return"))returned=event;
            else reached=true; // Any intervening or later evidence makes legacy inference unsafe.
        }
        if(returned==null||!returned.detail().equals("result=0 mask=0 ids="+ids+" decision=NOT_SELECTED"))return -1;
        long age=now-returned.time(),elapsed=returned.time()-open.time()-700;
        return age>=0&&age<=7L*24*60*60*1000&&WallpaperApplyDecision.entryRejected(false,false,false,reached,false,elapsed)?elapsed:-1;
    }
}
