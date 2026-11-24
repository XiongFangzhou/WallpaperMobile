package dev.workshopvideo;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.RippleDrawable;
import android.os.*;
import android.text.InputType;
import android.text.TextUtils.TruncateAt;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import dev.workshopvideo.core.*;
import in.dragonbra.javasteam.steam.authentication.IAuthenticator;
import in.dragonbra.javasteam.protobufs.steamclient.SteammessagesPublishedfileSteamclient.PublishedFileDetails;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
    private static final int TEXT=Ui.TEXT,MUTED=Ui.MUTED,ACCENT=Ui.ACCENT;
    private static final String[] TABS={"发现","收藏","已保存"},TAB_ICONS={"discover","heart","library"};
    private static final String[] QUICK_NAMES={"今年热门","最新发布","评分最高","订阅最多"};
    private static final int[] QUICK_SORTS={3,1,0,12};
    private static final String[] TYPES={"Video","Scene","Web","Application"},TYPE_NAMES={"视频","场景","网页","应用"};
    private VideoApp app;
    private LinearLayout root,body,nav,discover,discoverEmpty,emptyActions,loading;
    private TextView account,pageTitle,count,loadingText,pageLabel;
    private ImageView filterButton,previousPage,nextPage;
    private LinearLayout pager;
    private ProgressBar refreshing;
    private final ArrayList<TextView> chips=new ArrayList<>();
    private boolean hasResults,stale;
    private String lastError;
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
    // Download progress broadcasts arrive several times a second; coalesce them into one refresh.
    private final Runnable refreshViews=()->{refreshStatus();if(tab!=0)renderJobs();else renderResults();maybeStartupSearch();};
    private final BroadcastReceiver updates=new BroadcastReceiver(){public void onReceive(Context c,Intent i){main.removeCallbacks(refreshViews);main.postDelayed(refreshViews,120);}};

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);app=(VideoApp)getApplication();filters=app.loadFilters();
        root=column();root.setBackgroundColor(Ui.BG);
        root.setOnApplyWindowInsetsListener((view,insets)->{
            view.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;
        });
        LinearLayout top=row();top.setPadding(dp(18),dp(12),dp(14),dp(10));top.setGravity(Gravity.CENTER_VERTICAL);
        pageTitle=Ui.heading(this,TABS[0],24);pageTitle.setSingleLine(true);top.addView(pageTitle,new LinearLayout.LayoutParams(0,-2,1));
        account=text("登录",13,ACCENT);account.setGravity(Gravity.CENTER_VERTICAL);account.setMinHeight(dp(40));account.setCompoundDrawablePadding(dp(6));account.setPadding(dp(12),0,dp(14),0);
        account.setBackground(Ui.pressable(Ui.outlined(Ui.PANEL,Ui.LINE,dp(20),dp(1)),0x22a9efd5,dp(20)));account.setOnClickListener(v->accountMenu());top.addView(account);
        root.addView(top);
        body=column();root.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        View divider=new View(this);divider.setBackgroundColor(Ui.LINE);root.addView(divider,new LinearLayout.LayoutParams(-1,1));
        nav=row();nav.setPadding(dp(8),dp(6),dp(8),dp(6));
        for(int i=0;i<TABS.length;i++){
            final int index=i;LinearLayout item=column();item.setGravity(Gravity.CENTER_HORIZONTAL);item.setPadding(0,dp(4),0,dp(4));item.setBackground(new RippleDrawable(ColorStateList.valueOf(0x14ffffff),null,null));item.setOnClickListener(v->showTab(index));
            ImageView icon=new ImageView(this);icon.setScaleType(ImageView.ScaleType.CENTER);item.addView(icon,new LinearLayout.LayoutParams(dp(60),dp(30)));
            TextView label=text(TABS[i],12,MUTED);label.setGravity(Gravity.CENTER);label.setPadding(0,dp(4),0,0);item.addView(label);
            nav.addView(item,new LinearLayout.LayoutParams(0,-2,1));
        }
        root.addView(nav);setContentView(root);
        restoreSnapshot();showTab(getIntent().getIntExtra("tab",0)==2?2:0);refreshStatus();
    }

    @Override protected void onStart(){super.onStart();if(Build.VERSION.SDK_INT>=33)registerReceiver(updates,new IntentFilter(VideoApp.UPDATE),Context.RECEIVER_NOT_EXPORTED);else registerReceiver(updates,new IntentFilter(VideoApp.UPDATE));receiverRegistered=true;refreshStatus();if(tab!=0)renderJobs();
        startupSearch=(!hasResults||stale)&&tab==0;maybeStartupSearch();
    }
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);if(intent.getIntExtra("tab",0)==2)showTab(2);}
    @Override protected void onStop(){if(receiverRegistered){unregisterReceiver(updates);receiverRegistered=false;}main.removeCallbacks(refreshViews);super.onStop();}
    @Override protected void onDestroy(){queryToken++;main.removeCallbacksAndMessages(null);if(library!=null)library.close();super.onDestroy();}
    @Override public void onBackPressed(){if(tab!=0)showTab(0);else super.onBackPressed();}
    private int dp(float n){return Ui.dp(this,n);}
    private LinearLayout column(){LinearLayout x=new LinearLayout(this);x.setOrientation(LinearLayout.VERTICAL);return x;}
    private LinearLayout row(){LinearLayout x=new LinearLayout(this);x.setOrientation(LinearLayout.HORIZONTAL);return x;}
    private TextView text(String s,int size,int color){return Ui.text(this,s,size,color);}
    private EditText input(String hint){return Ui.input(this,hint);}
    private void toast(String value){runOnUiThread(()->Toast.makeText(this,value,Toast.LENGTH_LONG).show());}

    private void showTab(int index){
        if(search!=null)filters.text=search.getText().toString();
        if(tab==2&&index!=2&&library!=null)library.leaveManagement();
        tab=index;updateNav();pageTitle.setText(TABS[index]);
        if(index!=0){renderJobs();return;}
        // Built once: results arriving later must not recreate the search field or lose its focus.
        if(discover==null)discover=buildDiscover();
        if(discover.getParent()!=body){body.removeAllViews();body.addView(discover,new LinearLayout.LayoutParams(-1,-1));}
        updateChips();renderResults();
    }
    private void updateNav(){
        for(int i=0;i<nav.getChildCount();i++){
            boolean active=i==tab;LinearLayout item=(LinearLayout)nav.getChildAt(i);ImageView icon=(ImageView)item.getChildAt(0);TextView label=(TextView)item.getChildAt(1);
            icon.setImageDrawable(new Ui.Icon(active&&i==1?"heartFilled":TAB_ICONS[i],active?ACCENT:MUTED,dp(22)));icon.setBackground(active?Ui.round(Ui.ACCENT_SOFT,dp(15)):null);
            label.setTextColor(active?ACCENT:MUTED);label.setTypeface(null,active?Typeface.BOLD:Typeface.NORMAL);item.setSelected(active);item.setContentDescription(TABS[i]+(active?"，当前页面":""));
        }
    }
    private LinearLayout buildDiscover(){
        LinearLayout page=column(),header=column();header.setPadding(dp(18),0,dp(18),0);
        LinearLayout bar=row();bar.setGravity(Gravity.CENTER_VERTICAL);
        search=input("搜索壁纸标题或关键词");search.setText(filters.text);search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);search.setInputType(InputType.TYPE_CLASS_TEXT);
        search.setCompoundDrawablesRelativeWithIntrinsicBounds(new Ui.Icon("search",Ui.SUBTLE,dp(18)),null,null,null);search.setCompoundDrawablePadding(dp(10));
        search.setOnEditorActionListener((v,action,event)->{if(action==EditorInfo.IME_ACTION_SEARCH||action==EditorInfo.IME_ACTION_DONE){hideKeyboard();search(true);return true;}return false;});
        bar.addView(search,new LinearLayout.LayoutParams(0,dp(48),1));
        filterButton=Ui.iconButton(this,"filter","筛选",this::filterDialog);LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(dp(48),dp(48));fp.leftMargin=dp(10);bar.addView(filterButton,fp);header.addView(bar);
        HorizontalScrollView quick=new HorizontalScrollView(this);quick.setHorizontalScrollBarEnabled(false);quick.setClipToPadding(false);LinearLayout row=row();row.setPadding(0,dp(12),0,dp(2));chips.clear();
        for(int i=0;i<QUICK_NAMES.length;i++){
            final int sort=QUICK_SORTS[i];TextView chip=Ui.chip(this,QUICK_NAMES[i],false,()->{filters.sort=sort;if(sort==3)filters.days=365;try{app.saveFilters(filters);updateChips();search(true);}catch(Exception e){toast(VideoApp.error(e));}});
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,dp(34));p.rightMargin=dp(8);row.addView(chip,p);chips.add(chip);
        }
        quick.addView(row);header.addView(quick);
        LinearLayout status=row();status.setGravity(Gravity.CENTER_VERTICAL);status.setMinimumHeight(dp(36));
        count=text("",12,MUTED);count.setSingleLine(true);count.setEllipsize(TruncateAt.END);status.addView(count,new LinearLayout.LayoutParams(0,-2,1));
        refreshing=new ProgressBar(this,null,android.R.attr.progressBarStyleSmall);refreshing.setIndeterminateTintList(ColorStateList.valueOf(ACCENT));status.addView(refreshing,new LinearLayout.LayoutParams(dp(16),dp(16)));
        header.addView(status);page.addView(header);
        FrameLayout results=new FrameLayout(this);
        grid=new GridView(this);grid.setNumColumns(2);grid.setHorizontalSpacing(dp(12));grid.setVerticalSpacing(dp(12));grid.setPadding(dp(18),dp(2),dp(18),dp(16));grid.setClipToPadding(false);grid.setSelector(android.R.color.transparent);grid.setVerticalScrollBarEnabled(false);
        adapter=new CardAdapter();grid.setAdapter(adapter);results.addView(grid,new FrameLayout.LayoutParams(-1,-1));
        loading=column();loading.setGravity(Gravity.CENTER_HORIZONTAL);loading.setPadding(0,dp(96),0,0);loading.addView(Ui.spinner(this),new LinearLayout.LayoutParams(dp(36),dp(36)));
        loadingText=text("",13,MUTED);loadingText.setPadding(0,dp(14),0,0);loading.addView(loadingText);results.addView(loading,new FrameLayout.LayoutParams(-1,-2));
        discoverEmpty=Ui.empty(this,"discover","","");emptyActions=column();emptyActions.setGravity(Gravity.CENTER_HORIZONTAL);emptyActions.setPadding(0,dp(22),0,0);discoverEmpty.addView(emptyActions,new LinearLayout.LayoutParams(-1,-2));
        ScrollView emptyScroll=new ScrollView(this);emptyScroll.setFillViewport(true);emptyScroll.addView(discoverEmpty);results.addView(emptyScroll,new FrameLayout.LayoutParams(-1,-1));
        page.addView(results,new LinearLayout.LayoutParams(-1,0,1));
        pager=row();pager.setGravity(Gravity.CENTER_VERTICAL);pager.setPadding(dp(18),dp(6),dp(18),dp(10));
        previousPage=Ui.iconButton(this,"previous","上一页",()->goToPage(displayed.page-1));pager.addView(previousPage,new LinearLayout.LayoutParams(dp(46),dp(46)));
        pageLabel=text("",13,TEXT);pageLabel.setGravity(Gravity.CENTER);pageLabel.setTypeface(null,Typeface.BOLD);pageLabel.setBackground(Ui.pressable(Ui.outlined(Ui.RAISED,Ui.LINE,dp(14),dp(1)),0x22ffffff,dp(14)));pageLabel.setOnClickListener(v->jumpToPage());
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(46),1);lp.setMargins(dp(10),0,dp(10),0);pager.addView(pageLabel,lp);
        nextPage=Ui.iconButton(this,"next","下一页",()->goToPage(displayed.page+1));pager.addView(nextPage,new LinearLayout.LayoutParams(dp(46),dp(46)));
        page.addView(pager,new LinearLayout.LayoutParams(-1,-2));
        return page;
    }
    private void hideKeyboard(){if(search!=null){search.clearFocus();getSystemService(android.view.inputmethod.InputMethodManager.class).hideSoftInputFromWindow(search.getWindowToken(),0);}}
    private void updateChips(){
        for(int i=0;i<chips.size();i++){int sort=QUICK_SORTS[i];Ui.select(chips.get(i),filters.sort==sort&&(sort!=3||filters.days==365));}
        if(filterButton!=null){boolean active=filtersActive();float radius=dp(14);filterButton.setImageDrawable(new Ui.Icon("filter",active?ACCENT:TEXT,dp(20)));
            filterButton.setBackground(Ui.pressable(Ui.outlined(active?Ui.ACCENT_SOFT:Ui.RAISED,active?0x66a9efd5:Ui.LINE,radius,dp(1)),0x22ffffff,radius));filterButton.setContentDescription(active?"筛选，已启用筛选条件":"筛选");}
    }
    /** True when anything besides the search text and sort differs from the default filters. */
    private boolean filtersActive(){
        try{WorkshopQuery base=WeFilters.defaults(),current=filters.copy();base.text=current.text;base.sort=current.sort;base.days=current.days;base.page=current.page=1;return !base.cacheKey().equals(current.cacheKey());}
        catch(RuntimeException invalid){return false;}
    }
    private void renderResults(){
        if(adapter==null)return;adapter.notifyDataSetChanged();
        boolean empty=items.isEmpty(),signedOut=!app.canRecover();
        String waiting=!empty?null:querying?"正在加载作品…":app.connecting?"正在连接 Steam…":lastError==null&&!signedOut&&!hasResults?"正在连接 Steam…":null;
        loading.setVisibility(waiting!=null?View.VISIBLE:View.GONE);if(waiting!=null)loadingText.setText(waiting);
        ((View)discoverEmpty.getParent()).setVisibility(empty&&waiting==null?View.VISIBLE:View.GONE);emptyActions.removeAllViews();
        if(empty&&waiting==null){
            if(lastError!=null){Ui.empty(discoverEmpty,"暂时无法加载",lastError);emptyAction("重试",Ui.Kind.PRIMARY,()->search(true));}
            else if(signedOut){Ui.empty(discoverEmpty,"登录后开始发现壁纸","也可以先公开浏览热门作品。\n下载和在线播放需要拥有 Wallpaper Engine 的 Steam 账号。");emptyAction("登录 Steam",Ui.Kind.PRIMARY,this::loginDialog);emptyAction("先公开浏览",Ui.Kind.GHOST,()->connect(null,null,false,true));}
            else{Ui.empty(discoverEmpty,"没有找到作品","换个关键词，或在筛选中放宽条件。");emptyAction("调整筛选",Ui.Kind.SECONDARY,this::filterDialog);}
        }
        grid.setVisibility(empty?View.GONE:View.VISIBLE);grid.setAlpha(querying&&!empty?.55f:1);refreshing.setVisibility((querying||stale)&&!empty?View.VISIBLE:View.GONE);
        count.setText(!hasResults?"":resultLabel());updatePagination();
    }
    private void emptyAction(String label,Ui.Kind kind,Runnable action){Button button=Ui.button(this,label,kind,action);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(220),dp(46));if(emptyActions.getChildCount()>0)p.topMargin=dp(8);emptyActions.addView(button,p);}
    private void maybeStartupSearch(){
        if(startupSearch&&!querying&&tab==0&&app.session!=null&&app.session.isAuthenticated()){startupSearch=false;search(true);}
    }
    private String resultLabel(){
        String label="共 "+String.format(Locale.CHINA,"%,d",total)+" 个作品";
        if(stale)return label+" · 显示上次的结果，正在刷新";
        if(filtersActive())label+=" · 已筛选";if(displayed.titleOnly)label+=" · 仅匹配本页标题";return label;
    }
    private void refreshStatus(){
        if(account==null)return;
        boolean authenticated=app.session!=null&&app.session.isAuthenticated();
        account.setText(app.connecting?"连接中…":authenticated?app.guest?"公开浏览":"已登录":app.canRecover()?"离线":"登录");
        account.setCompoundDrawablesRelativeWithIntrinsicBounds(new Ui.Icon("account",ACCENT,dp(17)),null,null,null);
        account.setContentDescription("账号："+account.getText()+"，"+app.connection+"，点击管理账号");
    }
    private void updatePagination(){
        if(pager==null)return;
        int pages=WorkshopPages.count(total);boolean ready=hasResults&&!querying&&!stale;
        pager.setVisibility(hasResults&&pages>1&&!items.isEmpty()?View.VISIBLE:View.GONE);
        Ui.enable(previousPage,ready&&displayed.page>1);Ui.enable(nextPage,ready&&displayed.page<pages);Ui.enable(pageLabel,ready);
        pageLabel.setText("第 "+displayed.page+" / "+String.format(Locale.CHINA,"%,d",pages)+" 页");pageLabel.setContentDescription("当前第 "+displayed.page+" 页，共 "+pages+" 页，点击跳到指定页");
    }
    private void jumpToPage(){
        if(querying||!hasResults)return;int pages=WorkshopPages.count(total);
        AppSheet sheet=new AppSheet(this,"跳到指定页","共 "+String.format(Locale.CHINA,"%,d",pages)+" 页，当前第 "+displayed.page+" 页",null);
        LinearLayout form=column();EditText page=input("输入 1–"+pages);page.setInputType(InputType.TYPE_CLASS_NUMBER);page.setImeOptions(EditorInfo.IME_ACTION_GO);page.setSelectAllOnFocus(true);page.setContentDescription("目标页码");page.setText(Integer.toString(displayed.page));form.addView(page,new LinearLayout.LayoutParams(-1,dp(48)));
        TextView error=text("",12,Ui.DANGER);error.setPadding(0,dp(8),0,0);error.setVisibility(View.GONE);form.addView(error);sheet.field(form);
        Runnable submit=()->{try{int target=WorkshopPages.target(page.getText().toString(),total);sheet.dialog.dismiss();goToPage(target);}catch(IllegalArgumentException problem){error.setText(problem.getMessage());error.setVisibility(View.VISIBLE);}};
        sheet.footerButton("取消",false,()->sheet.dialog.dismiss());sheet.footerButton("跳转",true,submit);
        page.setOnEditorActionListener((v,action,event)->{if(action==EditorInfo.IME_ACTION_GO){submit.run();return true;}return false;});sheet.show();page.requestFocus();sheet.dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE|WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
    }
    private void goToPage(int page){
        if(!hasResults)return;
        if(page<1||page>WorkshopPages.count(total)){toast("页码超出范围。");return;}
        if(page==displayed.page)return;
        WorkshopQuery query=displayed.copy();query.page=page;submitQuery(query);
    }
    private void search(boolean reset){
        if(!reset){goToPage(displayed.page+1);return;}
        if(search!=null)filters.text=search.getText().toString();
        WorkshopQuery query=filters.copy();query.page=1;submitQuery(query);
    }
    /** The last first page is shown at once on launch; a fresh query replaces it when Steam is ready. */
    private void restoreSnapshot(){
        try{WorkshopQuery first=filters.copy();first.page=1;DiscoverResults.Page saved=app.results.restore(first);if(saved==null)return;
            items.addAll(saved.items());total=saved.total();displayed=first;hasResults=true;stale=true;}catch(RuntimeException ignored){}
    }
    private void submitQuery(WorkshopQuery query){
        if(!app.canRecover()){loginDialog();return;}
        try{query.request();app.saveFilters(filters);}catch(Exception e){toast(VideoApp.error(e));return;}
        startupSearch=false;long token=++queryToken;
        DiscoverResults.Page hit=app.results.cached(query);
        if(hit!=null){querying=false;show(query,hit);return;}
        querying=true;lastError=null;updateChips();renderResults();
        // A newer request supersedes this one: its result is ignored through the token check.
        app.work.submit(()->{
            if(token!=queryToken)return;
            try{
                SteamSession session=app.requireSession();DiscoverResults.Page page=app.results.fetch(session,query);if(query.page==1)app.results.save(page);
                runOnUiThread(()->{if(isFinishing()||isDestroyed()||token!=queryToken)return;querying=false;show(query,page);});
            }catch(Throwable e){runOnUiThread(()->{if(isFinishing()||isDestroyed()||token!=queryToken)return;querying=false;lastError=VideoApp.error(e);if(!items.isEmpty())toast(lastError);if(stale){stale=false;lastError=null;}renderResults();});}
        });
    }
    private void show(WorkshopQuery query,DiscoverResults.Page page){
        stale=false;lastError=null;hasResults=true;displayed=query;total=page.total();items.clear();items.addAll(page.items());
        if(grid!=null)grid.setSelection(0);updateChips();renderResults();
        if(query.page<WorkshopPages.count(total)){WorkshopQuery next=query.copy();next.page=query.page+1;app.results.prefetch(app.work,app,next);}
    }

    private void accountMenu(){
        boolean authenticated=app.session!=null&&app.session.isAuthenticated();
        boolean signedIn=app.hasToken()||app.session!=null&&!app.guest;
        AccountMenu.show(this,authenticated?(app.guest?"公开浏览中":"Steam 账号"):"WallpaperMobile",app.connection,signedIn,app.connecting,()->{if(app.connecting)toast(app.connection);else if(app.guest&&!app.hasToken())loginDialog();else if(app.canRecover())connect(null,null,true,false);else loginDialog();},this::leaveAccount,this::aboutDialog);
    }
    private void leaveAccount(){
        if(app.connecting){toast("请等待当前连接完成后再退出。");return;}
        if(!app.enqueued.isEmpty()){toast("请先暂停下载并等待任务停止。");return;}
        app.connecting=true;app.status("正在退出 Steam…");refreshStatus();
        app.work.submit(()->{try{synchronized(app.sessionLock){app.forget();SteamSession old=app.session;app.session=null;app.guest=true;if(old!=null)old.close();app.status("尚未连接 Steam");}}
            finally{app.connecting=false;app.changed();runOnUiThread(()->{refreshStatus();renderResults();});}});
    }
    private void aboutDialog(){
        AppSheet sheet=new AppSheet(this,"WallpaperMobile","版本 0.5.10",null);
        TextView info=text("在手机上搜索、收藏和下载 Steam 创意工坊的壁纸，不需要电脑。\n\n· 下载的内容只保存在本应用内，不会出现在系统相册。\n· 在「已保存」中单击播放或预览；长按可设为壁纸、收藏或删除。\n· 卸载应用或清除应用数据会删除已下载的内容。\n\n开源组件及许可见安装包内 assets/THIRD_PARTY_NOTICES.txt。",13,MUTED);info.setLineSpacing(dp(5),1);sheet.field(info);
        sheet.footerButton("知道了",true,()->sheet.dialog.dismiss());sheet.show();
    }
    private void loginDialog(){
        if(app.connecting){toast("正在连接 Steam，请稍等。");return;}
        AppSheet sheet=new AppSheet(this,"登录 Steam","下载和在线播放需要拥有 Wallpaper Engine 的账号。",null);
        EditText username=input("Steam 账号名");username.setText(app.lastUsername());username.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);username.setTypeface(Typeface.DEFAULT);username.setImeOptions(EditorInfo.IME_ACTION_NEXT);sheet.field(username);
        EditText password=input("密码");password.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);password.setTypeface(Typeface.DEFAULT);password.setSaveEnabled(false);password.setImeOptions(EditorInfo.IME_ACTION_DONE|EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);sheet.field(password);
        CheckBox remember=new CheckBox(this);remember.setText("在这台手机上保持登录");remember.setTextColor(TEXT);remember.setTextSize(14);remember.setChecked(true);remember.setMinHeight(dp(44));remember.setButtonTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{ACCENT,MUTED}));sheet.field(remember);
        TextView error=text("",13,Ui.DANGER);error.setVisibility(View.GONE);sheet.field(error);
        TextView note=text("密码只发送给 Steam，不会保存在手机上。需要验证时，在 Steam App 中确认或输入验证码即可。",12,Ui.SUBTLE);note.setLineSpacing(dp(3),1);sheet.field(note);
        Runnable submit=()->{
            String u=username.getText().toString().trim(),p=password.getText().toString();
            if(u.isEmpty()||p.isEmpty()){error.setText("请输入账号名和密码。");error.setVisibility(View.VISIBLE);return;}
            password.setText("");sheet.dialog.dismiss();connect(u,p,remember.isChecked(),false);
        };
        sheet.footerButton("先公开浏览",false,()->{sheet.dialog.dismiss();connect(null,null,false,true);});sheet.footerButton("登录",true,submit);
        sheet.dialog.setOnDismissListener(d->password.setText(""));
        password.setOnEditorActionListener((v,action,event)->{if(action==EditorInfo.IME_ACTION_DONE){submit.run();return true;}return false;});sheet.show();
    }
    private void connect(String username,String password,boolean remember,boolean guest){
        if(app.connecting)return;if(!app.enqueued.isEmpty()&&(username!=null||guest)){toast("请先暂停当前下载。");return;}
        app.connecting=true;lastError=null;refreshStatus();if(tab==0)renderResults();app.work.submit(()->{
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
                        public CompletableFuture<String> getDeviceCode(boolean incorrect){return guardCode("Steam Guard 验证码",incorrect?"上一个验证码不正确，请重新输入。":"输入 Steam App 中显示的验证码。");}
                        public CompletableFuture<String> getEmailCode(String email,boolean incorrect){return guardCode("邮件验证码",(incorrect?"上一个验证码不正确。":"")+"输入 Steam 发送到 "+email+" 的验证码。");}
                        public CompletableFuture<Boolean> acceptDeviceConfirmation(){
                            CompletableFuture<Boolean> result=new CompletableFuture<>();runOnUiThread(()->{
                                if(isFinishing()){result.completeExceptionally(new CancellationException());return;}
                                AppSheet sheet=new AppSheet(MainActivity.this,"在 Steam App 中确认","打开手机上的 Steam App，确认这次登录。也可以改用验证码。",null);
                                sheet.footerButton("输入验证码",false,()->{result.complete(false);sheet.dialog.dismiss();});
                                sheet.footerButton("我去确认",true,()->{result.complete(true);sheet.dialog.dismiss();});
                                sheet.dialog.setOnDismissListener(d->result.completeExceptionally(new CancellationException()));sheet.show();
                            });return result;
                        }
                    },app::remember);
                }
                app.session=fresh;app.guest=guest;fresh=null;
                runOnUiThread(()->{refreshStatus();if(tab==0)search(true);});
            }catch(Throwable e){if(fresh!=null)fresh.close();String message=VideoApp.error(e);app.status(message);runOnUiThread(()->{lastError=items.isEmpty()?message:null;refreshStatus();toast(message);if(tab==0)renderResults();});}
            finally {app.connecting=false;app.changed();}
            }
        });
    }
    private CompletableFuture<String> guardCode(String title,String message){
        CompletableFuture<String> response=new CompletableFuture<>();runOnUiThread(()->{
            if(isFinishing()||isDestroyed()){response.completeExceptionally(new CancellationException());return;}
            AppSheet sheet=new AppSheet(this,title,message,null);
            EditText code=input("验证码");code.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);code.setSaveEnabled(false);code.setImeOptions(EditorInfo.IME_ACTION_DONE|EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);sheet.field(code);
            TextView error=text("",13,Ui.DANGER);error.setVisibility(View.GONE);sheet.field(error);
            Runnable submit=()->{String value=code.getText().toString().trim();if(value.isEmpty()){error.setText("请输入验证码。");error.setVisibility(View.VISIBLE);return;}response.complete(value);sheet.dialog.dismiss();};
            sheet.footerButton("取消",false,()->sheet.dialog.dismiss());sheet.footerButton("验证",true,submit);
            sheet.dialog.setOnDismissListener(d->{code.setText("");response.completeExceptionally(new CancellationException());});
            code.setOnEditorActionListener((v,action,event)->{if(action==EditorInfo.IME_ACTION_DONE){submit.run();return true;}return false;});sheet.show();code.requestFocus();sheet.dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE|WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        });return response;
    }

    private void filterDialog(){
        WorkshopQuery current=filters.copy();if(search!=null)current.text=search.getText().toString();
        new FilterPanel(this,current,selected->{try{app.saveFilters(selected);filters=selected;updateChips();search(true);}catch(Exception e){toast(VideoApp.error(e));throw e;}}).show();
    }

    private static final class CardHolder {
        final View card;final ImageView image,heart;final TextView badge,title,info;PublishedFileDetails item;String bound;
        CardHolder(View card,ImageView image,ImageView heart,TextView badge,TextView title,TextView info){this.card=card;this.image=image;this.heart=heart;this.badge=badge;this.title=title;this.info=info;}
    }
    /** Cards are recycled; each bind replaces the preview tag so late loads cannot land on a reused cover. */
    private class CardAdapter extends BaseAdapter {
        public int getCount(){return items.size();}public Object getItem(int p){return items.get(p);}public long getItemId(int p){return p;}
        public View getView(int position,View recycled,android.view.ViewGroup parent){
            CardHolder holder=recycled!=null&&recycled.getTag() instanceof CardHolder existing?existing:createCard();bind(holder,items.get(position));return holder.card;
        }
    }
    private CardHolder createCard(){
        LinearLayout card=column();card.setBackground(Ui.card(this));card.setClipToOutline(true);card.setForeground(new RippleDrawable(ColorStateList.valueOf(0x1affffff),null,Ui.round(Color.WHITE,dp(18))));
        FrameLayout cover=new FrameLayout(this);ImageView image=new PreviewImageView(this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setBackgroundColor(0xff1b2634);cover.addView(image,new FrameLayout.LayoutParams(-1,-1));
        View shade=new View(this);shade.setBackground(Ui.coverShade());cover.addView(shade,new FrameLayout.LayoutParams(-1,dp(40),Gravity.BOTTOM));
        TextView badge=Ui.badge(this,"",ACCENT);FrameLayout.LayoutParams bp=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.START);bp.setMargins(dp(8),dp(8),dp(8),dp(8));cover.addView(badge,bp);
        ImageView heart=new ImageView(this);heart.setScaleType(ImageView.ScaleType.CENTER);heart.setBackground(Ui.round(Ui.SCRIM,dp(17)));FrameLayout.LayoutParams hp=new FrameLayout.LayoutParams(dp(34),dp(34),Gravity.TOP|Gravity.END);hp.setMargins(dp(6),dp(6),dp(6),dp(6));cover.addView(heart,hp);
        int width=(getResources().getDisplayMetrics().widthPixels-dp(48))/2;card.addView(cover,new LinearLayout.LayoutParams(-1,Math.max(dp(100),width*10/16)));
        LinearLayout meta=column();meta.setPadding(dp(12),dp(10),dp(12),dp(12));TextView title=text("",13,TEXT);Ui.cardTitle(title);title.setLineSpacing(dp(1),1);meta.addView(title);
        TextView info=text("",11,Ui.SUBTLE);info.setSingleLine(true);info.setEllipsize(TruncateAt.END);info.setPadding(0,dp(6),0,0);meta.addView(info);card.addView(meta);
        CardHolder holder=new CardHolder(card,image,heart,badge,title,info);card.setTag(holder);
        card.setOnClickListener(v->{if(holder.item!=null)detail(holder.item);});
        heart.setOnClickListener(v->{if(holder.item==null)return;try{app.toggleFavorite(favoriteItem(holder.item));}catch(Exception e){toast(VideoApp.error(e));}bindHeart(holder);});
        return holder;
    }
    private void bind(CardHolder holder,PublishedFileDetails item){
        holder.item=item;holder.title.setText(item.getTitle());holder.info.setText(bytes(item.getFileSize())+" · "+date(item.getTimeCreated()));
        String type=typeLabel(item);holder.badge.setText(type);holder.badge.setVisibility(type.isEmpty()?View.GONE:View.VISIBLE);bindHeart(holder);holder.card.setContentDescription(item.getTitle()+(type.isEmpty()?"":"，"+type));
        String url=item.getPreviewUrl();if(!url.equals(holder.bound)){holder.bound=url;holder.image.setImageDrawable(null);holder.image.setTag(url);app.previews.load(holder.image,url);}
    }
    private void bindHeart(CardHolder holder){boolean selected=app.isFavorite(Long.toUnsignedString(holder.item.getPublishedfileid()));holder.heart.setImageDrawable(new Ui.Icon(selected?"heartFilled":"heart",selected?ACCENT:TEXT,dp(18)));holder.heart.setContentDescription(selected?"取消收藏":"收藏");}
    private static String typeOf(PublishedFileDetails item){for(String type:TYPES)if(item.getTagsList().stream().anyMatch(t->t.getTag().equalsIgnoreCase(type)))return type;return "";}
    private static String typeLabel(PublishedFileDetails item){String type=typeOf(item);for(int i=0;i<TYPES.length;i++)if(TYPES[i].equals(type))return TYPE_NAMES[i];return "";}
    private static String typeName(String type){for(int i=0;i<TYPES.length;i++)if(TYPES[i].equalsIgnoreCase(type))return TYPE_NAMES[i];return "作品";}
    private JSONObject favoriteItem(PublishedFileDetails item){
        JSONObject value=new JSONObject();VideoApp.put(value,"itemId",Long.toUnsignedString(item.getPublishedfileid()));VideoApp.put(value,"title",item.getTitle());VideoApp.put(value,"previewUrl",item.getPreviewUrl());VideoApp.put(value,"description",item.getShortDescription());VideoApp.put(value,"video",typeOf(item).equals("Video"));
        if(!typeOf(item).isEmpty())VideoApp.put(value,"type",typeOf(item));if(item.getFileSize()>0)VideoApp.put(value,"size",item.getFileSize());if(item.getTimeCreated()!=0)VideoApp.put(value,"created",Integer.toUnsignedLong(item.getTimeCreated()));return value;
    }
    private void favoriteDetail(JSONObject item){
        if(item.optString("itemId").startsWith("local:")){for(JSONObject record:app.jobs())if(record.optString("itemId").equals(item.optString("itemId"))){openVideo(record);return;}toast("这个本地内容已删除，收藏记录仍保留。");return;}
        long created=item.optLong("created");
        showDetail(item,item.optLong("size")>0?Ui.size(item.optLong("size")):"",created>0?"发布于 "+date((int)created):"",List.of(),item.optString("description"),item.optBoolean("video",true));
    }
    private void detail(PublishedFileDetails item){
        String description=item.getFileDescription().isEmpty()?item.getShortDescription():item.getFileDescription();
        LinkedHashSet<String> tags=new LinkedHashSet<>();for(var tag:item.getTagsList())if(!Arrays.asList(TYPES).contains(tag.getTag()))tags.add(Ui.tagName(tag.getTag()));
        showDetail(favoriteItem(item),item.getFileSize()>0?bytes(item.getFileSize()):"","发布于 "+date(item.getTimeCreated())+" · 更新于 "+date(item.getTimeUpdated()),new ArrayList<>(tags),description,typeOf(item).equals("Video"));
    }
    private JSONObject existingJob(String itemId){for(JSONObject job:app.jobs())if(job.optString("itemId").equals(itemId))return job;return null;}
    private void showDetail(JSONObject item,String size,String dates,List<String> tags,String description,boolean video){
        String type=item.optString("type",video?"Video":"Scene");boolean supported=type.equalsIgnoreCase("Video")||type.equalsIgnoreCase("Scene")||type.equalsIgnoreCase("Image");
        AppSheet sheet=new AppSheet(this,item.optString("title"),typeName(type)+(size.isEmpty()?"":" · "+size),null);
        ImageView image=new PreviewImageView(this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setBackground(Ui.round(0xff1b2634,dp(16)));image.setClipToOutline(true);image.setContentDescription(item.optString("title")+"的预览");
        int coverHeight=Math.min(dp(240),(getResources().getDisplayMetrics().widthPixels-dp(36))*9/16);LinearLayout cover=column();cover.addView(image,new LinearLayout.LayoutParams(-1,coverHeight));sheet.field(cover);app.previews.load(image,item.optString("previewUrl"));
        if(!dates.isEmpty())sheet.field(text(dates,12,Ui.SUBTLE));
        if(!tags.isEmpty()){Ui.Flow flow=new Ui.Flow(this,dp(6));for(String tag:tags){TextView chip=text(tag,12,MUTED);chip.setPadding(dp(10),dp(5),dp(10),dp(5));chip.setBackground(Ui.outlined(Color.TRANSPARENT,Ui.LINE,dp(10),dp(1)));flow.addView(chip);}sheet.field(flow);}
        String body=description==null||description.isBlank()?"作者没有提供介绍。":description.trim();
        TextView info=text(body,14,MUTED);info.setLineSpacing(dp(5),1);info.setTextIsSelectable(true);sheet.field(info);
        if(body.length()>160||body.split("\n").length>6){info.setMaxLines(6);info.setEllipsize(TruncateAt.END);Button more=Ui.button(this,"展开全文",Ui.Kind.GHOST,null);more.setPadding(0,0,0,0);more.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
            more.setOnClickListener(v->{boolean expand=info.getMaxLines()!=Integer.MAX_VALUE;info.setMaxLines(expand?Integer.MAX_VALUE:6);more.setText(expand?"收起":"展开全文");});sheet.field(more);}
        if(!supported)sheet.field(Ui.callout(this,"info","这个作品需要网页或应用运行环境，手机暂不支持。",Ui.DANGER));
        else if(!video)sheet.field(Ui.callout(this,"info","下载后会自动转换成手机可用的壁纸。部分原作特效可能无法保留；无法转换时不会保存。",ACCENT));
        String itemId=item.optString("itemId");
        ImageView favorite=sheet.footerIcon("heart","收藏",null);
        Runnable updateFavorite=()->{boolean selected=app.isFavorite(itemId);favorite.setImageDrawable(new Ui.Icon(selected?"heartFilled":"heart",selected?ACCENT:TEXT,dp(20)));favorite.setContentDescription(selected?"取消收藏":"收藏");};
        favorite.setOnClickListener(v->{try{app.toggleFavorite(item);if(adapter!=null)adapter.notifyDataSetChanged();}catch(Exception e){toast(VideoApp.error(e));}updateFavorite.run();});updateFavorite.run();
        if(video&&supported)sheet.footerButton("在线播放",false,()->{if(!app.canRecover()||app.guest){toast("在线播放需要登录拥有 Wallpaper Engine 的 Steam 账号。");loginDialog();return;}sheet.dialog.dismiss();startActivity(new Intent(this,VideoPlayerActivity.class).putExtra("workshopId",itemId).putExtra("title",item.optString("title")));});
        JSONObject existing=existingJob(itemId);boolean saved=existing!=null&&existing.optString("state").equals("saved");
        String primary=!supported?"暂不支持":saved?"打开":existing!=null?"查看下载":video?"下载":"下载并转换";
        Button download=sheet.footerButton(primary,true,()->{
            sheet.dialog.dismiss();
            if(saved){openVideo(existing);return;}
            if(existing!=null){showTab(2);if(library!=null)library.showCategory(!video);return;}
            if(!app.canRecover()||app.guest){toast("下载需要登录拥有 Wallpaper Engine 的 Steam 账号。");loginDialog();return;}
            JSONObject record=app.create(itemId,item.optString("title"),item.optString("previewUrl"));if(record.optString("state").equals("saved")){openVideo(record);return;}VideoApp.put(record,"type",type);VideoApp.put(record,"kind",video?"video":"scene");app.save(record);startDownload(record.optString("id"));showTab(2);if(library!=null)library.showCategory(!video);
        });Ui.enable(download,supported);sheet.show();
    }
    private void startDownload(String id){
        if(!app.canRecover()||(app.session!=null&&app.guest)){toast("请先登录 Steam 账号。");loginDialog();return;}
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},11);
        if(app.enqueued.contains(id)){toast("任务仍在停止中，请稍后再继续。");return;}
        try{JSONObject record=app.job(id);VideoApp.put(record,"cancelRequested",false);VideoApp.put(record,"discardRequested",false);app.save(record);}catch(Exception e){toast(VideoApp.error(e));return;}
        try{startForegroundService(new Intent(this,VideoDownloadService.class).putExtra("id",id));}catch(Exception e){toast(VideoApp.error(e));}
    }
    private void renderJobs(){
        if(tab==0)return;
        if(tab==2){
            if(library==null)library=new SavedLibraryView(this,app,this::openVideo,this::startDownload);
            if(library.getParent()!=body){body.removeAllViews();body.addView(library,new LinearLayout.LayoutParams(-1,-1));}
            library.refresh();return;
        }
        if(favorites==null)favorites=new FavoritesView(this,app,this::favoriteDetail,()->showTab(0));
        if(favorites.getParent()!=body){body.removeAllViews();body.addView(favorites,new LinearLayout.LayoutParams(-1,-1));}
        favorites.refresh();
    }
    private void openVideo(JSONObject job){try{if(job.optBoolean("officialPackage")){SceneAdaptation.open(this,app,job);return;}if(!job.optString("kind","video").equals("video")){if(!job.optBoolean("compatible",true)){if(job.optString("sourceType").equals("scene")||job.optString("kind").equals("package")){SceneAdaptation.open(this,app,job);return;}AppSheet sheet=new AppSheet(this,job.optString("title"),"这个内容无法用作手机壁纸",null);TextView reason=text(job.optString("compatibilityReason","这个内容暂不兼容。"),14,MUTED);sheet.field(reason);sheet.footerButton("关闭",false,()->sheet.dialog.dismiss());sheet.show();return;}startActivity(new Intent(this,WallpaperSetupActivity.class).putExtra("id",job.getString("id")));return;}startActivity(new Intent(this,VideoPlayerActivity.class).putExtra("id",job.getString("id")));}catch(Exception e){toast(VideoApp.error(e));}}
    private static String bytes(long n){return n<=0?"大小未知":Ui.size(n);}
    private static String date(int n){return Ui.date(n);}
}
