package dev.workshopvideo.core;

/** Server totals describe unfiltered pages; local title filtering must not change their count. */
public final class WorkshopPages {
    public static int count(long total){
        if(total<0)throw new IllegalArgumentException("作品总数无效。");
        return total==0?1:(int)Math.min(Integer.MAX_VALUE,(total-1)/WorkshopQuery.PAGE_SIZE+1);
    }
    public static int target(String input,long total){
        int pages=count(total);
        try{
            String text=input==null?"":input.trim();
            if(!text.matches("[0-9]+"))throw new NumberFormatException();
            long page=Long.parseLong(text);
            if(page<1||page>pages)throw new NumberFormatException();
            return (int)page;
        }catch(NumberFormatException error){throw new IllegalArgumentException("请输入 1–"+pages+" 范围内的整数页码。");}
    }
}
