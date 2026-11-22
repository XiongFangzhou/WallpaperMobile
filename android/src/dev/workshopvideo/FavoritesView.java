package dev.workshopvideo;
import android.app.*;
import android.view.*;
import android.widget.*;
import android.text.TextUtils;
import android.graphics.drawable.GradientDrawable;
import org.json.JSONObject;
import java.util.*;
import java.util.function.Consumer;

/** Favorites retain workshop metadata independently from local jobs and files. */
public final class FavoritesView extends LinearLayout {
    private final Activity activity;private final VideoApp app;private final Consumer<JSONObject> open;
    private final ArrayList<JSONObject> entries=new ArrayList<>();private final GridView grid;private final TextView summary,empty;
    private String fingerprint="";private final BaseAdapter adapter=new BaseAdapter(){
        public int getCount(){return entries.size();}public Object getItem(int p){return entries.get(p);}public long getItemId(int p){return p;}
        public View getView(int p,View old,ViewGroup parent){JSONObject item=entries.get(p);LinearLayout card=new LinearLayout(activity);card.setOrientation(VERTICAL);GradientDrawable bg=new GradientDrawable();bg.setColor(Ui.PANEL);bg.setCornerRadius(dp(16));card.setBackground(bg);card.setClipToOutline(true);
            ImageView image=new PreviewImageView(activity);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setBackgroundColor(0xff233344);image.setContentDescription(item.optString("title"));card.addView(image,new LayoutParams(-1,dp(118)));app.previews.load(image,item.optString("previewUrl"));
            TextView title=label(item.optString("title"),13,Ui.TEXT);Ui.cardTitle(title);title.setPadding(dp(12),dp(10),dp(12),dp(12));card.addView(title);return card;
        }
    };
    public FavoritesView(Activity activity,VideoApp app,Consumer<JSONObject> open){super(activity);this.activity=activity;this.app=app;this.open=open;setOrientation(VERTICAL);setPadding(dp(18),dp(18),dp(18),0);
        addView(label("收藏",26,Ui.TEXT));summary=label("",12,Ui.MUTED);summary.setPadding(0,dp(8),0,dp(16));addView(summary);
        grid=new GridView(activity);grid.setNumColumns(2);grid.setHorizontalSpacing(dp(10));grid.setVerticalSpacing(dp(12));grid.setSelector(android.R.color.transparent);grid.setAdapter(adapter);grid.setOnItemClickListener((p,v,i,id)->open.accept(entries.get(i)));
        grid.setOnItemLongClickListener((p,v,i,id)->{JSONObject item=entries.get(i);AppSheet sheet=new AppSheet(activity,item.optString("title"),"已收藏的工坊作品",item.optString("previewUrl"));sheet.action("play","查看作品","预览、介绍与下载",false,()->open.accept(item));sheet.action("heart","取消收藏","本地视频会保留",false,()->{try{app.toggleFavorite(item);refresh();}catch(Exception e){Toast.makeText(activity,VideoApp.error(e),1).show();}});sheet.show();return true;});addView(grid,new LayoutParams(-1,0,1));
        empty=label("遇到喜欢的作品，点一下爱心。\n收藏无需下载，删除视频也会保留收藏。",14,Ui.MUTED);empty.setGravity(Gravity.CENTER);empty.setPadding(dp(12),dp(48),dp(12),dp(48));addView(empty);refresh();
    }
    private int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}private TextView label(String s,int size,int color){TextView v=new TextView(activity);v.setText(s);v.setTextSize(size);v.setTextColor(color);return v;}
    public void refresh(){List<JSONObject> next=app.favorites();Collections.reverse(next);String key=next.toString();if(!key.equals(fingerprint)){fingerprint=key;entries.clear();entries.addAll(next);adapter.notifyDataSetChanged();}summary.setText(entries.size()+" 个作品 · 长按管理收藏");empty.setVisibility(entries.isEmpty()?VISIBLE:GONE);grid.setVisibility(entries.isEmpty()?GONE:VISIBLE);}
}
