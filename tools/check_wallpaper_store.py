"""Exercise the real WallpaperStore against host preferences/files; no device UI test."""
import hashlib,os,pathlib,subprocess,urllib.request,uuid
ROOT=pathlib.Path(__file__).resolve().parents[1]
OUT=ROOT/'android/build/store-checks';OUT.mkdir(parents=True,exist_ok=True)
jar=ROOT/'.cache/maven/json-20240303.jar'
if not jar.exists():
    url='https://repo.maven.apache.org/maven2/org/json/json/20240303/json-20240303.jar'
    jar.parent.mkdir(parents=True,exist_ok=True);jar.write_bytes(urllib.request.urlopen(url,timeout=25).read())
    expected=urllib.request.urlopen(url+'.sha1',timeout=25).read().decode().strip()
    if hashlib.sha1(jar.read_bytes()).hexdigest()!=expected:raise RuntimeError('Host test JSON dependency checksum mismatch')
jdk=pathlib.Path(os.environ['WVB_JDK'])
cp=os.pathsep.join(map(str,[jar,ROOT/'android/build/core',*(ROOT/'.tools/android-libs').glob('*.jar')]))
sources=list((ROOT/'android/test-host/wallpaper').rglob('*.java'))+[ROOT/'android/src/dev/workshopvideo/WallpaperStore.java']
subprocess.run([str(jdk/'bin/javac.exe'),'-encoding','UTF-8','--release','17','-cp',cp,'-d',str(OUT),*map(str,sources)],check=True,cwd=ROOT)
subprocess.run([str(jdk/'bin/java.exe'),'-Dfile.encoding=UTF-8','-cp',str(OUT)+os.pathsep+cp,'dev.workshopvideo.WallpaperStoreChecks',str(ROOT/'.cache/tmp'/('store-checks-'+uuid.uuid4().hex))],check=True,cwd=ROOT)
