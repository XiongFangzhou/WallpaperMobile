"""Private, installed workshop corpus integration. Requires the six downloaded Steam samples."""
import os,subprocess,pathlib,sys
ROOT=pathlib.Path(__file__).resolve().parents[1]
subprocess.run([os.environ.get('WVB_PYTHON', sys.executable),str(ROOT/'tools/check_content_host.py')],check=True)
cp=os.pathsep.join(map(str,[ROOT/'android/build/content-host',ROOT/'.cache/maven/json-20240303.jar',ROOT/'android/build/core',*(ROOT/'.tools/android-libs').glob('*.jar')]))
subprocess.run([str(pathlib.Path(os.environ['WVB_JDK'])/'bin/java.exe'),'-Xmx768m','-Dfile.encoding=UTF-8','-Djava.awt.headless=true','-cp',cp,'dev.workshopvideo.SceneCorpusChecks',str(ROOT/'.cache/scene-corpus/converted')],check=True)
