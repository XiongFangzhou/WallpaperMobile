package dev.workshopvideo.core;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Wallpaper Engine user-property display conditions. They are JavaScript expressions over the settings
 * ("beijing.value==0", "a.value && !b.value", "x.value>0.5", "[1, 2, 3].includes(n.value)", "(a.value || b.value) && c.value == 'x'").
 * This evaluates the expression subset authors use: literals, arrays, name.value, .includes(), .length, ! && || == != === !==
 * < > <= >= + - and parentheses. Unknown syntax counts as true, so a setting is shown rather than hidden by a misread condition.
 */
public final class PropertyCondition {
    private PropertyCondition(){}

    /** lookup returns a property's current value (Boolean, Number or String), or null when unknown. */
    public static boolean test(String expression,Function<String,Object> lookup){
        if(expression==null||expression.isBlank())return true;
        try{
            Parser p=new Parser(expression,lookup);Object v=p.or();p.skip();if(p.i!=p.s.length())return true;
            return v==UNKNOWN||truthy(v);
        }catch(RuntimeException unreadable){return true;}
    }

    public static boolean truthy(Object v){
        if(v==null||v==UNKNOWN)return false;if(v instanceof Boolean b)return b;if(v instanceof Number n)return n.doubleValue()!=0;
        if(v instanceof List<?>)return true;
        String s=String.valueOf(v).trim();return !s.isEmpty()&&!s.equals("0")&&!s.equalsIgnoreCase("false")&&!s.equals("null");
    }

    /** A setting the lookup does not know: comparisons with it hold, so the condition does not hide anything. */
    private static final Object UNKNOWN=new Object();

    private static Double number(Object v){
        if(v instanceof Number n)return n.doubleValue();if(v instanceof Boolean b)return b?1d:0d;
        if(v==null||v==UNKNOWN||v instanceof List<?>)return null;
        String s=String.valueOf(v).trim();if(s.isEmpty())return null;
        if(s.equalsIgnoreCase("true"))return 1d;if(s.equalsIgnoreCase("false"))return 0d;
        try{return Double.parseDouble(s);}catch(NumberFormatException e){return null;}
    }
    /** Loose equality: numbers (and number-like strings or booleans) by value, everything else as text. */
    private static boolean same(Object a,Object b){
        if(a==UNKNOWN||b==UNKNOWN)return true;
        Double x=number(a),y=number(b);if(x!=null&&y!=null)return x.doubleValue()==y.doubleValue();
        if(a instanceof Boolean||b instanceof Boolean)return truthy(a)==truthy(b);
        return String.valueOf(a).equals(String.valueOf(b));
    }

    private static final class Parser {
        final String s;final Function<String,Object> lookup;int i;
        Parser(String s,Function<String,Object> lookup){this.s=s;this.lookup=lookup;}
        void skip(){while(i<s.length()&&Character.isWhitespace(s.charAt(i)))i++;}
        boolean eat(String token){skip();if(s.startsWith(token,i)){i+=token.length();return true;}return false;}
        RuntimeException fail(){return new IllegalArgumentException("condition syntax at "+i);}

        Object or(){Object v=and();while(eat("||")){Object r=and();v=v==UNKNOWN||truthy(v)?v:r;}return v;}
        Object and(){Object v=not();while(peek("&&")){eat("&&");Object r=not();v=v!=UNKNOWN&&!truthy(v)?v:r;}return v;}
        boolean peek(String t){skip();return s.startsWith(t,i);}
        Object not(){
            skip();
            if(i<s.length()&&s.charAt(i)=='!'&&!s.startsWith("!=",i)){i++;Object v=not();return v==UNKNOWN?UNKNOWN:!truthy(v);}
            return compare();
        }
        Object compare(){
            Object a=sum();skip();
            for(String op:new String[]{"===","!==","==","!=","<=",">=","<",">"}){
                if(s.startsWith(op,i)){i+=op.length();Object b=sum();
                    if(a==UNKNOWN||b==UNKNOWN)return UNKNOWN;
                    return switch(op){
                        case "===","=="->same(a,b);case "!==","!="->!same(a,b);
                        default->{Double x=number(a),y=number(b);if(x==null||y==null)yield UNKNOWN;
                            yield switch(op){case "<="->x<=y;case ">="->x>=y;case "<"->x<y;default->x>y;};}
                    };}
            }
            return a;
        }
        Object sum(){
            Object v=postfix();
            while(true){skip();if(i<s.length()&&(s.charAt(i)=='+'||s.charAt(i)=='-')){char op=s.charAt(i++);Object r=postfix();
                    if(v==UNKNOWN||r==UNKNOWN){v=UNKNOWN;continue;}Double x=number(v),y=number(r);
                    if(op=='+'&&(x==null||y==null))v=String.valueOf(v)+r;else if(x!=null&&y!=null)v=op=='+'?x+y:x-y;else v=UNKNOWN;}
                else return v;}
        }
        Object postfix(){
            Object v=primary();
            while(true){
                skip();
                if(s.startsWith(".includes",i)){i+=".includes".length();if(!eat("("))throw fail();Object arg=or();if(!eat(")"))throw fail();
                    if(v==UNKNOWN||arg==UNKNOWN)v=UNKNOWN;
                    else if(v instanceof List<?> list){boolean in=false;for(Object e:list)if(same(e,arg)){in=true;break;}v=in;}
                    else v=String.valueOf(v).contains(String.valueOf(arg));}
                else if(s.startsWith(".length",i)){i+=".length".length();v=v==UNKNOWN?UNKNOWN:v instanceof List<?> list?(double)list.size():(double)String.valueOf(v).length();}
                else return v;
            }
        }
        Object primary(){
            skip();if(i>=s.length())throw fail();char c=s.charAt(i);
            if(c=='('){i++;Object v=or();if(!eat(")"))throw fail();return v;}
            if(c=='['){i++;List<Object> list=new ArrayList<>();if(eat("]"))return list;
                do{list.add(or());}while(eat(","));if(!eat("]"))throw fail();return list;}
            if(c=='"'||c=='\''){int end=s.indexOf(c,i+1);if(end<0)throw fail();String v=s.substring(i+1,end);i=end+1;return v;}
            if(Character.isDigit(c)||c=='.'||c=='-'&&i+1<s.length()&&Character.isDigit(s.charAt(i+1))){
                int start=i++;while(i<s.length()&&(Character.isDigit(s.charAt(i))||s.charAt(i)=='.'||s.charAt(i)=='e'||s.charAt(i)=='E'))i++;
                return Double.parseDouble(s.substring(start,i));}
            if(Character.isLetter(c)||c=='_'||c=='$'){
                int start=i;while(i<s.length()&&(Character.isLetterOrDigit(s.charAt(i))||s.charAt(i)=='_'||s.charAt(i)=='$'))i++;
                String name=s.substring(start,i);
                switch(name){case "true":return true;case "false":return false;case "null":case "undefined":return null;default:}
                if(s.startsWith(".value",i)){i+=".value".length();Object v=lookup.apply(name);return v==null?UNKNOWN:v;}
                throw fail();
            }
            throw fail();
        }
    }
}
