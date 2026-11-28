package dev.workshopvideo;
import android.app.Activity;import android.content.res.ColorStateList;import android.graphics.Color;import android.view.*;import android.widget.*;import org.json.*;import java.util.*;import java.util.function.Consumer;
/** Live controls, shared by every locally supported wallpaper type. */
final class EffectPanel extends LinearLayout {
 private final Activity activity;private final Consumer<JSONObject> changed;private JSONObject value;private final boolean hasSensor;private final String kind;private int layerCount;private float[] layerDepths;private String[] layerNames;private boolean xray;private LinearLayout card;
 EffectPanel(Activity activity,JSONObject initial,boolean sensor,String kind,Consumer<JSONObject> changed){super(activity);this.activity=activity;this.changed=changed;this.hasSensor=sensor;this.kind=kind;value=kind.equals("video")?EffectOptions.video(initial):EffectOptions.normalize(initial);setOrientation(VERTICAL);build();}
 private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
 private TextView label(String s,int size,int color){TextView t=new TextView(activity);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setPadding(0,dp(10),0,dp(7));return t;}
 private void control(View view,LayoutParams p){if(card!=null)card.addView(view,p);else addView(view,p);}
 private void control(View view){control(view,new LayoutParams(-1,-2));}
 private void heading(String title){card=new LinearLayout(activity);card.setOrientation(VERTICAL);card.setPadding(dp(16),dp(6),dp(16),dp(14));card.setBackground(Ui.card(activity));LayoutParams p=new LayoutParams(-1,-2);p.topMargin=dp(12);addView(card,p);TextView name=label(title,15,Ui.TEXT);name.setTypeface(null,android.graphics.Typeface.BOLD);card.addView(name);}
 private void changed(){applyAuthorTargets();changed.accept(kind.equals("video")?EffectOptions.video(value):EffectOptions.normalize(value));}
 private void toggle(String key,String title,boolean enabled){Switch s=new Switch(activity);s.setText(title);s.setTextSize(13);s.setTextColor(Ui.TEXT);s.setMinHeight(dp(46));s.setPadding(0,dp(6),0,dp(6));s.setChecked(enabled&&value.optBoolean(key,key.equals("xray")));s.setEnabled(enabled);s.setAlpha(enabled?1:.45f);s.setThumbTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{Ui.ACCENT,0xff81909f}));s.setTrackTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{0xff355c51,0xff303d4b}));s.setOnCheckedChangeListener((b,on)->{VideoApp.put(value,key,on);if(!kind.equals("video")&&(java.util.List.of("gyro","touch","ripple","particles","pan","xray").contains(key)||key.startsWith("user.")))build();changed();});control(s);}
 private void slider(String key,String title,float min,float max,String suffix){
  float initial=(float)value.optDouble(key,key.equals("xraySize")?.3:min);LinearLayout header=new LinearLayout(activity);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(0,dp(8),0,0);TextView name=label(title,13,Ui.TEXT);name.setPadding(0,0,0,0);header.addView(name,new LayoutParams(0,-2,1));TextView number=label(number(key,initial)+suffix,12,Ui.ACCENT);number.setPadding(dp(8),dp(2),dp(8),dp(2));number.setBackground(AppSheet.background(Ui.ACCENT_SOFT,dp(8)));header.addView(number);control(header);
  SeekBar bar=new SeekBar(activity);bar.setMax(key.equals("fallbackPages")?11:key.equals("fps")?105:1000);bar.setProgress(Math.round((initial-min)/(max-min)*bar.getMax()));bar.setProgressTintList(ColorStateList.valueOf(Ui.ACCENT));bar.setProgressBackgroundTintList(ColorStateList.valueOf(Ui.LINE));bar.setThumbTintList(ColorStateList.valueOf(Ui.ACCENT));bar.setContentDescription(title);bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}public void onProgressChanged(SeekBar s,int p,boolean user){if(!user)return;float next=min+(max-min)*p/s.getMax();if(key.equals("fallbackPages")||key.equals("fps"))next=Math.round(next);VideoApp.put(value,key,next);number.setText(number(key,next)+suffix);changed();}});bar.setPadding(dp(4),0,dp(4),0);control(bar,new LayoutParams(-1,dp(36)));
 }
 private String number(String key,float value){return key.equals("fallbackPages")||key.equals("fps")||key.equals("hue")?String.valueOf(Math.round(value)):format(value);}
 private String format(float f){return String.format(Locale.CHINA,"%.2f",f);}
 /** Author switches of a converted scene (bool and combo properties that show or hide layers). */
 private org.json.JSONObject sceneProperties=new org.json.JSONObject();
 void sceneProperties(org.json.JSONObject properties){sceneProperties=properties==null?new org.json.JSONObject():properties;authorSnapshot.clear();for(java.util.Iterator<String> k=sceneProperties.keys();k.hasNext();){String n=k.next();authorSnapshot.put(n,String.valueOf(value.opt("user."+n)));}build();}
 /** Last applied value of each author setting, so only the one just changed rewrites the phone options it drives. */
 private final java.util.Map<String,String> authorSnapshot=new java.util.HashMap<>();
 /** Author settings that drive phone options: mouse parallax → tilt parallax, its influence → tilt strength, camera zoom and centre → zoom and position. */
 private void applyAuthorTargets(){
  for(java.util.Iterator<String> k=sceneProperties.keys();k.hasNext();){
   String name=k.next();org.json.JSONObject p=sceneProperties.optJSONObject(name);Object v=value.opt("user."+name);if(p==null||v==null)continue;
   String now=String.valueOf(v);if(now.equals(authorSnapshot.get(name)))continue;authorSnapshot.put(name,now);
   org.json.JSONArray targets=p.optJSONArray("targets");if(targets==null)continue;double number=value.optDouble("user."+name,0);
   for(int i=0;i<targets.length();i++){
    org.json.JSONObject t=targets.optJSONObject(i);if(t==null||!t.optString("kind").equals("option"))continue;
    switch(t.optString("key")){
     case "gyro"->VideoApp.put(value,"gyro",t.has("condition")?now.equals(t.optString("condition")):!now.equals("false")&&!now.equals("0"));
     case "strength"->VideoApp.put(value,"strength",Math.max(0,Math.min(1,number*2)));
     case "zoom"->VideoApp.put(value,"zoom",Math.max(1,Math.min(2.5,number)));
     case "x"->VideoApp.put(value,"x",Math.max(-.5,Math.min(.5,.5-number)));
     case "y"->VideoApp.put(value,"y",Math.max(-.5,Math.min(.5,number-.5)));
     default->{}
    }
   }
  }
 }
 /**
  * The work's own settings, generated from its declarations the way Wallpaper Engine lists them: author groups become
  * sections, display conditions hide what does not apply, and settings that have no effect on the phone are named once.
  */
 /** Shown above the settings when the saved scene came from an older converter that could not keep them. */
 private String sceneNotice;
 void sceneNotice(String notice){sceneNotice=notice;build();}
 private void propertyControls(){
  if(sceneNotice!=null){heading("作品设置");TextView note=label(sceneNotice,13,Ui.MUTED);note.setLineSpacing(dp(3),1);control(note);if(sceneProperties.length()==0)return;}
  applyAuthorTargets();if(sceneNotice==null)heading("作品设置");
  java.util.List<String> names=new java.util.ArrayList<>();for(java.util.Iterator<String> k=sceneProperties.keys();k.hasNext();)names.add(k.next());
  names.sort(java.util.Comparator.comparingInt(n->sceneProperties.optJSONObject(n).optInt("order",0)));
  java.util.function.Function<String,Object> current=n->{org.json.JSONObject p=sceneProperties.optJSONObject(n);return value.has("user."+n)?value.opt("user."+n):p==null?null:p.opt("value");};
  String group=null;
  for(String name:names){
   org.json.JSONObject p=sceneProperties.optJSONObject(name);String type=p.optString("type"),key="user."+name,label=p.optString("label",name);
   if(!dev.workshopvideo.core.PropertyCondition.test(p.optString("condition"),current))continue;
   // Groups fold like the desktop panel's sections; everything after a group belongs to it until the next one.
   if(type.equals("group")){group=name;boolean open=expanded.contains(name);TextView row=label((open?"▾  ":"▸  ")+label,14,Ui.TEXT);row.setTypeface(null,android.graphics.Typeface.BOLD);row.setMinHeight(dp(44));row.setGravity(Gravity.CENTER_VERTICAL);row.setOnClickListener(v->{if(!expanded.remove(name))expanded.add(name);build();});control(row);continue;}
   if(group!=null&&!expanded.contains(group))continue;
   if(type.equals("divider")){View line=new View(activity);line.setBackgroundColor(Ui.LINE);LayoutParams lp=new LayoutParams(-1,dp(1));lp.topMargin=dp(8);lp.bottomMargin=dp(8);control(line,lp);continue;}
   if(type.equals("text")){TextView note=label(label,12,Ui.MUTED);note.setLineSpacing(dp(3),1);control(note);continue;}
   boolean works=p.optBoolean("effective",true)&&java.util.List.of("bool","combo","slider","color","textinput","scenetexture").contains(type);String title=works?label:label+"（手机上暂不生效）";
   switch(type){
    case "slider"->{if(!value.has(key))VideoApp.put(value,key,p.optDouble("value",p.optDouble("min",0)));slider(key,title,(float)p.optDouble("min",0),(float)p.optDouble("max",1),"");}
    case "bool"->{if(!value.has(key))VideoApp.put(value,key,dev.workshopvideo.core.PropertyCondition.truthy(p.opt("value")));toggle(key,title,true);}
    case "color"->{if(!value.has(key))VideoApp.put(value,key,dev.workshopvideo.core.UserValues.hex(p.opt("value")));colorControl(key,title,dev.workshopvideo.core.UserValues.hex(p.opt("value")));}
    case "combo"->{org.json.JSONArray options=p.optJSONArray("options");if(options==null||options.length()<1)break;
     String[] keys=new String[Math.min(8,options.length())],labels=new String[keys.length];
     for(int i=0;i<keys.length;i++){keys[i]=options.optJSONArray(i).optString(1);labels[i]=options.optJSONArray(i).optString(0);}
     if(!value.has(key))VideoApp.put(value,key,String.valueOf(p.opt("value")));choice(key,title,keys,labels);}
    case "scenetexture"->pictureControl(key,title);
    case "textinput"->{if(!value.has(key))VideoApp.put(value,key,p.optString("value",""));textControl(key,title);}
    default->{String shown=String.valueOf(p.opt("value"));TextView item=label(label+(shown.isEmpty()||shown.equals("null")?"":"："+shown)+(p.optBoolean("unused")?"":"（手机上暂不生效）"),13,Ui.MUTED);control(item);}
   }
  }
 }
 /** Author groups the user opened in this session. */
 private final java.util.Set<String> expanded=new java.util.HashSet<>();
 void sceneLayers(float[] depths,String[] names,boolean xray){layerDepths=depths;layerNames=names;this.xray=xray;layerCount=Math.min(32,depths.length);build();}
 private void layerControls(){
  if(xray){heading("触摸透视");toggle("xray","按住并拖动查看另一层图片",true);if(value.optBoolean("xray",true))slider("xraySize","揭示范围",.01f,1,"");}
  for(int i=0;i<layerCount;i++){heading(layerNames!=null&&i<layerNames.length?layerNames[i]:"图层 "+(i+1));if(!value.has("depth"+i))VideoApp.put(value,"depth"+i,layerDepths[i]);if(!value.has("opacity"+i))VideoApp.put(value,"opacity"+i,1);if(!value.has("layerX"+i))VideoApp.put(value,"layerX"+i,0);if(!value.has("layerY"+i))VideoApp.put(value,"layerY"+i,0);slider("depth"+i,"视差深度",-2,2,"");slider("opacity"+i,"不透明度",0,1,"");slider("layerX"+i,"水平移动",-1,1,"");slider("layerY"+i,"垂直移动",-1,1,"");}
 }
 private void choice(String key,String title,String[] keys,String[] labels){
  TextView heading=label(title,13,Ui.TEXT);heading.setPadding(0,dp(10),0,dp(8));control(heading);LinearLayout strip=new LinearLayout(activity);strip.setPadding(dp(4),dp(4),dp(4),dp(4));strip.setBackground(AppSheet.background(Ui.BG,dp(14)));
  for(int i=0;i<keys.length;i++){String item=keys[i];boolean selected=item.equals(value.optString(key,keys[0]));boolean available=!key.equals("motionMode")||!item.equals("device")||hasSensor;TextView option=label(labels[i],13,selected?Ui.ACCENT:Ui.MUTED);if(selected)option.setTypeface(null,android.graphics.Typeface.BOLD);option.setGravity(Gravity.CENTER);option.setMinHeight(dp(42));option.setPadding(dp(4),dp(8),dp(4),dp(8));option.setMaxLines(2);option.setSelected(selected);option.setEnabled(available);option.setAlpha(available?1:.35f);option.setTag("choice-"+key+"-"+item);option.setContentDescription(title+"，"+labels[i]+(selected?"，已选择":""));option.setBackground(new android.graphics.drawable.RippleDrawable(ColorStateList.valueOf(0x22ffffff),AppSheet.background(selected?Ui.RAISED:0x00000000,dp(10)),null));option.setOnClickListener(v->{VideoApp.put(value,key,item);if(key.equals("motionMode")){VideoApp.put(value,"pan",item.equals("scroll"));VideoApp.put(value,"gyro",item.equals("device"));VideoApp.put(value,"touch",false);VideoApp.put(value,"ripple",false);VideoApp.put(value,"particles",false);}build();changed();});LayoutParams p=new LayoutParams(0,-2,1);if(i>0)p.leftMargin=dp(3);strip.addView(option,p);}
  control(strip);
  String hint="";if(key.equals("alignment"))hint=switch(value.optString(key)){case "fill"->"拉伸画面，填满整个屏幕";case "free"->"保持完整画面，可自由调整缩放与位置";default->"按原比例铺满屏幕，裁切超出部分";};
  if(key.equals("motionMode"))hint=switch(value.optString(key)){case "device"->"倾斜手机时，画面跟随产生视差";case "scroll"->"左右翻动桌面时，背景跟随移动";default->"保持画面位置";};
  // Options speak for themselves; no descriptive line under each choice.
 }
 private void backgroundColor(){colorControl("backgroundColor","背景颜色","#000000");}
 /** Free text from the author's settings (a textinput property), applied as it is typed. */
 /** Activities that can ask the system for a picture (scenetexture settings). */
 interface PicturePicker{void pickPicture(java.util.function.Consumer<android.net.Uri> done);}
 /** A scenetexture setting: the author's picture, or one chosen from the phone (stored privately by UserPictures). */
 private void pictureControl(String key,String title){
  TextView heading=label(title,13,Ui.TEXT);heading.setPadding(0,dp(10),0,dp(6));control(heading);
  boolean chosen=!value.optString(key,"").isEmpty()&&new java.io.File(value.optString(key)).isFile();
  TextView state=label(chosen?"已选择手机里的图片":"使用作品自带的图片",12,Ui.MUTED);control(state);
  LinearLayout row=new LinearLayout(activity);
  Button pick=Ui.button(activity,chosen?"更换图片":"选择图片",Ui.Kind.SECONDARY,()->{
   if(!(activity instanceof PicturePicker picker)){android.widget.Toast.makeText(activity,"这里不能选择图片。",android.widget.Toast.LENGTH_SHORT).show();return;}
   picker.pickPicture(uri->{if(uri==null)return;android.content.Context app=activity.getApplicationContext();
    new Thread(()->{try{java.io.File file=UserPictures.importPicture(app,uri);activity.runOnUiThread(()->{VideoApp.put(value,key,file.getAbsolutePath());build();changed();});}
     catch(Exception|OutOfMemoryError failed){activity.runOnUiThread(()->android.widget.Toast.makeText(activity,"无法读取这张图片。",android.widget.Toast.LENGTH_SHORT).show());}},"user-picture").start();});
  });pick.setTag("picture-"+key);
  LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(0,dp(44),1);row.addView(pick,pp);
  if(chosen){Button clear=Ui.button(activity,"恢复默认",Ui.Kind.GHOST,()->{VideoApp.put(value,key,"");build();changed();});LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,dp(44),1);cp.leftMargin=dp(8);row.addView(clear,cp);}
  control(row);
 }
 private void textControl(String key,String title){
  TextView heading=label(title,13,Ui.TEXT);heading.setPadding(0,dp(10),0,dp(6));control(heading);
  android.widget.EditText field=new android.widget.EditText(activity);field.setText(value.optString(key,""));field.setTextSize(14);field.setTextColor(Ui.TEXT);field.setHintTextColor(Ui.MUTED);field.setTag("text-"+key);
  field.setPadding(dp(12),dp(10),dp(12),dp(10));field.setBackground(AppSheet.background(Ui.RAISED,dp(14)));field.setMaxLines(4);
  field.addTextChangedListener(new android.text.TextWatcher(){
   @Override public void beforeTextChanged(CharSequence s,int a,int b,int c){}
   @Override public void onTextChanged(CharSequence s,int a,int b,int c){}
   @Override public void afterTextChanged(android.text.Editable s){VideoApp.put(value,key,s.toString());changed();}
  });
  control(field);
 }
 private void colorControl(String key,String title,String defaultColor){
  LinearLayout row=new LinearLayout(activity);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(10),dp(10),dp(12),dp(10));row.setBackground(Ui.pressable(AppSheet.background(Ui.RAISED,dp(14)),0x22a9efd5,dp(14)));row.setTag(key.equals("backgroundColor")?"background-color":"effect-color");View chip=new View(activity);row.addView(chip,new LayoutParams(dp(42),dp(42)));LinearLayout words=new LinearLayout(activity);words.setOrientation(VERTICAL);words.setPadding(dp(12),0,0,0);TextView heading=label(title,13,Ui.TEXT);heading.setPadding(0,0,0,dp(4));words.addView(heading);TextView colorName=label("",11,Ui.MUTED);colorName.setPadding(0,0,0,0);words.addView(colorName);row.addView(words,new LayoutParams(0,-2,1));TextView arrow=label("›",24,Ui.MUTED);arrow.setPadding(dp(8),0,0,0);row.addView(arrow);
  Runnable update=()->{int selected=Color.parseColor(value.optString(key,defaultColor));android.graphics.drawable.GradientDrawable bg=AppSheet.background(selected,dp(12));bg.setStroke(dp(1),0xff4a596b);chip.setBackground(bg);colorName.setText(WallpaperColorPicker.name(selected));};update.run();
  row.setOnClickListener(v->{int original=Color.parseColor(value.optString(key,defaultColor));boolean[] committed={false};AppSheet sheet=new AppSheet(activity,title,"选择色块，或在面板上自由调色",null);WallpaperColorPicker picker=new WallpaperColorPicker(activity,original,color->{VideoApp.put(value,key,String.format(Locale.ROOT,"#%06X",color&0xffffff));update.run();changed();});sheet.field(picker);sheet.dialog.setOnDismissListener(d->{if(!committed[0]){VideoApp.put(value,key,String.format(Locale.ROOT,"#%06X",original&0xffffff));update.run();changed();}});sheet.footerButton("取消",false,()->sheet.dialog.dismiss());sheet.footerButton("使用此颜色",true,()->{committed[0]=true;sheet.dialog.dismiss();});sheet.show();});LayoutParams rowMargin=new LayoutParams(-1,-2);rowMargin.topMargin=dp(12);control(row,rowMargin);
 }
 private void buildVideo(){
  removeAllViews();card=null;heading("运动响应");choice("motionMode","响应方式",new String[]{"disabled","device","scroll"},new String[]{"关闭","设备倾斜","桌面滚动"});if(value.optString("motionMode").equals("device"))slider("strength","运动强度",0,1,"");
  if(value.optString("motionMode").equals("scroll")){choice("pageMode","背景分段",new String[]{"auto","manual"},new String[]{"自动","自定义"});if(value.optString("pageMode").equals("manual"))slider("fallbackPages","分段页数",1,12," 页");}
  heading("播放与构图");toggle("flip","水平镜像",true);slider("speed","播放速度",.25f,2,"×");choice("alignment","画面适配",new String[]{"cover","fill","free"},new String[]{"铺满","拉伸","自由"});if(value.optString("alignment").equals("free")){slider("zoom","缩放",1,2.5f,"×");slider("x","水平位置",-.5f,.5f,"");slider("y","垂直位置",-.5f,.5f,"");}backgroundColor();
  heading("颜色调节");toggleRebuild("showColorOptions","调整画面颜色");if(value.optBoolean("showColorOptions",true)){slider("brightness","亮度",.2f,1.8f,"×");slider("contrast","对比度",0,2,"×");slider("saturation","饱和度",0,2,"×");slider("hue","色相",-180,180,"°");}
  card=null;addReset("重置视频设置",()->value=EffectOptions.video(null));
 }
 private void addReset(String title,Runnable defaults){Button reset=Ui.button(activity,title,Ui.Kind.GHOST,()->{defaults.run();build();changed();});reset.setTextColor(Ui.MUTED);reset.setCompoundDrawablesRelativeWithIntrinsicBounds(new Ui.Icon("refresh",Ui.MUTED,dp(16)),null,null,null);reset.setCompoundDrawablePadding(dp(6));LayoutParams p=new LayoutParams(-2,dp(48));p.gravity=Gravity.CENTER_HORIZONTAL;p.topMargin=dp(12);addView(reset,p);}
 private void toggleRebuild(String key,String title){Switch control=new Switch(activity);control.setText(title);control.setTextColor(Ui.TEXT);control.setTextSize(13);control.setThumbTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{Ui.ACCENT,0xff81909f}));control.setTrackTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{0xff355c51,0xff303d4b}));control.setMinHeight(dp(46));control.setChecked(value.optBoolean(key,key.equals("showColorOptions")));control.setOnCheckedChangeListener((v,on)->{VideoApp.put(value,key,on);build();changed();});control(control);}
 private void build(){if(kind.equals("video")){buildVideo();return;}removeAllViews();card=null;if(kind.equals("scene"))propertyControls();
  heading("体感与拖动");toggle("gyro",hasSensor?"随手机倾斜产生视差":"此设备暂无可用姿态传感器",hasSensor);if(value.optBoolean("gyro")&&hasSensor){slider("strength","倾斜强度",0,1,"");slider("smooth","响应平滑时间",.03f,1," 秒");toggle("reverseX","反转左右方向",true);toggle("reverseY","反转上下方向",true);}toggle("touch","触摸拖动画面",true);if(value.optBoolean("touch")&&!value.optBoolean("pan"))slider("touchStrength","拖动幅度",0,1,"");else if(value.optBoolean("touch")&&value.optBoolean("pan"))control(label("拖动用于桌面翻页",11,Ui.MUTED));
  heading("桌面翻页");toggle("pan","桌面翻页时跟随滑动",true);if(value.optBoolean("pan")){choice("pageMode","背景分段",new String[]{"auto","manual"},new String[]{"自动","自定义"});if(value.optString("pageMode").equals("manual"))slider("fallbackPages","分段页数",1,12," 页");}
  heading("构图与颜色");slider("zoom","缩放",1,2.5f,"×");slider("x","水平位置",-.5f,.5f,"");slider("y","垂直位置",-.5f,.5f,"");slider("rotation","画面旋转",-15,15,"°");slider("brightness","亮度",.2f,1.8f,"×");slider("contrast","对比度",0,2,"×");slider("saturation","饱和度",0,2,"×");slider("hue","色相",-180,180,"°");if(kind.equals("scene")&&layerCount>1)slider("layerDepth","分层视差深度",0,2,"×");
  heading("触摸与叠加");toggle("ripple","触摸波纹",true);toggle("particles","漂浮粒子",true);if(value.optBoolean("particles"))slider("particleCount","粒子数量",4,100,"");if(value.optBoolean("ripple")||value.optBoolean("particles"))colorControl("color","波纹与粒子颜色","#a9efd5");if(value.optBoolean("particles")||kind.equals("animated"))slider("speed",kind.equals("animated")?"动画速度":"粒子运动速度",.25f,2,"×");slider("fps","绘制帧率",15,120," 帧/秒");
  if(kind.equals("scene"))layerControls();card=null;addReset("恢复默认效果",()->value=EffectOptions.normalize(null));
 }
}
