package dev.workshopvideo;

import android.app.*;import android.view.*;import android.widget.*;
import org.json.*;import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.*;import org.robolectric.shadows.ShadowDialog;
import java.time.Duration;import java.util.concurrent.atomic.*;import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35,manifest=Config.NONE,application=android.app.Application.class)
public class AccountAndFrameChecks {
    @Before public void pacedVsync(){org.robolectric.shadows.ShadowChoreographer.setPaused(true);org.robolectric.shadows.ShadowChoreographer.setFrameDelay(Duration.ofMillis(16));}
    private View text(View root,String title){if(root instanceof TextView t&&t.getText().toString().equals(title))return t;if(root instanceof ViewGroup g)for(int i=0;i<g.getChildCount();i++){View found=text(g.getChildAt(i),title);if(found!=null)return found;}return null;}
    private View decor(){return ShadowDialog.getLatestDialog().getWindow().getDecorView();}
    private void clickAction(String title){View view=text(decor(),title);while(view!=null&&!view.isClickable()&&view.getParent() instanceof View parent)view=parent;assertNotNull(view);view.performClick();}
    @Test public void accountExitNeedsExplicitConfirmationAndNeverStartsLogin(){
        try(var controller=Robolectric.buildActivity(Activity.class).setup()){
            Activity a=controller.get();AtomicInteger logins=new AtomicInteger(),exits=new AtomicInteger();Runnable show=()->AccountMenu.show(a,"Steam 账号","已登录",true,false,logins::incrementAndGet,exits::incrementAndGet,()->{});
            show.run();assertNull(text(decor(),"切换 Steam 账号"));assertNull(text(decor(),"登录 Steam"));clickAction("退出 Steam 登录");assertEquals(0,exits.get());text(decor(),"取消").performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();assertEquals(0,exits.get());
            show.run();clickAction("退出 Steam 登录");assertEquals(0,exits.get());text(decor(),"退出登录").performClick();assertEquals(1,exits.get());assertEquals(0,logins.get());
            AccountMenu.show(a,"WallpaperMobile","尚未连接",false,false,logins::incrementAndGet,exits::incrementAndGet,()->{});assertNull(text(decor(),"退出 Steam 登录"));clickAction("登录 Steam");assertEquals(1,logins.get());assertEquals(1,exits.get());
        }
    }
    @Test public void oldDefaultMigratesButNewManualCapSurvivesResetNormalization()throws Exception{
        assertEquals(120,EffectOptions.normalize(null).optInt("fps"));assertEquals(120,EffectOptions.normalize(new JSONObject().put("fps",30)).optInt("fps"));JSONObject manual=EffectOptions.normalize(new JSONObject().put("fps",30).put("frameRateVersion",2));assertEquals(30,manual.optInt("fps"));assertEquals(30,EffectOptions.normalize(manual).optInt("fps"));assertEquals(60,EffectOptions.normalize(new JSONObject().put("fps",60)).optInt("fps"));assertEquals(120,EffectOptions.normalize(new JSONObject().put("fps",200)).optInt("fps"));
    }
    @Test public void repeatedInputRequestsCoalesceAndCancelStopsVsync(){
        AtomicInteger renders=new AtomicInteger();WallpaperFrameClock clock=new WallpaperFrameClock(()->120,()->false,renders::incrementAndGet);for(int i=0;i<100;i++)clock.request();Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(18));assertEquals(1,renders.get());Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(18));assertEquals(1,renders.get());clock.request();clock.cancel();Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(18));assertEquals(1,renders.get());
    }
    @Test public void activeAnimationRequestsOnlyOneNextFrameAndPausesCleanly(){
        AtomicBoolean running=new AtomicBoolean(true);AtomicInteger renders=new AtomicInteger();WallpaperFrameClock clock=new WallpaperFrameClock(()->120,running::get,renders::incrementAndGet);clock.request();for(int i=0;i<3;i++)Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(18));assertTrue(renders.get()>=2);running.set(false);clock.cancel();int before=renders.get();Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(50));assertEquals(before,renders.get());clock.request();Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(18));assertEquals(before+1,renders.get());
    }
    @Test public void cancellationInsideRenderDoesNotScheduleAnotherFrame(){
        AtomicInteger renders=new AtomicInteger();WallpaperFrameClock[] clock=new WallpaperFrameClock[1];clock[0]=new WallpaperFrameClock(()->120,()->true,()->{renders.incrementAndGet();clock[0].cancel();});clock[0].request();for(int i=0;i<3;i++)Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(18));assertEquals(1,renders.get());
    }
}
