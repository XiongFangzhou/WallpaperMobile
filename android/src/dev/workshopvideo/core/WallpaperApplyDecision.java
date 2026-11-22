package dev.workshopvideo.core;

/** A picker return code alone is neither proof of selection nor proof of video playback. */
public final class WallpaperApplyDecision {
    public enum State { FAILED,NOT_SELECTED,UNCONFIRMED,SELECTED,RUNNING }
    /** No engine and an immediate canceled return means this entry never reached the wallpaper.
     * It is an entry failure, not proof that the device or the media format is unsupported. */
    public static boolean entryRejected(boolean selected,boolean changed,boolean pickerOk,boolean bound,boolean mediaError,long elapsedMs){
        return !selected&&!changed&&!pickerOk&&!bound&&!mediaError&&elapsedMs>=0&&elapsedMs<=250;
    }
    /** A direct MIUI entry that never reached the candidate service needs the system permission flow.
     * A normal cancellation after preview binding and a media failure must not request permission. */
    public static boolean needsPermission(boolean miui,boolean direct,State state,boolean pickerOk,boolean changed,boolean activeCandidate,boolean bound,boolean mediaError){
        return miui&&direct&&(state==State.NOT_SELECTED||state==State.UNCONFIRMED)&&!pickerOk&&!changed&&!activeCandidate&&!bound&&!mediaError;
    }
    public static State evaluate(boolean selectedService,boolean wasSelected,boolean pickerOk,boolean destinationChanged,boolean activeCandidate,boolean actualFrame,boolean error){
        if(error)return State.FAILED;
        if(!selectedService)return State.NOT_SELECTED;
        if(pickerOk||!wasSelected||destinationChanged||activeCandidate)return actualFrame?State.RUNNING:State.SELECTED;
        return State.UNCONFIRMED;
    }
}
