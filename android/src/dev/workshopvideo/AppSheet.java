package dev.workshopvideo;

import android.app.*;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.Build;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;

/** Shared app sheet: a fixed heading, scrollable body and a horizontal action footer. */
final class AppSheet {
    final Dialog dialog;
    private final Activity activity;
    private final LinearLayout box,fields,actions,footer,header;
    private final ScrollView contentScroll;
    private final TextView subtitle;

    AppSheet(Activity activity,String title,String detail,String previewUrl){
        this.activity=activity;dialog=new Dialog(activity);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        box=column();box.setPadding(dp(18),dp(10),dp(18),dp(18));box.setBackground(background(Ui.PANEL,dp(26)));
        View handle=new View(activity);handle.setBackground(background(0xff425163,dp(3)));LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(dp(34),dp(4));hp.gravity=Gravity.CENTER_HORIZONTAL;hp.bottomMargin=dp(18);box.addView(handle,hp);
        header=new LinearLayout(activity);header.setGravity(Gravity.CENTER_VERTICAL);
        if(previewUrl!=null&&!previewUrl.isEmpty()){
            ImageView image=new PreviewImageView(activity);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setBackground(background(0xff263346,dp(12)));image.setClipToOutline(true);header.addView(image,new LinearLayout.LayoutParams(dp(64),dp(64)));((VideoApp)activity.getApplication()).previews.load(image,previewUrl);
        }
        LinearLayout names=column();names.setPadding(previewUrl!=null&&!previewUrl.isEmpty()?dp(13):0,0,dp(8),0);
        TextView heading=label(title,19,Ui.TEXT);heading.setTypeface(null,Typeface.BOLD);heading.setMaxLines(2);heading.setEllipsize(TextUtils.TruncateAt.END);names.addView(heading);
        subtitle=label(detail,12,Ui.MUTED);subtitle.setPadding(0,dp(6),0,0);subtitle.setVisibility(detail==null||detail.isEmpty()?View.GONE:View.VISIBLE);names.addView(subtitle);header.addView(names,new LinearLayout.LayoutParams(0,-2,1));
        ImageView close=new ImageView(activity);close.setImageDrawable(new Ui.Icon("close",Ui.MUTED,dp(20)));close.setPadding(dp(10),dp(10),dp(10),dp(10));close.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22ffffff),background(0xff202d3c,dp(13)),null));close.setContentDescription("关闭");close.setOnClickListener(v->dialog.dismiss());header.addView(close,new LinearLayout.LayoutParams(dp(40),dp(40)));box.addView(header);
        fields=column();actions=column();footer=new LinearLayout(activity);footer.setGravity(Gravity.CENTER_VERTICAL);footer.setPadding(0,dp(14),0,0);footer.setVisibility(View.GONE);
        LinearLayout content=column();content.addView(fields);content.addView(actions);
        contentScroll=new ScrollView(activity){
            @Override protected void onMeasure(int width,int height){
                Rect visible=new Rect();View decor=dialog.isShowing()?dialog.getWindow().getDecorView():activity.getWindow().getDecorView();decor.getWindowVisibleDisplayFrame(visible);
                int screen=activity.getResources().getDisplayMetrics().heightPixels,available=visible.height()>0?visible.height():screen;
                int reserved=box.getPaddingTop()+box.getPaddingBottom()+dp(42)+Math.max(header.getMeasuredHeight(),dp(58))+(footer.getVisibility()==View.VISIBLE?Math.max(footer.getMeasuredHeight(),dp(62)):0);
                int cap=Math.max(dp(64),Math.min((int)(screen*.64f),available-reserved));
                if(View.MeasureSpec.getMode(height)!=View.MeasureSpec.UNSPECIFIED)cap=Math.min(cap,View.MeasureSpec.getSize(height));
                super.onMeasure(width,View.MeasureSpec.makeMeasureSpec(cap,View.MeasureSpec.AT_MOST));
            }
        };
        contentScroll.addView(content);contentScroll.setVerticalScrollBarEnabled(false);contentScroll.setClipToPadding(false);contentScroll.setVisibility(View.GONE);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);sp.topMargin=dp(18);box.addView(contentScroll,sp);box.addView(footer);
        dialog.setContentView(box);dialog.setCanceledOnTouchOutside(true);
        Window window=dialog.getWindow();window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);window.setDimAmount(.6f);window.setGravity(Gravity.BOTTOM);window.setLayout(-1,-2);window.setNavigationBarColor(Ui.PANEL);window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        box.setOnApplyWindowInsetsListener((v,insets)->{int bottom=Build.VERSION.SDK_INT>=30?insets.getInsets(WindowInsets.Type.systemBars()).bottom:insets.getSystemWindowInsetBottom();v.setPadding(dp(18)+insets.getSystemWindowInsetLeft(),dp(10),dp(18)+insets.getSystemWindowInsetRight(),dp(18)+bottom);return insets;});
    }
    private LinearLayout column(){LinearLayout view=new LinearLayout(activity);view.setOrientation(LinearLayout.VERTICAL);return view;}
    private int dp(float value){return Math.round(value*activity.getResources().getDisplayMetrics().density);}
    private TextView label(String value,int size,int color){TextView text=new TextView(activity);text.setText(value);text.setTextSize(size);text.setTextColor(color);return text;}
    static GradientDrawable background(int color,float radius){GradientDrawable bg=new GradientDrawable();bg.setColor(color);bg.setCornerRadius(radius);return bg;}
    void detail(String value){subtitle.setText(value);subtitle.setVisibility(value==null||value.isEmpty()?View.GONE:View.VISIBLE);}
    void field(View content){LinearLayout.LayoutParams layout=new LinearLayout.LayoutParams(-1,-2);if(fields.getChildCount()>0)layout.topMargin=dp(12);fields.addView(content,layout);contentScroll.setVisibility(View.VISIBLE);}
    View action(String icon,String title,String caption,boolean destructive,Runnable action){
        int color=destructive?0xffff9caa:Ui.ACCENT;LinearLayout row=new LinearLayout(activity);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(12),dp(12),dp(12),dp(12));row.setMinimumHeight(dp(66));
        row.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22a9efd5),background(0xff1d2938,dp(16)),null));
        ImageView image=new ImageView(activity);image.setImageDrawable(new Ui.Icon(icon,color,dp(22)));image.setPadding(dp(10),dp(10),dp(10),dp(10));image.setBackground(background(destructive?0xff382733:0xff243d3c,dp(13)));row.addView(image,new LinearLayout.LayoutParams(dp(42),dp(42)));
        LinearLayout words=column();words.setPadding(dp(14),0,dp(8),0);words.addView(label(title,15,destructive?color:Ui.TEXT));if(caption!=null&&!caption.isEmpty()){TextView hint=label(caption,11,Ui.MUTED);hint.setPadding(0,dp(4),0,0);words.addView(hint);}row.addView(words,new LinearLayout.LayoutParams(0,-2,1));row.addView(label("›",23,Ui.MUTED));
        row.setContentDescription(title+(caption==null||caption.isEmpty()?"":"，"+caption));row.setOnClickListener(v->{dialog.dismiss();action.run();});LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.bottomMargin=dp(8);if(actions.getChildCount()==0&&fields.getChildCount()>0)rp.topMargin=dp(10);actions.addView(row,rp);contentScroll.setVisibility(View.VISIBLE);return row;
    }
    Button footerButton(String title,boolean primary,Runnable run){return footerButton(title,primary,false,run);}
    Button footerButton(String title,boolean primary,boolean destructive,Runnable run){
        Button button=new Button(activity);button.setText(title);button.setAllCaps(false);button.setSingleLine(true);button.setTextSize(13);button.setMinWidth(0);button.setMinimumWidth(0);button.setGravity(Gravity.CENTER);button.setPadding(dp(10),0,dp(10),0);button.setTextColor(primary?0xff15362c:destructive?0xffff9caa:Ui.TEXT);
        GradientDrawable bg=background(primary?(destructive?0xffffa7b3:Ui.ACCENT):0xff233141,dp(14));if(!primary)bg.setStroke(dp(1),0xff334355);button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33ffffff),bg,null));button.setOnClickListener(v->{if(run!=null)run.run();});
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(48),primary?1.4f:1);if(footer.getChildCount()>0)p.leftMargin=dp(9);footer.addView(button,p);footer.setVisibility(View.VISIBLE);return button;
    }
    void show(){if(activity.isFinishing()||activity.isDestroyed())return;dialog.show();dialog.getWindow().setLayout(-1,-2);box.setTranslationY(dp(24));box.setAlpha(0);box.animate().translationY(0).alpha(1).setDuration(180).start();}
    boolean showing(){return dialog.isShowing();}
    static void confirm(Activity activity,String title,String message,String action,boolean destructive,Runnable run){AppSheet sheet=new AppSheet(activity,title,message,null);sheet.footerButton("取消",false,()->sheet.dialog.dismiss());sheet.footerButton(action,true,destructive,()->{sheet.dialog.dismiss();run.run();});sheet.show();}
}
