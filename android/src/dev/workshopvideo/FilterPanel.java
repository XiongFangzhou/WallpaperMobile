package dev.workshopvideo;

import android.app.*;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.*;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import dev.workshopvideo.core.*;
import java.util.*;
import java.util.function.Consumer;

/** PC group/checkbox semantics in an app-owned, scrollable mobile sheet. */
final class FilterPanel {
    private final Activity activity;private final Dialog dialog;private final WorkshopQuery draft;private final Consumer<WorkshopQuery> apply;
    private final Map<String,CheckBox> checks=new LinkedHashMap<>();private final Map<String,Integer> utilities=new LinkedHashMap<>();
    private final LinearLayout content;private final TextView sortLabel;private final CheckBox exact,titleOnly;
    private final EditText from,to,include,exclude;private final TextView error;
    private TextView resolutionCount;
    private static final String[] SORT_NAMES={"评分最高","最热门（今年）","最热门（本月）","最热门（本周）","最热门（今日）","最近","最多投票","最多订阅","搜索相关"};
    private static final int[] SORTS={0,3,3,3,3,1,10,12,11},DAYS={365,365,30,7,1,365,365,365,365};
    FilterPanel(Activity activity,WorkshopQuery query,Consumer<WorkshopQuery> apply){
        this.activity=activity;this.apply=apply;draft=WeFilters.migrate(query.copy());dialog=new Dialog(activity);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout root=column();root.setPadding(dp(18),dp(12),dp(18),dp(12));root.setBackground(AppSheet.background(Ui.BG,dp(24)));
        View handle=new View(activity);handle.setBackground(AppSheet.background(0xff425163,dp(3)));LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(dp(34),dp(4));hp.gravity=Gravity.CENTER_HORIZONTAL;hp.bottomMargin=dp(16);root.addView(handle,hp);
        LinearLayout heading=new LinearLayout(activity);heading.setGravity(Gravity.CENTER_VERTICAL);TextView name=text("筛选作品",23,Ui.TEXT);name.setTypeface(null,Typeface.BOLD);heading.addView(name,new LinearLayout.LayoutParams(0,-2,1));heading.addView(small("取消",()->dialog.dismiss()));root.addView(heading);
        TextView hint=text("按 PC 分类勾选；未勾选项从结果中排除。",12,Ui.MUTED);hint.setPadding(0,dp(8),0,dp(14));root.addView(hint);
        ScrollView scroll=new ScrollView(activity);scroll.setFillViewport(false);scroll.setClipToPadding(false);content=column();scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout order=card();order.addView(text("排序",13,Ui.MUTED));sortLabel=text(currentSort(),16,Ui.ACCENT);sortLabel.setPadding(0,dp(12),0,dp(12));sortLabel.setOnClickListener(v->chooseSort());order.addView(sortLabel);content.addView(order,margin());
        LinearLayout type=card();addChecks(type,WeFilters.TYPES,false);addChecks(type,WeFilters.CATEGORIES,false);content.addView(type,margin());
        LinearLayout ratings=card();addChecks(ratings,WeFilters.RATINGS,false);content.addView(ratings,margin());
        LinearLayout resolutions=card();LinearLayout resolutionBody=column();TextView resolutionSummary=text("",11,Ui.MUTED);resolutionCount=resolutionSummary;updateCount(resolutionSummary,WeFilters.ALL_RESOLUTIONS);
        LinearLayout rh=new LinearLayout(activity);rh.setGravity(Gravity.CENTER_VERTICAL);rh.addView(text("分辨率",15,Ui.TEXT),new LinearLayout.LayoutParams(0,-2,1));rh.addView(small("所有",()->setGroup(WeFilters.ALL_RESOLUTIONS,true)));rh.addView(small("无",()->setGroup(WeFilters.ALL_RESOLUTIONS,false)));resolutions.addView(rh);resolutions.addView(resolutionSummary);
        for(WeFilters.Group group:WeFilters.RESOLUTIONS)addChecks(resolutionBody,group,true);resolutions.addView(resolutionBody);content.addView(resolutions,margin());
        LinearLayout genres=card();addChecks(genres,WeFilters.GENRES,true);content.addView(genres,margin());
        LinearLayout utilityBody=column();for(WeFilters.Option option:WeFilters.UTILITIES.options()){
            int initial=draft.required.contains(option.value())?1:draft.excluded.contains(option.value())?2:0;utilities.put(option.value(),initial);
            LinearLayout row=new LinearLayout(activity);row.setGravity(Gravity.CENTER_VERTICAL);row.addView(text(option.label(),13,Ui.TEXT),new LinearLayout.LayoutParams(0,-2,1));TextView state=small(stateText(initial),null);state.setOnClickListener(v->{int next=(utilities.get(option.value())+1)%3;utilities.put(option.value(),next);state.setText(stateText(next));});row.addView(state);utilityBody.addView(row);
        }fold(content,"功能标签",utilityBody,false);
        LinearLayout advanced=column();exact=check("精确匹配关键词",draft.exact);advanced.addView(exact);titleOnly=check("只在本页标题中匹配",draft.titleOnly);advanced.addView(titleOnly);
        from=input("发布开始日期 YYYY-MM-DD",draft.from);to=input("发布结束日期 YYYY-MM-DD",draft.to);advanced.addView(from);advanced.addView(to);
        Set<String> utilityKeys=WeFilters.keys(WeFilters.UTILITIES);Set<String> groupedKeys=WeFilters.allKeys();
        // Preserve pre-upgrade positive constraints explicitly until the user removes them.
        include=input("额外必须包含标签，用逗号分隔",String.join(", ",draft.required.stream().filter(t->!utilityKeys.contains(t)).toList()));
        exclude=input("额外排除标签，用逗号分隔",String.join(", ",draft.excluded.stream().filter(t->!groupedKeys.contains(t)).toList()));advanced.addView(include);advanced.addView(exclude);fold(content,"搜索与高级条件",advanced,false);
        error=text("",12,0xffff9caa);root.addView(error);
        LinearLayout footer=new LinearLayout(activity);footer.setPadding(0,dp(10),0,0);footer.addView(button("重置",false,()->{WorkshopQuery defaults=WeFilters.defaults();defaults.text=draft.text;apply.accept(defaults);dialog.dismiss();}),new LinearLayout.LayoutParams(0,dp(46),1));LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,dp(46),2);bp.leftMargin=dp(10);footer.addView(button("应用筛选",true,this::commit),bp);root.addView(footer);
        dialog.setContentView(root);Window window=dialog.getWindow();window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));window.setGravity(Gravity.BOTTOM);window.setDimAmount(.6f);window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);window.setNavigationBarColor(Ui.BG);window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        root.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(dp(18)+insets.getSystemWindowInsetLeft(),dp(12),dp(18)+insets.getSystemWindowInsetRight(),dp(12)+insets.getSystemWindowInsetBottom());return insets;});
    }
    private int dp(float value){return Math.round(value*activity.getResources().getDisplayMetrics().density);}
    private LinearLayout column(){LinearLayout box=new LinearLayout(activity);box.setOrientation(LinearLayout.VERTICAL);return box;}
    private TextView text(String value,int size,int color){TextView text=new TextView(activity);text.setText(value);text.setTextSize(size);text.setTextColor(color);return text;}
    private LinearLayout card(){LinearLayout box=column();box.setPadding(dp(14),dp(12),dp(14),dp(12));box.setBackground(AppSheet.background(Ui.PANEL,dp(18)));return box;}
    private LinearLayout.LayoutParams margin(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(12);return p;}
    private TextView small(String value,Runnable run){TextView text=text(value,12,Ui.ACCENT);text.setGravity(Gravity.CENTER);text.setPadding(dp(12),dp(10),dp(12),dp(10));if(run!=null)text.setOnClickListener(v->run.run());return text;}
    private CheckBox check(String value,boolean checked){CheckBox button=new CheckBox(activity);button.setText(value);button.setTextSize(13);button.setTextColor(Ui.TEXT);button.setButtonTintList(ColorStateList.valueOf(Ui.ACCENT));button.setChecked(checked);button.setMinHeight(dp(42));return button;}
    private Button button(String value,boolean primary,Runnable run){Button button=new Button(activity);button.setText(value);button.setAllCaps(false);button.setTextSize(14);button.setTextColor(primary?0xff15332c:Ui.TEXT);button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33ffffff),AppSheet.background(primary?Ui.ACCENT:Ui.PANEL,dp(14)),null));button.setOnClickListener(v->run.run());return button;}
    private EditText input(String hint,String value){EditText field=new EditText(activity);field.setText(value);field.setHint(hint);field.setTextColor(Ui.TEXT);field.setTextSize(12);field.setHintTextColor(Ui.MUTED);field.setSingleLine(true);field.setImeOptions(EditorInfo.IME_ACTION_DONE);return field;}
    private void addChecks(LinearLayout parent,WeFilters.Group group,boolean collapsed){
        LinearLayout section=column(),body=column();TextView count=text("",11,Ui.MUTED);LinearLayout header=new LinearLayout(activity);header.setGravity(Gravity.CENTER_VERTICAL);header.addView(text((collapsed?"› ":"")+group.title(),14,Ui.TEXT),new LinearLayout.LayoutParams(0,-2,1));
        header.addView(small("所有",()->setGroup(group,true)));header.addView(small("无",()->setGroup(group,false)));section.addView(header);section.addView(count);
        for(WeFilters.Option option:group.options()){
            if(group==WeFilters.TYPES&&option.value().equals("Application"))continue;
            CheckBox checkbox=check(option.label(),WeFilters.checked(draft,option.value()));checks.put(option.value(),checkbox);body.addView(checkbox);checkbox.setOnCheckedChangeListener((v,on)->{WeFilters.check(draft,option.value(),on);updateCount(count,group);if(resolutionCount!=null)updateCount(resolutionCount,WeFilters.ALL_RESOLUTIONS);});
        }
        updateCount(count,group);body.setVisibility(collapsed?View.GONE:View.VISIBLE);header.getChildAt(0).setOnClickListener(v->{body.setVisibility(body.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE);((TextView)header.getChildAt(0)).setText((body.getVisibility()==View.VISIBLE?"⌄ ":"› ")+group.title());});section.addView(body);parent.addView(section);
    }
    private void updateCount(TextView text,WeFilters.Group group){long count=group.options().stream().filter(option->!(group==WeFilters.TYPES&&option.value().equals("Application"))).filter(option->WeFilters.checked(draft,option.value())).count();text.setText(count+" / "+(group==WeFilters.TYPES?3:group.options().size())+" 已勾选");}
    private void setGroup(WeFilters.Group group,boolean on){for(WeFilters.Option option:group.options()){if(group==WeFilters.TYPES&&option.value().equals("Application")){WeFilters.check(draft,option.value(),on);continue;}CheckBox checkbox=checks.get(option.value());if(checkbox!=null)checkbox.setChecked(on);}}
    private void fold(LinearLayout parent,String title,LinearLayout body,boolean open){LinearLayout section=card();TextView heading=text((open?"⌄ ":"› ")+title,15,Ui.TEXT);heading.setPadding(0,dp(8),0,dp(8));section.addView(heading);section.addView(body);body.setVisibility(open?View.VISIBLE:View.GONE);heading.setOnClickListener(v->{body.setVisibility(body.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE);heading.setText((body.getVisibility()==View.VISIBLE?"⌄ ":"› ")+title);});parent.addView(section,margin());}
    private String stateText(int value){return value==1?"必须包含":value==2?"排除":"不限";}
    private String currentSort(){for(int i=0;i<SORTS.length;i++)if(draft.sort==SORTS[i]&&(draft.sort!=3||draft.days==DAYS[i]))return SORT_NAMES[i];return draft.sort==3?"最热门（"+draft.days+" 天）":"评分最高";}
    private void chooseSort(){AppSheet sheet=new AppSheet(activity,"排序","与 PC 的公开排序选项一致",null);for(int i=0;i<SORT_NAMES.length;i++){int index=i;sheet.action("check",SORT_NAMES[i],SORTS[i]==draft.sort&&(draft.sort!=3||DAYS[i]==draft.days)?"当前选择":"",false,()->{draft.sort=SORTS[index];draft.days=DAYS[index];sortLabel.setText(currentSort());});}sheet.show();}
    private void commit(){
        try{WorkshopQuery result=draft.copy();result.required.clear();for(Map.Entry<String,Integer> entry:utilities.entrySet()){result.excluded.remove(entry.getKey());if(entry.getValue()==1)result.required.add(entry.getKey());else if(entry.getValue()==2)result.excluded.add(entry.getKey());}
            for(String key:include.getText().toString().split("[,，]"))if(!key.trim().isEmpty())result.required.add(key.trim());for(String key:exclude.getText().toString().split("[,，]"))if(!key.trim().isEmpty())result.excluded.add(key.trim());
            result.exact=exact.isChecked();result.titleOnly=titleOnly.isChecked();result.from=from.getText().toString().trim();result.to=to.getText().toString().trim();result.request();apply.accept(result);dialog.dismiss();
        }catch(Exception problem){error.setText(VideoApp.error(problem));}
    }
    void show(){dialog.show();dialog.getWindow().setLayout(-1,(int)(activity.getResources().getDisplayMetrics().heightPixels*.91f));}
}
