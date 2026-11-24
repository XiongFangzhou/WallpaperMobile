package dev.workshopvideo;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import dev.workshopvideo.core.WeFilters;
import java.util.HashMap;
import java.util.Map;
import java.util.Date;
import java.util.Locale;
import java.util.function.IntConsumer;
/** Design tokens, shared components and the small vector icon family. */
public final class Ui {
    public static final int BG=0xff0b1018,PANEL=0xff151e2a,TEXT=0xffedf3f8,MUTED=0xff97a8ba,ACCENT=0xffa9efd5;
    public static final int RAISED=0xff1e2937,LINE=0xff263345,SUBTLE=0xff6f8194,ACCENT_INK=0xff0f3127,ACCENT_SOFT=0x24a9efd5,DANGER=0xffff9caa,DANGER_INK=0xff3d1219,SCRIM=0xd90b1018;
    public enum Kind{PRIMARY,SECONDARY,GHOST,DANGER}
    private Ui(){}

    public static int dp(Context context,float value){return Math.round(value*context.getResources().getDisplayMetrics().density);}
    public static TextView text(Context context,CharSequence value,float size,int color){TextView text=new TextView(context);text.setText(value);text.setTextSize(size);text.setTextColor(color);return text;}
    public static TextView heading(Context context,CharSequence value,float size){TextView text=text(context,value,size,TEXT);text.setTypeface(null,Typeface.BOLD);return text;}
    /** GridView uses one cell as its row-height reference. Reserve the same two
     * text lines in every card, measured from the current font rather than dp. */
    public static void cardTitle(TextView title){
        title.setLines(2);
        title.setGravity(Gravity.TOP|Gravity.START);
        title.setEllipsize(TextUtils.TruncateAt.END);
    }

    public static GradientDrawable round(int color,float radius){GradientDrawable shape=new GradientDrawable();shape.setColor(color);shape.setCornerRadius(radius);return shape;}
    public static GradientDrawable outlined(int color,int stroke,float radius,int width){GradientDrawable shape=round(color,radius);shape.setStroke(width,stroke);return shape;}
    /** Ripple bounded by an explicit mask so transparent backgrounds still show feedback. */
    public static Drawable pressable(Drawable base,int ripple,float radius){return new RippleDrawable(ColorStateList.valueOf(ripple),base,round(Color.WHITE,radius));}
    public static Drawable card(Context context){return outlined(PANEL,0xff202c3b,dp(context,18),dp(context,1));}
    public static Drawable coverShade(){return new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,new int[]{0xb30b1018,0x000b1018});}
    public static void enable(View view,boolean enabled){view.setEnabled(enabled);view.setAlpha(enabled?1f:.42f);}

    public static Button button(Context context,String label,Kind kind,Runnable action){
        Button button=new Button(context);button.setText(label);button.setAllCaps(false);button.setTextSize(13);button.setSingleLine(true);button.setGravity(Gravity.CENTER);
        button.setMinWidth(0);button.setMinimumWidth(0);button.setMinHeight(0);button.setMinimumHeight(0);button.setStateListAnimator(null);button.setPadding(dp(context,14),0,dp(context,14),0);
        style(button,kind);if(action!=null)button.setOnClickListener(v->action.run());return button;
    }
    public static void style(Button button,Kind kind){
        Context context=button.getContext();float radius=dp(context,14);int line=dp(context,1);Drawable base;int color;
        switch(kind){
            case PRIMARY->{base=round(ACCENT,radius);color=ACCENT_INK;}
            case DANGER->{base=round(DANGER,radius);color=DANGER_INK;}
            case GHOST->{base=round(Color.TRANSPARENT,radius);color=ACCENT;}
            default->{base=outlined(RAISED,LINE,radius,line);color=TEXT;}
        }
        button.setTextColor(color);button.setTypeface(null,kind==Kind.SECONDARY||kind==Kind.GHOST?Typeface.NORMAL:Typeface.BOLD);
        button.setBackground(pressable(base,kind==Kind.PRIMARY||kind==Kind.DANGER?0x33000000:0x22ffffff,radius));
    }
    public static TextView chip(Context context,String label,boolean selected,Runnable action){
        TextView chip=text(context,label,12,MUTED);chip.setGravity(Gravity.CENTER);chip.setSingleLine(true);chip.setPadding(dp(context,14),0,dp(context,14),0);chip.setMinHeight(dp(context,34));
        select(chip,selected);if(action!=null)chip.setOnClickListener(v->action.run());return chip;
    }
    public static void select(TextView chip,boolean selected){
        Context context=chip.getContext();float radius=dp(context,17);chip.setSelected(selected);chip.setTextColor(selected?ACCENT:MUTED);chip.setTypeface(null,selected?Typeface.BOLD:Typeface.NORMAL);
        chip.setBackground(pressable(outlined(selected?ACCENT_SOFT:Color.TRANSPARENT,selected?0x66a9efd5:LINE,radius,dp(context,1)),0x22a9efd5,radius));
    }
    /** Equal-width segments; the selected one is raised inside a recessed track. */
    public static LinearLayout segmented(Context context,String[] names,int selected,IntConsumer pick){
        LinearLayout track=new LinearLayout(context);int inset=dp(context,4);float outer=dp(context,14),inner=dp(context,11);track.setPadding(inset,inset,inset,inset);track.setBackground(round(PANEL,outer));
        for(int i=0;i<names.length;i++){
            int index=i;boolean active=i==selected;TextView segment=text(context,names[i],13,active?TEXT:MUTED);segment.setGravity(Gravity.CENTER);segment.setSingleLine(true);segment.setSelected(active);
            segment.setTypeface(null,active?Typeface.BOLD:Typeface.NORMAL);segment.setBackground(pressable(round(active?RAISED:Color.TRANSPARENT,inner),0x18ffffff,inner));segment.setOnClickListener(v->pick.accept(index));
            track.addView(segment,new LinearLayout.LayoutParams(0,dp(context,36),1));
        }
        return track;
    }
    public static EditText input(Context context,String hint){
        EditText input=new EditText(context);input.setTextSize(14);input.setTextColor(TEXT);input.setHintTextColor(SUBTLE);input.setHint(hint);input.setSingleLine(true);input.setGravity(Gravity.CENTER_VERTICAL|Gravity.START);
        int padding=dp(context,14);input.setPadding(padding,dp(context,12),padding,dp(context,12));
        float radius=dp(context,14);int line=dp(context,1);StateListDrawable states=new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused},outlined(PANEL,0x99a9efd5,radius,line));states.addState(new int[]{},outlined(PANEL,LINE,radius,line));input.setBackground(states);return input;
    }
    public static TextView badge(Context context,String label,int color){
        TextView badge=text(context,label,10,color);badge.setTypeface(null,Typeface.BOLD);badge.setSingleLine(true);badge.setPadding(dp(context,8),dp(context,3),dp(context,8),dp(context,3));badge.setBackground(round(SCRIM,dp(context,8)));return badge;
    }
    /** Centered icon, title and optional caption for lists without content. */
    public static LinearLayout empty(Context context,String icon,String title,String caption){
        LinearLayout box=new LinearLayout(context);box.setOrientation(LinearLayout.VERTICAL);box.setGravity(Gravity.CENTER_HORIZONTAL);box.setPadding(dp(context,24),dp(context,44),dp(context,24),dp(context,44));
        int size=dp(context,64);ImageView image=new ImageView(context);image.setImageDrawable(new Icon(icon,ACCENT,dp(context,28)));image.setScaleType(ImageView.ScaleType.CENTER);image.setBackground(round(ACCENT_SOFT,size/2f));image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);box.addView(image,new LinearLayout.LayoutParams(size,size));
        TextView heading=heading(context,title,16);heading.setGravity(Gravity.CENTER);heading.setPadding(0,dp(context,16),0,0);box.addView(heading);
        TextView hint=text(context,caption==null?"":caption,13,MUTED);hint.setGravity(Gravity.CENTER);hint.setLineSpacing(dp(context,3),1);hint.setPadding(0,dp(context,6),0,0);hint.setVisibility(caption==null||caption.isEmpty()?View.GONE:View.VISIBLE);box.addView(hint);
        return box;
    }
    /** Update an {@link #empty} box in place: child 1 is the title, child 2 the caption. */
    public static void empty(LinearLayout box,String title,String caption){((TextView)box.getChildAt(1)).setText(title);TextView hint=(TextView)box.getChildAt(2);hint.setText(caption==null?"":caption);hint.setVisibility(caption==null||caption.isEmpty()?View.GONE:View.VISIBLE);}
    /** Square icon-only button; the description is announced by screen readers. */
    public static ImageView iconButton(Context context,String icon,String description,Runnable action){
        ImageView button=new ImageView(context);button.setImageDrawable(new Icon(icon,TEXT,dp(context,20)));button.setScaleType(ImageView.ScaleType.CENTER);button.setContentDescription(description);
        float radius=dp(context,14);button.setBackground(pressable(outlined(RAISED,LINE,radius,dp(context,1)),0x22ffffff,radius));if(action!=null)button.setOnClickListener(v->action.run());return button;
    }
    /** Tinted note with a leading icon for explanations that should not read like body text. */
    public static LinearLayout callout(Context context,String icon,String message,int color){
        LinearLayout box=new LinearLayout(context);box.setGravity(Gravity.TOP);int pad=dp(context,12);box.setPadding(pad,pad,pad+dp(context,2),pad);box.setBackground(round((color&0x00ffffff)|0x1f000000,dp(context,14)));
        ImageView image=new ImageView(context);image.setImageDrawable(new Icon(icon,color,dp(context,18)));LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(dp(context,18),dp(context,18));ip.topMargin=dp(context,1);box.addView(image,ip);
        TextView text=text(context,message,13,TEXT);text.setLineSpacing(dp(context,3),1);text.setPadding(dp(context,10),0,0,0);box.addView(text,new LinearLayout.LayoutParams(0,-2,1));return box;
    }
    public static ProgressBar spinner(Context context){ProgressBar bar=new ProgressBar(context);bar.setIndeterminateTintList(ColorStateList.valueOf(ACCENT));return bar;}
    /** Multi-select chip with a leading check mark when chosen. */
    public static TextView toggleChip(Context context,String label,boolean selected,java.util.function.Consumer<Boolean> changed){
        TextView chip=chip(context,label,selected,null);chip.setMinHeight(dp(context,36));mark(chip,selected);
        chip.setOnClickListener(v->{boolean next=!chip.isSelected();select(chip,next);mark(chip,next);changed.accept(next);});return chip;
    }
    public static void toggle(TextView chip,boolean selected){select(chip,selected);mark(chip,selected);}
    private static void mark(TextView chip,boolean selected){Context context=chip.getContext();chip.setCompoundDrawablesRelativeWithIntrinsicBounds(selected?new Icon("check",ACCENT,dp(context,14)):null,null,null,null);chip.setCompoundDrawablePadding(dp(context,4));chip.setContentDescription(chip.getText()+(selected?"，已选":"，未选"));}
    /** Wrapping row layout for chips and tags. */
    public static final class Flow extends ViewGroup {
        private final int gap;
        public Flow(Context context,int gap){super(context);this.gap=gap;}
        @Override protected void onMeasure(int widthSpec,int heightSpec){
            int width=MeasureSpec.getSize(widthSpec)-getPaddingLeft()-getPaddingRight(),x=0,y=0,line=0;
            for(int i=0;i<getChildCount();i++){View child=getChildAt(i);if(child.getVisibility()==GONE)continue;measureChild(child,MeasureSpec.makeMeasureSpec(width,MeasureSpec.AT_MOST),MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));
                if(x>0&&x+child.getMeasuredWidth()>width){x=0;y+=line+gap;line=0;}x+=child.getMeasuredWidth()+gap;line=Math.max(line,child.getMeasuredHeight());}
            setMeasuredDimension(MeasureSpec.getSize(widthSpec),resolveSize(y+line+getPaddingTop()+getPaddingBottom(),heightSpec));
        }
        @Override protected void onLayout(boolean changed,int l,int t,int r,int b){
            int width=r-l-getPaddingLeft()-getPaddingRight(),x=0,y=0,line=0;
            for(int i=0;i<getChildCount();i++){View child=getChildAt(i);if(child.getVisibility()==GONE)continue;int w=child.getMeasuredWidth(),h=child.getMeasuredHeight();
                if(x>0&&x+w>width){x=0;y+=line+gap;line=0;}child.layout(getPaddingLeft()+x,getPaddingTop()+y,getPaddingLeft()+x+w,getPaddingTop()+y+h);x+=w+gap;line=Math.max(line,h);}
        }
    }
    private static Map<String,String> tagNames;
    /** Chinese label for a known Steam workshop tag; unknown tags are shown unchanged. */
    public static synchronized String tagName(String tag){
        if(tagNames==null){tagNames=new HashMap<>();java.util.List<WeFilters.Group> groups=new java.util.ArrayList<>(WeFilters.CHECK_GROUPS);groups.add(WeFilters.UTILITIES);for(WeFilters.Group group:groups)for(WeFilters.Option option:group.options())tagNames.put(option.value().toLowerCase(Locale.ROOT),option.label());tagNames.put("application","应用");}
        String label=tagNames.get(tag.toLowerCase(Locale.ROOT));return label==null?tag:label;
    }
    /** Small section label used above grouped content. */
    public static TextView overline(Context context,String value){TextView text=text(context,value,12,SUBTLE);text.setTypeface(null,Typeface.BOLD);text.setLetterSpacing(.04f);return text;}

    /** Readable size; a decimal only where it carries information (1.4 GB, 9.5 MB, 128 MB). */
    public static String size(long bytes){int power=Math.max(0,Math.min(3,(int)(Math.log(Math.max(1,bytes))/Math.log(1024))));double value=bytes/Math.pow(1024,power);return String.format(Locale.CHINA,power>1&&value<10?"%.1f %s":"%.0f %s",value,new String[]{"B","KB","MB","GB"}[power]);}
    public static String date(int seconds){return new java.text.SimpleDateFormat("yyyy-MM-dd",Locale.CHINA).format(new Date(Integer.toUnsignedLong(seconds)*1000));}

    /** Five stars filled to {@code value} out of 5, so 4.6 shows the last star 60% full. */
    public static final class Stars extends Drawable {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private final float value;private final int size;
        public Stars(float value,int size){this.value=Math.max(0,Math.min(5,value));this.size=size;setBounds(0,0,getIntrinsicWidth(),size);}
        public int getIntrinsicWidth(){return size*5+size/5*4;}public int getIntrinsicHeight(){return size;}
        public void draw(Canvas canvas){
            Rect b=getBounds();int gap=size/5;Path star=new Path();
            for(int i=0;i<10;i++){double a=-Math.PI/2+i*Math.PI/5;float r=i%2==0?size/2f:size/4.6f;float x=size/2f+r*(float)Math.cos(a),y=size*.53f+r*(float)Math.sin(a);if(i==0)star.moveTo(x,y);else star.lineTo(x,y);}star.close();
            for(int i=0;i<5;i++){
                canvas.save();canvas.translate(b.left+i*(size+gap),b.top);paint.setColor(0x40ffffff);canvas.drawPath(star,paint);
                float fill=Math.max(0,Math.min(1,value-i));if(fill>0){canvas.clipRect(0,0,size*fill,size);paint.setColor(0xffffc857);canvas.drawPath(star,paint);}
                canvas.restore();
            }
        }
        public void setAlpha(int alpha){paint.setAlpha(alpha);}public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
    public static final class Icon extends Drawable {
        private final String name;private final Paint paint=new Paint(3);private final int size;private int color;
        public Icon(String name,int color,int size){this.name=name;this.color=color;this.size=size;setBounds(0,0,size,size);}
        public int getIntrinsicWidth(){return size;}public int getIntrinsicHeight(){return size;}
        public void draw(Canvas canvas){canvas.save();canvas.translate(getBounds().left,getBounds().top);canvas.scale(getBounds().width()/24f,getBounds().height()/24f);paint.setColor(color);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.7f);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);Path p=new Path();
            if(name.equals("settings")){canvas.drawCircle(12,12,3.2f,paint);canvas.drawCircle(12,12,7.2f,paint);for(int i=0;i<8;i++){double a=i*Math.PI/4;canvas.drawLine(12+7.2f*(float)Math.cos(a),12+7.2f*(float)Math.sin(a),12+9.5f*(float)Math.cos(a),12+9.5f*(float)Math.sin(a),paint);}}
            else if(name.startsWith("heart")){p.moveTo(12,20);p.cubicTo(3,14,1,9,4,5.5f);p.cubicTo(7,2,10,4,12,6);p.cubicTo(14,4,17,2,20,5.5f);p.cubicTo(23,9,21,14,12,20);if(name.equals("heartFilled"))paint.setStyle(Paint.Style.FILL);canvas.drawPath(p,paint);}
            else if(name.equals("account")){canvas.drawCircle(12,7,3.4f,paint);canvas.drawArc(new RectF(4,13,20,28),180,180,false,paint);}
            else if(name.equals("logout")){p.moveTo(10,4);p.lineTo(4,4);p.lineTo(4,20);p.lineTo(10,20);canvas.drawPath(p,paint);canvas.drawLine(10,12,21,12,paint);p.reset();p.moveTo(17,8);p.lineTo(21,12);p.lineTo(17,16);canvas.drawPath(p,paint);}
            else if(name.equals("info")){canvas.drawCircle(12,12,9,paint);canvas.drawCircle(12,7,0.7f,paint);canvas.drawLine(12,11,12,17,paint);}
            else if(name.equals("download")){canvas.drawLine(12,3,12,15,paint);p.moveTo(7,10);p.lineTo(12,15);p.lineTo(17,10);canvas.drawPath(p,paint);p.reset();p.moveTo(4,15);p.lineTo(4,21);p.lineTo(20,21);p.lineTo(20,15);canvas.drawPath(p,paint);}
            else if(name.equals("shield")){p.moveTo(12,2);p.lineTo(20,6);p.lineTo(19,15);p.quadTo(17,20,12,22);p.quadTo(7,20,5,15);p.lineTo(4,6);p.close();canvas.drawPath(p,paint);p.reset();p.moveTo(8,12);p.lineTo(11,15);p.lineTo(16,9);canvas.drawPath(p,paint);}
            else if(name.equals("trash")){canvas.drawLine(5,6,19,6,paint);canvas.drawLine(9,3,15,3,paint);p.moveTo(7,6);p.lineTo(8,21);p.lineTo(16,21);p.lineTo(17,6);canvas.drawPath(p,paint);canvas.drawLine(10,10,10,17,paint);canvas.drawLine(14,10,14,17,paint);}
            else if(name.equals("wallpaper")){canvas.drawRoundRect(5,2,19,22,3,3,paint);p.moveTo(6,16);p.lineTo(10,11);p.lineTo(13,15);p.lineTo(16,12);p.lineTo(18,16);canvas.drawPath(p,paint);canvas.drawCircle(14,7,1.5f,paint);}
            else if(name.equals("check")){p.moveTo(5,12);p.lineTo(10,17);p.lineTo(20,6);canvas.drawPath(p,paint);}
            else if(name.equals("close")){canvas.drawLine(6,6,18,18,paint);canvas.drawLine(6,18,18,6,paint);}
            else if(name.equals("play")){p.moveTo(8,4);p.lineTo(20,12);p.lineTo(8,20);p.close();canvas.drawPath(p,paint);}
            else if(name.equals("library")){canvas.drawRoundRect(3,6,21,21,3,3,paint);canvas.drawLine(7,3,17,3,paint);p.moveTo(10,10);p.lineTo(16,13.5f);p.lineTo(10,17);p.close();canvas.drawPath(p,paint);}
            else if(name.equals("search")){canvas.drawCircle(10.5f,10.5f,6.5f,paint);canvas.drawLine(15.5f,15.5f,20.5f,20.5f,paint);}
            else if(name.equals("filter")){p.moveTo(4,5);p.lineTo(20,5);p.lineTo(14,12.5f);p.lineTo(14,19);p.lineTo(10,21);p.lineTo(10,12.5f);p.close();canvas.drawPath(p,paint);}
            else if(name.equals("previous")){p.moveTo(15,5);p.lineTo(8,12);p.lineTo(15,19);canvas.drawPath(p,paint);}
            else if(name.equals("next")){p.moveTo(9,5);p.lineTo(16,12);p.lineTo(9,19);canvas.drawPath(p,paint);}
            else if(name.equals("fit")){p.moveTo(4,9);p.lineTo(4,4);p.lineTo(9,4);p.moveTo(15,4);p.lineTo(20,4);p.lineTo(20,9);p.moveTo(20,15);p.lineTo(20,20);p.lineTo(15,20);p.moveTo(9,20);p.lineTo(4,20);p.lineTo(4,15);canvas.drawPath(p,paint);}
            else if(name.equals("rotate")){canvas.drawRoundRect(8,3,16,21,2,2,paint);canvas.drawArc(new RectF(2,6,22,18),200,60,false,paint);canvas.drawArc(new RectF(2,6,22,18),20,60,false,paint);}
            else if(name.startsWith("thumb")){boolean down=name.equals("thumbDown")||name.equals("thumbDownFilled");canvas.save();if(down)canvas.rotate(180,12,12);
                p.moveTo(7,10);p.lineTo(10.5f,3.5f);p.quadTo(13.5f,3.5f,13,7);p.lineTo(12.5f,10);p.lineTo(18.5f,10);p.quadTo(21,10,20.5f,12.5f);p.lineTo(19,19);p.quadTo(18.5f,21,16.5f,21);p.lineTo(7,21);p.close();
                if(name.endsWith("Filled"))paint.setStyle(Paint.Style.FILL);canvas.drawPath(p,paint);paint.setStyle(Paint.Style.STROKE);canvas.drawRoundRect(3,10,7,21,1,1,paint);canvas.restore();}
            else if(name.equals("pause")){canvas.drawLine(9,5,9,19,paint);canvas.drawLine(15,5,15,19,paint);}
            else if(name.equals("refresh")){canvas.drawArc(new RectF(4.5f,4.5f,19.5f,19.5f),-60,290,false,paint);p.moveTo(19.5f,3.5f);p.lineTo(19.5f,8.5f);p.lineTo(14.5f,8.5f);canvas.drawPath(p,paint);}
            else if(name.equals("copy")){canvas.drawRoundRect(8,8,20,20,2.5f,2.5f,paint);p.moveTo(16,5);p.lineTo(16,4);p.lineTo(6,4);p.quadTo(4,4,4,6);p.lineTo(4,16);canvas.drawPath(p,paint);}
            else {canvas.drawCircle(12,12,9,paint);p.moveTo(16,7);p.lineTo(14,14);p.lineTo(7,17);p.lineTo(10,10);p.close();canvas.drawPath(p,paint);}
            canvas.restore();
        }
        public void setAlpha(int alpha){paint.setAlpha(alpha);}public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
}
