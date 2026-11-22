package dev.workshopvideo;
import android.content.*;import android.hardware.*;import android.os.Handler;import java.util.*;
public class MotionChecks {
 static int checks;static void check(boolean v,String title){if(!v)throw new AssertionError(title);checks++;}
 public static void main(String[] args){
  Context context=new Context();context.sensors=new SensorManager();SensorManager sensors=context.sensors;List<String> errors=new ArrayList<>();float[] motion={0,0};
  MotionInput input=new MotionInput(context,(x,y)->{motion[0]=x;motion[1]=y;},errors::add);check(!input.available(),"missing optional sensor detected");input.active(true);check(errors.size()==1,"missing sensor cannot silently pretend to be active");input.active(false);errors.clear();
  Sensor game=new Sensor(Sensor.TYPE_GAME_ROTATION_VECTOR),rotation=new Sensor(Sensor.TYPE_ROTATION_VECTOR),accel=new Sensor(Sensor.TYPE_ACCELEROMETER);sensors.available.put(game.getType(),game);sensors.available.put(rotation.getType(),rotation);sensors.available.put(accel.getType(),accel);
  input=new MotionInput(context,(x,y)->{motion[0]=x;motion[1]=y;},errors::add);input.active(true);check(sensors.selected==game,"game rotation preferred");check(sensors.rate==20000,"50 Hz below high-rate permission threshold");int registrations=sensors.registered;input.active(true);check(sensors.registered==registrations,"one listener per active preview/engine");
  input.onSensorChanged(new SensorEvent(game,0,0,3.13f));check(motion[0]==0&&motion[1]==0,"first reading establishes neutral position");input.onSensorChanged(new SensorEvent(game,0,0,-3.13f));check(Math.abs(motion[0])<.1f,"angle crossing pi does not create a full-screen jump");Handler.fire();check(errors.isEmpty(),"received data cancels unavailable watchdog");input.active(false);check(sensors.listener==null&&motion[0]==0&&motion[1]==0,"hidden engine unregisters and resets stale motion");
  input.active(true);Handler.fire();check(errors.size()==1,"missing sensor data reported after registration");input.active(false);errors.clear();
  sensors.allow=false;input.active(true);check(errors.size()==1,"registration refusal is visible without permission crash");check(sensors.listener==null,"refused listener cleaned up");input.active(false);errors.clear();sensors.allow=true;sensors.denied=true;input.active(true);check(errors.size()==1,"vendor SecurityException handled without requesting unrelated permission");input.active(false);sensors.denied=false;errors.clear();
  sensors.available.remove(game.getType());input=new MotionInput(context,(x,y)->{},errors::add);input.active(true);check(sensors.selected==rotation,"rotation vector fallback");input.active(false);
  sensors.available.remove(rotation.getType());input=new MotionInput(context,(x,y)->{motion[0]=x;motion[1]=y;},errors::add);input.active(true);check(sensors.selected==accel,"accelerometer fallback");input.onSensorChanged(new SensorEvent(accel,0,0,9.81f));input.onSensorChanged(new SensorEvent(accel,4.905f,0,9.81f));check(Math.abs(motion[0]+.9f)<.01f,"accelerometer motion uses gravity-normalized relative tilt");input.active(false);Handler.fire();check(errors.isEmpty(),"stopped preview cannot emit delayed sensor warning");
  System.out.println("PASS: "+checks+" production MotionInput lifecycle/registration checks with sensor doubles; not device sensor validation");
 }
}
