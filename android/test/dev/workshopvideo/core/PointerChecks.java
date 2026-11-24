package dev.workshopvideo.core;
import static dev.workshopvideo.core.CoreChecks.*;
final class PointerChecks {
    static void run(){
        float[] rest=VirtualPointer.idle(0,0,.5f,true);
        check(rest[0]==.5f&&rest[1]==.5f,"an untilted phone on the middle page points at the centre");
        float[] tilted=VirtualPointer.idle(1,-1,.5f,false);
        check(Math.abs(tilted[0]-.85f)<1e-5&&Math.abs(tilted[1]-.15f)<1e-5,"a full tilt moves the pointer 35% of the screen");
        float[] paged=VirtualPointer.idle(0,0,1,true),unpaged=VirtualPointer.idle(0,0,1,false);
        check(Math.abs(paged[0]-.75f)<1e-5&&unpaged[0]==.5f,"paging moves the pointer only when the wallpaper follows paging");
        check(VirtualPointer.idle(9,-9,5,true)[0]==1&&VirtualPointer.idle(Float.NaN,0,0,false)[0]==.5f,"pointer stays on screen and ignores invalid tilt");
        float[] idle={.5f,.5f};
        float[] finger=VirtualPointer.position(.2f,.9f,true,0,true,idle);
        check(finger[0]==.2f&&finger[1]==.9f,"the finger is the pointer while touching");
        float[] held=VirtualPointer.position(.2f,.9f,false,VirtualPointer.HOLD_MS,true,idle);
        check(held[0]==.2f,"the pointer stays where the finger lifted for a moment");
        float[] back=VirtualPointer.position(.2f,.9f,false,VirtualPointer.HOLD_MS+VirtualPointer.RETURN_MS,true,idle);
        check(back[0]==.5f&&back[1]==.5f,"then returns to the tilt position");
        float[] mid=VirtualPointer.position(.2f,.9f,false,VirtualPointer.HOLD_MS+VirtualPointer.RETURN_MS/2,true,idle);
        check(mid[0]>.2f&&mid[0]<.5f,"and eases back smoothly");
        check(VirtualPointer.position(.2f,.9f,false,0,false,new float[]{.6f,.4f})[0]==.6f,"before any touch the tilt decides");
    }
}
