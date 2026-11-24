package dev.workshopvideo.core;
import static dev.workshopvideo.core.CoreChecks.*;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
final class CommentChecks {
    static void run()throws Exception{
        byte[] request=WorkshopComments.request(0x0110000100000001L,3650475846L,20,500);
        check(hex(request).equals("09010000000100100110051946d795d90000000030143832"),"comment request encodes creator, public workshop thread, item, start and clamped count: "+hex(request));

        ByteArrayOutputStream response=new ByteArrayOutputStream();
        message(response,1,comment(11,0x0110000100000002L,1764000000,"[b]好看[/b] :steamhappy:\n[quote=someone]旧回复[/quote]谢谢分享",7,false));
        message(response,1,comment(12,0x0110000100000003L,1764000100,"已删除",0,true));
        message(response,1,comment(13,0x0110000100000004L,1764000200,"&lt;3 [url=https://example.com]link[/url]",0,false));
        response.write(5<<3);varint(response,20);response.write(7<<3);varint(response,136);
        response.write((9<<3)|5);response.write(new byte[4]);
        WorkshopComments.Page page=WorkshopComments.parse(response.toByteArray(),20);
        check(page.comments.size()==2&&page.total==136&&page.start==20&&page.more(),"hidden comments are skipped and paging is read");
        WorkshopComments.Comment first=page.comments.get(0);
        check(first.id==11&&first.author==0x0110000100000002L&&first.time==1764000000L&&first.upvotes==7,"comment fields are decoded");
        check(first.text.equals("好看 \n谢谢分享")||first.text.equals("好看\n谢谢分享"),"BBCode, quotes and emoticons are stripped: "+first.text);
        check(page.comments.get(1).text.equals("<3 link"),"entities are decoded and link text kept");
        check(!WorkshopComments.parse(new byte[0],0).more(),"empty thread has no more pages");
        var outgoing=new in.dragonbra.javasteam.base.ClientMsgProtobuf<com.google.protobuf.Empty.Builder>(com.google.protobuf.Empty.class,in.dragonbra.javasteam.enums.EMsg.ServiceMethodResponse);
        outgoing.getProtoHeader().setTargetJobName(WorkshopComments.METHOD);outgoing.getPayload().write(response.toByteArray(),0,response.size());
        var incoming=new in.dragonbra.javasteam.base.ClientMsgProtobuf<com.google.protobuf.Empty.Builder>(com.google.protobuf.Empty.class,new in.dragonbra.javasteam.base.PacketClientMsgProtobuf(in.dragonbra.javasteam.enums.EMsg.ServiceMethodResponse,outgoing.serialize()));
        check(java.util.Arrays.equals(incoming.getBody().build().toByteArray(),response.toByteArray())&&incoming.getProtoHeader().getTargetJobName().equals(WorkshopComments.METHOD),"raw service bodies survive the empty-message wrapper both ways");
        String html="<div class=\"commentthread_comment responsive_body_text   \" id=\"comment_4101\"><div class=\"commentthread_comment_avatar\"></div><div class=\"commentthread_comment_content\"><div class=\"commentthread_comment_author\"><a class=\"hoverunderline commentthread_author_link\" href=\"https://steamcommunity.com/id/a\" data-miniprofile=\"12345\">\n<bdi>小明 &amp; Co</bdi></a><span class=\"commentthread_comment_timestamp\" title=\"x\" data-timestamp=\"1764000000\">1 hour ago</span></div><div class=\"commentthread_comment_text\" id=\"comment_content_4101\">\n\t\t\t\t\t好看<br>谢谢 <img src=\"e.png\" alt=\":steamhappy:\"> <a href=\"x\">链接</a>\t\t\t\t</div></div></div>"
            +"<div class=\"commentthread_comment responsive_body_text   \" id=\"comment_4102\"><a class=\"hoverunderline commentthread_author_link\" href=\"y\" data-miniprofile=\"7\"><bdi>B</bdi></a><span data-timestamp=\"1764000100\"></span><div class=\"commentthread_comment_text\" id=\"comment_content_4102\"><blockquote class=\"bb_blockquote\">旧</blockquote>second</div></div>";
        String json="{\"success\":true,\"name\":\"PublishedFile_Public_1_2\",\"start\":20,\"pagesize\":20,\"total_count\":57,\"upvotes\":0,\"has_upvoted\":0,\"comments_html\":"+quote(html)+",\"timelastpost\":1764000100,\"extra\":{\"a\":[1,\"}\"]}}";
        WorkshopComments.Page web=WorkshopComments.parseWeb(json,20);
        check(web.comments.size()==2&&web.total==57&&web.start==20&&web.more(),"web thread paging is read: "+web.comments.size()+" "+web.total);
        WorkshopComments.Comment w=web.comments.get(0);
        check(w.id==4101&&w.author==76561197960278073L&&w.time==1764000000L&&"小明 & Co".equals(w.name),"web comment author, id and time are decoded: "+w.author+" "+w.name);
        check(w.text.equals("好看\n谢谢  链接"),"web comment markup becomes plain text: ["+w.text+"]");
        check(web.comments.get(1).text.equals("second"),"web quotes are dropped");
        boolean refused=false;try{WorkshopComments.parseWeb("{\"success\":false}",0);}catch(java.io.IOException e){refused=true;}
        check(refused,"unsuccessful web thread is an error");
        boolean rejected=false;try{WorkshopComments.parse(new byte[]{0x0a,0x05,0x01},0);}catch(java.io.IOException e){rejected=true;}
        check(rejected,"truncated responses are rejected");
    }
    private static byte[] comment(long id,long author,long time,String text,int upvotes,boolean deleted){
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        out.write((1<<3)|1);fixed(out,id);out.write((2<<3)|1);fixed(out,author);out.write(3<<3);varint(out,time);
        byte[] t=text.getBytes(StandardCharsets.UTF_8);out.write((4<<3)|2);varint(out,t.length);out.write(t,0,t.length);
        out.write(5<<3);varint(out,upvotes);if(deleted){out.write(8<<3);out.write(1);}
        return out.toByteArray();
    }
    private static void message(ByteArrayOutputStream out,int field,byte[] body){out.write((field<<3)|2);varint(out,body.length);out.write(body,0,body.length);}
    private static void fixed(ByteArrayOutputStream out,long v){for(int i=0;i<8;i++)out.write((int)(v>>>(8*i))&0xff);}
    private static void varint(ByteArrayOutputStream out,long v){while((v&~0x7fL)!=0){out.write((int)((v&0x7f)|0x80));v>>>=7;}out.write((int)v);}
    private static String quote(String s){StringBuilder b=new StringBuilder("\"");for(char c:s.toCharArray()){if(c=='"'||c=='\\')b.append('\\').append(c);else if(c=='\n')b.append("\\n");else if(c=='\t')b.append("\\t");else if(c=='/')b.append("\\/");else if(c>127)b.append(String.format("\\u%04x",(int)c));else b.append(c);}return b.append('"').toString();}
    private static String hex(byte[] b){StringBuilder s=new StringBuilder();for(byte x:b)s.append(String.format("%02x",x&0xff));return s.toString();}
}
