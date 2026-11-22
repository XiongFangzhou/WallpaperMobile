package dev.workshopvideo.core;
import java.util.*;
import static dev.workshopvideo.core.CoreChecks.*;

/** Expected wire conditions from the supplied PC screenshots and native frontend loop. */
final class WeFilterChecks {
    static Set<String> excluded(WorkshopQuery query){return new LinkedHashSet<>(query.request().getExcludedtagsList());}
    static void select(WorkshopQuery query,WeFilters.Group group,Set<String> selected){for(WeFilters.Option option:group.options())WeFilters.check(query,option.value(),selected.contains(option.value()));}
    static void run() throws Exception {
        check(WeFilters.GENRES.options().size()==25,"25 genre checkboxes in ref");
        check(WeFilters.ALL_RESOLUTIONS.options().size()==25,"25 resolutions across six PC groups");
        check(WeFilters.RESOLUTIONS.stream().map(g->g.options().size()).toList().equals(List.of(6,3,4,5,5,2)),"PC widescreen/ultrawide/dual/triple/portrait/other groups");
        check(WeFilters.keys(WeFilters.GENRES).containsAll(List.of("Animal","Sci-fi","Girls","Guys","Pixel art","Television")),"exact Steam genre spelling");
        check(!WeFilters.allKeys().contains("Animals")&&!WeFilters.allKeys().contains("Sci-Fi"),"remove invented aliases");
        WorkshopQuery query=WeFilters.defaults();for(WeFilters.Group group:WeFilters.CHECK_GROUPS)select(query,group,WeFilters.keys(group));
        check(excluded(query).isEmpty(),"all groups checked produces no exclusion");
        select(query,WeFilters.TYPES,Set.of("Video"));select(query,WeFilters.RATINGS,Set.of("Everyone","Questionable"));
        check(excluded(query).equals(Set.of("Scene","Web","Application","Mature")),"exact ref screenshot video and G/PG13 selection");
        select(query,WeFilters.CATEGORIES,Set.of("Wallpaper"));check(excluded(query).contains("Preset")&&!query.request().getRequiredtagsList().contains("Wallpaper"),"PC regular wallpaper excludes preset instead of requiring tag");
        select(query,WeFilters.ALL_RESOLUTIONS,Set.of("Portrait 1080 x 1920","Portrait 1440 x 2560"));
        check(!excluded(query).contains("Portrait 1080 x 1920")&&!excluded(query).contains("Portrait 1440 x 2560")&&excluded(query).contains("1920 x 1080"),"portrait selection uses prefixed resolution keys");
        check(query.request().getRequiredtagsCount()==0,"multiple checked resolutions are not an impossible AND");
        select(query,WeFilters.ALL_RESOLUTIONS,Set.of());check(Collections.disjoint(excluded(query),WeFilters.keys(WeFilters.ALL_RESOLUTIONS)),"PC normalizes fully unchecked resolution group");
        select(query,WeFilters.GENRES,Set.of("Nature","Landscape"));
        check(excluded(query).contains("Animal")&&!excluded(query).contains("Nature")&&!excluded(query).contains("Landscape"),"genre multi-select excludes unchecked genres");
        select(query,WeFilters.GENRES,Set.of());Set<String> genreExcluded=new LinkedHashSet<>(excluded(query));genreExcluded.retainAll(WeFilters.keys(WeFilters.GENRES));check(genreExcluded.equals(Set.of("Unspecified")),"PC all-unchecked genre fallback retains Unspecified exclusion");
        select(query,WeFilters.TYPES,Set.of());check(Collections.disjoint(excluded(query),WeFilters.keys(WeFilters.TYPES)),"PC clears fully unchecked type exclusions");
        select(query,WeFilters.RATINGS,Set.of());check(Collections.disjoint(excluded(query),WeFilters.keys(WeFilters.RATINGS)),"PC clears fully unchecked rating exclusions");
        query.required.add("Audio responsive");query.excluded.add("Customizable");check(query.request().getRequiredtagsList().contains("Audio responsive")&&excluded(query).contains("Customizable"),"utilities retain three-state include/exclude semantics");
        WorkshopQuery saved=WorkshopQuery.fromSettings(query.settings());check(saved.pcFilters&&saved.required.equals(query.required)&&saved.excluded.equals(query.excluded),"persist raw checkbox state including all-unchecked groups");
        check(excluded(saved).equals(excluded(query)),"saved query has same normalized PC wire exclusions");
        WorkshopQuery copy=query.copy();check(copy.pcFilters&&excluded(copy).equals(excluded(query)),"snapshot retains PC semantics");
        WorkshopQuery legacy=new WorkshopQuery();legacy.required.add("Animals");legacy.required.add("1080 x 1920");legacy.excluded.add("Sci-Fi");WeFilters.migrate(legacy);
        check(legacy.pcFilters&&!legacy.required.contains("Animals")&&!legacy.excluded.contains("Sci-Fi"),"migrate old invalid tag names");
        check(!legacy.excluded.contains("Animal")&&legacy.excluded.contains("Anime")&&!legacy.excluded.contains("Portrait 1080 x 1920"),"migrate old single-select genre and portrait resolution into checkboxes");
        check(legacy.required.isEmpty(),"old grouped required filters no longer hidden positive constraints");
        WorkshopQuery defaults=WeFilters.defaults();check(excluded(defaults).containsAll(List.of("Questionable","Mature","Unspecified")),"PC default G rating and specified genres");
    }
}
