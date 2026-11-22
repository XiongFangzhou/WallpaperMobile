package dev.workshopvideo;

import android.app.*;import android.graphics.*;import android.hardware.SensorManager;import android.view.*;import android.widget.*;
import org.json.JSONObject;import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.*;import org.robolectric.shadows.ShadowDialog;
import java.io.*;import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35,manifest=Config.NONE,application=android.app.Application.class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
public class GeneralAndMotionChecks {
 private View find(View root,String title){if(root instanceof TextView t&&t.getText().toString().equals(title))return t;if(root instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){View v=find(group.getChildAt(i),title);if(v!=null)return v;}return null;}
 private void screenshot(View view,String name)throws Exception{view.measure(View.MeasureSpec.makeMeasureSpec(400,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));view.layout(0,0,400,view.getMeasuredHeight());Bitmap b=Bitmap.createBitmap(400,Math.min(2400,view.getHeight()),Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);c.drawColor(Ui.BG);view.draw(c);try(FileOutputStream out=new FileOutputStream(new File(System.getProperty("graphics.work"),name))){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();}
 @Test public void globalSettingsPersistAndDoNotBelongToWallpaperReset()throws Exception{
  try(var controller=Robolectric.buildActivity(Activity.class).setup()){
   Activity a=controller.get();AppSettings.prefs(a).edit().clear().commit();assertTrue(AppSettings.batteryStatic(a));assertTrue(AppSettings.gesturePaging(a));GeneralSettings.show(a);Dialog dialog=ShadowDialog.getLatestDialog();View decor=dialog.getWindow().getDecorView();((Switch)find(decor,"省电时保持静态画面")).setChecked(false);((Switch)find(decor,"桌面滑动兼容")).setChecked(false);assertFalse(AppSettings.gesturePaging(a));((Switch)find(decor,"桌面滑动兼容")).setChecked(true);assertFalse(AppSettings.batteryStatic(a));assertTrue(AppSettings.gesturePaging(a));EffectPanel video=new EffectPanel(a,null,true,"video",ignored->{});assertNull(find(video,"省电时保持静态画面"));find(video,"重置视频设置").performClick();assertFalse(AppSettings.batteryStatic(a));find(decor,"完成").performClick();GeneralSettings.show(a);assertFalse(((Switch)find(ShadowDialog.getLatestDialog().getWindow().getDecorView(),"省电时保持静态画面")).isChecked());ShadowDialog.getLatestDialog().dismiss();AppSettings.prefs(a).edit().clear().commit();
  }
 }
 @Test public void sceneLayoutKeepsOnlyRelevantLayerControlsAndUnifiedCards()throws Exception{
  try(var controller=Robolectric.buildActivity(Activity.class).setup()){
   EffectPanel panel=new EffectPanel(controller.get(),new JSONObject().put("gyro",true).put("pan",true),true,"scene",ignored->{});panel.sceneLayers(new float[]{.2f,.8f},new String[]{"背景图层","前景图层"},true);assertNotNull(find(panel,"背景图层"));assertNotNull(find(panel,"触摸透视"));assertNotNull(find(panel,"对比度"));assertNull(find(panel,"系统省电模式下暂停为静态画面"));assertNotNull(find(panel,"倾斜强度"));((Switch)find(panel,"随手机倾斜产生视差")).setChecked(false);assertNull(find(panel,"倾斜强度"));find(panel,"恢复默认效果").performClick();assertNotNull(find(panel,"背景图层"));assertNotNull(find(panel,"按住并拖动查看另一层图片"));screenshot(panel,"scene-settings-052.png");
   EffectPanel flat=new EffectPanel(controller.get(),null,false,"scene",ignored->{});flat.sceneLayers(new float[]{.2f},new String[]{"单层背景"},false);assertNull(find(flat,"触摸透视"));assertNull(find(flat,"分层视差深度"));assertNotNull(find(flat,"单层背景"));
  }
 }
 @Test public void realAndroidRotationMatricesKeepUprightPitchOffHorizontalAxis(){
  for(double upright:new double[]{0,Math.toRadians(80),Math.toRadians(89.9),Math.PI/2}){
   float[] base=new float[9],current=new float[9];SensorManager.getRotationMatrixFromVector(base,new float[]{(float)Math.sin(upright/2),0,0,(float)Math.cos(upright/2)});SensorManager.getRotationMatrixFromVector(current,new float[]{(float)Math.sin((upright+.15)/2),0,0,(float)Math.cos((upright+.15)/2)});dev.workshopvideo.core.RelativeTilt tilt=new dev.workshopvideo.core.RelativeTilt();tilt.matrix(base,0);float[] result=tilt.matrix(current,0);assertEquals(0,result[0],.0001);assertTrue(result[1]>.2f);
  }
 }
 @Test public void pageGestureDoesNotAlsoDragTheScene()throws Exception{
  WallpaperEffects effects=new WallpaperEffects();effects.options(new JSONObject().put("pan",true).put("touch",true));effects.touch(.8f,.5f,0);effects.touch(.2f,.5f,2);for(int i=0;i<20;i++){org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(16));assertEquals(0,effects.frame(false).x(),.00001);}assertTrue(effects.pointerDown());assertEquals(.2f,effects.pointerX(),0);
 }
}
