package dev.workshopvideo.core;
import static dev.workshopvideo.core.CoreChecks.*;
final class PaginationChecks {
    static void run() throws Exception {
        check(WorkshopPages.count(0)==1,"empty results have a stable first page");
        check(WorkshopPages.count(1)==1,"one item has one page");
        check(WorkshopPages.count(30)==1,"exact full last page does not expose a phantom next page");
        check(WorkshopPages.count(31)==2,"partial second page is available");
        check(WorkshopPages.count(60)==2,"two full pages end at page two");
        check(WorkshopPages.count(61)==3,"remote totals control pages even when local title filtering removes every item");
        check(WorkshopPages.count(0xffffffffL)==143165577,"unsigned Steam totals keep page count positive");
        check(WorkshopPages.target(" 1001 ",30060)==1001,"jumping beyond the old arbitrary 1000-page cap");
        check(WorkshopPages.target("1",30060)==1,"jump to first page");
        check(WorkshopPages.target("1002",30060)==1002,"jump to last page");
        check(WorkshopPages.target("1",0)==1,"empty results allow their first page only");
        rejects(()->WorkshopPages.target("0",60),"zero page");
        rejects(()->WorkshopPages.target("-1",60),"negative page");
        rejects(()->WorkshopPages.target("3",60),"beyond last page");
        rejects(()->WorkshopPages.target("",60),"empty page input");
        rejects(()->WorkshopPages.target("1.5",60),"fractional page input");
        rejects(()->WorkshopPages.target("999999999999999999999999",60),"overflowing page input");
        WorkshopQuery query=new WorkshopQuery();query.page=1001;check(query.request().getPage()==1001,"arbitrary valid page is sent unchanged to Steam");
        WorkshopQuery copy=query.copy();copy.page=2;check(query.page==1001&&copy.page==2,"navigation snapshots do not mutate displayed query");
    }
}
