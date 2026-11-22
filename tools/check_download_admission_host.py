"""Exercise real download service through transfer -> conversion -> publish or rejection."""
import os,pathlib,subprocess
ROOT=pathlib.Path(__file__).resolve().parents[1];OUT=ROOT/'android/build/admission-host';OUT.mkdir(parents=True,exist_ok=True);JDK=pathlib.Path(os.environ['WVB_JDK'])
cp=os.pathsep.join(map(str,[ROOT/'.cache/maven/json-20240303.jar',ROOT/'android/build/core',ROOT/'android/build/classes',ROOT/'.tools/android-sdk/platforms/android-35/android.jar',*(ROOT/'.tools/android-libs').glob('*.jar')]))
sources=list((ROOT/'android/test-host/downloads').rglob('*.java'))+list((ROOT/'android/test-host/content/android').rglob('*.java'))+list((ROOT/'android/test-host/admission').rglob('*.java'))
sources += [ROOT/'android/src/dev/workshopvideo'/f for f in ['VideoDownloadService.java','SceneDownload.java','SceneAdapter.java','WallpaperContent.java','WallpaperPackage.java','EffectOptions.java']]
subprocess.run([str(JDK/'bin/javac.exe'),'-encoding','UTF-8','--release','17','-cp',cp,'-d',str(OUT),*map(str,sources)],check=True)
subprocess.run([str(JDK/'bin/java.exe'),'-Dfile.encoding=UTF-8','-Djava.io.tmpdir='+str(ROOT/'.cache/tmp'),'-cp',str(OUT)+os.pathsep+cp,'dev.workshopvideo.DownloadAdmissionChecks'],check=True)
