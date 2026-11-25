package dev.workshopvideo.core;

/** User-property values as Wallpaper Engine stores them ("x y z", numbers, booleans) and as the phone's colour picker writes them ("#RRGGBB"). */
public final class UserValues {
    private UserValues(){}

    /** Components of a value; a single number is repeated to fill width (a scale slider bound to "x y z"). Null when unreadable. */
    public static float[] vector(Object value,int width){
        float[] v=parse(value);if(v==null)return null;
        if(width<=0||v.length>=width)return v;
        float[] out=new float[width];for(int i=0;i<width;i++)out[i]=v.length==1?v[0]:i<v.length?v[i]:0;return out;
    }
    private static float[] parse(Object value){
        if(value instanceof Boolean b)return new float[]{b?1:0};
        if(value instanceof Number n)return Float.isFinite(n.floatValue())?new float[]{n.floatValue()}:null;
        if(value==null)return null;String s=String.valueOf(value).trim();if(s.isEmpty())return null;
        if(s.matches("#[0-9a-fA-F]{6}")){int c=Integer.parseInt(s.substring(1),16);return new float[]{(c>>16&255)/255f,(c>>8&255)/255f,(c&255)/255f};}
        String[] parts=s.split("[\\s,]+");if(parts.length>16)return null;float[] out=new float[parts.length];
        try{for(int i=0;i<parts.length;i++){out[i]=Float.parseFloat(parts[i]);if(!Float.isFinite(out[i]))return null;}}catch(NumberFormatException e){return null;}
        return out;
    }
    /** "#RRGGBB" for a Wallpaper Engine colour ("0.5 1 0.25"). */
    public static String hex(Object value){
        float[] c=vector(value,3);if(c==null)return "#FFFFFF";
        int r=Math.round(Math.max(0,Math.min(1,c[0]))*255),g=Math.round(Math.max(0,Math.min(1,c[1]))*255),b=Math.round(Math.max(0,Math.min(1,c[2]))*255);
        return String.format(java.util.Locale.ROOT,"#%02X%02X%02X",r,g,b);
    }
    /** The form scripts expect: phone colours ("#RRGGBB") become "r g b"; everything else is unchanged. */
    public static Object forScripts(Object value){
        if(value instanceof String s&&s.matches("#[0-9a-fA-F]{6}")){float[] c=vector(s,3);return String.format(java.util.Locale.ROOT,"%.5f %.5f %.5f",c[0],c[1],c[2]);}
        return value;
    }
}
