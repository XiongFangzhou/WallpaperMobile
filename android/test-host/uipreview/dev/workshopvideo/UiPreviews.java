package dev.workshopvideo;

import android.app.*;
import android.content.Intent;
import android.graphics.*;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import in.dragonbra.javasteam.protobufs.steamclient.SteammessagesPublishedfileSteamclient.PublishedFileDetails;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowDialog;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;

/** Renders the real screens with sample data at 1080x2400 for visual review; not a phone test. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,application=VideoApp.class,qualifiers="w360dp-h800dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class UiPreviews {
    private static final int W=1080,H=2400;
    private static final String[] TITLES={"霓虹雨夜 · 赛博街景","Sakura Breeze 4K","深海鲸落","Mountain Lake at Dawn — Ultra Wide Edition","像素小镇的黄昏","星空列车","极光","Lo-fi Study Room"};
    private static final String[] TYPES={"Video","Video","Scene","Video","Scene","Video","Video","Scene"};
    private static final int[][] COLORS={{0xff3b1f6e,0xff0e7c9b},{0xffe58fb1,0xff6e3b74},{0xff07314f,0xff0a8f9e},{0xffd88b4a,0xff2f5d8a},{0xfff2a65a,0xff5b3f7d},{0xff101a3a,0xff5f6cc9},{0xff0b3d2e,0xff58d68d},{0xff6b4f3a,0xffc79b6d}};

    private VideoApp app(){return (VideoApp)RuntimeEnvironment.getApplication();}
    private void idle(){for(int i=0;i<40;i++){Shadows.shadowOf(Looper.getMainLooper()).idle();try{Thread.sleep(25);}catch(InterruptedException e){return;}}}
    private static String url(int i){return "https://images.steamusercontent.com/ugc/preview-"+i+".jpg";}

    @Before public void seed()throws Exception{
        Field field=PreviewImages.class.getDeclaredField("cache");field.setAccessible(true);
        @SuppressWarnings("unchecked") android.util.LruCache<String,byte[]> cache=(android.util.LruCache<String,byte[]>)field.get(app().previews);
        for(int i=0;i<COLORS.length;i++){
            Bitmap art=Bitmap.createBitmap(640,400,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(art);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setShader(new LinearGradient(0,0,640,400,COLORS[i][0],COLORS[i][1],Shader.TileMode.CLAMP));c.drawRect(0,0,640,400,p);p.setShader(null);
            p.setColor(0x33ffffff);c.drawCircle(470,120,70+i*6,p);p.setColor(0x26000000);Path hill=new Path();hill.moveTo(0,400);hill.lineTo(0,300);hill.quadTo(200,200-i*8,380,290);hill.quadTo(520,350,640,260);hill.lineTo(640,400);hill.close();c.drawPath(hill,p);
            ByteArrayOutputStream out=new ByteArrayOutputStream();art.compress(Bitmap.CompressFormat.PNG,100,out);cache.put(url(i),out.toByteArray());
        }
        app().filesReady.get(10,java.util.concurrent.TimeUnit.SECONDS);
    }
    private void records(boolean withFavorites,boolean withSaved)throws Exception{
        JSONArray jobs=new JSONArray(),favorites=new JSONArray();
        if(withSaved)for(int i=0;i<6;i++){JSONObject j=new JSONObject();j.put("id",String.format(Locale.ROOT,"%032x",i+1));j.put("itemId",String.valueOf(3000000000L+i));j.put("title",TITLES[i]);j.put("previewUrl",url(i));j.put("state","saved");j.put("storage","private");j.put("kind",i==2||i==4?"scene":"video");j.put("size",(i+1)*48L*1024*1024);j.put("sha1","00");jobs.put(j);}
        if(withSaved){
            JSONObject a=new JSONObject();a.put("id",String.format(Locale.ROOT,"%032x",100));a.put("itemId","3100000000");a.put("title",TITLES[6]);a.put("previewUrl",url(6));a.put("state","downloading");a.put("message","正在下载 · 2.4 MB/s");a.put("done",156L*1024*1024);a.put("total",412L*1024*1024);jobs.put(a);
            JSONObject b=new JSONObject();b.put("id",String.format(Locale.ROOT,"%032x",101));b.put("itemId","3100000001");b.put("title",TITLES[7]);b.put("previewUrl",url(7));b.put("state","error");b.put("error","Steam 请求超时，请检查网络后重试。");b.put("done",12L*1024*1024);b.put("total",96L*1024*1024);jobs.put(b);
        }
        if(withFavorites)for(int i=0;i<5;i++){JSONObject f=new JSONObject();f.put("itemId",String.valueOf(3000000000L+i));f.put("title",TITLES[i]);f.put("previewUrl",url(i));f.put("type",TYPES[i]);f.put("video",TYPES[i].equals("Video"));favorites.put(f);}
        app().getSharedPreferences("video",0).edit().putString("jobs",jobs.toString()).putString("favorites",favorites.toString()).commit();
    }
    private static PublishedFileDetails item(int i){
        PublishedFileDetails.Builder b=PublishedFileDetails.newBuilder().setResult(1).setConsumerAppid(431960).setPublishedfileid(3000000000L+i).setCreator(76561198000000000L+i).setTitle(TITLES[i]).setPreviewUrl(url(i))
            .setFileSize((i*37+12)*1024L*1024).setTimeCreated(1760000000+i*86400).setTimeUpdated(1762000000).setShortDescription("动态壁纸示例描述。").setVoteData(PublishedFileDetails.VoteData.newBuilder().setScore(.9f-i*.08f).setVotesUp(12000-i*1400).setVotesDown(300+i*90)).setFileDescription("一段较长的作品介绍，用于检查详情页的排版与可读性。\n\n支持 4K 分辨率，循环无缝衔接。");
        b.addTags(PublishedFileDetails.Tag.newBuilder().setTag(TYPES[i]));b.addTags(PublishedFileDetails.Tag.newBuilder().setTag("Anime"));b.addTags(PublishedFileDetails.Tag.newBuilder().setTag("3840 x 2160"));return b.build();
    }

    private static File out(String name){File dir=new File(System.getProperty("preview.work"));dir.mkdirs();return new File(dir,name+".png");}
    private static void write(Bitmap image,String name)throws IOException{try(FileOutputStream o=new FileOutputStream(out(name))){image.compress(Bitmap.CompressFormat.PNG,100,o);}image.recycle();}
    private static Bitmap draw(Activity activity){
        View decor=activity.getWindow().getDecorView();decor.measure(View.MeasureSpec.makeMeasureSpec(W,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(H,View.MeasureSpec.EXACTLY));decor.layout(0,0,W,H);
        Bitmap image=Bitmap.createBitmap(W,H,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(image);c.drawColor(Ui.BG);decor.draw(c);return image;
    }
    private void shot(Activity activity,String name)throws IOException{idle();write(draw(activity),name);}
    /** Composite the latest dialog over its activity the way a bottom sheet appears on the phone. */
    private void sheet(Activity activity,String name)throws IOException{
        idle();Bitmap image=draw(activity);Canvas c=new Canvas(image);c.drawColor(0x99000000);
        Dialog dialog=ShadowDialog.getLatestDialog();View decor=dialog.getWindow().getDecorView();WindowManager.LayoutParams lp=dialog.getWindow().getAttributes();
        int height=lp.height>0?lp.height:H;decor.measure(View.MeasureSpec.makeMeasureSpec(W,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,lp.height>0?View.MeasureSpec.EXACTLY:View.MeasureSpec.AT_MOST));decor.layout(0,0,W,decor.getMeasuredHeight());
        boolean bottom=(lp.gravity&Gravity.VERTICAL_GRAVITY_MASK)==Gravity.BOTTOM;c.save();c.translate(0,bottom?H-decor.getMeasuredHeight():(H-decor.getMeasuredHeight())/2f);decor.draw(c);c.restore();write(image,name);
    }
    private static Object call(Object target,String method,Class<?>[] types,Object... args)throws Exception{Method m=target.getClass().getDeclaredMethod(method,types);m.setAccessible(true);return m.invoke(target,args);}
    private static void set(Object target,String name,Object value)throws Exception{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target,value);}
    private static Object get(Object target,String name)throws Exception{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}

    @Test public void discover()throws Exception{
        records(true,false);
        try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()){
            MainActivity a=controller.get();shot(a,"01-discover-signed-out");
            @SuppressWarnings("unchecked") ArrayList<PublishedFileDetails> items=(ArrayList<PublishedFileDetails>)get(a,"items");for(int i=0;i<TITLES.length;i++)items.add(item(i));
            set(a,"hasResults",true);set(a,"total",18432L);call(a,"renderResults",new Class<?>[0]);shot(a,"02-discover-results");
            call(a,"detail",new Class<?>[]{PublishedFileDetails.class},item(0));sheet(a,"03-detail-video");ShadowDialog.getLatestDialog().dismiss();
            call(a,"detail",new Class<?>[]{PublishedFileDetails.class},item(2));sheet(a,"04-detail-scene");ShadowDialog.getLatestDialog().dismiss();
            call(a,"accountMenu",new Class<?>[0]);sheet(a,"05-account-menu");ShadowDialog.getLatestDialog().dismiss();
            call(a,"loginDialog",new Class<?>[0]);sheet(a,"06-login");ShadowDialog.getLatestDialog().dismiss();
            call(a,"filterDialog",new Class<?>[0]);sheet(a,"07-filters");ShadowDialog.getLatestDialog().dismiss();
            call(a,"jumpToPage",new Class<?>[0]);sheet(a,"08-jump-page");ShadowDialog.getLatestDialog().dismiss();
            call(a,"aboutDialog",new Class<?>[0]);sheet(a,"09-about");ShadowDialog.getLatestDialog().dismiss();
            GeneralSettings.show(a);sheet(a,"10-general-settings");ShadowDialog.getLatestDialog().dismiss();
        }
    }
    @Test public void library()throws Exception{
        records(true,true);
        try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class,new Intent().putExtra("tab",2)).setup()){
            MainActivity a=controller.get();shot(a,"11-saved-videos");
            Object library=get(a,"library");call(library,"showCategory",new Class<?>[]{boolean.class},true);shot(a,"12-saved-scenes");
            call(library,"showCategory",new Class<?>[]{boolean.class},false);set(library,"managing",true);@SuppressWarnings("unchecked") Set<String> selected=(Set<String>)get(library,"selected");selected.add(String.format(Locale.ROOT,"%032x",1));selected.add(String.format(Locale.ROOT,"%032x",4));
            call(library,"refresh",new Class<?>[0]);((BaseAdapter)get(library,"adapter")).notifyDataSetChanged();shot(a,"13-saved-manage");
            set(library,"managing",false);selected.clear();call(library,"refresh",new Class<?>[0]);
            call(library,"videoMenu",new Class<?>[]{JSONObject.class},app().job(String.format(Locale.ROOT,"%032x",1)));sheet(a,"14-saved-menu");ShadowDialog.getLatestDialog().dismiss();
            call(a,"showTab",new Class<?>[]{int.class},1);shot(a,"15-favorites");
        }
    }
    @Test public void empty()throws Exception{
        records(false,false);
        try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class,new Intent().putExtra("tab",2)).setup()){
            MainActivity a=controller.get();shot(a,"16-saved-empty");call(a,"showTab",new Class<?>[]{int.class},1);shot(a,"17-favorites-empty");
        }
    }
    @Test public void effects()throws Exception{
        try(ActivityController<Activity> controller=Robolectric.buildActivity(Activity.class).setup()){
            Activity a=controller.get();ScrollView scroll=new ScrollView(a);scroll.setBackgroundColor(Ui.BG);EffectPanel panel=new EffectPanel(a,null,true,"video",ignored->{});panel.setPadding(48,48,48,48);scroll.addView(panel);a.setContentView(scroll);
            idle();panel.measure(View.MeasureSpec.makeMeasureSpec(W,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));panel.layout(0,0,W,panel.getMeasuredHeight());
            Bitmap image=Bitmap.createBitmap(W,Math.min(6000,panel.getMeasuredHeight()),Bitmap.Config.ARGB_8888);Canvas c=new Canvas(image);c.drawColor(Ui.BG);panel.draw(c);write(image,"18-video-effects");
            EffectPanel scene=new EffectPanel(a,null,true,"scene",ignored->{});scene.sceneProperties(new JSONObject("{\"beijing\":{\"type\":\"combo\",\"value\":\"0\",\"label\":\"背景样式\",\"order\":1,\"options\":[[\"背景\",\"0\"],[\"纯色\",\"1\"]]},\"fg\":{\"type\":\"bool\",\"value\":false,\"label\":\"镜头前景\",\"order\":2},\"trail\":{\"type\":\"bool\",\"value\":true,\"label\":\"鼠标尾迹\",\"order\":3}}"));scene.setPadding(48,48,48,48);scene.measure(View.MeasureSpec.makeMeasureSpec(W,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));scene.layout(0,0,W,scene.getMeasuredHeight());
            image=Bitmap.createBitmap(W,Math.min(8000,scene.getMeasuredHeight()),Bitmap.Config.ARGB_8888);c=new Canvas(image);c.drawColor(Ui.BG);scene.draw(c);write(image,"19-scene-effects");
        }
    }
}
