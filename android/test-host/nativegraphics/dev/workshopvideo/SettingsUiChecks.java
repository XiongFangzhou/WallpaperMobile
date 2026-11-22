package dev.workshopvideo;

import android.app.*;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowDialog;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,manifest=Config.NONE,application=android.app.Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class SettingsUiChecks {
    private View find(View root,java.util.function.Predicate<View> match){if(match.test(root))return root;if(root instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){View found=find(group.getChildAt(i),match);if(found!=null)return found;}return null;}
    private View tag(View root,String value){return find(root,v->value.equals(v.getTag()));}
    private View text(View root,String prefix){return find(root,v->v instanceof TextView&&((TextView)v).getText().toString().startsWith(prefix));}
    private void readableLabels(View root){if(root instanceof TextView text){String label=text.getText().toString();assertFalse(label,label.contains("Cover")||label.contains("Fill")||label.contains("Free")||label.contains("（")||label.matches(".*#[0-9A-Fa-f]{6}.*"));}if(root instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++)readableLabels(group.getChildAt(i));}
    private void screenshot(View view,String name)throws Exception{view.measure(View.MeasureSpec.makeMeasureSpec(400,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));view.layout(0,0,400,view.getMeasuredHeight());Bitmap image=Bitmap.createBitmap(400,Math.min(2200,view.getHeight()),Bitmap.Config.ARGB_8888);Canvas c=new Canvas(image);c.drawColor(Ui.BG);view.draw(c);try(FileOutputStream out=new FileOutputStream(new File(System.getProperty("graphics.work"),name))){image.compress(Bitmap.CompressFormat.PNG,100,out);}image.recycle();}

    @Test public void threeChoicesAreInlineSelectedAndPersistTheirBehavior()throws Exception{
        try(var controller=Robolectric.buildActivity(Activity.class).setup()){
            AtomicReference<JSONObject> changed=new AtomicReference<>();EffectPanel panel=new EffectPanel(controller.get(),null,true,"video",changed::set);controller.get().setContentView(panel);
            assertTrue(tag(panel,"choice-motionMode-disabled").isSelected());tag(panel,"choice-motionMode-scroll").performClick();assertTrue(changed.get().optBoolean("pan"));assertFalse(changed.get().optBoolean("gyro"));assertTrue(tag(panel,"choice-motionMode-scroll").isSelected());assertNotNull(tag(panel,"choice-pageMode-auto"));tag(panel,"choice-pageMode-manual").performClick();assertEquals("manual",changed.get().optString("pageMode"));assertNotNull(text(panel,"分段页数"));
            tag(panel,"choice-alignment-free").performClick();assertEquals("free",changed.get().optString("alignment"));assertNotNull(text(panel,"水平位置"));tag(panel,"choice-alignment-cover").performClick();assertNull(text(panel,"水平位置"));readableLabels(panel);screenshot(panel,"video-settings-051.png");
        }
    }
    @Test public void colorSheetShowsRealSwatchesAndCancelRestoresPreview()throws Exception{
        try(var controller=Robolectric.buildActivity(Activity.class).setup()){
            AtomicReference<JSONObject> changed=new AtomicReference<>();EffectPanel panel=new EffectPanel(controller.get(),null,true,"video",changed::set);controller.get().setContentView(panel);tag(panel,"background-color").performClick();Dialog dialog=ShadowDialog.getLatestDialog();View decor=dialog.getWindow().getDecorView();assertTrue(dialog.isShowing());assertNull(find(decor,v->v instanceof EditText));tag(decor,"preset-7").performClick();assertEquals("#8EBDFF",changed.get().optString("backgroundColor"));assertNotNull(text(panel,"晴蓝"));readableLabels(decor);text(decor,"取消").performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();assertEquals("#000000",changed.get().optString("backgroundColor"));
            tag(panel,"background-color").performClick();dialog=ShadowDialog.getLatestDialog();decor=dialog.getWindow().getDecorView();tag(decor,"preset-8").performClick();text(decor,"使用此颜色").performClick();assertEquals("#FFADC4",changed.get().optString("backgroundColor"));assertFalse(dialog.isShowing());
        }
    }
    private void touch(View view,float x,float y){for(int action:new int[]{MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP}){MotionEvent event=MotionEvent.obtain(0,0,action,x,y,0);view.dispatchTouchEvent(event);event.recycle();}}
    @Test public void visualSpectrumGesturesProduceTheDisplayedColor()throws Exception{
        try(var controller=Robolectric.buildActivity(Activity.class).setup()){
            java.util.concurrent.atomic.AtomicInteger chosen=new java.util.concurrent.atomic.AtomicInteger();WallpaperColorPicker picker=new WallpaperColorPicker(controller.get(),Color.WHITE,chosen::set);picker.measure(View.MeasureSpec.makeMeasureSpec(400,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));picker.layout(0,0,400,picker.getMeasuredHeight());View hue=find(picker,v->"色相".contentEquals(v.getContentDescription()==null?"":v.getContentDescription()));View spectrum=find(picker,v->"饱和度与明暗".contentEquals(v.getContentDescription()==null?"":v.getContentDescription()));touch(hue,hue.getWidth()/2f,hue.getHeight()/2f);touch(spectrum,spectrum.getWidth()-5,5);assertTrue(Color.red(chosen.get())<10);assertTrue(Color.green(chosen.get())>240);assertTrue(Color.blue(chosen.get())>240);
            Bitmap pixels=Bitmap.createBitmap(spectrum.getWidth(),spectrum.getHeight(),Bitmap.Config.ARGB_8888);spectrum.draw(new Canvas(pixels));assertTrue(Color.red(pixels.getPixel(15,15))>210);assertTrue(Color.red(pixels.getPixel(pixels.getWidth()-20,15))<40);assertTrue(Color.green(pixels.getPixel(pixels.getWidth()-20,15))>220);assertTrue(Color.green(pixels.getPixel(180,pixels.getHeight()-12))<30);pixels.recycle();readableLabels(picker);screenshot(picker,"color-picker-051.png");
        }
    }
    @Test public void missingSensorKeepsTheChoiceVisibleButDisabled(){try(var controller=Robolectric.buildActivity(Activity.class).setup()){EffectPanel panel=new EffectPanel(controller.get(),null,false,"video",ignored->{});assertFalse(tag(panel,"choice-motionMode-device").isEnabled());assertTrue(tag(panel,"choice-motionMode-scroll").isEnabled());}}
}
