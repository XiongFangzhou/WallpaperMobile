"""Run production download service pause/cancel against host Android/session doubles."""
import pathlib,os,subprocess
ROOT=pathlib.Path(__file__).resolve().parents[1];OUT=ROOT/'android/build/download-host';OUT.mkdir(parents=True,exist_ok=True);JDK=pathlib.Path(os.environ['WVB_JDK'])
cp=os.pathsep.join(map(str,[ROOT/'.cache/maven/json-20240303.jar',ROOT/'android/build/core',ROOT/'android/build/classes',ROOT/'.tools/android-sdk/platforms/android-35/android.jar',*(ROOT/'.tools/android-libs').glob('*.jar')]))
sources=list((ROOT/'android/test-host/downloads').rglob('*.java'))+[ROOT/'android/src/dev/workshopvideo/VideoDownloadService.java']
subprocess.run([str(JDK/'bin/javac.exe'),'-encoding','UTF-8','--release','17','-cp',cp,'-d',str(OUT),*map(str,sources)],check=True)
subprocess.run([str(JDK/'bin/java.exe'),'-Djava.io.tmpdir='+str(ROOT/'.cache/tmp'),'-cp',str(OUT)+os.pathsep+cp,'dev.workshopvideo.DownloadServiceChecks'],check=True)
