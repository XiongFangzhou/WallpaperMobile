package dev.workshopvideo;

import android.app.*;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.RippleDrawable;
import android.media.MediaMetadataRetriever;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.LruCache;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;
import java.io.*;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Saved posters remain in place while management toggles selection on cards. */
public final class SavedLibraryView extends LinearLayout {
    private static final int TEXT=Ui.TEXT,MUTED=Ui.MUTED,ACCENT=Ui.ACCENT;
    private final Activity activity;
    private final VideoApp app;
    private final Consumer<JSONObject> open;
    private final Consumer<String> resume;
    private final LinearLayout transferCards;
    private final ScrollView transfers;
    private final TextView transferTitle;
    private final ArrayList<JSONObject> videos=new ArrayList<>();
    private final Set<String> selected=new HashSet<>(),loading=new HashSet<>();
    private final Map<String,List<WeakReference<ImageView>>> waiters=new HashMap<>();
    private final ExecutorService thumbnails=Executors.newSingleThreadExecutor();
    private final LruCache<String,Bitmap> cache=new LruCache<String,Bitmap>(12*1024*1024){@Override protected int sizeOf(String key,Bitmap value){return value.getByteCount();}};
    private final GridView grid;
    private final PosterAdapter adapter=new PosterAdapter();
    private final TextView summary,migration;
    private final View empty;
    private final Button manage,selectAll,clean;
    private final LinearLayout selectionBar,categories;
    private int category;
    private String fingerprint="";
    private long lastOpen;
    private boolean managing,cleaning;
    private volatile boolean closed;

    public SavedLibraryView(Activity activity,VideoApp app,Consumer<JSONObject> open,Consumer<String> resume){
        super(activity);this.activity=activity;this.app=app;this.open=open;this.resume=resume;setOrientation(VERTICAL);setPadding(dp(18),0,dp(18),0);
        LinearLayout heading=new LinearLayout(activity);heading.setGravity(Gravity.CENTER_VERTICAL);
        categories=new LinearLayout(activity);heading.addView(categories,new LinearLayout.LayoutParams(0,-2,1));manage=Ui.button(activity,"管理",Ui.Kind.SECONDARY,()->{
            if(cleaning)return;if(!app.filesReady.isDone()){toast("正在整理本地视频，请稍候。");return;}managing=!managing;selected.clear();updateSelection();adapter.notifyDataSetChanged();
        });LinearLayout.LayoutParams mp0=new LinearLayout.LayoutParams(-2,dp(44));mp0.leftMargin=dp(10);heading.addView(manage,mp0);addView(heading);
        category=activity.getSharedPreferences("library",0).getInt("category",0);categoryButtons();
        summary=Ui.text(activity,"",12,MUTED);summary.setPadding(0,dp(12),0,dp(10));addView(summary);
        migration=Ui.text(activity,"",12,ACCENT);migration.setPadding(dp(12),dp(10),dp(12),dp(10));migration.setBackground(Ui.pressable(Ui.round(Ui.ACCENT_SOFT,dp(12)),0x22a9efd5,dp(12)));migration.setOnClickListener(v->app.work.submit(app::migrateVideos));
        LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(-1,-2);mp.bottomMargin=dp(12);addView(migration,mp);
        transferTitle=Ui.overline(activity,"");transferTitle.setPadding(0,0,0,dp(8));addView(transferTitle);
        transfers=new ScrollView(activity);transfers.setVerticalScrollBarEnabled(false);transferCards=new LinearLayout(activity);transferCards.setOrientation(VERTICAL);transfers.addView(transferCards);addView(transfers,new LinearLayout.LayoutParams(-1,0));
        grid=new GridView(activity);grid.setNumColumns(2);grid.setHorizontalSpacing(dp(12));grid.setVerticalSpacing(dp(12));grid.setSelector(android.R.color.transparent);grid.setClipToPadding(false);grid.setPadding(0,0,0,dp(14));grid.setVerticalScrollBarEnabled(false);grid.setAdapter(adapter);
        grid.setOnItemClickListener((parent,view,index,rowId)->{
            JSONObject video=videos.get(index);String id=video.optString("id");
            if(managing){if(cleaning)return;if(!selected.add(id))selected.remove(id);updateSelection();adapter.notifyDataSetChanged();return;}
            long now=SystemClock.uptimeMillis();if(now-lastOpen>=500){lastOpen=now;open.accept(video);}
        });grid.setOnItemLongClickListener((parent,view,index,rowId)->{if(!cleaning)videoMenu(videos.get(index));return true;});addView(grid,new LinearLayout.LayoutParams(-1,0,1));
        empty=Ui.empty(activity,"library","这里还是空的","在「发现」中下载喜欢的作品，它们会出现在这里，可以直接播放或设为壁纸。");addView(empty);
        selectionBar=new LinearLayout(activity);selectionBar.setPadding(0,dp(8),0,dp(12));
        selectAll=Ui.button(activity,"全选",Ui.Kind.SECONDARY,()->{if(selected.size()==videos.size())selected.clear();else for(JSONObject video:videos)selected.add(video.optString("id"));updateSelection();adapter.notifyDataSetChanged();});
        selectionBar.addView(selectAll,new LinearLayout.LayoutParams(0,dp(46),1));clean=Ui.button(activity,"清理所选",Ui.Kind.DANGER,this::confirmClean);
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,dp(46),2);cp.leftMargin=dp(10);selectionBar.addView(clean,cp);addView(selectionBar);refresh();
    }
    void showCategory(boolean images){category=images?1:0;activity.getSharedPreferences("library",0).edit().putInt("category",category).apply();categoryButtons();refresh();}
    private void categoryButtons(){
        categories.removeAllViews();categories.addView(Ui.segmented(activity,new String[]{"视频","图片与场景"},category,index->{if(cleaning||index==category)return;leaveManagement();category=index;activity.getSharedPreferences("library",0).edit().putInt("category",category).apply();categoryButtons();refresh();}),new LinearLayout.LayoutParams(-1,-2));
    }
    private int dp(float value){return Ui.dp(activity,value);}
    private TextView text(String value,int size,int color){return Ui.text(activity,value,size,color);}
    private void toast(String value){activity.runOnUiThread(()->{if(!activity.isFinishing())Toast.makeText(activity,value,Toast.LENGTH_LONG).show();});}
    public void refresh(){
        if(closed)return;ArrayList<JSONObject> next=new ArrayList<>();StringBuilder signature=new StringBuilder();long used=0;int failedMigrations=0;
        signature.append(category);for(JSONObject record:app.jobs())if(record.optString("state").equals("saved")&&(category==0?record.optString("kind","video").equals("video"):!record.optString("kind","video").equals("video"))){
            next.add(record);used+=record.optLong("size");if(!record.optString("migrationError").isEmpty())failedMigrations++;
            signature.append(record.optString("id")).append(record.optString("title")).append(record.optString("previewUrl")).append(record.optString("uri")).append(record.optString("migrationError"));
        }
        summary.setText(next.size()+" 项 · "+(used>0?Ui.size(used):"0 B"));
        migration.setText(failedMigrations+" 个旧视频还在相册中，点此重新迁移到应用内");migration.setVisibility(failedMigrations>0?VISIBLE:GONE);
        refreshTransfers();
        signature.append(app.favorites().toString());String updated=signature.toString();
        if(!updated.equals(fingerprint)){
            fingerprint=updated;Collections.reverse(next);videos.clear();videos.addAll(next);Set<String> ids=new HashSet<>();for(JSONObject video:videos)ids.add(video.optString("id"));selected.retainAll(ids);adapter.notifyDataSetChanged();
        }
        if(videos.isEmpty()&&managing&&!cleaning){managing=false;selected.clear();}
        empty.setVisibility(videos.isEmpty()?VISIBLE:GONE);grid.setVisibility(videos.isEmpty()?GONE:VISIBLE);updateSelection();
    }
    private void updateSelection(){
        manage.setText(managing?"完成":"管理");Ui.style(manage,managing?Ui.Kind.PRIMARY:Ui.Kind.SECONDARY);Ui.enable(manage,!cleaning&&(managing||app.filesReady.isDone()&&!videos.isEmpty()));selectionBar.setVisibility(managing?VISIBLE:GONE);
        clean.setText(cleaning?"正在清理…":"清理所选 ("+selected.size()+")");Ui.enable(clean,!cleaning&&!selected.isEmpty());
        selectAll.setText(!videos.isEmpty()&&selected.size()==videos.size()?"取消全选":"全选");Ui.enable(selectAll,!cleaning&&!videos.isEmpty());
    }
    private void videoMenu(JSONObject video){
        String itemId=video.optString("itemId");AppSheet sheet=new AppSheet(activity,video.optString("title"),null,video.optString("previewUrl"));
        sheet.action("heart",app.isFavorite(itemId)?"取消收藏":"收藏","",false,()->{try{JSONObject metadata=new JSONObject(video.toString());VideoApp.put(metadata,"video",video.optString("kind","video").equals("video"));app.toggleFavorite(metadata);refresh();}catch(Exception e){toast(VideoApp.error(e));}});
        String report=video.optString("adaptationReport");
        if(!report.isEmpty())sheet.action("info","查看转换说明","",false,()->{AppSheet notes=new AppSheet(activity,"转换说明",video.optString("title"),null);TextView body=text(report,13,MUTED);body.setLineSpacing(dp(4),1);body.setTextIsSelectable(true);notes.field(body);notes.footerButton("知道了",true,()->notes.dialog.dismiss());notes.show();});
        sheet.action("trash","删除内容","",true,()->confirmDelete(java.util.List.of(video.optString("id"))));
        if(video.optBoolean("officialPackage")){sheet.action("download","重新下载为主线壁纸","",false,()->SceneAdaptation.open(activity,app,video));sheet.detail("Hydrogen 官方 MPKG，需主线适配后设置");sheet.show();return;}sheet.detail("正在准备…");sheet.show();app.eligibility.check(video,result->{if(closed||!sheet.showing())return;sheet.detail(result.video()?(video.optString("kind").equals("scene")?"场景已通过画面检查，可调整图层与交互":"可设置动态壁纸或静态画面"):result.still()?"不允许设置视频壁纸\n"+result.reason():"本机无法将此内容设为壁纸\n"+result.reason());if(result.video()||result.still()&&!video.optString("kind").equals("scene"))sheet.action("wallpaper",result.video()?"设为壁纸":"设为静态壁纸",result.video()?"调整运动、交互与画面效果":"从视频中自由选择画面",false,()->activity.startActivity(new android.content.Intent(activity,WallpaperSetupActivity.class).putExtra("id",video.optString("id"))));});
    }
    private void refreshTransfers(){
        int scroll=transfers.getScrollY();transferCards.removeAllViews();int count=0;List<JSONObject> jobs=app.jobs();Collections.reverse(jobs);
        for(JSONObject job:jobs){if(job.optString("state").equals("saved"))continue;count++;String id=job.optString("id");boolean active=app.enqueued.contains(id);
            long total=job.optLong("total"),done=job.optLong("done");boolean failed=!job.optString("error").isEmpty();String state=job.optString("state");
            boolean stopping=active&&(state.equals("pausing")||state.equals("canceling"));
            LinearLayout card=new LinearLayout(activity);card.setGravity(Gravity.CENTER_VERTICAL);card.setPadding(dp(14),dp(12),dp(8),dp(12));card.setBackground(Ui.card(activity));
            LinearLayout words=new LinearLayout(activity);words.setOrientation(VERTICAL);
            TextView title=text(job.optString("title"),14,TEXT);title.setTypeface(null,Typeface.BOLD);title.setMaxLines(1);title.setEllipsize(TextUtils.TruncateAt.END);words.addView(title);
            String status=failed?job.optString("error"):stopping?(state.equals("canceling")?"正在取消…":"正在暂停…"):!active?"已暂停":total>0?Ui.size(done)+" / "+Ui.size(total)+" · "+Math.min(100,Math.round(100d*done/total))+"%":job.optString("message","正在准备…");
            TextView detail=text(status,12,failed?Ui.DANGER:MUTED);detail.setMaxLines(2);detail.setEllipsize(TextUtils.TruncateAt.END);detail.setPadding(0,dp(3),0,0);words.addView(detail);
            ProgressBar progress=new ProgressBar(activity,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(1000);progress.setIndeterminate(active&&total<=0&&!stopping);progress.setProgress((int)(1000d*done/Math.max(1,total)));
            progress.setProgressTintList(ColorStateList.valueOf(failed?Ui.DANGER:active?ACCENT:Ui.SUBTLE));progress.setIndeterminateTintList(ColorStateList.valueOf(ACCENT));progress.setProgressBackgroundTintList(ColorStateList.valueOf(Ui.LINE));
            LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,dp(4));pp.topMargin=dp(8);words.addView(progress,pp);
            card.addView(words,new LinearLayout.LayoutParams(0,-2,1));
            if(failed){ImageView copy=Ui.iconButton(activity,"copy","复制错误信息",()->{activity.getSystemService(android.content.ClipboardManager.class).setPrimaryClip(android.content.ClipData.newPlainText("下载错误","WallpaperMobile\n作品 "+job.optString("itemId")+"\n"+job.optString("error")));toast("错误信息已复制");});copy.setBackground(null);card.addView(copy,new LinearLayout.LayoutParams(dp(44),dp(44)));}
            ImageView pause=Ui.iconButton(activity,active?"pause":failed?"refresh":"play",active?"暂停下载":failed?"重试下载":"继续下载",()->{if(active)stopTransfer(id,false);else resume.accept(id);});pause.setBackground(null);if(!active)pause.setImageDrawable(new Ui.Icon(failed?"refresh":"play",ACCENT,dp(20)));Ui.enable(pause,!stopping);card.addView(pause,new LinearLayout.LayoutParams(dp(44),dp(44)));
            ImageView cancel=Ui.iconButton(activity,"close","取消下载",()->confirmCancel(id));cancel.setBackground(null);Ui.enable(cancel,!state.equals("canceling"));card.addView(cancel,new LinearLayout.LayoutParams(dp(44),dp(44)));
            LayoutParams cp=new LayoutParams(-1,-2);cp.bottomMargin=dp(8);transferCards.addView(card,cp);
        }
        transferTitle.setText("下载任务 · "+count);transferTitle.setVisibility(count>0?VISIBLE:GONE);
        // One card wraps its content; more cards scroll inside a fixed band so saved items stay visible.
        transfers.setVisibility(count>0?VISIBLE:GONE);transfers.setLayoutParams(new LayoutParams(-1,count==0?0:count<=2?-2:dp(196)));transfers.post(()->transfers.scrollTo(0,scroll));
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
    private static final class Poster {
        final LinearLayout card;final ImageView image,heart,check;final TextView badge,title,info;String bound;
        Poster(LinearLayout card,ImageView image,ImageView heart,ImageView check,TextView badge,TextView title,TextView info){this.card=card;this.image=image;this.heart=heart;this.check=check;this.badge=badge;this.title=title;this.info=info;}
    }
    /** Recycled poster cards; selection is drawn as an accent outline plus a check mark. */
    private class PosterAdapter extends BaseAdapter {
        public int getCount(){return videos.size();}public Object getItem(int index){return videos.get(index);}public long getItemId(int index){return index;}
        public View getView(int index,View old,android.view.ViewGroup parent){Poster poster=old!=null&&old.getTag() instanceof Poster existing?existing:poster();bind(poster,videos.get(index));return poster.card;}
    }
    private Poster poster(){
        LinearLayout card=new LinearLayout(activity);card.setOrientation(VERTICAL);card.setClipToOutline(true);card.setForeground(new RippleDrawable(ColorStateList.valueOf(0x1affffff),null,Ui.round(Color.WHITE,dp(18))));
        FrameLayout cover=new FrameLayout(activity);cover.setBackgroundColor(0xff1b2634);
        ImageView image=new PreviewImageView(activity);image.setScaleType(ImageView.ScaleType.CENTER_CROP);cover.addView(image,new FrameLayout.LayoutParams(-1,-1));
        View shade=new View(activity);shade.setBackground(Ui.coverShade());cover.addView(shade,new FrameLayout.LayoutParams(-1,dp(40),Gravity.BOTTOM));
        TextView badge=Ui.badge(activity,"",ACCENT);FrameLayout.LayoutParams bp=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.START);bp.setMargins(dp(8),dp(8),dp(8),dp(8));cover.addView(badge,bp);
        ImageView heart=new ImageView(activity);heart.setImageDrawable(new Ui.Icon("heartFilled",ACCENT,dp(16)));heart.setScaleType(ImageView.ScaleType.CENTER);heart.setBackground(Ui.round(Ui.SCRIM,dp(15)));heart.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        FrameLayout.LayoutParams hp=new FrameLayout.LayoutParams(dp(30),dp(30),Gravity.TOP|Gravity.END);hp.setMargins(dp(8),dp(8),dp(8),dp(8));cover.addView(heart,hp);
        ImageView check=new ImageView(activity);check.setScaleType(ImageView.ScaleType.CENTER);check.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);FrameLayout.LayoutParams kp=new FrameLayout.LayoutParams(dp(30),dp(30),Gravity.TOP|Gravity.END);kp.setMargins(dp(8),dp(8),dp(8),dp(8));cover.addView(check,kp);
        int width=(getResources().getDisplayMetrics().widthPixels-dp(48))/2;card.addView(cover,new LinearLayout.LayoutParams(-1,Math.max(dp(100),width*10/16)));
        LinearLayout meta=new LinearLayout(activity);meta.setOrientation(VERTICAL);meta.setPadding(dp(12),dp(10),dp(12),dp(12));TextView title=text("",13,TEXT);Ui.cardTitle(title);meta.addView(title);
        TextView info=text("",11,Ui.SUBTLE);info.setSingleLine(true);info.setPadding(0,dp(6),0,0);meta.addView(info);card.addView(meta);
        Poster poster=new Poster(card,image,heart,check,badge,title,info);card.setTag(poster);return poster;
    }
    private void bind(Poster poster,JSONObject video){
        String id=video.optString("id");boolean chosen=managing&&selected.contains(id);
        poster.card.setBackground(chosen?Ui.outlined(Ui.PANEL,ACCENT,dp(18),dp(2)):Ui.card(activity));
        poster.heart.setVisibility(app.isFavorite(video.optString("itemId"))&&!managing?VISIBLE:GONE);
        poster.check.setVisibility(managing?VISIBLE:GONE);
        if(managing){poster.check.setImageDrawable(chosen?new Ui.Icon("check",Ui.ACCENT_INK,dp(18)):null);poster.check.setBackground(chosen?Ui.round(ACCENT,dp(15)):Ui.outlined(Ui.SCRIM,0xccffffff,dp(15),dp(2)));}
        String type=video.optString("kind","video");String label=type.equals("video")?"":type.equals("scene")?"分层场景":type.equals("animated")?"动画图片":type.equals("image")?"图片":"暂不兼容";
        poster.badge.setText(label);poster.badge.setTextColor(type.equals("package")?Ui.DANGER:ACCENT);poster.badge.setVisibility(label.isEmpty()?GONE:VISIBLE);
        poster.title.setText(video.optString("title"));long size=video.optLong("size");poster.info.setText(size>0?Ui.size(size):"已保存");
        poster.card.setContentDescription(video.optString("title")+(managing?(selected.contains(id)?"，已选":"，未选"):type.equals("video")?"，单击播放":"，单击预览"));
        String key=id+"|"+video.optString("previewUrl");if(!key.equals(poster.bound)){poster.bound=key;poster.image.setImageDrawable(null);poster.image.setTag(null);loadPoster(poster.image,video);}
    }
    private void loadPoster(ImageView image,JSONObject record){
        String url=record.optString("previewUrl");
        if(!url.isEmpty())app.previews.load(image,url,()->loadLocalPoster(image,record));else loadLocalPoster(image,record);
    }
    private void loadLocalPoster(ImageView image,JSONObject record){
        String id=record.optString("id");image.setTag(id);Bitmap existing=cache.get(id);if(existing!=null){image.setImageBitmap(existing);return;}
        if(closed)return;waiters.computeIfAbsent(id,key->new ArrayList<>()).add(new WeakReference<>(image));if(!loading.add(id))return;
        thumbnails.submit(()->{
            Bitmap bitmap=null;
            try {
                File disk=app.preview(id);if(disk.isFile())bitmap=BitmapFactory.decodeFile(disk.getAbsolutePath());
                if(bitmap==null){
                    JSONObject saved=app.job(id);bitmap=saved.optString("kind","video").equals("video")?videoFrame(saved):saved.optBoolean("compatible",true)?CanvasWallpaper.poster(app.video(id),640):null;
                    if(bitmap!=null&&!closed){
                        app.job(id);// Throws if the item was deleted meanwhile; never write an orphan poster.
                        File stage=new File(disk.getPath()+".tmp");
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
    public void close(){closed=true;thumbnails.shutdownNow();waiters.clear();cache.evictAll();}
    public void leaveManagement(){if(!cleaning){managing=false;selected.clear();updateSelection();adapter.notifyDataSetChanged();}}
}
