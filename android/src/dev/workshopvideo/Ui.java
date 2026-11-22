package dev.workshopvideo;
import android.graphics.*;
import android.graphics.drawable.*;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.TextView;
/** Small vector icon family shared by navigation and cards. */
public final class Ui {
    public static final int BG=0xff0b1018,PANEL=0xff151e2a,TEXT=0xffedf3f8,MUTED=0xff97a8ba,ACCENT=0xffa9efd5;
    /** GridView uses one cell as its row-height reference. Reserve the same two
     * text lines in every card, measured from the current font rather than dp. */
    public static void cardTitle(TextView title){
        title.setLines(2);
        title.setGravity(Gravity.TOP|Gravity.START);
        title.setEllipsize(TextUtils.TruncateAt.END);
    }
    public static final class Icon extends Drawable {
        private final String name;private final Paint paint=new Paint(3);private final int size;private int color;
        public Icon(String name,int color,int size){this.name=name;this.color=color;this.size=size;setBounds(0,0,size,size);}
        public int getIntrinsicWidth(){return size;}public int getIntrinsicHeight(){return size;}
        public void draw(Canvas canvas){canvas.save();canvas.translate(getBounds().left,getBounds().top);canvas.scale(getBounds().width()/24f,getBounds().height()/24f);paint.setColor(color);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.7f);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);Path p=new Path();
            if(name.startsWith("heart")){p.moveTo(12,20);p.cubicTo(3,14,1,9,4,5.5f);p.cubicTo(7,2,10,4,12,6);p.cubicTo(14,4,17,2,20,5.5f);p.cubicTo(23,9,21,14,12,20);if(name.equals("heartFilled"))paint.setStyle(Paint.Style.FILL);canvas.drawPath(p,paint);}
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
            else {canvas.drawCircle(12,12,9,paint);p.moveTo(16,7);p.lineTo(14,14);p.lineTo(7,17);p.lineTo(10,10);p.close();canvas.drawPath(p,paint);}
            canvas.restore();
        }
        public void setAlpha(int alpha){paint.setAlpha(alpha);}public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
}
