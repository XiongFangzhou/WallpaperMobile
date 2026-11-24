package dev.workshopvideo;
import android.app.*;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.RippleDrawable;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;
import java.util.*;
import java.util.function.Consumer;

/** Favorites retain workshop metadata independently from local jobs and files. */
public final class FavoritesView extends LinearLayout {
    private final Activity activity;private final VideoApp app;private final Consumer<JSONObject> open;
    private final ArrayList<JSONObject> entries=new ArrayList<>();private final GridView grid;private final TextView summary;private final View empty;
    private String fingerprint="";
    private static final class Holder{final View card;final ImageView image;final TextView badge,title,info;JSONObject item;String bound;Holder(View card,ImageView image,TextView badge,TextView title,TextView info){this.card=card;this.image=image;this.badge=badge;this.title=title;this.info=info;}}
    private final BaseAdapter adapter=new BaseAdapter(){
        public int getCount(){return entries.size();}public Object getItem(int p){return entries.get(p);}public long getItemId(int p){return p;}
        public View getView(int p,View old,ViewGroup parent){Holder holder=old!=null&&old.getTag() instanceof Holder existing?existing:card();bind(holder,entries.get(p));return holder.card;}
    };
    public FavoritesView(Activity activity,VideoApp app,Consumer<JSONObject> open,Runnable discover){super(activity);this.activity=activity;this.app=app;this.open=open;setOrientation(VERTICAL);setPadding(dp(18),0,dp(18),0);
        summary=Ui.text(activity,"",12,Ui.MUTED);summary.setPadding(0,dp(2),0,dp(12));addView(summary);
        grid=new GridView(activity);grid.setNumColumns(2);grid.setHorizontalSpacing(dp(12));grid.setVerticalSpacing(dp(12));grid.setSelector(android.R.color.transparent);grid.setClipToPadding(false);grid.setPadding(0,0,0,dp(16));grid.setVerticalScrollBarEnabled(false);grid.setAdapter(adapter);
        grid.setOnItemClickListener((p,v,i,id)->open.accept(entries.get(i)));
        grid.setOnItemLongClickListener((p,v,i,id)->{JSONObject item=entries.get(i);AppSheet sheet=new AppSheet(activity,item.optString("title"),"已收藏的工坊作品",item.optString("previewUrl"));sheet.action("play","查看详情","预览、介绍与下载",false,()->open.accept(item));sheet.action("heart","取消收藏","已下载的内容不受影响",false,()->{try{app.toggleFavorite(item);refresh();}catch(Exception e){Toast.makeText(activity,VideoApp.error(e),Toast.LENGTH_LONG).show();}});sheet.show();return true;});addView(grid,new LayoutParams(-1,0,1));
        LinearLayout box=Ui.empty(activity,"heart","还没有收藏","在发现中点一下作品封面上的爱心即可收藏。\n收藏不占空间，删除已下载内容也不会影响收藏。");
        Button go=Ui.button(activity,"去发现",Ui.Kind.PRIMARY,discover);LinearLayout.LayoutParams gp=new LinearLayout.LayoutParams(dp(200),dp(46));gp.topMargin=dp(22);gp.gravity=Gravity.CENTER_HORIZONTAL;box.addView(go,gp);
        empty=box;addView(empty);refresh();
    }
    private int dp(float value){return Ui.dp(activity,value);}
    /** Cards stay plain views without click listeners so GridView item clicks and long presses still fire. */
    private Holder card(){
        LinearLayout card=new LinearLayout(activity);card.setOrientation(VERTICAL);card.setBackground(Ui.card(activity));card.setClipToOutline(true);card.setForeground(new RippleDrawable(ColorStateList.valueOf(0x1affffff),null,Ui.round(Color.WHITE,dp(18))));
        FrameLayout cover=new FrameLayout(activity);ImageView image=new PreviewImageView(activity);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setBackgroundColor(0xff1b2634);cover.addView(image,new FrameLayout.LayoutParams(-1,-1));
        View shade=new View(activity);shade.setBackground(Ui.coverShade());cover.addView(shade,new FrameLayout.LayoutParams(-1,dp(40),Gravity.BOTTOM));
        TextView badge=Ui.badge(activity,"",Ui.ACCENT);FrameLayout.LayoutParams bp=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.START);bp.setMargins(dp(8),dp(8),dp(8),dp(8));cover.addView(badge,bp);
        int width=(getResources().getDisplayMetrics().widthPixels-dp(48))/2;card.addView(cover,new LayoutParams(-1,Math.max(dp(100),width*10/16)));
        LinearLayout meta=new LinearLayout(activity);meta.setOrientation(VERTICAL);meta.setPadding(dp(12),dp(10),dp(12),dp(12));
        TextView title=Ui.text(activity,"",13,Ui.TEXT);Ui.cardTitle(title);meta.addView(title);
        TextView info=Ui.text(activity,"",11,Ui.SUBTLE);info.setSingleLine(true);info.setPadding(0,dp(6),0,0);meta.addView(info);card.addView(meta);
        Holder holder=new Holder(card,image,badge,title,info);card.setTag(holder);return holder;
    }
    private void bind(Holder holder,JSONObject item){
        holder.item=item;holder.title.setText(item.optString("title"));holder.card.setContentDescription(item.optString("title"));
        long size=item.optLong("size"),created=item.optLong("created");String info=(size>0?Ui.size(size):"")+(size>0&&created>0?" · ":"")+(created>0?Ui.date((int)created):"");holder.info.setText(info);
        String type=typeName(item);holder.badge.setText(type);holder.badge.setVisibility(type.isEmpty()?GONE:VISIBLE);
        String url=item.optString("previewUrl");if(!url.equals(holder.bound)){holder.bound=url;holder.image.setImageDrawable(null);holder.image.setTag(url);app.previews.load(holder.image,url);}
    }
    private static String typeName(JSONObject item){
        if(item.optString("itemId").startsWith("local:"))return "本地";
        return switch(item.optString("type",item.optBoolean("video",true)?"Video":"")){case "Video"->"视频";case "Scene"->"场景";case "Web"->"网页";case "Application"->"应用";default->"";};
    }
    public void refresh(){List<JSONObject> next=app.favorites();Collections.reverse(next);String key=next.toString();if(!key.equals(fingerprint)){fingerprint=key;entries.clear();entries.addAll(next);adapter.notifyDataSetChanged();}summary.setText(entries.size()+" 个收藏 · 长按可取消收藏");summary.setVisibility(entries.isEmpty()?GONE:VISIBLE);empty.setVisibility(entries.isEmpty()?VISIBLE:GONE);grid.setVisibility(entries.isEmpty()?GONE:VISIBLE);}
}
