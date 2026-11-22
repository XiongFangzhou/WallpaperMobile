package dev.workshopvideo;

import java.util.concurrent.CopyOnWriteArrayList;

/** In-process launcher observations. No screen contents or navigation history are retained. */
final class LauncherSync {
    interface Listener {void scrolled(float position,long eventTime);}
    private static final CopyOnWriteArrayList<Listener> listeners=new CopyOnWriteArrayList<>();
    private static boolean connected;
    private static float position=Float.NaN;
    private static long time;
    static boolean connected(){return connected;}
    static float position(){return position;}
    static long time(){return time;}
    static void connected(boolean value){connected=value;position=Float.NaN;time=0;}
    static void add(Listener listener){listeners.add(listener);}
    static void remove(Listener listener){listeners.remove(listener);}
    static void publish(float value,long eventTime){if(!connected||!Float.isFinite(value)||value<0||value>1||eventTime<time)return;position=value;time=eventTime;for(Listener listener:listeners)listener.scrolled(value,eventTime);}
}
