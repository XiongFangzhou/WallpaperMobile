package dev.workshopvideo.core;
/** Hermite-free exponential settling cannot overshoot, even after delayed render frames. */
public final class PageAnimation {
 private float value=.5f,target=.5f;
 public void target(float offset,boolean immediate){target=WallpaperPaging.offset(offset);if(immediate)value=target;}
 public boolean moving(){return Math.abs(value-target)>.0001f;}
 public float step(float seconds){value=EffectMotion.approach(value,target,Math.max(0,Math.min(seconds,.1f)),.075f);if(Math.abs(value-target)<.0001f)value=target;return value;}
 public float value(){return value;}
}
