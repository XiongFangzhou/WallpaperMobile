package dev.workshopvideo;
import android.content.Context;
import android.hardware.*;
import android.os.*;
import java.util.function.Consumer;
/** Standard motion sensors at 50 Hz need no runtime permission or high-rate permission. */
final class MotionInput implements SensorEventListener {
 interface Target {void tilt(float x,float y);}
 private final SensorManager manager;private final Sensor sensor;private final android.view.WindowManager windows;private final Target target;private final Consumer<String> unavailable;
 private final Handler main=new Handler(Looper.getMainLooper());private boolean active,received;private long lastSample;private final dev.workshopvideo.core.RelativeTilt relative=new dev.workshopvideo.core.RelativeTilt();private final float[] matrix=new float[9];
 private final Runnable noData=this::reportNoData;
 private void reportNoData(){if(active&&!received)unavailable.accept("未收到姿态传感器数据，请检查系统传感器或隐私开关，再重新进入预览。");}
 MotionInput(Context context,Target target){this(context,target,message->{});}
 MotionInput(Context context,Target target,Consumer<String> unavailable){this.target=target;this.unavailable=unavailable;windows=context.getSystemService(android.view.WindowManager.class);manager=context.getSystemService(SensorManager.class);Sensor s=manager==null?null:manager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);if(s==null&&manager!=null)s=manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);if(s==null&&manager!=null)s=manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);sensor=s;}
 boolean available(){return sensor!=null;}
 void active(boolean value){
  if(!value){main.removeCallbacks(noData);if(manager!=null)manager.unregisterListener(this);active=false;received=false;target.tilt(0,0);return;}
  if(active)return;relative.reset();lastSample=0;received=false;
  if(sensor==null){unavailable.accept("此设备没有可用姿态传感器，倾斜视差无法启用。");return;}
  try{active=manager.registerListener(this,sensor,20_000);if(!active){manager.unregisterListener(this);unavailable.accept("系统暂时无法启用姿态传感器，请检查传感器或隐私开关后重试。");}else main.postDelayed(noData,2500);}
  catch(RuntimeException problem){active=false;manager.unregisterListener(this);unavailable.accept("系统限制了姿态传感器，请检查系统传感器或隐私设置后重试。");}
 }
 void center(){relative.reset();lastSample=0;}
 public void onSensorChanged(SensorEvent e){
  if(!active||e.values.length<3)return;int rotation=windows==null?0:windows.getDefaultDisplay().getRotation();float[] tilt;
  if(e.sensor.getType()==Sensor.TYPE_ACCELEROMETER){long now=SystemClock.elapsedRealtime();float dt=lastSample==0?.02f:(now-lastSample)/1000f;lastSample=now;tilt=relative.acceleration(e.values,rotation,dt);}
  else{SensorManager.getRotationMatrixFromVector(matrix,e.values);tilt=relative.matrix(matrix,rotation);}
  if(tilt==null)return;received=true;main.removeCallbacks(noData);target.tilt(tilt[0],tilt[1]);
 }
 public void onAccuracyChanged(Sensor sensor,int accuracy){}
}
