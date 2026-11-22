package dev.workshopvideo;

import android.app.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadataRetriever;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.LruCache;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;
import java.io.*;
import java.lang.ref.WeakReference;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Saved posters remain in place while management toggles selection on cards. */
public final class SavedLibraryView extends LinearLayout {
    private static final int TEXT=0xffedf3f8,MUTED=0xff97a8ba,ACCENT=0xffa9efd5,PANEL=0xff151e2a;
    private final Activity activity;
    private final VideoApp app;
    private final Consumer<JSONObject> open;
    private final Consumer<String> resume;
    private final LinearLayout transferCards;
    private final ScrollView transfers;
    private final ArrayList<JSONObject> videos=new ArrayList<>();
    private final Set<String> selected=new HashSet<>(),loading=new HashSet<>();
    private final Map<String,List<WeakReference<ImageView>>> waiters=new HashMap<>();
    private final ExecutorService thumbnails=Executors.newSingleThreadExecutor();
    private final LruCache<String,Bitmap> cache=new LruCache<String,Bitmap>(12*1024*1024){@Override protected int sizeOf(String key,Bitmap value){return value.getByteCount();}};
    private final GridView grid;
    private final PosterAdapter adapter=new PosterAdapter();
    private final TextView summary,migration,empty;
    private final Button manage,selectAll,clean;
    private final LinearLayout selectionBar;
    private int category;private LinearLayout categories;
    private String fingerprint="";
    private long lastOpen;
    private boolean managing,cleaning;
    private volatile boolean closed;

    public SavedLibraryView(Activity activity,VideoApp app,Consumer<JSONObject> open,Consumer<String> resume){
        super(activity);this.activity=activity;this.app=app;this.open=open;this.resume=resume;setOrientation(VERTICAL);setPadding(dp(18),dp(16),dp(18),0);
        LinearLayout heading=new LinearLayout(activity);heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.addView(text("已保存",26,TEXT),new LinearLayout.LayoutParams(0,-2,1));manage=button("管理",false,()->{
            if(cleaning)return;if(!app.filesReady.isDone()){toast("正在整理本地视频，请稍候。");return;}managing=!managing;selected.clear();updateSelection();adapter.notifyDataSetChanged();
        });heading.addView(manage,new LinearLayout.LayoutParams(dp(72),dp(43)));addView(heading);
        category=activity.getSharedPreferences("library",0).getInt("category",0);categories=new LinearLayout(activity);categories.setPadding(0,dp(12),0,0);addView(categories);categoryButtons();
        summary=text("",11,MUTED);summary.setPadding(0,dp(8),0,dp(12));addView(summary);
        migration=text("",12,ACCENT);migration.setPadding(0,0,0,dp(12));migration.setOnClickListener(v->app.work.submit(app::migrateVideos));addView(migration);
        transfers=new ScrollView(activity);transferCards=new LinearLayout(activity);transferCards.setOrientation(VERTICAL);transfers.addView(transferCards);addView(transfers,new LinearLayout.LayoutParams(-1,0));
        grid=new GridView(activity);grid.setNumColumns(2);grid.setHorizontalSpacing(dp(10));grid.setVerticalSpacing(dp(12));grid.setSelector(android.R.color.transparent);grid.setClipToPadding(false);grid.setPadding(0,0,0,dp(14));grid.setAdapter(adapter);
        grid.setOnItemClickListener((parent,view,index,rowId)->{
            JSONObject video=videos.get(index);String id=video.optString("id");
            if(managing){if(cleaning)return;if(!selected.add(id))selected.remove(id);updateSelection();adapter.notifyDataSetChanged();return;}
            long now=SystemClock.uptimeMillis();if(now-lastOpen>=500){lastOpen=now;open.accept(video);}
        });grid.setOnItemLongClickListener((parent,view,index,rowId)->{if(!cleaning)videoMenu(videos.get(index));return true;});addView(grid,new LinearLayout.LayoutParams(-1,0,1));
        empty=text("还没有保存的内容，去发现下载壁纸。",14,MUTED);empty.setGravity(Gravity.CENTER);empty.setPadding(0,dp(30),0,dp(30));addView(empty);
        selectionBar=new LinearLayout(activity);selectionBar.setPadding(0,dp(8),0,dp(12));
        selectAll=button("全选",false,()->{if(selected.size()==videos.size())selected.clear();else for(JSONObject video:videos)selected.add(video.optString("id"));updateSelection();adapter.notifyDataSetChanged();});
        selectionBar.addView(selectAll,new LinearLayout.LayoutParams(0,dp(45),1));clean=button("清理所选",true,this::confirmClean);
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,dp(45),2);cp.leftMargin=dp(10);selectionBar.addView(clean,cp);addView(selectionBar);refresh();
    }
    void showCategory(boolean images){category=images?1:0;activity.getSharedPreferences("library",0).edit().putInt("category",category).apply();categoryButtons();refresh();}
    private void categoryButtons(){categories.removeAllViews();String[] names={"视频","图片与场景"};for(int i=0;i<2;i++){int index=i;Button b=button(names[i],category==i,()->{if(cleaning)return;leaveManagement();category=index;activity.getSharedPreferences("library",0).edit().putInt("category",category).apply();categoryButtons();refresh();});LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(40),1);if(i>0)p.leftMargin=dp(8);categories.addView(b,p);}}
    private int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private TextView text(String value,int size,int color){TextView text=new TextView(activity);text.setText(value);text.setTextSize(size);text.setTextColor(color);return text;}
    private GradientDrawable panel(int color,int radius){GradientDrawable background=new GradientDrawable();background.setColor(color);background.setCornerRadius(dp(radius));return background;}
    private Button button(String value,boolean primary,Runnable action){Button button=new Button(activity);button.setText(value);button.setAllCaps(false);button.setTextSize(13);button.setPadding(dp(12),0,dp(12),0);button.setTextColor(primary?0xff173d31:TEXT);button.setBackground(panel(primary?ACCENT:PANEL,12));button.setOnClickListener(v->action.run());return button;}
    private void toast(String value){activity.runOnUiThread(()->{if(!activity.isFinishing())Toast.makeText(activity,value,Toast.LENGTH_LONG).show();});}
    public void refresh(){
        if(closed)return;ArrayList<JSONObject> next=new ArrayList<>();StringBuilder signature=new StringBuilder();long used=0;int failedMigrations=0;
        signature.append(category);for(JSONObject record:app.jobs())if(record.optString("state").equals("saved")&&(category==0?record.optString("kind","video").equals("video"):!record.optString("kind","video").equals("video"))){
            next.add(record);used+=record.optLong("size");if(!record.optString("migrationError").isEmpty())failedMigrations++;
            signature.append(record.optString("id")).append(record.optString("title")).append(record.optString("previewUrl")).append(record.optString("uri")).append(record.optString("migrationError"));
        }
        summary.setText(next.size()+" 项 · "+size(used)+(category==0?" · 单击播放 · 长按管理":" · 单击预览 · 长按管理"));
        migration.setText(failedMigrations+" 个旧视频尚未迁移，点此重试");migration.setVisibility(failedMigrations>0?VISIBLE:GONE);
        refreshTransfers();
        signature.append(app.favorites().toString());String updated=signature.toString();
        if(!updated.equals(fingerprint)){
            fingerprint=updated;Collections.reverse(next);videos.clear();videos.addAll(next);Set<String> ids=new HashSet<>();for(JSONObject video:videos)ids.add(video.optString("id"));selected.retainAll(ids);adapter.notifyDataSetChanged();
        }
        if(videos.isEmpty()&&managing&&!cleaning){managing=false;selected.clear();}
        empty.setVisibility(videos.isEmpty()?VISIBLE:GONE);grid.setVisibility(videos.isEmpty()?GONE:VISIBLE);updateSelection();
    }
    private void updateSelection(){
        manage.setText(managing?"完成":"管理");manage.setEnabled(!cleaning&&(managing||app.filesReady.isDone()&&!videos.isEmpty()));selectionBar.setVisibility(managing?VISIBLE:GONE);
        clean.setText(cleaning?"正在清理…":"清理所选 ("+selected.size()+")");clean.setEnabled(!cleaning&&!selected.isEmpty());clean.setAlpha(clean.isEnabled()?1f:.45f);
        selectAll.setText(!videos.isEmpty()&&selected.size()==videos.size()?"取消全选":"全选");selectAll.setEnabled(!cleaning&&!videos.isEmpty());
    }
    private void videoMenu(JSONObject video){
        String itemId=video.optString("itemId");AppSheet sheet=new AppSheet(activity,video.optString("title"),"已保存在应用内",video.optString("previewUrl"));
        sheet.action("heart",app.isFavorite(itemId)?"取消收藏":"收藏","收藏独立于下载",false,()->{try{JSONObject metadata=new JSONObject(video.toString());VideoApp.put(metadata,"video",video.optString("kind","video").equals("video"));app.toggleFavorite(metadata);refresh();}catch(Exception e){toast(VideoApp.error(e));}});
        sheet.action("trash","删除内容","收藏和正在使用的壁纸副本会保留",true,()->confirmDelete(java.util.List.of(video.optString("id"))));
        if(video.optBoolean("officialPackage")){sheet.action("download","重新下载为主线壁纸","保留原包直到成功",false,()->SceneAdaptation.open(activity,app,video));sheet.detail("Hydrogen 官方 MPKG，需主线适配后设置");sheet.show();return;}sheet.detail("正在准备…");sheet.show();app.eligibility.check(video,result->{if(closed||!sheet.showing())return;sheet.detail(result.video()?(video.optString("kind").equals("scene")?"场景已通过画面检查，可调整图层与交互":"可设置动态壁纸或静态画面"):result.still()?"不允许设置视频壁纸\n"+result.reason():"本机无法将此内容设为壁纸\n"+result.reason());if(result.video()||result.still()&&!video.optString("kind").equals("scene"))sheet.action("wallpaper",result.video()?"设为壁纸":"设为静态壁纸",result.video()?"调整运动、交互与画面效果":"从视频中自由选择画面",false,()->activity.startActivity(new android.content.Intent(activity,WallpaperSetupActivity.class).putExtra("id",video.optString("id"))));});
    }
    private void refreshTransfers(){
        int scroll=transfers.getScrollY();transferCards.removeAllViews();int count=0;List<JSONObject> jobs=app.jobs();Collections.reverse(jobs);
        for(JSONObject job:jobs){if(job.optString("state").equals("saved"))continue;count++;String id=job.optString("id");boolean active=app.enqueued.contains(id);
            LinearLayout card=new LinearLayout(activity);card.setOrientation(VERTICAL);card.setPadding(dp(12),dp(10),dp(12),dp(10));card.setBackground(panel(PANEL,12));
            TextView title=text(job.optString("title"),13,TEXT);title.setMaxLines(1);title.setEllipsize(TextUtils.TruncateAt.END);card.addView(title);
            String status=job.optString("error");if(status.isEmpty())status=job.optString("message","等待下载");TextView detail=text(status,11,MUTED);detail.setMaxLines(2);card.addView(detail);
            ProgressBar progress=new ProgressBar(activity,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(1000);long total=job.optLong("total");progress.setIndeterminate(active&&total<=0);progress.setProgress((int)(1000d*job.optLong("done")/Math.max(1,total)));card.addView(progress,new LayoutParams(-1,dp(10)));
            boolean stopping=active&&(job.optString("state").equals("pausing")||job.optString("state").equals("canceling"));
            LinearLayout actions=new LinearLayout(activity);Button pause=button(active?stopping?"正在停止…":"暂停":"继续下载",!active,()->{if(active)stopTransfer(id,false);else resume.accept(id);});pause.setEnabled(!stopping);pause.setAlpha(stopping?.45f:1);actions.addView(pause,new LayoutParams(0,dp(36),1));
            Button cancel=button("取消下载",false,()->confirmCancel(id));cancel.setEnabled(!job.optString("state").equals("canceling"));LayoutParams cancelParams=new LayoutParams(0,dp(36),1);cancelParams.leftMargin=dp(6);actions.addView(cancel,cancelParams);
            if(!job.optString("error").isEmpty())actions.addView(button("复制错误",false,()->{activity.getSystemService(android.content.ClipboardManager.class).setPrimaryClip(android.content.ClipData.newPlainText("下载错误","WallpaperMobile\n作品 "+job.optString("itemId")+"\n"+job.optString("error")));toast("错误已复制");}),new LayoutParams(0,dp(36),1));
            card.addView(actions);LayoutParams cp=new LayoutParams(-1,-2);cp.bottomMargin=dp(8);transferCards.addView(card,cp);
        }
        transfers.setVisibility(count>0?VISIBLE:GONE);transfers.setLayoutParams(new LayoutParams(-1,count>0?dp(count==1?142:218):0));transfers.post(()->transfers.scrollTo(0,scroll));
    }
    private void stopTransfer(String id,boolean discard){
        try{JSONObject record=app.job(id);if(record.optString("state").equals("saved")){toast("下载已完成，可在长按菜单中删除内容。");return;}
            if(app.enqueued.contains(id))activity.startService(new android.content.Intent(activity,VideoDownloadService.class).setAction(discard?VideoDownloadService.CANCEL:VideoDownloadService.PAUSE).putExtra("id",id));
            else if(discard)app.work.submit(()->{try{if(!app.job(id).optString("state").equals("saved"))app.discard(id);}catch(Exception problem){toast(VideoApp.error(problem));}});
        }catch(Exception problem){toast(VideoApp.error(problem));}
    }
    private void confirmCancel(String id){AppSheet.confirm(activity,"取消此下载？","停止任务并删除未完成资源和临时文件，收藏会保留。","取消下载",true,()->stopTransfer(id,true));}
    private void confirmClean(){
        if(cleaning||selected.isEmpty())return;confirmDelete(new ArrayList<>(selected));
    }
    private void confirmDelete(List<String> ids){
        if(cleaning||ids.isEmpty())return;
        AppSheet.confirm(activity,"删除 "+ids.size()+" 项内容？","将释放内容占用的空间。收藏和正在使用的动态壁纸会保留。","删除内容",true,()->{cleaning=true;updateSelection();app.work.submit(()->{
            int removed=0;String failure="";for(String id:ids)try{app.discard(id);removed++;}catch(Exception e){failure=VideoApp.error(e);}
            String result="已删除 "+removed+" 项内容"+(failure.isEmpty()?"":"；"+failure);
            activity.runOnUiThread(()->{if(closed)return;cleaning=false;managing=false;selected.clear();refresh();adapter.notifyDataSetChanged();toast(result);});
        });});
    }
    private class PosterAdapter extends BaseAdapter {
        public int getCount(){return videos.size();}public Object getItem(int index){return videos.get(index);}public long getItemId(int index){return index;}
        public View getView(int index,View old,android.view.ViewGroup parent){
            JSONObject video=videos.get(index);String id=video.optString("id");LinearLayout card=new LinearLayout(activity);card.setOrientation(VERTICAL);card.setBackground(panel(PANEL,12));card.setClipToOutline(true);
            FrameLayout poster=new FrameLayout(activity);poster.setBackgroundColor(0xff233344);int width=(getResources().getDisplayMetrics().widthPixels-dp(46))/2;
            ImageView image=new PreviewImageView(activity);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setContentDescription(video.optString("title"));poster.addView(image,new FrameLayout.LayoutParams(-1,-1));
            if(app.isFavorite(video.optString("itemId"))&&!managing){ImageView heart=new ImageView(activity);heart.setImageDrawable(new Ui.Icon("heartFilled",ACCENT,dp(18)));heart.setPadding(dp(6),dp(6),dp(6),dp(6));heart.setBackground(panel(0xb30b1018,16));FrameLayout.LayoutParams hp=new FrameLayout.LayoutParams(dp(30),dp(30),Gravity.TOP|Gravity.RIGHT);hp.setMargins(dp(7),dp(7),dp(7),dp(7));poster.addView(heart,hp);}
            if(managing){TextView check=text(selected.contains(id)?"✓":"○",23,selected.contains(id)?0xff173d31:TEXT);check.setGravity(Gravity.CENTER);check.setBackground(panel(selected.contains(id)?ACCENT:0xc5212a35,18));FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(dp(34),dp(34),Gravity.TOP|Gravity.RIGHT);p.setMargins(dp(8),dp(8),dp(8),dp(8));poster.addView(check,p);}
            String type=video.optString("kind","video");if(!type.equals("video")){String label=type.equals("scene")?"分层场景":type.equals("animated")?"动画图片":type.equals("image")?"图片":"暂不兼容";TextView badge=text(label,10,type.equals("package")?0xffffb3be:ACCENT);badge.setPadding(dp(7),dp(4),dp(7),dp(4));badge.setBackground(panel(0xd90b1018,8));FrameLayout.LayoutParams bp=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.RIGHT);bp.setMargins(dp(6),dp(6),dp(6),dp(6));poster.addView(badge,bp);}
            card.addView(poster,new LinearLayout.LayoutParams(-1,Math.max(dp(100),width*10/16)));
            TextView title=text(video.optString("title"),13,TEXT);title.setPadding(dp(10),dp(10),dp(10),dp(11));Ui.cardTitle(title);card.addView(title);
            card.setContentDescription(video.optString("title")+(managing?(selected.contains(id)?"，已勾选":"，未勾选"):"，单击播放"));loadPoster(image,video);return card;
        }
    }
    private void loadPoster(ImageView image,JSONObject record){
        String url=record.optString("previewUrl");
        if(!url.isEmpty())app.previews.load(image,url,()->loadLocalPoster(image,record));else loadLocalPoster(image,record);
    }
    private void loadLocalPoster(ImageView image,JSONObject record){
        String id=record.optString("id");image.setTag(id);Bitmap existing=cache.get(id);if(existing!=null){image.setImageBitmap(existing);return;}
        waiters.computeIfAbsent(id,key->new ArrayList<>()).add(new WeakReference<>(image));if(!loading.add(id)||closed)return;
        thumbnails.submit(()->{
            Bitmap bitmap=null;
            try {
                File disk=app.preview(id);if(disk.isFile())bitmap=BitmapFactory.decodeFile(disk.getAbsolutePath());
                if(bitmap==null){
                    if(bitmap==null){JSONObject saved=app.job(id);bitmap=saved.optString("kind","video").equals("video")?videoFrame(saved):saved.optBoolean("compatible",true)?CanvasWallpaper.poster(app.video(id),640):null;}
                    if(bitmap!=null&&!closed){
                        app.job(id);File stage=new File(disk.getPath()+".tmp");
                        try{try(FileOutputStream output=new FileOutputStream(stage)){bitmap.compress(Bitmap.CompressFormat.JPEG,86,output);}if(!stage.renameTo(disk))stage.delete();}finally{stage.delete();}
                    }
                }
            }catch(Exception ignored){}
            final Bitmap result=bitmap;activity.runOnUiThread(()->{
                loading.remove(id);List<WeakReference<ImageView>> views=waiters.remove(id);if(closed)return;
                if(result!=null){cache.put(id,result);if(views!=null)for(WeakReference<ImageView> ref:views){ImageView view=ref.get();if(view!=null&&id.equals(view.getTag()))view.setImageBitmap(result);}}
            });
        });
    }
    private Bitmap videoFrame(JSONObject record) throws Exception {
        try(MediaMetadataRetriever reader=new MediaMetadataRetriever()){
            if(record.optString("storage").equals("private"))reader.setDataSource(app.video(record.optString("id")).getAbsolutePath());
            else reader.setDataSource(activity,android.net.Uri.parse(record.getString("uri")));
            int width=number(reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)),height=number(reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
            int rotation=number(reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION));if(rotation==90||rotation==270){int swap=width;width=height;height=swap;}
            float scale=Math.min(1,640f/Math.max(1,Math.max(width,height)));
            return reader.getScaledFrameAtTime(0,MediaMetadataRetriever.OPTION_CLOSEST_SYNC,Math.max(1,(int)(width*scale)),Math.max(1,(int)(height*scale)));
        }
    }
    private static int number(String value){try{return Integer.parseInt(value);}catch(Exception ignored){return 0;}}
    private static String size(long bytes){if(bytes<=0)return "0 B";int power=Math.min(3,(int)(Math.log(bytes)/Math.log(1024)));return String.format(Locale.CHINA,power>1?"%.1f %s":"%.0f %s",bytes/Math.pow(1024,power),new String[]{"B","KB","MB","GB"}[power]);}
    public void close(){closed=true;thumbnails.shutdownNow();waiters.clear();cache.evictAll();}
    public void leaveManagement(){if(!cleaning){managing=false;selected.clear();updateSelection();adapter.notifyDataSetChanged();}}
}
