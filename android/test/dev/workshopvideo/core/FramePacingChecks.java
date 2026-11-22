package dev.workshopvideo.core;
import static dev.workshopvideo.core.CoreChecks.*;

final class FramePacingChecks {
    private static int count(int displayHz,int fps){FramePacing clock=new FramePacing();int draws=0;for(int i=0;i<displayHz;i++)if(clock.render(Math.round(i*1_000_000_000d/displayHz),fps))draws++;return draws;}
    static void run(){
        check(count(120,120)==120,"120-Hz display can draw all 120 vsync frames without fixed16ms ceiling");
        check(count(120,60)==60,"explicit60 cap keeps alternate display vsyncs");
        check(count(120,90)==90,"90fps budget on120Hz preserves fractional phase rather than falling to60");
        check(count(90,120)==90&&count(60,120)==60,"renderer never invents callbacks beyond the display refresh rate");
        check(count(120,30)==30&&count(120,15)==15,"lower manual frame budgets remain effective");
        FramePacing clock=new FramePacing();check(clock.render(0,120),"first requested frame is available");check(!clock.render(0,120),"multiple requests at one vsync cannot duplicate render");check(clock.render(4_000_000_000L,120),"resume after a long gap does not replay a frame backlog");check(!clock.render(4_000_000_000L,120),"same timestamp still coalesces after resume");clock.reset();check(clock.render(4_000_000_000L,120),"explicit visibility reset permits a fresh frame");check(clock.render(4_000_000_001L,30),"changing cap takes effect without a stale deadline");
    }
}
