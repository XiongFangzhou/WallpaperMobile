"""Inventory local WE samples as data only; never execute author scripts or shaders."""
from pathlib import Path
import json,struct,collections,hashlib,os
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'docs/research/scene-corpus';CACHE=ROOT/'.cache/scene-corpus';OUT.mkdir(parents=True,exist_ok=True);CACHE.mkdir(parents=True,exist_ok=True)
IDS=['1962375063','1888636115','1132505365','3483582101','3713073223','3601075812']
class Package:
 def __init__(self,path):
  self.path=path;self.f=path.open('rb');self.entries={}
  def num():return struct.unpack('<I',self.f.read(4))[0]
  length=num();assert 8<=length<=32;self.magic=self.f.read(length).decode('ascii');count=num();assert count<20000
  for _ in range(count):
   n=num();assert 0<n<2048;name=self.f.read(n).decode('utf-8');off,size=num(),num();self.entries[name.replace('\\','/')]=(off,size)
  self.base=self.f.tell()
 def read(self,name,limit=2*1024*1024):
  off,size=self.entries[name];assert size<=limit,(name,size);self.f.seek(self.base+off);return self.f.read(size)
 def close(self):self.f.close()
def main():
 summary=[]
 for id in IDS:
  folder=Path(os.environ['WVB_WORKSHOP_DIR'])/id
  if not (folder/'project.json').exists():print(id,'pending');continue
  target=CACHE/id;target.mkdir(exist_ok=True);meta=json.loads((folder/'project.json').read_text(encoding='utf-8-sig'))
  info={'id':id,'title':meta.get('title'),'type':meta.get('type'),'project_file':meta.get('file'),'files':[],'components':collections.Counter(),'effects':collections.Counter(),'shaders':collections.Counter(),'texture_headers':collections.Counter(),'json_files':[]}
  (target/'project.json').write_text(json.dumps(meta,ensure_ascii=False,indent=2),encoding='utf-8')
  for p in folder.rglob('*'):
   if p.is_file():info['files'].append({'name':p.relative_to(folder).as_posix(),'bytes':p.stat().st_size})
  package=next(folder.glob('*.pkg'),None)
  if not package:summary.append(info);continue
  a=Package(package);info['package']={'magic':a.magic,'bytes':package.stat().st_size,'sha256':hashlib.sha256(package.read_bytes()).hexdigest(),'entries':len(a.entries)}
  jsons={}
  for name in a.entries:
   if name.endswith('.json'):
    try:
     raw=a.read(name);data=json.loads(raw.decode('utf-8-sig'));jsons[name]=data;dest=target/name;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(raw);info['json_files'].append(name)
    except Exception as e:info.setdefault('json_errors',[]).append({'file':name,'error':str(e)})
   elif name.endswith('.tex'):
    try:
     raw=a.read(name,32*1024*1024);description={'signature':raw[:24].hex(),'headers':struct.unpack_from('<7I',raw,18),'block':raw[46:55].decode('ascii','replace')};info['texture_headers'][json.dumps(description,sort_keys=True)]+=1
    except Exception as e:info.setdefault('texture_errors',[]).append({'file':name,'error':str(e)})
  scene=jsons.get('scene.json',{});info['general']=scene.get('general',{});info['camera']=scene.get('camera',{});info['object_count']=len(scene.get('objects',[]));info['object_keys']=dict(collections.Counter(key for obj in scene.get('objects',[]) for key in obj))
  for obj in scene.get('objects',[]):
   for k in ['image','particle','text','model','sound','light','script','composition','group']:
    if obj.get(k) is not None:info['components'][k]+=1
   for effect in obj.get('effects',[]):info['effects'][effect.get('file','?')]+=1
  for name,data in jsons.items():
   for p in data.get('passes',[]) if isinstance(data,dict) else []:info['shaders'][p.get('shader','?')]+=1
  info['entry_names']=list(a.entries);a.close();summary.append(info)
 (OUT/'inventory.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2),encoding='utf-8')
 for info in summary:print(json.dumps({k:v for k,v in info.items() if k not in ['files','entry_names','json_files','texture_headers','general','camera']},ensure_ascii=False))
if __name__=='__main__':main()
