package dev.workshopvideo;

/** Host double: the JavaScript sandbox exists only on Android, so conversions here keep layers without effects. */
final class JsShaderTranslator implements SceneEffects.Translator, AutoCloseable {
    JsShaderTranslator(Object context){}
    @Override public String[] translate(String vertex,String fragment,org.json.JSONObject combos,org.json.JSONObject includes)throws Exception{throw new java.io.IOException("host checks do not run the JavaScript sandbox");}
    @Override public void close(){}
}
