"""Read public WE workshop metadata and download research samples via the running local Steam client. No credentials read."""
import ctypes as C,os,json,time,argparse
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'docs/research/scene-corpus';OUT.mkdir(parents=True,exist_ok=True)
U=C.c_uint64;P=C.c_void_p;I=C.c_uint32;B=C.c_bool
class Details(C.Structure):
 _fields_=[('id',U),('result',C.c_int),('filetype',C.c_int),('creator',I),('consumer',I),('title',C.c_char*129),('description',C.c_char*8000),('owner',U),('created',I),('updated',I),('added',I),('visibility',C.c_int),('banned',B),('accepted',B),('truncated',B),('tags',C.c_char*1025),('file',U),('preview',U),('filename',C.c_char*260),('filesize',C.c_int),('previewsize',C.c_int),('url',C.c_char*256),('up',I),('down',I),('score',C.c_float),('children',I),('total',U)]
class Completed(C.Structure):
 _fields_=[('handle',U),('result',C.c_int),('returned',I),('total',I),('cached',B),('cursor',C.c_char*256)]
class Steam:
 def __enter__(self):
  os.environ['SteamAppId']='431960';os.environ['SteamGameId']='431960'
  dll=Path(os.environ['WVB_STEAM_DLL']).resolve();self.dll_dir=os.add_dll_directory(str(dll.parent));self.lib=C.CDLL(str(dll))
  self.fn('SteamAPI_InitSafe',B,[])();self.ugc=self.fn('SteamAPI_SteamUGC_v020',P,[])();self.utils=self.fn('SteamAPI_SteamUtils_v010',P,[])()
  if not self.ugc or not self.utils:raise RuntimeError('Local Steam API unavailable')
  return self
 def fn(self,name,result,args):
  f=getattr(self.lib,name);f.restype=result;f.argtypes=args;return f
 def ug(self,name,result,args,*values):return self.fn('SteamAPI_ISteamUGC_'+name,result,[P]+args)(self.ugc,*values)
 def wait(self,call):
  failed=B();end=time.monotonic()+40
  while time.monotonic()<end:
   self.lib.SteamAPI_RunCallbacks()
   if self.fn('SteamAPI_ISteamUtils_IsAPICallCompleted',B,[P,U,C.POINTER(B)])(self.utils,call,C.byref(failed)):break
   time.sleep(.1)
  else:raise TimeoutError('Steam public workshop query timeout')
  info=Completed()
  okay=self.fn('SteamAPI_ISteamUtils_GetAPICallResult',B,[P,U,P,C.c_int,C.c_int,C.POINTER(B)])(self.utils,call,C.byref(info),C.sizeof(info),3401,C.byref(failed))
  if not okay or failed.value or info.result!=1:raise RuntimeError(f'Workshop query failed: {info.result} API={okay} failure={failed.value}')
  return info
 def query(self,search='',sort=0):
  handle=self.ug('CreateQueryAllUGCRequestPage',U,[C.c_int,C.c_int,I,I,I],sort,0,431960,431960,1)
  self.ug('AddRequiredTag',B,[U,C.c_char_p],handle,b'Scene')
  self.ug('AddExcludedTag',B,[U,C.c_char_p],handle,b'Mature')
  if search:self.ug('SetSearchText',B,[U,C.c_char_p],handle,search.encode())
  self.ug('SetReturnLongDescription',B,[U,B],handle,False)
  info=self.wait(self.ug('SendQueryUGCRequest',U,[U],handle));result=[]
  for index in range(info.returned):
   detail=Details()
   if self.ug('GetQueryUGCResult',B,[U,I,P],handle,index,C.byref(detail)):
    result.append({'id':str(detail.id),'result':detail.result,'title':detail.title.decode('utf-8','replace'),'tags':detail.tags.decode('utf-8','replace'),'bytes':detail.total or max(0,detail.filesize)})
  self.ug('ReleaseQueryUGCRequest',B,[U],handle);return result
 def status(self,id):
  size=U();folder=C.create_string_buffer(4096);stamp=I();done=U();total=U()
  state=self.ug('GetItemState',I,[U],id)
  installed=self.ug('GetItemInstallInfo',B,[U,C.POINTER(U),P,I,C.POINTER(I)],id,C.byref(size),folder,len(folder),C.byref(stamp))
  self.ug('GetItemDownloadInfo',B,[U,C.POINTER(U),C.POINTER(U)],id,C.byref(done),C.byref(total))
  return {'id':str(id),'state':state,'installed':installed,'path':folder.value.decode('utf-8','replace'),'done':done.value,'total':total.value,'bytes':size.value}
 def __exit__(self,*args):self.lib.SteamAPI_Shutdown();self.dll_dir.close()
def main():
 parser=argparse.ArgumentParser();parser.add_argument('mode',choices=['query','download','status']);parser.add_argument('--search',default='');parser.add_argument('--ids',nargs='*',type=int,default=[]);parser.add_argument('--seconds',type=int,default=20);args=parser.parse_args()
 with Steam() as steam:
  if args.mode=='query':
   values=steam.query(args.search);name='query-'+(''.join(c for c in args.search if c.isalnum()) or 'popular')+'.json';(OUT/name).write_text(json.dumps(values,ensure_ascii=False,indent=2),encoding='utf-8');print(json.dumps(values,ensure_ascii=False),flush=True);return
  if args.mode=='download':
   for id in args.ids:print('DOWNLOAD_REQUEST',id,steam.ug('DownloadItem',B,[U,B],id,True),flush=True)
  until=time.monotonic()+args.seconds
  while True:
   steam.lib.SteamAPI_RunCallbacks();values=[steam.status(id) for id in args.ids]
   if args.mode=='status' or all(v['installed'] and not v['state']&24 for v in values) or time.monotonic()>=until:break
   time.sleep(.25)
  (OUT/'download-status.json').write_text(json.dumps(values,ensure_ascii=False,indent=2),encoding='utf-8');print(json.dumps(values,ensure_ascii=False),flush=True)
if __name__=='__main__':main()
