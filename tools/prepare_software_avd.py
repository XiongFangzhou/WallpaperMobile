"""Install a pinned, software-CPU-capable Android toolchain entirely on E:."""
from pathlib import Path
import concurrent.futures,hashlib,urllib.request,zipfile,shutil
ROOT=Path(__file__).resolve().parents[1]
def fetch(url,name,destination,sha1=None):
 archive=ROOT/'.cache/downloads'/name;archive.parent.mkdir(parents=True,exist_ok=True)
 if destination.exists():return
 if not archive.exists():
  pending=archive.with_suffix('.part');print('Downloading '+name,flush=True)
  with urllib.request.urlopen(url,timeout=45) as src,pending.open('wb') as out:
   shutil.copyfileobj(src,out,1024*1024)
  pending.replace(archive)
 digest=hashlib.sha1(archive.read_bytes()).hexdigest()
 if sha1 and digest!=sha1:raise RuntimeError('Checksum mismatch: '+name)
 unpack=destination.parent/(destination.name+'-unpack');unpack.mkdir(parents=True,exist_ok=True)
 with zipfile.ZipFile(archive) as z:
  bad=z.testzip()
  if bad:raise RuntimeError('ZIP CRC failure '+bad)
  z.extractall(unpack)
 children=list(unpack.iterdir())
 if len(children)!=1 or not children[0].is_dir():raise RuntimeError('Unexpected archive layout')
 destination.parent.mkdir(parents=True,exist_ok=True);children[0].rename(destination);unpack.rmdir()
 print('Ready '+str(destination)+' SHA1='+digest,flush=True)
if __name__=='__main__':
 specs=[('https://dl.google.com/android/repository/emulator-windows_x64-9322596.zip','emulator-windows_x64-9322596.zip',ROOT/'.tools/emulator-software','632eba430f67abeddcf15ceb0a872b25fdbb7e62'),('https://dl.google.com/android/repository/sys-img/android/x86_64-30_r11.zip','aosp-x86_64-30_r11.zip',ROOT/'.tools/android-sdk/system-images/android-30/default/x86_64','5e4de3946d46f88856c35efcc4d797b381456347')]
 with concurrent.futures.ThreadPoolExecutor(2) as pool:
  for result in pool.map(lambda a:fetch(*a),specs):pass

 avd=ROOT/'.cache/avd/WESoftware.avd';avd.mkdir(parents=True,exist_ok=True)
 config=avd/'config.ini'
 if not config.exists():
     config.write_text("AvdId=WESoftware\navd.ini.displayname=WallpaperMobile Android 11\nabi.type=x86_64\nhw.cpu.arch=x86_64\nhw.cpu.ncore=1\nhw.ramSize=1536\nhw.lcd.width=480\nhw.lcd.height=960\nhw.lcd.density=240\nhw.keyboard=yes\nhw.gpu.enabled=yes\nhw.gpu.mode=swiftshader_indirect\nimage.sysdir.1=system-images/android-30/default/x86_64/\ntag.id=default\ndisk.dataPartition.size=2147483648\nPlayStore.enabled=false\nfastboot.forceColdBoot=yes\n",encoding='utf-8')
 (ROOT/'.cache/avd/WESoftware.ini').write_text('avd.ini.encoding=UTF-8\npath='+str(avd)+'\ntarget=android-30\n',encoding='utf-8')
