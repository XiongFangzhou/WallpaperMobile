"""Pinned Media3 progressive playback dependencies, confined to E: with Maven checksums."""
import sys,hashlib,json,zipfile,urllib.request,xml.etree.ElementTree as ET,concurrent.futures
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'.tools/android-libs';CACHE=ROOT/'.cache/maven/media3';CACHE.mkdir(parents=True,exist_ok=True)
NS={'m':'http://maven.apache.org/POM/4.0.0'};PIN={('androidx.annotation','annotation'):'1.6.0',('androidx.collection','collection'):'1.2.0',('androidx.core','core'):'1.8.0',('org.jetbrains.kotlin','kotlin-stdlib'):'2.2.21'}
def fetch(relative):
 path=CACHE/relative
 if path.exists():return path
 path.parent.mkdir(parents=True,exist_ok=True)
 bases=['https://dl.google.com/dl/android/maven2/'] if relative.startswith('androidx/') else ['https://maven-central.storage-download.googleapis.com/maven2/','https://repo.maven.apache.org/maven2/']
 for base in bases:
  try:data=urllib.request.urlopen(base+relative,timeout=18).read();path.write_bytes(data);return path
  except OSError as e:last=e
 raise last
def path(key,ext):g,a,v=key;return f"{g.replace('.', '/')}/{a}/{v}/{a}-{v}.{ext}"
def text(node,key,default=''):
 value=node.find('m:'+key,NS);return value.text.strip() if value is not None and value.text else default
def model(key):
 import android_dependencies as resolver
 resolver.download=fetch;resolver.PINNED.update(PIN)
 info=resolver.model(*key)
 return key,info['packaging'],info['dependencies']
def artifact(item):
 key,ext,_=item
 if ext=='pom':return None
 if ext=='bundle':ext='jar'
 print('Fetch',key,ext,flush=True)
 raw=fetch(path(key,ext));expected=fetch(path(key,ext)+'.sha1').read_text().strip().split()[0]
 if hashlib.sha1(raw.read_bytes()).hexdigest()!=expected:raise RuntimeError('Checksum mismatch '+str(key))
 output=OUT/(key[1]+'-'+key[2]+'.jar');resources=None;package=None
 if ext=='aar':
  with zipfile.ZipFile(raw) as z:
   output.write_bytes(z.read('classes.jar'))
   doc=ET.fromstring(z.read('AndroidManifest.xml'));package=doc.attrib.get('package')
   names=[n for n in z.namelist() if n.startswith('res/') and not n.endswith('/')]
   if names:
    resources=ROOT/'.cache/android-aar'/key[1]/'res'
    for n in names:
     target=resources/n[4:];target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(z.read(n))
 else:output.write_bytes(raw.read_bytes())
 result={'coordinate':':'.join(key),'artifact':path(key,ext),'sha256':hashlib.sha256(raw.read_bytes()).hexdigest(),'jar':output.relative_to(ROOT).as_posix()}
 if resources:result.update(resources=resources.relative_to(ROOT).as_posix(),android_package=package)
 print('Ready',result['coordinate'],flush=True);return result
lockfile=ROOT/'android/dependencies.lock.json';old=json.loads(lockfile.read_text());existing={tuple(i['coordinate'].split(':')[:2]):i['coordinate'].split(':')[2] for i in old};PIN.update(existing)
pending=[('androidx.media3','media3-exoplayer','1.5.1'),('androidx.annotation','annotation-jvm','1.6.0')];models={}
while pending:
 batch=[k for k in pending if k[:2] not in models and k[:2] not in existing];pending=[]
 if not batch:break
 with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:values=list(pool.map(model,batch))
 for item in values:
  key,ext,deps=item
  if key[:2] in models:continue
  models[key[:2]]=item;pending.extend(deps)
with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:new=[v for v in pool.map(artifact,models.values()) if v]
combined=sorted(old+new,key=lambda i:i['coordinate']);lockfile.write_text(json.dumps(combined,indent=2)+'\n',encoding='utf-8')
print('Media3 graph ready:',len(new),'new artifacts')
