package android.content;
import java.util.*;
public class SharedPreferences {
 private final Map<String,String> data=new HashMap<>();public boolean failNext;
 public String getString(String key,String fallback){return data.getOrDefault(key,fallback);}public boolean contains(String key){return data.containsKey(key);}public Editor edit(){return new Editor();}
 public class Editor {private final Map<String,String> pending=new HashMap<>();public Editor putString(String k,String v){pending.put(k,v);return this;}public Editor remove(String k){pending.put(k,null);return this;}public boolean commit(){if(failNext){failNext=false;return false;}pending.forEach((k,v)->{if(v==null)data.remove(k);else data.put(k,v);});return true;}public void apply(){commit();}}
}