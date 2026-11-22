"""Isolated local Android graphics test runtime. Never added to shipping dependencies."""
from pathlib import Path
import sys,concurrent.futures,json,hashlib,zipfile,urllib.request
ROOT=Path(__file__).resolve().parents[1];sys.path.insert(0,str(ROOT/'tools'));import android_dependencies as resolver
OUT=ROOT/'.tools/robolectric-libs';OUT.mkdir(parents=True,exist_ok=True);CACHE=ROOT/'.cache/robolectric-maven';CACHE.mkdir(parents=True,exist_ok=True)
def download(relative):
 p=CACHE/relative
 if p.exists():return p
 p.parent.mkdir(parents=True,exist_ok=True)
 bases=['https://dl.google.com/dl/android/maven2/'] if relative.startswith('androidx/') else ['https://maven-central.storage-download.googleapis.com/maven2/']
 url=bases[0]+relative
 if relative.endswith('.jar'):
  with urllib.request.urlopen(urllib.request.Request(url,method='HEAD'),timeout=20) as r:size=int(r.headers.get('Content-Length','0'))
  if size>12*1024*1024:
   block=4*1024*1024;parts=list(range(0,size,block))
   def part(at):
    dest=p.with_name(p.name+'.'+str(at)+'.part');expected=min(block,size-at)
    if dest.exists() and dest.stat().st_size==expected:return dest
    for attempt in range(3):
     try:
      request=urllib.request.Request(url+"?range="+str(at),headers={'Range':f'bytes={at}-{at+expected-1}'})
      with urllib.request.urlopen(request,timeout=30) as r:
       if r.status!=206:raise RuntimeError('Range download not supported')
       data=r.read()
      if len(data)!=expected:raise RuntimeError('Range length mismatch')
      dest.write_bytes(data);return dest
     except Exception:
      if attempt==2:raise
   print('Downloading bounded ranges',p.name,size,flush=True)
   with concurrent.futures.ThreadPoolExecutor(12) as pool:ready=list(pool.map(part,parts))
   with p.open('wb') as out:
    for piece in ready:out.write(piece.read_bytes());piece.unlink()
   return p
 with urllib.request.urlopen(url,timeout=40) as r:p.write_bytes(r.read())
 return p
resolver.download=download;resolver.PINNED={('org.robolectric','android-all'):'15-robolectric-13954326'}
roots=[('org.robolectric','robolectric','4.16.1'),('junit','junit','4.13.2'),('org.hamcrest','hamcrest-core','1.3')];models={};pending=roots
while pending:
 batch=[k for k in pending if k[:2] not in models];pending=[]
 if not batch:break
 with concurrent.futures.ThreadPoolExecutor(6) as pool:values=list(pool.map(lambda k:(k,resolver.model(*k)),batch))
 for k,v in values:
  if k[:2] in models:continue
  models[k[:2]]=v;pending+=v['dependencies']
def artifact(item):
 (g,a),info=item;version=next(k[2] for k in resolver.MODELS if k[:2]==(g,a));ext=info['packaging'];ext='jar' if ext=='bundle' else ext
 if ext=='pom':return None
 relative=resolver.path(g,a,version,ext);p=download(relative);sha=download(relative+'.sha1').read_text().strip().split()[0];assert hashlib.sha1(p.read_bytes()).hexdigest()==sha
 target=OUT/(a+'-'+version+'.jar')
 if ext=='aar':
  with zipfile.ZipFile(p) as z:target.write_bytes(z.read('classes.jar'))
 else:target.write_bytes(p.read_bytes())
 print('Ready',a,version,flush=True);return str(target)
with concurrent.futures.ThreadPoolExecutor(6) as pool:list(pool.map(artifact,models.items()))
# Pin Android API 35 framework independently of Robolectric's online resolver.
for artifact,version in [('android-all','15-robolectric-13954326')]:
 try:
  relative=resolver.path('org.robolectric',artifact,version,'jar');p=download(relative);expected=download(relative+'.sha1').read_text().strip().split()[0];assert hashlib.sha1(p.read_bytes()).hexdigest()==expected;print('Android SDK ready',p,flush=True)
 except Exception as e:print('SDK artifact',artifact,str(e))
