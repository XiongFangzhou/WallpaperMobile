package dev.workshopvideo;
import org.json.*;
import dev.workshopvideo.core.EffectMotion;
/** Canonical, bounded settings persisted with each wallpaper copy. */
final class EffectOptions {
    static JSONObject defaults(){JSONObject o=new JSONObject();VideoApp.put(o,"zoom",1.12);VideoApp.put(o,"strength",.28);VideoApp.put(o,"smooth",.18);VideoApp.put(o,"touchStrength",.3);VideoApp.put(o,"speed",1);VideoApp.put(o,"brightness",1);VideoApp.put(o,"saturation",1);VideoApp.put(o,"fps",30);VideoApp.put(o,"fallbackPages",3);VideoApp.put(o,"contrast",1);VideoApp.put(o,"hue",0);VideoApp.put(o,"backgroundColor","#000000");return o;}
    static JSONObject video(JSONObject source){JSONObject out=normalize(source);if(source==null)VideoApp.put(out,"zoom",1);String mode=out.optString("motionMode");VideoApp.put(out,"pan",mode.equals("scroll"));VideoApp.put(out,"gyro",mode.equals("device"));for(String key:new String[]{"touch","ripple","particles","reverseX","reverseY"})VideoApp.put(out,key,false);VideoApp.put(out,"rotation",0);return out;}
    static JSONObject normalize(JSONObject source){JSONObject out=defaults();if(source==null)source=new JSONObject();
        for(String key:new String[]{"gyro","touch","ripple","particles","pan","reverseX","reverseY"})VideoApp.put(out,key,source.optBoolean(key));
        if(source.has("xray"))VideoApp.put(out,"xray",source.optBoolean("xray",true));if(source.has("xraySize"))VideoApp.put(out,"xraySize",EffectMotion.clamp((float)source.optDouble("xraySize",.3),.01f,1));
        VideoApp.put(out,"flip",source.optBoolean("flip"));VideoApp.put(out,"showColorOptions",source.optBoolean("showColorOptions",true));VideoApp.put(out,"pageMode",source.optString("pageMode").equals("manual")?"manual":"auto");
        String motion=source.optString("motionMode",source.optBoolean("pan")?"scroll":source.optBoolean("gyro")?"device":"disabled");if(!java.util.List.of("disabled","device","scroll").contains(motion))motion="disabled";VideoApp.put(out,"motionMode",motion);
        String alignment=source.optString("alignment","cover");VideoApp.put(out,"alignment",java.util.List.of("cover","fill","free").contains(alignment)?alignment:"cover");
        VideoApp.put(out,"backgroundColor",source.optString("backgroundColor","#000000").matches("#[0-9a-fA-F]{6}")?source.optString("backgroundColor","#000000"):"#000000");
        VideoApp.put(out,"contrast",EffectMotion.clamp((float)source.optDouble("contrast",1),0,2));VideoApp.put(out,"hue",EffectMotion.clamp((float)source.optDouble("hue",0),-180,180));
        String[] keys={"zoom","strength","smooth","touchStrength","speed","brightness","saturation","x","y","rotation","particleCount","layerDepth","fps","fallbackPages"};
        double[] min={1,0,.03,0,.25,.2,0,-.5,-.5,-15,4,0,15,1},max={2.5,1,1,1,2,1.8,2,.5,.5,15,100,2,60,12};
        for(int i=0;i<keys.length;i++){double d=source.optDouble(keys[i],out.optDouble(keys[i],keys[i].equals("particleCount")?24:keys[i].equals("layerDepth")?1:0));VideoApp.put(out,keys[i],EffectMotion.clamp((float)d,(float)min[i],(float)max[i]));}
        for(int i=0;i<32;i++)for(String field:new String[]{"depth","opacity","layerX","layerY"}){String key=field+i;if(source.has(key))VideoApp.put(out,key,EffectMotion.clamp((float)source.optDouble(key,field.equals("opacity")?1:0),field.equals("opacity")?0:-2,field.equals("opacity")?1:2));}
        VideoApp.put(out,"color",source.optString("color","#a9efd5").matches("#[0-9a-fA-F]{6}")?source.optString("color","#a9efd5"):"#a9efd5");return out;
    }
}
