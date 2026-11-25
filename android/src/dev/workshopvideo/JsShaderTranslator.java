package dev.workshopvideo;

import android.content.Context;
import androidx.javascriptengine.JavaScriptIsolate;
import androidx.javascriptengine.JavaScriptSandbox;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;

/** Runs the bundled we-scene translator in the system WebView's isolated JavaScript sandbox.
 *  Connects on first use, so scenes without effects never start a sandbox. Not thread-safe. */
final class JsShaderTranslator implements SceneEffects.Translator, AutoCloseable {
    private static final String BUNDLE="we-shader-translator.js";
    private final Context context;
    private JavaScriptSandbox sandbox;
    private JavaScriptIsolate isolate;

    JsShaderTranslator(Context context){this.context=context.getApplicationContext();}

    private JavaScriptIsolate isolate()throws Exception {
        if(isolate!=null)return isolate;
        if(!JavaScriptSandbox.isSupported())throw new IOException("系统 WebView 版本过旧，不支持特效转换");
        sandbox=JavaScriptSandbox.createConnectedInstanceAsync(context).get(20,TimeUnit.SECONDS);
        isolate=sandbox.createIsolate();
        String script;try(InputStream input=context.getAssets().open(BUNDLE)){script=new String(dev.workshopvideo.core.VideoFiles.readAll(input),StandardCharsets.UTF_8);}
        isolate.evaluateJavaScriptAsync(script).get(30,TimeUnit.SECONDS);
        return isolate;
    }

    @Override public String[] translate(String vertex,String fragment,JSONObject combos,JSONObject includes)throws Exception {
        JSONObject request=new JSONObject().put("vertex",vertex).put("fragment",fragment).put("combos",combos).put("includes",includes);
        String result=isolate().evaluateJavaScriptAsync("weTranslateShader("+JSONObject.quote(request.toString())+")").get(30,TimeUnit.SECONDS);
        JSONObject output=new JSONObject(result);
        return new String[]{output.getString("vertex"),output.getString("fragment")};
    }

    @Override public void close(){
        if(isolate!=null)try{isolate.close();}catch(RuntimeException ignored){}
        if(sandbox!=null)try{sandbox.close();}catch(RuntimeException ignored){}
        isolate=null;sandbox=null;
    }
}
