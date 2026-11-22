"""Verify production sensor registration, fallback, visibility and failure handling."""
import pathlib,subprocess,os
ROOT=pathlib.Path(__file__).resolve().parents[1];OUT=ROOT/'android/build/sensor-host';OUT.mkdir(parents=True,exist_ok=True)
JDK=pathlib.Path(os.environ['WVB_JDK']);sources=list((ROOT/'android/test-host/sensors').rglob('*.java'))+[ROOT/'android/src/dev/workshopvideo/MotionInput.java']
subprocess.run([str(JDK/'bin/javac.exe'),'-encoding','UTF-8','--release','17','-d',str(OUT),*map(str,sources)],check=True)
subprocess.run([str(JDK/'bin/java.exe'),'-cp',str(OUT),'dev.workshopvideo.MotionChecks'],check=True)
