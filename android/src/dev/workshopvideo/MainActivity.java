package dev.workshopvideo;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.text.InputType;
import android.util.LruCache;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import dev.workshopvideo.core.*;
import in.dragonbra.javasteam.steam.authentication.IAuthenticator;
import in.dragonbra.javasteam.protobufs.steamclient.SteammessagesPublishedfileSteamclient.PublishedFileDetails;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
    private static final int BG=0xff0b1018,PANEL=0xff151e2a,LINE=0xff293748,TEXT=0xffedf3f8,MUTED=0xff97a8ba,ACCENT=0xffa9efd5;
    private VideoApp app;
    private LinearLayout root,body,header,nav;
    private TextView account,count;
    private Button previousPage,nextPage,jumpPage;
    private boolean hasResults;
    private long total;
    private EditText search;
    private GridView grid;

    private final ArrayList<PublishedFileDetails> items=new ArrayList<>();
    private WorkshopQuery filters=new WorkshopQuery();
    private WorkshopQuery displayed=new WorkshopQuery();
    private long queryToken=0;
    private boolean querying=false,receiverRegistered=false,startupSearch=false;
    private int tab=0;
    private CardAdapter adapter;
    private SavedLibraryView library;
    private FavoritesView favorites;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final BroadcastReceiver updates=new BroadcastReceiver(){public void onReceive(Context c,Intent i){refreshStatus();if(tab!=0)renderJobs();maybeStartupSearch();}};

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);app=(VideoApp)getApplication();filters=app.loadFilters();
        root=column();root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xff0c1722,BG,0xff101727}));
        root.setOnApplyWindowInsetsListener((view,insets)->{
            view.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;
        });
        LinearLayout top=row();top.setPadding(dp(18),dp(12),dp(16),dp(12));top.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo=new ImageView(this);logo.setImageResource(R.drawable.ic_launcher);logo.setContentDescription("WallpaperMobile");
        top.addView(logo,new LinearLayout.LayoutParams(dp(38),dp(38)));
        LinearLayout identity=column();identity.setPadding(dp(12),0,0,0);TextView appTitle=text("WallpaperMobile",18,TEXT);appTitle.setSingleLine(true);appTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);identity.addView(appTitle);
        top.addView(identity,new LinearLayout.LayoutParams(0,-2,1));
        account=text("连接 Steam",12,ACCENT);account.setGravity(Gravity.CENTER_VERTICAL);account.setMinHeight(dp(44));account.setCompoundDrawablePadding(dp(6));account.setPadding(dp(12),dp(9),dp(12),dp(9));account.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x22a9efd5),shape(PANEL,18),null));account.setOnClickListener(v->accountMenu());top.addView(account);
        root.addView(top);
        body=column();root.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        nav=row();nav.setBackgroundColor(0xff101824);nav.setPadding(dp(10),dp(4),dp(10),dp(5));
        String[] labels={"发现","收藏","已保存"};
        for(int i=0;i<labels.length;i++){final int index=i;TextView b=text(labels[i],13,i==0?ACCENT:MUTED);b.setGravity(Gravity.CENTER);b.setPadding(0,dp(10),0,dp(10));b.setCompoundDrawablePadding(dp(4));b.setCompoundDrawablesWithIntrinsicBounds(null,new Ui.Icon(i==0?"discover":i==1?"heart":"library",i==0?ACCENT:MUTED,dp(22)),null,null);b.setOnClickListener(v->showTab(index));nav.addView(b,new LinearLayout.LayoutParams(0,-2,1));}
        root.addView(nav);setContentView(root);showTab(getIntent().getIntExtra("tab",0)==2?2:0);refreshStatus();
    }

    @Override protected void onStart(){super.onStart();if(Build.VERSION.SDK_INT>=33)registerReceiver(updates,new IntentFilter(VideoApp.UPDATE),Context.RECEIVER_NOT_EXPORTED);else registerReceiver(updates,new IntentFilter(VideoApp.UPDATE));receiverRegistered=true;refreshStatus();if(tab!=0)renderJobs();
        startupSearch=!hasResults&&tab==0;maybeStartupSearch();
    }
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);if(intent.getIntExtra("tab",0)==2)showTab(2);}
    @Override protected void onStop(){if(receiverRegistered){unregisterReceiver(updates);receiverRegistered=false;}super.onStop();}
    @Override protected void onDestroy(){queryToken++;if(library!=null)library.close();super.onDestroy();}
    @Override public void onBackPressed(){if(tab!=0)showTab(0);else super.onBackPressed();}
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private LinearLayout column(){LinearLayout x=new LinearLayout(this);x.setOrientation(LinearLayout.VERTICAL);return x;}
    private LinearLayout row(){LinearLayout x=new LinearLayout(this);x.setOrientation(LinearLayout.HORIZONTAL);return x;}
    private TextView text(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);return t;}
    private GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));d.setStroke(dp(1),LINE);return d;}
    private Button button(String label,boolean primary,Runnable click){Button b=new Button(this);b.setText(label);b.setTextSize(13);b.setAllCaps(false);b.setTextColor(primary?0xff173d31:TEXT);b.setBackground(shape(primary?ACCENT:PANEL,12));b.setMinHeight(dp(43));b.setPadding(dp(12),0,dp(12),0);b.setOnClickListener(v->click.run());return b;}
    private EditText input(String hint){EditText e=new EditText(this);e.setTextSize(14);e.setTextColor(TEXT);e.setHintTextColor(MUTED);e.setHint(hint);e.setSingleLine(true);e.setPadding(dp(13),dp(12),dp(13),dp(12));e.setBackground(shape(PANEL,12));return e;}
    private void gap(LinearLayout box,int height){View v=new View(this);box.addView(v,new LinearLayout.LayoutParams(1,dp(height)));}
    private void toast(String value){runOnUiThread(()->Toast.makeText(this,value,Toast.LENGTH_LONG).show());}

    private void showTab(int index){
        if(search!=null)filters.text=search.getText().toString();
        if(tab==2&&index!=2&&library!=null)library.leaveManagement();
        tab=index;body.removeAllViews();
        for(int i=0;i<nav.getChildCount();i++){TextView label=(TextView)nav.getChildAt(i);int color=i==index?ACCENT:MUTED;label.setTextColor(color);label.setCompoundDrawablesWithIntrinsicBounds(null,new Ui.Icon(i==0?"discover":i==1?"heart":"library",color,dp(22)),null,null);}
        if(index!=0){search=null;renderJobs();return;}
        header=column();header.setPadding(dp(18),dp(13),dp(18),dp(6));
        LinearLayout bar=row();search=input("搜索标题或关键词…");search.setText(filters.text);search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);search.setInputType(InputType.TYPE_CLASS_TEXT);
        search.setOnEditorActionListener((v,action,event)->{if(action==EditorInfo.IME_ACTION_SEARCH){search(true);return true;}return false;});
        bar.addView(search,new LinearLayout.LayoutParams(0,dp(45),1));
        Button go=button("搜索",true,()->search(true));LinearLayout.LayoutParams gp=new LinearLayout.LayoutParams(dp(67),dp(45));gp.leftMargin=dp(7);bar.addView(go,gp);
        Button filter=button("筛选",false,this::filterDialog);LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(dp(63),dp(45));fp.leftMargin=dp(7);bar.addView(filter,fp);header.addView(bar);
        HorizontalScrollView quick=new HorizontalScrollView(this);quick.setHorizontalScrollBarEnabled(false);LinearLayout chips=row();chips.setPadding(0,dp(12),0,dp(8));
        String[] names={"最热门（今年）","最近","评分最高","最多订阅"};int[] sorts={3,1,0,12};
        for(int i=0;i<names.length;i++){final int sort=sorts[i];Button b=button(names[i],filters.sort==sort&&(sort!=3||filters.days==365),()->{filters.sort=sort;if(sort==3)filters.days=365;try{app.saveFilters(filters);search(true);}catch(Exception e){toast(VideoApp.error(e));}});b.setTextSize(11);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,dp(34));p.rightMargin=dp(8);chips.addView(b,p);}
        quick.addView(chips);header.addView(quick);
        count=text(querying?"正在查询…":!hasResults?"连接 Steam 后，开始发现壁纸。":resultLabel(),11,MUTED);count.setPadding(0,dp(5),0,dp(9));header.addView(count);body.addView(header);
        grid=new GridView(this);grid.setNumColumns(2);grid.setHorizontalSpacing(dp(10));grid.setVerticalSpacing(dp(10));grid.setPadding(dp(18),dp(5),dp(18),dp(15));grid.setClipToPadding(false);grid.setSelector(android.R.color.transparent);
        adapter=new CardAdapter();grid.setAdapter(adapter);grid.setOnItemClickListener((p,v,pos,id)->detail(items.get(pos)));
        body.addView(grid,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout pager=row();pager.setGravity(Gravity.CENTER_VERTICAL);
        previousPage=button("上一页",false,()->goToPage(displayed.page-1));pager.addView(previousPage,new LinearLayout.LayoutParams(0,dp(44),1));
        jumpPage=button("",false,this::jumpToPage);jumpPage.setTextSize(11);jumpPage.setPadding(dp(4),0,dp(4),0);LinearLayout.LayoutParams jp=new LinearLayout.LayoutParams(0,dp(44),1.65f);jp.setMargins(dp(7),0,dp(7),0);pager.addView(jumpPage,jp);
        nextPage=button("下一页",false,()->goToPage(displayed.page+1));pager.addView(nextPage,new LinearLayout.LayoutParams(0,dp(44),1));
        LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,-2);pp.setMargins(dp(18),dp(3),dp(18),dp(8));body.addView(pager,pp);updatePagination();
    }
    private void maybeStartupSearch(){
        if(startupSearch&&!querying&&tab==0&&app.session!=null&&app.session.isAuthenticated()){startupSearch=false;search(true);}
    }
    private String resultLabel(){return "约 "+String.format(Locale.CHINA,"%,d",total)+" 个结果 · 第 "+displayed.page+" / "+WorkshopPages.count(total)+" 页"+(displayed.sort==3?" · "+displayed.days+" 天趋势":"")+(displayed.titleOnly?" · 本页按标题过滤":"");}
    private void refreshStatus(){
        if(account==null)return;
        boolean authenticated=app.session!=null&&app.session.isAuthenticated();
        account.setText(app.connecting?"连接中…":authenticated?app.guest?"公开浏览":"已登录":app.canRecover()?"恢复连接":"登录 Steam");
        account.setCompoundDrawablesRelativeWithIntrinsicBounds(new Ui.Icon("account",ACCENT,dp(17)),null,null,null);
        account.setContentDescription(account.getText()+"，"+app.connection+"，点击管理账号");
    }
    private void updatePagination(){
        if(tab!=0||previousPage==null)return;
        int pages=WorkshopPages.count(total);boolean ready=hasResults&&!querying;
        previousPage.setEnabled(ready&&displayed.page>1);nextPage.setEnabled(ready&&displayed.page<pages);jumpPage.setEnabled(ready);
        previousPage.setAlpha(previousPage.isEnabled()?1:.4f);nextPage.setAlpha(nextPage.isEnabled()?1:.4f);jumpPage.setAlpha(ready?1:.4f);
        jumpPage.setText((hasResults?displayed.page+" / "+pages+" 页":"页码")+"\n点击跳转");jumpPage.setContentDescription("当前第 "+displayed.page+" 页，共 "+pages+" 页，点击输入页码跳转");
    }
    private void jumpToPage(){
        if(querying||!hasResults)return;
        AppSheet sheet=new AppSheet(this,"跳转页码","当前第 "+displayed.page+" 页，共 "+WorkshopPages.count(total)+" 页",null);
        LinearLayout form=column();EditText page=input("输入 1–"+WorkshopPages.count(total)+" 的页码");page.setInputType(InputType.TYPE_CLASS_NUMBER);page.setImeOptions(EditorInfo.IME_ACTION_GO);page.setSelectAllOnFocus(true);page.setContentDescription("目标页码");page.setText(Integer.toString(displayed.page));form.addView(page,new LinearLayout.LayoutParams(-1,dp(46)));
        TextView error=text("",12,0xffff9caa);error.setPadding(0,dp(8),0,0);error.setVisibility(View.GONE);form.addView(error);sheet.field(form);
        Runnable submit=()->{try{int target=WorkshopPages.target(page.getText().toString(),total);sheet.dialog.dismiss();goToPage(target);}catch(IllegalArgumentException problem){error.setText(problem.getMessage());error.setVisibility(View.VISIBLE);}};
        sheet.footerButton("取消",false,()->sheet.dialog.dismiss());sheet.footerButton("跳转",true,submit);
        page.setOnEditorActionListener((v,action,event)->{if(action==EditorInfo.IME_ACTION_GO){submit.run();return true;}return false;});sheet.show();page.requestFocus();sheet.dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE|WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
    }
    private void goToPage(int page){
        if(querying||!hasResults)return;
        if(page<1||page>WorkshopPages.count(total)){toast("页码超出范围。");return;}
        if(page==displayed.page)return;
        WorkshopQuery query=displayed.copy();query.page=page;submitQuery(query);
    }
    private void search(boolean reset){
        if(!reset){goToPage(displayed.page+1);return;}
        if(search!=null)filters.text=search.getText().toString();
        WorkshopQuery query=filters.copy();query.page=1;submitQuery(query);
    }
    private void submitQuery(WorkshopQuery query){
        if(querying){toast("正在查询，请稍等。");return;}
        if(!app.canRecover()){loginDialog();return;}
        try{query.request();app.saveFilters(filters);}catch(Exception e){toast(VideoApp.error(e));return;}
        startupSearch=false;querying=true;long token=++queryToken;if(count!=null)count.setText("正在从 Steam 查询…");updatePagination();
        app.work.submit(()->{
            try{
                SteamSession session=app.requireSession();var response=session.query(query);
                ArrayList<PublishedFileDetails> found=new ArrayList<>();
                for(var item:response.getPublishedfiledetailsList())if(item.getResult()==1&&item.getConsumerAppid()==WorkshopQuery.APP_ID&&query.acceptsTitle(item.getTitle()))found.add(item);
                runOnUiThread(()->{if(isFinishing()||isDestroyed()||token!=queryToken)return;querying=false;hasResults=true;displayed=query;total=Integer.toUnsignedLong(response.getTotal());items.clear();items.addAll(found);
                    if(tab==0){showTab(0);if(items.isEmpty())count.setText(resultLabel()+" · 本页没有符合条件的作品。");}});
            }catch(Throwable e){runOnUiThread(()->{if(isFinishing()||isDestroyed()||token!=queryToken)return;querying=false;updatePagination();if(count!=null&&tab==0)count.setText(VideoApp.error(e));toast(VideoApp.error(e));});}
        });
    }

    private void accountMenu(){
        boolean authenticated=app.session!=null&&app.session.isAuthenticated();
        boolean signedIn=app.hasToken()||app.session!=null&&!app.guest;
        AccountMenu.show(this,authenticated?(app.guest?"公开浏览":"Steam 账号"):"WallpaperMobile",app.connection,signedIn,app.connecting,()->{if(app.connecting)toast(app.connection);else if(app.guest&&!app.hasToken())loginDialog();else if(app.canRecover())connect(null,null,true,false);else loginDialog();},this::leaveAccount,this::aboutDialog);
    }
    private void leaveAccount(){
        if(app.connecting){toast("请等待当前连接完成后再退出。");return;}
        if(!app.enqueued.isEmpty()){toast("请先暂停下载并等待任务停止。");return;}
        app.connecting=true;app.status("正在退出 Steam…");refreshStatus();
        app.work.submit(()->{try{synchronized(app.sessionLock){app.forget();SteamSession old=app.session;app.session=null;app.guest=true;if(old!=null)old.close();app.status("尚未连接 Steam");}}
            finally{app.connecting=false;app.changed();runOnUiThread(this::refreshStatus);}});
    }
    private void aboutDialog(){
        AppSheet sheet=new AppSheet(this,"WallpaperMobile","版本 0.5.4",null);
        TextView info=text("从 Steam 创意工坊搜索和下载视频、图片与可解析的场景，电脑无需开机。\n\n内容保存在应用内，不加入系统相册。在“已保存”单击播放，长按可收藏、删除或设为壁纸；下载进度也显示在这里。收藏独立于下载。卸载或清除应用数据会删除已下载内容。\n\n默认使用最近一年趋势排序；尚未验证与 WE 的完整结果顺序一致。\n\n开源依赖及许可见安装包 assets/THIRD_PARTY_NOTICES.txt。",13,MUTED);info.setLineSpacing(dp(4),1);sheet.field(info);
        sheet.footerButton("知道了",true,()->sheet.dialog.dismiss());sheet.show();
    }
    private void loginDialog(){
        if(app.connecting){toast("正在连接 Steam，请稍等。");return;}
        AppSheet sheet=new AppSheet(this,"登录 Steam","直接连接 Steam，密码仅用于本次认证。",null);
        EditText username=input("Steam 账号名");username.setText(app.lastUsername());username.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);username.setImeOptions(EditorInfo.IME_ACTION_NEXT);sheet.field(username);
        EditText password=input("密码");password.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);password.setSaveEnabled(false);password.setImeOptions(EditorInfo.IME_ACTION_DONE|EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);sheet.field(password);
        CheckBox remember=new CheckBox(this);remember.setText("在此手机记住登录状态");remember.setTextColor(TEXT);remember.setTextSize(13);remember.setChecked(true);remember.setMinHeight(dp(44));remember.setButtonTintList(new android.content.res.ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{ACCENT,MUTED}));sheet.field(remember);
        TextView note=text("如需验证，请在 Steam App 确认，或输入 Steam Guard 验证码。下载账号需拥有 Wallpaper Engine。",12,MUTED);note.setLineSpacing(dp(3),1);sheet.field(note);
        TextView error=text("",12,0xffff9caa);error.setVisibility(View.GONE);sheet.field(error);
        TextView guest=text("仅公开浏览  ›",13,ACCENT);guest.setGravity(Gravity.CENTER);guest.setMinHeight(dp(44));guest.setBackground(shape(0xff1d2938,12));guest.setOnClickListener(v->{sheet.dialog.dismiss();connect(null,null,false,true);});sheet.field(guest);
        Runnable submit=()->{
            String u=username.getText().toString().trim(),p=password.getText().toString();
            if(u.isEmpty()||p.isEmpty()){error.setText("请输入账号名和密码。");error.setVisibility(View.VISIBLE);return;}
            password.setText("");sheet.dialog.dismiss();connect(u,p,remember.isChecked(),false);
        };
        sheet.footerButton("取消",false,()->sheet.dialog.dismiss());sheet.footerButton("登录",true,submit);
        sheet.dialog.setOnDismissListener(d->password.setText(""));
        password.setOnEditorActionListener((v,action,event)->{if(action==EditorInfo.IME_ACTION_DONE){submit.run();return true;}return false;});sheet.show();
    }
    private void connect(String username,String password,boolean remember,boolean guest){
        if(app.connecting)return;if(!app.enqueued.isEmpty()&&(username!=null||guest)){toast("请先暂停当前下载。");return;}
        app.connecting=true;refreshStatus();app.work.submit(()->{
            SteamSession fresh=null;
            synchronized(app.sessionLock) {
            try{
                if(username==null&&!guest) {
                    app.requireSession();
                    runOnUiThread(()->{refreshStatus();if(tab==0)search(true);});return;
                }
                SteamSession old=app.session;app.session=null;if(old!=null)old.close();
                fresh=new SteamSession(app::status);
                if(guest)fresh.anonymous();
                else{
                    if(!remember)app.forget();
                    fresh.login(username,password,remember,new IAuthenticator(){
                        public CompletableFuture<String> getDeviceCode(boolean incorrect){return guardCode("Steam Guard 验证码",incorrect?"上个验证码不正确，请重新输入。":"输入 Steam App 显示的验证码。");}
                        public CompletableFuture<String> getEmailCode(String email,boolean incorrect){return guardCode("邮件验证码",(incorrect?"上个验证码不正确。":"")+"输入 Steam 发往 "+email+" 的验证码。");}
                        public CompletableFuture<Boolean> acceptDeviceConfirmation(){
                            CompletableFuture<Boolean> result=new CompletableFuture<>();runOnUiThread(()->{
                                if(isFinishing()){result.completeExceptionally(new CancellationException());return;}
                                AppSheet sheet=new AppSheet(MainActivity.this,"Steam 登录确认","请在官方 Steam App 中确认这次登录，也可以使用 Steam Guard 验证码。",null);
                                sheet.footerButton("输入验证码",false,()->{result.complete(false);sheet.dialog.dismiss();});
                                sheet.footerButton("已准备确认",true,()->{result.complete(true);sheet.dialog.dismiss();});
                                sheet.dialog.setOnDismissListener(d->result.completeExceptionally(new CancellationException()));sheet.show();
                            });return result;
                        }
                    },app::remember);
                }
                app.session=fresh;app.guest=guest;fresh=null;
                runOnUiThread(()->{refreshStatus();if(tab==0)search(true);});
            }catch(Throwable e){if(fresh!=null)fresh.close();String message=VideoApp.error(e);app.status(message);runOnUiThread(()->{refreshStatus();toast(message);if(tab==0&&count!=null)count.setText(message);});}
            finally {app.connecting=false;app.changed();}
            }
        });
    }
    private CompletableFuture<String> guardCode(String title,String message){
        CompletableFuture<String> response=new CompletableFuture<>();runOnUiThread(()->{
            if(isFinishing()||isDestroyed()){response.completeExceptionally(new CancellationException());return;}
            AppSheet sheet=new AppSheet(this,title,message,null);
            EditText code=input("验证码");code.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);code.setSaveEnabled(false);code.setImeOptions(EditorInfo.IME_ACTION_DONE|EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);sheet.field(code);
            TextView error=text("",12,0xffff9caa);error.setVisibility(View.GONE);sheet.field(error);
            Runnable submit=()->{String value=code.getText().toString().trim();if(value.isEmpty()){error.setText("请输入验证码。");error.setVisibility(View.VISIBLE);return;}response.complete(value);sheet.dialog.dismiss();};
            sheet.footerButton("取消",false,()->sheet.dialog.dismiss());sheet.footerButton("验证",true,submit);
            sheet.dialog.setOnDismissListener(d->{code.setText("");response.completeExceptionally(new CancellationException());});
            code.setOnEditorActionListener((v,action,event)->{if(action==EditorInfo.IME_ACTION_DONE){submit.run();return true;}return false;});sheet.show();code.requestFocus();sheet.dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE|WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        });return response;
    }

    private void filterDialog(){
        WorkshopQuery current=filters.copy();if(search!=null)current.text=search.getText().toString();
        new FilterPanel(this,current,selected->{try{app.saveFilters(selected);filters=selected;search(true);}catch(Exception e){toast(VideoApp.error(e));throw e;}}).show();
    }

    private class CardAdapter extends BaseAdapter {
        public int getCount(){return items.size();}public Object getItem(int p){return items.get(p);}public long getItemId(int p){return p;}
        public View getView(int position,View recycled,android.view.ViewGroup parent){
            PublishedFileDetails item=items.get(position);LinearLayout card=column();card.setBackground(shape(PANEL,16));card.setClipToOutline(true);card.setOnClickListener(v->detail(item));
            ImageView image=new PreviewImageView(MainActivity.this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setBackgroundColor(0xff233344);int width=(getResources().getDisplayMetrics().widthPixels-dp(46))/2;FrameLayout cover=new FrameLayout(MainActivity.this);cover.addView(image,new FrameLayout.LayoutParams(-1,-1));
            ImageView heart=new ImageView(MainActivity.this);String itemId=Long.toUnsignedString(item.getPublishedfileid());heart.setImageDrawable(new Ui.Icon(app.isFavorite(itemId)?"heartFilled":"heart",ACCENT,dp(22)));heart.setBackground(shape(0xc90b141e,18));heart.setPadding(dp(9),dp(9),dp(9),dp(9));heart.setContentDescription(app.isFavorite(itemId)?"取消收藏":"收藏");
            FrameLayout.LayoutParams hp=new FrameLayout.LayoutParams(dp(40),dp(40),Gravity.TOP|Gravity.RIGHT);hp.setMargins(dp(6),dp(6),dp(6),dp(6));cover.addView(heart,hp);heart.setOnClickListener(v->{try{app.toggleFavorite(favoriteItem(item));}catch(Exception e){toast(VideoApp.error(e));}heart.setImageDrawable(new Ui.Icon(app.isFavorite(itemId)?"heartFilled":"heart",ACCENT,dp(22)));heart.setContentDescription(app.isFavorite(itemId)?"取消收藏":"收藏");});
            card.addView(cover,new LinearLayout.LayoutParams(-1,Math.max(dp(100),width*10/16)));loadPreview(image,item.getPreviewUrl());
            LinearLayout meta=column();meta.setPadding(dp(11),dp(10),dp(11),dp(12));TextView title=text(item.getTitle(),13,TEXT);Ui.cardTitle(title);meta.addView(title);gap(meta,8);
            TextView info=text(bytes(item.getFileSize())+" · "+date(item.getTimeCreated()),10,MUTED);info.setSingleLine(true);info.setEllipsize(android.text.TextUtils.TruncateAt.END);meta.addView(info);card.addView(meta);return card;
        }
    }
    private JSONObject favoriteItem(PublishedFileDetails item){
        JSONObject value=new JSONObject();VideoApp.put(value,"itemId",Long.toUnsignedString(item.getPublishedfileid()));VideoApp.put(value,"title",item.getTitle());VideoApp.put(value,"previewUrl",item.getPreviewUrl());VideoApp.put(value,"description",item.getShortDescription());VideoApp.put(value,"video",item.getTagsList().stream().anyMatch(t->t.getTag().equalsIgnoreCase("Video")));for(String type:new String[]{"Video","Scene","Web","Application"})if(item.getTagsList().stream().anyMatch(t->t.getTag().equalsIgnoreCase(type))){VideoApp.put(value,"type",type);break;}return value;
    }
    private void toggleFavorite(JSONObject item){try{app.toggleFavorite(item);if(adapter!=null)adapter.notifyDataSetChanged();}catch(Exception e){toast(VideoApp.error(e));}}
    private void favoriteDetail(JSONObject item){
        if(item.optString("itemId").startsWith("local:")){for(JSONObject record:app.jobs())if(record.optString("itemId").equals(item.optString("itemId"))){openVideo(record);return;}toast("此本地内容已删除，收藏记录仍保留。");return;}
        showDetail(item,"已收藏的工坊作品",null,item.optString("description","暂无介绍"),item.optBoolean("video",true));
    }
    private void loadPreview(ImageView image,String url){app.previews.load(image,url);}
    private void detail(PublishedFileDetails item){
        String description=item.getFileDescription().isEmpty()?item.getShortDescription():item.getFileDescription();
        String tags=String.join(" · ",item.getTagsList().stream().map(t->t.getTag()).toArray(String[]::new));
        showDetail(favoriteItem(item),bytes(item.getFileSize())+" · 发布 "+date(item.getTimeCreated())+" · 更新 "+date(item.getTimeUpdated()),tags,description,item.getTagsList().stream().anyMatch(t->t.getTag().equalsIgnoreCase("Video")));
    }
    private void showDetail(JSONObject item,String metadata,String tags,String description,boolean video){
        AppSheet sheet=new AppSheet(this,"作品详情",null,null);
        ImageView image=new PreviewImageView(this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setBackground(shape(0xff233344,16));image.setClipToOutline(true);image.setContentDescription(item.optString("title")+"的预览");
        LinearLayout cover=column();cover.addView(image,new LinearLayout.LayoutParams(-1,dp(200)));sheet.field(cover);loadPreview(image,item.optString("previewUrl"));
        TextView title=text(item.optString("title"),20,TEXT);title.setTypeface(null,Typeface.BOLD);title.setTextIsSelectable(true);sheet.field(title);sheet.field(text(metadata,11,MUTED));
        if(tags!=null&&!tags.isEmpty()){TextView tagText=text(tags,11,ACCENT);tagText.setLineSpacing(dp(3),1);sheet.field(tagText);}
        TextView info=text(description==null||description.isEmpty()?"暂无介绍":description,13,MUTED);info.setLineSpacing(dp(4),1);info.setTextIsSelectable(true);sheet.field(info);
        String itemId=item.optString("itemId");
        Button favorite=sheet.footerButton(app.isFavorite(itemId)?"取消收藏":"收藏",false,()->{});
        Runnable updateFavorite=()->{boolean selected=app.isFavorite(itemId);favorite.setText(selected?"取消收藏":"收藏");favorite.setCompoundDrawablePadding(dp(5));favorite.setCompoundDrawablesRelativeWithIntrinsicBounds(new Ui.Icon(selected?"heartFilled":"heart",ACCENT,dp(17)),null,null,null);};
        favorite.setOnClickListener(v->{toggleFavorite(item);updateFavorite.run();});updateFavorite.run();
        String type=item.optString("type",video?"Video":"Scene");boolean supported=type.equalsIgnoreCase("Video")||type.equalsIgnoreCase("Scene")||type.equalsIgnoreCase("Image");
        if(!video){TextView compatibility=text(supported?"下载后自动适配为可用壁纸，仅保存适配结果；部分原作特效可能不保留，无法适配则不保存。":"此作品需要当前尚不支持的网页或应用运行环境。",12,MUTED);compatibility.setLineSpacing(dp(3),1);sheet.field(compatibility);}
        if(video)sheet.action("play","在线播放","边缓冲边播放，不加入已保存",false,()->{if(!app.canRecover()||app.guest){toast("在线播放需要登录拥有 Wallpaper Engine 的 Steam 账号。");loginDialog();return;}startActivity(new Intent(this,VideoPlayerActivity.class).putExtra("workshopId",itemId).putExtra("title",item.optString("title")));});
        Button download=sheet.footerButton(supported?(video?"下载视频":"下载资源"):"暂不支持此类型",true,()->{
            sheet.dialog.dismiss();
            if(!app.canRecover()||app.guest){toast("下载需要登录拥有 Wallpaper Engine 的 Steam 账号。");loginDialog();return;}
            JSONObject record=app.create(itemId,item.optString("title"),item.optString("previewUrl"));if(record.optString("state").equals("saved")){openVideo(record);return;}VideoApp.put(record,"type",type);VideoApp.put(record,"kind",video?"video":"scene");app.save(record);startDownload(record.optString("id"));showTab(2);if(library!=null)library.showCategory(!video);
        });download.setEnabled(supported);download.setAlpha(supported?1:.45f);sheet.show();
    }
    private void startDownload(String id){
        if(!app.canRecover()||(app.session!=null&&app.guest)){toast("请先登录 Steam 账号。");loginDialog();return;}
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},11);
        app.change(id,"cancelRequested",false);app.change(id,"discardRequested",false);
        try{startForegroundService(new Intent(this,VideoDownloadService.class).putExtra("id",id));}catch(Exception e){toast(VideoApp.error(e));}
    }
    private void renderJobs(){
        if(tab==0)return;
        if(tab==2){
            if(library==null)library=new SavedLibraryView(this,app,this::openVideo,this::startDownload);
            if(library.getParent()!=body){body.removeAllViews();body.addView(library,new LinearLayout.LayoutParams(-1,-1));}
            library.refresh();return;
        }
        if(favorites==null)favorites=new FavoritesView(this,app,this::favoriteDetail);
        if(favorites.getParent()!=body){body.removeAllViews();body.addView(favorites,new LinearLayout.LayoutParams(-1,-1));}
        favorites.refresh();
    }
    private void openVideo(JSONObject job){try{if(job.optBoolean("officialPackage")){SceneAdaptation.open(this,app,job);return;}if(!job.optString("kind","video").equals("video")){if(!job.optBoolean("compatible",true)){if(job.optString("sourceType").equals("scene")||job.optString("kind").equals("package")){SceneAdaptation.open(this,app,job);return;}AppSheet sheet=new AppSheet(this,job.optString("title"),"此内容无法用作手机壁纸",null);TextView reason=text(job.optString("compatibilityReason","此内容尚不兼容"),13,MUTED);sheet.field(reason);sheet.footerButton("关闭",false,()->sheet.dialog.dismiss());sheet.show();return;}startActivity(new Intent(this,WallpaperSetupActivity.class).putExtra("id",job.getString("id")));return;}startActivity(new Intent(this,VideoPlayerActivity.class).putExtra("id",job.getString("id")));}catch(Exception e){toast(VideoApp.error(e));}}
    private static String bytes(long n){if(n<=0)return "大小未知";String[] units={"B","KB","MB","GB"};int i=Math.min(3,(int)(Math.log(n)/Math.log(1024)));return String.format(Locale.CHINA,i>1?"%.1f %s":"%.0f %s",n/Math.pow(1024,i),units[i]);}
    private static String date(int n){return new java.text.SimpleDateFormat("yyyy-MM-dd",Locale.CHINA).format(new Date(Integer.toUnsignedLong(n)*1000));}
}
