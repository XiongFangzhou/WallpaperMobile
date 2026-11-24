package dev.workshopvideo.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Wire format of Steam's Community.GetCommentThread#1 for a workshop item's public comments.
 * JavaSteam ships no generated classes for the Community service, so the few fields used here are encoded by hand.
 */
public final class WorkshopComments {
    public static final String METHOD="Community.GetCommentThread#1";
    /** k_ECommentThreadTypePublishedFile_Public. */
    static final int PUBLISHED_FILE_PUBLIC=5;
    private WorkshopComments(){}

    public static final class Comment {
        public final long id,author;public final long time;public final String text;public final int upvotes;
        /** Display name when the source already carries it (the web thread does), otherwise null. */
        public final String name;
        Comment(long id,long author,long time,String text,int upvotes){this(id,author,time,text,upvotes,null);}
        Comment(long id,long author,long time,String text,int upvotes,String name){this.id=id;this.author=author;this.time=time;this.text=text;this.upvotes=upvotes;this.name=name;}
    }
    public static final class Page {
        public final List<Comment> comments;public final int total,start;
        Page(List<Comment> comments,int total,int start){this.comments=comments;this.total=total;this.start=start;}
        public boolean more(){return start+comments.size()<total;}
    }

    /** CCommunity_GetCommentThread_Request: steamid=1, comment_thread_type=2, gidfeature=3, start=6, count=7. Newest first. */
    public static byte[] request(long creator,long itemId,int start,int count){
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        fixed64(out,1,creator);varint(out,2<<3,PUBLISHED_FILE_PUBLIC);fixed64(out,3,itemId);
        varint(out,6<<3,Math.max(0,start));varint(out,7<<3,Math.max(1,Math.min(50,count)));
        return out.toByteArray();
    }

    /** CCommunity_GetCommentThread_Response: comments=1, start=5, total_count=7; hidden and deleted comments are skipped. */
    public static Page parse(byte[] data,int requestedStart)throws IOException{
        Reader r=new Reader(data,0,data.length);List<Comment> comments=new ArrayList<>();int total=-1,start=requestedStart;
        while(r.more()){
            int tag=(int)r.varint();
            switch(tag){
                case (1<<3)|2->{Comment c=comment(r.slice());if(c!=null)comments.add(c);}
                case 5<<3->start=(int)r.varint();
                case 7<<3->total=(int)r.varint();
                default->r.skip(tag&7);
            }
        }
        return new Page(List.copyOf(comments),Math.max(total,start+comments.size()),start);
    }

    /** CCommunity_Comment: gidcomment=1, steamid=2, timestamp=3, text=4, upvotes=5, hidden=6, hidden_by_user=7, deleted=8. */
    private static Comment comment(Reader r)throws IOException{
        long id=0,author=0,time=0;String text="";int upvotes=0;boolean hidden=false;
        while(r.more()){
            int tag=(int)r.varint();
            switch(tag){
                case (1<<3)|1->id=r.fixed64();
                case (2<<3)|1->author=r.fixed64();
                case 3<<3->time=r.varint()&0xffffffffL;
                case (4<<3)|2->text=r.string();
                case 5<<3->upvotes=(int)r.varint();
                case 6<<3,7<<3,8<<3->hidden|=r.varint()!=0;
                default->r.skip(tag&7);
            }
        }
        String clean=plain(text);
        return hidden||clean.isEmpty()?null:new Comment(id,author,time,clean,upvotes);
    }

    private static final Pattern QUOTE=Pattern.compile("(?is)\\[quote(=[^\\]]*)?\\].*?\\[/quote\\]");
    private static final Pattern TAG=Pattern.compile("(?i)\\[/?(b|i|u|s|strike|spoiler|h1|h2|h3|url(=[^\\]]*)?|noparse|code|list|olist|\\*|hr|img)\\]");
    private static final Pattern EMOTICON=Pattern.compile(":[A-Za-z0-9_]{2,32}:");
    /** Comment text is Steam BBCode; quoted replies and markup are dropped, emoticon codes are removed. */
    public static String plain(String text){
        if(text==null)return "";
        String s=QUOTE.matcher(text).replaceAll("");s=TAG.matcher(s).replaceAll("");s=EMOTICON.matcher(s).replaceAll("");
        s=s.replace("&lt;","<").replace("&gt;",">").replace("&quot;","\"").replace("&#39;","'").replace("&amp;","&");
        return s.replaceAll("[ \\t]+\\n","\n").replaceAll("\\n{3,}","\n\n").trim();
    }

    /** steamcommunity.com/comment/PublishedFile_Public/render/{creator}/{item}/: the public thread the workshop page shows. */
    public static String webUrl(long creator,long itemId){return "https://steamcommunity.com/comment/PublishedFile_Public/render/"+Long.toUnsignedString(creator)+"/"+Long.toUnsignedString(itemId)+"/";}

    private static final Pattern WEB_COMMENT=Pattern.compile("(?s)<div class=\"commentthread_comment[^\"]*\"\\s+id=\"comment_(\\d+)\">(.*?)(?=<div class=\"commentthread_comment[^\"]*\"\\s+id=\"comment_\\d+\">|\\z)");
    private static final Pattern WEB_AUTHOR=Pattern.compile("(?s)commentthread_author_link[^>]*?data-miniprofile=\"(\\d+)\"[^>]*>\\s*<bdi>(.*?)</bdi>");
    private static final Pattern WEB_TIME=Pattern.compile("data-timestamp=\"(\\d+)\"");
    private static final Pattern WEB_TEXT=Pattern.compile("(?s)<div class=\"commentthread_comment_text\" id=\"comment_content_\\d+\">(.*?)</div>");
    private static final Pattern BREAK=Pattern.compile("(?i)<br\\s*/?>"),HTML_TAG=Pattern.compile("(?s)<[^>]+>");
    static final long STEAM_ID_BASE=76561197960265728L;

    /** The JSON the web thread returns: {success,start,total_count,comments_html}. Quotes are dropped, tags stripped. */
    public static Page parseWeb(String json,int requestedStart)throws IOException {
        java.util.Map<String,Object> root=topLevel(json);
        if(!Boolean.TRUE.equals(root.get("success")))throw new IOException("Steam 社区没有返回评论");
        String html=root.get("comments_html") instanceof String s?s:"";List<Comment> comments=new ArrayList<>();
        java.util.regex.Matcher m=WEB_COMMENT.matcher(html);
        while(m.find()){
            String block=m.group(2);java.util.regex.Matcher author=WEB_AUTHOR.matcher(block),time=WEB_TIME.matcher(block),text=WEB_TEXT.matcher(block);
            if(!text.find())continue;
            String body=text.group(1).replaceAll("(?is)<blockquote.*?</blockquote>","");body=BREAK.matcher(body).replaceAll("\n");body=HTML_TAG.matcher(body).replaceAll("");body=plain(body);
            if(body.isEmpty())continue;
            long account=0;String name=null;if(author.find()){account=Long.parseLong(author.group(1));name=plain(HTML_TAG.matcher(author.group(2)).replaceAll(""));}
            comments.add(new Comment(Long.parseLong(m.group(1)),account==0?0:STEAM_ID_BASE+account,time.find()?Long.parseLong(time.group(1)):0,body,0,name));
        }
        int start=root.get("start") instanceof Number n?n.intValue():requestedStart,total=root.get("total_count") instanceof Number n?n.intValue():start+comments.size();
        return new Page(List.copyOf(comments),Math.max(total,start+comments.size()),start);
    }

    /** Top-level members of a JSON object: strings, numbers and booleans; nested values are skipped. */
    static java.util.Map<String,Object> topLevel(String json)throws IOException {
        java.util.Map<String,Object> out=new java.util.HashMap<>();int[] at={skip(json,0)};
        if(at[0]>=json.length()||json.charAt(at[0])!='{')throw new IOException("评论页面格式错误");at[0]++;
        while(true){
            at[0]=skip(json,at[0]);if(at[0]<json.length()&&json.charAt(at[0])=='}')return out;
            String key=string(json,at);at[0]=skip(json,at[0]);if(at[0]>=json.length()||json.charAt(at[0])!=':')throw new IOException("评论页面格式错误");at[0]=skip(json,at[0]+1);
            out.put(key,value(json,at));at[0]=skip(json,at[0]);
            if(at[0]>=json.length())throw new IOException("评论页面不完整");char c=json.charAt(at[0]);if(c=='}')return out;if(c!=',')throw new IOException("评论页面格式错误");at[0]++;
        }
    }
    private static int skip(String s,int i){while(i<s.length()&&Character.isWhitespace(s.charAt(i)))i++;return i;}
    private static Object value(String s,int[] at)throws IOException {
        if(at[0]>=s.length())throw new IOException("评论页面不完整");char c=s.charAt(at[0]);
        if(c=='"')return string(s,at);
        if(s.startsWith("true",at[0])){at[0]+=4;return Boolean.TRUE;}if(s.startsWith("false",at[0])){at[0]+=5;return Boolean.FALSE;}if(s.startsWith("null",at[0])){at[0]+=4;return null;}
        if(c=='{'||c=='['){int depth=0;boolean quoted=false;for(int i=at[0];i<s.length();i++){char x=s.charAt(i);if(quoted){if(x=='\\')i++;else if(x=='"')quoted=false;}else if(x=='"')quoted=true;else if(x=='{'||x=='[')depth++;else if((x=='}'||x==']')&&--depth==0){at[0]=i+1;return null;}}throw new IOException("评论页面不完整");}
        int start=at[0];while(at[0]<s.length()&&"+-.eE0123456789".indexOf(s.charAt(at[0]))>=0)at[0]++;
        if(start==at[0])throw new IOException("评论页面格式错误");try{return Double.parseDouble(s.substring(start,at[0]));}catch(NumberFormatException e){throw new IOException("评论页面格式错误");}
    }
    private static String string(String s,int[] at)throws IOException {
        if(at[0]>=s.length()||s.charAt(at[0])!='"')throw new IOException("评论页面格式错误");StringBuilder b=new StringBuilder();int i=at[0]+1;
        while(i<s.length()){
            char c=s.charAt(i++);if(c=='"'){at[0]=i;return b.toString();}
            if(c!='\\'){b.append(c);continue;}if(i>=s.length())break;char e=s.charAt(i++);
            switch(e){case 'n'->b.append('\n');case 't'->b.append('\t');case 'r'->b.append('\r');case 'b'->b.append('\b');case 'f'->b.append('\f');
                case 'u'->{if(i+4>s.length())throw new IOException("评论页面不完整");b.append((char)Integer.parseInt(s.substring(i,i+4),16));i+=4;}default->b.append(e);}
        }
        throw new IOException("评论页面不完整");
    }

    private static void varint(ByteArrayOutputStream out,int tag,long value){writeVarint(out,tag);writeVarint(out,value);}
    private static void fixed64(ByteArrayOutputStream out,int field,long value){writeVarint(out,(field<<3)|1);for(int i=0;i<8;i++)out.write((int)(value>>>(8*i))&0xff);}
    private static void writeVarint(ByteArrayOutputStream out,long value){while((value&~0x7fL)!=0){out.write((int)((value&0x7f)|0x80));value>>>=7;}out.write((int)value);}

    private static final class Reader {
        private final byte[] data;private int pos;private final int end;
        Reader(byte[] data,int pos,int end){this.data=data;this.pos=pos;this.end=end;}
        boolean more(){return pos<end;}
        long varint()throws IOException{long v=0;for(int shift=0;shift<64;shift+=7){if(pos>=end)throw new IOException("评论数据不完整");int b=data[pos++]&0xff;v|=(long)(b&0x7f)<<shift;if((b&0x80)==0)return v;}throw new IOException("评论数据格式错误");}
        long fixed64()throws IOException{if(end-pos<8)throw new IOException("评论数据不完整");long v=0;for(int i=0;i<8;i++)v|=(long)(data[pos++]&0xff)<<(8*i);return v;}
        Reader slice()throws IOException{long n=varint();if(n<0||n>end-pos)throw new IOException("评论数据不完整");Reader r=new Reader(data,pos,pos+(int)n);pos+=(int)n;return r;}
        String string()throws IOException{Reader r=slice();return new String(data,r.pos,r.end-r.pos,java.nio.charset.StandardCharsets.UTF_8);}
        void skip(int wire)throws IOException{
            switch(wire){
                case 0->varint();
                case 1->{if(end-pos<8)throw new IOException("评论数据不完整");pos+=8;}
                case 2->slice();
                case 5->{if(end-pos<4)throw new IOException("评论数据不完整");pos+=4;}
                default->throw new IOException("评论数据格式错误");
            }
        }
    }
}
