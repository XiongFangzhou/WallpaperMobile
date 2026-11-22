"""Run production scene draw code in Android API 35 native graphics via isolated Robolectric."""
import pathlib,os,subprocess,sys
ROOT=pathlib.Path(__file__).resolve().parents[1];OUT=ROOT/'android/build/nativegraphics';OUT.mkdir(parents=True,exist_ok=True);JDK=pathlib.Path(os.environ['WVB_JDK'])
SDK=ROOT/'.cache/robolectric-maven/org/robolectric/android-all/15-robolectric-13954326/android-all-15-robolectric-13954326.jar'
cp=os.pathsep.join(map(str,[SDK,ROOT/'android/build/core',ROOT/'android/build/classes',*(ROOT/'.tools/robolectric-libs').glob('*.jar'),*(ROOT/'.tools/android-libs').glob('*.jar')]))
sources=list((ROOT/'android/test-host/nativegraphics').rglob('*.java'))+[ROOT/'android/src/dev/workshopvideo'/f for f in ['AppSettings.java','GeneralSettings.java','AppSheet.java','WallpaperColorPicker.java','ScenePainter.java','WallpaperEffects.java','CanvasWallpaper.java','EffectPanel.java','Ui.java','WallpaperContent.java','EffectOptions.java','SceneAdapter.java','SceneDownload.java','WallpaperPackage.java']]
subprocess.run([str(JDK/'bin/javac.exe'),'-encoding','UTF-8','--release','17','-cp',cp,'-d',str(OUT),*map(str,sources)],check=True)
work=ROOT/'.cache/android-native-graphics';work.mkdir(parents=True,exist_ok=True)
# Offline SDK jar selection prevents writes to a global ~/.m2 cache.
sdk_dir=ROOT/'.cache/robolectric-sdk';sdk_dir.mkdir(parents=True,exist_ok=True)
link=sdk_dir/SDK.name
if not link.exists():os.link(SDK,link)
subprocess.run([str(JDK/'bin/java.exe'),'-Xmx2g','-Dfile.encoding=UTF-8','-Djava.io.tmpdir='+str(ROOT/'.cache/tmp'),'-Duser.home='+str(ROOT/'.cache/android-native-home'),'-Drobolectric.offline=true','-Drobolectric.usePreinstrumentedJars=false','-Drobolectric.dependency.dir='+str(ROOT/'.cache/robolectric-sdk'),'-Dgraphics.work='+str(work),'-cp',str(OUT)+os.pathsep+cp,'org.junit.runner.JUnitCore',*(sys.argv[1:] or ['dev.workshopvideo.SceneGraphicsChecks'])],check=True)
