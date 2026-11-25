package dev.workshopvideo.core;

import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Wallpaper Engine user-property display conditions ("beijing.value==0", "a.value && !b.value", "x.value>0.5").
 * Unknown syntax counts as true, so a setting is shown rather than hidden by a misread condition.
 */
public final class PropertyCondition {
    private static final Pattern TERM=Pattern.compile("^(!?)\\s*\\(?\\s*([A-Za-z_][\\w]*)\\.value\\s*(==|!=|>=|<=|>|<)?\\s*(.*?)\\s*\\)?$");
    private PropertyCondition(){}

    /** lookup returns a property's current value (Boolean, Number or String), or null when unknown. */
    public static boolean test(String expression,Function<String,Object> lookup){
        if(expression==null||expression.isBlank())return true;
        for(String any:expression.split("\\|\\|")){
            boolean all=true;
            for(String term:any.split("&&")){if(!term(term.trim(),lookup)){all=false;break;}}
            if(all)return true;
        }
        return false;
    }

    private static boolean term(String term,Function<String,Object> lookup){
        Matcher m=TERM.matcher(term);if(!m.matches())return true;
        Object value=lookup.apply(m.group(2));if(value==null)return true;
        boolean result;String op=m.group(3);
        if(op==null)result=truthy(value);
        else{
            String rhs=m.group(4).replaceAll("^[\"']|[\"']$","");
            Double a=number(value),b=number(rhs);
            if(a!=null&&b!=null)result=switch(op){case "=="->a.doubleValue()==b.doubleValue();case "!="->a.doubleValue()!=b.doubleValue();case ">="->a>=b;case "<="->a<=b;case ">"->a>b;default->a<b;};
            else{boolean same=String.valueOf(value).equals(rhs)||rhs.equals("true")&&truthy(value)||rhs.equals("false")&&!truthy(value);result=switch(op){case "=="->same;case "!="->!same;default->true;};}
        }
        return m.group(1).isEmpty()?result:!result;
    }
    public static boolean truthy(Object v){
        if(v==null)return false;if(v instanceof Boolean b)return b;if(v instanceof Number n)return n.doubleValue()!=0;
        String s=String.valueOf(v).trim();return !s.isEmpty()&&!s.equals("0")&&!s.equalsIgnoreCase("false")&&!s.equals("null");
    }
    private static Double number(Object v){
        if(v instanceof Number n)return n.doubleValue();if(v instanceof Boolean b)return b?1d:0d;
        try{return Double.parseDouble(String.valueOf(v).trim());}catch(NumberFormatException e){return null;}
    }
}
