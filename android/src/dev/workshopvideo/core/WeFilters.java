package dev.workshopvideo.core;
import java.util.*;

/** PC taxonomy: ref screenshots, WE frontend query construction and zh-chs locale. */
public final class WeFilters {
    public record Option(String value,String label){}
    public record Group(String id,String title,List<Option> options){}
    private static List<Option> options(String... pairs){List<Option> values=new ArrayList<>();for(int i=0;i<pairs.length;i+=2)values.add(new Option(pairs[i],pairs[i+1]));return List.copyOf(values);}
    public static final Group TYPES=new Group("types","类型",options("Scene","场景","Video","视频","Web","网页","Application","应用程序"));
    public static final Group CATEGORIES=new Group("categories","作品分类",options("Wallpaper","常规壁纸","Preset","预设"));
    public static final Group RATINGS=new Group("ratings","年龄分级",options("Everyone","大众级（G）","Questionable","家长指导级（PG-13）","Mature","限制级/成人级（R-18）"));
    public static final Group GENRES=new Group("genres","标签",options("Abstract","抽象","Animal","动物","Anime","动漫","Cartoon","卡通","CGI","CGI","Cyberpunk","网络朋克","Fantasy","幻想","Game","游戏","Girls","女性","Guys","男性","Landscape","风景","Medieval","中世纪","Memes","网红事物","MMD","MMD（Miku-Miku Dance）","Music","音乐","Nature","自然","Pixel art","像素艺术","Relaxing","放松","Retro","复古","Sci-fi","科幻","Sports","运动","Technology","科技","Television","电视节目","Vehicle","汽车","Unspecified","未指定样式"));
    public static final List<Group> RESOLUTIONS=List.of(
        new Group("wide","宽屏",options("Standard Definition","标清","1280 x 720","1280 x 720","1366 x 768","1366 x 768","1920 x 1080","1920 x 1080 · 全高清","2560 x 1440","2560 x 1440","3840 x 2160","3840 x 2160 · 4K")),
        new Group("ultrawide","超宽屏",options("Ultrawide Standard Definition","超宽（标准）","Ultrawide 2560 x 1080","2560 x 1080","Ultrawide 3440 x 1440","3440 x 1440")),
        new Group("dual","双显示器",options("Dual Standard Definition","双显示器（标准）","Dual 3840 x 1080","3840 x 1080","Dual 5120 x 1440","5120 x 1440","Dual 7680 x 2160","7680 x 2160")),
        new Group("triple","三显示器",options("Triple Standard Definition","三显示器（标准）","Triple 4096 x 768","4096 x 768","Triple 5760 x 1080","5760 x 1080","Triple 7680 x 1440","7680 x 1440","Triple 11520 x 2160","11520 x 2160")),
        new Group("portrait","纵向监视器／手机",options("Portrait Standard Definition","纵向（标准）","Portrait 720 x 1280","720 x 1280","Portrait 1080 x 1920","1080 x 1920","Portrait 1440 x 2560","1440 x 2560","Portrait 2160 x 3840","2160 x 3840")),
        new Group("other","其他分辨率",options("Other resolution","其他分辨率","Dynamic resolution","动态分辨率")));
    public static final Group UTILITIES=new Group("utilities","功能标签",options("Approved","广受好评","Audio responsive","音频响应","Customizable","可自定义的","Multi monitor optimized","多显示器优化"));
    public static List<Option> resolutionOptions(){List<Option> values=new ArrayList<>();for(Group group:RESOLUTIONS)values.addAll(group.options());return List.copyOf(values);}
    public static final Group ALL_RESOLUTIONS=new Group("resolutions","分辨率",resolutionOptions());
    public static final List<Group> CHECK_GROUPS=List.of(TYPES,CATEGORIES,RATINGS,ALL_RESOLUTIONS,GENRES);
    public static Set<String> keys(Group group){LinkedHashSet<String> keys=new LinkedHashSet<>();for(Option option:group.options())keys.add(option.value());return keys;}
    public static Set<String> allKeys(){Set<String> keys=new LinkedHashSet<>();for(Group group:CHECK_GROUPS)keys.addAll(keys(group));keys.addAll(keys(UTILITIES));return keys;}
    public static WorkshopQuery defaults(){WorkshopQuery query=new WorkshopQuery();query.pcFilters=true;query.excluded.addAll(List.of("Questionable","Mature","Unspecified"));return query;}
    public static boolean checked(WorkshopQuery query,String key){return !query.excluded.contains(key);}
    public static void check(WorkshopQuery query,String key,boolean checked){query.pcFilters=true;query.required.remove(key);if(checked)query.excluded.remove(key);else query.excluded.add(key);}
    public static Set<String> wireExcluded(WorkshopQuery query){
        Set<String> result=new LinkedHashSet<>(query.excluded);
        // The PC frontend clears a fully unchecked group. Genres keep Unspecified.
        for(Group group:CHECK_GROUPS){Set<String> all=keys(group);if(result.containsAll(all)){result.removeAll(all);if(group==GENRES)result.add("Unspecified");}}
        return result;
    }
    public static WorkshopQuery migrate(WorkshopQuery query){
        if(query.pcFilters)return query;
        normalize(query.required);normalize(query.excluded);
        if(query.required.remove("Portrait")){for(Option option:ALL_RESOLUTIONS.options())if(!option.value().startsWith("Portrait "))query.excluded.add(option.value());}
        query.excluded.remove("Portrait");
        // Old single-choice controls become checkbox selections in their PC group.
        for(Group group:CHECK_GROUPS){Set<String> all=keys(group),selected=new LinkedHashSet<>(query.required);selected.retainAll(all);if(!selected.isEmpty()){query.required.removeAll(all);query.excluded.removeAll(selected);for(String value:all)if(!selected.contains(value))query.excluded.add(value);}}
        query.pcFilters=true;return query;
    }
    private static void normalize(Set<String> tags){List<String> old=new ArrayList<>(tags);tags.clear();for(String value:old)tags.add(switch(value){case "Animals"->"Animal";case "Sci-Fi"->"Sci-fi";case "720 x 1280","1080 x 1920","1440 x 2560","2160 x 3840"->"Portrait "+value;default->value;});}
}
