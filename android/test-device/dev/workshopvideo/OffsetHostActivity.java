package dev.workshopvideo;
import android.app.*;import android.os.*;import android.graphics.*;import android.graphics.drawable.*;import android.content.*;import android.view.*;import android.widget.*;
/** A genuine translucent wallpaper target, separate from the production UI. Emulator only. */
public final class OffsetHostActivity extends Activity {
 public void onCreate(Bundle state){super.onCreate(state);if(!Build.HARDWARE.equals("ranchu")&&!Build.HARDWARE.equals("goldfish")){finish();return;}getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));FrameLayout root=new FrameLayout(this);root.setBackgroundColor(Color.TRANSPARENT);setContentView(root);}
 public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(focused)offset();}
 protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);offset();}
 private void offset(){new Handler(Looper.getMainLooper()).postDelayed(()->{int pages=getIntent().getIntExtra("pages",3),page=getIntent().getIntExtra("page",0);WallpaperManager manager=WallpaperManager.getInstance(this);manager.setWallpaperOffsetSteps(1f/(pages-1),1);manager.setWallpaperOffsets(getWindow().getDecorView().getWindowToken(),page/(float)(pages-1),0);},50);}
}
