package dev.workshopvideo.core;
import static dev.workshopvideo.core.CoreChecks.*;
final class BudgetChecks {
    static void run(){
        long gb=1024L*1024*1024,mp=1024L*1024;
        check(SceneBudget.forMemory(0,false)==SceneBudget.BASELINE,"unknown memory keeps the baseline");
        check(SceneBudget.forMemory(2*gb,false).pixels()<SceneBudget.BASELINE.pixels()&&SceneBudget.forMemory(8*gb,true).pixels()<SceneBudget.BASELINE.pixels(),"low-memory phones keep less detail");
        SceneBudget mid=SceneBudget.forMemory(6*gb,false),high=SceneBudget.forMemory(12*gb,false);
        check(mid.pixels()>16*mp&&high.pixels()>mid.pixels()&&SceneBudget.forMemory(24*gb,false).pixels()==40*mp,"detail grows with memory and is capped");
    }
}
