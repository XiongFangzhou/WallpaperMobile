"""Exercise production scene parsing and option persistence using desktop image fixtures."""
import pathlib,os,subprocess,uuid
ROOT=pathlib.Path(__file__).resolve().parents[1];OUT=ROOT/'android/build/content-host';OUT.mkdir(parents=True,exist_ok=True)
JDK=pathlib.Path(os.environ['WVB_JDK']);jar=ROOT/'.cache/maven/json-20240303.jar'
if not jar.exists():raise RuntimeError('Run check_wallpaper_store.py to prepare the host JSON dependency.')
cp=os.pathsep.join(map(str,[jar,ROOT/'android/build/core',*(ROOT/'.tools/android-libs').glob('*.jar')]))
sources=list((ROOT/'android/test-host/content').rglob('*.java'))+[ROOT/'android/src/dev/workshopvideo/WallpaperContent.java',ROOT/'android/src/dev/workshopvideo/EffectOptions.java',ROOT/'android/src/dev/workshopvideo/WallpaperPackage.java',ROOT/'android/src/dev/workshopvideo/SceneAdapter.java',ROOT/'android/src/dev/workshopvideo/SceneDownload.java',ROOT/'android/src/dev/workshopvideo/SceneEffects.java',ROOT/'android/src/dev/workshopvideo/SceneParticles.java']
subprocess.run([str(JDK/'bin/javac.exe'),'-encoding','UTF-8','--release','17','-cp',cp,'-d',str(OUT),*map(str,sources)],check=True)
subprocess.run([str(JDK/'bin/java.exe'),'-Dfile.encoding=UTF-8','-Djava.awt.headless=true','-cp',str(OUT)+os.pathsep+cp,'dev.workshopvideo.ContentHostChecks',str(ROOT/'.cache/tmp'/('content-host-'+uuid.uuid4().hex)),str(ROOT/'android/assets/demo-scene.zip')],check=True)
