package dev.workshopvideo.core;
import static dev.workshopvideo.core.CoreChecks.*;
final class FrameSelectionChecks {
    static void run(){
        check(FrameSelection.atProgress(0,10000,10000)==0,"select opening frame");
        check(FrameSelection.atProgress(5000,10000,10000)==5000000,"select middle frame in microseconds");
        check(FrameSelection.atProgress(10000,10000,10000)==9999999,"end selection remains before EOF");
        check(FrameSelection.atProgress(-2,10000,10000)==0,"negative frame seek clamps to start");
        check(FrameSelection.atProgress(20000,10000,10000)==9999999,"overshoot frame seek clamps before end");
        check(FrameSelection.clamp(1234567,10000)==1234567,"subsecond precision retained for exact frame extraction");
        check(FrameSelection.clamp(Long.MAX_VALUE,10000)==9999999,"oversized frame timestamp cannot exceed source");
        check(FrameSelection.clamp(1,0)==0&&FrameSelection.clamp(-1,10)==0,"empty and negative frame requests safe");
        check(FrameSelection.atProgress(1,0,10000)==0,"invalid slider range safe");
        check(FrameSelection.atProgress(5000,10000,7200000)==3600000000L,"long video frame selection uses long microseconds");
        check(FrameSelection.clamp(Long.MAX_VALUE,Long.MAX_VALUE)>=0,"large duration timestamp does not overflow");
    }
}
