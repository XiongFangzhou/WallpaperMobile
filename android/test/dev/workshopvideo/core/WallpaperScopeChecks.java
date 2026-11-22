package dev.workshopvideo.core;
import static dev.workshopvideo.core.CoreChecks.*;
final class WallpaperScopeChecks {
    static void run() throws Exception {
        check(WallpaperScopes.flags(0)==1&&WallpaperScopes.flags(1)==2&&WallpaperScopes.flags(2)==3,"UI scopes match Android public flags");
        rejects(()->WallpaperScopes.flags(-1),"invalid wallpaper scope rejected");
        check(WallpaperScopes.screen(1,true)==1,"home-only engine keeps home video behind keyguard");
        check(WallpaperScopes.screen(2,false)==2,"lock-only engine never switches to home video");
        check(WallpaperScopes.screen(3,true)==2,"shared locked engine selects lock video");
        check(WallpaperScopes.screen(3,false)==1,"shared unlocked engine selects home video");
        check(WallpaperScopes.screen(0,true)==2,"legacy shared engine uses keyguard scope");
        for(int installed=0;installed<=3;installed++)for(int target=1;target<=3;target++){
            boolean expected=(target==1&&(installed==1||installed==3))||(target==2&&(installed==2||installed==3))||(target==3&&installed==3);
            check(WallpaperScopes.canUpdate(target,installed)==expected,"local scope update requires existing OS selection "+target+"/"+installed);
        }
        check(!WallpaperScopes.canUpdate(0,3)&&!WallpaperScopes.canUpdate(4,3),"invalid update cannot bypass system selection");
        check(WallpaperScopes.confirmedTargets(3,1)==1,"partial system selection never claims lock enabled");
        check(WallpaperScopes.confirmedTargets(2,1)==0,"wrong system selection cannot confirm requested lock");
        check(!WallpaperScopes.activeKey(1).equals(WallpaperScopes.activeKey(2)),"active home and lock keys are independent");
    }
}
