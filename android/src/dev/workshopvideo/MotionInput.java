package dev.workshopvideo;
import android.content.Context;
import android.hardware.*;
import android.os.*;
import java.util.function.Consumer;
/** Standard motion sensors at 50 Hz need no runtime permission or high-rate permission. */
final class MotionInput implements SensorEventListener {
 interface Target {void tilt(float x,float y);}
 private final SensorManager manager;private final Sensor sensor;private final android.view.WindowManager windows;private final Target target;private final Consumer<String> unavailable;
 private final Handler main=new Handler(Looper.getMainLooper());private boolean active,centered,received;private float cx,cy;
 private final Runnable noData=this::reportNoData;
 private void reportNoData(){if(active&&!received)unavailable.accept("未收到姿态传感器数据，请检查系统传感器或隐私开关，再重新进入预览。");}
 MotionInput(Context context,Target target){this(context,target,message->{});}
 MotionInput(Context context,Target target,Consumer<String> unavailable){this.target=target;this.unavailable=unavailable;windows=context.getSystemService(android.view.WindowManager.class);manager=context.getSystemService(SensorManager.class);Sensor s=manager==null?null:manager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);if(s==null&&manager!=null)s=manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);if(s==null&&manager!=null)s=manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);sensor=s;}
 boolean available(){return sensor!=null;}
 void active(boolean value){
  if(!value){main.removeCallbacks(noData);if(manager!=null)manager.unregisterListener(this);active=false;received=false;target.tilt(0,0);return;}
  if(active)return;centered=false;received=false;
  if(sensor==null){unavailable.accept("此设备没有可用姿态传感器，倾斜视差无法启用。");return;}
  try{active=manager.registerListener(this,sensor,20_000);if(!active){manager.unregisterListener(this);unavailable.accept("系统暂时无法启用姿态传感器，请检查传感器或隐私开关后重试。");}else main.postDelayed(noData,2500);}
  catch(RuntimeException problem){active=false;manager.unregisterListener(this);unavailable.accept("系统限制了姿态传感器，请检查系统传感器或隐私设置后重试。");}
 }
 void center(){centered=false;}
 private static float difference(float value,float center){return (float)Math.atan2(Math.sin(value-center),Math.cos(value-center));}
 public void onSensorChanged(SensorEvent e){
  if(!active)return;float x,y;
  if(e.sensor.getType()==Sensor.TYPE_ACCELEROMETER){if(e.values.length<3)return;x=-e.values[0]/9.81f;y=e.values[1]/9.81f;}
  else{if(e.values.length<3)return;float[] r=new float[9],orientation=new float[3];SensorManager.getRotationMatrixFromVector(r,e.values);SensorManager.getOrientation(r,orientation);x=orientation[2];y=orientation[1];}
  if(!Float.isFinite(x)||!Float.isFinite(y))return;received=true;main.removeCallbacks(noData);
  int rotation=windows==null?0:windows.getDefaultDisplay().getRotation();float rx=x,ry=y;
  if(rotation==android.view.Surface.ROTATION_90){rx=-y;ry=x;}else if(rotation==android.view.Surface.ROTATION_180){rx=-x;ry=-y;}else if(rotation==android.view.Surface.ROTATION_270){rx=y;ry=-x;}x=rx;y=ry;
  if(!centered){cx=x;cy=y;centered=true;}
  float dx=e.sensor.getType()==Sensor.TYPE_ACCELEROMETER?x-cx:difference(x,cx),dy=e.sensor.getType()==Sensor.TYPE_ACCELEROMETER?y-cy:difference(y,cy);
  target.tilt(Math.max(-1,Math.min(1,dx*1.8f)),Math.max(-1,Math.min(1,dy*1.8f)));
 }
 public void onAccuracyChanged(Sensor sensor,int accuracy){}
}
