"""Production Media3 range adapter against a real installed workshop video and its Steam manifest."""
import pathlib,os,subprocess
ROOT=pathlib.Path(__file__).resolve().parents[1];OUT=ROOT/'android/build/streaming-host';OUT.mkdir(parents=True,exist_ok=True)
JDK=pathlib.Path(os.environ['WVB_JDK']);platform=ROOT/'.tools/android-sdk/platforms/android-35/android.jar'
cp=os.pathsep.join(map(str,[ROOT/'android/build/core',*(ROOT/'.tools/android-libs').glob('*.jar'),platform]))
sources=[*list((ROOT/'android/test-host/streaming').rglob('*.java')),ROOT/'android/src/dev/workshopvideo/WorkshopRangeDataSource.java']
subprocess.run([str(JDK/'bin/javac.exe'),'-encoding','UTF-8','--release','17','-cp',cp,'-d',str(OUT),*map(str,sources)],check=True)
subprocess.run([str(JDK/'bin/java.exe'),'-Dfile.encoding=UTF-8','-cp',str(OUT)+os.pathsep+cp,'dev.workshopvideo.StreamingHostChecks'],check=True)
