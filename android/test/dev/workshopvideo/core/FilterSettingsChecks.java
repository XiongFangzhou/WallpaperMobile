package dev.workshopvideo.core;

import java.util.List;

final class FilterSettingsChecks {
    static void run(){
        WorkshopQuery query=new WorkshopQuery();query.text="星空 wallpaper \"4K\"";query.exact=true;query.titleOnly=true;
        query.sort=12;query.days=30;query.from="2026-11-01";query.to="2026-11-21";query.page=8;
        query.required.addAll(List.of("Nature","1920 x 1080","Custom=Tag: 中文\nline"));
        query.excluded.addAll(List.of("Mature","Questionable","Anime"));
        String saved=query.settings();WorkshopQuery restored=WorkshopQuery.fromSettings(saved);
        CoreChecks.check(restored.sort==12&&restored.days==30,"saved sorting and trend period survive restart");
        CoreChecks.check(restored.exact&&restored.titleOnly&&restored.text.equals(query.text),"saved match switches and keyword survive restart");
        CoreChecks.check(restored.required.equals(query.required)&&restored.excluded.equals(query.excluded),"all applied tags and type exclusions survive restart");
        CoreChecks.check(restored.from.equals(query.from)&&restored.to.equals(query.to),"saved date range survives restart");
        CoreChecks.check(restored.page==1,"restored filters start on first results page");
        restored.page=8;CoreChecks.check(restored.request().equals(query.request()),"restored filters generate identical Steam request");
        restored.required.clear();CoreChecks.check(!WorkshopQuery.fromSettings(saved).required.isEmpty(),"editing restored filters does not mutate persisted snapshot");
        WorkshopQuery defaults=new WorkshopQuery();defaults.setType("");defaults.excluded.clear();
        CoreChecks.check(WorkshopQuery.fromSettings(defaults.settings()).excluded.isEmpty(),"empty all-type selection remains empty instead of resetting to video");
        CoreChecks.check(WorkshopQuery.fromSettings("").days==365,"first launch uses defaults");
        CoreChecks.check(WorkshopQuery.fromSettings("version=1\nsort=invalid").sort==3,"corrupt settings recover to valid defaults");
        CoreChecks.check(WorkshopQuery.fromSettings("version=1\nrequired.count=99999").type().equals("Video"),"unbounded stored tags rejected");
        CoreChecks.check(WorkshopQuery.fromSettings("version=1\nrequired.count=1\nrequired.0=Nature\nexcluded.count=1\nexcluded.0=Nature").required.isEmpty(),"contradictory saved filters recover safely");
        CoreChecks.check(WorkshopQuery.fromSettings("version=999").type().equals("Video"),"unknown stored schema uses defaults");
        WorkshopQuery allTypes=new WorkshopQuery();allTypes.setType("");
        CoreChecks.check(allTypes.copy().excluded.isEmpty(),"opening filters does not silently restore video-only exclusions");
        allTypes.setType("Scene");CoreChecks.check(allTypes.copy().type().equals("Scene")&&!allTypes.copy().excluded.contains("Scene"),"editing scene filters does not re-add default scene exclusion");
    }
}
