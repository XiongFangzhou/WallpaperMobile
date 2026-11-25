package dev.workshopvideo;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * A text layer (Wallpaper Engine's ITextLayer fields), drawn into the layer bitmap and redrawn when its text changes.
 * Sizes follow what Wallpaper Engine saves for text objects: the box is the text block, one row being the font's line
 * height at pointsize × 25/6 scene units per em, and as wide as the longest row's advance.
 */
final class SceneText {
    static final float UNITS_PER_POINT=25f/6f;
    private static final int MAX_EDGE=2048,MAX_CHARS=4096;
    /** CJK fonts commonly run to tens of megabytes. */
    static final int MAX_FONT_BYTES=48*1024*1024;
    String value="",align="center",valign="center";
    /** Author properties the text and its point size are bound to, or null. */
    String valueProperty,sizeProperty;
    float pointsize=32,maxwidth=500,density=1;int maxrows=1;boolean limitrows,limitwidth,opaque;
    /** Text colour baked into the bitmap when the background is opaque; otherwise text is white and the layer tint colours it. */
    float[] color={1,1,1},background={0,0,0};
    Typeface typeface=Typeface.DEFAULT;
    /** The font while converting: its file and the scene's font name. */
    byte[] fontData;String fontName;
    /** Colour last reported by the scripts; only their changes recolour the text, so a colour chosen in settings stays. */
    float[] scriptColor;
    /** Size of the block last drawn, in scene units. */
    float blockW,blockH;
    private String drawn;

    static SceneText from(JSONObject o){
        SceneText t=new SceneText();
        t.value=o.optString("value","");t.align=o.optString("align","center");t.valign=o.optString("valign","center");
        t.valueProperty=o.has("valueProperty")?o.optString("valueProperty"):null;t.sizeProperty=o.has("sizeProperty")?o.optString("sizeProperty"):null;
        t.pointsize=(float)o.optDouble("pointsize",32);t.maxwidth=(float)o.optDouble("maxwidth",500);t.maxrows=o.optInt("maxrows",1);
        t.limitrows=o.optBoolean("limitrows");t.limitwidth=o.optBoolean("limitwidth");t.opaque=o.optBoolean("opaque");t.density=(float)o.optDouble("density",1);
        t.color=WallpaperContent.vector(o.opt("color"),1,1,1);t.background=WallpaperContent.vector(o.opt("background"),0,0,0);
        return t;
    }
    JSONObject json()throws JSONException{
        JSONObject o=new JSONObject().put("value",value).put("align",align).put("valign",valign).put("pointsize",pointsize).put("maxwidth",maxwidth).put("maxrows",maxrows)
            .put("limitrows",limitrows).put("limitwidth",limitwidth).put("opaque",opaque).put("density",density)
            .put("color",color[0]+" "+color[1]+" "+color[2]).put("background",background[0]+" "+background[1]+" "+background[2]);
        if(valueProperty!=null)o.put("valueProperty",valueProperty);if(sizeProperty!=null)o.put("sizeProperty",sizeProperty);
        return o;
    }

    /** Whether the text or its size changed since the last draw. */
    boolean stale(){return !key().equals(drawn);}
    /** Marks the current text as drawn (the converter's bitmap already shows it). */
    void drawn(float w,float h){drawn=key();blockW=w;blockH=h;}
    private String key(){return value+(char)0+pointsize;}

    Bitmap draw(){
        Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.SUBPIXEL_TEXT_FLAG);paint.setTypeface(typeface);
        float em=Math.max(.1f,Math.min(4096,pointsize*UNITS_PER_POINT));paint.setTextSize(em);
        Paint.FontMetrics fm=paint.getFontMetrics();float line=fm.descent-fm.ascent+fm.leading;
        List<String> rows=rows(paint);
        float w=0;for(String r:rows)w=Math.max(w,paint.measureText(r));
        blockW=Math.max(1,w);blockH=Math.max(1,line*rows.size());
        float scale=Math.max(.01f,Math.min(density,MAX_EDGE/Math.max(blockW,blockH)));
        Bitmap b=Bitmap.createBitmap(Math.max(1,(int)Math.ceil(blockW*scale)),Math.max(1,(int)Math.ceil(blockH*scale)),Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(b);c.scale(scale,scale);
        if(opaque)c.drawColor(rgb(background));
        paint.setColor(opaque?rgb(color):Color.WHITE);
        float y=-fm.ascent;
        for(String r:rows){
            float rw=paint.measureText(r),x=align.equals("left")?0:align.equals("right")?blockW-rw:(blockW-rw)/2;
            c.drawText(r,x,y,paint);y+=line;
        }
        drawn=key();return b;
    }
    /** Rows of the text: its own line breaks, wrapped at maxwidth and cut at maxrows when the author limits them. */
    private List<String> rows(Paint paint){
        String text=value.length()>MAX_CHARS?value.substring(0,MAX_CHARS):value;
        List<String> out=new ArrayList<>();
        for(String line:text.replace("\r","").split(String.valueOf((char)10),-1)){
            if(!limitwidth||paint.measureText(line)<=maxwidth){out.add(line);continue;}
            StringBuilder row=new StringBuilder();
            for(String word:line.split(" ",-1)){
                String next=row.length()==0?word:row+" "+word;
                if(paint.measureText(next)<=maxwidth){row.setLength(0);row.append(next);continue;}
                if(row.length()>0){out.add(row.toString());row.setLength(0);}
                // A word wider than the limit breaks between characters; its last piece starts the next row.
                int i=0;
                while(paint.measureText(word,i,word.length())>maxwidth){int n=Math.max(1,paint.breakText(word,i,word.length(),true,maxwidth,null));out.add(word.substring(i,i+n));i+=n;}
                row.append(word.substring(i));
            }
            out.add(row.toString());
        }
        if(limitrows&&out.size()>Math.max(1,maxrows))out=new ArrayList<>(out.subList(0,Math.max(1,maxrows)));
        return out;
    }
    private static int rgb(float[] c){return Color.rgb(channel(c,0),channel(c,1),channel(c,2));}
    private static int channel(float[] c,int i){return i<c.length?Math.round(Math.max(0,Math.min(1,c[i]))*255):255;}

    /** Horizontal and vertical share of a size change that moves the box's left/top edge: the aligned edge stays put. */
    float shiftX(){return align.equals("left")?0:align.equals("right")?1:.5f;}
    float shiftY(){return valign.equals("top")?0:valign.equals("bottom")?1:.5f;}

    /** The font: the scene's own file (or Wallpaper Engine's bundled one), else the closest Android system family. */
    static Typeface typeface(byte[] data,String name){
        if(data!=null&&data.length>0){
            File file=null;
            try{file=File.createTempFile("scene-font",".ttf");try(FileOutputStream out=new FileOutputStream(file)){out.write(data);}Typeface t=Typeface.createFromFile(file);if(t!=null)return t;}
            catch(Exception unreadable){}
            finally{if(file!=null)file.delete();}
        }
        String n=name==null?"":name.toLowerCase(Locale.ROOT);
        Typeface family=n.matches(".*(times|cambria|georgia|garamond|serif|song|ming).*")&&!n.contains("sans")?Typeface.SERIF:n.matches(".*(courier|consol|mono).*")?Typeface.MONOSPACE:Typeface.SANS_SERIF;
        return Typeface.create(family,n.matches(".*(bold|black|heavy).*")?Typeface.BOLD:Typeface.NORMAL);
    }
}
