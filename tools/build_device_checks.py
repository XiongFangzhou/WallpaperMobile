"""Build a separate signed instrumentation APK. Never included in the distributed app."""
from pathlib import Path
import os,subprocess,zipfile,shutil
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'android/build/device-checks';OUT.mkdir(parents=True,exist_ok=True)
JDK=Path(os.environ['WVB_JDK']);SDK=ROOT/'.tools/android-sdk';BT=SDK/'build-tools/35.0.0';PLATFORM=SDK/'platforms/android-35/android.jar';JAVA=JDK/'bin/java.exe'
def run(args,**kwargs):subprocess.run(list(map(str,args)),cwd=ROOT,check=True,**kwargs)
manifest=OUT/'AndroidManifest.xml';manifest.write_text('<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="dev.workshopvideo.test"><uses-sdk android:minSdkVersion="29" android:targetSdkVersion="35"/><instrumentation android:name="dev.workshopvideo.EmulatorChecks" android:targetPackage="dev.workshopvideo" android:functionalTest="true"/><instrumentation android:name="dev.workshopvideo.WallpaperServiceChecks" android:targetPackage="dev.workshopvideo" android:functionalTest="true"/><application android:label="WallpaperMobile local checks" android:testOnly="true"><activity android:name="dev.workshopvideo.OffsetHostActivity" android:exported="true" android:launchMode="singleTask" android:theme="@android:style/Theme.Wallpaper.NoTitleBar.Fullscreen"/></application></manifest>',encoding='utf-8')
assets=ROOT/'android/test-device/assets'
if not (assets/'validation.mp4').exists():raise RuntimeError('Generate the video fixture first')
run([BT/'aapt2.exe','link','--manifest',manifest,'-I',PLATFORM,'--min-sdk-version','29','--target-sdk-version','35','-A',assets,'-o',OUT/'resources.apk'])
classes=OUT/'classes';
if classes.resolve().parent!=OUT.resolve():raise RuntimeError("Test class output escaped its build directory")
if classes.exists():shutil.rmtree(classes)
classes.mkdir();libs=list((ROOT/'.tools/android-libs').glob('*.jar'));cp=os.pathsep.join(map(str,[PLATFORM,ROOT/'android/build/classes',*libs]))
run([JDK/'bin/javac.exe','-encoding','UTF-8','--release','17','-cp',cp,'-d',classes,*list((ROOT/'android/test-device').rglob('*.java'))])
program=OUT/'tests.jar'
with zipfile.ZipFile(program,'w',zipfile.ZIP_DEFLATED) as z:
 for p in classes.rglob('*.class'):z.write(p,p.relative_to(classes).as_posix())
dex=OUT/'dex';dex.mkdir(exist_ok=True)
run([JAVA,'-Djava.io.tmpdir='+str(ROOT/'.cache/tmp'),'-cp',ROOT/'.tools/r8.jar','com.android.tools.r8.D8','--lib',PLATFORM,'--classpath',ROOT/'android/build/program.jar','--min-api','29','--output',dex,program])
unsigned=OUT/'unsigned.apk'
with zipfile.ZipFile(OUT/'resources.apk') as resources,zipfile.ZipFile(unsigned,'w',zipfile.ZIP_DEFLATED) as z:
 for item in resources.infolist():z.writestr(item,resources.read(item.filename))
 for p in dex.glob('*.dex'):z.write(p,p.name)
run([BT/'zipalign.exe','-f','4',unsigned,OUT/'aligned.apk'])
env=dict(os.environ,WVB_SIGNING_PASSWORD=(ROOT/'.tools/signing/password').read_text().strip());apk=OUT/'WallpaperMobile-checks.apk'
run([JAVA,'-jar',BT/'lib/apksigner.jar','sign','--ks',ROOT/'.tools/signing/workshop-video.keystore','--ks-key-alias','workshop-video','--ks-pass','env:WVB_SIGNING_PASSWORD','--out',apk,OUT/'aligned.apk'],env=env)
run([JAVA,'-jar',BT/'lib/apksigner.jar','verify',apk]);print('Device checks APK:',apk)
