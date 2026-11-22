package dev.workshopvideo;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import java.util.function.IntConsumer;

/** Visual HSV controls and named swatches, without exposing storage color codes. */
final class WallpaperColorPicker extends LinearLayout {
    private static final int[] COLORS={0xff000000,0xffffffff,0xff182433,0xff31514b,0xff452a3c,0xff40392a,
        0xffa9efd5,0xff8ebdff,0xffffadc4,0xffffd48e,0xffb7a4ed,0xff9aa7b4};
    private static final String[] NAMES={"墨黑","纯白","深海","松绿","莓紫","暖棕","薄荷","晴蓝","樱粉","暖金","浅紫","雾灰"};
    private final float[] hsv=new float[3];
    private final IntConsumer changed;
    private final View preview;
    private final TextView caption;
    private final Spectrum spectrum;
    private final Hue hue;
    private final TextView[] swatches=new TextView[COLORS.length];

    WallpaperColorPicker(Context context,int initial,IntConsumer changed){
        super(context);this.changed=changed;setOrientation(VERTICAL);Color.colorToHSV(initial,hsv);
        preview=new View(context);preview.setContentDescription("当前颜色预览");addView(preview,new LayoutParams(-1,dp(42)));
        caption=text("",12,Ui.MUTED);caption.setPadding(0,dp(8),0,dp(12));addView(caption);
        spectrum=new Spectrum(context);spectrum.setContentDescription("饱和度与明暗");addView(spectrum,new LayoutParams(-1,dp(164)));
        hue=new Hue(context);hue.setContentDescription("色相");LayoutParams hp=new LayoutParams(-1,dp(36));hp.topMargin=dp(12);addView(hue,hp);
        TextView hint=text("横向选择浓淡，纵向调节明暗",11,Ui.MUTED);hint.setPadding(0,dp(4),0,dp(12));addView(hint);
        for(int row=0;row<2;row++){
            LinearLayout line=new LinearLayout(context);
            for(int col=0;col<6;col++){
                int index=row*6+col;TextView swatch=text("",17,Ui.TEXT);swatch.setGravity(Gravity.CENTER);swatch.setContentDescription(NAMES[index]);swatch.setTag("preset-"+index);
                swatch.setOnClickListener(v->setColor(COLORS[index]));swatches[index]=swatch;
                LayoutParams p=new LayoutParams(0,dp(42),1);p.setMargins(dp(3),dp(3),dp(3),dp(3));line.addView(swatch,p);
            }
            addView(line,new LayoutParams(-1,-2));
        }
        refresh(false);
    }
    static String name(int color){for(int i=0;i<COLORS.length;i++)if((color|0xff000000)==COLORS[i])return NAMES[i];return "自选颜色";}
    private int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private TextView text(String value,int size,int color){TextView t=new TextView(getContext());t.setText(value);t.setTextSize(size);t.setTextColor(color);return t;}
    int color(){return Color.HSVToColor(hsv);}
    void setColor(int color){Color.colorToHSV(color,hsv);refresh(true);}
    private void refresh(boolean notify){
        int selected=color();preview.setBackground(AppSheet.background(selected,dp(12)));caption.setText("当前颜色 · "+name(selected));spectrum.invalidate();hue.invalidate();
        for(int i=0;i<swatches.length;i++){if(swatches[i]==null)continue;boolean on=COLORS[i]==selected;GradientDrawable bg=AppSheet.background(COLORS[i],dp(12));bg.setStroke(dp(on?3:1),on?Ui.ACCENT:0xff485463);swatches[i].setBackground(bg);swatches[i].setSelected(on);swatches[i].setText(on?"✓":"");swatches[i].setTextColor(Color.luminance(COLORS[i])>.5?0xff17252c:Color.WHITE);}
        if(notify)changed.accept(selected);
    }
    private abstract class DragColor extends View {
        final Paint paint=new Paint(3);final RectF area=new RectF();
        DragColor(Context c){super(c);setLayerType(View.LAYER_TYPE_SOFTWARE,null);setFocusable(true);}
        @Override public boolean onTouchEvent(MotionEvent e){
            int action=e.getActionMasked();if(action==MotionEvent.ACTION_CANCEL){getParent().requestDisallowInterceptTouchEvent(false);return true;}
            if(action!=MotionEvent.ACTION_DOWN&&action!=MotionEvent.ACTION_MOVE&&action!=MotionEvent.ACTION_UP)return false;
            getParent().requestDisallowInterceptTouchEvent(action!=MotionEvent.ACTION_UP);choose(e.getX(),e.getY());refresh(true);if(action==MotionEvent.ACTION_UP)performClick();return true;
        }
        @Override public boolean performClick(){super.performClick();return true;}
        abstract void choose(float x,float y);
        float unit(float value){return Math.max(0,Math.min(1,value));}
    }
    private final class Spectrum extends DragColor {
        Spectrum(Context c){super(c);}
        @Override protected void onDraw(Canvas canvas){
            area.set(dp(10),dp(10),getWidth()-dp(10),getHeight()-dp(10));if(area.width()<=0||area.height()<=0)return;
            canvas.save();Path clip=new Path();clip.addRoundRect(area,dp(12),dp(12),Path.Direction.CW);canvas.clipPath(clip);
            paint.setStyle(Paint.Style.FILL);paint.setColor(Color.HSVToColor(new float[]{hsv[0],1,1}));paint.setShader(null);canvas.drawRect(area,paint);
            paint.setShader(new LinearGradient(area.left,0,area.right,0,Color.WHITE,Color.TRANSPARENT,Shader.TileMode.CLAMP));canvas.drawRect(area,paint);
            paint.setShader(new LinearGradient(0,area.top,0,area.bottom,Color.TRANSPARENT,Color.BLACK,Shader.TileMode.CLAMP));canvas.drawRect(area,paint);paint.setShader(null);canvas.restore();
            float x=area.left+hsv[1]*area.width(),y=area.top+(1-hsv[2])*area.height();paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(4));paint.setColor(0xaa101820);canvas.drawCircle(x,y,dp(7),paint);paint.setStrokeWidth(dp(2));paint.setColor(Color.WHITE);canvas.drawCircle(x,y,dp(7),paint);paint.setStyle(Paint.Style.FILL);
        }
        void choose(float x,float y){hsv[1]=unit((x-dp(10))/Math.max(1,getWidth()-dp(20)));hsv[2]=1-unit((y-dp(10))/Math.max(1,getHeight()-dp(20)));}
    }
    private final class Hue extends DragColor {
        Hue(Context c){super(c);}
        @Override protected void onDraw(Canvas canvas){
            area.set(dp(8),dp(8),getWidth()-dp(8),getHeight()-dp(8));paint.setShader(new LinearGradient(area.left,0,area.right,0,new int[]{Color.RED,Color.YELLOW,Color.GREEN,Color.CYAN,Color.BLUE,Color.MAGENTA,Color.RED},null,Shader.TileMode.CLAMP));canvas.drawRoundRect(area,dp(10),dp(10),paint);paint.setShader(null);float x=area.left+hsv[0]/360*area.width();paint.setColor(Color.HSVToColor(new float[]{hsv[0],1,1}));canvas.drawCircle(x,area.centerY(),dp(10),paint);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(3));paint.setColor(Color.WHITE);canvas.drawCircle(x,area.centerY(),dp(10),paint);paint.setStyle(Paint.Style.FILL);
        }
        void choose(float x,float y){hsv[0]=unit((x-dp(8))/Math.max(1,getWidth()-dp(16)))*359.9f;}
    }
}
