package dev.workshopvideo.core;
import static dev.workshopvideo.core.CoreChecks.*;
import java.util.Map;
final class PropertyChecks {
    static void run(){
        Map<String,Object> v=Map.of("beijing","0","mon",true,"off",false,"size",0.7,"mode","2");
        java.util.function.Function<String,Object> get=v::get;
        check(PropertyCondition.test("",get)&&PropertyCondition.test(null,get),"no condition shows the setting");
        check(PropertyCondition.test("beijing.value==0",get)&&!PropertyCondition.test("beijing.value==1",get),"combo equality");
        check(PropertyCondition.test("mon.value",get)&&!PropertyCondition.test("off.value",get)&&PropertyCondition.test("!off.value",get),"switch truth and negation");
        check(PropertyCondition.test("size.value>0.5",get)&&!PropertyCondition.test("size.value<=0.5",get)&&PropertyCondition.test("mode.value!=1",get),"numeric comparisons");
        check(PropertyCondition.test("mon.value && beijing.value==0",get)&&!PropertyCondition.test("mon.value && off.value",get)&&PropertyCondition.test("off.value || mon.value",get),"and / or");
        check(PropertyCondition.test("unknown.value==3",get)&&PropertyCondition.test("something odd",get),"unknown properties and syntax keep the setting visible");
        check(java.util.Arrays.equals(UserValues.vector("0.5 1 0.25",3),new float[]{.5f,1,.25f}),"vector strings");
        check(java.util.Arrays.equals(UserValues.vector(0.65,3),new float[]{.65f,.65f,.65f}),"a scalar slider fills a vector");
        float[] c=UserValues.vector("#FF8000",3);check(c[0]==1&&Math.abs(c[1]-128/255f)<1e-6&&c[2]==0,"phone colours are read");
        check(UserValues.hex("1 0.5019608 0").equals("#FF8000")&&UserValues.forScripts("#FF8000").toString().startsWith("1.00000 0.50196"),"colours convert both ways");
        check(UserValues.vector("abc",1)==null&&UserValues.vector(null,1)==null,"unreadable values are rejected");
    }
}
