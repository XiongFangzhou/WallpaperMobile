package dev.workshopvideo.core;

import in.dragonbra.javasteam.protobufs.steamclient.SteammessagesPublishedfileSteamclient.CPublishedFile_QueryFiles_Request;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Properties;
import java.io.StringReader;
import java.io.StringWriter;

/** WE-style search semantics, independent of the Android UI. */
public final class WorkshopQuery {
    public static final int APP_ID = 431960;
    public static final int PAGE_SIZE = 30;
    public String text = "";
    public boolean exact, titleOnly, pcFilters;
    public int sort = 3, days = 365, page = 1;
    public String from = "", to = "";
    public final Set<String> required = new LinkedHashSet<>();
    public final Set<String> excluded = new LinkedHashSet<>();

    public WorkshopQuery() { setType("Video"); }
    private static final List<String> TYPES=List.of("Scene","Video","Web","Application");
    public void setType(String selected) {
        if(!selected.isEmpty()&&!TYPES.contains(selected))throw new IllegalArgumentException("作品类型无效。");
        required.removeAll(TYPES);excluded.removeAll(TYPES);
        // WE's frontend excludes unchecked types instead of requiring a tag.
        if(!selected.isEmpty())for(String type:TYPES)if(!type.equals(selected))excluded.add(type);
    }
    public String type() {
        for(String type:TYPES)if(!excluded.contains(type)&&TYPES.stream().allMatch(t->t.equals(type)||excluded.contains(t)))return type;
        return "";
    }

    public CPublishedFile_QueryFiles_Request request() {
        if (page < 1 || days < 1 || days > 365 || text.length() > 200)
            throw new IllegalArgumentException("搜索条件超出范围。");
        if (sort != 0 && sort != 1 && sort != 3 && sort != 10 && sort != 11 && sort != 12)
            throw new IllegalArgumentException("排序方式无效。");
        Set<String> intersection = new LinkedHashSet<>(required);
        intersection.retainAll(excluded);
        if (!intersection.isEmpty()) throw new IllegalArgumentException("同一标签不能同时包含和排除：" + intersection);
        if (required.size() + excluded.size() > 100)
            throw new IllegalArgumentException("标签数量过多。");
        String search = text.trim();
        if (exact && !search.isEmpty() && !search.contains("\"")) search = "\"" + search + "\"";
        // ISteamUGC's enum and PublishedFile.QueryFiles' enum diverge after 8.
        // Keep UI sort semantics stable and explicitly translate the wire value.
        int backendSort=switch(sort) {case 10 -> 11; case 11 -> 12; case 12 -> 9; default -> sort;};
        CPublishedFile_QueryFiles_Request.Builder b = CPublishedFile_QueryFiles_Request.newBuilder()
            .setAppid(APP_ID).setCreatorAppid(APP_ID).setQueryType(backendSort).setPage(page)
            .setNumperpage(PAGE_SIZE).setFiletype(0).setLanguage(6).setDays(days)
            .setMatchAllTags(true).addAllRequiredtags(required).addAllExcludedtags(pcFilters?WeFilters.wireExcluded(this):excluded)
            .setReturnDetails(true).setReturnTags(true).setReturnVoteData(true)
            .setReturnShortDescription(true).setReturnPreviews(true).setStripDescriptionBbcode(true);
        if (!search.isEmpty()) b.setSearchText(search);
        if (!from.isEmpty() || !to.isEmpty()) {
            long start = from.isEmpty() ? 0 : day(from);
            long end = to.isEmpty() ? System.currentTimeMillis() / 1000 : day(to) + 86399;
            if (start > end || start < 0 || end > 0xffffffffL)
                throw new IllegalArgumentException("发布日期范围无效。");
            b.setDateRangeCreated(CPublishedFile_QueryFiles_Request.DateRange.newBuilder()
                .setTimestampStart((int)start).setTimestampEnd((int)end));
        }
        return b.build();
    }

    private static long day(String date) {
        try { return LocalDate.parse(date).atStartOfDay(ZoneId.of("Asia/Shanghai")).toEpochSecond(); }
        catch (RuntimeException e) { throw new IllegalArgumentException("日期格式应为 YYYY-MM-DD。"); }
    }

    public boolean acceptsTitle(String title) {
        // WE's native filterTitle mapping has not been established. Make the
        // title-only constraint explicit and deterministic on the returned page.
        return !titleOnly || text.trim().isEmpty() || title.toLowerCase(java.util.Locale.ROOT)
            .contains(text.trim().replace("\"", "").toLowerCase(java.util.Locale.ROOT));
    }

    public WorkshopQuery copy() {
        WorkshopQuery q = new WorkshopQuery();
        q.pcFilters=pcFilters; q.text=text; q.exact=exact; q.titleOnly=titleOnly; q.sort=sort; q.days=days;
        q.page=page; q.from=from; q.to=to;
        q.required.clear(); q.required.addAll(required); q.excluded.clear(); q.excluded.addAll(excluded);
        return q;
    }

    /** Save applied settings rather than the current results page. */
    public String settings() {
        request();Properties values=new Properties();
        values.setProperty("version","2");values.setProperty("pcFilters",Boolean.toString(pcFilters));values.setProperty("text",text);
        values.setProperty("sort",Integer.toString(sort));values.setProperty("days",Integer.toString(days));
        values.setProperty("exact",Boolean.toString(exact));values.setProperty("titleOnly",Boolean.toString(titleOnly));
        values.setProperty("from",from);values.setProperty("to",to);
        values.setProperty("required.count",Integer.toString(required.size()));values.setProperty("excluded.count",Integer.toString(excluded.size()));
        int index=0;for(String tag:required)values.setProperty("required."+(index++),tag);
        index=0;for(String tag:excluded)values.setProperty("excluded."+(index++),tag);
        try {StringWriter output=new StringWriter();values.store(output,null);return output.toString();}
        catch(java.io.IOException e){throw new IllegalStateException(e);}
    }
    public static WorkshopQuery fromSettings(String saved) {
        if(saved==null||saved.isEmpty())return new WorkshopQuery();
        try {
            if(saved.length()>65536)throw new IllegalArgumentException("筛选记录过大。");
            Properties values=new Properties();values.load(new StringReader(saved));
            if(!List.of("1","2").contains(values.getProperty("version","")))throw new IllegalArgumentException("筛选版本无效。");
            WorkshopQuery query=new WorkshopQuery();query.pcFilters=Boolean.parseBoolean(values.getProperty("pcFilters","false"));query.text=values.getProperty("text","");
            query.sort=Integer.parseInt(values.getProperty("sort","3"));query.days=Integer.parseInt(values.getProperty("days","365"));
            query.exact=Boolean.parseBoolean(values.getProperty("exact","false"));query.titleOnly=Boolean.parseBoolean(values.getProperty("titleOnly","false"));
            query.from=values.getProperty("from","");query.to=values.getProperty("to","");
            query.required.clear();query.excluded.clear();
            readTags(values,"required",query.required);readTags(values,"excluded",query.excluded);
            query.request();return query;
        }catch(Exception e){return new WorkshopQuery();}
    }
    private static void readTags(Properties values,String key,Set<String> tags) {
        int count=Integer.parseInt(values.getProperty(key+".count","0"));if(count<0||count>100)throw new IllegalArgumentException("标签数量无效。");
        for(int i=0;i<count;i++) {
            String value=values.getProperty(key+"."+i);
            if(value==null||value.isBlank()||value.length()>200)throw new IllegalArgumentException("标签无效。");
            tags.add(value);
        }
    }
}
